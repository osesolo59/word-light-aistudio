# Word & Light AI STUDIO — Free-Tier OAuth Token Broker

Runs on **Cloudflare Workers Free Plan** (100,000 requests/day, no Firebase Blaze billing required).

## Security Controls
- **Zero Firebase Blaze dependency**: Verifies Firebase Auth ID tokens (`alg=RS256`, `kid`, `aud`, `iss`, `sub`, `exp`, `iat`, `auth_time`) using Google's public JWKs via WebCrypto, with `Cache-Control` caching and `503` retryable error handling when JWKS is unreachable.
- **Google YouTube OAuth**: Handled natively on Android via Google Play Services `AuthorizationClient` (`https://www.googleapis.com/auth/youtube.upload` only) without custom URI scheme redirects.
- **Meta & TikTok Broker**: Keeps `META_APP_SECRET` and `TIKTOK_CLIENT_SECRET` exclusively inside encrypted Cloudflare Worker secrets. Validates `redirectUri` strictly against this Worker's HTTPS origin and expected callback path.
- **Strict Facebook Page Protection**: Strips personal `userAccessToken` from responses and returns only managed Pages with valid numeric Page IDs and Page access tokens.
- **Zero media proxy cost**: Android streams MP4 video payloads directly from device to official YouTube, Meta, and TikTok endpoints.

## Deployment Steps (Run Only After Domain & Credential Readiness)
1. Install dependencies & authenticate Wrangler:
   ```bash
   cd social-broker
   npm install
   npx wrangler login
   ```
2. Set encrypted secrets (never stored in git or APK):
   ```bash
   npx wrangler secret put META_APP_ID
   npx wrangler secret put META_APP_SECRET
   npx wrangler secret put TIKTOK_CLIENT_KEY
   npx wrangler secret put TIKTOK_CLIENT_SECRET
   npx wrangler secret put TIKTOK_SITE_VERIFICATION
   # Optional for Android App Links:
   # npx wrangler secret put APP_SIGNING_SHA256
   ```
3. Deploy to Cloudflare Workers Free:
   ```bash
   npm run deploy
   ```
4. Pass the confirmed public HTTPS URLs and public App IDs into Android build env vars (`WLTV_SOCIAL_BROKER_URL`, `WLTV_META_APP_ID`, `WLTV_TIKTOK_CLIENT_KEY`, `WLTV_PRIVACY_POLICY_URL`, `WLTV_TERMS_OF_SERVICE_URL`).
