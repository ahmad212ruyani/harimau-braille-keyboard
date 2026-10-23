package com.hbk.ruyani.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.hbk.ruyani.R
import com.hbk.ruyani.data.BraillePreferences
import com.hbk.ruyani.feedback.FeedbackManager
import com.hbk.ruyani.translate.BrailleCharacter
import com.hbk.ruyani.translate.BrailleTranslator

/**
 * Layanan Utama Harimau Braille Keyboard (InputMethodService).
 * Bertindak sebagai penyedia metode input papan ketik di seluruh sistem Android.
 */
class HarimauBrailleImeService : InputMethodService(), BrailleTouchView.BrailleInputListener {

    private lateinit var feedbackManager: FeedbackManager
    private lateinit var preferences: BraillePreferences
    private val translator = BrailleTranslator()
    private var touchView: BrailleTouchView? = null

    override fun onCreate() {
        super.onCreate()
        feedbackManager = FeedbackManager(this)
        preferences = BraillePreferences(this)
    }

    override fun onCreateInputView(): View {
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(android.R.dimen.app_icon_size) * 6
            )
            setBackgroundColor(resources.getColor(R.color.background_dark, null))
        }

        touchView = BrailleTouchView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            inputListener = this@HarimauBrailleImeService
        }

        root.addView(touchView)
        return root
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        feedbackManager.isHapticEnabled = preferences.isHapticEnabled
        feedbackManager.isSoundAnnouncementEnabled = preferences.isSoundEnabled
        translator.resetState()
        touchView?.reloadConfiguration()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        translator.resetState()
    }

    override fun onDestroy() {
        super.onDestroy()
        feedbackManager.release()
    }

    // --- BrailleTouchView Callbacks ---

    override fun onDotPressed(dotIndex: Int) {
        feedbackManager.vibrate(FeedbackManager.HapticType.DOT_TOUCH)
    }

    override fun onCharacterEntered(character: BrailleCharacter) {
        val ic = currentInputConnection ?: return

        when (val result = translator.translate(character)) {
            is BrailleTranslator.TranslationResult.CharacterOutput -> {
                ic.commitText(result.text, 1)
                feedbackManager.vibrate(FeedbackManager.HapticType.CHAR_COMMITTED)
                feedbackManager.speak(result.spokenDescription)
            }
            is BrailleTranslator.TranslationResult.ControlAction -> {
                feedbackManager.vibrate(FeedbackManager.HapticType.CHAR_COMMITTED)
                feedbackManager.speak(result.description)
            }
            is BrailleTranslator.TranslationResult.Unrecognized -> {
                feedbackManager.vibrate(FeedbackManager.HapticType.ERROR)
            }
        }
    }

    override fun onSwipeLeft(pointerCount: Int) {
        val ic = currentInputConnection ?: return

        if (pointerCount >= 2) {
            // Hapus satu kata
            deleteWord(ic)
            feedbackManager.vibrate(FeedbackManager.HapticType.DELETE)
            feedbackManager.speak(getString(R.string.tts_word_deleted))
        } else {
            // Hapus satu karakter
            ic.deleteSurroundingText(1, 0)
            feedbackManager.vibrate(FeedbackManager.HapticType.DELETE)
            feedbackManager.speak(getString(R.string.tts_deleted))
        }
    }

    override fun onSwipeRight(pointerCount: Int) {
        val ic = currentInputConnection ?: return

        if (pointerCount >= 2) {
            // Enter / Baris Baru
            sendKeyChar('\n')
            feedbackManager.vibrate(FeedbackManager.HapticType.SPACE)
            feedbackManager.speak(getString(R.string.tts_newline))
        } else {
            // Spasi
            ic.commitText(" ", 1)
            translator.onSpaceEntered()
            feedbackManager.vibrate(FeedbackManager.HapticType.SPACE)
            feedbackManager.speak(getString(R.string.tts_space))
        }
    }

    override fun onSwipeDown(pointerCount: Int) {
        if (pointerCount >= 2) {
            // Sembunyikan keyboard
            requestHideSelf(0)
            feedbackManager.speak(getString(R.string.tts_keyboard_hidden))
        }
    }

    override fun onSwipeUp(pointerCount: Int) {
        // Ganti metode input (IME picker) jika usap ke atas
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.showInputMethodPicker()
    }

    private fun deleteWord(ic: android.view.inputmethod.InputConnection) {
        val textBefore = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
        if (textBefore.isEmpty()) return

        var charsToDelete = 0
        var i = textBefore.length - 1

        // Lewati spasi di akhir kata jika ada
        while (i >= 0 && textBefore[i].isWhitespace()) {
            charsToDelete++
            i--
        }
        // Hitung panjang kata
        while (i >= 0 && !textBefore[i].isWhitespace()) {
            charsToDelete++
            i--
        }

        if (charsToDelete > 0) {
            ic.deleteSurroundingText(charsToDelete, 0)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }
}
