# Initiative — Scheduling Launcher Spec

Oct 19, 2026 · @Ken · rev 2

## Purpose

Initiative is an Android home screen that runs your day like a GM runs a session. It holds the notes; you see the current scene and whose turn it is.

It is built for a brain where sustained routine engagement is unreliable. So the app has to work when ignored, and welcome you back after a lapse with no debt.

Design rules:

- **Presence through placement.** It lives on the home screen and comes forward after every unlock, so you see the board each time you pick up the phone. The system lock screen stays as it is.
- **Interrupt rarely, and only when it matters.** Fixed commitments can interrupt. Flexible tasks never do.
- **No guilt state.** No overdue counters, red badges, or streaks.
- **One thing at a time.** The board shows one card. Everything else is one tap or swipe away.
- **Owned end to end.** Local data, no accounts, no Google services, no internet traffic by design. The only network use is sync with your own desktop on home Wi-Fi.

Success looks like this after 90 days: still installed, hard commitments reliably hit, and lapses end with a return to the board.

## Platform and constraints

A sideloaded Android app in Kotlin that runs the same on stock Android today and GrapheneOS later. For private use only; it is never distributed.

- **Launcher.** Registers as a home app. You set it as default once.
- **Its own calendar.** All events and tasks live in the app's local database. It reads no other calendar.
- **No Google dependency.** No Play Services, no Firebase, no cloud push. Alarms are scheduled on the device.
- **Home network sync.** The phone is the primary home of all data. The desktop counterpart is a secondary window that syncs over home Wi-Fi and sends its edits back to the phone. The app talks only to addresses on the local network and makes no internet requests. Android's network permission is all-or-nothing, so this is enforced in the app's code, not by the OS.
- **Distribution.** A signed APK you install directly. Updates are a new APK over the old one.
- **Model files.** Speech and language models ship as separate files the app loads from a folder you pick, so APK updates stay small. Syncthing can deliver them.
- **Target.** Android 10 or newer, so most phones from the last six years qualify. Built for and tested on Android 16 (API 36).

**On-device language model (optional).** The rule-based parser is the baseline. Phrases it can't handle fall back to a small Qwen model, run by llama.cpp with a grammar constraint that only allows valid task fields. No Claude or other cloud AI.

**Vendor constraint.** No Meta components anywhere in the stack: no Llama models, no Meta libraries or SDKs. llama.cpp and whisper.cpp are independent (ggml, MIT license) and allowed. Check every new dependency against this rule.

## Data model

Every entry is an Item, and every Item is either an Anchor or a Float.

- **Anchor.** Fixed time. School runs, appointments, game nights. Anchors are the walls of the day.
- **Float.** Flexible task. Calls, chores, errands. Floats fill the gaps between walls.

| Field | Applies to | Values |
| --- | --- | --- |
| Title, notes | Both | Free text |
| Kind | Both | Anchor, Float |
| Start, end | Anchor | Date and time |
| Wall strength | Anchor | Soft, Firm, Hard |
| Lead time | Anchor | Minutes of warning before start, default 15 |
| Partner alert | Anchor | On or off, Hard only |
| Duration | Float | Estimate in minutes: 15 for Quick tasks, 30 for Focus; learned over time |
| Earliest start | Float | Optional date |
| Deadline | Float | Optional date |
| Priority | Float | Auto (default), or Low, Normal, High set by hand |
| Effort | Float | Quick, Focus |
| Skip count | Float | Times skipped, manual or automatic |
| Recurrence | Both | See Recurrence |
| Miss policy | Both | Reroll, Drop, Ask |
| Status | Both | Pending, Active, Done, Missed, Dropped |
| Pinned slot | Float | Optional time you fix by hand |

An event log records every status change with a timestamp. The recap screen and duration learning read from it.

Settings hold the day window, quiet hours, the partner's phone number, default lead time, default duration, and the lapse thresholds.

### Recurrence

A recurring item is stored as a template. The app generates one instance per occurrence, a few weeks ahead. Each instance has its own status, so missing one never touches the others. Editing the template updates future instances only.

| Pattern | Options |
| --- | --- |
| Daily | Every day, or every other day |
| Weekdays | Monday to Friday |
| Weekly | Chosen days, every week or every other week |
| Monthly | Same date or same weekday (e.g. second Tuesday), every month or every other month |
| Yearly | Same date, every year or every other year |

### Learned details

Repeated items get better estimates as you use them.

- **Duration.** When an item goes from Active to Done, the elapsed time updates its estimate as a rolling average of the last five runs. Outliers beyond 3× the estimate are ignored.
- **Which items count as repeated.** Instances of one recurring template, and floats with the same title (case-insensitive) created again.
- **Other fields.** A new float with a known title inherits that title's last priority, effort, and miss policy. You can override any of them.

## The Board

The board is a hybrid. It uses Board B's single card and buttons, and folds Board A's Next card and wall strip into one ledger line.

Top to bottom: glance at the top, act at the bottom, where the thumb reaches. Steven Hoober's field study of how people hold phones found the bottom middle easiest to reach and the top corners hardest; Pixel's own home screen keeps glanceable facts at the top for the same reason (cited from memory).

0. **Plan ahead panel.** The time, large, and the date, over a faint arc running from the start of the planning day to its end, with a dot for now: how much day is left, without needing location. Tap it to open the Log. At large font sizes it keeps only the time. No weather: the app has no internet, and Android has no weather source an app can read.
1. **Now card.** Sits on the bottom group, below an open space that takes up whatever's left. One item in large type, its steps as a checklist, and a one-line reason, such as "Due tomorrow, fits before pickup." Three buttons: **Done**, **Skip**, **Tomorrow**. The card grows upward, so the buttons stay in one place whatever the card holds; its words scroll if they overflow, and the buttons never do.
2. **Next peek.** One small line under the card. For an anchor it shows a countdown: "School pickup in 1h 40m." It is also the handle of the day panel: tap it or swipe it up to open the panel; swipe down, tap outside, or press Back to fold it.
3. **Ledger line and forecast.** Inside the day panel, above the forecast: done items as ticks, the current item marked, upcoming walls as markers. Hard walls are visually heavier. The open panel lies over the board as a sheet, so the Now card never shrinks to make room. Below the line, the forecast lists the rest of today with times: anchors at their starts, and tasks filling the free time between in rank order. A skipped or missed task lands at the time it's back, not at the bottom. Tasks that can't fit today sit under Later. It's a forecast, not a promise, and reshuffles as the day goes.

**Reordering.** Hold a task in the forecast and drag it to a new place. It lands there only if it fits before the next anchor's warning; it can never displace an anchor, and anchors can't be dragged. A task that lands is held to that time; if the time passes undone, it's free to be placed again. Your order also breaks ties when the rules rank tasks equally; priority and due dates still lead. TalkBack gets Move earlier and Move later. Quick-add's empty field reads only "Type a new task", with no example task to catch the eye; an Add button appears once there's text.
4. **Quick-add bar.** One line of text to capture anything, with a mic button.
5. **Apps.** Below the dock, a slim ⌃ pull tab opens the app list with a tap; it's the visible twin of the swipe. A dock of six fixed slots for favourite apps runs along the bottom. Tap a slot to open its app; an empty slot shows + and opens a picker; hold a slot to change or empty it. Holding an app in the drawer adds it to the first empty slot. In the Sci-fi look the slots are nested hexagon trios (two stacked, one tucked to the right, each tile's point in the next one's notch); taps go by each tile's real outline, not its box. In Classic they're plain squares. A swipe up that starts in this bottom band (the dock and the Apps button, above Android's own gesture strip) opens the app list; a swipe anywhere else on the board doesn't. The pull tab does the same with a tap.

**Board after unlock.** Unlocking normally returns you to whatever app was open. With this setting on, the board moves to the front when the screen turns off, so the next unlock lands on it with no visible jump. Unlock itself is a backup trigger. Calls are left alone. It needs Android's "Display over other apps" permission, which Android 15 and later block for sideloaded apps until "Allow restricted settings" is chosen in the app's info page. On the owner's Pixel 9 Pro (Android 16) the unlock worked: Settings, Apps, Initiative, ⋮, Allow restricted settings, then Display over other apps. With it on, the board after unlock runs smoothly. It stays off by default, since a new install needs those steps first; as the Home app, the board is one press of Home away meanwhile. The switch is in Initiative settings; when on, the board shows a one-line prompt until the permission is granted.

**Lock-screen card.** A silent ongoing notification shows the Now card on the lock screen: "Now: call landlord · Next: pickup in 1h 40m." No sound, no badge, no counter. It refreshes at each gap boundary through the same scheduler as the alarms. Android phones have no lock-screen widgets, so this is the glance before unlocking; board after unlock is the full view after it.

**Initiative settings.** Opened from the app drawer's header. Grouped as Board (board after unlock, lock-screen card), Look (theme), and Alarms (quiet hours, with start and end times). The drawer itself holds only apps and the two exits to the normal phone.

The Log, from the Plan ahead panel, holds the week and beyond. It exists for planning and is never required.

When nothing needs doing, the Now card says **Free time**. An empty board is a valid, calm state.

**Buttons.**

- **Done** marks it finished. With steps still open it asks first, on purpose: tick what's done, then **All done** (the item and every step) or **Not yet** (it stays as it is). Back, or a tap outside, is Not yet.
- **Skip** moves the card to its next reasonable slot and shows where it landed, e.g. "Back at 3:30, after pickup." See Placement logic.
- **Tomorrow** takes it off today's board entirely.

**Focus.** Hold the card for about two-thirds of a second, or tap the small Focus button. See Focus mode.

**Weekly recopy.** Derived from Ken's old sticky-pad system: one list, recopied weekly, finished items simply fall off. Once a week the single card becomes "Recopy the list." It walks every open item, oldest first, one card at a time: Keep, Reschedule (later this week or next week), or Drop. Items skipped three or more times get a nudge to drop them or give them a real time.

**Readability.** Large type, high contrast, short labels. Font choice in settings, with Atkinson Hyperlegible as the default. It's an open-licensed font designed for low-vision readers.

**Themes.** Picked in Initiative settings. Each theme sets colors, card and button shapes, outlines, and an optional background pattern; layout and type stay the same.

- **Sci-fi** (default), after the Foundry quest log module: navy panels with thin teal frames and cut corners, cyan accents, a faint hexagon grid behind each screen. Section labels keep the quest log's cyan and bold but stay in sentence case.
- **Classic**: black and amber, rounded.

Every theme keeps all text at 7:1 contrast or better and outlines at 3:1; a CI test checks each one. Atkinson Hyperlegible stays the body font in every theme; a decorative font may only ever be used for titles.

&#91;embedded content: board mockup · first pass\]

The clickable prototype keeps Board A and Board B side by side for comparison by feel. Where the two disagree, Board B wins.

## Placement logic

The app picks the Now card for you, every time you look, using a fixed and explainable rule.

1. If an anchor is in progress, it is Now. That holds after it's marked Done on arrival: it stays Now, with no buttons, until its end ("Under way until 4:00 PM"), since you're still there. Skip frees the time instead.
2. If the next anchor's lead-time warning has passed, that anchor is Now: "Starts in 10 min."
3. If a float is pinned to this slot, it is Now.
4. Otherwise, find the current gap: the time until the next anchor's lead-time warning, or the end of the day window.
5. List floats that are eligible today: past their earliest start, not sent to Tomorrow, and short enough to fit the gap.
6. A float longer than every gap today is eligible for the day's longest gap instead, so big tasks still surface.
7. Rank them: priority first, then nearest deadline, then oldest.
8. The top float becomes Now. The reason line states which rule picked it.
9. If nothing fits, Now is Free time.

The day window runs from 8:00 to 22:00 by default and is set in settings. Outside it, Now is Free time unless an anchor is due.

The board recalculates on every open and every button press. Dragging is never required.

### Priority

You can set priority by hand. When you don't, it is Auto, and the app works it out from three signals:

- **Deadline.** Due today, tomorrow, or already past raises it to High.
- **Skips.** Every two skips raise it one step, up to High.
- **Age.** A float open for two weeks rises one step.

A float with no deadline, no skips, and recent creation sits at Normal. A hand-set priority is never changed by the app. The reason line names the signal, e.g. "Skipped twice, moved up."

### Skip and reslot

Skip, by hand or automatic, does two things:

1. **Reslot.** The float moves to the next gap it fits, after the current one, and the card shows where it landed. If there is no later gap today, it moves an hour later in the current gap when it still fits there. Otherwise it goes to tomorrow's first fitting gap.
2. **Reshuffle.** The skip count goes up, Auto priority is recalculated, and the rest of today's queue re-ranks around it.

A missed float is an automatic skip. When a float is Now and its gap ends untouched, the app skips it for you. Skips are never shown as misses, counters, or badges. They only feed priority, the recopy nudge, and the lapse rule.

## Focus mode

Holding the card, or tapping Focus, says "I'm in it," and the app protects the burst until the next real wall.

- The board collapses to the current task, a countdown to the next Firm or Hard anchor, a **Done with it** button (marks the task done, with Undo), and **End focus**. Focus is offered on floats only.
- Back never ends focus; it keeps going until the wall, Done with it, or End focus.
- The lock-screen card reads "Focus: <task>".
- Float prompts and Soft anchors go silent. Automatic skips pause.
- A wrap-up warning fires at the wall's lead time: "Pickup in 15. Save your work."
- Focus ends at the wall, or when you end it. It has no timer of its own.

This turns hyperfocus into an asset the app respects, with the walls still guarded.

## Escalation ladder

Only anchors can interrupt. Each anchor climbs one rung at a time until you respond, and its wall strength sets the highest rung it can reach. One tap on **On it** at any rung stops the climb.

| Rung | What happens | When it fires |
| --- | --- | --- |
| 0. Board | The anchor shows on the Next peek and ledger line. No alert. | Always |
| 1. Quiet notice | Silent notification with the countdown. | At lead time |
| 2. Nudge | Notification with sound and vibration, shown over other apps. | 5 minutes before start |
| 3. Alarm | Full-screen alarm with sound until you respond. | At start |
| 4. Partner | Pre-written SMS to your partner. | 5 minutes after start, if no On it |

| Wall strength | Highest rung |
| --- | --- |
| Soft | 2. Nudge |
| Firm | 3. Alarm |
| Hard | 3. Alarm, or 4. Partner when Partner alert is on |

If the lead time is under 5 minutes, rungs 1 and 2 merge into one nudge at lead time.

- **Snooze limits.** Soft and Firm allow two 5-minute snoozes. Hard allows one. A snooze pauses the climb; it resumes at the same rung.
- **Trust rule.** Every interruption must be worth it. A rung that fires when you didn't need it means the anchor's strength is set too high.
- **Anti-habituation.** Each rung rotates through several phrasings, so the words stay readable. Sound rotation comes in v0.3.
- **Quiet hours.** Only Hard anchors break through.
- **Focus mode.** Soft anchors stay at rung 0. Firm and Hard climb as normal.
- **Missed anchors.** An anchor with no On it by its end time is marked Missed.

## Lapse recovery

After a lapse, you get one "Previously on" screen, then a clean board.

A lapse starts when the board goes untouched for 24 hours, or three automatic skips happen in a row. Both thresholds are adjustable.

The recap shows three short blocks:

1. **What passed.** Anchors since you were last here, marked done or missed in neutral words.
2. **What the app handled.** Automatically skipped floats sorted by their miss policy. Reroll items went back in the queue. Drop items went to the archive.
3. **What's ahead.** Anchors in the next 24 hours.

Items set to **Ask** get one batch triage: Keep or Drop on each, plus a single Drop all button.

One tap on "Back to the board" closes the recap. The dropped items never appear again unless you go looking in the archive.

## Capture

One line in the quick-add bar creates an item, parsed on the device.

| You type | You get |
| --- | --- |
| dentist thu 3pm | Anchor, Thursday 3:00 PM, Firm |
| call landlord | Float, Auto priority, 30 min |
| pickup girls weekdays 3pm hard | Recurring Hard anchor, weekdays |
| taxes by fri high | Float, deadline Friday, High priority |
| game night tue 7pm soft | Anchor, Tuesday 7:00 PM, Soft |
| bins every other mon 7am | Recurring Firm anchor, every other Monday |

The rule of thumb: a time makes an anchor, no time makes a float. Words like *hard*, *soft*, *high*, *by*, and *every other* set the extra fields.

When the parser is unsure, it asks one question as a tap chip: "Anchor or float?"

- **Share target.** Text shared from any app lands in quick-add.
- **Voice.** A large mic button on the board. Speak a task and the parser creates the item with no typing. Speech runs on the phone through Whisper (whisper.cpp, base model), fully offline, the same on stock Android and GrapheneOS. A tap chip confirms when the parser is unsure.
- **Editing.** Tap any card's title to open a full form with every field.

## Data, backup, permissions

All data lives in one local SQLite database. It leaves the phone only as files you choose to move, or through home Wi-Fi sync with your desktop.

**Backup.** Each night the app writes two files to a folder you pick: a full JSON export and an .ics file of anchors. A Syncthing client watches that folder and copies it to the homelab. Restore reads the JSON back in.

**Import.** Any .ics file, such as a school calendar, imports as anchors. You set wall strength during import.

| Permission | Why |
| --- | --- |
| Home app | Lets it be the launcher |
| Exact alarms | Anchors fire on the minute |
| Full-screen alerts | Rung 3 alarm for Firm and Hard walls |
| Notifications | Rungs 1 and 2 |
| Start at boot | Re-arms alarms after a restart |
| Battery exemption | Stops the system from delaying alarms |
| Send SMS | Rung 4 only; requested when you turn it on |
| Display over other apps | Board after unlock |
| Network | Home Wi-Fi sync only |
| Microphone | Voice capture; requested on first use |

The app requests no location, contacts, or storage-wide access.

### Implementation notes

- **Alarms.** Schedule rung 3 with `setAlarmClock()`. Android exempts it from Doze and shows it in the status bar.
- **Exact alarm permission.** Declare `USE_EXACT_ALARM`. It is granted at install on Android 13 and newer. Play Store rules limiting it to alarm apps don't apply to a sideloaded build.
- **Full-screen alerts.** On Android 14 and newer, this needs a one-time grant in system Settings. First-run setup walks you through it, along with the battery exemption.
- **Licenses.** Private use only, so model licenses are not a constraint. The vendor rule still applies.

## Standards

Three rule sets every change follows. Under each, the checklist names the gaps found in the October audit; ticked items are closed. Where the pieces live: tokens and components in `app/.../ui/Design.kt`, colors and font in `Theme.kt`, formatters in `core/.../Formats.kt`, wording in `res/values/strings.xml`, services in `AppGraph.kt`, versions in `gradle/libs.versions.toml`.

**Guards.** CI enforces what it can: `scripts/design-guard.sh` blocks hand-set sizes, colors, shapes, wording, time formats, and services outside their one home; the emulator test sweeps every screen it visits for unlabelled controls and fails on StrictMode main-thread writes, network use, or leaked handles. `CLAUDE.md` carries the rest for any future session.

### Android engineering

- **Layers.** Screens talk to a ViewModel, the ViewModel to the repository, the repository to the database. Scheduling rules stay in `core/`, with no Android code.
- **Small parts.** The board's ViewModel holds the board, focus, quick-add, and which screen is open. Each other feature (recap, recopy, backup, settings, app list) is its own small class that shares the ViewModel's scope.
- **Wall-clock times.** Stored times have no zone: 3:00 PM stays 3:00 PM wherever the phone is. `core/.../WallClock.kt` is the one place they become real moments, for alarms and countdowns, so daylight-saving changes are handled once.
- **One of each service.** The database, repository, scheduler, and settings come from one app container, created once in `InitiativeApp`.
- **Main thread for the screen only.** Database work, app-list queries, and file work run in the background.
- **Survive being closed by Android.** What you typed or opened comes back after the system reclaims the app.
- **Schema history.** The database layout is saved in the repo, and every migration has a test.
- **Words in resources.** On-screen text lives in `strings.xml`, with plural forms where counts appear. One exception: the engine in `core/` writes the reason line ("Due tomorrow, fits before pickup") in English, since it has no Android resources. Moving reasons to structured codes is a later step if translation is ever wanted.
- **Lint in CI.** Android's code checker runs on every push with the engine tests and emulator check. Errors fail the build; warnings go to the report.
- **Small release build.** Code shrinking is on for releases. The APK is 19 MB today, mostly unused library code.
- **One version list.** Library versions live in one Gradle version catalog.
- **Least privilege.** Every permission maps to a feature in the permissions table, receivers stay private unless the system must reach them, and pending intents are immutable. This already holds.

Gaps today:

- [x] The repository and placement engine are built twice, in the ViewModel and the scheduler, each with its own day window. A settings change would need making in both.
- [x] The installed-apps list is read on the main thread every time you return home. That stalls on phones with many apps.
- [x] Database schema export is off, and the version 1 to 2 migration has no test.
- [x] 18 on-screen strings are written into Kotlin; `strings.xml` holds only the app name. "Snooze 5 min (1 left)" needs a plural.
- [x] Nothing is saved for process restarts. An expanded ledger or half-typed quick-add is lost if Android reclaims the app.
- [x] No lint in CI, code shrinking is off, and there's no version catalog.
- [x] The notification permission sends you to Settings. Android 13 and newer have a one-tap system prompt.

Second pass, after v0.1:

- [x] The board's ViewModel had grown to 518 lines covering seven features. It's split into parts.
- [x] The open screen and a recopy walk were lost if Android reclaimed the app. Both are now saved.
- [x] Countdowns across a daylight-saving change were an hour off. They now count real time, with tests for both changes.
- [x] The emulator test looked once for each thing, so slow screens failed it. Checks now wait up to a few seconds.
- [x] No tests for the ViewModel or the scheduler. The scheduler's decisions now live in core (`Pass`) with their own tests; the ViewModel's parts have tests for state kept across a restart; series edits are tested against a real database.
- [x] The scheduler decided and acted in one long function. It now carries out a plan from `Pass`.
- [ ] Libraries are about two years old (Compose, Room, Kotlin).
- [x] A lapse showed only when the board came back to the front. The board now checks every 30 seconds.
- [x] The reason line said "Skipped 3 times, moved up", a counter. It now says "Moved up after a few skips".
- [x] Pick-one rows ran off the screen with many options or large text. They now wrap.

### UI and UX

- **Readability first.** Atkinson Hyperlegible, sentence case, left-aligned, no italics, no all caps. Body text 18 sp or larger. Layouts hold at 200% system font size.
- **One primary action per screen.** It's the filled accent button; the rest are outlined.
- **Touch targets.** At least 48 dp; primary actions 64 dp.
- **Forgiving actions.** Done, Skip, and Tomorrow show a short Undo instead of asking first.
- **Every state designed.** Loading, empty (Free time), error, and permission missing.
- **TalkBack works.** Every control has a label, and custom drawings like the ledger line describe themselves.
- **Neutral copy.** Plain words, no blame, no counters. Times read "3:00 PM" and durations "1h 40m", everywhere.
- **Every gesture has a visible twin.** Swipe up has the Apps button; hold-to-focus has the Focus button.
- **Short motion.** Animations stay brief and follow the system's reduced-animation setting.

Gaps today:

- [x] The "NOW" labels on the board and alarm screen are all caps with wide letter spacing, the hardest form for dyslexic readers. They become "Now".
- [x] Button labels are set never to wrap, so they'll clip at large font sizes.
- [x] No Undo after Done, Skip, or Tomorrow. A mis-tap is permanent.
- [x] The board shows "Free time" for a moment while the database loads, before the real Now card appears.
- [x] No accessibility labels anywhere. TalkBack can't read the ledger line.
- [x] A swipe up from the very bottom edge can trigger the system's home gesture instead of the app drawer. Android owns that edge, so the board's swipe area sits above it, and the Apps button stays visible as the sure way in.

### Design system and templates

One source for every look, phrase, and format, so new screens are assembled from shared parts instead of styled by hand.

- **Tokens.** Colors (already `Ink`), a named type scale (title, body, label), spacing steps (4, 8, 12, 16, 24, 32 dp), and corner shapes.
- **Components.** Shared pieces for the primary and secondary buttons, card, setup line, setting switch, list row, and flash message. Screens use these instead of raw styling.
- **Screen template.** One scaffold handles system bars, keyboard space, margins, and background. The board, drawer, alarm screen, recap, and recopy all use it.
- **Formatters.** One place formats clock times, countdowns, dates, and wall labels for the board, notifications, and alarm screen alike.
- **Copy.** All wording in string resources, grouped by screen. The rotating rung phrasings in v0.2 go in the same file.
- **Layout rhythm.** Panels group by distance, so the eye sees the board's three jobs at a glance: what's Now, the day, and getting to things (quick-add, dock, apps).
  - Every gap and size is a multiple of 4 dp; side margins are 16 dp.
  - Between zones: `Space.zone`, 24 dp. Inside a zone: `Space.inside`, 8 dp. Form fields: 16 dp apart.
  - Touch targets are at least 48 dp, with at least 8 dp between neighbours. The dock's trio tiles touch, so their taps go by outline instead.
  - One text edge: panel text sits 24 dp in from the panel's side, in the Now card, the day strip, and the open day sheet alike.
  - Panels look like panels: the folded day panel is a framed strip (`Strip`), matching the card and the sheet it opens into.
  - The design guard fails on a hand-typed spacing value outside `Design.kt`.
  - Sources: SAP Fiori for Android, following Material 3 ([layout basics](https://www.sap.com/design-system/fiori-design-android/v26-4/foundations/adaptive-layout/layout-basics)); Material's older metrics and keylines ([m1.material.io](https://m1.material.io/layout/metrics-keylines.html)).
- **Notification templates.** One builder per rung, sharing layout and actions. The notifier already works this way.

Gaps today:

- [x] 34 hand-set font sizes across 3 files, in 9 different values from 14 to 48 sp, with no named scale.
- [x] Clock formatting is defined 4 times: board, notifications, alarm screen, and engine. The engine writes "3:30" where the app writes "3:30 PM".
- [x] Wall labels (Soft, Firm, Hard) are defined twice.
- [x] Button and switch styles live inside single screens. The alarm screen copies the board's button style by hand.
- [x] Each screen sets its own padding and background.

## Build plan

Version 0.1 is the smallest thing you could live on for two weeks and judge.

**v0.1 — Live on it**

Built and passing the emulator check:

- Launcher with the hybrid Board: Now card, Next peek, ledger line, app list
- Anchors and floats, with daily, weekday, weekly, monthly, and yearly recurrence, including every-other options
- Placement logic with reason lines, Auto priority, and skip reslot
- Duration learning, from a 15-minute default for Quick tasks and 30 for Focus (Quick was 30 until the first phone test, when a 25-minute gap before an anchor showed "Nothing fits")
- Escalation rungs 0 to 3, with the lock-screen card on the same scheduler
- Board after unlock
- Quick-add with the basic parser

Standards pass, done, so the remaining screens are built from shared parts:

- App container: one database, repository, scheduler, and settings
- Design tokens, shared components, screen template, and one set of formatters
- All wording moved to string resources
- Readability fixes: sentence-case labels, wrapping buttons, 200% font check
- Undo after Done, Skip, and Tomorrow; a loading state for the board
- TalkBack labels, including the ledger line
- App list read in the background; state kept across process restarts
- CI: lint, code shrinking, version catalog, database schema export with a migration test, and an emulator run at 200% font size
- Guards: design check, accessibility sweep, StrictMode, CLAUDE.md
- Themes: Sci-fi and Classic, with a contrast test

Then, built:

- Initiative settings screen, with quiet hours
- Focus mode
- Lapse recap with miss policies ("Previously on")
- Weekly recopy
- Nightly JSON export to a folder you pick (the .ics half comes in v0.2)
- Targets Android 16 (API 36), tested on an Android 16 emulator

After v0.1, built:

- The edit form: tap the Now card's title, or an item in the day queue, to change every field or remove it (with Undo).
- Repeating items in the form: make a one-off repeat, change how a series repeats, or stop it. For one time of a series, an edit covers this time or this and later.
- Restore from a backup file in settings. What's there is saved first as initiative-before-restore.json.
- Lapse and recopy timings in settings: recap after 12 hours, 1 day, or 2 days; recopy every 1 or 2 weeks.
- A calendar file of anchors, initiative-anchors.ics, written beside the nightly backup.
- The recap shows full dates ("Tue, Oct 20").
- A lapse or a due recopy shows while the board stays open, not only when it comes back to the front.

**Parked: icon drawer.** The app drawer as rows of icons, five to a row, seated by use: the most-used five in the top row, the next five below. Seats never move by themselves; new apps take the next empty seat, and only a drag or a "Sort by use" tap reorders. Counts come from launches through Initiative, so no Usage access permission. Parked because the dock and search cover the need for now. (Adaptive menus that reorder by use tend to slow people down; stable seats avoid that. Findlater and McGrenere, CHI 2004, cited from memory.)

**Parked: tasks by text.** Texts from one chosen number that start with a phrase such as "task:" become tasks: the words up to the first period are the title, the rest the description, and any time in the message is read as in quick-add (a time that lands on an anchor moves to just after it; anchors win). Each one shows "Added from a text" with Undo, since a sender's number can be faked. To weigh when it's built: reading texts is a restricted permission (the same "Allow restricted settings" unlock), and Google Messages' RCS chat messages never reach other apps the way SMS does, so RCS would need the Messages notification read instead (another restricted permission, matched by contact name). One "Partner" number could serve this and the partner alert rung.

**v0.2 — Close the loop**

- The Log: planning beyond today (below)
- Subtasks: one level of steps inside an item (below)
- Partner SMS rung
- .ics import
- Rotating alert wording

**The Log.** The board stays the default: the current task, with today's live timeline beneath. Planning is one tap away and closes back to the board.

- *Way in.* Tap the Plan ahead panel at the top of the board. **Back to board**, Back, or the Now bar along the Log's bottom returns.
- *Zoom.* Day, Week, Month, Year. It opens on Day (today), and pages with **Earlier** and **Later** at each zoom, so next year's appointment is a few flips away.
- *Lists, not folders.* Day and Week are flat lists grouped by date; Week's days fold, open by default when they hold something. Month is a grid, Monday first, with a small mark on days that hold anchors and no counts; tap a day to list it below. Year lists the twelve months with their fixed times written out ("Dentist 21"), in readable text rather than tiny grids; tap one to open it.
- *Search.* One box across titles, notes, and steps, including done and removed items, so the archive is a filter, not a separate screen. Coming items first, soonest first; then past ones, latest first.
- *Series.* A repeating item shows as one row on each date it falls, marked "repeats" beyond the three weeks already made. A series isn't a parent of steps; it's one rule on many dates.
- *Now stays in view.* A bar along the bottom shows Now and its next step.
- *Tap to edit.* Any item opens the edit form. **Put back** replaces Remove for a done or removed item and returns it to the board.

**Subtasks.** One level of steps inside an item: "laundry" holds collect, wash and dry, fold, put away. They show as a checklist on the Now card, tick off there, and are typed in the edit form (Add a step puts the cursor in the new one). An anchor can hold steps too: "school pickup" from 2:30 to 4:00 holds "pick up" and "playtime", like a quest holding its objectives. A repeating item keeps its steps on the series, and each time starts with all of them open. The board still shows one item. The Log lists steps under their item, and search reaches them. Breaking work into steps is a core skill in adult ADHD treatment, so this ranks with the Log.

**Research basis.** The Log and subtasks follow the two adult ADHD treatment programs with trial evidence (Safren's CBT, JAMA 2010; Solanto's meta-cognitive therapy, Am J Psychiatry 2010): one calendar and one task list, a daily and weekly review, tasks broken into steps, and time estimates checked against actual time. They also follow Barkley's principle of putting time and information outside the head, at the point of use. Working memory is reliably weaker in ADHD (Kasper, Alderson and Hudec, 2012), so the Log keeps lists flat and taps few. Citations are from memory; check them before quoting.

**v0.3 — Polish**

- Font choice (themes shipped in v0.1)
- Share target
- Parser handles more phrasing
- Optional per-item sounds

**v0.4 — Desktop and voice**

- Desktop counterpart app with the full board and editing: planning happens at the desk, and the timeline is pushed to the phone, which stays the interface
- Its task log takes the Fallhammer quest log's interface as the primary design reference (repo `FallhammerLord/fallhammer_quest_log`): the list and folder layout, parent and child entries, search, the read and edit views, status buttons with icon and label, and its themes. The code differs (a Foundry VTT module versus a desktop app); the look and flow carry over.
- Phone and desktop sync over home Wi-Fi, no cloud
- Voice capture with offline speech recognition

After two weeks on v0.1, we review what got ignored, what annoyed you, and what saved you. That review sets v0.2.

## Open questions

- [x] **Launcher base.** Written from scratch, so the codebase stays small and fully understood.
- [x] **Build setup.** GitHub Actions builds the APK and tests it on an Android 16 emulator; nothing to install.
- [ ] **Partner rung.** Does your partner want Hard-wall texts, and for which anchors?
- [ ] **Kids' lane.** Should the daughters' events get their own color or marker on the ledger line?
- [ ] **Lapse threshold.** Is 24 hours right, or does a shorter window catch you sooner?
- [ ] **Escalation timing.** Are the rung times and Soft's ceiling at rung 2 right?
- [ ] **Sync conflicts.** When phone and desktop both edit one item between syncs, does the phone always win, or the latest edit?
- [ ] **Name.** Initiative is a working title.
