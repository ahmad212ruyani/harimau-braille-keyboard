package com.hbk.ruyani.ui

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.hbk.ruyani.BuildConfig
import com.hbk.ruyani.R
import com.hbk.ruyani.data.CloudSyncManager
import com.hbk.ruyani.update.AppUpdateManager
import kotlinx.coroutines.launch

/**
 * Layar Utama (Pusat Kendali & Pengaturan Harimau Braille Keyboard).
 * Mengatur aktivasi keyboard Android, cadangan pengaturan, Google Sign-In,
 * dan in-app auto-update langsung dari GitHub Releases secara fail-safe (tahan crash).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var cloudSyncManager: CloudSyncManager
    private lateinit var updateManager: AppUpdateManager
    private var googleSignInClient: GoogleSignInClient? = null

    private lateinit var tvKeyboardStatus: TextView
    private lateinit var btnEnableIme: Button
    private lateinit var btnSelectIme: Button
    private lateinit var tvSyncStatus: TextView
    private lateinit var btnLoginGoogle: Button
    private lateinit var btnLogout: Button
    private lateinit var btnExportSettings: Button
    private lateinit var btnImportSettings: Button
    private lateinit var btnCalibrate: Button
    private lateinit var tvCurrentVersion: TextView
    private lateinit var btnCheckUpdate: Button

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            handleGoogleSignInSuccess(account)
        } catch (e: Exception) {
            Log.w("MainActivity", "Google sign-in canceled or failed: ${e.message}")
            Toast.makeText(this, "Login Google belum aktif atau dibatalkan.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cloudSyncManager = CloudSyncManager(this)
        updateManager = AppUpdateManager(this)

        initViews()
        setupGoogleSignInSafe()
        setupActions()
    }

    override fun onResume() {
        super.onResume()
        updateKeyboardStatusSafe()
        updateAuthStatusSafe()
    }

    private fun initViews() {
        tvKeyboardStatus = findViewById(R.id.tvKeyboardStatus)
        btnEnableIme = findViewById(R.id.btnEnableIme)
        btnSelectIme = findViewById(R.id.btnSelectIme)
        tvSyncStatus = findViewById(R.id.tvSyncStatus)
        btnLoginGoogle = findViewById(R.id.btnLoginGoogle)
        btnLogout = findViewById(R.id.btnLogout)
        btnExportSettings = findViewById(R.id.btnExportSettings)
        btnImportSettings = findViewById(R.id.btnImportSettings)
        btnCalibrate = findViewById(R.id.btnCalibrate)
        tvCurrentVersion = findViewById(R.id.tvCurrentVersion)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)

        tvCurrentVersion.text = "Versi Terpasang: v${BuildConfig.VERSION_NAME}"
    }

    private fun setupGoogleSignInSafe() {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
        } catch (e: Throwable) {
            Log.w("MainActivity", "Google Play Services sign-in unavailable: ${e.message}")
        }
    }

    private fun setupActions() {
        // 1. Tombol Aktifkan Keyboard di Pengaturan
        btnEnableIme.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            } catch (e: Throwable) {
                Toast.makeText(this, "Buka Setelan -> Bahasa & Masukan -> Keyboard", Toast.LENGTH_LONG).show()
            }
        }

        // 2. Tombol Pilih Keyboard Utama
        btnSelectIme.setOnClickListener {
            try {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            } catch (e: Throwable) {
                Toast.makeText(this, "Tidak dapat membuka pemilih keyboard", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Tombol Login Google
        btnLoginGoogle.setOnClickListener {
            try {
                googleSignInClient?.signInIntent?.let { intent ->
                    googleSignInLauncher.launch(intent)
                } ?: run {
                    Toast.makeText(this, "Layanan Google Sign-In sedang tidak tersedia.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Throwable) {
                Toast.makeText(this, "Login Google tidak dapat diluncurkan.", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Tombol Logout
        btnLogout.setOnClickListener {
            try {
                cloudSyncManager.signOut()
                googleSignInClient?.signOut()
                updateAuthStatusSafe()
                Toast.makeText(this, "Berhasil keluar akun", Toast.LENGTH_SHORT).show()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        // 5. Bagikan / Cadangkan Pengaturan (Export)
        btnExportSettings.setOnClickListener {
            exportSettings()
        }

        // 6. Pulihkan Pengaturan (Import)
        btnImportSettings.setOnClickListener {
            showImportDialog()
        }

        // 7. Tombol Kalibrasi 6 Titik
        btnCalibrate.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }

        // 8. Tombol Periksa Pembaruan
        btnCheckUpdate.setOnClickListener {
            checkAppUpdate(isManualCheck = true)
        }
    }

    private fun exportSettings() {
        try {
            val json = cloudSyncManager.exportSettingsToJsonString()
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, json)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Cadangkan Pengaturan Harimau Braille ke...")
            startActivity(shareIntent)
        } catch (e: Throwable) {
            Toast.makeText(this, "Gagal mengekspor: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showImportDialog() {
        val input = EditText(this).apply {
            hint = "Tempel teks cadangan JSON di sini..."
            setPadding(32, 32, 32, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Pulihkan Pengaturan")
            .setMessage("Tempelkan teks cadangan yang Anda simpan sebelumnya:")
            .setView(input)
            .setPositiveButton("Pulihkan") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty() && cloudSyncManager.importSettingsFromJsonString(text)) {
                    Toast.makeText(this, "Pengaturan berhasil dipulihkan!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Format teks cadangan tidak valid.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun handleGoogleSignInSuccess(account: GoogleSignInAccount) {
        val idToken = account.idToken
        lifecycleScope.launch {
            try {
                if (idToken != null && cloudSyncManager.isFirebaseAvailable) {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    cloudSyncManager.signInWithGoogleCredential(credential)
                }
            } catch (e: Throwable) {
                Log.w("MainActivity", "Firebase auth fallback: ${e.message}")
            }
            Toast.makeText(this@MainActivity, "Tersambung: ${account.email}", Toast.LENGTH_SHORT).show()
            updateAuthStatusSafe()
        }
    }

    private fun updateKeyboardStatusSafe() {
        try {
            val enabledImeList = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_INPUT_METHODS) ?: ""
            val isEnabled = enabledImeList.contains(packageName)

            val currentIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
            val isDefault = currentIme.contains(packageName)

            if (!isEnabled) {
                tvKeyboardStatus.text = getString(R.string.status_keyboard_disabled)
                tvKeyboardStatus.setTextColor(getColor(R.color.error))
                btnEnableIme.visibility = View.VISIBLE
                btnSelectIme.visibility = View.GONE
            } else if (!isDefault) {
                tvKeyboardStatus.text = getString(R.string.status_not_default)
                tvKeyboardStatus.setTextColor(getColor(R.color.accent))
                btnEnableIme.visibility = View.GONE
                btnSelectIme.visibility = View.VISIBLE
            } else {
                tvKeyboardStatus.text = "Keyboard Aktif & Siap Digunakan!"
                tvKeyboardStatus.setTextColor(getColor(R.color.success))
                btnEnableIme.visibility = View.GONE
                btnSelectIme.visibility = View.VISIBLE
            }
        } catch (e: Throwable) {
            tvKeyboardStatus.text = "Harimau Braille Keyboard"
        }
    }

    private fun updateAuthStatusSafe() {
        try {
            val user = cloudSyncManager.currentUser
            val googleAccount = try {
                GoogleSignIn.getLastSignedInAccount(this)
            } catch (e: Throwable) {
                null
            }

            if (user != null || googleAccount != null) {
                val email = user?.email ?: googleAccount?.email ?: "Pengguna"
                tvSyncStatus.text = getString(R.string.sync_status_logged_in, email)
                tvSyncStatus.setTextColor(getColor(R.color.success))
                btnLoginGoogle.visibility = View.GONE
                btnLogout.visibility = View.VISIBLE
            } else {
                tvSyncStatus.text = getString(R.string.sync_status_not_logged_in)
                tvSyncStatus.setTextColor(getColor(R.color.text_secondary))
                btnLoginGoogle.visibility = View.VISIBLE
                btnLogout.visibility = View.GONE
            }
        } catch (e: Throwable) {
            tvSyncStatus.text = "Pengaturan tersimpan lokal di HP Anda."
        }
    }

    private fun checkAppUpdate(isManualCheck: Boolean) {
        if (isManualCheck) {
            Toast.makeText(this, getString(R.string.update_checking), Toast.LENGTH_SHORT).show()
        }

        lifecycleScope.launch {
            val result = updateManager.checkForUpdates()
            result.onSuccess { info ->
                if (info.isNewer) {
                    showUpdateDialog(info)
                } else if (isManualCheck) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.update_up_to_date, BuildConfig.VERSION_NAME),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.onFailure { e ->
                if (isManualCheck) {
                    Toast.makeText(this@MainActivity, "Gagal memeriksa rilis: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showUpdateDialog(info: AppUpdateManager.UpdateInfo) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_update)
        dialog.setCancelable(false)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvUpdateTitle)
        val tvNotes = dialog.findViewById<TextView>(R.id.tvReleaseNotes)
        val progressBar = dialog.findViewById<ProgressBar>(R.id.progressBarUpdate)
        val tvProgressPercent = dialog.findViewById<TextView>(R.id.tvProgressPercent)
        val btnUpdateNow = dialog.findViewById<Button>(R.id.btnUpdateNow)
        val btnLater = dialog.findViewById<Button>(R.id.btnLater)

        tvTitle.text = getString(R.string.update_available_title, info.latestVersionName)
        tvNotes.text = info.releaseNotes

        btnLater.setOnClickListener {
            dialog.dismiss()
        }

        btnUpdateNow.setOnClickListener {
            btnUpdateNow.isEnabled = false
            btnLater.isEnabled = false
            progressBar.visibility = View.VISIBLE
            tvProgressPercent.visibility = View.VISIBLE

            lifecycleScope.launch {
                val downloadResult = updateManager.downloadApk(info.apkDownloadUrl) { progress ->
                    progressBar.progress = progress
                    tvProgressPercent.text = getString(R.string.downloading_update, progress)
                }

                downloadResult.onSuccess { apkFile ->
                    dialog.dismiss()
                    updateManager.installApk(this@MainActivity, apkFile)
                }.onFailure { err ->
                    btnUpdateNow.isEnabled = true
                    btnLater.isEnabled = true
                    progressBar.visibility = View.GONE
                    tvProgressPercent.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Gagal mengunduh APK: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }
}
