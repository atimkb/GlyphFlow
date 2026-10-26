package com.example.data.local

import com.example.domain.model.GlyphAnimation
import com.example.domain.model.TriggerRule

/**
 * Clean slate initializer:
 * All hardcoded presets, dummy mock rules, and preloaded animation sequences have been purged
 * so the database starts completely clean (0 active rules, 0 hardcoded animations).
 */
object StarterPresets {

    fun buildStarterAnimations(): List<GlyphAnimation> = emptyList()

    fun buildStarterRules(): List<TriggerRule> = emptyList()
}
