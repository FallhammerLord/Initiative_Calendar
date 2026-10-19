# Initiative: working notes

An Android home screen that runs the day like a GM runs a session. Private,
sideloaded, no Google services, no internet. The plan and every rule live in
`docs/spec.md`; read its **Standards** section before changing code.

## Layout

- `core/`: the scheduling engine in plain Kotlin (placement, priority, alarms
  ladder, recurrence, parser, formatters). No Android code. Test with
  `./gradlew -p core test` (works without the Android SDK).
- `app/`: the Android launcher. Services in `AppGraph.kt`; design system in
  `ui/Design.kt` (tokens and components) and `ui/Theme.kt` (themes: colors,
  shapes, outlines, pattern, font); wording in `res/values/strings.xml`.
  Read colors through `Ink` inside composables; capture them into locals
  before drawing on a `Canvas`.
- `app/schemas/`: saved database layouts, one per version, committed.
- `scripts/design-guard.sh`: fails on code that bypasses the design system.
- `scripts/emulator-smoke.sh`: the emulator test CI runs before any release.

## Rules that CI enforces

- Engine tests, the app's migration test, and lint errors.
- `ThemeContrastTest`: every theme keeps text at 7:1 and outlines at 3:1.
- `scripts/design-guard.sh`: no hand-set font sizes outside `Design.kt`, no
  colors or corner shapes outside `Theme.kt`; no wording outside `strings.xml`; no time
  formatting outside `core/.../Formats.kt`; no service built outside `AppGraph`;
  no all-caps labels. Run it locally before pushing.
- A changed saved database layout fails. Raise the version, add a `Migration`,
  extend `MigrationTest`, and commit the new `app/schemas/.../N.json` (CI prints
  it as a warning the first time).
- The emulator test: Home and every way back to the normal phone, the lock
  screen and PIN, alarms rung 1 to 3, Undo, 200% font, a TalkBack-label sweep
  of every screen it visits, and StrictMode (main-thread writes, network, and
  leaked handles fail).

## Rules CI can't fully check

- Build new screens from `Screen`, `Card`, the buttons, and the type scale.
  One primary button per screen.
- Readability: Atkinson Hyperlegible, sentence case, body text 18 sp or more,
  plain neutral words, no blame or counters.
- Every new screen gets emulator steps in `emulator-smoke.sh` (with `shot`, so
  the accessibility sweep covers it).
- Slow work (database, package queries, files) off the main thread.
- State a person typed or opened survives a process restart (`rememberSaveable`).

## Working on this repo

- Develop on `claude/kind-wright-nwzx7a` (the default branch).
- The Android SDK isn't reachable from Claude's cloud container; GitHub Actions
  builds the APK. Fetch emulator screenshots from a release's `screens-N.zip`,
  or a `failed-N` pre-release when the test fails.
- Every failed CI run emails the owner. Batch fixes into one validated push;
  use `[skip ci]` in the commit message for docs-only changes.
- The owner reads with dyslexia: keep replies and on-screen copy short, plain,
  and direct.
