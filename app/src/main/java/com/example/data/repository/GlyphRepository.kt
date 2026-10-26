package com.example.data.repository

import android.util.Base64
import com.example.data.local.GlyphAnimationEntity
import com.example.data.local.GlyphDao
import com.example.data.local.TriggerRuleEntity
import com.example.domain.model.ActionType
import com.example.domain.model.ChargingRequirement
import com.example.domain.model.ComparisonOperator
import com.example.domain.model.ConditionType
import com.example.domain.model.GlyphAnimation
import com.example.domain.model.GlyphFrame
import com.example.domain.model.MATRIX_CELL_COUNT
import com.example.domain.model.MediaTriggerState
import com.example.domain.model.MeterStyle
import com.example.domain.model.ScreenTriggerState
import com.example.domain.model.SystemToggleFilter
import com.example.domain.model.TextMatchField
import com.example.domain.model.TextMatchOperator
import com.example.domain.model.TransitionCurve
import com.example.domain.model.TriggerRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single source of truth for local-only Room persistence of user-created 25x25 animations
 * and IFTTT / Essential Glyph automation rules, plus 100% offline JSON export and import.
 */
class GlyphRepository(private val glyphDao: GlyphDao) {

    val animationsFlow: Flow<List<GlyphAnimation>> =
        glyphDao.observeAllAnimations().map { list -> list.map { it.toDomain() } }

    val rulesFlow: Flow<List<TriggerRule>> =
        glyphDao.observeAllRules().map { list -> list.map { it.toDomain() } }

    /**
     * Ensures any legacy preset animations are purged; starts completely clean (0 active rules).
     */
    suspend fun ensureSeeded() {
        glyphDao.deletePresetAnimations()
    }

    suspend fun getAllAnimationsSnapshot(): List<GlyphAnimation> {
        return glyphDao.getAllAnimationsSnapshot().map { it.toDomain() }
    }

    suspend fun getAnimationById(id: Long): GlyphAnimation? {
        return glyphDao.getAnimationById(id)?.toDomain()
    }

    suspend fun saveAnimation(animation: GlyphAnimation): Long {
        val updated = animation.copy(isPreset = false, updatedAt = System.currentTimeMillis())
        return glyphDao.insertAnimation(GlyphAnimationEntity.fromDomain(updated))
    }

    suspend fun deleteAnimation(id: Long) {
        glyphDao.deleteAnimationById(id)
    }

    suspend fun getAllRulesSnapshot(): List<TriggerRule> {
        return glyphDao.getAllRulesSnapshot().map { it.toDomain() }
    }

    suspend fun saveRule(rule: TriggerRule): Long {
        return glyphDao.insertRule(TriggerRuleEntity.fromDomain(rule))
    }

    suspend fun updateRule(rule: TriggerRule): Long {
        return glyphDao.insertRule(TriggerRuleEntity.fromDomain(rule))
    }

    suspend fun updateRuleThumbnail(ruleId: Long, thumbnail: ByteArray, timestamp: Long = System.currentTimeMillis()) {
        glyphDao.updateRuleThumbnail(ruleId, thumbnail, timestamp)
    }

    suspend fun updateRuleThumbnail(ruleId: String, thumbnail: ByteArray, timestamp: Long = System.currentTimeMillis()) {
        val idLong = ruleId.toLongOrNull() ?: return
        glyphDao.updateRuleThumbnail(idLong, thumbnail, timestamp)
    }

    suspend fun toggleRule(id: Long, enabled: Boolean) {
        glyphDao.setRuleEnabled(id, enabled)
    }

    suspend fun deleteRule(id: Long) {
        glyphDao.deleteRuleById(id)
    }

    /**
     * Exports all user-created 25x25 animations and automation rules into a portable offline JSON bundle.
     */
    suspend fun exportWorkspaceToJson(): String {
        val animations = getAllAnimationsSnapshot()
        val rules = getAllRulesSnapshot()
        val root = JSONObject()
        root.put("schema", "GlyphFlow_NP3_v2")
        root.put("exportedAt", System.currentTimeMillis())

        val animArray = JSONArray()
        for (anim in animations) {
            val animObj = JSONObject()
            animObj.put("id", anim.id)
            animObj.put("name", anim.name)
            animObj.put("tags", JSONArray(anim.tags))
            animObj.put("defaultFrameIntervalMs", anim.defaultFrameIntervalMs)
            animObj.put("loopCount", anim.loopCount)
            animObj.put("transitionCurve", anim.transitionCurve.code)
            val framesArr = JSONArray()
            for (frame in anim.frames) {
                val fObj = JSONObject()
                fObj.put("id", frame.id)
                fObj.put("durationMs", frame.durationMs)
                fObj.put("pixelsBase64", Base64.encodeToString(frame.pixels, Base64.NO_WRAP))
                framesArr.put(fObj)
            }
            animObj.put("frames", framesArr)
            animArray.put(animObj)
        }
        root.put("animations", animArray)

        val ruleArray = JSONArray()
        for (rule in rules) {
            val rObj = JSONObject()
            rObj.put("name", rule.name)
            rObj.put("isEnabled", rule.isEnabled)
            rObj.put("priority", rule.priority)
            rObj.put("cooldownMs", rule.cooldownMs)
            rObj.put("conditionType", rule.conditionType.name)
            rObj.put("targetPackageName", rule.targetPackageName)
            rObj.put("targetAppLabel", rule.targetAppLabel)
            rObj.put("senderFilterOperator", rule.senderFilterOperator.name)
            rObj.put("senderFilterPattern", rule.senderFilterPattern)
            rObj.put("bodyFilterOperator", rule.bodyFilterOperator.name)
            rObj.put("bodyFilterPattern", rule.bodyFilterPattern)
            rObj.put("essentialPriorityOverride", rule.essentialPriorityOverride)
            rObj.put("textMatchField", rule.textMatchField.name)
            rObj.put("textMatchOperator", rule.textMatchOperator.name)
            rObj.put("textMatchPattern", rule.textMatchPattern)
            rObj.put("requireProgress", rule.requireProgress)
            rObj.put("numericComparison", rule.numericComparison.name)
            rObj.put("numericThreshold", rule.numericThreshold)
            rObj.put("chargingRequirement", rule.chargingRequirement.name)
            rObj.put("mediaTriggerState", rule.mediaTriggerState.name)
            rObj.put("screenTriggerState", rule.screenTriggerState.name)
            rObj.put("systemToggleFilter", rule.systemToggleFilter.name)
            rObj.put("actionType", rule.actionType.name)
            rObj.put("targetAnimationId", rule.targetAnimationId)
            rObj.put("targetAnimationName", rule.targetAnimationName)
            rObj.put("holdUntilConditionClears", rule.holdUntilConditionClears)
            rObj.put("overrideDurationMs", rule.overrideDurationMs)
            rObj.put("overrideIntervalMs", rule.overrideIntervalMs)
            rObj.put("overrideLoopCount", rule.overrideLoopCount)
            rObj.put("overrideTransitionCurve", rule.overrideTransitionCurve.name)
            rObj.put("meterStyle", rule.meterStyle.name)
            rObj.put("meterPeakBrightness", rule.meterPeakBrightness)
            rObj.put("glyphScriptSource", rule.glyphScriptSource)
            ruleArray.put(rObj)
        }
        root.put("rules", ruleArray)

        return root.toString(2)
    }

    /**
     * Imports animations and rules from a GlyphFlow JSON payload.
     * Returns pair of (importedAnimationsCount, importedRulesCount).
     */
    suspend fun importWorkspaceFromJson(jsonPayload: String): Result<Pair<Int, Int>> {
        return runCatching {
            val root = JSONObject(jsonPayload.trim())
            var importedAnims = 0
            var importedRules = 0

            val animArray = root.optJSONArray("animations")
            if (animArray != null) {
                for (i in 0 until animArray.length()) {
                    val obj = animArray.getJSONObject(i)
                    val tagsArr = obj.optJSONArray("tags")
                    val tags = if (tagsArr != null) {
                        List(tagsArr.length()) { idx -> tagsArr.optString(idx) }
                    } else emptyList()

                    val framesArr = obj.optJSONArray("frames")
                    val frames = mutableListOf<GlyphFrame>()
                    if (framesArr != null) {
                        for (fIdx in 0 until framesArr.length()) {
                            val fObj = framesArr.getJSONObject(fIdx)
                            val b64 = fObj.optString("pixelsBase64", "")
                            val rawBytes = if (b64.isNotBlank()) {
                                val decoded = Base64.decode(b64, Base64.NO_WRAP)
                                if (decoded.size == MATRIX_CELL_COUNT) decoded else ByteArray(MATRIX_CELL_COUNT)
                            } else ByteArray(MATRIX_CELL_COUNT)
                            frames.add(
                                GlyphFrame(
                                    id = fObj.optString("id", "imp_$fIdx"),
                                    durationMs = fObj.optLong("durationMs", 120L),
                                    pixels = rawBytes
                                )
                            )
                        }
                    }

                    val curveCode = obj.optString("transitionCurve", "STEP_FADE")
                    val curve = TransitionCurve.entries.find { it.code == curveCode } ?: TransitionCurve.STEP_FADE

                    val anim = GlyphAnimation(
                        id = 0L,
                        name = obj.optString("name", "Imported Matrix"),
                        tags = tags,
                        frames = frames.ifEmpty { listOf(GlyphFrame()) },
                        defaultFrameIntervalMs = obj.optLong("defaultFrameIntervalMs", 120L),
                        loopCount = obj.optInt("loopCount", 1),
                        transitionCurve = curve,
                        isPreset = false
                    )
                    saveAnimation(anim)
                    importedAnims++
                }
            }

            val ruleArray = root.optJSONArray("rules")
            if (ruleArray != null) {
                for (i in 0 until ruleArray.length()) {
                    val rObj = ruleArray.getJSONObject(i)
                    val rule = TriggerRule(
                        id = 0L,
                        name = rObj.optString("name", "Imported Rule"),
                        isEnabled = rObj.optBoolean("isEnabled", true),
                        priority = rObj.optInt("priority", 5).coerceIn(1, 10),
                        cooldownMs = rObj.optLong("cooldownMs", 1500L),
                        conditionType = runCatching { ConditionType.valueOf(rObj.optString("conditionType")) }
                            .getOrDefault(ConditionType.NOTIFICATION),
                        targetPackageName = rObj.optString("targetPackageName", "*"),
                        targetAppLabel = rObj.optString("targetAppLabel", "Any App (*)"),
                        senderFilterOperator = runCatching { TextMatchOperator.valueOf(rObj.optString("senderFilterOperator")) }
                            .getOrDefault(TextMatchOperator.ANY),
                        senderFilterPattern = rObj.optString("senderFilterPattern", ""),
                        bodyFilterOperator = runCatching { TextMatchOperator.valueOf(rObj.optString("bodyFilterOperator")) }
                            .getOrDefault(TextMatchOperator.ANY),
                        bodyFilterPattern = rObj.optString("bodyFilterPattern", ""),
                        essentialPriorityOverride = rObj.optBoolean("essentialPriorityOverride", false),
                        textMatchField = runCatching { TextMatchField.valueOf(rObj.optString("textMatchField")) }
                            .getOrDefault(TextMatchField.ANY),
                        textMatchOperator = runCatching { TextMatchOperator.valueOf(rObj.optString("textMatchOperator")) }
                            .getOrDefault(TextMatchOperator.ANY),
                        textMatchPattern = rObj.optString("textMatchPattern", ""),
                        requireProgress = rObj.optBoolean("requireProgress", false),
                        numericComparison = runCatching { ComparisonOperator.valueOf(rObj.optString("numericComparison")) }
                            .getOrDefault(ComparisonOperator.LESS_OR_EQUAL),
                        numericThreshold = rObj.optInt("numericThreshold", 20),
                        chargingRequirement = runCatching { ChargingRequirement.valueOf(rObj.optString("chargingRequirement")) }
                            .getOrDefault(ChargingRequirement.ANY),
                        mediaTriggerState = runCatching { MediaTriggerState.valueOf(rObj.optString("mediaTriggerState")) }
                            .getOrDefault(MediaTriggerState.PLAYING),
                        screenTriggerState = runCatching { ScreenTriggerState.valueOf(rObj.optString("screenTriggerState")) }
                            .getOrDefault(ScreenTriggerState.SCREEN_OFF),
                        systemToggleFilter = runCatching { SystemToggleFilter.valueOf(rObj.optString("systemToggleFilter")) }
                            .getOrDefault(SystemToggleFilter.ANY_CHANGE),
                        actionType = runCatching { ActionType.valueOf(rObj.optString("actionType")) }
                            .getOrDefault(ActionType.PLAY_ANIMATION),
                        targetAnimationId = rObj.optLong("targetAnimationId", 0L),
                        targetAnimationName = rObj.optString("targetAnimationName", "Custom Matrix"),
                        holdUntilConditionClears = rObj.optBoolean("holdUntilConditionClears", true),
                        overrideDurationMs = rObj.optLong("overrideDurationMs", 4000L),
                        overrideIntervalMs = rObj.optLong("overrideIntervalMs", 100L),
                        overrideLoopCount = rObj.optInt("overrideLoopCount", 0),
                        overrideTransitionCurve = runCatching { TransitionCurve.valueOf(rObj.optString("overrideTransitionCurve")) }
                            .getOrDefault(TransitionCurve.STEP_FADE),
                        meterStyle = runCatching { MeterStyle.valueOf(rObj.optString("meterStyle")) }
                            .getOrDefault(MeterStyle.PERIMETER_RING),
                        meterPeakBrightness = rObj.optInt("meterPeakBrightness", 255),
                        glyphScriptSource = rObj.optString("glyphScriptSource", "")
                    )
                    saveRule(rule)
                    importedRules++
                }
            }
            importedAnims to importedRules
        }
    }
}
