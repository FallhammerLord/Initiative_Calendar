# Initiative: working notes

An Android home screen that runs the day like a GM runs a session. Private,
sideloaded, no Google services, no internet. The plan and every rule live in
`docs/spec.md`; read its **Standards** section before changing code.

## Plan mode and build mode

This comes before everything below, in every session. The owner sets the mode;
when it isn't clear which one a message means, ask in one line.

- **Plan mode** (gathering friction, ideas, "look at this"): talk, mock up, and add
  to the spec's Next list, then stop. No code, tests, merges, or pushes of code
  unless the owner asks for that piece. Saving the plan to `docs/spec.md` when asked
  is fine, pushed alone with `[skip ci]`.
- **Build mode** (the owner has said go on a piece of work): build it all the way
  to a release ready to install. That includes fixing what CI finds, test glitches
  too, and re-running or pushing again until it's green, within what was asked.
- **Either way, keep the dialogue open.** Say what you decided and why as you go,
  not after: a rule you invented, a change from a mockup, a skipped check-in, a
  merge. Report every CI result, pass or fail.
- Another session's branch or commit is never merged or built on without a report
  of what it does and the owner's go.

## Layout

- `core/`: the scheduling engine in plain Kotlin (placement, priority, alarms
  ladder, recurrence, parser, formatters, and `Pass`, every decision the
  scheduler makes). No Android code. Test with
  `./gradlew -p core test` (works without the Android SDK).
- `app/`: the Android launcher. Services in `AppGraph.kt`; the board's state in
  `ui/BoardViewModel.kt`, with each feature in its own part (`RecapModel`,
  `RecopyModel`, `EditModel`, `BackupModel`, `SettingsModel`, `LauncherModel`, `LogModel`, `PairsModel`, `NamesModel`); design system in
  `ui/Design.kt` (tokens and components), `ui/Theme.kt` (themes: colors,
  shapes, outlines, and the look kit's slots), `ui/Ornaments.kt` (the
  kit's small drawings), and `ui/Effects.kt` (the HUD effects); wording in `res/values/strings.xml`.
  Read colors through `Ink` inside composables; capture them into locals
  before drawing on a `Canvas`.
- `app/schemas/`: saved database layouts, one per version, committed.
- `scripts/design-guard.sh`: fails on code that bypasses the design system, and
  (through `scripts/emulator-labels.py`) on an emulator step whose label the app no longer has.
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
- The emulator test runs on Android 16 (the owner's Pixel 10 Pro): Home and every way back to the normal phone, the lock
  screen and PIN, alarms rung 1 to 3, Undo, 200% font, focus, the lapse recap,
  weekly recopy, and the edit form, a TalkBack-label sweep
  of every screen it visits, and StrictMode (main-thread writes, network, and
  leaked handles fail).

## How tests are written

Most CI failures so far were the tests, not the app. These rules keep them sturdy.

- Screens are tested as Compose UI tests in `app/src/test` (Robolectric, in the
  build job, about a minute, full messages). Find things by what they say or
  their description, never by position; `performScrollTo()` before touching or
  checking anything. Settings, a paired person's page, the pairmark row, the Now card, name suggestions, the manual, the Log, the edit form, and dock trays are there now (give a screen with a list a phone-sized `qualifiers`, or the list gets no room); when
  another screen changes, its checks move there too.
- `scripts/emulator-smoke.sh` covers only what needs a phone: being the home
  app and every way back, alarms and rungs, the lock screen and unlock,
  permissions, the drag, 200% font, StrictMode, the TalkBack sweep, and
  screenshots. Anything below the first screenful: `scroll_to` first.
- Typed text may come back capitalized by the keyboard: match from the second
  letter, or with `iexact`.
- A message that shows for only a few seconds (a flash, an Undo line) is
  checked with one `wait_for` right after the tap. When it comes at the end of
  something long (a search, a timer), log the outcome and check the log, as
  the Ping step does.
- The emulator test leaves Idle effects off: steady motion keeps the screen
  from settling, and the screen reader (`uiautomator dump`) waits for it to settle.
- Nothing depends on the real date or weekday: compute days relative to today.
- To see a screen before pushing, render it locally: a scratch Robolectric test
  with `@GraphicsMode(GraphicsMode.Mode.NATIVE)`, `createAndroidComposeRule<ComponentActivity>()`,
  and a phone-sized `qualifiers` (add `-xxhdpi`); advance `mainClock`, then draw
  `activity.window.decorView` into a `Bitmap` and save a PNG (`captureToImage`
  times out on the looks' steady drawing). Delete the scratch test after.
- See each new check fail once before trusting it (in `core`, run it with the
  expectation flipped; elsewhere, read its failure message path).

## Rules CI can't fully check

- A new action or rule gets a line in the manual (`ManualScreen.kt`, wording in
  `strings.xml`), so the manual matches the build.
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
- `emulator-smoke.sh` doesn't stop at a failure: `fail` records it (line, last
  passing check, a screenshot `zz-failure-N.png`) and the run goes on, listing
  every failure at the end; it stops after 10. Fix the first one first: later
  ones may follow from it. A step that can't go on without what failed still
  needs its own `|| return`, or a variable set before it's used.
- In `emulator-smoke.sh`, check with `expect_text`, `wait_for`, and `wait_gone`,
  which wait for the screen to settle, rather than a single `see`.

## Working on this repo

- Develop on `claude/kind-wright-nwzx7a` (the default branch).
- The Android SDK installs in Claude's cloud container from `dl.google.com`:
  command-line tools into `~/android-sdk`, then `sdkmanager "platforms;android-36"
  "build-tools;35.0.0" "platform-tools"`. With `ANDROID_HOME=~/android-sdk`, run
  `./gradlew :app:testDebugUnitTest` and `./gradlew :app:lintRelease` before every
  push; a new database layout file is written there too. Maven Central may answer
  429 at first: retry after a minute. The emulator test still runs only in CI.
  Fetch its screenshots from a release's `screens-N.zip`, or a `failed-N`
  pre-release when it fails.
- A new push cancels any run still going on the branch, and only the newest
  commit is released. Even a `[skip ci]` push during a run stops that run from
  releasing, since its commit is no longer the newest: wait for the run to end. Every failed CI run emails the owner. Batch fixes into one validated push;
  use `[skip ci]` in the commit message for docs-only changes.
- The owner reads with dyslexia: keep replies and on-screen copy short, plain,
  and direct.
