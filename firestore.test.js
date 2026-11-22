const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const crypto = require("node:crypto");

function hashEmail(email) {
  return crypto.createHash("sha256").update(email.trim().toLowerCase()).digest("hex");
}

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_farmer_01";
const BOB_UID = "bob_farmer_02";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfilePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    fullName: "Md. Rahim Uddin",
    division: "Rajshahi",
    district: "Natore",
    upazila: "Baraigram",
    email: "rahim@example.com",
    profileCompleted: true,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validScanPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    clientScanKey: "scan_1700000000000_1",
    predictedClass: "Anthracnose",
    decisionScore: 1.42,
    marginToRunnerUp: 0.85,
    allScores: {
      Anthracnose: 1.42,
      Healthy: -0.57,
      "Nutrient Stress": -0.92,
      "Pest Damage": -1.1,
      "Sooty Mold": -0.75,
    },
    inferenceTimeMs: 42,
    scannedAtMillis: 1700000000000,
    orchardName: "North Guava Block",
    plotId: "P-04",
    notes: "Detected dark leaf margins",
    explanationMethod: "SVM_FEATURE_MAP_CAM",
    mockResult: false,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

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

test("Unauthenticated users cannot read or write profiles or scans", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("scans").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("scans")
      .doc("scan_1")
      .set(validScanPayload(ALICE_UID))
  );
});

test("Authenticated user can create, read, and partially update own profile with Division/District/Upazila", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(profileRef.get());

  // Partial update of name and cascading location fields preserves createdAt & userId
  await assertSucceeds(
    profileRef.update({
      fullName: "Md. Rahim Mia",
      division: "Dhaka",
      district: "Gazipur",
      upazila: "Sreepur",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject mutating immutable createdAt
  await assertFails(
    profileRef.update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject Shadow Update / ghost field injection
  await assertFails(
    profileRef.update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject future tampered timestamp
  await assertFails(
    profileRef.update({
      fullName: "Tampered Time",
      updatedAt: new Date(Date.now() + 120000),
    })
  );
});

test("Direct Google Sign-In user can create profile without location fields and later add location", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    profileRef.set(
      validProfilePayload(ALICE_UID, {
        division: "",
        district: "",
        upazila: "",
      })
    )
  );
  await assertSucceeds(profileRef.get());

  await assertSucceeds(
    profileRef.update({
      division: "Barishal",
      district: "Pirojpur",
      upazila: "Nesarabad (Swarupkathi)",
      updatedAt: new Date(Date.now() - 400),
    })
  );
});

test("Profile creation rejects empty fullName, oversized location, extra @DocumentId key, or identity spoofing", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Reject extra `id` key (must never be written when @DocumentId is used)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .set(validProfilePayload(ALICE_UID, { id: ALICE_UID }))
  );

  // Reject empty fullName
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .set(validProfilePayload(ALICE_UID, { fullName: "" }))
  );

  // Reject oversized division string (>80 chars)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .set(validProfilePayload(ALICE_UID, { division: "A".repeat(81) }))
  );

  // Reject writing another user's profile
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID))
  );

  // Reject spoofing userId inside own doc
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(BOB_UID))
  );
});

test("PII Isolation: authenticated user cannot read or modify another user's profile or scans", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.collection("users").doc(BOB_UID).set(validProfilePayload(BOB_UID));
    await adminDb
      .collection("users")
      .doc(BOB_UID)
      .collection("scans")
      .doc("bob_scan_1")
      .set(validScanPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).update({
      fullName: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("scans").get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("scans")
      .doc("bob_scan_1")
      .get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("scans")
      .doc("bob_scan_1")
      .delete()
  );
});

test("Authenticated owner can create, list, update metadata, and delete own synced scans", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const scansCol = aliceDb.collection("users").doc(ALICE_UID).collection("scans");

  // Unfiltered subcollection list succeeds even when empty (no existing() dereference)
  await assertSucceeds(scansCol.get());

  // Create migrated guest scan with optional calibratedConfidence & localImagePath
  await assertSucceeds(
    scansCol.doc("scan_1700000000000_1").set(
      validScanPayload(ALICE_UID, {
        calibratedConfidence: 0.94,
        localImagePath: "/data/user/0/com.aistudio.guavacare.bgvkmq/files/scans/leaf1.jpg",
      })
    )
  );

  // List query matching Kotlin repository (orderBy scannedAtMillis desc)
  await assertSucceeds(scansCol.orderBy("scannedAtMillis", "desc").get());

  // Partial metadata update (orchardName, plotId, notes, updatedAt)
  await assertSucceeds(
    scansCol.doc("scan_1700000000000_1").update({
      orchardName: "Updated Orchard",
      plotId: "Block-B",
      notes: "Applied pruning",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject mutating diagnosis result or immutable clientScanKey/createdAt
  await assertFails(
    scansCol.doc("scan_1700000000000_1").update({
      predictedClass: "Healthy",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Reject invalid leaf class name on create
  await assertFails(
    scansCol.doc("scan_bad_class").set(
      validScanPayload(ALICE_UID, {
        clientScanKey: "scan_bad_class",
        predictedClass: "UnknownDisease",
      })
    )
  );

  // Reject extra `id` field on create
  await assertFails(
    scansCol.doc("scan_extra_id").set(
      validScanPayload(ALICE_UID, {
        id: "scan_extra_id",
        clientScanKey: "scan_extra_id",
      })
    )
  );

  // Owner can delete own scan
  await assertSucceeds(scansCol.doc("scan_1700000000000_1").delete());
});

test("Email registry enforces one-email-one-account immutability and blocks email enumeration", async () => {
  const aliceEmail = "Alice.Grower@Example.com";
  const bobEmail = "bob.grower@example.com";
  const aliceHash = hashEmail(aliceEmail);
  const bobHash = hashEmail(bobEmail);
  const now = new Date(Date.now() - 1000);

  // Unauthenticated cannot read, list, or write email_registry
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("email_registry").doc(aliceHash).get());
  await assertFails(unauthDb.collection("email_registry").get());

  // Alice authenticated with her token email can check and claim her own email hash once
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: aliceEmail })
    .firestore();

  await assertSucceeds(aliceDb.collection("email_registry").doc(aliceHash).get());
  await assertSucceeds(
    aliceDb.collection("email_registry").doc(aliceHash).set({
      userId: ALICE_UID,
      provider: "password",
      createdAt: now,
    })
  );

  // Alice cannot list the collection or probe Bob's email hash (zero enumeration)
  await assertFails(aliceDb.collection("email_registry").get());
  await assertFails(aliceDb.collection("email_registry").doc(bobHash).get());
  await assertFails(
    aliceDb.collection("email_registry").doc(bobHash).set({
      userId: ALICE_UID,
      provider: "password",
      createdAt: now,
    })
  );

  // Lock is strictly immutable (cannot overwrite provider or delete)
  await assertFails(
    aliceDb.collection("email_registry").doc(aliceHash).set({
      userId: ALICE_UID,
      provider: "google",
      createdAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("email_registry").doc(aliceHash).update({
      provider: "google",
    })
  );
  await assertFails(aliceDb.collection("email_registry").doc(aliceHash).delete());

  // Another UID with the same email (e.g., second provider account) cannot overwrite Alice's lock
  const duplicateUidDb = testEnv
    .authenticatedContext(BOB_UID, { email: aliceEmail.toLowerCase() })
    .firestore();
  // Can read own token email lock to detect collision, but CANNOT overwrite it
  await assertSucceeds(duplicateUidDb.collection("email_registry").doc(aliceHash).get());
  await assertFails(
    duplicateUidDb.collection("email_registry").doc(aliceHash).set({
      userId: BOB_UID,
      provider: "google",
      createdAt: now,
    })
  );
});
