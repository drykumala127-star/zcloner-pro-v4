# DRAFT — TRADEMARK & BRAND IDENTITY NOTICE

> **STATUS DOKUMEN: DRAFT — Kebijakan Tersinkron (`CP010J`)**  
> *(Tidak mengklaim pendaftaran merek resmi `®` sebelum sertifikat pendaftaran terbit; proyek **BELUM `LEGALLY CLEARED`**.)*

Copyright © 2026 Dhedry Afriazoni. All Rights Reserved.

---

## 1. Identitas Nama Ekosistem Dhedry

Penanda identitas berikut digunakan untuk mengidentifikasi ekosistem perangkat lunak yang dikembangkan untuk **Dhedry Afriazoni**:
- **Dhedry** / **Dhedry Original Ecosystem**
- **ZClone** (`com.fms.zclone.enginecoremode`)
- **FMS** / **FMS Full Custom Ecosystem** (`com.fms.*`)
- **FMSCloneEngine**

**Pernyataan Jujur Status Pendaftaran**: Hingga `CP010J`, **belum terdapat bukti pendaftaran merek dagang resmi (`®`)** di DJKI atau lembaga pendaftaran merek mana pun yang dilampirkan dalam repositori ini. Penanda-penanda di atas diatur melalui klaim identitas proyek yang terdokumentasi dan pembatasan kontraktual dalam `COMMUNITY-LICENSE.md`, `COMMERCIAL-LICENSE.md`, dan `EULA.md` (tunduk pada **Hukum Republik Indonesia**).

---

## 2. Aturan Penggunaan Nama Identitas & Redistribusi Fork

1. Anda **TIDAK BOLEH** menggunakan nama atau logo **Dhedry**, **ZClone**, **FMS**, atau **FMSCloneEngine** untuk memberi kesan bahwa produk turunan, layanan, atau APK modifikasi Anda didukung, disponsori, atau dirilis secara resmi oleh Dhedry Afriazoni tanpa izin tertulis.
2. Pengguna `COMMUNITY-LICENSE.md` yang memodifikasi dan mendistribusikan ulang *fork* atau APK secara nonkomersial **WAJIB menggunakan nama produk dan ikon aplikasi milik sendiri** (berbeda dari identitas **ZClone** dan ikon resminya), menandai perubahan secara jelas, serta mempertahankan seluruh notice hak cipta, lisensi, dan `THIRD-PARTY-NOTICES.md`.

---

## 3. Penghormatan Merek Dagang Pihak Ketiga (Tidak Diklaim Milik Dhedry)

Dhedry Afriazoni **tidak mengklaim kepemilikan apa pun** atas merek dagang pihak ketiga:
- **Android**, **Google**, **Google Play**, **Firebase**, dan **Jetpack Compose** adalah merek dagang milik **Google LLC**.
- **Kotlin** dan **JetBrains** adalah merek dagang milik **JetBrains s.r.o.**.
- **App Cloner** (`appcloner.app` / `appcloner.blog`) adalah merek/layanan milik pemilik haknya masing-masing, **bukan** milik Dhedry Afriazoni dan tidak memiliki afiliasi atau dukungan resmi dengan ekosistem ZClone/FMS.

**Temuan Audit Merek Terbuka (`FmsParityCatalog.kt`)**:
- Hasil audit mendokumentasikan adanya `16` referensi string `"App Cloner"` / `"https://appcloner.app"` / `"https://appcloner.blog"` pada `app/src/main/java/com/fms/clone/FmsParityCatalog.kt` (baris 5, 10, 15, 33, 47, 52, 70, 88, 106, 125, 143, 162, 180, 198, 216).
- **Tingkat Risiko**: **Sedang–Tinggi** untuk distribusi komersial atau listing toko aplikasi (berisiko keberatan merek dagang atau penolakan listing).
- **Rencana Penanganan**: Sesuai batasan `CP010J`, source code **tidak diubah** pada tahap ini; penghapusan string/URL pesaing dan penyusunan ulang katalog fitur dengan terminologi teknis netral independen (bukan sekadar penggantian kosmetik atau obfuscation) dijadwalkan pada checkpoint implementasi khusus sebelum rilis publik atau komersial.
