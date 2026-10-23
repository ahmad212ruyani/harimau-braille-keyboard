package com.hbk.ruyani.ime

import android.view.MotionEvent
import kotlin.math.abs

/**
 * Pendeteksi gestur usapan (Swipe) untuk papan ketik Braille.
 * Mengklasifikasikan arah usapan (Kiri, Kanan, Atas, Bawah) dan jumlah jari (1 atau 2).
 */
class BrailleGestureDetector(private val listener: GestureListener) {

    interface GestureListener {
        fun onSwipeLeft(pointerCount: Int)
        fun onSwipeRight(pointerCount: Int)
        fun onSwipeUp(pointerCount: Int)
        fun onSwipeDown(pointerCount: Int)
    }

    private var startX = 0f
    private var startY = 0f
    private var maxPointers = 1
    private var isGestureInProgress = false

    private val swipeThreshold = 120f // Jarak minimum usapan (pixel)

    fun onTouchEvent(event: MotionEvent): Boolean {
        val count = event.pointerCount
        if (count > maxPointers) {
            maxPointers = count
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                maxPointers = 1
                isGestureInProgress = true
            }

            MotionEvent.ACTION_UP -> {
                if (isGestureInProgress) {
                    val endX = event.x
                    val endY = event.y
                    val deltaX = endX - startX
                    val deltaY = endY - startY

                    if (abs(deltaX) > swipeThreshold || abs(deltaY) > swipeThreshold) {
                        if (abs(deltaX) > abs(deltaY)) {
                            // Usapan Horisontal
                            if (deltaX > 0) {
                                listener.onSwipeRight(maxPointers)
                            } else {
                                listener.onSwipeLeft(maxPointers)
                            }
                        } else {
                            // Usapan Vertikal
                            if (deltaY > 0) {
                                listener.onSwipeDown(maxPointers)
                            } else {
                                listener.onSwipeUp(maxPointers)
                            }
                        }
                        isGestureInProgress = false
                        return true
                    }
                }
                isGestureInProgress = false
            }

            MotionEvent.ACTION_CANCEL -> {
                isGestureInProgress = false
            }
        }
        return false
    }

    fun reset() {
        maxPointers = 1
        isGestureInProgress = false
    }
}
