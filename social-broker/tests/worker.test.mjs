import test from "node:test";
import assert from "node:assert/strict";
import { generateKeyPairSync, sign } from "node:crypto";
import {
  handleWorkerRequest,
  isAllowedBrokerRedirectUri,
  resetJwksCacheForTests,
  verifyFirebaseIdToken,
} from "../src/worker.ts";

function b64url(input) {
  return Buffer.from(input)
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

function createSignedJwt({ privateKey, kid = "kid-1", headerExtra = {}, payload }) {
  const header = { alg: "RS256", typ: "JWT", kid, ...headerExtra };
  const body = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(payload))}`;
  const sig = sign("RSA-SHA256", Buffer.from(body), privateKey);
  return `${body}.${b64url(sig)}`;
}

const TEST_ENV = {
  FIREBASE_PROJECT_ID: "gen-lang-client-0518223416",
  APP_REDIRECT_SCHEME: "com.aistudio.aimusic.wltv.jqr8x",
  APP_REDIRECT_HOST: "oauth2redirect",
  META_GRAPH_VERSION: "v21.0",
  META_APP_ID: "meta-app-1",
  META_APP_SECRET: "meta-secret-1",
  TIKTOK_CLIENT_KEY: "tt-key-1",
  TIKTOK_CLIENT_SECRET: "tt-secret-1",
};

test("isAllowedBrokerRedirectUri enforces exact HTTPS origin and callback path", () => {
  const reqUrl = new URL("https://broker.example.workers.dev/oauth/meta/exchange");
  assert.equal(
    isAllowedBrokerRedirectUri(reqUrl, "https://broker.example.workers.dev/oauth/meta/callback", "meta"),
    true
  );
  assert.equal(
    isAllowedBrokerRedirectUri(reqUrl, "https://evil.example.com/oauth/meta/callback", "meta"),
    false
  );
  assert.equal(
    isAllowedBrokerRedirectUri(reqUrl, "http://broker.example.workers.dev/oauth/meta/callback", "meta"),
    false
  );
  assert.equal(
    isAllowedBrokerRedirectUri(reqUrl, "https://broker.example.workers.dev/oauth/tiktok/callback", "meta"),
    false
  );
});

test("verifyFirebaseIdToken checks iat, auth_time, exp, aud, iss, and propagates 503 on JWKS failure", async () => {
  resetJwksCacheForTests();
  const { publicKey, privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const jwk = publicKey.export({ format: "jwk" });
  jwk.kid = "kid-1";
  const nowMs = Date.now();
  const nowSec = Math.floor(nowMs / 1000);

  const mockFetch = async () =>
    new Response(JSON.stringify({ keys: [jwk] }), {
      status: 200,
      headers: { "Content-Type": "application/json", "Cache-Control": "max-age=3600" },
    });

  const validJwt = createSignedJwt({
    privateKey,
    payload: {
      aud: TEST_ENV.FIREBASE_PROJECT_ID,
      iss: `https://securetoken.google.com/${TEST_ENV.FIREBASE_PROJECT_ID}`,
      sub: "firebase-uid-123",
      iat: nowSec - 60,
      auth_time: nowSec - 120,
      exp: nowSec + 3600,
    },
  });
  assert.equal(
    await verifyFirebaseIdToken(`Bearer ${validJwt}`, TEST_ENV.FIREBASE_PROJECT_ID, nowMs, mockFetch),
    "firebase-uid-123"
  );

  // Future iat > now + 300 must fail
  const futureIatJwt = createSignedJwt({
    privateKey,
    payload: {
      aud: TEST_ENV.FIREBASE_PROJECT_ID,
      iss: `https://securetoken.google.com/${TEST_ENV.FIREBASE_PROJECT_ID}`,
      sub: "firebase-uid-123",
      iat: nowSec + 900,
      exp: nowSec + 3600,
    },
  });
  assert.equal(
    await verifyFirebaseIdToken(`Bearer ${futureIatJwt}`, TEST_ENV.FIREBASE_PROJECT_ID, nowMs, mockFetch),
    null
  );

  // JWKS 500 error returns 503 on handleWorkerRequest
  resetJwksCacheForTests();
  const failingJwksFetch = async () => new Response("upstream error", { status: 500 });
  const workerResp = await handleWorkerRequest(
    new Request("https://broker.example.workers.dev/oauth/tiktok/refresh", {
      method: "POST",
      headers: { Authorization: `Bearer ${validJwt}`, "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: "rt-1" }),
    }),
    TEST_ENV,
    failingJwksFetch
  );
  assert.equal(workerResp.status, 503);
});

test("handleMetaExchange omits personal userAccessToken and blocks disallowed redirectUri", async () => {
  resetJwksCacheForTests();
  const { publicKey, privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const jwk = publicKey.export({ format: "jwk" });
  jwk.kid = "kid-meta";
  const nowSec = Math.floor(Date.now() / 1000);
  const jwt = createSignedJwt({
    privateKey,
    kid: "kid-meta",
    payload: {
      aud: TEST_ENV.FIREBASE_PROJECT_ID,
      iss: `https://securetoken.google.com/${TEST_ENV.FIREBASE_PROJECT_ID}`,
      sub: "uid-meta",
      iat: nowSec - 10,
      exp: nowSec + 3600,
    },
  });

  const fetchFn = async (url) => {
    const u = String(url);
    if (u.includes("securetoken@system.gserviceaccount.com")) {
      return new Response(JSON.stringify({ keys: [jwk] }), { status: 200 });
    }
    if (u.includes("fb_exchange_token")) {
      return new Response(JSON.stringify({ access_token: "long-user-secret-token", expires_in: 5184000 }), {
        status: 200,
      });
    }
    if (u.includes("/oauth/access_token")) {
      return new Response(JSON.stringify({ access_token: "short-user-token" }), { status: 200 });
    }
    if (u.includes("/me?")) {
      return new Response(JSON.stringify({ id: "user-1", name: "Pastor Osei" }), { status: 200 });
    }
    if (u.includes("/me/accounts?")) {
      return new Response(
        JSON.stringify({
          data: [
            { id: "100200300", name: "Word & Light TV", category: "Ministry", access_token: "page-tok-only" },
          ],
        }),
        { status: 200 }
      );
    }
    return new Response("{}", { status: 404 });
  };

  // Disallowed redirectUri rejected with 400
  const badRedirect = await handleWorkerRequest(
    new Request("https://broker.example.workers.dev/oauth/meta/exchange", {
      method: "POST",
      headers: { Authorization: `Bearer ${jwt}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        code: "c1",
        codeVerifier: "v1",
        redirectUri: "https://attacker.example/oauth/meta/callback",
      }),
    }),
    TEST_ENV,
    fetchFn
  );
  assert.equal(badRedirect.status, 400);

  // Valid request returns Page token only and never leaks userAccessToken
  const okResp = await handleWorkerRequest(
    new Request("https://broker.example.workers.dev/oauth/meta/exchange", {
      method: "POST",
      headers: { Authorization: `Bearer ${jwt}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        code: "c1",
        codeVerifier: "v1",
        redirectUri: "https://broker.example.workers.dev/oauth/meta/callback",
      }),
    }),
    TEST_ENV,
    fetchFn
  );
  assert.equal(okResp.status, 200);
  const body = await okResp.json();
  assert.equal("userAccessToken" in body, false);
  assert.equal(body.pages.length, 1);
  assert.equal(body.pages[0].accessToken, "page-tok-only");
});

test("handleWorkerRequest serves complete /privacy and /terms HTML pages", async () => {
  const priv = await handleWorkerRequest(new Request("https://broker.example.workers.dev/privacy"), TEST_ENV);
  assert.equal(priv.status, 200);
  const privText = await priv.text();
  assert.match(privText, /Word &amp; Light AI STUDIO/);
  assert.match(privText, /youtube\.upload/);

  const terms = await handleWorkerRequest(new Request("https://broker.example.workers.dev/terms"), TEST_ENV);
  assert.equal(terms.status, 200);
  const termsText = await terms.text();
  assert.match(termsText, /Terms of Service/);
  assert.match(termsText, /SELF_ONLY/);
});
