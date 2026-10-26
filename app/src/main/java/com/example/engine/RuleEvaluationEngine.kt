package com.example.engine

import android.os.Build
import android.os.SystemClock
import com.example.data.repository.GlyphRepository
import com.example.domain.model.ActionType
import com.example.domain.model.AuditLogEntry
import com.example.domain.model.AuditStatus
import com.example.domain.model.ChargingRequirement
import com.example.domain.model.ComparisonOperator
import com.example.domain.model.ConditionType
import com.example.domain.model.GlyphAnimation
import com.example.domain.model.GlyphFrame
import com.example.domain.model.MediaTriggerState
import com.example.domain.model.SystemSignalEvent
import com.example.domain.model.SystemToggleFilter
import com.example.domain.model.TextMatchField
import com.example.domain.model.TextMatchOperator
import com.example.domain.model.TriggerRule
import com.example.hardware.GlyphHardwareController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Universal IFTTT & Essential Glyph Rule Evaluation Engine with persistent 30 FPS frame streaming,
 * granular Sender/Body notification filtering, Quick Settings broadcast triggers, and automatic
 * state clearing when media stops or notifications are dismissed.
 */
class RuleEvaluationEngine(
    private val repository: GlyphRepository,
    private val hardwareController: GlyphHardwareController
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lastTriggeredTimestamps = ConcurrentHashMap<Long, Long>()

    @Volatile
    private var cachedRules: List<TriggerRule> = emptyList()

    @Volatile
    private var cachedAnimations: Map<Long, GlyphAnimation> = emptyMap()

    init {
        engineScope.launch {
            repository.ensureSeeded()
        }
        engineScope.launch {
            repository.rulesFlow.collectLatest { rules ->
                cachedRules = rules.sortedWith(
                    compareByDescending<TriggerRule> { it.effectivePriority }.thenBy { it.id }
                )
            }
        }
        engineScope.launch {
            repository.animationsFlow.collectLatest { anims ->
                cachedAnimations = anims.associateBy { it.id }
            }
        }
        engineScope.launch {
            EventIngestBus.signalFlow.collect { signal ->
                evaluateSignal(signal)
            }
        }
        engineScope.launch {
            EventIngestBus.clearSourceFlow.collect { sourceIdentifier ->
                if (sourceIdentifier == "MEDIA_STOPPED") {
                    ActiveTaskStateManager.clearAll(hardwareController)
                } else {
                    ActiveTaskStateManager.removeTaskBySource(sourceIdentifier, hardwareController)
                    hardwareController.clearIfMatchingSource(sourceIdentifier)
                }
            }
        }
    }

    suspend fun evaluateSignal(signal: SystemSignalEvent) {
        val evalStartNs = SystemClock.elapsedRealtimeNanos()

        // 0. If Media Playback paused/stopped and no rule explicitly triggers on PAUSED, shut down LEDs immediately
        if (signal.conditionType == ConditionType.MEDIA_PLAYBACK &&
            signal.mediaState == MediaTriggerState.PAUSED
        ) {
            val rules = if (cachedRules.isNotEmpty()) cachedRules else repository.getAllRulesSnapshot()
            val hasExplicitPauseRule = rules.any {
                it.isEnabled &&
                    it.conditionType == ConditionType.MEDIA_PLAYBACK &&
                    it.mediaTriggerState == MediaTriggerState.PAUSED
            }
            if (!hasExplicitPauseRule) {
                hardwareController.clearAndCloseSession()
                return
            }
        }

        // 1. Check Global Kill Switch
        if (!EventIngestBus.globalEngineEnabled.value) {
            ActiveTaskStateManager.clearAll(hardwareController)
            val latency = (SystemClock.elapsedRealtimeNanos() - evalStartNs) / 1_000_000.0
            EventIngestBus.recordAuditEntry(
                AuditLogEntry(
                    timestamp = signal.timestamp,
                    eventType = signal.conditionType,
                    sourceSummary = signal.summaryText(),
                    extrasSummary = buildExtrasSummary(signal),
                    matchedRuleId = null,
                    matchedRuleName = "GLOBAL KILL SWITCH ACTIVE",
                    matchedRulePriority = null,
                    actionFiredSummary = "Engine Muted",
                    frameName = "NONE",
                    peakBrightness = 0,
                    averageBrightness = 0,
                    renderLatencyMs = roundLatency(latency),
                    status = AuditStatus.KILL_SWITCH_BLOCKED,
                    isSimulated = signal.isSimulated
                )
            )
            return
        }

        val rules = if (cachedRules.isNotEmpty()) {
            cachedRules
        } else {
            repository.getAllRulesSnapshot().sortedWith(
                compareByDescending<TriggerRule> { it.effectivePriority }.thenBy { it.id }
            )
        }

        val matchingRules = rules.filter { it.isEnabled && matchesRuleCondition(it, signal) }

        if (matchingRules.isEmpty()) {
            val latency = (SystemClock.elapsedRealtimeNanos() - evalStartNs) / 1_000_000.0
            EventIngestBus.recordAuditEntry(
                AuditLogEntry(
                    timestamp = signal.timestamp,
                    eventType = signal.conditionType,
                    sourceSummary = signal.summaryText(),
                    extrasSummary = buildExtrasSummary(signal),
                    matchedRuleId = null,
                    matchedRuleName = null,
                    matchedRulePriority = null,
                    actionFiredSummary = "No matching rule condition",
                    frameName = "STANDBY",
                    peakBrightness = 0,
                    averageBrightness = 0,
                    renderLatencyMs = roundLatency(latency),
                    status = AuditStatus.UNMATCHED,
                    isSimulated = signal.isSimulated
                )
            )
            return
        }

        val winningRule = matchingRules.first()
        val effectivePriority = winningRule.effectivePriority
        val nowMs = SystemClock.elapsedRealtime()
        val lastFired = lastTriggeredTimestamps[winningRule.id] ?: 0L
        val isSameActiveRuleUpdating = hardwareController.matrixState.value.activeRuleId == winningRule.id &&
            winningRule.actionType == ActionType.GLYPH_SCRIPT_COMPOSITOR

        // 2. Cooldown / Anti-Strobe Debounce (bypassed when Essential Priority Override is active or live GlyphScript HUD updates)
        if (!winningRule.essentialPriorityOverride &&
            !isSameActiveRuleUpdating &&
            winningRule.cooldownMs > 0L &&
            lastFired > 0L &&
            (nowMs - lastFired) < winningRule.cooldownMs
        ) {
            val remainingMs = winningRule.cooldownMs - (nowMs - lastFired)
            val latency = (SystemClock.elapsedRealtimeNanos() - evalStartNs) / 1_000_000.0
            EventIngestBus.recordAuditEntry(
                AuditLogEntry(
                    timestamp = signal.timestamp,
                    eventType = signal.conditionType,
                    sourceSummary = signal.summaryText(),
                    extrasSummary = buildExtrasSummary(signal),
                    matchedRuleId = winningRule.id,
                    matchedRuleName = winningRule.name,
                    matchedRulePriority = effectivePriority,
                    actionFiredSummary = "Cooldown active (${remainingMs}ms remaining)",
                    frameName = "DEBOUNCED",
                    peakBrightness = 0,
                    averageBrightness = 0,
                    renderLatencyMs = roundLatency(latency),
                    status = AuditStatus.COOLDOWN_SKIPPED,
                    isSimulated = signal.isSimulated
                )
            )
            return
        }

        // 3. Priority Preemption
        if (!hardwareController.canPreempt(effectivePriority)) {
            val activeState = hardwareController.matrixState.value
            val latency = (SystemClock.elapsedRealtimeNanos() - evalStartNs) / 1_000_000.0
            EventIngestBus.recordAuditEntry(
                AuditLogEntry(
                    timestamp = signal.timestamp,
                    eventType = signal.conditionType,
                    sourceSummary = signal.summaryText(),
                    extrasSummary = buildExtrasSummary(signal),
                    matchedRuleId = winningRule.id,
                    matchedRuleName = winningRule.name,
                    matchedRulePriority = effectivePriority,
                    actionFiredSummary = "Blocked by higher priority P${activeState.activePriority} (${activeState.activeRuleName})",
                    frameName = activeState.activeFrameName,
                    peakBrightness = activeState.peakBrightness,
                    averageBrightness = activeState.averageBrightness,
                    renderLatencyMs = roundLatency(latency),
                    status = AuditStatus.PREEMPTED,
                    isSimulated = signal.isSimulated
                )
            )
            return
        }

        // 4. Execute Hardware Matrix Action
        lastTriggeredTimestamps[winningRule.id] = nowMs
        executeRuleAction(winningRule, signal, evalStartNs)
    }

    fun forceExecuteRule(
        rule: TriggerRule,
        titleOverride: String = "",
        bodyOverride: String = "",
        variableOverrides: Map<String, String> = emptyMap()
    ) {
        engineScope.launch {
            val startNs = SystemClock.elapsedRealtimeNanos()
            val isPhysicalNothing = Build.MANUFACTURER.equals("Nothing", ignoreCase = true)
            val syntheticSignal = SystemSignalEvent(
                conditionType = rule.conditionType,
                sourcePackage = if (rule.targetPackageName == "*") "com.nothing.glyph" else rule.targetPackageName,
                sourceAppLabel = rule.targetAppLabel,
                title = titleOverride.ifBlank { rule.senderFilterPattern },
                content = bodyOverride.ifBlank { rule.bodyFilterPattern.ifBlank { rule.textMatchPattern } },
                progress = 76,
                progressMax = 100,
                batteryLevel = 76,
                isCharging = true,
                isFaceDown = true,
                toggleActive = true,
                ringerMode = rule.systemToggleFilter,
                isSimulated = !isPhysicalNothing
            )
            executeRuleAction(rule, syntheticSignal, startNs, variableOverrides)
        }
    }

    private suspend fun executeRuleAction(
        rule: TriggerRule,
        signal: SystemSignalEvent,
        evalStartNs: Long,
        variableOverrides: Map<String, String> = emptyMap()
    ) {
        val effectivePriority = rule.effectivePriority
        val effectiveHold = rule.holdUntilConditionClears || rule.essentialPriorityOverride

        if (rule.targetPackages.size > 1 || rule.targetPackages.contains(signal.sourcePackage)) {
            AuditDiagnosticsBuffer.recordMultiAppRuleMatch(rule.name, signal.sourcePackage)
        }

        // If this rule is backed by a GlyphScript v2 program, evaluate EXTRACT regexes & composite multi-layer frames
        if (rule.actionType == ActionType.GLYPH_SCRIPT_COMPOSITOR || rule.glyphScriptSource.isNotBlank()) {
            val (program, _) = com.example.dsl.GlyphScriptCompiler.parseProgramOnly(rule.glyphScriptSource)
            if (program != null) {
                val warningsBefore = AuditDiagnosticsBuffer.extractFallbackWarnings.value.size
                val baseExtractedVars = com.example.dsl.GlyphFrameCompositor.evaluateExtractions(
                    declarations = program.extractDeclarations,
                    title = signal.title,
                    body = signal.content,
                    subText = signal.subtext,
                    progressPercent = signal.normalizedPercentage,
                    logFallbackWarnings = true,
                    extraPayload = signal.payload + GlobalSystemStateCache.getAll()
                )
                val extractedVars = LinkedHashMap(baseExtractedVars).apply {
                    putAll(variableOverrides)
                }
                val latestWarnings = AuditDiagnosticsBuffer.extractFallbackWarnings.value
                val newFallbackWarning = if (latestWarnings.size > warningsBefore) {
                    latestWarnings.firstOrNull()
                } else null

                val userVarSummary = program.extractDeclarations.joinToString(", ") { decl ->
                    "${decl.varName}=\"${extractedVars[decl.varName].orEmpty()}\""
                }
                val extractionSummaryWithWarnings = buildString {
                    append(userVarSummary.ifBlank { "default_env" })
                    if (!newFallbackWarning.isNullOrBlank()) {
                        append(" | ")
                        append(newFallbackWarning)
                    }
                }
                EventIngestBus.updateLatestPayloadExtraction(
                    packageName = signal.sourcePackage,
                    extractedSummary = extractionSummaryWithWarnings
                )

                val compositeFrames: List<ByteArray> = com.example.dsl.GlyphFrameCompositor.compositeFrames(
                    program = program,
                    variables = extractedVars
                )
                val firstFrameBytes = compositeFrames.firstOrNull() ?: ByteArray(625)
                val firstFrame = GlyphFrame(pixels = firstFrameBytes)

                val activeTask = ActiveTask(
                    key = signal.sourcePackage,
                    sourcePackage = signal.sourcePackage,
                    appLabel = signal.sourceAppLabel,
                    ruleId = rule.id,
                    ruleName = rule.name,
                    priority = effectivePriority,
                    compositeFrames = compositeFrames,
                    frameIntervalMs = rule.overrideIntervalMs ?: 100L,
                    isTransient = rule.isTransient,
                    durationMs = if (rule.isTransient) rule.intrudeDurationMs else rule.overrideDurationMs
                )
                ActiveTaskStateManager.pushTask(activeTask, hardwareController)

                val totalLatency = (SystemClock.elapsedRealtimeNanos() - evalStartNs) / 1_000_000.0
                EventIngestBus.recordAuditEntry(
                    AuditLogEntry(
                        timestamp = signal.timestamp,
                        eventType = signal.conditionType,
                        sourceSummary = signal.summaryText(),
                        extrasSummary = buildExtrasSummary(signal) +
                            if (extractionSummaryWithWarnings.isNotBlank()) " | EXTRACT: $extractionSummaryWithWarnings" else "",
                        matchedRuleId = rule.id,
                        matchedRuleName = rule.name,
                        matchedRulePriority = effectivePriority,
                        actionFiredSummary = if (compositeFrames.size > 1) {
                            "GlyphScript v2 (${program.displayLayers.size} layers, ${compositeFrames.size}f @ 100ms)"
                        } else {
                            "GlyphScript v2 (${program.displayLayers.size} layers, static hold)"
                        },
                        frameName = "DSL_${program.ruleName}",
                        peakBrightness = firstFrame.peakBrightness(),
                        averageBrightness = firstFrame.averageBrightness(),
                        renderLatencyMs = roundLatency(totalLatency),
                        status = AuditStatus.TRIGGERED,
                        isSimulated = signal.isSimulated
                    )
                )
                return
            }
        }

        val targetAnim = cachedAnimations[rule.targetAnimationId]
            ?: repository.getAnimationById(rule.targetAnimationId)
            ?: cachedAnimations.values.firstOrNull()
            ?: run {
                val promptSeed = rule.targetAnimationName.ifBlank { rule.name }.ifBlank { "star" }
                val (title, generatedFrames) = GlyphPatternGenerator.generateFromPrompt(promptSeed, 255)
                GlyphAnimation(id = 0L, name = title, frames = generatedFrames)
            }

        when (rule.actionType) {
            ActionType.SHOW_SINGLE_FRAME,
            ActionType.GLYPH_SCRIPT_COMPOSITOR -> {
                val frame = targetAnim.frames.firstOrNull() ?: GlyphFrame()
                val activeTask = ActiveTask(
                    key = signal.sourcePackage,
                    sourcePackage = signal.sourcePackage,
                    appLabel = signal.sourceAppLabel,
                    ruleId = rule.id,
                    ruleName = rule.name,
                    priority = effectivePriority,
                    compositeFrames = listOf(frame.pixels),
                    frameIntervalMs = 100L,
                    isTransient = rule.isTransient,
                    durationMs = if (rule.isTransient) rule.intrudeDurationMs else rule.overrideDurationMs
                )
                ActiveTaskStateManager.pushTask(activeTask, hardwareController)
                val totalLatency = (SystemClock.elapsedRealtimeNanos() - evalStartNs) / 1_000_000.0
                EventIngestBus.recordAuditEntry(
                    AuditLogEntry(
                        timestamp = signal.timestamp,
                        eventType = signal.conditionType,
                        sourceSummary = signal.summaryText(),
                        extrasSummary = buildExtrasSummary(signal),
                        matchedRuleId = rule.id,
                        matchedRuleName = rule.name,
                        matchedRulePriority = effectivePriority,
                        actionFiredSummary = if (effectiveHold)
                            "Essential Static Stream (30 FPS until dismissed)"
                        else
                            "Single Frame (${rule.overrideDurationMs}ms)",
                        frameName = targetAnim.name,
                        peakBrightness = frame.peakBrightness(),
                        averageBrightness = frame.averageBrightness(),
                        renderLatencyMs = roundLatency(totalLatency),
                        status = AuditStatus.TRIGGERED,
                        isSimulated = signal.isSimulated
                    )
                )
            }

            ActionType.PLAY_ANIMATION -> {
                val animFrames = targetAnim.frames.map { it.pixels }.ifEmpty { listOf(ByteArray(625)) }
                val activeTask = ActiveTask(
                    key = signal.sourcePackage,
                    sourcePackage = signal.sourcePackage,
                    appLabel = signal.sourceAppLabel,
                    ruleId = rule.id,
                    ruleName = rule.name,
                    priority = effectivePriority,
                    compositeFrames = animFrames,
                    frameIntervalMs = rule.overrideIntervalMs,
                    isTransient = rule.isTransient,
                    durationMs = if (rule.isTransient) rule.intrudeDurationMs else rule.overrideDurationMs
                )
                ActiveTaskStateManager.pushTask(activeTask, hardwareController)
                val firstFrame = targetAnim.frames.firstOrNull() ?: GlyphFrame()
                val totalLatency = (SystemClock.elapsedRealtimeNanos() - evalStartNs) / 1_000_000.0
                EventIngestBus.recordAuditEntry(
                    AuditLogEntry(
                        timestamp = signal.timestamp,
                        eventType = signal.conditionType,
                        sourceSummary = signal.summaryText(),
                        extrasSummary = buildExtrasSummary(signal),
                        matchedRuleId = rule.id,
                        matchedRuleName = rule.name,
                        matchedRulePriority = effectivePriority,
                        actionFiredSummary = if (effectiveHold)
                            "Persistent Animation Stream (30 FPS)"
                        else
                            "Animation (${targetAnim.frames.size}f @ ${rule.overrideIntervalMs}ms)",
                        frameName = targetAnim.name,
                        peakBrightness = firstFrame.peakBrightness(),
                        averageBrightness = firstFrame.averageBrightness(),
                        renderLatencyMs = roundLatency(totalLatency),
                        status = AuditStatus.TRIGGERED,
                        isSimulated = signal.isSimulated
                    )
                )
            }

            ActionType.AUDIO_WAVEFORM -> {
                val sampleFrame = GlyphFrame(
                    pixels = GlyphPatternGenerator.buildAudioWaveformShaderFrame(0, rule.meterPeakBrightness)
                )
                hardwareController.triggerAudioWaveformShader(
                    peakBrightness = rule.meterPeakBrightness,
                    durationMs = rule.overrideDurationMs,
                    holdWhileAudioPlaying = effectiveHold,
                    ruleId = rule.id,
                    ruleName = rule.name,
                    sourcePackage = signal.sourcePackage,
                    priority = effectivePriority
                )
                val totalLatency = (SystemClock.elapsedRealtimeNanos() - evalStartNs) / 1_000_000.0
                EventIngestBus.recordAuditEntry(
                    AuditLogEntry(
                        timestamp = signal.timestamp,
                        eventType = signal.conditionType,
                        sourceSummary = signal.summaryText(),
                        extrasSummary = buildExtrasSummary(signal),
                        matchedRuleId = rule.id,
                        matchedRuleName = rule.name,
                        matchedRulePriority = effectivePriority,
                        actionFiredSummary = "30 FPS Audio Waveform Shader",
                        frameName = "AUDIO_WAVEFORM_SHADER",
                        peakBrightness = sampleFrame.peakBrightness(),
                        averageBrightness = sampleFrame.averageBrightness(),
                        renderLatencyMs = roundLatency(totalLatency),
                        status = AuditStatus.TRIGGERED,
                        isSimulated = signal.isSimulated
                    )
                )
            }

            ActionType.MATRIX_METER -> {
                val pct = signal.normalizedPercentage ?: rule.numericThreshold.coerceIn(0, 100)
                val meterBytes = GlyphHardwareController.buildMeterFrame(
                    percentage = pct,
                    style = rule.meterStyle,
                    peakBrightness = rule.meterPeakBrightness
                )
                val meterFrame = GlyphFrame(durationMs = rule.overrideDurationMs, pixels = meterBytes)
                hardwareController.triggerDynamicMeter(
                    percentage = pct,
                    style = rule.meterStyle,
                    peakBrightness = rule.meterPeakBrightness,
                    durationMs = rule.overrideDurationMs,
                    holdIndefinitely = effectiveHold,
                    label = rule.meterStyle.label,
                    ruleId = rule.id,
                    ruleName = rule.name,
                    sourcePackage = signal.sourcePackage,
                    priority = effectivePriority
                )
                val totalLatency = (SystemClock.elapsedRealtimeNanos() - evalStartNs) / 1_000_000.0
                EventIngestBus.recordAuditEntry(
                    AuditLogEntry(
                        timestamp = signal.timestamp,
                        eventType = signal.conditionType,
                        sourceSummary = signal.summaryText(),
                        extrasSummary = buildExtrasSummary(signal),
                        matchedRuleId = rule.id,
                        matchedRuleName = rule.name,
                        matchedRulePriority = effectivePriority,
                        actionFiredSummary = "Dynamic Meter: ${rule.meterStyle.label} @ $pct%",
                        frameName = "METER_${rule.meterStyle.name} ($pct%)",
                        peakBrightness = meterFrame.peakBrightness(),
                        averageBrightness = meterFrame.averageBrightness(),
                        renderLatencyMs = roundLatency(totalLatency),
                        status = AuditStatus.TRIGGERED,
                        isSimulated = signal.isSimulated
                    )
                )
            }
        }
    }

    fun matchesRuleCondition(rule: TriggerRule, signal: SystemSignalEvent): Boolean {
        if (rule.glyphScriptSource.isNotBlank()) {
            val (program, _) = com.example.dsl.GlyphScriptCompiler.parseProgramOnly(rule.glyphScriptSource)
            if (program != null && program.whenClause.triggers.isNotEmpty()) {
                val anyTriggerMatched = program.whenClause.triggers.any { trigger ->
                    evalTriggerCondition(trigger, signal)
                }
                if (anyTriggerMatched) return true
            }
        }

        if (rule.conditionType != signal.conditionType) return false

        return when (rule.conditionType) {
            ConditionType.NOTIFICATION -> {
                // 1. Target App match
                val pkgMatches = rule.targetPackages.contains("*") ||
                    rule.targetPackages.contains(signal.sourcePackage) ||
                    rule.targetPackages.any { it.equals(signal.sourcePackage, ignoreCase = true) } ||
                    rule.targetPackageName.isBlank() ||
                    rule.targetPackageName == "*" ||
                    signal.sourcePackage.equals(rule.targetPackageName, ignoreCase = true)
                if (!pkgMatches) return false

                // 2. Progress Bar requirement
                if (rule.requireProgress) {
                    val hasValidProgress = signal.progress != null &&
                        signal.progressMax != null &&
                        signal.progressMax > 0
                    if (!hasValidProgress) return false
                }

                // 3. Granular Sender / Title Filter (Essential Glyphs logic)
                if (rule.senderFilterOperator != TextMatchOperator.ANY && rule.senderFilterPattern.isNotBlank()) {
                    if (!matchesText(signal.title, rule.senderFilterOperator, rule.senderFilterPattern)) {
                        return false
                    }
                }

                // 4. Granular Message Body Filter (Essential Glyphs logic)
                if (rule.bodyFilterOperator != TextMatchOperator.ANY && rule.bodyFilterPattern.isNotBlank()) {
                    val bodyMatched = matchesText(signal.content, rule.bodyFilterOperator, rule.bodyFilterPattern) ||
                        matchesText(signal.subtext, rule.bodyFilterOperator, rule.bodyFilterPattern)
                    if (!bodyMatched) {
                        return false
                    }
                }

                // 5. Combined / Legacy Text Match Filter
                if (rule.textMatchOperator != TextMatchOperator.ANY && rule.textMatchPattern.isNotBlank()) {
                    val candidateTexts = when (rule.textMatchField) {
                        TextMatchField.ANY -> listOf(signal.title, signal.content, signal.subtext)
                        TextMatchField.TITLE -> listOf(signal.title)
                        TextMatchField.CONTENT -> listOf(signal.content)
                        TextMatchField.SUBTEXT -> listOf(signal.subtext)
                    }
                    val legacyMatched = candidateTexts.any { candidate ->
                        matchesText(candidate, rule.textMatchOperator, rule.textMatchPattern)
                    }
                    if (!legacyMatched) return false
                }

                true
            }

            ConditionType.BATTERY_LEVEL -> {
                val level = signal.batteryLevel ?: return false
                val chargingOk = when (rule.chargingRequirement) {
                    ChargingRequirement.ANY -> true
                    ChargingRequirement.CHARGING_ONLY -> signal.isCharging == true
                    ChargingRequirement.DISCHARGING_ONLY -> signal.isCharging == false
                }
                chargingOk && compareInt(level, rule.numericComparison, rule.numericThreshold)
            }

            ConditionType.CHARGING_STATE -> {
                when (rule.chargingRequirement) {
                    ChargingRequirement.ANY -> signal.isCharging != null
                    ChargingRequirement.CHARGING_ONLY -> signal.isCharging == true
                    ChargingRequirement.DISCHARGING_ONLY -> signal.isCharging == false
                }
            }

            ConditionType.BLUETOOTH_STATE,
            ConditionType.WIFI_STATE,
            ConditionType.HOTSPOT_STATE,
            ConditionType.TORCH_STATE,
            ConditionType.AIRPLANE_MODE -> {
                when (rule.systemToggleFilter) {
                    SystemToggleFilter.ANY_CHANGE -> true
                    SystemToggleFilter.ENABLED_ON -> signal.toggleActive == true
                    SystemToggleFilter.DISABLED_OFF -> signal.toggleActive == false
                    else -> true
                }
            }

            ConditionType.RINGER_MODE -> {
                when (rule.systemToggleFilter) {
                    SystemToggleFilter.ANY_CHANGE -> true
                    SystemToggleFilter.RINGER_SILENT,
                    SystemToggleFilter.RINGER_VIBRATE,
                    SystemToggleFilter.RINGER_NORMAL -> signal.ringerMode == rule.systemToggleFilter
                    else -> true
                }
            }

            ConditionType.LOW_POWER_MODE -> signal.isLowPowerMode == true
            ConditionType.MEDIA_PLAYBACK -> signal.mediaState == rule.mediaTriggerState
            ConditionType.SCREEN_STATE -> signal.screenState == rule.screenTriggerState
            ConditionType.FACE_DOWN -> signal.isFaceDown == true
            ConditionType.TIMER_ALARM -> signal.isTimerOrAlarmActive == true

            ConditionType.SYSTEM_STATE -> {
                val systemVars = GlobalSystemStateCache.getAll()
                if (rule.glyphScriptSource.isNotBlank()) {
                    val (program, _) = com.example.dsl.GlyphScriptCompiler.parseProgramOnly(rule.glyphScriptSource)
                    if (program != null) {
                        val triggerEvt = program.whenClause.triggerEvent
                        when {
                            triggerEvt.contains("==") -> {
                                val varName = triggerEvt.substringBefore("==").trim().removePrefix("system.").trim()
                                val expected = triggerEvt.substringAfter("==").trim().removeSurrounding("\"").removeSurrounding("'")
                                val actual = systemVars[varName] ?: systemVars["system.$varName"] ?: ""
                                actual.equals(expected, ignoreCase = true)
                            }
                            triggerEvt.contains("<=") -> {
                                val varName = triggerEvt.substringBefore("<=").trim().removePrefix("system.").trim()
                                val expected = triggerEvt.substringAfter("<=").trim().toDoubleOrNull() ?: 0.0
                                val actual = (systemVars[varName] ?: systemVars["system.$varName"] ?: "0").toDoubleOrNull() ?: 0.0
                                actual <= expected
                            }
                            triggerEvt.contains("<") -> {
                                val varName = triggerEvt.substringBefore("<").trim().removePrefix("system.").trim()
                                val expected = triggerEvt.substringAfter("<").trim().toDoubleOrNull() ?: 0.0
                                val actual = (systemVars[varName] ?: systemVars["system.$varName"] ?: "0").toDoubleOrNull() ?: 0.0
                                actual < expected
                            }
                            triggerEvt.contains(">=") -> {
                                val varName = triggerEvt.substringBefore(">=").trim().removePrefix("system.").trim()
                                val expected = triggerEvt.substringAfter(">=").trim().toDoubleOrNull() ?: 0.0
                                val actual = (systemVars[varName] ?: systemVars["system.$varName"] ?: "0").toDoubleOrNull() ?: 0.0
                                actual >= expected
                            }
                            triggerEvt.contains(">") -> {
                                val varName = triggerEvt.substringBefore(">").trim().removePrefix("system.").trim()
                                val expected = triggerEvt.substringAfter(">").trim().toDoubleOrNull() ?: 0.0
                                val actual = (systemVars[varName] ?: systemVars["system.$varName"] ?: "0").toDoubleOrNull() ?: 0.0
                                actual > expected
                            }
                            else -> true
                        }
                    } else true
                } else true
            }

            ConditionType.KEY_PRESS -> {
                if (rule.sequenceEvents.isNotEmpty()) {
                    GlobalSystemStateCache.matchesSequence(rule.sequenceEvents, rule.sequenceTimeoutMs)
                } else {
                    signal.title.isNotBlank() && (signal.title == rule.name || signal.title.contains("key", ignoreCase = true))
                }
            }
        }
    }

    private fun evalTriggerCondition(trigger: com.example.dsl.TriggerCondition, signal: SystemSignalEvent): Boolean {
        val sysVars = GlobalSystemStateCache.getAll()
        val combinedPayload = signal.payload + sysVars

        return when (trigger.triggerType) {
            com.example.dsl.TriggerType.NOTIFICATION -> {
                if (signal.conditionType != ConditionType.NOTIFICATION) return false
                trigger.targetPackages.contains("*") ||
                    trigger.targetPackages.contains(signal.sourcePackage) ||
                    trigger.targetPackages.any { it.equals(signal.sourcePackage, ignoreCase = true) }
            }

            com.example.dsl.TriggerType.SYSTEM_PROPERTY -> {
                val rawProp = trigger.propertyName.lowercase()
                val prop = rawProp.removePrefix("system.")
                val propValueRaw = combinedPayload[trigger.propertyName]
                    ?: combinedPayload["system.$prop"]
                    ?: combinedPayload[prop]
                    ?: when (prop) {
                        "bluetooth" -> if (signal.conditionType == ConditionType.BLUETOOTH_STATE) (if (signal.toggleActive == true) "connected" else "disconnected") else null
                        "power", "charging" -> if (signal.conditionType == ConditionType.CHARGING_STATE) (if (signal.isCharging == true) "charging" else "discharging") else null
                        "battery_level", "battery" -> if (signal.conditionType == ConditionType.BATTERY_LEVEL) signal.batteryLevel?.toString() else null
                        "ringer" -> if (signal.conditionType == ConditionType.RINGER_MODE) signal.ringerMode?.name?.lowercase() else null
                        "wifi" -> if (signal.conditionType == ConditionType.WIFI_STATE) (if (signal.toggleActive == true) "connected" else "disconnected") else null
                        "airplane_mode" -> if (signal.conditionType == ConditionType.AIRPLANE_MODE) (if (signal.toggleActive == true) "on" else "off") else null
                        else -> null
                    }
                val propStr = propValueRaw?.toString() ?: ""

                val isMatchingSignal = when (prop) {
                    "bluetooth" -> signal.conditionType == ConditionType.BLUETOOTH_STATE || signal.conditionType == ConditionType.SYSTEM_STATE
                    "power", "charging" -> signal.conditionType == ConditionType.CHARGING_STATE || signal.conditionType == ConditionType.BATTERY_LEVEL || signal.conditionType == ConditionType.SYSTEM_STATE
                    "battery_level", "battery" -> signal.conditionType == ConditionType.BATTERY_LEVEL || signal.conditionType == ConditionType.CHARGING_STATE || signal.conditionType == ConditionType.SYSTEM_STATE
                    "ringer" -> signal.conditionType == ConditionType.RINGER_MODE || signal.conditionType == ConditionType.SYSTEM_STATE
                    "wifi" -> signal.conditionType == ConditionType.WIFI_STATE || signal.conditionType == ConditionType.SYSTEM_STATE
                    else -> true
                }
                if (!isMatchingSignal && propStr.isBlank()) return false

                when (trigger.operator) {
                    "CHANGED" -> propStr.isNotBlank() || isMatchingSignal
                    "==" -> propStr.equals(trigger.expectedValue, ignoreCase = true)
                    "!=" -> !propStr.equals(trigger.expectedValue, ignoreCase = true)
                    "<" -> (propStr.toDoubleOrNull() ?: 0.0) < (trigger.expectedValue.toDoubleOrNull() ?: 0.0)
                    "<=" -> (propStr.toDoubleOrNull() ?: 0.0) <= (trigger.expectedValue.toDoubleOrNull() ?: 0.0)
                    ">" -> (propStr.toDoubleOrNull() ?: 0.0) > (trigger.expectedValue.toDoubleOrNull() ?: 0.0)
                    ">=" -> (propStr.toDoubleOrNull() ?: 0.0) >= (trigger.expectedValue.toDoubleOrNull() ?: 0.0)
                    else -> propStr.isNotBlank()
                }
            }

            com.example.dsl.TriggerType.APP_FOREGROUND -> {
                val currentFg = sysVars["foreground_app"] ?: sysVars["app.foreground"] ?: signal.sourcePackage
                trigger.targetPackages.contains("*") ||
                    trigger.targetPackages.contains(currentFg) ||
                    trigger.targetPackages.any { it.equals(currentFg, ignoreCase = true) } ||
                    currentFg.equals(trigger.expectedValue, ignoreCase = true)
            }
        }
    }

    private fun matchesText(text: String, operator: TextMatchOperator, pattern: String): Boolean {
        val cleaned = com.example.dsl.GlyphFrameCompositor.sanitizeTextForRegex(text)
        return when (operator) {
            TextMatchOperator.ANY -> true
            TextMatchOperator.EQUALS -> cleaned.equals(pattern, ignoreCase = true) || text.equals(pattern, ignoreCase = true)
            TextMatchOperator.CONTAINS -> cleaned.contains(pattern, ignoreCase = true) || text.contains(pattern, ignoreCase = true)
            TextMatchOperator.STARTS_WITH -> cleaned.startsWith(pattern, ignoreCase = true) || text.startsWith(pattern, ignoreCase = true)
            TextMatchOperator.REGEX -> {
                runCatching {
                    val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                    regex.containsMatchIn(cleaned) || regex.containsMatchIn(text)
                }.getOrDefault(false)
            }
        }
    }

    private fun compareInt(actual: Int, op: ComparisonOperator, target: Int): Boolean {
        return when (op) {
            ComparisonOperator.LESS_THAN -> actual < target
            ComparisonOperator.LESS_OR_EQUAL -> actual <= target
            ComparisonOperator.EQUALS -> actual == target
            ComparisonOperator.GREATER_OR_EQUAL -> actual >= target
            ComparisonOperator.GREATER_THAN -> actual > target
        }
    }

    private fun buildExtrasSummary(signal: SystemSignalEvent): String {
        val parts = mutableListOf<String>()
        if (signal.progress != null && signal.progressMax != null) {
            parts.add("EXTRA_PROGRESS=${signal.progress}/${signal.progressMax}")
        }
        if (signal.subtext.isNotBlank()) {
            parts.add("subtext=\"${signal.subtext}\"")
        }
        if (signal.batteryLevel != null) {
            parts.add("battery=${signal.batteryLevel}%")
        }
        if (signal.isCharging != null) {
            parts.add("charging=${signal.isCharging}")
        }
        if (signal.toggleActive != null) {
            parts.add("toggleActive=${signal.toggleActive}")
        }
        if (signal.ringerMode != null) {
            parts.add("ringer=${signal.ringerMode.name}")
        }
        if (signal.isFaceDown != null) {
            parts.add("faceDown=${signal.isFaceDown}")
        }
        if (signal.isSimulated) {
            parts.add("SIMULATED")
        }
        return parts.joinToString(" | ").ifBlank { "standard_extras" }
    }

    private fun roundLatency(ms: Double): Double {
        return ((ms * 100.0).roundToInt() / 100.0).coerceAtLeast(0.05)
    }

    companion object {
        fun sanitizeTextForRegex(raw: String): String {
            return com.example.dsl.GlyphFrameCompositor.sanitizeTextForRegex(raw)
        }

        fun formatCompactUnitText(rawText: String, suffix: String = ""): String {
            return com.example.dsl.GlyphFrameCompositor.formatCompactUnitText(rawText, suffix)
        }
    }
}
