const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_zclone_1";
const BOB_UID = "bob_zclone_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any ZClone user collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("cloneConfigs").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("devices").doc("dev_1").set({
      userId: ALICE_UID,
      installUuid: "uuid-12345678",
      deviceLabel: "Rogue",
      modelName: "Pixel",
      androidVersion: "14",
      appVersion: "1.0.0",
      status: "ACTIVE",
      createdAt: new Date(Date.now() - 1000),
      lastSyncAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create FREE UserProfile and update preferences, but cannot self-escalate tier or deviceLimit", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      tier: "FREE",
      deviceLimit: 3,
      activeThemeId: "dark_neon",
      languageCode: "id",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid non-privileged update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      activeThemeId: "purple_dream",
      languageCode: "en",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Self-escalation to ULTRA must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      tier: "ULTRA",
      deviceLimit: 20,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Initial create with ULTRA tier must fail
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      tier: "ULTRA",
      deviceLimit: 10,
      activeThemeId: "dark_neon",
      languageCode: "id",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Owner can manage deviceRegistry, cloneConfigs, themePresets, and securityReports with strict parity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("dev_pixel").set({
      userId: ALICE_UID,
      installUuid: "fms-dev-9a8b7c6d",
      deviceLabel: "Pixel 8 Pro",
      modelName: "Google Pixel 8 Pro",
      androidVersion: "Android 14",
      appVersion: "1.0.0",
      status: "ACTIVE",
      createdAt: now,
      lastSyncAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("cloneConfigs").doc("cfg_1").set({
      userId: ALICE_UID,
      sourcePackage: "com.whatsapp",
      targetPackage: "com.whatsapp.zclone1",
      sourceAppName: "WhatsApp",
      cloneName: "WhatsApp Clone 1",
      iconPresetId: "neon_cyan",
      iconHueShift: 180,
      changePackageName: true,
      hideFromLauncher: false,
      isolateData: true,
      strippedPermissionsCount: 2,
      compatibilityStatus: "COMPATIBLE",
      buildStatus: "SAVED",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("cloneConfigs").doc("cfg_1").update({
      buildStatus: "BUILT",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("themePresets").doc("theme_1").set({
      userId: ALICE_UID,
      name: "Cyber Dusk",
      primaryHex: "#00D2FF",
      secondaryHex: "#8B5CF6",
      surfaceHex: "#11182E",
      fontFamily: "SPACE_GROTESK",
      iconStyle: "NEON_RING",
      customTheme: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("securityReports").doc("rep_1").set({
      userId: ALICE_UID,
      packageName: "com.whatsapp",
      appName: "WhatsApp",
      riskScore: 28,
      riskLevel: "LOW",
      exportedComponentsCount: 4,
      dangerousPermissionsCount: 6,
      cleartextAllowed: false,
      debuggableFlag: false,
      certSha256Prefix: "A1B2C3D4E5F6",
      summary: "Pemeriksaan statis selesai: tidak ada flag debuggable atau cleartext.",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Subcollection list queries succeed on owner's path
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("cloneConfigs").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("devices").get());
});

test("Cross-user access, @DocumentId 'id' injection, future timestamps, and ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("cloneConfigs").doc("cfg_alice").set({
      userId: ALICE_UID,
      sourcePackage: "org.telegram.messenger",
      targetPackage: "org.telegram.zclone1",
      sourceAppName: "Telegram",
      cloneName: "Telegram Work",
      iconPresetId: "purple_dream",
      iconHueShift: 260,
      changePackageName: true,
      hideFromLauncher: false,
      isolateData: true,
      strippedPermissionsCount: 0,
      compatibilityStatus: "COMPATIBLE",
      buildStatus: "SAVED",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob cannot read, list, update, or delete Alice's data
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("cloneConfigs").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("cloneConfigs").doc("cfg_alice").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("cloneConfigs").doc("cfg_alice").delete());

  // Reject ghost field / @DocumentId id injection
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("cloneConfigs").doc("cfg_bad").set({
      id: "cfg_bad",
      userId: ALICE_UID,
      sourcePackage: "org.telegram.messenger",
      targetPackage: "org.telegram.zclone2",
      sourceAppName: "Telegram",
      cloneName: "Telegram Bad",
      iconPresetId: "purple_dream",
      iconHueShift: 260,
      changePackageName: true,
      hideFromLauncher: false,
      isolateData: true,
      strippedPermissionsCount: 0,
      compatibilityStatus: "COMPATIBLE",
      buildStatus: "SAVED",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject future timestamp spoofing
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("securityReports").doc("rep_future").set({
      userId: ALICE_UID,
      packageName: "com.example.app",
      appName: "Sample",
      riskScore: 10,
      riskLevel: "LOW",
      exportedComponentsCount: 1,
      dangerousPermissionsCount: 1,
      cleartextAllowed: false,
      debuggableFlag: false,
      certSha256Prefix: "ABCD",
      summary: "Future test",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("REVOKED devices cannot reactivate to ACTIVE, mutate metadata, or delete their record", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);

  // Creating a device directly as REVOKED must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("dev_pre_revoked").set({
      userId: ALICE_UID,
      installUuid: "fms-dev-rev00001",
      deviceLabel: "PreRevoked",
      modelName: "Pixel 8",
      androidVersion: "Android 14",
      appVersion: "1.0.0",
      status: "REVOKED",
      createdAt: now,
      lastSyncAt: now,
      updatedAt: now,
    })
  );

  // Register device as ACTIVE then revoke it
  const devRef = aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("dev_target");
  await assertSucceeds(
    devRef.set({
      userId: ALICE_UID,
      installUuid: "fms-dev-11223344",
      deviceLabel: "Pixel Target",
      modelName: "Pixel 8",
      androidVersion: "Android 14",
      appVersion: "1.0.0",
      status: "ACTIVE",
      createdAt: now,
      lastSyncAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    devRef.update({
      status: "REVOKED",
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Self-reactivation REVOKED -> ACTIVE must fail
  await assertFails(
    devRef.update({
      status: "ACTIVE",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Transition REVOKED -> INACTIVE or metadata tampering must fail
  await assertFails(
    devRef.update({
      deviceLabel: "Renamed After Revoke",
      lastSyncAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Deleting a REVOKED device record to wipe status must fail
  await assertFails(devRef.delete());

  // Deleting an INACTIVE (non-revoked) device remains allowed
  const inactiveRef = aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("dev_inactive");
  await assertSucceeds(
    inactiveRef.set({
      userId: ALICE_UID,
      installUuid: "fms-dev-55667788",
      deviceLabel: "Pixel Secondary",
      modelName: "Pixel 7",
      androidVersion: "Android 14",
      appVersion: "1.0.0",
      status: "INACTIVE",
      createdAt: now,
      lastSyncAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(inactiveRef.delete());
});

