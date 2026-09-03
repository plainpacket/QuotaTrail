# CodexMeter Safe design references

Downloaded on 2026-08-29. These are references, not app source. Prefer the official Android material below; the community `DESIGN.md` files are useful checklists, but are not authoritative specifications.

## Downloaded primary references

### Now in Android (Google / Android)

- Repository: https://github.com/android/nowinandroid
- Exact revision: `7d45eae4f8720a0c77f507712ba2437ff974b6ed`
- Design PDF: https://raw.githubusercontent.com/android/nowinandroid/7d45eae4f8720a0c77f507712ba2437ff974b6ed/docs/Now-In-Android-Design-File.pdf
- Theme sources:
  - https://raw.githubusercontent.com/android/nowinandroid/7d45eae4f8720a0c77f507712ba2437ff974b6ed/core/designsystem/src/main/kotlin/com/google/samples/apps/nowinandroid/core/designsystem/theme/Color.kt
  - https://raw.githubusercontent.com/android/nowinandroid/7d45eae4f8720a0c77f507712ba2437ff974b6ed/core/designsystem/src/main/kotlin/com/google/samples/apps/nowinandroid/core/designsystem/theme/Theme.kt
  - https://raw.githubusercontent.com/android/nowinandroid/7d45eae4f8720a0c77f507712ba2437ff974b6ed/core/designsystem/src/main/kotlin/com/google/samples/apps/nowinandroid/core/designsystem/theme/Type.kt
- Local files: `official-now-in-android/`
- License: Apache License 2.0 (included locally).
- Why it matters: this is Google's maintained, production-quality Jetpack Compose sample. It demonstrates semantic Material 3 color roles, light/dark and dynamic color, a restrained type hierarchy, adaptive layouts, and screenshot-tested design components.

### Material Components for Android (Google)

- Repository: https://github.com/material-components/material-components-android
- Exact revision at download: `ac7e18efeefb331850c561faf9ab8bf81d27ba68`
- Original Markdown:
  - https://github.com/material-components/material-components-android/blob/ac7e18efeefb331850c561faf9ab8bf81d27ba68/docs/theming/Color.md
  - https://github.com/material-components/material-components-android/blob/ac7e18efeefb331850c561faf9ab8bf81d27ba68/docs/theming/Typography.md
  - https://github.com/material-components/material-components-android/blob/ac7e18efeefb331850c561faf9ab8bf81d27ba68/docs/theming/Shape.md
- Local files: `official-material-components/`
- License: Apache License 2.0 (included locally).
- Why it matters: authoritative semantic color, typography, and shape guidance. Some implementation examples target Views rather than Compose, so use the principles and tokens, not its older widget APIs.

## Downloaded community DESIGN.md references

### Awesome Mobile DESIGN.md

- Repository: https://github.com/TrustOtc/awesome-mobile-design-md
- Exact revision: `df2209dce31c2a8634b699d24e83154c552e8fde`
- Dashboard file: https://github.com/TrustOtc/awesome-mobile-design-md/blob/df2209dce31c2a8634b699d24e83154c552e8fde/design-md/data-dashboard.md
- Android/Material file: https://github.com/TrustOtc/awesome-mobile-design-md/blob/df2209dce31c2a8634b699d24e83154c552e8fde/design-md/material-clean.md
- Local files: `community/`
- License: MIT (included locally).
- Why it matters: concise AI-readable checklists for metric-led mobile dashboards and Material-aligned utilities: tabular numbers, compact 4/8-unit spacing, simple rails, semantic status color, 48 dp touch targets, and restrained motion.
- Caveat: the repository is small and recent. Treat it as a useful format/example, not as verified Android authority.

## Official online guidance consulted

These pages remain links rather than copied snapshots so the authoritative version stays current. Android documentation content is covered by Google's site content license; see each page footer.

- Android mobile design overview: https://developer.android.com/design/ui/mobile
- Content composition and structure: https://developer.android.com/design/ui/mobile/guides/layout-and-content/content-structure
  - Use 16 dp compact-screen margins, consistent alignment, and whitespace/typography before adding more containers.
- Grids and units: https://developer.android.com/design/ui/mobile/guides/layout-and-content/grids-and-units
  - Use an 8 dp baseline grid, with 4 dp for smaller elements.
- Color: https://developer.android.com/design/ui/mobile/guides/styles/color
  - Use semantic tokens, light/dark schemes, accessible contrast, and never color as the only state indicator.
- Accessibility: https://developer.android.com/design/ui/mobile/guides/foundations/accessibility
  - Minimum 48 dp touch targets, 4.5:1 text contrast, 3:1 non-text contrast, scalable text, and alternatives to gesture-only actions.
- Notifications: https://developer.android.com/design/ui/mobile/guides/home-screen/notifications
  - A title should be concise (roughly 30 characters), content should avoid repeating it, and actions should state what they do.
- Canonical widget layouts: https://developer.android.com/design/ui/mobile/guides/widgets/layouts
  - A quota widget fits a text or short scannable-list layout; expose only the most important metric/actions at small sizes and add detail at larger breakpoints.
- Glance widget codelab: https://developer.android.com/codelabs/glance
  - Follow system theme/dynamic color, provide a clear zero state, and keep widget updates explicit and predictable.

## Recommended direction for CodexMeter Safe

### Product character

CodexMeter is a glanceable monitoring utility. The visual hierarchy should be **provider/account → remaining quota → renewal time → freshness/action**. Precision and rapid scanning matter more than decorative depth. Keep every current feature and existing Claude/Codex horizontal paging behavior.

### Home layout

1. Keep the horizontal account/provider pager, but make the identity explicit at the top: provider name as the screen title and account name as a smaller subtitle. Keep a visible `1 of 2` indicator and dots so swiping is discoverable; TalkBack must also announce it.
2. Use 16 dp horizontal margins on compact screens and an 8 dp vertical rhythm. Avoid wrapping every section in a glass card. Group related content first with spacing and typography.
3. Make the two quota windows the main content. Prefer two full-width metric rows in one coherent quota panel (or stacked cards) over narrow side-by-side cards. The current two-column cards risk truncating `Renews ...` text and make comparison harder on compact phones.
4. Each metric row should always show all four pieces: `5-hour` or `7-day`, large remaining percentage, a progress rail, and the full local renewal date/time. An exhausted Claude 5-hour limit remains visible as `0% remaining`, `Exhausted`, and `Renews ...`.
5. Use tabular numerals for percentages and dates. A 28–32 sp bold percentage, 14–16 sp label/body, and 12–14 sp metadata is enough; avoid multiple competing display-size values.
6. Put `Last updated ...` and the refresh action in one compact footer/header row. Pull-to-refresh may remain, but the visible 48 dp refresh button is required because swipe cannot be the only affordance.
7. Keep trends, authentication/errors, and stale-data messages below quota. Use a clear inline status row instead of another decorative card when possible.

### Visual system

- Retain the current Material 3 foundation, light/dark support, and provider identity, but reduce glass/blur/shimmer dominance. Use tonal surfaces and whitespace for hierarchy; reserve glass or an accent surface for one focal area only.
- Use a 4/8 dp spacing system and a small shape scale such as 12 dp for rows/cards and 20–24 dp for the single hero container. The current repeated 28 dp cards make unrelated elements look equally important.
- Use one semantic accent family per screen and neutral surfaces. Status colors mean `comfortable`, `tight`, and `exhausted`, but also pair them with text/icon/rail pattern so red/green is never the sole cue.
- Limit continuous animation to actual refresh/loading. Data changes can use short 150–250 ms transitions; avoid decorative infinite motion in a monitoring screen.

### English copy direction

Use short labels and one vocabulary consistently:

- `5h quota` → `5-hour limit`
- `7-day quota` → `7-day limit`
- `Reset ...` / `Renewal ...` → always `Renews ...`
- `%` alone → `% remaining`
- `Quota status is current.` → `Updated just now` (or the actual relative time)
- `Possibly stale` → `May be out of date`
- `Log in again to refresh quota.` → `Sign in again to update usage.`
- Home subtitle example: `Usage limits for this account`
- Exhausted row example: `0% remaining` / `Exhausted` / `Renews Sat, Aug 29 at 9:00 PM`

Avoid explanatory sentences where a label/value pair works. Keep provider names exactly `Claude` and `Codex`; don't repeat the app name inside screen or notification titles.

### Notification and widget

- Persistent notification: provider/account in the title, one line per limit in expanded text, and one clear `Refresh all` action. Example: title `Claude · Personal`; lines `5-hour 38% · Renews 9:00 PM` and `7-day 72% · Renews Tue 6:00 AM`.
- On the lock screen, assume expanded detail may be hidden by system privacy settings. The collapsed title should still identify the provider without exposing credentials or sensitive account identifiers.
- Widget: use a canonical text/list layout. At the smallest size show provider + primary remaining percentage + renewal; reveal both quota rows and updated time only at larger sizes. Keep every tappable control at least 48 dp.

## Reference rejected from implementation input

- https://github.com/canine-labs/m3-expressive-design-md was reviewed because it offers an M3 Expressive `DESIGN.md`, but it had no detectable license and almost no adoption at research time. It was not retained locally and should not be treated as a verified source.
