# FMS Full Custom Ecosystem (`com.fms.*`) — Modular Architecture & API Catalog

## 1. Design Principles
- **API-First Modular Separation**: Every `com.fms.*` library depends strictly downward (`core` <- `security` / `data` / `ui` / `task` / `cloud` <- `device` / `clone`) with zero reverse dependencies on `com.example.zclone`.
- **Android-Standard Foundations**: Built cleanly atop official Android SDK primitives (`SQLiteOpenHelper`, `SharedPreferences`, `AndroidKeyStore`, `ConnectivityManager`, `Jetpack Compose Canvas`, `WindowInsets`, `PackageManager`, `ZipFile`).
- **No Unreviewed Crypto or DCL**: Uses `AES/GCM/NoPadding`, `SHA-256`, `SHA256withRSA`, and pure in-process plugin registration (`FmsPluginEngine`), avoiding external `.dex`/`.jar`/`.so` dynamic code loading.

## 2. Implemented Module Inventory
| Package & File | Exposed FMS Modules | Primary Responsibility |
| :--- | :--- | :--- |
| `com.fms.core/FmsCore.kt` | `FMSCore`, `FMSRuntime`, `FmsModule`, `FmsRuntimeContext` | Dependency-ordered topological initialization, health checks, lifecycle state flow |
| `com.fms.core/FmsEventEngine.kt` | `FmsEventBus`, `FmsConfiguration`, `FmsPluginEngine`, `FmsLogger`, `FmsErrorEngine`, `FmsResult` | Non-blocking event bus, safe in-process plugins, token-redacting logger, typed error wrapper |
| `com.fms.security/FmsSecurityEngine.kt` | `FMSKeyManager`, `FMSCryptoAdapter`, `FMSSecurityStorage`, `FMSIntegrityEngine`, `SelfSignedCertGenerator` | AndroidKeyStore AES-256-GCM storage, local user PKCS#12 RSA-2048 identity, Zip-Slip & package guards |
| `com.fms.data/FmsDatabaseEngine.kt` | `FMSDatabaseEngine`, `FMSMigrationEngine`, `FMSBackupEngine` | Local-first SQLite CRUD (`clone_configs`, `build_history`, `security_reports`), schema-bounded 2 MB JSON export/import |
| `com.fms.data/FmsStorageAndPrefs.kt` | `FMSPreferenceEngine`, `FMSStorageEngine`, `FMSCacheEngine` | App-private preferences, SAF stream copy/export, bounded memory LRU cache |
| `com.fms.ui/FmsThemeEngine.kt` | `FMSThemeEngine`, `FMSAutoTheme`, `FMSThemeStudio` | 6 theme presets, custom studio colors, bundled TTF typography (`Space Grotesk`, `Plus Jakarta Sans`, `JetBrains Mono`) |
| `com.fms.ui/FmsDesignSystem.kt` | `FMSLayoutEngine`, `FMSNavigationEngine`, `FmsBackHandler`, `FmsNeonWaveBackdrop`, `FmsGlassCard`, `FmsGradientButton`, `FmsStatusBadge` | Window width classification (`Compact`/`Medium`/`Expanded`), Compose backstack + `BackHandler`, glassmorphic components |
| `com.fms.task/FmsTaskEngine.kt` | `FMSBackgroundEngine`, `FmsJobQueue`, `FmsJobSnapshot` | Concurrency-bounded coroutine job queue (`Semaphore`) with real-time progress flows |
| `com.fms.cloud/FmsCloudEngine.kt` | `FMSConnectivity`, `FMSAuthEngine`, `FmsSyncState` | Network connectivity observer & Credential Manager Google Sign-In + Firebase Auth state flow |
| `com.fms.device/FmsDeviceManager.kt` | `FMSDeviceManager`, `LicenseTier`, `LocalDeviceIdentity`, `QuotaStatus` | Random install UUID persisted in `FMSSecurityStorage` (zero IMEI/Serial/MAC) |
| `com.fms.clone/*` | `FMSAPKInspector`, `FMSManifestEngine`, `FMSIconEngine`, `FMSRepackager`, `FMSSignatureManager`, `FMSCompatibilityEngine`, `FMSSecurityScanner`, `FmsParityCatalog` | Binary AXML inspection/mutation, PNG icon hue & badge patching, Zip-Slip-safe repackaging, JAR/PKCS#7 v1 local signing, static risk scanner |
