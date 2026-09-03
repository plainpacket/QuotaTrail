---
version: beta
name: QuotaTrail Field Instrument
description: A precise, glanceable Android dashboard for Claude and Codex usage limits.
colors:
  ink: "#1A1C1E"
  inkMuted: "#5F6065"
  background: "#F4F1EA"
  surface: "#FFFCF6"
  surfaceSubtle: "#F7F3EB"
  border: "#D1CBC0"
  accent: "#3154D5"
  accentSoft: "#E3E7FF"
  comparison: "#E7852F"
  success: "#1F7A63"
  successSoft: "#DDF2E9"
  warning: "#B96517"
  warningSoft: "#FFEBCF"
  danger: "#B54444"
  dangerSoft: "#FFE1DF"
typography:
  screenTitle:
    fontFamily: Geist Sans
    fontSize: 32px
    fontWeight: 700
    lineHeight: 1.16
  sectionTitle:
    fontFamily: Geist Sans
    fontSize: 18px
    fontWeight: 700
    lineHeight: 1.3
  body:
    fontFamily: Geist Sans
    fontSize: 14px
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: Geist Sans
    fontSize: 12px
    fontWeight: 600
    lineHeight: 1.35
  metric:
    fontFamily: Geist Mono
    fontSize: 32px
    fontWeight: 750
    lineHeight: 1.05
rounded:
  small: 8px
  medium: 12px
  card: 12px
  focal: asymmetric 8/28px
  pill: 999px
spacing:
  xxs: 4px
  xs: 8px
  sm: 12px
  md: 16px
  lg: 24px
  xl: 32px
components:
  metric-panel:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.focal}"
    padding: 16px
  metric-row:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.card}"
    padding: 16px
  supporting-card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.card}"
    padding: 16px
  primary-button:
    backgroundColor: "{colors.accent}"
    textColor: "#FFFFFF"
    rounded: "{rounded.small}"
    height: 48px
  bottom-navigation:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.inkMuted}"
    rounded: "{rounded.focal}"
    height: 64px
---

# QuotaTrail design

## 1. Purpose

This file is the source of truth for QuotaTrail UI, widget, notification, and UX work.
QuotaTrail is a monitoring utility, not an analytics suite. Its first job is to answer, within two
seconds:

1. Which provider and account am I viewing?
2. How much of each limit remains?
3. When does each limit renew?
4. Is the information current, and how do I refresh it?

Every existing feature remains available. The redesign changes information hierarchy, layout,
surface treatment, and English copy; it does not invent quota values or alter provider behavior.

## 2. Reference basis

Implementation decisions are based primarily on the downloaded Google references in
`design-references/`:

- Now in Android design file and Compose theme sources.
- Material Components color, typography, and shape documentation.
- Android mobile layout, accessibility, notification, and widget guidance linked from
  `design-references/SOURCES.md`.

The community dashboard `DESIGN.md` files are formatting and density references only. Google and
Android guidance wins if references disagree.

## 3. Product character

The visual direction is **Field Instrument**:

- Data first; decoration supports the data.
- Calm, precise, and native to Android.
- One asymmetric focal instrument per screen, not an identical rounded card around every section.
- Strong typographic hierarchy and aligned tabular numbers.
- Tonal surfaces and spacing create depth; shadows and blur stay subtle.
- Cobalt is the interaction accent; amber is reserved for the 5-hour comparison line.
- Green, amber, and red communicate status only and are always paired with words.
- Light and dark themes have equal information hierarchy and contrast.

Avoid a terminal aesthetic, neon glow, rainbow gradients, heavy blur, continuous shimmer, or dense
financial-dashboard styling.

## 4. Foundation

### 4.1 Grid and spacing

- Compact-screen horizontal margin: 16dp.
- Main vertical section gap: 24dp.
- Related-item gap: 8dp or 12dp.
- Component padding: 16dp.
- Use the 8dp grid; 4dp is allowed for small internal adjustments.
- The final scrollable item must clear the floating bottom navigation.

### 4.2 Shape

- Metric/supporting cards: 16dp.
- Single focal container: 20-24dp.
- Buttons, badges, segmented controls, and selected navigation indicators: full/pill.
- Dialogs and bottom sheets: 28dp where Material uses extra-large shape.
- Do not use 28dp on every content card; equal silhouettes imply equal importance.

### 4.3 Typography

- UI text uses Geist Sans; metrics use Geist Mono with tabular numerals.
- Metrics may use Geist Mono with tabular numerals.
- Screen title: 32sp bold.
- Section title: 18-20sp bold.
- Metric: 28-32sp bold; never compete with another display-sized number in the same row.
- Body: 14-16sp.
- Metadata: 12-14sp; do not go smaller to fit content.
- Percentages and dates use tabular numbers where available.

### 4.4 Color and surfaces

- Use Material semantic roles rather than hardcoded component colors.
- Background and surface containers provide most grouping.
- A focal quota panel may use a restrained accent tint; all other cards use neutral tonal surfaces.
- Status rail, icon, and label share a semantic tone, but body text retains accessible contrast.
- Text contrast is at least 4.5:1; meaningful non-text elements are at least 3:1.
- Provider logos retain recognizable shape and remain monochrome unless an official asset requires
  color.

### 4.5 Motion

- Motion explains refresh, selection, expansion, or navigation.
- Standard content transitions: 150-250ms.
- Continuous animation is limited to an active refresh/loading indicator.
- Quota status dots do not breathe indefinitely.
- Respect disabled system animations and reduced-motion behavior.

### 4.6 Product icon

- The launcher identity is an angular route passing through square telemetry nodes.
- Launcher background is cobalt; the primary route is white and its comparison node is amber.
- Keep the mark inside the adaptive-icon safe zone and provide round plus Android 13 monochrome
  layers.
- The notification icon uses the same meter silhouette as a simple one-color vector. It must not look
  like a warning or error symbol.
- Do not place a second rounded-square tile inside the adaptive icon.

## 5. App shell and navigation

- Three top-level destinations: `Usage`, `Accounts`, and `Settings`.
- Bottom navigation always shows both icon and text.
- Selected state uses a short cobalt edge marker; unselected items remain neutral.
- The dock is an opaque elevated Material surface, never translucent glass.
- A destination switch keeps scroll/state where practical.
- Top-level pages do not show redundant back buttons.

## 6. Home / Usage

### 6.1 Hierarchy

The screen order is:

1. Provider/account header and visible refresh action.
2. Account pager indicator when more than one account exists.
3. One quota panel containing every provider-reported limit.
4. Trend panel.
5. Compact freshness/error status.
6. Authentication or recovery action when required.

Do not repeat the app name, account name, provider, and freshness in multiple cards.

### 6.2 Header

- Screen title is the provider name (`Claude` or `Codex`).
- Subtitle is the account name, followed by a short state only when useful.
- Refresh is a visible 48dp icon button at the right edge.
- Pull-to-refresh remains available but is never the only refresh affordance.
- When multiple pages exist, show `1 of 2` plus dots beneath the header. TalkBack announces provider,
  position, and swipe availability.

### 6.3 Quota panel

- Use one coherent panel with full-width stacked metric rows.
- Never place 5-hour and 7-day limits in narrow half-width cards on compact phones.
- Preserve provider order and show only provider-reported windows.
- Each percent row contains:
  - `5-hour limit` or `7-day limit`.
  - Large `% remaining` value.
  - A short status label: `Comfortable`, `Running low`, `Tight`, or `Exhausted`.
  - A progress rail representing remaining quota.
  - Full local renewal text: `Renews Sat, Aug 29 at 9:00 PM`.
- The renewal line may wrap once; it must not be truncated.
- An exhausted Claude 5-hour limit remains visible as `0% remaining`, `Exhausted`, and its official
  renewal time.
- Missing official renewal data reads `Renewal unavailable`; never infer it.
- Balance/count windows retain their native value and unit while using the same row hierarchy.

### 6.4 Trend

- The trend is supporting information and follows quota.
- For Claude and Codex, show the overall 7-day and 5-hour limits' remaining percentages across the
  latest 72 hours. Use cobalt for 7-day and amber for 5-hour, with a compact legend.
- Use 72 hourly slots. Average multiple successful samples within one hour, retain exhausted samples
  at 0%, and leave a visible break where an hour has no sample.
- The Y axis is fixed at 0%, 50%, and 100%. The X axis uses `3d ago`, `2d ago`, `1d ago`, and `Now`.
- Do not infer, interpolate, zoom, or show tooltips.
- Empty state reads `Collecting hourly history` and does not imply an error.
- Providers without an overall 7-day window may retain the existing 24-hour consumption bars.

### 6.5 Freshness and errors

- Freshness is one compact status row/panel, not a second hero.
- Prefer actual information: `Updated 4 min ago`, `May be out of date`, or `Last update failed`.
- Keep last-known-good quota visible after a failure.
- `Re-login required` includes the direct action `Sign in again`.
- Error descriptions are short, safe, and never contain credentials or raw response data.

### 6.6 Empty/loading

- Empty title: `Connect Claude or Codex`.
- Supporting text: `See both usage limits here, in a widget, and in the notification shade.`
- Primary action: `Add account`.
- Initial loading uses stable skeleton/rows so the screen does not jump.

## 7. Accounts

- Screen title: `Accounts`.
- Subtitle: `Connections, alerts, and local account controls.`
- The add action is a 48dp filled icon button in the header.
- Do not show a redundant `Saved accounts` heading when the screen already says `Accounts`.
- Each collapsed account row shows provider icon, account name, connection state, last update, and
  chevron.
- Status badges use `Connected`, `Sign-in needed`, or `Disabled`.
- Tapping the row or chevron expands it.
- Expanded content keeps current features: plan/credits, 5-hour and 7-day summaries, quota alert
  switches, rename, re-login, and delete.
- Destructive action is visually separated and confirmed in a dialog.
- There is no `Set current` action; Home swiping controls the visible account.

## 8. Settings

- Screen title: `Settings`.
- Subtitle: `Choose how QuotaTrail updates and notifies you.`
- Use section labels plus one neutral 16dp card per group.
- Row titles are concise nouns or actions; descriptions explain consequences only when needed.
- Keep existing groups and behavior:
  - Appearance.
  - Status notification.
  - Alerts.
  - Background updates.
  - Local data.
  - Diagnostics.
  - About.
- Use dividers or spacing between rows inside a group; do not wrap each row in another card.
- Switch labels state the feature, not `Enable ...`.
- Choice rows show their current value at the trailing edge.
- Diagnostics stays collapsed by default.
- Safe builds do not show disabled upstream-update controls or provider settings that cannot affect
  Claude/Codex.

## 9. Authentication

- Provider selection uses a bottom sheet with clear provider names and official icons.
- Codex uses external device-code authentication.
- Claude uses the existing external-browser OAuth handoff and one-time code completion.
- Authentication screens use a standard top app bar with back navigation and at most one clearly
  named trailing action.
- Explain the next user action in one short paragraph; avoid implementation terms such as PKCE,
  callback URL, token, or cookie.
- Never display or log credentials, full OAuth queries, or raw provider responses.

## 10. Widget

- Widget remains glanceable and follows system light/dark theme.
- Smallest layout: provider, primary remaining percentage, and renewal time.
- Larger layouts reveal both 5-hour and 7-day rows plus updated time.
- Use `5-hour`, `7-day`, `% remaining`, and `Renews` consistently with the app.
- Account aliases are hidden where privacy requires it.
- Glass treatment may remain in the widget when it survives varied wallpaper contrast, but data and
  stable scrims win over refraction effects.
- Widget does not network directly and keeps all existing configuration behavior.
- Every configured widget size includes one quiet refresh icon near its account/status metadata.
  The control is 24dp with a 14dp glyph, uses the muted text color, and exposes the accessibility
  label `Refresh widget`; it is hidden when no account is available.
- Tapping the refresh icon updates only the account displayed by that widget without opening the
  app. Tapping anywhere else on the widget continues to open Home.
- A tap immediately shows the short toast `Refreshing…`. The worker reports `Quota refreshed`,
  `Refresh delayed. Retrying…`, or `Refresh failed` when the attempt reaches an outcome.

## 11. Persistent notification

- Notification is an auxiliary glance surface.
- Collapsed title identifies provider(s) and remaining quota without account aliases in aggregate or
  public lock-screen content.
- Expanded content uses the system `InboxStyle`: one row per official limit, two rows for one account
  and up to four provider-prefixed rows for Claude + Codex.
- Renewal timestamps use numeric English `MM/dd, h:mm a` in the device time zone (for example,
  `09/29, 9:00 PM`). Do not include weekday, month names, or year.
- Use the trail-teal accent for the system notification color and hide the meaningless post time on the
  ongoing status notification.
- Only action: `Refresh all`.
- Copy avoids repeating the title and stays concise enough for system truncation.
- Existing privacy, ongoing-notification, and WorkManager behavior remain unchanged.

## 12. English copy system

Use this vocabulary everywhere:

| Meaning | Required copy |
| --- | --- |
| Short window | `5-hour limit` |
| Weekly window | `7-day limit` |
| Percent value | `38% remaining` |
| Reset time | `Renews ...` |
| Missing reset | `Renewal unavailable` |
| Healthy status | `Comfortable` |
| First warning | `Running low` |
| Strong warning | `Tight` |
| No remaining quota | `Exhausted` |
| Fresh data | `Up to date` or `Updated ...` |
| Stale data | `May be out of date` |
| Invalid session | `Sign-in needed` / `Sign in again` |

Rules:

- User-visible app resources are English for every device locale.
- Use sentence case.
- Prefer label/value pairs over explanatory paragraphs.
- Do not use `quota` when `limit` is clearer to a person.
- Use `sign in` as a verb and `sign-in` as an adjective.
- Keep `Claude`, `Codex`, and `OpenAI` unchanged.
- App dates use the device locale and time zone. Persistent-notification renewal dates use the compact
  numeric `MM/dd, h:mm a` form in the device time zone.

## 13. Accessibility

- Every touch target is at least 48x48dp.
- Pager swiping has a visible indicator and an accessible non-gesture fallback through page
  semantics.
- Status never relies on color alone.
- Percent, limit name, status, renewal, and freshness form coherent TalkBack phrases.
- Text supports Android font scaling without clipped metrics or controls.
- Renewal metadata wraps instead of shrinking below 12sp.
- Destructive actions require confirmation.

## 14. Implementation boundaries

- Jetpack Compose + Material 3.
- UI consumes UI state only; it does not access Room, DataStore, network clients, or session data.
- Use `MaterialTheme` and QuotaTrail tokens; avoid one-off colors and dimensions.
- Widget and notification read clipped state models and never decrypt sessions.
- Preserve current provider APIs, account paging, refresh triggers, widget configuration, notification
  actions, and security boundaries.

## 15. Acceptance checklist

- [ ] Home title identifies the visible provider and account.
- [ ] Multiple accounts show provider, page position, dots, and horizontal paging.
- [ ] 5-hour and 7-day limits use full-width rows in one panel.
- [ ] Every percent value says `% remaining`.
- [ ] Every available reset time says `Renews ...` and is not truncated.
- [ ] Exhausted Claude 5-hour limit stays visible at 0%.
- [ ] Refresh has a visible 48dp control and pull-to-refresh still works.
- [ ] The 7-day remaining chart covers 72 hourly slots with fixed 0–100% and 3-day axes.
- [ ] Trend and freshness remain below the quota panel.
- [ ] Accounts has no `Set current` and no redundant saved-accounts heading.
- [ ] Settings groups retain their existing behavior with shorter copy.
- [ ] Bottom navigation remains Usage / Accounts / Settings with icon and label.
- [ ] Light and dark themes meet contrast requirements.
- [ ] No continuous decorative animation remains on idle monitoring screens.
- [ ] App, widget, and notification use the same limit/renewal vocabulary.
- [ ] UI, diagnostics, screenshots, and logs contain no credentials or raw OAuth data.
- [ ] Key screens are visually checked on a compact Android phone before release.
