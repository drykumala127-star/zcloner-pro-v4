# ZClone (`com.fms.zclone.enginecoremode`)

Aplikasi Android manajemen kloning APK, inspeksi manifest biner, kustomisasi ikon/tema, pemindaian risiko statis, dan Cloud Sync berbasis **FMS Full Custom Ecosystem** & **FMSCloneEngine**.

## Identitas & Arsitektur Proyek
- **Application ID & Namespace**: `com.fms.zclone.enginecoremode`
- **Application Name**: `ZClone`
- **SDK Target**: `minSdk = 24`, `compileSdk = 36`, `targetSdk = 36`, Java 11, Kotlin + Jetpack Compose (Material 3)
- **Namespace Library FMS (Dipertahankan)**:
  - `com.fms.core` — Runtime topologis, event bus, plugin in-process, logger dengan redaksi token
  - `com.fms.security` — `AndroidKeyStore` AES-256-GCM, PKCS#12 lokal, validasi Zip-Slip & sintaks paket
  - `com.fms.data` — Database SQLite lokal-first, preference engine, backup/restore JSON SAF
  - `com.fms.ui` — Theme studio, tipografi TTF lokal, layout adaptif (`Compact` / `Medium` / `Expanded`)
  - `com.fms.task` — Antrean job coroutine terukur (`Semaphore`)
  - `com.fms.cloud` — Observasi konektivitas & Google Sign-In eksklusif via Credential Manager
  - `com.fms.device` — Manajemen perangkat & Install UUID non-hardware
  - `com.fms.clone` — `FMSAPKInspector`, `FMSManifestEngine`, `FMSIconEngine`, `FMSRepackager`, `FMSSignatureManager`, `FMSSecurityScanner`, `FmsParityCatalog`
- **Paket Aplikasi**: `com.fms.zclone.enginecoremode.*` (`MainActivity`, UI screens, `ZCloneViewModel`, `FirestoreRepositories`)

## Pengaturan Konfigurasi Setelah Import dari GitHub

### 1. Environment Lokal (`.env`)
Proyek ini sudah terkonfigurasi dengan **Secrets Gradle Plugin** (`app/build.gradle.kts`) yang membaca `.env` secara otomatis (dengan fallback ke `.env.example` bila `.env` belum dibuat).

```bash
cp .env.example .env
```

- Gunakan `.env` **hanya** untuk konfigurasi development nonrahasia (seperti `ZCLONE_APP_ENV`, `ZCLONE_FIRESTORE_EMULATOR_HOST`, `ZCLONE_DEFAULT_LANGUAGE`).
- **Jangan pernah** menulis password keystore, private key, service-account JSON, atau token sensitif ke file yang dilacak Git. File `.env`, `*.jks`, `*.keystore`, `*service-account*.json`, dan `local.properties` sudah dilindungi oleh `.gitignore`.

### 2. Konfigurasi Firebase
- Konfigurasi klien Android tersimpan pada `app/google-services.json` (untuk `com.fms.zclone.enginecoremode`) dan `app/src/main/res/values/firebase_applet_config.xml`.
- Jika Anda mem-fork ke proyek Firebase baru:
  1. Daftarkan aplikasi Android dengan package `com.fms.zclone.enginecoremode` dan SHA-1 keystore debug/release Anda di Firebase Console.
  2. Ganti `app/google-services.json` dengan file proyek Anda dan perbarui `firestore_database_id` bila menggunakan database non-default.
  3. Deploy `firestore.rules` dan verifikasi dengan `firestore.test.js`.
- Untuk build CI awal tanpa `google-services.json`, proyek mengaktifkan `googleServices.missing.passthrough=true` di `gradle.properties` dan `MissingGoogleServicesStrategy.WARN` di `app/build.gradle.kts`.

### 3. Debug Keystore
- `app/build.gradle.kts` memakai `debug.keystore` pada root proyek untuk varian `debug`.
- Jika `debug.keystore` belum tersedia setelah clone dan terdapat `debug.keystore.base64`, pulihkan dengan:
  ```bash
  base64 -d debug.keystore.base64 > debug.keystore
  ```
- Atau jalankan task Gradle debug/Android Studio agar keystore debug lokal Anda dibuat otomatis (jangan lupa daftarkan SHA-1 barunya di Firebase Console jika memakai Google Sign-In).

## Perintah Build & Pengujian

Di lingkungan dengan Gradle CLI terpasang (seperti AI Studio), gunakan `gradle` secara langsung:

```bash
# Jalankan unit test & Robolectric test (11 pengujian)
gradle :app:testDebugUnitTest --console=plain

# Build Debug APK
gradle :app:assembleDebug --console=plain
```

Jika Anda mengimpor proyek ke mesin lokal/GitHub Actions baru tanpa Gradle sistem terpasang, inisialisasi wrapper Gradle terlebih dahulu (versi target pada `gradle/wrapper/gradle-wrapper.properties` adalah `9.3.1`):

```bash
gradle wrapper --gradle-version 9.3.1
./gradlew :app:testDebugUnitTest --console=plain
./gradlew :app:assembleDebug --console=plain
```

### Pengujian Aturan Keamanan Firestore (Emulator)
```bash
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

### Release Signing (CI / GitHub Actions Secrets)
Jangan simpan kredensial signing produksi di repositori. Konfigurasikan variabel environment berikut melalui shell atau **GitHub Actions Secrets** saat menjalankan `:app:assembleRelease` / `:app:bundleRelease`:
- `KEYSTORE_PATH` — path ke file `.jks` / `.keystore`
- `STORE_PASSWORD` — password keystore
- `KEY_PASSWORD` — password key alias `upload`

## Dokumentasi Lanjutan
- `docs/PROJECT_STATE.md` — Status proyek & ringkasan verifikasi terbaru
- `docs/CHECKPOINT_INDEX.md` — Riwayat checkpoint (`CP000`–`CP010E`)
- `docs/ANDROID_COMPATIBILITY.md` — Matriks kompatibilitas Android non-root
- `docs/SECURITY_MASTER_REPORT.md` — Kontrol keamanan kriptografi, validasi input, dan Firebase rules
