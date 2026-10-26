package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.example.engine.ActiveTaskStateManager
import com.example.engine.EventIngestBus
import com.example.hardware.GlyphHardwareController

/**
 * AccessibilityService that listens for physical volume key events when the screen is locked/off
 * or when the 25x25 Glyph Matrix is actively running:
 * - Intercepts Volume Down Long-Press (>= 500ms) or Double-Click:
 *   - Consumes the event to prevent changing system media volume when the matrix is active.
 *   - Cycles to the next task in the Base Ring Buffer via `ActiveTaskStateManager.cycleNextTask(...)`.
 *   - Provides haptic feedback (`VibrationEffect.createOneShot(25, 180)`).
 */
class GlyphKeyTriggerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastVolumeDownDownTime = 0L
    private var longPressTriggered = false
    private var isVolumeDownPressed = false

    private val longPressRunnable = Runnable {
        if (isVolumeDownPressed) {
            longPressTriggered = true
            dispatchCycleTask()
        }
    }

    private val cycleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_GLYPH_CYCLE_NEXT_TASK) {
                dispatchCycleTask()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(ACTION_GLYPH_CYCLE_NEXT_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(cycleReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(cycleReceiver, filter)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(cycleReceiver) }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkgName = event.packageName?.toString()
            if (!pkgName.isNullOrBlank()) {
                com.example.engine.GlobalSystemStateCache.setSystemVariable("foreground_app", pkgName)
            }
        }
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

        val keyName = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> "key.volume_down"
            KeyEvent.KEYCODE_VOLUME_UP -> "key.volume_up"
            KeyEvent.KEYCODE_POWER -> "key.power"
            else -> null
        }

        if (event.action == KeyEvent.ACTION_DOWN && keyName != null) {
            com.example.engine.GlobalSystemStateCache.recordEvent(keyName)
            EventIngestBus.emitSignal(
                com.example.domain.model.SystemSignalEvent(
                    conditionType = com.example.domain.model.ConditionType.KEY_PRESS,
                    sourcePackage = "com.android.systemui",
                    sourceAppLabel = "Hardware Keys",
                    title = keyName
                )
            )
        }

        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val now = SystemClock.elapsedRealtime()

            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (!isVolumeDownPressed) {
                        isVolumeDownPressed = true
                        longPressTriggered = false

                        // Check Double-Click (two presses within 350ms)
                        if (now - lastVolumeDownDownTime in 1L..350L) {
                            handler.removeCallbacks(longPressRunnable)
                            lastVolumeDownDownTime = 0L
                            dispatchCycleTask()
                            return shouldConsumeVolumeKey()
                        }
                        lastVolumeDownDownTime = now

                        // Schedule Long-Press (held for >= 500ms)
                        handler.postDelayed(longPressRunnable, 500L)
                    }
                    return shouldConsumeVolumeKey()
                }

                KeyEvent.ACTION_UP -> {
                    isVolumeDownPressed = false
                    handler.removeCallbacks(longPressRunnable)
                    if (longPressTriggered) {
                        longPressTriggered = false
                        return true
                    }
                    return shouldConsumeVolumeKey()
                }
            }
        }
        return super.onKeyEvent(event)
    }

    private fun dispatchCycleTask() {
        val vibrator = getSystemVibrator()
        val controller = GlyphHardwareController.getInstance(applicationContext)
        ActiveTaskStateManager.cycleNextTask(controller, vibrator)

        // Also broadcast for any external listeners
        val cycleIntent = Intent(ACTION_GLYPH_CYCLE_NEXT_TASK)
        sendBroadcast(cycleIntent)
    }

    private fun shouldConsumeVolumeKey(): Boolean {
        // Consume the key event if the engine is enabled and active ongoing tasks exist
        return EventIngestBus.globalEngineEnabled.value &&
            ActiveTaskStateManager.getOngoingTaskCount() > 0
    }

    private fun getSystemVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    companion object {
        const val ACTION_GLYPH_CYCLE_NEXT_TASK = "com.example.glyphflow.ACTION_GLYPH_CYCLE_NEXT_TASK"

        fun triggerManualCycle(context: Context) {
            val intent = Intent(ACTION_GLYPH_CYCLE_NEXT_TASK).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
    }
}
