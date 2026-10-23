package com.hbk.ruyani.ui

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
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
 * Mengatur aktivasi keyboard sistem Android, Google Sign-In (Cloud Sync),
 * kalibrasi titik, dan in-app auto-update langsung dari GitHub Releases.
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
    private lateinit var btnCalibrate: Button
    private lateinit var tvCurrentVersion: TextView
    private lateinit var btnCheckUpdate: Button

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            handleGoogleSignInSuccess(account)
        } catch (e: ApiException) {
            Toast.makeText(this, "Login Google gagal: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cloudSyncManager = CloudSyncManager(this)
        updateManager = AppUpdateManager(this)

        initViews()
        setupGoogleSignIn()
        setupActions()
    }

    override fun onResume() {
        super.onResume()
        updateKeyboardStatus()
        updateAuthStatus()
    }

    private fun initViews() {
        tvKeyboardStatus = findViewById(R.id.tvKeyboardStatus)
        btnEnableIme = findViewById(R.id.btnEnableIme)
        btnSelectIme = findViewById(R.id.btnSelectIme)
        tvSyncStatus = findViewById(R.id.tvSyncStatus)
        btnLoginGoogle = findViewById(R.id.btnLoginGoogle)
        btnLogout = findViewById(R.id.btnLogout)
        btnCalibrate = findViewById(R.id.btnCalibrate)
        tvCurrentVersion = findViewById(R.id.tvCurrentVersion)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)

        tvCurrentVersion.text = "Versi Terpasang: v${BuildConfig.VERSION_NAME}"
    }

    private fun setupGoogleSignIn() {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(this, gso)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupActions() {
        // 1. Tombol Aktifkan Keyboard di Pengaturan
        btnEnableIme.setOnClickListener {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }

        // 2. Tombol Pilih Keyboard Utama
        btnSelectIme.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        // 3. Tombol Login Google
        btnLoginGoogle.setOnClickListener {
            googleSignInClient?.signInIntent?.let { intent ->
                googleSignInLauncher.launch(intent)
            }
        }

        // 4. Tombol Logout
        btnLogout.setOnClickListener {
            cloudSyncManager.signOut()
            googleSignInClient?.signOut()
            updateAuthStatus()
            Toast.makeText(this, "Berhasil keluar akun", Toast.LENGTH_SHORT).show()
        }

        // 5. Tombol Kalibrasi
        btnCalibrate.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }

        // 6. Tombol Periksa Pembaruan
        btnCheckUpdate.setOnClickListener {
            checkAppUpdate(isManualCheck = true)
        }
    }

    private fun handleGoogleSignInSuccess(account: GoogleSignInAccount) {
        val idToken = account.idToken
        lifecycleScope.launch {
            if (idToken != null) {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = cloudSyncManager.signInWithGoogleCredential(credential)
                if (result.isSuccess) {
                    Toast.makeText(this@MainActivity, getString(R.string.sync_success), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "Tersambung lokal: ${account.email}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@MainActivity, "Login lokal: ${account.email}", Toast.LENGTH_SHORT).show()
            }
            updateAuthStatus()
        }
    }

    private fun updateKeyboardStatus() {
        val imeId = "${packageName}/.ime.HarimauBrailleImeService"
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
    }

    private fun updateAuthStatus() {
        val user = cloudSyncManager.currentUser
        val googleAccount = GoogleSignIn.getLastSignedInAccount(this)

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
