package com.example.dsl

import com.example.domain.model.GlyphFrame
import com.example.domain.model.MATRIX_CELL_COUNT
import com.example.domain.model.MATRIX_SIZE
import com.example.engine.GlyphPatternGenerator
import com.example.hardware.GlyphHardwareController
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Multi-Layer 25x25 Circular Matrix Compositor for GlyphScript v2.
 * Supports simultaneous rendering of:
 * - `ring(progress, style)`
 * - `arrow(direction, size)`
 * - `emoji(glyph, scale)`
 * - `text(content, size: "3x5"|"5x7", align, y)`
 * - `icon(name)`
 * - `shader(type, fps)`
 * Layers are composited via `max(layerA[i], layerB[i])` and strictly clamped to
 * `(col - 12)^2 + (row - 12)^2 <= 156.25`.
 */
object GlyphFrameCompositor {

    private const val CIRCLE_RADIUS_SQ = 156.25f

    private val HIDDEN_UNICODE_REGEX = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u206F\\uFEFF]")
    private val NEWLINE_REGEX = Regex("[\\r\\n]+")
    private val MINUTES_UNIT_REGEX = Regex("(?i)\\s*(?:minutes|mins|min)\\b")
    private val KILOMETERS_UNIT_REGEX = Regex("(?i)\\s*(?:kilometers|kilometres|kms|km)\\b")
    private val METERS_UNIT_REGEX = Regex("(?i)\\s*(?:meters|metres|mtrs|mtr)\\b")

    /**
     * Strips trailing/embedded newlines, bold markdown (`**`), and hidden Unicode directional/zero-width
     * markers before running regex extraction on notification payloads.
     */
    fun sanitizeTextForRegex(raw: String): String {
        return raw
            .replace("**", "")
            .replace(HIDDEN_UNICODE_REGEX, "")
            .replace(NEWLINE_REGEX, " ")
            .trim()
    }

    /**
     * Normalizes units in [rawText] and appends optional [suffix] if [rawText] does not already include a unit:
     * - "mins", "minutes", "min" -> "m"
     * - "kilometers", "km" -> "k"
     * - "meters" -> "m"
     */
    fun formatCompactUnitText(rawText: String, suffix: String = ""): String {
        val cleaned = sanitizeTextForRegex(rawText)
        if (cleaned.isEmpty()) return ""

        var normalized = cleaned
            .replace(MINUTES_UNIT_REGEX, "m")
            .replace(KILOMETERS_UNIT_REGEX, "k")
            .replace(METERS_UNIT_REGEX, "m")
            .replace(Regex("(?<=\\d)\\s+(?=[a-zA-Z])"), "")
            .trim()

        val normSuffix = suffix.trim()
            .replace(MINUTES_UNIT_REGEX, "m")
            .replace(KILOMETERS_UNIT_REGEX, "k")
            .replace(METERS_UNIT_REGEX, "m")
            .trim()

        if (normSuffix.isNotEmpty() && normalized.isNotEmpty()) {
            val alreadyHasUnit = normalized.last().isLetter() ||
                normalized.endsWith(normSuffix, ignoreCase = true)
            if (!alreadyHasUnit) {
                normalized += normSuffix
            }
        }
        return normalized
    }

    /**
     * Evaluates `EXTRACT` declarations against an incoming notification/signal payload.
     * Strips trailing newlines, bold markdown (`**`), and hidden unicode markers before running `.regex(...)`,
     * and logs an `EXTRACT_FALLBACK` warning in `AuditDiagnosticsBuffer` when a regex falls back to `DEFAULT`.
     */
    fun evaluateExtractions(
        declarations: List<ExtractDeclaration>,
        title: String,
        body: String,
        subText: String,
        progressPercent: Int?,
        logFallbackWarnings: Boolean = true,
        extraPayload: Map<String, Any?> = emptyMap()
    ): Map<String, String> {
        val cleanTitle = sanitizeTextForRegex(title)
        val cleanBody = sanitizeTextForRegex(body)
        val cleanSubText = sanitizeTextForRegex(subText)
        val combinedText = listOf(cleanTitle, cleanBody, cleanSubText).filter { it.isNotBlank() }.joinToString(" ")
        val pctStr = (progressPercent ?: 0).coerceIn(0, 100).toString()

        val env = LinkedHashMap<String, String>()
        val sysCache = com.example.engine.GlobalSystemStateCache.getAll()
        for ((k, v) in sysCache) {
            env[k] = v
            if (k.startsWith("system.")) {
                env[k.removePrefix("system.")] = v
            }
        }
        for ((k, v) in extraPayload) {
            if (v != null) {
                val strVal = v.toString()
                env[k] = strVal
                if (k.startsWith("system.")) {
                    env[k.removePrefix("system.")] = strVal
                }
            }
        }

        env["title"] = cleanTitle
        env["notification.title"] = cleanTitle
        env["body"] = cleanBody
        env["text"] = cleanBody
        env["content"] = cleanBody
        env["notification.body"] = cleanBody
        env["notification.text"] = cleanBody
        env["subText"] = cleanSubText
        env["subtext"] = cleanSubText
        env["sub_text"] = cleanSubText
        env["notification.subtext"] = cleanSubText
        env["notification.sub_text"] = cleanSubText
        env["progress_percent"] = pctStr
        env["progress"] = pctStr
        env["notification.progress"] = pctStr

        if (declarations.isEmpty() && !logFallbackWarnings && (cleanBody.isNotBlank() || cleanTitle.isNotBlank())) {
            env["__dry_run_text_override__"] = cleanBody.ifBlank { cleanTitle }
        }

        for (decl in declarations) {
            val normalizedField = decl.sourceField.substringAfterLast('.').lowercase()
            val primarySourceText = when {
                env.containsKey(decl.sourceField) -> env[decl.sourceField].orEmpty()
                env.containsKey(normalizedField) -> env[normalizedField].orEmpty()
                normalizedField == "title" -> cleanTitle
                normalizedField in setOf("body", "content", "text") -> cleanBody
                normalizedField in setOf("subtext", "sub_text") -> cleanSubText
                normalizedField in setOf("progress_percent", "progress") -> {
                    if (progressPercent != null) {
                        progressPercent.coerceIn(0, 100).toString()
                    } else {
                        decl.defaultValue.ifBlank { "0" }
                    }
                }
                else -> sanitizeTextForRegex(env[decl.sourceField] ?: "")
            }
            // In dry-run mode (!logFallbackWarnings), if the user typed into Title or Body and the primary field is empty,
            // also check the other non-empty text field so testing is seamless.
            val sourceText = if (!logFallbackWarnings && primarySourceText.isBlank() && normalizedField in setOf("title", "body", "content", "text")) {
                cleanBody.ifBlank { cleanTitle }
            } else {
                primarySourceText
            }

            val resolved = if (decl.mapRules.isNotEmpty()) {
                var matchedVal: String? = null
                val candidateTexts = if (!logFallbackWarnings && combinedText.isNotBlank()) {
                    listOf(sourceText, combinedText).distinct()
                } else {
                    listOf(sourceText)
                }
                for (rule in decl.mapRules) {
                    when (rule.conditionType) {
                        MapConditionType.CONTAINS -> {
                            if (candidateTexts.any { txt -> rule.patterns.any { pat -> txt.contains(pat, ignoreCase = true) } }) {
                                matchedVal = rule.targetValue
                                break
                            }
                        }
                        MapConditionType.REGEX -> {
                            if (candidateTexts.any { txt ->
                                    rule.patterns.any { pat ->
                                        runCatching { Regex(pat, RegexOption.IGNORE_CASE).containsMatchIn(txt) }.getOrDefault(false)
                                    }
                                }
                            ) {
                                matchedVal = rule.targetValue
                                break
                            }
                        }
                        MapConditionType.EQUALS -> {
                            if (candidateTexts.any { txt -> rule.patterns.any { pat -> txt.equals(pat, ignoreCase = true) } }) {
                                matchedVal = rule.targetValue
                                break
                            }
                        }
                        MapConditionType.DEFAULT -> {
                            if (matchedVal == null) {
                                matchedVal = rule.targetValue
                            }
                        }
                    }
                }
                matchedVal ?: decl.defaultValue
            } else if (decl.regexPattern != null) {
                val matched = runCatching {
                    val regex = Regex(decl.regexPattern, RegexOption.IGNORE_CASE)
                    val matchResult = regex.find(sourceText)
                        ?: if (!logFallbackWarnings && combinedText.isNotBlank()) regex.find(combinedText) else null
                    when {
                        matchResult == null -> null
                        decl.groupIndex > 0 && decl.groupIndex < matchResult.groupValues.size &&
                            matchResult.groupValues[decl.groupIndex].isNotBlank() -> {
                            matchResult.groupValues[decl.groupIndex]
                        }
                        matchResult.groupValues.size > 1 && matchResult.groupValues[1].isNotBlank() -> {
                            val full = matchResult.groupValues[0]
                            val g1 = matchResult.groupValues[1]
                            if (full.length > g1.length && full.endsWith(g1)) full else g1
                        }
                        else -> matchResult.value
                    }
                }.getOrNull()
                if (!matched.isNullOrBlank()) {
                    matched.trim()
                } else if (!logFallbackWarnings && sourceText.isNotBlank() && !decl.regexPattern.contains("|")) {
                    // In interactive Console Dry Run mode, if the user typed direct text/numbers that didn't include
                    // unit suffixes required by a strict regex (e.g. typing "45" for `([0-9]+)\s*min`), extract digits or use the typed text directly
                    val expectsDigits = decl.regexPattern.contains("0-9") || decl.regexPattern.contains("\\d")
                    val digitMatch = Regex("[0-9]+(?:\\.[0-9]+)?\\s*[a-zA-Z%]*").find(sourceText)?.value?.trim()
                    if (expectsDigits && !digitMatch.isNullOrBlank()) {
                        digitMatch
                    } else if (!expectsDigits) {
                        sourceText.trim()
                    } else {
                        decl.defaultValue
                    }
                } else {
                    if (logFallbackWarnings) {
                        val sourceLabel = when (normalizedField) {
                            "title" -> "title"
                            "subtext", "sub_text" -> "subText"
                            else -> "body"
                        }
                        val warning = "EXTRACT_FALLBACK: Variable '\$${decl.varName}' failed regex on $sourceLabel text, using fallback value."
                        com.example.engine.AuditDiagnosticsBuffer.recordExtractFallbackWarning(warning)
                    }
                    decl.defaultValue
                }
            } else {
                if (sourceText.isNotBlank() && (normalizedField !in setOf("progress_percent", "progress") || progressPercent != null)) {
                    sourceText.trim()
                } else {
                    decl.defaultValue.ifBlank {
                        if (normalizedField in setOf("progress_percent", "progress")) "0" else ""
                    }
                }
            }
            env[decl.varName] = resolved
        }
        return env
    }

    val BOUNCE_Y_OFFSETS = intArrayOf(0, -1, 0, 1)
    val PULSE_BRIGHTNESS_STEPS = intArrayOf(160, 210, 255, 200)

    /**
     * Generates the complete multi-frame animation sequence (`List<ByteArray>`) for [program]:
     * - `bounce: true` on `emoji(...)`, `icon(...)`, or `arrow(...)`: 4 frames shifting vertical `y` by `[0, -1, 0, 1]`
     *   while preserving bounds within the 25x25 circular mask.
     * - `pulse: true`: 4 frames modulating layer brightness `[160, 210, 255, 200]`.
     * - `marquee: true` on `text(...)` (or if formatted text length > 4 characters): horizontal sliding window
     *   of frames shifting text from right to left across row 17 (within the 16-dot circular aperture).
     * - `ring(..., animate: true)` / `border(..., animate: true)`: frames rotating the perimeter arc continuously.
     * Returns a 1-element `List<ByteArray>` when no layer has active animation parameters.
     */
    fun filterVisibleLayers(displayLayers: List<DisplayLayer>, variables: Map<String, String>): List<DisplayLayer> {
        val visible = mutableListOf<DisplayLayer>()
        val evaluatedGroups = mutableSetOf<String>()

        for (layer in displayLayers) {
            val group = layer.groupName
            if (group != null) {
                if (evaluatedGroups.contains(group)) continue
                val showIfOk = layer.showIfCondition?.evaluate(variables) ?: true
                if (showIfOk) {
                    evaluatedGroups.add(group)
                    visible.add(layer)
                }
            } else {
                val showIfOk = layer.showIfCondition?.evaluate(variables) ?: true
                if (showIfOk) {
                    visible.add(layer)
                }
            }
        }
        return visible
    }

    fun computeMacroFrameCount(macro: LayerAnimationMacro): Int {
        return when (macro) {
            is LayerAnimationMacro.SlideMacro -> {
                val dist = max(kotlin.math.abs(macro.toX - macro.fromX), kotlin.math.abs(macro.toY - macro.fromY)).coerceIn(4, 16)
                val pauseFrames = if (macro.pauseMs > 0 && macro.speedMs > 0) {
                    (macro.pauseMs / macro.speedMs).toInt().coerceIn(1, 6)
                } else 0
                dist + 1 + pauseFrames
            }
            is LayerAnimationMacro.BounceMacro -> {
                val span = (macro.maxOffset - macro.minOffset).coerceAtLeast(2)
                (span * 2).coerceIn(4, 16)
            }
            is LayerAnimationMacro.SpinMacro -> {
                val step = kotlin.math.abs(macro.stepDegrees).coerceIn(15, 180)
                (360 / step).coerceIn(4, 24)
            }
            is LayerAnimationMacro.PulseMacro -> {
                if (macro.isBreathe || macro.periodMs >= 1600L) 12 else 8
            }
        }
    }

    fun applyAnimationMacro(
        baseBuffer: IntArray,
        macro: LayerAnimationMacro,
        tick: Int,
        totalFrames: Int
    ): IntArray {
        if (totalFrames <= 1 && tick == 0) return baseBuffer
        val out = IntArray(MATRIX_CELL_COUNT)

        when (macro) {
            is LayerAnimationMacro.SlideMacro -> {
                var sumX = 0
                var sumY = 0
                var count = 0
                for (r in 0 until MATRIX_SIZE) {
                    for (c in 0 until MATRIX_SIZE) {
                        val v = baseBuffer[r * MATRIX_SIZE + c]
                        if (v > 0) {
                            sumX += c
                            sumY += r
                            count++
                        }
                    }
                }
                val baseCx = if (count > 0) (sumX.toFloat() / count).roundToInt() else 12
                val baseCy = if (count > 0) (sumY.toFloat() / count).roundToInt() else 12

                val steps = max(kotlin.math.abs(macro.toX - macro.fromX), kotlin.math.abs(macro.toY - macro.fromY)).coerceIn(4, 16)
                val pauseFrames = if (macro.pauseMs > 0 && macro.speedMs > 0) {
                    (macro.pauseMs / macro.speedMs).toInt().coerceIn(1, 6)
                } else 0
                val cycleTotal = (steps + 1 + pauseFrames).coerceAtLeast(2)
                val stepIdx = if (macro.loop) (tick.coerceAtLeast(0) % cycleTotal) else tick.coerceIn(0, cycleTotal - 1)
                val clampedStep = stepIdx.coerceAtMost(steps)
                val progress = clampedStep.toFloat() / steps.toFloat()

                val targetCx = (macro.fromX + (macro.toX - macro.fromX) * progress).roundToInt()
                val targetCy = (macro.fromY + (macro.toY - macro.fromY) * progress).roundToInt()
                val dx = targetCx - baseCx
                val dy = targetCy - baseCy

                for (r in 0 until MATRIX_SIZE) {
                    for (c in 0 until MATRIX_SIZE) {
                        val v = baseBuffer[r * MATRIX_SIZE + c]
                        if (v <= 0) continue
                        val nx = c + dx
                        val ny = r + dy
                        if (nx in 0 until MATRIX_SIZE && ny in 0 until MATRIX_SIZE &&
                            GlyphHardwareController.isCellInsideCircle(ny, nx)
                        ) {
                            val dstIdx = ny * MATRIX_SIZE + nx
                            out[dstIdx] = max(out[dstIdx], v)
                        }
                    }
                }
            }

            is LayerAnimationMacro.BounceMacro -> {
                val minO = kotlin.math.min(macro.minOffset, macro.maxOffset)
                val maxO = kotlin.math.max(macro.minOffset, macro.maxOffset)
                val offsets = mutableListOf<Int>()
                for (v in 0 downTo minO) offsets.add(v)
                for (v in (minO + 1)..maxO) offsets.add(v)
                for (v in (maxO - 1) downTo 1) offsets.add(v)
                val offset = if (offsets.isNotEmpty()) offsets[tick.coerceAtLeast(0) % offsets.size] else 0
                val dx = if (macro.axis.equals("x", ignoreCase = true)) offset else 0
                val dy = if (macro.axis.equals("x", ignoreCase = true)) 0 else offset

                for (r in 0 until MATRIX_SIZE) {
                    for (c in 0 until MATRIX_SIZE) {
                        val v = baseBuffer[r * MATRIX_SIZE + c]
                        if (v <= 0) continue
                        val nx = c + dx
                        val ny = r + dy
                        if (nx in 0 until MATRIX_SIZE && ny in 0 until MATRIX_SIZE &&
                            GlyphHardwareController.isCellInsideCircle(ny, nx)
                        ) {
                            val dstIdx = ny * MATRIX_SIZE + nx
                            out[dstIdx] = max(out[dstIdx], v)
                        }
                    }
                }
            }

            is LayerAnimationMacro.SpinMacro -> {
                val angleDeg = ((tick.coerceAtLeast(0) * macro.stepDegrees) % 360).toDouble()
                val theta = Math.toRadians(angleDeg)
                val cosT = cos(theta)
                val sinT = sin(theta)
                val cx = 12.0
                val cy = 12.0

                for (r in 0 until MATRIX_SIZE) {
                    for (c in 0 until MATRIX_SIZE) {
                        val v = baseBuffer[r * MATRIX_SIZE + c]
                        if (v <= 0) continue
                        val rx = (cx + (c - cx) * cosT - (r - cy) * sinT).roundToInt()
                        val ry = (cy + (c - cx) * sinT + (r - cy) * cosT).roundToInt()
                        if (rx in 0 until MATRIX_SIZE && ry in 0 until MATRIX_SIZE &&
                            GlyphHardwareController.isCellInsideCircle(ry, rx)
                        ) {
                            val dstIdx = ry * MATRIX_SIZE + rx
                            out[dstIdx] = max(out[dstIdx], v)
                        }
                    }
                }
            }

            is LayerAnimationMacro.PulseMacro -> {
                val cycle = totalFrames.coerceAtLeast(4)
                val phase = (tick.coerceAtLeast(0).toDouble() / cycle.toDouble()) * 2.0 * PI
                val norm = (1.0 + cos(phase)) / 2.0
                val targetPeak = (macro.minBrightness + (macro.maxBrightness - macro.minBrightness) * norm)
                    .roundToInt()
                    .coerceIn(0, 255)
                val scale = targetPeak / 255f
                for (i in 0 until MATRIX_CELL_COUNT) {
                    val v = baseBuffer[i]
                    if (v > 0 && com.example.hardware.GlyphAperture.PHYSICAL_LED_MASK[i]) {
                        out[i] = (v * scale).roundToInt().coerceIn(0, 255)
                    }
                }
            }
        }
        return out
    }

    fun applyAnimationMacroToBytes(
        basePixels: ByteArray,
        macro: LayerAnimationMacro,
        tick: Int,
        totalFrames: Int
    ): ByteArray {
        val baseInts = IntArray(MATRIX_CELL_COUNT) { idx ->
            if (idx < basePixels.size) (basePixels[idx].toInt() and 0xFF) else 0
        }
        val transformed = applyAnimationMacro(baseInts, macro, tick, totalFrames)
        val out = ByteArray(MATRIX_CELL_COUNT)
        for (i in 0 until MATRIX_CELL_COUNT) {
            out[i] = if (com.example.hardware.GlyphAperture.PHYSICAL_LED_MASK[i]) {
                transformed[i].coerceIn(0, 255).toByte()
            } else 0
        }
        return out
    }

    fun compositeFrames(
        program: GlyphScriptProgram,
        variables: Map<String, String>,
        frameCountOverride: Int? = null
    ): List<ByteArray> {
        val visibleLayers = filterVisibleLayers(program.displayLayers, variables)
        val allPrimitives = visibleLayers.flatMap { it.primitives }
        val hasAnimatedShader = allPrimitives.any { it is LayerPrimitive.ShaderPrimitive }
        val hasRotatingRing = allPrimitives.filterIsInstance<LayerPrimitive.RingPrimitive>().any { it.animate }

        val maxMacroFrames = visibleLayers.mapNotNull { it.animationMacro }
            .maxOfOrNull { computeMacroFrameCount(it) } ?: 1

        var maxMarqueeTextLength = 0
        val hasMarqueeText = allPrimitives.filterIsInstance<LayerPrimitive.TextPrimitive>().any { textPrim ->
            val resolved = formatCompactUnitText(
                resolveExpression(textPrim.contentExpr, variables),
                textPrim.suffix
            )
            val isMarquee = textPrim.marquee || textPrim.animate || resolved.length > 4
            if (isMarquee && resolved.length > maxMarqueeTextLength) {
                maxMarqueeTextLength = resolved.length
            }
            isMarquee
        }

        val hasBounceOrPulse = allPrimitives.any { prim ->
            when (prim) {
                is LayerPrimitive.RingPrimitive -> prim.pulse
                is LayerPrimitive.ArrowPrimitive -> prim.pulse || prim.bounce
                is LayerPrimitive.EmojiPrimitive -> prim.bounce || prim.pulse
                is LayerPrimitive.IconPrimitive -> prim.pulse || prim.bounce
                is LayerPrimitive.TextPrimitive -> prim.pulse
                is LayerPrimitive.ShaderPrimitive,
                is LayerPrimitive.BitmapPrimitive,
                is LayerPrimitive.SequencePrimitive,
                is LayerPrimitive.PlotPrimitive -> false
            }
        }

        val hasTextLayer = allPrimitives.any { it is LayerPrimitive.TextPrimitive }
        val hasUpperGraphicLayer = allPrimitives.any {
            it is LayerPrimitive.ArrowPrimitive ||
                it is LayerPrimitive.EmojiPrimitive ||
                it is LayerPrimitive.IconPrimitive
        }
        val splitVerticalLayout = hasTextLayer && hasUpperGraphicLayer

        val sequencePrimitives = allPrimitives.filterIsInstance<LayerPrimitive.SequencePrimitive>()
        val hasSequence = sequencePrimitives.isNotEmpty()
        val maxSequenceFrames = sequencePrimitives.maxOfOrNull { seq ->
            val frameDurationMs = seq.intervalMs.coerceAtLeast(50L)
            val pauseFrames = if (seq.pauseMs > 0 && frameDurationMs > 0) (seq.pauseMs / frameDurationMs).toInt().coerceIn(0, 8) else 0
            seq.frames.size + pauseFrames
        } ?: 1

        val totalFrames = frameCountOverride ?: when {
            maxMacroFrames > 1 -> max(maxMacroFrames, if (hasSequence) maxSequenceFrames else 1)
            hasAnimatedShader -> 12
            hasMarqueeText -> {
                val rawSteps = (maxMarqueeTextLength.coerceAtLeast(4) + 2) * 2
                ((rawSteps + 3) / 4 * 4).coerceIn(8, 24)
            }
            hasSequence -> maxSequenceFrames.coerceAtLeast(2)
            hasRotatingRing -> 8
            hasBounceOrPulse || program.hasAnimatedPrimitives -> 4
            else -> 1
        }

        val frames = ArrayList<ByteArray>(totalFrames)

        for (tick in 0 until totalFrames) {
            val blendedBuffer = IntArray(MATRIX_CELL_COUNT)
            for (layer in visibleLayers) {
                val layerBuffer = renderLayer(
                    layer = layer,
                    variables = variables,
                    tick = tick,
                    totalFrames = totalFrames,
                    splitVerticalLayout = splitVerticalLayout
                )
                for (i in 0 until MATRIX_CELL_COUNT) {
                    blendedBuffer[i] = max(blendedBuffer[i], layerBuffer[i])
                }
            }

            val finalBytes = ByteArray(MATRIX_CELL_COUNT)
            for (idx in 0 until MATRIX_CELL_COUNT) {
                if (com.example.hardware.GlyphAperture.PHYSICAL_LED_MASK[idx]) {
                    finalBytes[idx] = blendedBuffer[idx].coerceIn(0, 255).toByte()
                } else {
                    finalBytes[idx] = 0
                }
            }
            frames.add(finalBytes)
        }

        return frames.ifEmpty { listOf(ByteArray(MATRIX_CELL_COUNT)) }
    }

    /**
     * Composites all layers in [program] into a `List<GlyphFrame>`
     * backed by [compositeFrames].
     */
    fun compositeProgramFrames(
        program: GlyphScriptProgram,
        variables: Map<String, String>,
        frameCountOverride: Int? = null
    ): List<GlyphFrame> {
        val byteFrames = compositeFrames(
            program = program,
            variables = variables,
            frameCountOverride = frameCountOverride
        )
        val visibleLayers = filterVisibleLayers(program.displayLayers, variables)
        val macroInterval = visibleLayers.mapNotNull { it.animationMacro?.frameIntervalMs }.minOrNull()
        val seqInterval = visibleLayers.flatMap { it.primitives }
            .filterIsInstance<LayerPrimitive.SequencePrimitive>()
            .map { it.intervalMs }
            .minOrNull()
        val frameDurationMs = (macroInterval ?: seqInterval ?: 100L).coerceIn(33L, 2000L)
        val contentHash = program.sourceText.hashCode() xor variables.hashCode()
        return byteFrames.mapIndexed { tick, bytes ->
            GlyphFrame(
                id = "dsl_${contentHash}_${tick}_${bytes.contentHashCode()}",
                durationMs = frameDurationMs,
                pixels = bytes
            )
        }.ifEmpty { listOf(GlyphFrame()) }
    }

    /**
     * Universal Rasterization Pass:
     * Renders an individual [DisplayLayer] across its active keyframe duration into discrete,
     * explicit 25x25 [GlyphFrame]s so Matrix Studio can display and edit every frame dot-by-dot
     * on the timeline without locking graphics inside black-box procedural calls.
     */
    fun renderSingleLayerFrames(
        layer: DisplayLayer,
        variables: Map<String, String>,
        splitVerticalLayout: Boolean = false
    ): List<GlyphFrame> {
        val activeMacro = layer.animationMacro
        val seqPrim = layer.primitives.filterIsInstance<LayerPrimitive.SequencePrimitive>().firstOrNull()
        if (seqPrim != null && seqPrim.frames.isNotEmpty()) {
            val baseSeqFrames = seqPrim.frames.mapIndexed { idx, sFrame ->
                val ints = renderBitmapPrimitive(
                    asciiArt = sFrame.asciiArt,
                    align = seqPrim.align,
                    peakBrightness = 255
                )
                val bytes = ByteArray(MATRIX_CELL_COUNT)
                for (i in 0 until MATRIX_CELL_COUNT) {
                    bytes[i] = if (com.example.hardware.GlyphAperture.PHYSICAL_LED_MASK[i]) {
                        ints[i].coerceIn(0, 255).toByte()
                    } else 0
                }
                GlyphFrame(
                    id = "f_${layer.name}_$idx",
                    durationMs = seqPrim.intervalMs.coerceIn(33L, 2000L),
                    pixels = bytes
                )
            }
            if (activeMacro == null) {
                return baseSeqFrames
            }
            val totalSteps = max(baseSeqFrames.size, computeMacroFrameCount(activeMacro)).coerceIn(2, 32)
            val dur = activeMacro.frameIntervalMs.coerceIn(33L, 2000L)
            return (0 until totalSteps).map { step ->
                val srcPixels = baseSeqFrames[step % baseSeqFrames.size].pixels
                val transformed = applyAnimationMacroToBytes(srcPixels, activeMacro, step, totalSteps)
                GlyphFrame(
                    id = "f_${layer.name}_$step",
                    durationMs = dur,
                    pixels = transformed
                )
            }
        }

        val bmpPrim = layer.primitives.filterIsInstance<LayerPrimitive.BitmapPrimitive>().firstOrNull()
        if (bmpPrim != null) {
            val ints = renderBitmapPrimitive(
                asciiArt = bmpPrim.asciiArt,
                align = bmpPrim.align,
                offsetX = bmpPrim.x,
                offsetY = bmpPrim.y,
                peakBrightness = bmpPrim.brightness
            )
            val baseBytes = ByteArray(MATRIX_CELL_COUNT)
            for (i in 0 until MATRIX_CELL_COUNT) {
                baseBytes[i] = if (com.example.hardware.GlyphAperture.PHYSICAL_LED_MASK[i]) {
                    ints[i].coerceIn(0, 255).toByte()
                } else 0
            }
            val dur = (activeMacro?.frameIntervalMs ?: 110L).coerceIn(33L, 2000L)
            if (activeMacro == null) {
                return listOf(
                    GlyphFrame(
                        id = "f_${layer.name}_0",
                        durationMs = dur,
                        pixels = baseBytes
                    )
                )
            }
            val totalSteps = computeMacroFrameCount(activeMacro).coerceIn(2, 32)
            return (0 until totalSteps).map { step ->
                val transformed = applyAnimationMacroToBytes(baseBytes, activeMacro, step, totalSteps)
                GlyphFrame(
                    id = "f_${layer.name}_$step",
                    durationMs = dur,
                    pixels = transformed
                )
            }
        }

        val hasShader = layer.primitives.any { it is LayerPrimitive.ShaderPrimitive }
        val hasRotatingRing = layer.primitives.filterIsInstance<LayerPrimitive.RingPrimitive>().any { it.animate }
        val hasBounceOrPulse = layer.primitives.any { prim ->
            when (prim) {
                is LayerPrimitive.ArrowPrimitive -> prim.bounce || prim.pulse
                is LayerPrimitive.IconPrimitive -> prim.bounce || prim.pulse
                is LayerPrimitive.EmojiPrimitive -> prim.bounce || prim.pulse
                is LayerPrimitive.RingPrimitive -> prim.pulse
                is LayerPrimitive.TextPrimitive -> prim.pulse
                else -> false
            }
        }

        val totalSteps = when {
            activeMacro != null -> computeMacroFrameCount(activeMacro).coerceIn(2, 32)
            hasShader || hasRotatingRing -> 8
            hasBounceOrPulse -> 4
            else -> 1
        }
        val dur = (activeMacro?.frameIntervalMs ?: 110L).coerceIn(33L, 2000L)

        return (0 until totalSteps).map { step ->
            val ints = renderLayer(
                layer = layer,
                variables = variables,
                tick = step,
                totalFrames = totalSteps,
                splitVerticalLayout = splitVerticalLayout
            )
            val bytes = ByteArray(MATRIX_CELL_COUNT)
            for (i in 0 until MATRIX_CELL_COUNT) {
                bytes[i] = if (com.example.hardware.GlyphAperture.PHYSICAL_LED_MASK[i]) {
                    ints[i].coerceIn(0, 255).toByte()
                } else 0
            }
            GlyphFrame(
                id = "f_${layer.name}_$step",
                durationMs = dur,
                pixels = bytes
            )
        }
    }

    fun renderLayer(
        layer: DisplayLayer,
        variables: Map<String, String>,
        tick: Int,
        totalFrames: Int,
        splitVerticalLayout: Boolean
    ): IntArray {
        val layerBuffer = IntArray(MATRIX_CELL_COUNT)
        val bounceOffset = if (totalFrames > 1) {
            BOUNCE_Y_OFFSETS[tick.coerceAtLeast(0) % BOUNCE_Y_OFFSETS.size]
        } else {
            0
        }

        for (primitive in layer.primitives) {
            val primBuffer = when (primitive) {
                is LayerPrimitive.RingPrimitive -> {
                    val rawVal = resolveExpression(primitive.progressExpr, variables)
                    val pct = rawVal.filter { it.isDigit() || it == '.' }
                        .toFloatOrNull()?.roundToInt()?.coerceIn(0, 100) ?: 100
                    val effectiveBright = if (primitive.pulse && totalFrames > 1) {
                        modulatePulseBrightness(primitive.brightness, tick)
                    } else {
                        primitive.brightness
                    }
                    val rotationOffsetNorm = if (primitive.animate && totalFrames > 1) {
                        (tick.toFloat() / totalFrames.toFloat()) % 1f
                    } else {
                        0f
                    }
                    renderRingPrimitive(
                        progressPercent = pct,
                        style = primitive.style,
                        peakBrightness = effectiveBright,
                        thickness = primitive.thickness,
                        radius = primitive.radius,
                        rotationOffsetNorm = rotationOffsetNorm,
                        animateRotation = primitive.animate && totalFrames > 1
                    )
                }

                is LayerPrimitive.ArrowPrimitive -> {
                    val dir = resolveExpression(primitive.directionExpr, variables)
                    val effectiveBright = if (primitive.pulse && totalFrames > 1) {
                        modulatePulseBrightness(primitive.brightness, tick)
                    } else {
                        primitive.brightness
                    }
                    val baseSize = if (splitVerticalLayout) {
                        primitive.size.coerceIn(7, 9)
                    } else {
                        primitive.size.coerceIn(7, 13)
                    }
                    val rowShift = if (primitive.bounce && totalFrames > 1) bounceOffset else 0
                    renderArrowPrimitive(
                        directionRaw = dir,
                        size = baseSize,
                        peakBrightness = effectiveBright,
                        splitVerticalLayout = splitVerticalLayout,
                        rowOffset = rowShift
                    )
                }

                is LayerPrimitive.EmojiPrimitive -> {
                    val glyph = resolveExpression(primitive.glyphExpr, variables).ifBlank { "✦" }
                    val baseScale = (primitive.scalePercent.coerceIn(40, 120) / 100f) *
                        (if (splitVerticalLayout && !primitive.hasExplicitY) 0.78f else 0.95f)
                    val targetX = primitive.x.coerceIn(2, 22).toFloat()
                    val baseTargetY = when {
                        primitive.hasExplicitY -> primitive.y.coerceIn(3, 21).toFloat()
                        splitVerticalLayout -> primitive.y.coerceIn(6, 9).toFloat()
                        else -> 12f
                    }
                    val rowShift = if (primitive.bounce && totalFrames > 1) bounceOffset else 0
                    val effectiveBright = if (primitive.pulse && totalFrames > 1) {
                        modulatePulseBrightness(primitive.brightness, tick)
                    } else {
                        primitive.brightness
                    }

                    val bmp = GlyphPatternGenerator.rasterizeUnicodeToBitmap(
                        glyphText = glyph,
                        scale = baseScale,
                        offsetX = 0f,
                        offsetY = 0f,
                        x = targetX,
                        y = baseTargetY,
                        sizePx = primitive.size ?: (if (splitVerticalLayout) 11 else null)
                    )
                    val baseBytes = GlyphPatternGenerator.bitmapToCircularMatrixBytes(
                        bitmap = bmp,
                        peakBrightness = effectiveBright
                    )
                    val shiftedOut = IntArray(MATRIX_CELL_COUNT)
                    for (r in 0 until MATRIX_SIZE) {
                        for (c in 0 until MATRIX_SIZE) {
                            val srcVal = baseBytes[r * MATRIX_SIZE + c].toInt() and 0xFF
                            if (srcVal <= 0) continue
                            val dstR = r + rowShift
                            if (dstR !in 0 until MATRIX_SIZE) continue
                            if (!GlyphHardwareController.isCellInsideCircle(dstR, c)) continue
                            if (splitVerticalLayout && !primitive.hasExplicitY && (dstR < 2 || dstR > 13)) continue
                            if (splitVerticalLayout && primitive.hasExplicitY && dstR > 14 && baseTargetY <= 10f) continue
                            shiftedOut[dstR * MATRIX_SIZE + c] = max(shiftedOut[dstR * MATRIX_SIZE + c], srcVal)
                        }
                    }
                    shiftedOut
                }

                is LayerPrimitive.TextPrimitive -> {
                    val resolvedExpr = resolveExpression(primitive.contentExpr, variables)
                    val dryRunOverride = variables["__dry_run_text_override__"]
                    val cleanKey = primitive.contentExpr.trim().removeSurrounding("\"").removePrefix("$")
                    val isBoundToVariable = variables.containsKey(cleanKey) ||
                        variables.keys.any { it.equals(cleanKey, ignoreCase = true) }
                    val rawText = if (!isBoundToVariable && !dryRunOverride.isNullOrBlank()) {
                        dryRunOverride
                    } else {
                        resolvedExpr
                    }
                    val formattedText = formatCompactUnitText(rawText, primitive.suffix)
                    val effectiveY = if (splitVerticalLayout) {
                        primitive.y.coerceIn(15, 17)
                    } else {
                        primitive.y.coerceIn(0, 20)
                    }
                    val effectiveBright = if (primitive.pulse && totalFrames > 1) {
                        modulatePulseBrightness(primitive.brightness, tick)
                    } else {
                        primitive.brightness
                    }
                    val shouldMarquee = totalFrames > 1 &&
                        (primitive.marquee || primitive.animate || formattedText.length > 4)
                    renderDotMatrixText(
                        text = formattedText,
                        fontSize = primitive.size,
                        align = primitive.align,
                        yOffset = effectiveY,
                        brightness = effectiveBright,
                        animateMarquee = shouldMarquee,
                        tick = tick,
                        totalFrames = totalFrames,
                        suffix = primitive.suffix
                    )
                }

                is LayerPrimitive.IconPrimitive -> {
                    val iconName = resolveExpression(primitive.nameExpr, variables)
                    val effectiveBright = if (primitive.pulse && totalFrames > 1) {
                        modulatePulseBrightness(primitive.brightness, tick)
                    } else {
                        primitive.brightness
                    }
                    val rowShift = if (primitive.bounce && totalFrames > 1) bounceOffset else 0
                    renderIconPrimitive(
                        iconNameRaw = iconName,
                        peakBrightness = effectiveBright,
                        splitVerticalLayout = splitVerticalLayout,
                        rowOffset = rowShift
                    )
                }

                is LayerPrimitive.ShaderPrimitive -> {
                    val shaderType = resolveExpression(primitive.typeExpr, variables)
                    renderShaderPrimitive(shaderType, tick, totalFrames, primitive.brightness)
                }

                is LayerPrimitive.BitmapPrimitive -> {
                    renderBitmapPrimitive(
                        asciiArt = primitive.asciiArt,
                        align = primitive.align,
                        offsetX = primitive.x,
                        offsetY = primitive.y,
                        peakBrightness = primitive.brightness
                    )
                }

                is LayerPrimitive.SequencePrimitive -> {
                    if (primitive.frames.isEmpty()) {
                        IntArray(MATRIX_CELL_COUNT)
                    } else {
                        val frameDurationMs = primitive.intervalMs.coerceAtLeast(50L)
                        val pauseFrames = if (primitive.pauseMs > 0 && frameDurationMs > 0) (primitive.pauseMs / frameDurationMs).toInt() else 0
                        val totalCycleFrames = primitive.frames.size + pauseFrames
                        val cycleIdx = if (primitive.loop && totalCycleFrames > 0) {
                            tick % totalCycleFrames
                        } else {
                            tick.coerceAtMost((totalCycleFrames - 1).coerceAtLeast(0))
                        }

                        if (cycleIdx < primitive.frames.size) {
                            val currentFrame = primitive.frames[cycleIdx]
                            renderBitmapPrimitive(
                                asciiArt = currentFrame.asciiArt,
                                align = primitive.align,
                                peakBrightness = 255
                            )
                        } else {
                            if (primitive.holdLast && primitive.frames.isNotEmpty()) {
                                val lastFrame = primitive.frames.last()
                                renderBitmapPrimitive(
                                    asciiArt = lastFrame.asciiArt,
                                    align = primitive.align,
                                    peakBrightness = 255
                                )
                            } else {
                                IntArray(MATRIX_CELL_COUNT)
                            }
                        }
                    }
                }

                is LayerPrimitive.PlotPrimitive -> {
                    renderPlotPrimitive(primitive.dots)
                }
            }

            for (i in 0 until MATRIX_CELL_COUNT) {
                layerBuffer[i] = max(layerBuffer[i], primBuffer[i])
            }
        }
        val macro = layer.animationMacro
        return if (macro != null) {
            applyAnimationMacro(
                baseBuffer = layerBuffer,
                macro = macro,
                tick = tick,
                totalFrames = totalFrames
            )
        } else {
            layerBuffer
        }
    }

    /**
     * Modulates layer brightness across the 4-frame pulse sequence `[160, 210, 255, 200]`,
     * scaled proportionally if [peakBrightness] < 255.
     */
    fun modulatePulseBrightness(peakBrightness: Int, tick: Int): Int {
        val stepBrightness = PULSE_BRIGHTNESS_STEPS[tick.coerceAtLeast(0) % PULSE_BRIGHTNESS_STEPS.size]
        if (peakBrightness >= 255) return stepBrightness
        val scale = peakBrightness.coerceIn(32, 255) / 255f
        return (stepBrightness * scale).roundToInt().coerceIn(32, 255)
    }

    private fun resolveExpression(expr: String, variables: Map<String, String>): String {
        val rawTrimmed = expr.trim()
        val unquoted = if (rawTrimmed.startsWith("\"") && rawTrimmed.endsWith("\"") && rawTrimmed.length >= 2) {
            rawTrimmed.substring(1, rawTrimmed.length - 1).trim()
        } else {
            rawTrimmed
        }
        val cleanVarKey = unquoted.removePrefix("$").trim()
        variables[cleanVarKey]?.let { return it }
        variables.entries.firstOrNull { it.key.equals(cleanVarKey, ignoreCase = true) }?.let { return it.value }
        return if (unquoted.startsWith("$")) "" else unquoted
    }

    /**
     * Renders an optional perimeter ring / border with configurable thickness (`1..3`) and radius (`10..12`).
     * Distance mapping for default `radius: 12`:
     * - `thickness: 1` -> distance between `11.5` and `12.5` (single-pixel outer ring).
     * - `thickness: 2` -> distance between `10.5` and `12.5` (medium 2-pixel ring).
     * - `thickness: 3` -> distance between `9.5` and `12.5` (bold 3-pixel ring).
     * Styles: `"clock_cw"`, `"counter_cw"`, `"split"`, `"solid"`, `"dashed"`, `"rotate"`.
     */
    fun renderRingPrimitive(
        progressPercent: Int,
        style: String = "clock_cw",
        peakBrightness: Int = 255,
        thickness: Int = 1,
        radius: Int = 12,
        rotationOffsetNorm: Float = 0f,
        animateRotation: Boolean = false
    ): IntArray {
        val out = IntArray(MATRIX_CELL_COUNT)
        val clamped = progressPercent.coerceIn(0, 100)
        val styleLower = style.trim().lowercase()
        val effectiveSweep = if (animateRotation && clamped >= 100 && styleLower != "dashed") {
            75
        } else {
            clamped
        }
        val maxB = peakBrightness.coerceIn(32, 255)
        val safeThickness = thickness.coerceIn(1, 3)
        val safeRadius = radius.coerceIn(10, 12)
        val outerLimit = safeRadius + 0.5
        val innerLimit = (outerLimit - safeThickness.toDouble()).coerceAtLeast(6.5)
        val trackB = if (styleLower == "solid" || styleLower == "dashed") {
            0
        } else {
            (maxB * 0.16f).roundToInt().coerceAtLeast(18)
        }

        for (r in 0 until MATRIX_SIZE) {
            for (c in 0 until MATRIX_SIZE) {
                val dx = c - 12.0
                val dy = r - 12.0
                val dist = hypot(dx, dy)
                if (dist in innerLimit..outerLimit) {
                    // Angle from 12 o'clock clockwise in [0, 1], shifted by rotationOffsetNorm when rotating
                    val rawAngle = (atan2(dy, dx) + PI / 2.0 + 2 * PI) % (2 * PI)
                    val baseCwNorm = (rawAngle / (2 * PI)).toFloat()
                    val cwNorm = ((baseCwNorm - rotationOffsetNorm) % 1f + 1f) % 1f

                    val withinProgress = when (styleLower) {
                        "counter_cw", "ccw" -> {
                            val ccwNorm = (1f - cwNorm) % 1f
                            (ccwNorm * 100f) <= effectiveSweep
                        }
                        "split" -> {
                            val distFromTop = if (cwNorm <= 0.5f) cwNorm * 2f else (1f - cwNorm) * 2f
                            (distFromTop * 100f) <= effectiveSweep
                        }
                        else -> {
                            (cwNorm * 100f) <= effectiveSweep
                        }
                    }

                    val dashPass = if (styleLower == "dashed") {
                        ((cwNorm * 16f).toInt() % 2) == 0
                    } else {
                        true
                    }

                    out[r * MATRIX_SIZE + c] = if (withinProgress && dashPass && effectiveSweep > 0) {
                        maxB
                    } else {
                        trackB
                    }
                }
            }
        }
        return out
    }

    /**
     * Renders clean vector navigation arrows (`straight`/`up`, `left`, `right`, `u-turn`/`down`,
     * `slight_left`, `slight_right`).
     * When [splitVerticalLayout] is true (a `text` layer is also present), scales the arrow into a
     * compact upper bounding box (max height 9–11px, placed strictly within rows 3..13, centered at row 8)
     * so it never collides with lower text at row 15–16.
     */
    fun renderArrowPrimitive(
        directionRaw: String,
        size: Int = 9,
        peakBrightness: Int = 255,
        splitVerticalLayout: Boolean = true,
        rowOffset: Int = 0
    ): IntArray {
        val out = IntArray(MATRIX_CELL_COUNT)
        val b = peakBrightness.coerceIn(32, 255)
        val effectiveSize = if (splitVerticalLayout) size.coerceIn(7, 9) else size.coerceIn(7, 13)
        val half = (effectiveSize / 2).coerceIn(3, 6)
        val armLen = (half - 1).coerceIn(2, 4)
        val baseCenterR = if (splitVerticalLayout) 8 else 11
        val centerR = (baseCenterR + rowOffset).let { r ->
            if (splitVerticalLayout) r.coerceIn(3 + half, 13 - half) else r.coerceIn(4, 20)
        }
        val centerC = 12
        val dir = directionRaw.trim().lowercase()

        fun setDot(r: Int, c: Int, bright: Int = b) {
            if (splitVerticalLayout && (r < 3 || r > 13)) return
            if (r in 0 until MATRIX_SIZE && c in 0 until MATRIX_SIZE &&
                GlyphHardwareController.isCellInsideCircle(r, c)
            ) {
                val idx = r * MATRIX_SIZE + c
                out[idx] = max(out[idx], bright)
            }
        }

        when {
            dir.contains("slight_left") || dir.contains("slight-left") || dir.contains("keep left") -> {
                for (i in -half..half) {
                    setDot(centerR + i, centerC + i)
                    setDot(centerR + i, centerC + i - 1, (b * 0.75f).toInt())
                }
                val tipR = centerR - half
                val tipC = centerC - half
                for (arm in 0..armLen) {
                    setDot(tipR, tipC + arm)
                    setDot(tipR + arm, tipC)
                }
            }

            dir.contains("slight_right") || dir.contains("slight-right") || dir.contains("keep right") -> {
                for (i in -half..half) {
                    setDot(centerR + i, centerC - i)
                    setDot(centerR + i, centerC - i + 1, (b * 0.75f).toInt())
                }
                val tipR = centerR - half
                val tipC = centerC + half
                for (arm in 0..armLen) {
                    setDot(tipR, tipC - arm)
                    setDot(tipR + arm, tipC)
                }
            }

            dir.contains("left") -> {
                for (c in (centerC - half)..(centerC + half)) {
                    setDot(centerR, c)
                    setDot(centerR - 1, c, (b * 0.65f).toInt())
                }
                for (r in centerR..(centerR + half)) {
                    setDot(r, centerC + half)
                }
                val tipC = centerC - half
                for (arm in 1..armLen) {
                    setDot(centerR - arm, tipC + arm)
                    setDot(centerR + arm, tipC + arm)
                }
            }

            dir.contains("right") -> {
                for (c in (centerC - half)..(centerC + half)) {
                    setDot(centerR, c)
                    setDot(centerR - 1, c, (b * 0.65f).toInt())
                }
                for (r in centerR..(centerR + half)) {
                    setDot(r, centerC - half)
                }
                val tipC = centerC + half
                for (arm in 1..armLen) {
                    setDot(centerR - arm, tipC - arm)
                    setDot(centerR + arm, tipC - arm)
                }
            }

            dir.contains("u-turn") || dir.contains("uturn") || dir.contains("down") || dir.contains("back") -> {
                val topR = centerR - half
                val bottomR = centerR + half
                val leftC = centerC - 3
                val rightC = centerC + 3
                for (r in (topR + 2)..bottomR) {
                    setDot(r, rightC)
                }
                for (r in (topR + 2)..(bottomR - 1)) {
                    setDot(r, leftC)
                }
                for (c in (leftC + 1) until rightC) {
                    setDot(topR, c)
                }
                setDot(topR + 1, leftC)
                setDot(topR + 1, rightC)
                setDot(bottomR, leftC)
                for (arm in 1..2) {
                    setDot(bottomR - arm, leftC - arm)
                    setDot(bottomR - arm, leftC + arm)
                }
            }

            else -> {
                // Straight / Up arrow
                val topR = centerR - half
                val bottomR = centerR + half
                for (r in topR..bottomR) {
                    setDot(r, centerC)
                    setDot(r, centerC - 1, (b * 0.6f).toInt())
                }
                for (arm in 1..armLen) {
                    setDot(topR + arm, centerC - arm)
                    setDot(topR + arm, centerC + arm)
                }
            }
        }

        return out
    }

    /**
     * Built-in 3x5 and 5x7 dot-matrix bitmap font renderer for crisp micro-text on the 25x25 matrix.
     * - Automatically normalizes units ("mins"/"minutes"/"min" -> "m", "kilometers"/"km" -> "k", "meters" -> "m")
     *   and appends optional [suffix] when the text does not already include a unit.
     * - Enforces a maximum rendered width of 16 dots at row 17 (4 characters in 3x5 font: `4*3 + 3 = 15` dots <= 16).
     * - If a string exceeds 4 characters:
     *   1. When [animateMarquee] is true, renders a sliding micro-marquee window constrained to 16 dots width.
     *   2. Otherwise, automatically shrinks to 3x5 and truncates/compacts to 4 characters so it never bleeds
     *      past the 25x25 circular perimeter.
     */
    fun renderDotMatrixText(
        text: String,
        fontSize: String = "3x5",
        align: String = "center",
        yOffset: Int = 16,
        brightness: Int = 255,
        animateMarquee: Boolean = false,
        tick: Int = 0,
        totalFrames: Int = 1,
        suffix: String = ""
    ): IntArray {
        val out = IntArray(MATRIX_CELL_COUNT)
        val normalized = formatCompactUnitText(text, suffix).uppercase()
        if (normalized.isEmpty()) return out

        // If 5x7 would exceed 16 dots width or yOffset >= 16, automatically shrink to 3x5
        val requested5x7 = fontSize.trim().lowercase() == "5x7"
        val is5x7 = requested5x7 && yOffset < 16 && (normalized.length * 5 + (normalized.length - 1).coerceAtLeast(0)) <= 16
        val charW = if (is5x7) 5 else 3
        val charH = if (is5x7) 7 else 5
        val spacing = 1
        val maxStaticChars = if (is5x7) 2 else 4 // 4 chars * 3px + 3px spacing = 15 dots <= 16 dots max width
        val startRow = yOffset.coerceIn(0, (MATRIX_SIZE - charH).coerceAtLeast(0))
        val b = brightness.coerceIn(32, 255)

        // Horizontal right-to-left sliding window when marquee: true or formatted text exceeds 4 characters
        if (animateMarquee && (totalFrames > 1 || normalized.length > maxStaticChars)) {
            val padded = "$normalized  "
            val fullPixelWidth = padded.length * (charW + spacing)
            val scrollStep = ((tick * 2) % fullPixelWidth.coerceAtLeast(1))
            // Enforce max 16 dots width (cols 5..20 = 16 dots)
            val viewportLeft = 5
            val viewportRight = 20

            for (visCol in viewportLeft..viewportRight) {
                val virtualX = (visCol - viewportLeft + scrollStep) % fullPixelWidth
                val charIdx = (virtualX / (charW + spacing)) % padded.length
                val colInChar = virtualX % (charW + spacing)
                if (colInChar < charW) {
                    val glyphRows = if (is5x7) get5x7Glyph(padded[charIdx]) else get3x5Glyph(padded[charIdx])
                    val bitMask = 1 shl (charW - 1 - colInChar)
                    for (r in glyphRows.indices) {
                        if ((glyphRows[r] and bitMask) != 0) {
                            val targetR = startRow + r
                            if (targetR in 0 until MATRIX_SIZE &&
                                GlyphHardwareController.isCellInsideCircle(targetR, visCol)
                            ) {
                                out[targetR * MATRIX_SIZE + visCol] = b
                            }
                        }
                    }
                }
            }
            return out
        }

        val fitted = compactTextToMaxChars(normalized, maxStaticChars)
        val totalWidth = (fitted.length * charW + (fitted.length - 1).coerceAtLeast(0) * spacing).coerceAtMost(16)
        val startCol = when (align.trim().lowercase()) {
            "left" -> 5
            "right" -> (20 - totalWidth + 1).coerceAtLeast(5)
            else -> ((MATRIX_SIZE - totalWidth) / 2).coerceIn(5, 20)
        }

        var cursorC = startCol
        for (ch in fitted) {
            if (cursorC - startCol + charW > 16) break
            val glyphRows = if (is5x7) get5x7Glyph(ch) else get3x5Glyph(ch)
            for (r in glyphRows.indices) {
                val rowBits = glyphRows[r]
                for (c in 0 until charW) {
                    val bitMask = 1 shl (charW - 1 - c)
                    if ((rowBits and bitMask) != 0) {
                        val targetR = startRow + r
                        val targetC = cursorC + c
                        if (targetR in 0 until MATRIX_SIZE &&
                            targetC in 0 until MATRIX_SIZE &&
                            GlyphHardwareController.isCellInsideCircle(targetR, targetC)
                        ) {
                            out[targetR * MATRIX_SIZE + targetC] = b
                        }
                    }
                }
            }
            cursorC += charW + spacing
        }

        return out
    }

    /**
     * Compacts a string to fit within [maxChars] (default 4 for 3x5 font) without clipping the edges
     * of the circular aperture:
     * 1. Drops interior vowels (`A, E, I, O, U`) while preserving the first and last character
     *    (e.g., `"ALERT"` -> `"ALRT"`, `"URGENT"` -> `"URGT"`).
     * 2. If still longer than [maxChars], center-truncates to [maxChars] characters.
     */
    fun compactTextToMaxChars(clean: String, maxChars: Int = 4): String {
        if (clean.length <= maxChars) return clean
        val vowels = setOf('A', 'E', 'I', 'O', 'U', ' ')
        val chars = clean.toMutableList()
        var idx = chars.size - 2
        while (chars.size > maxChars && idx >= 1) {
            if (chars[idx] in vowels) {
                chars.removeAt(idx)
            }
            idx--
        }
        if (chars.size <= maxChars) {
            return chars.joinToString("")
        }
        val headCount = (maxChars + 1) / 2
        val tailCount = maxChars - headCount
        return chars.take(headCount).joinToString("") + chars.takeLast(tailCount).joinToString("")
    }

    /**
     * Renders built-in semantic icons: `"battery"`, `"wifi"`, `"bluetooth"`, `"bell"`, `"heart"`, `"check"`, `"cross"`.
     * When [splitVerticalLayout] is true, scales the icon into a compact upper bounding box (rows 3..13,
     * max height 9–11px) so a lower `text` layer at row 15–16 never touches or overlaps it.
     */
    fun renderIconPrimitive(
        iconNameRaw: String,
        peakBrightness: Int = 255,
        splitVerticalLayout: Boolean = false,
        rowOffset: Int = 0
    ): IntArray {
        val out = IntArray(MATRIX_CELL_COUNT)
        val b = peakBrightness.coerceIn(32, 255)
        val name = iconNameRaw.trim().lowercase()

        fun plot(r: Int, c: Int, v: Int = b) {
            val shiftedR = r + rowOffset
            if (splitVerticalLayout && (shiftedR < 3 || shiftedR > 13)) return
            if (shiftedR in 0 until MATRIX_SIZE && c in 0 until MATRIX_SIZE &&
                GlyphHardwareController.isCellInsideCircle(shiftedR, c)
            ) {
                out[shiftedR * MATRIX_SIZE + c] = max(out[shiftedR * MATRIX_SIZE + c], v)
            }
        }

        if (splitVerticalLayout) {
            // Compact upper-half icons (strictly rows 3..13, centered around row 8)
            when {
                name.contains("battery") -> {
                    for (c in 11..13) plot(3, c)
                    for (r in 4..12) {
                        plot(r, 9)
                        plot(r, 15)
                    }
                    for (c in 9..15) {
                        plot(4, c)
                        plot(12, c)
                    }
                    for (r in listOf(6, 8, 10)) {
                        for (c in 11..13) plot(r, c)
                    }
                }

                name.contains("wifi") -> {
                    plot(12, 12)
                    for (r in 4..12) {
                        for (c in 5..19) {
                            val dx = c - 12.0
                            val dy = r - 12.0
                            if (dy < -0.8 && kotlin.math.abs(dx) <= kotlin.math.abs(dy) * 0.95) {
                                val d = hypot(dx, dy)
                                if (d in 2.6..3.5 || d in 5.0..6.0 || d in 7.4..8.4) {
                                    plot(r, c)
                                }
                            }
                        }
                    }
                }

                name.contains("bluetooth") || name.contains("bt") -> {
                    for (r in 3..13) plot(r, 12)
                    for (i in 1..3) {
                        plot(3 + i, 12 + i)
                        plot(6 + i, 15 - i)
                        plot(13 - i, 12 + i)
                        plot(10 - i, 15 - i)
                    }
                    for (i in 1..2) {
                        plot(8 - i, 12 - i)
                        plot(8 + i, 12 - i)
                    }
                }

                name.contains("bell") || name.contains("notif") -> {
                    plot(3, 12)
                    for (r in 4..10) {
                        val halfW = ((r - 3) * 0.55f).roundToInt().coerceIn(1, 4)
                        for (c in (12 - halfW)..(12 + halfW)) {
                            plot(r, c)
                        }
                    }
                    for (c in 7..17) plot(11, c)
                    plot(12, 11)
                    plot(12, 12)
                    plot(12, 13)
                }

                name.contains("heart") -> {
                    for (r in 3..13) {
                        for (c in 7..17) {
                            val x = (c - 12) / 4.0
                            val y = (8 - r) / 4.0
                            val eq = (x * x + y * y - 1.0)
                            if (eq * eq * eq - x * x * y * y * y <= 0.0) {
                                plot(r, c)
                            }
                        }
                    }
                }

                name.contains("check") || name.contains("done") || name.contains("ok") -> {
                    for (i in 0..2) {
                        plot(9 + i, 8 + i)
                        plot(8 + i, 8 + i)
                    }
                    for (i in 0..5) {
                        plot(11 - i, 10 + i)
                        plot(10 - i, 10 + i)
                    }
                }

                else -> {
                    for (i in -4..4) {
                        plot(8 + i, 12 + i)
                        plot(8 + i, 12 - i)
                    }
                }
            }
            return out
        }

        // Full-canvas standalone icon when no text layer is present
        when {
            name.contains("battery") -> {
                for (c in 10..14) plot(5, c)
                for (r in 6..18) {
                    plot(r, 8)
                    plot(r, 16)
                }
                for (c in 8..16) {
                    plot(6, c)
                    plot(18, c)
                }
                for (r in listOf(9, 10, 12, 13, 15, 16)) {
                    for (c in 10..14) plot(r, c)
                }
            }

            name.contains("wifi") -> {
                plot(17, 12)
                plot(16, 12)
                for (r in 6..16) {
                    for (c in 4..20) {
                        val dx = c - 12.0
                        val dy = r - 17.0
                        if (dy < -1.0 && kotlin.math.abs(dx) <= kotlin.math.abs(dy) * 0.95) {
                            val d = hypot(dx, dy)
                            if (d in 3.6..4.6 || d in 6.6..7.6 || d in 9.6..10.6) {
                                plot(r, c)
                            }
                        }
                    }
                }
            }

            name.contains("bluetooth") || name.contains("bt") -> {
                for (r in 5..19) plot(r, 12)
                for (i in 1..5) {
                    plot(5 + i, 12 + i)
                    plot(10 + i, 17 - i)
                    plot(19 - i, 12 + i)
                    plot(14 - i, 17 - i)
                }
                for (i in 1..4) {
                    plot(12 - i, 12 - i)
                    plot(12 + i, 12 - i)
                }
            }

            name.contains("bell") || name.contains("notif") -> {
                plot(5, 12)
                for (r in 6..14) {
                    val halfW = ((r - 5) * 0.55f).roundToInt().coerceIn(2, 5)
                    for (c in (12 - halfW)..(12 + halfW)) {
                        plot(r, c)
                    }
                }
                for (c in 6..18) plot(15, c)
                plot(17, 11)
                plot(17, 12)
                plot(17, 13)
            }

            name.contains("heart") -> {
                for (r in 6..18) {
                    for (c in 5..19) {
                        val x = (c - 12) / 5.5
                        val y = (11 - r) / 5.5
                        val eq = (x * x + y * y - 1.0)
                        if (eq * eq * eq - x * x * y * y * y <= 0.0) {
                            plot(r, c)
                        }
                    }
                }
            }

            name.contains("check") || name.contains("done") || name.contains("ok") -> {
                for (i in 0..3) {
                    plot(13 + i, 7 + i)
                    plot(12 + i, 7 + i)
                }
                for (i in 0..7) {
                    plot(16 - i, 10 + i)
                    plot(15 - i, 10 + i)
                }
            }

            else -> {
                // Cross / Alert
                for (i in -5..5) {
                    plot(12 + i, 12 + i)
                    plot(12 + i, 12 - i)
                }
            }
        }
        return out
    }

    /**
     * Renders a shader frame (`"audio_waveform"`, `"pulse_radial"`, `"breathing"`).
     */
    fun renderShaderPrimitive(
        typeRaw: String,
        tick: Int,
        totalFrames: Int,
        peakBrightness: Int = 255
    ): IntArray {
        val type = typeRaw.trim().lowercase()
        val maxB = peakBrightness.coerceIn(32, 255)
        return when {
            type.contains("audio") || type.contains("wave") -> {
                val bytes = GlyphPatternGenerator.buildAudioWaveformShaderFrame(tick * 2, maxB)
                IntArray(MATRIX_CELL_COUNT) { idx -> bytes[idx].toInt() and 0xFF }
            }

            type.contains("pulse") || type.contains("radial") -> {
                val out = IntArray(MATRIX_CELL_COUNT)
                val phase = (tick.toFloat() / totalFrames.coerceAtLeast(1).toFloat())
                val activeRadius = 2.0 + phase * 10.2
                for (r in 0 until MATRIX_SIZE) {
                    for (c in 0 until MATRIX_SIZE) {
                        if (!GlyphHardwareController.isCellInsideCircle(r, c)) continue
                        val d = hypot((c - 12).toDouble(), (r - 12).toDouble())
                        val diff = kotlin.math.abs(d - activeRadius)
                        if (diff <= 1.8) {
                            val intensity = ((1.0 - diff / 1.8) * maxB).roundToInt().coerceIn(0, maxB)
                            out[r * MATRIX_SIZE + c] = intensity
                        }
                    }
                }
                out
            }

            else -> {
                // "breathing"
                val out = IntArray(MATRIX_CELL_COUNT)
                val angle = (tick.toDouble() / totalFrames.coerceAtLeast(1).toDouble()) * 2.0 * PI
                val breath = ((sin(angle) + 1.0) * 0.5).coerceIn(0.15, 1.0)
                for (r in 0 until MATRIX_SIZE) {
                    for (c in 0 until MATRIX_SIZE) {
                        if (!GlyphHardwareController.isCellInsideCircle(r, c)) continue
                        val d = hypot((c - 12).toDouble(), (r - 12).toDouble())
                        if (d <= 8.5) {
                            val falloff = (1.0 - (d / 9.5)).coerceIn(0.1, 1.0)
                            out[r * MATRIX_SIZE + c] = (maxB * breath * falloff).roundToInt().coerceIn(0, maxB)
                        }
                    }
                }
                out
            }
        }
    }

    /**
     * Parses and renders raw ASCII 2D art bitmaps with automatic bounding-box calculation,
     * smart anchor remapping (center / top_center / manual), proportional downscaling if exceeding
     * the physical 25x25 circular perimeter (r <= 12.5), and warning logging in AuditDiagnosticsBuffer.
     */
    fun renderBitmapPrimitive(
        asciiArt: String,
        align: String = "center",
        offsetX: Int = 0,
        offsetY: Int = 0,
        peakBrightness: Int = 255
    ): IntArray {
        val out = IntArray(MATRIX_CELL_COUNT)
        if (asciiArt.isBlank()) return out

        val rawLines = asciiArt.lines()
        val activePixels = mutableListOf<Triple<Int, Int, Int>>() // col, row, brightness

        for (r in rawLines.indices) {
            val line = rawLines[r]
            for (c in line.indices) {
                val ch = line[c]
                val b = when {
                    ch == '.' || ch == ' ' -> 0
                    ch == '#' || ch == '*' -> peakBrightness
                    ch.isDigit() -> (ch.digitToInt() * peakBrightness / 9).coerceIn(0, peakBrightness)
                    else -> peakBrightness
                }
                if (b > 0) {
                    activePixels.add(Triple(c, r, b))
                }
            }
        }

        if (activePixels.isEmpty()) return out

        val nonBlankLines = rawLines.map { it.trim() }.filter { it.isNotEmpty() }
        val isFull25x25Canvas = nonBlankLines.size == MATRIX_SIZE && nonBlankLines.all { it.length == MATRIX_SIZE }

        // Calculate Bounding Box (for full 25x25 Studio canvases, preserve exact 25x25 coordinates)
        val minX = activePixels.minOf { it.first }
        val maxX = activePixels.maxOf { it.first }
        val minY = activePixels.minOf { it.second }
        val maxY = activePixels.maxOf { it.second }

        val centerX = if (isFull25x25Canvas && align.trim().lowercase() != "top_center") 12.0f else (minX + maxX) / 2.0f
        val centerY = if (isFull25x25Canvas && align.trim().lowercase() != "top_center") 12.0f else (minY + maxY) / 2.0f

        val targetCenter = when (align.trim().lowercase()) {
            "top_center" -> Pair(12.0f, 8.0f)
            "manual" -> Pair(centerX - offsetX, centerY - offsetY)
            else -> Pair(12.0f, 12.0f) // "center"
        }

        val shiftX = targetCenter.first - centerX + offsetX
        val shiftY = targetCenter.second - centerY + offsetY

        // Calculate maximum radius from hardware center (12, 12)
        var maxRadius = 0.0f
        val shiftedPoints = activePixels.map { (c, r, b) ->
            val newC = c + shiftX
            val newR = r + shiftY
            val dist = hypot(newC - 12.0f, newR - 12.0f)
            if (dist > maxRadius) maxRadius = dist
            Triple(newC, newR, b)
        }

        val scale = if (maxRadius > 12.2f && maxRadius > 0f) {
            12.2f / maxRadius
        } else {
            1.0f
        }

        for ((c, r, b) in shiftedPoints) {
            val finalC = 12.0f + (c - 12.0f) * scale
            val finalR = 12.0f + (r - 12.0f) * scale
            val gridC = finalC.roundToInt()
            val gridR = finalR.roundToInt()

            if (gridC in 0 until MATRIX_SIZE && gridR in 0 until MATRIX_SIZE &&
                GlyphHardwareController.isCellInsideCircle(gridR, gridC)
            ) {
                val idx = gridR * MATRIX_SIZE + gridC
                out[idx] = max(out[idx], b)
            } else {
                val warning = "GEOMETRIC_WARNING: Pixel dropped at ($gridC, $gridR) outside circular boundary."
                com.example.engine.AuditDiagnosticsBuffer.recordExtractFallbackWarning(warning)
            }
        }

        return out
    }

    /**
     * Renders explicit [x, y, brightness] plot dots on the 25x25 circular matrix.
     */
    fun renderPlotPrimitive(dots: List<LayerPrimitive.PlotDot>): IntArray {
        val out = IntArray(MATRIX_CELL_COUNT)
        for (dot in dots) {
            val r = dot.y
            val c = dot.x
            if (r in 0 until MATRIX_SIZE && c in 0 until MATRIX_SIZE &&
                GlyphHardwareController.isCellInsideCircle(r, c)
            ) {
                val idx = r * MATRIX_SIZE + c
                out[idx] = max(out[idx], dot.brightness)
            } else {
                val warning = "GEOMETRIC_WARNING: Plot dot dropped at ($c, $r) outside circular boundary."
                com.example.engine.AuditDiagnosticsBuffer.recordExtractFallbackWarning(warning)
            }
        }
        return out
    }

    /**
     * 3x5 pixel-sharp bitmap font (each entry is 5 rows of 3-bit integers, MSB on left).
     */
    fun get3x5Glyph(ch: Char): IntArray {
        return when (ch) {
            '0' -> intArrayOf(0b111, 0b101, 0b101, 0b101, 0b111)
            '1' -> intArrayOf(0b010, 0b110, 0b010, 0b010, 0b111)
            '2' -> intArrayOf(0b111, 0b001, 0b111, 0b100, 0b111)
            '3' -> intArrayOf(0b111, 0b001, 0b111, 0b001, 0b111)
            '4' -> intArrayOf(0b101, 0b101, 0b111, 0b001, 0b001)
            '5' -> intArrayOf(0b111, 0b100, 0b111, 0b001, 0b111)
            '6' -> intArrayOf(0b111, 0b100, 0b111, 0b101, 0b111)
            '7' -> intArrayOf(0b111, 0b001, 0b010, 0b010, 0b010)
            '8' -> intArrayOf(0b111, 0b101, 0b111, 0b101, 0b111)
            '9' -> intArrayOf(0b111, 0b101, 0b111, 0b001, 0b111)
            'A' -> intArrayOf(0b010, 0b101, 0b111, 0b101, 0b101)
            'B' -> intArrayOf(0b110, 0b101, 0b110, 0b101, 0b110)
            'C' -> intArrayOf(0b111, 0b100, 0b100, 0b100, 0b111)
            'D' -> intArrayOf(0b110, 0b101, 0b101, 0b101, 0b110)
            'E' -> intArrayOf(0b111, 0b100, 0b110, 0b100, 0b111)
            'F' -> intArrayOf(0b111, 0b100, 0b110, 0b100, 0b100)
            'G' -> intArrayOf(0b111, 0b100, 0b101, 0b101, 0b111)
            'H' -> intArrayOf(0b101, 0b101, 0b111, 0b101, 0b101)
            'I' -> intArrayOf(0b111, 0b010, 0b010, 0b010, 0b111)
            'J' -> intArrayOf(0b001, 0b001, 0b001, 0b101, 0b111)
            'K' -> intArrayOf(0b101, 0b101, 0b110, 0b101, 0b101)
            'L' -> intArrayOf(0b100, 0b100, 0b100, 0b100, 0b111)
            'M' -> intArrayOf(0b101, 0b111, 0b111, 0b101, 0b101)
            'N' -> intArrayOf(0b111, 0b101, 0b101, 0b101, 0b101)
            'O' -> intArrayOf(0b111, 0b101, 0b101, 0b101, 0b111)
            'P' -> intArrayOf(0b111, 0b101, 0b111, 0b100, 0b100)
            'Q' -> intArrayOf(0b111, 0b101, 0b101, 0b111, 0b001)
            'R' -> intArrayOf(0b111, 0b101, 0b110, 0b101, 0b101)
            'S' -> intArrayOf(0b111, 0b100, 0b111, 0b001, 0b111)
            'T' -> intArrayOf(0b111, 0b010, 0b010, 0b010, 0b010)
            'U' -> intArrayOf(0b101, 0b101, 0b101, 0b101, 0b111)
            'V' -> intArrayOf(0b101, 0b101, 0b101, 0b101, 0b010)
            'W' -> intArrayOf(0b101, 0b101, 0b111, 0b111, 0b101)
            'X' -> intArrayOf(0b101, 0b101, 0b010, 0b101, 0b101)
            'Y' -> intArrayOf(0b101, 0b101, 0b010, 0b010, 0b010)
            'Z' -> intArrayOf(0b111, 0b001, 0b010, 0b100, 0b111)
            '%' -> intArrayOf(0b101, 0b001, 0b010, 0b100, 0b101)
            '.' -> intArrayOf(0b000, 0b000, 0b000, 0b000, 0b010)
            ':' -> intArrayOf(0b000, 0b010, 0b000, 0b010, 0b000)
            '-' -> intArrayOf(0b000, 0b000, 0b111, 0b000, 0b000)
            '+' -> intArrayOf(0b000, 0b010, 0b111, 0b010, 0b000)
            '/' -> intArrayOf(0b001, 0b001, 0b010, 0b100, 0b100)
            '!' -> intArrayOf(0b010, 0b010, 0b010, 0b000, 0b010)
            ' ' -> intArrayOf(0b000, 0b000, 0b000, 0b000, 0b000)
            else -> intArrayOf(0b111, 0b101, 0b111, 0b101, 0b111)
        }
    }

    /**
     * 5x7 pixel-sharp bitmap font (each entry is 7 rows of 5-bit integers, MSB on left).
     */
    private fun get5x7Glyph(ch: Char): IntArray {
        return when (ch) {
            '0' -> intArrayOf(0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110)
            '1' -> intArrayOf(0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110)
            '2' -> intArrayOf(0b01110, 0b10001, 0b00001, 0b00110, 0b01000, 0b10000, 0b11111)
            '3' -> intArrayOf(0b11110, 0b00001, 0b00001, 0b01110, 0b00001, 0b00001, 0b11110)
            '4' -> intArrayOf(0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010)
            '5' -> intArrayOf(0b11111, 0b10000, 0b11110, 0b00001, 0b00001, 0b10001, 0b01110)
            '6' -> intArrayOf(0b00110, 0b01000, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110)
            '7' -> intArrayOf(0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000)
            '8' -> intArrayOf(0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110)
            '9' -> intArrayOf(0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00010, 0b01100)
            'M' -> intArrayOf(0b10001, 0b11011, 0b10101, 0b10101, 0b10001, 0b10001, 0b10001)
            'K' -> intArrayOf(0b10001, 0b10010, 0b10100, 0b11000, 0b10100, 0b10010, 0b10001)
            'F' -> intArrayOf(0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b10000)
            'T' -> intArrayOf(0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100)
            '%' -> intArrayOf(0b11001, 0b11010, 0b00010, 0b00100, 0b01000, 0b01011, 0b10011)
            ' ' -> intArrayOf(0, 0, 0, 0, 0, 0, 0)
            else -> {
                // Upscale 3x5 glyph into 5x7
                val g3 = get3x5Glyph(ch)
                intArrayOf(
                    0,
                    g3[0] shl 1,
                    g3[1] shl 1,
                    g3[2] shl 1,
                    g3[3] shl 1,
                    g3[4] shl 1,
                    0
                )
            }
        }
    }
}
