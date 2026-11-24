# Panduan Manual On-Device QA — ZClone / FMS (`CP010L-03`)

Dokumen ini disusun sebagai panduan pengujian langsung pada perangkat Android fisik (disarankan Android 10–15 / API 29–35).  
Catatan: Lingkungan build cloud tidak memiliki emulator Android, Google Play Services interaktif, atau perangkat fisik; pengujian end-to-end di HP wajib dilakukan menggunakan checklist ini.

---

## 0. Persiapan Pra-Uji (Pre-Flight)

### A. Konfigurasi Firebase Console (Jika Menguji Google Sign-In & Firestore Sync)
1. Buka Firebase Console untuk project **`zclone-fms`** (`759195378420`).
2. Periksa kecocokan fingerprint sertifikat penandatangan Debug APK untuk aplikasi **`com.fms.zclone.enginecoremode`**:
   - **Fingerprint aktif `debug.keystore` saat ini (untuk build `app-debug.apk`)**:
     - SHA-1: `AC:19:D8:DF:B9:3F:CC:87:FF:32:F1:0A:91:6C:68:E3:EE:74:9A:C1`
     - SHA-256: `4F:C0:31:0D:24:00:61:3C:97:FF:7C:65:56:2E:39:FF:F0:E8:C6:3F:BA:16:8B:07:D4:50:C9:C4:F8:FE:F9:09`
   - **Fingerprint OAuth client yang saat ini tercatat di `app/google-services.json`**:
     - SHA-1: `41:11:A8:21:F9:E7:2E:05:F8:08:C4:DD:93:E2:9C:1F:EB:54:5E:EC`
   - ⚠️ **Risiko Google Sign-In**: Selama SHA-1 `AC:19:D8:...` belum ditambahkan ke **Firebase Console → Project Settings → Your apps (`com.fms.zclone.enginecoremode`) → Add fingerprint**, Google Sign-In pada `app-debug.apk` dapat gagal dengan `DeveloperError` / kode `10` (mode lokal SQLite & fitur clone tetap berjalan offline).
3. Buka **Authentication → Sign-in method** → pastikan provider **Google** berstatus **Enabled** dan Web SDK configuration client ID aktif (cocok dengan `default_web_client_id`).
4. Buka **Firestore Database** → pastikan database **`(default)`** sudah dibuat dan file **`firestore.rules`** terbaru sudah dideploy (`firebase deploy --only firestore:rules`).

### B. Persiapan Perangkat Android
1. Pasang `app-debug.apk` terbaru (`adb install -r app-debug.apk` atau melalui file manager).
2. Siapkan minimal **2 file APK uji** di penyimpanan HP (mis. folder `Download`):
   - **APK Uji 1 (Single standalone APK)**: contoh APK open-source ringan tanpa Split APK (mis. calculator / F-Droid single APK).
   - **APK Uji 2 (Split/App Bundle base APK atau APK berproteksi `sharedUserId`)** untuk memverifikasi blokir jujur (`UNSUPPORTED`).
3. Uji juga satu aplikasi terpasang langsung dari daftar **Clone Apps** yang memang non-split (single `base.apk`).

---

## 1. Skenario Pengujian 8 Alur Utama

### Skenario 1 — Import APK melalui SAF
- **Langkah**:
  1. Buka menu **Clone Apps → Import APK (.apk)** (tombol import SAF).
  2. Uji memilih file non-`.apk` / batal dari picker.
  3. Uji memilih file **APK Uji 1** yang valid.
  4. Coba rotasi layar saat/sesudah memilih file.
- **Hasil yang Diharapkan**:
  - Jika user membatalkan picker: tidak crash dan UI kembali normal.
  - Saat I/O copy SAF berjalan: tombol import/attach menampilkan indikator sibuk dan menonaktifkan klik ganda.
  - File APK disalin ke cache internal tanpa `SecurityException`, lalu langsung membuka **App Configuration** dengan nama, package ID, versi, dan ikon APK terisi.
- **Yang Harus Dicatat**: Ukuran file APK, lama proses copy/inspeksi, ada/tidaknya banner error.

### Skenario 2 — Analisis APK & Validasi Kompatibilitas
- **Langkah**:
  1. Inspeksi **APK Uji 1** (standalone APK dengan `classes.dex`).
  2. Inspeksi aplikasi Split APK / App Bundle atau pilih **APK Uji 2**.
  3. Periksa tab/daftar izin aplikasi di **App Configuration**.
- **Hasil yang Diharapkan**:
  - Standalone APK berstatus **COMPATIBLE** atau **LIMITED** (dengan daftar peringatan jujur seperti signature v2/v3, native `.so`, `CHECK_LICENSE`).
  - Split APK (`split != null` / banyak split APK), `sharedUserId != null`, atau APK tanpa `classes.dex` berstatus **UNSUPPORTED**, tombol Build Clone dinonaktifkan dengan alasan jelas.
  - Pada **Batch Clone**, aplikasi berstatus **UNSUPPORTED** tidak dapat dicentang untuk antrean build.
- **Yang Harus Dicatat**: Status kompatibilitas (`COMPATIBLE`/`LIMITED`/`UNSUPPORTED`), daftar blockers & warnings yang muncul.

### Skenario 3 — Repackaging, Signing (v1 + v2), Export & Instalasi APK Hasil Kloning
- **Langkah**:
  1. Pada **App Configuration** untuk APK Uji 1, aktifkan ubah Package ID (mis. `...zclone1`), ubah Label, pilih tint ikon / preset, dan opsional strip 1 permission non-kritis.
  2. Tekan **Build Clone**. Putar layar (portrait ↔ landscape) saat progress atau setelah card sukses muncul.
  3. Uji **Export APK** via SAF ke folder `Download`.
  4. Uji **Install APK**. Jika izin *Install unknown apps* belum aktif, ikuti dialog menuju Settings lalu kembali dan tekan Install lagi.
- **Hasil yang Diharapkan**:
  - Build berjalan real hingga selesai (`signatureVerified == true`), tidak tertimpa file korup jika gagal, dan card sukses tetap bertahan setelah rotasi layar.
  - File hasil export memiliki ukuran > 0 byte di folder tujuan SAF.
  - Pemasang paket Android (*PackageInstaller*) menerima sertifikat APK (v1 + v2 APK Sig Block 42) tanpa error `INSTALL_PARSE_FAILED_NO_CERTIFICATES` atau `INSTALL_FAILED_INVALID_APK`.
  - Setelah terpasang, aplikasi clone muncul di launcher dengan nama & ikon termodifikasi serta `targetPackage` berbeda dari aplikasi asli.
  - *Catatan penting*: Jika APK target memakai proteksi integritas/signature internal pada kode DEX atau resource terkompresi khusus (`resources.arsc` / `.so` check), aplikasi clone bisa terpasang tetapi force-close saat dibuka; ini merupakan batas repackaging statis tanpa root/DEX hooking (lihat bagian Decision Matrix).
- **Yang Harus Dicatat**: Package sumber & target, SHA-256 atau ukuran APK output, hasil PackageInstaller (`Installed` / kode error spesifik dari Android).

### Skenario 4 — Batch Clone & Progress Queue
- **Langkah**:
  1. Di **Clone Apps**, ketuk beberapa kartu aplikasi kompatibel untuk masuk mode seleksi batch, atau gunakan action Batch Clone (2–3 aplikasi kompatibel).
  2. Jalankan antrean batch dan amati progress per item.
  3. Tekan **Cancel** di tengah antrean (opsional pada uji ulang) untuk menguji pembatalan.
- **Hasil yang Diharapkan**:
  - `FMSJobQueue` menampilkan progress nyata per aplikasi, memvalidasi setiap `targetPackage`, dan memproses berurutan tanpa ANR.
  - Jika sebagian gagal (mis. manifest tak didukung), item gagal dilaporkan jujur tanpa memalsukan sukses.
- **Yang Harus Dicatat**: Jumlah item sukses vs gagal, apakah progress bar berhenti akurat saat selesai/batal.

### Skenario 5 — Backup/Restore & Clone Library (Time Machine + Version Diff)
- **Langkah**:
  1. Buka **My Clones Library**. Pastikan resep clone yang baru dibangun muncul lengkap.
  2. Uji **Time Machine**: pilih 2 versi atau lihat riwayat build; klik **Install** atau **Export** pada salah satu entri yang file APK-nya masih ada di perangkat.
  3. Uji **Version Diff**: pilih dua record untuk melihat selisih label, package, status build, dan izin yang di-strip.
  4. Uji resep tanpa file APK lokal (atau hasil restore): klik **Attach Source APK (.apk)**, pilih APK sumber yang cocok → pastikan prefill nama/izin tetap terjaga; uji juga memilih APK dengan package berbeda → pastikan prefill salah dibersihkan dengan pemberitahuan.
  5. Buka **Settings → Export Backup (.json)**, simpan ke SAF, ubah/hapus 1 resep di Library, lalu **Import Backup (.json)**.
- **Hasil yang Diharapkan**:
  - Backup JSON mengekspor seluruh resep + konfigurasi tema/font/ikon dan memulihkannya kembali.
  - File JSON kosong/korup/`> 2 MB`/tanpa `id`/`cloneName` ditolak dengan pesan error jelas.
- **Yang Harus Dicatat**: Ukuran file backup, apakah semua field resep & tema pulih utuh.

### Skenario 6 — Theme Studio & Penyimpanan Preferensi
- **Langkah**:
  1. Buka **Theme Studio**, pilih preset tema, ubah warna Custom Primary/Secondary, Font Family (`Space Grotesk` / `Inter` / dsb.), Icon Style (`SQUIRCLE`/`CIRCLE`/dll.), atau aktifkan Auto Theme.
  2. Putar layar HP dan pindah antar layar (**Home**, **Library**, **Settings**).
  3. Force-close aplikasi lalu buka kembali.
- **Hasil yang Diharapkan**:
  - Perubahan warna, font, dan bentuk ikon langsung diterapkan, bertahan saat rotasi layar, dan tetap tersimpan sesudah restart aplikasi (via `SharedPreferences` + SQLite, plus Firestore bila login).
- **Yang Harus Dicatat**: Preset/warna yang dipilih dan apakah tampil konsisten sesudah cold restart.

### Skenario 7 — Device Manager & Batas Kuota (Tier FREE = 3 Perangkat)
- **Langkah**:
  1. Buka **Device Manager**. Ubah label perangkat lokal dan simpan.
  2. Perhatikan badge status perangkat lokal dan angka ringkasan **Active Devices / Device Quota**.
  3. (Saat login akun Google sama pada >3 instalasi/perangkat) verifikasi pendaftaran perangkat ke-4 otomatis berstatus `INACTIVE`, dan mencoba mengaktifkan melebihi batas kuota diblokir dengan pesan kuota.
- **Hasil yang Diharapkan**:
  - Sebelum login: status tampil jujur **Local** (bukan `Synced`), dan perubahan label tersimpan ke disk.
  - Setelah login: daftar `cloudDevices` mencerminkan status nyata Firestore (`ACTIVE`/`INACTIVE`/`REVOKED`/`Syncing`/`SYNC ERR`), tanpa memaksa angka aktif minimal 1 jika status sebenarnya tidak aktif.
- **Yang Harus Dicatat**: Jumlah `cloudDevices`, status perangkat lokal, pesan error bila kuota penuh.

### Skenario 8 — Google Sign-In & Firestore Sync
- **Langkah**:
  1. Buka **Settings → Sign In with Google**. Pilih akun Google di sheet Credential Manager.
  2. Perhatikan badge sync di **Home Dashboard** (`SYNCING` → `SYNCED`).
  3. Buat/ubah 1 clone config atau jalankan 1 Security Scan, lalu matikan internet/Wi-Fi dan lakukan perubahan lain untuk melihat perilaku offline & status sync.
  4. Buka kembali **Settings → Sign Out**, pastikan data lokal SQLite tetap utuh dan badge kembali ke **LOCAL**.
- **Hasil yang Diharapkan**:
  - Login berhasil menggunakan akun Google tanpa crash.
  - Data clone configs, security reports, theme presets, dan devices tersinkron ke Firestore `users/{uid}/...` sesuai `firestore.rules`.
  - Jika terjadi error Firestore/jaringan/rules, badge berubah jujur ke **SYNC ERR** (atau **OFFLINE** saat tanpa koneksi) dan pesan error muncul di UI.
- **Yang Harus Dicatat**: `uid`/email terautentikasi, status badge di Home & Device Manager, pesan error exact bila Sign-In atau Firestore gagal.

---

## 2. Cara Mengambil Log Saat Uji & Kegagalan

### A. Dari Dalam Aplikasi (Tanpa Kabel USB / PC)
- Buka **FMS Hub → tab Logs / Telemetry (`FMSLogging`)** untuk melihat ringkasan event `Auth`, `Sync`, `JobQueue`, dan error repackaging yang sudah disanitasi token/kredensialnya.

### B. Via ADB dari PC (Disarankan Untuk Error Sign-In / Installer / Crash)
```bash
# Bersihkan buffer sebelum menguji 1 skenario
adb logcat -c

# Ambil log terfokus ZClone + Firebase + Credential Manager + PackageInstaller
adb logcat -v time Auth:V FirestoreRepo:V ZCloneVM:V FMSAuth:V FMSJobQueue:V FMSRepackager:V AndroidRuntime:E PackageInstaller:V *:W > zclone_qa_log.txt
```
Khusus saat gagal install APK clone:
```bash
adb logcat -d | grep -iE "PackageInstaller|INSTALL_PARSE|INSTALL_FAILED|FMSRepackager|FMSSignature"
```

---

## 3. Matriks Klasifikasi Masalah (Bug Aplikasi vs Batas OS Android vs Konfigurasi Firebase)

| Gejala di Perangkat | Kemungkinan Penyebab | Kategori | Tindakan Lanjut |
| :--- | :--- | :--- | :--- |
| APK hasil clone gagal install dengan `INSTALL_PARSE_FAILED_NO_CERTIFICATES` / struktur ZIP invalid | Bug generator tanda tangan v1/v2 atau ZIP alignment di `FMSSignatureManager` / `FMSRepackager` | **Bug Aplikasi** | Lampirkan `apksigner verify --verbose` atas APK hasil clone + logcat |
| Tombol/state UI reset saat rotasi layar atau status tampil `SYNCED` padahal offline/error | Manajemen state Compose / ViewModel | **Bug Aplikasi** | Catat layar & langkah reproduksi |
| Aplikasi target berstatus `UNSUPPORTED` karena Split APK / `sharedUserId` / tanpa `classes.dex` | Desain aplikasi modern pakai App Bundle multi-APK atau berbagi UID sistem | **Batas OS Android (Non-Root)** | Gunakan APK tunggal (`standalone`) atau merger eksternal sebelum import |
| Clone terpasang normal tetapi langsung crash saat dibuka karena `SignatureCheck` / `PlayIntegrity` / Google API key terikat SHA-1 pengembang asli | Proteksi kriptografi sisi server/native di aplikasi target; non-root tidak melakukan runtime hooking | **Batas OS Android (Non-Root)** | Tercatat pada keterbatasan `CP010L-02`; bukan bug ZClone |
| Tidak bisa mengambil data login/folder `/data/data/<pkg>` aplikasi asli ke clone | Sandbox keamanan Linux Android memblokir akses data antar UID tanpa root | **Batas OS Android (Non-Root)** | Normal pada perangkat non-root |
| Google Sign-In gagal dengan `NoCredentialException` / `DeveloperError` / kode error 10/16 | SHA-1 `debug.keystore` aktif (`58:14:...`) berbeda dari `google-services.json` (`41:11:...`) atau belum terdaftar di Firebase `zclone-fms`, provider Google belum aktif, atau HP tanpa akun Google / Play Services usang | **Konfigurasi Firebase / Perangkat** | Tambahkan SHA-1 `58:14:31:C4:81:74:41:3C:10:F5:54:E6:BD:36:25:AB:44:67:52:3E` di Firebase Console & aktifkan provider Google |
| Firestore gagal dengan `PERMISSION_DENIED` | `firestore.rules` belum dideploy ke project `zclone-fms` atau akun mencoba menulis di luar `users/{uid}` / melebihi batas validasi rules | **Konfigurasi Firebase** | Deploy `firestore.rules` dan periksa logcat `FirestoreRepo` |
