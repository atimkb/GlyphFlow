package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GlyphDao {

    // --- Animations / Frames ---
    @Query("SELECT * FROM glyph_animations ORDER BY updatedAt DESC")
    fun observeAllAnimations(): Flow<List<GlyphAnimationEntity>>

    @Query("SELECT * FROM glyph_animations ORDER BY updatedAt DESC")
    suspend fun getAllAnimationsSnapshot(): List<GlyphAnimationEntity>

    @Query("SELECT * FROM glyph_animations WHERE id = :id LIMIT 1")
    suspend fun getAnimationById(id: Long): GlyphAnimationEntity?

    @Query("SELECT COUNT(*) FROM glyph_animations")
    suspend fun getAnimationCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnimation(animation: GlyphAnimationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnimations(animations: List<GlyphAnimationEntity>): List<Long>

    @Query("DELETE FROM glyph_animations WHERE id = :id")
    suspend fun deleteAnimationById(id: Long)

    @Query("DELETE FROM glyph_animations WHERE isPreset = 1")
    suspend fun deletePresetAnimations()

    // --- Automation Trigger Rules ---
    @Query("SELECT * FROM trigger_rules ORDER BY essentialPriorityOverride DESC, priority DESC, id ASC")
    fun observeAllRules(): Flow<List<TriggerRuleEntity>>

    @Query("SELECT * FROM trigger_rules ORDER BY essentialPriorityOverride DESC, priority DESC, id ASC")
    suspend fun getAllRulesSnapshot(): List<TriggerRuleEntity>

    @Query("SELECT COUNT(*) FROM trigger_rules")
    suspend fun getRuleCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: TriggerRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<TriggerRuleEntity>): List<Long>

    @Update
    suspend fun updateRule(rule: TriggerRuleEntity)

    @Query("UPDATE trigger_rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setRuleEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE trigger_rules SET thumbnailBytes = :thumbnail, updatedAt = :timestamp WHERE id = :ruleId")
    suspend fun updateRuleThumbnail(ruleId: Long, thumbnail: ByteArray, timestamp: Long)

    @Query("DELETE FROM trigger_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)
}
