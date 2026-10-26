package com.example.domain.model

import java.util.UUID

const val MATRIX_SIZE = 25
const val MATRIX_CELL_COUNT = MATRIX_SIZE * MATRIX_SIZE // 625

/**
 * Single 25x25 Glyph Matrix keyframe.
 * Each byte in [pixels] stores unsigned LED brightness in range 0..255 (stored as signed Byte).
 */
data class GlyphFrame(
    val id: String = UUID.randomUUID().toString(),
    val durationMs: Long = 120L,
    val pixels: ByteArray = ByteArray(MATRIX_CELL_COUNT)
) {
    fun getBrightness(row: Int, col: Int): Int {
        if (row !in 0 until MATRIX_SIZE || col !in 0 until MATRIX_SIZE) return 0
        return pixels[row * MATRIX_SIZE + col].toInt() and 0xFF
    }

    fun withPixel(row: Int, col: Int, brightness: Int): GlyphFrame {
        if (row !in 0 until MATRIX_SIZE || col !in 0 until MATRIX_SIZE) return this
        val copy = pixels.copyOf()
        copy[row * MATRIX_SIZE + col] = brightness.coerceIn(0, 255).toByte()
        return copy(pixels = copy)
    }

    fun averageBrightness(): Int {
        if (pixels.isEmpty()) return 0
        var sum = 0
        for (b in pixels) {
            sum += (b.toInt() and 0xFF)
        }
        return sum / pixels.size
    }

    fun peakBrightness(): Int {
        var max = 0
        for (b in pixels) {
            val v = b.toInt() and 0xFF
            if (v > max) max = v
        }
        return max
    }

    fun activeDotCount(): Int {
        var count = 0
        for (b in pixels) {
            if ((b.toInt() and 0xFF) > 0) count++
        }
        return count
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlyphFrame) return false
        return id == other.id &&
            durationMs == other.durationMs &&
            pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}

enum class TransitionCurve(val displayName: String, val code: String) {
    HARD_CUT("HARD CUT", "HARD_CUT"),
    STEP_FADE("STEP FADE", "STEP_FADE")
}

/**
 * Multi-frame (or single-frame) 25x25 animation stored in Room Database.
 */
data class GlyphAnimation(
    val id: Long = 0L,
    val name: String,
    val tags: List<String> = emptyList(),
    val frames: List<GlyphFrame> = listOf(GlyphFrame()),
    val defaultFrameIntervalMs: Long = 120L,
    val loopCount: Int = 0, // 0 = infinite loop, 1..N = fixed cycle count
    val transitionCurve: TransitionCurve = TransitionCurve.STEP_FADE,
    val isPreset: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isMultiFrame: Boolean
        get() = frames.size > 1
}

/**
 * Canvas matrix geometric transformations for the Studio editor.
 */
enum class MatrixTransform {
    CLEAR,
    INVERT,
    ROTATE_90,
    SHIFT_UP,
    SHIFT_DOWN,
    SHIFT_LEFT,
    SHIFT_RIGHT,
    MIRROR_HORIZONTAL,
    MIRROR_VERTICAL
}

/**
 * Event Ingestion Condition Types for the Universal IFTTT & Essential Glyph Engine.
 */
enum class ConditionType(val label: String, val category: String) {
    NOTIFICATION("Notification Received (Essential Glyph)", "NOTIFICATIONS"),
    MEDIA_PLAYBACK("Audio / Media Playing", "MEDIA"),
    BATTERY_LEVEL("Battery Percentage Threshold", "TELEMETRY"),
    CHARGING_STATE("Charging State Change", "TELEMETRY"),
    BLUETOOTH_STATE("Bluetooth State Changed", "QUICK SETTINGS"),
    WIFI_STATE("Wi-Fi State Changed", "QUICK SETTINGS"),
    RINGER_MODE("Ringer Mode (Silent / Vibrate / Normal)", "QUICK SETTINGS"),
    HOTSPOT_STATE("Hotspot State Toggled", "QUICK SETTINGS"),
    TORCH_STATE("Flashlight / Torch Toggled", "QUICK SETTINGS"),
    AIRPLANE_MODE("Airplane Mode Toggled", "QUICK SETTINGS"),
    TIMER_ALARM("Timer / Stopwatch / Alarm", "SYSTEM"),
    FACE_DOWN("Flip to Glyph (Face Down)", "SENSORS"),
    SCREEN_STATE("Display Screen On / Off", "SYSTEM"),
    LOW_POWER_MODE("Low Power / Battery Saver", "TELEMETRY"),
    SYSTEM_STATE("System State / Hardware Trigger", "SYSTEM"),
    KEY_PRESS("Hardware Key / Button Sequence", "HARDWARE")
}

enum class SystemToggleFilter(val label: String) {
    ANY_CHANGE("Any State Change"),
    ENABLED_ON("Turned ON / Active"),
    DISABLED_OFF("Turned OFF / Inactive"),
    RINGER_SILENT("Silent Mode"),
    RINGER_VIBRATE("Vibrate Mode"),
    RINGER_NORMAL("Normal Sound Mode")
}

enum class TextMatchOperator(val label: String) {
    ANY("Any Text (No Filter)"),
    CONTAINS("Contains"),
    EQUALS("Exact Match"),
    STARTS_WITH("Starts With"),
    REGEX("Custom Regex")
}

enum class TextMatchField(val label: String) {
    ANY("Sender/Title, Body, or Subtext"),
    TITLE("Sender / Title Only"),
    CONTENT("Message Body Only"),
    SUBTEXT("Subtext Only")
}

enum class ComparisonOperator(val symbol: String, val label: String) {
    LESS_THAN("<", "Less Than"),
    LESS_OR_EQUAL("<=", "At Most"),
    EQUALS("==", "Exactly"),
    GREATER_OR_EQUAL(">=", "At Least"),
    GREATER_THAN(">", "Greater Than")
}

enum class ChargingRequirement(val label: String) {
    ANY("Any Power State"),
    CHARGING_ONLY("While Charging"),
    DISCHARGING_ONLY("While Unplugged")
}

enum class MediaTriggerState(val label: String) {
    PLAYING("Playback Started / Playing"),
    PAUSED("Playback Paused"),
    TRACK_CHANGED("Track Changed")
}

enum class ScreenTriggerState(val label: String) {
    SCREEN_ON("Screen Turned On"),
    SCREEN_OFF("Screen Turned Off")
}

/**
 * Hardware Matrix Action Types executed when a rule triggers.
 */
enum class ActionType(val label: String, val description: String) {
    PLAY_ANIMATION("Custom Matrix Animation", "Stream 25x25 keyframe sequence at 30 FPS"),
    SHOW_SINGLE_FRAME("Static Essential Glyph", "Hold static 25x25 emblem with continuous keep-alive"),
    AUDIO_WAVEFORM("Audio Waveform Shader", "Real-time 30 FPS circular audio visualizer"),
    MATRIX_METER("Dynamic Progress / Battery Ring", "Fill circular ring or bar from % or progress"),
    GLYPH_SCRIPT_COMPOSITOR("GlyphScript v2 Multi-Layer", "Dynamic regex extraction & multi-layer 25x25 compositor")
}

enum class MeterStyle(val label: String) {
    PERIMETER_RING("Perimeter Ring"),
    HORIZONTAL_BAR("Horizontal Progress Bar"),
    VERTICAL_BAR("Vertical Power Column"),
    RADIAL_CORE("Radial Core Expansion")
}

/**
 * Complete IFTTT & Essential Glyph Automation Rule linking a system condition to a 25x25 Matrix action.
 */
data class TriggerRule(
    val id: Long = 0L,
    val name: String,
    val isEnabled: Boolean = true,
    val priority: Int = 5, // 1 (lowest) to 10 (highest; preempts lower priority rules)
    val cooldownMs: Long = 1000L, // Anti-strobe debounce interval
    val conditionType: ConditionType = ConditionType.NOTIFICATION,
    // Essential Notification filter parameters
    val targetPackageName: String = "*", // "*" matches any package
    val targetPackages: List<String> = if (targetPackageName.contains(",")) {
        targetPackageName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    } else if (targetPackageName.isBlank() || targetPackageName == "*") {
        listOf("*")
    } else {
        listOf(targetPackageName)
    },
    val targetAppLabel: String = "Any App (*)",
    val senderFilterOperator: TextMatchOperator = TextMatchOperator.ANY,
    val senderFilterPattern: String = "",
    val bodyFilterOperator: TextMatchOperator = TextMatchOperator.ANY,
    val bodyFilterPattern: String = "",
    val essentialPriorityOverride: Boolean = false, // Forces highest priority (P10) and holds until notification dismissed
    // Legacy / combined text filter parameters (kept for compatibility)
    val textMatchField: TextMatchField = TextMatchField.ANY,
    val textMatchOperator: TextMatchOperator = TextMatchOperator.ANY,
    val textMatchPattern: String = "",
    val requireProgress: Boolean = false,
    // Telemetry, Hardware & Quick Settings parameters
    val numericComparison: ComparisonOperator = ComparisonOperator.LESS_OR_EQUAL,
    val numericThreshold: Int = 20, // e.g., Battery % threshold
    val chargingRequirement: ChargingRequirement = ChargingRequirement.ANY,
    val mediaTriggerState: MediaTriggerState = MediaTriggerState.PLAYING,
    val screenTriggerState: ScreenTriggerState = ScreenTriggerState.SCREEN_OFF,
    val systemToggleFilter: SystemToggleFilter = SystemToggleFilter.ANY_CHANGE,
    // Action execution parameters
    val actionType: ActionType = ActionType.PLAY_ANIMATION,
    val targetAnimationId: Long = 0L,
    val targetAnimationName: String = "Custom Matrix",
    val holdUntilConditionClears: Boolean = true,
    val overrideDurationMs: Long = 4000L,
    val overrideIntervalMs: Long = 100L,
    val overrideLoopCount: Int = 0, // 0 = loop until duration expires / condition clears
    val overrideTransitionCurve: TransitionCurve = TransitionCurve.STEP_FADE,
    val meterStyle: MeterStyle = MeterStyle.PERIMETER_RING,
    val meterPeakBrightness: Int = 255,
    val glyphScriptSource: String = "",
    val taskMode: String = "ongoing", // "ongoing" (base ring buffer) vs "transient" (intrusion queue)
    val intrudeDurationMs: Long = 4000L,
    val thumbnailBytes: ByteArray = ByteArray(0),
    val updatedAt: Long = 0L,
    val sequenceEvents: List<String> = emptyList(),
    val sequenceTimeoutMs: Long = 2000L
) {
    val effectivePriority: Int
        get() = if (essentialPriorityOverride) 10 else priority.coerceIn(1, 10)

    val isTransient: Boolean
        get() = taskMode.equals("transient", ignoreCase = true) || !holdUntilConditionClears

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TriggerRule) return false
        if (id != other.id) return false
        if (name != other.name) return false
        if (isEnabled != other.isEnabled) return false
        if (priority != other.priority) return false
        if (cooldownMs != other.cooldownMs) return false
        if (conditionType != other.conditionType) return false
        if (targetPackageName != other.targetPackageName) return false
        if (targetAppLabel != other.targetAppLabel) return false
        if (senderFilterOperator != other.senderFilterOperator) return false
        if (senderFilterPattern != other.senderFilterPattern) return false
        if (bodyFilterOperator != other.bodyFilterOperator) return false
        if (bodyFilterPattern != other.bodyFilterPattern) return false
        if (essentialPriorityOverride != other.essentialPriorityOverride) return false
        if (textMatchField != other.textMatchField) return false
        if (textMatchOperator != other.textMatchOperator) return false
        if (textMatchPattern != other.textMatchPattern) return false
        if (requireProgress != other.requireProgress) return false
        if (numericComparison != other.numericComparison) return false
        if (numericThreshold != other.numericThreshold) return false
        if (chargingRequirement != other.chargingRequirement) return false
        if (mediaTriggerState != other.mediaTriggerState) return false
        if (screenTriggerState != other.screenTriggerState) return false
        if (systemToggleFilter != other.systemToggleFilter) return false
        if (actionType != other.actionType) return false
        if (targetAnimationId != other.targetAnimationId) return false
        if (targetAnimationName != other.targetAnimationName) return false
        if (holdUntilConditionClears != other.holdUntilConditionClears) return false
        if (overrideDurationMs != other.overrideDurationMs) return false
        if (overrideIntervalMs != other.overrideIntervalMs) return false
        if (overrideLoopCount != other.overrideLoopCount) return false
        if (overrideTransitionCurve != other.overrideTransitionCurve) return false
        if (meterStyle != other.meterStyle) return false
        if (meterPeakBrightness != other.meterPeakBrightness) return false
        if (glyphScriptSource != other.glyphScriptSource) return false
        if (taskMode != other.taskMode) return false
        if (intrudeDurationMs != other.intrudeDurationMs) return false
        if (updatedAt != other.updatedAt) return false
        if (sequenceTimeoutMs != other.sequenceTimeoutMs) return false
        if (sequenceEvents != other.sequenceEvents) return false
        if (!thumbnailBytes.contentEquals(other.thumbnailBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + isEnabled.hashCode()
        result = 31 * result + priority
        result = 31 * result + cooldownMs.hashCode()
        result = 31 * result + conditionType.hashCode()
        result = 31 * result + targetPackageName.hashCode()
        result = 31 * result + targetAppLabel.hashCode()
        result = 31 * result + senderFilterOperator.hashCode()
        result = 31 * result + senderFilterPattern.hashCode()
        result = 31 * result + bodyFilterOperator.hashCode()
        result = 31 * result + bodyFilterPattern.hashCode()
        result = 31 * result + essentialPriorityOverride.hashCode()
        result = 31 * result + textMatchField.hashCode()
        result = 31 * result + textMatchOperator.hashCode()
        result = 31 * result + textMatchPattern.hashCode()
        result = 31 * result + requireProgress.hashCode()
        result = 31 * result + numericComparison.hashCode()
        result = 31 * result + numericThreshold
        result = 31 * result + chargingRequirement.hashCode()
        result = 31 * result + mediaTriggerState.hashCode()
        result = 31 * result + screenTriggerState.hashCode()
        result = 31 * result + systemToggleFilter.hashCode()
        result = 31 * result + actionType.hashCode()
        result = 31 * result + targetAnimationId.hashCode()
        result = 31 * result + targetAnimationName.hashCode()
        result = 31 * result + holdUntilConditionClears.hashCode()
        result = 31 * result + overrideDurationMs.hashCode()
        result = 31 * result + overrideIntervalMs.hashCode()
        result = 31 * result + overrideLoopCount
        result = 31 * result + overrideTransitionCurve.hashCode()
        result = 31 * result + meterStyle.hashCode()
        result = 31 * result + meterPeakBrightness
        result = 31 * result + glyphScriptSource.hashCode()
        result = 31 * result + taskMode.hashCode()
        result = 31 * result + intrudeDurationMs.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + sequenceEvents.hashCode()
        result = 31 * result + sequenceTimeoutMs.hashCode()
        result = 31 * result + thumbnailBytes.contentHashCode()
        return result
    }
}

/**
 * Normalized real-time system signal ingested by the universal rule engine.
 */
data class SystemSignalEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val conditionType: ConditionType,
    val sourcePackage: String = "android",
    val sourceAppLabel: String = "System",
    val title: String = "",
    val content: String = "",
    val subtext: String = "",
    val progress: Int? = null,
    val progressMax: Int? = null,
    val batteryLevel: Int? = null,
    val isCharging: Boolean? = null,
    val isLowPowerMode: Boolean? = null,
    val mediaState: MediaTriggerState? = null,
    val screenState: ScreenTriggerState? = null,
    val isFaceDown: Boolean? = null,
    val isTimerOrAlarmActive: Boolean? = null,
    val toggleActive: Boolean? = null,
    val ringerMode: SystemToggleFilter? = null,
    val isSimulated: Boolean = false,
    val payload: Map<String, Any?> = emptyMap()
) {
    val normalizedPercentage: Int?
        get() {
            if (progress != null && progressMax != null && progressMax > 0) {
                return ((progress.toFloat() / progressMax.toFloat()) * 100f).toInt().coerceIn(0, 100)
            }
            if (batteryLevel != null) {
                return batteryLevel.coerceIn(0, 100)
            }
            return null
        }

    fun summaryText(): String {
        return when (conditionType) {
            ConditionType.NOTIFICATION -> {
                val progStr = if (progress != null && progressMax != null && progressMax > 0) {
                    " [${normalizedPercentage}%]"
                } else ""
                "$sourceAppLabel ($sourcePackage): \"$title\" — \"$content\"$progStr"
            }
            ConditionType.BATTERY_LEVEL -> "Battery ${batteryLevel ?: 0}% (${if (isCharging == true) "Charging" else "Discharging"})"
            ConditionType.CHARGING_STATE -> if (isCharging == true) "Power Connected (${batteryLevel ?: 0}%)" else "Power Disconnected (${batteryLevel ?: 0}%)"
            ConditionType.BLUETOOTH_STATE -> "Bluetooth ${if (toggleActive == true) "ON / Connected" else "OFF"}"
            ConditionType.WIFI_STATE -> "Wi-Fi ${if (toggleActive == true) "ON / Enabled" else "OFF"}"
            ConditionType.RINGER_MODE -> "Ringer Mode: ${ringerMode?.label ?: title.ifBlank { "Changed" }}"
            ConditionType.HOTSPOT_STATE -> "Wi-Fi Hotspot ${if (toggleActive == true) "ENABLED" else "DISABLED"}"
            ConditionType.TORCH_STATE -> "Flashlight / Torch ${if (toggleActive == true) "ON" else "OFF"}"
            ConditionType.AIRPLANE_MODE -> "Airplane Mode ${if (toggleActive == true) "ON" else "OFF"}"
            ConditionType.LOW_POWER_MODE -> if (isLowPowerMode == true) "Battery Saver Enabled" else "Battery Saver Disabled"
            ConditionType.MEDIA_PLAYBACK -> "Media ${mediaState?.label ?: "Active"}: \"$title\""
            ConditionType.SCREEN_STATE -> screenState?.label ?: "Screen State Changed"
            ConditionType.FACE_DOWN -> if (isFaceDown == true) "Device Placed Face Down (Flip to Glyph)" else "Device Lifted Face Up"
            ConditionType.TIMER_ALARM -> if (isTimerOrAlarmActive == true) "System Timer / Alarm Ringing" else "Timer / Alarm Cleared"
            ConditionType.SYSTEM_STATE -> "System State Trigger: ${title.ifBlank { content.ifBlank { "State Changed" } }}"
            ConditionType.KEY_PRESS -> "Hardware Key Trigger: ${title.ifBlank { content.ifBlank { "Button Action" } }}"
        }
    }
}

enum class AuditStatus(val badgeText: String) {
    TRIGGERED("FIRED"),
    COOLDOWN_SKIPPED("DEBOUNCED"),
    PREEMPTED("PREEMPTED"),
    KILL_SWITCH_BLOCKED("MUTED"),
    UNMATCHED("NO MATCH")
}

/**
 * Entry in the zero-overhead 100-item rolling Audit & Diagnostics Log.
 */
data class AuditLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: ConditionType,
    val sourceSummary: String,
    val extrasSummary: String,
    val matchedRuleId: Long? = null,
    val matchedRuleName: String? = null,
    val matchedRulePriority: Int? = null,
    val actionFiredSummary: String,
    val frameName: String,
    val peakBrightness: Int = 0,
    val averageBrightness: Int = 0,
    val renderLatencyMs: Double = 0.0,
    val status: AuditStatus = AuditStatus.TRIGGERED,
    val isSimulated: Boolean = false
)

/**
 * Installed application metadata for the Notification Rule App Picker.
 */
data class InstalledAppOption(
    val packageName: String,
    val appName: String,
    val isSystemPreset: Boolean = false
)

/**
 * Audit entry recording a GlyphScript v2 compilation event (raw script, AST status, and package resolution).
 */
data class ScriptCompileAuditEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val ruleName: String,
    val rawScript: String,
    val astStatus: String,
    val packageResolutionOutcome: String,
    val isSuccess: Boolean,
    val errorLine: Int? = null,
    val errorColumn: Int? = null,
    val errorMessage: String? = null
)

/**
 * Audit entry recording a direct hardware binder call (`IGlyphService.setMatrixColors` / `setAppMatrixColors`).
 */
data class HardwareBinderAuditEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val binderMethod: String,
    val latencyMs: Double,
    val nonZeroPixelCount: Int,
    val peakBrightness: Int,
    val frameLabel: String
)

/**
 * Audit entry for the Live Payload Inspector showing raw notification `Bundle` extras and regex extractions.
 */
data class NotificationPayloadAuditEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val appLabel: String,
    val rawExtraTitle: String,
    val rawExtraText: String,
    val rawExtraSubText: String,
    val rawExtraProgress: Int?,
    val rawExtraProgressMax: Int?,
    val computedProgressPercent: Int?,
    val extractedVariablesSummary: String = ""
)

