# DRAFT — COPYRIGHT STATEMENT

> **STATUS DOKUMEN: DRAFT — Kebijakan Tersinkron (`CP010J`)**  
> *(Menunggu pembentukan riwayat Git baseline, peninjauan kode manusia, dan tinjauan hukum final. Proyek ini **BELUM `LEGALLY CLEARED`** atau siap jual.)*

**Copyright © 2026 Dhedry Afriazoni. All Rights Reserved.**

---

## 1. Pernyataan Pemegang Hak Cipta & Hak Penggunaan Pemilik

Seluruh modul kode sumber, arsitektur sistem, spesifikasi desain internal, aturan keamanan, skrip pengujian, dan dokumentasi teknis yang secara faktual diciptakan secara orisinal untuk ekosistem **ZClone**, **FMS Full Custom Ecosystem (`com.fms.*`)**, dan **FMSCloneEngine** dimiliki hak ciptanya oleh **Dhedry Afriazoni**, sepanjang memenuhi ketentuan hukum hak cipta yang berlaku dan didukung rantai bukti kepemilikan (*chain of authorship*).

Sepanjang kepemilikan orisinal tersebut terbukti sah, **Dhedry Afriazoni** berhak penuh untuk:
- Menggunakan dan menjalankan;
- Mengubah, memperbaiki, dan mengembangkan;
- Menggunakan ulang pada proyek lain;
- Menggabungkan dan membuat produk turunan; serta
- Menjual, melisensikan, dan mendistribusikan secara komersial.

**Pemisahan Hak Cipta vs Hak Guna Lisensi**: Pemberian lisensi di bawah `COMMUNITY-LICENSE.md` maupun pembelian lisensi di bawah `COMMERCIAL-LICENSE.md` **tidak memindahkan atau mengalihkan hak cipta** dari Dhedry Afriazoni kepada pengguna atau pembeli lisensi.

---

## 2. Pemisahan Tegas Hak Cipta & Status Bukti Saat Ini

Agar tidak terjadi klaim berlebihan (*overclaiming*), pemisahan lima kategori berikut berlaku secara mutlak:

1. **Kandidat Karya Orisinal Dhedry (Saat Ini Berstatus `OWNERSHIP UNVERIFIED`)**:
   - Modul library `com.fms.core`, `com.fms.security`, `com.fms.data`, `com.fms.ui`, `com.fms.task`, `com.fms.cloud`, `com.fms.device`, dan `com.fms.clone`; kode aplikasi `com.fms.zclone.enginecoremode.*` (selain file starter template); `firestore.rules`, `firestore.test.js`, `firebase-blueprint.json`; 5 aset grafis `VectorDrawable` XML & 10 bitmap fallback deterministik hasil `CP010H`; serta dokumentasi proyek di `/docs/` dan `/Deliverables/`.
   - **Status Bukti Faktual**: Karena workspace saat ini belum memiliki repositori `.git`, riwayat commit penulisan manusia, maupun persetujuan final header kode, seluruh berkas kustom di atas wajib tetap ditulis **`OWNERSHIP UNVERIFIED`** dalam `IP-OWNERSHIP-REGISTER.md` dan `docs/INTELLECTUAL_PROPERTY_AUDIT.md` hingga bukti penciptaan dilengkapi.

2. **Komponen Google & Pihak Ketiga (Bukan Milik Dhedry Afriazoni)**:
   - Seluruh SDK dan layanan Google (`googleid`, `firebase-auth`, `play-services-*`, `recaptcha` di bawah *Android SDK License*; `play:core-common` dan `play:integrity` di bawah *Play Core / Play Integrity ToS*; layanan Cloud Firestore & Auth di bawah *Google/Firebase ToS*).
   - Seluruh library open-source (`Apache 2.0`, `BSD-3-Clause`, `Go License`, `MIT`, `EPL 1.0`, dll.) dan font Google Fonts (`SIL OFL 1.1`). Hak cipta tetap pada pencipta masing-masing dan seluruh kewajiban lisensinya tetap berlaku (`THIRD-PARTY-NOTICES.md`).

3. **Konsep Teknis Netral (Tidak Diklaim Eksklusif oleh Dhedry)**:
   - Bahasa pemrograman (Kotlin, Java), sintaks bahasa, protokol dan format standar (ZIP, JAR, PKCS#7, ASN.1 DER, SQL, HTTP/gRPC, format biner Android AXML), algoritma umum (AES-GCM, SHA-256, LRU cache), pola desain umum (MVVM, Repository, EventBus, Job Queue), dan istilah teknis netral **bukan** objek kepemilikan eksklusif Dhedry Afriazoni.

4. **Aset dengan Status Belum Dipastikan & Starter Boilerplate**:
   - **Aset Input `public/assets/aistudio/`**: Berstatus **`PENDING` / `BELUM DIKETAHUI`** karena asal-usul pencipta asli belum terverifikasi. Folder ini **tidak boleh dimasukkan ke distribusi publik** (baik repositori Community maupun paket komersial) sebelum asal-usul dan hak penggunaannya dipastikan.
   - **Aset Raster AI Lama (`CP005`/`CP007`)**: `zclone_launcher_icon_*.jpg` dan `img_hero_clone_layers_*.jpg` telah dihapus dan digantikan pada `CP010H`; aset `VectorDrawable` penggantinya tetap berstatus `OWNERSHIP UNVERIFIED` sampai bukti Git diselesaikan.
   - **Starter Template**: Berkas bawaan template Android/AI Studio (`ui/theme/{Color,Theme,Type}.kt`, `colors.xml`, `Theme.MyApplication` di `themes.xml`, `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`) bukan karya eksklusif Dhedry.

5. **Platform, Toolchain & Artefak Generated**:
   - Android SDK, Gradle, AGP, OpenJDK, layanan cloud Firebase, `debug.keystore` container, serta artefak hasil kompilasi (`BuildConfig.java`, `R`, DEX, APK) tidak dapat diklaim hak ciptanya oleh Dhedry Afriazoni.

---

## 3. Catatan Kejujuran Pendaftaran & Kesiapan Hukum

Dokumen ini **tidak menyatakan atau mengklaim** adanya pendaftaran hak cipta/merek resmi (DJKI atau USCO), dan **tidak menyatakan proyek telah `LEGALLY CLEARED` atau siap dijual** sebelum penguatan rantai bukti Git, peninjauan manusia, penetralan merek pihak ketiga pada kode katalog, dan kewajiban atribusi selesai dilaksanakan.
