package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Gradient
import com.example.ui.RampGradientType
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.GlyphAnimation
import com.example.domain.model.GlyphFrame
import com.example.domain.model.MATRIX_CELL_COUNT
import com.example.domain.model.MATRIX_SIZE
import com.example.domain.model.MatrixTransform
import com.example.domain.model.TransitionCurve
import com.example.dsl.DecompiledVisualLayer
import com.example.dsl.LayerAnimationMacro
import com.example.hardware.GlyphAperture
import com.example.hardware.GlyphHardwareController
import com.example.ui.AnimationPlaybackMode
import com.example.ui.NudgeClampMode
import com.example.ui.StudioDockTab
import com.example.ui.StudioDrawTool
import com.example.ui.StudioEditorState
import com.example.ui.components.CircularGlyphMatrixPreview
import com.example.ui.components.MiniMatrixThumbnail
import com.example.ui.components.NothingCompactPill
import com.example.ui.components.NothingPillBadge
import com.example.ui.components.snapCoordinateToPhysicalAperture
import com.example.ui.theme.NothingBorderHairline
import com.example.ui.theme.NothingCarbonSurface
import com.example.ui.theme.NothingCrimsonRed
import com.example.ui.theme.NothingElevatedSurface
import com.example.ui.theme.NothingMatrixGreen
import com.example.ui.theme.NothingPureBlack
import com.example.ui.theme.NothingPureWhite
import com.example.ui.theme.NothingTechnicalGray
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign

enum class SymmetryMode(val label: String) {
    NONE("OFF"),
    HORIZONTAL("H-MIRROR"),
    VERTICAL("V-MIRROR"),
    QUAD("QUAD")
}

enum class BrushSizeMode(val label: String, val offsets: List<Pair<Int, Int>>) {
    SINGLE("1PX", listOf(0 to 0)),
    PLUS("PLUS+", listOf(0 to 0, -1 to 0, 1 to 0, 0 to -1, 0 to 1)),
    SQUARE_3X3(
        "3×3",
        listOf(
            -1 to -1, -1 to 0, -1 to 1,
            0 to -1, 0 to 0, 0 to 1,
            1 to -1, 1 to 0, 1 to 1
        )
    )
}

fun generateGlyphScriptCode(frames: List<GlyphFrame>, ruleName: String): String {
    fun frameToAscii(pixels: ByteArray): String {
        val sb = StringBuilder()
        for (r in 0 until MATRIX_SIZE) {
            for (c in 0 until MATRIX_SIZE) {
                if (!GlyphHardwareController.isCellInsideCircle(r, c)) {
                    sb.append('.')
                } else {
                    val b = pixels[r * MATRIX_SIZE + c].toInt() and 0xFF
                    when {
                        b == 0 -> sb.append('.')
                        b >= 200 -> sb.append('#')
                        b >= 100 -> sb.append('*')
                        else -> sb.append(':')
                    }
                }
            }
            if (r < MATRIX_SIZE - 1) sb.append("\n")
        }
        return sb.toString()
    }

    val intervalMs = frames.firstOrNull()?.durationMs ?: 120L
    return if (frames.size <= 1) {
        val ascii = frameToAscii(frames.firstOrNull()?.pixels ?: ByteArray(MATRIX_CELL_COUNT))
        """RULE "$ruleName"
WHEN notification FROM "*"
DISPLAY {
  LAYER studio_canvas {
    bitmap(align: "center") ${"\"\"\""}
$ascii
${"\"\"\""}
  }
}
BEHAVIOR {
  priority: 8
  until: notification.dismissed
  fallback_clear: true
}"""
    } else {
        val framesAscii = frames.joinToString("\n") { f ->
            val ascii = frameToAscii(f.pixels)
            """      frame ${"\"\"\""}
$ascii
${"\"\"\""}"""
        }
        """RULE "$ruleName"
WHEN notification FROM "*"
DISPLAY {
  LAYER studio_anim {
    sequence(interval: ${intervalMs}ms, loop: true, align: "center") {
$framesAscii
    }
  }
}
BEHAVIOR {
  priority: 8
  until: notification.dismissed
  fallback_clear: true
}"""
    }
}

private val SPEED_PRESETS_MS = listOf(66L, 110L, 165L, 330L)

enum class SpeedPresetOption(val durationMs: Long, val title: String, val badge: String) {
    FAST(66L, "Fast (66ms / ~15 FPS)", "Fast 66ms"),
    STANDARD(110L, "Standard (110ms)", "Std 110ms"),
    SMOOTH(165L, "Smooth (165ms)", "Smooth 165ms"),
    SLOW(330L, "Slow (330ms)", "Slow 330ms")
}

/**
 * Axis-snapping helper for the Two-Stage Interactive Line Tool:
 * Automatically locks to horizontal (Y locked), vertical (X locked), or 45-degree diagonal
 * when the user drags near those cardinal/diagonal axes.
 */
private fun snapLineEndpointToAxis(
    startRow: Int,
    startCol: Int,
    endRow: Int,
    endCol: Int
): Pair<Int, Int> {
    val dr = endRow - startRow
    val dc = endCol - startCol
    val absR = abs(dr)
    val absC = abs(dc)
    if (absR == 0 && absC == 0) return endRow to endCol

    return when {
        // Horizontal lock (within 2 rows when dragging horizontally)
        absR <= 2 && absC >= 2 -> startRow to endCol.coerceIn(0, MATRIX_SIZE - 1)
        // Vertical lock (within 2 cols when dragging vertically)
        absC <= 2 && absR >= 2 -> endRow.coerceIn(0, MATRIX_SIZE - 1) to startCol
        // 45-degree diagonal lock
        abs(absR - absC) <= 2 && maxOf(absR, absC) >= 2 -> {
            val step = ((absR + absC) / 2).coerceAtLeast(1)
            val snappedR = (startRow + step * dr.sign).coerceIn(0, MATRIX_SIZE - 1)
            val snappedC = (startCol + step * dc.sign).coerceIn(0, MATRIX_SIZE - 1)
            snappedR to snappedC
        }
        else -> endRow.coerceIn(0, MATRIX_SIZE - 1) to endCol.coerceIn(0, MATRIX_SIZE - 1)
    }
}

private fun computeLinePreviewPixels(
    startRow: Int,
    startCol: Int,
    endRow: Int,
    endCol: Int,
    brightness: Int
): ByteArray {
    val dst = ByteArray(MATRIX_CELL_COUNT)
    val b = brightness.coerceIn(64, 255).toByte()
    var x0 = startCol.coerceIn(0, MATRIX_SIZE - 1)
    var y0 = startRow.coerceIn(0, MATRIX_SIZE - 1)
    val x1 = endCol.coerceIn(0, MATRIX_SIZE - 1)
    val y1 = endRow.coerceIn(0, MATRIX_SIZE - 1)

    val dx = abs(x1 - x0)
    val sx = if (x0 < x1) 1 else -1
    val dy = -abs(y1 - y0)
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
    return dst
}

private fun computeCirclePreviewPixels(
    centerRow: Int,
    centerCol: Int,
    radius: Int,
    brightness: Int
): ByteArray {
    val dst = ByteArray(MATRIX_CELL_COUNT)
    val rClamped = radius.coerceIn(1, 12)
    val b = brightness.coerceIn(64, 255).toByte()
    for (deg in 0 until 360 step 6) {
        val rad = Math.toRadians(deg.toDouble())
        val rr = (centerRow + rClamped * kotlin.math.sin(rad)).roundToInt()
        val cc = (centerCol + rClamped * kotlin.math.cos(rad)).roundToInt()
        if (rr in 0 until MATRIX_SIZE && cc in 0 until MATRIX_SIZE && GlyphAperture.isPhysicalLed(cc, rr)) {
            dst[rr * MATRIX_SIZE + cc] = b
        }
    }
    return dst
}

/**
 * Fixed-Viewport (Zero Vertical Scrolling) Matrix Studio Workstation:
 * 1) Slim Header Strip (~28-32dp): [● LIVE] Rule Name • 489 LEDs | [Code] [SAVE] [✕]
 * 2) Maximized Circular 489-LED Canvas (weight(1f)):
 *    - 1.0x..4.0x Pinch-to-Zoom & 2-Finger Pan
 *    - Floating Pill: [1.0x Reset] [X:12 Y:12]
 *    - Interactive Dashed Cyan (Row) & Orange (Col) Guidelines with Edge Drag Handles
 *    - Dashed Marquee Selection Box
 * 3) Single-Line Animation Timeline Strip: [F1] [F2] ... [+] [Dup] [Trash] [Onion 20%] ▶ [100ms]
 * 4) Tier 2 Contextual Shelf (Swaps cleanly per selected category: DRAW, SELECT, GUIDES, TRANSFORM, LAYERS)
 * 5) Tier 1 Primary Category Selector Dock: [DRAW] [SELECT] [GUIDES] [TRANSFORM] [LAYERS]
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MatrixStudioScreen(
    studioState: StudioEditorState,
    savedAnimations: List<GlyphAnimation>,
    onCellInteract: (row: Int, col: Int, isDrag: Boolean) -> Unit,
    onBrushBrightnessChange: (Int) -> Unit,
    onTapToggleChange: (Boolean) -> Unit,
    onToggleLiveMirror: (Boolean) -> Unit,
    onToggleBezelOverlay: (Boolean) -> Unit,
    onUpdateAiPrompt: (String) -> Unit,
    onGenerateAiPrompt: (String?) -> Unit,
    onImportPhotoUri: (Uri) -> Unit,
    onLoadSamplePhoto: () -> Unit,
    onUpdatePhotoSettings: (contrast: Float, dither: Boolean, invert: Boolean) -> Unit,
    onToggleSequencerDrawer: (Boolean?) -> Unit,
    onApplyTransform: (MatrixTransform) -> Unit,
    onModifyRow: (row: Int, fill: Boolean) -> Unit = { _, _ -> },
    onModifyColumn: (col: Int, fill: Boolean) -> Unit = { _, _ -> },
    onStampPattern: (String) -> Unit,
    onUndo: () -> Unit,
    onSelectFrame: (Int) -> Unit,
    onAddFrame: () -> Unit,
    onDuplicateFrame: () -> Unit,
    onDeleteFrame: () -> Unit,
    onMoveFrame: (Int) -> Unit,
    onReorderFrame: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onToggleFrameMultiSelectMode: (Boolean?) -> Unit = {},
    onToggleFrameCheckedForBatch: (Int) -> Unit = {},
    onClearFrameBatchSelection: () -> Unit = {},
    onDuplicateSelectedFrames: () -> Unit = {},
    onDeleteSelectedFrames: () -> Unit = {},
    onReverseSelectedFrames: () -> Unit = {},
    onUpdateSelectedFramesDuration: (Long) -> Unit = {},
    onFrameDurationChange: (Long) -> Unit,
    onLoopCountChange: (Int) -> Unit,
    onTransitionCurveChange: (TransitionCurve) -> Unit,
    onTogglePreview: () -> Unit,
    onNameChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onSaveAnimation: () -> Unit,
    onNewBlankAnimation: () -> Unit,
    onLoadAnimation: (GlyphAnimation) -> Unit,
    onNudgeClampModeChange: (NudgeClampMode) -> Unit = {},
    onActiveDockTabChange: (StudioDockTab) -> Unit = {},
    onActiveDrawToolChange: (StudioDrawTool) -> Unit = {},
    onGlobalAlignChange: (String) -> Unit = {},
    onNudge: (dx: Int, dy: Int) -> Unit = { _, _ -> },
    onSaveAndApplyRule: () -> Unit = {},
    onSelectLayer: (String) -> Unit = {},
    onToggleFullPreviewComposite: () -> Unit = {},
    onSetDotExplicit: (row: Int, col: Int, addDot: Boolean) -> Unit = { _, _, _ -> },
    onToggleLayerVisibility: (String) -> Unit = {},
    onRemoveLayer: (String) -> Unit = {},
    onMoveLayer: (layerId: String, moveUp: Boolean) -> Unit = { _, _ -> },
    onUpdateLayer: (layerId: String, transform: (DecompiledVisualLayer) -> DecompiledVisualLayer) -> Unit = { _, _ -> },
    onAddVisualLayer: (DecompiledVisualLayer) -> Unit = {},
    // Selection & Clipboard callbacks
    onSetSelectionRegion: (startRow: Int, startCol: Int, endRow: Int, endCol: Int) -> Unit = { _, _, _, _ -> },
    onClearSelectionRegion: () -> Unit = {},
    onCopySelectedRegion: () -> Unit = {},
    onCutSelectedRegion: () -> Unit = {},
    onPasteClipboardRegion: (targetRow: Int, targetCol: Int) -> Unit = { _, _ -> },
    onNudgeSelectedRegion: (dx: Int, dy: Int) -> Unit = { _, _ -> },
    onFlipSelectedRegion: (horizontal: Boolean) -> Unit = {},
    onRotateSelectedRegion90: () -> Unit = {},
    // Guidelines & Pocket Fill callbacks
    onAddRowGuide: (Int) -> Unit = {},
    onAddColGuide: (Int) -> Unit = {},
    onMoveRowGuide: (fromRow: Int, toRow: Int) -> Unit = { _, _ -> },
    onMoveColGuide: (fromCol: Int, toCol: Int) -> Unit = { _, _ -> },
    onSelectActiveGuide: (rowGuide: Int?, colGuide: Int?) -> Unit = { _, _ -> },
    onRemoveRowGuide: (Int) -> Unit = {},
    onRemoveColGuide: (Int) -> Unit = {},
    onClearAllGuides: () -> Unit = {},
    onSetAxisLockEnabled: (Boolean) -> Unit = {},
    onSetPocketFillMode: (Boolean) -> Unit = {},
    onFillPocketAt: (row: Int, col: Int) -> Unit = { _, _ -> },
    // Shape & Flood Fill callbacks
    onDrawLine: (r0: Int, c0: Int, r1: Int, c1: Int) -> Unit = { _, _, _, _ -> },
    onDrawCircleOutline: (centerR: Int, centerC: Int, edgeR: Int, edgeC: Int) -> Unit = { _, _, _, _ -> },
    onFloodFill: (startR: Int, startC: Int) -> Unit = { _, _ -> },
    onInvert489Leds: () -> Unit = {},
    onRedo: () -> Unit = {},
    onPlaybackModeChange: (AnimationPlaybackMode) -> Unit = {},
    onRotateSelectionByAngle: (Float) -> Unit = {},
    onSetSelectionRotationAngle: (Float) -> Unit = {},
    onResetSelectionRotation: () -> Unit = {},
    onApplyProceduralAnimationPreset: (String) -> Unit = {},
    onToggleLayerBitmapMode: (String) -> Unit = {},
    onReorderLayer: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onToggleLayerMultiSelectMode: (Boolean?) -> Unit = {},
    onToggleLayerCheckedForBatch: (String) -> Unit = {},
    onDuplicateSelectedLayers: () -> Unit = {},
    onDeleteSelectedLayers: () -> Unit = {},
    onMergeSelectedLayers: () -> Unit = {},
    onGroupOrMoveSelectedLayers: (Boolean) -> Unit = {},
    onUpdateEmojiInput: (String) -> Unit = {},
    onRasterizeEmoji: (String?) -> Unit = {},
    onUpdatePhotoThresholdPct: (Int) -> Unit = {},
    onTogglePhotoEdgeDetect: (Boolean?) -> Unit = {},
    onSetCursorAutoStepEnabled: (Boolean?) -> Unit = {},
    onClearActiveFrame: () -> Unit = {},
    onAdjustRelativePwm: (deltaPercent: Int, selectionOnly: Boolean?) -> Unit = { _, _ -> },
    onSetLightingTargetSelectionOnly: (Boolean) -> Unit = {},
    onStageRelativePwmPreview: (deltaPercent: Int, selectionOnly: Boolean?) -> Unit = { _, _ -> },
    onStageAbsolutePwmPreview: (targetBrightness: Int, selectionOnly: Boolean?) -> Unit = { _, _ -> },
    onCommitStagingPreviewFrame: () -> Unit = {},
    onCancelStagingPreviewFrame: () -> Unit = {},
    onTogglePwmHeatmapOverlay: (Boolean?) -> Unit = {},
    onToggleLuminanceSampler: (Boolean?) -> Unit = {},
    onSampleLuminanceAt: (row: Int, col: Int) -> Int = { _, _ -> 255 },
    onApplyRampGradient: (RampGradientType, Boolean?) -> Unit = { _, _ -> },
    onClearLayerBatchSelection: () -> Unit = {},
    onInitScaleSession: () -> Unit = {},
    onSetScalePercent: (Int) -> Unit = {},
    onStepScalePercent: (Int) -> Unit = {},
    onToggleScaleAspectLock: (Boolean?) -> Unit = {},
    onFitScaleToGuides: () -> Unit = {},
    onUpdateScaleBoundingBoxDrag: (handleIndex: Int, newMinR: Float, newMinC: Float, newMaxR: Float, newMaxC: Float) -> Unit = { _, _, _, _, _ -> },
    onCommitScale: () -> Unit = {},
    onCancelScale: () -> Unit = {},
    toastMessage: String? = null,
    onDismissToast: () -> Unit = {},
    onCloseStudio: () -> Unit = onNewBlankAnimation,
    modifier: Modifier = Modifier
) {
    var symmetryMode by remember { mutableStateOf(SymmetryMode.NONE) }
    var onionSkinningEnabled by remember { mutableStateOf(false) }
    var onionOpacityPct by remember { mutableIntStateOf(20) }
    var brushSizeMode by remember { mutableStateOf(BrushSizeMode.SINGLE) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    var showFrameBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showFrameBatchSpeedStepper by remember { mutableStateOf(false) }

    // Permanent Top Status HUD Ticker State (fixed 26dp height, non-shifting, auto-fades after 1500ms)
    var studioTickerMessage by remember { mutableStateOf("") }
    var studioTickerVisible by remember { mutableStateOf(false) }
    var studioTickerKey by remember { mutableIntStateOf(0) }
    val pushStudioTicker: (String) -> Unit = { message ->
        studioTickerMessage = message
        studioTickerVisible = true
        studioTickerKey++
    }

    LaunchedEffect(toastMessage) {
        if (!toastMessage.isNullOrBlank()) {
            studioTickerMessage = "[ ${toastMessage.trim()} ]"
            studioTickerVisible = true
            studioTickerKey++
            onDismissToast()
        }
    }

    LaunchedEffect(studioTickerKey) {
        if (studioTickerVisible) {
            kotlinx.coroutines.delay(1500L)
            studioTickerVisible = false
        }
    }

    val tickerTextAlpha by animateFloatAsState(
        targetValue = if (studioTickerVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "studio_hud_ticker_alpha"
    )

    // Continuous Pinch-to-Zoom (1.0f..4.0f) & 2-Finger Pan Offset State
    var zoomScaleFloat by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    var cursorRow by remember { mutableIntStateOf(12) }
    var cursorCol by remember { mutableIntStateOf(12) }

    // Two-Stage Interactive Shape Tool Live Preview State
    var shapePreviewStart by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var shapePreviewEnd by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val clipboardManager = LocalClipboardManager.current
    val hapticFeedback = LocalHapticFeedback.current
    var draggedFrameId by remember { mutableStateOf<String?>(null) }
    var draggedFrameOffsetX by remember { mutableFloatStateOf(0f) }

    val activeFrame = studioState.activeFrame
    val displayedPixels = studioState.stagingPreviewFrame ?: studioState.previewPixels ?: activeFrame.pixels
    val activeLayer = studioState.selectedVisualLayer

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportPhotoUri(uri)
        }
    }

    // Plots a point (with active symmetry + brush thickness) onto the 489-LED circular matrix
    val plotPointWithBrushAndSymmetry: (Int, Int, Boolean, Boolean) -> Unit = { r, c, isDrag, forceErase ->
        val (lockedR, lockedC) = if (studioState.axisLockEnabled) {
            val rG = studioState.activeRowGuide
            val cG = studioState.activeColGuide
            when {
                rG != null && cG != null -> {
                    val distR = kotlin.math.abs(r - rG)
                    val distC = kotlin.math.abs(c - cG)
                    if (distR < distC) rG to c
                    else if (distC < distR) r to cG
                    else rG to cG
                }
                rG != null -> rG to c
                cG != null -> r to cG
                else -> r to c
            }
        } else {
            r to c
        }

        val origBrightness = studioState.brushBrightness
        if (forceErase) {
            onBrushBrightnessChange(0)
        }
        val stampedOffsets = brushSizeMode.offsets
        val candidatePoints = mutableListOf<Pair<Int, Int>>()
        for ((dr, dc) in stampedOffsets) {
            val br = (lockedR + dr).coerceIn(0, MATRIX_SIZE - 1)
            val bc = (lockedC + dc).coerceIn(0, MATRIX_SIZE - 1)
            when (symmetryMode) {
                SymmetryMode.NONE -> candidatePoints.add(br to bc)
                SymmetryMode.HORIZONTAL -> {
                    candidatePoints.add(br to bc)
                    candidatePoints.add(br to (24 - bc))
                }
                SymmetryMode.VERTICAL -> {
                    candidatePoints.add(br to bc)
                    candidatePoints.add((24 - br) to bc)
                }
                SymmetryMode.QUAD -> {
                    candidatePoints.add(br to bc)
                    candidatePoints.add(br to (24 - bc))
                    candidatePoints.add((24 - br) to bc)
                    candidatePoints.add((24 - br) to (24 - bc))
                }
            }
        }
        candidatePoints.distinct().forEach { (pr, pc) ->
            if (GlyphAperture.isPhysicalLed(pc, pr)) {
                if (forceErase) {
                    onSetDotExplicit(pr, pc, false)
                } else {
                    onCellInteract(pr, pc, isDrag || stampedOffsets.size > 1)
                }
            }
        }
        if (forceErase) {
            onBrushBrightnessChange(origBrightness)
        }
    }

    val isDragShapeOrSelectMode = studioState.activeDockTab == StudioDockTab.SELECT ||
        (studioState.activeDockTab == StudioDockTab.DRAW &&
            (studioState.activeDrawTool == StudioDrawTool.LINE || studioState.activeDrawTool == StudioDrawTool.CIRCLE))

    val handleCellInteract: (Int, Int, Boolean) -> Unit = { r, c, isDrag ->
        cursorRow = r
        cursorCol = c
        if (studioState.activeDockTab == StudioDockTab.CURSOR) {
            // Precision Reticle Navigation Mode: move reticle only, no accidental pen drawing
        } else if (studioState.activeDockTab == StudioDockTab.GUIDES && studioState.pocketFillMode) {
            if (!isDrag) {
                onFillPocketAt(r, c)
            }
        } else if (studioState.activeDockTab != StudioDockTab.SELECT) {
            when (studioState.activeDrawTool) {
                StudioDrawTool.FILL -> {
                    if (!isDrag) {
                        if (studioState.rowGuides.isNotEmpty() || studioState.colGuides.isNotEmpty()) {
                            onFillPocketAt(r, c)
                        } else {
                            onFloodFill(r, c)
                        }
                    }
                }
                StudioDrawTool.CIRCLE -> {
                    if (!isDrag) {
                        onDrawCircleOutline(r, c, (r + 4).coerceAtMost(24), c)
                    }
                }
                StudioDrawTool.LINE -> {
                    if (!isDrag) {
                        onDrawLine(r, 0, r, 24)
                    }
                }
                StudioDrawTool.ERASER -> {
                    plotPointWithBrushAndSymmetry(r, c, isDrag, true)
                }
                StudioDrawTool.PEN -> {
                    plotPointWithBrushAndSymmetry(r, c, isDrag, false)
                }
            }
        }
    }

    val handleDragStartEnd: ((Int, Int, Int, Int, Boolean) -> Unit)? = if (isDragShapeOrSelectMode) {
        { sR, sC, cR, cC, isFinished ->
            if (studioState.activeDockTab == StudioDockTab.SELECT) {
                cursorRow = cR
                cursorCol = cC
                shapePreviewStart = null
                shapePreviewEnd = null
                onSetSelectionRegion(sR, sC, cR, cC)
            } else {
                when (studioState.activeDrawTool) {
                    StudioDrawTool.LINE -> {
                        val (snappedR, snappedC) = snapLineEndpointToAxis(sR, sC, cR, cC)
                        cursorRow = snappedR
                        cursorCol = snappedC
                        if (!isFinished) {
                            shapePreviewStart = sR to sC
                            shapePreviewEnd = snappedR to snappedC
                        } else {
                            shapePreviewStart = null
                            shapePreviewEnd = null
                            onDrawLine(sR, sC, snappedR, snappedC)
                        }
                    }
                    StudioDrawTool.CIRCLE -> {
                        cursorRow = cR
                        cursorCol = cC
                        if (!isFinished) {
                            shapePreviewStart = sR to sC
                            shapePreviewEnd = cR to cC
                        } else {
                            shapePreviewStart = null
                            shapePreviewEnd = null
                            val edgeR = if (sR == cR && sC == cC) (sR + 4).coerceAtMost(24) else cR
                            val edgeC = cC
                            onDrawCircleOutline(sR, sC, edgeR, edgeC)
                        }
                    }
                    else -> {
                        shapePreviewStart = null
                        shapePreviewEnd = null
                    }
                }
            }
        }
    } else null

    val activeLinePreviewVector: Pair<Pair<Int, Int>, Pair<Int, Int>>? =
        if (studioState.activeDockTab == StudioDockTab.DRAW &&
            studioState.activeDrawTool == StudioDrawTool.LINE &&
            shapePreviewStart != null && shapePreviewEnd != null
        ) {
            shapePreviewStart!! to shapePreviewEnd!!
        } else null

    val activeCirclePreviewGeometry: Triple<Int, Int, Int>? =
        if (studioState.activeDockTab == StudioDockTab.DRAW &&
            studioState.activeDrawTool == StudioDrawTool.CIRCLE &&
            shapePreviewStart != null && shapePreviewEnd != null
        ) {
            val sPt = shapePreviewStart!!
            val ePt = shapePreviewEnd!!
            val rad = hypot((ePt.first - sPt.first).toDouble(), (ePt.second - sPt.second).toDouble())
                .roundToInt()
                .let { if (it == 0) 4 else it.coerceIn(1, 12) }
            Triple(sPt.first, sPt.second, rad)
        } else null

    val shapePreviewOverlayPixels: ByteArray? = remember(
        activeLinePreviewVector,
        activeCirclePreviewGeometry,
        studioState.brushBrightness
    ) {
        when {
            activeLinePreviewVector != null -> {
                val (s, e) = activeLinePreviewVector
                computeLinePreviewPixels(s.first, s.second, e.first, e.second, studioState.brushBrightness)
            }
            activeCirclePreviewGeometry != null -> {
                val (cR, cC, rad) = activeCirclePreviewGeometry
                computeCirclePreviewPixels(cR, cC, rad, studioState.brushBrightness)
            }
            else -> null
        }
    }

    // Fixed Viewport Layout (Zero Vertical Scrolling)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NothingPureBlack)
            .testTag("matrix_studio_screen")
    ) {
        // =====================================================================
        // 1. TOP HEADER BAR (54dp)
        // =====================================================================
        Surface(
            color = NothingCarbonSurface,
            border = BorderStroke(0.5.dp, NothingBorderHairline),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: [● LIVE] pill + Rule Name • 489 LEDs
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (studioState.isLiveHardwareMirror) NothingCrimsonRed.copy(alpha = 0.22f) else NothingElevatedSurface,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            1.dp,
                            if (studioState.isLiveHardwareMirror) NothingMatrixGreen else NothingBorderHairline
                        ),
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { onToggleLiveMirror(!studioState.isLiveHardwareMirror) }
                            .testTag("studio_live_mirror_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (studioState.isLiveHardwareMirror) NothingMatrixGreen else NothingCrimsonRed)
                            )
                            Text(
                                text = "LIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = NothingPureWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "${studioState.name.ifBlank { "Circular Matrix 01" }} • ${activeFrame.activeDotCount()}/489",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = NothingPureWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Right: [<>] Code View | [SAVE] | [✕] Close
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudioBareActionIcon(
                        icon = Icons.Default.Code,
                        contentDescription = "Export Code",
                        tint = NothingTechnicalGray,
                        onClick = {
                            val script = if (studioState.decompiledRule != null || studioState.visualLayers.isNotEmpty()) {
                                val rule = studioState.decompiledRule ?: com.example.dsl.DecompiledStudioRule(
                                    ruleName = studioState.name.ifBlank { "Custom Matrix" },
                                    frames = studioState.frames,
                                    visualLayers = studioState.visualLayers
                                )
                                com.example.dsl.GlyphScriptDecompiler.recompile(
                                    rule = rule,
                                    frames = studioState.frames,
                                    layers = studioState.visualLayers,
                                    activeLayerId = studioState.selectedLayerId
                                )
                            } else {
                                generateGlyphScriptCode(studioState.frames, studioState.name.ifBlank { "Custom Matrix" })
                            }
                            clipboardManager.setText(AnnotatedString(script))
                            pushStudioTicker("[ Copied GlyphScript to Clipboard ]")
                        },
                        size = 44.dp,
                        iconSize = 22.dp,
                        modifier = Modifier.testTag("studio_export_code_button")
                    )

                    Surface(
                        color = NothingCrimsonRed,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onSaveAndApplyRule()
                                pushStudioTicker("[ Saved & Applied to Active Rule ]")
                            }
                            .testTag("studio_save_and_apply_button")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "SAVE",
                                style = MaterialTheme.typography.labelMedium,
                                color = NothingPureWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    StudioBareActionIcon(
                        icon = Icons.Default.Close,
                        contentDescription = "Discard & Close Studio",
                        tint = NothingTechnicalGray,
                        onClick = { showDiscardConfirmDialog = true },
                        size = 44.dp,
                        iconSize = 22.dp,
                        modifier = Modifier.testTag("studio_close_reset_button")
                    )
                }
            }
        }

        // Permanent Non-Shifting Top Status HUD Ticker (Fixed 26.dp height — never collapses or shifts canvas)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .padding(horizontal = 16.dp)
                .pointerInput(Unit) { }
                .testTag("studio_subheader_ticker"),
            contentAlignment = Alignment.Center
        ) {
            // Subtle dim idle dot-matrix baseline when no active message is showing
            Text(
                text = "· · · MATRIX HUD READY · LAYER: ${studioState.editingLayerName.uppercase()} · · ·",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = NothingTechnicalGray.copy(alpha = 0.32f * (1f - tickerTextAlpha)),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Active HUD notification message fading in/out smoothly via alpha
            Text(
                text = studioTickerMessage,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = NothingPureWhite.copy(alpha = tickerTextAlpha),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showDiscardConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardConfirmDialog = false },
                containerColor = NothingCarbonSurface,
                titleContentColor = NothingPureWhite,
                textContentColor = NothingTechnicalGray,
                title = {
                    Text(
                        text = "Discard unsaved studio edits?",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = NothingPureWhite
                    )
                },
                text = {
                    Text(
                        text = "Any unsaved dot matrix edits, keyframes, or layer changes will be discarded and the studio will return to your previous view.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingTechnicalGray
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDiscardConfirmDialog = false
                            onCloseStudio()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NothingCrimsonRed,
                            contentColor = NothingPureWhite
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("studio_discard_confirm_button")
                    ) {
                        Text(
                            text = "Discard & Exit",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showDiscardConfirmDialog = false },
                        border = BorderStroke(1.dp, NothingBorderHairline),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("studio_keep_editing_button")
                    ) {
                        Text(
                            text = "Keep Editing",
                            fontFamily = FontFamily.Monospace,
                            color = NothingPureWhite,
                            fontSize = 12.sp
                        )
                    }
                }
            )
        }

        // =====================================================================
        // 2. COMPACT CIRCULAR MATRIX CANVAS (With 4-Corner Non-Overlapping HUD)
        //    • Top-Left: [1×] [2×] [3×] [4×] Zoom Chips
        //    • Top-Right: [1.0x | X:12 Y:12] Coordinate Readout
        //    • Bottom-Left: Compact Icon-Only [↶] Undo & [↷] Redo
        //    • Bottom-Right: Smart Dynamic [+ Add Dot] / [- Clear Dot]
        // =====================================================================
        val focusedIdx = (cursorRow.coerceIn(0, MATRIX_SIZE - 1) * MATRIX_SIZE + cursorCol.coerceIn(0, MATRIX_SIZE - 1))
            .coerceIn(0, MATRIX_CELL_COUNT - 1)
        val isFocusedDotLit = (displayedPixels.getOrNull(focusedIdx)?.toInt() ?: 0) and 0xFF > 0

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(NothingPureBlack)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularGlyphMatrixPreview(
                pixels = displayedPixels,
                ghostPixels = if (onionSkinningEnabled && !studioState.isPreviewPlaying && studioState.selectedFrameIndex > 0) {
                    studioState.frames[studioState.selectedFrameIndex - 1].pixels
                } else null,
                onionOpacityAlpha = onionOpacityPct / 100f,
                referenceOverlayPixels = if (studioState.showFullPreviewComposite && !studioState.isPreviewPlaying) {
                    studioState.compositeOverlayPixels
                } else null,
                previewOverlayPixels = shapePreviewOverlayPixels,
                interactive = !studioState.isPreviewPlaying,
                showCrosshairGuides = true,
                showCircularBezelOverlay = studioState.showCircularBezelOverlay,
                zoomFactor = zoomScaleFloat,
                panOffsetX = panOffsetX,
                panOffsetY = panOffsetY,
                highlightedCell = cursorRow to cursorCol,
                rowGuides = studioState.rowGuides,
                colGuides = studioState.colGuides,
                activeRowGuide = studioState.activeRowGuide,
                activeColGuide = studioState.activeColGuide,
                axisLockEnabled = studioState.axisLockEnabled,
                selectionBounds = studioState.selectionBounds,
                linePreviewVector = activeLinePreviewVector,
                circlePreviewGeometry = activeCirclePreviewGeometry,
                reticleFreeDragOnly = studioState.activeDockTab == StudioDockTab.CURSOR,
                isScaleMode = studioState.activeDockTab == StudioDockTab.SCALE,
                scaleBoundingBox = studioState.scaleActiveBounds,
                onScaleHandleDrag = onUpdateScaleBoundingBoxDrag,
                onCursorMove = { r, c ->
                    cursorRow = r
                    cursorCol = c
                },
                onTransformGesture = { newZoom, newPanX, newPanY, focusR, focusC ->
                    zoomScaleFloat = newZoom.coerceIn(1f, 4f)
                    panOffsetX = newPanX
                    panOffsetY = newPanY
                    cursorRow = focusR
                    cursorCol = focusC
                },
                onGuideDrag = { isRowGuide, fromIdx, toIdx ->
                    if (isRowGuide) {
                        onMoveRowGuide(fromIdx, toIdx)
                    } else {
                        onMoveColGuide(fromIdx, toIdx)
                    }
                },
                onDragStartEnd = handleDragStartEnd,
                onCellInteract = handleCellInteract,
                modifier = Modifier
                    .fillMaxWidth(0.66f)
                    .aspectRatio(1f)
            )

            // Top-Right Corner: [1.0x Reset] [X:12 Y:12]
            Surface(
                color = NothingCarbonSurface.copy(alpha = 0.90f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(
                    0.8.dp,
                    if (zoomScaleFloat > 1.05f) NothingCrimsonRed else NothingBorderHairline
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        zoomScaleFloat = 1f
                        panOffsetX = 0f
                        panOffsetY = 0f
                    }
                    .testTag("studio_zoom_reset_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val zoomLabel = String.format(java.util.Locale.US, "%.1fx", zoomScaleFloat)
                    Text(
                        text = if (zoomScaleFloat > 1.05f) "$zoomLabel Reset" else zoomLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (zoomScaleFloat > 1.05f) NothingCrimsonRed else NothingPureWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "|",
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingBorderHairline,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "X:$cursorCol Y:$cursorRow",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = NothingPureWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Top-Left Corner: Quick Zoom Stepper Chips (1x / 2x / 3x / 4x)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 2.dp, start = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(1f to "1×", 2f to "2×", 3f to "3×", 4f to "4×").forEach { (scaleVal, lbl) ->
                    val isSelected = kotlin.math.abs(zoomScaleFloat - scaleVal) < 0.25f
                    Surface(
                        color = if (isSelected) NothingCrimsonRed else NothingCarbonSurface.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.8.dp, if (isSelected) NothingPureWhite else NothingBorderHairline),
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                zoomScaleFloat = scaleVal
                                if (scaleVal == 1f) {
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                }
                            }
                            .testTag("studio_zoom_${scaleVal.toInt()}x_chip")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lbl,
                                style = MaterialTheme.typography.labelSmall,
                                color = NothingPureWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Bottom-Left Corner: Compact Icon-Only Universal Undo & Redo Buttons
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 2.dp, start = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = NothingCarbonSurface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (studioState.canUndo) NothingPureWhite else NothingBorderHairline
                    ),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onUndo)
                        .testTag("universal_undo_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (studioState.canUndo) NothingPureWhite else NothingTechnicalGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Surface(
                    color = NothingCarbonSurface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (studioState.canRedo) NothingPureWhite else NothingBorderHairline
                    ),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onRedo)
                        .testTag("universal_redo_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (studioState.canRedo) NothingPureWhite else NothingTechnicalGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Bottom-Right Corner: Smart Contextual Dynamic Dot Toggle ([+ Add Dot] / [- Clear Dot])
            // Bottom-Right Corner: Dedicated Quick-Clear Dustbin Button directly above [• Add / Clear Dot]
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 2.dp, end = 2.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Quick-Clear Dustbin Button (Frame Reset: zeroes PWM for current frame on active layer)
                Surface(
                    color = NothingCarbonSurface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NothingBorderHairline),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onClearActiveFrame()
                            pushStudioTicker("[ Wiped Current Frame ]")
                        }
                        .testTag("quick_clear_frame_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Clear Current Frame",
                            tint = NothingCrimsonRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Smart Contextual Dynamic Dot Toggle ([+ Add Dot] / [- Clear Dot])
                Surface(
                    color = if (isFocusedDotLit) NothingCrimsonRed else NothingCarbonSurface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.2.dp,
                        if (isFocusedDotLit) NothingPureWhite else NothingMatrixGreen
                    ),
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onSetDotExplicit(cursorRow, cursorCol, !isFocusedDotLit)
                        }
                        .testTag("smart_dynamic_dot_toggle_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (isFocusedDotLit) Icons.Default.Clear else Icons.Default.Add,
                            contentDescription = if (isFocusedDotLit) "Clear Dot" else "Add Dot",
                            tint = if (isFocusedDotLit) NothingPureWhite else NothingMatrixGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isFocusedDotLit) "Clear Dot" else "Add Dot",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = NothingPureWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 3A. DEDICATED ACTIVE LAYER STATUS HEADER (Full 100% Width — Zero Overlap)
        //     • Displays EDITING LAYER: <layer_name> & Quick Layer Switcher Chips
        // =====================================================================
        Surface(
            color = NothingCarbonSurface,
            border = BorderStroke(0.5.dp, NothingBorderHairline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("active_layer_status_header")
        ) {
            val activeLayerLabel = activeLayer?.name ?: "base_matrix"

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = NothingCrimsonRed.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, NothingCrimsonRed),
                    modifier = Modifier
                        .height(32.dp)
                        .widthIn(max = 175.dp)
                        .testTag("compact_el_status_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(NothingCrimsonRed)
                        )
                        Text(
                            text = if (activeLayer?.animationMacro != null) {
                                "EL: $activeLayerLabel [${activeLayer.animationMacro.presetLabel()}]"
                            } else {
                                "EL: $activeLayerLabel"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = NothingPureWhite,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Quick Switcher for existing visual layers
                studioState.visualLayers.forEach { layer ->
                    val isSelected = layer.id == (studioState.selectedLayerId ?: studioState.visualLayers.firstOrNull()?.id)
                    NothingCompactPill(
                        label = layer.name,
                        selected = isSelected,
                        onClick = { onSelectLayer(layer.id) },
                        fontSize = 10.sp,
                        horizontalPadding = 10.dp,
                        verticalPadding = 6.dp,
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("header_layer_chip_${layer.name}")
                    )
                }
            }
        }

        if (showFrameBatchDeleteConfirm) {
            val checkedFramesCount = studioState.selectedFrameIndicesForBatch.size.coerceAtLeast(1)
            AlertDialog(
                onDismissRequest = { showFrameBatchDeleteConfirm = false },
                containerColor = NothingCarbonSurface,
                titleContentColor = NothingPureWhite,
                textContentColor = NothingTechnicalGray,
                title = {
                    Text(
                        text = "DELETE $checkedFramesCount SELECTED FRAME${if (checkedFramesCount == 1) "" else "S"}?",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                },
                text = {
                    Text(
                        text = "This will remove the selected timeline frames from the active sequence.",
                        fontSize = 12.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showFrameBatchDeleteConfirm = false
                            onDeleteSelectedFrames()
                            pushStudioTicker("[ DELETED $checkedFramesCount FRAME(S) ]")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NothingCrimsonRed),
                        modifier = Modifier.testTag("confirm_batch_delete_frames_button")
                    ) {
                        Text("Delete Frames", color = NothingPureWhite)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showFrameBatchDeleteConfirm = false }) {
                        Text("Cancel", color = NothingPureWhite)
                    }
                }
            )
        }

        // =====================================================================
        // 3B. DEDICATED TALLER KEYFRAME THUMBNAIL FILMSTRIP ROW (Height 90dp)
        //     Drag-and-Drop Reorderable + Multi-Select Toggle & Checkbox Badges
        // =====================================================================
        Surface(
            color = NothingCarbonSurface,
            border = BorderStroke(0.5.dp, NothingBorderHairline),
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .testTag("keyframe_thumbnails_strip")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Dedicated Multi-Select Frame Mode Toggle Button beside the frame list
                Surface(
                    color = if (studioState.isFrameMultiSelectMode) NothingCrimsonRed else NothingElevatedSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        width = if (studioState.isFrameMultiSelectMode) 1.4.dp else 0.8.dp,
                        color = if (studioState.isFrameMultiSelectMode) NothingPureWhite else NothingBorderHairline
                    ),
                    modifier = Modifier
                        .size(width = 44.dp, height = 74.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onToggleFrameMultiSelectMode(null)
                            if (studioState.isFrameMultiSelectMode) {
                                showFrameBatchSpeedStepper = false
                            }
                        }
                        .testTag("timeline_multi_select_toggle_button")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SelectAll,
                            contentDescription = "Multi-Select Frames",
                            tint = NothingPureWhite,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (studioState.isFrameMultiSelectMode) {
                                "${studioState.selectedFrameIndicesForBatch.size}"
                            } else {
                                "SEL"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = NothingPureWhite,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                LazyRow(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(
                        items = studioState.frames,
                        key = { _, frame -> frame.id }
                    ) { idx, frame ->
                        val isSelected = idx == studioState.selectedFrameIndex
                        val isCheckedForBatch = idx in studioState.selectedFrameIndicesForBatch
                        val isBeingDragged = draggedFrameId == frame.id
                        val currentIdx by rememberUpdatedState(idx)
                        val currentFramesCount by rememberUpdatedState(studioState.frames.size)

                        val dragChipScale by animateFloatAsState(
                            targetValue = if (isBeingDragged) 1.12f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "timeline_frame_drag_scale"
                        )

                        Column(
                            modifier = Modifier
                                .animateItem(
                                    placementSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                                .zIndex(if (isBeingDragged) 10f else 0f)
                                .graphicsLayer {
                                    scaleX = dragChipScale
                                    scaleY = dragChipScale
                                    translationX = if (isBeingDragged) (draggedFrameOffsetX * 0.35f) else 0f
                                    shadowElevation = if (isBeingDragged) 16f else 0f
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isBeingDragged) NothingCrimsonRed.copy(alpha = 0.20f)
                                    else Color.Transparent
                                )
                                .border(
                                    width = when {
                                        isBeingDragged -> 1.8.dp
                                        studioState.isFrameMultiSelectMode && isCheckedForBatch -> 1.4.dp
                                        studioState.isFrameMultiSelectMode -> 0.8.dp
                                        else -> 0.dp
                                    },
                                    color = when {
                                        isBeingDragged -> NothingCrimsonRed
                                        studioState.isFrameMultiSelectMode && isCheckedForBatch -> NothingCrimsonRed
                                        studioState.isFrameMultiSelectMode -> NothingBorderHairline
                                        else -> Color.Transparent
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    if (studioState.isFrameMultiSelectMode) {
                                        onToggleFrameCheckedForBatch(idx)
                                    } else {
                                        onSelectFrame(idx)
                                    }
                                }
                                .pointerInput(frame.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            draggedFrameId = frame.id
                                            draggedFrameOffsetX = 0f
                                            pushStudioTicker("[ Dragging Frame F${currentIdx + 1} ]")
                                        },
                                        onDragCancel = {
                                            draggedFrameId = null
                                            draggedFrameOffsetX = 0f
                                        },
                                        onDragEnd = {
                                            draggedFrameId = null
                                            draggedFrameOffsetX = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            draggedFrameOffsetX += dragAmount.x
                                            val slotThresholdPx = 44.dp.toPx()
                                            if (draggedFrameOffsetX <= -slotThresholdPx && currentIdx > 0) {
                                                draggedFrameOffsetX += slotThresholdPx
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onReorderFrame(currentIdx, currentIdx - 1)
                                            } else if (draggedFrameOffsetX >= slotThresholdPx && currentIdx < currentFramesCount - 1) {
                                                draggedFrameOffsetX -= slotThresholdPx
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onReorderFrame(currentIdx, currentIdx + 1)
                                            }
                                        }
                                    )
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .testTag("timeline_frame_$idx"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                MiniMatrixThumbnail(
                                    pixels = frame.pixels,
                                    size = 52.dp,
                                    isSelected = isSelected,
                                    isMultiSelectMode = studioState.isFrameMultiSelectMode,
                                    isCheckedForBatch = isCheckedForBatch
                                )
                                if (studioState.isFrameMultiSelectMode) {
                                    Surface(
                                        color = if (isCheckedForBatch) NothingCrimsonRed else NothingCarbonSurface,
                                        shape = CircleShape,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isCheckedForBatch) NothingPureWhite else NothingTechnicalGray
                                        ),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onToggleFrameCheckedForBatch(idx) }
                                            .testTag("timeline_frame_checkbox_$idx")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (isCheckedForBatch) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Frame Selected",
                                                    tint = NothingPureWhite,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (studioState.frames.size > 1 && idx > 0 && (isSelected || studioState.isFrameMultiSelectMode)) {
                                    Text(
                                        text = "‹",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = NothingPureWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { onReorderFrame(idx, idx - 1) }
                                            .padding(horizontal = 2.dp)
                                            .testTag("timeline_frame_reorder_left_$idx")
                                    )
                                }
                                Surface(
                                    color = when {
                                        studioState.isFrameMultiSelectMode && isCheckedForBatch -> NothingCrimsonRed
                                        isSelected -> NothingCrimsonRed
                                        else -> NothingElevatedSurface
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(
                                        0.8.dp,
                                        if (isSelected || isCheckedForBatch) NothingPureWhite else NothingBorderHairline
                                    )
                                ) {
                                    Text(
                                        text = "F${idx + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = NothingPureWhite,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (studioState.frames.size > 1 && idx < studioState.frames.lastIndex && (isSelected || studioState.isFrameMultiSelectMode)) {
                                    Text(
                                        text = "›",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = NothingPureWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { onReorderFrame(idx, idx + 1) }
                                            .padding(horizontal = 2.dp)
                                            .testTag("timeline_frame_reorder_right_$idx")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 3C. SEPARATE FRAME EDITING, BATCH ACTIONS & PLAYBACK STRIP (Icon-First)
        // =====================================================================
        Surface(
            color = NothingCarbonSurface,
            border = BorderStroke(0.5.dp, NothingBorderHairline),
            modifier = Modifier.fillMaxWidth().testTag("frame_editing_tools_strip")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Anchored Batch Actions Toolbar when Multi-Select Frame Mode is active
                if (studioState.isFrameMultiSelectMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("timeline_batch_actions_bar"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NothingPillBadge(
                            text = "${studioState.selectedFrameIndicesForBatch.size} SEL",
                            color = NothingCrimsonRed
                        )

                        // Duplicate Batch (ContentCopy icon)
                        Surface(
                            color = NothingElevatedSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, NothingPureWhite.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onDuplicateSelectedFrames)
                                .testTag("batch_duplicate_frames_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Duplicate Batch Frames",
                                    tint = NothingPureWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "DUP",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = NothingPureWhite,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Reverse Batch (SwapHoriz icon)
                        Surface(
                            color = NothingElevatedSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.8.dp, NothingBorderHairline),
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onReverseSelectedFrames)
                                .testTag("batch_reverse_frames_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "Reverse Batch Frames",
                                    tint = NothingPureWhite,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = "REV",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = NothingPureWhite,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Batch Speed / Interval (Speed icon + inline cadence stepper)
                        Surface(
                            color = if (showFrameBatchSpeedStepper) NothingCrimsonRed else NothingElevatedSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (showFrameBatchSpeedStepper) NothingPureWhite else NothingBorderHairline
                            ),
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showFrameBatchSpeedStepper = !showFrameBatchSpeedStepper }
                                .testTag("batch_speed_frames_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Batch Speed / Interval",
                                    tint = NothingPureWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${activeFrame.durationMs}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = NothingPureWhite,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (showFrameBatchSpeedStepper) {
                            listOf(66L, 110L, 165L, 330L).forEach { msVal ->
                                NothingCompactPill(
                                    label = "${msVal}ms",
                                    selected = abs(activeFrame.durationMs - msVal) <= 10L,
                                    onClick = { onUpdateSelectedFramesDuration(msVal) },
                                    fontSize = 9.sp,
                                    horizontalPadding = 8.dp,
                                    verticalPadding = 6.dp,
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("batch_speed_step_${msVal}ms")
                                )
                            }
                        }

                        // Delete Batch (DeleteSweep icon)
                        Surface(
                            color = NothingElevatedSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, NothingCrimsonRed),
                            modifier = Modifier
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showFrameBatchDeleteConfirm = true }
                                .testTag("batch_delete_frames_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "Delete Batch Frames",
                                    tint = NothingCrimsonRed,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    text = "DEL",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = NothingCrimsonRed,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Deselect All (Close icon)
                        Surface(
                            color = NothingElevatedSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.8.dp, NothingBorderHairline),
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    showFrameBatchSpeedStepper = false
                                    onClearFrameBatchSelection()
                                }
                                .testTag("batch_deselect_frames_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Deselect All Frames",
                                    tint = NothingPureWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Standard Icon-First Timeline Strip: [+] [ContentCopy] [Delete] [Onion 20%] | [Play/Pause] [Speed] [Loop]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Icon-First Frame Actions: [+] Add, [ContentCopy] Duplicate, [Delete] Delete, [Layers 20%] Onion Skin
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = NothingElevatedSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, NothingPureWhite.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onAddFrame)
                                .testTag("timeline_add_frame_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Frame",
                                    tint = NothingPureWhite,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Surface(
                            color = NothingElevatedSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.8.dp, NothingBorderHairline),
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onDuplicateFrame)
                                .testTag("timeline_duplicate_frame_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Duplicate Active Frame",
                                    tint = NothingPureWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Surface(
                            color = NothingElevatedSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.8.dp, NothingBorderHairline),
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = studioState.frames.size > 1, onClick = onDeleteFrame)
                                .testTag("timeline_delete_frame_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Active Frame",
                                    tint = if (studioState.frames.size > 1) NothingCrimsonRed else NothingTechnicalGray.copy(alpha = 0.4f),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        // Onion Skinning Icon Button with compact percentage badge (20%)
                        Surface(
                            color = if (onionSkinningEnabled) NothingCrimsonRed else NothingElevatedSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                width = if (onionSkinningEnabled) 1.2.dp else 0.8.dp,
                                color = if (onionSkinningEnabled) NothingPureWhite else NothingBorderHairline
                            ),
                            modifier = Modifier
                                .height(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onionSkinningEnabled = !onionSkinningEnabled }
                                .testTag("timeline_onion_skin_inline_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = "Toggle Onion Skinning",
                                    tint = NothingPureWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${onionOpacityPct}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = NothingPureWhite,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Right: Play/Pause Vector Icon, Responsive Speed Presets & Playback Style Cycler
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StudioBareActionIcon(
                            icon = if (studioState.isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (studioState.isPreviewPlaying) "Pause Animation" else "Play Animation",
                            tint = if (studioState.isPreviewPlaying) NothingCrimsonRed else NothingMatrixGreen,
                            onClick = onTogglePreview,
                            size = 44.dp,
                            iconSize = 22.dp,
                            modifier = Modifier.testTag("studio_play_preview_button")
                        )

                        val currentMs = activeFrame.durationMs
                        val matchedPreset = SpeedPresetOption.entries.minByOrNull { abs(it.durationMs - currentMs) }
                            ?: SpeedPresetOption.STANDARD
                        NothingCompactPill(
                            label = matchedPreset.badge,
                            selected = true,
                            onClick = {
                                val idx = SpeedPresetOption.entries.indexOf(matchedPreset)
                                val nextPreset = SpeedPresetOption.entries[(idx + 1) % SpeedPresetOption.entries.size]
                                onFrameDurationChange(nextPreset.durationMs)
                            },
                            fontSize = 11.sp,
                            horizontalPadding = 10.dp,
                            verticalPadding = 8.dp,
                            modifier = Modifier.height(44.dp).testTag("timeline_speed_pill")
                        )

                        // Playback Style Cycler: Loop (CW) • Ping-Pong • Once
                        NothingCompactPill(
                            label = studioState.playbackMode.shortLabel,
                            selected = studioState.playbackMode != AnimationPlaybackMode.LOOP,
                            onClick = {
                                val modes = AnimationPlaybackMode.entries
                                val nextMode = modes[(modes.indexOf(studioState.playbackMode) + 1) % modes.size]
                                onPlaybackModeChange(nextMode)
                            },
                            fontSize = 11.sp,
                            horizontalPadding = 10.dp,
                            verticalPadding = 8.dp,
                            modifier = Modifier.height(44.dp).testTag("timeline_playback_mode_pill")
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 4. TIER 2: DYNAMIC CONTEXTUAL SHELF (Swaps per Active Category)
        // =====================================================================
        Surface(
            color = NothingCarbonSurface,
            border = BorderStroke(0.5.dp, NothingBorderHairline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("studio_active_tool_drawer")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                when (studioState.activeDockTab) {
                    StudioDockTab.DRAW -> {
                        DrawContextualShelf(
                            studioState = studioState,
                            cursorRow = cursorRow,
                            cursorCol = cursorCol,
                            brushSizeMode = brushSizeMode,
                            onBrushSizeModeChange = {
                                brushSizeMode = it
                                pushStudioTicker("[ Brush Size: ${it.label} ]")
                            },
                            onActiveDrawToolChange = { tool ->
                                onActiveDrawToolChange(tool)
                                val pct = ((studioState.brushBrightness / 255f) * 100).roundToInt()
                                pushStudioTicker("[ ${tool.label} Tool Active • Brush: $pct% ]")
                            },
                            onBrushBrightnessChange = onBrushBrightnessChange,
                            onSetDotExplicit = onSetDotExplicit,
                            onClearCanvas = {
                                onApplyTransform(MatrixTransform.CLEAR)
                                pushStudioTicker("[ Canvas Cleared ]")
                            },
                            onInvert489Leds = {
                                onInvert489Leds()
                                pushStudioTicker("[ Inverted 489 Physical LEDs ]")
                            },
                            onUndo = onUndo
                        )
                    }

                    StudioDockTab.CURSOR -> {
                        CursorContextualShelf(
                            studioState = studioState,
                            cursorRow = cursorRow,
                            cursorCol = cursorCol,
                            isFocusedDotLit = isFocusedDotLit,
                            onStepCursor = { dRow, dCol ->
                                if (studioState.cursorAutoStepEnabled) {
                                    plotPointWithBrushAndSymmetry(cursorRow, cursorCol, false, false)
                                }
                                val (nextR, nextC) = stepReticleWithinPhysicalAperture(
                                    startRow = cursorRow,
                                    startCol = cursorCol,
                                    dRow = dRow,
                                    dCol = dCol,
                                    axisLockEnabled = studioState.axisLockEnabled,
                                    activeRowGuide = studioState.activeRowGuide,
                                    activeColGuide = studioState.activeColGuide
                                )
                                cursorRow = nextR
                                cursorCol = nextC
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onJumpCursor = { targetRow, targetCol, label ->
                                val (snappedR, snappedC) = snapCoordinateToPhysicalAperture(
                                    rawRow = targetRow,
                                    rawCol = targetCol,
                                    axisLockEnabled = studioState.axisLockEnabled,
                                    activeRowGuide = studioState.activeRowGuide,
                                    activeColGuide = studioState.activeColGuide
                                )
                                cursorRow = snappedR
                                cursorCol = snappedC
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                pushStudioTicker("[ Reticle -> $label (X:$snappedC Y:$snappedR) ]")
                            },
                            onToggleDotAtCursor = {
                                val nextLit = !isFocusedDotLit
                                onSetDotExplicit(cursorRow, cursorCol, nextLit)
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                pushStudioTicker(
                                    if (nextLit) "[ +Dot @ X:$cursorCol Y:$cursorRow ]"
                                    else "[ -Dot @ X:$cursorCol Y:$cursorRow ]"
                                )
                            },
                            onToggleAutoStep = {
                                val nextAuto = !studioState.cursorAutoStepEnabled
                                onSetCursorAutoStepEnabled(nextAuto)
                                pushStudioTicker(
                                    if (nextAuto) "[ Auto-Step Draw: ON ]" else "[ Auto-Step Draw: OFF ]"
                                )
                            }
                        )
                    }

                    StudioDockTab.SELECT -> {
                        SelectContextualShelf(
                            studioState = studioState,
                            cursorRow = cursorRow,
                            cursorCol = cursorCol,
                            onCopySelectedRegion = {
                                onCopySelectedRegion()
                                pushStudioTicker("[ Selection Copied to Clipboard ]")
                            },
                            onCutSelectedRegion = {
                                onCutSelectedRegion()
                                pushStudioTicker("[ Selection Cut to Clipboard ]")
                            },
                            onPasteClipboardRegion = {
                                onPasteClipboardRegion(cursorRow, cursorCol)
                                pushStudioTicker("[ Pasted Clipboard at X:$cursorCol Y:$cursorRow ]")
                            },
                            onNudgeSelectedRegion = onNudgeSelectedRegion,
                            onFlipSelectedRegion = onFlipSelectedRegion,
                            onRotateSelectedRegion90 = onRotateSelectedRegion90,
                            onRotateSelectionByAngle = onRotateSelectionByAngle,
                            onSetSelectionRotationAngle = onSetSelectionRotationAngle,
                            onResetSelectionRotation = onResetSelectionRotation,
                            onClearSelectionRegion = onClearSelectionRegion
                        )
                    }

                    StudioDockTab.GUIDES -> {
                        GuidesContextualShelf(
                            studioState = studioState,
                            cursorRow = cursorRow,
                            cursorCol = cursorCol,
                            onAddRowGuide = { onAddRowGuide(cursorRow) },
                            onAddColGuide = { onAddColGuide(cursorCol) },
                            onMoveRowGuide = onMoveRowGuide,
                            onMoveColGuide = onMoveColGuide,
                            onSelectActiveGuide = onSelectActiveGuide,
                            onRemoveRowGuide = onRemoveRowGuide,
                            onRemoveColGuide = onRemoveColGuide,
                            onClearAllGuides = onClearAllGuides,
                            onSetAxisLockEnabled = onSetAxisLockEnabled,
                            onSetPocketFillMode = onSetPocketFillMode,
                            onFillPocketAtCursor = { onFillPocketAt(cursorRow, cursorCol) }
                        )
                    }

                    StudioDockTab.TRANSFORM -> {
                        TransformContextualShelf(
                            studioState = studioState,
                            symmetryMode = symmetryMode,
                            onSymmetryModeChange = { symmetryMode = it },
                            onNudgeClampModeChange = onNudgeClampModeChange,
                            onNudge = onNudge,
                            onApplyTransform = onApplyTransform,
                            onRotateSelectionByAngle = onRotateSelectionByAngle,
                            onSetSelectionRotationAngle = onSetSelectionRotationAngle,
                            onResetSelectionRotation = onResetSelectionRotation,
                            onUndo = onUndo
                        )
                    }

                    StudioDockTab.LAYERS -> {
                        LayersContextualShelf(
                            studioState = studioState,
                            onionSkinningEnabled = onionSkinningEnabled,
                            onionOpacityPct = onionOpacityPct,
                            onToggleOnionSkinning = { onionSkinningEnabled = !onionSkinningEnabled },
                            onSetOnionOpacity = {
                                onionOpacityPct = it
                                onionSkinningEnabled = true
                            },
                            onSelectLayer = onSelectLayer,
                            onToggleFullPreviewComposite = onToggleFullPreviewComposite,
                            onToggleLayerVisibility = onToggleLayerVisibility,
                            onRemoveLayer = onRemoveLayer,
                            onMoveLayer = onMoveLayer,
                            onReorderLayer = onReorderLayer,
                            onUpdateLayer = onUpdateLayer,
                            onAddVisualLayer = onAddVisualLayer,
                            onToggleLayerBitmapMode = {
                                onToggleLayerBitmapMode(it)
                                pushStudioTicker("[ Toggled Editable Pixel Dots Mode ]")
                            },
                            onToggleLayerMultiSelectMode = onToggleLayerMultiSelectMode,
                            onToggleLayerCheckedForBatch = onToggleLayerCheckedForBatch,
                            onDuplicateSelectedLayers = onDuplicateSelectedLayers,
                            onDeleteSelectedLayers = onDeleteSelectedLayers,
                            onMergeSelectedLayers = onMergeSelectedLayers,
                            onGroupOrMoveSelectedLayers = onGroupOrMoveSelectedLayers
                        )
                    }

                    StudioDockTab.ANIMATION -> {
                        AnimationContextualShelf(
                            studioState = studioState,
                            onFrameDurationChange = onFrameDurationChange,
                            onPlaybackModeChange = onPlaybackModeChange,
                            onTransitionCurveChange = onTransitionCurveChange,
                            onApplyProceduralAnimationPreset = onApplyProceduralAnimationPreset,
                            onTogglePreview = onTogglePreview
                        )
                    }

                    StudioDockTab.PARAMS -> {
                        ParamsContextualShelf(
                            studioState = studioState,
                            savedAnimations = savedAnimations,
                            onNameChange = onNameChange,
                            onGlobalAlignChange = onGlobalAlignChange,
                            onSaveAnimation = onSaveAnimation,
                            onSaveAndApplyRule = onSaveAndApplyRule,
                            onLoadAnimation = onLoadAnimation
                        )
                    }

                    StudioDockTab.AI_PHOTO -> {
                        AiPhotoContextualShelf(
                            studioState = studioState,
                            onUpdateAiPrompt = onUpdateAiPrompt,
                            onGenerateAiPrompt = onGenerateAiPrompt,
                            onImportPhotoLauncher = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onLoadSamplePhoto = onLoadSamplePhoto,
                            onUpdatePhotoSettings = onUpdatePhotoSettings,
                            onUpdateEmojiInput = onUpdateEmojiInput,
                            onRasterizeEmoji = onRasterizeEmoji,
                            onUpdatePhotoThresholdPct = onUpdatePhotoThresholdPct,
                            onTogglePhotoEdgeDetect = onTogglePhotoEdgeDetect
                        )
                    }

                    StudioDockTab.SCALE -> {
                        ScaleContextualShelf(
                            studioState = studioState,
                            onSetScalePercent = { pct ->
                                onSetScalePercent(pct)
                                pushStudioTicker("[ Scale: $pct% ]")
                            },
                            onStepScalePercent = { delta ->
                                onStepScalePercent(delta)
                                val sign = if (delta > 0) "+$delta%" else "$delta%"
                                pushStudioTicker("[ Scale Nudged $sign ]")
                            },
                            onToggleAspectLock = {
                                val nextLock = !studioState.scaleAspectLocked
                                onToggleScaleAspectLock(nextLock)
                                pushStudioTicker(if (nextLock) "[ Lock 1:1 Aspect: ON ]" else "[ Lock 1:1 Aspect: OFF ]")
                            },
                            onFitToGuides = {
                                onFitScaleToGuides()
                                pushStudioTicker("[ Snapped Scale to Guidelines ]")
                            },
                            onCommitScale = {
                                onCommitScale()
                                pushStudioTicker("[ Scale Applied ]")
                            },
                            onCancelScale = {
                                onCancelScale()
                                pushStudioTicker("[ Scale Cancelled ]")
                            }
                        )
                    }

                    StudioDockTab.LIGHTING -> {
                        LightingContextualShelf(
                            studioState = studioState,
                            onAdjustRelativePwm = { delta, sel ->
                                onAdjustRelativePwm(delta, sel)
                                val sign = if (delta >= 0) "+$delta%" else "$delta%"
                                val target = if (sel == true || (sel == null && studioState.lightingTargetSelectionOnly)) "Selection" else "Active Frame"
                                pushStudioTicker("[ PWM Adjusted $sign on $target ]")
                            },
                            onSetLightingTargetSelectionOnly = { selOnly ->
                                onSetLightingTargetSelectionOnly(selOnly)
                                pushStudioTicker(if (selOnly) "[ Scope: Selection Only ]" else "[ Scope: Entire Active Frame ]")
                            }
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 5. TIER 1: SCROLLABLE BOTTOM CATEGORY WHEEL (Anchored Above Nav Bar)
        //    ◄── [ DRAW ] [ SELECT ] [ GUIDES ] [ TRANSFORM ] [ LAYERS ] [ ANIMATION ] ──►
        // =====================================================================
        Surface(
            color = NothingPureBlack,
            border = BorderStroke(0.5.dp, NothingBorderHairline),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .testTag("studio_bottom_dock_bar")
        ) {
            val primaryCategories = remember {
                listOf(
                    Triple(StudioDockTab.DRAW, Icons.Default.Brush, "dock_tab_draw"),
                    Triple(StudioDockTab.CURSOR, Icons.Default.OpenWith, "dock_tab_cursor"),
                    Triple(StudioDockTab.SELECT, Icons.Default.CropFree, "dock_tab_select"),
                    Triple(StudioDockTab.SCALE, Icons.Default.Fullscreen, "dock_tab_scale"),
                    Triple(StudioDockTab.LIGHTING, Icons.Default.WbSunny, "dock_tab_lighting"),
                    Triple(StudioDockTab.GUIDES, Icons.Default.GridOn, "dock_tab_guides"),
                    Triple(StudioDockTab.TRANSFORM, Icons.Default.Transform, "dock_tab_transform"),
                    Triple(StudioDockTab.LAYERS, Icons.Default.Layers, "dock_tab_layers"),
                    Triple(StudioDockTab.ANIMATION, Icons.Default.Animation, "dock_tab_animation"),
                    Triple(StudioDockTab.PARAMS, Icons.Default.Save, "dock_tab_params"),
                    Triple(StudioDockTab.AI_PHOTO, Icons.Default.AutoAwesome, "dock_tab_ai_photo")
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                primaryCategories.forEach { (tab, icon, tag) ->
                    val selected = studioState.activeDockTab == tab
                    Surface(
                        color = if (selected) NothingCrimsonRed else NothingCarbonSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 0.8.dp,
                            color = if (selected) NothingPureWhite else NothingBorderHairline
                        ),
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onActiveDockTabChange(tab) }
                            .testTag(tag)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = tab.label,
                                tint = if (selected) NothingPureWhite else NothingTechnicalGray,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (selected) NothingPureWhite else NothingTechnicalGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// CONTEXTUAL SHELF 1: DRAW (Expanded Single Horizontal Carousel + PWM Slider)
// [Pen] [Eraser] [Line] [Circle] [Fill] [1PX] [PLUS+] [3x3] [+Dot] [-Dot] [Invert 489] [Clear Canvas] [Undo]
// =====================================================================
@Composable
private fun DrawContextualShelf(
    studioState: StudioEditorState,
    cursorRow: Int,
    cursorCol: Int,
    brushSizeMode: BrushSizeMode,
    onBrushSizeModeChange: (BrushSizeMode) -> Unit,
    onActiveDrawToolChange: (StudioDrawTool) -> Unit,
    onBrushBrightnessChange: (Int) -> Unit,
    onSetDotExplicit: (Int, Int, Boolean) -> Unit,
    onClearCanvas: () -> Unit,
    onInvert489Leds: () -> Unit,
    onUndo: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Single Expanded Horizontal Scroll Tool Wheel (Generous 46dp Touch Targets)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [Pen]
            StudioToolCarouselButton(
                label = "Pen",
                icon = Icons.Default.Brush,
                isSelected = studioState.activeDrawTool == StudioDrawTool.PEN,
                onClick = { onActiveDrawToolChange(StudioDrawTool.PEN) },
                testTag = "draw_tool_pen"
            )

            // [Eraser]
            StudioToolCarouselButton(
                label = "Eraser",
                icon = Icons.Default.FormatPaint,
                isSelected = studioState.activeDrawTool == StudioDrawTool.ERASER,
                onClick = { onActiveDrawToolChange(StudioDrawTool.ERASER) },
                testTag = "draw_tool_eraser"
            )

            // [Line]
            StudioToolCarouselButton(
                label = "Line",
                icon = Icons.Default.HorizontalRule,
                isSelected = studioState.activeDrawTool == StudioDrawTool.LINE,
                onClick = { onActiveDrawToolChange(StudioDrawTool.LINE) },
                testTag = "draw_tool_line"
            )

            // [Circle]
            StudioToolCarouselButton(
                label = "Circle",
                icon = Icons.Default.RadioButtonUnchecked,
                isSelected = studioState.activeDrawTool == StudioDrawTool.CIRCLE,
                onClick = { onActiveDrawToolChange(StudioDrawTool.CIRCLE) },
                testTag = "draw_tool_circle"
            )

            // [Fill]
            StudioToolCarouselButton(
                label = "Fill",
                icon = Icons.Default.FormatColorFill,
                isSelected = studioState.activeDrawTool == StudioDrawTool.FILL,
                onClick = { onActiveDrawToolChange(StudioDrawTool.FILL) },
                testTag = "draw_tool_fill"
            )

            // Brush size mode chips: [1PX], [PLUS+], [3x3]
            BrushSizeMode.entries.forEach { bMode ->
                StudioToolCarouselButton(
                    label = bMode.label,
                    icon = null,
                    isSelected = brushSizeMode == bMode,
                    onClick = { onBrushSizeModeChange(bMode) },
                    testTag = "brush_size_${bMode.name.lowercase()}_chip"
                )
            }

            // [Invert 489]
            StudioToolCarouselButton(
                label = "Invert",
                icon = Icons.Default.InvertColors,
                isSelected = false,
                onClick = onInvert489Leds,
                testTag = "tool_invert_button"
            )

            // [Clear Canvas]
            StudioToolCarouselButton(
                label = "Clear",
                icon = Icons.Default.DeleteOutline,
                isSelected = false,
                textColor = NothingCrimsonRed,
                onClick = onClearCanvas,
                testTag = "tool_clear_button"
            )
        }

        // Dedicated PWM / Intensity Slider Row with Status Readouts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val brightnessVal = studioState.brushBrightness
            val percentage = (brightnessVal * 100f / 255f).roundToInt()

            Text(
                text = "PWM: $brightnessVal",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = NothingPureWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.width(64.dp)
            )

            Slider(
                value = brightnessVal.toFloat(),
                onValueChange = { onBrushBrightnessChange(it.roundToInt()) },
                valueRange = 0f..255f,
                colors = SliderDefaults.colors(
                    thumbColor = NothingCrimsonRed,
                    activeTrackColor = NothingPureWhite,
                    inactiveTrackColor = NothingElevatedSurface
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .testTag("brush_brightness_slider")
            )

            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = NothingPureWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.width(42.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

// =====================================================================
// CONTEXTUAL SHELF 1B: CURSOR / NAV (Precision Reticle D-Pad, Edge Jumps, Toggle Dot & Auto-Step)
// =====================================================================
@Composable
private fun CursorContextualShelf(
    studioState: StudioEditorState,
    cursorRow: Int,
    cursorCol: Int,
    isFocusedDotLit: Boolean,
    onStepCursor: (dRow: Int, dCol: Int) -> Unit,
    onJumpCursor: (targetRow: Int, targetCol: Int, label: String) -> Unit,
    onToggleDotAtCursor: () -> Unit,
    onToggleAutoStep: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Row 1: 4-Way Precision D-Pad + Centered [• Toggle Dot] Trigger + [Auto-Step: ON / OFF]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StudioDpadButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Step Reticle Left",
                onClick = { onStepCursor(0, -1) },
                modifier = Modifier.testTag("cursor_dpad_left")
            )
            StudioDpadButton(
                icon = Icons.Default.KeyboardArrowUp,
                contentDescription = "Step Reticle Up",
                onClick = { onStepCursor(-1, 0) },
                modifier = Modifier.testTag("cursor_dpad_up")
            )
            StudioDpadButton(
                icon = Icons.Default.KeyboardArrowDown,
                contentDescription = "Step Reticle Down",
                onClick = { onStepCursor(1, 0) },
                modifier = Modifier.testTag("cursor_dpad_down")
            )
            StudioDpadButton(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Step Reticle Right",
                onClick = { onStepCursor(0, 1) },
                modifier = Modifier.testTag("cursor_dpad_right")
            )

            // Immediate Action Trigger: [• Toggle Dot]
            NothingCompactPill(
                label = if (isFocusedDotLit) "• Clear Dot" else "• Toggle Dot",
                leadingIcon = if (isFocusedDotLit) Icons.Default.Clear else Icons.Default.Add,
                selected = isFocusedDotLit,
                unselectedBorderColor = NothingCrimsonRed,
                unselectedTextColor = NothingPureWhite,
                onClick = onToggleDotAtCursor,
                fontSize = 10.sp,
                horizontalPadding = 12.dp,
                verticalPadding = 10.dp,
                modifier = Modifier
                    .height(46.dp)
                    .testTag("cursor_toggle_dot_button")
            )

            // Auto-Step / Continuous Draw Mode Toggle
            NothingCompactPill(
                label = if (studioState.cursorAutoStepEnabled) "Auto-Step: ON" else "Auto-Step: OFF",
                selected = studioState.cursorAutoStepEnabled,
                unselectedBorderColor = if (studioState.cursorAutoStepEnabled) NothingCrimsonRed else NothingBorderHairline,
                onClick = onToggleAutoStep,
                fontSize = 10.sp,
                horizontalPadding = 12.dp,
                verticalPadding = 10.dp,
                modifier = Modifier
                    .height(46.dp)
                    .testTag("cursor_auto_step_toggle")
            )
        }

        // Row 2: Quick Jump Buttons ([Center (12,12)], [Top Edge], [Bottom Edge], [Left Edge], [Right Edge])
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NothingPillBadge(
                text = "RETICLE X:$cursorCol Y:$cursorRow",
                color = NothingCrimsonRed,
                modifier = Modifier.testTag("cursor_shelf_coordinate_badge")
            )

            NothingCompactPill(
                label = "Center (12,12)",
                selected = cursorRow == 12 && cursorCol == 12,
                onClick = { onJumpCursor(12, 12, "Center") },
                fontSize = 9.5.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 7.dp,
                modifier = Modifier
                    .height(38.dp)
                    .testTag("cursor_jump_center")
            )

            NothingCompactPill(
                label = "Top Edge",
                selected = false,
                onClick = {
                    val topR = (0 until MATRIX_SIZE).firstOrNull { GlyphAperture.isPhysicalLed(cursorCol, it) } ?: 0
                    val topC = if (GlyphAperture.isPhysicalLed(cursorCol, topR)) cursorCol else 12
                    onJumpCursor(topR, topC, "Top Edge")
                },
                fontSize = 9.5.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 7.dp,
                modifier = Modifier
                    .height(38.dp)
                    .testTag("cursor_jump_top")
            )

            NothingCompactPill(
                label = "Bottom Edge",
                selected = false,
                onClick = {
                    val botR = (MATRIX_SIZE - 1 downTo 0).firstOrNull { GlyphAperture.isPhysicalLed(cursorCol, it) } ?: 24
                    val botC = if (GlyphAperture.isPhysicalLed(cursorCol, botR)) cursorCol else 12
                    onJumpCursor(botR, botC, "Bottom Edge")
                },
                fontSize = 9.5.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 7.dp,
                modifier = Modifier
                    .height(38.dp)
                    .testTag("cursor_jump_bottom")
            )

            NothingCompactPill(
                label = "Left Edge",
                selected = false,
                onClick = {
                    val leftC = (0 until MATRIX_SIZE).firstOrNull { GlyphAperture.isPhysicalLed(it, cursorRow) } ?: 0
                    val leftR = if (GlyphAperture.isPhysicalLed(leftC, cursorRow)) cursorRow else 12
                    onJumpCursor(leftR, leftC, "Left Edge")
                },
                fontSize = 9.5.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 7.dp,
                modifier = Modifier
                    .height(38.dp)
                    .testTag("cursor_jump_left")
            )

            NothingCompactPill(
                label = "Right Edge",
                selected = false,
                onClick = {
                    val rightC = (MATRIX_SIZE - 1 downTo 0).firstOrNull { GlyphAperture.isPhysicalLed(it, cursorRow) } ?: 24
                    val rightR = if (GlyphAperture.isPhysicalLed(rightC, cursorRow)) cursorRow else 12
                    onJumpCursor(rightR, rightC, "Right Edge")
                },
                fontSize = 9.5.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 7.dp,
                modifier = Modifier
                    .height(38.dp)
                    .testTag("cursor_jump_right")
            )
        }
    }
}

private fun stepReticleWithinPhysicalAperture(
    startRow: Int,
    startCol: Int,
    dRow: Int,
    dCol: Int,
    axisLockEnabled: Boolean,
    activeRowGuide: Int?,
    activeColGuide: Int?
): Pair<Int, Int> {
    var candidateRow = (startRow + dRow).coerceIn(0, MATRIX_SIZE - 1)
    var candidateCol = (startCol + dCol).coerceIn(0, MATRIX_SIZE - 1)

    if (axisLockEnabled) {
        val rG = activeRowGuide
        val cG = activeColGuide
        if (rG != null && cG == null) {
            candidateRow = rG.coerceIn(0, MATRIX_SIZE - 1)
        } else if (cG != null && rG == null) {
            candidateCol = cG.coerceIn(0, MATRIX_SIZE - 1)
        }
    }

    if (GlyphAperture.isPhysicalLed(candidateCol, candidateRow)) {
        return candidateRow to candidateCol
    }

    return snapCoordinateToPhysicalAperture(
        rawRow = candidateRow,
        rawCol = candidateCol,
        axisLockEnabled = axisLockEnabled,
        activeRowGuide = activeRowGuide,
        activeColGuide = activeColGuide
    )
}

// =====================================================================
// CONTEXTUAL SHELF 2: SELECT (Marquee Selection, Copy, Cut, Paste, Nudge, Flip, Rotate)
// =====================================================================
@Composable
private fun SelectContextualShelf(
    studioState: StudioEditorState,
    cursorRow: Int,
    cursorCol: Int,
    onCopySelectedRegion: () -> Unit,
    onCutSelectedRegion: () -> Unit,
    onPasteClipboardRegion: () -> Unit,
    onNudgeSelectedRegion: (dx: Int, dy: Int) -> Unit,
    onFlipSelectedRegion: (horizontal: Boolean) -> Unit,
    onRotateSelectedRegion90: () -> Unit,
    onRotateSelectionByAngle: (Float) -> Unit,
    onSetSelectionRotationAngle: (Float) -> Unit,
    onResetSelectionRotation: () -> Unit,
    onClearSelectionRegion: () -> Unit
) {
    val bounds = studioState.selectionBounds
    val hasClipboard = studioState.clipboardPixels != null

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Row 1: Selection status + [Copy] [Cut] [Paste] [Deselect]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (bounds != null) {
                    "BOX (${bounds[1]},${bounds[0]})..(${bounds[3]},${bounds[2]})"
                } else {
                    "DRAG ON CANVAS TO SELECT"
                },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = if (bounds != null) NothingCrimsonRed else NothingTechnicalGray,
                fontSize = 8.5.sp
            )

            NothingCompactPill(
                label = "Copy",
                leadingIcon = Icons.Default.ContentCopy,
                selected = false,
                onClick = onCopySelectedRegion,
                fontSize = 10.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 10.dp,
                modifier = Modifier.height(44.dp).testTag("select_copy_button")
            )

            NothingCompactPill(
                label = "Cut",
                leadingIcon = Icons.Default.ContentCut,
                selected = false,
                onClick = onCutSelectedRegion,
                fontSize = 10.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 10.dp,
                modifier = Modifier.height(44.dp).testTag("select_cut_button")
            )

            NothingCompactPill(
                label = if (hasClipboard) "Paste @($cursorCol,$cursorRow)" else "Paste",
                leadingIcon = Icons.Default.ContentPaste,
                selected = hasClipboard,
                onClick = onPasteClipboardRegion,
                fontSize = 10.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 10.dp,
                modifier = Modifier.height(44.dp).testTag("select_paste_button")
            )

            if (bounds != null) {
                NothingCompactPill(
                    label = "Deselect",
                    selected = false,
                    onClick = onClearSelectionRegion,
                    fontSize = 10.sp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 10.dp,
                    modifier = Modifier.height(44.dp).testTag("select_deselect_button")
                )
            }
        }

        // Row 2: Region Nudge D-Pad + [Flip H] [Flip V] + Quick Angle Chips ([15°] [30°] [45°] [90°])
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StudioDpadButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Nudge Selection Left",
                onClick = { onNudgeSelectedRegion(-1, 0) },
                modifier = Modifier.testTag("select_nudge_left_button")
            )
            StudioDpadButton(
                icon = Icons.Default.KeyboardArrowUp,
                contentDescription = "Nudge Selection Up",
                onClick = { onNudgeSelectedRegion(0, -1) },
                modifier = Modifier.testTag("select_nudge_up_button")
            )
            StudioDpadButton(
                icon = Icons.Default.KeyboardArrowDown,
                contentDescription = "Nudge Selection Down",
                onClick = { onNudgeSelectedRegion(0, 1) },
                modifier = Modifier.testTag("select_nudge_down_button")
            )
            StudioDpadButton(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Nudge Selection Right",
                onClick = { onNudgeSelectedRegion(1, 0) },
                modifier = Modifier.testTag("select_nudge_right_button")
            )

            NothingCompactPill(
                label = "H",
                leadingIcon = Icons.Default.Flip,
                selected = false,
                onClick = { onFlipSelectedRegion(true) },
                fontSize = 10.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 10.dp,
                modifier = Modifier.height(44.dp).testTag("select_flip_h_button")
            )

            NothingCompactPill(
                label = "V",
                leadingIcon = Icons.Default.Flip,
                selected = false,
                onClick = { onFlipSelectedRegion(false) },
                fontSize = 10.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 10.dp,
                modifier = Modifier.height(44.dp).testTag("select_flip_v_button")
            )

            listOf(15f to "15°", 30f to "30°", 45f to "45°", 90f to "90°").forEach { (degVal, degLbl) ->
                val isSelected = abs(studioState.selectionRotationAngle - degVal) < 2f
                NothingCompactPill(
                    label = degLbl,
                    selected = isSelected,
                    onClick = {
                        if (degVal == 90f && studioState.selectionRotationAngle == 0f) {
                            onRotateSelectedRegion90()
                        } else {
                            onRotateSelectionByAngle(degVal)
                        }
                    },
                    fontSize = 10.sp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 10.dp,
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("select_rotate_${degVal.toInt()}_button")
                )
            }
        }

        // Row 3: Fine-Tuning 1°/5° Stepper + Continuous Rotation Dial (-180°..+180°) around Selection Centroid
        val selectDegInt = studioState.selectionRotationAngle.roundToInt()
        val selectFormattedDeg = if (selectDegInt > 0) "+${selectDegInt}°" else "${selectDegInt}°"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            NothingCompactPill(
                label = "-5°",
                leadingIcon = Icons.AutoMirrored.Filled.RotateLeft,
                selected = false,
                onClick = { onRotateSelectionByAngle(-5f) },
                fontSize = 9.sp,
                horizontalPadding = 7.dp,
                verticalPadding = 6.dp,
                modifier = Modifier.height(34.dp).testTag("select_rotate_step_minus_5_button")
            )
            NothingCompactPill(
                label = "-1°",
                leadingIcon = Icons.AutoMirrored.Filled.RotateLeft,
                selected = false,
                onClick = { onRotateSelectionByAngle(-1f) },
                fontSize = 9.sp,
                horizontalPadding = 7.dp,
                verticalPadding = 6.dp,
                modifier = Modifier.height(34.dp).testTag("select_rotate_step_minus_1_button")
            )

            Text(
                text = selectFormattedDeg,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = if (abs(studioState.selectionRotationAngle) > 0.5f) NothingCrimsonRed else NothingTechnicalGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(44.dp)
            )

            Slider(
                value = studioState.selectionRotationAngle,
                onValueChange = { onSetSelectionRotationAngle(it) },
                valueRange = -180f..180f,
                colors = SliderDefaults.colors(
                    thumbColor = NothingCrimsonRed,
                    activeTrackColor = NothingPureWhite,
                    inactiveTrackColor = NothingElevatedSurface
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .testTag("select_free_angle_slider")
            )

            NothingCompactPill(
                label = "+1°",
                leadingIcon = Icons.AutoMirrored.Filled.RotateRight,
                selected = false,
                onClick = { onRotateSelectionByAngle(1f) },
                fontSize = 9.sp,
                horizontalPadding = 7.dp,
                verticalPadding = 6.dp,
                modifier = Modifier.height(34.dp).testTag("select_rotate_step_plus_1_button")
            )
            NothingCompactPill(
                label = "+5°",
                leadingIcon = Icons.AutoMirrored.Filled.RotateRight,
                selected = false,
                onClick = { onRotateSelectionByAngle(5f) },
                fontSize = 9.sp,
                horizontalPadding = 7.dp,
                verticalPadding = 6.dp,
                modifier = Modifier.height(34.dp).testTag("select_rotate_step_plus_5_button")
            )

            if (abs(studioState.selectionRotationAngle) > 0.5f) {
                NothingCompactPill(
                    label = "Commit",
                    selected = false,
                    unselectedBorderColor = NothingCrimsonRed,
                    onClick = onResetSelectionRotation,
                    fontSize = 9.sp,
                    horizontalPadding = 8.dp,
                    verticalPadding = 6.dp,
                    modifier = Modifier.height(34.dp).testTag("select_reset_angle_button")
                )
            }
        }
    }
}

// =====================================================================
// CONTEXTUAL SHELF 3: GUIDES (Interactive Row/Column Guidelines, Axis Lock & Pocket Fill)
// =====================================================================
@Composable
private fun GuidesContextualShelf(
    studioState: StudioEditorState,
    cursorRow: Int,
    cursorCol: Int,
    onAddRowGuide: () -> Unit,
    onAddColGuide: () -> Unit,
    onMoveRowGuide: (Int, Int) -> Unit,
    onMoveColGuide: (Int, Int) -> Unit,
    onSelectActiveGuide: (Int?, Int?) -> Unit,
    onRemoveRowGuide: (Int) -> Unit,
    onRemoveColGuide: (Int) -> Unit,
    onClearAllGuides: () -> Unit,
    onSetAxisLockEnabled: (Boolean) -> Unit,
    onSetPocketFillMode: (Boolean) -> Unit,
    onFillPocketAtCursor: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Row 1: [+ Add Row Guide (Y)] [+ Add Col Guide (X)] [Axis Lock] [Pocket Fill] [Clear]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            NothingCompactPill(
                label = "+ Row Guide (Y:$cursorRow)",
                selected = false,
                unselectedBorderColor = Color(0xFF00E5FF),
                unselectedTextColor = Color(0xFF00E5FF),
                onClick = onAddRowGuide,
                fontSize = 10.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 10.dp,
                modifier = Modifier.height(44.dp).testTag("guides_add_row_button")
            )

            NothingCompactPill(
                label = "+ Col Guide (X:$cursorCol)",
                selected = false,
                unselectedBorderColor = Color(0xFFFF9100),
                unselectedTextColor = Color(0xFFFF9100),
                onClick = onAddColGuide,
                fontSize = 10.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 10.dp,
                modifier = Modifier.height(44.dp).testTag("guides_add_col_button")
            )

            NothingCompactPill(
                label = if (studioState.axisLockEnabled) "Axis Lock: ON" else "Axis Lock",
                leadingIcon = Icons.Default.Lock,
                selected = studioState.axisLockEnabled,
                onClick = { onSetAxisLockEnabled(!studioState.axisLockEnabled) },
                fontSize = 10.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 10.dp,
                modifier = Modifier.height(44.dp).testTag("guides_axis_lock_chip")
            )

            NothingCompactPill(
                label = if (studioState.pocketFillMode) "Pocket Mode: ON" else "Pocket Fill Mode",
                leadingIcon = Icons.Default.FormatColorFill,
                selected = studioState.pocketFillMode,
                onClick = { onSetPocketFillMode(!studioState.pocketFillMode) },
                fontSize = 10.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 10.dp,
                modifier = Modifier.height(44.dp).testTag("guides_pocket_fill_mode_chip")
            )

            NothingCompactPill(
                label = "Fill Pocket",
                selected = false,
                unselectedBorderColor = NothingCrimsonRed,
                unselectedTextColor = NothingPureWhite,
                onClick = onFillPocketAtCursor,
                fontSize = 10.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 10.dp,
                modifier = Modifier.height(44.dp).testTag("guides_fill_pocket_button")
            )

            if (studioState.rowGuides.isNotEmpty() || studioState.colGuides.isNotEmpty()) {
                NothingCompactPill(
                    label = "Clear Guides",
                    selected = false,
                    onClick = onClearAllGuides,
                    fontSize = 10.sp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 10.dp,
                    modifier = Modifier.height(44.dp).testTag("guides_clear_all_button")
                )
            }
        }

        // Row 2: Active Guide Chips & Steppers (or hint when empty)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (studioState.rowGuides.isEmpty() && studioState.colGuides.isEmpty()) {
                Text(
                    text = "Add Row (Cyan) or Col (Orange) guides to lock strokes or flood-fill bounded pockets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingTechnicalGray,
                    fontSize = 9.sp
                )
            } else {
                studioState.rowGuides.forEach { r ->
                    val isActive = studioState.activeRowGuide == r
                    NothingCompactPill(
                        label = "Y:$r",
                        selected = isActive,
                        unselectedBorderColor = Color(0xFF00E5FF),
                        unselectedTextColor = Color(0xFF00E5FF),
                        onClick = { onSelectActiveGuide(r, studioState.activeColGuide) },
                        fontSize = 9.sp,
                        horizontalPadding = 8.dp,
                        verticalPadding = 6.dp
                    )
                    if (isActive) {
                        NothingCompactPill(
                            label = "Y-1",
                            selected = false,
                            onClick = { onMoveRowGuide(r, (r - 1).coerceIn(0, 24)) },
                            fontSize = 9.sp,
                            horizontalPadding = 8.dp,
                            verticalPadding = 6.dp
                        )
                        NothingCompactPill(
                            label = "Y+1",
                            selected = false,
                            onClick = { onMoveRowGuide(r, (r + 1).coerceIn(0, 24)) },
                            fontSize = 9.sp,
                            horizontalPadding = 8.dp,
                            verticalPadding = 6.dp
                        )
                        NothingCompactPill(
                            label = "✕Y",
                            selected = false,
                            onClick = { onRemoveRowGuide(r) },
                            fontSize = 9.sp,
                            horizontalPadding = 8.dp,
                            verticalPadding = 6.dp
                        )
                    }
                }

                studioState.colGuides.forEach { c ->
                    val isActive = studioState.activeColGuide == c
                    NothingCompactPill(
                        label = "X:$c",
                        selected = isActive,
                        unselectedBorderColor = Color(0xFFFF9100),
                        unselectedTextColor = Color(0xFFFF9100),
                        onClick = { onSelectActiveGuide(studioState.activeRowGuide, c) },
                        fontSize = 9.sp,
                        horizontalPadding = 8.dp,
                        verticalPadding = 6.dp
                    )
                    if (isActive) {
                        NothingCompactPill(
                            label = "X-1",
                            selected = false,
                            onClick = { onMoveColGuide(c, (c - 1).coerceIn(0, 24)) },
                            fontSize = 9.sp,
                            horizontalPadding = 8.dp,
                            verticalPadding = 6.dp
                        )
                        NothingCompactPill(
                            label = "X+1",
                            selected = false,
                            onClick = { onMoveColGuide(c, (c + 1).coerceIn(0, 24)) },
                            fontSize = 9.sp,
                            horizontalPadding = 8.dp,
                            verticalPadding = 6.dp
                        )
                        NothingCompactPill(
                            label = "✕X",
                            selected = false,
                            onClick = { onRemoveColGuide(c) },
                            fontSize = 9.sp,
                            horizontalPadding = 8.dp,
                            verticalPadding = 6.dp
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// CONTEXTUAL SHELF 4: TRANSFORM (Symmetry, Clamp Mode, Canvas Nudge D-Pad, Granular 1°/5° + Continuous Rotation)
// =====================================================================
@Composable
private fun TransformContextualShelf(
    studioState: StudioEditorState,
    symmetryMode: SymmetryMode,
    onSymmetryModeChange: (SymmetryMode) -> Unit,
    onNudgeClampModeChange: (NudgeClampMode) -> Unit,
    onNudge: (dx: Int, dy: Int) -> Unit,
    onApplyTransform: (MatrixTransform) -> Unit,
    onRotateSelectionByAngle: (Float) -> Unit,
    onSetSelectionRotationAngle: (Float) -> Unit,
    onResetSelectionRotation: () -> Unit,
    onUndo: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Row 1: Symmetry Toggles + Clamp Mode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SymmetryMode.entries.forEach { mode ->
                NothingCompactPill(
                    label = mode.label,
                    selected = symmetryMode == mode,
                    onClick = { onSymmetryModeChange(mode) },
                    fontSize = 10.sp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 8.dp,
                    modifier = Modifier.height(40.dp).testTag("symmetry_${mode.name.lowercase()}_chip")
                )
            }

            NudgeClampMode.entries.forEach { mode ->
                NothingCompactPill(
                    label = mode.label,
                    selected = studioState.nudgeClampMode == mode,
                    onClick = { onNudgeClampModeChange(mode) },
                    fontSize = 10.sp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 8.dp,
                    modifier = Modifier.height(40.dp).testTag("clamp_mode_${mode.name.lowercase()}_chip")
                )
            }
        }

        // Row 2: 1-Pixel Canvas Nudge D-Pad + Rotate 90° / Mirror H / Mirror V
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("directional_dpad"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StudioDpadButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Shift Left",
                onClick = { onNudge(-1, 0) },
                modifier = Modifier.testTag("tool_shift_left_button")
            )
            StudioDpadButton(
                icon = Icons.Default.KeyboardArrowUp,
                contentDescription = "Shift Up",
                onClick = { onNudge(0, -1) },
                modifier = Modifier.testTag("tool_shift_up_button")
            )
            StudioDpadButton(
                icon = Icons.Default.KeyboardArrowDown,
                contentDescription = "Shift Down",
                onClick = { onNudge(0, 1) },
                modifier = Modifier.testTag("tool_shift_down_button")
            )
            StudioDpadButton(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Shift Right",
                onClick = { onNudge(1, 0) },
                modifier = Modifier.testTag("tool_shift_right_button")
            )

            NothingCompactPill(
                label = "90°",
                leadingIcon = Icons.AutoMirrored.Filled.RotateRight,
                selected = false,
                onClick = { onApplyTransform(MatrixTransform.ROTATE_90) },
                fontSize = 10.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 10.dp,
                modifier = Modifier.height(44.dp).testTag("tool_rotate_90_button")
            )

            NothingCompactPill(
                label = "H",
                leadingIcon = Icons.Default.Flip,
                selected = false,
                onClick = { onApplyTransform(MatrixTransform.MIRROR_HORIZONTAL) },
                fontSize = 10.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 10.dp,
                modifier = Modifier.height(44.dp).testTag("tool_mirror_h_button")
            )

            NothingCompactPill(
                label = "V",
                leadingIcon = Icons.Default.Flip,
                selected = false,
                onClick = { onApplyTransform(MatrixTransform.MIRROR_VERTICAL) },
                fontSize = 10.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 10.dp,
                modifier = Modifier.height(44.dp).testTag("tool_mirror_v_button")
            )
        }

        // Row 3: Fine-Tuning Rotation Wheel & Stepper ([↺ -1°] [↺ -5°] [-180°..+180° Dial] [+5° ↻] [+1° ↻])
        val degInt = studioState.selectionRotationAngle.roundToInt()
        val formattedDeg = if (degInt > 0) "+${degInt}°" else "${degInt}°"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            NothingCompactPill(
                label = "-1°",
                leadingIcon = Icons.AutoMirrored.Filled.RotateLeft,
                selected = false,
                onClick = { onRotateSelectionByAngle(-1f) },
                fontSize = 9.5.sp,
                horizontalPadding = 8.dp,
                verticalPadding = 6.dp,
                modifier = Modifier.height(36.dp).testTag("rotate_step_minus_1_button")
            )
            NothingCompactPill(
                label = "-5°",
                leadingIcon = Icons.AutoMirrored.Filled.RotateLeft,
                selected = false,
                onClick = { onRotateSelectionByAngle(-5f) },
                fontSize = 9.5.sp,
                horizontalPadding = 8.dp,
                verticalPadding = 6.dp,
                modifier = Modifier.height(36.dp).testTag("rotate_step_minus_5_button")
            )

            Text(
                text = formattedDeg,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = if (abs(studioState.selectionRotationAngle) > 0.5f) NothingCrimsonRed else NothingPureWhite,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(46.dp)
                    .testTag("rotation_degree_readout")
            )

            Slider(
                value = studioState.selectionRotationAngle,
                onValueChange = { onSetSelectionRotationAngle(it) },
                valueRange = -180f..180f,
                colors = SliderDefaults.colors(
                    thumbColor = NothingCrimsonRed,
                    activeTrackColor = NothingPureWhite,
                    inactiveTrackColor = NothingElevatedSurface
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .testTag("transform_free_angle_slider")
            )

            NothingCompactPill(
                label = "+5°",
                leadingIcon = Icons.AutoMirrored.Filled.RotateRight,
                selected = false,
                onClick = { onRotateSelectionByAngle(5f) },
                fontSize = 9.5.sp,
                horizontalPadding = 8.dp,
                verticalPadding = 6.dp,
                modifier = Modifier.height(36.dp).testTag("rotate_step_plus_5_button")
            )
            NothingCompactPill(
                label = "+1°",
                leadingIcon = Icons.AutoMirrored.Filled.RotateRight,
                selected = false,
                onClick = { onRotateSelectionByAngle(1f) },
                fontSize = 9.5.sp,
                horizontalPadding = 8.dp,
                verticalPadding = 6.dp,
                modifier = Modifier.height(36.dp).testTag("rotate_step_plus_1_button")
            )

            if (abs(studioState.selectionRotationAngle) > 0.5f) {
                NothingCompactPill(
                    label = "Commit",
                    selected = false,
                    unselectedBorderColor = NothingCrimsonRed,
                    onClick = onResetSelectionRotation,
                    fontSize = 9.sp,
                    horizontalPadding = 8.dp,
                    verticalPadding = 6.dp,
                    modifier = Modifier.height(36.dp).testTag("commit_rotation_button")
                )
            }
        }
    }
}

// =====================================================================
// CONTEXTUAL SHELF 5: LAYERS (Reorder via Drag-and-Drop, Multi-Select & Batch Actions)
// =====================================================================
@Composable
private fun LayersContextualShelf(
    studioState: StudioEditorState,
    onionSkinningEnabled: Boolean,
    onionOpacityPct: Int,
    onToggleOnionSkinning: () -> Unit,
    onSetOnionOpacity: (Int) -> Unit,
    onSelectLayer: (String) -> Unit,
    onToggleFullPreviewComposite: () -> Unit,
    onToggleLayerVisibility: (String) -> Unit,
    onRemoveLayer: (String) -> Unit,
    onMoveLayer: (String, Boolean) -> Unit,
    onReorderLayer: (fromIndex: Int, toIndex: Int) -> Unit,
    onUpdateLayer: (String, (DecompiledVisualLayer) -> DecompiledVisualLayer) -> Unit,
    onAddVisualLayer: (DecompiledVisualLayer) -> Unit,
    onToggleLayerBitmapMode: (String) -> Unit = {},
    onToggleLayerMultiSelectMode: (Boolean?) -> Unit = {},
    onToggleLayerCheckedForBatch: (String) -> Unit = {},
    onDuplicateSelectedLayers: () -> Unit = {},
    onDeleteSelectedLayers: () -> Unit = {},
    onMergeSelectedLayers: () -> Unit = {},
    onGroupOrMoveSelectedLayers: (Boolean) -> Unit = {}
) {
    val activeLayer = studioState.selectedVisualLayer
    val activeIdx = studioState.visualLayers.indexOfFirst { it.id == activeLayer?.id }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    if (showBatchDeleteConfirm) {
        val checkedCount = studioState.selectedLayerIdsForBatch.size.coerceAtLeast(1)
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            containerColor = NothingCarbonSurface,
            titleContentColor = NothingPureWhite,
            textContentColor = NothingTechnicalGray,
            title = {
                Text(
                    text = "DELETE $checkedCount SELECTED LAYER${if (checkedCount == 1) "" else "S"}?",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            },
            text = {
                Text(
                    text = "This will remove the checked layers from the rule hierarchy and update the composite preview.",
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBatchDeleteConfirm = false
                        onDeleteSelectedLayers()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NothingCrimsonRed),
                    modifier = Modifier.testTag("confirm_batch_delete_layers_button")
                ) {
                    Text("Delete Selected", color = NothingPureWhite)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showBatchDeleteConfirm = false }
                ) {
                    Text("Cancel", color = NothingPureWhite)
                }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Row 0: ENLARGED ONION SKIN LAYER SETTINGS + [Select Layers] Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = if (onionSkinningEnabled) NothingCrimsonRed else NothingElevatedSurface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    width = if (onionSkinningEnabled) 1.4.dp else 0.8.dp,
                    color = if (onionSkinningEnabled) NothingPureWhite else NothingBorderHairline
                ),
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleOnionSkinning)
                    .testTag("timeline_onion_skin_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Onion Skinning",
                        tint = NothingPureWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${onionOpacityPct}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = NothingPureWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "GHOST:",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = NothingTechnicalGray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )

            listOf(10 to "10%", 20 to "20%", 30 to "30%", 50 to "50%").forEach { (pctVal, labelStr) ->
                val isSelected = onionSkinningEnabled && onionOpacityPct == pctVal
                NothingCompactPill(
                    label = labelStr,
                    selected = isSelected,
                    onClick = { onSetOnionOpacity(pctVal) },
                    fontSize = 10.sp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 8.dp,
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("onion_opacity_${pctVal}_chip")
                )
            }
        }

        // Row 1: [Composite 30%] + [Select Layers] + [+ TEXT LAYER] + [+ RING LAYER]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NothingCompactPill(
                label = if (studioState.showFullPreviewComposite) "Composite 30%: ON" else "Composite: OFF",
                leadingIcon = Icons.Default.Layers,
                selected = studioState.showFullPreviewComposite,
                onClick = onToggleFullPreviewComposite,
                fontSize = 10.5.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 8.dp,
                modifier = Modifier
                    .height(42.dp)
                    .testTag("toggle_full_preview_composite_chip")
            )

            NothingCompactPill(
                label = if (studioState.isLayerMultiSelectMode) {
                    "Select Layers (${studioState.selectedLayerIdsForBatch.size})"
                } else {
                    "Select Layers"
                },
                leadingIcon = Icons.Default.SelectAll,
                selected = studioState.isLayerMultiSelectMode,
                unselectedBorderColor = NothingCrimsonRed,
                onClick = { onToggleLayerMultiSelectMode(null) },
                fontSize = 10.5.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 8.dp,
                modifier = Modifier
                    .height(42.dp)
                    .testTag("layers_select_mode_toggle_button")
            )

            NothingCompactPill(
                label = "+ TEXT LAYER",
                selected = false,
                unselectedBorderColor = NothingCrimsonRed,
                unselectedTextColor = NothingPureWhite,
                onClick = {
                    val newLayer = DecompiledVisualLayer(
                        name = "text_${studioState.visualLayers.size + 1}",
                        primitiveType = "TEXT",
                        textContent = "GLYPH",
                        yOffset = 16
                    )
                    onAddVisualLayer(newLayer)
                    onSelectLayer(newLayer.id)
                },
                fontSize = 10.5.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 8.dp,
                modifier = Modifier
                    .height(42.dp)
                    .testTag("add_text_layer_button")
            )

            NothingCompactPill(
                label = "+ RING LAYER",
                selected = false,
                onClick = {
                    val newLayer = DecompiledVisualLayer(
                        name = "ring_${studioState.visualLayers.size + 1}",
                        primitiveType = "RING",
                        ringRadius = 12,
                        ringThickness = 1
                    )
                    onAddVisualLayer(newLayer)
                    onSelectLayer(newLayer.id)
                },
                fontSize = 10.5.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 8.dp,
                modifier = Modifier
                    .height(42.dp)
                    .testTag("add_ring_layer_button")
            )
        }

        // Row 2: Reorderable Layer Hierarchy Strip (Drag-and-Drop Handles + Multi-Select Checkboxes)
        if (studioState.visualLayers.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag("layers_hierarchy_list"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                studioState.visualLayers.forEachIndexed { idx, layer ->
                    val isSelected = layer.id == activeLayer?.id
                    val isChecked = layer.id in studioState.selectedLayerIdsForBatch
                    var dragAccumPx by remember(layer.id, idx) { mutableFloatStateOf(0f) }

                    Surface(
                        color = if (isSelected) NothingCrimsonRed.copy(alpha = 0.22f) else NothingElevatedSurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            width = if (isSelected || isChecked) 1.2.dp else 0.7.dp,
                            color = when {
                                isChecked -> NothingCrimsonRed
                                isSelected -> NothingPureWhite
                                else -> NothingBorderHairline
                            }
                        ),
                        modifier = Modifier
                            .height(40.dp)
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    dragAccumPx += delta
                                    if (dragAccumPx <= -26f && idx > 0) {
                                        dragAccumPx = 0f
                                        onReorderLayer(idx, idx - 1)
                                    } else if (dragAccumPx >= 26f && idx < studioState.visualLayers.lastIndex) {
                                        dragAccumPx = 0f
                                        onReorderLayer(idx, idx + 1)
                                    }
                                }
                            )
                            .clickable {
                                if (studioState.isLayerMultiSelectMode) {
                                    onToggleLayerCheckedForBatch(layer.id)
                                } else {
                                    onSelectLayer(layer.id)
                                }
                            }
                            .testTag("layer_hierarchy_item_${layer.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Drag handle icon + Z-index badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(1.dp),
                                modifier = Modifier.testTag("layer_drag_handle_${layer.name}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DragHandle,
                                    contentDescription = "Reorder Layer Z-Index",
                                    tint = NothingTechnicalGray,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Z${idx + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = NothingTechnicalGray,
                                    fontSize = 8.5.sp
                                )
                            }

                            if (studioState.isLayerMultiSelectMode) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onToggleLayerCheckedForBatch(layer.id) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NothingCrimsonRed,
                                        uncheckedColor = NothingTechnicalGray,
                                        checkmarkColor = NothingPureWhite
                                    ),
                                    modifier = Modifier
                                        .size(22.dp)
                                        .testTag("layer_checkbox_${layer.name}")
                                )
                            }

                            Text(
                                text = layer.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = NothingPureWhite,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 110.dp)
                            )

                            if (idx > 0) {
                                StudioBareActionIcon(
                                    icon = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Reorder Layer Up",
                                    tint = NothingPureWhite,
                                    size = 22.dp,
                                    iconSize = 14.dp,
                                    onClick = { onReorderLayer(idx, idx - 1) },
                                    modifier = Modifier.testTag("layer_reorder_up_${layer.name}")
                                )
                            }
                            if (idx < studioState.visualLayers.lastIndex) {
                                StudioBareActionIcon(
                                    icon = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Reorder Layer Down",
                                    tint = NothingPureWhite,
                                    size = 22.dp,
                                    iconSize = 14.dp,
                                    onClick = { onReorderLayer(idx, idx + 1) },
                                    modifier = Modifier.testTag("layer_reorder_down_${layer.name}")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Row 2B: Batch Actions Bar (Surfaces when Multi-Select Mode is active, Icon-First)
        if (studioState.isLayerMultiSelectMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag("layers_batch_actions_bar"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                NothingCompactPill(
                    label = "Duplicate Selected",
                    leadingIcon = Icons.Default.ContentCopy,
                    selected = false,
                    onClick = onDuplicateSelectedLayers,
                    fontSize = 9.5.sp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 7.dp,
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("batch_duplicate_layers_button")
                )

                NothingCompactPill(
                    label = "Merge Selected",
                    leadingIcon = Icons.AutoMirrored.Filled.CallMerge,
                    selected = false,
                    unselectedBorderColor = NothingCrimsonRed,
                    onClick = onMergeSelectedLayers,
                    fontSize = 9.5.sp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 7.dp,
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("batch_merge_layers_button")
                )

                NothingCompactPill(
                    label = "Group / Move ↑",
                    leadingIcon = Icons.Default.DragHandle,
                    selected = false,
                    onClick = { onGroupOrMoveSelectedLayers(true) },
                    fontSize = 9.5.sp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 7.dp,
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("batch_group_move_layers_button")
                )

                NothingCompactPill(
                    label = "Delete Selected",
                    leadingIcon = Icons.Default.Delete,
                    selected = false,
                    unselectedBorderColor = NothingCrimsonRed,
                    unselectedTextColor = NothingCrimsonRed,
                    onClick = { showBatchDeleteConfirm = true },
                    fontSize = 9.5.sp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 7.dp,
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("batch_delete_layers_button")
                )
            }
        }

        // Row 3: Active Layer Controls (Visibility, Z-Order ↑/↓, Y-offset or Radius, Delete)
        if (activeLayer != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NothingPillBadge(
                    text = "${activeLayer.primitiveType}: ${activeLayer.name}",
                    color = NothingCrimsonRed
                )

                NothingCompactPill(
                    label = if (activeLayer.isCanvasEdited || activeLayer.primitiveType in setOf("BITMAP", "SEQUENCE")) {
                        "Pixel Dots Mode"
                    } else {
                        "Convert to Pixel Dots"
                    },
                    selected = activeLayer.isCanvasEdited || activeLayer.primitiveType in setOf("BITMAP", "SEQUENCE"),
                    onClick = { onToggleLayerBitmapMode(activeLayer.id) },
                    fontSize = 9.sp,
                    horizontalPadding = 8.dp,
                    verticalPadding = 4.dp,
                    modifier = Modifier.testTag("layer_toggle_bitmap_mode_${activeLayer.id}")
                )

                StudioBareActionIcon(
                    icon = if (activeLayer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Toggle Layer Visibility",
                    tint = if (activeLayer.isVisible) NothingPureWhite else NothingTechnicalGray,
                    onClick = { onToggleLayerVisibility(activeLayer.id) },
                    modifier = Modifier.testTag("layer_toggle_visibility_${activeLayer.id}")
                )

                StudioBareActionIcon(
                    icon = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Move Layer Up",
                    tint = if (activeIdx > 0) NothingPureWhite else NothingTechnicalGray.copy(alpha = 0.4f),
                    enabled = activeIdx > 0,
                    onClick = { onMoveLayer(activeLayer.id, true) }
                )

                StudioBareActionIcon(
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Move Layer Down",
                    tint = if (activeIdx < studioState.visualLayers.lastIndex) NothingPureWhite else NothingTechnicalGray.copy(alpha = 0.4f),
                    enabled = activeIdx < studioState.visualLayers.lastIndex,
                    onClick = { onMoveLayer(activeLayer.id, false) }
                )

                if (activeLayer.primitiveType == "TEXT") {
                    NothingCompactPill(
                        label = "Y:${activeLayer.yOffset} (-1)",
                        selected = false,
                        onClick = {
                            onUpdateLayer(activeLayer.id) {
                                it.copy(yOffset = (it.yOffset - 1).coerceIn(0, 20))
                            }
                        },
                        fontSize = 8.sp,
                        horizontalPadding = 6.dp,
                        verticalPadding = 3.dp
                    )
                    NothingCompactPill(
                        label = "Y+1",
                        selected = false,
                        onClick = {
                            onUpdateLayer(activeLayer.id) {
                                it.copy(yOffset = (it.yOffset + 1).coerceIn(0, 20))
                            }
                        },
                        fontSize = 8.sp,
                        horizontalPadding = 6.dp,
                        verticalPadding = 3.dp
                    )
                } else if (activeLayer.primitiveType == "RING") {
                    NothingCompactPill(
                        label = "R:${activeLayer.ringRadius}",
                        selected = false,
                        onClick = {
                            onUpdateLayer(activeLayer.id) {
                                val nextR = if (it.ringRadius >= 12) 10 else it.ringRadius + 1
                                it.copy(ringRadius = nextR)
                            }
                        },
                        fontSize = 8.sp,
                        horizontalPadding = 6.dp,
                        verticalPadding = 3.dp
                    )
                    NothingCompactPill(
                        label = "${activeLayer.ringThickness}PX",
                        selected = false,
                        onClick = {
                            onUpdateLayer(activeLayer.id) {
                                val nextT = if (it.ringThickness >= 3) 1 else it.ringThickness + 1
                                it.copy(ringThickness = nextT)
                            }
                        },
                        fontSize = 8.sp,
                        horizontalPadding = 6.dp,
                        verticalPadding = 3.dp
                    )
                }

                StudioBareActionIcon(
                    icon = Icons.Default.DeleteOutline,
                    contentDescription = "Remove Layer",
                    tint = NothingCrimsonRed,
                    onClick = { onRemoveLayer(activeLayer.id) },
                    modifier = Modifier.testTag("layer_remove_${activeLayer.id}")
                )
            }
        } else {
            Text(
                text = "Base 489-LED Canvas Layer • Tap +TEXT or +RING to add composite layers.",
                style = MaterialTheme.typography.bodySmall,
                color = NothingTechnicalGray,
                fontSize = 9.sp
            )
        }
    }
}

// =====================================================================
// CONTEXTUAL SHELF 5B: ANIMATION (Responsive Speed Presets & Playback Styles)
// • Fast (66ms / ~15 FPS) • Standard (110ms) • Smooth (165ms) • Slow (330ms)
// • Loop (CW/Normal) • Ping-Pong (Bounce) • Once
// =====================================================================
@Composable
private fun AnimationContextualShelf(
    studioState: StudioEditorState,
    onFrameDurationChange: (Long) -> Unit,
    onPlaybackModeChange: (AnimationPlaybackMode) -> Unit,
    onTransitionCurveChange: (TransitionCurve) -> Unit,
    onApplyProceduralAnimationPreset: (String) -> Unit,
    onTogglePreview: () -> Unit
) {
    val currentMs = studioState.activeFrame.durationMs
    val activeMacro = studioState.selectedVisualLayer?.animationMacro

    val proceduralPresets = remember {
        listOf(
            "NONE" to "Static / None",
            "SLIDE_UP" to "Slide Up",
            "SLIDE_DOWN" to "Slide Down",
            "SLIDE_RIGHT" to "Slide Right",
            "BOUNCE" to "Bounce",
            "RADAR_SPIN" to "Radar Spin",
            "PULSE" to "Pulse",
            "BREATHE" to "Breathe"
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Row 0: One-Tap Procedural Code Animation Macros (Eliminates Manual Keyframe Drawing)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "MACRO:",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = NothingCrimsonRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            proceduralPresets.forEach { (presetKey, labelStr) ->
                val isSelected = when (presetKey) {
                    "NONE" -> activeMacro == null
                    "SLIDE_UP" -> activeMacro is LayerAnimationMacro.SlideMacro && activeMacro.toY < activeMacro.fromY
                    "SLIDE_DOWN" -> activeMacro is LayerAnimationMacro.SlideMacro && activeMacro.toY > activeMacro.fromY
                    "SLIDE_RIGHT" -> activeMacro is LayerAnimationMacro.SlideMacro && activeMacro.toX > activeMacro.fromX
                    "BOUNCE" -> activeMacro is LayerAnimationMacro.BounceMacro
                    "RADAR_SPIN" -> activeMacro is LayerAnimationMacro.SpinMacro
                    "PULSE" -> activeMacro is LayerAnimationMacro.PulseMacro && !activeMacro.isBreathe
                    "BREATHE" -> activeMacro is LayerAnimationMacro.PulseMacro && activeMacro.isBreathe
                    else -> false
                }
                NothingCompactPill(
                    label = labelStr,
                    selected = isSelected,
                    onClick = { onApplyProceduralAnimationPreset(presetKey) },
                    fontSize = 11.sp,
                    horizontalPadding = 12.dp,
                    verticalPadding = 10.dp,
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("anim_macro_preset_${presetKey.lowercase()}")
                )
            }
        }

        // Row 1: Responsive Speed Presets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "SPEED:",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = NothingTechnicalGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            SpeedPresetOption.entries.forEach { preset ->
                val isSelected = abs(currentMs - preset.durationMs) <= 15L
                NothingCompactPill(
                    label = preset.title,
                    selected = isSelected,
                    onClick = { onFrameDurationChange(preset.durationMs) },
                    fontSize = 11.sp,
                    horizontalPadding = 12.dp,
                    verticalPadding = 10.dp,
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("anim_speed_preset_${preset.durationMs}ms")
                )
            }
        }

        // Row 2: Animation Playback Styles & Transition Curves
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "STYLE:",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = NothingTechnicalGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            AnimationPlaybackMode.entries.forEach { mode ->
                NothingCompactPill(
                    label = mode.label,
                    selected = studioState.playbackMode == mode,
                    onClick = { onPlaybackModeChange(mode) },
                    fontSize = 11.sp,
                    horizontalPadding = 12.dp,
                    verticalPadding = 10.dp,
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("anim_playback_mode_${mode.name.lowercase()}")
                )
            }

            TransitionCurve.entries.forEach { curve ->
                NothingCompactPill(
                    label = curve.name.replace('_', ' '),
                    selected = studioState.transitionCurve == curve,
                    onClick = { onTransitionCurveChange(curve) },
                    fontSize = 10.sp,
                    horizontalPadding = 10.dp,
                    verticalPadding = 8.dp,
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("anim_curve_${curve.name.lowercase()}")
                )
            }
        }
    }
}

// =====================================================================
// CONTEXTUAL SHELF 6: PARAMS (Compact Single-Shelf Fallback)
// =====================================================================
@Composable
private fun ParamsContextualShelf(
    studioState: StudioEditorState,
    savedAnimations: List<GlyphAnimation>,
    onNameChange: (String) -> Unit,
    onGlobalAlignChange: (String) -> Unit,
    onSaveAnimation: () -> Unit,
    onSaveAndApplyRule: () -> Unit,
    onLoadAnimation: (GlyphAnimation) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("center", "top_center").forEach { align ->
            NothingCompactPill(
                label = align.uppercase(),
                selected = studioState.globalAlign == align,
                onClick = { onGlobalAlignChange(align) },
                fontSize = 10.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 8.dp,
                modifier = Modifier.height(42.dp).testTag("align_${align}_chip")
            )
        }

        NothingCompactPill(
            label = "+ SAVE",
            selected = false,
            unselectedBorderColor = NothingCrimsonRed,
            unselectedTextColor = NothingPureWhite,
            onClick = onSaveAnimation,
            fontSize = 10.sp,
            horizontalPadding = 10.dp,
            verticalPadding = 8.dp,
            modifier = Modifier.height(42.dp).testTag("studio_save_button")
        )

        savedAnimations.forEach { anim ->
            NothingCompactPill(
                label = anim.name,
                selected = anim.id == studioState.editingAnimationId,
                onClick = { onLoadAnimation(anim) },
                fontSize = 10.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 8.dp,
                modifier = Modifier.height(42.dp)
            )
        }
    }
}

// =====================================================================
// CONTEXTUAL SHELF 7: PHOTO & EMOJI RASTERIZER PIPELINE
// =====================================================================
@Composable
private fun AiPhotoContextualShelf(
    studioState: StudioEditorState,
    onUpdateAiPrompt: (String) -> Unit = {},
    onGenerateAiPrompt: (String?) -> Unit = {},
    onImportPhotoLauncher: () -> Unit,
    onLoadSamplePhoto: () -> Unit = {},
    onUpdatePhotoSettings: (Float, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onUpdateEmojiInput: (String) -> Unit = {},
    onRasterizeEmoji: (String?) -> Unit = {},
    onUpdatePhotoThresholdPct: (Int) -> Unit = {},
    onTogglePhotoEdgeDetect: (Boolean?) -> Unit = {}
) {
    var showSingleEmojiDialog by remember { mutableStateOf(false) }
    var singleEmojiDraft by remember(studioState.emojiInput) {
        mutableStateOf(studioState.emojiInput.ifBlank { "😀" })
    }

    if (showSingleEmojiDialog) {
        AlertDialog(
            onDismissRequest = { showSingleEmojiDialog = false },
            containerColor = NothingCarbonSurface,
            tonalElevation = 6.dp,
            title = {
                Text(
                    text = "SELECT EMOJI GLYPH",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = NothingPureWhite,
                    fontSize = 13.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Type or paste 1 emoji to rasterize onto 489-LED matrix:",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = NothingTechnicalGray,
                        fontSize = 11.sp
                    )

                    // Centered single-character box (64.dp) with large font size
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(NothingElevatedSurface, RoundedCornerShape(12.dp))
                            .border(1.5.dp, NothingCrimsonRed, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicTextField(
                            value = singleEmojiDraft,
                            onValueChange = { newVal ->
                                if (newVal.isNotEmpty()) {
                                    val codePoints = newVal.codePoints().toArray()
                                    val firstChar = if (codePoints.isNotEmpty()) {
                                        String(codePoints, 0, 1)
                                    } else newVal.takeLast(1)
                                    singleEmojiDraft = firstChar
                                } else {
                                    singleEmojiDraft = ""
                                }
                            },
                            singleLine = true,
                            maxLines = 1,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 32.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = NothingPureWhite
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("single_emoji_input_box")
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val emojiToStamp = singleEmojiDraft.ifBlank { "😀" }
                        onUpdateEmojiInput(emojiToStamp)
                        onRasterizeEmoji(emojiToStamp)
                        showSingleEmojiDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NothingCrimsonRed,
                        contentColor = NothingPureWhite
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("stamp_emoji_button")
                ) {
                    Text(
                        text = "OK / Stamp",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showSingleEmojiDialog = false },
                    border = BorderStroke(1.dp, NothingBorderHairline),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Cancel",
                        fontFamily = FontFamily.Monospace,
                        color = NothingTechnicalGray,
                        fontSize = 11.sp
                    )
                }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // Row 1: Compact Emoji Trigger Pill + Native Photo Import + Sobel Edge Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Compact [😀 Select Emoji] trigger pill
            val activeEmoji = studioState.emojiInput.ifBlank { "😀" }
            NothingCompactPill(
                label = "$activeEmoji Select Emoji",
                selected = false,
                unselectedBorderColor = NothingCrimsonRed,
                onClick = {
                    singleEmojiDraft = activeEmoji
                    showSingleEmojiDialog = true
                },
                fontSize = 10.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 6.dp,
                modifier = Modifier
                    .height(38.dp)
                    .testTag("select_emoji_trigger_pill")
            )

            // Direct Native Gallery Photo Import
            NothingCompactPill(
                label = "Import Photo",
                leadingIcon = Icons.Default.Image,
                selected = false,
                onClick = onImportPhotoLauncher,
                fontSize = 9.5.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 6.dp,
                modifier = Modifier
                    .height(38.dp)
                    .testTag("photo_to_glyph_import_button")
            )

            // Sobel Edge Detection Toggle
            NothingCompactPill(
                label = if (studioState.photoEdgeDetectEnabled) "Sobel Edge: ON" else "Sobel Edge: OFF",
                selected = studioState.photoEdgeDetectEnabled,
                onClick = { onTogglePhotoEdgeDetect(null) },
                fontSize = 9.5.sp,
                horizontalPadding = 10.dp,
                verticalPadding = 6.dp,
                modifier = Modifier
                    .height(38.dp)
                    .testTag("photo_edge_detect_toggle")
            )
        }

        // Row 2: Live Contrast / Threshold: 0% - 100% Slider (Otsu + Sobel Silhouette Fine-Tuning)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Contrast / Threshold: ${studioState.photoThresholdPct}%",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = NothingPureWhite,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("ai_photo_threshold_label")
            )

            Slider(
                value = studioState.photoThresholdPct.toFloat(),
                onValueChange = { onUpdatePhotoThresholdPct(it.roundToInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = NothingCrimsonRed,
                    activeTrackColor = NothingPureWhite,
                    inactiveTrackColor = NothingElevatedSurface
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .testTag("ai_photo_threshold_slider")
            )
        }
    }
}

// =====================================================================
// CONTEXTUAL SHELF 8: RELATIVE PWM BRIGHTNESS & LIGHTING TOOL
// =====================================================================
@Composable
private fun LightingContextualShelf(
    studioState: StudioEditorState,
    onAdjustRelativePwm: (deltaPercent: Int, selectionOnly: Boolean?) -> Unit,
    onSetLightingTargetSelectionOnly: (Boolean) -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(0f) }
    val stepDeltas = remember { listOf(-25, -10, -5, 5, 10, 25) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: Scope toggle + Quick Step Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Scope Toggle Button
            val isSelectionOnly = studioState.lightingTargetSelectionOnly
            Surface(
                color = if (isSelectionOnly) NothingCrimsonRed else NothingElevatedSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSelectionOnly) NothingPureWhite else NothingBorderHairline
                ),
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSetLightingTargetSelectionOnly(!isSelectionOnly) }
                    .testTag("lighting_scope_toggle_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isSelectionOnly) Icons.Default.CropFree else Icons.Default.Fullscreen,
                        contentDescription = null,
                        tint = NothingPureWhite,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isSelectionOnly) "Target: Selection" else "Target: Active Frame",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = NothingPureWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Quick Step Nudge Buttons: [-25%], [-10%], [-5%], [+5%], [+10%], [+25%]
            stepDeltas.forEach { delta ->
                val label = if (delta > 0) "+$delta%" else "$delta%"
                NothingCompactPill(
                    label = label,
                    selected = false,
                    onClick = {
                        onAdjustRelativePwm(delta, null)
                    },
                    fontSize = 10.sp,
                    horizontalPadding = 9.dp,
                    verticalPadding = 6.dp,
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("lighting_step_button_${if (delta > 0) "plus" else "minus"}_${kotlin.math.abs(delta)}")
                )
            }
        }

        // Row 2: Relative Brightness Delta Slider (-100% to +100%) + Apply Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val deltaInt = sliderValue.roundToInt()
            Text(
                text = "Delta: ${if (deltaInt >= 0) "+$deltaInt%" else "$deltaInt%"}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = NothingPureWhite,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("lighting_slider_readout")
            )

            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                valueRange = -100f..100f,
                steps = 39,
                colors = SliderDefaults.colors(
                    thumbColor = NothingCrimsonRed,
                    activeTrackColor = NothingCrimsonRed,
                    inactiveTrackColor = NothingElevatedSurface
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .testTag("lighting_slider")
            )

            // Apply Slider Delta Button
            Surface(
                color = if (deltaInt != 0) NothingCrimsonRed else NothingCarbonSurface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, if (deltaInt != 0) NothingPureWhite else NothingBorderHairline),
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = deltaInt != 0) {
                        onAdjustRelativePwm(deltaInt, null)
                        sliderValue = 0f
                    }
                    .testTag("lighting_apply_slider_button")
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "APPLY",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (deltaInt != 0) NothingPureWhite else NothingTechnicalGray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// =====================================================================
// COMPACT STUDIO UI PRIMITIVES
// =====================================================================
@Composable
private fun StudioBareActionIcon(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 30.dp,
    iconSize: androidx.compose.ui.unit.Dp = 18.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bare_icon_press_scale"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun StudioDpadButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dpad_press_scale"
    )
    Surface(
        color = NothingElevatedSurface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.8.dp, NothingBorderHairline),
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = NothingPureWhite,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun StudioToolCarouselButton(
    label: String,
    icon: ImageVector? = null,
    isSelected: Boolean,
    borderColor: Color? = null,
    textColor: Color = NothingPureWhite,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tool_carousel_press_scale"
    )
    Surface(
        color = if (isSelected) NothingCrimsonRed else NothingCarbonSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 1.2.dp else 0.8.dp,
            color = borderColor ?: if (isSelected) NothingPureWhite else NothingBorderHairline
        ),
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) NothingPureWhite else (borderColor ?: textColor),
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) NothingPureWhite else textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ScaleContextualShelf(
    studioState: StudioEditorState,
    onSetScalePercent: (Int) -> Unit,
    onStepScalePercent: (Int) -> Unit,
    onToggleAspectLock: () -> Unit,
    onFitToGuides: () -> Unit,
    onCommitScale: () -> Unit,
    onCancelScale: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: Aspect Ratio Lock + Fit to Guides + Precision Steppers + Action Buttons (Apply / Cancel)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Aspect Ratio Toggle
            val isLocked = studioState.scaleAspectLocked
            Surface(
                color = if (isLocked) NothingCrimsonRed else NothingElevatedSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    1.dp,
                    if (isLocked) NothingPureWhite else NothingBorderHairline
                ),
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleAspectLock)
                    .testTag("scale_aspect_lock_toggle")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock 1:1 Aspect Ratio",
                        tint = NothingPureWhite,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (isLocked) "Lock 1:1: ON" else "Lock 1:1: OFF",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = NothingPureWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Snap to Guides Chip
            Surface(
                color = NothingElevatedSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.8.dp, NothingBorderHairline),
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onFitToGuides)
                    .testTag("scale_fit_to_guides_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = "Fit to Guides",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Fit to Guides",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = NothingPureWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Precision Stepper Buttons: [-5%], [-1%], [100% Reset], [+1%], [+5%]
            NothingCompactPill(
                label = "-5%",
                selected = false,
                onClick = { onStepScalePercent(-5) },
                fontSize = 10.sp,
                horizontalPadding = 8.dp,
                verticalPadding = 6.dp,
                modifier = Modifier
                    .height(34.dp)
                    .testTag("scale_step_minus_5")
            )
            NothingCompactPill(
                label = "-1%",
                selected = false,
                onClick = { onStepScalePercent(-1) },
                fontSize = 10.sp,
                horizontalPadding = 8.dp,
                verticalPadding = 6.dp,
                modifier = Modifier
                    .height(34.dp)
                    .testTag("scale_step_minus_1")
            )
            NothingCompactPill(
                label = "100%",
                selected = studioState.scalePercent == 100,
                onClick = { onSetScalePercent(100) },
                fontSize = 10.sp,
                horizontalPadding = 8.dp,
                verticalPadding = 6.dp,
                modifier = Modifier
                    .height(34.dp)
                    .testTag("scale_reset_100")
            )
            NothingCompactPill(
                label = "+1%",
                selected = false,
                onClick = { onStepScalePercent(1) },
                fontSize = 10.sp,
                horizontalPadding = 8.dp,
                verticalPadding = 6.dp,
                modifier = Modifier
                    .height(34.dp)
                    .testTag("scale_step_plus_1")
            )
            NothingCompactPill(
                label = "+5%",
                selected = false,
                onClick = { onStepScalePercent(5) },
                fontSize = 10.sp,
                horizontalPadding = 8.dp,
                verticalPadding = 6.dp,
                modifier = Modifier
                    .height(34.dp)
                    .testTag("scale_step_plus_5")
            )

            // Commit / Apply Scale
            Surface(
                color = NothingCrimsonRed,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, NothingPureWhite),
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onCommitScale)
                    .testTag("scale_apply_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Apply Scale",
                        tint = NothingPureWhite,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Apply",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = NothingPureWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Cancel / Reset Scale
            Surface(
                color = NothingElevatedSurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.8.dp, NothingBorderHairline),
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onCancelScale)
                    .testTag("scale_cancel_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Scale",
                        tint = NothingTechnicalGray,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = NothingTechnicalGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Row 2: Live Numeric Readout + Continuous 5%..300% Scale Slider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val scalePct = studioState.scalePercent
            Text(
                text = "Scale: $scalePct%",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = NothingPureWhite,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("scale_slider_readout")
            )

            Slider(
                value = scalePct.toFloat().coerceIn(5f, 300f),
                onValueChange = { onSetScalePercent(it.roundToInt()) },
                valueRange = 5f..300f,
                colors = SliderDefaults.colors(
                    thumbColor = NothingCrimsonRed,
                    activeTrackColor = NothingCrimsonRed,
                    inactiveTrackColor = NothingElevatedSurface
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .testTag("scale_percentage_slider")
            )
        }
    }
}

