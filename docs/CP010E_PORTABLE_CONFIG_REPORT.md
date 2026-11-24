# ZClone — CP010E Portable Development Configuration & GitHub Readiness Report

- **Checkpoint ID**: `CP010E`
- **Timestamp (UTC)**: `2026-11-21T00:36:00Z`
- **Project**: ZClone (`com.fms.zclone.enginecoremode`, `v1.0` / `versionCode = 1`)
- **Application Name**: `ZClone`
- **Status**: `VERIFIED`

## 1. Audit Awal (Sebelum Patch)
1. **Konfigurasi Gradle & Secrets Loader**:
   - `app/build.gradle.kts` sudah menerapkan plugin resmi `com.google.android.libraries.mapsplatform.secrets-gradle-plugin` (`libs.plugins.secrets` v`2.0.1`) dengan konfigurasi:
     - `propertiesFileName = ".env"`
     - `defaultPropertiesFileName = ".env.example"`
     - `ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")`
   - `gradle.properties` sudah mengaktifkan `googleServices.missing.passthrough=true` dan `org.gradle.configuration-cache=true`; `app/build.gradle.kts` menetapkan `googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }`.
   - `signingConfigs.release` membaca `KEYSTORE_PATH`, `STORE_PASSWORD`, dan `KEY_PASSWORD` dari environment variables (`System.getenv(...)`), dan dibiarkan utuh tanpa modifikasi.
2. **File Environment & Git Hygiene**:
   - Root `.env` belum tersedia.
   - Root `.env.example` berisi placeholder komentar template default `GEMINI_API_KEY` yang tidak digunakan oleh ZClone.
   - Root `.gitignore` belum memblokir `*.jks`, `*.keystore`, `*.p12`, `*service-account*.json`, `*firebase-adminsdk*.json`, `.env.*`, `*.apk`, `*.aab`, `/app/build/`, `/.build-outputs/`, dan log emulator lokal.
   - `README.md` di root proyek belum tersedia.
   - `gradle/wrapper/` berisi `gradle-wrapper.properties` (Gradle `9.3.1-bin.zip`) tanpa binary `gradlew`/`gradle-wrapper.jar` (karena lingkungan AI Studio memakai CLI `gradle` global).
3. **Firebase**:
   - `app/google-services.json` (proyek `gen-lang-client-0584823370`, klien `com.fms.zclone.enginecoremode`) dan `app/src/main/res/values/firebase_applet_config.xml` (`firestore_database_id`) sudah valid dan dipertahankan apa adanya.

## 2. File yang Ditambah / Diubah & Alasan Teknis
1. **`/.env` (Baru — Nonrahasia saja)**:
   - Menyediakan default pengembangan nonrahasia: `ZCLONE_APP_ENV=development`, `ZCLONE_FIRESTORE_EMULATOR_HOST=127.0.0.1:8085`, `ZCLONE_DEFAULT_LANGUAGE=id`.
   - Dilindungi oleh `/.gitignore` agar aman apabila pengembang menambahkan konfigurasi lokal pribadi.
2. **`/.env.example` (Diperbarui)**:
   - Menghapus placeholder `GEMINI_API_KEY` yang tidak terpakai oleh arsitektur ZClone.
   - Mendokumentasikan template variabel nonrahasia dan variabel signing CI (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`) dalam kondisi terkomentar (`#`) agar tidak memicu pembatasan sharing/secret palsu dan tidak menyimpan rahasia di Git.
3. **`/.gitignore` (Diperbarui)**:
   - Mempertahankan seluruh aturan lama dan menambahkan perlindungan terhadap `.env.*` (dengan pengecualian `!.env.example`), semua file keystore/sertifikat privat (`*.jks`, `*.keystore`, `*.p12`, `*.pfx`, `*.pem`, `*.key`), file service account (`*service-account*.json`, `*firebase-adminsdk*.json`), artefak APK/AAB/build (`/app/build/`, `/.build-outputs/`, `*.apk`, `*.aab`), serta log lokal Firebase/emulator dan `node_modules/`.
4. **`/README.md` (Baru)**:
   - Mendokumentasikan identitas proyek (`com.fms.zclone.enginecoremode`, `ZClone`), namespace library FMS (`com.fms.*`) yang dipertahankan, cara menyalin `.env.example` ke `.env`, perilaku Secrets Gradle Plugin, pengaturan Firebase & `debug.keystore.base64`, cara inisialisasi `gradle wrapper --gradle-version 9.3.1` setelah clone ke GitHub/lokal, pengujian unit/Robolectric/Firestore emulator, serta penggunaan **GitHub Actions Secrets** untuk release signing.
5. **`app/build.gradle.kts` (Tidak Diubah)**:
   - Audit membuktikan Secrets Gradle Plugin v`2.0.1` sudah kompatibel penuh dengan `.env` dan `org.gradle.configuration-cache=true`, sehingga tidak diperlukan parser `.env` tambahan yang berisiko mengganggu konfigurasi signing atau build.

## 3. Identitas Proyek & Namespace yang Dipertahankan
- **Application ID & Namespace**: `com.fms.zclone.enginecoremode` (tidak diubah)
- **Application Name**: `ZClone` (tidak diubah)
- **Preserved FMS Library Namespaces**: `com.fms.core`, `com.fms.security`, `com.fms.data`, `com.fms.ui`, `com.fms.task`, `com.fms.cloud`, `com.fms.device`, `com.fms.clone` (seluruhnya utuh).

## 4. Hasil Validasi Aktual
1. **Gradle `.env` Loader Verification (`gradle :app:generateDebugBuildConfig --console=plain`)**:
   - **BUILD SUCCESSFUL** in `1s` (Configuration cache stored).
   - `app/build/generated/source/buildConfig/debug/com/fms/zclone/enginecoremode/BuildConfig.java` terisi otomatis dari `/.env`:
     - `APPLICATION_ID = "com.fms.zclone.enginecoremode"`
     - `ZCLONE_APP_ENV = "development"`
     - `ZCLONE_DEFAULT_LANGUAGE = "id"`
     - `ZCLONE_FIRESTORE_EMULATOR_HOST = "127.0.0.1:8085"`
2. **Pemindaian Kebocoran Rahasia (`grep`)**:
   - Pemindaian pada `.env`, `.env.example`, `README.md`, `app/src/`, dan `docs/` untuk pola private key, `client_secret`, `service_account`, `firebase-adminsdk`, `STORE_PASSWORD=.+`, `KEY_PASSWORD=.+`: **0 temuan** (`SECRET_SCAN_RC=1`).
3. **Unit & Robolectric Tests (`gradle :app:testDebugUnitTest --console=plain`)**:
   - **BUILD SUCCESSFUL** (**11/11 PASS**, `0` skipped, `0` failures, `0` errors):
     - `com.fms.zclone.enginecoremode.ExampleRobolectricTest`: 4/4 PASS
     - `com.fms.zclone.enginecoremode.ExampleUnitTest`: 1/1 PASS
     - `com.fms.zclone.enginecoremode.FmsCloneEngineTest`: 2/2 PASS
     - `com.fms.zclone.enginecoremode.FmsCoreAndDataTest`: 4/4 PASS
4. **Debug APK Build & Metadata (`compile_applet` + `gradle :app:assembleDebug` + `aapt2` + `apksigner` + `unzip -tq`)**:
   - **BUILD SUCCESSFUL**.
   - `aapt2 dump badging`: `package: name='com.fms.zclone.enginecoremode' versionCode='1' versionName='1.0'`, `application-label:'ZClone'`.
   - `apksigner verify`: **Verifies** (APK Signature Scheme v2 `true`).
   - `unzip -tq`: `No errors detected in compressed data`.
   - Output APK: `app/build/outputs/apk/debug/app-debug.apk` & `.build-outputs/app-debug.apk` (`25,795,138` bytes, SHA-256 `75a173c61a5832e1256a4c7b6cfc487a66d3d3396fd23f8557c1fb6c52cf203e`).

## 5. Keterbatasan yang Masih Ada
- Workspace container tidak memiliki repository `.git` terinisialisasi (sehingga `git check-ignore` mengembalikan `fatal: not a git repository`; validasi `.gitignore` diverifikasi dari isi aturan berkas `/.gitignore`).
- Binary `gradlew` / `gradle-wrapper.jar` tidak dibundel di container (hanya `gradle/wrapper/gradle-wrapper.properties` untuk Gradle `9.3.1`); pengembang yang mengimpor ke mesin tanpa Gradle global dapat menjalankan `gradle wrapper --gradle-version 9.3.1` atau membuka langsung di Android Studio (petunjuk sudah dicatat di `README.md`).
- Release APK/AAB signing dan Audit 2 belum dijalankan sesuai batasan `CP010E`.
