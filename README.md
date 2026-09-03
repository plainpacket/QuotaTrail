# QuotaTrail

QuotaTrail is a small Android app that keeps Claude and Codex usage limits visible.

[![Android 12+](https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/12)
[![License: MIT](https://img.shields.io/badge/License-MIT-black.svg)](LICENSE)

It reads each service's official usage endpoint and keeps the result on the device. The same
account data is available in the app, an optional home-screen widget, and an ongoing notification.

## Features

- Claude and Codex accounts, with multiple accounts per service.
- 5-hour and 7-day remaining quota, reset times, and refresh status.
- A 72-hour history chart for both quota windows.
- Four independently configured widget slots.
- One-tap refresh for the app, widget, and ongoing notification.
- Local encrypted session storage and re-login detection.

## Connections

| Service | Sign-in |
| --- | --- |
| Claude | OAuth in the app |
| Codex | Device-code authorization in the browser |

## Privacy

Sessions are encrypted with Android Keystore-backed AES-GCM and remain on the device. QuotaTrail
does not provide cloud sync, ads, analytics, remote logging, or a quota proxy. Credentials, cookies,
OAuth codes, and raw provider responses are not written to logs or diagnostics.

## Install

Release APKs, when available, are published on [GitHub Releases](https://github.com/plainpacket/QuotaTrail/releases).
QuotaTrail is an unofficial client and is not affiliated with Anthropic or OpenAI.

## Build

Use Android Studio with SDK 37 and JDK 17:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

The release workflow expects a repository signing key. Do not commit keystores or local
credentials; see [CONTRIBUTING.md](CONTRIBUTING.md) for the project rules.

## Source

This repository is an actual fork of [KyoMio/CodexMeter](https://github.com/KyoMio/CodexMeter),
substantially reworked and maintained as QuotaTrail. The original MIT terms and attribution are
preserved in [NOTICE.md](NOTICE.md).

## License

MIT. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md).
