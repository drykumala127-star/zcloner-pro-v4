# ZCloner PRO & FMS Engine Core V1 — Security Master Report

**Product:** `ZCloner PRO` (`com.fms.zclone.enginecoremode`) · **Attribution:** `Copyright FMS Clone Engine V1`  
**Active Checkpoint:** `CP010M-04`

## 1. Cryptographic & Key Management Controls
- **Encryption at Rest**: `FMSSecurityStorage` encrypts sensitive local entries (including installation UUID) using `AndroidKeyStore` AES-256-GCM (`FMSKeyManager`).
- **APK Signing Isolation & KeyStore-Wrapped PKCS#12 (`P1.A`)**: No private signing keys or plaintext passphrases are bundled in APK source, distribution archives, or persisted in plaintext:
  - `FMSKeyManager.getOrCreateSoftwareSigningIdentity()` generates a 32-byte `SecureRandom` per-device Base64 passphrase wrapped with `AndroidKeyStore` AES-256-GCM (`fms_p12_pass_wrap_v1`), stored encrypted in app-private `.fms_p12_pass_<alias>.enc` (`GCM:` payload) and mirrored into `FMSSecurityStorage("fms_keystore_vault")`. *(Non-Android JVM unit-test fallback `JVM_LOCAL:` activates only when `AndroidKeyStore` provider is absent in Robolectric/JVM environments.)*
  - **Non-Destructive Legacy Migration & Atomic Recovery**: Pre-patch `fms_local_signing.p12` files are unlocked once using internal migration logic, re-encrypted with the fresh wrapped passphrase into `fms_local_signing.p12.tmp`, verified for identical RSA keypair & certificate chain, persisted into `.fms_p12_pass_<alias>.enc`, and atomically swapped via `.bak` backup (`fms_local_signing.p12.bak` auto-recovers interrupted writes). All `CharArray` passphrases are zeroed in `finally`, and failed unlock aborts loudly rather than overwriting an existing keypair (preserving clone update continuity & SHA-256 fingerprint).
- **Debug Keystore Preservation**: Runtime `debug.keystore` and `signingConfigs` in `app/build.gradle.kts` remain untouched (`ac:19:d8:df:b9:3f:cc:87:ff:32:f1:0a:91:6c:68:e3:ee:74:9a:c1`).

## 2. Input Validation, Path Safety & Logging Redaction
- **Zip-Slip Protection**: `FMSIntegrityEngine.resolveZipEntrySafe`, `FmsDependencyGraphAnalyzer`, `FmsBinaryVersionDiff`, and `FMSRepackager` reject `..` traversal and leading `/` or `\` entry names.
- **Backup JSON Guard**: `FMSBackupEngine` enforces a strict `2 MB` payload cap (`MAX_BACKUP_BYTES`), schema version validation (`schemaVersion == 1`), item count cap (`<= 500`), and package syntax checks before import.
- **Logging & Error Redaction (`P0.C`)**: `FmsLogger.sanitizeMessage()` and `FmsErrorEngine.record()` automatically redact key/value & JSON secrets (`token`, `id_token`, `access_token`, `refresh_token`, `secret`, `password`, `passphrase`, `keystore_pass`, `apikey`/`api_key`, `bearer`, `privatekey`/`private_key`, `credential`), raw `Bearer ...` headers, `eyJ...` JWTs, and `-----BEGIN ... PRIVATE KEY-----` PEM blocks. Direct `android.util.Log.d/i/w/e/v` calls across `app/src/main/java` are `0` (verified by grep audit).
- **Environment Secrets Handling (`P1.B`)**: `/.env` is not created on disk; `Secrets Gradle Plugin` reads non-secret fallback keys from `/.env.example`, and any secret runtime credentials belong exclusively in the AI Studio Secrets panel. Zero `.env`, `.p12`, `.jks`, `.keystore`, or private key entries exist in `app-debug.apk`.

## 3. Firebase Zero-Trust & Auth Enforcement
- **Google Sign-In Exclusivity**: Uses Jetpack Credential Manager (`GetSignInWithGoogleOption` / `GetGoogleIdOption`, 1 option per request) + Firebase Auth (`zclone-fms`); `signInAnonymously()` and email/password auth are absent (verified by static grep audit).
- **Firestore Ownership & Anti-Escalation**: `firestore.rules` isolates all collections under `/users/{userId}/...`, validates schema/types/timestamps, rejects client `tier` or `deviceLimit` escalation on create/update, blocks `REVOKED` self-reactivation and `REVOKED` device self-deletion, and passed `5/5` emulator tests in `firestore.test.js` (`CP010M-02`).
- **Pending External Deployment**: Live `deploy_firebase` (`firestore.rules`) remains `BLOCKED` pending project owner permissions; Debug SHA-1 (`AC:19:D8:...`) must be registered in Firebase Console alongside `41:11:...` before live hardware Google Sign-In.

## 4. Play Policy & Permissions
- Uses narrow `<queries>` (`ACTION_MAIN` + `CATEGORY_LAUNCHER`) instead of `QUERY_ALL_PACKAGES`.
- Uses Storage Access Framework (`OpenDocument` / `CreateDocument`) instead of broad external storage permissions.
- `android:allowBackup="false"`, `FileProvider` `exported="false"` with `grantUriPermissions="true"`.
