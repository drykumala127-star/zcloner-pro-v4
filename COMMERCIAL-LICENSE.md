# DRAFT — DHEDRY COMMERCIAL LICENSE (v1.1-DRAFT)

> **STATUS DOKUMEN: DRAFT — Kebijakan Tersinkron (`CP010J`)**  
> *(Menunggu bukti Git baseline, pembersihan merek komparatif & layar atribusi in-app, serta tinjauan hukum final. Proyek saat ini **BELUM `LEGALLY CLEARED`** dan **BELUM SIAP DIJUAL** secara komersial.)*

Copyright © 2026 Dhedry Afriazoni. All Rights Reserved.

---

## 1. Kapan Commercial License Tertulis Diperlukan

`COMMERCIAL-LICENSE.md` berlaku bagi perusahaan, organisasi komersial, vendor, distributor, agensi, atau individu/pekerja profesional (**"Pemegang Lisensi"**) yang memperoleh izin komersial tertulis dari **Dhedry Afriazoni** (**"Pemberi Lisensi"**) melalui perjanjian resmi, faktur, atau sertifikat lisensi.

Lisensi komersial tertulis **WAJIB** dimiliki untuk seluruh aktivitas berikut:
1. **Setiap Penggunaan Komersial**: Penjualan aplikasi, langganan, SaaS, monetisasi iklan, penggunaan operasional bisnis internal, atau bundling komersial.
2. **Pekerjaan Freelancer Berbayar & Proyek Klien**: Pembuatan, modifikasi, integrasi, atau penyediaan aplikasi/modul berbasis ZClone atau `com.fms.*` untuk klien atau dengan imbalan jasa.
3. **Evaluasi Perusahaan**: Entitas bisnis **tidak** otomatis memperoleh hak evaluasi melalui `COMMUNITY-LICENSE.md`. Evaluasi perusahaan memerlukan **izin evaluasi tertulis terpisah** atau Commercial License.
4. **Sponsorship Bermuatan Promosi atau Bisnis**: Penggunaan pada proyek yang menerima sponsorship dengan imbalan promosi merek, eksposur iklan komersial, atau manfaat bisnis lain apabila penggunaannya masuk ruang lingkup komersial (berbeda dari donasi sukarela murni tanpa imbalan bisnis yang diatur di `COMMUNITY-LICENSE.md`).

---

## 2. Pemisahan Lisensi APK dan Source Code

Ketentuan lisensi untuk biner APK dan untuk kode sumber **dapat memiliki ruang lingkup serta syarat yang berbeda**, dipisahkan menjadi dua jalur resmi:

1. **Jalur A — Commercial APK / Binary Distribution License**:
   - Memberikan hak non-eksklusif kepada Pemegang Lisensi untuk menginstal internal atau mendistribusikan biner APK komersial sesuai kuota perangkat, pengguna, atau wilayah yang ditetapkan tertulis dalam kontrak (bersama ketentuan biner di `EULA.md` sepanjang tidak diubah oleh perjanjian komersial).
   - **Tidak mencakup** akses kode sumber, hak modifikasi source code, dekompilasi, atau penggunaan ulang modul `com.fms.*` di aplikasi lain.

2. **Jalur B — Commercial Source Code & SDK Embedding License**:
   - Memberikan hak kontraktual kepada Pemegang Lisensi untuk mengakses, memodifikasi, menggabungkan ke produk turunan, dan mengkompilasi ulang source code orisinal **ZClone**, modul **FMS (`com.fms.*`)**, dan/atau **FMSCloneEngine**.
   - **Penggunaan ulang lintas proyek** (*multi-app reuse*) hanya berlaku apabila secara tegas tercantum dalam paket **Enterprise / Multi-App License** tertulis.

---

## 3. Tidak Ada Pengalihan Hak Cipta

- **Pembelian lisensi komersial (baik Jalur A maupun Jalur B) TIDAK memindahkan atau mengalihkan kepemilikan hak cipta** atas karya orisinal ZClone, FMS Full Custom Ecosystem, atau FMSCloneEngine dari **Dhedry Afriazoni** kepada Pemegang Lisensi.
- Pemegang Lisensi hanya memperoleh hak penggunaan kontraktual sesuai ruang lingkup tertulis. Pemberi Lisensi tetap memegang hak penuh untuk menggunakan, mengembangkan, menggunakan ulang lintas proyek, membuat produk turunan, melisensikan ulang, dan menjual karya orisinal miliknya kepada pihak lain (kecuali disepakati hak eksklusif khusus secara tertulis).

---

## 4. Larangan & Prasyarat Sebelum Penjualan atau Rilis Komersial

1. Pemegang Lisensi **dilarang** menjual ulang, menyewakan, memsublisensikan, atau mempublikasikan source code Dhedry kepada publik atau pesaing, kecuali diizinkan secara tegas dalam **Authorized OEM / Reseller Agreement** tertulis.
2. Pemegang Lisensi **wajib** mematuhi seluruh hak dan kewajiban lisensi pihak ketiga di `THIRD-PARTY-NOTICES.md` (termasuk `Apache 2.0`, `BSD-3-Clause`, `Go License`, `MIT`, `SIL OFL 1.1` yang melarang penjualan file `.ttf` secara terpisah, `Android SDK License`, `Play Core/Integrity ToS`, dan `Firebase ToS`).
3. **Prasyarat Mutlak Sebelum Rilis/Penjualan Komersial (Saat Ini Belum Selesai)**:
   - Status modul dan aset kustom yang masih **`OWNERSHIP UNVERIFIED`** wajib diperkuat dengan riwayat Git, peninjauan manusia, dan verifikasi rantai bukti terlebih dahulu;
   - Direktori `public/assets/aistudio/` **dilarang keras** masuk ke distribusi publik maupun paket lisensi sebelum asal-usul dan hak penggunaannya dipastikan secara sah;
   - Referensi merek pihak ketiga di kode (`FmsParityCatalog.kt`), layar notice pihak ketiga di dalam APK, pemisahan proyek Firebase, dan keystore rilis privat wajib diselesaikan pada checkpoint implementasi sebelum dinyatakan siap jual.

---

## 5. Hukum yang Berlaku

Perjanjian lisensi komersial ini dan penafsiran ketentuannya tunduk pada **Hukum Republik Indonesia**, kecuali disepakati yurisdiksi khusus lain dalam kontrak komersial tertulis yang ditandatangani kedua belah pihak.
