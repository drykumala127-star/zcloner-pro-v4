# DRAFT — DHEDRY COMMUNITY LICENSE (v1.1-DRAFT)

> **STATUS DOKUMEN: DRAFT — Kebijakan Tersinkron (`CP010J`)**  
> *(Menunggu bukti Git baseline dan tinjauan hukum final sebelum pemberlakuan komersial ekosistem. Modul kode/aset tanpa bukti memadai tetap berstatus `OWNERSHIP UNVERIFIED`.)*

Copyright © 2026 Dhedry Afriazoni. All Rights Reserved.

---

## 1. Definisi

- **"Pemberi Lisensi"** adalah **Dhedry Afriazoni**.
- **"Perangkat Lunak"** adalah komponen kode sumber (pada jalur *source-available* GitHub) dan/atau biner APK gratis **ZClone**, **FMS Full Custom Ecosystem (`com.fms.*`)**, dan **FMSCloneEngine** yang merupakan komponen kustom ekosistem Pemberi Lisensi sebagaimana dicatat dalam `IP-OWNERSHIP-REGISTER.md` (tidak mencakup komponen pihak ketiga yang tunduk pada lisensinya sendiri dan tidak mencakup berkas yang dikecualikan seperti `public/assets/aistudio/`).
- **"Pengguna Community"** adalah **individu pribadi, organisasi nirlaba (nonprofit), atau institusi akademik/riset** yang menggunakan Perangkat Lunak secara **nonkomersial**.
  - **Pengecualian Tegas Perusahaan**: Perusahaan komersial atau entitas bisnis berorientasi laba **TIDAK** termasuk Pengguna Community dan **tidak otomatis memperoleh hak evaluasi** melalui lisensi ini. Evaluasi internal oleh perusahaan memerlukan **izin tertulis terpisah** dari Pemberi Lisensi atau `COMMERCIAL-LICENSE.md`.

---

## 2. Pemberian Hak (Community Edition — Source-Available & APK Gratis)

Tunduk pada seluruh persyaratan dalam lisensi ini, Pemberi Lisensi memberikan lisensi non-eksklusif, tidak dapat dipindahtangankan, bebas royalti, dan dapat dicabut apabila terjadi pelanggaran, kepada Pengguna Community untuk:

1. **Menggunakan** APK gratis ZClone dan/atau kode sumber *source-available* secara **nonkomersial** untuk keperluan pribadi, pembelajaran, riset akademik, dan proyek organisasi nirlaba.
2. **Mempelajari dan Memodifikasi** kode sumber resmi dari repositori GitHub untuk kebutuhan nonkomersial atau kontribusi kembali ke repositori resmi (tunduk pada `CONTRIBUTING.md`, termasuk kewajiban CLA tertulis sebelum *merge*).
3. **Mendistribusikan Ulang Fork atau APK Hasil Modifikasi secara Nonkomersial**, dengan syarat seluruh ketentuan berikut dipenuhi tanpa kecuali:
   - Distribusi dilakukan sepenuhnya secara **gratis dan nonkomersial**;
   - Anda **wajib menggunakan nama produk dan ikon aplikasi milik Anda sendiri** agar tidak membingungkan pengguna atau menyiratkan rilis resmi/dukungan dari Dhedry Afriazoni (`TRADEMARK-NOTICE.md`);
   - Anda **wajib menandai secara jelas** setiap modifikasi yang Anda buat serta mempertahankan secara utuh pemberitahuan hak cipta (`COPYRIGHT.md`), teks `COMMUNITY-LICENSE.md`, dan `THIRD-PARTY-NOTICES.md`;
   - Anda **dilarang menyertakan** direktori `public/assets/aistudio/` yang dikecualikan dari distribusi publik sebelum asal-usul dan hak penggunaannya dipastikan.

---

## 3. Batas Penggunaan Komersial vs Nonkomersial

Penggunaan di bawah jalur Community **wajib sepenuhnya nonkomersial**:
1. **Pekerjaan Freelancer & Proyek Klien (Komersial)**: Pekerjaan *freelancer* berbayar, proyek agensi, atau pembuatan/modifikasi aplikasi maupun modul `com.fms.*` untuk klien termasuk **penggunaan komersial** dan **wajib memiliki `COMMERCIAL-LICENSE.md` tertulis**.
2. **Donasi Murni vs Sponsorship Bisnis**:
   - Penerimaan **donasi sukarela tanpa imbalan bisnis** (tanpa akses fitur berbayar, tanpa kewajiban layanan bisnis, dan tanpa promosi komersial bagi penyumbang) **tidak otomatis dianggap penggunaan komersial**.
   - Penerimaan **sponsorship yang memberikan manfaat promosi atau manfaat bisnis** (misalnya penempatan merek komersial sponsor, promosi produk/jasa komersial, atau manfaat bisnis lain) **dapat memerlukan `COMMERCIAL-LICENSE.md` tertulis** apabila penggunaannya masuk ruang lingkup komersial.
3. **Evaluasi Bisnis & Komersialisasi**: Perusahaan tidak otomatis berhak mengevaluasi kode atau APK melalui Community License; seluruh penjualan aplikasi, monetization/iklan, SaaS berbayar, atau bundling produk bisnis wajib menggunakan `COMMERCIAL-LICENSE.md` tertulis.

---

## 4. Pemisahan Lisensi APK vs Source Code & Larangan

- **Perbedaan Ketentuan APK dan Source Code**: Penggunaan biner APK terkompilasi juga tunduk pada `EULA.md`. Ketentuan biner APK (seperti larangan *reverse engineering* atas APK resmi terkompilasi) dapat berbeda dari hak mempelajari/memodifikasi kode sumber *source-available* yang disediakan di repositori resmi GitHub.
- Pengguna Community **DILARANG**:
  1. Menghapus, mengubah, atau mengaburkan pemberitahuan hak cipta `© Dhedry Afriazoni`, status register/audit, atau atribusi pihak ketiga.
  2. Mengklaim karya kustom Dhedry sebagai karya asli Anda, atau menyatakan kepemilikan atas nama merek **ZClone**, **FMS**, **FMSCloneEngine**, atau **Dhedry**.
  3. Memasukkan `public/assets/aistudio/` ke paket distribusi publik atau fork mana pun.

---

## 5. Hak & Kewajiban Komponen Pihak Ketiga

Hak dan kewajiban lisensi komponen pihak ketiga (termasuk **AndroidX**, **Compose**, **Kotlin**, `Apache 2.0`, `BSD-3-Clause`, `Go License`, `MIT`, **Android SDK License** pada `googleid`/`firebase-auth`/`play-services-*`, **Play Core/Integrity ToS**, **Firebase Terms of Service**, serta font **SIL OFL 1.1**) **tetap berlaku penuh** (`THIRD-PARTY-NOTICES.md`). Tidak ada ketentuan dalam `COMMUNITY-LICENSE.md` yang mengklaim komponen pihak ketiga sebagai milik Dhedry atau membatasi lisensi terbuka pihak ketiga.

---

## 6. Pengakhiran & Hukum yang Berlaku

- Hak dalam lisensi ini berakhir secara otomatis apabila terjadi pelanggaran ketentuan di atas.
- Lisensi ini diatur dan ditafsirkan berdasarkan **Hukum Republik Indonesia**.
