# ZCloner PRO — Official Feature Parity & FMS Engine Core V1 Capabilities Matrix

This matrix is synchronized directly with `com.fms.clone.FmsParityCatalog` (`CP010M-04`, **Tahap E1 Stabilized**). It separates capabilities currently implemented on **Host (`ZCloner PRO` / `FMS Engine Core V1`)**, features whose runtime lives inside **APK hasil clone (`Tahap E2–E4`)**, and platform-restricted capabilities marked `UNSUPPORTED` on unrooted Android (API 24–36).

## 1. Official App Cloner Editions, Add-ons, Packages & Extensions (`FmsParityCatalog`)
| ID | Feature | Official Category | FMS Module & API | Status | Notes & Technical Limitations |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `AC-FREE-01` | Single Standalone APK Cloning | Free | `FMSCloneEngine / FMSRepackager.buildClonedApk()` | `IMPLEMENTED` | Repackages base APK with new `packageName`, local user PKCS#7 v1 + APK Sig Block v2 signature |
| `AC-FREE-02` | Clone Display Name Customization | Free | `FMSManifestEngine.transformManifest(newApplicationLabel)` | `IMPLEMENTED` | Updates `android:label` directly inside binary `AndroidManifest.xml` |
| `AC-PREM-01` | App Icon Color, Hue & Badge Studio | Premium | `FMSIconEngine.transformIconPng()` | `IMPLEMENTED` | Applies `ColorMatrix` hue rotation and clone badge across PNG & WebP launcher assets (skips `.9.png`) |
| `AC-PREM-02` | Permission Stripping (`Izin Kustom`) | Premium | `FMSManifestEngine` (`strippedPermissions`) | `IMPLEMENTED` | Removes selected `uses-permission` XML tags from cloned manifest |
| `AC-PREM-03` | Hide from Launcher & Backup Config | Premium | `FMSManifestEngine` / `FMSBackupEngine` | `IMPLEMENTED` | Rewrites `CATEGORY_LAUNCHER` to `DEFAULT`; exports/imports `<= 2 MB` validated JSON recipes |
| `AC-PREM-04` | Hide / Spoof Hardware Identifiers (IMEI/MAC/Serial) | Premium | `FMSSecurityEngine` (Policy Guard) | `UNSUPPORTED` | Prohibited by Android security & Play policy; replaced by safe `AndroidKeyStore` Install UUID |
| `AC-ULTRA-01` | Batch Multi-App Cloning & Orchestration | Ultra / Ultra+ / Ultra++ | `FMSBackgroundEngine` / `FMSCloneEngine` | `IMPLEMENTED` | Bounded coroutine queue (`Semaphore`) with real `%` progress & cooperative cancellation (`cancelJob`/`cancelAll`) |
| `AC-ULTRA-02` | Play Integrity / DRM / Root Hiding Bypass | Ultra++ | `FMSIntegrityEngine` | `UNSUPPORTED` | Strictly prohibited by Google Play policy and platform integrity rules |
| `AC-ADDON-01` | Manifest & Resource Inspector Add-on | Add-ons | `FMSAPKInspector.inspectApkFile()` | `IMPLEMENTED` | Extracts binary AXML summary, native ABIs, icons, and cert SHA-256 with Zip-Slip guard |
| `AC-ADDON-02` | Safe Update & App Data Export/Import | Add-ons | `FMSBackupEngine` | `LIMITED` | Android sandbox forbids reading third-party `/data/data` without root; exports ZCloner PRO recipes & themes |
| `AC-PKG-01` | Blue/Green Network Proxy & DoH Packages | Packages | `FMSNetworkSecurity` | `LIMITED` | Current scope focuses on safe Manifest, Resource & Signing without third-party DEX proxy injection |
| `AC-EXT-01` | TV / Wear OS Form-Factor Extensions | Extensions | `FMSLayoutEngine.classifyWidth()` | `LIMITED` | Responsive ZCloner PRO UI (`NavigationBar`/`NavigationRail`); does not inject TV mouse into external APKs |

## 2. 22 ZCloner PRO Exclusive Capabilities (`ZC-EX-01` – `ZC-EX-22`)
| ID | Capability | Scope (`Host` vs `Cloned APK`) | Module / Planned Runtime | Status | Notes & Evidence |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `ZC-EX-01` | Smart Compatibility | Host | `FMSCompatibilityEngine.analyze()` | `IMPLEMENTED` | Split APK, `sharedUserId`, missing DEX, ABI, SDK checks |
| `ZC-EX-02` | Clone Time Machine | Host | `FMSDatabaseEngine` (SQLite `v2`) | `IMPLEMENTED` | Full `snapshot_json` persistence & restore to `AppConfigScreen` |
| `ZC-EX-03` | Version Diff | Host (`Cloned APK` UI E2/E3) | `FmsBinaryVersionDiff` | `IMPLEMENTED` | Binary `.apk` delta (perms, components, DEX, `.so`, size) + JSON export |
| `ZC-EX-04` | Migration | Host | `FMSBackupEngine` / `FMSSyncEngine` | `IMPLEMENTED` | Metadata/theme backup JSON & Firestore sync (no root `/data/data`) |
| `ZC-EX-05` | Smart Update | Host | `FMSKeyManager` (`AndroidKeyStore`) | `IMPLEMENTED` | Consistent local RSA-2048 cert; KeyStore-wrapped PKCS#12 + safe legacy migration (`P1.A`) |
| `ZC-EX-06` | Privacy Policy Engine | Host -> Clone Manifest | `FMSManifestEngine.applyPrivacyPreset()` | `IMPLEMENTED` | Strips sensitive permissions into manifest config |
| `ZC-EX-07` | Visual Automation | Cloned APK Runtime *(Tahap E3)* | `FMSProRuntime` | `PLANNED` | In-process lifecycle + opt-in `AccessibilityService` inside cloned APK (`Tahap E3`) |
| `ZC-EX-08` | Crash Intelligence | Host | `FmsLogger` + `FmsErrorEngine` | `IMPLEMENTED` | Automatic token/passphrase/JWT/PEM private-key redaction (`P0.C`, `0` direct `Log.*`) |
| `ZC-EX-09` | Reproducible Builder | Host Engine + Clone Asset | `FMSRepackager` / `FMSSignatureManager` | `IMPLEMENTED` | Canonical DOS timestamp (`1981-01-01`), sorted entries/digests, identical `outputSha256`, `reproducible_build.json`, cancel-safe v1/v2 signing |
| `ZC-EX-10` | APK Dependency Graph | Host Engine + Clone Asset | `FmsDependencyGraphAnalyzer` / `ElfDependencyParser` | `IMPLEMENTED` | AXML components, `<queries>`, multidex, authorities, `uses-library`, ELF `DT_NEEDED` + `dependency_graph.json` |
| `ZC-EX-11` | Multi-Profile Workspace | Host | `FMSDatabaseEngine.upsertCloneConfig()` | `IMPLEMENTED` | Multiple saved clone profiles per source package |
| `ZC-EX-12` | Batch Orchestrator | Host *(Cloned APK E3)* | `FMSBackgroundEngine` | `LIMITED` | Active on host with `%` progress & clean cancellation; in-clone `JobScheduler` targeted `Tahap E3` |
| `ZC-EX-13` | Security Scanner | Host | `FMSSecurityScanner.scan()` | `IMPLEMENTED` | Static manifest, permissions, exported components, `SEC-UNSIGNED` scanner |
| `ZC-EX-14` | Visual Resource Studio | Host -> Clone Icons | `FMSIconEngine` | `IMPLEMENTED` | PNG & WebP hue transformation + clone badge (`AppConfigScreen`) |
| `ZC-EX-15` | Offline Clone Lab | Host *(Cloned APK E3)* | `FMSDatabaseEngine` + `FMSCloneEngine` | `LIMITED` | Host build/inspect/sign works 100% offline; in-clone self-test lab targeted `Tahap E3` |
| `ZC-EX-16` | Extension SDK | Host Registry *(Cloned APK E4)* | `FmsPluginEngine` | `LIMITED` | In-process host registry (zero DCL); declarative JSON/Intent/`ContentProvider` extension runtime targeted `Tahap E4` |
| `ZC-EX-17` | Auto Theme Engine | Host *(Cloned APK E2)* | `FMSAutoTheme.toggleAutoTheme()` | `LIMITED` | Active on host UI; in-clone Auto Theme overlay runtime targeted `Tahap E2` |
| `ZC-EX-18` | UI Customization Studio | Host | `FMSThemeStudio.applyCustomTheme()` | `LIMITED` | Host primary/secondary colors, bundled TTF fonts, icon style |
| `ZC-EX-19` | Live Preview | Host | `FMSIconEngine` / `FMSThemeStudio` | `IMPLEMENTED` | Live hue/theme preview in `AppConfigScreen` & `ThemeStudioScreen` |
| `ZC-EX-20` | Theme Import/Export | Host | `FMSBackupEngine` / `ThemeRepository` | `IMPLEMENTED` | Theme JSON backup & Firestore sync |
| `ZC-EX-21` | Device Management | Host | `FMSDeviceManager` | `IMPLEMENTED` | Random install UUID in `AndroidKeyStore`-backed storage (zero hardware IDs) |
| `ZC-EX-22` | Device License Manager | Host + Rules *(Cloned APK E4)* | `DeviceLicenseManager` + `firestore.rules` | `LIMITED` | Tier lock & `REVOKED` rules verified (`5/5` emulator, deploy `BLOCKED`); signed local token + IPC inside cloned APK targeted `Tahap E4` |
