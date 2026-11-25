const { test, after } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const Module = require("node:module");

let fft;
const originalLoad = Module._load;
let usingShims = false;

try {
  require.resolve("firebase-functions-test");
  require.resolve("firebase-functions");
  require.resolve("firebase-admin");
  fft = require("firebase-functions-test")();
} catch (_) {
  usingShims = true;
  class HttpsError extends Error {
    constructor(code, message) {
      super(message);
      this.code = code;
    }
  }
  const makeTrigger = (fn, triggerType) => {
    const wrapped = (data, context) => fn(data, context);
    wrapped.__triggerType = triggerType;
    wrapped.__handler = fn;
    return wrapped;
  };
  const regionShim = () => ({
    https: { onCall: (fn) => makeTrigger(fn, "callable") },
    auth: { user: () => ({ onCreate: (fn) => makeTrigger(fn, "auth.onCreate") }) },
  });
  const functionsShim = {
    region: regionShim,
    runWith: () => {
      throw new Error("runWith secrets must not be used in Strict Free Demo Mode");
    },
    https: { HttpsError },
  };
  const adminShim = {
    initializeApp: () => {},
    firestore: Object.assign(
      () => ({
        collection: () => ({ doc: () => ({}) }),
      }),
      { FieldValue: { serverTimestamp: () => "SERVER_TIMESTAMP" } }
    ),
  };
  Module._load = function (request, parent, isMain) {
    if (request === "firebase-functions") return functionsShim;
    if (request === "firebase-admin") return adminShim;
    return originalLoad.call(this, request, parent, isMain);
  };
  fft = {
    wrap: (fn) => fn.__handler || fn,
    cleanup: () => {
      Module._load = originalLoad;
    },
  };
}

const backend = require("./lib/index.js");

after(() => {
  fft.cleanup();
});

test("Server source and compiled bundle contain zero paid AI SDKs, keys, or calls", () => {
  const compiled = fs.readFileSync(path.join(__dirname, "lib/index.js"), "utf8");
  const source = fs.readFileSync(path.join(__dirname, "index.ts"), "utf8");
  const pkg = fs.readFileSync(path.join(__dirname, "package.json"), "utf8");

  for (const content of [compiled, source, pkg]) {
    assert.equal(content.includes("@google/genai"), false);
    assert.equal(content.includes("GoogleGenAI"), false);
    assert.equal(content.includes("GEMINI_API_KEY"), false);
    assert.equal(content.includes("interactions.create"), false);
    assert.equal(content.includes("generateContent"), false);
  }
  assert.equal(fs.existsSync(path.join(__dirname, "lib/index.js.map")), false);
  assert.equal(backend.STRICT_FREE_DEMO_MODE, true);
});

test("generateMusic rejects unauthenticated callers", async () => {
  const wrapped = fft.wrap(backend.generateMusic);
  await assert.rejects(
    async () => {
      await wrapped(
        {
          title: "Praise",
          prompt: "Uplifting",
          lyrics: "Hallelujah",
          genre: "Gospel",
          modelId: "demo-full",
          taskId: "valid-task-id-12345678901234",
          isDemo: false,
        },
        { auth: null }
      );
    },
    (err) => err && err.code === "unauthenticated"
  );
});

test("generateMusic enforces server demo mode even if client sends isDemo=false or omits isDemo", async () => {
  const wrapped = fft.wrap(backend.generateMusic);
  const payload = {
    title: "Praise",
    prompt: "Uplifting",
    lyrics: "Hallelujah",
    genre: "Gospel",
    modelId: "demo-full",
    taskId: "valid-task-id-12345678901234",
  };

  const resFalse = await wrapped({ ...payload, isDemo: false }, { auth: { uid: "user_1" } });
  assert.equal(resFalse.isDemo, true);
  assert.equal(resFalse.strictDemoMode, true);
  assert.ok(resFalse.audioUrl.startsWith("https://"));

  const resOmitted = await wrapped(payload, { auth: { uid: "user_1" } });
  assert.equal(resOmitted.isDemo, true);
  assert.equal(resOmitted.strictDemoMode, true);
});

test("generateLyrics enforces server demo mode even if client sends isDemo=false and supports English, Twi, Akan, Ga, Ewe", async () => {
  const wrapped = fft.wrap(backend.generateLyrics);
  for (const language of ["English", "Twi", "Akan", "Ga", "Ewe"]) {
    const res = await wrapped(
      { topic: "Grace", genre: "Gospel", mood: "Joyful", language, isDemo: false },
      { auth: { uid: "user_1" } }
    );
    assert.equal(res.isDemo, true);
    assert.equal(res.strictDemoMode, true);
    assert.ok(res.lyrics.includes(language));
    assert.ok(res.lyrics.includes("Strict Free Demo Mode"));
  }
});

test("generateLyrics rejects unauthenticated callers", async () => {
  const wrapped = fft.wrap(backend.generateLyrics);
  await assert.rejects(
    async () => {
      await wrapped(
        { topic: "Grace", genre: "Gospel", mood: "Joyful", language: "Twi", isDemo: false },
        { auth: null }
      );
    },
    (err) => err && err.code === "unauthenticated"
  );
});
