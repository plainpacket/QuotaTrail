# Contributing to QuotaTrail

QuotaTrail is a single-module Android app built with Kotlin, Jetpack Compose, Material 3, Room,
DataStore, WorkManager, and Jetpack Glance. The public repository is a fork of
[KyoMio/CodexMeter](https://github.com/KyoMio/CodexMeter), substantially reworked under the
`app.quotatrail` namespace.

## Before opening a change

- Keep provider credentials, cookies, OAuth codes, raw responses, keystores, and local properties
  out of commits, logs, screenshots, and tests.
- Keep UI state in ViewModels and StateFlow; Composables render state and emit events.
- Route refresh work through the shared synchronization coordinator so the app, widget, and
  notification do not implement separate quota logic.
- Follow the Material 3 visual language already expressed by the Compose theme and shared surfaces,
  including its English-only copy, semantic colors, and accessibility targets.

## Local verification

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

For provider, authentication, encryption, refresh, or redaction changes, add or update focused
tests before changing production code. Do not add dependencies without documenting why they are
needed.

## Commits and pull requests

Use a conventional commit subject such as `feat:`, `fix:`, `docs:`, `test:`, or `refactor:`.
Explain behavior changes and include the verification commands you ran. Keep generated APKs and
local design exports out of Git; publish installable APKs through GitHub Releases instead.
