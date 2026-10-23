package com.hbk.ruyani.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pengelola preferensi keyboard dan kalibrasi posisi 6 titik Braille.
 * Mendukung penyimpanan lokal terisolasi di SharedPreferences dan konversi ke Map/JSON
 * untuk sinkronisasi awan Firebase Firestore.
 */
class BraillePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    enum class KeyboardOrientation {
        TABLETOP,       // Ponsel diletakkan mendatar di atas meja
        SCREEN_AWAY     // Ponsel digenggam dengan layar menghadap ke depan/luar
    }

    data class DotPoint(val dotIndex: Int, val xPercent: Float, val yPercent: Float)

    var orientation: KeyboardOrientation
        get() {
            val name = prefs.getString(KEY_ORIENTATION, KeyboardOrientation.TABLETOP.name)
            return try {
                KeyboardOrientation.valueOf(name ?: KeyboardOrientation.TABLETOP.name)
            } catch (e: Exception) {
                KeyboardOrientation.TABLETOP
            }
        }
        set(value) = prefs.edit().putString(KEY_ORIENTATION, value.name).apply()

    var isHapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC, value).apply()

    var isSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    var isGrade2Enabled: Boolean
        get() = prefs.getBoolean(KEY_GRADE2, false)
        set(value) = prefs.edit().putBoolean(KEY_GRADE2, value).apply()

    var dotRadiusDp: Float
        get() = prefs.getFloat(KEY_DOT_RADIUS, 38f)
        set(value) = prefs.edit().putFloat(KEY_DOT_RADIUS, value).apply()

    /**
     * Mengambil koordinat 6 titik (persentase layar 0.0 - 1.0).
     * Jika belum pernah dikalibrasi, memberikan posisi default ergonomis 6 jari.
     */
    fun getCalibratedDots(): List<DotPoint> {
        val jsonString = prefs.getString(KEY_CALIBRATION_DOTS, null)
        if (!jsonString.isNullOrBlank()) {
            try {
                val list = mutableListOf<DotPoint>()
                val array = JSONArray(jsonString)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        DotPoint(
                            dotIndex = obj.getInt("dotIndex"),
                            xPercent = obj.getDouble("xPercent").toFloat(),
                            yPercent = obj.getDouble("yPercent").toFloat()
                        )
                    )
                }
                if (list.size == 6) return list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return getDefaultDots()
    }

    fun saveCalibratedDots(dots: List<DotPoint>) {
        val array = JSONArray()
        dots.forEach { dot ->
            val obj = JSONObject()
            obj.put("dotIndex", dot.dotIndex)
            obj.put("xPercent", dot.xPercent.toDouble())
            obj.put("yPercent", dot.yPercent.toDouble())
            array.put(obj)
        }
        prefs.edit().putString(KEY_CALIBRATION_DOTS, array.toString()).apply()
    }

    fun resetToDefaults() {
        prefs.edit()
            .remove(KEY_CALIBRATION_DOTS)
            .putBoolean(KEY_HAPTIC, true)
            .putBoolean(KEY_SOUND, true)
            .putBoolean(KEY_GRADE2, false)
            .putString(KEY_ORIENTATION, KeyboardOrientation.TABLETOP.name)
            .apply()
    }

    /**
     * Konversi seluruh pengaturan menjadi Map untuk disimpan ke Firebase Firestore.
     */
    fun toMap(): Map<String, Any> {
        val dotsList = getCalibratedDots().map { dot ->
            mapOf(
                "dotIndex" to dot.dotIndex,
                "xPercent" to dot.xPercent,
                "yPercent" to dot.yPercent
            )
        }
        return mapOf(
            "orientation" to orientation.name,
            "isHapticEnabled" to isHapticEnabled,
            "isSoundEnabled" to isSoundEnabled,
            "isGrade2Enabled" to isGrade2Enabled,
            "dotRadiusDp" to dotRadiusDp,
            "dots" to dotsList,
            "updatedAt" to System.currentTimeMillis()
        )
    }

    /**
     * Memulihkan pengaturan dari data Firebase Firestore (atau JSON cadangan).
     */
    @Suppress("UNCHECKED_CAST")
    fun applyFromMap(map: Map<String, Any>) {
        val editor = prefs.edit()
        (map["orientation"] as? String)?.let { editor.putString(KEY_ORIENTATION, it) }
        (map["isHapticEnabled"] as? Boolean)?.let { editor.putBoolean(KEY_HAPTIC, it) }
        (map["isSoundEnabled"] as? Boolean)?.let { editor.putBoolean(KEY_SOUND, it) }
        (map["isGrade2Enabled"] as? Boolean)?.let { editor.putBoolean(KEY_GRADE2, it) }
        (map["dotRadiusDp"] as? Number)?.let { editor.putFloat(KEY_DOT_RADIUS, it.toFloat()) }

        (map["dots"] as? List<Map<String, Any>>)?.let { dotsList ->
            val array = JSONArray()
            dotsList.forEach { dotMap ->
                val obj = JSONObject()
                obj.put("dotIndex", (dotMap["dotIndex"] as? Number)?.toInt() ?: 1)
                obj.put("xPercent", (dotMap["xPercent"] as? Number)?.toDouble() ?: 0.0)
                obj.put("yPercent", (dotMap["yPercent"] as? Number)?.toDouble() ?: 0.0)
                array.put(obj)
            }
            editor.putString(KEY_CALIBRATION_DOTS, array.toString())
        }
        editor.apply()
    }

    private fun getDefaultDots(): List<DotPoint> {
        // Standar posisi 6 titik ergonomis (Tangan kiri: 1, 2, 3; Tangan kanan: 4, 5, 6)
        return listOf(
            DotPoint(1, 0.28f, 0.30f), // Telunjuk kiri
            DotPoint(2, 0.20f, 0.50f), // Jari tengah kiri
            DotPoint(3, 0.12f, 0.70f), // Jari manis kiri
            DotPoint(4, 0.72f, 0.30f), // Telunjuk kanan
            DotPoint(5, 0.80f, 0.50f), // Jari tengah kanan
            DotPoint(6, 0.88f, 0.70f)  // Jari manis kanan
        )
    }

    companion object {
        private const val PREFS_NAME = "harimau_braille_prefs"
        private const val KEY_ORIENTATION = "key_orientation"
        private const val KEY_HAPTIC = "key_haptic"
        private const val KEY_SOUND = "key_sound"
        private const val KEY_GRADE2 = "key_grade2"
        private const val KEY_DOT_RADIUS = "key_dot_radius"
        private const val KEY_CALIBRATION_DOTS = "key_calibration_dots"
    }
}
