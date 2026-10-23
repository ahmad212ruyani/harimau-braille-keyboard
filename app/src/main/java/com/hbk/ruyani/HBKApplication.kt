package com.hbk.ruyani

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

/**
 * Kelas Application Utama Harimau Braille Keyboard.
 * Menyediakan pengaman global terhadap crash (UncaughtExceptionHandler)
 * serta inisialisasi aman untuk dependensi eksternal.
 */
class HBKApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Pasang Global Uncaught Exception Handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("HBK_CRASH", "Fatal unhandled exception in thread ${thread.name}: ${throwable.message}", throwable)
            try {
                // Simpan pesan error terakhir ke SharedPreferences agar bisa didiagnosis jika perlu
                getSharedPreferences("hbk_crash_logs", MODE_PRIVATE)
                    .edit()
                    .putString("last_crash", "${throwable.javaClass.simpleName}: ${throwable.message}\n${Log.getStackTraceString(throwable)}")
                    .putLong("crash_time", System.currentTimeMillis())
                    .commit()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // 2. Inisialisasi Firebase secara aman (Fail-Safe)
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (e: Exception) {
            Log.w("HBK_FIREBASE", "Firebase initialization deferred or unavailable: ${e.message}")
        }
    }
}
