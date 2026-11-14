const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_owner";
const BOB_UID = "bob_member";
const CHARLIE_UID = "charlie_outsider";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function ts() {
  return new Date(Date.now() - 1000);
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

test("Unauthenticated user cannot read or list messes or users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("messes").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("User profile PII isolation and shadow update rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      themeMode: "system",
      isDeactivated: false,
      createdAt: ts(),
      updatedAt: ts(),
    })
  );

  // Bob cannot read Alice's private PII profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Shadow update with extra ghost field fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(
      {
        userId: ALICE_UID,
        displayName: "Alice",
        themeMode: "dark",
        isDeactivated: false,
        isAdmin: true,
        createdAt: ts(),
        updatedAt: ts(),
      },
      { merge: true }
    )
  );
});

test("Multi-tenant mess creation, invite lookup, and cross-mess isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const batch = aliceDb.batch();
  const messRef = aliceDb.collection("messes").doc("mess_alpha");
  batch.set(messRef, {
    name: "Green Crescent Mess",
    address: "Road 4, Dhanmondi",
    currencySymbol: "৳",
    ownerId: ALICE_UID,
    managerUids: [ALICE_UID],
    assistantUids: [],
    memberUids: [ALICE_UID],
    inviteCode: "GCM2026A",
    allowMultipleManagers: true,
    availableSeats: 2,
    isArchived: false,
    closedMonths: [],
    createdAt: ts(),
    updatedAt: ts(),
  });
  batch.set(messRef.collection("members").doc(ALICE_UID), {
    messId: "mess_alpha",
    userId: ALICE_UID,
    displayName: "Alice",
    role: "owner",
    status: "active",
    memberUids: [ALICE_UID],
    joinedAt: ts(),
    updatedAt: ts(),
  });
  batch.set(aliceDb.collection("invites").doc("GCM2026A"), {
    messId: "mess_alpha",
    messName: "Green Crescent Mess",
    ownerId: ALICE_UID,
    managerUids: [ALICE_UID],
    inviteCode: "GCM2026A",
    isActive: true,
    createdAt: ts(),
    updatedAt: ts(),
  });
  await assertSucceeds(batch.commit());

  // Alice can list her messes with matching array-contains filter
  await assertSucceeds(
    aliceDb.collection("messes").where("memberUids", "array-contains", ALICE_UID).get()
  );

  // Blanket list without filter fails
  await assertFails(aliceDb.collection("messes").get());

  // Charlie (outsider) cannot read mess_alpha or its subcollections
  await assertFails(charlieDb.collection("messes").doc("mess_alpha").get());
  await assertFails(
    charlieDb
      .collection("messes")
      .doc("mess_alpha")
      .collection("expenses")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );

  // Charlie cannot list invites collection (no scraping)
  await assertFails(charlieDb.collection("invites").get());

  // Charlie CAN look up the exact invite code and submit a join request
  await assertSucceeds(charlieDb.collection("invites").doc("GCM2026A").get());
  await assertSucceeds(
    charlieDb
      .collection("messes")
      .doc("mess_alpha")
      .collection("join_requests")
      .doc(CHARLIE_UID)
      .set({
        messId: "mess_alpha",
        applicantUid: CHARLIE_UID,
        applicantName: "Charlie",
        inviteCode: "GCM2026A",
        status: "pending",
        managerUids: [ALICE_UID],
        createdAt: ts(),
        updatedAt: ts(),
      })
  );
});

test("Closed month locks meals and expenses against modifications", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    await adminDb.collection("messes").doc("mess_locked").set({
      name: "Locked Mess",
      currencySymbol: "৳",
      ownerId: ALICE_UID,
      managerUids: [ALICE_UID],
      assistantUids: [],
      memberUids: [ALICE_UID, BOB_UID],
      inviteCode: "LOCK2026",
      allowMultipleManagers: true,
      availableSeats: 0,
      isArchived: false,
      closedMonths: ["2026-10"],
      createdAt: ts(),
      updatedAt: ts(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Closed month 2026-10 meal creation fails even for owner
  await assertFails(
    aliceDb
      .collection("messes")
      .doc("mess_locked")
      .collection("meals")
      .doc("m_locked")
      .set({
        messId: "mess_locked",
        monthKey: "2026-10",
        date: "2026-10-15",
        memberUid: ALICE_UID,
        memberName: "Alice",
        breakfast: 1,
        lunch: 1,
        dinner: 1,
        totalMeals: 3,
        enteredByUid: ALICE_UID,
        memberUids: [ALICE_UID, BOB_UID],
        createdAt: ts(),
        updatedAt: ts(),
      })
  );

  // Open month 2026-11 meal creation succeeds
  await assertSucceeds(
    aliceDb
      .collection("messes")
      .doc("mess_locked")
      .collection("meals")
      .doc("m_open")
      .set({
        messId: "mess_locked",
        monthKey: "2026-11",
        date: "2026-11-14",
        memberUid: ALICE_UID,
        memberName: "Alice",
        breakfast: 1,
        lunch: 1,
        dinner: 1,
        totalMeals: 3,
        enteredByUid: ALICE_UID,
        memberUids: [ALICE_UID, BOB_UID],
        createdAt: ts(),
        updatedAt: ts(),
      })
  );

  // Regular member Bob cannot record a deposit (manager/assistant only)
  await assertFails(
    bobDb
      .collection("messes")
      .doc("mess_locked")
      .collection("deposits")
      .doc("dep_1")
      .set({
        messId: "mess_locked",
        monthKey: "2026-11",
        date: "2026-11-14",
        memberUid: BOB_UID,
        memberName: "Bob",
        amountPaisa: 500000,
        method: "cash",
        recordedByUid: BOB_UID,
        memberUids: [ALICE_UID, BOB_UID],
        createdAt: ts(),
        updatedAt: ts(),
      })
  );

  // Regular member Bob cannot auto-approve his own expense
  await assertFails(
    bobDb
      .collection("messes")
      .doc("mess_locked")
      .collection("expenses")
      .doc("exp_cheat")
      .set({
        messId: "mess_locked",
        monthKey: "2026-11",
        date: "2026-11-14",
        category: "bazar",
        description: "Rice & Fish",
        amountPaisa: 120000,
        purchasedByUid: BOB_UID,
        purchasedByName: "Bob",
        status: "approved",
        memberUids: [ALICE_UID, BOB_UID],
        createdAt: ts(),
        updatedAt: ts(),
      })
  );

  // Regular member Bob CAN submit a pending expense
  await assertSucceeds(
    bobDb
      .collection("messes")
      .doc("mess_locked")
      .collection("expenses")
      .doc("exp_pending")
      .set({
        messId: "mess_locked",
        monthKey: "2026-11",
        date: "2026-11-14",
        category: "bazar",
        description: "Vegetables",
        amountPaisa: 45000,
        purchasedByUid: BOB_UID,
        purchasedByName: "Bob",
        status: "pending",
        memberUids: [ALICE_UID, BOB_UID],
        createdAt: ts(),
        updatedAt: ts(),
      })
  );
});
