# Dependency, Supply-Chain & License Audit

## 1. Active Gradle Dependencies (`app/build.gradle.kts` & `gradle/libs.versions.toml`)
| Library / Component | Version / BoM | License | Audit Verdict | Usage Justification |
| :--- | :--- | :--- | :--- | :--- |
| `androidx.core:core-ktx` | `1.17.0` | Apache 2.0 | APPROVED | Core Android KTX extensions |
| `androidx.lifecycle:*` | `2.10.0` | Apache 2.0 | APPROVED | Lifecycle-aware `StateFlow` collection & ViewModel |
| `androidx.activity:activity-compose` | `1.13.0` | Apache 2.0 | APPROVED | Edge-to-edge Activity, `BackHandler`, SAF launchers |
| `androidx.compose:*` & Material 3 | BoM `2024.09.00` | Apache 2.0 | APPROVED | Jetpack Compose UI & Material 3 components |
| `com.google.firebase:firebase-bom` (`firebase-firestore`, `firebase-auth`) | `35.1.0` | Apache 2.0 | APPROVED | Cloud Firestore sync & Firebase Auth (Google Sign-In) |
| `androidx.credentials` & `googleid` | `1.5.0` / `1.1.1` | Apache 2.0 | APPROVED | Jetpack Credential Manager Google Sign-In |
| `junit` / `robolectric` | `4.13.2` / `4.16.1` | EPL 1.0 / MIT | APPROVED (test only) | Local JVM & Robolectric unit verification |

## 2. Commented-Out / Excluded Dependencies (APK Size Optimization)
- `androidx.room:*` (replaced by `FMSDatabaseEngine` over Android SQLite)
- `firebase-ai`, `firebase-appcheck-*`, `retrofit`, `moshi`, `okhttp` (unused in ZClone)

## 3. Bundled Fonts (`app/src/main/res/font/`)
- `space_grotesk.ttf` — SIL Open Font License 1.1 (OFL) — APPROVED
- `plus_jakarta_sans.ttf` — SIL Open Font License 1.1 (OFL) — APPROVED
- `jetbrains_mono.ttf` — SIL Open Font License 1.1 (OFL) — APPROVED

## 4. Supply-Chain & Binary Policy
- Zero prebuilt third-party cloning binaries, `.dex`/`.jar`/`.so` dynamic code loaders, or hardcoded private signing keys.
- Cloned APK signing uses a per-user RSA-2048 PKCS#12 keystore generated on device in app-private storage (`FMSKeyManager`).
