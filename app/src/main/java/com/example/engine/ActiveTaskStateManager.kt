package com.example.engine

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.example.domain.model.MeterStyle
import com.example.hardware.GlyphHardwareController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Model representing an active task queued in the Dual-Layer Task Stack.
 */
data class ActiveTask(
    val key: String, // e.g. package name or notification key
    val sourcePackage: String,
    val appLabel: String,
    val ruleId: Long,
    val ruleName: String,
    val priority: Int,
    val compositeFrames: List<ByteArray>,
    val frameIntervalMs: Long = 100L,
    val isTransient: Boolean = false,
    val durationMs: Long = 4000L,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Dual-Layer Task Stack & Carousel Manager:
 * 1. Base Ring Buffer (`OngoingTaskBuffer`): Holds active ongoing tasks (maps, delivery, ride-hailing, media).
 *    Swapped cyclically via physical Volume Down key or UI trigger (`ACTION_GLYPH_CYCLE_NEXT_TASK`).
 * 2. Transient Intrusion Layer (`IntrusionQueue`): Hijacks the matrix display for high-priority short alerts
 *    (OTP, chats, flash notifications), then restores the active background task seamlessly.
 */
object ActiveTaskStateManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    private val ongoingTasks = mutableListOf<ActiveTask>()
    private var selectedTaskIndex = 0

    private var activeIntrusionJob: Job? = null
    private var currentIntrusionTask: ActiveTask? = null

    private val _ongoingTasksFlow = MutableStateFlow<List<ActiveTask>>(emptyList())
    val ongoingTasksFlow: StateFlow<List<ActiveTask>> = _ongoingTasksFlow.asStateFlow()

    private val _selectedTaskIndexFlow = MutableStateFlow(0)
    val selectedTaskIndexFlow: StateFlow<Int> = _selectedTaskIndexFlow.asStateFlow()

    private val _activeIntrusionFlow = MutableStateFlow<ActiveTask?>(null)
    val activeIntrusionFlow: StateFlow<ActiveTask?> = _activeIntrusionFlow.asStateFlow()

    private val _currentDisplayTaskFlow = MutableStateFlow<ActiveTask?>(null)
    val currentDisplayTaskFlow: StateFlow<ActiveTask?> = _currentDisplayTaskFlow.asStateFlow()

    fun getOngoingTaskCount(): Int = ongoingTasks.size

    fun getCurrentTask(): ActiveTask? = lockSync {
        currentIntrusionTask ?: ongoingTasks.getOrNull(selectedTaskIndex)
    }

    private fun <T> lockSync(block: () -> T): T {
        return synchronized(this) { block() }
    }

    /**
     * Ingests a new or updated task into either the Transient Intrusion Layer or the Base Ring Buffer.
     */
    fun pushTask(task: ActiveTask, hardwareController: GlyphHardwareController) {
        scope.launch {
            lock.withLock {
                if (task.isTransient) {
                    handleTransientIntrusion(task, hardwareController)
                } else {
                    handleOngoingTask(task, hardwareController)
                }
                syncStateFlows()
            }
        }
    }

    private suspend fun handleTransientIntrusion(
        intrusion: ActiveTask,
        hardwareController: GlyphHardwareController
    ) {
        val baseTask = ongoingTasks.getOrNull(selectedTaskIndex)
        EventIngestBus.recordIntrusionStart(intrusion.sourcePackage, intrusion.durationMs)

        activeIntrusionJob?.cancelAndJoin()
        currentIntrusionTask = intrusion
        _activeIntrusionFlow.value = intrusion
        _currentDisplayTaskFlow.value = intrusion

        activeIntrusionJob = scope.launch {
            // Stream transient intrusion alert
            hardwareController.triggerCompositeFrames(
                compositeFrames = intrusion.compositeFrames,
                frameName = "INTRUSION_${intrusion.ruleName}",
                frameIntervalMs = intrusion.frameIntervalMs,
                maxDurationMs = intrusion.durationMs,
                holdIndefinitely = false,
                ruleId = intrusion.ruleId,
                ruleName = intrusion.ruleName,
                sourcePackage = intrusion.sourcePackage,
                priority = 10
            )

            delay(intrusion.durationMs)

            lock.withLock {
                currentIntrusionTask = null
                _activeIntrusionFlow.value = null
                val restored = ongoingTasks.getOrNull(selectedTaskIndex)
                EventIngestBus.recordIntrusionEnd(restored?.sourcePackage ?: "Standby")

                if (restored != null) {
                    _currentDisplayTaskFlow.value = restored
                    renderTaskToHardware(restored, hardwareController)
                } else {
                    _currentDisplayTaskFlow.value = null
                    hardwareController.clearAndCloseSession()
                }
                syncStateFlows()
            }
        }
    }

    private fun handleOngoingTask(
        task: ActiveTask,
        hardwareController: GlyphHardwareController
    ) {
        val existingIndex = ongoingTasks.indexOfFirst {
            it.key == task.key || it.sourcePackage == task.sourcePackage
        }

        if (existingIndex >= 0) {
            ongoingTasks[existingIndex] = task
            if (currentIntrusionTask == null && existingIndex == selectedTaskIndex) {
                renderTaskToHardware(task, hardwareController)
            }
        } else {
            ongoingTasks.add(task)
            if (ongoingTasks.size == 1) {
                selectedTaskIndex = 0
            }
            if (currentIntrusionTask == null) {
                if (ongoingTasks.size == 1 || task.priority > (ongoingTasks.getOrNull(selectedTaskIndex)?.priority ?: 0)) {
                    selectedTaskIndex = ongoingTasks.lastIndex
                    renderTaskToHardware(task, hardwareController)
                }
            }
        }
    }

    /**
     * Cycles to the next ongoing task in the Base Ring Buffer (triggered via Volume Down long-press or double-click).
     */
    fun cycleNextTask(
        hardwareController: GlyphHardwareController,
        vibrator: Vibrator? = null
    ) {
        scope.launch {
            lock.withLock {
                if (ongoingTasks.isEmpty()) {
                    triggerShortHaptic(vibrator)
                    return@withLock
                }

                // If a transient intrusion is active, cancel it and return to carousel immediately
                if (currentIntrusionTask != null) {
                    activeIntrusionJob?.cancelAndJoin()
                    currentIntrusionTask = null
                    _activeIntrusionFlow.value = null
                }

                val oldTask = ongoingTasks.getOrNull(selectedTaskIndex)
                selectedTaskIndex = (selectedTaskIndex + 1) % ongoingTasks.size
                val newTask = ongoingTasks[selectedTaskIndex]

                val fromPkg = oldTask?.sourcePackage ?: "None"
                val toPkg = newTask.sourcePackage

                EventIngestBus.recordHardwareCycleEvent(fromPkg, toPkg)
                triggerShortHaptic(vibrator)

                // 1-frame context change transition sweep (60ms perimeter flash)
                val flashFrame = GlyphHardwareController.buildMeterFrame(
                    percentage = 100,
                    style = MeterStyle.PERIMETER_RING,
                    peakBrightness = 255
                )
                hardwareController.renderImmediateFrame(
                    pixels = flashFrame,
                    frameLabel = "CAROUSEL_SWAP_SWEEP",
                    ruleId = newTask.ruleId,
                    ruleName = "Carousel Transition",
                    sourcePackage = toPkg,
                    priority = 10,
                    pushToHardware = true
                )
                delay(60L)

                // Stream newly selected task
                _currentDisplayTaskFlow.value = newTask
                renderTaskToHardware(newTask, hardwareController)
                syncStateFlows()
            }
        }
    }

    /**
     * Removes an ongoing task when its notification is dismissed or media stops.
     */
    fun removeTaskBySource(
        sourceIdentifier: String,
        hardwareController: GlyphHardwareController
    ) {
        scope.launch {
            lock.withLock {
                val removedIdx = ongoingTasks.indexOfFirst {
                    it.key.equals(sourceIdentifier, ignoreCase = true) ||
                        it.sourcePackage.equals(sourceIdentifier, ignoreCase = true)
                }

                if (removedIdx >= 0) {
                    ongoingTasks.removeAt(removedIdx)
                    if (ongoingTasks.isEmpty()) {
                        selectedTaskIndex = 0
                        if (currentIntrusionTask == null) {
                            _currentDisplayTaskFlow.value = null
                            hardwareController.clearAndCloseSession()
                        }
                    } else {
                        selectedTaskIndex = selectedTaskIndex.coerceIn(0, ongoingTasks.lastIndex)
                        if (currentIntrusionTask == null) {
                            val nextTask = ongoingTasks[selectedTaskIndex]
                            _currentDisplayTaskFlow.value = nextTask
                            renderTaskToHardware(nextTask, hardwareController)
                        }
                    }
                }
                syncStateFlows()
            }
        }
    }

    fun clearAll(hardwareController: GlyphHardwareController) {
        scope.launch {
            lock.withLock {
                activeIntrusionJob?.cancel()
                activeIntrusionJob = null
                currentIntrusionTask = null
                ongoingTasks.clear()
                selectedTaskIndex = 0
                _activeIntrusionFlow.value = null
                _currentDisplayTaskFlow.value = null
                syncStateFlows()
                hardwareController.clearAndCloseSession()
            }
        }
    }

    private fun renderTaskToHardware(
        task: ActiveTask,
        hardwareController: GlyphHardwareController
    ) {
        hardwareController.triggerCompositeFrames(
            compositeFrames = task.compositeFrames,
            frameName = "TASK_${task.ruleName}",
            frameIntervalMs = task.frameIntervalMs,
            maxDurationMs = 60_000L,
            holdIndefinitely = true,
            ruleId = task.ruleId,
            ruleName = task.ruleName,
            sourcePackage = task.sourcePackage,
            priority = task.priority
        )
    }

    private fun triggerShortHaptic(vibrator: Vibrator?) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(25L, 180))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(25L)
            }
        } catch (_: Exception) {
            // Ignore vibration errors on devices without vibrator hardware
        }
    }

    private fun syncStateFlows() {
        _ongoingTasksFlow.value = ongoingTasks.toList()
        _selectedTaskIndexFlow.value = selectedTaskIndex
        _currentDisplayTaskFlow.value = currentIntrusionTask ?: ongoingTasks.getOrNull(selectedTaskIndex)
    }
}
