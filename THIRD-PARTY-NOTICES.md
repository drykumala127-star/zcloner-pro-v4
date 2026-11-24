# DRAFT — THIRD-PARTY NOTICES & ATTRIBUTION

> **STATUS DOKUMEN: DRAFT — Kebijakan Tersinkron (`CP010J`) berdasarkan audit POM Gradle aktual `CP010I`**  
> *(`166` artefak `releaseRuntimeClasspath`, `173` `debugRuntimeClasspath`, `218` `debugUnitTestRuntimeClasspath`, `122` `buildEnvironment`; menunggu pemasangan penampil notice in-app dan tinjauan hukum final.)*

ZClone (`com.fms.zclone.enginecoremode`) dan **FMS Full Custom Ecosystem** menggunakan komponen pihak ketiga berikut. **Tidak ada satu pun komponen Google, library open-source, font, SDK, atau toolchain di bawah ini yang diklaim sebagai milik Dhedry Afriazoni.** Hak cipta masing-masing komponen tetap berada pada pencipta dan pemegang haknya, dan **seluruh hak serta kewajiban lisensi pihak ketiga tetap berlaku penuh** baik pada jalur `COMMUNITY-LICENSE.md` maupun `COMMERCIAL-LICENSE.md`.

---

## 1. Library Runtime yang Masuk ke APK (`releaseRuntimeClasspath` = 166 Artefak)

### 1A. Dependensi Langsung & Kelompok Apache 2.0 Utama

| Komponen / Artefak | Versi Terselesaikan | Pemegang Hak / Sumber | Lisensi Aktual (POM) | Kewajiban Atribusi & Catatan |
| :--- | :--- | :--- | :--- | :--- |
| `androidx.core:core-ktx` + transitif `androidx.core:core`, `annotation`, `collection`, `profileinstaller`, `startup-runtime`, `vectordrawable`, dll. | `1.18.0` | The Android Open Source Project (Google) | Apache License 2.0 | Pertahankan notice Apache 2.0 dalam distribusi. |
| `androidx.activity:activity-compose` (+ `activity`, `activity-ktx`, `savedstate`) | `1.10.1` | The Android Open Source Project | Apache License 2.0 | Pertahankan notice Apache 2.0. |
| `androidx.lifecycle:lifecycle-runtime-ktx`, `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` (+ transitif `lifecycle-*`) | `2.8.7` | The Android Open Source Project | Apache License 2.0 | Pertahankan notice Apache 2.0. |
| `androidx.compose:compose-bom` (`ui`, `ui-graphics`, `ui-tooling-preview`, `material3`, `material-icons-core`, `material-icons-extended`, + ~30 transitif `foundation`, `animation`, `runtime:1.7.1`, `material-ripple`) | `2024.09.00` (`ui 1.7.0`, `m3 1.3.0`) | The Android Open Source Project | Apache License 2.0 | Pertahankan notice Apache 2.0. Material Icons tunduk Apache 2.0. |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android`, `kotlinx-coroutines-core` (+ `coroutines-bom`, `coroutines-core-jvm`, `coroutines-play-services`) | `1.10.2` | JetBrains s.r.o. | Apache License 2.0 | Pertahankan notice Apache 2.0 & NOTICE file. |
| `org.jetbrains.kotlin:kotlin-stdlib` (+ `kotlin-stdlib-common`, `jdk7/8`, `kotlin-parcelize-runtime`, `annotations`, `jspecify`) | `2.4.21` | JetBrains s.r.o. | Apache License 2.0 | Pertahankan notice Apache 2.0. |
| `com.google.firebase:firebase-bom` (`firebase-firestore`, `firebase-common`, `firebase-components`, `firebase-annotations`, `firebase-appcheck-interop`, `firebase-database-collection`, `protolite-well-known-types`) | `35.1.0` (`firestore 27.1.0`) | Google LLC | Apache License 2.0 (SDK) + Google/Firebase Terms | Pertahankan notice Apache 2.0 untuk komponen SDK terbuka ini. |
| `androidx.credentials:credentials`, `credentials-play-services-auth` (+ transitif `androidx.datastore:datastore*` `1.1.7`, `biometric`, `browser`, `fragment`) | `1.5.0` | Google / AOSP | Apache License 2.0 | Pertahankan notice Apache 2.0 (`datastore*` terbawa transitif oleh Credential Manager). |
| `com.google.firebase:firebase-auth` (langsung) & `firebase-auth-interop` (transitif) | `25.0.0` / `20.0.0` | Google LLC | **Android Software Development Kit License** *(Bukan Apache 2.0)* | Tunduk ketentuan resmi Android SDK & Firebase ToS; tidak untuk dipisah di luar ketentuan Google SDK. |
| `com.google.android.libraries.identity.googleid:googleid` | `1.1.1` | Google LLC | **Android Software Development Kit License** *(Bukan Apache 2.0)* | Tunduk Android SDK License; koreksi dari draf `CP010F`. |

### 1B. Dependensi Transitif Non-Apache & Kelompok Transitif Lain yang Masuk APK

| Komponen / Artefak Transitif | Versi Terselesaikan | Ditarik Oleh | Lisensi Aktual (POM) | Kewajiban Atribusi & Catatan |
| :--- | :--- | :--- | :--- | :--- |
| `com.google.android.gms:play-services-{auth,auth-api-phone,auth-base,auth-blockstore,base,basement,fido,identity-credentials,tasks}` & `com.google.android.recaptcha:recaptcha` | `16.0.0-alpha02`–`21.1.1` / `18.6.1` | `credentials-play-services-auth`, `firebase-auth` | **Android Software Development Kit License** | Ketentuan SDK Google Play Services & reCAPTCHA (`https://developer.android.com/studio/terms.html`). |
| `com.google.android.play:core-common` & `com.google.android.play:integrity` | `2.0.3` / `1.3.0` | `firebase-auth` / `recaptcha` | **Play Core SDK ToS** & **Play Integrity API ToS** | Tunduk Google Play SDK Terms of Service. |
| `com.google.protobuf:protobuf-javalite` & `androidx.datastore:datastore-preferences-external-protobuf` | `4.36.1` / `1.1.7` | `firebase-firestore`, `datastore-preferences-core` | **BSD-3-Clause** | **Wajib** sertakan teks/copyright notice BSD-3-Clause pada distribusi source & aplikasi. |
| `com.google.re2j:re2j` | `1.6` | `recaptcha` / `firebase-auth` | **Go License** (BSD-style) | **Wajib** sertakan notice hak cipta & lisensi Go License. |
| `org.codehaus.mojo:animal-sniffer-annotations` | `1.27` | `guava` (`firebase-firestore`) | **MIT License** | **Wajib** sertakan notice MIT License. |
| `io.grpc:grpc-*` (`1.84.0`), `perfmark-api:0.27.0`, `com.squareup.okio:okio*:3.4.0`, `com.google.guava:guava:33.6.0-android` (+ `failureaccess`, `listenablefuture`), `gson:2.14.0`, `jsr305:3.0.2`, `error_prone_annotations:2.50.0`, `j2objc-annotations:3.1`, `javax.inject:1` | Lihat versi samping | `firebase-firestore` | **Apache License 2.0** | Pertahankan notice Apache 2.0 & file NOTICE penyertanya. |

*(Catatan: Build debug juga menyertakan 7 artefak debug-only Compose `ui-tooling*` & `material*` berlisensi **Apache 2.0** yang tidak masuk ke APK `release`.)*

---

## 2. Font yang Dibundel di dalam APK (`app/src/main/res/font/`)

| File Font | Nama Font | Pemegang Hak / Penulis | Lisensi | Kewajiban Lisensi |
| :--- | :--- | :--- | :--- | :--- |
| `space_grotesk.ttf` | Space Grotesk | Florian Karsten (Google Fonts) | SIL Open Font License 1.1 (OFL 1.1) | Boleh dibundel & dipakai komersial dalam APK; **dilarang dijual terpisah sebagai file font**; wajib sertakan teks/atribusi OFL 1.1. |
| `plus_jakarta_sans.ttf` | Plus Jakarta Sans | Tokotype Studio (Google Fonts) | SIL Open Font License 1.1 (OFL 1.1) | Sama seperti di atas (OFL 1.1). |
| `jetbrains_mono.ttf` | JetBrains Mono | JetBrains s.r.o. (Google Fonts) | SIL Open Font License 1.1 (OFL 1.1) | Sama seperti di atas (OFL 1.1). |

---

## 3. Dependensi Pengujian (Test-Only — Tidak Masuk APK Rilis)

| Komponen | Versi | Lisensi (POM) | Catatan |
| :--- | :--- | :--- | :--- |
| `junit:junit` (+ transitif `org.hamcrest:hamcrest-*` `1.3`) | `4.13.2` | EPL 1.0 (`junit`) & New BSD (`hamcrest`) | Hanya `testImplementation`. |
| `org.robolectric:robolectric` (+ 12 sub-modul `org.robolectric:*`) | `4.16.1` | MIT License | Hanya `testImplementation`. |
| `androidx.test:core` (`1.7.0`), `androidx.test.ext:junit` (`1.3.0`), `runner` (`1.6.2`), `espresso-core` (`3.7.0`), `ui-test-junit4` (`1.7.0`), `kotlinx-coroutines-test` (`1.10.2`) | Lihat versi samping | Apache License 2.0 | Hanya untuk pengujian unit/instrumentasi. |
| Transitif Robolectric (`javax.annotation:javax.annotation-api:1.3.2`, `icu4j:77.1`, `bcprov-jdk18on:1.81`, `asm*:9.8`) | Lihat versi samping | **CDDL + GPLv2+CE** (`javax.annotation`), Unicode-3.0, Bouncy Castle Licence, BSD-3-Clause | Hanya dijalankan di JVM test (`debugUnitTestRuntimeClasspath`); **tidak dikompilasi ke APK rilis**. |

---

## 4. Plugin Build & Toolchain Eksternal (`buildEnvironment` = 122 Artefak, Build-Time Only)

| Komponen | Versi | Lisensi / Ketentuan (POM) | Masuk APK? |
| :--- | :--- | :--- | :--- |
| Android Gradle Plugin (`9.1.1`), Kotlin Compose Compiler Plugin (`2.4.21`), KSP (`2.3.5`), Secrets Plugin (`2.0.1`), Google Services (`4.5.0`), Gradle Wrapper (`9.3.1`), + ~94 artefak transitif build | Lihat versi samping | Apache License 2.0 | Tidak (build-time only). |
| Transitif Build AGP (`jna`/`jna-platform:5.6.0`, `juniversalchardet:1.0.3`, `bcprov/bcpkix/bcutil:1.79`, `asm*:9.9`, `protobuf-java*:3.25.5`, `jaxb-runtime/jakarta:2.3.2`, `kxml2:2.3.0`, `jdom2:2.0.6`, `jopt-simple:4.9`, `slf4j-api:1.7.30`) | Lihat versi samping | Dual **LGPL 2.1 OR Apache 2.0** (`jna`), **MPL 1.1** (`juniversalchardet`), Bouncy Castle, BSD-3-Clause, EDL 1.0, BSD/PD, MIT | Tidak (hanya berjalan di host build Gradle; tidak masuk APK). |
| Android SDK (`compileSdk 36`, `aapt2`, `apksigner`) | API 36 / Build Tools 36.0.0 | Android SDK License Agreement | Tidak. |
| Firebase Cloud Firestore & Google Identity Backend | Layanan SaaS | Google APIs / Firebase Terms of Service | Layanan eksternal. |

---

## 5. Dependensi yang Dikomentari / Tidak Aktif (`app/build.gradle.kts`)
Daftar berikut ada di `gradle/libs.versions.toml` dan dikomentari di `app/build.gradle.kts` sehingga **tidak aktif sebagai dependensi langsung**: `room`, `navigation-compose`, `coil-compose`, `retrofit`, `converter-moshi`, `moshi`, `okhttp`, `logging-interceptor`, `datastore-preferences` *(catatan: versi `1.1.7` masih terbawa secara transitif via `androidx.credentials`)*, `accompanist-permissions`, `play-services-location`, `camera-*`, `firebase-ai`, `firebase-appcheck-*`.
