package com.example

import android.app.Application
import com.example.data.local.GlyphDatabase
import com.example.data.repository.GlyphRepository
import com.example.engine.RuleEvaluationEngine
import com.example.hardware.GlyphHardwareController

/**
 * Application-level container holding the singletons for Room persistence,
 * Nothing Phone (3) GlyphHardwareController, and the real-time RuleEvaluationEngine.
 */
class GlyphFlowApplication : Application() {

    lateinit var database: GlyphDatabase
        private set

    lateinit var repository: GlyphRepository
        private set

    lateinit var hardwareController: GlyphHardwareController
        private set

    lateinit var ruleEngine: RuleEvaluationEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = GlyphDatabase.getInstance(this)
        repository = GlyphRepository(database.glyphDao())
        hardwareController = GlyphHardwareController(this)
        ruleEngine = RuleEvaluationEngine(repository, hardwareController)
    }

    companion object {
        @Volatile
        var instance: GlyphFlowApplication? = null
            private set
    }
}
