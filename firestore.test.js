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

test("Unauthenticated user: cannot read subjects or questions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("subjects").get());
  await assertFails(unauthDb.collection("questions").get());
});

test("Authenticated user: can create and query own subject", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("subjects").doc("subj_1").set({
      userId: ALICE_UID,
      subjectName: "Science",
      description: "Physics, Chemistry, Biology",
      colorHex: "#7C3AED",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("subjects").where("userId", "==", ALICE_UID).get()
  );
});

test("Authenticated user: cannot read another user's question", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("questions").doc("bob_q1").set({
      userId: BOB_UID,
      subjectId: "subj_1",
      subjectName: "Science",
      chapterId: "chap_1",
      chapterName: "Physics",
      questionNumber: 1,
      questionText: "What is the SI unit of force?",
      options: ["Joule", "Newton", "Watt", "Pascal"],
      correctAnswer: "B",
      explanation: "Newton is the SI unit of force.",
      sourceReference: "Physics Notes",
      sourceImage: "",
      answerConfidence: "VERIFIED",
      difficulty: "EASY",
      isBookmarked: false,
      collectionTag: "Mechanics",
      lastAttemptCorrect: false,
      attemptCount: 0,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("questions").doc("bob_q1").get());
});

test("Shadow update test: rejects ghost field on question update", async () => {
  const now = new Date();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("questions").doc("alice_q1").set({
      userId: ALICE_UID,
      subjectId: "subj_1",
      subjectName: "Science",
      chapterId: "chap_1",
      chapterName: "Physics",
      questionNumber: 1,
      questionText: "What is the SI unit of force?",
      options: ["Joule", "Newton", "Watt", "Pascal"],
      correctAnswer: "B",
      explanation: "Newton is the SI unit of force.",
      sourceReference: "Physics Notes",
      sourceImage: "",
      answerConfidence: "VERIFIED",
      difficulty: "EASY",
      isBookmarked: false,
      collectionTag: "Mechanics",
      lastAttemptCorrect: false,
      attemptCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    aliceDb.collection("questions").doc("alice_q1").update({
      isBookmarked: true,
      ghostField: "hacked",
      updatedAt: new Date(),
    })
  );
});

test("Query trust test: fails list query without userId filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("questions").get());
});
