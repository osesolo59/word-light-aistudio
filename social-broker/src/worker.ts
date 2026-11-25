/**
 * Word & Light AI STUDIO — Non-Blaze OAuth Token Broker (Cloudflare Workers Free Plan)
 *
 * Security guarantees:
 * - Zero Firebase Blaze dependency; verifies Firebase Auth ID tokens (alg, kid, aud, iss, sub, exp, iat, auth_time, RSA signature)
 *   using Google's public JWKs via WebCrypto with Cache-Control-aware caching and explicit JWKS error handling.
 * - Strict callback redirect_uri allowlisting (must match this Worker's HTTPS origin + expected callback path).
 * - Never handles or stores user social media passwords.
 * - Keeps META_APP_SECRET and TIKTOK_CLIENT_SECRET strictly inside encrypted Cloudflare Worker secrets.
 * - Never returns Meta personal userAccessToken to client; returns only verified numeric Page IDs & Page tokens.
 * - Android streams MP4 video payloads directly to YouTube / Meta / TikTok upload endpoints (zero media proxy cost).
 */

export interface Env {
  FIREBASE_PROJECT_ID: string;
  APP_REDIRECT_SCHEME: string;
  APP_REDIRECT_HOST: string;
  META_GRAPH_VERSION: string;
  META_APP_ID?: string;
  META_APP_SECRET?: string;
  TIKTOK_CLIENT_KEY?: string;
  TIKTOK_CLIENT_SECRET?: string;
  TIKTOK_SITE_VERIFICATION?: string;
  APP_SIGNING_SHA256?: string;
}

const GOOGLE_SECURETOKEN_JWKS =
  "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

export type FetchLike = typeof fetch;

interface CachedJwks {
  keys: Array<JsonWebKey & { kid?: string }>;
  expiresAtMs: number;
}

let cachedJwks: CachedJwks | null = null;

export function resetJwksCacheForTests(): void {
  cachedJwks = null;
}

export class JwksUnavailableError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "JwksUnavailableError";
  }
}

export function isAllowedBrokerRedirectUri(
  requestUrl: URL,
  redirectUri: string | undefined,
  platform: "meta" | "tiktok"
): boolean {
  if (!redirectUri) return false;
  let parsed: URL;
  try {
    parsed = new URL(redirectUri);
  } catch {
    return false;
  }
  if (parsed.protocol !== "https:") return false;
  const expectedPath = platform === "meta" ? "/oauth/meta/callback" : "/oauth/tiktok/callback";
  return parsed.origin === requestUrl.origin && parsed.pathname === expectedPath && !parsed.search && !parsed.hash;
}

export async function handleWorkerRequest(
  request: Request,
  env: Env,
  fetchFn: FetchLike = fetch
): Promise<Response> {
  const url = new URL(request.url);

  // Public OAuth browser callback relays -> 302 into Android custom scheme
  if (request.method === "GET" && url.pathname === "/oauth/meta/callback") {
    return relayOAuthCallback("facebook", url, env);
  }
  if (request.method === "GET" && url.pathname === "/oauth/tiktok/callback") {
    return relayOAuthCallback("tiktok", url, env);
  }

  // Optional Android App Links verification when signing SHA-256 fingerprint(s) are configured
  if (request.method === "GET" && url.pathname === "/.well-known/assetlinks.json") {
    const fingerprints = (env.APP_SIGNING_SHA256 || "")
      .split(",")
      .map((s) => s.trim().toUpperCase())
      .filter(Boolean);
    if (fingerprints.length === 0) {
      return jsonResponse({ error: "Asset links fingerprints not configured." }, 404);
    }
    return jsonResponse([
      {
        relation: ["delegate_permission/common.handle_all_urls"],
        target: {
          namespace: "android_app",
          package_name: "com.aistudio.aimusic.wltv.jqr8x",
          sha256_cert_fingerprints: fingerprints,
        },
      },
    ]);
  }

  // Developer review & user legal compliance routes (live once user confirms domain & deploys)
  if (request.method === "GET" && url.pathname === "/privacy") {
    return htmlResponse(PRIVACY_POLICY_HTML);
  }
  if (request.method === "GET" && url.pathname === "/terms") {
    return htmlResponse(TERMS_OF_SERVICE_HTML);
  }
  if (request.method === "GET" && url.pathname === "/tiktok-developers-site-verification.txt") {
    const token = (env.TIKTOK_SITE_VERIFICATION || "").trim();
    if (!token) {
      return new Response("Verification token not yet configured.", { status: 404 });
    }
    return new Response(token, {
      status: 200,
      headers: { "Content-Type": "text/plain; charset=utf-8" },
    });
  }

  // Authenticated OAuth token exchange & refresh routes (Firebase ID token required)
  if (request.method === "POST") {
    let uid: string | null = null;
    try {
      uid = await verifyFirebaseIdToken(
        request.headers.get("Authorization"),
        env.FIREBASE_PROJECT_ID,
        Date.now(),
        fetchFn
      );
    } catch (err) {
      if (err instanceof JwksUnavailableError) {
        return jsonResponse({ error: "Unable to verify Firebase identity right now; retry shortly." }, 503);
      }
      return jsonResponse({ error: "Token verification failure." }, 401);
    }

    if (!uid) {
      return jsonResponse({ error: "Unauthorized: valid Firebase Auth Bearer token required." }, 401);
    }

    if (url.pathname === "/oauth/meta/exchange") {
      return handleMetaExchange(request, url, env, fetchFn);
    }
    if (url.pathname === "/oauth/tiktok/exchange") {
      return handleTikTokExchange(request, url, env, fetchFn);
    }
    if (url.pathname === "/oauth/tiktok/refresh") {
      return handleTikTokRefresh(request, env, fetchFn);
    }
  }

  return jsonResponse({ error: "Not found" }, 404);
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    return handleWorkerRequest(request, env, fetch);
  },
};

function relayOAuthCallback(platform: "facebook" | "tiktok", url: URL, env: Env): Response {
  const deepLink = new URL(`${env.APP_REDIRECT_SCHEME}://${env.APP_REDIRECT_HOST}/${platform}`);
  for (const key of ["code", "state", "error", "error_description"]) {
    const val = url.searchParams.get(key);
    if (val) deepLink.searchParams.set(key, val);
  }
  return Response.redirect(deepLink.toString(), 302);
}

async function handleMetaExchange(
  request: Request,
  requestUrl: URL,
  env: Env,
  fetchFn: FetchLike
): Promise<Response> {
  if (!env.META_APP_ID || !env.META_APP_SECRET) {
    return jsonResponse({ error: "Meta App credentials are not configured on broker." }, 503);
  }
  const body = (await request.json().catch(() => ({}))) as {
    code?: string;
    codeVerifier?: string;
    redirectUri?: string;
  };
  if (!body.code || !body.redirectUri) {
    return jsonResponse({ error: "Missing code or redirectUri." }, 400);
  }
  if (!isAllowedBrokerRedirectUri(requestUrl, body.redirectUri, "meta")) {
    return jsonResponse({ error: "Disallowed Meta redirectUri." }, 400);
  }

  const graphBase = `https://graph.facebook.com/${env.META_GRAPH_VERSION || "v21.0"}`;
  const tokenParams = new URLSearchParams({
    client_id: env.META_APP_ID,
    client_secret: env.META_APP_SECRET,
    redirect_uri: body.redirectUri,
    code: body.code,
  });
  if (body.codeVerifier) {
    tokenParams.set("code_verifier", body.codeVerifier);
  }

  const tokenResp = await fetchFn(`${graphBase}/oauth/access_token?${tokenParams.toString()}`);
  const tokenJson = (await tokenResp.json().catch(() => ({}))) as Record<string, any>;
  if (!tokenResp.ok || !tokenJson.access_token) {
    return jsonResponse(
      { error: tokenJson?.error?.message || "Meta code exchange failed." },
      tokenResp.status >= 400 ? tokenResp.status : 400
    );
  }

  // Upgrade short-lived user token to long-lived user token solely inside the Worker to mint Page tokens
  const longParams = new URLSearchParams({
    grant_type: "fb_exchange_token",
    client_id: env.META_APP_ID,
    client_secret: env.META_APP_SECRET,
    fb_exchange_token: tokenJson.access_token,
  });
  const longResp = await fetchFn(`${graphBase}/oauth/access_token?${longParams.toString()}`);
  const longJson = (await longResp.json().catch(() => ({}))) as Record<string, any>;
  const userAccessToken: string = longJson.access_token || tokenJson.access_token;
  const expiresInSec: number = Number(longJson.expires_in || tokenJson.expires_in || 5184000);

  const proof = await hmacSha256Hex(env.META_APP_SECRET, userAccessToken);

  const meResp = await fetchFn(
    `${graphBase}/me?fields=id,name&access_token=${encodeURIComponent(userAccessToken)}&appsecret_proof=${proof}`
  );
  const meJson = (await meResp.json().catch(() => ({}))) as Record<string, any>;

  const pagesResp = await fetchFn(
    `${graphBase}/me/accounts?fields=id,name,category,access_token&access_token=${encodeURIComponent(userAccessToken)}&appsecret_proof=${proof}`
  );
  if (!pagesResp.ok) {
    const pageErr = (await pagesResp.json().catch(() => ({}))) as Record<string, any>;
    return jsonResponse(
      { error: pageErr?.error?.message || "Failed to list managed Facebook Pages." },
      502
    );
  }
  const pagesJson = (await pagesResp.json().catch(() => ({}))) as { data?: Array<Record<string, any>> };

  const pages = (pagesJson.data || [])
    .map((p) => ({
      id: String(p.id || "").trim(),
      name: String(p.name || "").trim(),
      category: String(p.category || "").trim(),
      accessToken: String(p.access_token || "").trim(),
    }))
    .filter((p) => /^\d+$/.test(p.id) && p.accessToken.length > 0);

  if (pages.length === 0) {
    return jsonResponse(
      { error: "No Facebook Page with publishing access tokens found for this account." },
      422
    );
  }

  // Strictly omit userAccessToken so personal tokens never reach the Android client
  return jsonResponse({
    userId: String(meJson.id || ""),
    userName: String(meJson.name || "Facebook Page Admin"),
    expiresAtEpochMs: Date.now() + expiresInSec * 1000,
    pages,
  });
}

async function handleTikTokExchange(
  request: Request,
  requestUrl: URL,
  env: Env,
  fetchFn: FetchLike
): Promise<Response> {
  if (!env.TIKTOK_CLIENT_KEY || !env.TIKTOK_CLIENT_SECRET) {
    return jsonResponse({ error: "TikTok credentials are not configured on broker." }, 503);
  }
  const body = (await request.json().catch(() => ({}))) as {
    code?: string;
    codeVerifier?: string;
    redirectUri?: string;
  };
  if (!body.code || !body.redirectUri) {
    return jsonResponse({ error: "Missing code or redirectUri." }, 400);
  }
  if (!isAllowedBrokerRedirectUri(requestUrl, body.redirectUri, "tiktok")) {
    return jsonResponse({ error: "Disallowed TikTok redirectUri." }, 400);
  }

  const form = new URLSearchParams({
    client_key: env.TIKTOK_CLIENT_KEY,
    client_secret: env.TIKTOK_CLIENT_SECRET,
    code: body.code,
    grant_type: "authorization_code",
    redirect_uri: body.redirectUri,
  });
  if (body.codeVerifier) {
    form.set("code_verifier", body.codeVerifier);
  }

  const tokenResp = await fetchFn("https://open.tiktokapis.com/v2/oauth/token/", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: form.toString(),
  });
  const tokenJson = (await tokenResp.json().catch(() => ({}))) as Record<string, any>;
  if (!tokenResp.ok || !tokenJson.access_token) {
    return jsonResponse(
      { error: tokenJson.error_description || tokenJson.error || "TikTok token exchange failed." },
      tokenResp.status >= 400 ? tokenResp.status : 400
    );
  }

  const userResp = await fetchFn("https://open.tiktokapis.com/v2/user/info/?fields=open_id,display_name,username", {
    headers: { Authorization: `Bearer ${tokenJson.access_token}` },
  });
  const userJson = (await userResp.json().catch(() => ({}))) as { data?: { user?: Record<string, any> } };
  const user = userJson?.data?.user;

  return jsonResponse({
    accessToken: tokenJson.access_token,
    refreshToken: tokenJson.refresh_token || "",
    expiresIn: Number(tokenJson.expires_in || 86400),
    openId: tokenJson.open_id || user?.open_id || "",
    scope: tokenJson.scope || "",
    displayName: user?.display_name || "TikTok Creator",
    username: user?.username ? `@${String(user.username).replace(/^@/, "")}` : "",
  });
}

async function handleTikTokRefresh(
  request: Request,
  env: Env,
  fetchFn: FetchLike
): Promise<Response> {
  if (!env.TIKTOK_CLIENT_KEY || !env.TIKTOK_CLIENT_SECRET) {
    return jsonResponse({ error: "TikTok credentials are not configured on broker." }, 503);
  }
  const body = (await request.json().catch(() => ({}))) as { refreshToken?: string };
  const refreshToken = (body.refreshToken || "").trim();
  if (!refreshToken) {
    return jsonResponse({ error: "Missing refreshToken." }, 400);
  }

  const form = new URLSearchParams({
    client_key: env.TIKTOK_CLIENT_KEY,
    client_secret: env.TIKTOK_CLIENT_SECRET,
    grant_type: "refresh_token",
    refresh_token: refreshToken,
  });

  const tokenResp = await fetchFn("https://open.tiktokapis.com/v2/oauth/token/", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: form.toString(),
  });
  const tokenJson = (await tokenResp.json().catch(() => ({}))) as Record<string, any>;
  if (!tokenResp.ok || !tokenJson.access_token) {
    return jsonResponse(
      {
        error: tokenJson.error_description || tokenJson.error || "TikTok refresh failed.",
        requiresReauth: true,
      },
      401
    );
  }

  return jsonResponse({
    accessToken: tokenJson.access_token,
    refreshToken: tokenJson.refresh_token || refreshToken,
    expiresIn: Number(tokenJson.expires_in || 86400),
  });
}

async function getJwkForKid(
  kid: string,
  nowMs: number,
  fetchFn: FetchLike
): Promise<(JsonWebKey & { kid?: string }) | null> {
  if (cachedJwks && nowMs < cachedJwks.expiresAtMs) {
    const hit = cachedJwks.keys.find((k) => k.kid === kid);
    if (hit) return hit;
  }

  const jwksResp = await fetchFn(GOOGLE_SECURETOKEN_JWKS, { cf: { cacheTtl: 3600 } } as any);
  if (!jwksResp.ok) {
    throw new JwksUnavailableError(`Google JWKS returned HTTP ${jwksResp.status}`);
  }
  const jwks = (await jwksResp.json().catch(() => null)) as { keys?: Array<JsonWebKey & { kid?: string }> } | null;
  if (!jwks || !Array.isArray(jwks.keys)) {
    throw new JwksUnavailableError("Malformed JWKS payload from Google.");
  }

  const maxAgeMatch = jwksResp.headers.get("Cache-Control")?.match(/max-age=(\d+)/i);
  const ttlSec = maxAgeMatch ? Math.min(Math.max(Number(maxAgeMatch[1]), 60), 86400) : 3600;
  cachedJwks = {
    keys: jwks.keys,
    expiresAtMs: nowMs + ttlSec * 1000,
  };

  return jwks.keys.find((k) => k.kid === kid) || null;
}

export async function verifyFirebaseIdToken(
  authHeader: string | null,
  projectId: string,
  nowMs: number = Date.now(),
  fetchFn: FetchLike = fetch
): Promise<string | null> {
  if (!projectId || !authHeader || !authHeader.startsWith("Bearer ")) return null;
  const jwt = authHeader.slice("Bearer ".length).trim();
  const parts = jwt.split(".");
  if (parts.length !== 3) return null;

  let header: { alg?: string; kid?: string };
  let payload: {
    aud?: string;
    iss?: string;
    sub?: string;
    exp?: number;
    iat?: number;
    auth_time?: number;
  };
  try {
    header = JSON.parse(decodeBase64UrlText(parts[0]));
    payload = JSON.parse(decodeBase64UrlText(parts[1]));
  } catch {
    return null;
  }

  const nowSec = Math.floor(nowMs / 1000);
  if (
    header.alg !== "RS256" ||
    typeof header.kid !== "string" ||
    header.kid.length === 0 ||
    payload.aud !== projectId ||
    payload.iss !== `https://securetoken.google.com/${projectId}` ||
    typeof payload.sub !== "string" ||
    payload.sub.length === 0 ||
    payload.sub.length > 128 ||
    typeof payload.exp !== "number" ||
    payload.exp <= nowSec ||
    typeof payload.iat !== "number" ||
    payload.iat > nowSec + 300 ||
    (payload.auth_time !== undefined && (typeof payload.auth_time !== "number" || payload.auth_time > nowSec + 300))
  ) {
    return null;
  }

  const jwk = await getJwkForKid(header.kid, nowMs, fetchFn);
  if (!jwk) return null;

  try {
    const cryptoKey = await crypto.subtle.importKey(
      "jwk",
      jwk,
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["verify"]
    );
    const signedData = new TextEncoder().encode(`${parts[0]}.${parts[1]}`);
    const sigBytes = decodeBase64UrlBytes(parts[2]);
    const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", cryptoKey, sigBytes, signedData);
    return valid ? payload.sub : null;
  } catch {
    return null;
  }
}

async function hmacSha256Hex(secret: string, message: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(message));
  return Array.from(new Uint8Array(sig))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function decodeBase64UrlBytes(input: string): Uint8Array {
  const padded = input.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((input.length + 3) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

function decodeBase64UrlText(input: string): string {
  return new TextDecoder().decode(decodeBase64UrlBytes(input));
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}

function htmlResponse(html: string): Response {
  return new Response(html, {
    status: 200,
    headers: { "Content-Type": "text/html; charset=utf-8" },
  });
}

const PRIVACY_POLICY_HTML = `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Word &amp; Light AI STUDIO — Privacy Policy</title></head>
<body style="font-family:system-ui,sans-serif;max-width:760px;margin:40px auto;padding:0 18px;color:#210044;line-height:1.6">
<h1>Word &amp; Light AI STUDIO — Privacy Policy</h1>
<p><strong>Operator:</strong> Christians Strong Foundation Ministry<br><strong>Support Phone:</strong> +233245200758</p>
<h2>1. Information We Collect</h2>
<ul>
<li><strong>Google Account Sign-In:</strong> Used via Android Credential Manager and Firebase Authentication to store your workspace profile and project metadata.</li>
<li><strong>Connected Publishing Scopes (Optional):</strong> YouTube (<code>https://www.googleapis.com/auth/youtube.upload</code> via Google AuthorizationClient), Facebook Pages (<code>pages_show_list</code>, <code>pages_read_engagement</code>, <code>pages_manage_posts</code>), and TikTok (<code>user.info.basic</code>, <code>video.publish</code>).</li>
</ul>
<h2>2. Zero Passwords &amp; Encryption</h2>
<ul>
<li>We never request, collect, or store your YouTube, Facebook, or TikTok passwords.</li>
<li>OAuth tokens on Android are stored using hardware-backed Android Keystore AES-256-GCM encryption and are excluded from Android cloud backup and device transfer.</li>
<li>Application client secrets are kept exclusively inside encrypted Cloudflare Worker secrets and are never shipped inside the APK.</li>
</ul>
<h2>3. Data Usage, Sharing &amp; Deletion</h2>
<ul>
<li>Exported MP4 videos and captions are uploaded directly from your device to the official platform APIs you authorize.</li>
<li>You can disconnect any channel immediately inside the app (Settings &rarr; Direct Video Publishing), revoke access in Google/Meta/TikTok security settings, or request account deletion by contacting +233245200758.</li>
</ul>
</body></html>`;

const TERMS_OF_SERVICE_HTML = `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Word &amp; Light AI STUDIO — Terms of Service</title></head>
<body style="font-family:system-ui,sans-serif;max-width:760px;margin:40px auto;padding:0 18px;color:#210044;line-height:1.6">
<h1>Word &amp; Light AI STUDIO — Terms of Service</h1>
<p><strong>Operator:</strong> Christians Strong Foundation Ministry<br><strong>Support Phone:</strong> +233245200758</p>
<p>By using Word &amp; Light AI STUDIO and its optional Direct Video Publishing integrations for YouTube, Facebook Pages, and TikTok, you confirm that:</p>
<ul>
<li>You own or hold broadcast rights to all audio tracks, images, scriptures, and lyrics included in your exported MP4 videos.</li>
<li>You will comply with Google/YouTube Terms of Service, Meta Platform Terms, and TikTok Developer Terms.</li>
<li>Unverified or sandbox integrations may enforce private-only upload limits (such as TikTok <code>SELF_ONLY</code> or YouTube private mode) until platform review completes.</li>
</ul>
</body></html>`;
