package com.hbk.ruyani.ui

import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hbk.ruyani.R
import com.hbk.ruyani.data.BraillePreferences
import com.hbk.ruyani.data.CloudSyncManager
import com.hbk.ruyani.feedback.FeedbackManager
import com.hbk.ruyani.ime.BrailleTouchView
import kotlinx.coroutines.launch

/**
 * Aktivitas kalibrasi posisi 6 jari pengguna (Layar Penuh Landscape).
 * Pengguna menempelkan 6 jari, posisi sentuhan direkam secara otomatis
 * dan disimpan baik di memori lokal maupun di awan Firebase.
 */
class CalibrationActivity : AppCompatActivity() {

    private lateinit var preferences: BraillePreferences
    private lateinit var cloudSyncManager: CloudSyncManager
    private lateinit var feedbackManager: FeedbackManager
    private var touchView: BrailleTouchView? = null

    private val detectedPoints = mutableListOf<BraillePreferences.DotPoint>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)

        preferences = BraillePreferences(this)
        cloudSyncManager = CloudSyncManager(this)
        feedbackManager = FeedbackManager(this)

        touchView = findViewById(R.id.calibrationTouchView)
        val btnSave = findViewById<Button>(R.id.btnSaveCalibration)
        val btnReset = findViewById<Button>(R.id.btnResetDefault)

        btnSave.setOnClickListener {
            if (detectedPoints.size == 6) {
                preferences.saveCalibratedDots(detectedPoints)
                touchView?.reloadConfiguration()
                feedbackManager.vibrate(FeedbackManager.HapticType.CHAR_COMMITTED)

                // Unggah kalibrasi baru ke Cloud jika login
                lifecycleScope.launch {
                    cloudSyncManager.saveSettingsToCloud()
                }

                Toast.makeText(this, "Posisi 6 titik berhasil disimpan!", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(
                    this,
                    "Harap tempelkan 6 jari secara bersamaan terlebih dahulu.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        btnReset.setOnClickListener {
            preferences.resetToDefaults()
            touchView?.reloadConfiguration()
            detectedPoints.clear()
            feedbackManager.vibrate(FeedbackManager.HapticType.SPACE)
            Toast.makeText(this, "Posisi titik direset ke standar.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount == 6 && event.actionMasked == MotionEvent.ACTION_MOVE) {
            val width = resources.displayMetrics.widthPixels.toFloat()
            val height = resources.displayMetrics.heightPixels.toFloat()

            // Kumpulkan 6 koordinat sentuhan
            val touchCoordinates = (0 until 6).map { i ->
                Pair(event.getX(i), event.getY(i))
            }

            // Pisahkan tangan kiri (3 titik di sisi kiri layar) dan tangan kanan (3 titik di sisi kanan layar)
            val sortedByX = touchCoordinates.sortedBy { it.first }
            val leftHand = sortedByX.take(3).sortedBy { it.second } // Urut dari atas ke bawah: titik 1, 2, 3
            val rightHand = sortedByX.takeLast(3).sortedBy { it.second } // Urut dari atas ke bawah: titik 4, 5, 6

            detectedPoints.clear()
            // Tangan kiri: Titik 1, 2, 3
            leftHand.forEachIndexed { idx, point ->
                detectedPoints.add(
                    BraillePreferences.DotPoint(
                        dotIndex = idx + 1,
                        xPercent = (point.first / width).coerceIn(0.05f, 0.45f),
                        yPercent = (point.second / height).coerceIn(0.1f, 0.9f)
                    )
                )
            }
            // Tangan kanan: Titik 4, 5, 6
            rightHand.forEachIndexed { idx, point ->
                detectedPoints.add(
                    BraillePreferences.DotPoint(
                        dotIndex = idx + 4,
                        xPercent = (point.first / width).coerceIn(0.55f, 0.95f),
                        yPercent = (point.second / height).coerceIn(0.1f, 0.9f)
                    )
                )
            }

            // Beri getaran feedback saat 6 titik sukses terdeteksi
            feedbackManager.vibrate(FeedbackManager.HapticType.CHAR_COMMITTED)
            feedbackManager.speak("6 jari terdeteksi. Tekan simpan untuk menerapkan.")

            preferences.saveCalibratedDots(detectedPoints)
            touchView?.reloadConfiguration()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        feedbackManager.release()
    }
}
