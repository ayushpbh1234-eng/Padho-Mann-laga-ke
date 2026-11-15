const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

function validSessionPayload(uid) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    day: "2026-11-14",
    subject: "Maths",
    sound: "Barish 🌧",
    durationMinutes: 25,
    done: true,
    rewardText: "⭐ Chhota kaam bhi aage badhata hai.",
    distractionReasons: [],
    distractionCount: 0,
    createdAt: now,
    updatedAt: now,
  };
}

function validProfilePayload(uid) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    onboardingCompleted: true,
    currentStudy: "B.Tech CSE Sem 4",
    syllabusText: "Unit 1: ER Model, Unit 2: Normalization",
    studySummary: "B.Tech CSE Sem 4 focus plan ready.",
    syllabusTopics: ["DBMS: ER Model", "DBMS: Normalization"],
    customChallenges: ["DBMS ka ek ER diagram draw karo."],
    mood: "energetic",
    challengeIdx: 1,
    alarmTime: "18:30",
    subjectsText: "DBMS, OS, Computer Networks",
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user cannot read or write user profiles or sessions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("sessions").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc("sess_1")
      .set(validSessionPayload(ALICE_UID))
  );
});

test("Authenticated user can create, read, and update their own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(profileRef.set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(profileRef.get());
  await assertSucceeds(
    profileRef.update({
      mood: "thaki",
      challengeIdx: 2,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Authenticated user cannot list all users or access another user's profile/sessions", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc("sess_1")
      .set(validSessionPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("sessions").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc("sess_2")
      .set(validSessionPayload(BOB_UID))
  );
});

test("Authenticated user can create and list their own study sessions", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sessionsCol = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("sessions");
  await assertSucceeds(sessionsCol.doc("sess_1").set(validSessionPayload(ALICE_UID)));
  await assertSucceeds(sessionsCol.get());
});

test("Shadow update with ghost field and invalid mood/day are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .set({
        ...validProfilePayload(ALICE_UID),
        isAdmin: true,
      })
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .set({
        ...validProfilePayload(ALICE_UID),
        mood: "invalid_mood",
      })
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc("sess_bad")
      .set({
        ...validSessionPayload(ALICE_UID),
        day: "not-a-date",
      })
  );
});
