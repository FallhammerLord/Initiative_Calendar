# Initiative

An Android home screen that runs your day like a GM runs a session. You see the current scene and whose turn it is; the app holds the notes.

- **The plan:** [docs/spec.md](docs/spec.md)
- **Getting it on your phone:** [docs/BUILDING.md](docs/BUILDING.md)

## Layout

| Folder | What's in it |
| --- | --- |
| `core/` | The scheduling engine in plain Kotlin: placement, priority, skip, recurrence, the quick-add parser, duration learning. No Android code, so it tests anywhere with `./gradlew -p core test`. |
| `app/` | The Android launcher: the board, the app drawer, the database. |
| `.github/workflows/` | The GitHub job that tests the engine, builds the APK, and runs it on an emulator on every push. |
| `app/schemas/` | The saved database layout for each version. CI fails if one changes without a migration. |
| `gradle/libs.versions.toml` | Every library version, shared by the app and the engine. |
| `scripts/emulator-smoke.sh` | The emulator checks: Home, drawer, Settings, home-app chooser, PIN unlock, board after unlock. |

## What works so far (first pass)

- Registers as a home app, with an app drawer, search, pinned apps, and exits to phone Settings and the home-app chooser
- The board: Now card with Done / Skip / Tomorrow, the reason line, Next peek, ledger line that expands into the day queue
- Board after unlock (off by default; the Home button does the job, and Android blocks the permission for sideloaded apps)
- A dock of six favourite apps along the bottom, as hexagon trios in the Sci-fi look
- Initiative settings (from the drawer): board after unlock, lock-screen card, theme, quiet hours, backup folder
- Focus mode: hold the card or tap Focus; a countdown to the next Firm or Hard wall, Soft alarms silent
- Alarms: the escalation ladder, rungs 1 to 3, with On it and Snooze, a full-screen alarm over the lock screen, and re-arming after a restart
- Lock-screen card: a silent Now card with a live countdown to the next anchor
- A setup line on the board that walks through each permission
- Undo after Done, Skip, and Tomorrow
- A shared design system (`ui/Design.kt`), one set of formatters (`core/.../Formats.kt`), and all wording in `strings.xml`
- Quick-add with the rule-based parser, including "Anchor or float?" chips
- Placement, Auto priority, skip reslot, recurrence instances, duration learning

- "Previously on": after a day untouched (or three missed floats in a row), a recap of what passed, what the app handled, and what's ahead
- Weekly recopy: once a week, walk the open list oldest first and keep, move, or drop each item
- Nightly backup: one JSON file to a folder you pick in settings (for Syncthing)
- Missed floats skip themselves when their window closes
- Built for Android 16 (API 36)

- Edit form: tap the Now title or a day-queue item to change it, or remove it with Undo
- Restore from a backup file, in settings
- Repeating items in the edit form: start, change, or stop a series; edit this time or this and later
- Lapse and recopy timings in settings
- A calendar file of anchors (initiative-anchors.ics) beside the nightly backup

Font: Atkinson Hyperlegible, SIL Open Font License ([docs/OFL-AtkinsonHyperlegible.txt](docs/OFL-AtkinsonHyperlegible.txt)).
