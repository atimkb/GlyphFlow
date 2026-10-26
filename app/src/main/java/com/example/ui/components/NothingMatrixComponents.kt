package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.MATRIX_SIZE
import com.example.hardware.GlyphAperture
import com.example.hardware.GlyphHardwareController
import com.example.hardware.HardwareMatrixState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.example.ui.theme.NothingBorderHairline
import com.example.ui.theme.NothingCarbonSurface
import com.example.ui.theme.NothingCrimsonRed
import com.example.ui.theme.NothingElevatedSurface
import com.example.ui.theme.NothingGridUnlit
import com.example.ui.theme.NothingPureBlack
import com.example.ui.theme.NothingPureWhite
import com.example.ui.theme.NothingTechnicalGray
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Canonical 489-LED Circular Matrix Renderer (`CircularGlyphMatrixPreview`) shared across:
 * - Console Tab (Dry Run Live Simulation)
 * - Dashboard Tab (Active Rule Cards & Compact Live Matrix Card)
 * - Studio Tab (Maximized Viewfinder Drawing Canvas)
 * - Audit Log Tab (Live Inspector Previews)
 *
 * Physical Coordinate Constraints:
 * - Bounding envelope: 25x25 (x in [0, 24], y in [0, 24]).
 * - Origin / Center LED: (12, 12) with subtle crosshair guides in editor mode.
 * - Physical Active Condition: (x - 12)^2 + (y - 12)^2 <= 156.25 (489 addressable LEDs).
 * - Dead Corner Pixels (136 non-physical coordinates): Rendered 100% transparent with no outline,
 *   stroke, or touch hit interception.
 * - Circular Outer Bezel: Clips perimeter cleanly with a subtle Nothing-style outer accent ring.
 */
@Composable
fun CircularGlyphMatrixPreview(
    pixels: ByteArray,
    modifier: Modifier = Modifier,
    ghostPixels: ByteArray? = null,
    onionOpacityAlpha: Float = 0.20f,
    referenceOverlayPixels: ByteArray? = null,
    previewOverlayPixels: ByteArray? = null,
    interactive: Boolean = false,
    showCrosshairGuides: Boolean = false,
    showCircularBezelOverlay: Boolean = true,
    showPwmHeatmap: Boolean = false,
    zoomFactor: Float = 1.0f,
    panOffsetX: Float = 0f,
    panOffsetY: Float = 0f,
    highlightedCell: Pair<Int, Int>? = null,
    rowGuides: List<Int> = emptyList(),
    colGuides: List<Int> = emptyList(),
    activeRowGuide: Int? = null,
    activeColGuide: Int? = null,
    axisLockEnabled: Boolean = false,
    selectionBounds: IntArray? = null, // [minRow, minCol, maxRow, maxCol]
    linePreviewVector: Pair<Pair<Int, Int>, Pair<Int, Int>>? = null,
    circlePreviewGeometry: Triple<Int, Int, Int>? = null, // (centerRow, centerCol, radius)
    reticleFreeDragOnly: Boolean = false,
    isScaleMode: Boolean = false,
    scaleBoundingBox: FloatArray? = null, // [minRow, minCol, maxRow, maxCol]
    onScaleHandleDrag: ((handleIndex: Int, newMinR: Float, newMinC: Float, newMaxR: Float, newMaxC: Float) -> Unit)? = null,
    onCursorMove: ((row: Int, col: Int) -> Unit)? = null,
    onTransformGesture: ((newZoom: Float, newPanX: Float, newPanY: Float, focusRow: Int, focusCol: Int) -> Unit)? = null,
    onDragStartEnd: ((startRow: Int, startCol: Int, currentRow: Int, currentCol: Int, isFinished: Boolean) -> Unit)? = null,
    onGuideDrag: ((isRowGuide: Boolean, oldIndex: Int, newIndex: Int) -> Unit)? = null,
    onCellInteract: ((row: Int, col: Int, isDrag: Boolean) -> Unit)? = null
) {
    val effectiveZoom = zoomFactor.coerceIn(1.0f, 4.0f)
    val currentOnCellInteract by rememberUpdatedState(onCellInteract)
    val currentOnCursorMove by rememberUpdatedState(onCursorMove)
    val currentOnTransformGesture by rememberUpdatedState(onTransformGesture)
    val currentOnDragStartEnd by rememberUpdatedState(onDragStartEnd)
    val currentOnGuideDrag by rememberUpdatedState(onGuideDrag)
    val currentIsScaleMode by rememberUpdatedState(isScaleMode)
    val currentScaleBoundingBox by rememberUpdatedState(scaleBoundingBox)
    val currentOnScaleHandleDrag by rememberUpdatedState(onScaleHandleDrag)
    val currentZoom by rememberUpdatedState(effectiveZoom)
    val currentPanX by rememberUpdatedState(panOffsetX)
    val currentPanY by rememberUpdatedState(panOffsetY)
    val currentRowGuides by rememberUpdatedState(rowGuides)
    val currentColGuides by rememberUpdatedState(colGuides)
    val currentHighlightedCell by rememberUpdatedState(highlightedCell)
    val currentReticleFreeDragOnly by rememberUpdatedState(reticleFreeDragOnly)

    val currentPixels by rememberUpdatedState(pixels)
    val currentGhostPixels by rememberUpdatedState(ghostPixels)
    val currentReferenceOverlayPixels by rememberUpdatedState(referenceOverlayPixels)
    val currentPreviewOverlayPixels by rememberUpdatedState(previewOverlayPixels)
    val drawContentSignature by rememberUpdatedState(
        (((pixels.contentHashCode() * 31 + (ghostPixels?.contentHashCode() ?: 0)) * 31 +
            (referenceOverlayPixels?.contentHashCode() ?: 0)) * 31 +
            (previewOverlayPixels?.contentHashCode() ?: 0))
    )

    // Subtle radial pulse animation when a dot is placed or erased under the highlighted reticle
    val dotPulseProgress = remember { Animatable(1f) }
    var lastReticleCoord by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var lastReticleLitState by remember { mutableStateOf<Boolean?>(null) }
    val currentReticleLit = highlightedCell?.let { (hr, hc) ->
        if (hr in 0 until MATRIX_SIZE && hc in 0 until MATRIX_SIZE) {
            (pixels[hr * MATRIX_SIZE + hc].toInt() and 0xFF) > 0
        } else null
    }
    LaunchedEffect(highlightedCell, currentReticleLit) {
        if (highlightedCell != null && currentReticleLit != null) {
            if (lastReticleCoord == highlightedCell && lastReticleLitState != null && lastReticleLitState != currentReticleLit) {
                dotPulseProgress.snapTo(0f)
                dotPulseProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 260)
                )
            }
            lastReticleCoord = highlightedCell
            lastReticleLitState = currentReticleLit
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(NothingPureBlack)
            .border(
                width = if (showCircularBezelOverlay) 1.5.dp else 1.dp,
                color = if (effectiveZoom > 1.02f) NothingCrimsonRed.copy(alpha = 0.65f)
                else if (showCircularBezelOverlay) Color(0xFF2E2E38)
                else NothingBorderHairline,
                shape = CircleShape
            )
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag(if (interactive) "interactive_matrix_canvas" else "preview_matrix_canvas")
                .then(
                    if (interactive) {
                        Modifier.pointerInput(
                            interactive,
                            axisLockEnabled,
                            activeRowGuide,
                            activeColGuide,
                            reticleFreeDragOnly,
                            isScaleMode
                        ) {
                            awaitEachGesture {
                                val firstDown = awaitFirstDown(requireUnconsumed = false)
                                var isPinching = false
                                var hasDragged = false
                                var lastPaintedCell: Pair<Int, Int>? = null
                                val firstPos = firstDown.position

                                fun offsetToGridCoord(offset: Offset, snapToPhysical: Boolean = false): Pair<Int, Int> {
                                    val w = size.width.coerceAtLeast(1).toFloat()
                                    val h = size.height.coerceAtLeast(1).toFloat()
                                    val cx = w / 2f
                                    val cy = h / 2f
                                    val worldX = (offset.x - cx - currentPanX) / currentZoom + cx
                                    val worldY = (offset.y - cy - currentPanY) / currentZoom + cy
                                    val rawCol = ((worldX / w) * MATRIX_SIZE).toInt().coerceIn(0, MATRIX_SIZE - 1)
                                    val rawRow = ((worldY / h) * MATRIX_SIZE).toInt().coerceIn(0, MATRIX_SIZE - 1)
                                    return if (snapToPhysical) {
                                        snapCoordinateToPhysicalAperture(
                                            rawRow = rawRow,
                                            rawCol = rawCol,
                                            axisLockEnabled = axisLockEnabled,
                                            activeRowGuide = activeRowGuide,
                                            activeColGuide = activeColGuide
                                        )
                                    } else {
                                        var row = rawRow
                                        var col = rawCol
                                        if (axisLockEnabled) {
                                            val rG = activeRowGuide
                                            val cG = activeColGuide
                                            when {
                                                rG != null && cG != null -> {
                                                    val distR = abs(row - rG)
                                                    val distC = abs(col - cG)
                                                    if (distR < distC) row = rG
                                                    else if (distC < distR) col = cG
                                                    else {
                                                        row = rG
                                                        col = cG
                                                    }
                                                }
                                                rG != null -> row = rG
                                                cG != null -> col = cG
                                            }
                                        }
                                        row to col
                                    }
                                }

                                val sBox = currentScaleBoundingBox
                                var activeScaleHandle: Int? = null
                                var initialScaleBox: FloatArray? = null
                                if (currentIsScaleMode && sBox != null && sBox.size >= 4) {
                                    val bMinR = sBox[0]
                                    val bMinC = sBox[1]
                                    val bMaxR = sBox[2]
                                    val bMaxC = sBox[3]
                                    val midR = (bMinR + bMaxR) / 2f
                                    val midC = (bMinC + bMaxC) / 2f

                                    fun handleScreenPos(r: Float, c: Float): Offset {
                                        val cx = size.width / 2f
                                        val cy = size.height / 2f
                                        val bW = size.width / MATRIX_SIZE.toFloat()
                                        val bH = size.height / MATRIX_SIZE.toFloat()
                                        val baseX = (c + 0.5f) * bW
                                        val baseY = (r + 0.5f) * bH
                                        val screenX = (baseX - cx) * currentZoom + cx + currentPanX
                                        val screenY = (baseY - cy) * currentZoom + cy + currentPanY
                                        return Offset(screenX, screenY)
                                    }

                                    val handles = listOf(
                                        handleScreenPos(bMinR, bMinC), // 0: TL
                                        handleScreenPos(bMinR, midC),  // 1: T
                                        handleScreenPos(bMinR, bMaxC), // 2: TR
                                        handleScreenPos(midR, bMaxC),  // 3: R
                                        handleScreenPos(bMaxR, bMaxC), // 4: BR
                                        handleScreenPos(bMaxR, midC),  // 5: B
                                        handleScreenPos(bMaxR, bMinC), // 6: BL
                                        handleScreenPos(midR, bMinC),  // 7: L
                                        handleScreenPos(midR, midC)    // 8: Center
                                    )

                                    val hitRadiusPx = 36.dp.toPx()
                                    var closestIdx: Int? = null
                                    var closestDist = Float.MAX_VALUE
                                    handles.forEachIndexed { index, hPos ->
                                        val dist = (hPos - firstPos).getDistance()
                                        if (dist <= hitRadiusPx && dist < closestDist) {
                                            closestDist = dist
                                            closestIdx = index
                                        }
                                    }

                                    if (closestIdx != null) {
                                        activeScaleHandle = closestIdx
                                        initialScaleBox = sBox.clone()
                                    } else {
                                        val tl = handleScreenPos(bMinR, bMinC)
                                        val br = handleScreenPos(bMaxR, bMaxC)
                                        val left = minOf(tl.x, br.x) - 12f
                                        val top = minOf(tl.y, br.y) - 12f
                                        val right = maxOf(tl.x, br.x) + 12f
                                        val bottom = maxOf(tl.y, br.y) + 12f
                                        if (firstPos.x in left..right && firstPos.y in top..bottom) {
                                            activeScaleHandle = 8
                                            initialScaleBox = sBox.clone()
                                        }
                                    }
                                }

                                val hCell = currentHighlightedCell
                                val (rawInitRow, rawInitCol) = offsetToGridCoord(firstPos, snapToPhysical = false)
                                val touchedReticleDirectly = hCell != null &&
                                    rawInitRow == hCell.first &&
                                    rawInitCol == hCell.second
                                val isReticleDragCandidate = currentReticleFreeDragOnly || touchedReticleDirectly

                                val (initialRow, initialCol) = offsetToGridCoord(
                                    firstPos,
                                    snapToPhysical = currentReticleFreeDragOnly
                                )
                                var lastDragRow = initialRow
                                var lastDragCol = initialCol
                                // Check if user grabbed an existing guide near the edge (first/last 3 cells)
                                var draggingRowGuide: Int? = if (
                                    activeScaleHandle == null &&
                                    currentOnGuideDrag != null &&
                                    initialRow in currentRowGuides &&
                                    (initialCol <= 3 || initialCol >= 21)
                                ) initialRow else null
                                var draggingColGuide: Int? = if (
                                    activeScaleHandle == null &&
                                    currentOnGuideDrag != null &&
                                    draggingRowGuide == null &&
                                    initialCol in currentColGuides &&
                                    (initialRow <= 3 || initialRow >= 21)
                                ) initialCol else null

                                if (activeScaleHandle == null) {
                                    if (currentReticleFreeDragOnly && draggingRowGuide == null && draggingColGuide == null) {
                                        currentOnCursorMove?.invoke(initialRow, initialCol)
                                    } else if (currentOnDragStartEnd != null && draggingRowGuide == null && draggingColGuide == null) {
                                        currentOnCursorMove?.invoke(initialRow, initialCol)
                                        currentOnDragStartEnd?.invoke(initialRow, initialCol, initialRow, initialCol, false)
                                    }
                                }

                                do {
                                    val event = awaitPointerEvent()
                                    val activeChanges = event.changes.filter { it.pressed }

                                    if (activeChanges.size >= 2) {
                                        isPinching = true
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        val centroid = event.calculateCentroid(useCurrent = true)
                                        val (focusR, focusC) = if (centroid != Offset.Unspecified) {
                                            offsetToGridCoord(centroid, snapToPhysical = true)
                                        } else {
                                            12 to 12
                                        }
                                        val nextZoom = (currentZoom * zoomChange).coerceIn(1.0f, 4.0f)
                                        val maxPan = (size.width * (nextZoom - 1f) * 0.48f).coerceAtLeast(0f)
                                        val nextPanX = if (nextZoom <= 1.02f) 0f else (currentPanX + panChange.x).coerceIn(-maxPan, maxPan)
                                        val nextPanY = if (nextZoom <= 1.02f) 0f else (currentPanY + panChange.y).coerceIn(-maxPan, maxPan)
                                        currentOnTransformGesture?.invoke(nextZoom, nextPanX, nextPanY, focusR, focusC)
                                        event.changes.forEach { it.consume() }
                                    } else if (activeChanges.size == 1 && !isPinching) {
                                        val change = activeChanges.first()
                                        val pos = change.position
                                        val movedDist = abs(pos.x - firstPos.x) + abs(pos.y - firstPos.y)
                                        if (movedDist > 4f) {
                                            if (activeScaleHandle != null && initialScaleBox != null) {
                                                hasDragged = true
                                                val cellWPx = (size.width / MATRIX_SIZE.toFloat()) * currentZoom
                                                val cellHPx = (size.height / MATRIX_SIZE.toFloat()) * currentZoom
                                                val deltaCol = (pos.x - firstPos.x) / cellWPx
                                                val deltaRow = (pos.y - firstPos.y) / cellHPx

                                                var newMinR = initialScaleBox[0]
                                                var newMinC = initialScaleBox[1]
                                                var newMaxR = initialScaleBox[2]
                                                var newMaxC = initialScaleBox[3]

                                                when (activeScaleHandle) {
                                                    0 -> { // TL
                                                        newMinR += deltaRow
                                                        newMinC += deltaCol
                                                    }
                                                    1 -> { // T
                                                        newMinR += deltaRow
                                                    }
                                                    2 -> { // TR
                                                        newMinR += deltaRow
                                                        newMaxC += deltaCol
                                                    }
                                                    3 -> { // R
                                                        newMaxC += deltaCol
                                                    }
                                                    4 -> { // BR
                                                        newMaxR += deltaRow
                                                        newMaxC += deltaCol
                                                    }
                                                    5 -> { // B
                                                        newMaxR += deltaRow
                                                    }
                                                    6 -> { // BL
                                                        newMaxR += deltaRow
                                                        newMinC += deltaCol
                                                    }
                                                    7 -> { // L
                                                        newMinC += deltaCol
                                                    }
                                                    8 -> { // Center translate
                                                        newMinR += deltaRow
                                                        newMaxR += deltaRow
                                                        newMinC += deltaCol
                                                        newMaxC += deltaCol
                                                    }
                                                }
                                                currentOnScaleHandleDrag?.invoke(activeScaleHandle!!, newMinR, newMinC, newMaxR, newMaxC)
                                            } else {
                                                val (row, col) = offsetToGridCoord(
                                                    pos,
                                                    snapToPhysical = isReticleDragCandidate
                                                )
                                                lastDragRow = row
                                                lastDragCol = col
                                                currentOnCursorMove?.invoke(row, col)

                                                if (draggingRowGuide != null) {
                                                    hasDragged = true
                                                    if (row != draggingRowGuide) {
                                                        currentOnGuideDrag?.invoke(true, draggingRowGuide!!, row)
                                                        draggingRowGuide = row
                                                    }
                                                } else if (draggingColGuide != null) {
                                                    hasDragged = true
                                                    if (col != draggingColGuide) {
                                                        currentOnGuideDrag?.invoke(false, draggingColGuide!!, col)
                                                        draggingColGuide = col
                                                    }
                                                } else if (currentReticleFreeDragOnly) {
                                                    // Precision Cursor mode: free-drag reticle without drawing strokes
                                                    hasDragged = true
                                                } else if (currentOnDragStartEnd != null) {
                                                    hasDragged = true
                                                    currentOnDragStartEnd?.invoke(initialRow, initialCol, row, col, false)
                                                } else if (touchedReticleDirectly && (row != initialRow || col != initialCol || hasDragged)) {
                                                    // User grabbed the red reticle ring directly and dragged it to a new coordinate
                                                    hasDragged = true
                                                } else if (!touchedReticleDirectly) {
                                                    if (!hasDragged) {
                                                        if (GlyphAperture.isPhysicalLed(initialCol, initialRow)) {
                                                            currentOnCellInteract?.invoke(initialRow, initialCol, false)
                                                            lastPaintedCell = initialRow to initialCol
                                                        }
                                                        hasDragged = true
                                                    }
                                                    if (lastPaintedCell != (row to col) &&
                                                        GlyphAperture.isPhysicalLed(col, row)
                                                    ) {
                                                        currentOnCellInteract?.invoke(row, col, true)
                                                        lastPaintedCell = row to col
                                                    }
                                                }
                                            }
                                            change.consume()
                                        }
                                    }
                                } while (event.changes.any { it.pressed })

                                if (!isPinching && activeScaleHandle == null) {
                                    val (endRow, endCol) = offsetToGridCoord(
                                        firstPos,
                                        snapToPhysical = currentReticleFreeDragOnly
                                    )
                                    if (currentReticleFreeDragOnly) {
                                        currentOnCursorMove?.invoke(lastDragRow, lastDragCol)
                                    } else if (hasDragged && currentOnDragStartEnd != null && draggingRowGuide == null && draggingColGuide == null) {
                                        currentOnDragStartEnd?.invoke(initialRow, initialCol, lastDragRow, lastDragCol, true)
                                    } else if (!hasDragged) {
                                        currentOnCursorMove?.invoke(endRow, endCol)
                                        // Dead corner pixels (outside 489 physical LEDs) NEVER intercept touch hits
                                        if (GlyphAperture.isPhysicalLed(endCol, endRow)) {
                                            if (currentOnDragStartEnd != null) {
                                                currentOnDragStartEnd?.invoke(endRow, endCol, endRow, endCol, true)
                                            } else {
                                                currentOnCellInteract?.invoke(endRow, endCol, false)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val baseCellWidth = w / MATRIX_SIZE.toFloat()
            val baseCellHeight = h / MATRIX_SIZE.toFloat()
            val cellWidth = baseCellWidth * effectiveZoom
            val cellHeight = baseCellHeight * effectiveZoom
            val dotRadius = (minOf(cellWidth, cellHeight) * 0.37f).coerceAtLeast(1.2f)
            val outerBezelRadius = (minOf(w, h) / 2f) * 0.99f

            fun cellCenter(row: Int, col: Int): Offset {
                val baseX = (col + 0.5f) * baseCellWidth
                val baseY = (row + 0.5f) * baseCellHeight
                val screenX = (baseX - cx) * effectiveZoom + cx + panOffsetX
                val screenY = (baseY - cy) * effectiveZoom + cy + panOffsetY
                return Offset(screenX, screenY)
            }

            fun cellCenterF(row: Float, col: Float): Offset {
                val baseX = (col + 0.5f) * baseCellWidth
                val baseY = (row + 0.5f) * baseCellHeight
                val screenX = (baseX - cx) * effectiveZoom + cx + panOffsetX
                val screenY = (baseY - cy) * effectiveZoom + cy + panOffsetY
                return Offset(screenX, screenY)
            }

            // 1. Subtle Nothing-style Circular Outer Bezel
            if (showCircularBezelOverlay) {
                drawCircle(
                    color = Color(0xFF25252E),
                    radius = outerBezelRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                for (deg in 0 until 360 step 15) {
                    val rad = Math.toRadians(deg.toDouble())
                    val rStart = outerBezelRadius * 0.968f
                    val rEnd = outerBezelRadius * 0.995f
                    drawLine(
                        color = if (deg % 90 == 0) Color(0x55FFFFFF) else Color(0x22FFFFFF),
                        start = Offset(
                            cx + (cos(rad) * rStart).toFloat(),
                            cy + (sin(rad) * rStart).toFloat()
                        ),
                        end = Offset(
                            cx + (cos(rad) * rEnd).toFloat(),
                            cy + (sin(rad) * rEnd).toFloat()
                        ),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            // 2. Origin (12, 12) Subtle Crosshair Guides in Editor Mode
            if (showCrosshairGuides) {
                val origin = cellCenter(12, 12)
                drawLine(
                    color = Color(0x24FFFFFF),
                    start = Offset(origin.x, 0f),
                    end = Offset(origin.x, h),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color(0x24FFFFFF),
                    start = Offset(0f, origin.y),
                    end = Offset(w, origin.y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // 3. Render ONLY the 489 Physical Addressable LEDs; 136 dead corner pixels are 100% transparent
            val selMinR = selectionBounds?.getOrNull(0) ?: -1
            val selMinC = selectionBounds?.getOrNull(1) ?: -1
            val selMaxR = selectionBounds?.getOrNull(2) ?: -1
            val selMaxC = selectionBounds?.getOrNull(3) ?: -1

            // Observe drawContentSignature inside DrawScope so Compose always invalidates Canvas when ByteArray contents change
            @Suppress("UNUSED_VARIABLE")
            val activeSignature = drawContentSignature
            val activePixels = currentPixels
            val activeGhostPixels = currentGhostPixels
            val activeRefPixels = currentReferenceOverlayPixels
            val activePreviewPixels = currentPreviewOverlayPixels

            for (r in 0 until MATRIX_SIZE) {
                for (c in 0 until MATRIX_SIZE) {
                    // Dead Corner Pixels (136 non-physical coordinates) -> 100% transparent, no outline or stroke
                    if (!GlyphAperture.isPhysicalLed(c, r)) continue

                    val center = cellCenter(r, c)
                    if (center.x < -cellWidth || center.x > w + cellWidth ||
                        center.y < -cellHeight || center.y > h + cellHeight
                    ) continue

                    val idx = r * MATRIX_SIZE + c
                    val previewVal = if (activePreviewPixels != null && idx < activePreviewPixels.size) {
                        activePreviewPixels[idx].toInt() and 0xFF
                    } else 0
                    val rawBrightness = if (previewVal > 0) {
                        previewVal
                    } else if (idx < activePixels.size) {
                        activePixels[idx].toInt() and 0xFF
                    } else 0

                    // Origin / Center LED (12, 12) subtle marker in editor mode
                    if (showCrosshairGuides && r == 12 && c == 12) {
                        drawCircle(
                            color = NothingCrimsonRed.copy(alpha = 0.65f),
                            radius = dotRadius * 1.45f,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    if (rawBrightness <= 0) {
                        val refBrightness = if (activeRefPixels != null && idx < activeRefPixels.size) {
                            activeRefPixels[idx].toInt() and 0xFF
                        } else 0
                        val ghostBrightness = if (activeGhostPixels != null && idx < activeGhostPixels.size) {
                            activeGhostPixels[idx].toInt() and 0xFF
                        } else 0

                        when {
                            refBrightness > 0 -> {
                                // 30% brightness reference composite overlay for non-targeted rule layers
                                val refAlpha = (0.30f * (refBrightness / 255f).coerceIn(0.45f, 1f)).coerceIn(0.16f, 0.34f)
                                drawCircle(
                                    color = Color(0xFFD6E4FF).copy(alpha = refAlpha),
                                    radius = dotRadius * 0.92f,
                                    center = center
                                )
                            }
                            ghostBrightness > 0 -> {
                                // Onion skin for previous timeline frame
                                drawCircle(
                                    color = Color.White.copy(alpha = onionOpacityAlpha),
                                    radius = dotRadius * 0.88f,
                                    center = center
                                )
                            }
                            else -> {
                                drawCircle(
                                    color = if (effectiveZoom > 1.2f) Color(0xFF202028) else NothingGridUnlit,
                                    radius = dotRadius * 0.76f,
                                    center = center
                                )
                            }
                        }
                    } else {
                        if (showPwmHeatmap) {
                            // PWM Heatmap Overlay: false-color rings
                            // Dim cyan/blue for low PWM 1–85, amber for mid PWM 86–170, bright red/white for high PWM 171–255
                            val heatmapColor = when {
                                rawBrightness <= 85 -> Color(0xFF38BDF8) // cyan/blue
                                rawBrightness <= 170 -> Color(0xFFFBBF24) // warm amber
                                else -> Color(0xFFEF4444) // bright red
                            }
                            drawCircle(
                                color = heatmapColor.copy(alpha = 0.38f),
                                radius = dotRadius * 1.35f,
                                center = center
                            )
                            drawCircle(
                                color = heatmapColor,
                                radius = dotRadius,
                                center = center
                            )
                        } else {
                            val norm = (rawBrightness / 255f).coerceIn(0.08f, 1f)
                            if (rawBrightness > 160) {
                                drawCircle(
                                    color = Color.White.copy(alpha = (norm - 0.5f) * 0.40f),
                                    radius = dotRadius * 1.38f,
                                    center = center
                                )
                            }
                            drawCircle(
                                color = Color.White.copy(alpha = norm),
                                radius = dotRadius,
                                center = center
                            )
                        }
                    }

                    // Selection highlight for physical LEDs inside marquee
                    if (r in selMinR..selMaxR && c in selMinC..selMaxC) {
                        drawCircle(
                            color = Color(0xFF00E5FF).copy(alpha = 0.7f),
                            radius = dotRadius * 1.25f,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    // Highlight active coordinate cell (row, col) + subtle radial pulse on dot toggle
                    if (highlightedCell != null && highlightedCell.first == r && highlightedCell.second == c) {
                        val pulseVal = dotPulseProgress.value
                        if (pulseVal < 0.99f) {
                            val pulseRadius = dotRadius * (1.36f + pulseVal * 1.45f)
                            val pulseAlpha = ((1f - pulseVal) * 0.85f).coerceIn(0f, 1f)
                            drawCircle(
                                color = NothingCrimsonRed.copy(alpha = pulseAlpha),
                                radius = pulseRadius,
                                center = center,
                                style = Stroke(width = 2.0.dp.toPx())
                            )
                        }
                        drawCircle(
                            color = NothingCrimsonRed,
                            radius = dotRadius * (if (reticleFreeDragOnly) 1.52f else 1.36f),
                            center = center,
                            style = Stroke(width = if (reticleFreeDragOnly) 2.0.dp.toPx() else 1.6.dp.toPx())
                        )
                    }
                }
            }

            // 4. Smart Interactive Guidelines Overlay (Dashed Cyan for Row Y, Dashed Orange for Col X)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            for (rGuide in rowGuides) {
                if (rGuide !in 0 until MATRIX_SIZE) continue
                val yCoord = cellCenter(rGuide, 12).y
                val isActiveGuide = activeRowGuide == rGuide
                val guideColor = if (isActiveGuide) Color(0xFF00E5FF) else Color(0xAA00E5FF)
                drawLine(
                    color = guideColor,
                    start = Offset(0f, yCoord),
                    end = Offset(w, yCoord),
                    strokeWidth = if (isActiveGuide) 1.8.dp.toPx() else 1.2.dp.toPx(),
                    pathEffect = dashEffect
                )
                // Edge drag-handle indicators
                drawCircle(
                    color = guideColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(6.dp.toPx(), yCoord)
                )
                drawCircle(
                    color = guideColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(w - 6.dp.toPx(), yCoord)
                )
            }

            for (cGuide in colGuides) {
                if (cGuide !in 0 until MATRIX_SIZE) continue
                val xCoord = cellCenter(12, cGuide).x
                val isActiveGuide = activeColGuide == cGuide
                val guideColor = if (isActiveGuide) Color(0xFFFF9100) else Color(0xAAFF9100)
                drawLine(
                    color = guideColor,
                    start = Offset(xCoord, 0f),
                    end = Offset(xCoord, h),
                    strokeWidth = if (isActiveGuide) 1.8.dp.toPx() else 1.2.dp.toPx(),
                    pathEffect = dashEffect
                )
                // Edge drag-handle indicators
                drawCircle(
                    color = guideColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(xCoord, 6.dp.toPx())
                )
                drawCircle(
                    color = guideColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(xCoord, h - 6.dp.toPx())
                )
            }

            // 5. Dashed Marquee Selection Box
            if (selMinR in 0 until MATRIX_SIZE && selMinC in 0 until MATRIX_SIZE &&
                selMaxR in 0 until MATRIX_SIZE && selMaxC in 0 until MATRIX_SIZE
            ) {
                val topLeftCenter = cellCenter(selMinR, selMinC)
                val bottomRightCenter = cellCenter(selMaxR, selMaxC)
                val rectLeft = topLeftCenter.x - cellWidth * 0.5f
                val rectTop = topLeftCenter.y - cellHeight * 0.5f
                val rectW = (bottomRightCenter.x + cellWidth * 0.5f) - rectLeft
                val rectH = (bottomRightCenter.y + cellHeight * 0.5f) - rectTop
                drawRect(
                    color = Color(0xFF00E5FF),
                    topLeft = Offset(rectLeft, rectTop),
                    size = Size(rectW.coerceAtLeast(2f), rectH.coerceAtLeast(2f)),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = dashEffect)
                )
            }

            // 6. Two-Stage Interactive Line Preview Vector (Axis-Snapped Dashed Line + Endpoints)
            if (linePreviewVector != null) {
                val (startPt, endPt) = linePreviewVector
                val startCenter = cellCenter(startPt.first, startPt.second)
                val endCenter = cellCenter(endPt.first, endPt.second)
                drawLine(
                    color = NothingCrimsonRed,
                    start = startCenter,
                    end = endCenter,
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = dashEffect
                )
                drawCircle(
                    color = Color(0xFF00E5FF),
                    radius = dotRadius * 1.45f,
                    center = startCenter,
                    style = Stroke(width = 1.8.dp.toPx())
                )
                drawCircle(
                    color = NothingCrimsonRed,
                    radius = dotRadius * 1.55f,
                    center = endCenter,
                    style = Stroke(width = 1.8.dp.toPx())
                )
            }

            // 7. Two-Stage Interactive Circle Preview (Center Origin + Dashed Radius Ring)
            if (circlePreviewGeometry != null) {
                val (cRow, cCol, radCells) = circlePreviewGeometry
                val originCenter = cellCenter(cRow, cCol)
                val ringRadiusPx = (radCells.coerceAtLeast(1) * minOf(cellWidth, cellHeight)).coerceAtLeast(dotRadius)
                drawCircle(
                    color = NothingCrimsonRed,
                    radius = ringRadiusPx,
                    center = originCenter,
                    style = Stroke(width = 1.8.dp.toPx(), pathEffect = dashEffect)
                )
                drawCircle(
                    color = Color(0xFF00E5FF),
                    radius = dotRadius * 1.45f,
                    center = originCenter,
                    style = Stroke(width = 1.8.dp.toPx())
                )
            }

            // 8. Interactive 8-Point Bounding Box for Continuous Freeform Scaling
            if (isScaleMode && scaleBoundingBox != null && scaleBoundingBox.size >= 4) {
                val bMinR = scaleBoundingBox[0]
                val bMinC = scaleBoundingBox[1]
                val bMaxR = scaleBoundingBox[2]
                val bMaxC = scaleBoundingBox[3]

                val tl = cellCenterF(bMinR, bMinC)
                val br = cellCenterF(bMaxR, bMaxC)
                val boxLeft = minOf(tl.x, br.x) - cellWidth * 0.5f
                val boxTop = minOf(tl.y, br.y) - cellHeight * 0.5f
                val boxRight = maxOf(tl.x, br.x) + cellWidth * 0.5f
                val boxBottom = maxOf(tl.y, br.y) + cellHeight * 0.5f
                val boxW = (boxRight - boxLeft).coerceAtLeast(2f)
                val boxH = (boxBottom - boxTop).coerceAtLeast(2f)

                // Dashed Cyan Bounding Box
                drawRect(
                    color = Color(0xFF00E5FF),
                    topLeft = Offset(boxLeft, boxTop),
                    size = Size(boxW, boxH),
                    style = Stroke(width = 1.8.dp.toPx(), pathEffect = dashEffect)
                )

                // Corner & Edge handle points
                val midR = (bMinR + bMaxR) / 2f
                val midC = (bMinC + bMaxC) / 2f
                val handlePositions = listOf(
                    cellCenterF(bMinR, bMinC), // 0: TL
                    cellCenterF(bMinR, midC),  // 1: T
                    cellCenterF(bMinR, bMaxC), // 2: TR
                    cellCenterF(midR, bMaxC),  // 3: R
                    cellCenterF(bMaxR, bMaxC), // 4: BR
                    cellCenterF(bMaxR, midC),  // 5: B
                    cellCenterF(bMaxR, bMinC), // 6: BL
                    cellCenterF(midR, bMinC)   // 7: L
                )

                val cornerHandleSize = 8.dp.toPx()
                val edgeHandleRadius = 4.5.dp.toPx()

                // 4 Corner Square chips (white with cyan outline)
                listOf(0, 2, 4, 6).forEach { idx ->
                    val pt = handlePositions[idx]
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(pt.x - cornerHandleSize / 2f, pt.y - cornerHandleSize / 2f),
                        size = Size(cornerHandleSize, cornerHandleSize)
                    )
                    drawRect(
                        color = Color(0xFF00E5FF),
                        topLeft = Offset(pt.x - cornerHandleSize / 2f, pt.y - cornerHandleSize / 2f),
                        size = Size(cornerHandleSize, cornerHandleSize),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                // 4 Tactile Edge circle chips (cyan with white core)
                listOf(1, 3, 5, 7).forEach { idx ->
                    val pt = handlePositions[idx]
                    drawCircle(
                        color = Color(0xFF00E5FF),
                        radius = edgeHandleRadius,
                        center = pt
                    )
                    drawCircle(
                        color = Color.White,
                        radius = edgeHandleRadius * 0.5f,
                        center = pt
                    )
                }

                // Center pivot crosshair in crimson red
                val centerPt = cellCenterF(midR, midC)
                val crosshairArm = 7.dp.toPx()
                drawLine(
                    color = NothingCrimsonRed,
                    start = Offset(centerPt.x - crosshairArm, centerPt.y),
                    end = Offset(centerPt.x + crosshairArm, centerPt.y),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = NothingCrimsonRed,
                    start = Offset(centerPt.x, centerPt.y - crosshairArm),
                    end = Offset(centerPt.x, centerPt.y + crosshairArm),
                    strokeWidth = 2.dp.toPx()
                )
                drawCircle(
                    color = NothingCrimsonRed,
                    radius = 3.dp.toPx(),
                    center = centerPt,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }
    }
}

/**
 * Pure Circular 25x25 Nothing Phone (3) Glyph Matrix Board (delegates to [CircularGlyphMatrixPreview]).
 */
@Composable
fun GlyphMatrix25Canvas(
    pixels: ByteArray,
    modifier: Modifier = Modifier,
    ghostPixels: ByteArray? = null,
    referenceOverlayPixels: ByteArray? = null,
    interactive: Boolean = false,
    showCrosshairGuides: Boolean = false,
    showCircularBezelOverlay: Boolean = true,
    zoomScale: Int = 1,
    zoomCenterRow: Int = 12,
    zoomCenterCol: Int = 12,
    highlightedCell: Pair<Int, Int>? = null,
    onCursorMove: ((row: Int, col: Int) -> Unit)? = null,
    onPinchZoom: ((newScale: Int, focusRow: Int, focusCol: Int) -> Unit)? = null,
    onCellInteract: ((row: Int, col: Int, isDrag: Boolean) -> Unit)? = null
) {
    CircularGlyphMatrixPreview(
        pixels = pixels,
        modifier = modifier,
        ghostPixels = ghostPixels,
        referenceOverlayPixels = referenceOverlayPixels,
        interactive = interactive,
        showCrosshairGuides = showCrosshairGuides,
        showCircularBezelOverlay = showCircularBezelOverlay,
        zoomFactor = zoomScale.coerceIn(1, 4).toFloat(),
        highlightedCell = highlightedCell,
        onCursorMove = onCursorMove,
        onTransformGesture = if (onPinchZoom != null) {
            { newZoom, _, _, focusR, focusC ->
                onPinchZoom(newZoom.roundToInt().coerceIn(1, 4), focusR, focusC)
            }
        } else null,
        onCellInteract = onCellInteract
    )
}

/**
 * Circular 25x25 Thumbnail preview used in Rule Cards, Presets, and Keyframe Filmstrip.
 */
@Composable
fun MiniMatrixThumbnail(
    pixels: ByteArray,
    frames: List<ByteArray> = emptyList(),
    size: Dp = 52.dp,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false,
    isCheckedForBatch: Boolean = false,
    modifier: Modifier = Modifier
) {
    var animFrameIdx by remember { mutableStateOf(0) }
    LaunchedEffect(frames, isSelected) {
        if (frames.size > 1 && isSelected) {
            while (isActive) {
                delay(100L)
                animFrameIdx = (animFrameIdx + 1) % frames.size
            }
        } else {
            animFrameIdx = 0
        }
    }

    val displayPixels = if (frames.isNotEmpty() && isSelected) {
        frames[animFrameIdx.coerceIn(0, frames.lastIndex)]
    } else {
        pixels
    }
    val currentDisplayPixels by rememberUpdatedState(displayPixels)
    val thumbSignature by rememberUpdatedState(displayPixels.contentHashCode())

    val borderColor = when {
        isMultiSelectMode && isCheckedForBatch -> NothingCrimsonRed
        isSelected -> NothingCrimsonRed
        isMultiSelectMode -> NothingPureWhite.copy(alpha = 0.65f)
        else -> NothingBorderHairline
    }
    val borderWidth = when {
        isMultiSelectMode && isCheckedForBatch -> 2.dp
        isSelected -> 1.5.dp
        else -> 1.dp
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(NothingPureBlack)
            .border(borderWidth, borderColor, CircleShape)
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE")
            val sig = thumbSignature
            val activeBytes = currentDisplayPixels
            val cellW = this.size.width / MATRIX_SIZE.toFloat()
            val cellH = this.size.height / MATRIX_SIZE.toFloat()
            val radius = (minOf(cellW, cellH) * 0.42f).coerceAtLeast(0.75f)

            for (r in 0 until MATRIX_SIZE) {
                for (c in 0 until MATRIX_SIZE) {
                    if (!GlyphHardwareController.isCellInsideCircle(r, c)) continue
                    val idx = r * MATRIX_SIZE + c
                    val b = if (idx < activeBytes.size) (activeBytes[idx].toInt() and 0xFF) else 0
                    val center = Offset((c + 0.5f) * cellW, (r + 0.5f) * cellH)
                    if (b > 0) {
                        val alpha = (b / 255f).coerceIn(0.18f, 1f)
                        drawCircle(
                            color = Color.White.copy(alpha = alpha),
                            radius = radius,
                            center = center
                        )
                    } else {
                        drawCircle(
                            color = Color(0xFF17171C),
                            radius = radius * 0.58f,
                            center = center
                        )
                    }
                }
            }
        }
    }
}

/**
 * Minimalist Nothing OS Top Bar:
 * - Minimal status pill (`● MATRIX CONNECTED` in white / crimson red dot)
 * - Quick Help (`?`)
 * - Master Toggle Switch
 */
@Composable
fun GlyphMinimalTopAppBar(
    globalEngineEnabled: Boolean,
    isMatrixSessionActive: Boolean,
    onToggleKillSwitch: (Boolean) -> Unit,
    onOpenUserGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(NothingPureBlack)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Minimal Status Pill: ● MATRIX CONNECTED
        Surface(
            color = NothingCarbonSurface,
            shape = RoundedCornerShape(50),
            border = BorderStroke(
                width = 1.dp,
                color = if (isMatrixSessionActive) NothingCrimsonRed else NothingBorderHairline
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (globalEngineEnabled) NothingCrimsonRed else NothingTechnicalGray
                        )
                )
                Text(
                    text = if (globalEngineEnabled) "MATRIX CONNECTED" else "ENGINE MUTED",
                    style = MaterialTheme.typography.labelMedium,
                    color = NothingPureWhite
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onOpenUserGuide,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("open_user_guide_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = "Quick Help",
                    tint = NothingPureWhite,
                    modifier = Modifier.size(20.dp)
                )
            }

            Switch(
                checked = globalEngineEnabled,
                onCheckedChange = onToggleKillSwitch,
                modifier = Modifier.testTag("global_kill_switch"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NothingPureWhite,
                    checkedTrackColor = NothingCrimsonRed,
                    uncheckedThumbColor = NothingTechnicalGray,
                    uncheckedTrackColor = NothingElevatedSurface
                )
            )
        }
    }
}

/**
 * Compact, elegant circular 25x25 LED widget showing real-time rear matrix activity on the Dashboard.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompactLiveMatrixCard(
    hardwareState: HardwareMatrixState,
    globalEngineEnabled: Boolean,
    onStopActiveSession: () -> Unit,
    onVerifyPhysicalToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val compositeFrames = hardwareState.activeCompositeFrames
    var animatedLivePixels by remember {
        mutableStateOf(hardwareState.currentPixels)
    }
    val liveFramesSignature = remember(hardwareState.currentPixels.contentHashCode(), compositeFrames) {
        var h = hardwareState.currentPixels.contentHashCode()
        for (f in compositeFrames) {
            h = 31 * h + f.contentHashCode()
        }
        h
    }

    LaunchedEffect(liveFramesSignature) {
        if (compositeFrames.size <= 1) {
            animatedLivePixels = hardwareState.currentPixels.copyOf()
            return@LaunchedEffect
        }
        var idx = 0
        while (isActive) {
            animatedLivePixels = compositeFrames[idx % compositeFrames.size].copyOf()
            delay(100L)
            idx = (idx + 1) % compositeFrames.size
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        color = NothingCarbonSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (!hardwareState.lastHardwareError.isNullOrBlank() || hardwareState.activeDotCount > 0) {
                NothingCrimsonRed.copy(alpha = 0.75f)
            } else {
                NothingBorderHairline
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    CircularGlyphMatrixPreview(
                        pixels = animatedLivePixels,
                        modifier = Modifier.size(82.dp),
                        interactive = false,
                        showCircularBezelOverlay = true
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "REAR GLYPH MATRIX • 489 LEDS",
                                style = MaterialTheme.typography.labelSmall,
                                color = NothingTechnicalGray
                            )
                            if (hardwareState.isPersistentStreamActive && hardwareState.activeFps > 0) {
                                NothingPillBadge(
                                    text = "${hardwareState.activeFps} FPS STREAM",
                                    color = NothingCrimsonRed
                                )
                            } else {
                                NothingPillBadge(
                                    text = if (hardwareState.isServiceBound) "BOUND: TRUE" else "BOUND: FALSE",
                                    color = if (hardwareState.isServiceBound) NothingPureWhite else NothingCrimsonRed
                                )
                            }
                        }
                        Text(
                            text = when {
                                !globalEngineEnabled -> "Engine Muted"
                                hardwareState.activeRuleName != null -> hardwareState.activeRuleName
                                else -> hardwareState.activeFrameName
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = NothingPureWhite,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = hardwareState.handshakeStatusLog,
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTechnicalGray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (hardwareState.activeDotCount > 0) {
                    IconButton(
                        onClick = onStopActiveSession,
                        modifier = Modifier.testTag("stop_hardware_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.StopCircle,
                            contentDescription = "Shut Off Matrix",
                            tint = NothingCrimsonRed,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Direct Physical Toggle Verification Button
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onVerifyPhysicalToggle,
                    border = BorderStroke(1.dp, NothingCrimsonRed),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentHeight()
                        .testTag("verify_physical_toggle_button")
                ) {
                    Text(
                        text = "VERIFY PHYSICAL 489 LEDS (255)",
                        style = MaterialTheme.typography.labelMedium,
                        color = NothingPureWhite
                    )
                }

                if (hardwareState.activeDotCount > 0) {
                    OutlinedButton(
                        onClick = onStopActiveSession,
                        border = BorderStroke(1.dp, NothingBorderHairline),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                        modifier = Modifier
                            .wrapContentHeight()
                            .testTag("clear_physical_toggle_button")
                    ) {
                        Text(
                            text = "OFF",
                            style = MaterialTheme.typography.labelMedium,
                            color = NothingPureWhite
                        )
                    }
                }
            }

            // Display exact hardware exception in red text immediately on screen without needing Logcat
            if (!hardwareState.lastHardwareError.isNullOrBlank()) {
                Text(
                    text = hardwareState.lastHardwareError,
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingCrimsonRed,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hardware_error_red_text")
                )
            }
        }
    }
}

/**
 * Built-in Step-by-Step User Guide Dialog accessible via the `?` icon in the top bar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlyphUserGuideDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NothingCarbonSurface,
        titleContentColor = NothingPureWhite,
        textContentColor = NothingPureWhite,
        title = {
            Text(
                text = "GLYPHFLOW // QUICK GUIDE",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = NothingElevatedSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NothingBorderHairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NothingPillBadge(text = "STEP 01 // PERMISSIONS", color = NothingCrimsonRed)
                        Text(
                            text = "Notification Access & Unrestricted Battery",
                            style = MaterialTheme.typography.titleMedium,
                            color = NothingPureWhite
                        )
                        Text(
                            text = "Grant Notification Access and set Battery to 'Unrestricted' so GlyphFlow can stream 30 FPS animations continuously without Nothing OS putting the matrix to sleep.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTechnicalGray
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                    }
                                },
                                border = BorderStroke(1.dp, NothingBorderHairline),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.wrapContentHeight()
                            ) {
                                Text(
                                    text = "NOTIFICATIONS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NothingPureWhite
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    }
                                },
                                border = BorderStroke(1.dp, NothingBorderHairline),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.wrapContentHeight()
                            ) {
                                Text(
                                    text = "BATTERY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NothingPureWhite
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = NothingElevatedSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NothingBorderHairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NothingPillBadge(text = "STEP 02 // 3-STEP RULE CREATOR", color = NothingCrimsonRed)
                        Text(
                            text = "Trigger ➔ Matrix Action ➔ Until Condition Stops",
                            style = MaterialTheme.typography.titleMedium,
                            color = NothingPureWhite
                        )
                        Text(
                            text = "Tap '+ Create Rule' on the Dashboard. Pick any installed app visually or select Audio Playing, choose 'Audio Waveform Shader' or a saved preset, and select 'Until condition stops' for zero-timeout playback.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTechnicalGray
                        )
                    }
                }

                Surface(
                    color = NothingElevatedSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NothingBorderHairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NothingPillBadge(text = "STEP 03 // CIRCULAR STUDIO & AI TOOLS", color = NothingCrimsonRed)
                        Text(
                            text = "Live Mirror, Prompt-to-Glyph & Photo-to-Glyph",
                            style = MaterialTheme.typography.titleMedium,
                            color = NothingPureWhite
                        )
                        Text(
                            text = "In Studio, toggle 'LIVE' to mirror touches to your rear LEDs. Type prompts like 'right arrow' or 'heartbeat pulse' in the AI Assistant, or import any photo to auto-crop and dither it to 25×25.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTechnicalGray
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingCrimsonRed,
                    contentColor = NothingPureWhite
                ),
                modifier = Modifier.testTag("close_user_guide_button")
            ) {
                Text("GOT IT", style = MaterialTheme.typography.labelMedium)
            }
        }
    )
}

@Composable
fun NothingPillBadge(
    text: String,
    color: Color = NothingPureWhite,
    backgroundColor: Color = NothingElevatedSurface,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .border(0.75.dp, NothingBorderHairline, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontSize = 9.sp,
            letterSpacing = 0.3.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Shared responsive compact pill chip used across Studio, Rule Builder, Console, and Settings.
 * Eliminates Material 3 FilterChip/AssistChip internal padding clipping and ensures consistent
 * spacing, vertical alignment, and controlled text wrapping.
 */
@Composable
fun NothingCompactPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    fontSize: TextUnit = 9.5.sp,
    horizontalPadding: Dp = 10.dp,
    verticalPadding: Dp = 6.dp,
    unselectedBorderColor: Color = NothingBorderHairline,
    unselectedTextColor: Color = NothingPureWhite
) {
    val containerColor = if (selected) NothingCrimsonRed else NothingElevatedSurface
    val borderColor = if (selected) NothingCrimsonRed else unselectedBorderColor
    val contentColor = if (selected) NothingPureWhite else unselectedTextColor
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pill_press_scale"
    )

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .wrapContentHeight()
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    fontSize = fontSize,
                    letterSpacing = 0.2.sp,
                    lineHeight = 12.sp,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 5x5 Zoomed Magnifier Loupe showing the local LED neighborhood around (centerRow, centerCol)
 * so the user can see exact dot placement without finger occlusion.
 */
@Composable
fun GlyphMagnifierLoupeBadge(
    pixels: ByteArray,
    centerRow: Int,
    centerCol: Int,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(NothingPureBlack)
            .border(1.dp, NothingCrimsonRed.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            .padding(3.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val span = 5
            val cellW = this.size.width / span.toFloat()
            val cellH = this.size.height / span.toFloat()
            val dotR = (minOf(cellW, cellH) * 0.36f).coerceAtLeast(1.2f)

            for (dr in -2..2) {
                for (dc in -2..2) {
                    val r = centerRow + dr
                    val c = centerCol + dc
                    val cx = (dc + 2.5f) * cellW
                    val cy = (dr + 2.5f) * cellH
                    val center = Offset(cx, cy)

                    if (r !in 0 until MATRIX_SIZE || c !in 0 until MATRIX_SIZE || !GlyphHardwareController.isCellInsideCircle(r, c)) {
                        drawCircle(
                            color = Color(0x18FF1B2D),
                            radius = dotR * 0.4f,
                            center = center
                        )
                    } else {
                        val b = pixels[r * MATRIX_SIZE + c].toInt() and 0xFF
                        if (b > 0) {
                            drawCircle(
                                color = Color.White.copy(alpha = (b / 255f).coerceIn(0.2f, 1f)),
                                radius = dotR,
                                center = center
                            )
                        } else {
                            drawCircle(
                                color = Color(0xFF282832),
                                radius = dotR * 0.7f,
                                center = center
                            )
                        }
                    }

                    if (dr == 0 && dc == 0) {
                        drawCircle(
                            color = NothingCrimsonRed,
                            radius = dotR * 1.4f,
                            center = center,
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

/**
 * Snaps any raw 25x25 coordinate `(rawRow, rawCol)` onto the nearest physical LED within
 * `(x - 12)^2 + (y - 12)^2 <= 156.25`, while strictly clamping to the active horizontal or
 * vertical guideline when `axisLockEnabled` is active.
 */
fun snapCoordinateToPhysicalAperture(
    rawRow: Int,
    rawCol: Int,
    axisLockEnabled: Boolean = false,
    activeRowGuide: Int? = null,
    activeColGuide: Int? = null
): Pair<Int, Int> {
    var targetRow = rawRow.coerceIn(0, MATRIX_SIZE - 1)
    var targetCol = rawCol.coerceIn(0, MATRIX_SIZE - 1)

    var lockedRow: Int? = null
    var lockedCol: Int? = null
    if (axisLockEnabled) {
        val rG = activeRowGuide?.coerceIn(0, MATRIX_SIZE - 1)
        val cG = activeColGuide?.coerceIn(0, MATRIX_SIZE - 1)
        when {
            rG != null && cG != null -> {
                val distR = abs(targetRow - rG)
                val distC = abs(targetCol - cG)
                if (distR <= distC) {
                    targetRow = rG
                    lockedRow = rG
                } else {
                    targetCol = cG
                    lockedCol = cG
                }
            }
            rG != null -> {
                targetRow = rG
                lockedRow = rG
            }
            cG != null -> {
                targetCol = cG
                lockedCol = cG
            }
        }
    }

    if (GlyphAperture.isPhysicalLed(targetCol, targetRow)) {
        return targetRow to targetCol
    }

    var bestRow = 12
    var bestCol = 12
    var bestDistSq = Int.MAX_VALUE

    for (r in 0 until MATRIX_SIZE) {
        if (lockedRow != null && r != lockedRow) continue
        for (c in 0 until MATRIX_SIZE) {
            if (lockedCol != null && c != lockedCol) continue
            if (!GlyphAperture.isPhysicalLed(c, r)) continue
            val dr = r - targetRow
            val dc = c - targetCol
            val distSq = dr * dr + dc * dc
            if (distSq < bestDistSq) {
                bestDistSq = distSq
                bestRow = r
                bestCol = c
            }
        }
    }
    return bestRow to bestCol
}


