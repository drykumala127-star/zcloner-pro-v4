# ZCloner PRO — Roadmap Ekspansi Engine: Fitur PRO di Dalam APK Hasil Clone

**Status:** `IN PROGRESS — CP010M-04 (E1 STABILIZED: Host/Engine & Build Integration Verified; Cloned-APK Runtime Tahap E2–E4 PLANNED)`  
**Prinsip Arsitektur:** Seluruh 9 fitur tambahan PRO beroperasi di dalam **APK hasil clone** (bukan fitur eksklusif panel host ZCloner), tanpa root, tanpa runtime hooking (Xposed/Frida), dan tanpa dynamic code loading (`.dex`/`.jar`/`.so` eksternal).

---

## Pembagian Implementasi

### A. Fitur Runtime di Dalam APK Hasil Clone (`classes{N}.dex` + AXML + Signed Assets)
1. **Visual Automation**: Kombinasi `ActivityLifecycleCallbacks` + `Window.Callback` (in-process intra-app flow) dan opt-in `AccessibilityService` (`BIND_ACCESSIBILITY_SERVICE`) di dalam APK hasil clone.
2. **Batch Orchestrator**: `DhedryBatchOrchestrator` berbasis `JobScheduler` / `JobService` + antrean SQLite terisolasi di sandbox APK hasil clone.
3. **Auto Theme**: `DhedryThemeRuntime` via `ActivityLifecycleCallbacks` (`Configuration.uiMode`, warna system bar, `setForceDarkAllowed`, `UiModeManager`) tanpa merusak `resources.arsc` target.
4. **Extension SDK**: `DhedryExtensionRuntime` berbasis **ekstensi deklaratif JSON, Intent, Deep-Link, dan `ContentProvider`** dengan `<queries>` AXML — **nol dynamic DEX/native loading**.
5. **Device License Manager**: `DhedryLicenseGuard` memverifikasi **token bertanda tangan kriptografis + AndroidKeyStore** secara luring serta **sinkronisasi IPC (`ContentProvider` + `<queries>`)** dengan host ZCloner PRO.

### B. Fitur Engine & Build yang Hasilnya Terintegrasi ke APK
6. **Dependency Graph**: Analisis statis AXML komponen, `<queries>`, referensi DEX multidex, & ELF `.so` `DT_NEEDED` yang menjaga keutuhan manifest saat repackaging serta menyematkan `assets/dhedry/dependency_graph.json`.
7. **Reproducible Builder**: Penulis ZIP kanonis deterministik (`SORTED` entry order, fixed DOS timestamp, `MANIFEST.MF` & alignment `4`/`16384` byte tetap) + penyematan provenance `assets/dhedry/reproducible_build.json`.
8. **Version Diff**: Komputasi delta antar versi APK (izin, komponen exported, SDK, jumlah DEX, ABI `.so`, modul PRO) yang hasilnya disematkan di `assets/dhedry/version_diff.json` dan dapat dilihat di `DhedryProStatusActivity` dalam APK klon.

### C. Fitur Lab & Validasi
9. **Offline Lab**: Harness validasi biner luring di host ZCloner + penyematan `assets/dhedry/offline_lab_report.json` & `DhedrySelfTestActivity` di dalam APK hasil clone untuk uji Stages 8–10 tanpa koneksi internet.

---

## Feature Integration Matrix

| Nama fitur | Komponen ZCloner | Komponen APK hasil clone | Metode integrasi | Android API | Root requirement | Runtime hook requirement | Status implementasi | Test dan bukti keberhasilan |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Visual Automation** | Flow Designer UI, validator skema langkah, compiler JSON aturan ke bundel build | `DhedryProInitProvider`, `DhedryAutomationManager`, `Window.Callback` decorator, opsional `DhedryAutomationService` (opt-in) | Injeksi `classes{N}.dex` + `assets/dhedry/automation_flows.json` + deklarasi opt-in `<service>` (`BIND_ACCESSIBILITY_SERVICE`) di AXML | API 24+ (`ActivityLifecycleCallbacks`, `Window.Callback`, `AccessibilityService.dispatchGesture`, `AccessibilityNodeInfo`) | Tidak | Tidak | `PLANNED` | Unit test parser/eksekutor flow + Robolectric simulasi traversal `View` Activity + `aapt2` verify |
| **Batch Orchestrator** | Task & Schedule Composer, validator constraint, batch build queue (`FMSJobQueue`) | `DhedryBatchOrchestrator`, `DhedryJobService`, antrean SQLite internal clone | Injeksi `classes{N}.dex` + `assets/dhedry/orchestrator_plan.json` + registrasi `<service BIND_JOB_SERVICE>` di AXML | API 24+ (`JobScheduler`, `JobService`), API 26+ (`NotificationChannel`), API 34+ (`dataSync`) | Tidak | Tidak | `PLANNED` | Robolectric test `JobScheduler`, retry/backoff, & persistensi antrean internal clone |
| **Dependency Graph** | Static Analyzer (AXML, `<queries>`, DEX, ELF `.so` `DT_NEEDED`), pratinjau graf (`AppConfigScreen`) | `assets/dhedry/dependency_graph.json` *(UI in-clone `DhedryProStatusActivity` di Tahap E2/E3)* | Analisis pra-build memandu `FMSManifestEngine`/`FMSRepackager` & menanam graf JSON tervalidasi ke aset APK | API 24+ (parser statis host; read-only JSON di clone) | Tidak | Tidak | `PARTIAL (TAHAP E1 VERIFIED)` | `FmsEngineCoreV1E1Test` (ELF64 `DT_NEEDED` + `<queries>` + Stage 3 `dependency_graph.json`) PASS |
| **Reproducible Builder** | Canonical ZIP writer (`1981-01-01` safe DOS timestamp, sorted entries/digests), KeyStore-wrapped PKCS#12 (`FMSKeyManager`), & cancel-safe deterministik `FMSSignatureManager` (v1+v2) | `assets/dhedry/reproducible_build.json` (`ZCloner PRO` / `Copyright FMS Clone Engine V1`; *(cek startup in-clone di Tahap E2/E3)*) | Build deterministik di `FMSRepackager` + provenance bertanda tangan di aset APK + pembatalan aman v1/v2 tanpa korup | API 24+ (`ZipOutputStream`, `MessageDigest`, `AndroidKeyStore`, APK v2 deterministik) | Tidak | Tidak | `VERIFIED (CP010M-04 E1 STABILIZED)` | `FmsEngineCoreV1E1Test` + `E1StabilizationPatchTest`: 2x build SHA-256 identik, `apksigner verify` PASS, cancel saat v1/v2 bersih tanpa temp, migrasi KeyStore non-destruktif PASS |
| **Offline Lab** | Harness validasi biner luring (`FMSOfflineLabEngine`) | `DhedrySelfTestActivity` + `assets/dhedry/offline_lab_report.json` | Verifikasi statis & biner luring + modul uji mandiri di dalam APK hasil clone | API 24+ (`PackageManager`, `SUPPORTED_ABIS`, `AssetManager`, verifikasi sig) | Tidak | Tidak | `PLANNED` | Automated Robolectric/JVM test verifikasi APK sehat vs rusak 100% offline |
| **Version Diff** | `FmsBinaryVersionDiff` real binary `.apk` comparator + Recipe diff + JSON export (`MyClonesLibraryScreen`) | `assets/dhedry/version_diff.json` + tab ringkasan di `DhedryProStatusActivity` *(Tahap E2/E3)* | Comparator `.apk` nyata di host aktif; penyematan UI in-clone menunggu Tahap E2/E3 | API 24+ (`JSONObject` & comparator biner ZIP/AXML/ELF) | Tidak | Tidak | `PARTIAL (TAHAP E1 HOST)` | `FmsEngineCoreV1E1Test` diff `.apk` v1 vs v2 + ekspor JSON PASS |
| **Auto Theme** | Preset & kebijakan tema (`SYSTEM`, `FORCE_DARK`, `FORCE_LIGHT`, warna bar) | `DhedryThemeRuntime` via `ActivityLifecycleCallbacks` | Injeksi `classes{N}.dex` + `assets/dhedry/theme_policy.json` | API 24+ (`Configuration.uiMode`, `Window`), API 29+ (`setForceDarkAllowed`), API 31+ (`UiModeManager`) | Tidak | Tidak | `PLANNED` | Robolectric test penerapan `uiMode` & warna bar pada Activity target APK clone |
| **Extension SDK** | Extension Registry & Validator skema JSON, penandatanganan bundel | `DhedryExtensionRuntime` (eksekutor JSON, Intent dispatcher, bridge `ContentProvider`) | Injeksi runtime & `assets/dhedry/extensions/*.json` + `<queries>` (tanpa muat `.dex`/`.so`) | API 24+ (`Intent`, `ContentResolver`, `<queries>` API 30+) | Tidak | Tidak | `PLANNED` | Test validasi skema (tolak DCL/payload berbahaya) & eksekusi Intent/JSON rule di clone |
| **Device License Manager** | Otoritas akun (`FMSAuthEngine` + Firestore), penerbit token, `ZCloneLicenseProvider` | `DhedryLicenseGuard` (signature + KeyStore + IPC host ZCloner) | Injeksi `classes{N}.dex` + public key & token awal di aset + `<queries>` IPC | API 24+ (`Signature`, `AndroidKeyStore`, `ContentResolver.query`) | Tidak | Tidak | `PLANNED` | Unit & Robolectric test token valid, kedaluwarsa, tamper, & respons `REVOKED` via IPC |
