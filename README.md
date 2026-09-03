# QuotaTrail

**A quiet, local-first quota instrument for Claude, Codex, and other AI services.**

[![Android 12+](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/12)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-black.svg)](LICENSE)
[![Build](https://github.com/plainpacket/QuotaTrail/actions/workflows/release.yml/badge.svg)](https://github.com/plainpacket/QuotaTrail/actions/workflows/release.yml)

QuotaTrail keeps the limits you care about visible without turning them into a noisy analytics
dashboard. It reads official provider usage data, stores sensitive sessions on-device, and exposes
the same state in three places: the app, a resizable home-screen widget, and a low-noise ongoing
notification.

## What it does

- Shows 5-hour and 7-day remaining percentages, renewal times, balances, usage counts, and model
  buckets when a provider exposes them.
- Keeps exhausted Claude 5-hour windows visible as `0% remaining` with their renewal time.
- Plots the latest 72 hours of remaining quota with separate 7-day and 5-hour lines.
- Provides four independently configurable widget slots, so Claude and Codex can share one widget.
- Offers one `Refresh all` action for the ongoing notification and a refresh button in the widget.
- Supports multiple saved accounts with per-account alerts and re-login detection.
- Runs periodic refresh through WorkManager without a foreground service.

## Providers

| Provider | Connection |
| --- | --- |
| Codex | Device-code authorization in the external browser |
| Claude | OAuth sign-in in the app |
| Antigravity | Google OAuth sign-in in the app |
| DeepSeek | API key |
| z.ai | API key |
| MiniMax | API key |
| Cursor | Cookie capture in the app |
| Kimi | Cookie capture in the app |

## Privacy and security

QuotaTrail is local-first. OAuth sessions are encrypted with Android Keystore-backed AES-GCM. It
does not use cloud sync, ads, analytics, remote logging, a quota proxy, or a foreground service for
refresh. Tokens, cookies, OAuth codes, raw responses, and complete `auth.json` files are never
shown, logged, or committed.

## Install

Installable APKs are published through [GitHub Releases](https://github.com/plainpacket/QuotaTrail/releases).
QuotaTrail targets Android 12 and newer. The app is unofficial and is not affiliated with
Anthropic, OpenAI, Google, or any other provider.

## Build locally

Requirements: Android Studio with SDK 37, JDK 17, and an Android 12+ device or emulator.

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

## Project map

- `app/src/main/java/app/quotatrail/domain` — provider-neutral models and policies
- `app/src/main/java/app/quotatrail/providers` — provider sessions, clients, DTOs, and mappers
- `app/src/main/java/app/quotatrail/storage` — Room, DataStore, and encrypted session storage
- `app/src/main/java/app/quotatrail/presentation` — Compose Material 3 screens and state rendering
- `app/src/main/java/app/quotatrail/surfaces` — notification and Jetpack Glance widget surfaces
- `docs/PERSONAL_SECURITY_BUILD.md` — optional least-privilege build and release checklist
- `CONTRIBUTING.md` — contributor safety and verification rules

## Origin and attribution

This public repository is an actual fork of [KyoMio/CodexMeter](https://github.com/KyoMio/CodexMeter),
with the application identity, namespace, architecture naming, UI system, and provider surfaces
substantially reworked for QuotaTrail. The original MIT license and required attribution are
preserved in [NOTICE.md](NOTICE.md).

Some provider response mappings reference [steipete/CodexBar](https://github.com/steipete/CodexBar).

## License

MIT. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md).
