package com.example.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.GlyphFlowApplication
import com.example.domain.model.ActionType
import com.example.domain.model.AuditLogEntry
import com.example.domain.model.ConditionType
import com.example.domain.model.GlyphAnimation
import com.example.domain.model.GlyphFrame
import com.example.domain.model.HardwareBinderAuditEntry
import com.example.domain.model.InstalledAppOption
import com.example.domain.model.MATRIX_CELL_COUNT
import com.example.domain.model.MATRIX_SIZE
import com.example.domain.model.MatrixTransform
import com.example.domain.model.MediaTriggerState
import com.example.domain.model.NotificationPayloadAuditEntry
import com.example.domain.model.ScreenTriggerState
import com.example.domain.model.ScriptCompileAuditEntry
import com.example.domain.model.SystemSignalEvent
import com.example.domain.model.SystemToggleFilter
import com.example.domain.model.TransitionCurve
import com.example.domain.model.TriggerRule
import com.example.dsl.DEFAULT_CONSOLE_SCRIPT_SAMPLE
import com.example.dsl.DecompiledStudioRule
import com.example.dsl.DecompiledVisualLayer
import com.example.dsl.GlyphFrameCompositor
import com.example.dsl.GlyphScriptCompileResult
import com.example.dsl.GlyphScriptCompiler
import com.example.dsl.GlyphScriptDecompiler
import com.example.dsl.LayerAnimationMacro
import com.example.engine.EventIngestBus
import com.example.engine.GlyphPatternGenerator
import com.example.hardware.GlyphAperture
import com.example.hardware.GlyphHardwareController
import com.example.hardware.HardwareMatrixState
import com.example.service.GlyphEngineForegroundService
import com.example.ui.screens.ConsoleUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Bottom Navigation Tabs:
 * 1. Dashboard (Rules & Live Status)
 * 2. Studio (Circular Board & Creative Tools)
 * 3. Console (GlyphScript v2 Universal DSL Compiler & LLM Prompt Card)
 * 4. Audit (Live Diagnostics, Script Parse History & Payload Inspector)
 * 5. Settings (Hardware Self-Audit, Permissions & Visual User Guide)
 */
enum class GlyphScreenTab(val route: String, val title: String) {
    DASHBOARD("dashboard", "DASHBOARD"),
    STUDIO("studio", "STUDIO"),
    CONSOLE("console", "CONSOLE"),
    AUDIT("audit", "AUDIT"),
    SETTINGS("settings", "SETTINGS")
}

enum class NudgeClampMode(val label: String, val subtitle: String) {
    SAFE_CLAMP("SAFE CLAMP", "Stops movement if any dot bleeds outside 489 LEDs"),
    APERTURE_CLIP("APERTURE CLIP", "Allows movement, cleanly clipping dots outside circle")
}

enum class StudioDockTab(val label: String) {
    DRAW("DRAW"),
    CURSOR("CURSOR"),
    SELECT("SELECT"),
    SCALE("SCALE"),
    LIGHTING("LIGHTING"),
    GUIDES("GUIDES"),
    TRANSFORM("TRANSFORM"),
    LAYERS("LAYERS"),
    ANIMATION("ANIMATION"),
    PARAMS("PARAMS"),
    AI_PHOTO("AI & PHOTO")
}

enum class AnimationPlaybackMode(val label: String, val shortLabel: String) {
    LOOP("Loop (CW/Normal)", "Loop"),
    PING_PONG("Ping-Pong (Bounce)", "Ping-Pong"),
    ONCE("Once", "Once")
}

enum class StudioDrawTool(val label: String) {
    PEN("PEN"),
    ERASER("ERASER"),
    LINE("LINE"),
    CIRCLE("CIRCLE"),
    FILL("FILL")
}

enum class RampGradientType(val label: String) {
    FADE_LEFT_TO_RIGHT("Fade L→R"),
    FADE_TOP_TO_BOTTOM("Fade T→B"),
    RADIAL("Radial Fade")
}

data class StudioEditorState(
    val editingAnimationId: Long = 0L,
    val name: String = "Circular Matrix 01",
    val tagsInput: String = "CUSTOM, NP3, CIRCULAR",
    val frames: List<GlyphFrame> = listOf(GlyphFrame(id = "f_0", durationMs = 110L)),
    val selectedFrameIndex: Int = 0,
    val brushBrightness: Int = 255,
    val tapTogglesPixel: Boolean = true,
    val isLiveHardwareMirror: Boolean = false,
    val showCircularBezelOverlay: Boolean = true,
    val showFullPreviewComposite: Boolean = true,
    val compositeOverlayPixels: ByteArray? = null,
    val dryRunVariables: Map<String, String> = emptyMap(),
    val aiPromptInput: String = "",
    val emojiInput: String = "🚀",
    val photoContrast: Float = 1.45f,
    val photoThresholdPct: Int = 50,
    val photoEdgeDetectEnabled: Boolean = false,
    val photoDitherEnabled: Boolean = true,
    val photoInvertEnabled: Boolean = false,
    val hasImportedPhoto: Boolean = false,
    val isSequencerDrawerExpanded: Boolean = false,
    val loopCount: Int = 0, // 0 = infinite
    val transitionCurve: TransitionCurve = TransitionCurve.STEP_FADE,
    val isPreviewPlaying: Boolean = false,
    val previewPixels: ByteArray? = null,
    val previewFramePositionLabel: String = "1/1",
    val nudgeClampMode: NudgeClampMode = NudgeClampMode.SAFE_CLAMP,
    val activeDockTab: StudioDockTab = StudioDockTab.DRAW,
    val activeDrawTool: StudioDrawTool = StudioDrawTool.PEN,
    val decompiledRule: DecompiledStudioRule? = null,
    val visualLayers: List<DecompiledVisualLayer> = emptyList(),
    val selectedLayerId: String? = null,
    val isLayerMultiSelectMode: Boolean = false,
    val selectedLayerIdsForBatch: Set<String> = emptySet(),
    val isFrameMultiSelectMode: Boolean = false,
    val selectedFrameIndicesForBatch: Set<Int> = emptySet(),
    val globalAlign: String = "center",
    val pauseDurationMs: Long = 0L,
    // Marquee Selection & Clipboard (SELECT Shelf)
    val selectionStart: Pair<Int, Int>? = null,
    val selectionEnd: Pair<Int, Int>? = null,
    val clipboardPixels: ByteArray? = null,
    val clipboardWidth: Int = 0,
    val clipboardHeight: Int = 0,
    // Smart Guidelines & Pocket Fill (GUIDES Shelf)
    val rowGuides: List<Int> = emptyList(),
    val colGuides: List<Int> = emptyList(),
    val activeRowGuide: Int? = null,
    val activeColGuide: Int? = null,
    val axisLockEnabled: Boolean = false,
    val pocketFillMode: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val playbackMode: AnimationPlaybackMode = AnimationPlaybackMode.LOOP,
    val selectionRotationAngle: Float = 0f,
    val activeStudioRuleId: String? = null,
    val cursorAutoStepEnabled: Boolean = false,
    val lightingTargetSelectionOnly: Boolean = false,
    val stagingPreviewFrame: ByteArray? = null,
    val showPwmHeatmapOverlay: Boolean = false,
    val isLuminanceSamplerActive: Boolean = false,
    val scaleFactorX: Float = 1.0f,
    val scaleFactorY: Float = 1.0f,
    val scaleAspectLocked: Boolean = true,
    val scaleSourceBounds: IntArray? = null,
    val scaleActiveBounds: FloatArray? = null,
    val scaleBasePixels: ByteArray? = null
) {
    val scalePercent: Int
        get() = ((scaleFactorX + scaleFactorY) / 2f * 100f).roundToInt()

    val activeFrame: GlyphFrame
        get() = frames.getOrElse(selectedFrameIndex.coerceIn(0, (frames.size - 1).coerceAtLeast(0))) {
            GlyphFrame()
        }

    val selectedVisualLayer: DecompiledVisualLayer?
        get() = visualLayers.find { it.id == selectedLayerId } ?: visualLayers.firstOrNull()

    val editingLayerName: String
        get() = selectedVisualLayer?.name ?: "custom_matrix"

    val selectionBounds: IntArray?
        get() {
            val s = selectionStart ?: return null
            val e = selectionEnd ?: return null
            val minR = minOf(s.first, e.first).coerceIn(0, MATRIX_SIZE - 1)
            val maxR = maxOf(s.first, e.first).coerceIn(0, MATRIX_SIZE - 1)
            val minC = minOf(s.second, e.second).coerceIn(0, MATRIX_SIZE - 1)
            val maxC = maxOf(s.second, e.second).coerceIn(0, MATRIX_SIZE - 1)
            return intArrayOf(minR, minC, maxR, maxC)
        }
}

class GlyphFlowViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GlyphFlowApplication
    private val repository = app.repository
    private val hardwareController = app.hardwareController
    private val ruleEngine = app.ruleEngine

    private val _currentTab = MutableStateFlow(GlyphScreenTab.DASHBOARD)
    val currentTab: StateFlow<GlyphScreenTab> = _currentTab.asStateFlow()

    private val _isRuleCreatorOpen = MutableStateFlow(false)
    val isRuleCreatorOpen: StateFlow<Boolean> = _isRuleCreatorOpen.asStateFlow()

    val animations: StateFlow<List<GlyphAnimation>> = repository.animationsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val rules: StateFlow<List<TriggerRule>> = repository.rulesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val hardwareState: StateFlow<HardwareMatrixState> = hardwareController.matrixState
    val auditLogs: StateFlow<List<AuditLogEntry>> = EventIngestBus.auditLogs
    val scriptCompileHistory: StateFlow<List<ScriptCompileAuditEntry>> = EventIngestBus.scriptCompileHistory
    val hardwareBinderLogs: StateFlow<List<HardwareBinderAuditEntry>> = EventIngestBus.hardwareBinderLogs
    val payloadInspectorLogs: StateFlow<List<NotificationPayloadAuditEntry>> = EventIngestBus.payloadInspectorLogs
    val globalEngineEnabled: StateFlow<Boolean> = EventIngestBus.globalEngineEnabled
    val isForegroundServiceRunning: StateFlow<Boolean> = EventIngestBus.isForegroundServiceRunning
    val isNotificationListenerConnected: StateFlow<Boolean> = EventIngestBus.isNotificationListenerConnected
    val isNotificationSnifferActive: StateFlow<Boolean> = EventIngestBus.isNotificationSnifferActive
    val capturedSnifferNotification: StateFlow<NotificationPayloadAuditEntry?> = EventIngestBus.capturedSnifferNotification

    private val _isBatteryUnrestricted = MutableStateFlow(false)
    val isBatteryUnrestricted: StateFlow<Boolean> = _isBatteryUnrestricted.asStateFlow()

    private var lastClearedConsoleScriptBuffer: String? = null

    private val _activeStudioRuleId = MutableStateFlow<String?>(null)
    val activeStudioRuleId: StateFlow<String?> = _activeStudioRuleId.asStateFlow()

    private val _activeRule = MutableStateFlow<TriggerRule?>(null)
    val activeRule: StateFlow<TriggerRule?> = _activeRule.asStateFlow()

    private var lastSyncedScriptFromStudio: String = ""

    private val _studioState = MutableStateFlow(StudioEditorState())
    val studioState: StateFlow<StudioEditorState> = _studioState.asStateFlow()
    private val undoStack = ArrayDeque<List<GlyphFrame>>()
    private val redoStack = ArrayDeque<List<GlyphFrame>>()
    private var studioPreviewJob: Job? = null
    private var studioLiveKeepAliveJob: Job? = null
    private var consoleDryRunDebounceJob: Job? = null
    private var lastImportedPhotoBitmap: Bitmap? = null
    private var studioEntrySnapshot: StudioEditorState? = null
    private var consoleScriptSnapshotBeforeStudio: String? = null
    private var previousTabBeforeStudio: GlyphScreenTab = GlyphScreenTab.DASHBOARD
    private var rotationBasePixels: ByteArray? = null

    private val _consoleState = MutableStateFlow(
        ConsoleUiState(scriptInput = "")
    )
    val consoleState: StateFlow<ConsoleUiState> = _consoleState.asStateFlow()

    private val _draftRule = MutableStateFlow(TriggerRule(name = "Custom Signal Rule"))
    val draftRule: StateFlow<TriggerRule> = _draftRule.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppOption>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppOption>> = _installedApps.asStateFlow()

    private val _jsonModalPayload = MutableStateFlow<String?>(null)
    val jsonModalPayload: StateFlow<String?> = _jsonModalPayload.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            com.example.engine.ActiveTaskStateManager.clearAll(hardwareController)
            hardwareController.clearAndCloseSession()
            loadInstalledApps()
            refreshSystemPermissionsStatus()
            evaluateConsoleDryRunInternal(pushToHardware = false, recordAudit = false)
        }
        viewModelScope.launch {
            rules.collect { ruleList ->
                val currentActive = _activeRule.value
                val currentStudioId = _activeStudioRuleId.value?.toLongOrNull()
                if (currentActive != null) {
                    val refreshed = ruleList.firstOrNull { it.id == currentActive.id }
                    if (refreshed != null) {
                        _activeRule.value = refreshed
                    }
                } else if (currentStudioId != null && currentStudioId != 0L) {
                    val matched = ruleList.firstOrNull { it.id == currentStudioId }
                    if (matched != null) {
                        _activeRule.value = matched
                    }
                } else if (ruleList.isNotEmpty() && _consoleState.value.scriptInput.isBlank()) {
                    val firstActive = ruleList.firstOrNull { it.isEnabled } ?: ruleList.first()
                    _activeRule.value = firstActive
                }
            }
        }
    }

    /**
     * Strict Active Rule Binding when navigating into the [Studio] tab:
     * If an active rule or Console script is already open, binds the Studio session directly
     * to that persistent state rather than leaving an unlinked blank canvas session.
     */
    private fun bindStudioToActiveRuleOrConsoleIfNeeded() {
        val currentStudio = _studioState.value
        val consoleScript = _consoleState.value.scriptInput.trim()
        val activeRuleVal = _activeRule.value
            ?: _activeStudioRuleId.value?.toLongOrNull()?.let { id -> rules.value.firstOrNull { it.id == id } }

        val hasUnlinkedStudioSession = currentStudio.decompiledRule == null &&
            currentStudio.visualLayers.isEmpty() &&
            currentStudio.activeStudioRuleId == null

        val consoleChangedExternally = consoleScript.isNotBlank() &&
            consoleScript != lastSyncedScriptFromStudio &&
            currentStudio.decompiledRule?.originalProgram?.sourceText?.trim() != consoleScript

        if (consoleScript.isNotBlank() && (hasUnlinkedStudioSession || consoleChangedExternally)) {
            bindStudioToScriptInternal(
                script = consoleScript,
                ruleId = activeRuleVal?.id ?: currentStudio.decompiledRule?.originalRuleId ?: 0L,
                preserveSelectedLayerName = currentStudio.selectedVisualLayer?.name
            )
        } else if (hasUnlinkedStudioSession && activeRuleVal != null) {
            val scriptSource = activeRuleVal.glyphScriptSource.ifBlank {
                GlyphScriptCompiler.exportRuleToGlyphScript(activeRuleVal)
            }
            if (scriptSource.isNotBlank()) {
                _activeRule.value = activeRuleVal
                _activeStudioRuleId.value = activeRuleVal.id.toString()
                if (_consoleState.value.scriptInput.isBlank()) {
                    _consoleState.update { it.copy(scriptInput = scriptSource, compileError = null) }
                    evaluateConsoleDryRunInternal(pushToHardware = false, recordAudit = false)
                }
                bindStudioToScriptInternal(
                    script = scriptSource,
                    ruleId = activeRuleVal.id,
                    preserveSelectedLayerName = null
                )
            }
        }
    }

    private fun bindStudioToScriptInternal(
        script: String,
        ruleId: Long,
        preserveSelectedLayerName: String?
    ) {
        val context = getApplication<Application>()
        val console = _consoleState.value
        val compileRes = GlyphScriptCompiler.compile(
            scriptText = script,
            context = context,
            dryRunTitle = console.dryRunTitle,
            dryRunBody = console.dryRunBody,
            dryRunProgress = console.dryRunProgress
        )
        val effectiveVars = LinkedHashMap<String, String>().apply {
            if (compileRes is GlyphScriptCompileResult.Success) {
                putAll(compileRes.extractedPreviewVariables)
            } else {
                putAll(console.extractedVariablesPreview)
            }
            putAll(console.dryRunVariableOverrides)
        }
        val resolvedRuleId = if (ruleId != 0L) {
            ruleId
        } else {
            val parsedName = (compileRes as? GlyphScriptCompileResult.Success)?.program?.ruleName.orEmpty()
            rules.value.firstOrNull {
                parsedName.isNotBlank() && it.name.equals(parsedName, ignoreCase = true)
            }?.id ?: 0L
        }
        val decompiled = GlyphScriptDecompiler.decompile(
            scriptSource = script,
            context = context,
            ruleId = resolvedRuleId,
            dryRunVariables = effectiveVars
        )
        val preservedLayer = preserveSelectedLayerName?.let { targetName ->
            decompiled.visualLayers.firstOrNull { it.name == targetName }
        }
        val selectedId = preservedLayer?.id
            ?: decompiled.initialSelectedLayerId
            ?: decompiled.visualLayers.firstOrNull()?.id
        val selectedFrames = decompiled.visualLayers.firstOrNull { it.id == selectedId }?.frames?.ifEmpty { null }
            ?: decompiled.frames
        val overlay = GlyphScriptDecompiler.renderCompositeOverlay(
            layers = decompiled.visualLayers,
            selectedLayerId = selectedId,
            variables = decompiled.dryRunVariables
        )
        val ruleIdStr = if (resolvedRuleId != 0L) resolvedRuleId.toString() else decompiled.ruleName
        _activeStudioRuleId.value = ruleIdStr
        lastSyncedScriptFromStudio = script

        _studioState.update {
            it.copy(
                name = decompiled.ruleName,
                frames = selectedFrames,
                selectedFrameIndex = 0,
                visualLayers = decompiled.visualLayers,
                selectedLayerId = selectedId,
                decompiledRule = decompiled,
                dryRunVariables = decompiled.dryRunVariables,
                showFullPreviewComposite = true,
                compositeOverlayPixels = overlay,
                globalAlign = decompiled.globalAlign,
                activeStudioRuleId = ruleIdStr
            )
        }
    }

    fun selectTab(tab: GlyphScreenTab) {
        val leavingStudio = _currentTab.value == GlyphScreenTab.STUDIO && tab != GlyphScreenTab.STUDIO
        if (leavingStudio) {
            recompileAndRunDryRun(pushToHardware = false)
        }
        if (tab == GlyphScreenTab.STUDIO && _currentTab.value != GlyphScreenTab.STUDIO) {
            previousTabBeforeStudio = _currentTab.value
            bindStudioToActiveRuleOrConsoleIfNeeded()
            studioEntrySnapshot = _studioState.value.copy(
                frames = _studioState.value.frames.map { it.copy(pixels = it.pixels.copyOf()) }
            )
            consoleScriptSnapshotBeforeStudio = _consoleState.value.scriptInput
        }
        _currentTab.value = tab
        refreshSystemPermissionsStatus()
        if (tab == GlyphScreenTab.STUDIO && _studioState.value.isLiveHardwareMirror) {
            startStudioLiveKeepAlive()
        } else {
            stopStudioLiveKeepAlive()
            if (tab != GlyphScreenTab.STUDIO && _studioState.value.isPreviewPlaying) {
                stopStudioPreview()
            }
        }
    }

    fun createBlankApertureFrame(durationMs: Long = 110L): GlyphFrame {
        return GlyphFrame(
            id = "f_0",
            durationMs = durationMs,
            pixels = ByteArray(MATRIX_CELL_COUNT)
        )
    }

    /**
     * Completely tears down the current Studio session, cancels active preview/live jobs,
     * clears working canvas buffers to a fresh 25x25 aperture, and zeroes physical LEDs.
     */
    fun resetStudioSession() {
        // 1. Cancel running preview streaming loops
        stopStudioPreview()
        stopStudioLiveKeepAlive()

        // 2. Clear out working canvas buffers
        _studioState.update { state ->
            state.copy(
                activeStudioRuleId = null,
                editingAnimationId = 0L,
                name = "Circular Matrix 01",
                decompiledRule = null,
                visualLayers = emptyList(),
                selectedLayerId = null,
                isLayerMultiSelectMode = false,
                selectedLayerIdsForBatch = emptySet(),
                isFrameMultiSelectMode = false,
                selectedFrameIndicesForBatch = emptySet(),
                frames = listOf(createBlankApertureFrame()),
                selectedFrameIndex = 0,
                selectionStart = null,
                selectionEnd = null,
                clipboardPixels = null,
                selectionRotationAngle = 0f,
                stagingPreviewFrame = null,
                isLiveHardwareMirror = false,
                isPreviewPlaying = false,
                previewPixels = null,
                canUndo = false,
                canRedo = false,
                scaleSourceBounds = null,
                scaleActiveBounds = null,
                scaleBasePixels = null,
                scaleFactorX = 1.0f,
                scaleFactorY = 1.0f,
                showPwmHeatmapOverlay = false,
                isLuminanceSamplerActive = false
            )
        }

        _activeStudioRuleId.value = null
        _activeRule.value = null
        undoStack.clear()
        redoStack.clear()
        rotationBasePixels = null
        studioEntrySnapshot = null
        consoleScriptSnapshotBeforeStudio = null

        // 3. Clear hardware stream if active
        hardwareController.clearPhysicalMatrix()
    }

    /**
     * Discards unsaved Studio edits, resets the entire Studio session state,
     * clears hardware mirrors, and returns to the previous tab.
     */
    fun discardStudioEditsAndExit() {
        resetStudioSession()
        val scriptSnap = consoleScriptSnapshotBeforeStudio
        if (scriptSnap != null) {
            _consoleState.update { it.copy(scriptInput = scriptSnap, compileError = null) }
            evaluateConsoleDryRunInternal(pushToHardware = false, recordAudit = false)
        }
        val targetTab = if (previousTabBeforeStudio == GlyphScreenTab.STUDIO) {
            GlyphScreenTab.DASHBOARD
        } else {
            previousTabBeforeStudio
        }
        _currentTab.value = targetTab
        showToast("Studio edits discarded")
    }

    /**
     * Loads a specific trigger rule into Matrix Studio by ID, clearing stale buffers first.
     */
    fun loadRuleIntoStudio(ruleId: Long) {
        val targetRule = rules.value.firstOrNull { it.id == ruleId } ?: return
        editRuleInStudio(targetRule)
    }

    fun dismissToast() {
        _toastMessage.value = null
    }

    private fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun refreshSystemPermissionsStatus() {
        val ctx = getApplication<Application>()
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners").orEmpty()
        val cn = ComponentName(ctx, com.example.service.GlyphNotificationListenerService::class.java)
        val granted = flat.contains(cn.flattenToString()) || flat.contains(ctx.packageName)
        EventIngestBus.setNotificationListenerConnected(granted)

        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        _isBatteryUnrestricted.value = runCatching {
            pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
        }.getOrDefault(false)
    }

    fun refreshNotificationAccessStatus() {
        refreshSystemPermissionsStatus()
    }

    fun toggleGlobalKillSwitch(enabled: Boolean) {
        EventIngestBus.setGlobalEngineEnabled(enabled)
        if (!enabled) {
            hardwareController.clearAndCloseSession()
            showToast("Engine Muted — Rear Matrix Cleared")
        } else {
            showToast("Matrix Engine Armed")
        }
    }

    fun toggleForegroundService(context: Context) {
        if (EventIngestBus.isForegroundServiceRunning.value) {
            GlyphEngineForegroundService.stopEngine(context)
            showToast("Background Engine Stopped")
        } else {
            runCatching {
                GlyphEngineForegroundService.startEngine(context)
                showToast("Background Engine Active")
            }.onFailure {
                EventIngestBus.setForegroundServiceRunning(true)
            }
        }
    }

    fun stopActiveMatrixSession() {
        stopStudioPreview()
        stopStudioLiveKeepAlive()
        hardwareController.clearAndCloseSession()
    }

    fun runHardwareSelfAudit() {
        stopStudioPreview()
        stopStudioLiveKeepAlive()
        hardwareController.runHardwareSelfAudit()
        showToast("Running 3-Step Hardware Self-Test...")
    }

    fun verifyPhysicalToggle() {
        stopStudioPreview()
        stopStudioLiveKeepAlive()
        val err = hardwareController.verifyPhysicalToggle()
        if (err == null) {
            showToast("Physical 625-LED Matrix Frame Sent (255 Brightness)")
        }
    }

    // --- Studio Circular Matrix Board, Live Mirror, AI Assistant & Photo Converter ---

    fun toggleStudioLiveMirror(enabled: Boolean) {
        _studioState.update { it.copy(isLiveHardwareMirror = enabled) }
        if (enabled) {
            hardwareController.invalidateDispatchedBuffer()
            renderCurrentStudioCompositeToHardware("LIVE MIRROR ON // ${_studioState.value.name}")
            startStudioLiveKeepAlive()
            showToast("LIVE Glyph Mirror ON — Real-Time 30 FPS Stream")
        } else {
            stopStudioLiveKeepAlive()
            hardwareController.clearAndCloseSession()
            showToast("LIVE Glyph Mirror OFF")
        }
    }

    private fun startStudioLiveKeepAlive() {
        studioLiveKeepAliveJob?.cancel()
        studioLiveKeepAliveJob = viewModelScope.launch {
            while (isActive &&
                _currentTab.value == GlyphScreenTab.STUDIO &&
                _studioState.value.isLiveHardwareMirror &&
                !_studioState.value.isPreviewPlaying
            ) {
                val current = _studioState.value
                val fg = current.activeFrame.pixels
                val bg = if (current.showFullPreviewComposite) current.compositeOverlayPixels else null
                val out = if (bg != null) {
                    ByteArray(MATRIX_CELL_COUNT) { idx ->
                        maxOf(fg[idx].toInt() and 0xFF, bg[idx].toInt() and 0xFF).toByte()
                    }
                } else {
                    fg
                }
                hardwareController.renderImmediateFrame(
                    pixels = out,
                    frameLabel = "LIVE MIRROR // ${current.name}",
                    pushToHardware = true,
                    activeFps = 30,
                    isPersistentStream = true
                )
                delay(45L)
            }
        }
    }

    private fun stopStudioLiveKeepAlive() {
        studioLiveKeepAliveJob?.cancel()
        studioLiveKeepAliveJob = null
    }

    fun toggleStudioBezelOverlay(enabled: Boolean) {
        _studioState.update { it.copy(showCircularBezelOverlay = enabled) }
    }

    fun toggleSequencerDrawer(expanded: Boolean? = null) {
        _studioState.update {
            it.copy(isSequencerDrawerExpanded = expanded ?: !it.isSequencerDrawerExpanded)
        }
    }

    fun setStudioBrushBrightness(brightness: Int) {
        _studioState.update { it.copy(brushBrightness = brightness.coerceIn(0, 255)) }
    }

    fun setTapTogglesPixel(enabled: Boolean) {
        _studioState.update { it.copy(tapTogglesPixel = enabled) }
    }

    fun updateStudioName(name: String) {
        _studioState.update { it.copy(name = name) }
    }

    fun updateStudioTags(tags: String) {
        _studioState.update { it.copy(tagsInput = tags) }
    }

    fun updateStudioLoopCount(loops: Int) {
        _studioState.update { it.copy(loopCount = loops.coerceIn(0, 50)) }
    }

    fun updateStudioTransitionCurve(curve: TransitionCurve) {
        _studioState.update { it.copy(transitionCurve = curve) }
    }

    fun updateAiPromptInput(prompt: String) {
        _studioState.update { it.copy(aiPromptInput = prompt) }
    }

    /**
     * AI Matrix Assistant (Prompt-to-Glyph):
     * Converts a text prompt into a 25x25 circular bitmap / keyframe sequence and paints it onto the canvas.
     */
    fun generateFromAiPrompt(overridePrompt: String? = null) {
        val prompt = (overridePrompt ?: _studioState.value.aiPromptInput).trim()
        if (prompt.isBlank()) {
            showToast("Type a prompt (e.g. 'right arrow', 'heartbeat', 'lightning bolt')")
            return
        }
        stopStudioPreview()
        val current = _studioState.value
        pushUndoSnapshot(current.frames)

        val (generatedTitle, generatedFrames) = GlyphPatternGenerator.generateFromPrompt(
            prompt = prompt,
            peakBrightness = current.brushBrightness.coerceAtLeast(180)
        )

        _studioState.update {
            it.copy(
                name = generatedTitle,
                aiPromptInput = prompt,
                frames = generatedFrames,
                selectedFrameIndex = 0
            )
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)

        val firstFrame = generatedFrames.first()
        hardwareController.renderImmediateFrame(
            pixels = firstFrame.pixels,
            frameLabel = "PROMPT // $generatedTitle",
            pushToHardware = current.isLiveHardwareMirror
        )
        showToast("Generated \"$generatedTitle\" (${generatedFrames.size} frame${if (generatedFrames.size > 1) "s" else ""})")
    }

    /**
     * Photo to Glyph (Image Converter):
     * Loads an image from a gallery Uri, crops 1:1, downscales to 25x25, applies grayscale contrast
     * thresholding & dithering (0..255), and loads it into the circular Studio canvas.
     */
    fun importImageUriToGlyph(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val decoded = runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()

            withContext(Dispatchers.Main) {
                if (decoded != null) {
                    applyBitmapToStudioCanvas(decoded, label = "Imported Photo")
                } else {
                    showToast("Could not decode selected image")
                }
            }
        }
    }

    fun updateEmojiInput(emoji: String) {
        _studioState.update { it.copy(emojiInput = emoji) }
    }

    /**
     * Emoji-to-LED Rasterizer:
     * Renders the native emoji onto an internal 25x25 canvas, binarizes/quantizes luminance into
     * discrete PWM brightness levels (`64`, `128`, `255`) with anti-aliased edge smoothing inside
     * the 489-LED circular mask, and converts the result directly into an editable `bitmap("""...""")`
     * layer in Matrix Studio.
     */
    fun rasterizeEmojiToStudioCanvas(emojiOverride: String? = null, recordToast: Boolean = true) {
        val current = _studioState.value
        val emoji = (emojiOverride ?: current.emojiInput).trim().ifEmpty { "🚀" }
        stopStudioPreview()
        if (recordToast) {
            pushUndoSnapshot(current.frames)
        }

        val rasterizedBytes = GlyphPatternGenerator.rasterizeEmojiToCircularMatrixBytes(
            emojiInput = emoji,
            thresholdPct = current.photoThresholdPct,
            peakBrightness = current.brushBrightness.coerceAtLeast(180)
        )
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val updatedFrame = current.frames[activeIdx].copy(pixels = rasterizedBytes)
        val updatedFrames = current.frames.toMutableList().apply {
            set(activeIdx, updatedFrame)
        }

        _studioState.update {
            it.copy(
                emojiInput = emoji,
                frames = updatedFrames
            )
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("EMOJI // $emoji")
        if (recordToast) {
            showToast("Rasterized Emoji $emoji to 25×25 PWM Bitmap")
        }
    }

    fun loadSamplePhotoToGlyph() {
        val sampleBmp = GlyphPatternGenerator.createSampleCameraBitmap()
        applyBitmapToStudioCanvas(sampleBmp, label = "Sample Lens Glyph")
    }

    fun updatePhotoConverterSettings(
        contrast: Float = _studioState.value.photoContrast,
        dither: Boolean = _studioState.value.photoDitherEnabled,
        invert: Boolean = _studioState.value.photoInvertEnabled
    ) {
        _studioState.update {
            it.copy(
                photoContrast = contrast.coerceIn(0.5f, 3.0f),
                photoDitherEnabled = dither,
                photoInvertEnabled = invert
            )
        }
        val bmp = lastImportedPhotoBitmap ?: GlyphPatternGenerator.createSampleCameraBitmap()
        applyBitmapToStudioCanvas(bmp, label = "Photo Glyph", recordToast = false)
    }

    fun updatePhotoThresholdPct(thresholdPct: Int) {
        val clamped = thresholdPct.coerceIn(0, 100)
        val mappedContrast = (0.75f + (clamped / 100f) * 1.85f).coerceIn(0.5f, 3.0f)
        _studioState.update {
            it.copy(
                photoThresholdPct = clamped,
                photoContrast = mappedContrast
            )
        }
        val bmp = lastImportedPhotoBitmap
        if (bmp != null || _studioState.value.hasImportedPhoto) {
            applyBitmapToStudioCanvas(
                bmp ?: GlyphPatternGenerator.createSampleCameraBitmap(),
                label = _studioState.value.name.ifBlank { "Photo Glyph" },
                recordToast = false
            )
        } else if (_studioState.value.emojiInput.isNotBlank()) {
            rasterizeEmojiToStudioCanvas(_studioState.value.emojiInput, recordToast = false)
        }
    }

    fun togglePhotoEdgeDetect(enabled: Boolean? = null) {
        val nextEdge = enabled ?: !_studioState.value.photoEdgeDetectEnabled
        _studioState.update { it.copy(photoEdgeDetectEnabled = nextEdge) }
        val bmp = lastImportedPhotoBitmap ?: GlyphPatternGenerator.createSampleCameraBitmap()
        applyBitmapToStudioCanvas(bmp, label = _studioState.value.name.ifBlank { "Edge Blueprint" }, recordToast = true)
    }

    private fun applyBitmapToStudioCanvas(
        bitmap: Bitmap,
        label: String,
        recordToast: Boolean = true
    ) {
        lastImportedPhotoBitmap = bitmap
        val current = _studioState.value
        if (recordToast) {
            pushUndoSnapshot(current.frames)
        }

        val convertedPixels = GlyphPatternGenerator.convertBitmapToCircularGlyph(
            source = bitmap,
            contrast = current.photoContrast,
            dither = current.photoDitherEnabled,
            invert = current.photoInvertEnabled,
            thresholdPct = current.photoThresholdPct,
            edgeDetectEnabled = current.photoEdgeDetectEnabled
        )

        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val updatedFrame = current.frames[activeIdx].copy(pixels = convertedPixels)
        val updatedFrames = current.frames.toMutableList().apply {
            set(activeIdx, updatedFrame)
        }

        _studioState.update {
            it.copy(
                name = label,
                frames = updatedFrames,
                hasImportedPhoto = true
            )
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)

        hardwareController.renderImmediateFrame(
            pixels = convertedPixels,
            frameLabel = "PHOTO // $label",
            pushToHardware = current.isLiveHardwareMirror
        )

        if (recordToast) {
            showToast("Converted 1:1 Photo to 25×25 Circular Glyph")
        }
    }

    /**
     * Paints or toggles a single dot inside the circular 25x25 matrix aperture
     * (`(col - 12)^2 + (row - 12)^2 <= 12.5^2`), honoring Axis Lock and Pocket Fill when enabled.
     */
    fun onCanvasCellInteraction(row: Int, col: Int, isDrag: Boolean) {
        val current = _studioState.value
        val lockedRow = if (current.axisLockEnabled && current.activeRowGuide != null) {
            current.activeRowGuide
        } else row
        val lockedCol = if (current.axisLockEnabled && current.activeColGuide != null && current.activeRowGuide == null) {
            current.activeColGuide
        } else col

        if (lockedRow !in 0 until MATRIX_SIZE || lockedCol !in 0 until MATRIX_SIZE) return
        if (!GlyphAperture.isPhysicalLed(lockedCol, lockedRow)) return

        if (current.pocketFillMode && !isDrag) {
            fillPocketAt(lockedRow, lockedCol)
            return
        }

        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val frame = current.frames[activeIdx]
        val existingBrightness = frame.getBrightness(lockedRow, lockedCol)

        val targetBrightness = when {
            current.activeDrawTool == StudioDrawTool.ERASER -> 0
            !isDrag && current.tapTogglesPixel -> {
                if (existingBrightness > 0) 0 else current.brushBrightness.coerceAtLeast(32)
            }
            else -> current.brushBrightness
        }

        if (existingBrightness == targetBrightness) return

        if (!isDrag) {
            pushUndoSnapshot(current.frames)
        }

        val updatedFrame = frame.withPixel(lockedRow, lockedCol, targetBrightness)
        val updatedFrames = current.frames.toMutableList().apply {
            set(activeIdx, updatedFrame)
        }
        _studioState.update {
            it.copy(frames = updatedFrames)
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("${current.name} [F${activeIdx + 1}]")
    }

    /**
     * Explicitly adds or removes a dot at ([row], [col]) on the active layer canvas.
     */
    fun setCanvasDotExplicit(row: Int, col: Int, addDot: Boolean) {
        if (row !in 0 until MATRIX_SIZE || col !in 0 until MATRIX_SIZE) return
        if (!GlyphAperture.isPhysicalLed(col, row)) {
            showToast("Cell ($col, $row) is outside physical 489-LED circular aperture")
            return
        }

        val current = _studioState.value
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val frame = current.frames[activeIdx]
        val targetBrightness = if (addDot) current.brushBrightness.coerceIn(32, 255) else 0
        if (frame.getBrightness(row, col) == targetBrightness) return

        pushUndoSnapshot(current.frames)
        val updatedFrame = frame.withPixel(row, col, targetBrightness)
        val updatedFrames = current.frames.toMutableList().apply {
            set(activeIdx, updatedFrame)
        }
        _studioState.update {
            it.copy(frames = updatedFrames)
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("${if (addDot) "ADD" else "REMOVE"} DOT ($col,$row)")
    }

    // --- Marquee Region Selection & Clipboard Transforms (SELECT Shelf) ---

    fun setSelectionRegion(startRow: Int, startCol: Int, endRow: Int, endCol: Int) {
        val sr = startRow.coerceIn(0, MATRIX_SIZE - 1)
        val sc = startCol.coerceIn(0, MATRIX_SIZE - 1)
        val er = endRow.coerceIn(0, MATRIX_SIZE - 1)
        val ec = endCol.coerceIn(0, MATRIX_SIZE - 1)
        rotationBasePixels = null
        _studioState.update {
            it.copy(
                selectionStart = minOf(sr, er) to minOf(sc, ec),
                selectionEnd = maxOf(sr, er) to maxOf(sc, ec),
                selectionRotationAngle = 0f
            )
        }
    }

    fun clearSelectionRegion() {
        rotationBasePixels = null
        _studioState.update {
            it.copy(selectionStart = null, selectionEnd = null, selectionRotationAngle = 0f)
        }
    }

    fun copySelectedRegion() {
        val current = _studioState.value
        val bounds = current.selectionBounds ?: intArrayOf(0, 0, MATRIX_SIZE - 1, MATRIX_SIZE - 1)
        val (minR, minC, maxR, maxC) = bounds
        val w = (maxC - minC + 1).coerceAtLeast(1)
        val h = (maxR - minR + 1).coerceAtLeast(1)
        val src = current.activeFrame.pixels
        val clipBuf = ByteArray(w * h)
        var litCount = 0
        for (r in minR..maxR) {
            for (c in minC..maxC) {
                val b = if (GlyphAperture.isPhysicalLed(c, r)) src[r * MATRIX_SIZE + c] else 0
                clipBuf[(r - minR) * w + (c - minC)] = b
                if ((b.toInt() and 0xFF) > 0) litCount++
            }
        }
        _studioState.update {
            it.copy(
                clipboardPixels = clipBuf,
                clipboardWidth = w,
                clipboardHeight = h
            )
        }
        showToast("Copied ${w}×${h} region ($litCount LEDs)")
    }

    fun cutSelectedRegion() {
        val current = _studioState.value
        val bounds = current.selectionBounds ?: intArrayOf(0, 0, MATRIX_SIZE - 1, MATRIX_SIZE - 1)
        val (minR, minC, maxR, maxC) = bounds
        copySelectedRegion()
        pushUndoSnapshot(current.frames)
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val dst = current.activeFrame.pixels.copyOf()
        for (r in minR..maxR) {
            for (c in minC..maxC) {
                dst[r * MATRIX_SIZE + c] = 0
            }
        }
        val updatedFrame = current.activeFrame.copy(pixels = dst)
        val updatedFrames = current.frames.toMutableList().apply { set(activeIdx, updatedFrame) }
        _studioState.update { it.copy(frames = updatedFrames) }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("CUT REGION")
    }

    fun pasteClipboardRegion(targetTopRow: Int? = null, targetLeftCol: Int? = null) {
        val current = _studioState.value
        val clip = current.clipboardPixels ?: run {
            showToast("Clipboard is empty — Copy or Cut a region first")
            return
        }
        val w = current.clipboardWidth
        val h = current.clipboardHeight
        if (w <= 0 || h <= 0) return

        val bounds = current.selectionBounds
        val topR = targetTopRow ?: bounds?.get(0) ?: ((MATRIX_SIZE - h) / 2)
        val leftC = targetLeftCol ?: bounds?.get(1) ?: ((MATRIX_SIZE - w) / 2)

        pushUndoSnapshot(current.frames)
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val dst = current.activeFrame.pixels.copyOf()
        for (lr in 0 until h) {
            for (lc in 0 until w) {
                val r = topR + lr
                val c = leftC + lc
                if (r in 0 until MATRIX_SIZE && c in 0 until MATRIX_SIZE && GlyphAperture.isPhysicalLed(c, r)) {
                    val v = clip[lr * w + lc].toInt() and 0xFF
                    if (v > 0) {
                        dst[r * MATRIX_SIZE + c] = v.toByte()
                    }
                }
            }
        }
        val clamped = GlyphAperture.clampToPhysicalAperture(dst)
        val updatedFrame = current.activeFrame.copy(pixels = clamped)
        val updatedFrames = current.frames.toMutableList().apply { set(activeIdx, updatedFrame) }
        _studioState.update {
            it.copy(
                frames = updatedFrames,
                selectionStart = topR.coerceIn(0, MATRIX_SIZE - 1) to leftC.coerceIn(0, MATRIX_SIZE - 1),
                selectionEnd = (topR + h - 1).coerceIn(0, MATRIX_SIZE - 1) to (leftC + w - 1).coerceIn(0, MATRIX_SIZE - 1)
            )
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("PASTE REGION")
        showToast("Pasted ${w}×${h} region into Frame ${activeIdx + 1}")
    }

    fun nudgeSelectedRegion(dx: Int, dy: Int) {
        val current = _studioState.value
        val bounds = current.selectionBounds ?: run {
            nudgeStudioCanvas(dx, dy)
            return
        }
        val (minR, minC, maxR, maxC) = bounds
        pushUndoSnapshot(current.frames)
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val src = current.activeFrame.pixels
        val dst = src.copyOf()

        // Clear original selected region
        for (r in minR..maxR) {
            for (c in minC..maxC) {
                dst[r * MATRIX_SIZE + c] = 0
            }
        }
        // Write shifted region clamped to 489 physical LEDs
        for (r in minR..maxR) {
            for (c in minC..maxC) {
                val b = src[r * MATRIX_SIZE + c].toInt() and 0xFF
                if (b > 0) {
                    val nr = r + dy
                    val nc = c + dx
                    if (nr in 0 until MATRIX_SIZE && nc in 0 until MATRIX_SIZE && GlyphAperture.isPhysicalLed(nc, nr)) {
                        dst[nr * MATRIX_SIZE + nc] = b.toByte()
                    }
                }
            }
        }
        val clamped = GlyphAperture.clampToPhysicalAperture(dst)
        val updatedFrame = current.activeFrame.copy(pixels = clamped)
        val updatedFrames = current.frames.toMutableList().apply { set(activeIdx, updatedFrame) }
        val nextMinR = (minR + dy).coerceIn(0, MATRIX_SIZE - 1)
        val nextMaxR = (maxR + dy).coerceIn(0, MATRIX_SIZE - 1)
        val nextMinC = (minC + dx).coerceIn(0, MATRIX_SIZE - 1)
        val nextMaxC = (maxC + dx).coerceIn(0, MATRIX_SIZE - 1)
        _studioState.update {
            it.copy(
                frames = updatedFrames,
                selectionStart = nextMinR to nextMinC,
                selectionEnd = nextMaxR to nextMaxC
            )
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("NUDGE SELECTION [$dx,$dy]")
    }

    fun flipSelectedRegion(horizontal: Boolean) {
        val current = _studioState.value
        val bounds = current.selectionBounds ?: run {
            applyMatrixTransform(if (horizontal) MatrixTransform.MIRROR_HORIZONTAL else MatrixTransform.MIRROR_VERTICAL)
            return
        }
        val (minR, minC, maxR, maxC) = bounds
        pushUndoSnapshot(current.frames)
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val src = current.activeFrame.pixels
        val dst = src.copyOf()

        for (r in minR..maxR) {
            for (c in minC..maxC) {
                dst[r * MATRIX_SIZE + c] = 0
            }
        }
        for (r in minR..maxR) {
            for (c in minC..maxC) {
                val tr = if (horizontal) r else (maxR - (r - minR))
                val tc = if (horizontal) (maxC - (c - minC)) else c
                if (tr in 0 until MATRIX_SIZE && tc in 0 until MATRIX_SIZE && GlyphAperture.isPhysicalLed(tc, tr)) {
                    dst[tr * MATRIX_SIZE + tc] = src[r * MATRIX_SIZE + c]
                }
            }
        }
        val clamped = GlyphAperture.clampToPhysicalAperture(dst)
        val updatedFrame = current.activeFrame.copy(pixels = clamped)
        val updatedFrames = current.frames.toMutableList().apply { set(activeIdx, updatedFrame) }
        _studioState.update { it.copy(frames = updatedFrames) }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware(if (horizontal) "FLIP REGION H" else "FLIP REGION V")
    }

    fun rotateSelectedRegion90() {
        val current = _studioState.value
        val bounds = current.selectionBounds ?: run {
            applyMatrixTransform(MatrixTransform.ROTATE_90)
            return
        }
        val (minR, minC, maxR, maxC) = bounds
        val h = maxR - minR + 1
        val w = maxC - minC + 1
        val centerR = (minR + maxR) / 2.0
        val centerC = (minC + maxC) / 2.0

        pushUndoSnapshot(current.frames)
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val src = current.activeFrame.pixels
        val dst = src.copyOf()

        for (r in minR..maxR) {
            for (c in minC..maxC) {
                dst[r * MATRIX_SIZE + c] = 0
            }
        }
        for (r in minR..maxR) {
            for (c in minC..maxC) {
                val b = src[r * MATRIX_SIZE + c].toInt() and 0xFF
                if (b > 0) {
                    val dr = r - centerR
                    val dc = c - centerC
                    // 90 deg clockwise: (dr, dc) -> (dc, -dr)
                    val nr = (centerR + dc).roundToInt()
                    val nc = (centerC - dr).roundToInt()
                    if (nr in 0 until MATRIX_SIZE && nc in 0 until MATRIX_SIZE && GlyphAperture.isPhysicalLed(nc, nr)) {
                        dst[nr * MATRIX_SIZE + nc] = b.toByte()
                    }
                }
            }
        }
        val clamped = GlyphAperture.clampToPhysicalAperture(dst)
        val updatedFrame = current.activeFrame.copy(pixels = clamped)
        val updatedFrames = current.frames.toMutableList().apply { set(activeIdx, updatedFrame) }
        val newMinR = (centerR - w / 2.0).roundToInt().coerceIn(0, MATRIX_SIZE - 1)
        val newMaxR = (newMinR + w - 1).coerceIn(0, MATRIX_SIZE - 1)
        val newMinC = (centerC - h / 2.0).roundToInt().coerceIn(0, MATRIX_SIZE - 1)
        val newMaxC = (newMinC + h - 1).coerceIn(0, MATRIX_SIZE - 1)
        _studioState.update {
            it.copy(
                frames = updatedFrames,
                selectionStart = newMinR to newMinC,
                selectionEnd = newMaxR to newMaxC
            )
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("ROTATE REGION 90")
    }

    /**
     * Rotates the selected marquee bounding box (or full 489-LED canvas if no selection) by an
     * arbitrary angle in degrees around the geometric centroid (cx, cy) of the active pixel cluster:
     *   x' = round(cx + (x - cx)*cos(theta) - (y - cy)*sin(theta))
     *   y' = round(cy + (x - cx)*sin(theta) + (y - cy)*cos(theta))
     * Clamping and masking resulting coordinates to the 489-LED circular boundary.
     */
    fun rotateSelectedRegionByAngle(deltaDegrees: Float) {
        val rawNext = _studioState.value.selectionRotationAngle + deltaDegrees
        val nextAngle = when {
            rawNext > 180f -> rawNext - 360f
            rawNext < -180f -> rawNext + 360f
            else -> rawNext
        }.coerceIn(-180f, 180f)
        setSelectionRotationAngle(nextAngle)
    }

    fun setSelectionRotationAngle(angleDegrees: Float) {
        val current = _studioState.value
        val clampedAngle = angleDegrees.coerceIn(-180f, 180f)
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)

        val basePixels = rotationBasePixels ?: run {
            pushUndoSnapshot(current.frames)
            val snap = current.activeFrame.pixels.copyOf()
            rotationBasePixels = snap
            snap
        }

        val bounds = current.selectionBounds ?: intArrayOf(0, 0, MATRIX_SIZE - 1, MATRIX_SIZE - 1)
        val (minR, minC, maxR, maxC) = bounds

        // Compute true geometric centroid (cx, cy) of the active pixel cluster inside the selection
        var sumX = 0.0
        var sumY = 0.0
        var countLit = 0
        for (r in minR..maxR) {
            for (c in minC..maxC) {
                val b = basePixels[r * MATRIX_SIZE + c].toInt() and 0xFF
                if (b > 0) {
                    sumX += c.toDouble()
                    sumY += r.toDouble()
                    countLit++
                }
            }
        }
        val cx = when {
            countLit > 0 -> sumX / countLit.toDouble()
            current.selectionBounds != null -> (minC + maxC) / 2.0
            else -> 12.0
        }
        val cy = when {
            countLit > 0 -> sumY / countLit.toDouble()
            current.selectionBounds != null -> (minR + maxR) / 2.0
            else -> 12.0
        }

        val dst = basePixels.copyOf()
        for (r in minR..maxR) {
            for (c in minC..maxC) {
                dst[r * MATRIX_SIZE + c] = 0
            }
        }

        val rad = Math.toRadians(clampedAngle.toDouble())
        val cosT = cos(rad)
        val sinT = sin(rad)

        for (r in minR..maxR) {
            for (c in minC..maxC) {
                val b = basePixels[r * MATRIX_SIZE + c].toInt() and 0xFF
                if (b > 0) {
                    val dx = c - cx
                    val dy = r - cy
                    val nx = (cx + dx * cosT - dy * sinT).roundToInt()
                    val ny = (cy + dx * sinT + dy * cosT).roundToInt()
                    if (nx in 0 until MATRIX_SIZE && ny in 0 until MATRIX_SIZE && GlyphAperture.isPhysicalLed(nx, ny)) {
                        val existing = dst[ny * MATRIX_SIZE + nx].toInt() and 0xFF
                        if (b > existing) {
                            dst[ny * MATRIX_SIZE + nx] = b.toByte()
                        }
                    }
                }
            }
        }

        val clamped = GlyphAperture.clampToPhysicalAperture(dst)
        val updatedFrame = current.activeFrame.copy(pixels = clamped)
        val updatedFrames = current.frames.toMutableList().apply { set(activeIdx, updatedFrame) }
        _studioState.update {
            it.copy(
                frames = updatedFrames,
                selectionRotationAngle = clampedAngle
            )
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("ROTATE ${clampedAngle.roundToInt()}°")
    }

    fun commitSelectionRotation() {
        val currentAngle = _studioState.value.selectionRotationAngle
        rotationBasePixels = null
        _studioState.update { it.copy(selectionRotationAngle = 0f) }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        if (kotlin.math.abs(currentAngle) > 0.4f) {
            showToast("Committed ${currentAngle.roundToInt()}° rotation to layer")
        }
    }

    fun resetSelectionRotation() {
        commitSelectionRotation()
    }

    // --- Smart Interactive Guidelines & Pocket Fill (GUIDES Shelf) ---

    fun addRowGuide(row: Int? = null) {
        _studioState.update { state ->
            val candidate = row?.coerceIn(0, MATRIX_SIZE - 1) ?: run {
                val defaults = listOf(8, 16, 12, 5, 19, 3, 21)
                defaults.firstOrNull { it !in state.rowGuides } ?: ((state.rowGuides.lastOrNull() ?: 10) + 2).coerceIn(0, 24)
            }
            val next = (state.rowGuides + candidate).distinct().sorted()
            state.copy(rowGuides = next, activeRowGuide = candidate)
        }
    }

    fun addColGuide(col: Int? = null) {
        _studioState.update { state ->
            val candidate = col?.coerceIn(0, MATRIX_SIZE - 1) ?: run {
                val defaults = listOf(8, 16, 12, 5, 19, 3, 21)
                defaults.firstOrNull { it !in state.colGuides } ?: ((state.colGuides.lastOrNull() ?: 10) + 2).coerceIn(0, 24)
            }
            val next = (state.colGuides + candidate).distinct().sorted()
            state.copy(colGuides = next, activeColGuide = candidate)
        }
    }

    fun moveRowGuide(oldRow: Int, newRow: Int) {
        val clamped = newRow.coerceIn(0, MATRIX_SIZE - 1)
        _studioState.update { state ->
            val next = state.rowGuides.map { if (it == oldRow) clamped else it }.distinct().sorted()
            state.copy(rowGuides = next, activeRowGuide = clamped)
        }
    }

    fun moveColGuide(oldCol: Int, newCol: Int) {
        val clamped = newCol.coerceIn(0, MATRIX_SIZE - 1)
        _studioState.update { state ->
            val next = state.colGuides.map { if (it == oldCol) clamped else it }.distinct().sorted()
            state.copy(colGuides = next, activeColGuide = clamped)
        }
    }

    fun selectActiveGuide(rowGuide: Int?, colGuide: Int?) {
        _studioState.update {
            it.copy(activeRowGuide = rowGuide, activeColGuide = colGuide)
        }
    }

    fun removeRowGuide(row: Int) {
        _studioState.update { state ->
            val next = state.rowGuides.filter { it != row }
            state.copy(
                rowGuides = next,
                activeRowGuide = if (state.activeRowGuide == row) next.firstOrNull() else state.activeRowGuide
            )
        }
    }

    fun removeColGuide(col: Int) {
        _studioState.update { state ->
            val next = state.colGuides.filter { it != col }
            state.copy(
                colGuides = next,
                activeColGuide = if (state.activeColGuide == col) next.firstOrNull() else state.activeColGuide
            )
        }
    }

    fun clearAllGuides() {
        _studioState.update {
            it.copy(
                rowGuides = emptyList(),
                colGuides = emptyList(),
                activeRowGuide = null,
                activeColGuide = null,
                axisLockEnabled = false,
                pocketFillMode = false
            )
        }
    }

    fun setAxisLockEnabled(enabled: Boolean) {
        _studioState.update {
            it.copy(
                axisLockEnabled = enabled,
                pocketFillMode = if (enabled) false else it.pocketFillMode
            )
        }
    }

    fun setPocketFillMode(enabled: Boolean) {
        _studioState.update {
            it.copy(
                pocketFillMode = enabled,
                axisLockEnabled = if (enabled) false else it.axisLockEnabled
            )
        }
    }

    /**
     * Pocket Fill (Region Bounding):
     * Intersecting row and column guides divide the circular 489-LED canvas into closed geometric pockets.
     * Floods only the physical LEDs bounded inside the pocket containing ([tapRow], [tapCol])
     * without bleeding outside the guideline borders or outside the 489-LED circular perimeter.
     */
    fun fillPocketAt(tapRow: Int, tapCol: Int) {
        if (tapRow !in 0 until MATRIX_SIZE || tapCol !in 0 until MATRIX_SIZE) return
        if (!GlyphAperture.isPhysicalLed(tapCol, tapRow)) return

        val current = _studioState.value
        val sortedRows = current.rowGuides.sorted()
        val sortedCols = current.colGuides.sorted()

        val lowerRowGuide = sortedRows.filter { it < tapRow }.maxOrNull()
        val upperRowGuide = sortedRows.filter { it > tapRow }.minOrNull()
        val minR = if (lowerRowGuide != null) lowerRowGuide + 1 else 0
        val maxR = if (upperRowGuide != null) upperRowGuide - 1 else (MATRIX_SIZE - 1)

        val lowerColGuide = sortedCols.filter { it < tapCol }.maxOrNull()
        val upperColGuide = sortedCols.filter { it > tapCol }.minOrNull()
        val minC = if (lowerColGuide != null) lowerColGuide + 1 else 0
        val maxC = if (upperColGuide != null) upperColGuide - 1 else (MATRIX_SIZE - 1)

        pushUndoSnapshot(current.frames)
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val dst = current.activeFrame.pixels.copyOf()
        val fillVal = current.brushBrightness.coerceIn(32, 255).toByte()
        var filledCount = 0

        for (r in minR..maxR) {
            for (c in minC..maxC) {
                if (GlyphAperture.isPhysicalLed(c, r)) {
                    dst[r * MATRIX_SIZE + c] = fillVal
                    filledCount++
                }
            }
        }

        val clamped = GlyphAperture.clampToPhysicalAperture(dst)
        val updatedFrame = current.activeFrame.copy(pixels = clamped)
        val updatedFrames = current.frames.toMutableList().apply { set(activeIdx, updatedFrame) }
        _studioState.update { it.copy(frames = updatedFrames) }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("POCKET FILL ($filledCount LEDs)")
        showToast("Pocket Filled $filledCount LEDs in Y[$minR..$maxR] X[$minC..$maxC]")
    }

    // --- Vector Line, Circle Outline & Flood Fill Tools (DRAW Shelf) ---

    fun drawLineOnCanvas(startRow: Int, startCol: Int, endRow: Int, endCol: Int) {
        val current = _studioState.value
        pushUndoSnapshot(current.frames)
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val dst = current.activeFrame.pixels.copyOf()
        val b = current.brushBrightness.coerceIn(32, 255).toByte()

        var x0 = startCol.coerceIn(0, MATRIX_SIZE - 1)
        var y0 = startRow.coerceIn(0, MATRIX_SIZE - 1)
        val x1 = endCol.coerceIn(0, MATRIX_SIZE - 1)
        val y1 = endRow.coerceIn(0, MATRIX_SIZE - 1)

        val dx = kotlin.math.abs(x1 - x0)
        val sx = if (x0 < x1) 1 else -1
        val dy = -kotlin.math.abs(y1 - y0)
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy

        while (true) {
            if (GlyphAperture.isPhysicalLed(x0, y0)) {
                dst[y0 * MATRIX_SIZE + x0] = b
            }
            if (x0 == x1 && y0 == y1) break
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy
                x0 += sx
            }
            if (e2 <= dx) {
                err += dx
                y0 += sy
            }
        }

        val clamped = GlyphAperture.clampToPhysicalAperture(dst)
        val updatedFrame = current.activeFrame.copy(pixels = clamped)
        val updatedFrames = current.frames.toMutableList().apply { set(activeIdx, updatedFrame) }
        _studioState.update { it.copy(frames = updatedFrames) }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("LINE ($startCol,$startRow)->($endCol,$endRow)")
    }

    fun drawCircleOutlineOnCanvas(centerRow: Int, centerCol: Int, radius: Int) {
        val current = _studioState.value
        val rClamped = radius.coerceIn(1, 12)
        pushUndoSnapshot(current.frames)
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val dst = current.activeFrame.pixels.copyOf()
        val b = current.brushBrightness.coerceIn(32, 255).toByte()

        for (deg in 0 until 360 step 6) {
            val rad = Math.toRadians(deg.toDouble())
            val rr = (centerRow + rClamped * kotlin.math.sin(rad)).roundToInt()
            val cc = (centerCol + rClamped * kotlin.math.cos(rad)).roundToInt()
            if (rr in 0 until MATRIX_SIZE && cc in 0 until MATRIX_SIZE && GlyphAperture.isPhysicalLed(cc, rr)) {
                dst[rr * MATRIX_SIZE + cc] = b
            }
        }

        val clamped = GlyphAperture.clampToPhysicalAperture(dst)
        val updatedFrame = current.activeFrame.copy(pixels = clamped)
        val updatedFrames = current.frames.toMutableList().apply { set(activeIdx, updatedFrame) }
        _studioState.update { it.copy(frames = updatedFrames) }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("CIRCLE R=$rClamped")
    }

    fun floodFillCanvas(startRow: Int, startCol: Int) {
        if (startRow !in 0 until MATRIX_SIZE || startCol !in 0 until MATRIX_SIZE) return
        if (!GlyphAperture.isPhysicalLed(startCol, startRow)) return

        val current = _studioState.value
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val dst = current.activeFrame.pixels.copyOf()
        val targetVal = dst[startRow * MATRIX_SIZE + startCol].toInt() and 0xFF
        val replacementVal = current.brushBrightness.coerceIn(32, 255)
        if (targetVal == replacementVal) return

        pushUndoSnapshot(current.frames)
        val visited = BooleanArray(MATRIX_CELL_COUNT)
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.addLast(startRow to startCol)
        visited[startRow * MATRIX_SIZE + startCol] = true

        while (queue.isNotEmpty()) {
            val (cr, cc) = queue.removeFirst()
            dst[cr * MATRIX_SIZE + cc] = replacementVal.toByte()
            val neighbors = listOf(cr - 1 to cc, cr + 1 to cc, cr to cc - 1, cr to cc + 1)
            for ((nr, nc) in neighbors) {
                if (nr in 0 until MATRIX_SIZE && nc in 0 until MATRIX_SIZE && GlyphAperture.isPhysicalLed(nc, nr)) {
                    val nIdx = nr * MATRIX_SIZE + nc
                    if (!visited[nIdx] && (dst[nIdx].toInt() and 0xFF) == targetVal) {
                        visited[nIdx] = true
                        queue.addLast(nr to nc)
                    }
                }
            }
        }

        val clamped = GlyphAperture.clampToPhysicalAperture(dst)
        val updatedFrame = current.activeFrame.copy(pixels = clamped)
        val updatedFrames = current.frames.toMutableList().apply { set(activeIdx, updatedFrame) }
        _studioState.update { it.copy(frames = updatedFrames) }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("FLOOD FILL ($startCol,$startRow)")
    }

    fun invert489Leds() {
        applyMatrixTransform(MatrixTransform.INVERT)
    }

    fun toggleShowFullPreviewComposite() {
        _studioState.update { state ->
            val nextShow = !state.showFullPreviewComposite
            val overlay = if (nextShow && state.visualLayers.isNotEmpty()) {
                GlyphScriptDecompiler.renderCompositeOverlay(
                    layers = state.visualLayers,
                    selectedLayerId = state.selectedLayerId,
                    variables = state.dryRunVariables
                )
            } else {
                null
            }
            state.copy(
                showFullPreviewComposite = nextShow,
                compositeOverlayPixels = overlay
            )
        }
        renderCurrentStudioCompositeToHardware("COMPOSITE PREVIEW")
    }

    fun selectVisualLayer(layerId: String) {
        stopStudioPreview()
        val current = _studioState.value
        val targetLayer = current.visualLayers.find { it.id == layerId } ?: return
        if (current.selectedLayerId == layerId) return

        // Save current frames into previously selected layer first
        val prevLayerId = current.selectedLayerId
        val syncedLayers = current.visualLayers.map { layer ->
            if (layer.id == prevLayerId) {
                layer.copy(frames = current.frames)
            } else {
                layer
            }
        }

        val targetFrames = if (targetLayer.frames.isNotEmpty()) {
            targetLayer.frames
        } else {
            GlyphScriptDecompiler.renderVisualLayerFrames(targetLayer, current.dryRunVariables)
        }

        val impliedVar = GlyphScriptDecompiler.extractImpliedVariableAssignment(targetLayer)
        val updatedStudioVars = if (impliedVar != null) {
            LinkedHashMap(current.dryRunVariables).apply { put(impliedVar.first, impliedVar.second) }
        } else {
            current.dryRunVariables
        }

        val overlay = if (current.showFullPreviewComposite) {
            GlyphScriptDecompiler.renderCompositeOverlay(
                layers = syncedLayers,
                selectedLayerId = layerId,
                variables = updatedStudioVars
            )
        } else {
            null
        }

        _studioState.update {
            it.copy(
                visualLayers = syncedLayers,
                selectedLayerId = layerId,
                frames = targetFrames,
                selectedFrameIndex = 0,
                globalAlign = targetLayer.align.ifBlank { it.globalAlign },
                dryRunVariables = updatedStudioVars,
                compositeOverlayPixels = overlay
            )
        }
        if (impliedVar != null) {
            _consoleState.update { state ->
                val nextOverrides = LinkedHashMap(state.dryRunVariableOverrides).apply {
                    put(impliedVar.first, impliedVar.second)
                }
                state.copy(dryRunVariableOverrides = nextOverrides)
            }
            evaluateConsoleDryRunInternal(pushToHardware = false, recordAudit = false)
        }
        renderCurrentStudioCompositeToHardware("LAYER // ${targetLayer.name}")
        showToast("Editing LAYER ${targetLayer.name}")
    }

    private fun syncActiveLayerToScriptBuffer(markCanvasEdited: Boolean) {
        val current = _studioState.value
        val existingLayers = if (current.visualLayers.isEmpty() && markCanvasEdited) {
            val defaultLayer = DecompiledVisualLayer(
                name = "custom_matrix",
                primitiveType = if (current.frames.size > 1) "SEQUENCE" else "BITMAP",
                originalPrimitiveType = "BITMAP",
                frames = current.frames,
                isCanvasEdited = true,
                align = current.globalAlign.ifBlank { "center" }
            )
            listOf(defaultLayer)
        } else {
            current.visualLayers
        }
        val selectedId = current.selectedLayerId ?: existingLayers.firstOrNull()?.id
        val updatedLayers = if (existingLayers.isNotEmpty() && selectedId != null) {
            existingLayers.map { layer ->
                if (layer.id == selectedId) {
                    val nowEdited = layer.isCanvasEdited || markCanvasEdited
                    val nextType = if (nowEdited) {
                        if (current.frames.size > 1) "SEQUENCE" else "BITMAP"
                    } else {
                        layer.primitiveType
                    }
                    val nextInterval = current.frames.firstOrNull()?.durationMs?.coerceIn(33L, 2000L)
                        ?: layer.intervalMs
                    layer.copy(
                        primitiveType = nextType,
                        frames = current.frames,
                        isCanvasEdited = nowEdited,
                        align = current.globalAlign.ifBlank { layer.align },
                        intervalMs = nextInterval
                    )
                } else {
                    layer
                }
            }
        } else {
            existingLayers
        }

        val activeEditedLayer = updatedLayers.firstOrNull { it.id == selectedId }
        val impliedVar = GlyphScriptDecompiler.extractImpliedVariableAssignment(activeEditedLayer)
        val updatedStudioVars = if (impliedVar != null) {
            LinkedHashMap(current.dryRunVariables).apply { put(impliedVar.first, impliedVar.second) }
        } else {
            current.dryRunVariables
        }

        val overlay = if (current.showFullPreviewComposite && updatedLayers.isNotEmpty()) {
            GlyphScriptDecompiler.renderCompositeOverlay(
                layers = updatedLayers,
                selectedLayerId = selectedId,
                variables = updatedStudioVars
            )
        } else {
            null
        }

        val baseRule = current.decompiledRule ?: DecompiledStudioRule(
            ruleName = current.name.ifBlank { "Custom Studio Rule" },
            frames = current.frames,
            visualLayers = updatedLayers,
            initialSelectedLayerId = selectedId,
            dryRunVariables = updatedStudioVars
        )
        val updatedDecompiled = baseRule.copy(
            ruleName = current.name.ifBlank { baseRule.ruleName },
            frames = current.frames,
            visualLayers = updatedLayers,
            globalAlign = current.globalAlign,
            dryRunVariables = updatedStudioVars
        )

        _studioState.update {
            it.copy(
                visualLayers = updatedLayers,
                selectedLayerId = selectedId,
                decompiledRule = updatedDecompiled,
                dryRunVariables = updatedStudioVars,
                compositeOverlayPixels = overlay
            )
        }

        val recompiledScript = GlyphScriptDecompiler.recompile(
            rule = updatedDecompiled,
            frames = current.frames,
            layers = updatedLayers,
            activeLayerId = selectedId
        )
        lastSyncedScriptFromStudio = recompiledScript
        _consoleState.update { state ->
            val nextOverrides = if (impliedVar != null) {
                LinkedHashMap(state.dryRunVariableOverrides).apply {
                    put(impliedVar.first, impliedVar.second)
                }
            } else {
                state.dryRunVariableOverrides
            }
            state.copy(
                scriptInput = recompiledScript,
                compileError = null,
                dryRunVariableOverrides = nextOverrides
            )
        }
        evaluateConsoleDryRunInternal(pushToHardware = false, recordAudit = false)
    }

    private fun renderCurrentStudioCompositeToHardware(label: String) {
        val state = _studioState.value
        val fg = state.activeFrame.pixels
        val bg = if (state.showFullPreviewComposite) state.compositeOverlayPixels else null
        val out = if (bg != null) {
            ByteArray(MATRIX_CELL_COUNT) { idx ->
                val fVal = fg[idx].toInt() and 0xFF
                val bVal = bg[idx].toInt() and 0xFF
                maxOf(fVal, bVal).toByte()
            }
        } else {
            fg
        }
        if (state.isLiveHardwareMirror) {
            hardwareController.invalidateDispatchedBuffer()
        }
        hardwareController.renderImmediateFrame(
            pixels = out,
            frameLabel = label,
            pushToHardware = state.isLiveHardwareMirror,
            activeFps = 30,
            isPersistentStream = state.isLiveHardwareMirror
        )
        if (state.isLiveHardwareMirror) {
            hardwareController.pushToPhysicalDriverOnly(out, label)
        }
    }

    fun setNudgeClampMode(mode: NudgeClampMode) {
        _studioState.update { it.copy(nudgeClampMode = mode) }
    }

    fun setActiveDockTab(tab: StudioDockTab) {
        val prev = _studioState.value.activeDockTab
        if (tab == StudioDockTab.SCALE && prev != StudioDockTab.SCALE) {
            initScaleSession()
        } else if (prev == StudioDockTab.SCALE && tab != StudioDockTab.SCALE) {
            cancelScale()
        }
        _studioState.update { it.copy(activeDockTab = tab) }
    }

    fun setCursorAutoStepEnabled(enabled: Boolean? = null) {
        _studioState.update { state ->
            val next = enabled ?: !state.cursorAutoStepEnabled
            state.copy(cursorAutoStepEnabled = next)
        }
    }

    fun setLightingTargetSelectionOnly(selectionOnly: Boolean) {
        _studioState.update { it.copy(lightingTargetSelectionOnly = selectionOnly) }
    }

    /**
     * Relative PWM Brightness / Lighting Tool:
     * Applies a proportional percentage delta to illuminated LEDs, preserving contrast ratios.
     * Clamped cleanly to [0, 255].
     * Scope: Target Selection Only vs. Entire Active Frame.
     */
    fun adjustRelativePwm(deltaPercent: Int, selectionOnly: Boolean? = null) {
        val current = _studioState.value
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val src = current.frames[activeIdx].pixels
        val dst = src.copyOf()

        val targetSelection = selectionOnly ?: current.lightingTargetSelectionOnly
        val bounds = if (targetSelection) current.selectionBounds else null

        pushUndoSnapshot(current.frames)
        val deltaPwm = (255f * (deltaPercent / 100f)).roundToInt()
        var modifiedCount = 0

        for (r in 0 until MATRIX_SIZE) {
            if (bounds != null && (r < bounds[0] || r > bounds[2])) continue
            for (c in 0 until MATRIX_SIZE) {
                if (bounds != null && (c < bounds[1] || c > bounds[3])) continue
                if (!GlyphAperture.isPhysicalLed(c, r)) continue

                val idx = r * MATRIX_SIZE + c
                val b = src[idx].toInt() and 0xFF
                if (b > 0) {
                    val newB = (b + deltaPwm).coerceIn(0, 255)
                    dst[idx] = newB.toByte()
                    modifiedCount++
                }
            }
        }

        val updatedFrame = current.frames[activeIdx].copy(pixels = dst)
        val updatedFrames = current.frames.toMutableList().apply { set(activeIdx, updatedFrame) }
        _studioState.update { it.copy(frames = updatedFrames) }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("PWM ${if (deltaPercent >= 0) "+$deltaPercent%" else "$deltaPercent%"}")
        showToast("Adjusted PWM by ${if (deltaPercent >= 0) "+$deltaPercent%" else "$deltaPercent%"} ($modifiedCount LEDs)")
    }

    /**
     * Non-Destructive Interactive Staging Buffer for PWM Lighting:
     * Computes the adjusted frame in real time on slider drag.
     * Renders directly to canvas (displayedPixels) and pushes immediately
     * to physical matrix driver if live mirror is enabled.
     */
    fun stageRelativePwmPreview(deltaPercent: Int, selectionOnly: Boolean? = null) {
        val current = _studioState.value
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val src = current.frames[activeIdx].pixels
        val dst = src.copyOf()

        val targetSelection = selectionOnly ?: current.lightingTargetSelectionOnly
        val bounds = if (targetSelection) current.selectionBounds else null
        val deltaPwm = (255f * (deltaPercent / 100f)).roundToInt()

        for (r in 0 until MATRIX_SIZE) {
            if (bounds != null && (r < bounds[0] || r > bounds[2])) continue
            for (c in 0 until MATRIX_SIZE) {
                if (bounds != null && (c < bounds[1] || c > bounds[3])) continue
                if (!GlyphAperture.isPhysicalLed(c, r)) continue

                val idx = r * MATRIX_SIZE + c
                val b = src[idx].toInt() and 0xFF
                if (b > 0) {
                    val newB = (b + deltaPwm).coerceIn(0, 255)
                    dst[idx] = newB.toByte()
                }
            }
        }

        _studioState.update { it.copy(stagingPreviewFrame = dst) }
        if (current.isLiveHardwareMirror) {
            hardwareController.pushToPhysicalDriverOnly(dst, "STAGE RELATIVE")
        }
    }

    fun stageAbsolutePwmPreview(targetBrightness: Int, selectionOnly: Boolean? = null) {
        val current = _studioState.value
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val src = current.frames[activeIdx].pixels
        val dst = src.copyOf()

        val targetSelection = selectionOnly ?: current.lightingTargetSelectionOnly
        val bounds = if (targetSelection) current.selectionBounds else null
        val clampedB = targetBrightness.coerceIn(0, 255).toByte()

        for (r in 0 until MATRIX_SIZE) {
            if (bounds != null && (r < bounds[0] || r > bounds[2])) continue
            for (c in 0 until MATRIX_SIZE) {
                if (bounds != null && (c < bounds[1] || c > bounds[3])) continue
                if (!GlyphAperture.isPhysicalLed(c, r)) continue

                val idx = r * MATRIX_SIZE + c
                val b = src[idx].toInt() and 0xFF
                if (b > 0 || (bounds != null && bounds[0] == bounds[2] && bounds[1] == bounds[3])) {
                    dst[idx] = clampedB
                }
            }
        }

        _studioState.update { it.copy(stagingPreviewFrame = dst) }
        if (current.isLiveHardwareMirror) {
            hardwareController.pushToPhysicalDriverOnly(dst, "STAGE ABSOLUTE")
        }
    }

    fun commitStagingPreviewFrame() {
        val current = _studioState.value
        val staged = current.stagingPreviewFrame ?: return
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        pushUndoSnapshot(current.frames)
        val updatedFrame = current.frames[activeIdx].copy(pixels = staged)
        val updatedFrames = current.frames.toMutableList().apply { set(activeIdx, updatedFrame) }
        _studioState.update { it.copy(frames = updatedFrames, stagingPreviewFrame = null) }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("PWM COMMITTED")
        showToast("Committed PWM lighting changes")
    }

    fun cancelStagingPreviewFrame() {
        val current = _studioState.value
        if (current.stagingPreviewFrame == null) return
        _studioState.update { it.copy(stagingPreviewFrame = null) }
        if (current.isLiveHardwareMirror) {
            renderCurrentStudioCompositeToHardware("PWM REVERTED")
        }
        showToast("Reverted PWM changes")
    }

    fun togglePwmHeatmapOverlay(enabled: Boolean? = null) {
        _studioState.update { state ->
            val next = enabled ?: !state.showPwmHeatmapOverlay
            state.copy(showPwmHeatmapOverlay = next)
        }
    }

    fun toggleLuminanceSampler(active: Boolean? = null) {
        _studioState.update { state ->
            val next = active ?: !state.isLuminanceSamplerActive
            state.copy(isLuminanceSamplerActive = next)
        }
    }

    fun sampleLuminanceAt(row: Int, col: Int): Int {
        val current = _studioState.value
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val src = current.stagingPreviewFrame ?: current.frames[activeIdx].pixels
        val rClamped = row.coerceIn(0, MATRIX_SIZE - 1)
        val cClamped = col.coerceIn(0, MATRIX_SIZE - 1)
        val b = if (GlyphAperture.isPhysicalLed(cClamped, rClamped)) {
            src[rClamped * MATRIX_SIZE + cClamped].toInt() and 0xFF
        } else 0
        val sampledVal = if (b > 0) b else 255
        _studioState.update { it.copy(brushBrightness = sampledVal, isLuminanceSamplerActive = false) }
        val pct = ((sampledVal / 255f) * 100).roundToInt()
        showToast("Sampled PWM: $sampledVal ($pct%) at (X:$cClamped, Y:$rClamped)")
        return sampledVal
    }

    fun applyRampGradient(type: RampGradientType, selectionOnly: Boolean? = null) {
        val current = _studioState.value
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val src = current.frames[activeIdx].pixels
        val dst = src.copyOf()

        val targetSelection = selectionOnly ?: current.lightingTargetSelectionOnly
        val bounds = if (targetSelection) current.selectionBounds else null

        var minR = MATRIX_SIZE
        var maxR = 0
        var minC = MATRIX_SIZE
        var maxC = 0
        var count = 0

        for (r in 0 until MATRIX_SIZE) {
            if (bounds != null && (r < bounds[0] || r > bounds[2])) continue
            for (c in 0 until MATRIX_SIZE) {
                if (bounds != null && (c < bounds[1] || c > bounds[3])) continue
                if (!GlyphAperture.isPhysicalLed(c, r)) continue
                val b = src[r * MATRIX_SIZE + c].toInt() and 0xFF
                if (b > 0) {
                    if (r < minR) minR = r
                    if (r > maxR) maxR = r
                    if (c < minC) minC = c
                    if (c > maxC) maxC = c
                    count++
                }
            }
        }

        if (count == 0) {
            showToast("No active dots in target to apply ramp")
            return
        }

        pushUndoSnapshot(current.frames)
        val spanC = (maxC - minC).coerceAtLeast(1).toFloat()
        val spanR = (maxR - minR).coerceAtLeast(1).toFloat()

        for (r in 0 until MATRIX_SIZE) {
            if (bounds != null && (r < bounds[0] || r > bounds[2])) continue
            for (c in 0 until MATRIX_SIZE) {
                if (bounds != null && (c < bounds[1] || c > bounds[3])) continue
                if (!GlyphAperture.isPhysicalLed(c, r)) continue

                val idx = r * MATRIX_SIZE + c
                val b = src[idx].toInt() and 0xFF
                if (b > 0) {
                    val factor = when (type) {
                        RampGradientType.FADE_LEFT_TO_RIGHT -> ((c - minC) / spanC).coerceIn(0.1f, 1f)
                        RampGradientType.FADE_TOP_TO_BOTTOM -> ((r - minR) / spanR).coerceIn(0.1f, 1f)
                        RampGradientType.RADIAL -> {
                            val d = hypot((c - 12).toFloat(), (r - 12).toFloat())
                            (1f - (d / 12f)).coerceIn(0.1f, 1f)
                        }
                    }
                    val newB = (255f * factor).roundToInt().coerceIn(15, 255)
                    dst[idx] = newB.toByte()
                }
            }
        }

        val updatedFrame = current.frames[activeIdx].copy(pixels = dst)
        val updatedFrames = current.frames.toMutableList().apply { set(activeIdx, updatedFrame) }
        _studioState.update { it.copy(frames = updatedFrames, stagingPreviewFrame = null) }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("RAMP: ${type.label}")
        showToast("Applied ${type.label} gradient ($count LEDs)")
    }

    fun initScaleSession() {
        val current = _studioState.value
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val srcPixels = current.frames[activeIdx].pixels.copyOf()

        val bounds = current.selectionBounds ?: run {
            var minR = MATRIX_SIZE
            var maxR = 0
            var minC = MATRIX_SIZE
            var maxC = 0
            var found = false
            for (r in 0 until MATRIX_SIZE) {
                for (c in 0 until MATRIX_SIZE) {
                    if (GlyphAperture.isPhysicalLed(c, r) && (srcPixels[r * MATRIX_SIZE + c].toInt() and 0xFF) > 0) {
                        if (r < minR) minR = r
                        if (r > maxR) maxR = r
                        if (c < minC) minC = c
                        if (c > maxC) maxC = c
                        found = true
                    }
                }
            }
            if (found) intArrayOf(minR, minC, maxR, maxC) else intArrayOf(6, 6, 18, 18)
        }

        val floatBounds = floatArrayOf(
            bounds[0].toFloat(),
            bounds[1].toFloat(),
            bounds[2].toFloat(),
            bounds[3].toFloat()
        )

        _studioState.update {
            it.copy(
                scaleFactorX = 1.0f,
                scaleFactorY = 1.0f,
                scaleSourceBounds = bounds,
                scaleActiveBounds = floatBounds,
                scaleBasePixels = srcPixels,
                activeDockTab = StudioDockTab.SCALE
            )
        }
        recomputeScalePreview(1.0f, 1.0f, floatBounds)
    }

    fun setScalePercent(percent: Int) {
        val f = (percent.coerceIn(1, 300) / 100f)
        val current = _studioState.value
        val srcBounds = current.scaleSourceBounds ?: return
        val currentAspectLocked = current.scaleAspectLocked

        val scaleX = f
        val scaleY = if (currentAspectLocked) f else current.scaleFactorY

        val srcW = (srcBounds[3] - srcBounds[1] + 1).toFloat()
        val srcH = (srcBounds[2] - srcBounds[0] + 1).toFloat()
        val cx = (srcBounds[1] + srcBounds[3]) / 2f
        val cy = (srcBounds[0] + srcBounds[2]) / 2f

        val destW = srcW * scaleX
        val destH = srcH * scaleY

        val floatBounds = floatArrayOf(
            cy - destH / 2f,
            cx - destW / 2f,
            cy + destH / 2f,
            cx + destW / 2f
        )

        _studioState.update {
            it.copy(
                scaleFactorX = scaleX,
                scaleFactorY = scaleY,
                scaleActiveBounds = floatBounds
            )
        }
        recomputeScalePreview(scaleX, scaleY, floatBounds)
    }

    fun stepScalePercent(deltaPercent: Int) {
        val current = _studioState.value
        val currentPct = ((current.scaleFactorX * 100f).roundToInt() + deltaPercent).coerceIn(1, 300)
        setScalePercent(currentPct)
    }

    fun toggleScaleAspectLock(locked: Boolean? = null) {
        _studioState.update { state ->
            val next = locked ?: !state.scaleAspectLocked
            state.copy(scaleAspectLocked = next)
        }
    }

    fun fitScaleToGuides() {
        val current = _studioState.value
        val srcBounds = current.scaleSourceBounds ?: return
        val rowG = current.rowGuides.sorted()
        val colG = current.colGuides.sorted()

        val targetMinR = if (rowG.size >= 2) rowG.first().toFloat() else 4f
        val targetMaxR = if (rowG.size >= 2) rowG.last().toFloat() else 20f
        val targetMinC = if (colG.size >= 2) colG.first().toFloat() else 4f
        val targetMaxC = if (colG.size >= 2) colG.last().toFloat() else 20f

        val targetW = (targetMaxC - targetMinC).coerceAtLeast(1f)
        val targetH = (targetMaxR - targetMinR).coerceAtLeast(1f)
        val srcW = (srcBounds[3] - srcBounds[1] + 1).toFloat().coerceAtLeast(1f)
        val srcH = (srcBounds[2] - srcBounds[0] + 1).toFloat().coerceAtLeast(1f)

        val scaleX: Float
        val scaleY: Float
        if (current.scaleAspectLocked) {
            val uniform = minOf(targetW / srcW, targetH / srcH)
            scaleX = uniform
            scaleY = uniform
        } else {
            scaleX = targetW / srcW
            scaleY = targetH / srcH
        }

        val cx = (targetMinC + targetMaxC) / 2f
        val cy = (targetMinR + targetMaxR) / 2f
        val destW = srcW * scaleX
        val destH = srcH * scaleY

        val floatBounds = floatArrayOf(
            cy - destH / 2f,
            cx - destW / 2f,
            cy + destH / 2f,
            cx + destW / 2f
        )

        _studioState.update {
            it.copy(
                scaleFactorX = scaleX,
                scaleFactorY = scaleY,
                scaleActiveBounds = floatBounds
            )
        }
        recomputeScalePreview(scaleX, scaleY, floatBounds)
        showToast("Fitted scale to active guides (${(scaleX * 100).roundToInt()}%)")
    }

    fun updateScaleBoundingBoxDrag(
        handleIndex: Int,
        newMinR: Float,
        newMinC: Float,
        newMaxR: Float,
        newMaxC: Float
    ) {
        val current = _studioState.value
        val srcBounds = current.scaleSourceBounds ?: return
        val srcW = (srcBounds[3] - srcBounds[1] + 1).toFloat().coerceAtLeast(1f)
        val srcH = (srcBounds[2] - srcBounds[0] + 1).toFloat().coerceAtLeast(1f)

        var destW = (newMaxC - newMinC).coerceAtLeast(0.5f)
        var destH = (newMaxR - newMinR).coerceAtLeast(0.5f)

        var scaleX = destW / srcW
        var scaleY = destH / srcH

        if (current.scaleAspectLocked && handleIndex in 0..3) {
            val uniform = (scaleX + scaleY) / 2f
            scaleX = uniform
            scaleY = uniform
        }

        val floatBounds = floatArrayOf(newMinR, newMinC, newMaxR, newMaxC)
        _studioState.update {
            it.copy(
                scaleFactorX = scaleX,
                scaleFactorY = scaleY,
                scaleActiveBounds = floatBounds
            )
        }
        recomputeScalePreview(scaleX, scaleY, floatBounds)
    }

    fun recomputeScalePreview(scaleX: Float, scaleY: Float, activeBounds: FloatArray) {
        val current = _studioState.value
        val basePixels = current.scaleBasePixels ?: return
        val srcBounds = current.scaleSourceBounds ?: return

        val srcMinR = srcBounds[0]
        val srcMinC = srcBounds[1]
        val srcMaxR = srcBounds[2]
        val srcMaxC = srcBounds[3]
        val srcW = srcMaxC - srcMinC + 1
        val srcH = srcMaxR - srcMinR + 1

        val destMinR = activeBounds[0].roundToInt()
        val destMinC = activeBounds[1].roundToInt()
        val destMaxR = activeBounds[2].roundToInt()
        val destMaxC = activeBounds[3].roundToInt()
        val destW = (destMaxC - destMinC + 1).coerceAtLeast(1)
        val destH = (destMaxR - destMinR + 1).coerceAtLeast(1)

        val out = ByteArray(MATRIX_CELL_COUNT)

        // If a selection region was scaled, keep dots outside selection untouched
        val isSelectionOnly = current.selectionBounds != null
        if (isSelectionOnly) {
            for (r in 0 until MATRIX_SIZE) {
                for (c in 0 until MATRIX_SIZE) {
                    if (r < srcMinR || r > srcMaxR || c < srcMinC || c > srcMaxC) {
                        val idx = r * MATRIX_SIZE + c
                        out[idx] = basePixels[idx]
                    }
                }
            }
        }

        // Resample scaled dots using exact mathematical formula:
        // x_src = clamp(floor(x_dest * W_src / W_dest), 0, W_src - 1)
        // y_src = clamp(floor(y_dest * H_src / H_dest), 0, H_src - 1)
        for (dy in 0 until destH) {
            val destR = destMinR + dy
            if (destR !in 0 until MATRIX_SIZE) continue
            val sy = ((dy.toFloat() * srcH) / destH.toFloat()).toInt().coerceIn(0, srcH - 1)
            val srcR = srcMinR + sy
            if (srcR !in 0 until MATRIX_SIZE) continue

            for (dx in 0 until destW) {
                val destC = destMinC + dx
                if (destC !in 0 until MATRIX_SIZE) continue
                if (!GlyphAperture.isPhysicalLed(destC, destR)) continue

                val sx = ((dx.toFloat() * srcW) / destW.toFloat()).toInt().coerceIn(0, srcW - 1)
                val srcC = srcMinC + sx
                if (srcC !in 0 until MATRIX_SIZE) continue

                val b = basePixels[srcR * MATRIX_SIZE + srcC].toInt() and 0xFF
                if (b > 0) {
                    out[destR * MATRIX_SIZE + destC] = b.toByte()
                }
            }
        }

        _studioState.update { it.copy(stagingPreviewFrame = out) }
        if (current.isLiveHardwareMirror) {
            hardwareController.pushToPhysicalDriverOnly(out, "SCALE PREVIEW")
        }
    }

    fun commitScale() {
        val current = _studioState.value
        val staged = current.stagingPreviewFrame ?: return
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        pushUndoSnapshot(current.frames)
        val updatedFrame = current.frames[activeIdx].copy(pixels = staged)
        val updatedFrames = current.frames.toMutableList().apply { set(activeIdx, updatedFrame) }
        _studioState.update {
            it.copy(
                frames = updatedFrames,
                stagingPreviewFrame = null,
                scaleSourceBounds = null,
                scaleActiveBounds = null,
                scaleBasePixels = null,
                scaleFactorX = 1f,
                scaleFactorY = 1f
            )
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("SCALE COMMITTED")
        showToast("Committed ${(current.scaleFactorX * 100).roundToInt()}% scale")
    }

    fun cancelScale() {
        val current = _studioState.value
        _studioState.update {
            it.copy(
                stagingPreviewFrame = null,
                scaleSourceBounds = null,
                scaleActiveBounds = null,
                scaleBasePixels = null,
                scaleFactorX = 1f,
                scaleFactorY = 1f
            )
        }
        if (current.isLiveHardwareMirror) {
            renderCurrentStudioCompositeToHardware("SCALE CANCELLED")
        }
        showToast("Cancelled scale")
    }

    /**
     * Quick-Clear Dustbin Reset:
     * Instantly zeroes out (PWM = 0) all physical LED coordinates on the current frame of the active layer
     * without resetting other frames, timeline metadata, or neighboring layers.
     * Registers an entry on the undo stack.
     */
    fun clearCurrentFrame() {
        val current = _studioState.value
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        pushUndoSnapshot(current.frames)
        val updatedFrame = current.frames[activeIdx].copy(pixels = ByteArray(MATRIX_CELL_COUNT))
        val updatedFrames = current.frames.toMutableList().apply { set(activeIdx, updatedFrame) }
        _studioState.update { it.copy(frames = updatedFrames) }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("FRAME WIPED")
        showToast("Cleared active frame")
    }

    fun setActiveDrawTool(tool: StudioDrawTool) {
        _studioState.update { it.copy(activeDrawTool = tool) }
    }

    fun setGlobalAlign(align: String) {
        _studioState.update { it.copy(globalAlign = align) }
        syncActiveLayerToScriptBuffer(markCanvasEdited = false)
    }

    /**
     * Nudge & Shift Controls with Advanced Bounds Auto-Clamping:
     * - SAFE_CLAMP: Stops movement if ANY active dot would bleed outside the 489 physical circular LEDs.
     * - APERTURE_CLIP: Allows continuous movement, cleanly clipping dots that exceed the physical circle.
     */
    fun nudgeStudioCanvas(dx: Int, dy: Int) {
        val current = _studioState.value
        val activeLayer = current.selectedVisualLayer
        if (activeLayer != null && activeLayer.primitiveType == "TEXT") {
            val newY = (activeLayer.yOffset + dy).coerceIn(0, 20)
            updateVisualLayer(activeLayer.id) {
                it.copy(yOffset = newY, isCanvasEdited = false)
            }
            return
        }
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val frame = current.frames[activeIdx]
        val src = frame.pixels
        val dst = ByteArray(MATRIX_CELL_COUNT)

        // Safe Clamp check: ensure no active LED exits the circular aperture
        if (current.nudgeClampMode == NudgeClampMode.SAFE_CLAMP) {
            for (r in 0 until MATRIX_SIZE) {
                for (c in 0 until MATRIX_SIZE) {
                    val idx = r * MATRIX_SIZE + c
                    if ((src[idx].toInt() and 0xFF) > 0) {
                        val newC = c + dx
                        val newR = r + dy
                        if (newC !in 0 until MATRIX_SIZE || newR !in 0 until MATRIX_SIZE || !GlyphAperture.isPhysicalLed(newC, newR)) {
                            showToast("Edge reached (Safe Clamp)")
                            return
                        }
                    }
                }
            }
        }

        pushUndoSnapshot(current.frames)
        for (r in 0 until MATRIX_SIZE) {
            for (c in 0 until MATRIX_SIZE) {
                val idx = r * MATRIX_SIZE + c
                val b = src[idx].toInt() and 0xFF
                if (b > 0) {
                    val newC = c + dx
                    val newR = r + dy
                    if (newC in 0 until MATRIX_SIZE && newR in 0 until MATRIX_SIZE && GlyphAperture.isPhysicalLed(newC, newR)) {
                        dst[newR * MATRIX_SIZE + newC] = b.toByte()
                    }
                }
            }
        }

        val clamped = GlyphAperture.clampToPhysicalAperture(dst)
        val updatedFrame = frame.copy(pixels = clamped)
        val updatedFrames = current.frames.toMutableList().apply { set(activeIdx, updatedFrame) }
        _studioState.update { it.copy(frames = updatedFrames) }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("NUDGE [$dx, $dy]")
    }

    fun applyMatrixTransform(transform: MatrixTransform) {
        when (transform) {
            MatrixTransform.SHIFT_UP -> {
                nudgeStudioCanvas(0, -1)
                return
            }
            MatrixTransform.SHIFT_DOWN -> {
                nudgeStudioCanvas(0, 1)
                return
            }
            MatrixTransform.SHIFT_LEFT -> {
                nudgeStudioCanvas(-1, 0)
                return
            }
            MatrixTransform.SHIFT_RIGHT -> {
                nudgeStudioCanvas(1, 0)
                return
            }
            else -> Unit
        }

        val current = _studioState.value
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val src = current.frames[activeIdx].pixels
        pushUndoSnapshot(current.frames)

        val rawDst = ByteArray(MATRIX_CELL_COUNT)
        when (transform) {
            MatrixTransform.CLEAR -> Unit
            MatrixTransform.INVERT -> {
                for (r in 0 until MATRIX_SIZE) {
                    for (c in 0 until MATRIX_SIZE) {
                        if (GlyphHardwareController.isCellInsideCircle(r, c)) {
                            val idx = r * MATRIX_SIZE + c
                            val v = src[idx].toInt() and 0xFF
                            rawDst[idx] = (255 - v).toByte()
                        }
                    }
                }
            }
            MatrixTransform.MIRROR_HORIZONTAL -> {
                for (r in 0 until MATRIX_SIZE) {
                    for (c in 0 until MATRIX_SIZE) {
                        rawDst[r * MATRIX_SIZE + c] = src[r * MATRIX_SIZE + (MATRIX_SIZE - 1 - c)]
                    }
                }
            }
            MatrixTransform.MIRROR_VERTICAL -> {
                for (r in 0 until MATRIX_SIZE) {
                    for (c in 0 until MATRIX_SIZE) {
                        rawDst[r * MATRIX_SIZE + c] = src[(MATRIX_SIZE - 1 - r) * MATRIX_SIZE + c]
                    }
                }
            }
            MatrixTransform.ROTATE_90 -> {
                for (r in 0 until MATRIX_SIZE) {
                    for (c in 0 until MATRIX_SIZE) {
                        rawDst[r * MATRIX_SIZE + c] = src[(MATRIX_SIZE - 1 - c) * MATRIX_SIZE + r]
                    }
                }
            }
            else -> Unit
        }

        val dst = GlyphHardwareController.applyCircularMask(rawDst)
        val updatedFrame = current.frames[activeIdx].copy(pixels = dst)
        val updatedFrames = current.frames.toMutableList().apply { set(activeIdx, updatedFrame) }
        _studioState.update { it.copy(frames = updatedFrames) }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware(transform.name)
    }

    /**
     * Launches Matrix Studio directly from the Console Dry Run Preview, passing current mock
     * variables (`$maneuver = "right"`, `$dist = "200m"`, etc.) and focusing the active conditional layer.
     */
    fun openStudioFromConsoleDryRun() {
        val context = getApplication<Application>()
        if (_currentTab.value != GlyphScreenTab.STUDIO) {
            previousTabBeforeStudio = _currentTab.value
            studioEntrySnapshot = _studioState.value.copy(
                frames = _studioState.value.frames.map { it.copy(pixels = it.pixels.copyOf()) }
            )
            consoleScriptSnapshotBeforeStudio = _consoleState.value.scriptInput
        }
        evaluateConsoleDryRunInternal(pushToHardware = false, recordAudit = false)
        val console = _consoleState.value
        val script = console.scriptInput.trim()
        if (script.isBlank()) {
            createNewBlankStudioAnimation()
            _currentTab.value = GlyphScreenTab.STUDIO
            return
        }

        val compileRes = GlyphScriptCompiler.compile(
            scriptText = script,
            context = context,
            dryRunTitle = console.dryRunTitle,
            dryRunBody = console.dryRunBody,
            dryRunProgress = console.dryRunProgress
        )
        val effectiveVars = LinkedHashMap<String, String>().apply {
            if (compileRes is GlyphScriptCompileResult.Success) {
                putAll(compileRes.extractedPreviewVariables)
            } else {
                putAll(console.extractedVariablesPreview)
            }
            putAll(console.dryRunVariableOverrides)
        }
        val ruleName = (compileRes as? GlyphScriptCompileResult.Success)?.program?.ruleName
            ?: console.lastCompiledRuleName
            ?: ""
        val existingRule = rules.value.firstOrNull {
            ruleName.isNotBlank() && it.name.equals(ruleName, ignoreCase = true)
        } ?: _activeRule.value
        val existingRuleId = existingRule?.id ?: 0L
        if (existingRule != null) {
            _activeRule.value = existingRule
        }

        val decompiled = GlyphScriptDecompiler.decompile(
            scriptSource = script,
            context = context,
            ruleId = existingRuleId,
            dryRunVariables = effectiveVars
        )

        val selectedId = decompiled.initialSelectedLayerId ?: decompiled.visualLayers.firstOrNull()?.id
        val overlay = GlyphScriptDecompiler.renderCompositeOverlay(
            layers = decompiled.visualLayers,
            selectedLayerId = selectedId,
            variables = decompiled.dryRunVariables
        )
        val studioRuleIdStr = if (existingRuleId != 0L) existingRuleId.toString() else decompiled.ruleName
        _activeStudioRuleId.value = studioRuleIdStr
        lastSyncedScriptFromStudio = script

        _studioState.update {
            it.copy(
                name = decompiled.ruleName,
                frames = decompiled.frames,
                selectedFrameIndex = 0,
                visualLayers = decompiled.visualLayers,
                selectedLayerId = selectedId,
                decompiledRule = decompiled,
                dryRunVariables = decompiled.dryRunVariables,
                showFullPreviewComposite = true,
                compositeOverlayPixels = overlay,
                globalAlign = decompiled.globalAlign,
                activeDockTab = StudioDockTab.DRAW,
                selectionRotationAngle = 0f,
                activeStudioRuleId = studioRuleIdStr
            )
        }

        val selectedLayerName = decompiled.visualLayers.find { it.id == selectedId }?.name ?: "matrix"
        renderCurrentStudioCompositeToHardware("STUDIO // $selectedLayerName")
        _isRuleCreatorOpen.value = false
        _currentTab.value = GlyphScreenTab.STUDIO
        showToast("Editing LAYER $selectedLayerName in Studio")
    }

    /**
     * Code-to-Visual Decompiler ("Edit Existing Rule in Builder"):
     * Decomposes any runtime rule's GlyphScript AST into visual dot matrix frames, timeline sequences,
     * text/ring layers, and structural properties for live interactive editing.
     */
    fun editRuleInStudio(rule: TriggerRule) {
        stopStudioPreview()
        stopStudioLiveKeepAlive()
        undoStack.clear()
        redoStack.clear()
        rotationBasePixels = null
        val context = getApplication<Application>()
        _activeRule.value = rule
        _activeStudioRuleId.value = rule.id.toString()
        if (_currentTab.value != GlyphScreenTab.STUDIO) {
            previousTabBeforeStudio = _currentTab.value
            studioEntrySnapshot = _studioState.value.copy(
                frames = _studioState.value.frames.map { it.copy(pixels = it.pixels.copyOf()) }
            )
            consoleScriptSnapshotBeforeStudio = _consoleState.value.scriptInput
        }
        val scriptSource = rule.glyphScriptSource.ifBlank {
            GlyphScriptCompiler.exportRuleToGlyphScript(rule)
        }
        val console = _consoleState.value
        val compileWithDryRun = GlyphScriptCompiler.compile(
            scriptText = scriptSource,
            context = context,
            dryRunTitle = console.dryRunTitle,
            dryRunBody = console.dryRunBody,
            dryRunProgress = console.dryRunProgress
        )
        val effectiveVars = LinkedHashMap<String, String>().apply {
            if (compileWithDryRun is GlyphScriptCompileResult.Success) {
                putAll(compileWithDryRun.extractedPreviewVariables)
            } else {
                putAll(console.extractedVariablesPreview)
            }
            putAll(console.dryRunVariableOverrides)
        }

        val decompiled = if (scriptSource.isNotBlank()) {
            GlyphScriptDecompiler.decompile(
                scriptSource = scriptSource,
                context = context,
                ruleId = rule.id,
                dryRunVariables = effectiveVars
            )
        } else {
            val anim = animations.value.firstOrNull { it.id == rule.targetAnimationId }
            val frames = anim?.frames?.ifEmpty { null } ?: listOf(GlyphFrame(id = "f_0", durationMs = 120L))
            val defaultLayer = DecompiledVisualLayer(
                name = "custom_matrix",
                primitiveType = if (frames.size > 1) "SEQUENCE" else "BITMAP",
                frames = frames
            )
            DecompiledStudioRule(
                ruleName = rule.name,
                targetPackages = rule.targetPackages,
                whenTriggerEvent = if (rule.conditionType == ConditionType.NOTIFICATION) "notification" else rule.conditionType.name.lowercase(),
                frames = frames,
                visualLayers = listOf(defaultLayer),
                initialSelectedLayerId = defaultLayer.id,
                originalRuleId = rule.id
            )
        }

        val selectedId = decompiled.initialSelectedLayerId ?: decompiled.visualLayers.firstOrNull()?.id
        val overlay = GlyphScriptDecompiler.renderCompositeOverlay(
            layers = decompiled.visualLayers,
            selectedLayerId = selectedId,
            variables = decompiled.dryRunVariables
        )
        lastSyncedScriptFromStudio = scriptSource

        _studioState.update {
            it.copy(
                name = decompiled.ruleName,
                frames = decompiled.frames,
                selectedFrameIndex = 0,
                visualLayers = decompiled.visualLayers,
                selectedLayerId = selectedId,
                decompiledRule = decompiled,
                dryRunVariables = decompiled.dryRunVariables,
                showFullPreviewComposite = true,
                compositeOverlayPixels = overlay,
                globalAlign = decompiled.globalAlign,
                activeDockTab = StudioDockTab.DRAW,
                selectionRotationAngle = 0f,
                selectionStart = null,
                selectionEnd = null,
                clipboardPixels = null,
                stagingPreviewFrame = null,
                canUndo = false,
                canRedo = false,
                scaleSourceBounds = null,
                scaleActiveBounds = null,
                scaleBasePixels = null,
                scaleFactorX = 1.0f,
                scaleFactorY = 1.0f,
                activeStudioRuleId = rule.id.toString()
            )
        }

        _consoleState.update {
            it.copy(
                scriptInput = scriptSource,
                compileError = null
            )
        }
        evaluateConsoleDryRunInternal(pushToHardware = false, recordAudit = false)

        val selectedLayerName = decompiled.visualLayers.find { it.id == selectedId }?.name ?: decompiled.ruleName
        renderCurrentStudioCompositeToHardware("DECOMPILED // $selectedLayerName")

        _isRuleCreatorOpen.value = false
        _currentTab.value = GlyphScreenTab.STUDIO
        showToast("Loaded \"${decompiled.ruleName}\" (Layer: $selectedLayerName)")
    }

    /**
     * Recompiles the current Console script buffer and refreshes the Dry Run preview
     * (and optionally physical LEDs).
     */
    fun recompileAndRunDryRun(pushToHardware: Boolean = false) {
        hardwareController.invalidateDispatchedBuffer()
        evaluateConsoleDryRunInternal(pushToHardware = pushToHardware, recordAudit = true)
    }

    /**
     * Atomic [SAVE & APPLY] Pipeline:
     * 1. Identifies the targeted layer currently being edited (`editingLayerName`).
     * 2. Converts active timeline frames (`canvasFrames`) into an explicit AST primitive
     *    (`LayerPrimitive.SequencePrimitive` with `frame """..."""` or `LayerPrimitive.BitmapPrimitive`).
     * 3. Replaces the target layer's primitive in the parent `GlyphScriptRule` AST.
     * 4. Uses `GlyphScriptDecompiler.decompile(updatedAst)` to generate the updated GlyphScript v2 code string
     *    and writes it back to `consoleState.scriptInput`.
     * 5. Persists the updated rule to Room SQLite via `repository.updateRule(updatedRuleEntity)` on `Dispatchers.IO`.
     * 6. Invalidates hardware cache (`hardwareController.invalidateDispatchedBuffer()`) and calls
     *    `recompileAndRunDryRun()` so updated design streams immediately to Matrix and Dry Run.
     */
    fun onSaveStudioChanges() {
        stopStudioPreview()
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)

        val current = _studioState.value
        val canvasFrames = current.frames.map { frame ->
            frame.copy(pixels = GlyphHardwareController.applyCircularMask(frame.pixels))
        }.ifEmpty {
            listOf(GlyphFrame(durationMs = 110L))
        }

        val editingLayer = current.selectedVisualLayer
        val editingLayerName = current.editingLayerName
        val layerPrimitive = GlyphScriptDecompiler.framesToAstPrimitive(
            frames = canvasFrames,
            align = editingLayer?.align ?: current.globalAlign,
            brightness = editingLayer?.brightness ?: 255,
            loop = editingLayer?.loop ?: true,
            pauseMs = editingLayer?.pauseMs ?: 0L,
            holdLast = editingLayer?.holdLast ?: false
        )

        val baseDecompiled = current.decompiledRule ?: DecompiledStudioRule(
            ruleName = current.name.ifBlank { "Custom Studio Rule" },
            frames = canvasFrames,
            visualLayers = current.visualLayers,
            initialSelectedLayerId = current.selectedLayerId,
            dryRunVariables = current.dryRunVariables
        )

        val matchedRuleId = when {
            baseDecompiled.originalRuleId != 0L -> baseDecompiled.originalRuleId
            _activeStudioRuleId.value?.toLongOrNull() != null && _activeStudioRuleId.value?.toLongOrNull() != 0L ->
                _activeStudioRuleId.value!!.toLong()
            _activeRule.value != null && _activeRule.value!!.id != 0L -> _activeRule.value!!.id
            else -> 0L
        }

        val updatedDecompiled = baseDecompiled.copy(
            ruleName = current.name.ifBlank { baseDecompiled.ruleName.ifBlank { "Custom Studio Rule" } },
            frames = canvasFrames,
            visualLayers = current.visualLayers,
            globalAlign = current.globalAlign,
            originalRuleId = matchedRuleId
        )

        val mutatedAst = GlyphScriptDecompiler.mutateRuleAstWithLayerPrimitive(
            baseRule = updatedDecompiled,
            targetLayerName = editingLayerName,
            updatedPrimitive = layerPrimitive,
            updatedLayers = current.visualLayers
        )
        val decompiledScript = GlyphScriptDecompiler.decompile(
            updatedRule = mutatedAst,
            studioRule = updatedDecompiled,
            frames = canvasFrames,
            layers = current.visualLayers,
            activeLayerId = current.selectedLayerId
        )

        val impliedVar = GlyphScriptDecompiler.extractImpliedVariableAssignment(editingLayer)
        lastSyncedScriptFromStudio = decompiledScript

        _consoleState.update { state ->
            val nextOverrides = if (impliedVar != null) {
                LinkedHashMap(state.dryRunVariableOverrides).apply {
                    put(impliedVar.first, impliedVar.second)
                }
            } else {
                state.dryRunVariableOverrides
            }
            state.copy(
                scriptInput = decompiledScript,
                compileError = null,
                dryRunVariableOverrides = nextOverrides
            )
        }

        // Flush hardware frame cache and immediately recompile & run Dry Run
        hardwareController.invalidateDispatchedBuffer()
        recompileAndRunDryRun(pushToHardware = false)

        val consoleAfterSync = _consoleState.value
        val compileResult = GlyphScriptCompiler.compile(
            scriptText = decompiledScript,
            context = getApplication<Application>(),
            dryRunTitle = consoleAfterSync.dryRunTitle,
            dryRunBody = consoleAfterSync.dryRunBody,
            dryRunProgress = consoleAfterSync.dryRunProgress
        )

        // Update studioEntrySnapshot & consoleScriptSnapshotBeforeStudio so tab switches preserve saved edits
        studioEntrySnapshot = _studioState.value.copy(
            frames = canvasFrames.map { it.copy(pixels = it.pixels.copyOf()) }
        )
        consoleScriptSnapshotBeforeStudio = decompiledScript

        viewModelScope.launch(Dispatchers.IO) {
            val cleanName = updatedDecompiled.ruleName.trim().ifBlank { "Custom Studio Rule" }
            val cleanTags = current.tagsInput.split(",")
                .map { it.trim().uppercase() }
                .filter { it.isNotBlank() }
                .ifEmpty { listOf("CUSTOM") }

            val firstFramePixels = canvasFrames.firstOrNull()?.pixels ?: ByteArray(MATRIX_CELL_COUNT)
            val thumbnail = GlyphHardwareController.applyCircularMask(firstFramePixels)
            val now = System.currentTimeMillis()

            val animToSave = GlyphAnimation(
                id = current.editingAnimationId,
                name = cleanName,
                tags = cleanTags,
                frames = canvasFrames,
                defaultFrameIntervalMs = canvasFrames.firstOrNull()?.durationMs ?: 110L,
                loopCount = current.loopCount,
                transitionCurve = current.transitionCurve,
                isPreset = false,
                updatedAt = now
            )
            val savedAnimId = repository.saveAnimation(animToSave)

            if (compileResult is GlyphScriptCompileResult.Success) {
                val existingEntity = rules.value.firstOrNull { it.id == matchedRuleId } ?: _activeRule.value
                val ruleEntity = compileResult.compiledRule.copy(
                    id = matchedRuleId,
                    name = cleanName,
                    isEnabled = existingEntity?.isEnabled ?: true,
                    targetAnimationId = savedAnimId,
                    targetAnimationName = cleanName,
                    glyphScriptSource = decompiledScript,
                    thumbnailBytes = thumbnail,
                    updatedAt = now
                )
                val persistedId = repository.updateRule(ruleEntity)
                repository.updateRuleThumbnail(persistedId, thumbnail, now)
                val savedRule = ruleEntity.copy(id = persistedId)

                withContext(Dispatchers.Main) {
                    _activeRule.value = savedRule
                    _activeStudioRuleId.value = persistedId.toString()
                    _studioState.update { state ->
                        state.copy(
                            editingAnimationId = savedAnimId,
                            name = cleanName,
                            frames = canvasFrames,
                            activeStudioRuleId = persistedId.toString(),
                            decompiledRule = updatedDecompiled.copy(
                                originalRuleId = persistedId,
                                originalProgram = compileResult.program
                            )
                        )
                    }
                    hardwareController.invalidateDispatchedBuffer()
                    recompileAndRunDryRun(pushToHardware = false)
                    val latestConsole = _consoleState.value
                    ruleEngine.forceExecuteRule(
                        rule = savedRule,
                        titleOverride = latestConsole.dryRunTitle,
                        bodyOverride = latestConsole.dryRunBody,
                        variableOverrides = latestConsole.dryRunVariableOverrides
                    )
                    renderCurrentStudioCompositeToHardware("SAVED // ${savedRule.name}")
                    showToast("Saved & Applied Rule \"${savedRule.name}\"")
                }
            } else {
                if (matchedRuleId != 0L) {
                    val existingEntity = rules.value.firstOrNull { it.id == matchedRuleId }
                    if (existingEntity != null) {
                        val updated = existingEntity.copy(
                            targetAnimationId = savedAnimId,
                            thumbnailBytes = thumbnail,
                            updatedAt = now
                        )
                        repository.updateRule(updated)
                        repository.updateRuleThumbnail(matchedRuleId, thumbnail, now)
                    }
                }
                withContext(Dispatchers.Main) {
                    _studioState.update {
                        it.copy(
                            editingAnimationId = savedAnimId,
                            name = cleanName,
                            frames = canvasFrames
                        )
                    }
                    renderCurrentStudioCompositeToHardware("SAVED // $cleanName")
                    showToast("Saved \"$cleanName\"")
                }
            }
        }
    }

    fun saveAndApplyDecompiledRule() {
        onSaveStudioChanges()
    }

    fun toggleLayerBitmapMode(layerId: String) {
        val current = _studioState.value
        val vars = current.dryRunVariables
        var updatedLayerName = ""
        var convertedToDots = true
        _studioState.update { state ->
            val updatedLayers = state.visualLayers.map { layer ->
                if (layer.id == layerId) {
                    val toggled = GlyphScriptDecompiler.toggleLayerBitmapMode(layer, vars)
                    updatedLayerName = toggled.name
                    convertedToDots = toggled.isCanvasEdited || toggled.primitiveType in setOf("BITMAP", "SEQUENCE")
                    toggled
                } else {
                    layer
                }
            }
            val nextFrames = if (state.selectedLayerId == layerId) {
                updatedLayers.find { it.id == layerId }?.frames ?: state.frames
            } else {
                state.frames
            }
            state.copy(
                visualLayers = updatedLayers,
                frames = nextFrames,
                selectedFrameIndex = 0
            )
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = convertedToDots)
        renderCurrentStudioCompositeToHardware("LAYER MODE // $updatedLayerName")
        val modeLabel = if (convertedToDots) "Editable Pixel Dots" else "Preset Vector"
        showToast("Layer $updatedLayerName: $modeLabel")
    }

    fun toggleVisualLayerVisibility(layerId: String) {
        _studioState.update { state ->
            val updated = state.visualLayers.map {
                if (it.id == layerId) it.copy(isVisible = !it.isVisible) else it
            }
            state.copy(visualLayers = updated)
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = false)
        renderCurrentStudioCompositeToHardware("LAYER VISIBILITY")
    }

    fun addVisualLayer(layer: DecompiledVisualLayer) {
        val current = _studioState.value
        val renderedFrames = GlyphScriptDecompiler.renderVisualLayerFrames(layer, current.dryRunVariables)
        val enrichedLayer = layer.copy(frames = renderedFrames)
        _studioState.update { state ->
            state.copy(visualLayers = state.visualLayers + enrichedLayer)
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = false)
        renderCurrentStudioCompositeToHardware("ADD LAYER ${layer.name}")
    }

    fun removeVisualLayer(layerId: String) {
        _studioState.update { state ->
            val remaining = state.visualLayers.filter { it.id != layerId }
            val nextSelected = if (state.selectedLayerId == layerId) {
                remaining.firstOrNull()?.id
            } else {
                state.selectedLayerId
            }
            val nextFrames = if (state.selectedLayerId == layerId) {
                remaining.firstOrNull()?.frames ?: state.frames
            } else {
                state.frames
            }
            state.copy(
                visualLayers = remaining,
                selectedLayerId = nextSelected,
                frames = nextFrames
            )
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = false)
        renderCurrentStudioCompositeToHardware("REMOVE LAYER")
    }

    fun moveVisualLayer(layerId: String, moveUp: Boolean) {
        _studioState.update { state ->
            val list = state.visualLayers.toMutableList()
            val idx = list.indexOfFirst { it.id == layerId }
            if (idx != -1) {
                val targetIdx = if (moveUp) idx - 1 else idx + 1
                if (targetIdx in 0..list.lastIndex) {
                    val item = list.removeAt(idx)
                    list.add(targetIdx, item)
                }
            }
            state.copy(visualLayers = list)
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = false)
        renderCurrentStudioCompositeToHardware("REORDER LAYER")
    }

    /**
     * Reorders a layer from [fromIndex] to [toIndex] via Drag-and-Drop in the Layers shelf,
     * immediately updating the compositing Z-index order in [GlyphFrameCompositor] and the script buffer.
     */
    fun reorderVisualLayer(fromIndex: Int, toIndex: Int) {
        val currentLayers = _studioState.value.visualLayers
        if (currentLayers.size <= 1) return
        val safeFrom = fromIndex.coerceIn(0, currentLayers.lastIndex)
        val safeTo = toIndex.coerceIn(0, currentLayers.lastIndex)
        if (safeFrom == safeTo) return

        _studioState.update { state ->
            val list = state.visualLayers.toMutableList()
            val moved = list.removeAt(safeFrom)
            list.add(safeTo, moved)
            state.copy(visualLayers = list)
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = false)
        renderCurrentStudioCompositeToHardware("Z-INDEX REORDER")
    }

    fun toggleLayerMultiSelectMode(enabled: Boolean? = null) {
        _studioState.update { state ->
            val nextMode = enabled ?: !state.isLayerMultiSelectMode
            val defaultSelection = if (nextMode && state.selectedLayerIdsForBatch.isEmpty()) {
                setOfNotNull(state.selectedLayerId ?: state.visualLayers.firstOrNull()?.id)
            } else if (!nextMode) {
                emptySet()
            } else {
                state.selectedLayerIdsForBatch
            }
            state.copy(
                isLayerMultiSelectMode = nextMode,
                selectedLayerIdsForBatch = defaultSelection
            )
        }
    }

    fun toggleLayerCheckedForBatch(layerId: String) {
        _studioState.update { state ->
            val nextSet = state.selectedLayerIdsForBatch.toMutableSet()
            if (nextSet.contains(layerId)) {
                nextSet.remove(layerId)
            } else {
                nextSet.add(layerId)
            }
            state.copy(
                isLayerMultiSelectMode = true,
                selectedLayerIdsForBatch = nextSet
            )
        }
    }

    fun clearLayerBatchSelection() {
        _studioState.update { it.copy(selectedLayerIdsForBatch = emptySet(), isLayerMultiSelectMode = false) }
    }

    /**
     * Batch Action 1: `[Duplicate Selected]`
     * Duplicates all checked layers in-place with appended suffixes (`_copy`).
     */
    fun duplicateSelectedLayers() {
        val current = _studioState.value
        val targetIds = current.selectedLayerIdsForBatch.ifEmpty {
            setOfNotNull(current.selectedLayerId)
        }
        if (targetIds.isEmpty()) return

        var duplicatedCount = 0
        _studioState.update { state ->
            val syncedLayers = state.visualLayers.map { layer ->
                if (layer.id == state.selectedLayerId) layer.copy(frames = state.frames) else layer
            }
            val nextLayers = mutableListOf<DecompiledVisualLayer>()
            for (layer in syncedLayers) {
                nextLayers.add(layer)
                if (layer.id in targetIds) {
                    duplicatedCount++
                    val copyFrames = layer.frames.map { f ->
                        GlyphFrame(
                            id = UUID.randomUUID().toString(),
                            durationMs = f.durationMs,
                            pixels = f.pixels.copyOf()
                        )
                    }
                    nextLayers.add(
                        layer.copy(
                            id = UUID.randomUUID().toString(),
                            name = "${layer.name}_copy",
                            frames = copyFrames
                        )
                    )
                }
            }
            state.copy(visualLayers = nextLayers)
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = false)
        renderCurrentStudioCompositeToHardware("DUPLICATE LAYERS ($duplicatedCount)")
        showToast("Duplicated $duplicatedCount layer${if (duplicatedCount == 1) "" else "s"} (_copy)")
    }

    /**
     * Batch Action 2: `[Delete Selected]`
     * Batch-deletes all checked layers while keeping at least one base canvas layer if all were selected.
     */
    fun deleteSelectedLayers() {
        val current = _studioState.value
        val targetIds = current.selectedLayerIdsForBatch.ifEmpty {
            setOfNotNull(current.selectedLayerId)
        }
        if (targetIds.isEmpty()) return

        var deletedCount = 0
        _studioState.update { state ->
            val remaining = state.visualLayers.filter { layer ->
                val remove = layer.id in targetIds
                if (remove) deletedCount++
                !remove
            }.ifEmpty {
                listOf(
                    DecompiledVisualLayer(
                        name = "base_matrix",
                        primitiveType = "BITMAP",
                        originalPrimitiveType = "BITMAP",
                        frames = listOf(GlyphFrame(id = UUID.randomUUID().toString(), durationMs = 110L)),
                        isCanvasEdited = true
                    )
                )
            }
            val nextSelectedLayer = remaining.find { it.id == state.selectedLayerId } ?: remaining.first()
            state.copy(
                visualLayers = remaining,
                selectedLayerId = nextSelectedLayer.id,
                frames = nextSelectedLayer.frames.ifEmpty { listOf(GlyphFrame(durationMs = 110L)) },
                selectedFrameIndex = 0,
                selectedLayerIdsForBatch = emptySet()
            )
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("BATCH DELETE ($deletedCount)")
        showToast("Deleted $deletedCount selected layer${if (deletedCount == 1) "" else "s"}")
    }

    /**
     * Batch Action 3: `[Merge Selected]`
     * Combines selected layers into a unified single `BITMAP` (or `SEQUENCE`) layer.
     */
    fun mergeSelectedLayers() {
        val current = _studioState.value
        val syncedLayers = current.visualLayers.map { layer ->
            if (layer.id == current.selectedLayerId) layer.copy(frames = current.frames) else layer
        }
        val targetIds = current.selectedLayerIdsForBatch.ifEmpty {
            setOfNotNull(current.selectedLayerId)
        }
        val layersToMerge = syncedLayers.filter { it.id in targetIds }
        if (layersToMerge.size < 2) {
            showToast("Select at least 2 layers to merge")
            return
        }

        pushUndoSnapshot(current.frames)
        val mergedLayer = GlyphScriptDecompiler.mergeVisualLayers(
            layersToMerge = layersToMerge,
            variables = current.dryRunVariables
        )
        val firstIdx = syncedLayers.indexOfFirst { it.id in targetIds }.coerceAtLeast(0)
        val remaining = syncedLayers.filterNot { it.id in targetIds }.toMutableList()
        val insertIdx = firstIdx.coerceAtMost(remaining.size)
        remaining.add(insertIdx, mergedLayer)

        _studioState.update { state ->
            state.copy(
                visualLayers = remaining,
                selectedLayerId = mergedLayer.id,
                frames = mergedLayer.frames,
                selectedFrameIndex = 0,
                selectedLayerIdsForBatch = setOf(mergedLayer.id)
            )
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("MERGED // ${mergedLayer.name}")
        showToast("Merged ${layersToMerge.size} layers into ${mergedLayer.name}")
    }

    /**
     * Batch Action 4: `[Group / Move]`
     * Shifts/reorders selected layers collectively in Z-order and assigns a shared group tag.
     */
    fun groupOrMoveSelectedLayers(moveUp: Boolean = true) {
        val current = _studioState.value
        val targetIds = current.selectedLayerIdsForBatch.ifEmpty {
            setOfNotNull(current.selectedLayerId)
        }
        if (targetIds.isEmpty()) return

        _studioState.update { state ->
            val list = state.visualLayers.toMutableList()
            val sharedGroup = list.firstOrNull { it.id in targetIds }?.groupName?.takeIf { it.isNotBlank() }
                ?: "batch_group"

            if (moveUp) {
                for (i in 1..list.lastIndex) {
                    if (list[i].id in targetIds && list[i - 1].id !in targetIds) {
                        val tmp = list[i - 1]
                        list[i - 1] = list[i].copy(groupName = sharedGroup)
                        list[i] = tmp
                    } else if (list[i].id in targetIds) {
                        list[i] = list[i].copy(groupName = sharedGroup)
                    }
                }
                if (list.isNotEmpty() && list[0].id in targetIds) {
                    list[0] = list[0].copy(groupName = sharedGroup)
                }
            } else {
                for (i in (list.lastIndex - 1) downTo 0) {
                    if (list[i].id in targetIds && list[i + 1].id !in targetIds) {
                        val tmp = list[i + 1]
                        list[i + 1] = list[i].copy(groupName = sharedGroup)
                        list[i] = tmp
                    } else if (list[i].id in targetIds) {
                        list[i] = list[i].copy(groupName = sharedGroup)
                    }
                }
                if (list.isNotEmpty() && list.last().id in targetIds) {
                    list[list.lastIndex] = list.last().copy(groupName = sharedGroup)
                }
            }
            state.copy(visualLayers = list)
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = false)
        renderCurrentStudioCompositeToHardware("GROUP / MOVE LAYERS")
        showToast("Grouped & shifted ${targetIds.size} layer${if (targetIds.size == 1) "" else "s"}")
    }

    fun updateVisualLayer(layerId: String, transform: (DecompiledVisualLayer) -> DecompiledVisualLayer) {
        val vars = _studioState.value.dryRunVariables
        _studioState.update { state ->
            val updated = state.visualLayers.map { layer ->
                if (layer.id == layerId) {
                    val modified = transform(layer)
                    if (!modified.isCanvasEdited && modified.primitiveType !in setOf("BITMAP", "SEQUENCE", "PLOT")) {
                        modified.copy(frames = GlyphScriptDecompiler.renderVisualLayerFrames(modified, vars))
                    } else {
                        modified
                    }
                } else {
                    layer
                }
            }
            val updatedSelectedFrames = if (state.selectedLayerId == layerId) {
                updated.find { it.id == layerId }?.frames ?: state.frames
            } else {
                state.frames
            }
            state.copy(
                visualLayers = updated,
                frames = updatedSelectedFrames
            )
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = false)
        renderCurrentStudioCompositeToHardware("UPDATE LAYER")
    }

    fun stampProceduralPattern(patternType: String) {
        val current = _studioState.value
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        pushUndoSnapshot(current.frames)
        val b = current.brushBrightness.coerceIn(32, 255)
        val dst = ByteArray(MATRIX_CELL_COUNT)

        for (r in 0 until MATRIX_SIZE) {
            for (c in 0 until MATRIX_SIZE) {
                if (!GlyphHardwareController.isCellInsideCircle(r, c)) continue
                val dist = hypot((r - 12).toDouble(), (c - 12).toDouble())
                when (patternType) {
                    "RING" -> if (dist in 9.5..11.8 || dist < 2.0) dst[r * MATRIX_SIZE + c] = b.toByte()
                    "CROSSHAIR" -> if (r == 12 || c == 12 || dist in 10.8..12.0) {
                        dst[r * MATRIX_SIZE + c] = b.toByte()
                    }
                    "GRID_DOTS" -> if (r % 2 == 0 && c % 2 == 0) dst[r * MATRIX_SIZE + c] = b.toByte()
                    "RADIAL_GRADIENT" -> {
                        val factor = (1.0 - (dist / 12.8)).coerceIn(0.0, 1.0)
                        dst[r * MATRIX_SIZE + c] = (b * factor).roundToInt().toByte()
                    }
                }
            }
        }
        val updatedFrame = current.frames[activeIdx].copy(pixels = dst)
        val updatedFrames = current.frames.toMutableList().apply { set(activeIdx, updatedFrame) }
        _studioState.update { it.copy(frames = updatedFrames) }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware(patternType)
    }

    fun modifyRow(row: Int, fill: Boolean) {
        if (row !in 0 until MATRIX_SIZE) return
        val current = _studioState.value
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val frame = current.frames[activeIdx]
        pushUndoSnapshot(current.frames)
        val targetB = if (fill) current.brushBrightness.coerceAtLeast(32) else 0
        val copy = frame.pixels.copyOf()
        for (c in 0 until MATRIX_SIZE) {
            if (GlyphHardwareController.isCellInsideCircle(row, c)) {
                copy[row * MATRIX_SIZE + c] = targetB.toByte()
            }
        }
        val updatedFrame = frame.copy(pixels = copy)
        val updatedFrames = current.frames.toMutableList().apply { set(activeIdx, updatedFrame) }
        _studioState.update { it.copy(frames = updatedFrames) }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("${if (fill) "FILL" else "CLEAR"} ROW $row")
    }

    fun modifyColumn(col: Int, fill: Boolean) {
        if (col !in 0 until MATRIX_SIZE) return
        val current = _studioState.value
        val activeIdx = current.selectedFrameIndex.coerceIn(0, current.frames.lastIndex)
        val frame = current.frames[activeIdx]
        pushUndoSnapshot(current.frames)
        val targetB = if (fill) current.brushBrightness.coerceAtLeast(32) else 0
        val copy = frame.pixels.copyOf()
        for (r in 0 until MATRIX_SIZE) {
            if (GlyphHardwareController.isCellInsideCircle(r, col)) {
                copy[r * MATRIX_SIZE + col] = targetB.toByte()
            }
        }
        val updatedFrame = frame.copy(pixels = copy)
        val updatedFrames = current.frames.toMutableList().apply { set(activeIdx, updatedFrame) }
        _studioState.update { it.copy(frames = updatedFrames) }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("${if (fill) "FILL" else "CLEAR"} COL $col")
    }

    fun undoStudioEdit() {
        val previous = undoStack.removeLastOrNull() ?: return
        val currentFrames = _studioState.value.frames.map { it.copy(pixels = it.pixels.copyOf()) }
        if (redoStack.size >= 20) {
            redoStack.removeFirst()
        }
        redoStack.addLast(currentFrames)
        _studioState.update { state ->
            val safeIdx = state.selectedFrameIndex.coerceIn(0, previous.lastIndex)
            state.copy(
                frames = previous,
                selectedFrameIndex = safeIdx,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("UNDO")
    }

    fun redoStudioEdit() {
        val next = redoStack.removeLastOrNull() ?: return
        val currentFrames = _studioState.value.frames.map { it.copy(pixels = it.pixels.copyOf()) }
        if (undoStack.size >= 20) {
            undoStack.removeFirst()
        }
        undoStack.addLast(currentFrames)
        _studioState.update { state ->
            val safeIdx = state.selectedFrameIndex.coerceIn(0, next.lastIndex)
            state.copy(
                frames = next,
                selectedFrameIndex = safeIdx,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
        renderCurrentStudioCompositeToHardware("REDO")
    }

    fun setStudioPlaybackMode(mode: AnimationPlaybackMode) {
        _studioState.update { state ->
            val nextLoops = when (mode) {
                AnimationPlaybackMode.ONCE -> 1
                else -> 0
            }
            state.copy(playbackMode = mode, loopCount = nextLoops)
        }
    }

    private fun pushUndoSnapshot(frames: List<GlyphFrame>) {
        if (undoStack.size >= 20) {
            undoStack.removeFirst()
        }
        undoStack.addLast(frames.map { it.copy(pixels = it.pixels.copyOf()) })
        redoStack.clear()
        _studioState.update {
            it.copy(canUndo = undoStack.isNotEmpty(), canRedo = false)
        }
    }

    // --- Timeline / Multi-Frame Sequencer ---

    fun selectTimelineFrame(index: Int) {
        stopStudioPreview()
        _studioState.update { state ->
            val safeIndex = index.coerceIn(0, state.frames.lastIndex)
            state.copy(selectedFrameIndex = safeIndex)
        }
        renderCurrentStudioCompositeToHardware("${_studioState.value.name} [F${index + 1}]")
    }

    fun addBlankFrame() {
        stopStudioPreview()
        _studioState.update { state ->
            pushUndoSnapshot(state.frames)
            val newFrame = GlyphFrame(
                id = UUID.randomUUID().toString(),
                durationMs = state.activeFrame.durationMs
            )
            val nextList = state.frames + newFrame
            state.copy(
                frames = nextList,
                selectedFrameIndex = nextList.lastIndex
            )
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
    }

    fun duplicateActiveFrame() {
        stopStudioPreview()
        _studioState.update { state ->
            pushUndoSnapshot(state.frames)
            val src = state.activeFrame
            val copy = GlyphFrame(
                id = UUID.randomUUID().toString(),
                durationMs = src.durationMs,
                pixels = src.pixels.copyOf()
            )
            val insertPos = (state.selectedFrameIndex + 1).coerceAtMost(state.frames.size)
            val nextList = state.frames.toMutableList().apply { add(insertPos, copy) }
            state.copy(frames = nextList, selectedFrameIndex = insertPos)
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
    }

    fun deleteActiveFrame() {
        stopStudioPreview()
        _studioState.update { state ->
            if (state.frames.size <= 1) {
                val cleared = listOf(GlyphFrame(durationMs = state.activeFrame.durationMs))
                return@update state.copy(frames = cleared, selectedFrameIndex = 0)
            }
            pushUndoSnapshot(state.frames)
            val nextList = state.frames.toMutableList().apply {
                removeAt(state.selectedFrameIndex)
            }
            val nextIdx = state.selectedFrameIndex.coerceIn(0, nextList.lastIndex)
            state.copy(frames = nextList, selectedFrameIndex = nextIdx)
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
    }

    fun moveActiveFrame(delta: Int) {
        stopStudioPreview()
        _studioState.update { state ->
            val from = state.selectedFrameIndex
            val to = (from + delta).coerceIn(0, state.frames.lastIndex)
            if (from == to) return@update state
            pushUndoSnapshot(state.frames)
            val nextList = state.frames.toMutableList()
            val item = nextList.removeAt(from)
            nextList.add(to, item)
            state.copy(frames = nextList, selectedFrameIndex = to)
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = true)
    }

    /**
     * Reorders a timeline frame from [fromIndex] to [toIndex] via horizontal drag-and-drop,
     * immediately updating the sequence playback order and serializing back to the AST sequence.
     */
    fun reorderTimelineFrame(fromIndex: Int, toIndex: Int) {
        stopStudioPreview()
        var reordered = false
        _studioState.update { state ->
            if (state.frames.size <= 1) return@update state
            val safeFrom = fromIndex.coerceIn(0, state.frames.lastIndex)
            val safeTo = toIndex.coerceIn(0, state.frames.lastIndex)
            if (safeFrom == safeTo) return@update state
            pushUndoSnapshot(state.frames)
            val nextList = state.frames.toMutableList()
            val moved = nextList.removeAt(safeFrom)
            nextList.add(safeTo, moved)

            // Remap multi-selected indices so checked frames stay checked after reorder
            val remappedSelection = if (state.selectedFrameIndicesForBatch.isNotEmpty()) {
                val selectedIds = state.selectedFrameIndicesForBatch
                    .filter { it in state.frames.indices }
                    .map { state.frames[it].id }
                    .toSet()
                nextList.mapIndexedNotNull { idx, f -> if (f.id in selectedIds) idx else null }.toSet()
            } else {
                emptySet()
            }

            reordered = true
            state.copy(
                frames = nextList,
                selectedFrameIndex = safeTo,
                selectedFrameIndicesForBatch = remappedSelection
            )
        }
        if (reordered) {
            syncActiveLayerToScriptBuffer(markCanvasEdited = true)
            renderCurrentStudioCompositeToHardware("FRAME REORDER // F${toIndex + 1}")
            showToast("Moved Frame F${fromIndex + 1} -> F${toIndex + 1}")
        }
    }

    fun toggleFrameMultiSelectMode(enabled: Boolean? = null) {
        _studioState.update { state ->
            val nextMode = enabled ?: !state.isFrameMultiSelectMode
            val nextSelected = if (nextMode) {
                state.selectedFrameIndicesForBatch.ifEmpty {
                    setOf(state.selectedFrameIndex.coerceIn(0, (state.frames.size - 1).coerceAtLeast(0)))
                }
            } else {
                emptySet()
            }
            state.copy(
                isFrameMultiSelectMode = nextMode,
                selectedFrameIndicesForBatch = nextSelected
            )
        }
    }

    fun toggleFrameCheckedForBatch(index: Int) {
        _studioState.update { state ->
            if (index !in state.frames.indices) return@update state
            val nextSet = state.selectedFrameIndicesForBatch.toMutableSet()
            if (!nextSet.add(index)) {
                nextSet.remove(index)
            }
            state.copy(
                isFrameMultiSelectMode = true,
                selectedFrameIndicesForBatch = nextSet,
                selectedFrameIndex = index
            )
        }
    }

    fun clearFrameBatchSelection() {
        _studioState.update { state ->
            state.copy(
                isFrameMultiSelectMode = false,
                selectedFrameIndicesForBatch = emptySet()
            )
        }
        showToast("Cleared frame selection")
    }

    /**
     * Clones all selected frames in sequence order and inserts them immediately after the last selected index.
     */
    fun duplicateSelectedFrames() {
        stopStudioPreview()
        var duplicatedCount = 0
        _studioState.update { state ->
            val targetIndices = state.selectedFrameIndicesForBatch
                .filter { it in state.frames.indices }
                .sorted()
                .ifEmpty { listOf(state.selectedFrameIndex.coerceIn(0, state.frames.lastIndex)) }
            if (targetIndices.isEmpty()) return@update state
            pushUndoSnapshot(state.frames)

            val clones = targetIndices.map { idx ->
                val src = state.frames[idx]
                GlyphFrame(
                    id = UUID.randomUUID().toString(),
                    durationMs = src.durationMs,
                    pixels = src.pixels.copyOf()
                )
            }
            val insertPos = (targetIndices.max() + 1).coerceIn(0, state.frames.size)
            val nextList = state.frames.toMutableList().apply {
                addAll(insertPos, clones)
            }
            val newSelectedIndices = (insertPos until (insertPos + clones.size)).toSet()
            duplicatedCount = clones.size
            state.copy(
                frames = nextList,
                selectedFrameIndex = (insertPos + clones.size - 1).coerceIn(0, nextList.lastIndex),
                selectedFrameIndicesForBatch = if (state.isFrameMultiSelectMode) newSelectedIndices else emptySet()
            )
        }
        if (duplicatedCount > 0) {
            syncActiveLayerToScriptBuffer(markCanvasEdited = true)
            renderCurrentStudioCompositeToHardware("BATCH DUP FRAMES ($duplicatedCount)")
            showToast("Duplicated $duplicatedCount frame${if (duplicatedCount == 1) "" else "s"}")
        }
    }

    /**
     * Removes all selected frames simultaneously, keeping at least 1 blank frame if all were selected.
     */
    fun deleteSelectedFrames() {
        stopStudioPreview()
        var deletedCount = 0
        _studioState.update { state ->
            val targetIndices = state.selectedFrameIndicesForBatch
                .filter { it in state.frames.indices }
                .toSet()
                .ifEmpty { setOf(state.selectedFrameIndex.coerceIn(0, state.frames.lastIndex)) }
            if (targetIndices.isEmpty()) return@update state
            pushUndoSnapshot(state.frames)

            val remaining = state.frames.filterIndexed { idx, _ -> idx !in targetIndices }
            deletedCount = targetIndices.size
            val nextFrames = remaining.ifEmpty {
                listOf(
                    GlyphFrame(
                        id = UUID.randomUUID().toString(),
                        durationMs = state.activeFrame.durationMs
                    )
                )
            }
            val nextIdx = (targetIndices.minOrNull() ?: 0).coerceIn(0, nextFrames.lastIndex)
            state.copy(
                frames = nextFrames,
                selectedFrameIndex = nextIdx,
                selectedFrameIndicesForBatch = emptySet(),
                isFrameMultiSelectMode = false
            )
        }
        if (deletedCount > 0) {
            syncActiveLayerToScriptBuffer(markCanvasEdited = true)
            renderCurrentStudioCompositeToHardware("BATCH DEL FRAMES ($deletedCount)")
            showToast("Deleted $deletedCount frame${if (deletedCount == 1) "" else "s"}")
        }
    }

    /**
     * Reverses the chronological playback order of only the selected frames within the sequence.
     */
    fun reverseSelectedFrames() {
        stopStudioPreview()
        var reversedCount = 0
        _studioState.update { state ->
            val sortedIndices = state.selectedFrameIndicesForBatch
                .filter { it in state.frames.indices }
                .sorted()
                .let { sel ->
                    if (sel.size >= 2) sel else state.frames.indices.toList()
                }
            if (sortedIndices.size < 2) return@update state
            pushUndoSnapshot(state.frames)

            val reversedSubset = sortedIndices.map { state.frames[it] }.reversed()
            val nextList = state.frames.toMutableList()
            sortedIndices.forEachIndexed { idx, framePos ->
                nextList[framePos] = reversedSubset[idx]
            }
            reversedCount = sortedIndices.size
            state.copy(
                frames = nextList,
                selectedFrameIndex = sortedIndices.first().coerceIn(0, nextList.lastIndex)
            )
        }
        if (reversedCount >= 2) {
            syncActiveLayerToScriptBuffer(markCanvasEdited = true)
            renderCurrentStudioCompositeToHardware("REVERSE FRAMES ($reversedCount)")
            showToast("Reversed $reversedCount selected frames")
        }
    }

    /**
     * Adjusts the frame interval/duration (e.g., 66ms, 110ms, 165ms, 330ms) for all selected frames at once.
     */
    fun updateSelectedFramesDuration(durationMs: Long) {
        val clampedMs = durationMs.coerceIn(33L, 2000L)
        var updatedCount = 0
        _studioState.update { state ->
            val targetIndices = state.selectedFrameIndicesForBatch
                .filter { it in state.frames.indices }
                .toSet()
                .ifEmpty { state.frames.indices.toSet() }
            if (targetIndices.isEmpty()) return@update state
            pushUndoSnapshot(state.frames)

            val nextList = state.frames.mapIndexed { idx, frame ->
                if (idx in targetIndices) {
                    frame.copy(durationMs = clampedMs)
                } else {
                    frame
                }
            }
            updatedCount = targetIndices.size
            state.copy(frames = nextList)
        }
        if (updatedCount > 0) {
            syncActiveLayerToScriptBuffer(markCanvasEdited = true)
            showToast("Set ${clampedMs}ms cadence on $updatedCount frame${if (updatedCount == 1) "" else "s"}")
        }
    }

    /**
     * Attaches a procedural code animation macro (`slide`, `bounce`, `spin`, `pulse`) to the
     * currently active Studio visual layer and immediately starts or updates preview playback.
     */
    fun applyProceduralAnimationPreset(presetKey: String) {
        val current = _studioState.value
        val activeSpeedMs = current.activeFrame.durationMs.coerceIn(50L, 400L)
        val macro: LayerAnimationMacro? = when (presetKey.uppercase()) {
            "NONE", "STATIC" -> null
            "SLIDE_UP" -> LayerAnimationMacro.SlideMacro(
                fromX = 12,
                fromY = 18,
                toX = 12,
                toY = 6,
                speedMs = activeSpeedMs,
                pauseMs = 1500L,
                loop = true
            )
            "SLIDE_DOWN" -> LayerAnimationMacro.SlideMacro(
                fromX = 12,
                fromY = 6,
                toX = 12,
                toY = 18,
                speedMs = activeSpeedMs,
                pauseMs = 1500L,
                loop = true
            )
            "SLIDE_RIGHT" -> LayerAnimationMacro.SlideMacro(
                fromX = 6,
                fromY = 12,
                toX = 18,
                toY = 12,
                speedMs = activeSpeedMs,
                pauseMs = 1500L,
                loop = true
            )
            "BOUNCE" -> LayerAnimationMacro.BounceMacro(
                axis = "y",
                minOffset = -3,
                maxOffset = 3,
                speedMs = activeSpeedMs.coerceAtMost(110L)
            )
            "RADAR_SPIN", "SPIN" -> LayerAnimationMacro.SpinMacro(
                stepDegrees = 45,
                intervalMs = activeSpeedMs
            )
            "PULSE" -> LayerAnimationMacro.PulseMacro(
                minBrightness = 40,
                maxBrightness = 255,
                periodMs = 1000L,
                isBreathe = false
            )
            "BREATHE" -> LayerAnimationMacro.PulseMacro(
                minBrightness = 25,
                maxBrightness = 255,
                periodMs = 1600L,
                isBreathe = true
            )
            else -> null
        }

        _studioState.update { state ->
            val layers = if (state.visualLayers.isEmpty()) {
                val defaultLayer = DecompiledVisualLayer(
                    name = "base_matrix",
                    primitiveType = if (state.frames.size > 1) "SEQUENCE" else "BITMAP",
                    frames = state.frames,
                    animationMacro = macro
                )
                listOf(defaultLayer)
            } else {
                val targetId = state.selectedLayerId ?: state.visualLayers.first().id
                state.visualLayers.map { layer ->
                    if (layer.id == targetId) {
                        layer.copy(animationMacro = macro, frames = state.frames)
                    } else {
                        layer
                    }
                }
            }
            val selectedId = state.selectedLayerId ?: layers.firstOrNull()?.id
            state.copy(
                visualLayers = layers,
                selectedLayerId = selectedId
            )
        }

        syncActiveLayerToScriptBuffer(markCanvasEdited = false)
        evaluateConsoleDryRunInternal(pushToHardware = false, recordAudit = false)

        if (macro != null) {
            startStudioPreview()
            showToast("Applied ${macro.presetLabel()} to active layer")
        } else {
            stopStudioPreview()
            renderCurrentStudioCompositeToHardware("MACRO CLEARED")
            showToast("Cleared procedural animation macro")
        }
    }

    fun updateActiveFrameDuration(durationMs: Long) {
        val clamped = durationMs.coerceIn(30L, 2000L)
        _studioState.update { state ->
            // Apply the selected speed preset across all keyframes and active layer interval
            val nextList = state.frames.map { it.copy(durationMs = clamped) }
            val targetLayerId = state.selectedLayerId ?: state.visualLayers.firstOrNull()?.id
            val updatedLayers = state.visualLayers.map { layer ->
                if (layer.id == targetLayerId) {
                    val updatedMacro = when (val m = layer.animationMacro) {
                        is LayerAnimationMacro.SlideMacro -> m.copy(speedMs = clamped)
                        is LayerAnimationMacro.BounceMacro -> m.copy(speedMs = clamped)
                        is LayerAnimationMacro.SpinMacro -> m.copy(intervalMs = clamped)
                        is LayerAnimationMacro.PulseMacro -> m
                        null -> null
                    }
                    layer.copy(
                        intervalMs = clamped,
                        frames = nextList,
                        animationMacro = updatedMacro
                    )
                } else {
                    layer
                }
            }
            state.copy(
                frames = nextList,
                visualLayers = updatedLayers
            )
        }
        syncActiveLayerToScriptBuffer(markCanvasEdited = false)
        if (_studioState.value.isPreviewPlaying) {
            startStudioPreview()
        }
    }

    fun toggleStudioPreview() {
        if (_studioState.value.isPreviewPlaying) {
            stopStudioPreview()
        } else {
            startStudioPreview()
        }
    }

    private fun startStudioPreview() {
        studioPreviewJob?.cancel()
        studioPreviewJob = viewModelScope.launch {
            _studioState.update { it.copy(isPreviewPlaying = true) }
            var completedCycles = 0

            try {
                while (isActive) {
                    val state = _studioState.value
                    val frames = state.frames
                    if (frames.isEmpty()) break

                    val activeLayer = state.selectedVisualLayer
                    val activeMacro = activeLayer?.animationMacro

                    // Case 1: Single static canvas frame with a Procedural Animation Macro attached
                    if (frames.size == 1 && activeMacro != null) {
                        val baseFrame = frames.first()
                        val macroInterval = activeMacro.frameIntervalMs.coerceIn(33L, 500L)
                        val totalMacroSteps = GlyphFrameCompositor.computeMacroFrameCount(activeMacro)
                            .coerceIn(2, 48)

                        for (step in 0 until totalMacroSteps) {
                            if (!isActive || !_studioState.value.isPreviewPlaying) break
                            val transformed = GlyphFrameCompositor.applyAnimationMacroToBytes(
                                basePixels = baseFrame.pixels,
                                macro = activeMacro,
                                tick = step,
                                totalFrames = totalMacroSteps
                            )
                            val bg = if (state.showFullPreviewComposite) state.compositeOverlayPixels else null
                            val compositeOut = if (bg != null) {
                                ByteArray(MATRIX_CELL_COUNT) { i ->
                                    maxOf(transformed[i].toInt() and 0xFF, bg[i].toInt() and 0xFF).toByte()
                                }
                            } else {
                                transformed
                            }
                            _studioState.update {
                                it.copy(
                                    previewPixels = transformed,
                                    previewFramePositionLabel = "${step + 1}/$totalMacroSteps"
                                )
                            }
                            val liveMirror = _studioState.value.isLiveHardwareMirror
                            if (liveMirror) {
                                hardwareController.invalidateDispatchedBuffer()
                            }
                            hardwareController.renderImmediateFrame(
                                pixels = compositeOut,
                                frameLabel = "${state.name} [${activeMacro.presetLabel()}]",
                                pushToHardware = liveMirror,
                                activeFps = (1000L / macroInterval).toInt().coerceIn(5, 30),
                                isPersistentStream = true
                            )
                            delay(macroInterval)
                        }
                    } else {
                        // Case 2: Multi-frame timeline sequence (exact frame duration timing — zero slow-mo lag)
                        val playbackOrder: List<Int> = when (state.playbackMode) {
                            AnimationPlaybackMode.PING_PONG -> {
                                if (frames.size <= 2) {
                                    frames.indices.toList()
                                } else {
                                    frames.indices.toList() + (frames.lastIndex - 1 downTo 1).toList()
                                }
                            }
                            else -> frames.indices.toList()
                        }

                        for (pos in playbackOrder.indices) {
                            if (!isActive || !_studioState.value.isPreviewPlaying) break
                            val idx = playbackOrder[pos]
                            val frame = frames[idx]
                            val dur = frame.durationMs.coerceIn(30L, 2000L)
                            val rawPixels = if (activeMacro != null) {
                                GlyphFrameCompositor.applyAnimationMacroToBytes(
                                    basePixels = frame.pixels,
                                    macro = activeMacro,
                                    tick = pos,
                                    totalFrames = playbackOrder.size.coerceAtLeast(2)
                                )
                            } else {
                                frame.pixels
                            }

                            val bg = if (state.showFullPreviewComposite) state.compositeOverlayPixels else null
                            val compositeOut = if (bg != null) {
                                ByteArray(MATRIX_CELL_COUNT) { i ->
                                    maxOf(rawPixels[i].toInt() and 0xFF, bg[i].toInt() and 0xFF).toByte()
                                }
                            } else {
                                rawPixels
                            }

                            _studioState.update {
                                it.copy(
                                    previewPixels = rawPixels,
                                    previewFramePositionLabel = "${idx + 1}/${frames.size}"
                                )
                            }
                            val liveMirror = _studioState.value.isLiveHardwareMirror
                            if (liveMirror) {
                                hardwareController.invalidateDispatchedBuffer()
                            }
                            hardwareController.renderImmediateFrame(
                                pixels = compositeOut,
                                frameLabel = "${state.name} [${idx + 1}/${frames.size}]",
                                pushToHardware = liveMirror,
                                activeFps = (1000L / dur).toInt().coerceIn(3, 30),
                                isPersistentStream = true
                            )
                            delay(dur)
                        }
                    }

                    completedCycles++
                    if (state.playbackMode == AnimationPlaybackMode.ONCE) {
                        break
                    }
                    val targetLoops = _studioState.value.loopCount
                    if (targetLoops > 0 && completedCycles >= targetLoops) {
                        break
                    }
                }
            } finally {
                _studioState.update {
                    it.copy(isPreviewPlaying = false, previewPixels = null)
                }
                if (!_studioState.value.isLiveHardwareMirror) {
                    hardwareController.clearAndCloseSession()
                } else {
                    renderCurrentStudioCompositeToHardware("STUDIO // ${_studioState.value.name}")
                }
            }
        }
    }

    fun stopStudioPreview() {
        studioPreviewJob?.cancel()
        studioPreviewJob = null
        _studioState.update {
            it.copy(isPreviewPlaying = false, previewPixels = null)
        }
    }

    fun saveStudioAnimationToDatabase() {
        onSaveStudioChanges()
    }

    fun createNewBlankStudioAnimation() {
        stopStudioPreview()
        undoStack.clear()
        redoStack.clear()
        _activeStudioRuleId.value = null
        val liveMirror = _studioState.value.isLiveHardwareMirror
        _studioState.value = StudioEditorState(
            editingAnimationId = 0L,
            name = "Circular Seq ${System.currentTimeMillis() % 1000}",
            tagsInput = "CUSTOM, NP3",
            isLiveHardwareMirror = liveMirror,
            frames = listOf(GlyphFrame(id = UUID.randomUUID().toString(), durationMs = 110L)),
            activeStudioRuleId = null
        )
        if (liveMirror) {
            hardwareController.clearAndCloseSession()
        }
    }

    fun loadAnimationIntoStudio(
        animation: GlyphAnimation,
        navigateToStudio: Boolean = true,
        pushToHardware: Boolean = _studioState.value.isLiveHardwareMirror
    ) {
        stopStudioPreview()
        undoStack.clear()
        redoStack.clear()
        val currentMirror = _studioState.value.isLiveHardwareMirror
        val maskedFrames = animation.frames.map {
            it.copy(pixels = GlyphHardwareController.applyCircularMask(it.pixels))
        }.ifEmpty { listOf(GlyphFrame()) }

        _studioState.value = StudioEditorState(
            editingAnimationId = animation.id,
            name = animation.name,
            tagsInput = animation.tags.joinToString(", "),
            frames = maskedFrames,
            selectedFrameIndex = 0,
            isLiveHardwareMirror = currentMirror,
            loopCount = animation.loopCount,
            transitionCurve = animation.transitionCurve
        )
        val firstFrame = maskedFrames.firstOrNull()
        if (firstFrame != null) {
            hardwareController.renderImmediateFrame(
                pixels = firstFrame.pixels,
                frameLabel = animation.name,
                pushToHardware = pushToHardware
            )
        }
        if (navigateToStudio) {
            _currentTab.value = GlyphScreenTab.STUDIO
        }
    }

    // --- Streamlined 3-Step Accordion Rule Creator ---

    fun startNewRuleDraft() {
        _activeRule.value = null
        _activeStudioRuleId.value = null
        _consoleState.update { it.copy(scriptInput = "", compileError = null, allDiagnostics = emptyList()) }
        val defaultAnim = animations.value.firstOrNull()
        _draftRule.value = TriggerRule(
            id = 0L,
            name = "Custom Glyph Rule",
            isEnabled = true,
            priority = 6,
            cooldownMs = 1500L,
            conditionType = ConditionType.NOTIFICATION,
            actionType = if (defaultAnim != null) ActionType.PLAY_ANIMATION else ActionType.AUDIO_WAVEFORM,
            targetAnimationId = defaultAnim?.id ?: 0L,
            targetAnimationName = defaultAnim?.name ?: "Custom Matrix Sequence",
            holdUntilConditionClears = true
        )
        _isRuleCreatorOpen.value = true
    }

    fun editExistingRule(rule: TriggerRule) {
        _draftRule.value = rule
        _isRuleCreatorOpen.value = true
    }

    fun closeRuleCreator() {
        _isRuleCreatorOpen.value = false
    }

    fun openStudioFromRuleCreator() {
        _isRuleCreatorOpen.value = false
        _currentTab.value = GlyphScreenTab.STUDIO
    }

    fun updateDraftRule(transform: (TriggerRule) -> TriggerRule) {
        _draftRule.update(transform)
    }

    fun saveDraftRule() {
        val current = _draftRule.value
        val validName = current.name.trim().ifBlank {
            "${current.targetAppLabel} → ${current.actionType.label}"
        }
        viewModelScope.launch {
            val ruleToSave = current.copy(name = validName)
            val savedId = repository.saveRule(ruleToSave)
            val savedRule = ruleToSave.copy(id = savedId)
            _activeRule.value = savedRule
            _activeStudioRuleId.value = savedId.toString()
            _draftRule.update { it.copy(id = savedId, name = validName) }
            _isRuleCreatorOpen.value = false
            showToast("Saved Rule \"$validName\"")
            _currentTab.value = GlyphScreenTab.DASHBOARD
        }
    }

    fun toggleRuleEnabled(ruleId: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleRule(ruleId, enabled)
            if (!enabled) {
                hardwareController.clearAndCloseSession()
            }
        }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch {
            repository.deleteRule(ruleId)
            if (_activeRule.value?.id == ruleId) {
                _activeRule.value = null
            }
            if (_activeStudioRuleId.value == ruleId.toString()) {
                _activeStudioRuleId.value = null
            }
            showToast("Rule deleted")
        }
    }

    fun testFireRule(rule: TriggerRule) {
        _activeRule.value = rule
        _activeStudioRuleId.value = rule.id.toString()
        val console = _consoleState.value
        ruleEngine.forceExecuteRule(
            rule = rule,
            titleOverride = console.dryRunTitle,
            bodyOverride = console.dryRunBody,
            variableOverrides = console.dryRunVariableOverrides
        )
        showToast("Streaming Rule at 30 FPS: ${rule.name}")
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = getApplication<Application>().packageManager

            val installedPackages = runCatching {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
            }.getOrDefault(emptyList())

            val userApps = installedPackages.mapNotNull { appInfo ->
                val pkg = appInfo.packageName ?: return@mapNotNull null
                val isUserApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 ||
                    pm.getLaunchIntentForPackage(pkg) != null
                if (!isUserApp) return@mapNotNull null
                val label = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg)
                InstalledAppOption(packageName = pkg, appName = label, isSystemPreset = false)
            }

            val popularPresets = listOf(
                InstalledAppOption("*", "Any Installed App", true),
                InstalledAppOption("com.whatsapp", "WhatsApp", true),
                InstalledAppOption("com.google.android.youtube", "YouTube", true),
                InstalledAppOption("com.spotify.music", "Spotify", true),
                InstalledAppOption("com.google.android.apps.maps", "Google Maps", true),
                InstalledAppOption("com.google.android.dialer", "Phone / Dialer", true),
                InstalledAppOption("com.ubercab", "Uber", true),
                InstalledAppOption("com.google.android.apps.messaging", "Google Messages", true),
                InstalledAppOption("org.telegram.messenger", "Telegram", true),
                InstalledAppOption("com.google.android.gm", "Gmail", true)
            )

            val merged = (popularPresets + userApps)
                .distinctBy { it.packageName }
                .sortedWith(
                    compareByDescending<InstalledAppOption> { it.packageName == "*" }
                        .thenByDescending { it.isSystemPreset }
                        .thenBy { it.appName.lowercase() }
                )

            withContext(Dispatchers.Main) {
                _installedApps.value = merged
            }
        }
    }

    // --- Diagnostics & Signal Simulation ---

    fun clearAuditLogs() {
        EventIngestBus.clearAuditLogs()
        showToast("Diagnostics log cleared")
    }

    fun simulateNotificationSignal(
        packageName: String,
        appLabel: String,
        title: String,
        content: String,
        subtext: String = "",
        progress: Int? = null
    ) {
        EventIngestBus.recordPayloadInspectorEvent(
            NotificationPayloadAuditEntry(
                packageName = packageName,
                appLabel = appLabel,
                rawExtraTitle = title,
                rawExtraText = content,
                rawExtraSubText = subtext,
                rawExtraProgress = progress,
                rawExtraProgressMax = if (progress != null) 100 else null,
                computedProgressPercent = progress?.coerceIn(0, 100)
            )
        )
        EventIngestBus.emitSignal(
            SystemSignalEvent(
                conditionType = ConditionType.NOTIFICATION,
                sourcePackage = packageName,
                sourceAppLabel = appLabel,
                title = title,
                content = content,
                subtext = subtext,
                progress = progress,
                progressMax = if (progress != null) 100 else null,
                isSimulated = true
            )
        )
        showToast("Simulated Notification: $appLabel")
    }

    // --- Notification Sniffer ("Listen & Capture") Actions ---

    fun toggleNotificationSniffer(active: Boolean) {
        EventIngestBus.setNotificationSnifferActive(active)
        if (active) {
            showToast("Notification Sniffer Active — Listening for next notification")
        } else {
            showToast("Notification Sniffer Stopped")
        }
    }

    fun clearCapturedSnifferNotification() {
        EventIngestBus.clearCapturedSnifferNotification()
    }

    // --- GlyphScript v2 Console & Auditing Actions ---

    fun updateConsoleScriptInput(script: String) {
        _consoleState.update { it.copy(scriptInput = script, compileError = null, allDiagnostics = emptyList()) }
        consoleDryRunDebounceJob?.cancel()
        consoleDryRunDebounceJob = viewModelScope.launch {
            delay(220L)
            evaluateConsoleDryRunInternal(pushToHardware = false, recordAudit = false)
        }
    }

    fun pasteIntoConsoleScript(pastedText: String) {
        if (pastedText.isBlank()) return
        val current = _consoleState.value.scriptInput
        val updated = if (current.isBlank()) pastedText else "$current\n\n$pastedText"
        updateConsoleScriptInput(updated)
        evaluateConsoleDryRunInternal(pushToHardware = false, recordAudit = false)
        showToast("Pasted text into GlyphScript Editor")
    }

    fun clearConsoleScript() {
        val current = _consoleState.value.scriptInput
        if (current.isNotBlank()) {
            lastClearedConsoleScriptBuffer = current
            updateConsoleScriptInput("")
            showToast("Editor cleared (Tap UNDO to restore)")
        }
    }

    fun undoClearConsoleScript() {
        val restored = lastClearedConsoleScriptBuffer
        if (!restored.isNullOrBlank()) {
            updateConsoleScriptInput(restored)
            evaluateConsoleDryRunInternal(pushToHardware = false, recordAudit = false)
            lastClearedConsoleScriptBuffer = null
            showToast("Restored cleared script")
        }
    }

    fun loadConsoleTemplatePreset(presetIndex: Int) {
        val preset = when (presetIndex) {
            0 -> com.example.dsl.PRESET_FOOD_DELIVERY
            1 -> com.example.dsl.PRESET_CAB_TRACKER
            2 -> com.example.dsl.PRESET_PIXEL_BATTERY
            3 -> com.example.dsl.PRESET_URGENT_ALERT
            else -> com.example.dsl.DEFAULT_CONSOLE_SCRIPT_SAMPLE
        }
        updateConsoleScriptInput(preset)
        evaluateConsoleDryRunInternal(pushToHardware = false, recordAudit = false)
        showToast("Loaded Preset #${presetIndex + 1}")
    }

    fun appendScratchpadBitmapToConsole(bitmapBlock: String) {
        if (bitmapBlock.isBlank()) return
        val current = _consoleState.value.scriptInput
        val updated = if (current.isBlank()) {
            """RULE "Custom 25x25 Scratchpad Bitmap"
WHEN notification FROM "*"
DISPLAY {
$bitmapBlock
}
BEHAVIOR {
  priority: 8
  until: notification.dismissed
}"""
        } else {
            "$current\n\n$bitmapBlock"
        }
        updateConsoleScriptInput(updated)
        evaluateConsoleDryRunInternal(pushToHardware = false, recordAudit = false)
        showToast("Exported Scratchpad Bitmap to GlyphScript Editor")
    }

    fun updateConsoleDryRunInputs(title: String, body: String, progress: Int) {
        _consoleState.update {
            it.copy(
                dryRunTitle = title,
                dryRunBody = body,
                dryRunProgress = progress.coerceIn(0, 100),
                dryRunVariableOverrides = emptyMap()
            )
        }
        evaluateConsoleDryRunInternal(pushToHardware = false, recordAudit = false)
    }

    /**
     * Updates an individual extracted variable in-place on the Dry Run card,
     * synchronizes the mock notification Title/Body inputs when applicable, and
     * immediately re-composites the 489-LED circular preview without needing a separate intent.
     */
    fun updateConsoleDryRunVariable(varName: String, newValue: String) {
        val cleanKey = varName.removePrefix("$").trim()
        if (cleanKey.isBlank()) return

        _consoleState.update { state ->
            val nextOverrides = LinkedHashMap(state.dryRunVariableOverrides).apply {
                put(cleanKey, newValue)
            }
            val lowerKey = cleanKey.lowercase()
            val syncTitle = if (lowerKey.contains("title") || lowerKey == "maneuver" || lowerKey == "direction") {
                newValue
            } else {
                state.dryRunTitle
            }
            val syncBody = if (lowerKey.contains("body") || lowerKey.contains("text") || lowerKey == "trip" || lowerKey == "eta") {
                newValue
            } else {
                state.dryRunBody
            }
            state.copy(
                dryRunVariableOverrides = nextOverrides,
                dryRunTitle = syncTitle,
                dryRunBody = syncBody
            )
        }
        evaluateConsoleDryRunInternal(pushToHardware = false, recordAudit = false)
    }

    fun dryRunConsoleScript() {
        consoleDryRunDebounceJob?.cancel()
        hardwareController.clearAndCloseSession()
        hardwareController.invalidateDispatchedBuffer()
        val ok = evaluateConsoleDryRunInternal(pushToHardware = true, recordAudit = true)
        if (ok) {
            showToast("Dry Run Rendered to 25×25 Matrix")
        }
    }

    fun importAndApplyConsoleScript() {
        val current = _consoleState.value
        val result = GlyphScriptCompiler.compile(
            scriptText = current.scriptInput,
            context = getApplication(),
            dryRunTitle = current.dryRunTitle,
            dryRunBody = current.dryRunBody,
            dryRunProgress = current.dryRunProgress
        )

        when (result) {
            is GlyphScriptCompileResult.Failure -> {
                _consoleState.update { it.copy(compileError = result.error, allDiagnostics = result.diagnostics) }
                EventIngestBus.recordScriptCompileEvent(
                    ScriptCompileAuditEntry(
                        ruleName = "Compile Error",
                        rawScript = current.scriptInput,
                        astStatus = "ERROR (Line ${result.error.line})",
                        packageResolutionOutcome = "Failed",
                        isSuccess = false,
                        errorLine = result.error.line,
                        errorColumn = result.error.column,
                        errorMessage = result.error.formattedMessage
                    )
                )
            }

            is GlyphScriptCompileResult.Success -> {
                val mergedVars = LinkedHashMap(result.extractedPreviewVariables).apply {
                    putAll(current.dryRunVariableOverrides)
                }
                val activeFrames = if (current.dryRunVariableOverrides.isNotEmpty()) {
                    GlyphFrameCompositor.compositeProgramFrames(result.program, mergedVars)
                } else {
                    result.previewFrames
                }
                val firstPreview = activeFrames.firstOrNull()?.pixels?.copyOf() ?: ByteArray(MATRIX_CELL_COUNT)
                val freshFrames = activeFrames.map { it.copy(pixels = it.pixels.copyOf()) }
                _consoleState.update {
                    it.copy(
                        compileError = null,
                        allDiagnostics = result.warnings,
                        lastCompiledRuleName = result.program.ruleName,
                        resolvedPackageBadge = result.resolvedPackageName,
                        resolvedAppLabel = result.resolvedAppLabel,
                        extractedVariablesPreview = mergedVars.filterKeys { k ->
                            k !in setOf(
                                "title", "notification.title",
                                "body", "text", "content", "notification.body", "notification.text",
                                "subText", "subtext", "sub_text", "notification.subtext", "notification.sub_text",
                                "progress_percent", "progress", "notification.progress",
                                "__dry_run_text_override__"
                            )
                        },
                        dryRunPreviewPixels = firstPreview,
                        dryRunPreviewFrames = freshFrames,
                        dryRunGenerationId = it.dryRunGenerationId + 1L
                    )
                }

                EventIngestBus.recordScriptCompileEvent(
                    ScriptCompileAuditEntry(
                        ruleName = result.program.ruleName,
                        rawScript = current.scriptInput,
                        astStatus = "AST VALID (${result.program.displayLayers.size} layers)",
                        packageResolutionOutcome = "${result.resolvedAppLabel} (${result.resolvedPackageName})",
                        isSuccess = true
                    )
                )

                viewModelScope.launch(Dispatchers.IO) {
                    val existingId = rules.value.firstOrNull {
                        it.name.equals(result.program.ruleName.trim(), ignoreCase = true)
                    }?.id ?: _activeRule.value?.takeIf {
                        it.name.equals(result.program.ruleName.trim(), ignoreCase = true)
                    }?.id ?: 0L
                    val ruleToPersist = result.compiledRule.copy(id = existingId)
                    val savedId = repository.updateRule(ruleToPersist)
                    val savedRule = ruleToPersist.copy(id = savedId)
                    withContext(Dispatchers.Main) {
                        _activeRule.value = savedRule
                        _activeStudioRuleId.value = savedId.toString()
                        bindStudioToScriptInternal(
                            script = current.scriptInput,
                            ruleId = savedId,
                            preserveSelectedLayerName = _studioState.value.selectedVisualLayer?.name
                        )
                        hardwareController.invalidateDispatchedBuffer()
                        ruleEngine.forceExecuteRule(
                            rule = savedRule,
                            titleOverride = current.dryRunTitle,
                            bodyOverride = current.dryRunBody,
                            variableOverrides = current.dryRunVariableOverrides
                        )
                        showToast("Compiled & Applied Rule: \"${result.program.ruleName}\"")
                    }
                }
            }
        }
    }

    fun loadRuleIntoConsole(rule: TriggerRule) {
        _activeRule.value = rule
        _activeStudioRuleId.value = rule.id.toString()
        val exported = rule.glyphScriptSource.ifBlank {
            GlyphScriptCompiler.exportRuleToGlyphScript(rule)
        }
        _consoleState.update {
            it.copy(
                scriptInput = exported,
                compileError = null,
                dryRunVariableOverrides = emptyMap()
            )
        }
        bindStudioToScriptInternal(
            script = exported,
            ruleId = rule.id,
            preserveSelectedLayerName = null
        )
        evaluateConsoleDryRunInternal(pushToHardware = true, recordAudit = true)
        showToast("Loaded \"${rule.name}\" into Script Console")
    }

    fun notifySystemPromptCopied() {
        showToast("System prompt copied to clipboard.")
    }

    fun notifyDiagnosticsExported() {
        showToast("Diagnostics log copied to clipboard.")
    }

    private fun evaluateConsoleDryRunInternal(
        pushToHardware: Boolean,
        recordAudit: Boolean
    ): Boolean {
        val current = _consoleState.value
        if (current.scriptInput.isBlank()) {
            _consoleState.update {
                it.copy(
                    compileError = null,
                    lastCompiledRuleName = null,
                    extractedVariablesPreview = emptyMap(),
                    dryRunPreviewPixels = ByteArray(MATRIX_CELL_COUNT),
                    dryRunPreviewFrames = emptyList(),
                    dryRunGenerationId = it.dryRunGenerationId + 1L
                )
            }
            return false
        }
        val result = GlyphScriptCompiler.compile(
            scriptText = current.scriptInput,
            context = getApplication(),
            dryRunTitle = current.dryRunTitle,
            dryRunBody = current.dryRunBody,
            dryRunProgress = current.dryRunProgress
        )

        return when (result) {
            is GlyphScriptCompileResult.Failure -> {
                _consoleState.update {
                    it.copy(
                        compileError = result.error,
                        allDiagnostics = result.diagnostics,
                        dryRunGenerationId = it.dryRunGenerationId + 1L
                    )
                }
                if (recordAudit) {
                    EventIngestBus.recordScriptCompileEvent(
                        ScriptCompileAuditEntry(
                            ruleName = "Dry Run Error",
                            rawScript = current.scriptInput,
                            astStatus = "ERROR (Line ${result.error.line})",
                            packageResolutionOutcome = "Failed",
                            isSuccess = false,
                            errorLine = result.error.line,
                            errorColumn = result.error.column,
                            errorMessage = result.error.formattedMessage
                        )
                    )
                }
                false
            }

            is GlyphScriptCompileResult.Success -> {
                val mergedVars = LinkedHashMap(result.extractedPreviewVariables).apply {
                    putAll(current.dryRunVariableOverrides)
                }
                val activePreviewFrames = if (current.dryRunVariableOverrides.isNotEmpty()) {
                    GlyphFrameCompositor.compositeProgramFrames(result.program, mergedVars)
                } else {
                    result.previewFrames
                }
                val activeCompositeBytes = if (current.dryRunVariableOverrides.isNotEmpty()) {
                    GlyphFrameCompositor.compositeFrames(result.program, mergedVars)
                } else {
                    result.compositeFrames
                }
                val firstPreview = activePreviewFrames.firstOrNull()?.pixels?.copyOf() ?: ByteArray(MATRIX_CELL_COUNT)
                val freshFrames = activePreviewFrames.map { it.copy(pixels = it.pixels.copyOf()) }

                val userVars = LinkedHashMap<String, String>()
                for (decl in result.program.extractDeclarations) {
                    userVars[decl.varName] = mergedVars[decl.varName] ?: decl.defaultValue
                }
                for ((k, v) in mergedVars) {
                    if (k !in setOf(
                            "title", "notification.title",
                            "body", "text", "content", "notification.body", "notification.text",
                            "subText", "subtext", "sub_text", "notification.subtext", "notification.sub_text",
                            "progress_percent", "progress", "notification.progress",
                            "__dry_run_text_override__"
                        )
                    ) {
                        userVars[k] = v
                    }
                }

                _consoleState.update {
                    it.copy(
                        compileError = null,
                        allDiagnostics = result.warnings,
                        lastCompiledRuleName = result.program.ruleName,
                        resolvedPackageBadge = result.resolvedPackageName,
                        resolvedAppLabel = result.resolvedAppLabel,
                        extractedVariablesPreview = userVars,
                        dryRunPreviewPixels = firstPreview,
                        dryRunPreviewFrames = freshFrames,
                        dryRunGenerationId = it.dryRunGenerationId + 1L
                    )
                }
                if (recordAudit) {
                    EventIngestBus.recordScriptCompileEvent(
                        ScriptCompileAuditEntry(
                            ruleName = result.program.ruleName,
                            rawScript = current.scriptInput,
                            astStatus = "DRY RUN OK (${result.program.displayLayers.size} layers)",
                            packageResolutionOutcome = "${result.resolvedAppLabel} (${result.resolvedPackageName})",
                            isSuccess = true
                        )
                    )
                }
                if (pushToHardware) {
                    val intervalMs = result.compiledRule.overrideIntervalMs ?: 100L
                    GlyphEngineForegroundService.startCompositeHardwareLoop(
                        hardwareController = hardwareController,
                        compositeFrames = activeCompositeBytes,
                        frameName = "DRY RUN // ${result.program.ruleName}",
                        frameIntervalMs = intervalMs,
                        maxDurationMs = 10_000L,
                        holdIndefinitely = true,
                        ruleId = 0L,
                        ruleName = "DRY RUN // ${result.program.ruleName}",
                        sourcePackage = result.resolvedPackageName,
                        priority = 10
                    )
                }
                true
            }
        }
    }

    fun simulateBatterySignal(level: Int, isCharging: Boolean, triggerChargingEvent: Boolean) {
        EventIngestBus.emitSignal(
            SystemSignalEvent(
                conditionType = if (triggerChargingEvent) ConditionType.CHARGING_STATE else ConditionType.BATTERY_LEVEL,
                sourceAppLabel = "Battery Telemetry",
                batteryLevel = level.coerceIn(0, 100),
                isCharging = isCharging,
                isSimulated = true
            )
        )
        showToast("Simulated Battery: $level%")
    }

    fun simulateMediaSignal(state: MediaTriggerState, trackTitle: String) {
        if (state == MediaTriggerState.PAUSED) {
            EventIngestBus.emitClearForSource("MEDIA_STOPPED")
        }
        EventIngestBus.emitSignal(
            SystemSignalEvent(
                conditionType = ConditionType.MEDIA_PLAYBACK,
                sourceAppLabel = "Media Session",
                title = trackTitle,
                mediaState = state,
                isSimulated = true
            )
        )
        showToast("Simulated Media: ${state.label}")
    }

    fun simulateSystemPostureSignal(conditionType: ConditionType) {
        val signal = when (conditionType) {
            ConditionType.FACE_DOWN -> SystemSignalEvent(
                conditionType = ConditionType.FACE_DOWN,
                sourceAppLabel = "Accelerometer",
                isFaceDown = true,
                isSimulated = true
            )
            ConditionType.TIMER_ALARM -> SystemSignalEvent(
                conditionType = ConditionType.TIMER_ALARM,
                sourceAppLabel = "Clock / Timer",
                isTimerOrAlarmActive = true,
                isSimulated = true
            )
            ConditionType.LOW_POWER_MODE -> SystemSignalEvent(
                conditionType = ConditionType.LOW_POWER_MODE,
                sourceAppLabel = "Battery Saver",
                isLowPowerMode = true,
                isSimulated = true
            )
            ConditionType.BLUETOOTH_STATE,
            ConditionType.WIFI_STATE,
            ConditionType.HOTSPOT_STATE,
            ConditionType.TORCH_STATE,
            ConditionType.AIRPLANE_MODE -> SystemSignalEvent(
                conditionType = conditionType,
                sourceAppLabel = conditionType.label,
                title = "${conditionType.label}: ON",
                toggleActive = true,
                isSimulated = true
            )
            ConditionType.RINGER_MODE -> SystemSignalEvent(
                conditionType = ConditionType.RINGER_MODE,
                sourceAppLabel = "Ringer Mode",
                title = "Ringer: Vibrate",
                ringerMode = SystemToggleFilter.RINGER_VIBRATE,
                toggleActive = true,
                isSimulated = true
            )
            else -> SystemSignalEvent(
                conditionType = ConditionType.SCREEN_STATE,
                sourceAppLabel = "Display",
                screenState = ScreenTriggerState.SCREEN_OFF,
                isSimulated = true
            )
        }
        EventIngestBus.emitSignal(signal)
        showToast("Simulated: ${conditionType.label}")
    }

    // --- Offline JSON Export & Import ---

    fun openJsonExportModal() {
        viewModelScope.launch {
            val json = repository.exportWorkspaceToJson()
            _jsonModalPayload.value = json
        }
    }

    fun closeJsonModal() {
        _jsonModalPayload.value = null
    }

    fun importJsonWorkspace(jsonText: String) {
        viewModelScope.launch {
            val result = repository.importWorkspaceFromJson(jsonText)
            result.onSuccess { (animCount, ruleCount) ->
                _jsonModalPayload.value = null
                showToast("Imported $animCount presets & $ruleCount rules")
            }.onFailure { err ->
                showToast("Invalid JSON: ${err.message ?: "Parse error"}")
            }
        }
    }
}
