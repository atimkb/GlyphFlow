package com.example.dsl

import android.content.Context
import com.example.domain.model.GlyphFrame
import com.example.domain.model.MATRIX_CELL_COUNT
import com.example.domain.model.MATRIX_SIZE
import com.example.hardware.GlyphAperture
import com.example.hardware.GlyphHardwareController
import java.util.UUID
import kotlin.math.max

/**
 * Visual Layer model for the Studio Dock Layers panel and multi-layer canvas editor.
 */
data class DecompiledVisualLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val primitiveType: String, // "BITMAP", "SEQUENCE", "TEXT", "RING", "ARROW", "EMOJI", "ICON", "SHADER", "PLOT"
    val originalPrimitiveType: String = primitiveType,
    var isVisible: Boolean = true,
    var brightness: Int = 255,
    var yOffset: Int = 16,
    var textContent: String = "",
    var suffix: String = "",
    var textSize: String = "3x5",
    var ringRadius: Int = 12,
    var ringThickness: Int = 1,
    var ringStyle: String = "clock_cw",
    var progressExpr: String = "100",
    var arrowSize: Int = 9,
    var emojiScale: Int = 75,
    var emojiX: Int = 12,
    var pulse: Boolean = false,
    var bounce: Boolean = false,
    var animate: Boolean = false,
    var align: String = "center",
    val showIfCondition: ShowIfCondition? = null,
    val showIfRaw: String? = null,
    val groupName: String? = null,
    val animationMacro: LayerAnimationMacro? = null,
    val isActiveInDryRun: Boolean = true,
    val frames: List<GlyphFrame> = listOf(GlyphFrame(id = "f_0", durationMs = 100L)),
    val isCanvasEdited: Boolean = false,
    val intervalMs: Long = 100L,
    val pauseMs: Long = 0L,
    val holdLast: Boolean = false,
    val loop: Boolean = true
)

/**
 * Decompiled rule container storing both AST structural logic (extract regexes, conditions, behavior)
 * and visual studio primitives (per-layer frames, layers, alignment, and conditional dry-run state).
 */
data class DecompiledStudioRule(
    val ruleName: String = "Custom Glyph Rule",
    val targetPackages: List<String> = listOf("*"),
    val whenTriggerEvent: String = "notification",
    val rawExtractBlock: String = "",
    val rawBehaviorBlock: String = "",
    val frames: List<GlyphFrame> = listOf(GlyphFrame(id = "f_0", durationMs = 100L)),
    val visualLayers: List<DecompiledVisualLayer> = emptyList(),
    val initialSelectedLayerId: String? = null,
    val dryRunVariables: Map<String, String> = emptyMap(),
    val globalAlign: String = "center",
    val defaultIntervalMs: Long = 100L,
    val defaultPauseMs: Long = 0L,
    val loop: Boolean = true,
    val originalProgram: GlyphScriptProgram? = null,
    val originalRuleId: Long = 0L
)

/**
 * Bidirectional GlyphScript v2 Code-to-Visual Decompiler and Recompiler.
 *
 * Decompiles any valid GlyphScript v2 rule into per-layer visual dot grid keyframes and dock layer models,
 * preserving conditional `group:` and `show_if:` metadata, and recompiling canvas edits back to the target layer.
 */
object GlyphScriptDecompiler {

    /**
     * Converts a 25x25 circular pixel byte buffer into 2D ASCII art.
     */
    fun pixelsToAscii(pixels: ByteArray): String {
        val sb = StringBuilder()
        for (r in 0 until MATRIX_SIZE) {
            for (c in 0 until MATRIX_SIZE) {
                if (!GlyphHardwareController.isCellInsideCircle(r, c)) {
                    sb.append('.')
                } else {
                    val idx = r * MATRIX_SIZE + c
                    val b = if (idx < pixels.size) (pixels[idx].toInt() and 0xFF) else 0
                    when {
                        b == 0 -> sb.append('.')
                        b >= 200 -> sb.append('#')
                        b >= 100 -> sb.append('*')
                        else -> sb.append(':')
                    }
                }
            }
            if (r < MATRIX_SIZE - 1) sb.append("\n")
        }
        return sb.toString()
    }

    /**
     * Converts raw ASCII art lines into a 625-byte circular matrix buffer.
     */
    fun asciiToPixels(ascii: String, peakBrightness: Int = 255): ByteArray {
        val out = ByteArray(MATRIX_CELL_COUNT)
        if (ascii.isBlank()) return out

        val renderedInts = GlyphFrameCompositor.renderBitmapPrimitive(
            asciiArt = ascii,
            align = "center",
            peakBrightness = peakBrightness
        )
        for (i in 0 until MATRIX_CELL_COUNT) {
            out[i] = renderedInts[i].coerceIn(0, 255).toByte()
        }
        return GlyphAperture.clampToPhysicalAperture(out)
    }

    /**
     * Renders a single [DecompiledVisualLayer] into fresh [GlyphFrame]s based on its current primitive properties.
     */
    fun renderVisualLayerFrames(
        layer: DecompiledVisualLayer,
        variables: Map<String, String>,
        splitVerticalLayout: Boolean = true
    ): List<GlyphFrame> {
        if (layer.isCanvasEdited || layer.primitiveType == "BITMAP" || layer.primitiveType == "SEQUENCE" || layer.primitiveType == "PLOT") {
            return layer.frames.ifEmpty { listOf(GlyphFrame(id = "f_${layer.name}_0", durationMs = 100L)) }
        }
        val displayLayer = toDisplayLayer(layer)
        return GlyphFrameCompositor.renderSingleLayerFrames(
            layer = displayLayer,
            variables = variables,
            splitVerticalLayout = splitVerticalLayout
        )
    }

    /**
     * Toggles a layer between Preset Vector/Emoji mode and Editable Pixel Dots (25x25 Bitmap/Sequence) mode.
     */
    fun toggleLayerBitmapMode(
        layer: DecompiledVisualLayer,
        variables: Map<String, String>,
        splitVerticalLayout: Boolean = true
    ): DecompiledVisualLayer {
        val isCurrentlyBitmap = layer.isCanvasEdited || layer.primitiveType in setOf("BITMAP", "SEQUENCE")
        val canRevertToPreset = layer.originalPrimitiveType in setOf("ARROW", "ICON", "EMOJI", "RING", "TEXT")
        return if (isCurrentlyBitmap && canRevertToPreset) {
            val reverted = layer.copy(
                primitiveType = layer.originalPrimitiveType,
                isCanvasEdited = false
            )
            val freshFrames = renderVisualLayerFrames(reverted, variables, splitVerticalLayout)
            reverted.copy(frames = freshFrames)
        } else {
            val rasterizedFrames = if (layer.frames.isNotEmpty() && layer.frames.any { it.activeDotCount() > 0 }) {
                layer.frames.map { it.copy(pixels = it.pixels.copyOf()) }
            } else {
                val baseDisplay = toDisplayLayer(layer.copy(primitiveType = layer.originalPrimitiveType, isCanvasEdited = false))
                GlyphFrameCompositor.renderSingleLayerFrames(baseDisplay, variables, splitVerticalLayout)
            }
            layer.copy(
                primitiveType = if (rasterizedFrames.size > 1) "SEQUENCE" else "BITMAP",
                isCanvasEdited = true,
                frames = rasterizedFrames
            )
        }
    }

    /**
     * Computes a 625-byte composite reference background of all visible non-targeted layers
     * evaluated under [variables]. Mutually exclusive siblings in the same `groupName` as the
     * selected layer are automatically excluded so they do not ghost on top of the active maneuver.
     */
    fun renderCompositeOverlay(
        layers: List<DecompiledVisualLayer>,
        selectedLayerId: String?,
        variables: Map<String, String>
    ): ByteArray {
        val out = ByteArray(MATRIX_CELL_COUNT)
        if (layers.isEmpty()) return out

        val selectedLayer = layers.find { it.id == selectedLayerId }
        val selectedGroup = selectedLayer?.groupName
        val hasTextLayer = layers.any { it.isVisible && it.primitiveType == "TEXT" }
        val hasUpperGraphic = layers.any {
            it.isVisible && it.primitiveType in setOf("ARROW", "EMOJI", "ICON", "BITMAP", "SEQUENCE")
        }
        val splitVerticalLayout = hasTextLayer && hasUpperGraphic

        val evaluatedGroups = mutableSetOf<String>()
        if (!selectedGroup.isNullOrBlank()) {
            evaluatedGroups.add(selectedGroup)
        }

        val blended = IntArray(MATRIX_CELL_COUNT)

        for (layer in layers) {
            if (!layer.isVisible) continue
            if (layer.id == selectedLayerId) continue

            val grp = layer.groupName
            if (!grp.isNullOrBlank()) {
                if (evaluatedGroups.contains(grp)) continue
                val condOk = layer.showIfCondition?.evaluate(variables) ?: true
                if (!condOk) continue
                evaluatedGroups.add(grp)
            } else {
                val condOk = layer.showIfCondition?.evaluate(variables) ?: true
                if (!condOk) continue
            }

            val layerPixels = if (layer.isCanvasEdited || layer.primitiveType in setOf("BITMAP", "SEQUENCE", "PLOT")) {
                layer.frames.firstOrNull()?.pixels ?: ByteArray(MATRIX_CELL_COUNT)
            } else {
                val renderedFrames = renderVisualLayerFrames(layer, variables, splitVerticalLayout)
                renderedFrames.firstOrNull()?.pixels ?: ByteArray(MATRIX_CELL_COUNT)
            }

            for (i in 0 until MATRIX_CELL_COUNT) {
                val v = layerPixels[i].toInt() and 0xFF
                if (v > blended[i]) {
                    blended[i] = v
                }
            }
        }

        for (i in 0 until MATRIX_CELL_COUNT) {
            out[i] = if (GlyphAperture.PHYSICAL_LED_MASK[i]) {
                blended[i].coerceIn(0, 255).toByte()
            } else {
                0
            }
        }
        return out
    }

    /**
     * Merges multiple [DecompiledVisualLayer]s into a unified single `BITMAP` (or `SEQUENCE`) layer
     * by max-blending their 25x25 pixel buffers across frames within the 489-LED circular mask.
     */
    fun mergeVisualLayers(
        layersToMerge: List<DecompiledVisualLayer>,
        variables: Map<String, String>,
        mergedName: String? = null
    ): DecompiledVisualLayer {
        if (layersToMerge.isEmpty()) {
            return DecompiledVisualLayer(
                name = mergedName ?: "merged_layer",
                primitiveType = "BITMAP",
                originalPrimitiveType = "BITMAP",
                isCanvasEdited = true
            )
        }
        val renderedPerLayer = layersToMerge.map { layer ->
            val frames = if (layer.frames.isNotEmpty() && (layer.isCanvasEdited || layer.primitiveType in setOf("BITMAP", "SEQUENCE", "PLOT"))) {
                layer.frames
            } else {
                renderVisualLayerFrames(layer, variables, splitVerticalLayout = false)
            }
            frames.ifEmpty { listOf(GlyphFrame(id = "f_0", durationMs = 100L)) }
        }
        val maxFrames = renderedPerLayer.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
        val baseIntervalMs = layersToMerge.first().frames.firstOrNull()?.durationMs
            ?: layersToMerge.first().intervalMs.coerceAtLeast(66L)

        val mergedFrames = (0 until maxFrames).map { frameIdx ->
            val blended = ByteArray(MATRIX_CELL_COUNT)
            for (layerFrames in renderedPerLayer) {
                val srcFrame = layerFrames[frameIdx % layerFrames.size]
                for (i in 0 until MATRIX_CELL_COUNT) {
                    if (!GlyphAperture.PHYSICAL_LED_MASK[i]) continue
                    val v = srcFrame.pixels[i].toInt() and 0xFF
                    val cur = blended[i].toInt() and 0xFF
                    if (v > cur) {
                        blended[i] = v.toByte()
                    }
                }
            }
            GlyphFrame(
                id = "merged_f_$frameIdx",
                durationMs = baseIntervalMs,
                pixels = GlyphAperture.clampToPhysicalAperture(blended)
            )
        }

        val cleanName = mergedName?.takeIf { it.isNotBlank() } ?: run {
            val base = layersToMerge.first().name.take(14)
            "${base}_merged"
        }

        return DecompiledVisualLayer(
            name = cleanName,
            primitiveType = if (mergedFrames.size > 1) "SEQUENCE" else "BITMAP",
            originalPrimitiveType = "BITMAP",
            isVisible = true,
            brightness = 255,
            align = "center",
            frames = mergedFrames,
            isCanvasEdited = true,
            intervalMs = baseIntervalMs
        )
    }

    private fun toDisplayLayer(layer: DecompiledVisualLayer): DisplayLayer {
        val effectiveType = if (layer.isCanvasEdited && layer.primitiveType in setOf("BITMAP", "SEQUENCE")) {
            if (layer.frames.size > 1) "SEQUENCE" else layer.primitiveType
        } else {
            layer.primitiveType
        }
        val prim: LayerPrimitive = when (effectiveType) {
            "TEXT" -> LayerPrimitive.TextPrimitive(
                contentExpr = layer.textContent.ifBlank { "TEXT" },
                suffix = layer.suffix,
                size = layer.textSize,
                align = layer.align,
                y = layer.yOffset,
                animate = layer.animate,
                marquee = layer.animate,
                pulse = layer.pulse,
                brightness = layer.brightness
            )
            "RING" -> LayerPrimitive.RingPrimitive(
                progressExpr = layer.progressExpr,
                style = layer.ringStyle,
                thickness = layer.ringThickness,
                radius = layer.ringRadius,
                pulse = layer.pulse,
                animate = layer.animate,
                brightness = layer.brightness
            )
            "ARROW" -> LayerPrimitive.ArrowPrimitive(
                directionExpr = layer.textContent.ifBlank { "straight" },
                size = layer.arrowSize,
                pulse = layer.pulse,
                bounce = layer.bounce,
                brightness = layer.brightness
            )
            "EMOJI" -> LayerPrimitive.EmojiPrimitive(
                glyphExpr = layer.textContent.ifBlank { "✦" },
                scalePercent = layer.emojiScale,
                x = layer.emojiX,
                y = layer.yOffset,
                hasExplicitY = true,
                bounce = layer.bounce,
                pulse = layer.pulse,
                brightness = layer.brightness
            )
            "ICON" -> LayerPrimitive.IconPrimitive(
                nameExpr = layer.textContent.ifBlank { "bell" },
                pulse = layer.pulse,
                bounce = layer.bounce,
                brightness = layer.brightness
            )
            "SHADER" -> LayerPrimitive.ShaderPrimitive(
                typeExpr = layer.textContent.ifBlank { "pulse" },
                brightness = layer.brightness
            )
            "SEQUENCE" -> framesToAstPrimitive(
                frames = layer.frames,
                align = layer.align,
                brightness = layer.brightness,
                loop = layer.loop,
                pauseMs = layer.pauseMs,
                holdLast = layer.holdLast
            )
            else -> LayerPrimitive.BitmapPrimitive(
                asciiArt = pixelsToAscii(layer.frames.firstOrNull()?.pixels ?: ByteArray(MATRIX_CELL_COUNT)),
                align = layer.align,
                brightness = layer.brightness
            )
        }
        return DisplayLayer(
            name = layer.name,
            primitives = listOf(prim),
            showIfCondition = layer.showIfCondition,
            groupName = layer.groupName,
            animationMacro = layer.animationMacro
        )
    }

    /**
     * Decompiles GlyphScript v2 code into a [DecompiledStudioRule], preserving per-layer frames,
     * expanding/rasterizing preset arrows & icons into editable 25x25 bitmap frames, and selecting
     * the active conditional layer matching [dryRunVariables].
     */
    fun decompile(
        scriptSource: String,
        context: Context,
        ruleId: Long = 0L,
        dryRunVariables: Map<String, String> = emptyMap()
    ): DecompiledStudioRule {
        val cleanScript = scriptSource.trim()
        if (cleanScript.isEmpty()) {
            val defaultLayer = DecompiledVisualLayer(name = "canvas_base", primitiveType = "BITMAP", isCanvasEdited = true)
            return DecompiledStudioRule(
                visualLayers = listOf(defaultLayer),
                initialSelectedLayerId = defaultLayer.id,
                originalRuleId = ruleId
            )
        }

        val compileResult = GlyphScriptCompiler.compile(
            scriptText = cleanScript,
            context = context,
            dryRunTitle = dryRunVariables["title"] ?: dryRunVariables["notification.title"] ?: "",
            dryRunBody = dryRunVariables["body"] ?: dryRunVariables["text"] ?: dryRunVariables["notification.body"] ?: "",
            dryRunSubText = dryRunVariables["subText"] ?: dryRunVariables["subtext"] ?: "",
            dryRunProgress = dryRunVariables["progress_percent"]?.toIntOrNull() ?: 72
        )
        if (compileResult is GlyphScriptCompileResult.Success) {
            val program = compileResult.program
            val effectiveVars = LinkedHashMap<String, String>().apply {
                putAll(compileResult.extractedPreviewVariables)
                for ((k, v) in dryRunVariables) {
                    if (v.isNotBlank()) {
                        put(k.removePrefix("$"), v)
                    }
                }
            }

            val activeDisplayLayers = GlyphFrameCompositor.filterVisibleLayers(program.displayLayers, effectiveVars)
            val activeLayerNames = activeDisplayLayers.map { it.name }.toSet()

            val allPrimitives = program.displayLayers.flatMap { it.primitives }
            val hasTextLayer = allPrimitives.any { it is LayerPrimitive.TextPrimitive }
            val hasUpperGraphicLayer = allPrimitives.any {
                it is LayerPrimitive.ArrowPrimitive ||
                    it is LayerPrimitive.EmojiPrimitive ||
                    it is LayerPrimitive.IconPrimitive ||
                    it is LayerPrimitive.BitmapPrimitive ||
                    it is LayerPrimitive.SequencePrimitive
            }
            val splitVerticalLayout = hasTextLayer && hasUpperGraphicLayer

            val visualLayers = mutableListOf<DecompiledVisualLayer>()
            var detectedAlign = "center"
            var detectedInterval = 100L
            var detectedPause = 0L
            var detectedLoop = true

            for (layer in program.displayLayers) {
                val layerFrames = GlyphFrameCompositor.renderSingleLayerFrames(
                    layer = layer,
                    variables = effectiveVars,
                    splitVerticalLayout = splitVerticalLayout
                )
                val isActive = layer.name in activeLayerNames
                val showIfStr = layer.showIfCondition?.toDslString()
                val prim = layer.primitives.firstOrNull()
                val layerMacro = layer.animationMacro

                when (prim) {
                    is LayerPrimitive.SequencePrimitive -> {
                        detectedAlign = prim.align
                        detectedInterval = (layerMacro?.frameIntervalMs ?: prim.intervalMs).coerceIn(33L, 2000L)
                        detectedPause = prim.pauseMs
                        detectedLoop = prim.loop
                        visualLayers.add(
                            DecompiledVisualLayer(
                                name = layer.name,
                                primitiveType = "SEQUENCE",
                                originalPrimitiveType = "SEQUENCE",
                                align = prim.align,
                                animate = true,
                                showIfCondition = layer.showIfCondition,
                                showIfRaw = showIfStr,
                                groupName = layer.groupName,
                                animationMacro = null, // Baked into discrete timeline frames
                                isActiveInDryRun = isActive,
                                frames = layerFrames,
                                isCanvasEdited = true,
                                intervalMs = detectedInterval,
                                pauseMs = detectedPause,
                                holdLast = prim.holdLast,
                                loop = prim.loop
                            )
                        )
                    }

                    is LayerPrimitive.BitmapPrimitive -> {
                        detectedAlign = prim.align
                        if (layerMacro != null) {
                            detectedInterval = layerMacro.frameIntervalMs
                        }
                        visualLayers.add(
                            DecompiledVisualLayer(
                                name = layer.name,
                                primitiveType = if (layerFrames.size > 1) "SEQUENCE" else "BITMAP",
                                originalPrimitiveType = "BITMAP",
                                align = prim.align,
                                brightness = prim.brightness,
                                animate = layerFrames.size > 1,
                                showIfCondition = layer.showIfCondition,
                                showIfRaw = showIfStr,
                                groupName = layer.groupName,
                                animationMacro = null, // Baked into discrete timeline frames
                                isActiveInDryRun = isActive,
                                frames = layerFrames,
                                isCanvasEdited = true,
                                intervalMs = layerMacro?.frameIntervalMs ?: 100L
                            )
                        )
                    }

                    is LayerPrimitive.TextPrimitive -> {
                        val isStaticTextWithMacro = layerMacro != null && !prim.contentExpr.trim().startsWith("$")
                        visualLayers.add(
                            DecompiledVisualLayer(
                                name = layer.name,
                                primitiveType = if (isStaticTextWithMacro && layerFrames.size > 1) "SEQUENCE" else "TEXT",
                                originalPrimitiveType = "TEXT",
                                textContent = prim.contentExpr,
                                suffix = prim.suffix,
                                textSize = prim.size,
                                yOffset = prim.y,
                                brightness = prim.brightness,
                                pulse = prim.pulse,
                                animate = prim.animate || prim.marquee || layerMacro != null,
                                align = prim.align,
                                showIfCondition = layer.showIfCondition,
                                showIfRaw = showIfStr,
                                groupName = layer.groupName,
                                animationMacro = if (isStaticTextWithMacro) null else layerMacro,
                                isActiveInDryRun = isActive,
                                frames = layerFrames,
                                isCanvasEdited = isStaticTextWithMacro,
                                intervalMs = layerMacro?.frameIntervalMs ?: 100L
                            )
                        )
                    }

                    is LayerPrimitive.RingPrimitive -> {
                        val rasterizeRing = layerMacro != null
                        visualLayers.add(
                            DecompiledVisualLayer(
                                name = layer.name,
                                primitiveType = if (rasterizeRing) {
                                    if (layerFrames.size > 1) "SEQUENCE" else "BITMAP"
                                } else {
                                    "RING"
                                },
                                originalPrimitiveType = "RING",
                                ringRadius = prim.radius,
                                ringThickness = prim.thickness,
                                ringStyle = prim.style,
                                progressExpr = prim.progressExpr,
                                brightness = prim.brightness,
                                pulse = prim.pulse,
                                animate = prim.animate || layerMacro != null,
                                showIfCondition = layer.showIfCondition,
                                showIfRaw = showIfStr,
                                groupName = layer.groupName,
                                animationMacro = null,
                                isActiveInDryRun = isActive,
                                frames = layerFrames,
                                isCanvasEdited = rasterizeRing,
                                intervalMs = layerMacro?.frameIntervalMs ?: 100L
                            )
                        )
                    }

                    is LayerPrimitive.ArrowPrimitive,
                    is LayerPrimitive.IconPrimitive,
                    is LayerPrimitive.EmojiPrimitive,
                    is LayerPrimitive.ShaderPrimitive -> {
                        val origType = when (prim) {
                            is LayerPrimitive.ArrowPrimitive -> "ARROW"
                            is LayerPrimitive.IconPrimitive -> "ICON"
                            is LayerPrimitive.EmojiPrimitive -> "EMOJI"
                            is LayerPrimitive.ShaderPrimitive -> "SHADER"
                            else -> "BITMAP"
                        }
                        val rawExpr = when (prim) {
                            is LayerPrimitive.ArrowPrimitive -> prim.directionExpr.trim()
                            is LayerPrimitive.IconPrimitive -> prim.nameExpr.trim()
                            is LayerPrimitive.EmojiPrimitive -> prim.glyphExpr.trim()
                            is LayerPrimitive.ShaderPrimitive -> prim.typeExpr.trim()
                            else -> ""
                        }
                        val candidateVarName = rawExpr.removePrefix("$")
                        val matchingDecl = program.extractDeclarations.find { it.varName == candidateVarName }
                        val mappedStates = matchingDecl?.mapRules
                            ?.map { it.targetValue.trim() }
                            ?.filter { it.isNotBlank() }
                            ?.distinct()
                            .orEmpty()

                        val staticArrowDirs = setOf("left", "right", "straight", "up", "down", "u-turn", "uturn", "slight_left", "slight_right")
                        val isDynamicExpr = layer.showIfCondition == null && (
                            rawExpr.startsWith("$") ||
                                matchingDecl != null ||
                                (prim is LayerPrimitive.ArrowPrimitive &&
                                    rawExpr.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")) &&
                                    rawExpr.lowercase() !in staticArrowDirs)
                            )

                        val stateValuesToExpand: List<String> = when {
                            !isDynamicExpr -> emptyList()
                            mappedStates.isNotEmpty() -> mappedStates
                            prim is LayerPrimitive.ArrowPrimitive -> listOf("left", "right", "straight", "uturn")
                            else -> emptyList()
                        }

                        if (stateValuesToExpand.isNotEmpty()) {
                            val varName = candidateVarName.ifBlank { "state" }
                            val currentVal = (effectiveVars[varName] ?: stateValuesToExpand.first()).lowercase()
                            val groupLabel = layer.groupName ?: "${varName}_group"

                            for (stateVal in stateValuesToExpand) {
                                val layerTitle = if (prim is LayerPrimitive.ArrowPrimitive) {
                                    when (stateVal.lowercase()) {
                                        "left" -> "turn_left"
                                        "right" -> "turn_right"
                                        "straight" -> "straight"
                                        "uturn", "u-turn" -> "uturn"
                                        else -> "${layer.name}_${stateVal.replace(Regex("[^a-zA-Z0-9_]"), "_")}"
                                    }
                                } else {
                                    "${layer.name}_${stateVal.replace(Regex("[^a-zA-Z0-9_]"), "_")}"
                                }
                                val concretePrim: LayerPrimitive = when (prim) {
                                    is LayerPrimitive.ArrowPrimitive -> prim.copy(directionExpr = "\"$stateVal\"")
                                    is LayerPrimitive.IconPrimitive -> prim.copy(nameExpr = "\"$stateVal\"")
                                    is LayerPrimitive.EmojiPrimitive -> prim.copy(glyphExpr = "\"$stateVal\"")
                                    is LayerPrimitive.ShaderPrimitive -> prim.copy(typeExpr = "\"$stateVal\"")
                                    else -> prim
                                }
                                val syntheticDisplayLayer = DisplayLayer(
                                    name = layerTitle,
                                    primitives = listOf(concretePrim),
                                    showIfCondition = ShowIfCondition(leftVar = varName, operator = "==", rightValues = listOf(stateVal)),
                                    groupName = groupLabel,
                                    animationMacro = layerMacro
                                )
                                val stateFrames = GlyphFrameCompositor.renderSingleLayerFrames(
                                    layer = syntheticDisplayLayer,
                                    variables = effectiveVars + (varName to stateVal),
                                    splitVerticalLayout = splitVerticalLayout
                                )
                                val isStateActive = currentVal == stateVal.lowercase() ||
                                    (stateVal == "uturn" && currentVal == "u-turn") ||
                                    (stateVal == "left" && currentVal == "turn_left") ||
                                    (stateVal == "right" && currentVal == "turn_right")

                                visualLayers.add(
                                    DecompiledVisualLayer(
                                        name = layerTitle,
                                        primitiveType = if (stateFrames.size > 1) "SEQUENCE" else "BITMAP",
                                        originalPrimitiveType = origType,
                                        textContent = stateVal,
                                        arrowSize = (prim as? LayerPrimitive.ArrowPrimitive)?.size ?: 9,
                                        emojiScale = (prim as? LayerPrimitive.EmojiPrimitive)?.scalePercent ?: 75,
                                        emojiX = (prim as? LayerPrimitive.EmojiPrimitive)?.x ?: 12,
                                        yOffset = (prim as? LayerPrimitive.EmojiPrimitive)?.y ?: 16,
                                        brightness = when (prim) {
                                            is LayerPrimitive.ArrowPrimitive -> prim.brightness
                                            is LayerPrimitive.IconPrimitive -> prim.brightness
                                            is LayerPrimitive.EmojiPrimitive -> prim.brightness
                                            is LayerPrimitive.ShaderPrimitive -> prim.brightness
                                            else -> 255
                                        },
                                        pulse = when (prim) {
                                            is LayerPrimitive.ArrowPrimitive -> prim.pulse
                                            is LayerPrimitive.IconPrimitive -> prim.pulse
                                            is LayerPrimitive.EmojiPrimitive -> prim.pulse
                                            else -> false
                                        },
                                        bounce = when (prim) {
                                            is LayerPrimitive.ArrowPrimitive -> prim.bounce
                                            is LayerPrimitive.IconPrimitive -> prim.bounce
                                            is LayerPrimitive.EmojiPrimitive -> prim.bounce
                                            else -> false
                                        },
                                        animate = stateFrames.size > 1,
                                        showIfCondition = syntheticDisplayLayer.showIfCondition,
                                        showIfRaw = "\$$varName == \"$stateVal\"",
                                        groupName = groupLabel,
                                        animationMacro = null, // Sampled into discrete timeline frames
                                        isActiveInDryRun = isStateActive,
                                        frames = stateFrames,
                                        isCanvasEdited = true,
                                        intervalMs = layerMacro?.frameIntervalMs ?: 100L
                                    )
                                )
                            }
                        } else {
                            // Universal rasterization of procedural primitive into discrete 25x25 pixel frames
                            visualLayers.add(
                                DecompiledVisualLayer(
                                    name = layer.name,
                                    primitiveType = if (layerFrames.size > 1) "SEQUENCE" else "BITMAP",
                                    originalPrimitiveType = origType,
                                    textContent = rawExpr,
                                    arrowSize = (prim as? LayerPrimitive.ArrowPrimitive)?.size ?: 9,
                                    emojiScale = (prim as? LayerPrimitive.EmojiPrimitive)?.scalePercent ?: 75,
                                    emojiX = (prim as? LayerPrimitive.EmojiPrimitive)?.x ?: 12,
                                    yOffset = (prim as? LayerPrimitive.EmojiPrimitive)?.y ?: 16,
                                    brightness = when (prim) {
                                        is LayerPrimitive.ArrowPrimitive -> prim.brightness
                                        is LayerPrimitive.IconPrimitive -> prim.brightness
                                        is LayerPrimitive.EmojiPrimitive -> prim.brightness
                                        is LayerPrimitive.ShaderPrimitive -> prim.brightness
                                        else -> 255
                                    },
                                    pulse = when (prim) {
                                        is LayerPrimitive.ArrowPrimitive -> prim.pulse
                                        is LayerPrimitive.IconPrimitive -> prim.pulse
                                        is LayerPrimitive.EmojiPrimitive -> prim.pulse
                                        else -> false
                                    },
                                    bounce = when (prim) {
                                        is LayerPrimitive.ArrowPrimitive -> prim.bounce
                                        is LayerPrimitive.IconPrimitive -> prim.bounce
                                        is LayerPrimitive.EmojiPrimitive -> prim.bounce
                                        else -> false
                                    },
                                    animate = layerFrames.size > 1,
                                    showIfCondition = layer.showIfCondition,
                                    showIfRaw = showIfStr,
                                    groupName = layer.groupName,
                                    animationMacro = null, // Sampled into discrete timeline frames
                                    isActiveInDryRun = isActive,
                                    frames = layerFrames,
                                    isCanvasEdited = true,
                                    intervalMs = layerMacro?.frameIntervalMs ?: 100L
                                )
                            )
                        }
                    }

                    is LayerPrimitive.PlotPrimitive -> {
                        visualLayers.add(
                            DecompiledVisualLayer(
                                name = layer.name,
                                primitiveType = if (layerFrames.size > 1) "SEQUENCE" else "BITMAP",
                                originalPrimitiveType = "PLOT",
                                showIfCondition = layer.showIfCondition,
                                showIfRaw = showIfStr,
                                groupName = layer.groupName,
                                animationMacro = null,
                                isActiveInDryRun = isActive,
                                frames = layerFrames,
                                isCanvasEdited = true,
                                intervalMs = layerMacro?.frameIntervalMs ?: 100L
                            )
                        )
                    }

                    null -> Unit
                }
            }

            // Pick the active conditional graphic layer first (e.g., turn_right when $maneuver == "right"),
            // then any active graphic/bitmap/sequence layer, then any active layer, then first layer.
            val graphicTypes = setOf("BITMAP", "SEQUENCE", "ARROW", "EMOJI", "ICON", "PLOT")
            val initialSelectedLayer = visualLayers.firstOrNull {
                it.isActiveInDryRun && it.primitiveType in graphicTypes && (!it.groupName.isNullOrBlank() || it.showIfCondition != null)
            } ?: visualLayers.firstOrNull {
                it.isActiveInDryRun && it.primitiveType in graphicTypes
            } ?: visualLayers.firstOrNull {
                it.isActiveInDryRun
            } ?: visualLayers.firstOrNull()

            val initialFrames = initialSelectedLayer?.frames?.takeIf { it.isNotEmpty() }
                ?: compileResult.previewFrames.ifEmpty { listOf(GlyphFrame(id = "f_0", durationMs = 120L)) }

            val extractBlock = extractBlockFromSource(cleanScript, "EXTRACT")
            val behaviorBlock = extractBlockFromSource(cleanScript, "BEHAVIOR")

            return DecompiledStudioRule(
                ruleName = program.ruleName.ifBlank { "Decompiled Rule" },
                targetPackages = program.targetPackages.ifEmpty { listOf("*") },
                whenTriggerEvent = program.whenClause.triggerEvent,
                rawExtractBlock = extractBlock,
                rawBehaviorBlock = behaviorBlock,
                frames = initialFrames,
                visualLayers = visualLayers,
                initialSelectedLayerId = initialSelectedLayer?.id,
                dryRunVariables = effectiveVars,
                globalAlign = initialSelectedLayer?.align ?: detectedAlign,
                defaultIntervalMs = initialSelectedLayer?.intervalMs ?: detectedInterval,
                defaultPauseMs = initialSelectedLayer?.pauseMs ?: detectedPause,
                loop = initialSelectedLayer?.loop ?: detectedLoop,
                originalProgram = program,
                originalRuleId = ruleId
            )
        }

        // Graceful fallback for unparsed scripts
        val nameMatch = Regex("""RULE\s+"([^"]+)"""").find(cleanScript)
        val ruleName = nameMatch?.groupValues?.get(1) ?: "Custom Matrix Rule"
        val fallbackLayer = DecompiledVisualLayer(name = "canvas_base", primitiveType = "BITMAP")

        return DecompiledStudioRule(
            ruleName = ruleName,
            targetPackages = listOf("*"),
            whenTriggerEvent = "notification",
            rawExtractBlock = extractBlockFromSource(cleanScript, "EXTRACT"),
            rawBehaviorBlock = extractBlockFromSource(cleanScript, "BEHAVIOR"),
            frames = listOf(GlyphFrame(id = "f_0", durationMs = 100L)),
            visualLayers = listOf(fallbackLayer),
            initialSelectedLayerId = fallbackLayer.id,
            dryRunVariables = dryRunVariables,
            originalRuleId = ruleId
        )
    }

    /**
     * Synchronized Bidirectional Recompiler:
     * Generates valid GlyphScript v2 reflecting all per-layer visual modifications
     * (active layer frames, timeline sequences, layer properties) while preserving
     * all other layers, `group:`, `show_if:`, `EXTRACT`, and `BEHAVIOR` blocks.
     */
    fun recompile(
        rule: DecompiledStudioRule,
        frames: List<GlyphFrame>,
        layers: List<DecompiledVisualLayer>,
        activeLayerId: String? = null
    ): String {
        val displayLayersBuilder = StringBuilder()

        if (layers.isEmpty()) {
            val intervalMs = frames.firstOrNull()?.durationMs ?: rule.defaultIntervalMs
            val align = rule.globalAlign.ifBlank { "center" }
            displayLayersBuilder.append("  LAYER custom_matrix {\n")
            displayLayersBuilder.append(formatFramesAsPrimitiveDsl(frames, intervalMs, 0L, false, rule.loop, align, 255))
            displayLayersBuilder.append("\n  }")
        } else {
            var firstEmitted = true
            for (l in layers) {
                if (!l.isVisible) continue
                if (!firstEmitted) {
                    displayLayersBuilder.append("\n")
                }
                firstEmitted = false

                val safeLayerName = l.name.trim().replace(Regex("\\s+"), "_").ifBlank { "layer" }
                displayLayersBuilder.append("  LAYER $safeLayerName {\n")
                if (!l.groupName.isNullOrBlank()) {
                    displayLayersBuilder.append("    group: \"${l.groupName}\"\n")
                }
                val showIfStr = l.showIfRaw ?: l.showIfCondition?.toDslString()
                if (!showIfStr.isNullOrBlank()) {
                    displayLayersBuilder.append("    show_if: $showIfStr\n")
                }
                if (l.animationMacro != null) {
                    displayLayersBuilder.append("    animate: ${l.animationMacro.toDslString()}\n")
                }

                val layerFrames = if (l.id == activeLayerId && frames.isNotEmpty()) frames else l.frames
                val serializeAsPixelArt = l.isCanvasEdited ||
                    l.primitiveType in setOf("BITMAP", "SEQUENCE", "PLOT")

                if (serializeAsPixelArt) {
                    val intervalMs = layerFrames.firstOrNull()?.durationMs ?: l.intervalMs.coerceAtLeast(50L)
                    val align = l.align.ifBlank { rule.globalAlign.ifBlank { "center" } }
                    displayLayersBuilder.append(
                        formatFramesAsPrimitiveDsl(
                            frames = layerFrames,
                            intervalMs = intervalMs,
                            pauseMs = l.pauseMs,
                            holdLast = l.holdLast,
                            loop = l.loop,
                            align = align,
                            brightness = l.brightness,
                            forceSequence = l.primitiveType == "SEQUENCE" && layerFrames.size > 1
                        )
                    )
                    displayLayersBuilder.append("\n")
                } else {
                    when (l.primitiveType) {
                        "TEXT" -> {
                            val rawExpr = l.textContent.trim()
                            val isIdentifierOrVar = rawExpr.startsWith("$") ||
                                rawExpr.contains(".") ||
                                rawExpr.startsWith("\"") ||
                                rawExpr.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$"))
                            val content = if (isIdentifierOrVar) rawExpr else "\"$rawExpr\""
                            val suffixStr = if (l.suffix.isNotBlank()) ", suffix: \"${l.suffix}\"" else ""
                            val sizeStr = if (l.textSize.isNotBlank() && l.textSize != "3x5" && l.textSize != "auto") ", size: \"${l.textSize}\"" else ""
                            displayLayersBuilder.append(
                                "    text(content: $content$suffixStr$sizeStr, align: \"${l.align}\", y: ${l.yOffset}" +
                                    (if (l.animate) ", animate: true" else "") +
                                    (if (l.pulse) ", pulse: true" else "") +
                                    ")\n"
                            )
                        }
                        "RING" -> {
                            val progTrim = l.progressExpr.trim()
                            val progFormatted = if (progTrim.startsWith("$") || progTrim.endsWith("%") || progTrim.all { it.isDigit() }) {
                                progTrim
                            } else {
                                progTrim.ifBlank { "100" }
                            }
                            val progClause = if (progFormatted.isNotBlank() && progFormatted != "100") ", progress: $progFormatted" else ""
                            val styleClause = if (l.ringStyle.isNotBlank() && l.ringStyle != "clock_cw" && l.ringStyle != "solid") ", style: \"${l.ringStyle}\"" else ""
                            displayLayersBuilder.append(
                                "    ring(radius: ${l.ringRadius}, thickness: ${l.ringThickness}$progClause$styleClause" +
                                    (if (l.pulse) ", pulse: true" else "") +
                                    (if (l.animate) ", animate: true" else "") +
                                    ")\n"
                            )
                        }
                        "ARROW" -> {
                            val rawDir = l.textContent.trim().ifBlank { "straight" }
                            val isVar = rawDir.startsWith("$") ||
                                (rawDir.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")) &&
                                    rawDir.lowercase() !in setOf("left", "right", "straight", "up", "down", "u-turn", "uturn", "slight_left", "slight_right"))
                            val dirExpr = if (isVar || rawDir.startsWith("\"")) rawDir else "\"$rawDir\""
                            displayLayersBuilder.append(
                                "    arrow(direction: $dirExpr, size: ${l.arrowSize}" +
                                    (if (l.pulse) ", pulse: true" else "") +
                                    (if (l.bounce) ", bounce: true" else "") +
                                    ")\n"
                            )
                        }
                        "EMOJI" -> {
                            val cleanGlyph = l.textContent.trim().removeSurrounding("\"").ifBlank { "✦" }
                            displayLayersBuilder.append(
                                "    emoji(glyph: \"$cleanGlyph\", scale: ${l.emojiScale}%, y: ${l.yOffset}" +
                                    (if (l.pulse) ", pulse: true" else "") +
                                    (if (l.bounce) ", bounce: true" else "") +
                                    ")\n"
                            )
                        }
                        "ICON" -> {
                            val cleanIcon = l.textContent.trim().removeSurrounding("\"").ifBlank { "bell" }
                            displayLayersBuilder.append(
                                "    icon(name: \"$cleanIcon\"" +
                                    (if (l.pulse) ", pulse: true" else "") +
                                    (if (l.bounce) ", bounce: true" else "") +
                                    ")\n"
                            )
                        }
                        "SHADER" -> {
                            val cleanShader = l.textContent.trim().removeSurrounding("\"").ifBlank { "pulse" }
                            displayLayersBuilder.append("    shader(type: \"$cleanShader\")\n")
                        }
                    }
                }
                displayLayersBuilder.append("  }")
            }
        }

        val nonBlankPkgs = rule.targetPackages.map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { listOf("*") }
        val pkgTarget = if (nonBlankPkgs.size == 1) {
            "\"${nonBlankPkgs.first()}\""
        } else {
            "[" + nonBlankPkgs.joinToString(", ") { "\"$it\"" } + "]"
        }

        val referencedVars = LinkedHashSet<String>()
        val builtInVars = setOf(
            "notification.title", "notification.body", "notification.subtext", "notification.sub_text",
            "notification.progress", "notification.text", "battery.level", "battery.charging",
            "media.playing", "media.state"
        )
        val varRegex = Regex("""\$([a-zA-Z_][a-zA-Z0-9_]*)""")
        for (l in layers) {
            if (!l.isVisible) continue
            val showIfStr = l.showIfRaw ?: l.showIfCondition?.toDslString().orEmpty()
            varRegex.findAll(showIfStr).forEach { m ->
                val v = m.groupValues[1]
                if (v.lowercase() !in builtInVars) referencedVars.add(v)
            }
            if (!l.isCanvasEdited && l.primitiveType == "TEXT") {
                val rawT = l.textContent.trim()
                if (rawT.startsWith("$")) {
                    val v = rawT.removePrefix("$")
                    if (v.lowercase() !in builtInVars) referencedVars.add(v)
                }
            }
        }

        val baseExtract = if (rule.rawExtractBlock.isNotBlank()) {
            rule.rawExtractBlock.trim()
        } else {
            """EXTRACT {
  // Visual Studio rule
}"""
        }

        val missingVars = referencedVars.filter { v ->
            !Regex("""\bvar\s+${Regex.escape(v)}\b""").containsMatchIn(baseExtract)
        }
        val extractSection = if (missingVars.isEmpty()) {
            baseExtract
        } else {
            val synthesizedLines = missingVars.joinToString("\n") { v ->
                val defVal = (rule.dryRunVariables[v] ?: "default").replace("\"", "")
                "  var $v = title.regex(\".*\") DEFAULT \"$defVal\""
            }
            val closeIdx = baseExtract.lastIndexOf('}')
            if (closeIdx != -1) {
                baseExtract.substring(0, closeIdx).trimEnd() + "\n" + synthesizedLines + "\n}"
            } else {
                """EXTRACT {
$synthesizedLines
}"""
            }
        }

        val behaviorSection = if (rule.rawBehaviorBlock.isNotBlank()) {
            rule.rawBehaviorBlock
        } else {
            """BEHAVIOR {
  priority: 8
  mode: "ongoing"
  until: notification.dismissed
  fallback_clear: true
}"""
        }

        val triggerEvt = rule.whenTriggerEvent.trim().ifBlank { "notification" }
        val whenLine = if (rule.targetPackages.firstOrNull() == "*" &&
            (triggerEvt.startsWith("system.") || triggerEvt.startsWith("key.") || triggerEvt.contains("=="))
        ) {
            "WHEN $triggerEvt"
        } else {
            "WHEN $triggerEvt FROM $pkgTarget"
        }
        return """RULE "${rule.ruleName.ifBlank { "Custom Studio Rule" }}"
$whenLine
$extractSection
DISPLAY {
$displayLayersBuilder
}
$behaviorSection"""
    }

    /**
     * Converts active studio canvas/timeline frames directly into an explicit AST primitive
     * ([LayerPrimitive.SequencePrimitive] for multi-frame or [LayerPrimitive.BitmapPrimitive] for single-frame).
     */
    fun framesToAstPrimitive(
        frames: List<GlyphFrame>,
        align: String = "center",
        brightness: Int = 255,
        loop: Boolean = true,
        pauseMs: Long = 0L,
        holdLast: Boolean = false
    ): LayerPrimitive {
        val safeAlign = align.ifBlank { "center" }
        val nonEmptyFrames = frames.ifEmpty { listOf(GlyphFrame(id = "f_0", durationMs = 100L)) }
        return if (nonEmptyFrames.size > 1) {
            val intervalMs = nonEmptyFrames.first().durationMs.coerceIn(33L, 2000L)
            LayerPrimitive.SequencePrimitive(
                intervalMs = intervalMs,
                pauseMs = pauseMs,
                holdLast = holdLast,
                loop = loop,
                align = safeAlign,
                frames = nonEmptyFrames.map { f ->
                    LayerPrimitive.SequenceFrame(
                        asciiArt = pixelsToAscii(GlyphAperture.clampToPhysicalAperture(f.pixels)),
                        durationMs = f.durationMs.coerceIn(33L, 2000L)
                    )
                }
            )
        } else {
            val single = nonEmptyFrames.first()
            LayerPrimitive.BitmapPrimitive(
                asciiArt = pixelsToAscii(GlyphAperture.clampToPhysicalAperture(single.pixels)),
                align = safeAlign,
                brightness = brightness.coerceIn(1, 255)
            )
        }
    }

    /**
     * Mutates the parent [GlyphScriptRule] AST by replacing the targeted layer's primitive
     * with [updatedPrimitive] and synchronizing all visual layers.
     */
    fun mutateRuleAstWithLayerPrimitive(
        baseRule: DecompiledStudioRule,
        targetLayerName: String,
        updatedPrimitive: LayerPrimitive,
        updatedLayers: List<DecompiledVisualLayer>
    ): GlyphScriptRule {
        val cleanTargetName = targetLayerName.trim().ifBlank { "custom_matrix" }
        val existingProgram = baseRule.originalProgram
        val astDisplayLayers: List<DisplayLayer> = if (updatedLayers.isNotEmpty()) {
            updatedLayers.filter { it.isVisible }.map { vLayer ->
                if (vLayer.name.equals(cleanTargetName, ignoreCase = true)) {
                    DisplayLayer(
                        name = vLayer.name,
                        primitives = listOf(updatedPrimitive),
                        showIfCondition = vLayer.showIfCondition,
                        groupName = vLayer.groupName,
                        animationMacro = vLayer.animationMacro
                    )
                } else {
                    toDisplayLayer(vLayer)
                }
            }
        } else {
            listOf(
                DisplayLayer(
                    name = cleanTargetName,
                    primitives = listOf(updatedPrimitive)
                )
            )
        }

        val whenClause = existingProgram?.whenClause ?: WhenClause(
            triggerEvent = baseRule.whenTriggerEvent.ifBlank { "notification" },
            fromTarget = baseRule.targetPackages.firstOrNull() ?: "*",
            targetPackages = baseRule.targetPackages.ifEmpty { listOf("*") }
        )

        val mutatedProgram = GlyphScriptProgram(
            ruleName = baseRule.ruleName.ifBlank { "Custom Studio Rule" },
            whenClause = whenClause,
            extractDeclarations = existingProgram?.extractDeclarations ?: emptyList(),
            displayLayers = astDisplayLayers,
            behaviorClause = existingProgram?.behaviorClause ?: BehaviorClause(),
            sourceText = existingProgram?.sourceText.orEmpty()
        )

        return GlyphScriptRule(
            name = mutatedProgram.ruleName,
            targetPackages = mutatedProgram.targetPackages,
            whenClause = whenClause,
            program = mutatedProgram
        )
    }

    /**
     * Generates the updated raw GlyphScript v2 code string from a mutated [GlyphScriptRule] AST
     * and studio layer state.
     */
    fun decompile(
        updatedRule: GlyphScriptRule,
        studioRule: DecompiledStudioRule? = null,
        frames: List<GlyphFrame> = emptyList(),
        layers: List<DecompiledVisualLayer> = emptyList(),
        activeLayerId: String? = null
    ): String {
        if (studioRule != null) {
            return recompile(
                rule = studioRule.copy(
                    ruleName = updatedRule.name.ifBlank { studioRule.ruleName },
                    originalProgram = updatedRule.program ?: studioRule.originalProgram
                ),
                frames = frames.ifEmpty { studioRule.frames },
                layers = layers.ifEmpty { studioRule.visualLayers },
                activeLayerId = activeLayerId
            )
        }
        val prog = updatedRule.program
        val synthLayers = if (layers.isNotEmpty()) {
            layers
        } else {
            prog?.displayLayers?.map { dl ->
                val prim = dl.primitives.firstOrNull()
                when (prim) {
                    is LayerPrimitive.SequencePrimitive -> DecompiledVisualLayer(
                        name = dl.name,
                        primitiveType = "SEQUENCE",
                        align = prim.align,
                        intervalMs = prim.intervalMs,
                        pauseMs = prim.pauseMs,
                        holdLast = prim.holdLast,
                        loop = prim.loop,
                        showIfCondition = dl.showIfCondition,
                        groupName = dl.groupName,
                        animationMacro = dl.animationMacro,
                        frames = prim.frames.mapIndexed { idx, sf ->
                            GlyphFrame(
                                id = "f_$idx",
                                durationMs = prim.intervalMs,
                                pixels = asciiToPixels(sf.asciiArt)
                            )
                        },
                        isCanvasEdited = true
                    )
                    is LayerPrimitive.BitmapPrimitive -> DecompiledVisualLayer(
                        name = dl.name,
                        primitiveType = "BITMAP",
                        align = prim.align,
                        brightness = prim.brightness,
                        showIfCondition = dl.showIfCondition,
                        groupName = dl.groupName,
                        animationMacro = dl.animationMacro,
                        frames = listOf(
                            GlyphFrame(
                                id = "f_0",
                                durationMs = 100L,
                                pixels = asciiToPixels(prim.asciiArt, prim.brightness)
                            )
                        ),
                        isCanvasEdited = true
                    )
                    else -> DecompiledVisualLayer(
                        name = dl.name,
                        primitiveType = "BITMAP",
                        frames = frames.ifEmpty { listOf(GlyphFrame(id = "f_0", durationMs = 100L)) },
                        isCanvasEdited = true
                    )
                }
            }.orEmpty()
        }
        val effectiveStudioRule = DecompiledStudioRule(
            ruleName = updatedRule.name.ifBlank { prog?.ruleName ?: "Custom Studio Rule" },
            targetPackages = updatedRule.targetPackages.ifEmpty { listOf("*") },
            whenTriggerEvent = updatedRule.whenClause?.triggerEvent ?: "notification",
            rawExtractBlock = extractBlockFromSource(prog?.sourceText.orEmpty(), "EXTRACT"),
            rawBehaviorBlock = extractBlockFromSource(prog?.sourceText.orEmpty(), "BEHAVIOR"),
            frames = frames.ifEmpty { synthLayers.firstOrNull()?.frames.orEmpty() },
            visualLayers = synthLayers,
            originalProgram = prog
        )
        return recompile(
            rule = effectiveStudioRule,
            frames = effectiveStudioRule.frames,
            layers = synthLayers,
            activeLayerId = activeLayerId
        )
    }

    /**
     * Extracts the implied variable assignment `(varName, targetValue)` from a conditional layer's
     * `show_if` clause (e.g. `$maneuver == "left"` -> `"maneuver" to "left"`) so selecting or editing
     * that layer in Studio synchronizes Dry Run previews to display that exact conditional branch.
     */
    fun extractImpliedVariableAssignment(layer: DecompiledVisualLayer?): Pair<String, String>? {
        if (layer == null) return null
        val cond = layer.showIfCondition
        if (cond != null && cond.operator == "==" && cond.rightValues.isNotEmpty()) {
            val varName = cond.leftVar.removePrefix("$").trim()
            val targetVal = cond.rightValues.first().removeSurrounding("\"").removeSurrounding("'").trim()
            if (varName.isNotBlank() && targetVal.isNotBlank()) {
                return varName to targetVal
            }
        }
        val raw = (layer.showIfRaw ?: cond?.toDslString()).orEmpty().trim()
        if (raw.isNotBlank()) {
            val eqMatch = Regex("""^\$?([a-zA-Z_][a-zA-Z0-9_]*)\s*==\s*["']([^"']+)["']""").find(raw)
            if (eqMatch != null) {
                return eqMatch.groupValues[1] to eqMatch.groupValues[2]
            }
            val inMatch = Regex("""^\$?([a-zA-Z_][a-zA-Z0-9_]*)\s+in\s+\[\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(raw)
            if (inMatch != null) {
                return inMatch.groupValues[1] to inMatch.groupValues[2]
            }
        }
        return null
    }

    private fun formatFramesAsPrimitiveDsl(
        frames: List<GlyphFrame>,
        intervalMs: Long,
        pauseMs: Long,
        holdLast: Boolean,
        loop: Boolean,
        align: String,
        brightness: Int,
        forceSequence: Boolean = false
    ): String {
        return if (frames.size > 1 || forceSequence) {
            val framesCode = frames.joinToString("\n") { f ->
                """      frame ${"\"\"\""}
${pixelsToAscii(f.pixels)}
${"\"\"\""}"""
            }
            val pauseAttr = if (pauseMs > 0L) ", pause: ${pauseMs}ms" else ""
            val holdAttr = if (holdLast) ", hold_last: true" else ""
            """    sequence(interval: ${intervalMs}ms$pauseAttr$holdAttr, loop: $loop, align: "$align") {
$framesCode
    }"""
        } else {
            val single = frames.firstOrNull() ?: GlyphFrame()
            val brightAttr = if (brightness in 1..254) ", brightness: $brightness" else ""
            """    bitmap(align: "$align"$brightAttr) ${"\"\"\""}
${pixelsToAscii(single.pixels)}
${"\"\"\""}"""
        }
    }

    /**
     * Extracts a top-level block (e.g., `EXTRACT { ... }` or `BEHAVIOR { ... }`) using balanced brace matching
     * so nested blocks like `body.map { ... }` are preserved intact.
     */
    private fun extractBlockFromSource(source: String, blockHeader: String): String {
        val headerRegex = Regex("""\b$blockHeader\s*\{""", RegexOption.IGNORE_CASE)
        val match = headerRegex.find(source) ?: return ""
        val startIdx = match.range.first
        val openBraceIdx = match.range.last
        var depth = 0
        var inQuotes = false
        var i = openBraceIdx
        while (i < source.length) {
            val c = source[i]
            if (c == '"' && (i == 0 || source[i - 1] != '\\')) {
                inQuotes = !inQuotes
            } else if (!inQuotes) {
                if (c == '{') depth++
                else if (c == '}') {
                    depth--
                    if (depth == 0) {
                        return source.substring(startIdx, i + 1).trim()
                    }
                }
            }
            i++
        }
        return ""
    }
}

