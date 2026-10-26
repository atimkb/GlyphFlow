package com.example.hardware

import com.example.domain.model.MATRIX_CELL_COUNT
import com.example.domain.model.MATRIX_SIZE

/**
 * Physical 489-LED Circular Coordinate Architecture & LUT for Nothing Phone (3).
 *
 * Matrix Bounds: 25 rows by 25 columns (x in 0..24, y in 0..24).
 * Hardware Center: (12, 12) is the exact dead-center LED.
 * Physical LED Equation: (x - 12)^2 + (y - 12)^2 <= 156.25 (radius <= 12.5).
 * Exactly 489 valid addressable LEDs exist inside this circular aperture.
 * Corner indices where isPhysical == false (136 total points) are strictly dead pixels.
 */
object GlyphAperture {
    const val MATRIX_SIZE = 25
    const val MATRIX_CELL_COUNT = 625
    const val CENTER_X = 12
    const val CENTER_Y = 12
    const val RADIUS_SQ = 156.25f
    const val PHYSICAL_LED_COUNT = 489
    const val DEAD_PIXEL_COUNT = 136

    /**
     * Static lookup table of size 625:
     * For index i = y * 25 + x, true if (x - 12)^2 + (y - 12)^2 <= 156.25.
     */
    val PHYSICAL_LED_MASK: BooleanArray = BooleanArray(MATRIX_CELL_COUNT) { index ->
        val x = index % MATRIX_SIZE
        val y = index / MATRIX_SIZE
        val dx = x - CENTER_X
        val dy = y - CENTER_Y
        (dx * dx + dy * dy) <= RADIUS_SQ
    }

    /**
     * Checks if coordinates (x, y) fall inside the physical circular aperture.
     */
    fun isPhysicalLed(x: Int, y: Int): Boolean {
        if (x !in 0 until MATRIX_SIZE || y !in 0 until MATRIX_SIZE) return false
        return PHYSICAL_LED_MASK[y * MATRIX_SIZE + x]
    }

    /**
     * Checks if 1D matrix index falls inside the physical circular aperture.
     */
    fun isPhysicalLed(index: Int): Boolean {
        if (index !in 0 until MATRIX_CELL_COUNT) return false
        return PHYSICAL_LED_MASK[index]
    }

    /**
     * Strictly zeros out all 136 non-physical indices on a ByteArray in-place.
     */
    fun clampToPhysicalAperture(buffer: ByteArray): ByteArray {
        val len = minOf(buffer.size, MATRIX_CELL_COUNT)
        for (i in 0 until len) {
            if (!PHYSICAL_LED_MASK[i]) {
                buffer[i] = 0.toByte()
            }
        }
        return buffer
    }

    /**
     * Strictly zeros out all 136 non-physical indices on an IntArray in-place.
     */
    fun clampToPhysicalAperture(buffer: IntArray): IntArray {
        val len = minOf(buffer.size, MATRIX_CELL_COUNT)
        for (i in 0 until len) {
            if (!PHYSICAL_LED_MASK[i]) {
                buffer[i] = 0
            }
        }
        return buffer
    }

    /**
     * Returns the safe horizontal chord width (number of physical dots) on row y.
     */
    fun getSafeChordWidth(y: Int): Int {
        if (y !in 0 until MATRIX_SIZE) return 0
        var count = 0
        for (x in 0 until MATRIX_SIZE) {
            if (isPhysicalLed(x, y)) count++
        }
        return count
    }
}
