package com.example.engine

import com.example.domain.model.AuditLogEntry
import com.example.domain.model.AuditStatus
import com.example.domain.model.ConditionType
import com.example.domain.model.HardwareBinderAuditEntry
import com.example.domain.model.NotificationPayloadAuditEntry
import com.example.domain.model.ScriptCompileAuditEntry
import com.example.domain.model.SystemSignalEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thread-safe event ingestion bus (`SharedFlow`), active state clearing bus, and zero-overhead
 * rolling Audit & Diagnostics Ring Buffer for:
 * - Rule Evaluation Events (`auditLogs`)
 * - GlyphScript v2 Parse & Package Resolution History (`scriptCompileHistory`)
 * - Direct Hardware Binder Call Telemetry (`hardwareBinderLogs`)
 * - Live Notification Payload & Regex Extraction Inspector (`payloadInspectorLogs`)
 */
object EventIngestBus {
    private const val MAX_AUDIT_ENTRIES = 100
    private const val MAX_BINDER_ENTRIES = 60

    private val _signalFlow = MutableSharedFlow<SystemSignalEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val signalFlow: SharedFlow<SystemSignalEvent> = _signalFlow.asSharedFlow()

    // Emits packageName when a notification is removed or "MEDIA_STOPPED" when playback ceases
    private val _clearSourceFlow = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val clearSourceFlow: SharedFlow<String> = _clearSourceFlow.asSharedFlow()

    private val _auditLogs = MutableStateFlow<List<AuditLogEntry>>(emptyList())
    val auditLogs: StateFlow<List<AuditLogEntry>> = _auditLogs.asStateFlow()

    private val _scriptCompileHistory = MutableStateFlow<List<ScriptCompileAuditEntry>>(emptyList())
    val scriptCompileHistory: StateFlow<List<ScriptCompileAuditEntry>> = _scriptCompileHistory.asStateFlow()

    private val _hardwareBinderLogs = MutableStateFlow<List<HardwareBinderAuditEntry>>(emptyList())
    val hardwareBinderLogs: StateFlow<List<HardwareBinderAuditEntry>> = _hardwareBinderLogs.asStateFlow()

    private val _payloadInspectorLogs = MutableStateFlow<List<NotificationPayloadAuditEntry>>(emptyList())
    val payloadInspectorLogs: StateFlow<List<NotificationPayloadAuditEntry>> = _payloadInspectorLogs.asStateFlow()

    private val _extractFallbackWarnings = MutableStateFlow<List<String>>(emptyList())
    val extractFallbackWarnings: StateFlow<List<String>> = _extractFallbackWarnings.asStateFlow()

    private val _globalEngineEnabled = MutableStateFlow(true)
    val globalEngineEnabled: StateFlow<Boolean> = _globalEngineEnabled.asStateFlow()

    private val _isForegroundServiceRunning = MutableStateFlow(false)
    val isForegroundServiceRunning: StateFlow<Boolean> = _isForegroundServiceRunning.asStateFlow()

    private val _isNotificationListenerConnected = MutableStateFlow(false)
    val isNotificationListenerConnected: StateFlow<Boolean> = _isNotificationListenerConnected.asStateFlow()

    private val _isNotificationSnifferActive = MutableStateFlow(false)
    val isNotificationSnifferActive: StateFlow<Boolean> = _isNotificationSnifferActive.asStateFlow()

    private val _capturedSnifferNotification = MutableStateFlow<NotificationPayloadAuditEntry?>(null)
    val capturedSnifferNotification: StateFlow<NotificationPayloadAuditEntry?> = _capturedSnifferNotification.asStateFlow()

    fun setNotificationSnifferActive(active: Boolean) {
        _isNotificationSnifferActive.value = active
    }

    fun captureSnifferNotification(entry: NotificationPayloadAuditEntry) {
        _capturedSnifferNotification.value = entry
    }

    fun clearCapturedSnifferNotification() {
        _capturedSnifferNotification.value = null
    }

    fun emitSignal(event: SystemSignalEvent) {
        _signalFlow.tryEmit(event)
    }

    fun emitClearForSource(sourceIdentifier: String) {
        _clearSourceFlow.tryEmit(sourceIdentifier)
    }

    fun recordAuditEntry(entry: AuditLogEntry) {
        _auditLogs.update { current ->
            prependBounded(current, entry, MAX_AUDIT_ENTRIES)
        }
    }

    fun recordScriptCompileEvent(entry: ScriptCompileAuditEntry) {
        _scriptCompileHistory.update { current ->
            prependBounded(current, entry, MAX_AUDIT_ENTRIES)
        }
    }

    fun recordHardwareBinderEvent(entry: HardwareBinderAuditEntry) {
        _hardwareBinderLogs.update { current ->
            prependBounded(current, entry, MAX_BINDER_ENTRIES)
        }
    }

    fun recordPayloadInspectorEvent(entry: NotificationPayloadAuditEntry) {
        _payloadInspectorLogs.update { current ->
            prependBounded(current, entry, MAX_AUDIT_ENTRIES)
        }
    }

    fun recordExtractFallbackWarning(warningMessage: String) {
        _extractFallbackWarnings.update { current ->
            prependBounded(current, warningMessage, MAX_AUDIT_ENTRIES)
        }
    }

    private val _carouselEvents = MutableStateFlow<List<String>>(emptyList())
    val carouselEvents: StateFlow<List<String>> = _carouselEvents.asStateFlow()

    fun recordHardwareCycleEvent(fromPkg: String, toPkg: String) {
        val msg = "HW_CYCLE_TRIGGER: Triggered via Volume Key, switched from [$fromPkg] to [$toPkg]."
        _carouselEvents.update { prependBounded(it, msg, MAX_AUDIT_ENTRIES) }
        recordAuditEntry(
            AuditLogEntry(
                eventType = ConditionType.NOTIFICATION,
                sourceSummary = "Hardware Carousel (Volume Down Key)",
                extrasSummary = "from=$fromPkg | to=$toPkg",
                actionFiredSummary = msg,
                frameName = "CAROUSEL_SWAP",
                status = AuditStatus.TRIGGERED
            )
        )
    }

    fun recordIntrusionStart(pkg: String, durationMs: Long) {
        val msg = "INTRUSION_START: Hijacked display for [$pkg] (${durationMs}ms)."
        _carouselEvents.update { prependBounded(it, msg, MAX_AUDIT_ENTRIES) }
        recordAuditEntry(
            AuditLogEntry(
                eventType = ConditionType.NOTIFICATION,
                sourceSummary = "Transient Intrusion Layer",
                extrasSummary = "pkg=$pkg | duration=${durationMs}ms",
                actionFiredSummary = msg,
                frameName = "INTRUSION_ALERT",
                status = AuditStatus.TRIGGERED
            )
        )
    }

    fun recordIntrusionEnd(restoredPkg: String) {
        val msg = "INTRUSION_END: Restored base task [$restoredPkg]."
        _carouselEvents.update { prependBounded(it, msg, MAX_AUDIT_ENTRIES) }
        recordAuditEntry(
            AuditLogEntry(
                eventType = ConditionType.NOTIFICATION,
                sourceSummary = "Transient Intrusion Disposed",
                extrasSummary = "restored=$restoredPkg",
                actionFiredSummary = msg,
                frameName = "BASE_TASK_RESTORED",
                status = AuditStatus.TRIGGERED
            )
        )
    }

    fun recordMultiAppRuleMatch(ruleName: String, triggeringPackage: String) {
        val msg = "RULE_MATCH: \"$ruleName\" triggered by incoming package: [$triggeringPackage]"
        _payloadInspectorLogs.update { current ->
            val idx = current.indexOfFirst { it.packageName.equals(triggeringPackage, ignoreCase = true) }
            if (idx >= 0) {
                val mutable = current.toMutableList()
                val existing = mutable[idx].extractedVariablesSummary
                val updated = if (existing.isBlank()) msg else if (existing.contains("RULE_MATCH:")) existing else "$msg | $existing"
                mutable[idx] = mutable[idx].copy(extractedVariablesSummary = updated)
                mutable
            } else {
                val newEntry = NotificationPayloadAuditEntry(
                    packageName = triggeringPackage,
                    appLabel = triggeringPackage,
                    rawExtraTitle = "",
                    rawExtraText = "",
                    rawExtraSubText = "",
                    rawExtraProgress = null,
                    rawExtraProgressMax = null,
                    computedProgressPercent = null,
                    extractedVariablesSummary = msg
                )
                prependBounded(current, newEntry, MAX_AUDIT_ENTRIES)
            }
        }
    }

    fun updateLatestPayloadExtraction(packageName: String, extractedSummary: String) {
        _payloadInspectorLogs.update { current ->
            if (current.isEmpty()) return@update current
            val idx = current.indexOfFirst { it.packageName.equals(packageName, ignoreCase = true) }
            if (idx < 0) return@update current
            val mutable = current.toMutableList()
            val existing = mutable[idx].extractedVariablesSummary
            val finalSummary = if (existing.contains("RULE_MATCH:") && !extractedSummary.contains("RULE_MATCH:")) {
                val ruleMatchPrefix = existing.substringBefore(" | ")
                if (ruleMatchPrefix.startsWith("RULE_MATCH:")) {
                    "$ruleMatchPrefix | $extractedSummary"
                } else {
                    extractedSummary
                }
            } else {
                extractedSummary
            }
            mutable[idx] = mutable[idx].copy(extractedVariablesSummary = finalSummary)
            mutable
        }
    }

    fun clearAuditLogs() {
        _auditLogs.value = emptyList()
        _scriptCompileHistory.value = emptyList()
        _hardwareBinderLogs.value = emptyList()
        _payloadInspectorLogs.value = emptyList()
        _extractFallbackWarnings.value = emptyList()
    }

    fun setGlobalEngineEnabled(enabled: Boolean) {
        _globalEngineEnabled.value = enabled
    }

    fun setForegroundServiceRunning(running: Boolean) {
        _isForegroundServiceRunning.value = running
    }

    fun setNotificationListenerConnected(connected: Boolean) {
        _isNotificationListenerConnected.value = connected
    }

    /**
     * Formats the complete diagnostic state (Hardware Session, Script Parse History,
     * Payload Inspector, Binder Telemetry, and Rule Audit Log) for one-tap clipboard export.
     */
    fun buildFullDiagnosticsExportText(hardwareSummary: String): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val sb = StringBuilder()
        sb.appendLine("=== GLYPHFLOW NP3 DIAGNOSTICS EXPORT ===")
        sb.appendLine("Exported At: ${fmt.format(Date())}")
        sb.appendLine("Engine Armed: ${_globalEngineEnabled.value} | FGS Daemon: ${_isForegroundServiceRunning.value} | NLS Connected: ${_isNotificationListenerConnected.value}")
        sb.appendLine()
        sb.appendLine("--- 1. HARDWARE SESSION STATUS ---")
        sb.appendLine(hardwareSummary)
        sb.appendLine()
        sb.appendLine("--- 2. GLYPHSCRIPT V2 PARSE & PACKAGE RESOLUTION HISTORY (${_scriptCompileHistory.value.size}) ---")
        for (item in _scriptCompileHistory.value.take(25)) {
            sb.appendLine("[${fmt.format(Date(item.timestamp))}] Rule=\"${item.ruleName}\" | AST=${item.astStatus} | Pkg=${item.packageResolutionOutcome}")
            if (!item.errorMessage.isNullOrBlank()) {
                sb.appendLine("   Error: ${item.errorMessage}")
            }
        }
        sb.appendLine()
        sb.appendLine("--- 3. NOTIFICATION PAYLOAD INSPECTOR (${_payloadInspectorLogs.value.size}) ---")
        for (p in _payloadInspectorLogs.value.take(25)) {
            sb.appendLine("[${fmt.format(Date(p.timestamp))}] ${p.appLabel} (${p.packageName})")
            sb.appendLine("   EXTRA_TITLE=\"${p.rawExtraTitle}\" | EXTRA_TEXT=\"${p.rawExtraText}\" | EXTRA_SUB_TEXT=\"${p.rawExtraSubText}\"")
            sb.appendLine("   EXTRA_PROGRESS=${p.rawExtraProgress}/${p.rawExtraProgressMax} (${p.computedProgressPercent ?: 0}%) | Extracted: ${p.extractedVariablesSummary.ifBlank { "none" }}")
        }
        if (_extractFallbackWarnings.value.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("--- 3B. EXTRACT FALLBACK WARNINGS (${_extractFallbackWarnings.value.size}) ---")
            for (w in _extractFallbackWarnings.value.take(25)) {
                sb.appendLine("   $w")
            }
        }
        sb.appendLine()
        sb.appendLine("--- 4. HARDWARE BINDER CALL TELEMETRY (${_hardwareBinderLogs.value.size}) ---")
        for (b in _hardwareBinderLogs.value.take(25)) {
            sb.appendLine("[${fmt.format(Date(b.timestamp))}] ${b.binderMethod} | Frame=\"${b.frameLabel}\" | NonZero=${b.nonZeroPixelCount}/625 | Peak=${b.peakBrightness} | Latency=${b.latencyMs}ms")
        }
        sb.appendLine()
        sb.appendLine("--- 5. RULE EVALUATION AUDIT LOG (${_auditLogs.value.size}) ---")
        for (a in _auditLogs.value.take(30)) {
            sb.appendLine("[${fmt.format(Date(a.timestamp))}] [${a.status.badgeText}] ${a.sourceSummary} -> ${a.matchedRuleName ?: "Unmatched"} (${a.actionFiredSummary}) @ ${a.renderLatencyMs}ms")
        }
        return sb.toString()
    }

    private fun <T> prependBounded(current: List<T>, entry: T, maxCount: Int): List<T> {
        val updated = ArrayList<T>(minOf(current.size + 1, maxCount))
        updated.add(entry)
        val keepCount = minOf(current.size, maxCount - 1)
        for (i in 0 until keepCount) {
            updated.add(current[i])
        }
        return updated
    }
}

typealias AuditDiagnosticsBuffer = EventIngestBus
