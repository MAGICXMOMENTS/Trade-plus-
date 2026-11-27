const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_trader_1";
const BOB_UID = "bob_trader_2";

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

test("Unauthenticated users cannot read or write any user-scoped collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("positions").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("watchlist").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("riskAlerts").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("profile").doc("settings").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("positions").doc("pos_1").set({
      userId: ALICE_UID,
      symbol: "NVDA",
      exchange: "NASDAQ",
      companyName: "NVIDIA Corp",
      shares: 10,
      avgEntryPrice: 120.5,
      stopLossPrice: 110.0,
      takeProfitPrice: 150.0,
      maxRiskPercent: 2.0,
      notes: "AI momentum",
      status: "ACTIVE",
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Owner can create, list, partially update, and delete positions; closed positions are locked against updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const posRef = aliceDb.collection("users").doc(ALICE_UID).collection("positions").doc("pos_nvda");

  await assertSucceeds(
    posRef.set({
      userId: ALICE_UID,
      symbol: "NVDA",
      exchange: "NASDAQ",
      companyName: "NVIDIA Corp",
      shares: 12.5,
      avgEntryPrice: 132.4,
      stopLossPrice: 121.0,
      takeProfitPrice: 160.0,
      maxRiskPercent: 2.5,
      notes: "Breakout above EMA21",
      status: "ACTIVE",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("positions").get());

  // Valid partial update preserving immutable fields
  await assertSucceeds(
    posRef.update({
      stopLossPrice: 125.0,
      takeProfitPrice: 165.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    posRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost/id field on create
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("positions").doc("pos_bad").set({
      id: "pos_bad",
      userId: ALICE_UID,
      symbol: "AAPL",
      exchange: "NASDAQ",
      companyName: "Apple Inc",
      shares: 5,
      avgEntryPrice: 220,
      stopLossPrice: 205,
      takeProfitPrice: 250,
      maxRiskPercent: 2.0,
      notes: "",
      status: "ACTIVE",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Transition to CLOSED succeeds, then subsequent update is locked
  await assertSucceeds(
    posRef.update({
      status: "CLOSED",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    posRef.update({
      notes: "Should fail after close",
      updatedAt: new Date(Date.now() - 100),
    })
  );
  await assertSucceeds(posRef.delete());
});

test("Cross-user access to positions, watchlist, riskAlerts, and profile is denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const t = new Date(Date.now() - 10000);
    await db.collection("users").doc(BOB_UID).collection("positions").doc("bob_pos").set({
      userId: BOB_UID,
      symbol: "MSFT",
      exchange: "NASDAQ",
      companyName: "Microsoft Corp",
      shares: 4,
      avgEntryPrice: 410,
      stopLossPrice: 390,
      takeProfitPrice: 460,
      maxRiskPercent: 1.5,
      notes: "",
      status: "ACTIVE",
      createdAt: t,
      updatedAt: t,
    });
    await db.collection("users").doc(BOB_UID).collection("watchlist").doc("MSFT").set({
      userId: BOB_UID,
      symbol: "MSFT",
      exchange: "NASDAQ",
      companyName: "Microsoft Corp",
      sector: "Technology",
      createdAt: t,
      updatedAt: t,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("positions").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("positions").doc("bob_pos").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("positions").doc("bob_pos").update({
      shares: 100,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("watchlist").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("riskAlerts").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("profile").doc("settings").get());
});

test("Watchlist, RiskAlertRule, and RiskProfile valid payloads and bounds enforcement", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const t = new Date(Date.now() - 5000);

  // Watchlist
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("watchlist").doc("RELIANCE_NS").set({
      userId: ALICE_UID,
      symbol: "RELIANCE.NS",
      exchange: "NSE",
      companyName: "Reliance Industries",
      sector: "Energy",
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("watchlist").get());

  // RiskAlertRule
  const alertRef = aliceDb.collection("users").doc(ALICE_UID).collection("riskAlerts").doc("alert_1");
  await assertSucceeds(
    alertRef.set({
      userId: ALICE_UID,
      symbol: "NVDA",
      alertType: "RSI_OVERBOUGHT",
      thresholdValue: 72.0,
      active: true,
      notes: "Trim 25% if RSI > 72",
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertSucceeds(
    alertRef.update({
      active: false,
      thresholdValue: 75.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  // Reject invalid enum
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("riskAlerts").doc("alert_bad").set({
      userId: ALICE_UID,
      symbol: "NVDA",
      alertType: "INVALID_TYPE",
      thresholdValue: 70,
      active: true,
      notes: "",
      createdAt: t,
      updatedAt: t,
    })
  );

  // RiskProfile
  const profileRef = aliceDb.collection("users").doc(ALICE_UID).collection("profile").doc("settings");
  await assertSucceeds(
    profileRef.set({
      userId: ALICE_UID,
      riskTolerance: "MODERATE",
      accountCapital: 50000.0,
      defaultRiskPerTradePct: 1.5,
      maxPortfolioDrawdownPct: 10.0,
      preferredTimeframe: "3M",
      createdAt: t,
      updatedAt: t,
    })
  );
  await assertSucceeds(
    profileRef.update({
      accountCapital: 65000.0,
      riskTolerance: "AGGRESSIVE",
      preferredTimeframe: "5Y",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  // Reject future timestamp
  await assertFails(
    profileRef.update({
      accountCapital: 70000.0,
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});
