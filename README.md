# Harimau Braille Keyboard 🐯⠓⠃⠅

Aplikasi Papan Ketik Braille Android mandiri (*InputMethodService*) dengan dukungan multi-sentuh 6 titik / 8 titik, kalibrasi ergonomis jari, sinkronisasi awan (**Cloud Sync via Firebase & Google Sign-In**), serta pembaruan otomatis langsung di dalam aplikasi (**In-App Auto-Update & Overwrite via GitHub Actions & Releases**).

---

## 📱 Spesifikasi Aplikasi

- **Nama Aplikasi**: Harimau Braile Keyboard
- **Package ID (Application ID)**: `com.hbk.ruyani`
- **Minimum Android SDK**: Android 8.0 Oreo (API level 26)
- **Target Android SDK**: Android 14 / 15 (API level 34+)
- **Bahasa Pemrograman**: 100% Kotlin & AndroidX
- **Sistem Kompilasi (CI/CD)**: GitHub Actions (Bisa dibangun tanpa Android Studio lokal!)

---

## 🚀 Panduan Membangun APK via GitHub Actions (Tanpa Android Studio)

Karena Anda belum memasang Android Studio di komputer, Anda bisa mengompilasi APK langsung di server awan GitHub:

### Langkah 1: Buat Repositori Baru di GitHub
1. Buka [GitHub.com](https://github.com) dan buat repositori publik atau privat baru, misalnya dengan nama: `harimau-braille-keyboard`.
2. Buka terminal (PowerShell / Command Prompt) di folder `Harimau Braile Keyboard`.
3. Jalankan perintah berikut untuk mengunggah kode ke GitHub:
   ```bash
   git init
   git add .
   git commit -m "Inisialisasi Harimau Braille Keyboard v1.0.0"
   git branch -M main
   git remote add origin https://github.com/<USERNAME-GITHUB-ANDA>/harimau-braille-keyboard.git
   git push -u origin main
   ```

### Langkah 2: Mengunduh APK Hasil Kompilasi
1. Buka tab **Actions** di repositori GitHub Anda.
2. Anda akan melihat alur kerja **"Build Harimau Braille Keyboard APK"** sedang berjalan secara otomatis.
3. Setelah tanda centang hijau muncul (selesai dalam ~2-3 menit), klik alur kerja tersebut.
4. Di bagian bawah (*Artifacts*), klik file **`HarimauBrailleKeyboard-debug-apk`** untuk mengunduh berkas APK siap pasang ke ponsel Anda!

---

## 🔄 Pembaruan Otomatis (In-App Auto-Update & Overwrite)

Anda **tidak perlu mengirim file APK ulang secara manual** ke pengguna setiap kali ada penambahan fitur baru:

1. Setiap kali Anda memperbarui kode dan membuat rilis baru (misal dengan membuat tag git `v1.0.1` atau menaikkan `versionName` di `app/build.gradle` lalu push):
   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```
2. GitHub Actions akan otomatis membuat **GitHub Release** resmi dan melampirkan file APK terbaru.
3. Pengguna yang membuka aplikasi Harimau Braille di ponsel akan langsung melihat pop-up:
   > **"Pembaruan Tersedia (v1.0.1) — Perbarui Sekarang"**
4. Saat tombol ditekan, aplikasi mengunduh APK baru dan **langsung menimpa versi sebelumnya** tanpa menghapus data atau pengaturan pengguna.

---

## ☁️ Sinkronisasi Pengaturan Awan (Cloud Sync saat Ganti HP)

Setiap pengguna yang masuk dengan **Akun Google**:
1. Seluruh posisi kalibrasi 6 jari, pengaturan getaran haptic, suara pengucapan (TTS), dan mode keyboard tersimpan rapi di Cloud Firebase di bawah `users/{UID}/settings`.
2. **Saat berganti ke ponsel baru**: Cukup pasang aplikasi dan login dengan akun Google yang sama. Semua pengaturan dan posisi titik jari langsung otomatis pulih seperti sediakala!

### (Opsional) Mengaktifkan Firebase Proyek Sendiri:
Aplikasi sudah dilengkapi template konfigurasi bawaan. Jika Anda ingin menghubungkannya ke Firebase Console pribadi Anda:
1. Kunjungi [Firebase Console](https://console.firebase.google.com/) dan buat proyek baru.
2. Tambahkan aplikasi Android dengan package name: `com.hbk.ruyani`.
3. Aktifkan **Authentication** (Google Sign-In) dan **Cloud Firestore**.
4. Unduh berkas `google-services.json` resmi dari Firebase dan timpa berkas `app/google-services.json` di proyek ini.

---

## ⌨️ Panduan Penggunaan Gestur & Pengetikan

- **Mengetik Huruf (Akord Braille)**:
  - Sentuh kombinasi titik (1–6) secara bersamaan sesuai huruf Braille yang diinginkan, lalu angkat jari.
  - Titik 1-3 berada di sisi kiri layar; Titik 4-6 berada di sisi kanan layar.
- **Spasi**: Usap (swipe) ke **kanan** dengan 1 jari.
- **Hapus Karakter (Backspace)**: Usap ke **kiri** dengan 1 jari.
- **Hapus Satu Kata Penuh**: Usap ke **kiri** dengan 2 jari.
- **Baris Baru (Enter)**: Usap ke **kanan** dengan 2 jari.
- **Sembunyikan Papan Ketik**: Usap ke **bawah** dengan 2 jari.
- **Ganti Metode Input**: Usap ke **atas** dengan 1 jari.

---

## 📁 Struktur Berkas Proyek

```
Harimau Braile Keyboard/
├── .github/
│   └── workflows/
│       └── build-and-release.yml   # Otomasi kompilasi APK & rilis GitHub
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml     # Deklarasi InputMethodService & Izin
│   │   ├── java/com/hbk/ruyani/
│   │   │   ├── ime/
│   │   │   │   ├── HarimauBrailleImeService.kt  # Layanan Keyboard Android
│   │   │   │   ├── BrailleTouchView.kt          # Bidang multi-sentuh 6 titik
│   │   │   │   └── BrailleGestureDetector.kt    # Detektor gestur usapan
│   │   │   ├── translate/
│   │   │   │   ├── BrailleTranslator.kt         # Mesin alih aksara Braille
│   │   │   │   └── BrailleCharacter.kt          # Model sel bitmask titik
│   │   │   ├── feedback/
│   │   │   │   └── FeedbackManager.kt           # Haptic & TTS suara pembaca
│   │   │   ├── data/
│   │   │   │   ├── BraillePreferences.kt        # Pengaturan lokal sandboxed
│   │   │   │   └── CloudSyncManager.kt          # Sinkronisasi Firebase Firestore
│   │   │   ├── update/
│   │   │   │   └── AppUpdateManager.kt          # OTA in-app auto updater
│   │   │   └── ui/
│   │   │       ├── MainActivity.kt              # Pusat kendali & login Google
│   │   │       └── CalibrationActivity.kt       # Kalibrasi 6 jari landscape
│   │   └── res/                                 # Tata letak, warna, string
│   ├── build.gradle                             # Konfigurasi modul aplikasi
│   └── google-services.json                     # Konfigurasi Firebase
├── build.gradle                                 # Konfigurasi build root
├── settings.gradle
└── README.md
```
