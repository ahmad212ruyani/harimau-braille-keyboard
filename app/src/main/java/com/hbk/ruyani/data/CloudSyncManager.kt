package com.hbk.ruyani.data

import android.content.Context
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Manajer Sinkronisasi Awan (Cloud Sync) menggunakan Firebase Auth & Firestore.
 * Memastikan saat pengguna login di HP baru, seluruh setelan keyboard dan titik kalibrasi
 * otomatis terunduh dan terpasang tanpa tertukar dengan pengguna lain.
 */
class CloudSyncManager(private val context: Context) {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val preferences = BraillePreferences(context)

    val currentUser: FirebaseUser?
        get() = try {
            auth.currentUser
        } catch (e: Exception) {
            null
        }

    val isUserLoggedIn: Boolean
        get() = currentUser != null

    /**
     * Masuk menggunakan kredensial Google Sign-In.
     */
    suspend fun signInWithGoogleCredential(credential: AuthCredential): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: throw IllegalStateException("Pengguna tidak ditemukan")
            // Otomatis tarik data awan setelah login berhasil
            restoreSettingsFromCloud(user.uid)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unggah setelan lokal ke dokumen Firestore privat: users/{uid}/settings/keyboard
     */
    suspend fun saveSettingsToCloud(): Result<Unit> {
        val uid = currentUser?.uid ?: return Result.failure(IllegalStateException("Pengguna belum login"))
        return try {
            val data = preferences.toMap()
            firestore.collection("users")
                .document(uid)
                .collection("settings")
                .document("keyboard")
                .set(data, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unduh dan pulihkan setelan dari Cloud saat pengguna masuk di HP baru.
     */
    suspend fun restoreSettingsFromCloud(uid: String = currentUser?.uid ?: ""): Result<Boolean> {
        if (uid.isBlank()) return Result.failure(IllegalStateException("UID kosong"))
        return try {
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("settings")
                .document("keyboard")
                .get()
                .await()

            if (snapshot.exists() && snapshot.data != null) {
                preferences.applyFromMap(snapshot.data!!)
                Result.success(true) // Data ditemukan dan diterapkan
            } else {
                // Belum ada data di cloud, simpan data awal ke cloud
                saveSettingsToCloud()
                Result.success(false) // Data baru diinisialisasi
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
