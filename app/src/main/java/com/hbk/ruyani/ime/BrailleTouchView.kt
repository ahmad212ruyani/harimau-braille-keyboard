package com.hbk.ruyani.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.hbk.ruyani.data.BraillePreferences
import com.hbk.ruyani.translate.BrailleCharacter
import kotlin.math.hypot

/**
 * Tampilan bidang sentuh multi-jari (6 Titik Braille).
 * Mendeteksi akord sentuhan multi-titik dan gestur usapan sesuai dengan
 * posisi kalibrasi jari pengguna.
 */
class BrailleTouchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface BrailleInputListener {
        fun onCharacterEntered(character: BrailleCharacter)
        fun onSwipeLeft(pointerCount: Int)
        fun onSwipeRight(pointerCount: Int)
        fun onSwipeUp(pointerCount: Int)
        fun onSwipeDown(pointerCount: Int)
        fun onDotPressed(dotIndex: Int)
    }

    var inputListener: BrailleInputListener? = null
    private val preferences = BraillePreferences(context)

    // Data titik terkalibrasi
    private var dotPoints: List<BraillePreferences.DotPoint> = emptyList()

    // Status titik yang sedang aktif ditekan pada gesture saat ini
    private val activeDots = BooleanArray(7) // index 1..6
    private val pressedInCurrentChord = mutableSetOf<Int>()

    private val gestureDetector = BrailleGestureDetector(object : BrailleGestureDetector.GestureListener {
        override fun onSwipeLeft(pointerCount: Int) {
            inputListener?.onSwipeLeft(pointerCount)
        }

        override fun onSwipeRight(pointerCount: Int) {
            inputListener?.onSwipeRight(pointerCount)
        }

        override fun onSwipeUp(pointerCount: Int) {
            inputListener?.onSwipeUp(pointerCount)
        }

        override fun onSwipeDown(pointerCount: Int) {
            inputListener?.onSwipeDown(pointerCount)
        }
    })

    // Paint objek untuk penggambaran titik
    private val paintIdle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33424242")
        style = Paint.Style.FILL
    }

    private val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val paintActive = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676")
        style = Paint.Style.FILL
    }

    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        textAlign = Paint.Align.CENTER
    }

    init {
        reloadConfiguration()
    }

    fun reloadConfiguration() {
        dotPoints = preferences.getCalibratedDots()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        reloadConfiguration()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Cek apakah aksi merupakan usapan (swipe)
        val isSwipe = gestureDetector.onTouchEvent(event)

        val pointerCount = event.pointerCount
        val radius = preferences.dotRadiusDp * resources.displayMetrics.density

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_MOVE -> {
                // Periksa setiap jari yang sedang menyentuh layar
                for (p in 0 until pointerCount) {
                    val px = event.getX(p)
                    val py = event.getY(p)

                    // Cocokkan dengan titik braille terdekat
                    dotPoints.forEach { dot ->
                        val targetX = dot.xPercent * width
                        val targetY = dot.yPercent * height
                        val distance = hypot(px - targetX, py - targetY)

                        if (distance <= radius * 1.6f) { // Toleransi sentuhan
                            if (!activeDots[dot.dotIndex]) {
                                activeDots[dot.dotIndex] = true
                                pressedInCurrentChord.add(dot.dotIndex)
                                inputListener?.onDotPressed(dot.dotIndex)
                            }
                        }
                    }
                }
                invalidate()
            }

            MotionEvent.ACTION_POINTER_UP -> {
                invalidate()
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                // Ketika seluruh jari diangkat, jika bukan swipe, keluarkan karakter Braille
                if (!isSwipe && pressedInCurrentChord.isNotEmpty()) {
                    val char = BrailleCharacter(*pressedInCurrentChord.toIntArray())
                    inputListener?.onCharacterEntered(char)
                }

                // Reset status sentuhan
                for (i in 1..6) activeDots[i] = false
                pressedInCurrentChord.clear()
                gestureDetector.reset()
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val radius = preferences.dotRadiusDp * resources.displayMetrics.density

        dotPoints.forEach { dot ->
            val cx = dot.xPercent * width
            val cy = dot.yPercent * height
            val isActive = activeDots[dot.dotIndex]

            // Lingkaran luar/border
            canvas.drawCircle(cx, cy, radius, paintBorder)

            // Lingkaran isi (aktif / redup)
            val fillPaint = if (isActive) paintActive else paintIdle
            canvas.drawCircle(cx, cy, radius - 2, fillPaint)

            // Nomor titik
            val textY = cy - ((paintText.descent() + paintText.ascent()) / 2)
            canvas.drawText(dot.dotIndex.toString(), cx, textY, paintText)
        }
    }
}
