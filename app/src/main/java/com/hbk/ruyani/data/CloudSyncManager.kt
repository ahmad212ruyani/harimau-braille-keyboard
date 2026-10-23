package com.hbk.ruyani.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Manajer Sinkronisasi Awan (Cloud Sync) aman & tahan crash.
 * Mendukung Firebase Auth + Firestore jika terhubung,
 * dan menyediakan fitur pencadangan berkas JSON mandiri (Export/Import).
 */
class CloudSyncManager(private val context: Context) {

    private val preferences = BraillePreferences(context)

    val isFirebaseAvailable: Boolean
        get() = try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Throwable) {
            false
        }

    val currentUser: FirebaseUser?
        get() = try {
            if (isFirebaseAvailable) FirebaseAuth.getInstance().currentUser else null
        } catch (e: Throwable) {
            Log.w("CloudSyncManager", "FirebaseAuth not ready: ${e.message}")
            null
        }

    val isUserLoggedIn: Boolean
        get() = currentUser != null

    /**
     * Masuk menggunakan kredensial Google Sign-In.
     */
    suspend fun signInWithGoogleCredential(credential: AuthCredential): Result<FirebaseUser> {
        if (!isFirebaseAvailable) {
            return Result.failure(IllegalStateException("Layanan Firebase belum diaktifkan"))
        }
        return try {
            val auth = FirebaseAuth.getInstance()
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: throw IllegalStateException("Pengguna tidak ditemukan")
            restoreSettingsFromCloud(user.uid)
            Result.success(user)
        } catch (e: Throwable) {
            Result.failure(Exception(e))
        }
    }

    /**
     * Unggah setelan lokal ke dokumen Firestore: users/{uid}/settings/keyboard
     */
    suspend fun saveSettingsToCloud(): Result<Unit> {
        val user = currentUser ?: return Result.failure(IllegalStateException("Pengguna belum login"))
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val data = preferences.toMap()
            firestore.collection("users")
                .document(user.uid)
                .collection("settings")
                .document("keyboard")
                .set(data, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Throwable) {
            Result.failure(Exception(e))
        }
    }

    /**
     * Unduh dan pulihkan setelan dari Cloud saat pengguna masuk di HP baru.
     */
    suspend fun restoreSettingsFromCloud(uid: String = currentUser?.uid ?: ""): Result<Boolean> {
        if (uid.isBlank() || !isFirebaseAvailable) {
            return Result.failure(IllegalStateException("Firebase tidak tersedia atau UID kosong"))
        }
        return try {
            val firestore = FirebaseFirestore.getInstance()
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("settings")
                .document("keyboard")
                .get()
                .await()

            if (snapshot.exists() && snapshot.data != null) {
                preferences.applyFromMap(snapshot.data!!)
                Result.success(true)
            } else {
                saveSettingsToCloud()
                Result.success(false)
            }
        } catch (e: Throwable) {
            Result.failure(Exception(e))
        }
    }

    fun signOut() {
        try {
            if (isFirebaseAvailable) {
                FirebaseAuth.getInstance().signOut()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    /**
     * Ekspor seluruh pengaturan ke teks JSON untuk cadangan mandiri (bisa dikirim via WA / Drive).
     */
    fun exportSettingsToJsonString(): String {
        val map = preferences.toMap()
        return JSONObject(map).toString(2)
    }

    /**
     * Pulihkan pengaturan dari teks JSON cadangan.
     */
    fun importSettingsFromJsonString(jsonString: String): Boolean {
        return try {
            val json = JSONObject(jsonString)
            val map = mutableMapOf<String, Any>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "dots") {
                    val dotsArray = json.getJSONArray(key)
                    val dotsList = mutableListOf<Map<String, Any>>()
                    for (i in 0 until dotsArray.length()) {
                        val dObj = dotsArray.getJSONObject(i)
                        dotsList.add(
                            mapOf(
                                "dotIndex" to dObj.getInt("dotIndex"),
                                "xPercent" to dObj.getDouble("xPercent").toFloat(),
                                "yPercent" to dObj.getDouble("yPercent").toFloat()
                            )
                        )
                    }
                    map[key] = dotsList
                } else {
                    map[key] = json.get(key)
                }
            }
            preferences.applyFromMap(map)
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }
}
