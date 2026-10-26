package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.ActionType
import com.example.domain.model.ChargingRequirement
import com.example.domain.model.ComparisonOperator
import com.example.domain.model.ConditionType
import com.example.domain.model.GlyphAnimation
import com.example.domain.model.GlyphFrame
import com.example.domain.model.MediaTriggerState
import com.example.domain.model.MeterStyle
import com.example.domain.model.ScreenTriggerState
import com.example.domain.model.SystemToggleFilter
import com.example.domain.model.TextMatchField
import com.example.domain.model.TextMatchOperator
import com.example.domain.model.TransitionCurve
import com.example.domain.model.TriggerRule

@Entity(tableName = "glyph_animations")
data class GlyphAnimationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val tags: List<String>,
    val frames: List<GlyphFrame>,
    val defaultFrameIntervalMs: Long,
    val loopCount: Int,
    val transitionCurveCode: String,
    val isPreset: Boolean,
    val updatedAt: Long
) {
    fun toDomain(): GlyphAnimation {
        val curve = TransitionCurve.entries.find { it.code == transitionCurveCode }
            ?: TransitionCurve.STEP_FADE
        return GlyphAnimation(
            id = id,
            name = name,
            tags = tags,
            frames = frames.ifEmpty { listOf(GlyphFrame()) },
            defaultFrameIntervalMs = defaultFrameIntervalMs,
            loopCount = loopCount,
            transitionCurve = curve,
            isPreset = isPreset,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(domain: GlyphAnimation): GlyphAnimationEntity {
            return GlyphAnimationEntity(
                id = domain.id,
                name = domain.name,
                tags = domain.tags,
                frames = domain.frames,
                defaultFrameIntervalMs = domain.defaultFrameIntervalMs,
                loopCount = domain.loopCount,
                transitionCurveCode = domain.transitionCurve.code,
                isPreset = domain.isPreset,
                updatedAt = domain.updatedAt
            )
        }
    }
}

@Entity(tableName = "trigger_rules")
data class TriggerRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val isEnabled: Boolean,
    val priority: Int,
    val cooldownMs: Long,
    val conditionTypeName: String,
    val targetPackageName: String,
    val targetAppLabel: String,
    val senderFilterOperatorName: String = TextMatchOperator.ANY.name,
    val senderFilterPattern: String = "",
    val bodyFilterOperatorName: String = TextMatchOperator.ANY.name,
    val bodyFilterPattern: String = "",
    val essentialPriorityOverride: Boolean = false,
    val textMatchFieldName: String,
    val textMatchOperatorName: String,
    val textMatchPattern: String,
    val requireProgress: Boolean,
    val numericComparisonName: String,
    val numericThreshold: Int,
    val chargingRequirementName: String,
    val mediaTriggerStateName: String,
    val screenTriggerStateName: String,
    val systemToggleFilterName: String = SystemToggleFilter.ANY_CHANGE.name,
    val actionTypeName: String,
    val targetAnimationId: Long,
    val targetAnimationName: String,
    val holdUntilConditionClears: Boolean,
    val overrideDurationMs: Long,
    val overrideIntervalMs: Long,
    val overrideLoopCount: Int,
    val overrideTransitionCurveName: String,
    val meterStyleName: String,
    val meterPeakBrightness: Int,
    val glyphScriptSource: String = "",
    val taskMode: String = "ongoing",
    val intrudeDurationMs: Long = 4000L,
    val thumbnailBytes: ByteArray = ByteArray(0),
    val updatedAt: Long = 0L,
    val sequenceEventsString: String = "",
    val sequenceTimeoutMs: Long = 2000L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TriggerRuleEntity
        if (id != other.id) return false
        if (name != other.name) return false
        if (isEnabled != other.isEnabled) return false
        if (priority != other.priority) return false
        if (cooldownMs != other.cooldownMs) return false
        if (conditionTypeName != other.conditionTypeName) return false
        if (targetPackageName != other.targetPackageName) return false
        if (targetAppLabel != other.targetAppLabel) return false
        if (senderFilterOperatorName != other.senderFilterOperatorName) return false
        if (senderFilterPattern != other.senderFilterPattern) return false
        if (bodyFilterOperatorName != other.bodyFilterOperatorName) return false
        if (bodyFilterPattern != other.bodyFilterPattern) return false
        if (essentialPriorityOverride != other.essentialPriorityOverride) return false
        if (textMatchFieldName != other.textMatchFieldName) return false
        if (textMatchOperatorName != other.textMatchOperatorName) return false
        if (textMatchPattern != other.textMatchPattern) return false
        if (requireProgress != other.requireProgress) return false
        if (numericComparisonName != other.numericComparisonName) return false
        if (numericThreshold != other.numericThreshold) return false
        if (chargingRequirementName != other.chargingRequirementName) return false
        if (mediaTriggerStateName != other.mediaTriggerStateName) return false
        if (screenTriggerStateName != other.screenTriggerStateName) return false
        if (systemToggleFilterName != other.systemToggleFilterName) return false
        if (actionTypeName != other.actionTypeName) return false
        if (targetAnimationId != other.targetAnimationId) return false
        if (targetAnimationName != other.targetAnimationName) return false
        if (holdUntilConditionClears != other.holdUntilConditionClears) return false
        if (overrideDurationMs != other.overrideDurationMs) return false
        if (overrideIntervalMs != other.overrideIntervalMs) return false
        if (overrideLoopCount != other.overrideLoopCount) return false
        if (overrideTransitionCurveName != other.overrideTransitionCurveName) return false
        if (meterStyleName != other.meterStyleName) return false
        if (meterPeakBrightness != other.meterPeakBrightness) return false
        if (glyphScriptSource != other.glyphScriptSource) return false
        if (taskMode != other.taskMode) return false
        if (intrudeDurationMs != other.intrudeDurationMs) return false
        if (!thumbnailBytes.contentEquals(other.thumbnailBytes)) return false
        if (updatedAt != other.updatedAt) return false
        if (sequenceTimeoutMs != other.sequenceTimeoutMs) return false
        if (sequenceEventsString != other.sequenceEventsString) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + isEnabled.hashCode()
        result = 31 * result + priority
        result = 31 * result + cooldownMs.hashCode()
        result = 31 * result + conditionTypeName.hashCode()
        result = 31 * result + targetPackageName.hashCode()
        result = 31 * result + targetAppLabel.hashCode()
        result = 31 * result + senderFilterOperatorName.hashCode()
        result = 31 * result + senderFilterPattern.hashCode()
        result = 31 * result + bodyFilterOperatorName.hashCode()
        result = 31 * result + bodyFilterPattern.hashCode()
        result = 31 * result + essentialPriorityOverride.hashCode()
        result = 31 * result + textMatchFieldName.hashCode()
        result = 31 * result + textMatchOperatorName.hashCode()
        result = 31 * result + textMatchPattern.hashCode()
        result = 31 * result + requireProgress.hashCode()
        result = 31 * result + numericComparisonName.hashCode()
        result = 31 * result + numericThreshold
        result = 31 * result + chargingRequirementName.hashCode()
        result = 31 * result + mediaTriggerStateName.hashCode()
        result = 31 * result + screenTriggerStateName.hashCode()
        result = 31 * result + systemToggleFilterName.hashCode()
        result = 31 * result + actionTypeName.hashCode()
        result = 31 * result + targetAnimationId.hashCode()
        result = 31 * result + targetAnimationName.hashCode()
        result = 31 * result + holdUntilConditionClears.hashCode()
        result = 31 * result + overrideDurationMs.hashCode()
        result = 31 * result + overrideIntervalMs.hashCode()
        result = 31 * result + overrideLoopCount
        result = 31 * result + overrideTransitionCurveName.hashCode()
        result = 31 * result + meterStyleName.hashCode()
        result = 31 * result + meterPeakBrightness
        result = 31 * result + glyphScriptSource.hashCode()
        result = 31 * result + taskMode.hashCode()
        result = 31 * result + intrudeDurationMs.hashCode()
        result = 31 * result + thumbnailBytes.contentHashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + sequenceEventsString.hashCode()
        result = 31 * result + sequenceTimeoutMs.hashCode()
        return result
    }

    fun toDomain(): TriggerRule {
        val seqList = if (sequenceEventsString.isBlank()) emptyList() else sequenceEventsString.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return TriggerRule(
            id = id,
            name = name,
            isEnabled = isEnabled,
            priority = priority.coerceIn(1, 10),
            cooldownMs = cooldownMs.coerceAtLeast(0L),
            conditionType = runCatching { ConditionType.valueOf(conditionTypeName) }
                .getOrDefault(ConditionType.NOTIFICATION),
            targetPackageName = targetPackageName,
            targetAppLabel = targetAppLabel,
            senderFilterOperator = runCatching { TextMatchOperator.valueOf(senderFilterOperatorName) }
                .getOrDefault(TextMatchOperator.ANY),
            senderFilterPattern = senderFilterPattern,
            bodyFilterOperator = runCatching { TextMatchOperator.valueOf(bodyFilterOperatorName) }
                .getOrDefault(TextMatchOperator.ANY),
            bodyFilterPattern = bodyFilterPattern,
            essentialPriorityOverride = essentialPriorityOverride,
            textMatchField = runCatching { TextMatchField.valueOf(textMatchFieldName) }
                .getOrDefault(TextMatchField.ANY),
            textMatchOperator = runCatching { TextMatchOperator.valueOf(textMatchOperatorName) }
                .getOrDefault(TextMatchOperator.ANY),
            textMatchPattern = textMatchPattern,
            requireProgress = requireProgress,
            numericComparison = runCatching { ComparisonOperator.valueOf(numericComparisonName) }
                .getOrDefault(ComparisonOperator.LESS_OR_EQUAL),
            numericThreshold = numericThreshold.coerceIn(0, 100),
            chargingRequirement = runCatching { ChargingRequirement.valueOf(chargingRequirementName) }
                .getOrDefault(ChargingRequirement.ANY),
            mediaTriggerState = runCatching { MediaTriggerState.valueOf(mediaTriggerStateName) }
                .getOrDefault(MediaTriggerState.PLAYING),
            screenTriggerState = runCatching { ScreenTriggerState.valueOf(screenTriggerStateName) }
                .getOrDefault(ScreenTriggerState.SCREEN_OFF),
            systemToggleFilter = runCatching { SystemToggleFilter.valueOf(systemToggleFilterName) }
                .getOrDefault(SystemToggleFilter.ANY_CHANGE),
            actionType = runCatching { ActionType.valueOf(actionTypeName) }
                .getOrDefault(ActionType.PLAY_ANIMATION),
            targetAnimationId = targetAnimationId,
            targetAnimationName = targetAnimationName,
            holdUntilConditionClears = holdUntilConditionClears,
            overrideDurationMs = overrideDurationMs,
            overrideIntervalMs = overrideIntervalMs,
            overrideLoopCount = overrideLoopCount,
            overrideTransitionCurve = runCatching { TransitionCurve.valueOf(overrideTransitionCurveName) }
                .getOrDefault(TransitionCurve.STEP_FADE),
            meterStyle = runCatching { MeterStyle.valueOf(meterStyleName) }
                .getOrDefault(MeterStyle.PERIMETER_RING),
            meterPeakBrightness = meterPeakBrightness.coerceIn(32, 255),
            glyphScriptSource = glyphScriptSource,
            taskMode = taskMode,
            intrudeDurationMs = intrudeDurationMs,
            thumbnailBytes = thumbnailBytes,
            updatedAt = updatedAt,
            sequenceEvents = seqList,
            sequenceTimeoutMs = sequenceTimeoutMs
        )
    }

    companion object {
        fun fromDomain(rule: TriggerRule): TriggerRuleEntity {
            return TriggerRuleEntity(
                id = rule.id,
                name = rule.name,
                isEnabled = rule.isEnabled,
                priority = rule.priority,
                cooldownMs = rule.cooldownMs,
                conditionTypeName = rule.conditionType.name,
                targetPackageName = rule.targetPackageName,
                targetAppLabel = rule.targetAppLabel,
                senderFilterOperatorName = rule.senderFilterOperator.name,
                senderFilterPattern = rule.senderFilterPattern,
                bodyFilterOperatorName = rule.bodyFilterOperator.name,
                bodyFilterPattern = rule.bodyFilterPattern,
                essentialPriorityOverride = rule.essentialPriorityOverride,
                textMatchFieldName = rule.textMatchField.name,
                textMatchOperatorName = rule.textMatchOperator.name,
                textMatchPattern = rule.textMatchPattern,
                requireProgress = rule.requireProgress,
                numericComparisonName = rule.numericComparison.name,
                numericThreshold = rule.numericThreshold,
                chargingRequirementName = rule.chargingRequirement.name,
                mediaTriggerStateName = rule.mediaTriggerState.name,
                screenTriggerStateName = rule.screenTriggerState.name,
                systemToggleFilterName = rule.systemToggleFilter.name,
                actionTypeName = rule.actionType.name,
                targetAnimationId = rule.targetAnimationId,
                targetAnimationName = rule.targetAnimationName,
                holdUntilConditionClears = rule.holdUntilConditionClears,
                overrideDurationMs = rule.overrideDurationMs,
                overrideIntervalMs = rule.overrideIntervalMs,
                overrideLoopCount = rule.overrideLoopCount,
                overrideTransitionCurveName = rule.overrideTransitionCurve.name,
                meterStyleName = rule.meterStyle.name,
                meterPeakBrightness = rule.meterPeakBrightness,
                glyphScriptSource = rule.glyphScriptSource,
                taskMode = rule.taskMode,
                intrudeDurationMs = rule.intrudeDurationMs,
                thumbnailBytes = rule.thumbnailBytes,
                updatedAt = rule.updatedAt,
                sequenceEventsString = rule.sequenceEvents.joinToString(","),
                sequenceTimeoutMs = rule.sequenceTimeoutMs
            )
        }
    }
}
