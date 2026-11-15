const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const CHILD_UID = "child_alice_123";
const PARENT_UID = "parent_bob_456";
const STRANGER_UID = "stranger_eve_789";

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

function validSessionPayload(uid, parentUids = []) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    authorizedParentUids: parentUids,
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

function validProfilePayload(uid, role = "child") {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    role,
    displayName: role === "child" ? "Aarav" : "Papa",
    onboardingCompleted: true,
    currentStudy: "Class 10 Boards",
    syllabusText: "Unit 1: Trigonometry",
    studySummary: "Class 10 focus plan ready.",
    syllabusTopics: ["Maths: Trigonometry"],
    customChallenges: ["Maths ka 1 question solve karo."],
    mood: "energetic",
    challengeIdx: 1,
    alarmTime: "18:30",
    subjectsText: "Maths, Science",
    createdAt: now,
    updatedAt: now,
  };
}

function validDeviceStatePayload(childUid) {
  const now = new Date(Date.now() - 1000);
  return {
    childUid,
    childName: "Aarav",
    deviceModel: "Pixel 8",
    androidVersion: "Android 15",
    batteryPercent: 82,
    isCharging: false,
    usageAccessGranted: true,
    notificationsGranted: true,
    supervisionServiceRunning: true,
    currentForegroundApp: "Focus Garden",
    currentForegroundPackage: "com.aistudio.focusgarden.kxmzpq",
    totalPhoneScreenMinutesToday: 95,
    isFocusSessionActive: false,
    activeFocusSubject: "",
    todayStudyMinutes: 50,
    todayCompletedSessions: 2,
    streakDays: 3,
    recentBlockedAttempt: "",
    installedApps: [
      { packageName: "com.google.android.youtube", appName: "YouTube", minutesToday: 42 },
    ],
    createdAt: now,
    updatedAt: now,
  };
}

function validParentControlsPayload(childUid, parentUid) {
  const now = new Date(Date.now() - 1000);
  return {
    childUid,
    parentUid,
    isPhoneLocked: false,
    lockMessage: "Abhi padhai ka time hai!",
    dailyScreenLimitMinutes: 120,
    bonusMinutesToday: 0,
    bonusDay: "2026-11-14",
    bedtimeEnabled: true,
    bedtimeStart: "22:00",
    bedtimeEnd: "06:00",
    blockedPackages: ["com.google.android.youtube"],
    studyOnlyMode: false,
    parentNudgeText: "",
    parentNudgeNonce: 0,
    parentPin: "1234",
    assignedSubject: "",
    assignedDurationMinutes: 0,
    assignedAlarmTime: "",
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user cannot read or write any Focus Garden or family documents", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(CHILD_UID).get());
  await assertFails(unauthDb.collection("pairingCodes").doc("FG1234").get());
  await assertFails(
    unauthDb.collection("familyLinks").doc(`${PARENT_UID}_${CHILD_UID}`).get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(CHILD_UID)
      .collection("deviceState")
      .doc("current")
      .get()
  );
});

test("Authenticated user can manage their own profile and study sessions", async () => {
  const childDb = testEnv.authenticatedContext(CHILD_UID).firestore();
  const profileRef = childDb.collection("users").doc(CHILD_UID);
  await assertSucceeds(profileRef.set(validProfilePayload(CHILD_UID)));
  await assertSucceeds(profileRef.get());
  await assertSucceeds(
    profileRef.collection("sessions").doc("sess_1").set(validSessionPayload(CHILD_UID))
  );
  await assertSucceeds(profileRef.collection("sessions").get());
});

test("Parent can pair via 6-char pairingCode, read child phone telemetry, lock child phone, and block child tamper", async () => {
  const childDb = testEnv.authenticatedContext(CHILD_UID).firestore();
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  const now = new Date(Date.now() - 1000);
  const code = "FG7890";

  // Child creates profile + pairing code + device telemetry
  await assertSucceeds(
    childDb.collection("users").doc(CHILD_UID).set(validProfilePayload(CHILD_UID, "child"))
  );
  await assertSucceeds(
    childDb.collection("pairingCodes").doc(code).set({
      code,
      childUid: CHILD_UID,
      childName: "Aarav",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    childDb
      .collection("users")
      .doc(CHILD_UID)
      .collection("deviceState")
      .doc("current")
      .set(validDeviceStatePayload(CHILD_UID))
  );
  await assertSucceeds(
    childDb
      .collection("users")
      .doc(CHILD_UID)
      .collection("sessions")
      .doc("sess_shared")
      .set(validSessionPayload(CHILD_UID, [PARENT_UID]))
  );

  // Stranger cannot link without pairing code or read child telemetry
  await assertFails(
    strangerDb
      .collection("familyLinks")
      .doc(`${STRANGER_UID}_${CHILD_UID}`)
      .set({
        parentUid: STRANGER_UID,
        childUid: CHILD_UID,
        parentName: "Eve",
        childName: "Aarav",
        pairingCode: "BAD000",
        status: "active",
        createdAt: now,
        updatedAt: now,
      })
  );
  await assertFails(
    strangerDb
      .collection("users")
      .doc(CHILD_UID)
      .collection("deviceState")
      .doc("current")
      .get()
  );

  // Parent claims code and creates familyLink atomically
  const batch = parentDb.batch();
  batch.set(parentDb.collection("familyLinks").doc(`${PARENT_UID}_${CHILD_UID}`), {
    parentUid: PARENT_UID,
    childUid: CHILD_UID,
    parentName: "Papa",
    childName: "Aarav",
    pairingCode: code,
    status: "active",
    createdAt: now,
    updatedAt: now,
  });
  batch.update(parentDb.collection("pairingCodes").doc(code), {
    active: false,
    usedByParentUid: PARENT_UID,
    updatedAt: new Date(Date.now() - 500),
  });
  await assertSucceeds(batch.commit());

  // Linked parent can read child profile, live phone telemetry, and shared sessions
  await assertSucceeds(parentDb.collection("users").doc(CHILD_UID).get());
  await assertSucceeds(
    parentDb
      .collection("users")
      .doc(CHILD_UID)
      .collection("deviceState")
      .doc("current")
      .get()
  );
  await assertSucceeds(
    parentDb
      .collection("users")
      .doc(CHILD_UID)
      .collection("sessions")
      .where("authorizedParentUids", "array-contains", PARENT_UID)
      .get()
  );

  // Parent creates and updates parental control rules (locks child phone + blocks YouTube)
  const controlsRef = parentDb
    .collection("users")
    .doc(CHILD_UID)
    .collection("parentControls")
    .doc("current");
  await assertSucceeds(controlsRef.set(validParentControlsPayload(CHILD_UID, PARENT_UID)));
  await assertSucceeds(
    controlsRef.update({
      isPhoneLocked: true,
      studyOnlyMode: true,
      parentNudgeText: "Beta phone rakho aur padhai karo!",
      parentNudgeNonce: 1,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Child can read parentControls, but CANNOT unlock phone or disable studyOnlyMode
  const childControlsRef = childDb
    .collection("users")
    .doc(CHILD_UID)
    .collection("parentControls")
    .doc("current");
  await assertSucceeds(childControlsRef.get());
  await assertFails(
    childControlsRef.update({
      isPhoneLocked: false,
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertFails(
    childControlsRef.update({
      blockedPackages: [],
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Stranger STILL cannot read or modify child's controls
  await assertFails(
    strangerDb
      .collection("users")
      .doc(CHILD_UID)
      .collection("parentControls")
      .doc("current")
      .get()
  );

  // Child can publish consented screenShare frames; linked parent can read; parent/stranger cannot write
  const childScreenRef = childDb
    .collection("users")
    .doc(CHILD_UID)
    .collection("screenShare")
    .doc("current");
  await assertSucceeds(
    childScreenRef.set({
      childUid: CHILD_UID,
      isSharing: true,
      frameBase64: "ZmFrZV9qcGVnX2RhdGE=",
      frameWidth: 360,
      frameHeight: 720,
      frameSeq: 1,
      captureMode: "full_screen",
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 50),
    })
  );
  await assertSucceeds(
    parentDb
      .collection("users")
      .doc(CHILD_UID)
      .collection("screenShare")
      .doc("current")
      .get()
  );
  await assertFails(
    parentDb
      .collection("users")
      .doc(CHILD_UID)
      .collection("screenShare")
      .doc("current")
      .update({
        isSharing: false,
        updatedAt: new Date(Date.now() - 10),
      })
  );
  await assertFails(
    strangerDb
      .collection("users")
      .doc(CHILD_UID)
      .collection("screenShare")
      .doc("current")
      .get()
  );
});

test("Shadow update with ghost field and invalid day/role are rejected", async () => {
  const childDb = testEnv.authenticatedContext(CHILD_UID).firestore();
  await assertFails(
    childDb
      .collection("users")
      .doc(CHILD_UID)
      .set({
        ...validProfilePayload(CHILD_UID),
        isAdmin: true,
      })
  );
  await assertFails(
    childDb
      .collection("users")
      .doc(CHILD_UID)
      .set({
        ...validProfilePayload(CHILD_UID),
        role: "superadmin",
      })
  );
  await assertFails(
    childDb
      .collection("users")
      .doc(CHILD_UID)
      .collection("sessions")
      .doc("sess_bad")
      .set({
        ...validSessionPayload(CHILD_UID),
        day: "not-a-date",
      })
  );
});
