package com.example.dsl

import com.example.domain.model.MATRIX_CELL_COUNT
import com.example.domain.model.MATRIX_SIZE
import com.example.hardware.GlyphHardwareController
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Mathematical Text Rasterizer and Fit Ladder for the Nothing Phone (3) 25x25 circular matrix.
 *
 * Implements row-level safe chord calculations:
 * halfChord(r) = floor(sqrt(12.5^2 - (r - 12)^2))
 * width(r) = 2 * halfChord(r) + 1
 * safeWidth(y) = min_{r in [y..y+4]} width(r)
 */
object CanvasTextRasterizer {

    private const val RADIUS_SQ = 156.25 // 12.5^2
    private const val CENTER = 12.0

    /**
     * Calculates the half-chord length for a given row r in [0..24].
     */
    fun calculateRowHalfChord(r: Int): Int {
        if (r !in 0 until MATRIX_SIZE) return 0
        val dy = r - CENTER
        val diff = RADIUS_SQ - (dy * dy)
        if (diff < 0) return 0
        return floor(sqrt(diff)).toInt()
    }

    /**
     * Calculates the total allowable dot width for a given row r in [0..24].
     */
    fun calculateRowWidth(r: Int): Int {
        if (r !in 0 until MATRIX_SIZE) return 0
        return 2 * calculateRowHalfChord(r) + 1
    }

    /**
     * Calculates the minimum safe width across rows spanning [y..y+glyphHeight-1].
     */
    fun calculateSafeWidth(y: Int, glyphHeight: Int = 5): Int {
        var minW = Int.MAX_VALUE
        val endRow = (y + glyphHeight - 1).coerceAtMost(MATRIX_SIZE - 1)
        for (r in y..endRow) {
            if (r in 0 until MATRIX_SIZE) {
                val w = calculateRowWidth(r)
                if (w < minW) minW = w
            } else {
                return 0
            }
        }
        return if (minW == Int.MAX_VALUE) 0 else minW
    }

    /**
     * Rendered text width in dots for string of given length L:
     * 3 dots per char + 1 dot space between chars = 4 * L - 1.
     */
    fun calculateTextRenderWidth(textLength: Int): Int {
        if (textLength <= 0) return 0
        return 4 * textLength - 1
    }

    /**
     * Compacts unit representations:
     * - "1200 m" / "1200m" -> "1.2k"
     * - "1.2 km" / "1.2km" -> "1.2k"
     * - "500 meters" / "500 m" -> "500m"
     * - "minutes" / "mins" / "min" -> "'"
     */
    fun compactUnits(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return ""

        // Distance conversions
        val distMeterMatch = Regex("""^(\d+)\s*(?:meters|metres|mtrs|mtr|m)$""", RegexOption.IGNORE_CASE).matchEntire(trimmed)
        if (distMeterMatch != null) {
            val num = distMeterMatch.groupValues[1].toLongOrNull() ?: 0L
            return if (num >= 1000) {
                val kVal = num / 1000.0
                val formatted = if (kVal % 1.0 == 0.0) "${kVal.toLong()}k" else String.format("%.1fk", kVal)
                formatted
            } else {
                "${num}m"
            }
        }

        val distKmMatch = Regex("""^(\d+(?:\.\d+)?)\s*(?:kilometers|kilometres|kms|km|k)$""", RegexOption.IGNORE_CASE).matchEntire(trimmed)
        if (distKmMatch != null) {
            val num = distKmMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val formatted = if (num % 1.0 == 0.0) "${num.toLong()}k" else String.format("%.1fk", num)
            return formatted
        }

        // Time conversions ("minutes", "mins", "min" -> "'")
        var timeCompacted = trimmed
            .replace(Regex("""(?i)\s*(?:minutes|mins|min)\b"""), "'")
            .replace(Regex("""(?<=\d)\s+(?=\')"""), "")

        return timeCompacted
    }

    /**
     * Resolves the Fit Ladder for text content:
     * 1. Check if original content + suffix fits safeWidth(y).
     * 2. Apply compact unit conversions; check if compacted content + suffix fits.
     * 3. Drop suffix; check if compacted content fits.
     * 4. Marquee scroll smoothly within safeWidth(y) with a brief hold at loop start.
     */
    fun resolveFitLadder(
        content: String,
        suffix: String,
        y: Int,
        fitMode: String = "auto"
    ): ResolvedTextFit {
        if (content.isEmpty()) {
            return ResolvedTextFit(textToRender = "", isMarquee = false, safeWidth = calculateSafeWidth(y))
        }

        val safeW = calculateSafeWidth(y)
        val fullOriginal = content + suffix

        if (fitMode.equals("clip", ignoreCase = true)) {
            return ResolvedTextFit(textToRender = fullOriginal, isMarquee = false, safeWidth = safeW)
        }

        if (fitMode.equals("marquee", ignoreCase = true)) {
            return ResolvedTextFit(textToRender = fullOriginal, isMarquee = true, safeWidth = safeW)
        }

        // 1. Original text + suffix
        if (calculateTextRenderWidth(fullOriginal.length) <= safeW) {
            return ResolvedTextFit(textToRender = fullOriginal, isMarquee = false, safeWidth = safeW)
        }

        // 2. Compacted unit conversion + suffix
        val compactedContent = compactUnits(content)
        val compactedFull = compactedContent + suffix
        if (calculateTextRenderWidth(compactedFull.length) <= safeW) {
            return ResolvedTextFit(textToRender = compactedFull, isMarquee = false, safeWidth = safeW)
        }

        // 3. Compacted content without suffix
        if (calculateTextRenderWidth(compactedContent.length) <= safeW) {
            return ResolvedTextFit(textToRender = compactedContent, isMarquee = false, safeWidth = safeW)
        }

        // 4. Marquee scrolling
        return ResolvedTextFit(textToRender = compactedContent, isMarquee = true, safeWidth = safeW)
    }

    data class ResolvedTextFit(
        val textToRender: String,
        val isMarquee: Boolean,
        val safeWidth: Int
    )

    /**
     * Rasterizes text onto a 25x25 matrix buffer (IntArray of size 625).
     */
    fun renderText(
        content: String,
        suffix: String = "",
        y: Int = 16,
        brightness: Int = 255,
        align: String = "center",
        fitMode: String = "auto",
        timeClockMs: Long = 0L
    ): IntArray {
        val out = IntArray(MATRIX_CELL_COUNT)
        if (content.isEmpty()) return out

        val fit = resolveFitLadder(content, suffix, y, fitMode)
        val text = fit.textToRender.uppercase()
        if (text.isEmpty()) return out

        val charW = 3
        val charH = 5
        val spacing = 1
        val safeW = fit.safeWidth
        val halfChord = calculateRowHalfChord(y)
        val startColBounds = 12 - halfChord
        val endColBounds = 12 + halfChord

        val b = brightness.coerceIn(32, 255)

        if (fit.isMarquee) {
            val padded = "$text   "
            val fullPixelWidth = padded.length * (charW + spacing)
            val holdMs = 200L
            val cycleMs = fullPixelWidth * 120L + holdMs
            val elapsedInCycle = if (cycleMs > 0) timeClockMs % cycleMs else 0L
            val scrollPx = if (elapsedInCycle < holdMs) 0 else (((elapsedInCycle - holdMs) / 120L) % fullPixelWidth).toInt()

            for (col in startColBounds..endColBounds) {
                if (col !in 0 until MATRIX_SIZE) continue
                val virtualX = (col - startColBounds + scrollPx) % fullPixelWidth
                val charIdx = (virtualX / (charW + spacing)) % padded.length
                val colInChar = virtualX % (charW + spacing)
                if (colInChar < charW) {
                    val glyphRows = GlyphFrameCompositor.get3x5Glyph(padded[charIdx])
                    val bitMask = 1 shl (charW - 1 - colInChar)
                    for (r in glyphRows.indices) {
                        if ((glyphRows[r] and bitMask) != 0) {
                            val targetR = y + r
                            if (targetR in 0 until MATRIX_SIZE &&
                                GlyphHardwareController.isCellInsideCircle(targetR, col)
                            ) {
                                out[targetR * MATRIX_SIZE + col] = b
                            }
                        }
                    }
                }
            }
            return out
        }

        val renderW = calculateTextRenderWidth(text.length)
        val startCol = when (align.lowercase().trim()) {
            "left" -> startColBounds
            "right" -> (endColBounds - renderW + 1).coerceAtLeast(startColBounds)
            else -> (12 - renderW / 2).coerceIn(startColBounds, endColBounds)
        }

        var cursorC = startCol
        for (ch in text) {
            val glyphRows = GlyphFrameCompositor.get3x5Glyph(ch)
            for (r in glyphRows.indices) {
                val rowBits = glyphRows[r]
                for (c in 0 until charW) {
                    val bitMask = 1 shl (charW - 1 - c)
                    if ((rowBits and bitMask) != 0) {
                        val targetR = y + r
                        val targetC = cursorC + c
                        if (targetR in 0 until MATRIX_SIZE &&
                            targetC in 0 until MATRIX_SIZE &&
                            GlyphHardwareController.isCellInsideCircle(targetR, targetC)
                        ) {
                            out[targetR * MATRIX_SIZE + targetC] = b
                        }
                    }
                }
            }
            cursorC += charW + spacing
        }

        return out
    }
}
