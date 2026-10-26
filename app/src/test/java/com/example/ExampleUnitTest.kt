package com.example

import com.example.domain.model.GlyphFrame
import com.example.domain.model.MATRIX_CELL_COUNT
import com.example.domain.model.MeterStyle
import com.example.hardware.GlyphHardwareController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun glyphFrame_pixelReadWriteAndMetrics_areAccurate() {
        val blank = GlyphFrame()
        assertEquals(MATRIX_CELL_COUNT, blank.pixels.size)
        assertEquals(0, blank.activeDotCount())

        val painted = blank
            .withPixel(row = 0, col = 0, brightness = 255)
            .withPixel(row = 12, col = 12, brightness = 128)
            .withPixel(row = 24, col = 24, brightness = 64)

        assertEquals(255, painted.getBrightness(0, 0))
        assertEquals(128, painted.getBrightness(12, 12))
        assertEquals(64, painted.getBrightness(24, 24))
        assertEquals(3, painted.activeDotCount())
        assertEquals(255, painted.peakBrightness())
    }

    @Test
    fun glyphScriptCompiler_parsesBehaviorModeAndIntrudeDuration() {
        val script = """
            RULE "Uber Arrival Alert"
            WHEN notification FROM "com.ubercab"
            EXTRACT {
              var eta = body.regex("[0-9]+\\s?min") DEFAULT "4m"
            }
            DISPLAY {
              LAYER arrival {
                text(content: eta, size: "3x5", align: "center", y: 17)
              }
            }
            BEHAVIOR {
              priority: 9
              mode: "transient"
              intrude_duration: 3500ms
              until: "duration(3500ms)"
              fallback_clear: true
            }
        """.trimIndent()

        val parsed = com.example.dsl.GlyphScriptCompiler.parseProgramOnly(script)
        val program = parsed.first
        org.junit.Assert.assertNotNull(program)
        assertEquals(com.example.dsl.TaskMode.TRANSIENT, program!!.behaviorClause.mode)
        assertEquals(3500L, program.behaviorClause.intrudeDurationMs)
        assertEquals("duration(3500ms)", program.behaviorClause.untilLifecycle)
    }

    @Test
    fun glyphScriptCompiler_ongoingModeDefault_entersCarousel() {
        val script = """
            RULE "Maps Continuous HUD"
            WHEN notification FROM "maps"
            EXTRACT {
              var dist = body.regex("[0-9]+m") DEFAULT "450m"
            }
            DISPLAY {
              LAYER hud {
                arrow(direction: "right", size: 9)
                text(content: dist, size: "3x5", y: 16)
              }
            }
            BEHAVIOR {
              priority: 8
              mode: "ongoing"
              until: notification.dismissed
              fallback_clear: true
            }
        """.trimIndent()

        val parsed = com.example.dsl.GlyphScriptCompiler.parseProgramOnly(script)
        val program = parsed.first
        org.junit.Assert.assertNotNull(program)
        assertEquals(com.example.dsl.TaskMode.ONGOING, program!!.behaviorClause.mode)
    }
}
