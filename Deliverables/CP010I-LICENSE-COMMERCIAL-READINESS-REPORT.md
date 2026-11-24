# CP010I — LICENSE CONSISTENCY & COMMERCIAL READINESS AUDIT REPORT

> **STATUS PENILAIAN AKHIR: `PARTIALLY VERIFIED` — `NOT COMMERCIAL READY / NOT LEGALLY CLEARED`**  
> *(Proyek **BELUM** memenuhi syarat rilis komersial penuh maupun clearance hukum karena masih terdapat inkonsistensi draf lisensi yang memerlukan keputusan Dhedry Afriazoni, ketiadaan bukti kepemilikan Git/author trail pada modul kustom (`OWNERSHIP UNVERIFIED`), koreksi lisensi dependency aktual (`Android SDK License`, `BSD-3-Clause`, `Go License`, `Play SDK Terms`), serta berkas `public/assets/aistudio/` yang masih `PENDING`.)*

- **Pemilik Target Ekosistem**: `© Dhedry Afriazoni`
- **Tanggal Audit**: `2026-11-21` (`CP010I`)
- **Identitas APK Dipertahankan**: `com.fms.zclone.enginecoremode` (`ZClone`)
- **Namespace Library FMS Dipertahankan**: `com.fms.core`, `com.fms.security`, `com.fms.data`, `com.fms.ui`, `com.fms.task`, `com.fms.cloud`, `com.fms.device`, `com.fms.clone`
- **Lingkup CP010I**: Audit dokumen lisensi, audit dependency Gradle aktual (`releaseRuntimeClasspath`, `debugRuntimeClasspath`, `debugUnitTestRuntimeClasspath`, `buildEnvironment`), audit risiko distribusi, dan patch minimal dokumentasi audit saja (tanpa mengubah kode `.kt`, aset, dependency, Gradle, Firebase, signing, atau membangun APK/ZIP).

---

## 1. Ringkasan Hasil Audit

1. **Konsistensi 11 Dokumen Lisensi & IP (`DRAFT`)**:
   - Struktur Dual License (`LICENSE`, `COMMUNITY-LICENSE.md`, `COMMERCIAL-LICENSE.md`, `EULA.md`, `COPYRIGHT.md`, `TRADEMARK-NOTICE.md`, `CONTRIBUTING.md`, `LICENSE-FAQ.md`) sudah memisahkan secara eksplisit antara APK biner, source code, tidak berpindahnya hak cipta pada lisensi komersial, serta ketiadaan klaim pendaftaran merek `®`.
   - Namun ditemukan **5 konflik/inkonsistensi antardokumen** (terutama antara cakupan evaluasi di `LICENSE` vs definisi pengguna di `COMMUNITY-LICENSE.md`, klausul aset `.jpg` lama di `COPYRIGHT.md` & `LICENSE-FAQ.md` pasca-`CP010H`, status `OWNERSHIP UNVERIFIED` pasca-`CP010G`, ambiguitas reverse-engineering APK vs Community Source-Available, dan kesalahan klasifikasi lisensi di `THIRD-PARTY-NOTICES.md`).
2. **Audit Dependency Gradle Aktual (Berdasarkan POM Cache Resmi, Bukan Perkiraan)**:
   - `releaseRuntimeClasspath`: **166** artefak terselesaikan (`21` langsung, `145` transitif).
   - `debugRuntimeClasspath`: **173** artefak terselesaikan (`23` langsung, `150` transitif; `7` artefak tambahan khusus debug UI tooling/material).
   - `debugUnitTestRuntimeClasspath`: **218** artefak (`45` artefak khusus unit test di luar `debugRuntimeClasspath`, termasuk `junit` EPL 1.0, `robolectric` MIT, `javax.annotation-api` **CDDL + GPLv2+CE**, `bcprov-jdk18on` Bouncy Castle, `icu4j` Unicode-3.0).
   - `buildEnvironment`: **122** artefak build/plugin (`5` plugin langsung + `117` transitif, termasuk `jna`/`jna-platform` **LGPL 2.1 OR Apache 2.0** dan `juniversalchardet` **MPL 1.1** yang hanya berjalan saat kompilasi/build, tidak masuk APK).
   - **Temuan Penting yang Sebelumnya Luput**:
     - `com.google.android.libraries.identity.googleid:googleid:1.1.1`, `com.google.firebase:firebase-auth:25.0.0`, `firebase-auth-interop:20.0.0`, `play-services-*` (9 artefak), dan `recaptcha:18.6.1` di POM resminya berlisensi **`Android Software Development Kit License`** (ketentuan SDK Google berhak milik), **bukan Apache 2.0**.
     - Masuknya library transitif non-Apache ke dalam APK rilis: `Play Core ToS` (`core-common`), `Play Integrity ToS` (`integrity`), **`BSD-3-Clause`** (`protobuf-javalite`, `datastore-preferences-external-protobuf`), **`Go License`** (`re2j`), dan **`MIT`** (`animal-sniffer-annotations`).
     - Tidak ada dependency copyleft agresif (GPL/AGPL) yang masuk ke `releaseRuntimeClasspath` APK.
3. **Kesiapan Komersial (`Commercial Readiness`)**:
   - **BELUM SIAP KOMERSIAL (`NOT COMMERCIAL READY`)**. Hak atas source code kustom (`8.982` baris Kotlin + 5 VectorDrawable baru + aturan Firebase) masih berstatus **`OWNERSHIP UNVERIFIED`** karena belum ada repositori Git, commit history manusia, maupun finalisasi hukum atas draf lisensi.

---

## 2. Daftar Konflik dan Inkonsistensi Lisensi

| ID | Dokumen Terlibat | Konflik / Inkonsistensi yang Ditemukan | Dampak | Rekomendasi (Menunggu Persetujuan Pemilik) |
| :--- | :--- | :--- | :--- | :--- |
| **LC-01** | `LICENSE` (§1.1) vs `COMMUNITY-LICENSE.md` (§1, §3) | `LICENSE` menyebut jalur Community untuk *"evaluasi"* secara umum, sedangkan `COMMUNITY-LICENSE.md` membatasi `"Pengguna Community"` hanya kepada *"individu atau organisasi nirlaba/akademik"*. Perusahaan komersial yang ingin mengevaluasi kode menjadi berada di wilayah abu-abu. | Potensi sengketa apakah evaluasi internal perusahaan boleh memakai jalur Community atau wajib membayar lisensi evaluasi komersial. | Pilih salah satu: (a) izinkan evaluasi internal non-produksi bagi perusahaan di `COMMUNITY-LICENSE.md` selama terbatas waktu/non-produksi, **atau** (b) hapus kata *"evaluasi bisnis"* dari `LICENSE` untuk jalur Community. |
| **LC-02** | `COMMUNITY-LICENSE.md` (§2–3) vs `EULA.md` (§3.2) | `COMMUNITY-LICENSE.md` memberi hak *mempelajari dan memodifikasi kode sumber*, sedangkan `EULA.md` §3.2 melarang *reverse engineering* APK kecuali diizinkan lisensi source code. Untuk distribusi APK gratis tanpa source code, `EULA.md` jelas; tetapi jika proyek dirilis source-available di GitHub, relasi antara APK Community dan repo source belum ditegaskan. | Kebingungan pengguna Community apakah APK gratis boleh diinspeksi bersama source atau apakah rilis publik hanya berupa source/APK terpisah. | Tegaskan di `EULA.md` & `COMMUNITY-LICENSE.md` bahwa `EULA.md` mengatur biner APK terkompilasi, sedangkan akses source code diatur oleh `COMMUNITY-LICENSE.md` atau `COMMERCIAL-LICENSE.md`. |
| **LC-03** | `COMMUNITY-LICENSE.md` (§2–3) vs praktik penggunaan non-komersial | `COMMUNITY-LICENSE.md` melarang *"tujuan komersial langsung maupun tidak langsung"*, namun belum merinci kasus abu-abu: pemakaian internal oleh pekerja lepas (*freelancer*), proyek dengan sponsor/donasi sukarela, atau batas jumlah perangkat/pengguna organisasi. | Interpretasi yang terlalu luas dapat menghambat adopsi komunitas atau meloloskan pemakaian komersial terselubung. | Tetapkan definisi objektif batas non-komersial (mis. batas pendapatan/entitas komersial vs pribadi/pendidikan/nirlaba) melalui persetujuan Dhedry. |
| **LC-04** | `COPYRIGHT.md` (§2.1, §2.3) & `LICENSE-FAQ.md` (§7) vs hasil `CP010G` & `CP010H` | `COPYRIGHT.md` dan `LICENSE-FAQ.md` masih berstatus `DRAFT` dengan narasi era `CP010F` (masih menyebut berkas `.jpg` AI-generated yang sudah dihapus di `CP010H`, dan belum merefleksikan temuan `CP010G` bahwa modul kustom masih berstatus bukti `OWNERSHIP UNVERIFIED`). | Pembaca/pembeli bisa keliru mengira klaim hak cipta dalam draf sudah final dan aset `.jpg` masih ada. | Setelah Dhedry menyetujui pembaruan draf akhir di fase pasca-Git baseline, sinkronkan `COPYRIGHT.md` dan `LICENSE-FAQ.md` (tidak diubah sepihak di `CP010I`). |
| **LC-05** | `THIRD-PARTY-NOTICES.md` vs POM Gradle aktual `CP010I` | Sebelum pemutakhiran di `CP010I`, `THIRD-PARTY-NOTICES.md` mencantumkan `googleid` dan `firebase-auth` sebagai Apache 2.0 (padahal POM resminya **Android SDK License**), serta belum mencatat dependensi transitif non-Apache (`BSD-3-Clause`, `Go License`, `MIT`, `Play Core ToS`, `Play Integrity ToS`). | Ketidakpatuhan atribusi saat distribusi APK/source (terutama kewajiban notice BSD-3-Clause, Go License, MIT, dan syarat Play/Android SDK). | **Sudah diperbaiki pada `THIRD-PARTY-NOTICES.md` di CP010I** berdasarkan verifikasi POM cache aktual. |
| **LC-06** | `FmsParityCatalog.kt` vs `TRADEMARK-NOTICE.md` | `FmsParityCatalog.kt` memuat string `App Cloner` dan tautan `https://appcloner.app` / `https://appcloner.blog` yang turut tampil atau tersimpan di APK. Meskipun `TRADEMARK-NOTICE.md` sudah memberi disclaimer komparatif, materi komersial/listing toko sangat rentan terhadap klaim pelanggaran merek dagang pihak ketiga. | Risiko *trademark takedown* di Google Play atau keluhan pemilik merek *App Cloner* jika APK dipublikasikan secara komersial. | Putuskan pada fase pembersihan: hapus/netralkan string merek komparatif dari kode/UI rilis komersial (pindahkan referensi riset ke dokumen internal saja). |

---

## 3. Tabel Dependency Langsung dan Transitif Beserta Lisensinya (Aktual Gradle POM)

Ringkasan terverifikasi dari resolusi aktual `:app:dependencies` (`releaseRuntimeClasspath` = 166 artefak; `debugRuntimeClasspath` = 173 artefak; `debugUnitTestRuntimeClasspath` = 218 artefak; `buildEnvironment` = 122 artefak) dengan membaca tag `<licenses>` pada berkas `.pom` di `/opt/gradle/.gradle/caches/modules-2/files-2.1/`.

### 3.1 Dependency Runtime APK (`releaseRuntimeClasspath` & `debugRuntimeClasspath`)

| Komponen / Kelompok Artefak & Versi | Langsung / Transitif | Masuk Release APK? | Tujuan Penggunaan | Lisensi Aktual (POM) | Sumber / Bukti | Kewajiban Atribusi / Distribusi | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `androidx.compose:compose-bom:2024.09.00` + 7 artefak langsung Compose UI/M3/Icons (`activity-compose:1.10.1`, `material3:1.3.0`, `material-icons-core/extended:1.7.0`, `ui:1.7.0`, `ui-graphics:1.7.0`, `ui-tooling-preview:1.7.0`) + ~30 artefak transitif Compose (`foundation`, `animation`, `runtime:1.7.1`, `material-ripple`, `ui-text`, `ui-unit`, `ui-util`, dll.) | Langsung (BOM + 7) & Transitif (~30) | **Ya** | Rendering UI Jetpack Compose & Material Design 3 | `The Apache Software License, Version 2.0` | POM masing-masing (`compose-bom-2024.09.00.pom`, `material3-android-1.3.0.pom`, dll.) | Pertahankan notice Apache 2.0 dalam distribusi source & dokumentasi APK. | `VERIFIED` |
| `androidx.compose.ui:ui-tooling:1.7.0`, `ui-test-manifest:1.7.0` + 5 transitif debug (`ui-tooling-android`, `ui-tooling-data*`, `compose.material:material*`) | Langsung (`debugImplementation`) & Transitif (5) | **Tidak** (Debug-only) | Inspeksi preview UI & test manifest debug | `The Apache Software License, Version 2.0` | `ui-tooling-preview-1.7.0.pom`, `material-1.7.0.pom`, dll. | Hanya berlaku pada build debug; tidak masuk `releaseRuntimeClasspath`. | `VERIFIED` |
| `androidx.core:core-ktx:1.18.0`, `androidx.lifecycle:lifecycle-{runtime-compose,runtime-ktx,viewmodel-compose}:2.8.7`, `androidx.credentials:credentials:1.5.0`, `credentials-play-services-auth:1.5.0` + ~44 transitif AndroidX (`core`, `appcompat`, `fragment`, `biometric`, `browser`, `datastore*` 1.1.7, `savedstate`, `startup`, `profileinstaller`, `collection`, `concurrent-futures`, dll.) | Langsung (6) & Transitif (~44) | **Ya** | Fondasi AndroidX, lifecycle, dan Credential Manager Google Sign-In | `The Apache Software License, Version 2.0` | POM resmi masing-masing di cache Gradle | Pertahankan notice Apache 2.0; catat bahwa `datastore*` terbawa secara transitif meski entri langsungnya dikomentari. | `VERIFIED` |
| `androidx.datastore:datastore-preferences-external-protobuf:1.1.7` | **Transitif** (via `datastore-preferences-core:1.1.7` <- `credentials`) | **Ya** | Skema protobuf internal DataStore | `BSD-3-Clause` | `datastore-preferences-external-protobuf-1.1.7.pom` | Wajib mempertahankan copyright notice & disclaimer BSD-3-Clause pada distribusi source/biner. | `VERIFIED` |
| `org.jetbrains.kotlin:kotlin-stdlib:2.4.21` + `kotlin-stdlib-common:2.4.21`, `kotlin-stdlib-jdk7:1.9.0`, `kotlin-stdlib-jdk8:1.8.0`, `kotlin-parcelize-runtime:1.9.22`, `kotlin-android-extensions-runtime:1.9.22` | Langsung (`kotlin-stdlib`) & Transitif (5) | **Ya** | Runtime Kotlin & parcelize | `Apache-2.0` | `kotlin-stdlib-2.4.21.pom`, dll. | Pertahankan notice Apache 2.0. | `VERIFIED` |
| `org.jetbrains.kotlinx:kotlinx-coroutines-{android,core}:1.10.2` + `coroutines-bom`, `coroutines-core-jvm`, `coroutines-play-services:1.10.2`, `annotations:23.0.0`, `jspecify:1.0.0` | Langsung (2) & Transitif (5) | **Ya** | Konkurensi coroutine & integrasi Task Play Services | `Apache-2.0` | `kotlinx-coroutines-android-1.10.2.pom`, dll. | Pertahankan notice Apache 2.0 & NOTICE JetBrains. | `VERIFIED` |
| `com.google.firebase:firebase-bom:35.1.0`, `com.google.firebase:firebase-firestore:27.1.0` + `firebase-common:23.0.0`, `firebase-components:20.0.0`, `firebase-annotations:18.0.0`, `firebase-appcheck-interop:18.0.0`, `firebase-database-collection:19.0.0`, `protolite-well-known-types:19.0.0` | Langsung (BOM + `firestore`) & Transitif (6) | **Ya** | Cloud Sync Firestore & interop Firebase | `The Apache Software License, Version 2.0` | `firebase-firestore-27.1.0.pom`, `firebase-common-23.0.0.pom`, dll. | Notice Apache 2.0 untuk SDK + patuhi Firebase Terms of Service untuk layanan cloud. | `VERIFIED` |
| `com.google.firebase:firebase-auth:25.0.0` (Langsung) & `firebase-auth-interop:20.0.0` (Transitif) | Langsung (1) & Transitif (1) | **Ya** | Autentikasi Firebase Auth client | **`Android Software Development Kit License`** *(Bukan Apache 2.0!)* | `firebase-auth-25.0.0.pom`, `firebase-auth-interop-20.0.0.pom` | Tunduk Android SDK License (proprietary Google SDK binary terms); **tidak boleh diklaim open-source Apache 2.0** atau dipisah keluar dari ekosistem Android yang diizinkan. | `NEEDS REVIEW` |
| `com.google.android.libraries.identity.googleid:googleid:1.1.1` | **Langsung** | **Ya** | Parsing token Google ID Credential Manager | **`Android Software Development Kit License`** *(Bukan Apache 2.0!)* | `googleid-1.1.1.pom` | Sama seperti di atas (`Android SDK License`); koreksi wajib di `THIRD-PARTY-NOTICES.md`. | `NEEDS REVIEW` |
| `com.google.android.gms:play-services-{auth,auth-api-phone,auth-base,auth-blockstore,base,basement,fido,identity-credentials,tasks}` (`16.0.0-alpha02`–`21.1.1`) & `com.google.android.recaptcha:recaptcha:18.6.1` | **Transitif** (10 artefak via `credentials-play-services-auth` & `firebase-auth`) | **Ya** | Integrasi Play Services Auth / reCAPTCHA | **`Android Software Development Kit License`** | POM masing-masing (`play-services-auth-21.1.1.pom`, `recaptcha-18.6.1.pom`, dll.) | Tunduk Android SDK License & Google Play Services Terms; cantumkan di `THIRD-PARTY-NOTICES.md`. | `NEEDS REVIEW` |
| `com.google.android.play:core-common:2.0.3` & `com.google.android.play:integrity:1.3.0` | **Transitif** (2 artefak via `firebase-auth` / `recaptcha`) | **Ya** | Interop Play Core & Play Integrity SDK (ditarik oleh Auth/reCAPTCHA) | **`Play Core Software Development Kit Terms of Service`** & **`Play Integrity API Terms of Service`** | `core-common-2.0.3.pom`, `integrity-1.3.0.pom` | Wajib mematuhi Play Core ToS & Play Integrity ToS; perhatikan kebijakan Google Play dalam `EULA.md`. | `NEEDS REVIEW` |
| `io.grpc:grpc-{android,api,context,core,okhttp,protobuf-lite,stub,util}:1.84.0`, `io.perfmark:perfmark-api:0.27.0`, `com.squareup.okio:okio(-jvm):3.4.0`, `com.google.guava:guava:33.6.0-android` (+ `failureaccess`, `listenablefuture`), `com.google.code.gson:gson:2.14.0`, `com.google.code.findbugs:jsr305:3.0.2`, `com.google.errorprone:error_prone_annotations:2.50.0`, `com.google.j2objc:j2objc-annotations:3.1`, `com.google.android:annotations:4.1.1.4`, `javax.inject:javax.inject:1` | **Transitif** (20 artefak via `firebase-firestore`) | **Ya** | Transport gRPC/OkHttp/Protobuf Firestore client | `Apache License 2.0` | POM masing-masing (`grpc-core-1.84.0.pom`, `okio-3.4.0.pom`, `guava-parent-33.6.0-android.pom`, dll.) | Pertahankan notice Apache 2.0 (khusus Guava/gRPC bila ada file NOTICE di distribusi SDK). | `VERIFIED` |
| `com.google.protobuf:protobuf-javalite:4.36.1` | **Transitif** (via `firebase-firestore` / `protolite`) | **Ya** | Serialisasi wire Protobuf Firestore & DataStore | **`BSD-3-Clause`** | `protobuf-parent-4.36.1.pom` | **Wajib** menyertakan teks/notice hak cipta Protobuf (BSD-3-Clause) pada distribusi biner APK dan source. | `VERIFIED` |
| `com.google.re2j:re2j:1.6` | **Transitif** (via `recaptcha` / `firebase-auth`) | **Ya** | Mesin regex aman linear-time | **`Go License`** *(BSD-style + tambahan klausul Go)* | `re2j-1.6.pom` | **Wajib** mempertahankan notice hak cipta & lisensi Go License dalam dokumentasi/biner. | `VERIFIED` |
| `org.codehaus.mojo:animal-sniffer-annotations:1.27` | **Transitif** (via `guava`) | **Ya** | Anotasi kompatibilitas Java API | **`MIT license`** | `animal-sniffer-parent-1.27.pom` | Sertakan copyright notice MIT dalam `THIRD-PARTY-NOTICES.md`. | `VERIFIED` |

*(Catatan: Sebanyak `0` artefak di `releaseRuntimeClasspath` atau `debugRuntimeClasspath` yang berstatus `UNKNOWN` atau kehilangan tag `<license>` POM.)*

### 3.2 Dependency Pengujian (Test-Only — Tidak Masuk APK Rilis)

| Komponen & Versi | Langsung / Transitif | Tujuan | Lisensi Aktual (POM) | Kewajiban / Status |
| :--- | :--- | :--- | :--- | :--- |
| `junit:junit:4.13.2` | Langsung (`testImplementation`) | Runner unit test | `Eclipse Public License 1.0 (EPL 1.0)` | Test-only; tidak masuk APK (`VERIFIED`). |
| `org.hamcrest:hamcrest-{core,integration,library}:1.3` | Transitif (via JUnit) | Assertion matcher | `New BSD License` (BSD-3-Clause) | Test-only (`VERIFIED`). |
| `org.robolectric:robolectric:4.16.1` (+ 12 sub-artefak `org.robolectric:*`) | Langsung & Transitif | Simulasi Android SDK di JVM | `The MIT License` | Test-only (`VERIFIED`). |
| `androidx.test:*` (`core:1.7.0`, `ext:junit:1.3.0`, `runner`, `espresso-core`), `ui-test-junit4:1.7.0`, `kotlinx-coroutines-test:1.10.2` | Langsung & Transitif | Test framework AndroidX & coroutine test | `Apache License 2.0` | Test-only (`VERIFIED`). |
| `javax.annotation:javax.annotation-api:1.3.2` | **Transitif** (test-only via Robolectric) | Anotasi JAX/Java | **`CDDL + GPLv2 with classpath exception`** | Test-only (`NEEDS REVIEW` hanya bila suatu saat masuk compile/runtime; saat ini aman karena **tidak masuk APK**). |
| `com.ibm.icu:icu4j:77.1`, `org.bouncycastle:bcprov-jdk18on:1.81`, `org.ow2.asm:asm*:9.8` | **Transitif** (test-only via Robolectric) | Simulasi lokalitas/crypto/bytecode oleh Robolectric | `Unicode-3.0`, `Bouncy Castle Licence`, `BSD-3-Clause` | Test-only; tidak masuk APK (`VERIFIED`). |

### 3.3 Plugin Gradle, Build Classpath (`buildEnvironment`), Font, Aset Visual, SDK, & Layanan Eksternal

| Komponen & Versi | Jenis | Masuk APK? | Lisensi / Ketentuan Resmi | Bukti | Status & Kewajiban |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `com.android.application:9.1.1` (AGP), `kotlin.plugin.compose:2.4.21`, `google.devtools.ksp:2.3.5`, `secrets-gradle-plugin:2.0.1`, `google-services:4.5.0` (+ 94 artefak Apache 2.0 build-time) | Plugin Gradle & Transitif Build | Tidak | `Apache License 2.0` | POM di `/opt/gradle/.gradle/` | Build-time only (`VERIFIED`). |
| `net.java.dev.jna:jna:5.6.0`, `jna-platform:5.6.0` | Build Transitif (via AGP) | Tidak | **`LGPL 2.1 OR Apache License 2.0`** (Dual) | `jna-5.6.0.pom` | Build-time only; opsi Apache 2.0 aman (`VERIFIED`). |
| `com.googlecode.juniversalchardet:juniversalchardet:1.0.3` | Build Transitif (via AGP) | Tidak | **`Mozilla Public License 1.1 (MPL 1.1)`** | `juniversalchardet-1.0.3.pom` | Build-time only; tidak terbundel di APK (`VERIFIED`). |
| `org.bouncycastle:{bcprov,bcpkix,bcutil}-jdk18on:1.79` | Build Transitif (via AGP `signflinger`) | Tidak | `Bouncy Castle Licence` | `bcprov-jdk18on-1.79.pom` | Build-time signing tooling saja (`VERIFIED`). |
| `org.ow2.asm:asm*:9.9`, `com.google.protobuf:protobuf-java*:3.25.5`, `net.sf.kxml:kxml2:2.3.0`, `org.jdom:jdom2:2.0.6`, `jakarta.*`/`jaxb-runtime:2.3.2`, `jopt-simple:4.9`, `slf4j-api:1.7.30` | Build Transitif (via AGP) | Tidak | `BSD-3-Clause`, `BSD style / Public Domain`, `EDL 1.0`, `MIT`, `JDOM Apache-like` | POM masing-masing di cache Gradle | Build-time only; tidak masuk APK (`VERIFIED`). |
| `space_grotesk.ttf`, `plus_jakarta_sans.ttf`, `jetbrains_mono.ttf` (`app/src/main/res/font/`) | Aset Font Bundled | **Ya** | **`SIL Open Font License 1.1 (OFL 1.1)`** | Google Fonts / `font-util` (CP005) | **Wajib** sertakan teks/notice OFL 1.1 di APK & dokumentasi; **dilarang menjual file font secara terpisah**. (`VERIFIED`). |
| `ic_launcher_{background,foreground,monochrome}.xml`, `ic_zclone_brand_mark.xml`, `img_zclone_hero_layers.xml`, `mipmap-*/ic_launcher*.png` (`CP010H`) | Aset Vektor & Fallback PNG | **Ya** | Kandidat Proprietary Dhedry (disusun AI sesi `CP010H`) | Dibuat di `CP010H` (`BUILD SUCCESSFUL`) | **`OWNERSHIP UNVERIFIED`** hingga Git baseline & verifikasi manusia. |
| `public/assets/aistudio/*` (2 berkas lampiran sesi) | Referensi Eksternal/Input | Tidak | Asal-usul pencipta belum diverifikasi | Attachment sesi pengguna | **`PENDING` / `UNKNOWN`** — jangan sertakan di rilis komersial/publik tanpa verifikasi hak. |
| Android SDK (API 24–36), Gradle 9.3.1, OpenJDK, `debug.keystore` | Platform / Toolchain / Debug Key | Key debug masuk debug APK | Android SDK License / Apache 2.0 / GPLv2+CE / Generic Debug Key | Container environment | Tidak dimiliki Dhedry; `debug.keystore` dilarang dipakai untuk APK rilis publik (`VERIFIED`). |
| Google Firebase Auth & Cloud Firestore (`gen-lang-client-0584823370`) | Layanan Cloud SaaS | Akses jaringan dari APK | Google APIs / Firebase Terms of Service | `app/google-services.json`, `firebase_applet_config.xml` | Tunduk kuota, privasi, & Google/Firebase ToS; tidak dimiliki Dhedry (`VERIFIED`). |

---

## 4. Daftar Kewajiban Atribusi dan Distribusi

Setiap distribusi APK (gratis maupun berbayar) atau source code **wajib** memenuhi kewajiban pihak ketiga berikut:
1. **Apache License 2.0** (AndroidX, Jetpack Compose, Kotlin Stdlib, Coroutines, Firebase Firestore, gRPC, Guava, Okio, Gson):
   - Wajib menyertakan salinan pemberitahuan hak cipta dan lisensi Apache 2.0 (dan file `NOTICE` bila disyaratkan, mis. pada distribusi source/SDK atau layar *About / Open-Source Licenses* di dalam aplikasi).
   - *Celah UI saat ini*: Meskipun `THIRD-PARTY-NOTICES.md` sudah ada di repositori, **aplikasi APK sendiri belum menampilkan menu/halaman in-app Open-Source Licenses & Third-Party Notices** (layar Settings saat ini hanya ringkas). Sangat disarankan menambahkan penampil notice in-app sebelum rilis Play Store / komersial.
2. **BSD-3-Clause (`protobuf-javalite`, `datastore-preferences-external-protobuf`), Go License (`re2j`), & MIT (`animal-sniffer-annotations`)**:
   - Ketiga lisensi permisif ini mewajibkan **penyertaan copyright notice dan teks disclaimer** dalam distribusi sumber maupun dokumentasi distribusi biner APK.
3. **SIL Open Font License 1.1 (`space_grotesk.ttf`, `plus_jakarta_sans.ttf`, `jetbrains_mono.ttf`)**:
   - Boleh dibundel dan didistribusikan secara komersial di dalam APK ZClone.
   - **Dilarang keras** menjual file `.ttf` secara mandiri/terpisah (*standalone*).
   - Wajib mencantumkan atribusi pencipta font & teks lisensi OFL 1.1 dalam `THIRD-PARTY-NOTICES.md` (dan disarankan di notice in-app).
4. **Android SDK License, Play Core ToS, Play Integrity ToS, & Firebase Terms of Service**:
   - `googleid`, `firebase-auth`, `play-services-*`, `recaptcha`, `core-common`, dan `integrity` tidak boleh diedarkan ulang sebagai library SDK mandiri di luar aplikasi Android yang mematuhi ketentuan Google.
   - Penggunaan Cloud Firestore & Google Sign-In tunduk pada Google Privacy/API Terms (mensyaratkan Kebijakan Privasi pengguna akhir bila APK dipublikasikan ke toko aplikasi).

---

## 5. Risiko Community Edition (`COMMUNITY-LICENSE.md`)

1. **Konflik Definisi Evaluasi Perusahaan vs Nirlaba (LC-01)**: Membiarkan kata *"evaluasi"* di `LICENSE` sementara `COMMUNITY-LICENSE.md` hanya menyebut *"individu atau organisasi nirlaba/akademik"* dapat memicu ketidakjelasan ruang lingkup penggunaan bebas royalti.
2. **Kebocoran Komersial Terselubung (LC-03)**: Belum ada definisi batas tegas antara penggunaan non-komersial pribadi vs pemakaian oleh kontraktor/agensi/internal bisnis.
3. **Tidak Ada Kewajiban Source-Back / Copyleft untuk Modifikasi**: Karena Community License saat ini bersifat *permissive-with-non-commercial-restriction* (mengizinkan modifikasi dan redistribusi gratis tanpa kewajiban membuka kode modifikasi), pihak ketiga dapat merilis APK ZClone versi modifikasi gratis berbasis kode sumber Community tanpa menyetor perbaikan kembali — selama mereka tidak melanggar aturan merek `TRADEMARK-NOTICE.md`.
4. **Status Kontribusi Tanpa CLA Tertanda**: Jika repo Community dibuka sebelum sistem CLA/DCO aktif, kontribusi pihak ketiga berisiko mencampur hak milik saat Dhedry merilis jalur komersial.

---

## 6. Risiko Commercial Edition (`COMMERCIAL-LICENSE.md`)

1. **Rantai Bukti Kepemilikan Belum Lengkap (`OWNERSHIP UNVERIFIED`)**: Menjual lisensi komersial saat workspace belum memiliki `.git`, signed commit sejarah pencipta, serta kode & VectorDrawable baru berstatus AI-synthesized tanpa tinjauan/deklarasi manusia menciptakan risiko cacat *chain of title* bila pembeli melakukan audit hukum (*due diligence*).
2. **Celah *In-App Attribution* & Ketentuan Google SDK**: Menjual APK atau source komersial tanpa memverifikasi kepatuhan Android SDK License (`firebase-auth`, `googleid`, `play-services-*`), Play Integrity ToS, dan tanpa layar notice BSD-3-Clause/Apache-2.0/OFL-1.1 di dalam APK dapat melanggar ketentuan pihak ketiga.
3. **Referensi Merek `App Cloner` di `FmsParityCatalog.kt`**: Penjualan produk komersial yang di dalamnya (baik di source maupun di layar hub aplikasi) menyebut merek pesaing langsung dan URL `https://appcloner.app` berisiko keluhan merek dagang atau penolakan distribusi.
4. **Kredensial Backend Firebase Terikat Proyek Sesi**: `app/google-services.json` terikat ke proyek Firebase `gen-lang-client-0584823370`. Pembeli Commercial Source Code License tidak boleh mengandalkan atau dibiarkan memakai backend Firebase pribadi pemilik tanpa pemisahan proyek Google Cloud masing-masing.
5. **Ketiadaan Dokumen Privasi & Kontrak Komersial Baku**: `EULA.md` dan `COMMERCIAL-LICENSE.md` masih berstatus `DRAFT`; belum tersedia *Privacy Policy* final dan jadwal harga/kuota kontrak komersial (*Order Form / SLA*).

---

## 7. Risiko Distribusi APK dan Source Code + Pemisahan Tegas Kepemilikan

### 7.1 Risiko per Jalur Distribusi
- **Distribusi APK Gratis**: Risiko kepatuhan Google Play Policy (izin `REQUEST_INSTALL_PACKAGES` wajib memenuhi ketentuan fungsi inti kloner/installer, kebijakan privasi wajib untuk Firebase Auth/Firestore, serta kewajiban atribusi OFL 1.1 / Apache 2.0 / BSD-3-Clause / Go License). Penggunaan `debug.keystore` **wajib dilarang** untuk rilis publik.
- **Penjualan APK**: Seluruh risiko APK gratis ditambah pelanggaran kontrak jika buyer mengetahui status kode masih `OWNERSHIP UNVERIFIED` dan `COMMERCIAL-LICENSE.md` belum final secara hukum.
- **Distribusi Source Code (Publik / Pembeli)**: Risiko tertinggi bila `public/assets/aistudio/` (mockup/paste dengan hak cipta belum terverifikasi) ikut terunggah ke GitHub publik, atau bila `app/google-services.json` proyek produksi pemilik ikut terdistribusi ke repositori publik tanpa panduan penggantian.
- **Penggunaan Firebase & Layanan Eksternal**: Ketergantungan pada kuota proyek `gen-lang-client-0584823370`, kebijakan Google Sign-In/Credential Manager, serta kebutuhan pemisahan proyek Firebase untuk setiap pemegang lisensi source code komersial.
- **Font & Aset Visual**: `SIL OFL 1.1` melarang penjualan font standalone; aset `VectorDrawable` `CP010H` bebas dari JPEG AI lama tetapi belum memiliki bukti penciptaan berbasis Git.

### 7.2 Pemisahan Tegas Komponen Kepemilikan

| Kategori Kepemilikan | Cakupan Komponen Aktual di Workspace | Dasar Faktual |
| :--- | :--- | :--- |
| **A. Karya yang Dapat Diklaim sebagai Karya Dhedry Berdasarkan Bukti Tersedia Saat Ini** | **Belum ada berkas kode/aset yang terbukti penuh secara hukum (`0` berkas).** Yang terbukti saat ini barulah *spesifikasi niat & identitas proyek yang diarahkan pengguna selama sesi* (`com.fms.*`, `com.fms.zclone.enginecoremode`). | Hasil pemeriksaan `CP010G`: tidak ada `.git`, tidak ada commit sejarah manusia, dan seluruh modul ditulis AI selama sesi container. |
| **B. Kode / Aset yang Statusnya Belum Terbukti (`OWNERSHIP UNVERIFIED` / `PENDING` / `UNKNOWN`)** | 15 modul `com.fms.*` (`3.735` baris), 6 file aplikasi ZClone (`4.755` baris), 3 test kustom (`492` baris), `firestore.rules`, `firestore.test.js`, `firebase-blueprint.json`, `CP010H` VectorDrawables + 10 fallback PNGs, dokumen `CP000`–`CP010I` (`OWNERSHIP UNVERIFIED`), serta `public/assets/aistudio/*` (`PENDING` / `UNKNOWN`). | Dibuat oleh asisten AI dari spesifikasi sesi tanpa bukti Git/review manusia; `public/assets/aistudio/` berasal dari input lampiran tanpa pencatatan hak pencipta asli. |
| **C. Komponen Pihak Ketiga (Mengikuti Lisensi Masing-Masing)** | 166 artefak runtime release / 173 debug / 45 test-only (Apache 2.0, BSD-3-Clause, Go License, MIT, Android SDK License, Play Core ToS, Play Integrity ToS, EPL 1.0, CDDL+GPLv2+CE test-only), 3 font Google Fonts (SIL OFL 1.1), dan 122 artefak build-time (Apache 2.0, Bouncy Castle, BSD-3-Clause, EDL 1.0, MPL 1.1, LGPL 2.1 OR Apache 2.0). | Terverifikasi langsung dari `.pom` di `/opt/gradle/.gradle/caches/modules-2/files-2.1/` dan font di `res/font/`. |
| **D. Platform & Toolchain Eksternal (Tidak Dimiliki Dhedry)** | Android SDK (API 24–36), Gradle `9.3.1`, AGP `9.1.1`, OpenJDK, `debug.keystore` (`CN=Android Debug`), layanan Google Cloud / Firebase (`gen-lang-client-0584823370`), serta artefak generated (`BuildConfig.java`, `values.xml`, `app-debug.apk`). | Lingkungan container build, toolchain Google/JetBrains, dan infrastruktur cloud Google LLC. |

---

## 8. Komponen `"OWNERSHIP UNVERIFIED"` dan `"UNKNOWN"`

- **`OWNERSHIP UNVERIFIED`**:
  - Seluruh **24 berkas kode sumber & test kustom** (`8.982` baris Kotlin di `com.fms.*` + `com.fms.zclone.enginecoremode`), `firestore.rules`, `firestore.test.js`, `firebase-blueprint.json`, `res/xml/file_paths.xml`, `res/values/strings.xml`, `res/values-en/strings.xml`, dan **15 berkas aset grafis `CP010H`** (5 VectorDrawable + 10 fallback PNG deterministik).
- **`UNKNOWN` / `PENDING` (Asal-Usul Hak Cipta Belum Diketahui)**:
  - `public/assets/aistudio/file_00000000a6548211b51d0f3bb49be351_4830d05e.png` (`PENDING` — pencipta desain mockup tidak terverifikasi).
  - `public/assets/aistudio/Paste_November_21__2026_-_2_10AM_c6154edd` (`PENDING` — penulis asli teks spesifikasi tidak terverifikasi).
- **Dependensi Gradle Berstatus `UNKNOWN`**: **`0` artefak** — seluruh 166 artefak `releaseRuntimeClasspath`, 173 artefak `debugRuntimeClasspath`, 218 artefak test, dan 122 artefak build berhasil diverifikasi dari `.pom` aktualnya. Namun **15 artefak Google runtime** (`googleid`, `firebase-auth`, `firebase-auth-interop`, `play-services-*`, `recaptcha`, `core-common`, `integrity`) ditandai **`NEEDS REVIEW`** karena berlisensi proprietary Google SDK/Play ToS (bukan Apache 2.0) sehingga membutuhkan kepatuhan ToS khusus.

---

## 9. Perbaikan Dokumentasi yang Dilakukan pada CP010I

Perbaikan minimal berbasis bukti dilakukan **hanya** pada dokumen audit & notice berikut (tanpa menulis ulang keseluruhan isi lama dan tanpa mengubah draf klausul komersial/komunitas secara sepihak):
1. **`THIRD-PARTY-NOTICES.md`**:
   - Memperbaiki klasifikasi keliru sebelumnya: memindahkan `com.google.android.libraries.identity.googleid:googleid:1.1.1` dan `com.google.firebase:firebase-auth:25.0.0` (+ `firebase-auth-interop`) dari kolom Apache 2.0 ke **`Android Software Development Kit License`**.
   - Menambahkan seksi/tabel akurat untuk seluruh kelompok dependensi **transitif runtime yang masuk APK** (`166` artefak release / `173` debug): `play-services-*` & `recaptcha` (**Android SDK License**), `core-common` & `integrity` (**Play Core / Play Integrity ToS**), `protobuf-javalite` & `datastore-preferences-external-protobuf` (**BSD-3-Clause**), `re2j` (**Go License**), `animal-sniffer-annotations` (**MIT**), serta `datastore*`, `grpc*`, `guava`, `okio`, `gson` (**Apache 2.0**).
   - Menambahkan catatan detail dependensi transitif test-only (`javax.annotation-api` CDDL+GPLv2+CE, `icu4j` Unicode-3.0, `bcprov` test) dan build-time (`jna` LGPL 2.1 OR Apache 2.0, `juniversalchardet` MPL 1.1) agar transparan tanpa dicampur ke lisensi APK.
2. **`IP-OWNERSHIP-REGISTER.md`**:
   - Memutakhirkan **Kategori C** agar memisahkan `firebase-auth` & `googleid` sebagai **`Android SDK License`** serta mencantumkan artefak transitif non-Apache (`BSD-3-Clause`, `Go License`, `MIT`, `Play SDK ToS`).
3. **`docs/INTELLECTUAL_PROPERTY_AUDIT.md`**:
   - Menambahkan catatan pemutakhiran `CP010I` di banner atas mengenai koreksi lisensi POM (`Android SDK License`, `BSD-3-Clause`, `Go License`, `MIT`, `Play ToS`) dan status `NOT COMMERCIAL READY`.
4. **`docs/CHECKPOINT_INDEX.md`** & **`docs/PROJECT_STATE.md`**:
   - Menambahkan entri `CP010I` sambil mempertahankan seluruh sejarah `CP000`–`CP010H`.
5. **Baru**: `Deliverables/CP010I-LICENSE-COMMERCIAL-READINESS-REPORT.md`.

*(Catatan: `LICENSE`, `COPYRIGHT.md`, `COMMUNITY-LICENSE.md`, `COMMERCIAL-LICENSE.md`, `EULA.md`, `TRADEMARK-NOTICE.md`, `CONTRIBUTING.md`, dan `LICENSE-FAQ.md` **TIDAK** diubah secara sepihak karena penyelesaian konflik klausul menunggu keputusan Dhedry Afriazoni.)*

---

## 10. Daftar Keputusan yang Membutuhkan Persetujuan Pengguna

Sebelum menetapkan versi final (non-DRAFT) lisensi dan rantai kepemilikan, keputusan berikut memerlukan persetujuan eksplisit **Dhedry Afriazoni**:
1. **Batasan Evaluasi Perusahaan di `COMMUNITY-LICENSE.md` vs `LICENSE` (LC-01)**: Apakah perusahaan komersial diperbolehkan memakai jalur Community sebatas evaluasi non-produksi, atau jalur Community murni hanya untuk individu/nirlaba/akademik (sehingga evaluasi bisnis membutuhkan izin/lisensi terpisah)?
2. **Batas Objektif Non-Komersial (LC-03)**: Apakah proyek open/community dengan donasi sukarela, atau pemakaian internal pekerja lepas, diizinkan di bawah `COMMUNITY-LICENSE.md` atau dikategorikan sebagai penggunaan komersial?
3. **Model Distribusi Community (LC-02)**: Apakah Community Edition akan didistribusikan sebagai *source-available* (di GitHub) + APK gratis, atau hanya APK gratis tanpa membuka kode sumber penuh `com.fms.*`?
4. **Nasib `public/assets/aistudio/`**: Apakah Dhedry memiliki hak penuh atas mockup gambar dan teks di `public/assets/aistudio/`, ataukah folder tersebut harus dikeluarkan sebelum inisialisasi repositori Git publik/komersial?
5. **Penetapan SPDX Identifier & Header di `CP010J`+**: Persetujuan penggunaan identifier kustom `LicenseRef-Dhedry-Proprietary` (atau varian Dual License) hanya pada berkas kode kustom setelah inisialisasi Git baseline dan peninjauan kode oleh Dhedry.
6. **Penetralan Referensi `App Cloner` (`FmsParityCatalog.kt`) & Penambahan Layar Notice In-App**: Persetujuan untuk menghapus/mengganti nama merek pesaing dari kode/UI yang masuk APK serta menambahkan layar *Open-Source & Third-Party Licenses* di menu Pengaturan sebelum distribusi komersial.

---

## 11. Rekomendasi Tahap Berikutnya

Urutan eksekusi bertahap yang disarankan setelah persetujuan pengguna:
1. **Keputusan Kebijakan Lisensi & Aset Input**: Dhedry menentukan 6 poin keputusan pada Bagian 10 (termasuk status `public/assets/aistudio/`).
2. **Inisialisasi Git & Penguatan Rantai Bukti**: Membersihkan starter boilerplate tidak terpakai (`Theme.MyApplication`, `colors.xml`, `ui/theme/*`), melakukan tinjauan manusia atas modul `com.fms.*` & ZClone, memasang header copyright + `LicenseRef-Dhedry-Proprietary` pada berkas layak, lalu membuat repositori Git dengan signed commit baseline.
3. **Sinkronisasi Final Dokumen Lisensi**: Mengubah seluruh draf (`LICENSE`, `COPYRIGHT.md`, `COMMUNITY-LICENSE.md`, `COMMERCIAL-LICENSE.md`, `EULA.md`, `LICENSE-FAQ.md`) dari status `DRAFT` menjadi teks final yang konsisten dan ditinjau penasihat hukum, serta menambahkan *Privacy Policy* dan layar in-app Open-Source Notices.
4. **Pembersihan Pra-Rilis Komersial**: Meninjau `FmsParityCatalog.kt`, memisahkan proyek Firebase khusus rilis komersial, dan menandatangani APK/AAB menggunakan keystore rilis privat milik Dhedry (bukan `debug.keystore`).

---

## 12. Status Akhir dan Alasan

- **Status Akhir `CP010I`**: **`PARTIALLY VERIFIED` — `NOT COMMERCIAL READY / NOT LEGALLY CLEARED`**.
- **Alasan**:
  1. Seluruh **166 artefak runtime release / 173 debug / 218 test / 122 build** kini **telah diaudit 100% dari laporan Gradle dan berkas `.pom` aktualnya**, dan tidak ditemukan dependensi copyleft GPL/AGPL yang masuk ke APK rilis.
  2. Namun proyek **belum dapat dinyatakan `LEGALLY CLEARED` atau `COMMERCIAL READY`** karena: (a) seluruh kode sumber & aset vektor kustom masih berstatus **`OWNERSHIP UNVERIFIED`** (tanpa `.git` & bukti penciptaan manusia), (b) terdapat **konflik klausul di draf lisensi** yang menunggu keputusan Dhedry, (c) berkas `public/assets/aistudio/` masih berstatus **`PENDING`**, (d) aplikasi APK belum memiliki penampil notice pihak ketiga in-app (`Apache 2.0`, `BSD-3-Clause`, `Go License`, `MIT`, `SIL OFL 1.1`), dan (e) ketergantungan SDK Google proprietary (`Android SDK License`, `Play Core/Integrity ToS`) serta merek komparatif di `FmsParityCatalog.kt` masih memerlukan penyelesaian pra-komersialisasi.
