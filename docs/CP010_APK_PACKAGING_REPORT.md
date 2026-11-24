# ZClone — CP010 APK Packaging Report

- **Checkpoint ID**: `CP010`
- **Timestamp (UTC)**: `2026-11-21T00:02:00Z`
- **Project**: ZClone (`com.aistudio.zclone.mpxqkz`, `v1.0` / `versionCode = 1`)
- **Status**: `VERIFIED`

## 1. Build Variant & Result
- **Debug Variant (`debug`)**: **BUILD SUCCESSFUL** (`gradle :app:assembleDebug --console=plain` -> exit code `0`, 38 tasks up-to-date).
- **Release Variant (`release`)**: **NOT BUILT (Skipped Safely)** — Release signing requires developer-supplied `KEYSTORE_PATH` (or `my-upload-key.jks`), `STORE_PASSWORD`, and `KEY_PASSWORD` environment credentials, all of which are absent (`MISSING` / `UNSET`). No placeholder or self-generated release keystore was created, keeping Debug signing (`debug.keystore` / `debugConfig`) independent.

## 2. APK Output Details (Debug)
- **APK Filename**: `app-debug.apk`
- **Exact Output Paths**:
  - Primary Gradle output: `app/build/outputs/apk/debug/app-debug.apk`
  - Platform build output mirror: `.build-outputs/app-debug.apk`
- **Exact Size**: `26,790,721` bytes (`~25.55 MB`)
- **SHA-256 Checksum**: `3d735d16002a1f86e175aabf77d7ff2268e7b3014a5a578c366081646cdb08e2` (identical across both files)

## 3. Packaging & Signing Integrity Verification
- **`apksigner verify --verbose --print-certs`**: **Verifies (`true`)**
  - APK Signature Scheme v2: `true`
  - Signer #1 Certificate DN: `CN=Android Debug, O=Android, C=US` (`RSA 2048`)
  - Signer #1 SHA-1: `71d8305b6ea13ab671a270a1ac52f7f2c0f9960e` (matches Firebase `google-services.json` `certificate_hash`)
  - Signer #1 SHA-256: `172f7a83d7f11a8723c1860fe1779e6ac140fb1b458e50c211437857681dfb35`
- **ZIP Structural Integrity (`unzip -tq`)**: `No errors detected in compressed data of app/build/outputs/apk/debug/app-debug.apk.`
- **`output-metadata.json`**: `applicationId = com.aistudio.zclone.mpxqkz`, `variantName = debug`, `versionCode = 1`, `versionName = 1.0`, `minSdkVersionForDexing = 24`.

## 4. Build Errors
- **None** for `debug` packaging.
- No source code, dependencies, or configurations were altered or rebuilt.

## 5. Remaining Packaging Limitations
1. **Release Signing Unavailable in Current Environment**: Building a signed Release APK/AAB requires configuring `KEYSTORE_PATH` (or `my-upload-key.jks`), `STORE_PASSWORD`, and `KEY_PASSWORD` securely at build/CI time.
2. **APK Minification Disabled in Release Template**: `isMinifyEnabled = false` in `app/build.gradle.kts` (preserved untouched per CP010 non-modification rules).
3. **Device Installation Not Tested**: No physical device or ADB runtime installation test was executed in this cloud environment.

## 6. Source ZIP Preservation Status
- **No pre-existing source `.zip` archive was present in the workspace root** (`NO_ROOT_ZIP`).
- All existing project source files, `com.fms.*` and `com.example.zclone.*` modules, Firebase configurations, `debug.keystore`, `signingConfigs`, and prior checkpoint records (`CP000`–`CP009`) remain completely intact and unmodified.

## 7. Resume Instructions
1. Read `docs/PROJECT_STATE.md`, `docs/CHECKPOINT_INDEX.md`, and `docs/CP010_APK_PACKAGING_REPORT.md` to confirm `CP010` status.
2. Proceed to the scheduled final **QA / Audit 2 & Comprehensive Security/Feature Audit** stage without modifying verified source files unless remediating an audited finding.
3. For signed **Release APK/AAB** generation outside AI Studio or via secure CI, provide `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` via environment secrets and run `gradle :app:assembleRelease`.
