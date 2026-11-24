# ZClone — CP010B Package Identity Fix & APK Rebuild Report

- **Checkpoint ID**: `CP010B`
- **Timestamp (UTC)**: `2026-11-21T00:10:00Z`
- **Project**: ZClone (`com.fms.filemanager`, `v1.0` / `versionCode = 1`)
- **Status**: `VERIFIED`

## 1. Old & New Package Identity
- **Previous Application ID / Namespace**:
  - Gradle `namespace`: `com.example`
  - Gradle `applicationId`: `com.aistudio.zclone.mpxqkz`
  - App Kotlin Packages: `com.example`, `com.example.ui.theme`, `com.example.zclone.data`, `com.example.zclone.ui`, `com.example.zclone.ui.screens`
  - Resolved `FileProvider` Authority: `com.aistudio.zclone.mpxqkz.fileprovider`
- **New Required Application ID / Namespace**:
  - Gradle `namespace`: **`com.fms.filemanager`**
  - Gradle `applicationId`: **`com.fms.filemanager`**
  - App Kotlin Packages: `com.fms.filemanager`, `com.fms.filemanager.ui.theme`, `com.fms.filemanager.zclone.data`, `com.fms.filemanager.zclone.ui`, `com.fms.filemanager.zclone.ui.screens`
  - Resolved `FileProvider` Authority: **`com.fms.filemanager.fileprovider`**
  - Preserved FMS Engine Packages (unchanged): `com.fms.core`, `com.fms.security`, `com.fms.data`, `com.fms.ui`, `com.fms.task`, `com.fms.cloud`, `com.fms.device`, `com.fms.clone`

## 2. Files Changed
- **`app/build.gradle.kts`**: Updated `namespace` (`"com.fms.filemanager"`) and `applicationId` (`"com.fms.filemanager"`). Preserved `signingConfigs`, `buildTypes`, SDK versions, and dependencies.
- **Migrated Source Directories & Package/Import Headers (`com.example` -> `com.fms.filemanager`)**:
  - `app/src/main/java/com/fms/filemanager/MainActivity.kt`
  - `app/src/main/java/com/fms/filemanager/ui/theme/{Color.kt, Theme.kt, Type.kt}`
  - `app/src/main/java/com/fms/filemanager/zclone/data/FirestoreRepositories.kt`
  - `app/src/main/java/com/fms/filemanager/zclone/ui/ZCloneViewModel.kt`
  - `app/src/main/java/com/fms/filemanager/zclone/ui/screens/{SplashAndHomeScreens.kt, CloneAndConfigScreens.kt, LibraryToolsAndSettingsScreens.kt}`
  - Removed empty obsolete directory `app/src/main/java/com/example`
- **Updated `R` Imports in Preserved FMS Modules**:
  - `app/src/main/java/com/fms/cloud/FmsCloudEngine.kt` (`import com.fms.filemanager.R`)
  - `app/src/main/java/com/fms/ui/FmsThemeEngine.kt` (`import com.fms.filemanager.R`)
- **Migrated Unit, Robolectric & Instrumented Tests**:
  - `app/src/test/java/com/fms/filemanager/{ExampleUnitTest.kt, ExampleRobolectricTest.kt, FmsCoreAndDataTest.kt, FmsCloneEngineTest.kt}` (package updated to `com.fms.filemanager`; fixture strings updated from `com.example.*` to `com.sample.*` / `com.demo.*`)
  - `app/src/androidTest/java/com/fms/filemanager/ExampleInstrumentedTest.kt` (package updated to `com.fms.filemanager`)
- **Firebase Configuration (`app/google-services.json`, `app/src/main/res/values/firebase_applet_config.xml`)**:
  - Re-provisioned via `fax.ProvisionFirebase` RPC for `package_name = "com.fms.filemanager"` inside project `gen-lang-client-0584823370`.
- **Manifest & Provider Verification (`app/src/main/AndroidManifest.xml`)**:
  - Activity `android:name=".MainActivity"` resolves to `com.fms.filemanager.MainActivity`.
  - Provider `android:authorities="${applicationId}.fileprovider"` resolves to `com.fms.filemanager.fileprovider`.

## 3. Remaining `com.example` / `com.aistudio.zclone` References (with Explanations)
- **In `app/src/` and `app/build.gradle.kts`**: **0 matches** (`grep -rnE 'com\.example|com\.aistudio\.zclone' app/src/ app/build.gradle.kts settings.gradle.kts metadata.json` returned exit code `1`).
- **Outside `app/src/` (Intentionally Preserved / Non-App-Namespace)**:
  1. `app/google-services.json` (lines 12 & 20): Contains the historical `com.aistudio.zclone.mpxqkz` Android client entry alongside the newly provisioned `com.fms.filemanager` Android client entry (`fax.ProvisionFirebase` appends client registrations without hand-editing). The Google Services Gradle plugin selects the exact client matching `applicationId` (`com.fms.filemanager`).
  2. `firestore.test.js` (line 252): Sample fixture field `packageName: "com.example.app"` representing an arbitrary scanned third-party APK in Firestore security rules unit tests (not ZClone's package ID).
  3. Historical pre-CP010B documentation (`docs/CHECKPOINT_INDEX.md` CP005/CP010 rows, `docs/CP010_APK_PACKAGING_REPORT.md`, `docs/FMS_ECOSYSTEM_CATALOG.md`, `docs/RESUME_INSTRUCTIONS.md`, and `.aistudio/.../implementation_plan.md`): Preserved intact per CP010B preservation rules (`CP000`–`CP010`).

## 4. Firebase Compatibility Status
- **Status**: **COMPATIBLE & VERIFIED**
- **Firebase Project ID**: `gen-lang-client-0584823370` (`project_number`: `13772767437`)
- **Firestore Database ID**: `ai-studio-android-b9abcc11-8e2d-470f-94d0-3895ea552be1`
- **Registered Android Client (`com.fms.filemanager`)**:
  - `mobilesdk_app_id`: `1:13772767437:android:56f7fd5dcd9fa952726984`
  - Android OAuth `client_id` (`client_type: 1`): `13772767437-0l7qdd27jcdjagbtqtoto92s7jos1u92.apps.googleusercontent.com`
  - Web OAuth `client_id` (`client_type: 3`): `13772767437-93j6uos5968i3113hhv0a2r2j8t9h11f.apps.googleusercontent.com`
  - Registered `certificate_hash`: `71d8305b6ea13ab671a270a1ac52f7f2c0f9960e` (verified via `grep` and matching `debug.keystore` SHA-1 `71:D8:30:5B:6E:A1:3A:B6:71:A2:70:A1:AC:52:F7:F2:C0:F9:96:0E`).
- `:app:processDebugGoogleServices` executed and succeeded with zero warnings or mismatches.

## 5. Build, Unit Test, Metadata & Signature Verification
- **Unit & Robolectric Tests (`gradle :app:testDebugUnitTest --console=plain`)**: **BUILD SUCCESSFUL**
  - `com.fms.filemanager.ExampleRobolectricTest`: 4/4 PASS
  - `com.fms.filemanager.ExampleUnitTest`: 1/1 PASS
  - `com.fms.filemanager.FmsCloneEngineTest`: 2/2 PASS
  - `com.fms.filemanager.FmsCoreAndDataTest`: 4/4 PASS
  - **Total**: **11/11 tests PASS** (`0` skipped, `0` failures, `0` errors).
- **Debug APK Build**: **BUILD SUCCESSFUL** (`compile_applet` + `gradle :app:assembleDebug --console=plain`).
- **Actual APK Metadata (`aapt2 dump badging` & `aapt2 dump xmltree`)**:
  - `package: name='com.fms.filemanager' versionCode='1' versionName='1.0' platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36' minSdkVersion:'24' targetSdkVersion:'36'`
  - Launcher Activity: `com.fms.filemanager.MainActivity`
  - `FileProvider` Authority: `com.fms.filemanager.fileprovider`
  - `output-metadata.json`: `applicationId = com.fms.filemanager`, `variantName = debug`, `versionCode = 1`, `versionName = 1.0`, `minSdkVersionForDexing = 24`.
- **Signature Verification (`apksigner verify --verbose --print-certs`)**:
  - Result: **Verifies** (`APK Signature Scheme v2: true`)
  - Signer #1 DN: `CN=Android Debug, O=Android, C=US` (`RSA 2048`)
  - Signer #1 SHA-1: `71d8305b6ea13ab671a270a1ac52f7f2c0f9960e`
  - Signer #1 SHA-256: `172f7a83d7f11a8723c1860fe1779e6ac140fb1b458e50c211437857681dfb35`
- **ZIP Integrity (`unzip -tq`)**: `No errors detected in compressed data of app/build/outputs/apk/debug/app-debug.apk.`

## 6. APK Output Path, Size & SHA-256
- **APK Filename**: `app-debug.apk`
- **Exact Output Paths**:
  - `app/build/outputs/apk/debug/app-debug.apk`
  - `.build-outputs/app-debug.apk`
- **Exact Size**: `25,795,067` bytes
- **SHA-256**: `b5dbb88c6ee56249a3dbfd8ade3a80423fbb6478564b255262ba6f8858e6b48d` (verified identical on both paths)

## 7. Blockers & Limitations
- **Blockers for Debug Package Identity**: **None**.
- **Unchanged Environment Limitations**: Release APK signing remains unconfigured in this cloud environment (developer keystore credentials absent), and physical device/ADB installation testing was not run (no emulator/ADB available in container).
