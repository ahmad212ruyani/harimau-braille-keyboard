package com.hbk.ruyani.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Pengatur umpan balik multimodal (Getaran Haptic dan Suara TextToSpeech)
 * untuk pengguna tunanetra / tunarungu saat menggunakan keyboard Braille.
 */
class FeedbackManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    var isHapticEnabled: Boolean = true
    var isSoundAnnouncementEnabled: Boolean = true

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("id", "ID"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback ke bahasa Inggris jika suara Bahasa Indonesia belum terpasang
                tts?.setLanguage(Locale.US)
            }
            isTtsReady = true
        }
    }

    enum class HapticType {
        DOT_TOUCH,
        CHAR_COMMITTED,
        SPACE,
        DELETE,
        ERROR,
        LONG_PRESS
    }

    fun vibrate(type: HapticType) {
        if (!isHapticEnabled || vibrator == null || !vibrator!!.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when (type) {
                HapticType.DOT_TOUCH -> VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE)
                HapticType.CHAR_COMMITTED -> VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)
                HapticType.SPACE -> VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE)
                HapticType.DELETE -> VibrationEffect.createOneShot(55, 200)
                HapticType.ERROR -> {
                    val timings = longArrayOf(0, 50, 40, 50)
                    val amplitudes = intArrayOf(0, 255, 0, 255)
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                }
                HapticType.LONG_PRESS -> VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            val duration = when (type) {
                HapticType.DOT_TOUCH -> 15L
                HapticType.CHAR_COMMITTED -> 35L
                HapticType.SPACE -> 45L
                HapticType.DELETE -> 55L
                HapticType.ERROR -> 120L
                HapticType.LONG_PRESS -> 100L
            }
            @Suppress("DEPRECATION")
            vibrator?.vibrate(duration)
        }
    }

    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (!isSoundAnnouncementEnabled || !isTtsReady || text.isBlank()) return
        tts?.speak(text, queueMode, null, "HBK_${System.currentTimeMillis()}")
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
    }
}
