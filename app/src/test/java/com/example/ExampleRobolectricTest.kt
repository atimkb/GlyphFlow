package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.StarterPresets
import com.example.domain.model.ConditionType
import com.example.domain.model.SystemSignalEvent
import com.example.domain.model.SystemToggleFilter
import com.example.domain.model.TextMatchField
import com.example.domain.model.TextMatchOperator
import com.example.domain.model.TriggerRule
import com.example.engine.GlyphPatternGenerator
import com.example.hardware.GlyphHardwareController
import com.example.hardware.GlyphAperture
import com.example.domain.model.GlyphFrame
import com.example.ui.screens.generateGlyphScriptCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import com.example.engine.AuditDiagnosticsBuffer
import com.example.engine.EventIngestBus
import com.example.domain.model.NotificationPayloadAuditEntry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GlyphFlow", appName)
        assertEquals("com.amit.glyphvisualizer", context.packageName)
    }

    @Test
    fun `initial database and starter presets are completely clean with zero hardcoded rules`() = runBlocking {
        assertTrue(StarterPresets.buildStarterAnimations().isEmpty())
        assertTrue(StarterPresets.buildStarterRules().isEmpty())

        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        app.repository.ensureSeeded()
        val initialRules = app.repository.getAllRulesSnapshot()
        assertEquals(0, initialRules.size)
    }

    @Test
    fun `circular matrix mask zeroes corners and keeps center LEDs`() {
        assertFalse(GlyphHardwareController.isCellInsideCircle(0, 0))
        assertFalse(GlyphHardwareController.isCellInsideCircle(24, 24))
        assertTrue(GlyphHardwareController.isCellInsideCircle(12, 12))
        assertTrue(GlyphHardwareController.isCellInsideCircle(0, 12))

        val fullWhite = ByteArray(625) { 255.toByte() }
        val masked = GlyphHardwareController.applyCircularMask(fullWhite)
        assertEquals(0, masked[0].toInt() and 0xFF)
        assertEquals(255, masked[12 * 25 + 12].toInt() and 0xFF)
    }

    @Test
    fun `dynamic 2D canvas and emoji rasterizer renders prompts and animations into 25x25 circular frames`() {
        val prompts = listOf(
            "draw a right arrow",
            "bear emoji",
            "dog face",
            "wifi logo",
            "crescent moon",
            "running man",
            "🐻",
            "A"
        )

        for (prompt in prompts) {
            val (title, frames) = GlyphPatternGenerator.generateFromPrompt(prompt, 255)
            assertTrue("Title should not be blank for $prompt", title.isNotBlank())
            assertTrue("Frames should not be empty for $prompt", frames.isNotEmpty())
            val firstFrame = frames.first()
            assertTrue(
                "Prompt '$prompt' should illuminate pixels inside the 25x25 circle",
                firstFrame.pixels.any { (it.toInt() and 0xFF) > 0 }
            )
            // Corner outside circular mask must remain 0
            assertEquals(0, firstFrame.getBrightness(0, 0))
            assertEquals(0, firstFrame.getBrightness(24, 24))
        }

        // Verify direct 25x25 Bitmap creation & circular masking
        val unicodeBmp = GlyphPatternGenerator.rasterizeUnicodeToBitmap("★", scale = 1.0f)
        assertEquals(25, unicodeBmp.width)
        assertEquals(25, unicodeBmp.height)

        // Verify keyframe sequence interpolation and circular masking
        val (_, baseFrames) = GlyphPatternGenerator.generateFromPrompt("running man", 255)
        assertTrue(baseFrames.size >= 3)
        val interpolated = GlyphPatternGenerator.interpolateKeyframeSequence(baseFrames, subStepsBetweenKeys = 1)
        assertEquals(baseFrames.size * 2, interpolated.size)
        for (frame in interpolated) {
            assertEquals(0, frame.getBrightness(0, 0))
            assertEquals(0, frame.getBrightness(0, 24))
            assertEquals(0, frame.getBrightness(24, 0))
            assertEquals(0, frame.getBrightness(24, 24))
        }
    }

    @Test
    fun `photo to glyph converter crops and dithers into 625 circular pixels`() {
        val sampleBmp = GlyphPatternGenerator.createSampleCameraBitmap()
        val converted = GlyphPatternGenerator.convertBitmapToCircularGlyph(
            source = sampleBmp,
            contrast = 1.45f,
            dither = true,
            invert = false
        )
        assertEquals(625, converted.size)
        // Corner outside circular mask must be 0
        assertEquals(0, converted[0].toInt() and 0xFF)
        // Center lens highlight should be illuminated
        assertTrue((converted[12 * 25 + 12].toInt() and 0xFF) > 0)
    }

    @Test
    fun `audio waveform shader generates animated circular frames`() {
        val frame0 = GlyphPatternGenerator.buildAudioWaveformShaderFrame(tick = 0, peakBrightness = 255)
        val frame5 = GlyphPatternGenerator.buildAudioWaveformShaderFrame(tick = 5, peakBrightness = 255)
        assertEquals(625, frame0.size)
        assertTrue(frame0.any { (it.toInt() and 0xFF) > 0 })
        assertFalse(frame0.contentEquals(frame5))
    }

    @Test
    fun `granular essential notification matching and quick settings broadcast triggers work`() {
        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        val engine = app.ruleEngine

        // 1. Granular Essential Notification Rule (Sender + Body + Priority Override)
        val essentialRule = TriggerRule(
            name = "Mom Urgent Essential Alert",
            conditionType = ConditionType.NOTIFICATION,
            targetPackageName = "com.whatsapp",
            senderFilterOperator = TextMatchOperator.CONTAINS,
            senderFilterPattern = "Mom",
            bodyFilterOperator = TextMatchOperator.REGEX,
            bodyFilterPattern = "(?i)(urgent|call me|otp)",
            essentialPriorityOverride = true
        )

        assertEquals(10, essentialRule.effectivePriority)

        val matchingSignal = SystemSignalEvent(
            conditionType = ConditionType.NOTIFICATION,
            sourcePackage = "com.whatsapp",
            title = "Mom (WhatsApp)",
            content = "Please call me, it is urgent!"
        )
        val wrongSenderSignal = matchingSignal.copy(title = "Random Group")
        val wrongBodySignal = matchingSignal.copy(content = "See you tomorrow")

        assertTrue(engine.matchesRuleCondition(essentialRule, matchingSignal))
        assertFalse(engine.matchesRuleCondition(essentialRule, wrongSenderSignal))
        assertFalse(engine.matchesRuleCondition(essentialRule, wrongBodySignal))

        // 2. Quick Settings / System State Broadcast Trigger (Ringer Mode Vibrate & Torch ON)
        val ringerRule = TriggerRule(
            name = "Vibrate Mode Glyph",
            conditionType = ConditionType.RINGER_MODE,
            systemToggleFilter = SystemToggleFilter.RINGER_VIBRATE
        )
        val vibrateSignal = SystemSignalEvent(
            conditionType = ConditionType.RINGER_MODE,
            ringerMode = SystemToggleFilter.RINGER_VIBRATE,
            toggleActive = true
        )
        val silentSignal = SystemSignalEvent(
            conditionType = ConditionType.RINGER_MODE,
            ringerMode = SystemToggleFilter.RINGER_SILENT,
            toggleActive = true
        )

        assertTrue(engine.matchesRuleCondition(ringerRule, vibrateSignal))
        assertFalse(engine.matchesRuleCondition(ringerRule, silentSignal))

        val torchRule = TriggerRule(
            name = "Torch Active Glyph",
            conditionType = ConditionType.TORCH_STATE,
            systemToggleFilter = SystemToggleFilter.ENABLED_ON
        )
        assertTrue(
            engine.matchesRuleCondition(
                torchRule,
                SystemSignalEvent(conditionType = ConditionType.TORCH_STATE, toggleActive = true)
            )
        )
        assertFalse(
            engine.matchesRuleCondition(
                torchRule,
                SystemSignalEvent(conditionType = ConditionType.TORCH_STATE, toggleActive = false)
            )
        )
    }

    @Test
    fun `physical toggle verification runs builder pipeline and captures unbound error`() {
        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        val controller = app.hardwareController

        // 1. With embedded fallback bridge attached on non-Nothing emulator/JVM, verifyPhysicalToggle succeeds cleanly
        controller.initializeDriver()
        val connectedError = controller.verifyPhysicalToggle()
        val connectedState = controller.matrixState.value

        assertEquals(497, connectedState.activeDotCount)
        assertEquals(255, connectedState.peakBrightness)
        assertTrue(connectedState.isServiceBound)
        assertTrue(connectedState.isDeviceRegistered)
        assertEquals(null, connectedError)

        // 2. When service is unbound/closed, verifyPhysicalToggle captures the exact GlyphException message
        controller.close()
        val unboundError = controller.verifyPhysicalToggle()
        assertTrue(!unboundError.isNullOrBlank())
        assertTrue(unboundError!!.contains("Please use it after service connected"))

        // Re-initialize so subsequent tests have an active bridge
        controller.initializeDriver()
    }

    @Test
    fun `glyphscript v2 compiler parses multi-layer script and composites ring arrow and 3x5 text`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val script = """
            RULE "Maps Turn & Distance HUD"
            WHEN notification FROM "*"
            EXTRACT {
              var dir = title.regex("(left|right|straight|u-turn)") DEFAULT "straight"
              var dist = body.regex("[0-9]+(m|km|ft)") DEFAULT "100m"
              var pct = progress_percent DEFAULT "75"
            }
            DISPLAY {
              LAYER perimeter {
                ring(progress: pct, style: "clock_cw")
              }
              LAYER nav_arrow {
                arrow(direction: dir, size: 11)
              }
              LAYER hud_text {
                text(content: dist, size: "3x5", align: "center", y: 18)
              }
            }
            BEHAVIOR {
              priority: 10
              until: notification.dismissed
              fallback_clear: true
            }
        """.trimIndent()

        val result = com.example.dsl.GlyphScriptCompiler.compile(
            scriptText = script,
            context = context,
            dryRunTitle = "Turn left onto Main St",
            dryRunBody = "500m to destination",
            dryRunProgress = 80
        )

        assertTrue(result is com.example.dsl.GlyphScriptCompileResult.Success)
        val success = result as com.example.dsl.GlyphScriptCompileResult.Success
        assertEquals("Maps Turn & Distance HUD", success.program.ruleName)
        assertEquals("left", success.extractedPreviewVariables["dir"])
        assertEquals("500m", success.extractedPreviewVariables["dist"])
        assertEquals("80", success.extractedPreviewVariables["pct"])
        assertEquals(3, success.program.displayLayers.size)
        assertTrue(success.previewFrames.isNotEmpty())

        val frame = success.previewFrames.first()
        assertTrue(frame.activeDotCount() > 25)
        // Strictly clamped to circular aperture
        assertEquals(0, frame.getBrightness(0, 0))
        assertEquals(0, frame.getBrightness(24, 24))
    }

    @Test
    fun `glyphscript v2 package resolver fails with exact line error when target app is not installed`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scriptWithUninstalledApp = """
            RULE "Swiggy Food Tracker"
            WHEN notification FROM "NonExistentDeliveryAppXYZ"
            DISPLAY {
              LAYER main {
                ring(progress: 60, style: "clock_cw")
              }
            }
            BEHAVIOR {
              priority: 8
              until: notification.dismissed
              fallback_clear: true
            }
        """.trimIndent()

        val result = com.example.dsl.GlyphScriptCompiler.compile(
            scriptText = scriptWithUninstalledApp,
            context = context
        )

        assertTrue(result is com.example.dsl.GlyphScriptCompileResult.Failure)
        val failure = result as com.example.dsl.GlyphScriptCompileResult.Failure
        assertEquals(2, failure.error.line)
        assertTrue(
            "Expected uninstalled app message",
            failure.error.reason.contains("Target app \"NonExistentDeliveryAppXYZ\" is not installed on this device.")
        )
    }

    @Test
    fun `audit diagnostics buffer logs script compilations binder calls payload extras and exports report`() {
        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        com.example.engine.EventIngestBus.clearAuditLogs()

        com.example.engine.EventIngestBus.recordScriptCompileEvent(
            com.example.domain.model.ScriptCompileAuditEntry(
                ruleName = "Test Nav HUD",
                rawScript = "RULE \"Test Nav HUD\" ...",
                astStatus = "AST VALID (3 layers)",
                packageResolutionOutcome = "Any App (*)",
                isSuccess = true
            )
        )
        com.example.engine.EventIngestBus.recordPayloadInspectorEvent(
            com.example.domain.model.NotificationPayloadAuditEntry(
                packageName = "com.google.android.apps.maps",
                appLabel = "Google Maps",
                rawExtraTitle = "Turn right",
                rawExtraText = "200m ahead",
                rawExtraSubText = "ETA 3 min",
                rawExtraProgress = 70,
                rawExtraProgressMax = 100,
                computedProgressPercent = 70,
                extractedVariablesSummary = "dir=\"right\", dist=\"200m\""
            )
        )

        // Render a frame to trigger hardware binder telemetry recording
        app.hardwareController.renderImmediateFrame(
            pixels = ByteArray(625) { 200.toByte() },
            frameLabel = "Audit Binder Test",
            pushToHardware = true
        )

        val exportReport = com.example.engine.EventIngestBus.buildFullDiagnosticsExportText("A024 Registered")
        assertTrue(exportReport.contains("GLYPHFLOW NP3 DIAGNOSTICS EXPORT"))
        assertTrue(exportReport.contains("Test Nav HUD"))
        assertTrue(exportReport.contains("EXTRA_TITLE=\"Turn right\""))
        assertTrue(exportReport.contains("IGlyphService.setMatrixColors + setAppMatrixColors"))
    }

    @Test
    fun `multi-layer compositor separates upper graphic and lower text without overlap and omits unrequested borders`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scriptWithoutBorder = """
            RULE "Clean Arrow & Distance HUD"
            WHEN notification FROM "*"
            EXTRACT {
              var dir = title.regex("(left|right|straight)") DEFAULT "right"
              var dist = body.regex("[0-9]+m") DEFAULT "450m"
            }
            DISPLAY {
              LAYER nav_arrow {
                arrow(direction: dir, size: 9, pulse: true)
              }
              LAYER hud_text {
                text(content: dist, size: "3x5", align: "center", y: 16)
              }
            }
            BEHAVIOR {
              priority: 10
              until: notification.dismissed
              fallback_clear: true
            }
        """.trimIndent()

        val result = com.example.dsl.GlyphScriptCompiler.compile(
            scriptText = scriptWithoutBorder,
            context = context,
            dryRunTitle = "Turn right in 450m",
            dryRunBody = "450m ahead"
        )
        assertTrue(result is com.example.dsl.GlyphScriptCompileResult.Success)
        val success = result as com.example.dsl.GlyphScriptCompileResult.Success

        // Multi-frame pulse animation generated
        assertTrue("Expected multi-frame animation", success.previewFrames.size > 1)
        val frame0 = success.previewFrames[0]
        val frame2 = success.previewFrames[2]
        assertFalse(frame0.pixels.contentEquals(frame2.pixels))

        // Verify NO default border is rendered at top outer edge (row 0, col 12)
        assertEquals(0, frame0.getBrightness(0, 12))
        assertEquals(0, frame0.getBrightness(24, 12))

        // Verify separator row 14 between upper arrow (rows 3..13) and lower text (rows 16..20) has 0 lit pixels
        for (c in 0 until 25) {
            assertEquals("Row 14 col $c must remain clear between upper arrow and lower text", 0, frame0.getBrightness(14, c))
        }

        // Verify upper zone (rows 3..13) and lower text zone (rows 16..20) both have lit pixels
        val upperLit = (3..13).sumOf { r -> (0 until 25).count { c -> frame0.getBrightness(r, c) > 0 } }
        val lowerLit = (16..20).sumOf { r -> (0 until 25).count { c -> frame0.getBrightness(r, c) > 0 } }
        assertTrue("Upper arrow zone should be lit", upperLit > 0)
        assertTrue("Lower text zone should be lit", lowerLit > 0)
    }

    @Test
    fun `customizable border thickness radius styles and 4-char text truncation work`() {
        // 1. Verify smart 4-char text compaction ("ALERT" -> "ALRT")
        assertEquals("ALRT", com.example.dsl.GlyphFrameCompositor.compactTextToMaxChars("ALERT", 4))
        assertEquals("450M", com.example.dsl.GlyphFrameCompositor.compactTextToMaxChars("450M", 4))

        // 2. Verify border thickness 1 vs 2 vs 3 dot counts
        val ringThin = com.example.dsl.GlyphFrameCompositor.renderRingPrimitive(
            progressPercent = 100,
            style = "solid",
            peakBrightness = 255,
            thickness = 1,
            radius = 12
        )
        val ringMedium = com.example.dsl.GlyphFrameCompositor.renderRingPrimitive(
            progressPercent = 100,
            style = "solid",
            peakBrightness = 255,
            thickness = 2,
            radius = 12
        )
        val ringThick = com.example.dsl.GlyphFrameCompositor.renderRingPrimitive(
            progressPercent = 100,
            style = "solid",
            peakBrightness = 255,
            thickness = 3,
            radius = 12
        )

        val thinCount = ringThin.count { it > 0 }
        val mediumCount = ringMedium.count { it > 0 }
        val thickCount = ringThick.count { it > 0 }
        assertTrue("Thin border should have lit LEDs", thinCount > 20)
        assertTrue("Medium border ($mediumCount) should have more LEDs than thin ($thinCount)", mediumCount > thinCount)
        assertTrue("Thick border ($thickCount) should have more LEDs than medium ($mediumCount)", thickCount > mediumCount)

        // 3. Verify border(...) DSL syntax with dashed style and emoji bounce animation
        val context = ApplicationProvider.getApplicationContext<Context>()
        val borderScript = """
            RULE "Ride Arrival Dashed Border"
            WHEN notification FROM "*"
            DISPLAY {
              LAYER outer {
                ring(thickness: 2, radius: 12, progress: 80%, style: "dashed")
              }
              LAYER car {
                emoji(glyph: "🚗", scale: 85%, bounce: true)
              }
              LAYER label {
                text(content: "ALERT", size: "3x5", align: "center", y: 16, animate: true)
              }
            }
            BEHAVIOR {
              priority: 9
              until: notification.dismissed
              fallback_clear: true
            }
        """.trimIndent()

        val compiled = com.example.dsl.GlyphScriptCompiler.compile(borderScript, context)
        assertTrue(compiled is com.example.dsl.GlyphScriptCompileResult.Success)
        val success = compiled as com.example.dsl.GlyphScriptCompileResult.Success
        assertTrue(success.previewFrames.size >= 6)
        assertTrue(success.compositeFrames.isNotEmpty())
    }

    @Test
    fun `direct progress_percent extract, emoji y-bounds centering, compact unit suffixing, and extract fallback audit warning work`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.example.engine.AuditDiagnosticsBuffer.clearAuditLogs()

        // 1. Verify unit normalization & suffixing ("mins"/"minutes"/"min" -> "m", "kilometers"/"km" -> "k", "meters" -> "m")
        assertEquals("4m", com.example.engine.RuleEvaluationEngine.formatCompactUnitText("4", "m"))
        assertEquals("4m", com.example.engine.RuleEvaluationEngine.formatCompactUnitText("4 min", "m"))
        assertEquals("12m", com.example.engine.RuleEvaluationEngine.formatCompactUnitText("12 minutes", "m"))
        assertEquals("8k", com.example.engine.RuleEvaluationEngine.formatCompactUnitText("8 kilometers"))
        assertEquals("5k", com.example.engine.RuleEvaluationEngine.formatCompactUnitText("5 km"))
        assertEquals("450m", com.example.engine.RuleEvaluationEngine.formatCompactUnitText("450 meters"))

        // 2. Verify max 16-dot width enforcement at row 17
        val row17Buffer = com.example.dsl.GlyphFrameCompositor.renderDotMatrixText(
            text = "125 minutes",
            fontSize = "3x5",
            align = "center",
            yOffset = 17,
            suffix = "m"
        )
        val litColsAtRow17 = (0 until 25).filter { c ->
            (17..21).any { r -> row17Buffer[r * 25 + c] > 0 }
        }
        assertTrue("Row 17 text should have lit pixels", litColsAtRow17.isNotEmpty())
        val renderedSpan = litColsAtRow17.max() - litColsAtRow17.min() + 1
        assertTrue("Rendered width at row 17 ($renderedSpan) must not exceed 16 dots", renderedSpan <= 16)

        // 3. Compile script with direct `var progress = progress_percent DEFAULT 0`, `emoji(..., y: 8)`, and `text(..., suffix: "m", y: 17)`
        val deliveryScript = """
            RULE "Delivery Scooter HUD"
            WHEN notification FROM "*"
            EXTRACT {
              var progress = progress_percent DEFAULT 0
              var eta = body.regex("([0-9]+\\s*(?:mins|minutes|min)?)") DEFAULT "5"
            }
            DISPLAY {
              LAYER scooter {
                emoji(glyph: "🛵", scale: 75%, x: 12, y: 8, bounce: false)
              }
              LAYER eta_label {
                text(content: ${'$'}eta, suffix: "m", size: "3x5", align: "center", y: 17)
              }
            }
            BEHAVIOR {
              priority: 9
              until: notification.dismissed
              fallback_clear: true
            }
        """.trimIndent()

        // Test markdown (**), hidden unicode (\u200B), and trailing newline sanitization in body.regex(...)
        val compileResult = com.example.dsl.GlyphScriptCompiler.compile(
            scriptText = deliveryScript,
            context = context,
            dryRunTitle = "Order on the way",
            dryRunBody = "Arriving in **\u200B4 mins**\n\n",
            dryRunProgress = 64
        )
        assertTrue(compileResult is com.example.dsl.GlyphScriptCompileResult.Success)
        val success = compileResult as com.example.dsl.GlyphScriptCompileResult.Success
        assertEquals("64", success.extractedPreviewVariables["progress"])
        assertEquals("4 mins", success.extractedPreviewVariables["eta"])

        // Verify emoji with y: 8 is positioned cleanly in the upper half of the 25x25 aperture
        val frame = success.previewFrames.first()
        val upperEmojiDots = (2..13).sumOf { r -> (0 until 25).count { c -> frame.getBrightness(r, c) > 0 } }
        assertTrue("Emoji centered at y=8 should light upper half pixels", upperEmojiDots > 0)

        // 4. Verify EXTRACT_FALLBACK warning is logged in AuditDiagnosticsBuffer when body.regex(...) fails
        com.example.engine.AuditDiagnosticsBuffer.clearAuditLogs()
        val fallbackVars = com.example.dsl.GlyphFrameCompositor.evaluateExtractions(
            declarations = success.program.extractDeclarations,
            title = "Order status",
            body = "Preparing your order in kitchen",
            subText = "",
            progressPercent = null,
            logFallbackWarnings = true
        )
        assertEquals("0", fallbackVars["progress"])
        assertEquals("5", fallbackVars["eta"])
        val warnings = com.example.engine.AuditDiagnosticsBuffer.extractFallbackWarnings.value
        assertTrue(
            "Expected EXTRACT_FALLBACK warning in AuditDiagnosticsBuffer",
            warnings.contains("EXTRACT_FALLBACK: Variable '\$eta' failed regex on body text, using fallback value.")
        )

        // 5. Verify system prompt contains updated emoji y coordinate and text suffix syntax
        assertTrue(
            com.example.dsl.OFFICIAL_GLYPHSCRIPT_V2_SYSTEM_PROMPT.contains(
                "emoji(glyph: \"🛵\", scale: <50..100%>, y: <4..20>)"
            )
        )
    }

    private fun com.example.dsl.GlyphScriptCompileResult.Success.OFFICIAL_CHECK(): Boolean {
        return com.example.dsl.OFFICIAL_GLYPHSCRIPT_V2_SYSTEM_PROMPT.contains(
            "Do NOT add a ring/border layer unless explicitly requested."
        ) && this.previewFrames.first().activeDotCount() > 15
    }

    @Test
    fun `glyphscript v2 value mapping show_if predicate and sequence cadence timing work`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val mapScript = """
            RULE "Smart Media Player HUD"
            WHEN notification FROM "*"
            EXTRACT {
              var mode = body.map {
                contains(["playing", "resumed"]) => "play",
                contains(["paused", "stopped"]) => "pause",
                regex("(?i)error|fail") => "alert",
                DEFAULT => "idle"
              }
              var volume = subText.map {
                equals("muted") => "0",
                DEFAULT => "80"
              }
            }
            DISPLAY {
              LAYER playing_seq {
                show_if: ${'$'}mode == "play"
                sequence(interval: 100ms, pause: 200ms, hold_last: true, loop: true) {
                  frame """ + "\"\"\"" + """
                  ...#...
                  ..###..
                  """ + "\"\"\"" + """
                  frame """ + "\"\"\"" + """
                  ..###..
                  .#####.
                  """ + "\"\"\"" + """
                }
              }
              LAYER paused_icon {
                show_if: ${'$'}mode == "pause"
                icon(name: "bell", pulse: true)
              }
            }
            BEHAVIOR {
              priority: 8
              until: notification.dismissed
            }
        """.trimIndent()

        // 1. Evaluate with body = "Now playing music" -> mode = "play"
        val (parsedPgm, parseErr) = com.example.dsl.GlyphScriptCompiler.parseProgramOnly(mapScript)
        if (parseErr != null) {
            println("PARSE ERROR: ${parseErr.formattedMessage}")
        }
        val compiledPlay = com.example.dsl.GlyphScriptCompiler.compile(
            scriptText = mapScript,
            context = context,
            dryRunBody = "Now playing music",
            dryRunSubText = "Volume 80%"
        )
        if (compiledPlay is com.example.dsl.GlyphScriptCompileResult.Failure) {
            throw IllegalStateException("compiledPlay failed: ${compiledPlay.error.reason} (formatted: ${compiledPlay.error.formattedMessage}, line: ${compiledPlay.error.line}, col: ${compiledPlay.error.column})")
        }
        val playSuccess = compiledPlay as com.example.dsl.GlyphScriptCompileResult.Success
        assertEquals("play", playSuccess.extractedPreviewVariables["mode"])
        assertEquals("80", playSuccess.extractedPreviewVariables["volume"])

        // 2. Evaluate with body = "Audio paused" -> mode = "pause"
        val compiledPause = com.example.dsl.GlyphScriptCompiler.compile(
            scriptText = mapScript,
            context = context,
            dryRunBody = "Audio paused",
            dryRunSubText = "muted"
        )
        assertTrue(compiledPause is com.example.dsl.GlyphScriptCompileResult.Success)
        val pauseSuccess = compiledPause as com.example.dsl.GlyphScriptCompileResult.Success
        assertEquals("pause", pauseSuccess.extractedPreviewVariables["mode"])
        assertEquals("0", pauseSuccess.extractedPreviewVariables["volume"])

        // Verify sequence primitive parsed interval, pause, and hold_last correctly
        val seqPrim = playSuccess.program.displayLayers.first().primitives.first() as com.example.dsl.LayerPrimitive.SequencePrimitive
        assertEquals(100L, seqPrim.intervalMs)
        assertEquals(200L, seqPrim.pauseMs)
        assertTrue(seqPrim.holdLast)
    }

    @Test
    fun `undeclared variable in layer emits E102 with line and column`() {
        val script = """
            RULE "Test E102"
            WHEN notification FROM "*"
            DISPLAY {
              LAYER main {
                ring(progress: ${'$'}progress, style: "solid")
              }
            }
            BEHAVIOR {
              priority: 8
              until: notification.dismissed
            }
        """.trimIndent()

        val result = com.example.dsl.GlyphScriptCompiler.compile(scriptText = script, context = ApplicationProvider.getApplicationContext())
        assertTrue(result is com.example.dsl.GlyphScriptCompileResult.Failure)
        val failure = result as com.example.dsl.GlyphScriptCompileResult.Failure
        val err102 = failure.diagnostics.find { it.code == "E102" }
        assertTrue("Expected E102 diagnostic", err102 != null)
        assertEquals("E102", err102!!.code)
        assertTrue("Undeclared variable '\$progress'", err102.message.contains("\$progress"))
        assertTrue("Expected line 4 or 5", err102.line in 4..5)
    }

    @Test
    fun `missing DEFAULT fallback in regex or map emits E105`() {
        val scriptRegexMissingDefault = """
            RULE "Test E105 Regex"
            WHEN notification FROM "*"
            EXTRACT {
              var dist = body.regex("[0-9]+m")
            }
            DISPLAY {
              LAYER main {
                text(content: dist, size: "3x5", y: 17)
              }
            }
            BEHAVIOR {
              priority: 8
              until: notification.dismissed
            }
        """.trimIndent()

        val result = com.example.dsl.GlyphScriptCompiler.compile(scriptText = scriptRegexMissingDefault, context = ApplicationProvider.getApplicationContext())
        assertTrue(result is com.example.dsl.GlyphScriptCompileResult.Failure)
        val failure = result as com.example.dsl.GlyphScriptCompileResult.Failure
        val err105 = failure.diagnostics.find { it.code == "E105" }
        assertTrue("Expected E105 diagnostic for missing DEFAULT", err105 != null)
        assertEquals("E105", err105!!.code)
    }

    @Test
    fun `multiple parser syntax errors collect together in single pass`() {
        val multiErrorScript = """
            RULE "Multi Error Test"
            WHEN notification FROM "*"
            EXTRACT {
              var a = body.regex("[0-9]+")
            }
            DISPLAY {
              LAYER layer1 {
                ring(progress: ${'$'}undeclaredVar, style: "solid")
              }
            }
            BEHAVIOR {
              PRIORITY 8
              until: notification.dismissed
            }
        """.trimIndent()

        val result = com.example.dsl.GlyphScriptCompiler.compile(scriptText = multiErrorScript, context = ApplicationProvider.getApplicationContext())
        assertTrue(result is com.example.dsl.GlyphScriptCompileResult.Failure)
        val failure = result as com.example.dsl.GlyphScriptCompileResult.Failure
        assertTrue("Expected multiple diagnostics", failure.diagnostics.size >= 2)
        val codes = failure.diagnostics.map { it.code }
        assertTrue("Expected E105 and E102 or E101", codes.contains("E105") && (codes.contains("E102") || codes.contains("E101")))
    }

    @Test
    fun `layer group mutual exclusivity renders exactly one layer per state`() {
        val groupScript = """
            RULE "Nav Direction Group"
            WHEN notification FROM "*"
            EXTRACT {
              var dir = title.regex("(left|right)") DEFAULT "straight"
            }
            DISPLAY {
              LAYER turn_left {
                group: "nav"
                show_if: ${'$'}dir == "left"
                arrow(direction: "left", size: 11)
              }
              LAYER turn_right {
                group: "nav"
                show_if: ${'$'}dir == "right"
                arrow(direction: "right", size: 11)
              }
              LAYER straight_fallback {
                group: "nav"
                arrow(direction: "straight", size: 11)
              }
            }
            BEHAVIOR {
              priority: 8
              until: notification.dismissed
            }
        """.trimIndent()

        val context = ApplicationProvider.getApplicationContext<Context>()

        // Case 1: dir = "left" -> only turn_left rendered
        val leftResult = com.example.dsl.GlyphScriptCompiler.compile(groupScript, context, dryRunTitle = "turn left")
        assertTrue(leftResult is com.example.dsl.GlyphScriptCompileResult.Success)
        val leftSuccess = leftResult as com.example.dsl.GlyphScriptCompileResult.Success
        val leftVisibleLayers = com.example.dsl.GlyphFrameCompositor.filterVisibleLayers(leftSuccess.program.displayLayers, leftSuccess.extractedPreviewVariables)
        assertEquals(1, leftVisibleLayers.size)
        assertEquals("turn_left", leftVisibleLayers.first().name)

        // Case 2: dir = "right" -> only turn_right rendered
        val rightResult = com.example.dsl.GlyphScriptCompiler.compile(groupScript, context, dryRunTitle = "turn right")
        assertTrue(rightResult is com.example.dsl.GlyphScriptCompileResult.Success)
        val rightSuccess = rightResult as com.example.dsl.GlyphScriptCompileResult.Success
        val rightVisibleLayers = com.example.dsl.GlyphFrameCompositor.filterVisibleLayers(rightSuccess.program.displayLayers, rightSuccess.extractedPreviewVariables)
        assertEquals(1, rightVisibleLayers.size)
        assertEquals("turn_right", rightVisibleLayers.first().name)

        // Case 3: dir = "straight" (fallback) -> only straight_fallback rendered
        val fallbackResult = com.example.dsl.GlyphScriptCompiler.compile(groupScript, context, dryRunTitle = "go straight")
        assertTrue(fallbackResult is com.example.dsl.GlyphScriptCompileResult.Success)
        val fallbackSuccess = fallbackResult as com.example.dsl.GlyphScriptCompileResult.Success
        val fallbackVisibleLayers = com.example.dsl.GlyphFrameCompositor.filterVisibleLayers(fallbackSuccess.program.displayLayers, fallbackSuccess.extractedPreviewVariables)
        assertEquals(1, fallbackVisibleLayers.size)
        assertEquals("straight_fallback", fallbackVisibleLayers.first().name)
    }

    @Test
    fun `two identical consecutive frames result in exactly one binder call`() {
        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        val controller = app.hardwareController
        controller.initializeDriver()
        com.example.engine.EventIngestBus.clearAuditLogs()

        val frameData = ByteArray(625) { 128.toByte() }

        // First push
        controller.renderImmediateFrame(pixels = frameData, frameLabel = "Frame 1", pushToHardware = true)
        val count1 = com.example.engine.EventIngestBus.hardwareBinderLogs.value.size

        // Second identical push
        controller.renderImmediateFrame(pixels = frameData, frameLabel = "Frame 2", pushToHardware = true)
        val count2 = com.example.engine.EventIngestBus.hardwareBinderLogs.value.size

        assertEquals("Identical consecutive frame must skip binder call", count1, count2)
    }

    @Test
    fun `notification distance update retains running animation phase`() {
        val program = com.example.dsl.GlyphScriptProgram(
            ruleName = "Test",
            whenClause = com.example.dsl.WhenClause("notification", "*"),
            extractDeclarations = emptyList(),
            displayLayers = listOf(
                com.example.dsl.DisplayLayer(
                    name = "pulse_arrow",
                    primitives = listOf(
                        com.example.dsl.LayerPrimitive.ArrowPrimitive(directionExpr = "right", pulse = true)
                    )
                )
            ),
            behaviorClause = com.example.dsl.BehaviorClause(),
            sourceText = ""
        )
        val frames1 = com.example.dsl.GlyphFrameCompositor.compositeFrames(
            program = program,
            variables = mapOf("dist" to "500m")
        )
        val frames2 = com.example.dsl.GlyphFrameCompositor.compositeFrames(
            program = program,
            variables = mapOf("dist" to "400m")
        )
        assertEquals(frames1.size, frames2.size)
        for (i in frames1.indices) {
            assertTrue(frames1[i].contentEquals(frames2[i]))
        }
    }

    @Test
    fun `text compacts cleanly at row 17 and long string triggers marquee`() {
        val rasterizer = com.example.dsl.CanvasTextRasterizer
        val fit = rasterizer.resolveFitLadder("1200 m", suffix = "", y = 17, fitMode = "auto")
        assertEquals("1.2k", fit.textToRender)
        val safeWidthAt17 = rasterizer.calculateSafeWidth(17)
        assertEquals(17, safeWidthAt17)
        assertTrue("Compacted text length width must fit safeWidth", 4 * fit.textToRender.length - 1 <= safeWidthAt17)

        val longText = "ABCDEF"
        val overflowFit = rasterizer.resolveFitLadder(longText, suffix = "", y = 17, fitMode = "auto")
        assertTrue(overflowFit.isMarquee)

        val bufferHold = rasterizer.renderText(longText, y = 17, fitMode = "auto", timeClockMs = 100L)
        val bufferScroll = rasterizer.renderText(longText, y = 17, fitMode = "auto", timeClockMs = 600L)

        assertFalse(bufferHold.contentEquals(bufferScroll))

        for (r in 0 until 25) {
            for (c in 0 until 25) {
                if (!GlyphHardwareController.isCellInsideCircle(r, c)) {
                    assertEquals(0, bufferScroll[r * 25 + c])
                }
            }
        }
    }

    @Test
    fun `multi-app package matching WHEN notification FROM array compiles and triggers correctly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AuditDiagnosticsBuffer.clearAuditLogs()

        val multiAppScript = """
            RULE "Quick Commerce & Food Delivery"
            WHEN notification FROM [
                "pkgA",
                "pkgB"
            ]
            EXTRACT {
              var eta = body.regex("([0-9]+m)") DEFAULT "10m"
            }
            DISPLAY {
              LAYER hud {
                text(content: ${'$'}eta, y: 16)
              }
            }
            BEHAVIOR {
              priority: 8
              until: notification.dismissed
            }
        """.trimIndent()

        // 1. WHEN notification FROM ["pkgA", "pkgB"] compiles cleanly into targetPackages = listOf("pkgA", "pkgB")
        val compileResult = com.example.dsl.GlyphScriptCompiler.compile(multiAppScript, context)
        assertTrue("Multi-app script should compile cleanly", compileResult is com.example.dsl.GlyphScriptCompileResult.Success)
        val success = compileResult as com.example.dsl.GlyphScriptCompileResult.Success

        assertEquals(listOf("pkgA", "pkgB"), success.targetPackages)
        assertEquals(listOf("pkgA", "pkgB"), success.program.targetPackages)
        assertEquals(listOf("pkgA", "pkgB"), success.compiledRule.targetPackages)

        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        val engine = app.ruleEngine

        // 2. Notification from "pkgA" triggers the rule
        val signalA = SystemSignalEvent(
            conditionType = ConditionType.NOTIFICATION,
            sourcePackage = "pkgA",
            title = "Order picked up",
            content = "Arriving in 5m"
        )
        assertTrue("Notification from pkgA should match rule", engine.matchesRuleCondition(success.compiledRule, signalA))

        // 3. Notification from "pkgB" triggers the same rule
        val signalB = SystemSignalEvent(
            conditionType = ConditionType.NOTIFICATION,
            sourcePackage = "pkgB",
            title = "Driver approaching",
            content = "Arriving in 3m"
        )
        assertTrue("Notification from pkgB should match rule", engine.matchesRuleCondition(success.compiledRule, signalB))

        // 4. Notification from "pkgC" does NOT trigger the rule
        val signalC = SystemSignalEvent(
            conditionType = ConditionType.NOTIFICATION,
            sourcePackage = "pkgC",
            title = "Irrelevant app",
            content = "Some other notification"
        )
        assertFalse("Notification from pkgC should NOT match rule", engine.matchesRuleCondition(success.compiledRule, signalC))

        // 5. Test Payload Inspector logging: RULE_MATCH: "..." triggered by incoming package: [...]
        AuditDiagnosticsBuffer.clearAuditLogs()
        val entry = NotificationPayloadAuditEntry(
            packageName = "pkgA",
            appLabel = "App A",
            rawExtraTitle = "Title",
            rawExtraText = "5m",
            rawExtraSubText = "",
            rawExtraProgress = null,
            rawExtraProgressMax = null,
            computedProgressPercent = null
        )
        EventIngestBus.recordPayloadInspectorEvent(entry)

        AuditDiagnosticsBuffer.recordMultiAppRuleMatch(success.compiledRule.name, "pkgA")

        val payloadLogs = AuditDiagnosticsBuffer.payloadInspectorLogs.value
        val loggedMatch = payloadLogs.firstOrNull { it.packageName == "pkgA" }?.extractedVariablesSummary
        assertNotNull("Payload inspector log should exist for pkgA", loggedMatch)
        assertTrue("Payload inspector should log RULE_MATCH", loggedMatch!!.contains("RULE_MATCH: \"Quick Commerce & Food Delivery\" triggered by incoming package: [pkgA]"))

        // 6. Test Empty Package list diagnostic: E101 Empty package list in WHEN clause; specify at least one package ID or "*"
        val emptyListScript = """
            RULE "Empty Pkg Test"
            WHEN notification FROM []
            DISPLAY {
              LAYER l {
                ring(progress: 50)
              }
            }
            BEHAVIOR {
              priority: 5
            }
        """.trimIndent()
        val emptyResult = com.example.dsl.GlyphScriptCompiler.compile(emptyListScript, context)
        assertTrue("Empty package list should fail compilation", emptyResult is com.example.dsl.GlyphScriptCompileResult.Failure)
        val failure = emptyResult as com.example.dsl.GlyphScriptCompileResult.Failure
        val diag = failure.diagnostics.firstOrNull { it.code == "E101" }
        assertNotNull("E101 diagnostic should be emitted for empty package list", diag)
        assertTrue("Diagnostic message should mention empty package list", diag!!.message.contains("Empty package list in WHEN clause; specify at least one package ID or \"*\""))
    }

    @Test
    fun `physical 489-LED circular matrix coordinate architecture, LUT, and Studio export test`() {
        // 1. Verify exact 489 physical LEDs and 136 dead corners
        assertEquals(489, GlyphAperture.PHYSICAL_LED_COUNT)
        assertEquals(136, GlyphAperture.DEAD_PIXEL_COUNT)
        assertEquals(625, GlyphAperture.PHYSICAL_LED_MASK.size)

        val physicalCount = GlyphAperture.PHYSICAL_LED_MASK.count { it }
        val deadPixelCount = GlyphAperture.PHYSICAL_LED_MASK.count { !it }
        assertEquals(489, physicalCount)
        assertEquals(136, deadPixelCount)

        // 2. Center LED (12, 12) is physical; corners (0, 0), (0, 24), (24, 0), (24, 24) are dead
        assertTrue(GlyphAperture.isPhysicalLed(12, 12))
        assertFalse(GlyphAperture.isPhysicalLed(0, 0))
        assertFalse(GlyphAperture.isPhysicalLed(0, 24))
        assertFalse(GlyphAperture.isPhysicalLed(24, 0))
        assertFalse(GlyphAperture.isPhysicalLed(24, 24))

        // 3. Safe horizontal chord widths per row
        // Rows 0 & 24: 7 dots wide (x in 9..15)
        assertEquals(7, GlyphAperture.getSafeChordWidth(0))
        assertEquals(7, GlyphAperture.getSafeChordWidth(24))
        for (x in 9..15) {
            assertTrue("Row 0 x=$x should be physical", GlyphAperture.isPhysicalLed(x, 0))
            assertTrue("Row 24 x=$x should be physical", GlyphAperture.isPhysicalLed(x, 24))
        }
        assertFalse("Row 0 x=8 should be dead", GlyphAperture.isPhysicalLed(8, 0))
        assertFalse("Row 0 x=16 should be dead", GlyphAperture.isPhysicalLed(16, 0))

        // Rows 3 & 21: 17 dots wide (x in 4..20)
        assertEquals(17, GlyphAperture.getSafeChordWidth(3))
        assertEquals(17, GlyphAperture.getSafeChordWidth(21))

        // Rows 7 to 17: 23 to 25 dots wide
        for (y in 7..17) {
            val chord = GlyphAperture.getSafeChordWidth(y)
            assertTrue("Row $y chord width $chord should be in 23..25", chord in 23..25)
        }

        // Center row 12 is 25 dots wide
        assertEquals(25, GlyphAperture.getSafeChordWidth(12))

        // 4. Buffer clamping strictly zeroes all 136 dead pixels
        val allWhite = ByteArray(625) { 255.toByte() }
        GlyphAperture.clampToPhysicalAperture(allWhite)
        assertEquals(0, allWhite[0].toInt() and 0xFF)
        assertEquals(0, allWhite[24].toInt() and 0xFF)
        assertEquals(0, allWhite[24 * 25].toInt() and 0xFF)
        assertEquals(0, allWhite[24 * 25 + 24].toInt() and 0xFF)
        assertEquals(255, allWhite[12 * 25 + 12].toInt() and 0xFF)

        var nonZeroCount = 0
        for (b in allWhite) {
            if ((b.toInt() and 0xFF) > 0) nonZeroCount++
        }
        assertEquals(489, nonZeroCount)

        // 5. Studio Direct Code Export: single frame produces valid bitmap(align: "center")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val singleFrame = GlyphFrame(durationMs = 120L, pixels = allWhite)
        val singleExport = generateGlyphScriptCode(listOf(singleFrame), "Single Test")
        assertTrue("Single frame export should contain bitmap", singleExport.contains("bitmap(align: \"center\")"))
        val singleCompile = com.example.dsl.GlyphScriptCompiler.compile(singleExport, context)
        assertTrue("Single frame export should compile cleanly", singleCompile is com.example.dsl.GlyphScriptCompileResult.Success)

        // 6. Studio Direct Code Export: multi-frame produces sequence(interval: 150ms, ...)
        val frame2 = singleFrame.copy(durationMs = 150L)
        val multiExport = generateGlyphScriptCode(listOf(singleFrame, frame2), "Sequence Test")
        assertTrue("Multi-frame export should contain sequence", multiExport.contains("sequence(interval: 120ms"))
        val multiCompile = com.example.dsl.GlyphScriptCompiler.compile(multiExport, context)
        assertTrue("Multi-frame export should compile cleanly", multiCompile is com.example.dsl.GlyphScriptCompileResult.Success)
    }

    @Test
    fun `bidirectional GlyphScriptDecompiler decompile and recompile preserves AST logic and layers`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scriptSource = """
RULE "Messenger VIP Alert"
WHEN notification FROM "com.facebook.orca"
EXTRACT {
  var sender = title.regex("([A-Za-z]+)") DEFAULT "Friend"
}
DISPLAY {
  LAYER top_text {
    text(content: sender, align: "center", y: 8)
  }
  LAYER ring_pulse {
    ring(radius: 10, thickness: 1, pulse: true)
  }
}
BEHAVIOR {
  priority: 9
  until: notification.dismissed
  fallback_clear: true
}
""".trimIndent()

        // 1. Decompile
        val decompiled = com.example.dsl.GlyphScriptDecompiler.decompile(scriptSource, context, ruleId = 42L)
        assertEquals("Messenger VIP Alert", decompiled.ruleName)
        assertEquals(listOf("com.facebook.orca"), decompiled.targetPackages)
        assertEquals(2, decompiled.visualLayers.size)

        val textLayer = decompiled.visualLayers.firstOrNull { it.name == "top_text" }
        assertNotNull(textLayer)
        assertEquals("TEXT", textLayer!!.primitiveType)
        assertEquals(8, textLayer.yOffset)

        val ringLayer = decompiled.visualLayers.firstOrNull { it.name == "ring_pulse" }
        assertNotNull(ringLayer)
        assertEquals("RING", ringLayer!!.primitiveType)
        assertEquals(10, ringLayer.ringRadius)
        assertTrue(ringLayer.pulse)

        // 2. Recompile
        val recompiled = com.example.dsl.GlyphScriptDecompiler.recompile(
            rule = decompiled,
            frames = decompiled.frames,
            layers = decompiled.visualLayers
        )
        assertTrue(recompiled.contains("RULE \"Messenger VIP Alert\""))
        assertTrue(recompiled.contains("WHEN notification FROM \"com.facebook.orca\""))
        assertTrue(recompiled.contains("var sender = title.regex("))
        assertTrue(recompiled.contains("text(content: sender, align: \"center\", y: 8)"))
        assertTrue(recompiled.contains("ring(radius: 10, thickness: 1, pulse: true)"))
        assertTrue(recompiled.contains("priority: 9"))

        // Recompiled script must compile successfully
        val compileRes = com.example.dsl.GlyphScriptCompiler.compile(recompiled, context)
        assertTrue("Recompiled script should compile without error", compileRes is com.example.dsl.GlyphScriptCompileResult.Success)
    }

    @Test
    fun `studio nudge respects SAFE_CLAMP vs APERTURE_CLIP bounds`() {
        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        val vm = com.example.ui.GlyphFlowViewModel(app)

        // Light up center dot (12, 12)
        vm.onCanvasCellInteraction(12, 12, isDrag = false)
        val initialPixels = vm.studioState.value.activeFrame.pixels
        assertTrue((initialPixels[12 * 25 + 12].toInt() and 0xFF) > 0)

        // Test SAFE_CLAMP: shifting within circle works
        vm.setNudgeClampMode(com.example.ui.NudgeClampMode.SAFE_CLAMP)
        vm.nudgeStudioCanvas(1, 0)
        val shiftedPixels = vm.studioState.value.activeFrame.pixels
        assertEquals(0, shiftedPixels[12 * 25 + 12].toInt() and 0xFF)
        assertTrue((shiftedPixels[12 * 25 + 13].toInt() and 0xFF) > 0)

        // Shift dot near edge: x = 24, y = 12 is physical edge
        // Nudge to 24 (11 more steps)
        for (i in 0 until 11) {
            vm.nudgeStudioCanvas(1, 0)
        }
        val edgePixels = vm.studioState.value.activeFrame.pixels
        assertTrue((edgePixels[12 * 25 + 24].toInt() and 0xFF) > 0)

        // SAFE_CLAMP: One more step to the right (x=25) would exit grid/circle -> must be blocked
        vm.nudgeStudioCanvas(1, 0)
        val blockedPixels = vm.studioState.value.activeFrame.pixels
        // Dot should still be at x = 24
        assertTrue("SAFE_CLAMP should abort shift when dot reaches boundary", (blockedPixels[12 * 25 + 24].toInt() and 0xFF) > 0)

        // APERTURE_CLIP: Shift to the right allows movement and cleanly zeroes the exiting dot
        vm.setNudgeClampMode(com.example.ui.NudgeClampMode.APERTURE_CLIP)
        vm.nudgeStudioCanvas(1, 0)
        val clippedPixels = vm.studioState.value.activeFrame.pixels
        assertEquals("APERTURE_CLIP should allow shift and clip dots outside circle", 0, clippedPixels[12 * 25 + 24].toInt() and 0xFF)
    }

    @Test
    fun `conditional group state preservation selects active turn_right layer and preserves sibling layers on recompile`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val navScript = """
RULE "Maps Navigation Matrix"
WHEN notification FROM "com.google.android.apps.maps"
EXTRACT {
  var maneuver = title.regex("(left|right)") DEFAULT "right"
  var dist = body.regex("([0-9]+m)") DEFAULT "200m"
}
DISPLAY {
  LAYER turn_left {
    group: "maneuver_group"
    show_if: ${'$'}maneuver == "left"
    arrow(direction: "left", size: 7)
  }
  LAYER turn_right {
    group: "maneuver_group"
    show_if: ${'$'}maneuver == "right"
    arrow(direction: "right", size: 7)
  }
  LAYER distance_display {
    text(content: dist, align: "center", y: 17)
  }
  LAYER perimeter_progress {
    ring(radius: 12, thickness: 1, progress: 72%)
  }
}
BEHAVIOR {
  priority: 9
  until: notification.dismissed
}
""".trimIndent()

        val mockVars = mapOf("maneuver" to "right", "dist" to "200m")
        val decompiled = com.example.dsl.GlyphScriptDecompiler.decompile(
            scriptSource = navScript,
            context = context,
            ruleId = 99L,
            dryRunVariables = mockVars
        )

        assertEquals(4, decompiled.visualLayers.size)
        val initialSelected = decompiled.visualLayers.firstOrNull { it.id == decompiled.initialSelectedLayerId }
        assertNotNull(initialSelected)
        assertEquals("turn_right should be auto-selected when maneuver=right", "turn_right", initialSelected!!.name)

        // Verify composite overlay renders non-targeted layers (distance_display + perimeter_progress)
        val overlayPixels = com.example.dsl.GlyphScriptDecompiler.renderCompositeOverlay(
            layers = decompiled.visualLayers,
            selectedLayerId = initialSelected.id,
            variables = mockVars
        )
        assertTrue("Composite overlay should have lit pixels from 200m text and ring", overlayPixels.any { (it.toInt() and 0xFF) > 0 })

        // Simulate editing turn_right on canvas
        val editedPixels = initialSelected.frames.first().pixels.copyOf()
        editedPixels[12 * 25 + 12] = 255.toByte()
        val editedFrames = listOf(com.example.domain.model.GlyphFrame(durationMs = 150L, pixels = editedPixels))
        val updatedLayers = decompiled.visualLayers.map {
            if (it.id == initialSelected.id) it.copy(frames = editedFrames, isCanvasEdited = true) else it
        }

        val recompiled = com.example.dsl.GlyphScriptDecompiler.recompile(
            rule = decompiled,
            frames = editedFrames,
            layers = updatedLayers,
            activeLayerId = initialSelected.id
        )

        assertTrue(recompiled.contains("LAYER turn_left"))
        assertTrue(recompiled.contains("LAYER turn_right"))
        assertTrue(recompiled.contains("group: \"maneuver_group\""))
        assertTrue(recompiled.contains("show_if: ${'$'}maneuver == \"right\""))
        assertTrue(recompiled.contains("LAYER distance_display"))
        assertTrue(recompiled.contains("LAYER perimeter_progress"))

        val compileRes = com.example.dsl.GlyphScriptCompiler.compile(recompiled, context)
        assertTrue("Recompiled multi-layer script must compile cleanly", compileRes is com.example.dsl.GlyphScriptCompileResult.Success)
    }

    @Test
    fun `studio guidelines axis lock pocket fill and marquee selection copy paste nudge flip rotate work`() {
        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        val vm = com.example.ui.GlyphFlowViewModel(app)

        // 1. Test Smart Guidelines + Axis Lock (Mode A)
        vm.createNewBlankStudioAnimation()
        vm.addRowGuide(10)
        vm.addColGuide(14)
        vm.selectActiveGuide(rowGuide = 10, colGuide = null)
        vm.setAxisLockEnabled(true)

        // Attempt to draw at (12, 8) while Axis Lock is locked to row Y=10 -> dot should land at (10, 8)
        vm.onCanvasCellInteraction(row = 12, col = 8, isDrag = true)
        val afterAxisLock = vm.studioState.value.activeFrame.pixels
        assertTrue("Axis Lock should plot on locked row Y=10", (afterAxisLock[10 * 25 + 8].toInt() and 0xFF) > 0)
        assertEquals("Original row Y=12 should remain unlit", 0, afterAxisLock[12 * 25 + 8].toInt() and 0xFF)

        // 2. Test Pocket Fill (Mode B): Bound a 3x3 pocket between Row guides 10 & 14 and Col guides 10 & 14
        vm.createNewBlankStudioAnimation()
        vm.clearAllGuides()
        vm.addRowGuide(10)
        vm.addRowGuide(14)
        vm.addColGuide(10)
        vm.addColGuide(14)
        vm.fillPocketAt(12, 12)

        val pocketPixels = vm.studioState.value.activeFrame.pixels
        // Inside pocket (rows 11..13, cols 11..13) should be lit
        for (r in 11..13) {
            for (c in 11..13) {
                assertTrue("Pocket interior ($r, $c) must be filled", (pocketPixels[r * 25 + c].toInt() and 0xFF) > 0)
            }
        }
        // Outside pocket (e.g., row 9 or row 15 or col 9 or col 15) must remain 0
        assertEquals(0, pocketPixels[9 * 25 + 12].toInt() and 0xFF)
        assertEquals(0, pocketPixels[15 * 25 + 12].toInt() and 0xFF)
        assertEquals(0, pocketPixels[12 * 25 + 9].toInt() and 0xFF)
        assertEquals(0, pocketPixels[12 * 25 + 15].toInt() and 0xFF)

        // 3. Test Marquee Selection: Copy, Paste, Nudge, Flip H, and Rotate 90
        vm.createNewBlankStudioAnimation()
        vm.setCanvasDotExplicit(10, 10, true)
        vm.setCanvasDotExplicit(10, 12, true)
        vm.setSelectionRegion(10, 10, 11, 12)
        vm.copySelectedRegion()
        assertNotNull("Clipboard should contain copied region", vm.studioState.value.clipboardPixels)

        // Nudge selected region right by +1 col
        vm.nudgeSelectedRegion(dx = 1, dy = 0)
        val nudged = vm.studioState.value.activeFrame.pixels
        assertEquals(0, nudged[10 * 25 + 10].toInt() and 0xFF)
        assertTrue((nudged[10 * 25 + 11].toInt() and 0xFF) > 0)
        assertTrue((nudged[10 * 25 + 13].toInt() and 0xFF) > 0)

        // Paste clipboard at (14, 10)
        vm.pasteClipboardRegion(14, 10)
        val pasted = vm.studioState.value.activeFrame.pixels
        assertTrue((pasted[14 * 25 + 10].toInt() and 0xFF) > 0)
        assertTrue((pasted[14 * 25 + 12].toInt() and 0xFF) > 0)

        // Flip and Rotate selection without crashing or lighting dead corners
        vm.flipSelectedRegion(horizontal = true)
        vm.rotateSelectedRegion90()
        val finalPixels = vm.studioState.value.activeFrame.pixels
        assertEquals("Dead corner (0,0) must remain 0", 0, finalPixels[0].toInt() and 0xFF)
    }

    @Test
    fun `studio atomic save persistence and bidirectional tab switch sync work`() {
        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        val vm = com.example.ui.GlyphFlowViewModel(app)

        // 1. Load a rule script in Console with directional arrow layers
        val navScript = """
            RULE "Maps Turn HUD"
            WHEN notification FROM "com.google.android.apps.maps"
            EXTRACT {
              var maneuver = title.map {
                contains(["left"]) => "left",
                contains(["right"]) => "right",
                DEFAULT => "straight"
              }
            }
            DISPLAY {
              LAYER turn_left {
                group: "dir"
                show_if: ${'$'}maneuver == "left"
                arrow(direction: "left", size: 9)
              }
              LAYER turn_right {
                group: "dir"
                show_if: ${'$'}maneuver == "right"
                arrow(direction: "right", size: 9)
              }
            }
            BEHAVIOR {
              priority: 9
              until: notification.dismissed
            }
        """.trimIndent()

        vm.updateConsoleScriptInput(navScript)
        vm.recompileAndRunDryRun(pushToHardware = false)

        // 2. Navigate to Studio tab via bottom bar — must bind directly to the active Console script
        vm.selectTab(com.example.ui.GlyphScreenTab.STUDIO)
        assertEquals("Maps Turn HUD", vm.studioState.value.name)
        assertTrue(
            "Procedural arrow layers must be rasterized into editable layers in Studio",
            vm.studioState.value.visualLayers.any { it.name == "turn_left" && it.frames.first().activeDotCount() > 0 }
        )

        // 3. Select turn_left layer, clear it, draw a custom dot at (5, 12), and call onSaveStudioChanges()
        val leftLayer = vm.studioState.value.visualLayers.first { it.name == "turn_left" }
        vm.selectVisualLayer(leftLayer.id)
        vm.applyMatrixTransform(com.example.domain.model.MatrixTransform.CLEAR)
        vm.setCanvasDotExplicit(row = 5, col = 12, addDot = true)
        vm.onSaveStudioChanges()

        // 4. Verify Console scriptInput & Dry Run preview immediately reflect the edited pixel at (5, 12)
        val updatedScript = vm.consoleState.value.scriptInput
        assertTrue("Saved script must contain bitmap/sequence for turn_left", updatedScript.contains("LAYER turn_left"))
        assertTrue(
            "Dry Run preview must reflect custom dot at (5, 12) immediately after save",
            (vm.consoleState.value.dryRunPreviewPixels[5 * 25 + 12].toInt() and 0xFF) > 0
        )

        // 5. Switch to Console and back to Studio — design must remain synchronized
        vm.selectTab(com.example.ui.GlyphScreenTab.CONSOLE)
        assertTrue((vm.consoleState.value.dryRunPreviewPixels[5 * 25 + 12].toInt() and 0xFF) > 0)
        vm.selectTab(com.example.ui.GlyphScreenTab.STUDIO)
        assertTrue((vm.studioState.value.activeFrame.pixels[5 * 25 + 12].toInt() and 0xFF) > 0)
    }

    @Test
    fun `advanced layer management centroid rotation and emoji photo rasterizer pipeline serialize to script`() {
        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        val vm = com.example.ui.GlyphFlowViewModel(app)

        // 1. Setup multi-layer rule in Studio
        val multiLayerScript = """
            RULE "Multi Layer Studio Test"
            WHEN notification FROM "*"
            DISPLAY {
              LAYER arrow_left {
                arrow(direction: "left", size: 9)
              }
              LAYER distance_body {
                text(content: "200m", align: "center", y: 17)
              }
            }
            BEHAVIOR {
              priority: 8
              until: notification.dismissed
            }
        """.trimIndent()
        vm.updateConsoleScriptInput(multiLayerScript)
        vm.recompileAndRunDryRun(pushToHardware = false)
        vm.selectTab(com.example.ui.GlyphScreenTab.STUDIO)

        assertEquals(2, vm.studioState.value.visualLayers.size)
        val firstLayerBefore = vm.studioState.value.visualLayers[0].name
        val secondLayerBefore = vm.studioState.value.visualLayers[1].name
        assertEquals("arrow_left", firstLayerBefore)
        assertEquals("distance_body", secondLayerBefore)

        // 2. Reorder via Drag-and-Drop (Z-Index Reordering)
        vm.reorderVisualLayer(0, 1)
        assertEquals("distance_body", vm.studioState.value.visualLayers[0].name)
        assertEquals("arrow_left", vm.studioState.value.visualLayers[1].name)

        // 3. Layer Multi-Select Mode & Batch Actions ([Duplicate Selected], [Merge Selected], [Group / Move], [Delete Selected])
        vm.toggleLayerMultiSelectMode(true)
        assertTrue(vm.studioState.value.isLayerMultiSelectMode)
        val layerIds = vm.studioState.value.visualLayers.map { it.id }
        layerIds.forEach { id ->
            if (id !in vm.studioState.value.selectedLayerIdsForBatch) {
                vm.toggleLayerCheckedForBatch(id)
            }
        }
        assertEquals(2, vm.studioState.value.selectedLayerIdsForBatch.size)

        // Batch Duplicate Selected -> appends _copy layers
        vm.duplicateSelectedLayers()
        assertEquals(4, vm.studioState.value.visualLayers.size)
        assertTrue(vm.studioState.value.visualLayers.any { it.name == "arrow_left_copy" })
        assertTrue(vm.studioState.value.visualLayers.any { it.name == "distance_body_copy" })

        // Batch Group / Move Selected
        vm.groupOrMoveSelectedLayers(moveUp = true)
        assertTrue(vm.studioState.value.visualLayers.any { it.groupName == "batch_group" })

        // Batch Merge Selected -> combines checked layers into unified _merged bitmap layer
        vm.mergeSelectedLayers()
        assertEquals(3, vm.studioState.value.visualLayers.size)
        val mergedLayer = vm.studioState.value.visualLayers.first { it.name.endsWith("_merged") }
        assertTrue("Merged layer must contain active combined LEDs", mergedLayer.frames.first().activeDotCount() > 0)

        // Batch Delete Selected -> deletes the currently checked merged layer
        vm.deleteSelectedLayers()
        assertEquals(2, vm.studioState.value.visualLayers.size)

        // 4. Granular 1°/5° Continuous Centroid-Based Rotation
        vm.createNewBlankStudioAnimation()
        // Plot a horizontal 3-dot bar centered at (row=10, col=10..12) -> geometric centroid is (cx=11.0, cy=10.0)
        vm.setCanvasDotExplicit(row = 10, col = 10, addDot = true)
        vm.setCanvasDotExplicit(row = 10, col = 11, addDot = true)
        vm.setCanvasDotExplicit(row = 10, col = 12, addDot = true)
        vm.rotateSelectedRegionByAngle(1f)
        assertEquals(1f, vm.studioState.value.selectionRotationAngle, 0.01f)
        vm.rotateSelectedRegionByAngle(5f)
        assertEquals(6f, vm.studioState.value.selectionRotationAngle, 0.01f)
        vm.setSelectionRotationAngle(90f)
        val rotatedPixels = vm.studioState.value.activeFrame.pixels
        // Rotating horizontal bar (10,10..12) by 90° around centroid (11,10) produces vertical bar at col=11, row=9..11
        assertTrue("Centroid rotation 90° must light (9, 11)", (rotatedPixels[9 * 25 + 11].toInt() and 0xFF) > 0)
        assertTrue("Centroid rotation 90° must light (10, 11)", (rotatedPixels[10 * 25 + 11].toInt() and 0xFF) > 0)
        assertTrue("Centroid rotation 90° must light (11, 11)", (rotatedPixels[11 * 25 + 11].toInt() and 0xFF) > 0)
        assertTrue("Rotation must push undo snapshot", vm.studioState.value.canUndo)
        vm.commitSelectionRotation()
        assertEquals(0f, vm.studioState.value.selectionRotationAngle, 0.01f)

        // 5. Emoji-to-LED Rasterizer & Photo Otsu+Sobel Thresholding
        vm.rasterizeEmojiToStudioCanvas("🚀")
        val emojiPixels = vm.studioState.value.activeFrame.pixels
        var litEmojiDots = 0
        for (i in emojiPixels.indices) {
            val v = emojiPixels[i].toInt() and 0xFF
            if (v > 0) {
                litEmojiDots++
                assertTrue("PWM brightness must quantize to 64, 128, or 255 (got $v)", v in setOf(64, 128, 255))
            }
        }
        assertTrue("Emoji rasterizer must produce lit 25x25 dots", litEmojiDots > 5)
        assertTrue("Emoji rasterization must write back bitmap to script", vm.consoleState.value.scriptInput.contains("bitmap"))

        // Otsu + Sobel Edge mode & live threshold slider (0%..100%)
        vm.loadSamplePhotoToGlyph()
        vm.updatePhotoThresholdPct(35)
        assertEquals(35, vm.studioState.value.photoThresholdPct)
        vm.togglePhotoEdgeDetect(true)
        assertTrue(vm.studioState.value.photoEdgeDetectEnabled)
        assertTrue(vm.studioState.value.activeFrame.activeDotCount() > 0)
    }

    @Test
    fun `advanced timeline frame management drag reorder multi select batch actions and sequence AST write back work`() {
        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        val vm = com.example.ui.GlyphFlowViewModel(app)

        vm.createNewBlankStudioAnimation()
        // Frame 0: dot at (6, 12)
        vm.setCanvasDotExplicit(row = 6, col = 12, addDot = true)
        // Add Frame 1: dot at (12, 12)
        vm.addBlankFrame()
        vm.setCanvasDotExplicit(row = 12, col = 12, addDot = true)
        // Add Frame 2: dot at (18, 12)
        vm.addBlankFrame()
        vm.setCanvasDotExplicit(row = 18, col = 12, addDot = true)

        assertEquals(3, vm.studioState.value.frames.size)
        assertTrue((vm.studioState.value.frames[0].pixels[6 * 25 + 12].toInt() and 0xFF) > 0)
        assertTrue((vm.studioState.value.frames[1].pixels[12 * 25 + 12].toInt() and 0xFF) > 0)
        assertTrue((vm.studioState.value.frames[2].pixels[18 * 25 + 12].toInt() and 0xFF) > 0)

        // 1. Reorder via Drag-and-Drop: Move Frame 2 (index 2) to index 0
        vm.reorderTimelineFrame(fromIndex = 2, toIndex = 0)
        assertEquals(0, vm.studioState.value.selectedFrameIndex)
        assertTrue("Frame at index 0 should now have dot at (18, 12)", (vm.studioState.value.frames[0].pixels[18 * 25 + 12].toInt() and 0xFF) > 0)
        assertTrue("Frame at index 1 should now have dot at (6, 12)", (vm.studioState.value.frames[1].pixels[6 * 25 + 12].toInt() and 0xFF) > 0)
        assertTrue("Frame at index 2 should now have dot at (12, 12)", (vm.studioState.value.frames[2].pixels[12 * 25 + 12].toInt() and 0xFF) > 0)

        // 2. Multi-Select Frame Mode & Reverse Selected Batch
        vm.toggleFrameMultiSelectMode(true)
        assertTrue(vm.studioState.value.isFrameMultiSelectMode)
        // Select indices 0 and 2 (currently dot at 18,12 and dot at 12,12)
        vm.clearFrameBatchSelection()
        vm.toggleFrameCheckedForBatch(0)
        vm.toggleFrameCheckedForBatch(2)
        assertEquals(setOf(0, 2), vm.studioState.value.selectedFrameIndicesForBatch)

        vm.reverseSelectedFrames()
        // Now index 0 should have dot at (12, 12) and index 2 should have dot at (18, 12), while index 1 stays (6, 12)
        assertTrue("Reversed frame 0 should have dot at (12, 12)", (vm.studioState.value.frames[0].pixels[12 * 25 + 12].toInt() and 0xFF) > 0)
        assertTrue("Unselected frame 1 should remain at (6, 12)", (vm.studioState.value.frames[1].pixels[6 * 25 + 12].toInt() and 0xFF) > 0)
        assertTrue("Reversed frame 2 should have dot at (18, 12)", (vm.studioState.value.frames[2].pixels[18 * 25 + 12].toInt() and 0xFF) > 0)

        // 3. Batch Duplicate Selected Frames (indices 0 and 2 -> inserts 2 clones immediately after index 2)
        vm.duplicateSelectedFrames()
        assertEquals(5, vm.studioState.value.frames.size)
        assertEquals(setOf(3, 4), vm.studioState.value.selectedFrameIndicesForBatch)
        assertTrue((vm.studioState.value.frames[3].pixels[12 * 25 + 12].toInt() and 0xFF) > 0)
        assertTrue((vm.studioState.value.frames[4].pixels[18 * 25 + 12].toInt() and 0xFF) > 0)

        // 4. Batch Speed / Interval Override (set all selected frames or all frames to 165ms)
        vm.toggleFrameCheckedForBatch(0)
        vm.updateSelectedFramesDuration(165L)
        assertEquals(165L, vm.studioState.value.frames[0].durationMs)
        assertEquals(165L, vm.studioState.value.frames[3].durationMs)
        assertEquals(165L, vm.studioState.value.frames[4].durationMs)

        // 5. Batch Delete Selected Frames (removes indices 0, 3, 4 -> leaves 2 frames)
        vm.deleteSelectedFrames()
        assertEquals(2, vm.studioState.value.frames.size)
        assertFalse("Multi-select mode should exit after batch delete", vm.studioState.value.isFrameMultiSelectMode)

        // 6. Save and verify bidirectional AST SequencePrimitive & Console script write-back
        vm.updateSelectedFramesDuration(165L)
        vm.onSaveStudioChanges()
        val consoleScript = vm.consoleState.value.scriptInput
        assertTrue("Console script must contain sequence(interval: 165ms", consoleScript.contains("sequence(interval: 165ms"))
        assertEquals(2, vm.consoleState.value.dryRunPreviewFrames.size)
    }

    @Test
    fun `cursor precision reticle category auto step toggle and physical LED axis lock snapping work`() {
        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        val vm = com.example.ui.GlyphFlowViewModel(app)

        // 1. Switch to dedicated CURSOR dock category
        vm.setActiveDockTab(com.example.ui.StudioDockTab.CURSOR)
        assertEquals(com.example.ui.StudioDockTab.CURSOR, vm.studioState.value.activeDockTab)

        // 2. Toggle Auto-Step continuous draw mode
        assertFalse(vm.studioState.value.cursorAutoStepEnabled)
        vm.setCursorAutoStepEnabled(true)
        assertTrue(vm.studioState.value.cursorAutoStepEnabled)
        vm.setCursorAutoStepEnabled(null)
        assertFalse(vm.studioState.value.cursorAutoStepEnabled)

        // 3. Verify free-drag reticle snapping onto 489-LED circular boundary
        // Dead corner (0, 0) must snap to a valid physical LED inside (x-12)^2 + (y-12)^2 <= 156.25
        val (snappedTopLeftR, snappedTopLeftC) = com.example.ui.components.snapCoordinateToPhysicalAperture(
            rawRow = 0,
            rawCol = 0,
            axisLockEnabled = false
        )
        assertTrue(
            "Snapped coordinate ($snappedTopLeftC, $snappedTopLeftR) must be a physical LED",
            GlyphAperture.isPhysicalLed(snappedTopLeftC, snappedTopLeftR)
        )

        // Center (12, 12) stays (12, 12)
        val (centerR, centerC) = com.example.ui.components.snapCoordinateToPhysicalAperture(
            rawRow = 12,
            rawCol = 12,
            axisLockEnabled = false
        )
        assertEquals(12, centerR)
        assertEquals(12, centerC)

        // 4. Verify Axis Lock strictly clamps reticle along active horizontal row guide (Y = 6)
        val (lockedRowR, lockedRowC) = com.example.ui.components.snapCoordinateToPhysicalAperture(
            rawRow = 19,
            rawCol = 0, // outside chord on row 6 -> snaps to leftmost physical LED on row 6
            axisLockEnabled = true,
            activeRowGuide = 6,
            activeColGuide = null
        )
        assertEquals("Row must be strictly clamped to activeRowGuide=6", 6, lockedRowR)
        assertTrue(
            "Clamped coordinate ($lockedRowC, $lockedRowR) on row 6 must be a physical LED",
            GlyphAperture.isPhysicalLed(lockedRowC, lockedRowR)
        )

        // 5. Verify Axis Lock strictly clamps reticle along active vertical col guide (X = 15)
        val (lockedColR, lockedColC) = com.example.ui.components.snapCoordinateToPhysicalAperture(
            rawRow = 24,
            rawCol = 3,
            axisLockEnabled = true,
            activeRowGuide = null,
            activeColGuide = 15
        )
        assertEquals("Column must be strictly clamped to activeColGuide=15", 15, lockedColC)
        assertTrue(
            "Clamped coordinate ($lockedColC, $lockedColR) on col 15 must be a physical LED",
            GlyphAperture.isPhysicalLed(lockedColC, lockedColR)
        )
    }

    @Test
    fun `quick-clear dustbin zeroes active frame, registers undo, leaves other frames untouched`() {
        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        val vm = com.example.ui.GlyphFlowViewModel(app)

        // 1. Populate frame 0 with some dots
        vm.selectTimelineFrame(0)
        vm.setCanvasDotExplicit(12, 12, true)
        vm.setCanvasDotExplicit(10, 10, true)
        assertTrue("Center dot should be lit", (vm.studioState.value.frames[0].pixels[12 * 25 + 12].toInt() and 0xFF) > 0)
        assertTrue("Dot at (10,10) should be lit", (vm.studioState.value.frames[0].pixels[10 * 25 + 10].toInt() and 0xFF) > 0)

        // 2. Add frame 1 and set a dot on frame 1
        vm.addBlankFrame()
        vm.selectTimelineFrame(1)
        vm.setCanvasDotExplicit(14, 14, true)
        assertTrue("Dot at (14,14) on frame 1 should be lit", (vm.studioState.value.frames[1].pixels[14 * 25 + 14].toInt() and 0xFF) > 0)

        // 3. Clear current frame (frame 1)
        vm.clearCurrentFrame()
        assertFalse("Dot on frame 1 should now be zeroed", (vm.studioState.value.frames[1].pixels[14 * 25 + 14].toInt() and 0xFF) > 0)
        assertEquals(0, vm.studioState.value.frames[1].pixels.count { (it.toInt() and 0xFF) > 0 })

        // 4. Verify frame 0 was NOT touched
        vm.selectTimelineFrame(0)
        assertTrue("Frame 0 dot (12,12) must remain intact", (vm.studioState.value.frames[0].pixels[12 * 25 + 12].toInt() and 0xFF) > 0)
        assertTrue("Frame 0 dot (10,10) must remain intact", (vm.studioState.value.frames[0].pixels[10 * 25 + 10].toInt() and 0xFF) > 0)

        // 5. Undo on frame 1 restores the cleared dot
        vm.selectTimelineFrame(1)
        vm.undoStudioEdit()
        assertTrue("Undo must restore wiped frame 1 dot", (vm.studioState.value.frames[1].pixels[14 * 25 + 14].toInt() and 0xFF) > 0)
    }

    @Test
    fun `relative PWM brightness scaling adjusts illuminated LEDs proportionally, respects selection scope, and clamps to 0-255`() {
        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        val vm = com.example.ui.GlyphFlowViewModel(app)

        vm.selectTimelineFrame(0)
        // Dot A at (12, 12) with brightness 128 (50%)
        vm.setStudioBrushBrightness(128)
        vm.setCanvasDotExplicit(12, 12, true)

        // Dot B at (10, 10) with brightness 153 (60%)
        vm.setStudioBrushBrightness(153)
        vm.setCanvasDotExplicit(10, 10, true)

        val idxA = 12 * 25 + 12
        val idxB = 10 * 25 + 10
        val p0 = vm.studioState.value.frames[0].pixels
        assertEquals(128, p0[idxA].toInt() and 0xFF)
        assertEquals(153, p0[idxB].toInt() and 0xFF)

        // 1. Proportional offset scaling: +10% (+26 PWM) across active frame
        vm.adjustRelativePwm(10, selectionOnly = false)
        val p1 = vm.studioState.value.frames[0].pixels
        // 128 + round(255 * 0.1) = 128 + 26 = 154
        assertEquals(154, p1[idxA].toInt() and 0xFF)
        // 153 + 26 = 179
        assertEquals(179, p1[idxB].toInt() and 0xFF)

        // 2. Selection-scoped relative adjustment
        // Select region containing only Dot A (11,11 to 13,13)
        vm.setSelectionRegion(11, 11, 13, 13)
        vm.setLightingTargetSelectionOnly(true)
        assertTrue(vm.studioState.value.lightingTargetSelectionOnly)

        // Adjust +20% with selectionOnly = true
        vm.adjustRelativePwm(20, selectionOnly = true)
        val p2 = vm.studioState.value.frames[0].pixels
        // Dot A gets +51 PWM: 154 + 51 = 205
        assertEquals(205, p2[idxA].toInt() and 0xFF)
        // Dot B outside selection remains unchanged at 179
        assertEquals(179, p2[idxB].toInt() and 0xFF)

        // 3. Clamping test: apply +50% twice to ensure max 255 clamping without overflow
        vm.adjustRelativePwm(50, selectionOnly = false)
        vm.adjustRelativePwm(50, selectionOnly = false)
        val p3 = vm.studioState.value.frames[0].pixels
        assertEquals(255, p3[idxA].toInt() and 0xFF)
        assertEquals(255, p3[idxB].toInt() and 0xFF)

        // 4. Clamping test: apply -100% to ensure zero clamping
        vm.adjustRelativePwm(-100, selectionOnly = false)
        val p4 = vm.studioState.value.frames[0].pixels
        assertEquals(0, p4[idxA].toInt() and 0xFF)
        assertEquals(0, p4[idxB].toInt() and 0xFF)
    }

    @Test
    fun `studio dock tab lighting and single emoji picker workflow`() {
        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        val vm = com.example.ui.GlyphFlowViewModel(app)

        // 1. Switch to StudioDockTab.LIGHTING
        vm.setActiveDockTab(com.example.ui.StudioDockTab.LIGHTING)
        assertEquals(com.example.ui.StudioDockTab.LIGHTING, vm.studioState.value.activeDockTab)
        assertEquals("LIGHTING", com.example.ui.StudioDockTab.LIGHTING.label)

        // 2. Toggle lighting scope
        assertFalse(vm.studioState.value.lightingTargetSelectionOnly)
        vm.setLightingTargetSelectionOnly(true)
        assertTrue(vm.studioState.value.lightingTargetSelectionOnly)
        vm.setLightingTargetSelectionOnly(false)
        assertFalse(vm.studioState.value.lightingTargetSelectionOnly)

        // 3. Emoji selection & rasterization onto circular aperture
        vm.updateEmojiInput("⚡")
        assertEquals("⚡", vm.studioState.value.emojiInput)
        vm.rasterizeEmojiToStudioCanvas("⚡")
        val activePixels = vm.studioState.value.frames[0].pixels
        assertTrue("Rasterized emoji must light physical LEDs", activePixels.any { (it.toInt() and 0xFF) > 0 })
        // Corners must be zero
        assertEquals(0, activePixels[0].toInt() and 0xFF)
        assertEquals(0, activePixels[24].toInt() and 0xFF)
    }

    @Test
    fun `studio dock tab scale arbitrary scaling from 1 to 300 percent with steppers and aspect lock`() {
        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        val vm = com.example.ui.GlyphFlowViewModel(app)

        // Draw a 3x3 block in the center (11..13, 11..13)
        for (r in 11..13) {
            for (c in 11..13) {
                vm.setCanvasDotExplicit(r, c, true)
            }
        }
        val origPixels = vm.studioState.value.frames[0].pixels.clone()
        assertTrue((origPixels[12 * 25 + 12].toInt() and 0xFF) > 0)

        // 1. Switch to StudioDockTab.SCALE
        vm.setActiveDockTab(com.example.ui.StudioDockTab.SCALE)
        assertEquals(com.example.ui.StudioDockTab.SCALE, vm.studioState.value.activeDockTab)
        assertEquals("SCALE", com.example.ui.StudioDockTab.SCALE.label)

        // Bounding box should be initialized
        val srcBounds = vm.studioState.value.scaleSourceBounds
        assertNotNull(srcBounds)
        assertEquals(11, srcBounds!![0])
        assertEquals(11, srcBounds[1])
        assertEquals(13, srcBounds[2])
        assertEquals(13, srcBounds[3])

        // 2. Arbitrary scaling: set to 57%
        vm.setScalePercent(57)
        assertEquals(57, vm.studioState.value.scalePercent)
        assertNotNull(vm.studioState.value.stagingPreviewFrame)

        // 3. Stepper nudges: [-5%], [+1%]
        vm.stepScalePercent(-5)
        assertEquals(52, vm.studioState.value.scalePercent)

        vm.stepScalePercent(1)
        assertEquals(53, vm.studioState.value.scalePercent)

        // 4. Aspect ratio toggle
        assertTrue(vm.studioState.value.scaleAspectLocked)
        vm.toggleScaleAspectLock(false)
        assertFalse(vm.studioState.value.scaleAspectLocked)
        vm.toggleScaleAspectLock(true)
        assertTrue(vm.studioState.value.scaleAspectLocked)

        // 5. Fit to guides
        vm.addRowGuide(8)
        vm.addRowGuide(16)
        vm.addColGuide(8)
        vm.addColGuide(16)
        vm.fitScaleToGuides()
        val guideBounds = vm.studioState.value.scaleActiveBounds
        assertNotNull(guideBounds)
        assertEquals(8f, guideBounds!![0], 0.1f)
        assertEquals(8f, guideBounds[1], 0.1f)
        assertEquals(16f, guideBounds[2], 0.1f)
        assertEquals(16f, guideBounds[3], 0.1f)

        // 6. Test cancel: reverts staging preview and leaves original pixels intact
        vm.cancelScale()
        val currentPixelsAfterCancel = vm.studioState.value.frames[0].pixels
        assertEquals(origPixels[12 * 25 + 12], currentPixelsAfterCancel[12 * 25 + 12])

        // 7. Test commit: scales to 200%, commits to active frame and clears staging preview
        vm.initScaleSession()
        vm.setScalePercent(200)
        assertEquals(200, vm.studioState.value.scalePercent)
        assertNotNull(vm.studioState.value.stagingPreviewFrame)
        vm.commitScale()
        assertFalse(vm.studioState.value.frames[0].pixels.all { it.toInt() == 0 })
        // Staging buffer is cleared after commit
        assertEquals(null, vm.studioState.value.stagingPreviewFrame)

        // 8. Physical aperture mask: verify non-physical corner is 0
        assertEquals(0, vm.studioState.value.frames[0].pixels[0].toInt() and 0xFF)
        assertEquals(0, vm.studioState.value.frames[0].pixels[24].toInt() and 0xFF)
    }

    @Test
    fun `studio scale bounding box drag updates activeBounds and staging preview`() {
        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        val vm = com.example.ui.GlyphFlowViewModel(app)

        vm.setCanvasDotExplicit(12, 12, true)
        vm.setActiveDockTab(com.example.ui.StudioDockTab.SCALE)

        // Drag handle 4 (bottom-right) outward
        vm.updateScaleBoundingBoxDrag(
            handleIndex = 4,
            newMinR = 10f,
            newMinC = 10f,
            newMaxR = 15f,
            newMaxC = 15f
        )
        val activeBounds = vm.studioState.value.scaleActiveBounds
        assertNotNull(activeBounds)
        assertEquals(10f, activeBounds!![0], 0.01f)
        assertEquals(10f, activeBounds[1], 0.01f)
        assertEquals(15f, activeBounds[2], 0.01f)
        assertEquals(15f, activeBounds[3], 0.01f)
        assertNotNull(vm.studioState.value.stagingPreviewFrame)
    }

    @Test
    fun `rule thumbnail persistence is strictly scoped to target rule id without collisions`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        val repository = app.repository

        val rule1 = TriggerRule(name = "Battery Low", targetPackageName = "android.battery")
        val rule2 = TriggerRule(name = "Telegram Ping", targetPackageName = "org.telegram.messenger")

        val id1 = repository.saveRule(rule1)
        val id2 = repository.saveRule(rule2)
        try {
            val customThumb = ByteArray(625) { if (it == 312) 255.toByte() else 0.toByte() }
            repository.updateRuleThumbnail(id1, customThumb, 123456L)

            val updatedRules = repository.getAllRulesSnapshot()
            val loaded1 = updatedRules.first { it.id == id1 }
            val loaded2 = updatedRules.first { it.id == id2 }

            assertEquals(255, loaded1.thumbnailBytes[312].toInt() and 0xFF)
            assertEquals(123456L, loaded1.updatedAt)

            // Rule 2 must NOT have received Rule 1's thumbnail
            assertEquals(0, loaded2.thumbnailBytes.size)
            assertEquals(0L, loaded2.updatedAt)
        } finally {
            repository.deleteRule(id1)
            repository.deleteRule(id2)
        }
    }

    @Test
    fun `studio discard and exit performs complete board teardown and reinitialization`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        val vm = com.example.ui.GlyphFlowViewModel(app)

        val testRule = TriggerRule(id = 888L, name = "Test Discard Rule")
        vm.editRuleInStudio(testRule)
        assertEquals("888", vm.studioState.value.activeStudioRuleId)

        // Modify canvas with some pixels
        vm.setCanvasDotExplicit(12, 12, true)
        vm.setCanvasDotExplicit(12, 13, true)
        assertTrue(vm.studioState.value.frames[0].pixels[12 * 25 + 12].toInt() != 0)

        // Discard & Exit
        vm.discardStudioEditsAndExit()

        val state = vm.studioState.value
        assertEquals(null, state.activeStudioRuleId)
        assertEquals(null, vm.activeStudioRuleId.value)
        assertEquals(1, state.frames.size)
        assertTrue("Working canvas must be blank after discard", state.frames[0].pixels.all { it == 0.toByte() })
        assertFalse(state.isLiveHardwareMirror)
        assertFalse(state.isPreviewPlaying)
        assertEquals(null, state.stagingPreviewFrame)
        assertEquals(null, state.selectionStart)
        assertEquals(null, state.selectionEnd)
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
        assertEquals(com.example.ui.GlyphScreenTab.DASHBOARD, vm.currentTab.value)
    }

    @Test
    fun `bottom navigation entry to studio without edit target opens clean blank canvas`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        val vm = com.example.ui.GlyphFlowViewModel(app)

        // Reset studio session first
        vm.resetStudioSession()
        vm.selectTab(com.example.ui.GlyphScreenTab.DASHBOARD)

        // Now select studio tab via bottom nav
        vm.selectTab(com.example.ui.GlyphScreenTab.STUDIO)

        val state = vm.studioState.value
        assertEquals(null, state.activeStudioRuleId)
        assertTrue("Studio frame should be blank rather than bound to first rule", state.frames[0].pixels.all { it == 0.toByte() })
    }

    @Test
    fun `strict id generation AST surgical merging and system sequence engine work`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<GlyphFlowApplication>()
        val vm = com.example.ui.GlyphFlowViewModel(app)

        // 1. Strict ID Generation on "Create Rule"
        vm.startNewRuleDraft()
        assertEquals(null, vm.activeStudioRuleId.value)
        assertEquals("", vm.consoleState.value.scriptInput)
        assertEquals(0L, vm.draftRule.value.id)

        // 2. System State Cache & Sequence Engine
        com.example.engine.GlobalSystemStateCache.setSystemVariable("bluetooth", "connected")
        com.example.engine.GlobalSystemStateCache.setSystemVariable("power", "charging")
        val stateMap = com.example.engine.GlobalSystemStateCache.getAll()
        assertEquals("connected", stateMap["bluetooth"])
        assertEquals("charging", stateMap["power"])

        // Sequence Event Buffer
        com.example.engine.GlobalSystemStateCache.recordEvent("key.volume_up")
        com.example.engine.GlobalSystemStateCache.recordEvent("key.volume_down")
        assertTrue(
            com.example.engine.GlobalSystemStateCache.matchesSequence(
                listOf("key.volume_up", "key.volume_down"),
                timeoutMs = 2000L
            )
        )

        // 3. AST Surgical Merging (Non-destructive update preserving WHEN, EXTRACT, and untouched layers)
        val scriptSource = """
            RULE "Custom Navigation Alert"
            WHEN system.bluetooth == "connected"
            EXTRACT {
              var eta = body.regex("([0-9]+m)") DEFAULT "10m"
            }
            DISPLAY {
              LAYER indicator {
                icon(name: "bell", pulse: true)
              }
              LAYER bottom_text {
                text(content: eta, y: 18)
              }
            }
            BEHAVIOR {
              priority: 8
              until: duration(5s)
            }
        """.trimIndent()

        val decompiled = com.example.dsl.GlyphScriptDecompiler.decompile(
            scriptSource = scriptSource,
            context = app.applicationContext
        )

        val updatedIndicatorLayer = decompiled.visualLayers.first().copy(brightness = 200, isCanvasEdited = true)
        val mutatedProgram = com.example.dsl.DisplayLayer(
            name = updatedIndicatorLayer.name,
            primitives = listOf(com.example.dsl.LayerPrimitive.IconPrimitive(nameExpr = "bell", pulse = true, brightness = 200))
        )
        val mergedRule = com.example.dsl.GlyphScriptDecompiler.mutateRuleAstWithLayerPrimitive(
            baseRule = decompiled,
            targetLayerName = "indicator",
            updatedPrimitive = mutatedProgram.primitives.first(),
            updatedLayers = decompiled.visualLayers
        )

        val recompiledScript = com.example.dsl.GlyphScriptDecompiler.decompile(
            updatedRule = mergedRule,
            studioRule = decompiled
        )

        assertTrue("Recompiled script must preserve WHEN system.bluetooth == \"connected\"", recompiledScript.contains("WHEN system.bluetooth == \"connected\""))
        assertTrue("Recompiled script must preserve EXTRACT block and var eta", recompiledScript.contains("var eta = body.regex"))
        assertTrue("Recompiled script must preserve bottom_text layer", recompiledScript.contains("LAYER bottom_text"))
    }
}






