# Android Non-Root Compatibility & Platform Behavior Report

## 1. Supported Android Versions
- **minSdk**: 24 (Android 7.0 Nougat)
- **targetSdk / compileSdk**: 36 (Android 16)
- **Window Size Classes**: Adaptive `Compact` (< 600dp, `NavigationBar`), `Medium` (600–840dp, `NavigationRail`), and `Expanded` (> 840dp, `NavigationRail` + centered max-width pane) with edge-to-edge `WindowInsets.safeDrawing`.

## 2. Capability Truthfulness Matrix (Standard Unrooted Devices)
| Capability | Status | Platform Reality & Mitigation |
| :--- | :--- | :--- |
| APK Structure, Manifest, Signature & Resource Inspection | **VERIFIED / IMPLEMENTED** | Real `ZipFile`, binary AXML parser (`FMSManifestEngine`), and X.509 SHA-256 fingerprint extraction (`FMSAPKInspector`) |
| Package Rename, Label Customization, Permission Stripping, Hide Launcher, Debuggable Disable | **IMPLEMENTED** | Binary AXML StringPool & chunk serializer (`FMSManifestEngine`) + authority rewriting |
| App Icon Color/Hue Transform & Clone Badge | **IMPLEMENTED** | Raster PNG `ColorMatrix` hue rotation + cyan clone badge (`FMSIconEngine`) |
| APK Repackaging & Local User Signing | **IMPLEMENTED** | Zip-Slip guarded `FMSRepackager` preserving `resources.arsc` alignment/STORED mode + local RSA-2048 JAR/PKCS#7 v1 signer (`FMSSignatureManager`) |
| Installing & Exporting Cloned APKs | **IMPLEMENTED** | System package installer via `FileProvider` (`REQUEST_INSTALL_PACKAGES` user prompt) & SAF `CreateDocument` export |
| Split APKs / App Bundles | **LIMITED** | Detected automatically by `FMSCompatibilityEngine`; standalone single-APK cloning only supports base APKs without split-merge bundles |
| App Data Isolation | **LIMITED** | New `packageName` yields an independent Android `/data/data/<targetPackage>` sandbox; third-party `/data/data` export/import is blocked by OS sandbox without root |
| Hardware ID Spoofing (IMEI/Serial/MAC), Play Integrity / DRM Bypass, Root Hiding | **UNSUPPORTED** | Explicitly prohibited by security policy & Play compliance; replaced by random `FMSSecurityStorage` Install UUID (`FMSDeviceManager`) |
