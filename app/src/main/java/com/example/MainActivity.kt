package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.GlyphFlowViewModel
import com.example.ui.GlyphScreenTab
import com.example.ui.components.GlyphMinimalTopAppBar
import com.example.ui.components.GlyphUserGuideDialog
import com.example.ui.screens.AuditLogScreen
import com.example.ui.screens.ConsoleScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.MatrixStudioScreen
import com.example.ui.screens.RuleBuilderScreen
import com.example.ui.screens.SettingsGuideScreen
import com.example.ui.theme.GlyphFlowTheme
import com.example.ui.theme.NothingBorderHairline
import com.example.ui.theme.NothingCarbonSurface
import com.example.ui.theme.NothingCrimsonRed
import com.example.ui.theme.NothingElevatedSurface
import com.example.ui.theme.NothingPureBlack
import com.example.ui.theme.NothingPureWhite
import com.example.ui.theme.NothingTechnicalGray

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlyphFlowTheme {
                GlyphFlowRootApp()
            }
        }
    }
}

@Composable
fun GlyphFlowRootApp(
    viewModel: GlyphFlowViewModel = viewModel()
) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val isRuleCreatorOpen by viewModel.isRuleCreatorOpen.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val animations by viewModel.animations.collectAsStateWithLifecycle()
    val hardwareState by viewModel.hardwareState.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val scriptCompileHistory by viewModel.scriptCompileHistory.collectAsStateWithLifecycle()
    val hardwareBinderLogs by viewModel.hardwareBinderLogs.collectAsStateWithLifecycle()
    val payloadInspectorLogs by viewModel.payloadInspectorLogs.collectAsStateWithLifecycle()
    val globalEngineEnabled by viewModel.globalEngineEnabled.collectAsStateWithLifecycle()
    val isFgsRunning by viewModel.isForegroundServiceRunning.collectAsStateWithLifecycle()
    val isNlsConnected by viewModel.isNotificationListenerConnected.collectAsStateWithLifecycle()
    val isBatteryUnrestricted by viewModel.isBatteryUnrestricted.collectAsStateWithLifecycle()
    val isSnifferActive by viewModel.isNotificationSnifferActive.collectAsStateWithLifecycle()
    val capturedNotification by viewModel.capturedSnifferNotification.collectAsStateWithLifecycle()
    val studioState by viewModel.studioState.collectAsStateWithLifecycle()
    val consoleState by viewModel.consoleState.collectAsStateWithLifecycle()
    val draftRule by viewModel.draftRule.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val jsonModalPayload by viewModel.jsonModalPayload.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemPermissionsStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showUserGuideDialog by remember { mutableStateOf(false) }
    var globalTickerText by remember { mutableStateOf<String?>(null) }
    var globalTickerKey by remember { mutableIntStateOf(0) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.toggleForegroundService(context)
    }

    BackHandler(enabled = isRuleCreatorOpen || currentTab != GlyphScreenTab.DASHBOARD) {
        if (isRuleCreatorOpen) {
            viewModel.closeRuleCreator()
        } else {
            viewModel.selectTab(GlyphScreenTab.DASHBOARD)
        }
    }

    LaunchedEffect(toastMessage, currentTab) {
        val msg = toastMessage
        if (!msg.isNullOrBlank() && currentTab != GlyphScreenTab.STUDIO) {
            globalTickerText = "[ ${msg.trim()} ]"
            globalTickerKey++
            viewModel.dismissToast()
        }
    }

    LaunchedEffect(globalTickerKey) {
        if (globalTickerText != null) {
            kotlinx.coroutines.delay(1200L)
            globalTickerText = null
        }
    }

    if (showUserGuideDialog) {
        GlyphUserGuideDialog(
            onDismiss = { showUserGuideDialog = false }
        )
    }

    if (jsonModalPayload != null) {
        JsonWorkspaceBackupDialog(
            initialJson = jsonModalPayload.orEmpty(),
            onImportJson = { payload -> viewModel.importJsonWorkspace(payload) },
            onDismiss = { viewModel.closeJsonModal() }
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(NothingPureBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .systemBarsPadding(),
        containerColor = NothingPureBlack,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (currentTab != GlyphScreenTab.STUDIO && !isRuleCreatorOpen) {
                    GlyphMinimalTopAppBar(
                        globalEngineEnabled = globalEngineEnabled,
                        isMatrixSessionActive = hardwareState.isSessionOpen || hardwareState.isSelfAuditRunning,
                        onToggleKillSwitch = { viewModel.toggleGlobalKillSwitch(it) },
                        onOpenUserGuide = { showUserGuideDialog = true }
                    )
                }
                val activeGlobalTicker = globalTickerText
                if (activeGlobalTicker != null && currentTab != GlyphScreenTab.STUDIO) {
                    Surface(
                        color = NothingCarbonSurface.copy(alpha = 0.94f),
                        border = BorderStroke(0.5.dp, NothingCrimsonRed.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .testTag("global_subheader_ticker")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = activeGlobalTicker,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = NothingPureWhite,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (!isRuleCreatorOpen) {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = NothingCarbonSurface,
                    contentColor = NothingPureWhite
                ) {
                    val navItems = listOf(
                        Triple(GlyphScreenTab.DASHBOARD, Icons.Default.AutoAwesomeMotion, "nav_tab_dashboard"),
                        Triple(GlyphScreenTab.STUDIO, Icons.Default.GridOn, "nav_tab_studio"),
                        Triple(GlyphScreenTab.CONSOLE, Icons.Default.Terminal, "nav_tab_console"),
                        Triple(GlyphScreenTab.AUDIT, Icons.Default.Analytics, "nav_tab_audit"),
                        Triple(GlyphScreenTab.SETTINGS, Icons.Default.Tune, "nav_tab_settings")
                    )
                    navItems.forEach { (tab, icon, tag) ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = tab.title
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.2.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.testTag(tag),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NothingPureWhite,
                                selectedTextColor = NothingCrimsonRed,
                                indicatorColor = NothingCrimsonRed.copy(alpha = 0.24f),
                                unselectedIconColor = NothingTechnicalGray,
                                unselectedTextColor = NothingTechnicalGray
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isRuleCreatorOpen) {
                RuleBuilderScreen(
                    draftRule = draftRule,
                    animations = animations,
                    installedApps = installedApps,
                    onUpdateDraft = { viewModel.updateDraftRule(it) },
                    onSaveRule = { viewModel.saveDraftRule() },
                    onTestDraftRule = { viewModel.testFireRule(it) },
                    onDrawOnTheFly = { viewModel.openStudioFromRuleCreator() },
                    onClose = { viewModel.closeRuleCreator() }
                )
            } else {
                when (currentTab) {
                    GlyphScreenTab.DASHBOARD -> {
                        DashboardScreen(
                            hardwareState = hardwareState,
                            globalEngineEnabled = globalEngineEnabled,
                            rules = rules,
                            animations = animations,
                            isNotificationSnifferActive = isSnifferActive,
                            capturedSnifferNotification = capturedNotification,
                            onToggleNotificationSniffer = { viewModel.toggleNotificationSniffer(it) },
                            onClearCapturedSnifferNotification = { viewModel.clearCapturedSnifferNotification() },
                            onGenerateLlmPrompt = { cap ->
                                val prompt = """RULE "Captured ${cap.appLabel} Rule"
WHEN notification FROM "${cap.packageName}"
EXTRACT {
  var title_val = title.regex(".*") DEFAULT "${cap.rawExtraTitle}"
  var body_val = body.regex(".*") DEFAULT "${cap.rawExtraText}"
}
DISPLAY {
  LAYER top_text {
    text(content: title_val, align: "center", y: 8)
  }
  LAYER bottom_text {
    text(content: body_val, align: "center", y: 17)
  }
}
BEHAVIOR {
  priority: 8
  until: notification.dismissed
}"""
                                viewModel.updateConsoleScriptInput(prompt)
                                viewModel.selectTab(GlyphScreenTab.CONSOLE)
                            },
                            onStopActiveSession = { viewModel.stopActiveMatrixSession() },
                            onVerifyPhysicalToggle = { viewModel.verifyPhysicalToggle() },
                            onToggleRule = { id, enabled -> viewModel.toggleRuleEnabled(id, enabled) },
                            onTestRule = { rule -> viewModel.testFireRule(rule) },
                            onEditRule = { rule -> viewModel.editExistingRule(rule) },
                            onEditInBuilder = { viewModel.editRuleInStudio(it) },
                            onDeleteRule = { id -> viewModel.deleteRule(id) },
                            onCreateNewRule = { viewModel.startNewRuleDraft() }
                        )
                    }

                    GlyphScreenTab.STUDIO -> {
                        MatrixStudioScreen(
                            studioState = studioState,
                            savedAnimations = animations,
                            onCellInteract = { r, c, drag -> viewModel.onCanvasCellInteraction(r, c, drag) },
                            onBrushBrightnessChange = { viewModel.setStudioBrushBrightness(it) },
                            onTapToggleChange = { viewModel.setTapTogglesPixel(it) },
                            onToggleLiveMirror = { viewModel.toggleStudioLiveMirror(it) },
                            onToggleBezelOverlay = { viewModel.toggleStudioBezelOverlay(it) },
                            onUpdateAiPrompt = { viewModel.updateAiPromptInput(it) },
                            onGenerateAiPrompt = { viewModel.generateFromAiPrompt(it) },
                            onImportPhotoUri = { viewModel.importImageUriToGlyph(it) },
                            onLoadSamplePhoto = { viewModel.loadSamplePhotoToGlyph() },
                            onUpdatePhotoSettings = { contrast, dither, invert ->
                                viewModel.updatePhotoConverterSettings(contrast, dither, invert)
                            },
                            onToggleSequencerDrawer = { viewModel.toggleSequencerDrawer(it) },
                            onApplyTransform = { viewModel.applyMatrixTransform(it) },
                            onModifyRow = { r, fill -> viewModel.modifyRow(r, fill) },
                            onModifyColumn = { c, fill -> viewModel.modifyColumn(c, fill) },
                            onStampPattern = { viewModel.stampProceduralPattern(it) },
                            onUndo = { viewModel.undoStudioEdit() },
                            onSelectFrame = { viewModel.selectTimelineFrame(it) },
                            onAddFrame = { viewModel.addBlankFrame() },
                            onDuplicateFrame = { viewModel.duplicateActiveFrame() },
                            onDeleteFrame = { viewModel.deleteActiveFrame() },
                            onMoveFrame = { viewModel.moveActiveFrame(it) },
                            onReorderFrame = { fromIdx, toIdx -> viewModel.reorderTimelineFrame(fromIdx, toIdx) },
                            onToggleFrameMultiSelectMode = { viewModel.toggleFrameMultiSelectMode(it) },
                            onToggleFrameCheckedForBatch = { viewModel.toggleFrameCheckedForBatch(it) },
                            onClearFrameBatchSelection = { viewModel.clearFrameBatchSelection() },
                            onDuplicateSelectedFrames = { viewModel.duplicateSelectedFrames() },
                            onDeleteSelectedFrames = { viewModel.deleteSelectedFrames() },
                            onReverseSelectedFrames = { viewModel.reverseSelectedFrames() },
                            onUpdateSelectedFramesDuration = { viewModel.updateSelectedFramesDuration(it) },
                            onFrameDurationChange = { viewModel.updateActiveFrameDuration(it) },
                            onLoopCountChange = { viewModel.updateStudioLoopCount(it) },
                            onTransitionCurveChange = { viewModel.updateStudioTransitionCurve(it) },
                            onTogglePreview = { viewModel.toggleStudioPreview() },
                            onNameChange = { viewModel.updateStudioName(it) },
                            onTagsChange = { viewModel.updateStudioTags(it) },
                            onSaveAnimation = { viewModel.saveStudioAnimationToDatabase() },
                            onNewBlankAnimation = { viewModel.createNewBlankStudioAnimation() },
                            onLoadAnimation = { viewModel.loadAnimationIntoStudio(it, navigateToStudio = false) },
                            onNudgeClampModeChange = { viewModel.setNudgeClampMode(it) },
                            onActiveDockTabChange = { viewModel.setActiveDockTab(it) },
                            onActiveDrawToolChange = { viewModel.setActiveDrawTool(it) },
                            onGlobalAlignChange = { viewModel.setGlobalAlign(it) },
                            onNudge = { dx, dy -> viewModel.nudgeStudioCanvas(dx, dy) },
                            onSaveAndApplyRule = { viewModel.saveAndApplyDecompiledRule() },
                            onToggleLayerVisibility = { viewModel.toggleVisualLayerVisibility(it) },
                            onSelectLayer = { viewModel.selectVisualLayer(it) },
                            onToggleFullPreviewComposite = { viewModel.toggleShowFullPreviewComposite() },
                            onSetDotExplicit = { row, col, addDot -> viewModel.setCanvasDotExplicit(row, col, addDot) },
                            onRemoveLayer = { viewModel.removeVisualLayer(it) },
                            onMoveLayer = { id, up -> viewModel.moveVisualLayer(id, up) },
                            onUpdateLayer = { id, transform -> viewModel.updateVisualLayer(id, transform) },
                            onAddVisualLayer = { viewModel.addVisualLayer(it) },
                            onSetSelectionRegion = { sR, sC, eR, eC -> viewModel.setSelectionRegion(sR, sC, eR, eC) },
                            onClearSelectionRegion = { viewModel.clearSelectionRegion() },
                            onCopySelectedRegion = { viewModel.copySelectedRegion() },
                            onCutSelectedRegion = { viewModel.cutSelectedRegion() },
                            onPasteClipboardRegion = { tR, tC -> viewModel.pasteClipboardRegion(tR, tC) },
                            onNudgeSelectedRegion = { dx, dy -> viewModel.nudgeSelectedRegion(dx, dy) },
                            onFlipSelectedRegion = { horiz -> viewModel.flipSelectedRegion(horiz) },
                            onRotateSelectedRegion90 = { viewModel.rotateSelectedRegion90() },
                            onAddRowGuide = { viewModel.addRowGuide(it) },
                            onAddColGuide = { viewModel.addColGuide(it) },
                            onMoveRowGuide = { fromR, toR -> viewModel.moveRowGuide(fromR, toR) },
                            onMoveColGuide = { fromC, toC -> viewModel.moveColGuide(fromC, toC) },
                            onSelectActiveGuide = { rG, cG -> viewModel.selectActiveGuide(rG, cG) },
                            onRemoveRowGuide = { viewModel.removeRowGuide(it) },
                            onRemoveColGuide = { viewModel.removeColGuide(it) },
                            onClearAllGuides = { viewModel.clearAllGuides() },
                            onSetAxisLockEnabled = { viewModel.setAxisLockEnabled(it) },
                            onSetPocketFillMode = { viewModel.setPocketFillMode(it) },
                            onFillPocketAt = { r, c -> viewModel.fillPocketAt(r, c) },
                            onDrawLine = { r0, c0, r1, c1 -> viewModel.drawLineOnCanvas(r0, c0, r1, c1) },
                            onDrawCircleOutline = { cR, cC, eR, eC ->
                                val rad = kotlin.math.hypot((eR - cR).toDouble(), (eC - cC).toDouble()).toInt().coerceAtLeast(1)
                                viewModel.drawCircleOutlineOnCanvas(cR, cC, rad)
                            },
                            onFloodFill = { sR, sC -> viewModel.floodFillCanvas(sR, sC) },
                            onInvert489Leds = { viewModel.invert489Leds() },
                            onRedo = { viewModel.redoStudioEdit() },
                            onPlaybackModeChange = { viewModel.setStudioPlaybackMode(it) },
                            onRotateSelectionByAngle = { viewModel.rotateSelectedRegionByAngle(it) },
                            onSetSelectionRotationAngle = { viewModel.setSelectionRotationAngle(it) },
                            onResetSelectionRotation = { viewModel.resetSelectionRotation() },
                            onApplyProceduralAnimationPreset = { viewModel.applyProceduralAnimationPreset(it) },
                            onToggleLayerBitmapMode = { viewModel.toggleLayerBitmapMode(it) },
                            onReorderLayer = { fromIdx, toIdx -> viewModel.reorderVisualLayer(fromIdx, toIdx) },
                            onToggleLayerMultiSelectMode = { viewModel.toggleLayerMultiSelectMode(it) },
                            onToggleLayerCheckedForBatch = { viewModel.toggleLayerCheckedForBatch(it) },
                            onDuplicateSelectedLayers = { viewModel.duplicateSelectedLayers() },
                            onDeleteSelectedLayers = { viewModel.deleteSelectedLayers() },
                            onMergeSelectedLayers = { viewModel.mergeSelectedLayers() },
                            onGroupOrMoveSelectedLayers = { viewModel.groupOrMoveSelectedLayers(it) },
                            onUpdateEmojiInput = { viewModel.updateEmojiInput(it) },
                            onRasterizeEmoji = { viewModel.rasterizeEmojiToStudioCanvas(it) },
                            onUpdatePhotoThresholdPct = { viewModel.updatePhotoThresholdPct(it) },
                            onTogglePhotoEdgeDetect = { viewModel.togglePhotoEdgeDetect(it) },
                            onSetCursorAutoStepEnabled = { viewModel.setCursorAutoStepEnabled(it) },
                            onClearActiveFrame = { viewModel.clearCurrentFrame() },
                            onAdjustRelativePwm = { delta, sel -> viewModel.adjustRelativePwm(delta, sel) },
                            onSetLightingTargetSelectionOnly = { viewModel.setLightingTargetSelectionOnly(it) },
                            onInitScaleSession = { viewModel.initScaleSession() },
                            onSetScalePercent = { viewModel.setScalePercent(it) },
                            onStepScalePercent = { viewModel.stepScalePercent(it) },
                            onToggleScaleAspectLock = { viewModel.toggleScaleAspectLock(it) },
                            onFitScaleToGuides = { viewModel.fitScaleToGuides() },
                            onUpdateScaleBoundingBoxDrag = { idx, minR, minC, maxR, maxC ->
                                viewModel.updateScaleBoundingBoxDrag(idx, minR, minC, maxR, maxC)
                            },
                            onCommitScale = { viewModel.commitScale() },
                            onCancelScale = { viewModel.cancelScale() },
                            toastMessage = toastMessage,
                            onDismissToast = { viewModel.dismissToast() },
                            onCloseStudio = { viewModel.discardStudioEditsAndExit() }
                        )
                    }

                    GlyphScreenTab.CONSOLE -> {
                        ConsoleScreen(
                            consoleState = consoleState,
                            existingRules = rules,
                            onUpdateScriptInput = { viewModel.updateConsoleScriptInput(it) },
                            onPasteScript = { viewModel.pasteIntoConsoleScript(it) },
                            onClearScript = { viewModel.clearConsoleScript() },
                            onUndoClearScript = { viewModel.undoClearConsoleScript() },
                            onLoadTemplatePreset = { viewModel.loadConsoleTemplatePreset(it) },
                            onExportScratchpadBitmap = { viewModel.appendScratchpadBitmapToConsole(it) },
                            onUpdateDryRunInputs = { title, body, prog ->
                                viewModel.updateConsoleDryRunInputs(title, body, prog)
                            },
                            onUpdateDryRunVariable = { varName, newValue ->
                                viewModel.updateConsoleDryRunVariable(varName, newValue)
                            },
                            onDryRunScript = { viewModel.dryRunConsoleScript() },
                            onImportAndApplyScript = { viewModel.importAndApplyConsoleScript() },
                            onLoadRuleIntoConsole = { viewModel.loadRuleIntoConsole(it) },
                            onSystemPromptCopied = { viewModel.notifySystemPromptCopied() },
                            onEditDryRunInStudio = { viewModel.openStudioFromConsoleDryRun() }
                        )
                    }

                    GlyphScreenTab.AUDIT -> {
                        AuditLogScreen(
                            hardwareState = hardwareState,
                            auditLogs = auditLogs,
                            scriptCompileHistory = scriptCompileHistory,
                            hardwareBinderLogs = hardwareBinderLogs,
                            payloadInspectorLogs = payloadInspectorLogs,
                            onClearLogs = { viewModel.clearAuditLogs() },
                            onExportDiagnosticsCopied = { viewModel.notifyDiagnosticsExported() },
                            onSimulateNotification = { pkg, label, title, content, prog ->
                                viewModel.simulateNotificationSignal(
                                    packageName = pkg,
                                    appLabel = label,
                                    title = title,
                                    content = content,
                                    progress = prog
                                )
                            },
                            onSimulateBattery = { level, charging, isChargeEvt ->
                                viewModel.simulateBatterySignal(level, charging, isChargeEvt)
                            },
                            onSimulateMedia = { state, track ->
                                viewModel.simulateMediaSignal(state, track)
                            },
                            onSimulatePosture = { cond ->
                                viewModel.simulateSystemPostureSignal(cond)
                            }
                        )
                    }

                    GlyphScreenTab.SETTINGS -> {
                        SettingsGuideScreen(
                            hardwareState = hardwareState,
                            isNotificationListenerConnected = isNlsConnected,
                            isBatteryUnrestricted = isBatteryUnrestricted,
                            isForegroundServiceRunning = isFgsRunning,
                            auditLogs = auditLogs,
                            onRunHardwareSelfTest = { viewModel.runHardwareSelfAudit() },
                            onVerifyPhysicalToggle = { viewModel.verifyPhysicalToggle() },
                            onStopMatrixSession = { viewModel.stopActiveMatrixSession() },
                            onOpenNotificationAccess = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                }
                                viewModel.refreshSystemPermissionsStatus()
                            },
                            onOpenBatterySettings = {
                                runCatching {
                                    val intent = Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }.onFailure {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    }
                                }
                                viewModel.refreshSystemPermissionsStatus()
                            },
                            onToggleForegroundService = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.toggleForegroundService(context)
                                }
                            },
                            onOpenJsonBackupModal = { viewModel.openJsonExportModal() },
                            onClearAuditLogs = { viewModel.clearAuditLogs() },
                            onSimulateNotification = { pkg, label, title, content, prog ->
                                viewModel.simulateNotificationSignal(
                                    packageName = pkg,
                                    appLabel = label,
                                    title = title,
                                    content = content,
                                    progress = prog
                                )
                            },
                            onSimulateMedia = { state, track ->
                                viewModel.simulateMediaSignal(state, track)
                            },
                            onSimulateBattery = { level, charging, isChargeEvt ->
                                viewModel.simulateBatterySignal(level, charging, isChargeEvt)
                            },
                            onSimulatePosture = { cond ->
                                viewModel.simulateSystemPostureSignal(cond)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JsonWorkspaceBackupDialog(
    initialJson: String,
    onImportJson: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var editableJson by remember(initialJson) { mutableStateOf(initialJson) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NothingCarbonSurface,
        titleContentColor = NothingPureWhite,
        textContentColor = NothingPureWhite,
        title = {
            Text(
                text = "OFFLINE JSON EXPORT / IMPORT",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                Text(
                    text = "Copy this JSON bundle to back up your 25×25 animations and rules offline, or paste a GlyphFlow JSON payload below to import.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingTechnicalGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editableJson,
                    onValueChange = { editableJson = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .testTag("json_backup_textfield"),
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NothingCrimsonRed,
                        unfocusedBorderColor = NothingBorderHairline,
                        focusedTextColor = NothingPureWhite,
                        unfocusedTextColor = NothingPureWhite
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImportJson(editableJson) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingCrimsonRed,
                    contentColor = NothingPureWhite
                ),
                modifier = Modifier.testTag("json_import_confirm_button")
            ) {
                Text("IMPORT JSON", style = MaterialTheme.typography.labelMedium)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("GlyphFlow Backup", editableJson))
                    onDismiss()
                }
            ) {
                Text("COPY & CLOSE", color = NothingPureWhite, style = MaterialTheme.typography.labelMedium)
            }
        }
    )
}
