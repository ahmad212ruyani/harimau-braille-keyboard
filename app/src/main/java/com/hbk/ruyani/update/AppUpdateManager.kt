package com.hbk.ruyani.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.hbk.ruyani.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Manajer Pembaruan Otomatis (In-App Auto Update) langsung dari GitHub Releases.
 * Memeriksa versi baru, mengunduh APK, dan langsung menimpa versi terpasang tanpa perantara.
 */
class AppUpdateManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class UpdateInfo(
        val latestVersionName: String,
        val releaseNotes: String,
        val apkDownloadUrl: String,
        val isNewer: Boolean
    )

    /**
     * Memeriksa rilis terbaru di repositori GitHub.
     */
    suspend fun checkForUpdates(
        repoOwner: String = BuildConfig.GITHUB_REPO_OWNER,
        repoName: String = BuildConfig.GITHUB_REPO_NAME
    ): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("GitHub API error: ${response.code}"))
                }

                val body = response.body?.string() ?: throw Exception("Empty response body")
                val json = JSONObject(body)

                val tagName = json.optString("tag_name", "v1.0.0").removePrefix("v").trim()
                val releaseNotes = json.optString("body", "Pembaruan performa dan perbaikan bug.")

                // Cari aset berkas berekstensi .apk
                var apkUrl = ""
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                if (apkUrl.isBlank()) {
                    return@withContext Result.failure(Exception("Tidak ditemukan berkas APK pada rilis terbaru."))
                }

                val currentVersion = BuildConfig.VERSION_NAME.removePrefix("v").trim()
                val isNewer = isVersionNewer(tagName, currentVersion)

                Result.success(
                    UpdateInfo(
                        latestVersionName = tagName,
                        releaseNotes = releaseNotes,
                        apkDownloadUrl = apkUrl,
                        isNewer = isNewer
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mengunduh berkas APK baru dengan pemantauan progres (0..100%).
     */
    suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(downloadUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Gagal mengunduh berkas: ${response.code}"))
                }

                val body = response.body ?: throw Exception("Respons unduhan kosong")
                val totalBytes = body.contentLength()
                val destFile = File(context.cacheDir, "update_harimau_braille.apk")

                body.byteStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var downloadedBytes = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                                withContext(Dispatchers.Main) {
                                    onProgress(percent)
                                }
                            }
                        }
                        output.flush()
                    }
                }
                Result.success(destFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Meminta sistem Android untuk memasang APK dan langsung menimpa aplikasi aktif.
     */
    fun installApk(activity: Activity, apkFile: File) {
        if (!apkFile.exists()) return

        // Periksa izin menginstal aplikasi dari sumber tak dikenal (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(installIntent)
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        try {
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val maxLength = maxOf(latestParts.size, currentParts.size)

            for (i in 0 until maxLength) {
                val lat = latestParts.getOrElse(i) { 0 }
                val cur = currentParts.getOrElse(i) { 0 }
                if (lat > cur) return true
                if (lat < cur) return false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
