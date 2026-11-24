# DRAFT — CONTRIBUTING GUIDELINES & CONTRIBUTOR IP AGREEMENT

> **STATUS DOKUMEN: DRAFT — Kebijakan Tersinkron (`CP010J`)**  
> *(Wajib CLA Tertulis Sebelum Merge — Menunggu Peninjauan Hukum & Inisialisasi Git Baseline; Proyek Saat Ini `OWNERSHIP UNVERIFIED` dan Belum `LEGALLY CLEARED`.)*

Terima kasih atas minat Anda berkontribusi pada **ZClone** dan **FMS Full Custom Ecosystem**. Karena ekosistem ini dikelola dengan model **Dual License (`COMMUNITY-LICENSE.md` + `COMMERCIAL-LICENSE.md`)** untuk melindungi hak pemilik (**Dhedry Afriazoni**) sekaligus kejelasan rantai kepemilikan, setiap kontributor wajib mematuhi ketentuan di bawah ini.

---

## 1. Wajib CLA Tertulis Sebelum *Merge*

Kontribusi eksternal ke repositori resmi hanya dapat diterima apabila persyaratan berikut terpenuhi **sebelum Pull Request di-*merge***:
1. **Penandatanganan CLA Tertulis Wajib**: Kontributor wajib menandatangani dan menyerahkan **Contributor License Agreement (CLA)** tertulis yang disetujui oleh **Dhedry Afriazoni** *sebelum* kontribusi di-*merge* ke branch utama. Tanpa CLA tertulis yang sah, PR tidak dapat digabungkan ke basis kode proyek.
2. **Lingkup Hak CLA**: Melalui CLA tertulis tersebut, Kontributor menjamin keaslian karyanya dan memberikan kepada **Dhedry Afriazoni** lisensi yang global, berlaku terus-menerus (*perpetual*), tidak dapat dicabut (*irrevocable*), bebas royalti, non-eksklusif, dan dapat disublisensikan ulang di bawah seluruh jalur **Dual License** (baik `COMMUNITY-LICENSE.md` maupun `COMMERCIAL-LICENSE.md`, termasuk hak pengembangan lanjutan, *reuse* lintas proyek, dan distribusi komersial).
3. Kontributor tidak memperoleh pengalihan hak cipta atas modul ekosistem Dhedry melalui pengiriman PR.

---

## 2. Larangan Keras Penyisipan Kode, Merek, atau Aset Tanpa Audit

Kontributor **DILARANG KERAS**:
1. Menyalin (*copy-paste*), melakukan *decompile*, atau mengambil potongan kode maupun desain UI milik pihak ketiga (termasuk aplikasi kloner komersial/proprietary lain atau kode GPL/AGPL) tanpa dasar lisensi yang kompatibel dan audit tertulis.
2. Menambahkan dependency Gradle baru (`libs.versions.toml` / `build.gradle.kts`), plugin, font, ikon, atau aset gambar tanpa melampirkan sumber dan lisensi aktualnya serta memperbarui `THIRD-PARTY-NOTICES.md` dan `IP-OWNERSHIP-REGISTER.md`.
3. Memasukkan berkas dari direktori **`public/assets/aistudio/`** (atau aset AI/eksternal tanpa bukti hak penggunaan) ke dalam paket distribusi publik, build rilis, atau PR baru sebelum asal-usul dan hak penggunaannya dipastikan secara sah.
4. Menambahkan merek dagang pihak ketiga ke dalam kode aplikasi atau UI yang mendistribusikan produk (seperti referensi komparatif merek pesaing).

---

## 3. Keamanan & Rahasia

- **Dilarang keras** meng-commit file `.env`, keystore (`.jks`, `.keystore`, `.p12`), API key pribadi, token CI, password, atau `service-account*.json`.
- Setiap kontribusi yang berisi kredensial sensitif akan ditolak dan rahasia terkait wajib segera dirotasi.

---

## 4. Standar Header Kode & Pengujian

- **Catatan Tahap `CP010J`**: Pemasangan header hak cipta/SPDX maupun inisialisasi Git baseline **belum** diaktifkan pada `CP010J`. Setelah checkpoint implementasi dan peninjauan manusia disetujui oleh pemilik, berkas Kotlin baru/terverifikasi wajib mengikuti standar header yang ditetapkan serta lulus `gradle :app:testDebugUnitTest` dan `gradle :app:assembleDebug`.
- Penafsiran syarat kontribusi dan CLA mengacu pada **Hukum Republik Indonesia** kecuali ditentukan lain secara tertulis dalam teks CLA yang ditandatangani.
