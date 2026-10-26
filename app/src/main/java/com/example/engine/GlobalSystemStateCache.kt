package com.example.engine

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Lightweight recorded system/hardware temporal event (e.g. key press, screen wake).
 */
data class TemporalEvent(
    val name: String,
    val timestampMs: Long = SystemClock.elapsedRealtime()
)

/**
 * Singleton state cache maintaining real-time Android OS system variables and a rolling
 * temporal event buffer (~5 seconds) for chronological key/sequence matching in GlyphScript v2.
 */
object GlobalSystemStateCache {

    private val state = ConcurrentHashMap<String, String>()
    private val temporalEvents = ConcurrentLinkedDeque<TemporalEvent>()

    init {
        // Sensible initial defaults
        update("system.bluetooth", "disconnected")
        update("system.bluetooth_state", "off")
        update("system.power", "discharging")
        update("system.battery_level", "100")
        update("system.ringer", "normal")
        update("system.screen", "on")
        update("system.wifi", "disconnected")
        update("system.airplane_mode", "off")
        update("system.nfc", "off")
        update("system.auto_rotate", "on")
        update("system.location", "on")
        update("system.foreground_app", "")
    }

    fun get(key: String): String? {
        return state[key] ?: if (!key.startsWith("system.")) state["system.$key"] else null
    }

    fun getAll(): Map<String, String> = HashMap(state)

    fun update(key: String, value: String) {
        state[key] = value
        if (key.startsWith("system.")) {
            val raw = key.removePrefix("system.")
            if (raw.isNotBlank()) {
                state[raw] = value
            }
        } else if (!key.startsWith("key.")) {
            state["system.$key"] = value
        }
    }

    fun setSystemVariable(key: String, value: String) {
        update(key, value)
    }

    fun updateAll(values: Map<String, String>) {
        state.putAll(values)
    }

    /**
     * Records a hardware/system event into the rolling sliding buffer.
     * Automatically prunes events older than 8000ms.
     */
    fun recordTemporalEvent(name: String, timestampMs: Long = SystemClock.elapsedRealtime()) {
        val cleanName = name.trim().lowercase()
        val now = SystemClock.elapsedRealtime()
        val cutoff = now - 8000L

        while (temporalEvents.peekFirst()?.let { it.timestampMs < cutoff } == true) {
            temporalEvents.pollFirst()
        }
        temporalEvents.addLast(TemporalEvent(cleanName, timestampMs))
    }

    fun recordEvent(name: String) = recordTemporalEvent(name)

    fun getRecentTemporalEvents(windowMs: Long = 5000L): List<TemporalEvent> {
        val cutoff = SystemClock.elapsedRealtime() - windowMs
        return temporalEvents.filter { it.timestampMs >= cutoff }
    }

    /**
     * Checks if the chronological tail of recorded events matches [sequence] within [timeoutMs].
     */
    fun matchesSequence(sequence: List<String>, timeoutMs: Long = 2000L): Boolean {
        if (sequence.isEmpty()) return false
        val now = SystemClock.elapsedRealtime()
        val cutoff = now - timeoutMs.coerceAtLeast(100L)
        val recent = temporalEvents.filter { it.timestampMs >= cutoff }.toList()
        if (recent.size < sequence.size) return false

        // Match the sequence at the tail of recent events
        val tail = recent.takeLast(sequence.size)
        for (i in sequence.indices) {
            val expected = sequence[i].trim().lowercase()
            val actual = tail[i].name
            if (expected != actual) {
                return false
            }
        }

        val elapsed = tail.last().timestampMs - tail.first().timestampMs
        return elapsed <= timeoutMs
    }

    fun clearTemporalEvents() {
        temporalEvents.clear()
    }
}
