# QuotaTrail Safe — Personal Security Build

## Purpose and provenance

This repository is a least-privilege Android build for viewing only Claude and Codex quota
usage. It is derived from QuotaTrail `v0.1.5` at commit
`e0c42ff651b7f50813109c4be6b9708cd18c395c`. It is not an official OpenAI or Anthropic app and has
not received an independent professional security audit.

The package ID is `app.quotatrail.safe`, the visible name is `QuotaTrail`, and upstream
APK self-updates are intentionally disabled. Future APKs must be rebuilt from reviewed source and
signed with the same private key.

## Enforced security boundaries

- The account picker exposes only Claude and Codex.
- The shared HTTP client permits only `auth.openai.com`, `chatgpt.com`, `platform.claude.com`, and
  `api.anthropic.com`. Redirected HTTP calls pass through the same network interceptor.
- Cleartext HTTP is disabled without a loopback exception.
- Public exchange-rate calls and GitHub release/update calls are disabled.
- Claude requests only the `user:profile` OAuth scope needed by the usage endpoint. It does not ask
  for inference or API-key creation privileges.
- Claude authorization uses the current Claude Code flow at `claude.com/cai/oauth/authorize`, with
  the exact `platform.claude.com/oauth/code/callback` redirect and platform token endpoint.
- Codex uses OpenAI's external device-code flow. Raw tokens are never entered in an app text field.
- Access and refresh tokens are encrypted with AES-GCM using an Android Keystore key. Android backup
  and device-transfer backup are disabled for app files, preferences, and the database.
- Claude authorization opens in the system browser, so the app never renders the Google/Claude login
  page or handles browser cookies. The user pastes the one-time `code#state`; the app requires the
  exact generated state, clears the in-memory input before exchange, and never persists the callback.
- Claude access tokens are refreshed 30 minutes before their local expiry. A server-side 401 also
  triggers one silent refresh and one retry. Rotated refresh tokens are saved before use, refreshes
  are serialized, and a missing `expires_in` field receives a conservative eight-hour fallback so
  the app does not repeatedly consume a single-use refresh token. Token exchange and refresh calls
  carry the Claude CLI user agent expected by Anthropic's edge. A refresh-edge 403 or an immediate
  post-rotation usage rejection stays retryable instead of prematurely marking the account signed
  out; a confirmed refresh-credential rejection still requires interactive sign-in.
- Codex access-token expiry is persisted from `expires_in`, with JWT `exp` and a conservative
  one-hour legacy fallback. The app refreshes five minutes before expiry, retries once after a
  server-side 401, serializes rotating refresh-token writes, and no longer rotates a refresh token
  on every background quota poll.
- Non-status notifications are `VISIBILITY_SECRET`. The persistent status notification is private
  and has a separate public lock-screen version containing Claude/Codex quota and freshness text but
  no account alias. When both providers are connected, one ongoing notification shows both.
- Its private `Refresh all` action uses an immutable explicit PendingIntent to a non-exported receiver;
  the receiver only queues constrained unique WorkManager refresh work and accepts no credentials.
- Each displayed 5h/7-day item may expose the provider-supplied reset timestamp converted to the
  device time zone. Missing reset data is labeled unavailable and is never inferred.
- The widget advertises both home-screen and keyguard categories. Its exported configuration activity
  verifies that the supplied widget ID belongs to this app's receiver. Widget headers always show
  only the provider name (Claude or Codex), never the account alias. Every configured widget size
  also shows the latest successful quota snapshot time as `Updated ...`.
- The WorkManager foreground-service permission contributed by a dependency is removed.
- The Gradle wrapper distribution is pinned by SHA-256, and dependency verification metadata is
  generated for the release inputs.

## Lock-screen behavior

The persistent status notification is the reliable Android 12+ lock-screen surface. Enable it in
Settings, leave `Notification account` on `All connected accounts`, allow notifications, then
configure Android's lock-screen notification policy to show notification content. The public version
deliberately shows provider quota/freshness without account names.

`WIDGET_CATEGORY_KEYGUARD` is also declared for hosts that support it. Stock modern Android does not
provide the old universal lock-screen widget host; availability depends on the device vendor/launcher.
On Samsung devices, Good Lock/LockStar may be able to place a supported widget on the lock screen.
The notification remains the fallback when the host ignores keyguard widgets.

## Remaining risks and maintenance rules

- Claude's consumer OAuth usage endpoint is not a stable public API; Anthropic can change the flow,
  client behavior, or response schema. A failure should require reauthentication, not broader scopes.
- The Claude authorization page is remote content in the user's system browser. Verify the displayed
  Claude host before authorizing and never share the one-time code; it is accepted only for the
  matching in-app state and PKCE verifier.
- Android Keystore protects secrets at rest, not while the phone is unlocked and the app process is
  legitimately using them. A rooted/compromised OS is outside this threat model.
- Do not install an APK whose SHA-256 and signing certificate do not match the handoff record. Back up
  the personal signing key and its DPAPI-encrypted password file; losing either prevents safe updates.
- Do not re-enable in-app APK updates unless release metadata includes a pinned signing certificate
  and verified digest before installation.

## Verification gates

A release is acceptable only when all unit/Robolectric tests pass, lint succeeds, release minification
succeeds, the merged manifest has the safe package/permission/network policy, and `apksigner verify
--verbose --print-certs` succeeds. Record the final APK SHA-256 and signer certificate SHA-256 with the
handoff.
