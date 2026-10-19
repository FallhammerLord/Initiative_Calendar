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
| `scripts/emulator-smoke.sh` | The emulator checks: Home, drawer, Settings, home-app chooser, PIN unlock, board after unlock. |

## What works so far (first pass)

- Registers as a home app, with an app drawer, search, pinned apps, and exits to phone Settings and the home-app chooser
- The board: Now card with Done / Skip / Tomorrow, the reason line, Next peek, ledger line that expands into the day queue
- Board after unlock (switch in the app drawer)
- Alarms: the escalation ladder, rungs 1 to 3, with On it and Snooze, a full-screen alarm over the lock screen, and re-arming after a restart
- Lock-screen card: a silent Now card with a live countdown to the next anchor (switch in the app drawer)
- A setup line on the board that walks through each permission
- Quick-add with the rule-based parser, including "Anchor or float?" chips
- Placement, Auto priority, skip reslot, recurrence instances, duration learning

Not yet: focus mode, lapse recap, weekly recopy, editing form, nightly export.

Font: Atkinson Hyperlegible, SIL Open Font License ([docs/OFL-AtkinsonHyperlegible.txt](docs/OFL-AtkinsonHyperlegible.txt)).
