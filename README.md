# Initiative

An Android home screen that runs your day like a GM runs a session. You see the current scene and whose turn it is; the app holds the notes.

- **The plan:** [docs/spec.md](docs/spec.md)
- **Getting it on your phone:** [docs/BUILDING.md](docs/BUILDING.md)

## Layout

| Folder | What's in it |
| --- | --- |
| `core/` | The scheduling engine in plain Kotlin: placement, priority, skip, recurrence, the quick-add parser, duration learning. No Android code, so it tests anywhere with `./gradlew -p core test`. |
| `app/` | The Android launcher: the board, the app drawer, the database. |
| `.github/workflows/` | The GitHub job that tests the engine and builds the APK on every push. |

## What works so far (first pass)

- Registers as a home app, with an app drawer, search, pinned apps, and exits to phone Settings and the home-app chooser
- The board: Now card with Done / Skip / Tomorrow, the reason line, Next peek, ledger line that expands into the day queue
- Board after unlock (switch in the app drawer)
- Quick-add with the rule-based parser, including "Anchor or float?" chips
- Placement, Auto priority, skip reslot, recurrence instances, duration learning

Not yet: alarms and the escalation ladder, focus mode, lapse recap, weekly recopy, editing form, nightly export.

Font: Atkinson Hyperlegible, SIL Open Font License ([docs/OFL-AtkinsonHyperlegible.txt](docs/OFL-AtkinsonHyperlegible.txt)).
