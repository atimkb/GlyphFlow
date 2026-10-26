package com.example.dsl

import com.example.domain.model.GlyphFrame
import com.example.domain.model.TriggerRule

/**
 * Abstract Syntax Tree (AST) and compilation models for the GlyphScript v2 Universal DSL.
 */
data class GlyphScriptRule(
    val name: String = "",
    val targetPackages: List<String> = emptyList(),
    val whenClause: WhenClause? = null,
    val program: GlyphScriptProgram? = null
) {
    val targetPackage: String
        get() = targetPackages.firstOrNull() ?: "*"
}

data class GlyphScriptProgram(
    val ruleName: String,
    val whenClause: WhenClause,
    val extractDeclarations: List<ExtractDeclaration>,
    val displayLayers: List<DisplayLayer>,
    val behaviorClause: BehaviorClause,
    val sourceText: String
) {
    val targetPackages: List<String>
        get() = whenClause.targetPackages

    val targetPackage: String
        get() = whenClause.targetPackage

    val hasAnimatedPrimitives: Boolean
        get() = displayLayers.any { layer ->
            layer.animationMacro != null || layer.primitives.any { prim ->
                when (prim) {
                    is LayerPrimitive.RingPrimitive -> prim.pulse || prim.animate
                    is LayerPrimitive.ArrowPrimitive -> prim.pulse || prim.bounce
                    is LayerPrimitive.EmojiPrimitive -> prim.bounce || prim.pulse
                    is LayerPrimitive.TextPrimitive -> prim.animate || prim.marquee || prim.pulse
                    is LayerPrimitive.IconPrimitive -> prim.pulse || prim.bounce
                    is LayerPrimitive.ShaderPrimitive -> true
                    is LayerPrimitive.SequencePrimitive -> prim.frames.size > 1
                    is LayerPrimitive.BitmapPrimitive,
                    is LayerPrimitive.PlotPrimitive -> false
                }
            }
        }
}

enum class TriggerType {
    NOTIFICATION,
    SYSTEM_PROPERTY,
    APP_FOREGROUND
}

data class TriggerCondition(
    val triggerType: TriggerType = TriggerType.NOTIFICATION,
    val propertyName: String = "",
    val operator: String = "CHANGED",
    val expectedValue: String = "",
    val targetPackages: List<String> = listOf("*"),
    val rawExpression: String = ""
)

data class WhenClause(
    val triggerEvent: String, // e.g. "notification", "media.playing", "battery.low", "system.flip_face_down"
    val fromTarget: String? = null, // e.g. "Google Maps", "Swiggy", "com.whatsapp", "*", or null
    val targetPackages: List<String> = if (fromTarget != null) listOf(fromTarget) else listOf("*"),
    val fromLineNumber: Int = 1,
    val fromColumnNumber: Int = 1,
    val conditionField: String? = null,
    val conditionValue: String? = null,
    val triggers: List<TriggerCondition> = emptyList()
) {
    val targetPackage: String
        get() = targetPackages.firstOrNull() ?: fromTarget ?: "*"
}

enum class MapConditionType {
    CONTAINS,
    REGEX,
    EQUALS,
    DEFAULT
}

data class ValueMapRule(
    val conditionType: MapConditionType,
    val patterns: List<String>,
    val targetValue: String
)

data class ShowIfCondition(
    val leftVar: String,
    val operator: String = "EXPR",
    val rightValues: List<String> = emptyList()
) {
    fun toDslString(): String {
        return if (operator == "EXPR") {
            rightValues.firstOrNull() ?: leftVar
        } else {
            val cleanL = if (leftVar.startsWith("$")) leftVar else "\$$leftVar"
            if (operator.lowercase() in setOf("in", "not in", "not_in")) {
                val formattedVals = rightValues.joinToString(", ") {
                    val v = it.trim()
                    if (v.startsWith("\"") || v.startsWith("'")) v else "\"$v\""
                }
                "$cleanL $operator [$formattedVals]"
            } else {
                val r = rightValues.firstOrNull()?.trim() ?: "\"\""
                val formattedR = if (r.startsWith("\"") || r.startsWith("'") || r.startsWith("$") || r.toDoubleOrNull() != null) r else "\"$r\""
                "$cleanL $operator $formattedR"
            }
        }
    }

    fun evaluate(variables: Map<String, String>): Boolean {
        val rawExpr = toDslString()
        return runCatching {
            evaluateBooleanExpr(rawExpr, variables)
        }.getOrDefault(false)
    }

    private fun evaluateBooleanExpr(exprStr: String, variables: Map<String, String>): Boolean {
        var clean = exprStr.trim()
        if (clean.isEmpty()) return true

        if (clean.startsWith("(") && clean.endsWith(")")) {
            var depth = 0
            var matchedOuter = true
            for (i in 0 until clean.length - 1) {
                val c = clean[i]
                if (c == '(') depth++
                else if (c == ')') depth--
                if (depth == 0) {
                    matchedOuter = false
                    break
                }
            }
            if (matchedOuter) {
                return evaluateBooleanExpr(clean.substring(1, clean.length - 1), variables)
            }
        }

        val orParts = splitByOperator(clean, "or")
        if (orParts.size > 1) {
            return orParts.any { evaluateBooleanExpr(it, variables) }
        }

        val andParts = splitByOperator(clean, "and")
        if (andParts.size > 1) {
            return andParts.all { evaluateBooleanExpr(it, variables) }
        }

        if (clean.startsWith("not ", ignoreCase = true)) {
            val sub = clean.substring(4).trim()
            return !evaluateBooleanExpr(sub, variables)
        }

        val lower = clean.lowercase()
        return when {
            lower.contains(" not in ") || lower.contains(" not_in ") -> {
                val sep = if (lower.contains(" not in ")) " not in " else " not_in "
                val leftRaw = clean.substringBefore(sep).trim()
                val rightRaw = clean.substringAfter(sep).trim()
                val leftVal = resolveVar(leftRaw, variables)
                val listVals = parseListVals(rightRaw, variables)
                listVals.none { it.equals(leftVal, ignoreCase = true) }
            }
            lower.contains(" in ") -> {
                val leftRaw = clean.substringBefore(" in ").trim()
                val rightRaw = clean.substringAfter(" in ").trim()
                val leftVal = resolveVar(leftRaw, variables)
                val listVals = parseListVals(rightRaw, variables)
                listVals.any { it.equals(leftVal, ignoreCase = true) }
            }
            clean.contains("==") -> {
                val l = resolveVar(clean.substringBefore("==").trim(), variables)
                val r = resolveVar(clean.substringAfter("==").trim(), variables)
                l.equals(r, ignoreCase = true)
            }
            clean.contains("!=") -> {
                val l = resolveVar(clean.substringBefore("!=").trim(), variables)
                val r = resolveVar(clean.substringAfter("!=").trim(), variables)
                !l.equals(r, ignoreCase = true)
            }
            clean.contains(">=") -> {
                val l = resolveVar(clean.substringBefore(">=").trim(), variables).toDoubleOrNull()
                val r = resolveVar(clean.substringAfter(">=").trim(), variables).toDoubleOrNull()
                if (l != null && r != null) l >= r else false
            }
            clean.contains("<=") -> {
                val l = resolveVar(clean.substringBefore("<=").trim(), variables).toDoubleOrNull()
                val r = resolveVar(clean.substringAfter("<=").trim(), variables).toDoubleOrNull()
                if (l != null && r != null) l <= r else false
            }
            clean.contains(">") -> {
                val l = resolveVar(clean.substringBefore(">").trim(), variables).toDoubleOrNull()
                val r = resolveVar(clean.substringAfter(">").trim(), variables).toDoubleOrNull()
                if (l != null && r != null) l > r else false
            }
            clean.contains("<") -> {
                val l = resolveVar(clean.substringBefore("<").trim(), variables).toDoubleOrNull()
                val r = resolveVar(clean.substringAfter("<").trim(), variables).toDoubleOrNull()
                if (l != null && r != null) l < r else false
            }
            else -> {
                val v = resolveVar(clean, variables)
                v.isNotBlank() && v != "0" && !v.equals("false", ignoreCase = true)
            }
        }
    }

    private fun splitByOperator(s: String, op: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        var depth = 0
        val targetOp = " $op "
        var startIdx = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '"' || c == '\'') inQuotes = !inQuotes
            else if (c == '(' && !inQuotes) depth++
            else if (c == ')' && !inQuotes) depth = (depth - 1).coerceAtLeast(0)
            else if (!inQuotes && depth == 0 && i + targetOp.length <= s.length) {
                val sub = s.substring(i, i + targetOp.length)
                if (sub.equals(targetOp, ignoreCase = true)) {
                    result.add(s.substring(startIdx, i).trim())
                    startIdx = i + targetOp.length
                    i += targetOp.length - 1
                }
            }
            i++
        }
        if (startIdx < s.length) {
            result.add(s.substring(startIdx).trim())
        }
        return if (result.isEmpty()) listOf(s) else result
    }

    private fun resolveVar(token: String, variables: Map<String, String>): String {
        val clean = token.trim()
        if (clean.startsWith("\"") && clean.endsWith("\"")) return clean.removeSurrounding("\"")
        if (clean.startsWith("'") && clean.endsWith("'")) return clean.removeSurrounding("'")
        val vName = clean.removePrefix("$")
        return variables[vName] ?: variables[vName.lowercase()] ?: clean
    }

    private fun parseListVals(raw: String, variables: Map<String, String>): List<String> {
        val clean = raw.trim()
        val inside = if (clean.startsWith("[") && clean.endsWith("]")) clean.substring(1, clean.length - 1) else clean
        return inside.split(",").map { resolveVar(it.trim(), variables) }
    }
}

/**
 * Variable extraction declaration inside `EXTRACT { ... }`:
 * `var dist = body.regex("[0-9]+\\s?(m|km|ft)") DEFAULT ""`
 */
enum class DiagnosticSeverity { ERROR, WARNING }

data class GlyphScriptDiagnostic(
    val code: String,
    val line: Int,
    val column: Int,
    val message: String,
    val severity: DiagnosticSeverity = DiagnosticSeverity.ERROR
) {
    val formattedMessage: String
        get() = "$code L$line:C$column $message"
}

data class ExtractDeclaration(
    val varName: String,
    val sourceField: String, // "title", "body", "subText", "progress_percent"
    val regexPattern: String? = null,
    val groupIndex: Int = 0,
    val regexFlags: String = "",
    val mapRules: List<ValueMapRule> = emptyList(),
    val defaultValue: String = "",
    val lineNumber: Int = 1,
    val columnNumber: Int = 1
)

sealed interface LayerAnimationMacro {
    val frameIntervalMs: Long
    fun toDslString(): String
    fun presetLabel(): String

    data class SlideMacro(
        val fromX: Int = 12,
        val fromY: Int = 18,
        val toX: Int = 12,
        val toY: Int = 6,
        val speedMs: Long = 100L,
        val pauseMs: Long = 1500L,
        val loop: Boolean = true
    ) : LayerAnimationMacro {
        override val frameIntervalMs: Long get() = speedMs.coerceIn(33L, 2000L)
        override fun toDslString(): String =
            "slide(from: [$fromX, $fromY], to: [$toX, $toY], speed: ${speedMs}ms, pause: ${pauseMs}ms, loop: $loop)"
        override fun presetLabel(): String = when {
            toY < fromY && toX == fromX -> "Slide Up"
            toY > fromY && toX == fromX -> "Slide Down"
            toX > fromX && toY == fromY -> "Slide Right"
            toX < fromX && toY == fromY -> "Slide Left"
            else -> "Slide"
        }
    }

    data class BounceMacro(
        val axis: String = "y",
        val minOffset: Int = -3,
        val maxOffset: Int = 3,
        val speedMs: Long = 80L
    ) : LayerAnimationMacro {
        override val frameIntervalMs: Long get() = speedMs.coerceIn(33L, 2000L)
        override fun toDslString(): String =
            "bounce(axis: \"$axis\", range: [$minOffset, $maxOffset], speed: ${speedMs}ms)"
        override fun presetLabel(): String = "Bounce"
    }

    data class SpinMacro(
        val stepDegrees: Int = 45,
        val intervalMs: Long = 100L
    ) : LayerAnimationMacro {
        override val frameIntervalMs: Long get() = intervalMs.coerceIn(33L, 2000L)
        override fun toDslString(): String =
            "spin(step: ${stepDegrees}deg, interval: ${intervalMs}ms)"
        override fun presetLabel(): String = "Radar Spin"
    }

    data class PulseMacro(
        val minBrightness: Int = 40,
        val maxBrightness: Int = 255,
        val periodMs: Long = 1000L,
        val isBreathe: Boolean = false
    ) : LayerAnimationMacro {
        override val frameIntervalMs: Long get() = (periodMs / 8L).coerceIn(50L, 400L)
        override fun toDslString(): String =
            "pulse(min: $minBrightness, max: $maxBrightness, period: ${periodMs}ms)"
        override fun presetLabel(): String = if (isBreathe || periodMs >= 1600L) "Breathe" else "Pulse"
    }
}

data class DisplayLayer(
    val name: String,
    val primitives: List<LayerPrimitive>,
    val showIfCondition: ShowIfCondition? = null,
    val groupName: String? = null,
    val animationMacro: LayerAnimationMacro? = null,
    val lineNumber: Int = 1,
    val columnNumber: Int = 1
)

sealed interface LayerPrimitive {
    data class RingPrimitive(
        val progressExpr: String = "100",
        val style: String = "clock_cw",
        val thickness: Int = 1,
        val radius: Int = 12,
        val pulse: Boolean = false,
        val animate: Boolean = false,
        val brightness: Int = 255
    ) : LayerPrimitive

    data class ArrowPrimitive(
        val directionExpr: String,
        val size: Int = 11,
        val pulse: Boolean = false,
        val bounce: Boolean = false,
        val brightness: Int = 255
    ) : LayerPrimitive

    data class EmojiPrimitive(
        val glyphExpr: String,
        val scalePercent: Int = 75,
        val x: Int = 12,
        val y: Int = 8,
        val size: Int? = null,
        val hasExplicitY: Boolean = false,
        val bounce: Boolean = false,
        val pulse: Boolean = false,
        val brightness: Int = 255
    ) : LayerPrimitive {
        val scale: Int get() = scalePercent
    }

    data class TextPrimitive(
        val contentExpr: String,
        val suffix: String = "",
        val size: String = "3x5",
        val align: String = "center",
        val y: Int = 16,
        val animate: Boolean = false,
        val marquee: Boolean = animate,
        val pulse: Boolean = false,
        val brightness: Int = 255
    ) : LayerPrimitive

    data class IconPrimitive(
        val nameExpr: String,
        val pulse: Boolean = false,
        val bounce: Boolean = false,
        val brightness: Int = 255
    ) : LayerPrimitive

    data class ShaderPrimitive(
        val typeExpr: String,
        val fps: Int = 30,
        val brightness: Int = 255
    ) : LayerPrimitive

    data class BitmapPrimitive(
        val asciiArt: String,
        val align: String = "center",
        val x: Int = 0,
        val y: Int = 0,
        val brightness: Int = 255
    ) : LayerPrimitive

    data class SequenceFrame(
        val asciiArt: String,
        val durationMs: Long? = null
    )

    data class SequencePrimitive(
        val intervalMs: Long = 100L,
        val pauseMs: Long = 0L,
        val holdLast: Boolean = false,
        val loop: Boolean = true,
        val align: String = "center",
        val frames: List<SequenceFrame>
    ) : LayerPrimitive {
        fun reorderFrame(fromIndex: Int, toIndex: Int): SequencePrimitive {
            if (frames.size <= 1) return this
            val safeFrom = fromIndex.coerceIn(0, frames.lastIndex)
            val safeTo = toIndex.coerceIn(0, frames.lastIndex)
            if (safeFrom == safeTo) return this
            val mutable = frames.toMutableList()
            val moved = mutable.removeAt(safeFrom)
            mutable.add(safeTo, moved)
            return copy(frames = mutable)
        }

        fun reverseSelectedFrames(selectedIndices: Set<Int>): SequencePrimitive {
            val sorted = selectedIndices.filter { it in frames.indices }.sorted()
            if (sorted.size <= 1) return this
            val reversedFrames = sorted.map { frames[it] }.reversed()
            val mutable = frames.toMutableList()
            sorted.forEachIndexed { i, targetIdx ->
                mutable[targetIdx] = reversedFrames[i]
            }
            return copy(frames = mutable)
        }
    }

    data class PlotDot(
        val x: Int,
        val y: Int,
        val brightness: Int = 255
    )

    data class PlotPrimitive(
        val dots: List<PlotDot>
    ) : LayerPrimitive
}

enum class TaskMode(val value: String) {
    ONGOING("ongoing"),
    TRANSIENT("transient")
}

data class BehaviorClause(
    val priority: Int = 8,
    val mode: TaskMode = TaskMode.ONGOING,
    val intrudeDurationMs: Long = 4000L,
    val untilLifecycle: String = "notification.dismissed",
    val durationMs: Long = 5000L,
    val fallbackClear: Boolean = true
)

data class GlyphScriptCompileError(
    val line: Int,
    val column: Int,
    val reason: String,
    val formattedMessage: String = "Compilation Error (Line $line, Col $column): $reason"
)

sealed class GlyphScriptCompileResult {
    data class Success(
        val program: GlyphScriptProgram,
        val compiledRule: TriggerRule,
        val resolvedPackageName: String,
        val resolvedAppLabel: String,
        val previewFrames: List<GlyphFrame>,
        val extractedPreviewVariables: Map<String, String>,
        val warnings: List<GlyphScriptDiagnostic> = emptyList()
    ) : GlyphScriptCompileResult() {
        val compositeFrames: List<ByteArray>
            get() = previewFrames.map { it.pixels }

        val targetPackages: List<String>
            get() = program.targetPackages
    }

    data class Failure(
        val diagnostics: List<GlyphScriptDiagnostic>
    ) : GlyphScriptCompileResult() {
        val error: GlyphScriptCompileError
            get() {
                val firstErr = diagnostics.firstOrNull { it.severity == DiagnosticSeverity.ERROR } ?: diagnostics.firstOrNull()
                return GlyphScriptCompileError(
                    line = firstErr?.line ?: 1,
                    column = firstErr?.column ?: 1,
                    reason = firstErr?.message ?: "Compilation failed.",
                    formattedMessage = firstErr?.formattedMessage ?: "Compilation Error"
                )
            }
    }
}

/**
 * Built-in LLM System Instruction for external AI assistants (ChatGPT, Claude, Gemini)
 * to author valid GlyphScript v2 rules for the Nothing Phone (3) 25x25 Glyph Matrix.
 */
const val OFFICIAL_GLYPHSCRIPT_V2_SYSTEM_PROMPT = """[GLYPHSCRIPT V2 HARDWARE COMPILER SPECIFICATION]
You are a GlyphScript v2 developer for the Nothing Phone (3) 25x25 rear LED matrix.
Output ONLY a valid `glyphscript` code block. No explanations outside the block.

[NOTHING PHONE (3) PHYSICAL MATRIX ANATOMY]
- Total Physical LEDs: Exactly 489 individually addressable LEDs arranged in a circle.
- Coordinate Envelope: 25 rows by 25 columns (x: 0..24, y: 0..24).
- Center Origin: (12, 12) is the exact dead-center LED.
- Boundary Equation: Only pixels satisfying (x - 12)^2 + (y - 12)^2 <= 156.25 physically exist.
- Safe Horizontal Chord Width per Row:
  - Rows 0 & 24: 7 dots wide (x: 9..15)
  - Rows 3 & 21: 17 dots wide (x: 4..20)
  - Rows 7 to 17: 23 to 25 dots wide (x: 0..24)
- Drawing Guidelines:
  - Always center primary iconography around (12, 8) when text is placed at y: 17.
  - If drawing custom ASCII bitmaps, keep graphics within a 17x17 circle to avoid aperture clipping.

HARDWARE & LAYOUT CONSTRAINTS:
- Grid: 25x25 (625 pixels), circular aperture `(x-12)^2 + (y-12)^2 <= 156.25` (489 active LEDs).
- Brightness: 0 (off) to 255 (peak white).
- Do NOT add a ring/border layer unless explicitly requested.
- Upper graphic around `y: 7` or `y: 8` (rows 3..13 or `align: "top_center"`), bottom text at `y: 16` or `y: 17` (max 4 chars static, or `animate: true` for marquee).

[SYSTEM OS TRIGGERS & MULTI-TRIGGERS]
Rules no longer rely solely on notifications. You can trigger rules using hardware and OS-level state changes. Combine triggers using `OR`.

Supported System Triggers:
- `system.bluetooth` (Values: "connected", "disconnected", "bt_on", "bt_off")
- `system.power` (Values: "charging", "discharging", "full")
- `system.battery_level` (Numeric, e.g., `< 15`)
- `system.ringer` (Values: "silent", "vibrate", "normal")
- `system.wifi` (Values: "connected", "disconnected")

When triggering via system states, the `EXTRACT` block can directly access system payload variables (e.g., `system.device_name`, `system.device_type`) rather than parsing notification titles.

Example of a Multi-Trigger Bluetooth Rule:
RULE "Universal Bluetooth HUD"
WHEN notification FROM ["com.android.bluetooth"] OR system.bluetooth CHANGED
EXTRACT {
  // Pull directly from system state if available, fallback to notification map
  var state = system.bluetooth DEFAULT body.map {
    regex("(?i)disconnect") => "disconnected",
    regex("(?i)connected") => "connected",
    DEFAULT => "bt_on"
  }
  var device = system.device_name DEFAULT title.regex("(?i)connected to\\s*(.+)$") DEFAULT "Unknown Device"
}
DISPLAY {
  LAYER dev_connected {
    show_if: ${'$'}state == "connected"
    animate: pulse(min: 80, max: 255, period: 700ms)
    bitmap(align: "center") \"\"\"
    ...#####...
    ..#.....#..
    .#.......#.
    .###...###.
    \"\"\"
    text(content: ${'$'}device, size: "3x5", align: "center", y: 18, animate: true)
  }
}
BEHAVIOR {
  priority: 10
  until: duration(4s)
}

PACKAGE TARGETING & MULTI-APP MATCHING:
In the `WHEN notification FROM` clause, ALWAYS use formal reverse-DNS Android package IDs (e.g., `FROM ["com.application.zomato", "in.swiggy.android"]`). Never use generic display titles like 'Zomato'. If the user mentions a category like 'food delivery' or 'quick commerce', include all common package identifiers for that category in an array:
- Food & Quick Commerce: `FROM ["in.swiggy.android", "com.application.zomato", "com.blinkit.consumer", "com.zeptonow.android"]`
- Ride Hailing: `FROM ["com.ubercab", "com.olacabs.customer", "com.rapido.passenger"]`
- Media & Music: `FROM ["com.spotify.music", "com.google.android.apps.youtube.music", "com.amazon.mp3"]`
- Navigation: `FROM "com.google.android.apps.maps"`
- Chat & Messaging: `FROM ["com.whatsapp", "org.telegram.messenger", "com.slack"]`
- Wildcard (any app): `FROM "*"`

DIRECT PIXEL ART & KEYFRAME PRIMITIVES:
- `bitmap(align: "center"|"top_center") \"\"\"...\"\"\"`: Raw 2D ASCII art (`.` or space = off, `#` or `*` = peak white 255, digits `1`..`9` = brightness 28..255).
- `sequence(interval: 100ms, pause: 2000ms, hold_last: true, loop: true, align: "center") { frame \"\"\"...\"\"\" frame \"\"\"...\"\"\" }`: Multi-frame animation sequence with interval timing, pause gaps, and hold_last.
- `plot(dots: [[12, 12, 255], [12, 11, 180]])`: Plot individual dots by `[x, y, brightness]`.

[PROCEDURAL ANIMATION MACROS]
Instead of writing massive multi-frame ASCII sequences for simple motion, use `animate:` inside any `LAYER` alongside a static `bitmap`, `arrow`, or `icon`:
- `animate: slide(from: [12, 18], to: [12, 6], speed: 100ms, pause: 1500ms, loop: true)`
- `animate: bounce(axis: "y", range: [-2, 2], speed: 100ms)`
- `animate: spin(step: 45deg, interval: 100ms)`
- `animate: pulse(min: 40, max: 255, period: 1000ms)`

Example:
LAYER moving_arrow {
  animate: slide(from: [12, 16], to: [12, 6], speed: 80ms, pause: 1200ms, loop: true)
  bitmap(align: "center") \"\"\"
  ...#...
  ..###..
  .#.#.#.
  ...#...
  ...#...
  \"\"\"
}

CONDITIONAL LAYER VISIBILITY & GROUPS:
- `LAYER name { show_if: ${'$'}state == "active" ... }`
- Support `group: "nav"` for mutually exclusive layers (first match renders; layer without `show_if:` serves as fallback).

VALUE MAPPING & REGEX IN EXTRACT:
- `EXTRACT { var state = body.map { contains(["playing", "resumed"]) => "play", regex("(?i)error|fail") => "alert", DEFAULT => "idle" } }`
- Mandatory fallback: Every regex and map declaration MUST specify `DEFAULT "<value>"`.

GRAMMAR:
RULE "<Rule Name>"
WHEN notification FROM ["com.pkg1", "com.pkg2"]
EXTRACT {
  var <name> = <title | body | subText | progress_percent>.regex("<pattern>") DEFAULT "<fallback>"
  var state = body.map {
    contains(["playing", "resumed"]) => "play",
    contains(["paused", "stopped"]) => "pause",
    DEFAULT => "idle"
  }
}
DISPLAY {
  LAYER <layerName> {
    show_if: ${'$'}state == "play"
    ring(thickness: <1|2|3>, radius: <10..12>, progress: <0..100% | ${'$'}var>, style: "clock_cw" | "solid" | "dashed", pulse: <true|false>)
    arrow(direction: <var_or_"left"|"right"|"straight"|"u-turn">, size: <7..15>, pulse: <true|false>)
    emoji(glyph: "🛵", scale: <50..100%>, y: <4..20>)
    bitmap(align: "center") \"\"\"
    ..###..
    .#####.
    ...#...
    \"\"\"
    sequence(interval: 100ms, pause: 1000ms, hold_last: true, loop: true, align: "center") {
      frame \"\"\"
      ..#.#..
      .#####.
      \"\"\"
      frame \"\"\"
      .##.##.
      #######
      \"\"\"
    }
    text(content: ${'$'}eta, suffix: "m", size: "3x5", align: "center", y: 17)
    icon(name: "battery" | "wifi" | "bell" | "heart" | "check", pulse: <true|false>)
  }
}
BEHAVIOR {
  priority: <1..10>
  mode: <"ongoing" | "transient">
  intrude_duration: 4000ms
  until: <notification.dismissed | media.stopped | duration(5s) | condition_cleared>
  fallback_clear: true
}

USER REQUEST:
"""

const val DEFAULT_CONSOLE_SCRIPT_SAMPLE = """RULE "Maps Turn & Distance HUD"
WHEN notification FROM "*"
EXTRACT {
  var dir = title.regex("(left|right|straight|u-turn)") DEFAULT "right"
  var dist = body.regex("[0-9]+(m|km|ft)") DEFAULT "450m"
}
DISPLAY {
  LAYER center_nav {
    arrow(direction: dir, size: 9, pulse: true)
  }
  LAYER bottom_hud {
    text(content: dist, size: "3x5", align: "center", y: 16)
  }
}
BEHAVIOR {
  priority: 10
  until: notification.dismissed
  fallback_clear: true
}"""

const val PRESET_FOOD_DELIVERY = """RULE "Food Delivery (ETA + Scooter)"
WHEN notification FROM "com.ubercab.eats"
EXTRACT {
  var eta = body.regex("([0-9]+)\\s*min") DEFAULT "12"
}
DISPLAY {
  LAYER delivery_icon {
    sequence(interval: 200ms, loop: true, align: "center") {
      frame """ + "\"\"\"" + """
      ...###...
      ..#####..
      .##.#.##.
      ..#####..
      ...#.#...
      """ + "\"\"\"" + """
      frame """ + "\"\"\"" + """
      ...###...
      ..#####..
      .##.##.#.
      ..#####..
      ...#.#...
      """ + "\"\"\"" + """
    }
  }
  LAYER eta_text {
    text(content: eta, suffix: "m", size: "3x5", align: "center", y: 17)
  }
}
BEHAVIOR {
  priority: 9
  mode: "ongoing"
  until: notification.dismissed
  fallback_clear: true
}"""

const val PRESET_CAB_TRACKER = """RULE "Cab Arrival (Animated Car + Ring)"
WHEN notification FROM "com.ubercab"
EXTRACT {
  var mins = body.regex("([0-9]+)\\s*min") DEFAULT "3"
}
DISPLAY {
  LAYER outer_ring {
    ring(radius: 11, stroke: 1, pulse: true)
  }
  LAYER car_icon {
    bitmap(align: "center") """ + "\"\"\"" + """
    ..#####..
    .#######.
    #########
    .#.###.#.
    """ + "\"\"\"" + """
  }
  LAYER eta_label {
    text(content: mins, suffix: "m", size: "3x5", align: "center", y: 17)
  }
}
BEHAVIOR {
  priority: 8
  until: notification.dismissed
  fallback_clear: true
}"""

const val PRESET_PIXEL_BATTERY = """RULE "Pixel Battery (3-Frame ASCII)"
WHEN battery_level AT 0..100
DISPLAY {
  LAYER batt_seq {
    sequence(interval: 250ms, loop: true, align: "center") {
      frame """ + "\"\"\"" + """
      ...###...
      ..#...#..
      ..#...#..
      ..#.#.#..
      ..#####..
      """ + "\"\"\"" + """
      frame """ + "\"\"\"" + """
      ...###...
      ..#...#..
      ..#####..
      ..#####..
      ..#####..
      """ + "\"\"\"" + """
      frame """ + "\"\"\"" + """
      ...###...
      ..#####..
      ..#####..
      ..#####..
      ..#####..
      """ + "\"\"\"" + """
    }
  }
  LAYER level_txt {
    text(content: battery.level, suffix: "%", size: "3x5", align: "center", y: 17)
  }
}
BEHAVIOR {
  priority: 6
  until: duration(5s)
}"""

const val PRESET_URGENT_ALERT = """RULE "Urgent Alert (Flashing Ring + Bell)"
WHEN notification FROM "com.google.android.dialer"
DISPLAY {
  LAYER flashing_border {
    ring(radius: 12, stroke: 2, pulse: true)
  }
  LAYER bell {
    icon(name: "bell", pulse: true)
  }
}
BEHAVIOR {
  priority: 10
  mode: "transient"
  intrude_duration: 4000ms
  until: duration(4s)
}"""
