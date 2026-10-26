package com.example.data.local

import android.util.Base64
import androidx.room.TypeConverter
import com.example.domain.model.GlyphFrame
import com.example.domain.model.MATRIX_CELL_COUNT
import org.json.JSONArray
import org.json.JSONObject

/**
 * Room TypeConverters for 25x25 byte arrays, multi-frame keyframe lists, and tag lists.
 * Uses compact Base64 + JSON encoding for zero-overhead local SQLite persistence.
 */
class GlyphTypeConverters {

    @TypeConverter
    fun fromByteArray(bytes: ByteArray?): String {
        if (bytes == null) return ""
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    @TypeConverter
    fun toByteArray(encoded: String?): ByteArray {
        if (encoded.isNullOrBlank()) return ByteArray(MATRIX_CELL_COUNT)
        return try {
            val decoded = Base64.decode(encoded, Base64.NO_WRAP)
            if (decoded.size == MATRIX_CELL_COUNT) decoded else ByteArray(MATRIX_CELL_COUNT) { idx ->
                decoded.getOrElse(idx) { 0 }
            }
        } catch (_: Exception) {
            ByteArray(MATRIX_CELL_COUNT)
        }
    }

    @TypeConverter
    fun fromFrameList(frames: List<GlyphFrame>?): String {
        val array = JSONArray()
        frames?.forEach { frame ->
            val obj = JSONObject()
            obj.put("id", frame.id)
            obj.put("durationMs", frame.durationMs)
            obj.put("pixels", fromByteArray(frame.pixels))
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toFrameList(json: String?): List<GlyphFrame> {
        if (json.isNullOrBlank()) return listOf(GlyphFrame())
        return try {
            val array = JSONArray(json)
            val result = mutableListOf<GlyphFrame>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", "frame_$i")
                val durationMs = obj.optLong("durationMs", 120L)
                val pixels = toByteArray(obj.optString("pixels", ""))
                result.add(GlyphFrame(id = id, durationMs = durationMs, pixels = pixels))
            }
            if (result.isEmpty()) listOf(GlyphFrame()) else result
        } catch (_: Exception) {
            listOf(GlyphFrame())
        }
    }

    @TypeConverter
    fun fromStringList(tags: List<String>?): String {
        val array = JSONArray()
        tags?.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            List(array.length()) { idx -> array.optString(idx) }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
