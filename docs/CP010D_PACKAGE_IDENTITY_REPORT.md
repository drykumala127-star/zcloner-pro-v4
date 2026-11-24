# ZClone — CP010D Corrected Final Package Identity & APK Rebuild Report

- **Checkpoint ID**: `CP010D`
- **Timestamp (UTC)**: `2026-11-21T00:24:00Z`
- **Project**: ZClone (`com.fms.zclone.enginecoremode`, `v1.0` / `versionCode = 1`)
- **Application Name**: `ZClone`
- **Status**: `VERIFIED`

## 1. Corrected Final Package Identity & Preserved Library Namespaces
- **Authoritative APK Application ID & Namespace**:
  - Gradle `namespace`: **`com.fms.zclone.enginecoremode`**
  - Gradle `applicationId`: **`com.fms.zclone.enginecoremode`**
  - Application Packages: `com.fms.zclone.enginecoremode`, `com.fms.zclone.enginecoremode.ui.theme`, `com.fms.zclone.enginecoremode.zclone.data`, `com.fms.zclone.enginecoremode.zclone.ui`, `com.fms.zclone.enginecoremode.zclone.ui.screens`
  - Main Launcher Activity: **`com.fms.zclone.enginecoremode.MainActivity`**
  - `FileProvider` Authority: **`com.fms.zclone.enginecoremode.fileprovider`**
- **Preserved FMS Library Namespaces (Unchanged)**:
  - `com.fms.core`
  - `com.fms.security`
  - `com.fms.data`
  - `com.fms.ui`
  - `com.fms.task`
  - `com.fms.cloud`
  - `com.fms.device`
  - `com.fms.clone`

## 2. Files Changed
- **`app/build.gradle.kts`**: Updated `namespace` and `applicationId` to `"com.fms.zclone.enginecoremode"` while preserving `signingConfigs`, `buildTypes`, SDK versions, and dependencies.
- **Migrated Application-Owned Packages (`com.fms.filemanager` -> `com.fms.zclone.enginecoremode`)**:
  - `app/src/main/java/com/fms/zclone/enginecoremode/MainActivity.kt`
  - `app/src/main/java/com/fms/zclone/enginecoremode/ui/theme/{Color.kt, Theme.kt, Type.kt}`
  - `app/src/main/java/com/fms/zclone/enginecoremode/zclone/data/FirestoreRepositories.kt`
  - `app/src/main/java/com/fms/zclone/enginecoremode/zclone/ui/ZCloneViewModel.kt`
  - `app/src/main/java/com/fms/zclone/enginecoremode/zclone/ui/screens/{SplashAndHomeScreens.kt, CloneAndConfigScreens.kt, LibraryToolsAndSettingsScreens.kt}`
- **Updated `R` Imports in Preserved FMS Library Modules**:
  - `app/src/main/java/com/fms/cloud/FmsCloudEngine.kt` (`import com.fms.zclone.enginecoremode.R`)
  - `app/src/main/java/com/fms/ui/FmsThemeEngine.kt` (`import com.fms.zclone.enginecoremode.R`)
- **Migrated Unit, Robolectric & Instrumented Tests**:
  - `app/src/test/java/com/fms/zclone/enginecoremode/{ExampleUnitTest.kt, ExampleRobolectricTest.kt, FmsCoreAndDataTest.kt, FmsCloneEngineTest.kt}`
  - `app/src/androidTest/java/com/fms/zclone/enginecoremode/ExampleInstrumentedTest.kt`
- **Firebase Registration (`app/google-services.json`)**:
  - Registered `com.fms.zclone.enginecoremode` via `fax.ProvisionFirebase` into project `gen-lang-client-0584823370`.
- **Cleanliness Verification**:
  - `grep -rnE 'com\.fms\.filemanager|com\.example|com\.aistudio\.zclone' app/src/ app/build.gradle.kts settings.gradle.kts metadata.json` returned **0 matches** (`exit code 1`).
  - Unrelated fixture in `firestore.test.js` (`packageName: "com.example.app"`) and historical `CP000`–`CP010B` documentation remain preserved.

## 3. Firebase Compatibility Status
- **Status**: **COMPATIBLE & VERIFIED**
- **Firebase Project ID**: `gen-lang-client-0584823370` (`project_number`: `13772767437`)
- **Firestore Database ID**: `ai-studio-android-b9abcc11-8e2d-470f-94d0-3895ea552be1`
- **Registered Android Client (`com.fms.zclone.enginecoremode`)**:
  - `mobilesdk_app_id`: `1:13772767437:android:e316bd325a7baabd726984`
  - Android OAuth `client_id` (`client_type: 1`): `13772767437-civuchkq3h6d8li4aepkm6ppln0no2nj.apps.googleusercontent.com`
  - Web OAuth `client_id` (`client_type: 3`): `13772767437-93j6uos5968i3113hhv0a2r2j8t9h11f.apps.googleusercontent.com`
  - Registered `certificate_hash`: `71d8305b6ea13ab671a270a1ac52f7f2c0f9960e` (verified in `app/google-services.json`, matching `debug.keystore` SHA-1).

## 4. Build, Test, Metadata & Signature Verification
- **Unit & Robolectric Tests (`gradle :app:testDebugUnitTest --console=plain`)**: **BUILD SUCCESSFUL** (**11/11 PASS**, `0` skipped, `0` failures, `0` errors) across `com.fms.zclone.enginecoremode.*` (`ExampleRobolectricTest` 4, `ExampleUnitTest` 1, `FmsCloneEngineTest` 2, `FmsCoreAndDataTest` 4).
- **Debug APK Build (`compile_applet` & `gradle :app:assembleDebug --console=plain`)**: **BUILD SUCCESSFUL**.
- **Actual APK Metadata (`aapt2 dump badging` & `aapt2 dump xmltree`)**:
  - `package: name='com.fms.zclone.enginecoremode' versionCode='1' versionName='1.0' platformBuildVersionName='16' platformBuildVersionCode='36' compileSdkVersion='36' minSdkVersion:'24' targetSdkVersion:'36'`
  - `application-label:'ZClone'`
  - Launcher Activity: `com.fms.zclone.enginecoremode.MainActivity`
  - `FileProvider` Authority: `com.fms.zclone.enginecoremode.fileprovider` (`exported=false`, `grantUriPermissions=true`)
  - `output-metadata.json`: `applicationId = "com.fms.zclone.enginecoremode"`, `variantName = "debug"`, `versionCode = 1`, `versionName = "1.0"`, `minSdkVersionForDexing = 24`.
- **Signature Verification (`apksigner verify --verbose --print-certs`)**:
  - Result: **Verifies** (`APK Signature Scheme v2: true`)
  - Signer #1 DN: `CN=Android Debug, O=Android, C=US` (`RSA 2048`)
  - Signer #1 SHA-1: `71d8305b6ea13ab671a270a1ac52f7f2c0f9960e`
  - Signer #1 SHA-256: `172f7a83d7f11a8723c1860fe1779e6ac140fb1b458e50c211437857681dfb35`
- **ZIP Integrity (`unzip -tq`)**: `No errors detected in compressed data of app/build/outputs/apk/debug/app-debug.apk.`

## 5. APK Output Path, Size & SHA-256
- **APK Filename**: `app-debug.apk`
- **Exact Output Paths**:
  - `app/build/outputs/apk/debug/app-debug.apk`
  - `.build-outputs/app-debug.apk`
- **Exact Size**: `25,795,138` bytes
- **SHA-256**: `75a173c61a5832e1256a4c7b6cfc487a66d3d3396fd23f8557c1fb6c52cf203e` (verified identical on both paths)

## 6. Preservation & Limitations
- `CP000`–`CP010B` checkpoints preserved intact; no feature reimplementation, no Audit 2, and no final source ZIP generated at this stage.
- Release APK signing and physical device/ADB installation testing remain outside the current cloud environment.
