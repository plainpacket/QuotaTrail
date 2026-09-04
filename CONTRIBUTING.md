# Contributing to QuotaTrail

QuotaTrail is a single-module Android app built with Kotlin, Room, DataStore, WorkManager, and
Jetpack Glance. The public repository is a fork of
[KyoMio/CodexMeter](https://github.com/KyoMio/CodexMeter), substantially reworked under the
`app.quotatrail` namespace.

## Before opening a change

- Keep provider credentials, cookies, OAuth codes, raw responses, keystores, and local properties
  out of commits, logs, screenshots, and tests.
- Keep UI state in ViewModels and StateFlow; Composables render state and emit events.
- Route refresh work through the shared synchronization coordinator so the app, widget, and
  notification do not implement separate quota logic.
- Route account deletion through `CoordinatedAccountDeletion`. The shared per-account gate must
  cover provider session writes, history writes, and deletion; queued refreshes must recheck that
  their account still exists after acquiring the gate.
- Keep the two-provider scope explicit: Claude and Codex are the only providers exposed by the app.

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

## Releases

Add English release notes at `docs/releases/<version>.md`, run local verification, and tag the
verified commit as `v<major>.<minor>.<patch>`. Do not move an already published tag. The release
workflow tests and lints that exact commit before signing and uploading an APK and SHA-256 file.
The manual workflow accepts an existing tag; it does not publish an arbitrary branch checkout.

Keep the existing signing key in repository secrets. The workflow checks its public certificate
fingerprint so users can install updates without uninstalling. Never change this key as a routine
version bump. Font binaries require their bundled copyright and SIL OFL notices in every APK.
