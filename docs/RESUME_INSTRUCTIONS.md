# Resume & Recovery Instructions

To resume work on **ZClone** or extend the **FMS Full Custom Ecosystem (`com.fms.*`)** in any future session:

1. **Inspect State**: Read `/docs/PROJECT_STATE.md` and `/docs/CHECKPOINT_INDEX.md` first — do not re-create `debug.keystore` or modify `signingConfigs` / `applicationId`.
2. **Verify Local Build & Tests**:
   - Run `gradle :app:testDebugUnitTest --console=plain` to verify unit & Robolectric suites.
   - Run `compile_applet` to verify APK packaging.
3. **Modify Firestore Rules Safely**:
   - Edit `/firestore.rules` and `/firestore.test.js` together.
   - Verify on the Firestore emulator (`FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js`) before calling `rpc_action(service_name="fax", method_name="DeployRules")`.
4. **Module Dependency Boundary**:
   - Keep `com.fms.*` reusable and decoupled from `com.example.zclone.*`.
   - Maintain Google Sign-In exclusively via `FMSAuthEngine` (Credential Manager) and never re-introduce `signInAnonymously()`, restricted hardware identifiers, or dynamic code loading (`.dex`/`.jar`/`.so`).
