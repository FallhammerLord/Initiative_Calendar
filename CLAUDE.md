# Initiative: working notes

An Android home screen that runs the day like a GM runs a session. Private,
sideloaded, no Google services, no internet. The plan and every rule live in
`docs/spec.md`; read its **Standards** section before changing code.

## Layout

- `core/`: the scheduling engine in plain Kotlin (placement, priority, alarms
  ladder, recurrence, parser, formatters, and `Pass`, every decision the
  scheduler makes). No Android code. Test with
  `./gradlew -p core test` (works without the Android SDK).
- `app/`: the Android launcher. Services in `AppGraph.kt`; the board's state in
  `ui/BoardViewModel.kt`, with each feature in its own part (`RecapModel`,
  `RecopyModel`, `EditModel`, `BackupModel`, `SettingsModel`, `LauncherModel`, `LogModel`, `PairsModel`); design system in
  `ui/Design.kt` (tokens and components), `ui/Theme.kt` (themes: colors,
  shapes, outlines, and the look kit's slots), and `ui/Ornaments.kt` (the
  kit's small drawings); wording in `res/values/strings.xml`.
  Read colors through `Ink` inside composables; capture them into locals
  before drawing on a `Canvas`.
- `app/schemas/`: saved database layouts, one per version, committed.
- `scripts/design-guard.sh`: fails on code that bypasses the design system.
- `scripts/emulator-smoke.sh`: the emulator test CI runs before any release.

## Rules that CI enforces

- Engine tests, the app's migration test, and lint errors.
- `ThemeContrastTest`: every theme keeps text at 7:1 and outlines at 3:1.
- `scripts/design-guard.sh`: no hand-set font sizes or spacing outside `Design.kt`, no
  colors or corner shapes outside `Theme.kt`; no wording outside `strings.xml`; no time
  formatting outside `core/.../Formats.kt`; no service built outside `AppGraph`;
  no all-caps labels; no Material button outside `Design.kt`, and every text field
  uses `fieldColors()`, so buttons and fields always sit on the dark surface, never
  on the background pattern. Run it locally before pushing.
- A changed saved database layout fails. Raise the version, add a `Migration`,
  extend `MigrationTest`, and commit the new `app/schemas/.../N.json` (CI prints
  it as a warning the first time).
- The emulator test runs on Android 16 (the owner's Pixel 9 Pro): Home and every way back to the normal phone, the lock
  screen and PIN, alarms rung 1 to 3, Undo, 200% font, focus, the lapse recap,
  weekly recopy, and the edit form, a TalkBack-label sweep
  of every screen it visits, and StrictMode (main-thread writes, network, and
  leaked handles fail).

## How tests are written

Most CI failures so far were the tests, not the app. These rules keep them sturdy.

- Screens are tested as Compose UI tests in `app/src/test` (Robolectric, in the
  build job, about a minute, full messages). Find things by what they say or
  their description, never by position; `performScrollTo()` before touching or
  checking anything. Settings and a paired person's page are there now; when
  another screen changes, its checks move there too.
- `scripts/emulator-smoke.sh` covers only what needs a phone: being the home
  app and every way back, alarms and rungs, the lock screen and unlock,
  permissions, the drag, 200% font, StrictMode, the TalkBack sweep, and
  screenshots. Anything below the first screenful: `scroll_to` first.
- Typed text may come back capitalized by the keyboard: match from the second
  letter, or with `iexact`.
- Nothing depends on the real date or weekday: compute days relative to today.
- See each new check fail once before trusting it (in `core`, run it with the
  expectation flipped; elsewhere, read its failure message path).

## Rules CI can't fully check

- Build new screens from `Screen`, `Card`, `Strip`, the buttons, and the type scale.
  One primary button per screen. Space panels with `Space.zone` (24dp, between
  zones) and `Space.inside` (8dp, within one); see the spec's Layout rhythm.
- Readability: Atkinson Hyperlegible, sentence case, body text 18 sp or more,
  plain neutral words, no blame or counters.
- Every new screen gets emulator steps in `emulator-smoke.sh` (with `shot`, so
  the accessibility sweep covers it).
- Slow work (database, package queries, files) off the main thread.
- The backup folder and restore file pickers (Android's file picker) can't be
  automated; check them by hand on the phone. The restore itself is unit-tested.
- State a person typed or opened survives a process restart (`rememberSaveable`
  in screens, `SavedStateHandle` in the view model).
- Times are wall-clock with no zone; turn them into real moments only through
  `WallClock` in `core/`.
- In `emulator-smoke.sh`, check with `expect_text`, `wait_for`, and `wait_gone`,
  which wait for the screen to settle, rather than a single `see`.

## Working on this repo

- Develop on `claude/kind-wright-nwzx7a` (the default branch).
- The Android SDK isn't reachable from Claude's cloud container; GitHub Actions
  builds the APK. Fetch emulator screenshots from a release's `screens-N.zip`,
  or a `failed-N` pre-release when the test fails.
- A new push cancels any run still going on the branch, and only the newest
  commit is released. Every failed CI run emails the owner. Batch fixes into one validated push;
  use `[skip ci]` in the commit message for docs-only changes.
- The owner reads with dyslexia: keep replies and on-screen copy short, plain,
  and direct.
