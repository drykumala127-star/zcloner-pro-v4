# DRAFT — LICENSE FAQ (PENGGUNA, PEMBELI, KONTRIBUTOR & DISTRIBUTOR)

> **STATUS DOKUMEN: DRAFT — Kebijakan Tersinkron (`CP010J`)**  
> *(Menunggu bukti Git baseline, pembersihan merek komparatif & layar atribusi in-app, serta tinjauan hukum final. Proyek saat ini **BELUM `LEGALLY CLEARED`** atau siap jual.)*

---

## A. Untuk Pengguna Jalur Community (`COMMUNITY-LICENSE.md`)

1. **Siapa yang boleh memakai Community Edition secara gratis?**
   - Community Edition (kode *source-available* di GitHub dan APK gratis) hanya ditujukan untuk penggunaan **nonkomersial oleh individu pribadi, organisasi nirlaba (nonprofit), dan institusi akademik/riset**.
2. **Bolehkah perusahaan mengevaluasi ZClone atau modul `com.fms.*` lewat Community License?**
   - **Tidak otomatis.** Perusahaan/entitas bisnis tidak otomatis mendapat hak evaluasi melalui `COMMUNITY-LICENSE.md`. Evaluasi perusahaan memerlukan **izin tertulis terpisah** dari Dhedry Afriazoni atau `COMMERCIAL-LICENSE.md`.
3. **Apakah pekerjaan *freelancer* atau proyek untuk klien boleh memakai jalur Community?**
   - **Tidak.** Pekerjaan *freelancer* berbayar atau proyek untuk klien termasuk **penggunaan komersial** dan wajib memperoleh `COMMERCIAL-LICENSE.md` tertulis.
4. **Bagaimana dengan proyek komunitas yang menerima donasi atau sponsorship?**
   - **Donasi sukarela tanpa imbalan bisnis** (tanpa akses fitur berbayar, tanpa jasa komersial, dan tanpa promosi bisnis) **tidak otomatis dianggap komersial**.
   - Sebaliknya, **sponsorship yang memberikan manfaat promosi atau manfaat bisnis** **dapat memerlukan `COMMERCIAL-LICENSE.md`** apabila penggunaannya masuk ruang lingkup komersial.
5. **Bolehkah saya memodifikasi dan mendistribusikan ulang *fork* atau APK Community?**
   - **Boleh, khusus untuk distribusi gratis dan nonkomersial**, dengan syarat Anda wajib menggunakan **nama produk dan ikon sendiri** (`TRADEMARK-NOTICE.md`), menandai perubahan Anda secara jelas, mempertahankan seluruh pemberitahuan hak cipta (`COPYRIGHT.md`), `COMMUNITY-LICENSE.md`, dan `THIRD-PARTY-NOTICES.md`, serta **tidak menyertakan** direktori `public/assets/aistudio/`.
6. **Bolehkah saya merilis versi modifikasi ke Google Play Store dengan iklan atau fitur berbayar, atau menjual file font `.ttf`?**
   - **Tidak boleh.** Monetisasi/iklan/penjualan aplikasi memerlukan **Commercial License** tertulis, sedangkan ketiga font (`space_grotesk.ttf`, `plus_jakarta_sans.ttf`, `jetbrains_mono.ttf`) tunduk pada **SIL OFL 1.1** yang melarang keras penjualan file font secara terpisah.

## B. Untuk Pembeli Lisensi Komersial (`COMMERCIAL-LICENSE.md`)

7. **Apa beda lisensi APK vs lisensi Source Code?**
   - **Lisensi APK dan source code dapat memiliki ketentuan berbeda.** *Commercial APK License* (bersama `EULA.md`) hanya memberi hak instalasi/distribusi biner terkompilasi sesuai kuota kontrak tanpa akses atau hak modifikasi source code. *Commercial Source Code & SDK Embedding License* memberi hak akses, modifikasi, dan integrasi source code ke produk pemegang lisensi sesuai paket kontrak.
8. **Jika saya membeli lisensi komersial, apakah hak cipta berpindah kepada saya?**
   - **Tidak.** Pembelian lisensi tidak memindahkan atau mengalihkan hak cipta. Hak cipta atas karya orisinal tetap sepenuhnya berada pada **Dhedry Afriazoni**.
9. **Apakah seluruh berkas di repositori ini 100% milik Dhedry Afriazoni dan sudah siap dijual (*legal-clear*)?**
   - **Belum.** Pertama, komponen Google (`Android SDK License`, `Firebase ToS`, `Play Core/Integrity ToS`), library open-source (`Apache 2.0`, `BSD-3-Clause`, `Go License`, `MIT`), font (`SIL OFL 1.1`), serta bahasa pemrograman/sintaks/protokol standar/algoritma umum/istilah teknis netral **bukan milik Dhedry**. Kedua, aset raster AI lama telah digantikan oleh `VectorDrawable` XML pada `CP010H`, tetapi seluruh kode dan aset kustom saat ini masih berstatus **`OWNERSHIP UNVERIFIED`** (menunggu `.git` & bukti penciptaan manusia), `public/assets/aistudio/` masih berstatus **`PENDING`** dan dilarang masuk distribusi publik, serta penetralan merek komparatif dan layar atribusi in-app masih harus diselesaikan sebelum dinyatakan siap jual.

## C. Untuk Kontributor & Distributor (`CONTRIBUTING.md` & `EULA.md`)

10. **Apa syarat menyumbangkan Pull Request ke repositori resmi?**
    - Kontributor **wajib menandatangani CLA tertulis sebelum *merge*** (`CONTRIBUTING.md`), dilarang menyalin kode pihak ketiga/GPL/AGPL tanpa izin, dan dilarang memasukkan aset yang belum jelas asal-usulnya (`public/assets/aistudio/`).
11. **Kewajiban apa yang tetap melekat pada setiap distribusi APK atau source?**
    - Distributor wajib mematuhi seluruh kewajiban lisensi pihak ketiga di `THIRD-PARTY-NOTICES.md` (`Apache 2.0`, `BSD-3-Clause`, `Go License`, `MIT`, `SIL OFL 1.1`, `Android SDK License`, `Play Core/Integrity ToS`), `EULA.md`, dan ketentuan layanan Google/Firebase. Sengketa dan penafsiran mengacu pada **Hukum Republik Indonesia**.
