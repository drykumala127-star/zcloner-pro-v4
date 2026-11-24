# DRAFT — INTELLECTUAL PROPERTY & ORIGINALITY AUDIT REPORT (CP010F)

> **STATUS LAPORAN: DRAFT AUDIT (Audit + Draft Documentation Only — Tidak Ada Penggantian Kode/Dependency Dilakukan)**  
> **Pemutakhiran Bukti `CP010G` (`Deliverables/CP010G-SOURCE-OWNERSHIP-REPORT.md`)**: Karena belum ada repository `.git` lokal/riwayat commit pencipta dan seluruh kode disintesis dalam sesi AI ini, label bukti hukum untuk modul kustom di bawah diperketat menjadi **`OWNERSHIP UNVERIFIED` (`PARTIALLY VERIFIED`)** serta identifier SPDX kustom yang direkomendasikan adalah **`LicenseRef-Dhedry-Proprietary`** (bukan header komersial standar atau klaim kepemilikan mutlak sebelum rantai bukti disahkan).  
> **Pemutakhiran `CP010H` (`Deliverables/CP010H-ORIGINAL-ASSET-REPORT.md`)**: Dua aset raster AI-generated (`zclone_launcher_icon_*.jpg`, `img_hero_clone_layers_*.jpg`) dan 10 bitmap `mipmap-*/ic_launcher*.png` lama telah digantikan oleh 5 `VectorDrawable` XML orisinal + fallback PNG deterministik. Status hukum aset baru tetap `OWNERSHIP UNVERIFIED` (menunggu Git baseline), sedangkan `public/assets/aistudio/` tetap **`PENDING`**.  
> **Pemutakhiran `CP010I` (`Deliverables/CP010I-LICENSE-COMMERCIAL-READINESS-REPORT.md`)**: Audit POM Gradle aktual (`166` release / `173` debug runtime, `218` test, `122` build) mengoreksi `googleid` & `firebase-auth` menjadi **Android SDK License**, serta mencatat dependensi transitif runtime **BSD-3-Clause**, **Go License**, **MIT**, dan **Play Core/Integrity ToS** ke `THIRD-PARTY-NOTICES.md`. Status kesiapan komersial saat ini adalah **`NOT COMMERCIAL READY`** menunggu keputusan pemilik proyek dan penguatan bukti Git.  
> **Pemutakhiran `CP010J` (`Deliverables/CP010J-LICENSE-POLICY-RESOLUTION-REPORT.md`)**: Seluruh 11 dokumen lisensi & kebijakan (`LICENSE`, `COPYRIGHT.md`, `COMMUNITY-LICENSE.md`, `COMMERCIAL-LICENSE.md`, `EULA.md`, `LICENSE-FAQ.md`, `TRADEMARK-NOTICE.md`, `CONTRIBUTING.md`, `THIRD-PARTY-NOTICES.md`, `IP-OWNERSHIP-REGISTER.md`, dan laporan ini) telah diselaraskan terhadap 12 kebijakan resmi Dhedry + 3 klarifikasi pengguna (status `DRAFT` tersinkron, redistribusi fork nonkomersial dengan nama/ikon sendiri + notice utuh, Hukum Republik Indonesia, wajib CLA tertulis sebelum *merge*, serta pengecualian konsep teknis netral). Status proyek **tetap `OWNERSHIP UNVERIFIED` dan `NOT LEGALLY CLEARED`**.

- **Pemilik Target Ekosistem**: `© Dhedry Afriazoni`
- **Tanggal Audit**: `2026-11-21` (`CP010F`)
- **Identitas APK Saat Ini**: `com.fms.zclone.enginecoremode` (`ZClone`, `v1.0`)
- **Namespace Library FMS Dipertahankan**: `com.fms.core`, `com.fms.security`, `com.fms.data`, `com.fms.ui`, `com.fms.task`, `com.fms.cloud`, `com.fms.device`, `com.fms.clone`

---

## 1. Ringkasan Eksekutif & Pernyataan Kejujuran Audit

Audit terhadap workspace **ZClone**, **FMS Full Custom Ecosystem**, dan **FMSCloneEngine** menunjukkan bahwa:
1. **Inti Engine & Arsitektur Modular (`com.fms.*` + logika aplikasi ZClone)** ditulis dalam Kotlin murni tanpa menyalin kode engine eksternal (bebas Apktool, BouncyCastle, smali, atau DEX hooker pihak ketiga).
2. **Proyek ini TIDAK 100% bebas pihak ketiga**: APK mengompilasi library pihak ketiga — mencakup **Apache 2.0** (AndroidX, Jetpack Compose, Kotlin Stdlib/Coroutines, `firebase-firestore`, gRPC, Guava, Okio, Gson), **Android SDK License** (`googleid`, `firebase-auth`, `play-services-*`, `recaptcha`), **Play Core / Play Integrity ToS**, **BSD-3-Clause** (`protobuf-javalite`, `datastore-preferences-external-protobuf`), **Go License** (`re2j`), dan **MIT** (`animal-sniffer-annotations`) — serta membundel 3 font Google Fonts (`SIL OFL 1.1`) dan bergantung pada Android SDK, Gradle, dan layanan cloud Google/Firebase. Hak dan kewajiban lisensi pihak ketiga tetap berlaku penuh dan tidak diklaim sebagai milik Dhedry.
3. **Celah Pembuktian Kepemilikan Saat Ini**:
   - Workspace container saat ini **belum memiliki repository `.git` lokal** dan **belum memiliki header copyright / SPDX** di berkas `.kt`.
   - Kode disusun dengan bantuan AI berdasarkan spesifikasi arsitektur pengguna (`© Dhedry Afriazoni`), sehingga seluruh modul kustom berstatus **`OWNERSHIP UNVERIFIED`** sampai penguatan rantai bukti (Git history, commit tertanda, SHA-256 baseline, tinjauan manusia, dan header hak cipta) selesai.
   - Dua aset raster AI lama (`zclone_launcher_icon_*.jpg` dan `img_hero_clone_layers_*.jpg`) telah dihapus dan digantikan `VectorDrawable` XML pada `CP010H` (aset baru tetap `OWNERSHIP UNVERIFIED`), sementara **2 berkas referensi input pengguna (`public/assets/aistudio/...`) berstatus `BELUM DIKETAHUI` / `PENDING` dan dilarang masuk distribusi publik** sebelum asal-usul dan hak penggunaannya dipastikan.

---

## 2. Pengelompokan Komponen (5 Kategori Wajib)

### Kelompok 1 — Terbukti / Dinilai Orisinal (Subjek Penguatan Bukti Git & SPDX)

| Komponen | Status Kepemilikan | Bukti | Risiko Hukum / Teknis | Perlu Diganti? | Rekomendasi | Prioritas |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `com.fms.core` (`FmsCore.kt`, `FmsEventEngine.kt`) | `ORISINAL` (perlu penguatan bukti) | Implementasi Kotlin murni; diuji `FmsCoreAndDataTest` | Belum ada header copyright & riwayat Git lokal | Tidak | Tambah header `© Dhedry Afriazoni`, inisialisasi Git & baseline SHA-256 | Tinggi |
| `com.fms.security` (`FmsSecurityEngine.kt`, `SelfSignedCertGenerator`) | `ORISINAL` (perlu penguatan bukti) | Encoder DER X.509 & wrapper AndroidKeyStore mandiri tanpa BouncyCastle | Belum ada header copyright & riwayat Git lokal | Tidak | Tambah header hak cipta; dokumentasikan standar AES-GCM/SHA256withRSA | Tinggi |
| `com.fms.data` (`FmsDatabaseEngine.kt`, `FmsStorageAndPrefs.kt`) | `ORISINAL` (perlu penguatan bukti) | CRUD SQLite & JSON backup SAF mandiri; diuji Robolectric | Belum ada header copyright & riwayat Git lokal | Tidak | Tambah header hak cipta & skema SQLite terversi | Tinggi |
| `com.fms.ui` (`FmsThemeEngine.kt`, `FmsDesignSystem.kt`) | `ORISINAL` (perlu penguatan bukti) | Komponen Compose Canvas & layout engine mandiri di atas Compose M3 | Belum ada header copyright; bergantung Compose M3 | Tidak | Tambah header hak cipta | Tinggi |
| `com.fms.task` (`FmsTaskEngine.kt`) & `com.fms.device` (`FmsDeviceManager.kt`) | `ORISINAL` (perlu penguatan bukti) | Job queue `Semaphore` & Install UUID non-hardware mandiri | Belum ada header copyright & riwayat Git lokal | Tidak | Tambah header hak cipta | Tinggi |
| `com.fms.cloud` (`FmsCloudEngine.kt`) | `ORISINAL` (kode integrasi) | Pembungkus Credential Manager & Firebase Auth mandiri | Bergantung pada SDK & layanan Google eksternal | Tidak | Pertahankan kepatuhan ToS Google & `THIRD-PARTY-NOTICES.md` | Sedang |
| `com.fms.clone` (`FMSAPKInspector`, `FMSManifestEngine`, `FMSIconEngine`, `FMSRepackager`, `FMSSignatureManager`, `FMSSecurityScanner`, `FmsParityCatalog`) | `ORISINAL` (perlu penguatan bukti) | Parser/mutator AXML `ByteBuffer`, PNG `ColorMatrix`, JAR/PKCS#7 DER signer mandiri; diuji `FmsCloneEngineTest` | Penyebutan *App Cloner* di `FmsParityCatalog`; belum ada header copyright & Git | Tidak (tinjau teks katalog merek untuk materi iklan) | Tambah header hak cipta; audit teks komparatif merek pihak ketiga | Tinggi |
| Modul Aplikasi ZClone (`MainActivity.kt`, `ZCloneViewModel.kt`, `FirestoreRepositories.kt`, Screens) | `ORISINAL` (perlu penguatan bukti) | Disusun khusus untuk spesifikasi UI & alur kerja ZClone | Belum ada header copyright & riwayat Git lokal | Tidak | Tambah header hak cipta | Tinggi |
| `firestore.rules`, `firestore.test.js`, `firebase-blueprint.json`, `docs/*.md`, `README.md` | `ORISINAL` (perlu penguatan bukti) | Skema & aturan zero-trust dibuat khusus; diuji emulator 4/4 | Belum ada riwayat Git lokal | Tidak | Simpan dalam repositori Git bertanda & hash baseline | Sedang |

---

### Kelompok 2 — Pihak Ketiga dengan Lisensi yang Dapat Digunakan

| Komponen | Status Kepemilikan | Bukti | Risiko Hukum / Teknis | Perlu Diganti? | Rekomendasi | Prioritas |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| AndroidX (`core-ktx` 1.18.0, `activity-compose` 1.10.1, `lifecycle-*` 2.8.7, `compose-bom` 2024.09.00, `credentials` 1.5.0) | `PIHAK KETIGA` (Apache 2.0) | Terdaftar di `gradle/libs.versions.toml` & `app/build.gradle.kts` | Risiko rendah: wajib menyertakan atribusi Apache 2.0 | Tidak | Sertakan `THIRD-PARTY-NOTICES.md` pada setiap distribusi APK & source | Tinggi |
| Kotlin Stdlib & `kotlinx-coroutines` (`2.4.21` / `1.10.2`) | `PIHAK KETIGA` (Apache 2.0) | Terdaftar di `gradle/libs.versions.toml` | Risiko rendah: wajib atribusi Apache 2.0 | Tidak | Cantumkan di `THIRD-PARTY-NOTICES.md` | Tinggi |
| Google Identity (`googleid` 1.1.1), `firebase-auth` (`25.0.0`), `play-services-*` (9 artefak), `recaptcha` (`18.6.1`), `play:core-common`/`integrity`, & `firebase-firestore` (`27.1.0`) + transitif | `PIHAK KETIGA` (**Android SDK License**, **Play Core/Integrity ToS**, **Apache 2.0**, **BSD-3-Clause**, **Go License**, **MIT**) | Terverifikasi dari `.pom` aktual di `/opt/gradle/.gradle/` (`CP010I`) | Komponen SDK proprietary Google tidak boleh diklaim OSS/milik Dhedry; wajib patuhi ToS Google & sertakan notice BSD-3-Clause/Go/MIT/Apache 2.0 | Tidak | Cantumkan di `THIRD-PARTY-NOTICES.md`, `EULA.md`, & layar notice in-app | Tinggi |
| Google Fonts (`space_grotesk.ttf`, `plus_jakarta_sans.ttf`, `jetbrains_mono.ttf`) | `PIHAK KETIGA` (SIL OFL 1.1) | File biner `.ttf` di `app/src/main/res/font/` | **Dilarang menjual file font secara terpisah**; wajib atribusi OFL 1.1 | Tidak | Sertakan teks/atribusi SIL OFL 1.1 di `THIRD-PARTY-NOTICES.md` | Tinggi |
| JUnit (`4.13.2`), Robolectric (`4.16.1`), `androidx.test.*`, `coroutines-test` | `PIHAK KETIGA` (EPL 1.0 / MIT / Apache 2.0) | Hanya pada konfigurasi `testImplementation` / `androidTestImplementation` | Tidak masuk APK rilis; EPL 1.0 hanya di lingkup test runner | Tidak | Pertahankan sebagai test-only | Rendah |

---

### Kelompok 3 — Pihak Ketiga / Komponen yang Perlu Diganti atau Dirapikan Sebelum Komersialisasi

*(Catatan: Tidak ada dependency Gradle open-source yang melanggar atau bersifat copyleft agresif seperti GPL/AGPL. Daftar berikut adalah komponen non-dependency atau turunan template/AI yang direkomendasikan untuk diganti pada fase persetujuan berikutnya.)*

| Komponen | Status Kepemilikan | Bukti | Risiko Hukum / Teknis | Perlu Diganti? | Rekomendasi | Prioritas |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Aset raster AI-generated (`zclone_launcher_icon_1795216406150.jpg`, `img_hero_clone_layers_1795216430001.jpg`, `mipmap-*/ic_launcher*.png` lama) | **Diganti di `CP010H`** (`OWNERSHIP UNVERIFIED`) | Diganti oleh `ic_launcher_{background,foreground,monochrome}.xml`, `ic_zclone_brand_mark.xml`, `img_zclone_hero_layers.xml`, & fallback PNG deterministik | Aset AI lama sudah dihapus; aset vektor baru menunggu Git baseline untuk bukti hukum | Selesai secara teknis (`CP010H`) | Sertakan pada Git baseline & verifikasi pencipta pada fase berikutnya | Tinggi |
| File boilerplate starter template (`ui/theme/{Color,Theme,Type}.kt`, `themes.xml` nama `Theme.MyApplication`, `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`) | `PIHAK KETIGA` / Template Standar | Bawaan template Android/AI Studio sejak turn awal | Tidak berisiko hukum (Apache 2.0/template), namun meninggalkan jejak template (`Theme.MyApplication`) | Opsional (Kosmetik/Kebersihan IP) | Refactor ke `Theme.ZClone` dan gabungkan logika warna langsung dari `FMSThemeEngine` setelah persetujuan | Rendah |
| Nama merek pihak ketiga di `FmsParityCatalog.kt` (*App Cloner* & URL `appcloner.app`/`appcloner.blog`, 16 kemunculan) | `OWNERSHIP UNVERIFIED` (teks menyebut merek pihak ketiga) | Terbaca di `FmsParityCatalog.kt` baris 5–216 | **Risiko Sedang–Tinggi** pelanggaran merek dagang / penolakan listing bila masuk APK rilis komersial atau publik | Ya (di checkpoint implementasi khusus pasca-CP010J) | Hapus string/URL pesaing & ganti dengan katalog fungsional teknis independen (bukan sekadar rename/obfuscation); **jangan ubah kode di CP010J** | Tinggi |

---

### Kelompok 4 — Asal-Usul Belum Diketahui (`BELUM DIKETAHUI`)

| Komponen | Status Kepemilikan | Bukti | Risiko Hukum / Teknis | Perlu Diganti? | Rekomendasi | Prioritas |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `public/assets/aistudio/file_00000000a6548211b51d0f3bb49be351_4830d05e.png` & `Paste_November_21__2026_-_2_10AM_c6154edd` | `BELUM DIKETAHUI` | File lampiran sesi pengguna (`public/assets/aistudio/`) | Jika berasal dari desainer/pihak ketiga lain, mempublikasikan file mentah ke GitHub publik berisiko melanggar hak cipta pengarang mockup | Periksa / Jangan Sertakan di Repo Publik | Konfirmasi pencipta asli mockup; kecualikan `public/assets/aistudio/` dari distribusi source komersial jika bukan milik Dhedry | Tinggi |
| Aset visual AI-generated di `res/drawable` & `mipmap-*` | **Diganti di `CP010H`** (`OWNERSHIP UNVERIFIED`) | File `.jpg` AI lama dihapus setelah diganti oleh `VectorDrawable` orisinal + fallback PNG deterministik | Aset vektor baru masih menunggu Git baseline untuk pembuktian hukum penuh | Selesai (terganti) | Tetapkan Git baseline & konfirmasi pencipta pada fase berikutnya | Tinggi |

---

### Kelompok 5 — Platform / Toolchain / Generated yang Tidak Dapat Diklaim sebagai Milik Dhedry

| Komponen | Status Kepemilikan | Bukti | Risiko Hukum / Teknis | Perlu Diganti? | Rekomendasi | Prioritas |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Android SDK (API 24–36, `aapt2`, `apksigner`), OpenJDK, Gradle (`9.3.1`), AGP (`9.1.1`), KSP (`2.3.5`), Secrets Plugin (`2.0.1`), Google Services Plugin (`4.5.0`) | `PIHAK KETIGA` (Platform/Toolchain) | `gradle/libs.versions.toml`, `gradle-wrapper.properties`, SDK container | Klaim kepemilikan atas toolchain adalah keliru & tidak sah | Tidak | Kecualikan tegas dalam `COPYRIGHT.md` & `LICENSE` | Tinggi |
| Firebase Cloud Firestore, Firebase Auth, Google Play Services Identity Backend | `PIHAK KETIGA` (Layanan Eksternal) | `google-services.json`, `firebase_applet_config.xml` | Layanan milik Google LLC; aplikasi tunduk Google/Firebase ToS | Tidak | Dokumentasikan batasan layanan di `EULA.md` | Sedang |
| `debug.keystore` / `debug.keystore.base64` | `PIHAK KETIGA` (Debug Runtime Key) | File root container (`CN=Android Debug`) | **Bukan** kunci rilis pribadi Dhedry; tidak boleh dipakai untuk distribusi produksi | Tidak (untuk debug dev); gunakan keystore rilis terpisah | Jangan anggap sebagai aset IP atau keystore rilis | Tinggi |
| `BuildConfig.java`, `R`, DEX, `app-debug.apk`, `output-metadata.json` | `GENERATED` | Output di `app/build/` dan `.build-outputs/` | Artefak terkompilasi, bukan source orisinal terpisah | Tidak | Diabaikan oleh `.gitignore` | Rendah |

---

## 3. Rencana Alternatif Independen (Siap Dieksekusi Setelah Persetujuan)

> **Catatan**: Sesuai batasan `CP010F`, belum ada kode, dependensi, atau aset yang diganti pada tahap ini.

1. **Penggantian Aset Grafis AI-Generated (`zclone_launcher_icon_*.jpg`, `img_hero_clone_layers_*.jpg`)**:
   - **Alasan**: Menghilangkan ketidakpastian hukum hak cipta atas aset raster AI-generated.
   - **Rancangan Alternatif**: Membuat Android `VectorDrawable` XML (`.xml` path vektor) untuk ikon adaptif ZClone dan ilustrasi geometris beranda yang ditulis/didesain langsung oleh Dhedry Afriazoni atau desainer dengan perjanjian pengalihan IP tertulis.
   - **Kompatibilitas API**: `ic_launcher_foreground.xml` dan `SplashAndHomeScreens.kt` (`painterResource(...)`) mendukung `VectorDrawable` secara native tanpa mengubah dependency, ukuran APK justru mengecil, dan 100% kompatibel dengan API 24–36.
   - **Estimasi Pekerjaan & Risiko**: Pekerjaan ringan (1 iterasi desain + validasi build), risiko regresi teknis sangat rendah.

2. **Pembersihan Sisa Template (`Theme.MyApplication` -> `Theme.ZClone`) & Evaluasi `public/assets/aistudio/`**:
   - **Alasan**: Menghilangkan nama style template default dan mencegah file mockup referensi terbawa secara tidak sengaja ke repositori komersial.
   - **Estimasi Pekerjaan & Risiko**: Sangat rendah (rename style di `themes.xml` + `AndroidManifest.xml`, serta pemindahan/pengecualian folder referensi).

3. **Penguatan Rantai Bukti Kepemilikan (IP Evidence Hardening)**:
   - Inisialisasi Git repository, tambahkan header copyright `© 2026 Dhedry Afriazoni` + `SPDX-License-Identifier` pada seluruh berkas Kotlin orisinal di `com.fms.*` dan `com.fms.zclone.enginecoremode.*`, buat tag rilis dengan checksum SHA-256 baseline, dan aktifkan `CONTRIBUTING.md` (CLA) sebelum menerima kontributor.

4. **Daftar Dependency untuk Tinjauan Berkala (Tanpa Penggantian Saat Ini)**:
   - Seluruh dependency aktif saat ini (AndroidX, Compose, Kotlin, Firebase BoM, Credential Manager, GoogleId) berlisensi **Apache 2.0** (kompatibel dengan Dual License komersial & proprietary).
   - Font Google Fonts (OFL 1.1) kompatibel selama **tidak dijual terpisah** dan atribusi dipertahankan.
   - `google-devtools-ksp` (`2.3.5`) terpasang sebagai plugin namun saat ini berstatus `SKIPPED` (tidak dipakai oleh Room/Moshi karena dikomentari) — dapat ditinjau di fase pembersihan berikutnya bila tidak direncanakan dipakai.
