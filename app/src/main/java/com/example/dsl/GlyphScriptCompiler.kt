package com.example.dsl

import android.content.Context
import android.content.pm.PackageManager
import com.example.domain.model.ActionType
import com.example.domain.model.ConditionType
import com.example.domain.model.MediaTriggerState
import com.example.domain.model.MeterStyle
import com.example.domain.model.TransitionCurve
import com.example.domain.model.TriggerRule

/**
 * GlyphScript v2 Lexer, Parser, Universal Dynamic Package Resolver, and Rule Compiler.
 */
object GlyphScriptCompiler {

    @Volatile
    private var cachedLabeledApps: List<Pair<String, String>>? = null

    private fun getOrLoadLabeledApps(pm: PackageManager): List<Pair<String, String>> {
        val existing = cachedLabeledApps
        if (existing != null) return existing
        val installedApps = runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())
        val loaded = installedApps.map { appInfo ->
            val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                .getOrDefault(appInfo.packageName)
            appInfo.packageName to label
        }
        cachedLabeledApps = loaded
        return loaded
    }

    data class PackageResolutionResult(
        val isResolved: Boolean,
        val packageName: String = "*",
        val appLabel: String = "Any App (*)",
        val errorMessage: String? = null
    )

    fun resolveTargetPackage(
        context: Context?,
        rawTarget: String?,
        lineNumber: Int
    ): PackageResolutionResult {
        val target = rawTarget?.trim().orEmpty()
        if (target.isEmpty() || target == "*" || target.equals("any", ignoreCase = true)) {
            return PackageResolutionResult(
                isResolved = true,
                packageName = "*",
                appLabel = "Any App (*)"
            )
        }

        if (context == null) {
            if (target.contains(".") || target.startsWith("pkg")) {
                return PackageResolutionResult(
                    isResolved = true,
                    packageName = target,
                    appLabel = target.substringAfterLast('.')
                )
            }
            return PackageResolutionResult(
                isResolved = false,
                errorMessage = "Compilation Error (Line $lineNumber): Target app \"$target\" is not installed on this device."
            )
        }

        val pm = context.packageManager
        val labeledApps = getOrLoadLabeledApps(pm)

        val exactPkgMatch = labeledApps.firstOrNull { (pkgName, _) ->
            pkgName.equals(target, ignoreCase = true)
        }
        if (exactPkgMatch != null) {
            return PackageResolutionResult(
                isResolved = true,
                packageName = exactPkgMatch.first,
                appLabel = exactPkgMatch.second
            )
        }

        val directAppInfo = runCatching { pm.getApplicationInfo(target, 0) }.getOrNull()
        if (directAppInfo != null) {
            val label = runCatching { pm.getApplicationLabel(directAppInfo).toString() }
                .getOrDefault(directAppInfo.packageName)
            return PackageResolutionResult(
                isResolved = true,
                packageName = directAppInfo.packageName,
                appLabel = label
            )
        }

        val exactLabelMatch = labeledApps.firstOrNull { (_, label) ->
            label.equals(target, ignoreCase = true)
        }
        if (exactLabelMatch != null) {
            return PackageResolutionResult(
                isResolved = true,
                packageName = exactLabelMatch.first,
                appLabel = exactLabelMatch.second
            )
        }

        val partialLabelMatch = labeledApps.firstOrNull { (pkgName, label) ->
            label.contains(target, ignoreCase = true) ||
                pkgName.substringAfterLast('.').equals(target, ignoreCase = true)
        }
        if (partialLabelMatch != null) {
            return PackageResolutionResult(
                isResolved = true,
                packageName = partialLabelMatch.first,
                appLabel = partialLabelMatch.second
            )
        }

        if (target.contains(".") || target.startsWith("pkg")) {
            return PackageResolutionResult(
                isResolved = true,
                packageName = target,
                appLabel = target.substringAfterLast('.')
            )
        }

        return PackageResolutionResult(
            isResolved = false,
            errorMessage = "Compilation Error (Line $lineNumber): Target app \"$target\" is not installed on this device."
        )
    }

    /**
     * Compiles a GlyphScript v2 source string into a [GlyphScriptCompileResult].
     */
    fun compile(
        scriptText: String,
        context: Context?,
        dryRunTitle: String = "",
        dryRunBody: String = "",
        dryRunSubText: String = "",
        dryRunProgress: Int? = 72
    ): GlyphScriptCompileResult {
        val (program, diagnostics) = parseProgramDiagnostics(scriptText)
        val errors = diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }

        if (errors.isNotEmpty() || program == null) {
            val fallbackDiag = if (diagnostics.isEmpty()) {
                listOf(GlyphScriptDiagnostic("E101", 1, 1, "Invalid or empty GlyphScript v2 input."))
            } else diagnostics
            return GlyphScriptCompileResult.Failure(fallbackDiag)
        }

        val targetPackages = program.whenClause.targetPackages
        val fromTarget = program.whenClause.fromTarget
        val pkgResolution = if (targetPackages.size > 1) {
            PackageResolutionResult(
                isResolved = true,
                packageName = targetPackages.joinToString(","),
                appLabel = "Multi-App (${targetPackages.size} apps)"
            )
        } else if (fromTarget != null) {
            resolveTargetPackage(
                context = context,
                rawTarget = fromTarget,
                lineNumber = program.whenClause.fromLineNumber
            )
        } else {
            PackageResolutionResult(isResolved = true, packageName = "*", appLabel = "Any App (*)")
        }

        if (!pkgResolution.isResolved) {
            val msg = pkgResolution.errorMessage
                ?: "Compilation Error (Line ${program.whenClause.fromLineNumber}): Target app \"$fromTarget\" is not installed on this device."
            val targetDiag = GlyphScriptDiagnostic("E101", program.whenClause.fromLineNumber, program.whenClause.fromColumnNumber, msg)
            return GlyphScriptCompileResult.Failure(listOf(targetDiag))
        }

        val extractedVars = GlyphFrameCompositor.evaluateExtractions(
            declarations = program.extractDeclarations,
            title = dryRunTitle,
            body = dryRunBody,
            subText = dryRunSubText,
            progressPercent = dryRunProgress,
            logFallbackWarnings = false
        )

        val previewFrames = GlyphFrameCompositor.compositeProgramFrames(
            program = program,
            variables = extractedVars
        )

        val conditionType = mapTriggerEventToConditionType(program.whenClause.triggerEvent)
        val untilLower = program.behaviorClause.untilLifecycle.lowercase()
        val holdIndefinitely = !untilLower.startsWith("duration")
        val durationMs = parseDurationMs(untilLower, program.behaviorClause.durationMs)
        val priority = program.behaviorClause.priority.coerceIn(1, 10)

        val macroInterval = program.displayLayers.mapNotNull { it.animationMacro?.frameIntervalMs }.minOrNull()
        val seqInterval = program.displayLayers.flatMap { it.primitives }
            .filterIsInstance<LayerPrimitive.SequencePrimitive>()
            .map { it.intervalMs }
            .minOrNull()
        val effectiveIntervalMs = (macroInterval ?: seqInterval ?: 80L).coerceIn(33L, 1000L)

        val compiledRule = TriggerRule(
            id = 0L,
            name = program.ruleName,
            isEnabled = true,
            priority = priority,
            cooldownMs = 500L,
            conditionType = conditionType,
            targetPackageName = if (targetPackages.isNotEmpty()) targetPackages.joinToString(",") else pkgResolution.packageName,
            targetPackages = if (targetPackages.isNotEmpty()) targetPackages else listOf(pkgResolution.packageName),
            targetAppLabel = pkgResolution.appLabel,
            essentialPriorityOverride = priority >= 10,
            requireProgress = program.extractDeclarations.any {
                it.sourceField.equals("progress_percent", ignoreCase = true) && it.defaultValue.isBlank()
            },
            mediaTriggerState = MediaTriggerState.PLAYING,
            actionType = ActionType.GLYPH_SCRIPT_COMPOSITOR,
            targetAnimationId = 0L,
            targetAnimationName = "GlyphScript // ${program.ruleName}",
            holdUntilConditionClears = holdIndefinitely,
            overrideDurationMs = durationMs,
            overrideIntervalMs = effectiveIntervalMs,
            overrideLoopCount = 0,
            overrideTransitionCurve = TransitionCurve.STEP_FADE,
            meterStyle = MeterStyle.PERIMETER_RING,
            meterPeakBrightness = 255,
            glyphScriptSource = scriptText.trim(),
            taskMode = program.behaviorClause.mode.value,
            intrudeDurationMs = program.behaviorClause.intrudeDurationMs
        )

        val warnings = diagnostics.filter { it.severity == DiagnosticSeverity.WARNING }

        return GlyphScriptCompileResult.Success(
            program = program,
            compiledRule = compiledRule,
            resolvedPackageName = pkgResolution.packageName,
            resolvedAppLabel = pkgResolution.appLabel,
            previewFrames = previewFrames,
            extractedPreviewVariables = extractedVars,
            warnings = warnings
        )
    }

    fun parseProgramOnly(rawText: String): Pair<GlyphScriptProgram?, GlyphScriptCompileError?> {
        val (program, diagnostics) = parseProgramDiagnostics(rawText)
        val firstError = diagnostics.firstOrNull { it.severity == DiagnosticSeverity.ERROR }
        return if (firstError != null || program == null) {
            val err = firstError?.let {
                GlyphScriptCompileError(it.line, it.column, it.message, it.formattedMessage)
            } ?: GlyphScriptCompileError(1, 1, "Compilation failed.")
            null to err
        } else {
            program to null
        }
    }

    fun parseProgramDiagnostics(rawText: String): Pair<GlyphScriptProgram?, List<GlyphScriptDiagnostic>> {
        val diagnostics = mutableListOf<GlyphScriptDiagnostic>()
        val rawLines = rawText.lines()
        if (rawText.isBlank()) {
            diagnostics.add(GlyphScriptDiagnostic("E101", 1, 1, "Script is empty. Expected RULE \"<Rule Name>\"."))
            return null to diagnostics
        }

        var ruleName: String? = null
        var whenClause: WhenClause? = null
        val extractDecls = mutableListOf<ExtractDeclaration>()
        val displayLayers = mutableListOf<DisplayLayer>()
        var priority = 8
        var untilLifecycle = "notification.dismissed"
        var durationMs = 5000L
        var fallbackClear = true
        var mode = TaskMode.ONGOING
        var intrudeDurationMs = 4000L

        var currentSection = "ROOT"
        var activeLayerName: String? = null
        var activeLayerGroup: String? = null
        var activeLayerLine = 1
        var activeLayerCol = 1
        var activeLayerShowIf: ShowIfCondition? = null
        var activeLayerAnimationMacro: LayerAnimationMacro? = null
        val activeLayerPrimitives = mutableListOf<LayerPrimitive>()

        var index = 0
        while (index < rawLines.size) {
            val lineNum = index + 1
            val rawLine = rawLines[index]
            val trimmed = stripCommentsAndFences(rawLine).trim()
            val colNum = (rawLine.indexOf(trimmed).coerceAtLeast(0)) + 1

            if (trimmed.isEmpty()) {
                index++
                continue
            }

            if (trimmed == "}") {
                when (currentSection) {
                    "LAYER" -> {
                        val layerName = activeLayerName ?: "layer_${displayLayers.size + 1}"
                        if (activeLayerPrimitives.isNotEmpty()) {
                            displayLayers.add(
                                DisplayLayer(
                                    name = layerName,
                                    primitives = activeLayerPrimitives.toList(),
                                    showIfCondition = activeLayerShowIf,
                                    groupName = activeLayerGroup,
                                    animationMacro = activeLayerAnimationMacro,
                                    lineNumber = activeLayerLine,
                                    columnNumber = activeLayerCol
                                )
                            )
                        }
                        activeLayerName = null
                        activeLayerGroup = null
                        activeLayerShowIf = null
                        activeLayerAnimationMacro = null
                        activeLayerPrimitives.clear()
                        currentSection = "DISPLAY"
                    }
                    "EXTRACT", "DISPLAY", "BEHAVIOR" -> {
                        currentSection = "ROOT"
                    }
                    else -> {
                        diagnostics.add(GlyphScriptDiagnostic("E101", lineNum, rawLine.indexOf('}') + 1, "Unexpected closing brace '}'."))
                    }
                }
                index++
                continue
            }

            var nextIndex = index + 1

            when (currentSection) {
                "ROOT" -> {
                    when {
                        trimmed.startsWith("RULE", ignoreCase = true) -> {
                            val ruleMatch = Regex("""^RULE\s+"([^"]+)"$""", RegexOption.IGNORE_CASE).matchEntire(trimmed)
                            if (ruleMatch != null) {
                                ruleName = ruleMatch.groupValues[1].trim()
                            } else {
                                diagnostics.add(GlyphScriptDiagnostic("E101", lineNum, colNum, "Malformed RULE header. Expected: RULE \"<Rule Name>\""))
                            }
                        }
                        trimmed.startsWith("WHEN", ignoreCase = true) -> {
                            val afterWhen = trimmed.substring(4).trim()
                            val (parsedWhenClause, updatedIdx) = parseWhenClause(
                                rawAfterWhen = afterWhen,
                                lineNum = lineNum,
                                colNum = colNum,
                                rawLines = rawLines,
                                startIndex = index,
                                diagnostics = diagnostics
                            )
                            whenClause = parsedWhenClause
                            index = updatedIdx
                            nextIndex = index + 1
                        }
                        trimmed.matches(Regex("""^EXTRACT\s*\{?$""", RegexOption.IGNORE_CASE)) -> {
                            currentSection = "EXTRACT"
                        }
                        trimmed.matches(Regex("""^DISPLAY\s*\{?$""", RegexOption.IGNORE_CASE)) -> {
                            currentSection = "DISPLAY"
                        }
                        trimmed.matches(Regex("""^BEHAVIOR\s*\{?$""", RegexOption.IGNORE_CASE)) -> {
                            currentSection = "BEHAVIOR"
                        }
                        trimmed == "{" -> {}
                        else -> {
                            diagnostics.add(GlyphScriptDiagnostic("E101", lineNum, colNum, "Unrecognized top-level statement \"$trimmed\"."))
                        }
                    }
                }

                "EXTRACT" -> {
                    if (trimmed == "{") {
                        index++
                        continue
                    }
                    if (trimmed.contains(".map", ignoreCase = true)) {
                        val varName = trimmed.substringBefore("=").removePrefix("var").trim()
                        val rawSourceField = trimmed.substringAfter("=").substringBefore(".map").trim()
                        val sourceField = rawSourceField.substringAfterLast('.')
                        val (decl, advancedIdx, mapDiag) = parseExtractMapBlockWithDiagnostics(
                            varName = varName,
                            sourceField = sourceField,
                            lineNum = lineNum,
                            colNum = colNum,
                            rawLines = rawLines,
                            startIndex = index
                        )
                        diagnostics.addAll(mapDiag)
                        if (decl != null) extractDecls.add(decl)
                        nextIndex = advancedIdx + 1
                    } else if (trimmed.startsWith("var ", ignoreCase = true)) {
                        val (decl, regexDiag) = parseExtractRegexDeclaration(trimmed, lineNum, colNum, rawLine)
                        diagnostics.addAll(regexDiag)
                        if (decl != null) extractDecls.add(decl)
                    } else {
                        diagnostics.add(GlyphScriptDiagnostic("E101", lineNum, colNum, "Invalid EXTRACT statement \"$trimmed\"."))
                    }
                }

                "DISPLAY" -> {
                    if (trimmed == "{") {
                        index++
                        continue
                    }
                    if (trimmed.startsWith("LAYER", ignoreCase = true)) {
                        val rawName = trimmed.substring(5).substringBefore("{").trim().removeSurrounding("\"")
                        activeLayerName = rawName.ifBlank { "layer_${displayLayers.size + 1}" }
                        activeLayerGroup = null
                        activeLayerLine = lineNum
                        activeLayerCol = colNum
                        activeLayerShowIf = null
                        activeLayerAnimationMacro = null
                        activeLayerPrimitives.clear()
                        currentSection = "LAYER"
                    } else {
                        diagnostics.add(GlyphScriptDiagnostic("E101", lineNum, colNum, "Expected LAYER declaration inside DISPLAY block."))
                    }
                }

                "LAYER" -> {
                    if (trimmed == "{") {
                        index++
                        continue
                    }
                    if (trimmed.startsWith("LAYER", ignoreCase = true)) {
                        if (activeLayerPrimitives.isNotEmpty()) {
                            displayLayers.add(
                                DisplayLayer(
                                    name = activeLayerName ?: "layer_${displayLayers.size + 1}",
                                    primitives = activeLayerPrimitives.toList(),
                                    showIfCondition = activeLayerShowIf,
                                    groupName = activeLayerGroup,
                                    animationMacro = activeLayerAnimationMacro,
                                    lineNumber = activeLayerLine,
                                    columnNumber = activeLayerCol
                                )
                            )
                            activeLayerPrimitives.clear()
                        }
                        val rawName = trimmed.substring(5).substringBefore("{").trim().removeSurrounding("\"")
                        activeLayerName = rawName.ifBlank { "layer_${displayLayers.size + 1}" }
                        activeLayerGroup = null
                        activeLayerLine = lineNum
                        activeLayerCol = colNum
                        activeLayerShowIf = null
                        activeLayerAnimationMacro = null
                        currentSection = "LAYER"
                    } else if (trimmed.startsWith("group:", ignoreCase = true)) {
                        activeLayerGroup = trimmed.substringAfter("group:").trim().removeSurrounding("\"")
                    } else if (trimmed.startsWith("group ", ignoreCase = true)) {
                        diagnostics.add(GlyphScriptDiagnostic("E101", lineNum, colNum, "'group <name>' is removed; use 'group: <name>'"))
                        activeLayerGroup = trimmed.substringAfter("group").trim().removeSurrounding("\"")
                    } else if (trimmed.startsWith("show_if:", ignoreCase = true)) {
                        val exprStr = trimmed.substringAfter("show_if:").trim()
                        activeLayerShowIf = parseShowIfCondition(exprStr)
                    } else if (trimmed.startsWith("show_if ", ignoreCase = true)) {
                        diagnostics.add(GlyphScriptDiagnostic("E101", lineNum, colNum, "'show_if <expr>' is removed; use 'show_if: <expr>'"))
                        val exprStr = trimmed.substringAfter("show_if").trim()
                        activeLayerShowIf = parseShowIfCondition(exprStr)
                    } else if (trimmed.startsWith("animate:", ignoreCase = true) || trimmed.startsWith("animate ", ignoreCase = true)) {
                        val macroExpr = if (trimmed.startsWith("animate:", ignoreCase = true)) {
                            trimmed.substringAfter("animate:").trim()
                        } else {
                            trimmed.substringAfter("animate").trim()
                        }
                        val (macro, macroDiag) = parseAnimationMacro(macroExpr, lineNum, colNum)
                        if (macroDiag != null) diagnostics.add(macroDiag)
                        if (macro != null) activeLayerAnimationMacro = macro
                    } else if (trimmed.startsWith("border(", ignoreCase = true)) {
                        diagnostics.add(GlyphScriptDiagnostic("E101", lineNum, colNum, "'border(...)' is removed; use 'ring(...)'"))
                        val replaced = "ring(" + trimmed.substringAfter("border(")
                        val (prim, advancedIdx, primDiag) = parseAnyPrimitiveWithDiagnostics(replaced, lineNum, colNum, rawLine, rawLines, index)
                        diagnostics.addAll(primDiag)
                        if (prim != null) activeLayerPrimitives.add(prim)
                        nextIndex = advancedIdx + 1
                    } else {
                        val (prim, advancedIdx, primDiag) = parseAnyPrimitiveWithDiagnostics(trimmed, lineNum, colNum, rawLine, rawLines, index)
                        diagnostics.addAll(primDiag)
                        if (prim != null) activeLayerPrimitives.add(prim)
                        nextIndex = advancedIdx + 1
                    }
                }

                "BEHAVIOR" -> {
                    if (trimmed == "{") {
                        index++
                        continue
                    }
                    when {
                        trimmed.startsWith("priority:", ignoreCase = true) -> {
                            val valStr = trimmed.substringAfter("priority:").trim()
                            val p = valStr.toIntOrNull()
                            if (p == null || p !in 1..10) {
                                diagnostics.add(GlyphScriptDiagnostic("E101", lineNum, colNum, "Invalid priority value \"$valStr\". Expected integer 1..10."))
                            } else {
                                priority = p
                            }
                        }
                        trimmed.startsWith("PRIORITY", ignoreCase = true) -> {
                            val valStr = trimmed.substringAfter("PRIORITY").trim()
                            diagnostics.add(GlyphScriptDiagnostic("E101", lineNum, colNum, "'PRIORITY $valStr' is removed; use 'priority: $valStr'"))
                            val p = valStr.toIntOrNull()
                            if (p != null && p in 1..10) priority = p
                        }
                        trimmed.startsWith("mode:", ignoreCase = true) -> {
                            val valStr = trimmed.substringAfter("mode:").trim().removeSurrounding("\"").lowercase()
                            when (valStr) {
                                "transient" -> mode = TaskMode.TRANSIENT
                                "ongoing" -> mode = TaskMode.ONGOING
                                else -> diagnostics.add(GlyphScriptDiagnostic("E101", lineNum, colNum, "Invalid mode \"$valStr\". Expected \"ongoing\" or \"transient\"."))
                            }
                        }
                        trimmed.startsWith("MODE", ignoreCase = true) -> {
                            val valStr = trimmed.substringAfter("MODE").trim()
                            diagnostics.add(GlyphScriptDiagnostic("E101", lineNum, colNum, "'MODE $valStr' is removed; use 'mode: $valStr'"))
                        }
                        trimmed.startsWith("intrude_duration:", ignoreCase = true) -> {
                            val durStr = trimmed.substringAfter("intrude_duration:").trim()
                            intrudeDurationMs = parseDurationMs(durStr.lowercase(), 4000L)
                        }
                        trimmed.startsWith("INTRUDE_DURATION", ignoreCase = true) -> {
                            val durStr = trimmed.substringAfter("INTRUDE_DURATION").trim()
                            diagnostics.add(GlyphScriptDiagnostic("E101", lineNum, colNum, "'INTRUDE_DURATION $durStr' is removed; use 'intrude_duration: $durStr'"))
                            intrudeDurationMs = parseDurationMs(durStr.lowercase(), 4000L)
                        }
                        trimmed.startsWith("until:", ignoreCase = true) -> {
                            untilLifecycle = trimmed.substringAfter("until:").trim().removeSurrounding("\"").removeSurrounding("'")
                            durationMs = parseDurationMs(untilLifecycle.lowercase(), 5000L)
                            if (untilLifecycle.lowercase().startsWith("duration")) {
                                mode = TaskMode.TRANSIENT
                                intrudeDurationMs = durationMs
                            }
                        }
                        trimmed.startsWith("UNTIL", ignoreCase = true) -> {
                            val untilStr = trimmed.substringAfter("UNTIL").trim()
                            diagnostics.add(GlyphScriptDiagnostic("E101", lineNum, colNum, "'UNTIL $untilStr' is removed; use 'until: $untilStr'"))
                            untilLifecycle = untilStr
                            durationMs = parseDurationMs(untilLifecycle.lowercase(), 5000L)
                        }
                        trimmed.startsWith("fallback_clear:", ignoreCase = true) -> {
                            val valStr = trimmed.substringAfter("fallback_clear:").trim().lowercase()
                            fallbackClear = valStr == "true"
                        }
                        trimmed.startsWith("FALLBACK_CLEAR", ignoreCase = true) -> {
                            val valStr = trimmed.substringAfter("FALLBACK_CLEAR").trim()
                            diagnostics.add(GlyphScriptDiagnostic("E101", lineNum, colNum, "'FALLBACK_CLEAR $valStr' is removed; use 'fallback_clear: $valStr'"))
                            fallbackClear = valStr.lowercase() == "true"
                        }
                        else -> {
                            diagnostics.add(GlyphScriptDiagnostic("E101", lineNum, colNum, "Unknown BEHAVIOR property \"$trimmed\"."))
                        }
                    }
                }
            }
            index = nextIndex
        }

        if (currentSection == "LAYER" && activeLayerPrimitives.isNotEmpty()) {
            displayLayers.add(
                DisplayLayer(
                    name = activeLayerName ?: "layer_${displayLayers.size + 1}",
                    primitives = activeLayerPrimitives.toList(),
                    showIfCondition = activeLayerShowIf,
                    groupName = activeLayerGroup,
                    animationMacro = activeLayerAnimationMacro,
                    lineNumber = activeLayerLine,
                    columnNumber = activeLayerCol
                )
            )
        }

        // Check variable scoping (E102) across all layers
        val declaredVars = extractDecls.map { it.varName }.toSet()
        val builtInVars = setOf(
            "notification.title", "notification.body", "notification.subtext", "notification.sub_text", "notification.progress", "notification.text",
            "battery.level", "battery.charging", "media.playing", "media.state"
        )

        for (layer in displayLayers) {
            val varsInLayer = extractVariableReferencesInLayer(layer, declaredVars)
            for ((vName, line, col) in varsInLayer) {
                val cleanV = vName.removePrefix("$").lowercase()
                if (cleanV !in declaredVars.map { it.lowercase() } && cleanV !in builtInVars) {
                    diagnostics.add(GlyphScriptDiagnostic("E102", line, col, "Undeclared variable '\$$vName'"))
                }
            }
        }

        // Check W203 (Unreachable layer after group fallback)
        val groupLayers = displayLayers.filter { it.groupName != null }.groupBy { it.groupName!! }
        for ((gName, layersInGroup) in groupLayers) {
            var fallbackFound = false
            for (l in layersInGroup) {
                if (fallbackFound) {
                    diagnostics.add(GlyphScriptDiagnostic("W203", l.lineNumber, l.columnNumber, "Unreachable layer '${l.name}' after a group fallback in group '$gName'", severity = DiagnosticSeverity.WARNING))
                } else if (l.showIfCondition == null) {
                    fallbackFound = true
                }
            }
        }

        // Check W201 / W202 static warnings
        for (layer in displayLayers) {
            for (prim in layer.primitives) {
                if (prim is LayerPrimitive.TextPrimitive) {
                    if (!prim.contentExpr.contains("$") && prim.contentExpr.lowercase() !in declaredVars.map { it.lowercase() }) {
                        val renderW = CanvasTextRasterizer.calculateTextRenderWidth(prim.contentExpr.length)
                        val safeW = CanvasTextRasterizer.calculateSafeWidth(prim.y)
                        if (renderW > safeW) {
                            diagnostics.add(GlyphScriptDiagnostic("W202", layer.lineNumber, layer.columnNumber, "Static text \"${prim.contentExpr}\" (width $renderW) exceeds safe width ($safeW) for row ${prim.y}", severity = DiagnosticSeverity.WARNING))
                        }
                    }
                }
            }
        }

        val hasErrors = diagnostics.any { it.severity == DiagnosticSeverity.ERROR }
        if (hasErrors || ruleName.isNullOrBlank() || whenClause == null || displayLayers.isEmpty()) {
            if (ruleName.isNullOrBlank() && !hasErrors) {
                diagnostics.add(GlyphScriptDiagnostic("E101", 1, 1, "Missing RULE \"<Rule Name>\" declaration."))
            }
            if (whenClause == null && !hasErrors) {
                diagnostics.add(GlyphScriptDiagnostic("E101", 1, 1, "Missing WHEN clause."))
            }
            if (displayLayers.isEmpty() && !hasErrors) {
                diagnostics.add(GlyphScriptDiagnostic("E101", rawLines.size, 1, "DISPLAY block must contain at least one LAYER."))
            }
            return null to diagnostics
        }

        val program = GlyphScriptProgram(
            ruleName = ruleName,
            whenClause = whenClause,
            extractDeclarations = extractDecls,
            displayLayers = displayLayers,
            behaviorClause = BehaviorClause(
                priority = priority,
                mode = mode,
                intrudeDurationMs = intrudeDurationMs,
                untilLifecycle = untilLifecycle,
                durationMs = durationMs,
                fallbackClear = fallbackClear
            ),
            sourceText = rawText.trim()
        )

        return program to diagnostics
    }

    private fun parseExtractRegexDeclaration(
        line: String,
        lineNum: Int,
        colNum: Int,
        rawLine: String
    ): Pair<ExtractDeclaration?, List<GlyphScriptDiagnostic>> {
        val diags = mutableListOf<GlyphScriptDiagnostic>()
        val varName = line.substringBefore("=").removePrefix("var").trim()
        val rightSide = line.substringAfter("=").trim()

        val defaultIdx = rightSide.indexOf("DEFAULT", ignoreCase = true)
        val sourceFieldRaw = if (rightSide.contains(".regex")) {
            rightSide.substringBefore(".regex").trim()
        } else if (defaultIdx != -1) {
            rightSide.substring(0, defaultIdx).trim()
        } else {
            rightSide.trim()
        }
        val sourceField = sourceFieldRaw.substringAfterLast('.').trim()
        val validSources = setOf("title", "body", "subtext", "sub_text", "progress_percent", "progress")
        if (sourceField.lowercase() !in validSources) {
            diags.add(GlyphScriptDiagnostic("E101", lineNum, colNum, "Invalid extraction field \"$sourceField\". Expected title, body, subText, or progress_percent."))
        }

        val hasDefault = rightSide.contains("DEFAULT", ignoreCase = true)
        if (!hasDefault) {
            diags.add(GlyphScriptDiagnostic("E105", lineNum, colNum, "Missing mandatory DEFAULT fallback for variable '$varName'"))
        }

        val defaultVal = if (hasDefault) {
            val idx = rightSide.indexOf("DEFAULT", ignoreCase = true)
            rightSide.substring(idx + 7).trim().removeSurrounding("\"").removeSurrounding("'")
        } else ""

        val beforeDefault = if (hasDefault) {
            val idx = rightSide.indexOf("DEFAULT", ignoreCase = true)
            rightSide.substring(0, idx).trim()
        } else rightSide.trim()

        val regexPart = if (beforeDefault.contains(".regex(")) {
            val afterRegex = beforeDefault.substringAfter(".regex(")
            if (afterRegex.endsWith(")")) afterRegex.substring(0, afterRegex.length - 1) else afterRegex
        } else ""

        val patternMatch = Regex(""""((?:[^"\\]|\\.)*)"""").find(regexPart)
        val pattern = patternMatch?.groupValues?.get(1)?.replace("""\\""", """\""") ?: ".*"

        val groupMatch = Regex("""group\s*:\s*(\d+)""", RegexOption.IGNORE_CASE).find(regexPart)
        val requestedGroup = groupMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val flagsMatch = Regex("""flags\s*:\s*"([^"]*)"""", RegexOption.IGNORE_CASE).find(regexPart)
        val flags = flagsMatch?.groupValues?.get(1) ?: ""

        val compiledRegex = runCatching {
            var options = emptySet<RegexOption>()
            if (flags.contains("i", ignoreCase = true)) options = options + RegexOption.IGNORE_CASE
            Regex(pattern, options)
        }.getOrNull()

        if (compiledRegex != null) {
            val captureGroupCount = java.util.regex.Pattern.compile(pattern).matcher("").groupCount()
            if (requestedGroup > captureGroupCount) {
                diags.add(GlyphScriptDiagnostic("E101", lineNum, colNum, "Capture group index $requestedGroup exceeds pattern capture group count ($captureGroupCount)"))
            }
        }

        val decl = ExtractDeclaration(
            varName = varName,
            sourceField = sourceField,
            regexPattern = pattern,
            groupIndex = requestedGroup,
            regexFlags = flags,
            defaultValue = defaultVal,
            lineNumber = lineNum,
            columnNumber = colNum
        )

        return decl to diags
    }

    private fun parseExtractMapBlockWithDiagnostics(
        varName: String,
        sourceField: String,
        lineNum: Int,
        colNum: Int,
        rawLines: List<String>,
        startIndex: Int
    ): Triple<ExtractDeclaration?, Int, List<GlyphScriptDiagnostic>> {
        val diags = mutableListOf<GlyphScriptDiagnostic>()
        val (decl, advancedIdx) = parseExtractMapBlock(varName, sourceField, lineNum, rawLines, startIndex)

        if (decl != null) {
            val hasDefault = decl.mapRules.any { it.conditionType == MapConditionType.DEFAULT }
            if (!hasDefault) {
                diags.add(GlyphScriptDiagnostic("E105", lineNum, colNum, "Missing mandatory DEFAULT fallback for variable '$varName'"))
            }
        }

        return Triple(decl, advancedIdx, diags)
    }

    private fun extractVariableReferencesInLayer(
        layer: DisplayLayer,
        declaredVars: Set<String> = emptySet()
    ): List<Triple<String, Int, Int>> {
        val result = mutableListOf<Triple<String, Int, Int>>()

        layer.showIfCondition?.let { cond ->
            val vars = extractVarsFromExpr(cond.leftVar, declaredVars)
            for (v in vars) {
                result.add(Triple(v, layer.lineNumber, layer.columnNumber))
            }
        }

        for (p in layer.primitives) {
            when (p) {
                is LayerPrimitive.TextPrimitive -> {
                    for (v in extractVarsFromExpr(p.contentExpr, declaredVars)) {
                        result.add(Triple(v, layer.lineNumber, layer.columnNumber))
                    }
                }
                is LayerPrimitive.ArrowPrimitive -> {
                    for (v in extractVarsFromExpr(p.directionExpr, declaredVars)) {
                        result.add(Triple(v, layer.lineNumber, layer.columnNumber))
                    }
                }
                is LayerPrimitive.EmojiPrimitive -> {
                    for (v in extractVarsFromExpr(p.glyphExpr, declaredVars)) {
                        result.add(Triple(v, layer.lineNumber, layer.columnNumber))
                    }
                }
                is LayerPrimitive.RingPrimitive -> {
                    for (v in extractVarsFromExpr(p.progressExpr, declaredVars)) {
                        result.add(Triple(v, layer.lineNumber, layer.columnNumber))
                    }
                }
                is LayerPrimitive.IconPrimitive -> {
                    for (v in extractVarsFromExpr(p.nameExpr, declaredVars)) {
                        result.add(Triple(v, layer.lineNumber, layer.columnNumber))
                    }
                }
                is LayerPrimitive.ShaderPrimitive -> {
                    for (v in extractVarsFromExpr(p.typeExpr, declaredVars)) {
                        result.add(Triple(v, layer.lineNumber, layer.columnNumber))
                    }
                }
                is LayerPrimitive.BitmapPrimitive,
                is LayerPrimitive.SequencePrimitive,
                is LayerPrimitive.PlotPrimitive -> {}
            }
        }

        return result
    }

    private fun extractVarsFromExpr(expr: String, declaredVars: Set<String> = emptySet()): List<String> {
        val vars = mutableListOf<String>()
        val dollarMatches = Regex("""\$([a-zA-Z0-9_.]+)""").findAll(expr)
        for (m in dollarMatches) {
            vars.add(m.groupValues[1])
        }
        if (vars.isEmpty() && declaredVars.isNotEmpty()) {
            val clean = expr.trim()
            if (clean.lowercase() in declaredVars.map { it.lowercase() }) {
                vars.add(clean)
            }
        }
        return vars
    }

    private fun parseAnyPrimitiveWithDiagnostics(
        trimmed: String,
        lineNum: Int,
        colNum: Int,
        rawLine: String,
        rawLines: List<String>,
        currentIndex: Int
    ): Triple<LayerPrimitive?, Int, List<GlyphScriptDiagnostic>> {
        val diags = mutableListOf<GlyphScriptDiagnostic>()
        val primitiveCallRegex = Regex("""^([a-zA-Z_][a-zA-Z0-9_]*)\s*\((.*)\)$""")

        val (prim, advancedIdx, err) = parseAnyPrimitive(trimmed, lineNum, rawLine, rawLines, currentIndex, primitiveCallRegex)
        if (err != null) {
            diags.add(GlyphScriptDiagnostic("E101", err.line, err.column, err.reason))
        }
        return Triple(prim, advancedIdx, diags)
    }

    private fun parseAnyPrimitive(
        trimmed: String,
        lineNum: Int,
        rawLine: String,
        rawLines: List<String>,
        currentIndex: Int,
        primitiveCallRegex: Regex
    ): Triple<LayerPrimitive?, Int, GlyphScriptCompileError?> {
        return when {
            trimmed.startsWith("bitmap", ignoreCase = true) -> {
                val (res, err) = parseBitmapBlock(rawLines, currentIndex)
                if (err != null) Triple(null, currentIndex, err)
                else Triple(res?.first, res?.second ?: currentIndex, null)
            }
            trimmed.startsWith("sequence", ignoreCase = true) -> {
                val (res, err) = parseSequenceBlock(rawLines, currentIndex)
                if (err != null) Triple(null, currentIndex, err)
                else Triple(res?.first, res?.second ?: currentIndex, null)
            }
            trimmed.startsWith("plot", ignoreCase = true) -> {
                val (res, err) = parsePlotBlock(rawLines, currentIndex)
                if (err != null) Triple(null, currentIndex, err)
                else Triple(res?.first, res?.second ?: currentIndex, null)
            }
            else -> {
                val (prim, err) = parsePrimitiveCall(trimmed, lineNum, rawLine, primitiveCallRegex)
                Triple(prim, currentIndex, err)
            }
        }
    }

    private fun parseBitmapBlock(
        rawLines: List<String>,
        startIndex: Int
    ): Pair<Pair<LayerPrimitive.BitmapPrimitive, Int>?, GlyphScriptCompileError?> {
        val firstLine = rawLines[startIndex].trim()
        val argsStr = firstLine.substringAfter("bitmap").substringBefore("\"\"\"").trim().removeSurrounding("(", ")")
        val argsMap = parseNamedOrPositionalArgs(argsStr)
        val align = (argsMap["align"] ?: "center").removeSurrounding("\"")
        val x = argsMap["x"]?.toIntOrNull() ?: 0
        val y = argsMap["y"]?.toIntOrNull() ?: 0
        val brightness = (argsMap["brightness"]?.toIntOrNull() ?: 255).coerceIn(32, 255)

        val asciiLines = mutableListOf<String>()
        var currentIndex = startIndex

        if (firstLine.contains("\"\"\"")) {
            val afterTriple = firstLine.substringAfter("\"\"\"")
            if (afterTriple.contains("\"\"\"")) {
                val content = afterTriple.substringBefore("\"\"\"")
                return Pair(LayerPrimitive.BitmapPrimitive(content, align, x, y, brightness), currentIndex) to null
            } else if (afterTriple.isNotBlank()) {
                asciiLines.add(afterTriple)
            }
            currentIndex++
            while (currentIndex < rawLines.size) {
                val line = rawLines[currentIndex]
                if (line.contains("\"\"\"")) {
                    val beforeTriple = line.substringBefore("\"\"\"")
                    if (beforeTriple.isNotBlank() || asciiLines.isNotEmpty()) {
                        asciiLines.add(beforeTriple)
                    }
                    break
                } else {
                    asciiLines.add(line)
                }
                currentIndex++
            }
        }

        val asciiArt = asciiLines.joinToString("\n")
        val primitive = LayerPrimitive.BitmapPrimitive(asciiArt, align, x, y, brightness)
        return Pair(primitive, currentIndex) to null
    }

    private fun parseSequenceBlock(
        rawLines: List<String>,
        startIndex: Int
    ): Pair<Pair<LayerPrimitive.SequencePrimitive, Int>?, GlyphScriptCompileError?> {
        val firstLine = rawLines[startIndex].trim()
        val lineNum = startIndex + 1
        val argsStr = firstLine.substringAfter("sequence").substringBefore("{").trim().removeSurrounding("(", ")")
        val argsMap = parseNamedOrPositionalArgs(argsStr)
        val rawInterval = argsMap["interval"] ?: "100ms"
        val intervalMs = parseDurationMs(rawInterval.lowercase(), 100L)
        val rawPause = argsMap["pause"] ?: "0ms"
        val pauseMs = parseDurationMs(rawPause.lowercase(), 0L)
        val holdLast = parseBoolArg(argsMap["hold_last"] ?: argsMap["holdlast"] ?: "false")
        val loop = parseBoolArg(argsMap["loop"] ?: "true")
        val align = (argsMap["align"] ?: "center").removeSurrounding("\"")

        val frames = mutableListOf<LayerPrimitive.SequenceFrame>()
        var currentIndex = startIndex + 1

        var inFrame = false
        val currentFrameLines = mutableListOf<String>()

        while (currentIndex < rawLines.size) {
            val line = rawLines[currentIndex]
            val trimmed = line.trim()

            if (!inFrame) {
                if (trimmed == "}") {
                    break
                } else if (trimmed.startsWith("frame", ignoreCase = true)) {
                    inFrame = true
                    currentFrameLines.clear()
                    if (trimmed.contains("\"\"\"")) {
                        val afterTriple = trimmed.substringAfter("\"\"\"")
                        if (afterTriple.contains("\"\"\"")) {
                            val content = afterTriple.substringBefore("\"\"\"")
                            frames.add(LayerPrimitive.SequenceFrame(content))
                            inFrame = false
                        } else if (afterTriple.isNotBlank()) {
                            currentFrameLines.add(afterTriple)
                        }
                    }
                }
            } else {
                if (trimmed.contains("\"\"\"")) {
                    val beforeTriple = line.substringBefore("\"\"\"")
                    if (beforeTriple.isNotBlank() || currentFrameLines.isNotEmpty()) {
                        currentFrameLines.add(beforeTriple)
                    }
                    frames.add(LayerPrimitive.SequenceFrame(currentFrameLines.joinToString("\n")))
                    inFrame = false
                } else {
                    currentFrameLines.add(line)
                }
            }
            currentIndex++
        }

        if (frames.isEmpty()) {
            return null to GlyphScriptCompileError(lineNum, 1, "sequence(...) requires at least one frame block.")
        }

        val primitive = LayerPrimitive.SequencePrimitive(
            intervalMs = intervalMs,
            pauseMs = pauseMs,
            holdLast = holdLast,
            loop = loop,
            align = align,
            frames = frames
        )
        return Pair(primitive, currentIndex) to null
    }

    private fun parsePlotBlock(
        rawLines: List<String>,
        startIndex: Int
    ): Pair<Pair<LayerPrimitive.PlotPrimitive, Int>?, GlyphScriptCompileError?> {
        var currentIndex = startIndex
        val sb = StringBuilder()
        while (currentIndex < rawLines.size) {
            val line = rawLines[currentIndex]
            sb.append(line).append(" ")
            if (line.contains(")")) break
            currentIndex++
        }
        val fullStr = sb.toString().trim()
        val dotsStr = fullStr.substringAfter("plot").removeSurrounding("(", ")")

        val dots = mutableListOf<LayerPrimitive.PlotDot>()
        val bracketPattern = Regex("""\[\s*(-?\d+)\s*,\s*(-?\d+)(?:\s*,\s*(-?\d+))?\s*\]""")
        for (match in bracketPattern.findAll(dotsStr)) {
            val x = match.groupValues[1].toIntOrNull() ?: 12
            val y = match.groupValues[2].toIntOrNull() ?: 12
            val b = match.groupValues[3].toIntOrNull() ?: 255
            dots.add(LayerPrimitive.PlotDot(x, y, b.coerceIn(0, 255)))
        }

        return Pair(LayerPrimitive.PlotPrimitive(dots), currentIndex) to null
    }

    private fun parsePrimitiveCall(
        trimmed: String,
        lineNum: Int,
        rawLine: String,
        primitiveCallRegex: Regex
    ): Pair<LayerPrimitive?, GlyphScriptCompileError?> {
        val match = primitiveCallRegex.matchEntire(trimmed)
            ?: return null to GlyphScriptCompileError(
                line = lineNum,
                column = 1,
                reason = "Malformed primitive call \"$trimmed\". Expected ring(...), arrow(...), emoji(...), text(...), icon(...), or shader(...)."
            )

        val funcName = match.groupValues[1].lowercase()
        val argsBody = match.groupValues[2]
        val argsMap = parseNamedOrPositionalArgs(argsBody)

        return when (funcName) {
            "ring", "border" -> {
                val firstNamedIsNonProgress = argsMap.keys.firstOrNull() in setOf(
                    "radius", "thickness", "stroke", "width", "style", "pulse", "animate", "rotate", "spin", "brightness"
                )
                val rawProgress = argsMap["progress"]
                    ?: (if (funcName == "ring" && !firstNamedIsNonProgress) argsMap["arg0"] else null)
                    ?: "100"
                val progressExpr = stripVarPrefix(rawProgress).removeSuffix("%").trim().ifEmpty { "100" }
                val defaultStyle = if (funcName == "border" && argsMap["progress"] == null) "solid" else "clock_cw"
                val style = (argsMap["style"] ?: (if (funcName == "ring") argsMap["arg1"] else null) ?: defaultStyle)
                    .removeSurrounding("\"")
                val thickness = (argsMap["thickness"] ?: argsMap["width"])
                    ?.filter { it.isDigit() }
                    ?.toIntOrNull()
                    ?.coerceIn(1, 3) ?: 1
                val radius = argsMap["radius"]
                    ?.filter { it.isDigit() }
                    ?.toIntOrNull()
                    ?.coerceIn(10, 12) ?: 12
                val pulse = parseBoolArg(argsMap["pulse"])
                val animate = parseBoolArg(argsMap["animate"] ?: argsMap["rotate"] ?: argsMap["spin"]) ||
                    style.lowercase() in setOf("rotate", "rotating", "spin", "spinner")
                val bright = (argsMap["brightness"]?.toIntOrNull() ?: 255).coerceIn(32, 255)
                LayerPrimitive.RingPrimitive(
                    progressExpr = progressExpr,
                    style = style,
                    thickness = thickness,
                    radius = radius,
                    pulse = pulse,
                    animate = animate,
                    brightness = bright
                ) to null
            }

            "arrow" -> {
                val rawDir = argsMap["direction"] ?: argsMap["dir"] ?: argsMap["arg0"] ?: "straight"
                val size = (argsMap["size"]?.toIntOrNull() ?: 11).coerceIn(7, 15)
                val pulse = parseBoolArg(argsMap["pulse"])
                val bounce = parseBoolArg(argsMap["bounce"])
                val bright = (argsMap["brightness"]?.toIntOrNull() ?: 255).coerceIn(32, 255)
                LayerPrimitive.ArrowPrimitive(
                    directionExpr = stripVarPrefix(rawDir),
                    size = size,
                    pulse = pulse,
                    bounce = bounce,
                    brightness = bright
                ) to null
            }

            "emoji" -> {
                val rawGlyph = argsMap["glyph"] ?: argsMap["arg0"] ?: "🛵"
                val scale = (argsMap["scale"] ?: argsMap["scale_percent"])
                    ?.filter { it.isDigit() }
                    ?.toIntOrNull()
                    ?.coerceIn(25, 100) ?: 75
                val hasExplicitY = argsMap.containsKey("y")
                val y = argsMap["y"]?.toIntOrNull() ?: 8
                val x = argsMap["x"]?.toIntOrNull() ?: 12
                val bounce = parseBoolArg(argsMap["bounce"])
                val pulse = parseBoolArg(argsMap["pulse"])
                val bright = (argsMap["brightness"]?.toIntOrNull() ?: 255).coerceIn(32, 255)
                LayerPrimitive.EmojiPrimitive(
                    glyphExpr = stripVarPrefix(rawGlyph),
                    scalePercent = scale,
                    x = x,
                    y = y,
                    hasExplicitY = hasExplicitY,
                    bounce = bounce,
                    pulse = pulse,
                    brightness = bright
                ) to null
            }

            "text" -> {
                val rawContent = argsMap["content"] ?: argsMap["arg0"] ?: ""
                val suffix = (argsMap["suffix"] ?: "").removeSurrounding("\"")
                val size = (argsMap["size"] ?: "3x5").removeSurrounding("\"")
                val align = (argsMap["align"] ?: "center").removeSurrounding("\"")
                val y = argsMap["y"]?.toIntOrNull() ?: 16
                val animate = parseBoolArg(argsMap["animate"] ?: argsMap["marquee"])
                val marquee = parseBoolArg(argsMap["marquee"]) || animate
                val pulse = parseBoolArg(argsMap["pulse"])
                val bright = (argsMap["brightness"]?.toIntOrNull() ?: 255).coerceIn(32, 255)
                LayerPrimitive.TextPrimitive(
                    contentExpr = stripVarPrefix(rawContent),
                    suffix = suffix,
                    size = size,
                    align = align,
                    y = y,
                    animate = animate,
                    marquee = marquee,
                    pulse = pulse,
                    brightness = bright
                ) to null
            }

            "icon" -> {
                val rawName = argsMap["name"] ?: argsMap["arg0"] ?: "bell"
                val pulse = parseBoolArg(argsMap["pulse"])
                val bounce = parseBoolArg(argsMap["bounce"])
                val bright = (argsMap["brightness"]?.toIntOrNull() ?: 255).coerceIn(32, 255)
                LayerPrimitive.IconPrimitive(
                    nameExpr = stripVarPrefix(rawName),
                    pulse = pulse,
                    bounce = bounce,
                    brightness = bright
                ) to null
            }

            "shader" -> {
                val rawType = argsMap["type"] ?: argsMap["arg0"] ?: "wave"
                val fps = (argsMap["fps"]?.toIntOrNull() ?: 30).coerceIn(5, 60)
                val bright = (argsMap["brightness"]?.toIntOrNull() ?: 255).coerceIn(32, 255)
                LayerPrimitive.ShaderPrimitive(
                    typeExpr = stripVarPrefix(rawType),
                    fps = fps,
                    brightness = bright
                ) to null
            }

            else -> null to GlyphScriptCompileError(
                line = lineNum,
                column = 1,
                reason = "Unknown primitive function \"$funcName(...)\"."
            )
        }
    }

    private fun parseNamedOrPositionalArgs(raw: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        val clean = raw.trim()
        if (clean.isEmpty()) return map

        val parts = mutableListOf<String>()
        var sb = StringBuilder()
        var inQuotes = false
        var depth = 0
        for (i in clean.indices) {
            val ch = clean[i]
            if (ch == '"' && (i == 0 || clean[i - 1] != '\\')) {
                inQuotes = !inQuotes
                sb.append(ch)
            } else if ((ch == '[' || ch == '(') && !inQuotes) {
                depth++
                sb.append(ch)
            } else if ((ch == ']' || ch == ')') && !inQuotes) {
                depth = (depth - 1).coerceAtLeast(0)
                sb.append(ch)
            } else if (ch == ',' && !inQuotes && depth == 0) {
                parts.add(sb.toString().trim())
                sb = StringBuilder()
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) {
            parts.add(sb.toString().trim())
        }

        parts.forEachIndexed { idx, part ->
            if (part.isEmpty()) return@forEachIndexed
            val colonIdx = findUnquotedSeparator(part, ':')
            val eqIdx = findUnquotedSeparator(part, '=')
            val sepIdx = when {
                colonIdx >= 0 -> colonIdx
                eqIdx >= 0 -> eqIdx
                else -> -1
            }
            if (sepIdx > 0) {
                val key = part.substring(0, sepIdx).trim().lowercase()
                val value = part.substring(sepIdx + 1).trim()
                map[key] = value
                if (idx == 0) map["arg0"] = value
            } else {
                map["arg$idx"] = part
            }
        }
        return map
    }

    private fun findUnquotedSeparator(s: String, sep: Char): Int {
        var inQuotes = false
        for (i in s.indices) {
            val ch = s[i]
            if (ch == '"') inQuotes = !inQuotes
            else if (ch == sep && !inQuotes) return i
        }
        return -1
    }

    private fun stripCommentsAndFences(line: String): String {
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) return ""
        var inQuotes = false
        for (i in 0 until line.length) {
            val ch = line[i]
            if (ch == '"' && (i == 0 || line[i - 1] != '\\')) {
                inQuotes = !inQuotes
            } else if (!inQuotes && ch == '/' && i + 1 < line.length && line[i + 1] == '/') {
                return line.substring(0, i).trim()
            }
        }
        return line.trim()
    }

    private fun parseExtractMapBlock(
        varName: String,
        sourceField: String,
        lineNum: Int,
        rawLines: List<String>,
        startIndex: Int
    ): Pair<ExtractDeclaration?, Int> {
        val mapRules = mutableListOf<ValueMapRule>()
        var defaultVal = ""
        var currentIndex = startIndex

        val firstLine = rawLines[startIndex].trim()
        if (!firstLine.contains("{")) {
            var idx = startIndex + 1
            while (idx < rawLines.size) {
                val l = rawLines[idx].trim()
                if (l.contains("{")) {
                    currentIndex = idx
                    break
                }
                idx++
            }
        }

        currentIndex++
        val arrowRegex = Regex("""^(.*?)\s*=>\s*(.*?)$""")

        while (currentIndex < rawLines.size) {
            val lineRaw = rawLines[currentIndex]
            val line = stripCommentsAndFences(lineRaw).trim()
            if (line == "}") {
                break
            }
            if (line.isNotBlank()) {
                if (line.startsWith("DEFAULT", ignoreCase = true)) {
                    val valStr = line.substringAfter("DEFAULT").trim().removePrefix("=>").trim().removeSuffix(",").trim().removeSurrounding("\"")
                    defaultVal = valStr
                    mapRules.add(ValueMapRule(MapConditionType.DEFAULT, emptyList(), valStr))
                } else {
                    val match = arrowRegex.matchEntire(line)
                    if (match != null) {
                        val condPart = match.groupValues[1].trim()
                        val targetVal = match.groupValues[2].trim().removeSuffix(",").trim().removeSurrounding("\"")

                        when {
                            condPart.startsWith("contains", ignoreCase = true) -> {
                                val inner = condPart.substringAfter("contains").trim().removeSurrounding("(", ")")
                                val patterns = parseStringArray(inner)
                                mapRules.add(ValueMapRule(MapConditionType.CONTAINS, patterns, targetVal))
                            }
                            condPart.startsWith("regex", ignoreCase = true) -> {
                                val inner = condPart.substringAfter("regex").trim().removeSurrounding("(", ")")
                                val patterns = parseStringArray(inner)
                                mapRules.add(ValueMapRule(MapConditionType.REGEX, patterns, targetVal))
                            }
                            condPart.startsWith("equals", ignoreCase = true) -> {
                                val inner = condPart.substringAfter("equals").trim().removeSurrounding("(", ")")
                                val patterns = parseStringArray(inner)
                                mapRules.add(ValueMapRule(MapConditionType.EQUALS, patterns, targetVal))
                            }
                            else -> {
                                val patterns = parseStringArray(condPart)
                                mapRules.add(ValueMapRule(MapConditionType.EQUALS, patterns, targetVal))
                            }
                        }
                    }
                }
            }
            currentIndex++
        }

        val decl = ExtractDeclaration(
            varName = varName,
            sourceField = sourceField,
            regexPattern = null,
            mapRules = mapRules,
            defaultValue = defaultVal,
            lineNumber = lineNum
        )
        return Pair(decl, currentIndex)
    }

    private fun parseStringArray(raw: String): List<String> {
        val clean = raw.trim()
        if (clean.startsWith("[") && clean.endsWith("]")) {
            val inside = clean.substring(1, clean.length - 1)
            return inside.split(",").map { it.trim().removeSurrounding("\"").removeSurrounding("'") }.filter { it.isNotBlank() }
        }
        return listOf(clean.removeSurrounding("\"").removeSurrounding("'"))
    }

    fun parseShowIfCondition(line: String): ShowIfCondition? {
        val expr = line.substringAfter("show_if:").substringAfter("show_if").trim()
        if (expr.isBlank()) return null
        return ShowIfCondition(leftVar = expr, operator = "EXPR", rightValues = listOf(expr))
    }

    private fun stripVarPrefix(raw: String): String {
        val clean = raw.trim().removeSurrounding("\"").removeSurrounding("'")
        return clean
    }

    fun parseAnimationMacro(
        rawExpr: String,
        lineNum: Int = 1,
        colNum: Int = 1
    ): Pair<LayerAnimationMacro?, GlyphScriptDiagnostic?> {
        val clean = rawExpr.trim()
        if (clean.isEmpty()) return null to null
        val callMatch = Regex("""^([a-zA-Z_][a-zA-Z0-9_]*)\s*\((.*)\)$""").matchEntire(clean)
        if (callMatch == null) {
            return null to GlyphScriptDiagnostic(
                "E101",
                lineNum,
                colNum,
                "Malformed animate macro \"$clean\". Expected slide(...), bounce(...), spin(...), or pulse(...)."
            )
        }
        val funcName = callMatch.groupValues[1].lowercase()
        val argsBody = callMatch.groupValues[2]
        val argsMap = parseNamedOrPositionalArgs(argsBody)

        return when (funcName) {
            "slide" -> {
                val fromPair = parseIntPairArg(argsMap["from"] ?: argsMap["arg0"], 12, 18)
                val toPair = parseIntPairArg(argsMap["to"] ?: argsMap["arg1"], 12, 6)
                val speedMs = parseDurationMs((argsMap["speed"] ?: argsMap["interval"] ?: "100ms").lowercase(), 100L)
                val pauseMs = parseDurationMs((argsMap["pause"] ?: "1500ms").lowercase(), 1500L)
                val loop = if (argsMap.containsKey("loop")) parseBoolArg(argsMap["loop"]) else true
                LayerAnimationMacro.SlideMacro(
                    fromX = fromPair.first.coerceIn(-24, 48),
                    fromY = fromPair.second.coerceIn(-24, 48),
                    toX = toPair.first.coerceIn(-24, 48),
                    toY = toPair.second.coerceIn(-24, 48),
                    speedMs = speedMs.coerceIn(33L, 2000L),
                    pauseMs = pauseMs.coerceIn(0L, 10000L),
                    loop = loop
                ) to null
            }

            "bounce" -> {
                val axis = (argsMap["axis"] ?: argsMap["arg0"] ?: "y").removeSurrounding("\"").removeSurrounding("'").lowercase()
                val rangePair = parseIntPairArg(argsMap["range"] ?: argsMap["arg1"], -3, 3)
                val speedMs = parseDurationMs((argsMap["speed"] ?: argsMap["interval"] ?: "80ms").lowercase(), 80L)
                LayerAnimationMacro.BounceMacro(
                    axis = if (axis == "x") "x" else "y",
                    minOffset = rangePair.first.coerceIn(-12, 12),
                    maxOffset = rangePair.second.coerceIn(-12, 12),
                    speedMs = speedMs.coerceIn(33L, 2000L)
                ) to null
            }

            "spin", "rotate" -> {
                val rawStep = (argsMap["step"] ?: argsMap["angle"] ?: argsMap["arg0"] ?: "45deg")
                    .lowercase()
                    .removeSuffix("deg")
                    .removeSuffix("°")
                    .trim()
                val stepDeg = rawStep.toIntOrNull()?.takeIf { it != 0 } ?: 45
                val intervalMs = parseDurationMs((argsMap["interval"] ?: argsMap["speed"] ?: "100ms").lowercase(), 100L)
                LayerAnimationMacro.SpinMacro(
                    stepDegrees = stepDeg.coerceIn(-180, 180),
                    intervalMs = intervalMs.coerceIn(33L, 2000L)
                ) to null
            }

            "pulse", "breathe" -> {
                val isBreathe = funcName == "breathe"
                val minB = (argsMap["min"] ?: argsMap["arg0"])?.toIntOrNull()?.coerceIn(0, 255) ?: if (isBreathe) 24 else 40
                val maxB = (argsMap["max"] ?: argsMap["arg1"])?.toIntOrNull()?.coerceIn(32, 255) ?: 255
                val defaultPeriod = if (isBreathe) 2000L else 1000L
                val periodMs = parseDurationMs((argsMap["period"] ?: argsMap["speed"] ?: "${defaultPeriod}ms").lowercase(), defaultPeriod)
                LayerAnimationMacro.PulseMacro(
                    minBrightness = minB,
                    maxBrightness = maxB.coerceAtLeast(minB),
                    periodMs = periodMs.coerceIn(200L, 10000L),
                    isBreathe = isBreathe
                ) to null
            }

            else -> null to GlyphScriptDiagnostic(
                "E101",
                lineNum,
                colNum,
                "Unknown animate macro \"$funcName(...)\". Expected slide(...), bounce(...), spin(...), or pulse(...)."
            )
        }
    }

    private fun parseIntPairArg(raw: String?, defaultFirst: Int, defaultSecond: Int): Pair<Int, Int> {
        if (raw.isNullOrBlank()) return defaultFirst to defaultSecond
        val clean = raw.trim().removeSurrounding("[", "]")
        val parts = clean.split(",")
        if (parts.size >= 2) {
            val a = parts[0].trim().toIntOrNull() ?: defaultFirst
            val b = parts[1].trim().toIntOrNull() ?: defaultSecond
            return a to b
        }
        return defaultFirst to defaultSecond
    }

    private fun parseBoolArg(raw: String?): Boolean {
        val clean = raw?.trim()?.removeSurrounding("\"")?.lowercase()
        return clean == "true" || clean == "1" || clean == "yes" || clean == "on"
    }

    private fun parseWhenClause(
        rawAfterWhen: String,
        lineNum: Int,
        colNum: Int,
        rawLines: List<String>,
        startIndex: Int,
        diagnostics: MutableList<GlyphScriptDiagnostic>
    ): Pair<WhenClause, Int> {
        var combinedWhenStr = rawAfterWhen
        var scanIdx = startIndex + 1
        var depth = 0
        for (ch in combinedWhenStr) {
            if (ch == '[') depth++
            else if (ch == ']') depth--
        }
        while (depth > 0 && scanIdx < rawLines.size) {
            val nextLine = rawLines[scanIdx].trim()
            if (nextLine.startsWith("EXTRACT", ignoreCase = true) ||
                nextLine.startsWith("DISPLAY", ignoreCase = true) ||
                nextLine.startsWith("BEHAVIOR", ignoreCase = true)
            ) {
                break
            }
            combinedWhenStr += " " + nextLine
            for (ch in nextLine) {
                if (ch == '[') depth++
                else if (ch == ']') depth--
            }
            scanIdx++
        }
        val updatedIndex = (scanIdx - 1).coerceAtLeast(startIndex)

        val subTriggerStrings = splitByTopLevelOr(combinedWhenStr)
        val parsedTriggers = mutableListOf<TriggerCondition>()
        val allTargetPackages = mutableListOf<String>()

        for (part in subTriggerStrings) {
            val trimmedPart = part.trim()
            val fromIdx = trimmedPart.indexOf(" FROM ", ignoreCase = true)
            val fromBracketIdx = trimmedPart.indexOf(" FROM[", ignoreCase = true)
            val hasFrom = fromIdx != -1 || fromBracketIdx != -1

            if (trimmedPart.startsWith("notification", ignoreCase = true) || hasFrom) {
                val triggerEvent = if (hasFrom) {
                    val splitIdx = if (fromIdx != -1) fromIdx else fromBracketIdx
                    trimmedPart.substring(0, splitIdx).trim()
                } else {
                    trimmedPart.substringBefore(" WHERE").trim()
                }

                val pkgs = if (hasFrom) {
                    val afterFrom = if (fromIdx != -1) trimmedPart.substring(fromIdx + 6).trim() else trimmedPart.substring(fromBracketIdx + 5).trim()
                    if (afterFrom.startsWith("[")) {
                        val inside = afterFrom.substringAfter("[").substringBefore("]").trim()
                        val list = inside.split(",").map { it.trim().removeSurrounding("\"").removeSurrounding("'") }.filter { it.isNotBlank() }
                        if (list.isEmpty()) {
                            diagnostics.add(GlyphScriptDiagnostic("E101", lineNum, colNum, "Empty package list in WHEN clause; specify at least one package ID or \"*\""))
                        }
                        list
                    } else {
                        val p = afterFrom.substringBefore(" WHERE").trim().removeSurrounding("\"").removeSurrounding("'")
                        if (p.isBlank()) listOf("*") else listOf(p)
                    }
                } else listOf("*")

                allTargetPackages.addAll(pkgs)
                parsedTriggers.add(
                    TriggerCondition(
                        triggerType = TriggerType.NOTIFICATION,
                        targetPackages = pkgs.ifEmpty { listOf("*") },
                        rawExpression = trimmedPart
                    )
                )
            } else if (trimmedPart.startsWith("system.", ignoreCase = true)) {
                val propAndRest = trimmedPart.substring(7).trim()
                when {
                    propAndRest.endsWith(" CHANGED", ignoreCase = true) -> {
                        val prop = propAndRest.substringBefore(" ").trim()
                        parsedTriggers.add(
                            TriggerCondition(
                                triggerType = TriggerType.SYSTEM_PROPERTY,
                                propertyName = prop,
                                operator = "CHANGED",
                                rawExpression = trimmedPart
                            )
                        )
                    }
                    propAndRest.contains("==") -> {
                        val prop = propAndRest.substringBefore("==").trim()
                        val value = propAndRest.substringAfter("==").trim().removeSurrounding("\"").removeSurrounding("'")
                        parsedTriggers.add(
                            TriggerCondition(
                                triggerType = TriggerType.SYSTEM_PROPERTY,
                                propertyName = prop,
                                operator = "==",
                                expectedValue = value,
                                rawExpression = trimmedPart
                            )
                        )
                    }
                    propAndRest.contains("<=") -> {
                        val prop = propAndRest.substringBefore("<=").trim()
                        val value = propAndRest.substringAfter("<=").trim().removeSurrounding("\"").removeSurrounding("'")
                        parsedTriggers.add(
                            TriggerCondition(
                                triggerType = TriggerType.SYSTEM_PROPERTY,
                                propertyName = prop,
                                operator = "<=",
                                expectedValue = value,
                                rawExpression = trimmedPart
                            )
                        )
                    }
                    propAndRest.contains("<") -> {
                        val prop = propAndRest.substringBefore("<").trim()
                        val value = propAndRest.substringAfter("<").trim().removeSurrounding("\"").removeSurrounding("'")
                        parsedTriggers.add(
                            TriggerCondition(
                                triggerType = TriggerType.SYSTEM_PROPERTY,
                                propertyName = prop,
                                operator = "<",
                                expectedValue = value,
                                rawExpression = trimmedPart
                            )
                        )
                    }
                    propAndRest.contains(">=") -> {
                        val prop = propAndRest.substringBefore(">=").trim()
                        val value = propAndRest.substringAfter(">=").trim().removeSurrounding("\"").removeSurrounding("'")
                        parsedTriggers.add(
                            TriggerCondition(
                                triggerType = TriggerType.SYSTEM_PROPERTY,
                                propertyName = prop,
                                operator = ">=",
                                expectedValue = value,
                                rawExpression = trimmedPart
                            )
                        )
                    }
                    propAndRest.contains(">") -> {
                        val prop = propAndRest.substringBefore(">").trim()
                        val value = propAndRest.substringAfter(">").trim().removeSurrounding("\"").removeSurrounding("'")
                        parsedTriggers.add(
                            TriggerCondition(
                                triggerType = TriggerType.SYSTEM_PROPERTY,
                                propertyName = prop,
                                operator = ">",
                                expectedValue = value,
                                rawExpression = trimmedPart
                            )
                        )
                    }
                    else -> {
                        parsedTriggers.add(
                            TriggerCondition(
                                triggerType = TriggerType.SYSTEM_PROPERTY,
                                propertyName = propAndRest.substringBefore(" ").trim(),
                                operator = "CHANGED",
                                rawExpression = trimmedPart
                            )
                        )
                    }
                }
            } else if (trimmedPart.startsWith("app.foreground", ignoreCase = true) || trimmedPart.startsWith("system.foreground_app", ignoreCase = true)) {
                val targetPkg = trimmedPart.substringAfter("==").trim().removeSurrounding("\"").removeSurrounding("'")
                allTargetPackages.add(targetPkg)
                parsedTriggers.add(
                    TriggerCondition(
                        triggerType = TriggerType.APP_FOREGROUND,
                        targetPackages = listOf(targetPkg),
                        expectedValue = targetPkg,
                        rawExpression = trimmedPart
                    )
                )
            } else {
                parsedTriggers.add(
                    TriggerCondition(
                        triggerType = TriggerType.NOTIFICATION,
                        targetPackages = listOf("*"),
                        rawExpression = trimmedPart
                    )
                )
            }
        }

        val finalPkgs = allTargetPackages.distinct().filter { it.isNotBlank() }.ifEmpty { listOf("*") }
        val primaryEvent = subTriggerStrings.firstOrNull() ?: rawAfterWhen

        val whenClause = WhenClause(
            triggerEvent = primaryEvent,
            fromTarget = finalPkgs.firstOrNull() ?: "*",
            targetPackages = finalPkgs,
            fromLineNumber = lineNum,
            fromColumnNumber = colNum,
            triggers = parsedTriggers
        )
        return whenClause to updatedIndex
    }

    private fun splitByTopLevelOr(input: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        var inBrackets = false
        var current = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '"' || c == '\'') inQuotes = !inQuotes
            else if (c == '[' && !inQuotes) inBrackets = true
            else if (c == ']' && !inQuotes) inBrackets = false

            if (!inQuotes && !inBrackets && input.regionMatches(i, " OR ", 0, 4, ignoreCase = true)) {
                result.add(current.toString().trim())
                current = StringBuilder()
                i += 4
                continue
            }
            current.append(c)
            i++
        }
        if (current.isNotBlank()) {
            result.add(current.toString().trim())
        }
        return result.ifEmpty { listOf(input) }
    }

    private fun parseDurationMs(rawInput: String, defaultMs: Long): Long {
        val clean = rawInput.trim().lowercase()
        val inside = if (clean.startsWith("duration")) {
            clean.substringAfter("(").substringBefore(")").trim()
        } else {
            clean
        }
        return when {
            inside.endsWith("ms") -> inside.removeSuffix("ms").trim().toLongOrNull() ?: defaultMs
            inside.endsWith("s") -> (inside.removeSuffix("s").trim().toDoubleOrNull()?.let { (it * 1000).toLong() }) ?: defaultMs
            inside.endsWith("m") -> (inside.removeSuffix("m").trim().toDoubleOrNull()?.let { (it * 60_000).toLong() }) ?: defaultMs
            else -> inside.toLongOrNull() ?: defaultMs
        }.coerceIn(0L, 120_000L)
    }

    private fun isValidTriggerEvent(event: String): Boolean {
        val lower = event.lowercase()
        return lower.startsWith("system.") || lower.startsWith("key.") || lower.startsWith("sequence") || lower in setOf(
            "notification", "media.playing", "battery.low", "battery_level",
            "system.flip_face_down", "system.charging", "system.torch", "system.ringer"
        )
    }

    private fun mapTriggerEventToConditionType(event: String): ConditionType {
        val lower = event.lowercase()
        return when {
            lower.startsWith("system.") -> ConditionType.SYSTEM_STATE
            lower.startsWith("key.") || lower.startsWith("sequence") -> ConditionType.KEY_PRESS
            lower == "notification" -> ConditionType.NOTIFICATION
            lower == "media.playing" -> ConditionType.MEDIA_PLAYBACK
            lower == "battery.low" || lower == "battery_level" -> ConditionType.BATTERY_LEVEL
            lower == "system.charging" -> ConditionType.CHARGING_STATE
            lower == "system.flip_face_down" -> ConditionType.FACE_DOWN
            lower == "system.torch" -> ConditionType.TORCH_STATE
            lower == "system.ringer" -> ConditionType.RINGER_MODE
            else -> ConditionType.NOTIFICATION
        }
    }

    fun exportRuleToGlyphScript(rule: TriggerRule): String {
        if (rule.glyphScriptSource.isNotBlank()) {
            return rule.glyphScriptSource
        }
        val whenTrigger = when (rule.conditionType) {
            ConditionType.NOTIFICATION -> {
                if (rule.targetPackages.size > 1) {
                    "notification FROM [\n" + rule.targetPackages.joinToString(",\n") { "    \"$it\"" } + "\n  ]"
                } else if (rule.targetPackages.isNotEmpty()) {
                    "notification FROM \"${rule.targetPackages.first()}\""
                } else {
                    "notification FROM \"${rule.targetPackageName}\""
                }
            }
            ConditionType.MEDIA_PLAYBACK -> "media.playing"
            ConditionType.BATTERY_LEVEL -> "battery.low"
            ConditionType.CHARGING_STATE -> "system.charging"
            ConditionType.FACE_DOWN -> "system.flip_face_down"
            ConditionType.TORCH_STATE -> "system.torch"
            ConditionType.RINGER_MODE -> "system.ringer"
            else -> "notification FROM \"*\""
        }
        val untilText = if (rule.holdUntilConditionClears) {
            if (rule.conditionType == ConditionType.MEDIA_PLAYBACK) "media.stopped" else "notification.dismissed"
        } else {
            "duration(${rule.overrideDurationMs / 1000}s)"
        }

        val displayBlock = if (rule.actionType == ActionType.MATRIX_METER) {
            """  LAYER progress_border {
    ring(thickness: 1, radius: 12, progress: pct, style: "clock_cw")
  }
  LAYER hud_text {
    text(content: pct, size: "3x5", align: "center", y: 11)
  }"""
        } else {
            """  LAYER top_icon {
    icon(name: "bell", pulse: true)
  }
  LAYER bottom_text {
    text(content: pct, size: "3x5", align: "center", y: 16)
  }"""
        }

        return """RULE "${rule.name}"
WHEN $whenTrigger
EXTRACT {
  var pct = progress_percent DEFAULT "${rule.numericThreshold}"
}
DISPLAY {
$displayBlock
}
BEHAVIOR {
  priority: ${rule.priority}
  until: $untilText
  fallback_clear: true
}"""
    }
}
