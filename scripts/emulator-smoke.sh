#!/usr/bin/env bash
# Smoke test on an emulator: install, make it the home app, and check that the
# phone stays usable around it. Run by .github/workflows/build.yml.
#   usage: emulator-smoke.sh <apk> <out-dir>
set -uo pipefail

APK=$1
OUT=$2
PKG=net.fallhammer.initiative
mkdir -p "$OUT"
STEP=0
# Failures are collected, not fatal: the run goes on and lists them all at the end.
# One failure can leave the phone in a state later steps don't expect, so the first
# is the one to trust; past MAX_FAILS the rest is likely knock-on, and the run stops.
FAILS=()
FAILED_STEP=0
MAX_FAILS=10
LAST_PASS="the start"

log() { echo "$*" | tee -a "$OUT/steps.log"; }

shot() {
  STEP=$((STEP + 1))
  adb exec-out screencap -p > "$OUT/$(printf %02d $STEP)-$1.png"
  a11y_sweep "$1"
}

# Every tappable element of ours must carry something TalkBack can read: its
# own text, a description, a hint, or text inside it. Runs at every screenshot.
a11y_sweep() {
  ui > "$OUT/a11y.xml"
  python3 - "$OUT/a11y.xml" "$PKG" <<'PY' || fail "unlabelled control on '$1' (see above)"
import sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    sys.exit(0)  # no dump (lock screen, transition): nothing to check
pkg = sys.argv[2]
import re
def spoken(n):
    return any((m.get(a) or "").strip() for m in n.iter("node") for a in ("text", "content-desc", "hint"))
def box(n):
    return tuple(map(int, re.findall(r"\d+", n.get("bounds") or "[0,0][0,0]")))
# A control running past the top or bottom edge of a scrolling list has its
# label scrolled out of view; TalkBack scrolls it in, so it isn't unlabelled.
# A control squeezed flat inside the screen still fails.
scrollers = [box(n) for n in root.iter("node") if n.get("scrollable") == "true"]
def scrolled_off(n):
    x1, y1, x2, y2 = box(n)
    return any(sx1 <= x1 and x2 <= sx2 and (y2 > sy2 or y1 < sy1) for sx1, sy1, sx2, sy2 in scrollers)
bad = [n for n in root.iter("node")
       if n.get("package") == pkg and (n.get("clickable") == "true" or n.get("long-clickable") == "true")
       and not spoken(n) and not scrolled_off(n)]
for n in bad:
    print("    unlabelled", n.get("class"), n.get("bounds"))
sys.exit(1 if bad else 0)
PY
}

fail() {
  local n=$(( ${#FAILS[@]} + 1 )) line=${BASH_LINENO[0]}
  FAILS+=("line $line, after '$LAST_PASS': $*")
  FAILED_STEP=1
  log "FAIL $n (line $line): $*"
  log "--- app log ---"
  adb logcat -d -s Initiative:V | tail -20 | tee -a "$OUT/steps.log"
  adb shell dumpsys notification --noredact > "$OUT/notifications-$n.txt"
  adb exec-out screencap -p > "$OUT/zz-failure-$n.png"
  ui > "$OUT/zz-failure-$n-ui.xml" || true
  adb shell dumpsys input_method > "$OUT/ime-at-failure-$n.txt" || true
  adb logcat -d > "$OUT/logcat.txt"
  [ "$n" -ge "$MAX_FAILS" ] && finish
  return 1
}

# A check whose steps failed says so, rather than "ok".
pass() {
  if [ "$FAILED_STEP" = 1 ]; then
    log "--   not passed: $*"
    FAILED_STEP=0
  else
    log "ok   $*"
    LAST_PASS=$*
  fi
}

# Lists every failure, and exits 1 if there were any.
finish() {
  DONE=1
  adb logcat -d > "$OUT/logcat.txt"
  if [ ${#FAILS[@]} -eq 0 ]; then
    log "all checks passed"
    exit 0
  fi
  log ""
  log "=== ${#FAILS[@]} failed (the first is the one to trust; later ones may follow from it) ==="
  [ ${#FAILS[@]} -ge "$MAX_FAILS" ] && log "    stopped after $MAX_FAILS: the rest is likely knock-on"
  local i
  for i in "${!FAILS[@]}"; do log "  $((i + 1)). ${FAILS[$i]}"; done
  exit 1
}

# If the script itself stops early (an unset variable, say), the list still prints.
DONE=0
on_exit() {
  local rc=$?
  [ "$DONE" = 1 ] && return
  FAILS+=("the script stopped early, after '$LAST_PASS' (exit $rc)")
  finish
}
trap on_exit EXIT

ui() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb exec-out cat /sdcard/ui.xml
}

# Prints "x y" for the first node matching $1. Modes: contains (default), exact, class.
# Class mode takes the last match: the top-most layer, such as the open drawer.
# "lastexact" is exact, but takes the last match, such as an app in a list
# below a button of the same name.
find_node() {
  ui > "$OUT/ui.xml"
  python3 - "$OUT/ui.xml" "$1" "${2:-contains}" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, q, mode = sys.argv[1:4]
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(1)
nodes = list(root.iter("node"))
if mode in ("class", "lastexact"):
    nodes.reverse()
for n in nodes:
    # Text boxes echo what was typed; never treat them as labels.
    # "field" mode finds a text box by what it holds, ignoring case: the keyboard
    # capitalizes some words by itself, such as day names ("fri" becomes "Fri").
    is_box = n.get("class") == "android.widget.EditText"
    if mode == "field":
        if is_box and (n.get("text") or "").lower() == q.lower():
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
            print((x1 + x2) // 2, (y1 + y2) // 2)
            sys.exit(0)
        continue
    if mode != "class" and is_box:
        continue
    vals = [n.get("class") or ""] if mode == "class" else [n.get("text") or "", n.get("content-desc") or ""]
    for v in vals:
        # "iexact" ignores case too: the keyboard may capitalize a line's first letter.
        hit = (v == q) if mode in ("exact", "class", "lastexact") else (v.lower() == q.lower()) if mode == "iexact" else (q in v)
        if hit:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
            print((x1 + x2) // 2, (y1 + y2) // 2)
            sys.exit(0)
sys.exit(1)
PY
}

see() { find_node "$1" "${2:-contains}" > /dev/null; }

# Hold at ($1, $2), drag to y $3, and let go: a long-press drag, as a finger does it.
drag() {
  adb shell input motionevent DOWN "$1" "$2"
  sleep 1
  local step=$(( ($3 - $2) / 6 ))
  for i in 1 2 3 4 5 6; do
    adb shell input motionevent MOVE "$1" $(( $2 + step * i ))
    sleep 0.1
  done
  adb shell input motionevent UP "$1" "$3"
  sleep 1.5
}

# Bounds of the first node whose text or description is exactly $1: "x1 y1 x2 y2".
node_bounds() {
  ui > "$OUT/ui.xml"
  python3 - "$OUT/ui.xml" "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for n in root.iter("node"):
    if sys.argv[2] in ((n.get("text") or ""), (n.get("content-desc") or "")):
        print(*re.findall(r"\d+", n.get("bounds")))
        sys.exit(0)
sys.exit(1)
PY
}

# Screens settle at different speeds, so checks wait rather than look once.
# wait_for: up to $3 seconds (default 6) for text to appear.
wait_for() {
  local end=$((SECONDS + ${3:-6}))
  until see "$1" "${2:-contains}"; do
    [ $SECONDS -ge $end ] && return 1
    sleep 0.5
  done
}

# scroll_to: swipe up a screen at a time, up to 6 times, until $1 shows.
scroll_to() {
  local i
  for i in 1 2 3 4 5 6; do
    see "$1" "${2:-contains}" && return 0
    adb shell input swipe 540 1700 540 900 300
    sleep 1
  done
  see "$1" "${2:-contains}"
}

# wait_gone: up to $3 seconds (default 6) for text to go away.
wait_gone() {
  local end=$((SECONDS + ${3:-6}))
  while see "$1" "${2:-contains}"; do
    [ $SECONDS -ge $end ] && return 1
    sleep 0.5
  done
}

# A step inside a check: logged, but it leaves the check's own pass or "not passed" alone.
expect_text() { wait_for "$1" && log "ok   shows '$1'" || fail "expected '$1' on screen"; }

# The Now card shows On it and Later until the task is under way; then Done,
# Later, Tomorrow, and Focus. start_now taps On it if it's showing.
start_now() {
  if wait_for "On it" exact 3; then tap "On it" exact; fi
  wait_for "Done" exact || fail "the Now card should offer Done once on it"
}

tap() {
  local xy end=$((SECONDS + 6))
  until xy=$(find_node "$1" "${2:-contains}"); do
    # Recorded once: the run goes on past a failure, so the loop must stop here.
    [ $SECONDS -ge $end ] && { fail "can't find '$1' to tap"; return 1; }
    sleep 0.5
  done
  adb shell input tap $xy
  sleep 1.5
}

resumed() { adb shell dumpsys activity activities | grep -E "topResumedActivity|ResumedActivity:" | head -1; }

expect_front() {
  local want=$1 why=$2
  for _ in 1 2 3 4 5; do
    resumed | grep -Eq "$want" && { pass "$why"; return; }
    sleep 1
  done
  fail "$why (front: $(resumed))"
}

home() { adb shell input keyevent KEYCODE_HOME; sleep 2; }

quick_add() {
  local xy
  xy=$(find_node android.widget.EditText class) || fail "no quick-add field"
  adb shell input tap $xy
  sleep 1
  # The emulator now and then drops a keystroke, so check the field before
  # saving and retype if it doesn't match.
  for _ in 1 2 3; do
    adb shell input text "${1// /%s}"
    wait_for "$1" field 4 && break
    log "note: quick-add holds '$(field_text)', not '$1'; retyping"
    adb shell input keyevent KEYCODE_MOVE_END
    adb shell input keyevent $(printf 'KEYCODE_DEL %.0s' $(seq 40))
  done
  wait_for "$1" field 2 || fail "couldn't type '$1' into quick-add (field holds '$(field_text)')"
  adb shell input keyevent KEYCODE_ENTER
  sleep 2
}

# What the quick-add field holds right now.
# The last field by default (quick-add sits at the bottom); "first" for a form's title.
field_text() {
  ui > "$OUT/field.xml"
  python3 - "$OUT/field.xml" "${1:-last}" <<'PY'
import sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    sys.exit(0)
fields = [n for n in root.iter("node") if n.get("class") == "android.widget.EditText"]
pick = fields[0] if sys.argv[2] == "first" else fields[-1] if fields else None
print(pick.get("text") or "" if pick is not None else "")
PY
}

# The keyboard is up. (Not "mIsInputViewShown": the keyboard app keeps that
# true even while hidden.)
ime_shown() {
  adb shell dumpsys input_method | grep -qE "mInputShown=true|mImeWindowVis=[1-9]"
}

hide_keyboard() {
  if ime_shown; then
    adb shell input keyevent KEYCODE_BACK
    sleep 1
  fi
}

# Swipe up from the board's bottom band (the Apps button), above Android's own
# gesture strip; the drawer must open. If a keyboard the check missed swallowed
# the swipe, Back closes it (on the board Back does nothing else).
open_drawer() {
  hide_keyboard
  adb shell input swipe 540 2200 540 1000 250
  sleep 2
  if ! wait_for "Change home app" exact 3; then
    adb shell dumpsys input_method > "$OUT/ime.txt"
    log "note: swipe didn't open the drawer; pressing Back once and retrying"
    adb shell input keyevent KEYCODE_BACK
    sleep 1
    adb shell input swipe 540 2200 540 1000 250
    sleep 2
    wait_for "Change home app" exact || fail "swipe up didn't open the app drawer"
  fi
}

locked() { adb shell dumpsys window | grep -qiE "keyguardShowing=true|mShowingLockscreen=true"; }

lock_screen() {
  adb shell input keyevent KEYCODE_SLEEP
  sleep 2
  adb shell input keyevent KEYCODE_WAKEUP
  sleep 2
  adb shell dumpsys window | grep -iE "keyguard.*showing|showinglockscreen" | head -3 >> "$OUT/steps.log"
  locked || fail "lock screen didn't appear after sleep"
  pass "system lock screen appears"
}

# Bring up the PIN pad and enter the PIN, retrying while the bouncer settles.
unlock_with_pin() {
  for _ in 1 2 3; do
    adb shell wm dismiss-keyguard
    sleep 2
    adb shell input text 1234
    sleep 1
    adb shell input keyevent KEYCODE_ENTER
    sleep 3
    locked || { pass "PIN unlocks the phone"; return; }
  done
  fail "PIN didn't unlock the phone"
}

# Active notifications only. dumpsys also lists recently removed ones further down.
notifications() { adb shell dumpsys notification --noredact | sed -n '/Notification List:/,/Archive/p'; }

expect_notification() {
  for _ in 1 2 3 4 5; do
    adb shell dumpsys notification --noredact | grep -q "$1" && { pass "$2"; return; }
    sleep 1
  done
  fail "$2 (no notification with '$1')"
}

# Jump the emulator clock to HHMM today. The app re-arms on clock changes.
set_clock() {
  adb shell date "$(date -u +%m%d)$1$(date -u +%Y).00" > /dev/null
  sleep 4
}

# From the board: drawer, then Initiative's Settings.
open_settings() {
  open_drawer
  tap Settings exact
  wait_for "Lock-screen card" exact || fail "Settings didn't open"
}

# pick_theme: open the Theme dropdown in settings and choose $1.
pick_theme() {
  scroll_to "Theme, " || fail "Settings should have the Theme dropdown"
  tap "Theme, "
  wait_for "$1" exact || fail "the Theme list should offer $1"
  tap "$1" exact
}

crash_check() {
  if adb logcat -d -b crash | grep -q "$PKG"; then
    local n=$(( ${#FAILS[@]} + 1 ))
    adb logcat -d -b crash > "$OUT/crash-$n.txt"
    # Cleared once kept, so the next check only sees a new crash.
    adb logcat -b crash -c
    fail "app crashed (see crash-$n.txt)"
  fi
  pass "no crashes so far"
  strict_check
}

# StrictMode logs main-thread disk and network work. Writes, network, and leaked
# database or file handles fail the run; disk reads are counted for review, since
# Android itself reads fonts and settings on the main thread at startup.
strict_check() {
  local pid
  pid=$(adb shell pidof $PKG | tr -d '\r')
  [ -n "$pid" ] || return 0
  adb logcat -d --pid="$pid" -s StrictMode:D > "$OUT/strictmode.txt"
  # A violation counts when its stack passes through app or library code. Ones
  # raised entirely inside Android (such as its own window-surface cleanup) don't.
  # Each violation is reported once: ones already in strictmode-seen.txt are skipped.
  python3 - "$OUT/strictmode.txt" "$OUT/strictmode-seen.txt" <<'PY' | tee -a "$OUT/steps.log" || fail "StrictMode: main-thread write, network, or leaked handle in app code (see strictmode.txt)"
import re, sys
SERIOUS = ("DiskWriteViolation", "NetworkViolation", "LeakedClosableViolation", "SqliteObjectLeakedViolation")
PLATFORM = ("android.", "java.", "javax.", "libcore.", "com.android.", "dalvik.", "sun.", "jdk.internal.")
blocks, cur = [], None
for line in open(sys.argv[1], errors="replace"):
    if "StrictMode policy violation" in line:
        cur = {"kind": next((k for k in SERIOUS if k in line), None), "read": "DiskReadViolation" in line, "frames": []}
        blocks.append(cur)
    elif cur is not None:
        m = re.search(r"\bat ([\w$.<>]+)\(", line)
        if m:
            cur["frames"].append(m.group(1))
ours = [b for b in blocks if b["kind"] and any(not f.startswith(PLATFORM) for f in b["frames"])]
import os
seen_path = sys.argv[2]
seen = set(open(seen_path).read().split("\n")) if os.path.exists(seen_path) else set()
def sig(b):
    return b["kind"] + " via " + next(f for f in b["frames"] if not f.startswith(PLATFORM))
ours = [b for b in ours if sig(b) not in seen]
with open(seen_path, "a") as out:
    for b in ours:
        out.write(sig(b) + "\n")
for b in ours:
    print("    ", sig(b))
reads = sum(b["read"] for b in blocks)
print(f"     StrictMode: {len(ours)} serious in app code, {reads} main-thread reads noted, "
      f"{sum(1 for b in blocks if b['kind']) - len(ours)} inside Android only")
sys.exit(1 if ours else 0)
PY
  pass "StrictMode clean"
}

# --- Setup -------------------------------------------------------------------

adb wait-for-device
# Fix the clock at noon so the day window and placement are predictable.
adb root > /dev/null 2>&1 && adb wait-for-device && sleep 2
# adb restarts as root, so retry until the clock really reads noon. Android's
# time service can still put back the real time a moment later (run 73), so it's
# switched off too, and noon must hold for a few seconds before going on.
for _ in 1 2 3 4 5 6 7 8 9 10; do
  adb wait-for-device
  adb shell settings put global auto_time 0 > /dev/null 2>&1
  adb shell cmd time_detector set_auto_detection_enabled false > /dev/null 2>&1
  adb shell date "$(date -u +%m%d)1200$(date -u +%Y).00" > /dev/null 2>&1
  sleep 5
  [ "$(adb shell date +%H | tr -d '\r')" = 12 ] && break
done
[ "$(adb shell date +%H | tr -d '\r')" = 12 ] || fail "couldn't set the emulator clock to noon"
log "emulator time: $(adb shell date)"
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb logcat -c

adb install -r "$APK" > /dev/null || fail "install failed"
pass "installed"

adb shell cmd role add-role-holder android.app.role.HOME $PKG > /dev/null 2>&1 \
  || adb shell cmd package set-home-activity $PKG/.MainActivity > /dev/null
adb shell cmd role get-role-holders android.app.role.HOME | grep -q $PKG \
  || fail "couldn't make it the home app"
pass "set as home app"

# --- The board ---------------------------------------------------------------

home
expect_front $PKG "Home opens the board"
# A freshly booted emulator can take a while before the screen can be read.
wait_for "Free time" contains 20 || fail "expected 'Free time' on screen"
pass "shows 'Free time'"
expect_text "Setup:"
expect_text "Nothing else today"
shot board-first-run
# The day panel: folded to the Next line; tap opens it, Back folds it.
tap "Nothing else today" exact
wait_for "Hide today" exact || fail "the day panel should open"
shot day-panel
adb shell input keyevent KEYCODE_BACK
wait_gone "Hide today" exact || fail "Back should fold the day panel"
pass "the day panel opens and folds"
# A swipe up mid-board is not the drawer.
adb shell input swipe 540 1200 540 300 250
sleep 2
wait_gone "Change home app" exact 2 || fail "a swipe up mid-board opened the drawer"
pass "only the bottom band opens the drawer"

# Grant what a person grants through the setup line.
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell appops set $PKG USE_FULL_SCREEN_INTENT allow
adb shell dumpsys deviceidle whitelist +$PKG > /dev/null
home
wait_gone "Setup:" || fail "setup line still showing after every grant"
pass "setup line clears once everything is granted"

# Taxes first: a new Now holds its place 5 minutes, so a task added after waits.
quick_add "taxes by fri high"
quick_add "call landlord"
hide_keyboard
# Names used before show while typing: "cal" offers "call landlord" above the field.
xy=$(find_node android.widget.EditText class) || fail "no quick-add field"
adb shell input tap $xy
sleep 1
adb shell input text cal
wait_for "call landlord" exact 6 || fail "typing 'cal' should suggest 'call landlord'"
shot name-suggestions
adb shell input keyevent KEYCODE_MOVE_END
adb shell input keyevent KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL
hide_keyboard
[ -z "$(field_text)" ] || fail "the quick-add field should be empty again (holds '$(field_text)')"
pass "names used before show while typing"
expect_text "taxes"
expect_text "High priority"
expect_text "Next: call landlord"
shot two-floats
# Closing the keyboard lets go of the field: it doesn't stay highlighted.
# Under CI load Android can drop the app's keyboard tracking (ImeTracker
# "onCancelled"), so the window is never told the keyboard opened and can't see
# it close. That's the system's miss, not the app's: log it and try once more.
# A second miss fails.
quick_add_releases() {
  local xy
  xy=$(find_node android.widget.EditText class) || fail "no quick-add field"
  adb shell input tap $xy
  for _ in 1 2 3 4; do ime_shown && break; sleep 1; done
  ime_shown || fail "tapping quick-add should open the keyboard"
  adb shell input keyevent KEYCODE_BACK
  # The field lets go once the keyboard's closing animation ends; allow a few seconds.
  for _ in 1 2 3 4 5 6 7 8; do
    sleep 0.5
    ui > "$OUT/focus.xml"
    python3 - "$OUT/focus.xml" <<'PY' && return 0
import sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    sys.exit(1)
sys.exit(1 if any(n.get("class") == "android.widget.EditText" and n.get("focused") == "true" for n in root.iter("node")) else 0)
PY
  done
  return 1
}
if ! quick_add_releases; then
  log "     quick-add still focused; app log and keyboard tracking:"
  adb logcat -d | grep -E "Initiative: quick-add|ImeTracker: net.fallhammer" | tail -12 | tee -a "$OUT/steps.log"
  home
  sleep 2
  quick_add_releases || fail "quick-add stayed focused after the keyboard closed"
fi
pass "closing the keyboard releases the quick-add field"
# Swipe up on the Next line opens the day panel; a tap outside it folds it.
xy=$(find_node "Next: call landlord") || fail "no Next line"
x=${xy% *}; y=${xy#* }
adb shell input swipe $x $y $x $((y - 400)) 250
wait_for "Hide today" exact || fail "the day panel should open"
expect_text "taxes"
adb shell input tap 540 300
wait_gone "Hide today" exact || fail "a tap outside should fold the day panel"
pass "swipe up opens the day panel; a tap outside folds it"
expect_notification "Now: taxes" "lock-screen card shows the Now card"
# On it: the task is in hand, not skipped; the card says so, with when.
expect_text "Later"
tap "On it" exact
wait_for "On it since" || fail "On it should show on the Now card"
wait_for "Focus" exact || fail "On it should turn the row to Done, Later, Tomorrow, Focus"
expect_text "Tomorrow"
shot on-it
pass "On it marks the task in hand"

tap Later exact
expect_text "Back at"
expect_text "call landlord"
shot after-skip

start_now
tap Done exact
expect_text "Undo"
tap Undo exact
expect_text "Put back"
expect_text "call landlord"
pass "Undo puts the item back"
start_now
tap Done exact
expect_text "Free time"
shot after-done

quick_add "pickup 2pm hard"
hide_keyboard
# Walls show on the Plan ahead dial, which TalkBack reads out.
expect_text "pickup at 2:00 PM"
shot anchor-in-ledger

# A weekday two days on, whatever today is, so the task stays off today's list.
later_day=$(date -d '+2 days' +%a | tr '[:upper:]' '[:lower:]')
quick_add "dentist $later_day"
expect_text "anchor, float, or marker"
shot ask-chips
tap Float exact
hide_keyboard
wait_gone "anchor, float, or marker" || fail "ask chips didn't close"
pass "ask chips close"

adb shell input keyevent KEYCODE_BACK
sleep 1
expect_front $PKG "Back on the board doesn't leave it"
crash_check

# --- Ways out to the normal phone ---------------------------------------------

open_drawer
expect_text "Change home app"
shot app-drawer
# The manual: from the apps list, beside Settings; one part opens at a time.
tap Manual exact
wait_for "How Initiative works" exact || fail "Manual should open How Initiative works"
shot manual
tap "The Now card" exact
scroll_to "Hold the card" exact || fail "The Now card should list what you do there"
shot manual-now-card
adb shell input keyevent KEYCODE_BACK
wait_gone "How Initiative works" exact || fail "Back should close the manual"
wait_for "Change home app" exact || fail "Back from the manual should return to the apps list"
pass "the manual opens from the apps list, and Back returns there"

tap "Phone settings" exact
expect_front com.android.settings "Phone settings opens Settings"
home
expect_front $PKG "Home returns to the board from Settings"
ime_shown && fail "keyboard opened by itself on returning home"
pass "keyboard stays closed on returning home"
shot back-home

open_drawer
tap "Change home app" exact
expect_front "com.android.settings|permissioncontroller" "Change home app opens the system chooser"
shot home-chooser
home

open_drawer
xy=$(find_node android.widget.EditText class) || fail "no app search field"
adb shell input tap $xy
sleep 1
adb shell input text Settings
sleep 1
hide_keyboard
shot drawer-search
# The phone's Settings app, in the list, not Initiative's Settings button above it.
tap Settings lastexact
expect_front com.android.settings "Apps in the drawer launch"
home
expect_front $PKG "Home returns to the board"
ui > "$OUT/ui.xml"
python3 - "$OUT/ui.xml" <<'PY' || fail "drawer typing leaked into the board's quick-add"
import sys, xml.etree.ElementTree as ET
bad = [n for n in ET.parse(sys.argv[1]).getroot().iter("node")
       if n.get("class") == "android.widget.EditText" and "Settings" in (n.get("text") or "")]
sys.exit(1 if bad else 0)
PY
pass "drawer input stays in the drawer"
crash_check

# --- Lock screen and board after unlock ----------------------------------------

adb shell locksettings set-pin 1234 > /dev/null || fail "couldn't set a test PIN"
adb shell locksettings verify --old 1234 > /dev/null 2>&1 && pass "test PIN set"
# Off by default; switch it on.
open_settings
xy=$(find_node "Board after unlock" exact) || fail "no board-after-unlock switch"
adb shell input tap 980 "${xy#* }"
sleep 1
home
adb shell am start -a android.settings.SETTINGS > /dev/null
sleep 2
lock_screen
shot lock-screen
unlock_with_pin
expect_front $PKG "Unlock lands on the board"
adb logcat -d -s Initiative:V | tail -4 | tee -a "$OUT/steps.log"
shot after-unlock

# Switch it off: unlock should return to the last app instead.
open_settings
xy=$(find_node "Board after unlock" exact) || fail "no board-after-unlock switch"
y=${xy#* }
adb shell input tap 980 "$y"
sleep 1
shot switch-off
home
adb shell am start -a android.settings.SETTINGS > /dev/null
sleep 2
lock_screen
unlock_with_pin
expect_front com.android.settings "With the switch off, unlock returns to the last app"
adb shell locksettings clear --old 1234 > /dev/null
home
crash_check

# --- Dock -----------------------------------------------------------------------
# Six slots along the bottom. An empty one opens the picker; a filled one opens
# its app; holding one lets it be emptied. In the Sci-fi look the slots are
# nested hexagon trios, so a tap on a tile's point, which lies inside the next
# tile's box, must still open that tile's app.

home
expect_text "Add an app"
shot dock-empty
tap "Add an app"
wait_for "Pick an app" exact || fail "an empty dock space should open the picker"
shot dock-picker
# The list runs past the screen; search for the app, as a person would.
xy=$(find_node android.widget.EditText class) || fail "no search box in the picker"
adb shell input tap $xy
sleep 1
adb shell input text Settings
tap Settings exact
wait_gone "Pick an app" exact || fail "picking an app should close the picker"
hide_keyboard
read -r x1 y1 x2 y2 < <(node_bounds "Settings") || fail "no Settings tile in the dock"
# 1.65 of the tile's 1.75 hexagon widths: inside its point, inside the next tile's box.
x=$(( x1 + (x2 - x1) * 165 / 175 )); y=$(( (y1 + y2) / 2 ))
adb shell input tap $x $y
expect_front com.android.settings "a tap on a tile's point opens that tile's app"
home
# Trays: holding a tile opens the picker, which stays open to fill the tray and
# lists what the space holds. Then the tile's tap slides its tray open (instant
# here, with animations off); Back shuts it, and an app in it opens.
read -r x1 y1 x2 y2 < <(node_bounds "Settings") || fail "no Settings tile to hold"
adb shell input swipe $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )) $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )) 900
wait_for "In this space" exact || fail "holding a tile should list what its space holds"
xy=$(find_node android.widget.EditText class) || fail "no search box in the picker"
adb shell input tap $xy
sleep 1
adb shell input text Contacts
tap Contacts exact
# The picker stays open and clears the search; the slot's list shows once the keyboard is down.
wait_for "Added Contacts" 3 || fail "a tapped app should join the tray, and say so"
hide_keyboard
wait_for "Move Contacts down" exact || fail "a tapped app should join the tray, and the picker stay open"
shot dock-tray-picker
tap Close exact
wait_gone "Pick an app" exact || fail "Close should shut the picker"
tap "Settings, opens a tray"
wait_for "Contacts" exact || fail "tapping a tile with a tray should open it"
expect_text "Settings, tray open"
shot dock-tray-open
adb shell input keyevent KEYCODE_BACK
wait_gone "Contacts" exact || fail "Back should shut the tray"
expect_front $PKG "Back shuts the tray and stays on the board"
# The tray covers its slot, so the slot's own app is in it too, and opens.
tap "Settings, opens a tray"
wait_for "Contacts" exact || fail "the tray should open again"
tap Settings exact
expect_front com.android.settings "the slot's own app opens from its open tray"
home
tap "Settings, opens a tray"
wait_for "Contacts" exact || fail "the tray should open again"
tap Contacts exact
expect_front "contacts" "an app in the tray opens"
home
wait_gone "Settings, tray open" exact 3 || fail "the tray should be shut after opening an app"
pass "a tray fills from the picker, slides open, shuts, and opens its apps"
# With Contacts in its tray, the tile's name says so.
read -r x1 y1 x2 y2 < <(node_bounds "Settings, opens a tray") || fail "Settings tile gone after Home"
adb shell input swipe $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )) $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )) 900
wait_for "Empty this space" exact || fail "holding a tile should open the picker with Empty"
tap "Empty this space" exact
wait_gone "Pick an app" exact || fail "Empty should close the picker"
wait_gone "Settings" exact 3 || fail "the emptied space still shows Settings"
pass "a held tile can be emptied"
crash_check

# --- Alarms ---------------------------------------------------------------------

quick_add "dentist 12:40pm hard"
hide_keyboard
expect_text "dentist at 12:40 PM"
set_clock 1226
expect_notification "dentist in" "rung 1: quiet notice at lead time"
set_clock 1236
expect_notification "Time to move" "rung 2: nudge 5 minutes before"
shot nudge
adb shell input keyevent KEYCODE_SLEEP
sleep 2
set_clock 1240
expect_front AlarmActivity "rung 3: full-screen alarm over the lock screen"
shot alarm-screen
expect_text "Snooze 5 min (1 left)"
tap "On it" exact
sleep 2
adb logcat -d -s Initiative:V | grep -q "on it: dentist" || fail "On it didn't register"
resumed | grep -q AlarmActivity && fail "alarm screen still up after On it"
if notifications | grep -q "dentist now"; then fail "alarm notification still up after On it"; fi
pass "On it stops the alarm"
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
sleep 2
set_clock 1311
if notifications | grep -q "dentist"; then fail "dentist notifications linger after it ended"; fi
pass "ended anchor clears"
adb logcat -d -s Initiative:V | tail -12 | tee -a "$OUT/steps.log"
crash_check

# --- Themes ---------------------------------------------------------------------

open_settings
expect_text "Theme"
expect_text "Recopy the list every"
shot settings
tap "2 weeks" exact
tap "1 week" exact
pass "recopy timing switches and back"
pick_theme Classic
shot classic-settings
adb shell input keyevent KEYCODE_BACK
sleep 1
wait_for "Change home app" exact || fail "Back from settings should return to the drawer"
pass "Back from settings returns to the drawer"
adb shell input keyevent KEYCODE_BACK
sleep 1
expect_front $PKG "Back closes the drawer"
shot classic-board
# Deco: coffee and gold, stepped corners, fans. Ornaments off leaves it plain.
open_settings
pick_theme Deco
# Texture strength shows while ornaments are on, for looks with a pattern.
wait_for "Texture strength" exact || fail "the texture slider should show for Deco"
expect_text "50%"
shot deco-settings
tap Close exact
wait_gone "Quiet hours" exact || fail "Close should close settings"
shot deco-board
open_settings
tap Ornaments exact
wait_gone "Texture strength" exact || fail "the texture slider should hide with ornaments off"
tap Close exact
wait_gone "Quiet hours" exact || fail "Close should close settings"
shot deco-plain
open_settings
tap Ornaments exact
pass "Deco and the Ornaments switch"
# Stained glass: leaded glass, arched panes, a rose on the Now card's arch.
pick_theme "Stained glass"
wait_for "Texture strength" exact || fail "the texture slider should show for Stained glass"
shot glass-settings
tap Close exact
wait_gone "Quiet hours" exact || fail "Close should close settings"
shot glass-board
pass "Stained glass look"
# Pastel: the light look, with dots and hearts, a bow on Now, and dark status bar icons.
open_settings
pick_theme Pastel
wait_for "Texture strength" exact || fail "the texture slider should show for Pastel"
shot pastel-settings
tap Close exact
wait_gone "Quiet hours" exact || fail "Close should close settings"
shot pastel-board
pass "Pastel look"
# Starlight: night sky, sparkles, a winged brooch on Now, gems for markers.
open_settings
pick_theme Starlight
wait_for "Texture strength" exact || fail "the texture slider should show for Starlight"
shot starlight-settings
tap Close exact
wait_gone "Quiet hours" exact || fail "Close should close settings"
shot starlight-board
pass "Starlight look"
# Halloween: a big web, cobwebs, a jack-o'-lantern on Now, candy corn on the dial.
open_settings
pick_theme Halloween
wait_for "Texture strength" exact || fail "the texture slider should show for Halloween"
tap Close exact
wait_gone "Quiet hours" exact || fail "Close should close settings"
shot halloween-board
pass "Halloween look"
# Christmas: knit bands, snow on each card, holly on Now, a little tree on the dial.
open_settings
pick_theme Christmas
wait_for "Texture strength" exact || fail "the texture slider should show for Christmas"
tap Close exact
wait_gone "Quiet hours" exact || fail "Close should close settings"
shot christmas-board
pass "Christmas look"
# Your colors: Sci-fi's shapes in a picked color. It starts red; eight to choose from.
open_settings
pick_theme "Your colors"
scroll_to "Your color" exact || fail "Your colors should offer its colors"
scroll_to "Blue" exact || fail "Your colors should offer blue"
shot your-colors-settings
tap "Blue" exact
tap Close exact
wait_gone "Quiet hours" exact || fail "Close should close settings"
shot your-colors-blue
pass "Your colors look"
# Holiday looks: off until turned on. On, today (Oct 29) wears Halloween over Sci-fi.
open_settings
pick_theme Sci-fi
scroll_to "Change with the holidays" exact || fail "Settings should have Holiday looks"
tap "Change with the holidays" exact
scroll_to "Oct 1 to Nov 1" exact || fail "the holidays should list once holiday looks are on"
shot holiday-settings
tap Close exact
wait_gone "Quiet hours" exact || fail "Close should close settings"
shot holiday-board
open_settings
scroll_to "Change with the holidays" exact || fail "Settings should have Holiday looks"
tap "Change with the holidays" exact
wait_gone "Oct 1 to Nov 1" exact || fail "the holidays should hide once holiday looks are off"
pass "holiday looks switch on and off"
tap Close exact
wait_gone "Quiet hours" exact || fail "Close should close settings"

# --- Pairs ----------------------------------------------------------------------
# Real pairing comes with Bluetooth; the emulator alone can add a pretend partner,
# with a peek from a minute ago, to check the Pairs screens.

adb shell am start -n $PKG/.MainActivity --ez pretend_partner true > /dev/null
sleep 2
home
# On the board: the pairmark under the Plan ahead panel, a line under the dial,
# Ping in the panel, and the edge tab that folds the pairmarks away.
wait_for "Partner, " || fail "the pretend partner's pairmark should show on the board"
expect_text "groceries"
shot pairmark-board
# Ping really searches now. The emulator has no second phone, so after a minute
# it finds no one (or says it has no Bluetooth). Permission and Bluetooth first,
# so no system dialog stops the test.
for perm in BLUETOOTH_SCAN BLUETOOTH_ADVERTISE BLUETOOTH_CONNECT; do adb shell pm grant $PKG android.permission.$perm 2>/dev/null; done
adb shell cmd bluetooth_manager enable 2>/dev/null
sleep 2
tap "Ping" exact
# Any end will do: no one found, or a Bluetooth problem. Its message shows for
# only a few seconds, so the app's log is checked instead. If Android still asks
# to turn Bluetooth on, decline it; the test goes on either way.
ping_end=""
end=$((SECONDS + 90))
while [ -z "$ping_end" ] && [ $SECONDS -lt $end ]; do
  ping_end=$(adb logcat -d -s Initiative:V | grep -o "ping: .*" | tail -1)
  if [ -z "$ping_end" ] && see "turn on Bluetooth"; then
    adb shell input keyevent KEYCODE_BACK
    ping_end="declined turning Bluetooth on"
  fi
  [ -z "$ping_end" ] && sleep 3
done
[ -n "$ping_end" ] || fail "Ping should search, then say how it went"
pass "Ping ended: $ping_end"
wait_for "Hide pairs" exact 10 || fail "the board should be back after Ping"
tap "Hide pairs" exact
wait_gone "Partner, " || fail "the edge tab should fold the pairmark away"
# The line under the dial stays, so nothing on the board moves.
expect_text "groceries"
shot pairs-hidden
expect_text "Ping"
tap "Show pairs" exact
wait_for "Partner, " || fail "the edge tab should pull the pairmark back out"
pass "a pairmark and dial line on the board, Ping, and the edge tab folds and pulls"
# A tap on the pairmark opens their panel: their day report and their month.
tap "Partner, "
wait_for "Partner's day report" || fail "a tap on the pairmark should open their panel"
expect_text "walk the dog"
shot pair-panel
# Add for them: a line, read as quick-add; it lists under Just for them, off this board.
scroll_to "Add for Partner" exact || fail "their panel should offer Add for them"
tap "Add for Partner" exact
wait_for "What for Partner?" || fail "Add for them should open a line to type"
adb shell input text "water%splants"
sleep 1
# Enter adds, as the field's Done key; the Add button may sit under the keyboard.
# The line closes with it, and the keyboard goes by itself: no Back, which would
# close the panel if it landed after the keyboard had gone.
adb shell input keyevent KEYCODE_ENTER
sleep 2
wait_for "ater plants" || fail "a task added for them should list on their panel"
expect_text "Just for Partner"
shot pair-panel-for
tap Close exact
wait_gone "Partner's day report" || fail "Close should shut their panel"
pass "the pairmark opens their panel, with their day report"
# A shared task from them, tomorrow: theirs to change, so it opens to be done, not edited.
tap "Plan ahead"
wait_for "Day report" exact || fail "Plan ahead should open the Log"
tap Later exact
wait_for "school run" || fail "tomorrow should list Partner's shared task"
tap "school run"
wait_for "From Partner" exact || fail "their shared task should open as theirs"
expect_text "Only Partner can change this"
shot shared-from-them
tap Cancel exact
wait_gone "From Partner" exact || fail "Cancel should close their task"
adb shell input keyevent KEYCODE_BACK
wait_gone "Back to board" exact || fail "Back should close the Log"
pass "a shared task from them opens as theirs, with both boxes"
# One of mine at the same time as theirs tomorrow: a double-booking marker,
# dim a day ahead. A tap shows its label; a tap on that opens the choice.
quick_add "vet tomorrow 10am"
hide_keyboard
wait_for "Double-booked: " || fail "mine against theirs should show a double-booking marker"
tap "Double-booked: "
tap "Double-booked: "
wait_for "Keep both" exact || fail "the double-booking should open its choice"
expect_text "From Partner"
shot clash-sheet
# Move mine opens my vet to change; removing it clears the double-booking.
tap "Move mine" exact
wait_for "Cancel" exact || fail "Move mine should open mine to change"
scroll_to "Remove" exact || fail "my vet's form should offer Remove"
tap Remove exact
wait_gone "Double-booked: " || fail "removing mine should clear the double-booking"
pass "a double-booking shows, opens its choice, and clears"
# Settings offers Pair with someone; pairing itself needs two phones, tested by hand.
open_settings
scroll_to "Partner, " || fail "a pretend partner should show under Pairs"
scroll_to "Pair with someone" exact || fail "Pairs should offer Pair with someone"
shot pairs-settings
tap "Partner, "
wait_for "What you share with Partner" || fail "a paired person's page should open"
shot pair-page
# Sync at home over Wi-Fi: on, the background sync starts with its quiet
# notification; off again, it stops. Bluetooth's asks for permission, so it's
# a screen test (PairScreenTest); a real sync needs two phones, tested by hand.
scroll_to "Sync at home over Wi-Fi" exact || fail "their page should offer Sync at home over Wi-Fi"
tap "Sync at home over Wi-Fi" exact
sync_running() { adb shell dumpsys activity services $PKG | grep -q "SyncService"; }
for _ in 1 2 3 4 5; do sync_running && break; sleep 1; done
sync_running && pass "Sync at home over Wi-Fi starts the background sync" || fail "Sync at home over Wi-Fi should start the background sync"
shot pair-sync
tap "Sync at home over Wi-Fi" exact
for _ in 1 2 3 4 5; do sync_running || break; sleep 1; done
sync_running && fail "turning the sync off should stop it" || pass "turning the sync off stops it"
# Sharing levels, ticks, and Unpair asking first are checked as a screen test
# (PairScreenTest); here, only that Unpair works on the phone.
scroll_to "Unpair" exact || fail "the page should end with Unpair"
shot pair-page-end
tap "Unpair" exact
scroll_to "Keep" exact || fail "Unpair should ask first"
tap "Unpair" exact
wait_gone "What you share with Partner" || fail "Unpair should close the page"
wait_gone "Partner, " || fail "Unpair should take them off the list"
pass "Unpair takes them off the list"
tap Close exact
wait_gone "Quiet hours" exact || fail "Close should close settings"
crash_check
# About: the build and the Update button, which only opens the browser.
open_settings
scroll_to "Update Initiative" exact || fail "Settings should end with the Update button"
shot settings-about
pass "About and Update"
home
wait_gone "Update Initiative" exact || fail "Home should close settings"
open_settings
pick_theme Sci-fi
# The sections are panels now; scroll the alarms into view.
scroll_to "Quiet hours" exact || fail "Settings should have Quiet hours"
tap "Quiet hours" exact
# The times open below the switch; bring them into view.
scroll_to "From 10:00 PM" || fail "the quiet-hour times should show"
expect_text "From 10:00 PM"
shot quiet-hours
tap "Quiet hours" exact
wait_gone "From 10:00 PM" || fail "quiet-hour times still showing after switching off"
pass "quiet hours switch on and off"
home
wait_gone "Quiet hours" exact || fail "Home should close settings"
pass "Home closes settings"
open_settings
tap Close exact
wait_gone "Quiet hours" exact || fail "Close should close settings"
wait_gone "Change home app" exact 2 || fail "Close should go to the board, not the drawer"
pass "Close in settings goes to the board"
shot scifi-board
pass "theme switches both ways"

# --- Large text -----------------------------------------------------------------

adb shell settings put system font_scale 2.0
home
sleep 2
shot large-font
start_now
for label in Done Later Tomorrow Focus; do
  xy=$(find_node "$label" exact) || fail "'$label' button missing at 200% font"
  y=${xy#* }
  [ "$y" -lt 2400 ] || fail "'$label' button off screen at 200% font"
done
pass "buttons fit at 200% font"
adb shell settings put system font_scale 1.0
home
crash_check

# --- Focus ----------------------------------------------------------------------
# It's past 1:00 PM: taxes is back as Now, and pickup at 2:00 PM is the next Hard wall.

home
expect_text "taxes"
start_now
tap Focus exact
expect_text "Done with it"
expect_text "until pickup at 2:00 PM"
shot focus
expect_notification "Focus: taxes" "lock-screen card shows the focus"
tap "End focus" exact
expect_text "Later"
pass "End focus returns to the board"
xy=$(find_node "taxes" exact) || fail "no Now title to hold"
adb shell input swipe $xy $xy 900
sleep 2
expect_text "Done with it"
pass "holding the card starts focus"
tap "Done with it" exact
expect_text "Undo"
expect_text "Free time"
pass "Done with it finishes the task and returns to the board"
crash_check

# --- Lapse recap and weekly recopy -----------------------------------------------
# Eight days on with no action: a lapse, so "Previously on" comes first. The
# weekly recopy is due too. The recap is checked when the board comes back to
# the front, so leave and return. (Backup needs a folder picked by hand in
# Android's file picker, which the test can't drive.)

adb shell date "$(date -u -d '+8 days' +%m%d)1200$(date -u -d '+8 days' +%Y).00" > /dev/null
sleep 4
adb shell am start -a android.settings.SETTINGS > /dev/null
sleep 2
home
expect_text "Previously on"
expect_text "What passed"
expect_text "pickup"
shot recap
tap "Back to the board" exact
wait_gone "Previously on" || fail "recap still showing after Back to the board"
pass "Back to the board closes the recap"
expect_text "Recopy the list"
shot recopy-card
tap "Recopy the list"
expect_text "Later this week"
expect_text "dentist"
shot recopy-walk
# The closing note shows for 4 seconds, so look for it right after each Keep.
finished=no
for _ in 1 2 3 4 5 6; do
  xy=$(find_node Keep exact) || break
  adb shell input tap $xy
  sleep 0.5
  if see "List recopied"; then finished=yes; break; fi
done
[ $finished = yes ] || fail "expected 'List recopied' after the last Keep"
pass "shows 'List recopied'"
wait_gone "Later this week" exact || fail "recopy walk still showing after the last item"
wait_gone "Recopy the list" || fail "recopy card still showing after the walk"
pass "the recopy walk finishes and the card clears"
shot after-recopy
crash_check

# --- Edit form ---------------------------------------------------------------------
# Tap the Now title to edit. "Type" marks the form; lower fields scroll off screen. Rename, Save, then Remove with Undo, then Back to cancel.

home
tap "dentist" exact
wait_for Type exact || fail "edit form didn't open"
expect_text "Remove"
shot edit-form
xy=$(find_node "dentist" field) || fail "no title field holding 'dentist'"
adb shell input tap $xy
# Add to the end: the field capitalizes a sentence's first letter, so retyping
# from empty would give "Dentist call".
# The space as its own key: a space leading typed text was sometimes dropped (run 78).
# The emulator still drops a key now and then (run 130: "dentistcall"), so check the
# field and, if a key went missing, take back what was typed and type it again.
for _ in 1 2 3; do
  adb shell input keyevent KEYCODE_MOVE_END
  adb shell input keyevent KEYCODE_SPACE
  adb shell input text "call"
  wait_for "dentist call" field 4 && break
  held=$(field_text first)
  log "note: title holds '$held', not 'dentist call'; retyping"
  adb shell input keyevent KEYCODE_MOVE_END
  # Back to "dentist": whatever follows it goes.
  adb shell input keyevent $(printf 'KEYCODE_DEL %.0s' $(seq $(( ${#held} > 7 ? ${#held} - 7 : 1 ))))
done
wait_for "dentist call" field 2 || fail "couldn't rename in the title field"
hide_keyboard
tap "Every day" exact
shot edit-repeat
tap Save exact
expect_text "Saved"
expect_text "dentist call"
wait_gone Type exact || fail "edit form still open after Save"
pass "Save renames the item, makes it repeat, and returns to the board"

tap "dentist call" exact
expect_text "Remove"
# It repeats now, so Remove asks which: this time, or this and later.
tap Remove exact
wait_for "Remove which?" exact || fail "Remove on a repeating item should ask which"
shot remove-which
tap "This and later" exact
expect_text "Removed, and later times"
expect_text "Free time"
tap Undo exact
expect_text "dentist call"
pass "Remove on a series takes this and later times, and Undo puts it all back"

tap "dentist call" exact
wait_for Type exact || fail "edit form didn't open"
adb shell input keyevent KEYCODE_BACK
wait_gone Type exact || fail "Back should close the edit form"
expect_text "dentist call"
pass "Back closes the edit form without saving"
crash_check

# --- Steps ------------------------------------------------------------------------
# Add two steps in the edit form; each new step gets the cursor. They show on the
# Now card and tick off there. Done with a step open asks first: Back is Not yet,
# All done finishes it; Undo puts it back for the checks after this.
# (The form may capitalize a step's first letter, so checks match the rest.)

home
tap "dentist call" exact
wait_for Type exact || fail "edit form didn't open"
tap "Add a step" exact
adb shell input text "find%sthe%snumber"
wait_for "find the number" field 3 || wait_for "Find the number" field 3 || fail "couldn't type the first step"
# The form's panels run past the keyboard: close it and scroll Add a step into view.
hide_keyboard
adb shell input swipe 540 1600 540 900 300
sleep 1
tap "Add a step" exact
adb shell input text "make%sthe%scall"
wait_for "make the call" field 3 || wait_for "Make the call" field 3 || fail "couldn't type the second step"
hide_keyboard
shot edit-steps
tap Save exact
expect_text "Saved"
expect_text "ind the number"
expect_text "ake the call"
shot now-steps
tap "ind the number"
start_now
tap Done exact
expect_text "All done"
expect_text "Not yet"
shot done-steps
adb shell input keyevent KEYCODE_BACK
wait_gone "All done" exact || fail "Back should close the Done question"
expect_text "dentist call"
pass "Done with a step open asks first, and Back leaves the item as it was"
start_now
tap Done exact
tap "All done" exact
expect_text "Undo"
wait_gone "ake the call" || fail "All done should finish the item"
tap Undo exact
expect_text "dentist call"
pass "All done finishes the item, and Undo puts it back"
crash_check

# --- Day forecast and dragging ---------------------------------------------------
# The open day panel lists the rest of today with times. Hold a task and drag it
# above another: it moves there, since it fits.

home
quick_add "read mail"
quick_add "water plants"
hide_keyboard
tap "Next:"
wait_for "Hide today" exact || fail "the day panel should open"
expect_text "water plants"
shot forecast
read -r _ wtop _ wbottom < <(node_bounds "water plants") || fail "no water plants in the forecast"
read -r rx1 rtop rx2 _ < <(node_bounds "read mail") || fail "no read mail in the forecast"
[ "$wtop" -gt "$rtop" ] || fail "expected water plants below read mail before the drag"
drag $(( (rx1 + rx2) / 2 )) $(( (wtop + wbottom) / 2 )) $(( rtop + 4 ))
# Dropped on read mail's top edge: between dentist call and read mail.
sleep 1
read -r _ dtop _ _ < <(node_bounds "dentist call")
read -r _ wtop _ _ < <(node_bounds "water plants")
read -r _ rtop _ _ < <(node_bounds "read mail")
log "     after the drag: dentist call $dtop, water plants $wtop, read mail $rtop"
[ "$dtop" -lt "$wtop" ] && [ "$wtop" -lt "$rtop" ] || fail "water plants should land between dentist call and read mail"
pass "dragging a task moves it to where it's dropped"
shot forecast-after-drag
adb shell input keyevent KEYCODE_BACK
wait_gone "Hide today" exact || fail "Back should fold the day panel"
crash_check

# --- Waits ------------------------------------------------------------------------
# A step can have a wait after it. Ticking it offers Undo; then the task steps
# aside, the next task takes Now, and a dim waitmark counts down above the card.
# When the wait is up the waitmark lights; a tap brings the task back as Now.

home
tap "dentist call" exact
wait_for Type exact || fail "edit form didn't open"
tap "Add a wait after step 1" exact
wait_for "Wait 15m after step 1, change" exact 3 || fail "a tap should set a 15-minute wait on the step"
shot edit-wait
tap Save exact
expect_text "Saved"
# The first step is still ticked from the steps check: untick it (its wait shows), then tick it again.
tap "ind the number"
expect_text "then wait 15m"
tap "ind the number"
expect_text "waits 15m"
wait_for "back in" contains 15 || fail "the task should step aside after the Undo time"
shot wait-waiting
pass "a step with a wait sets the task aside, with a waitmark counting down"
# Move the emulator clock 16 minutes on. The board's clock catches up within 30 seconds.
t=$(( $(adb shell date +%s | tr -d '\r') + 960 ))
adb shell date "$(adb shell date -d "@$t" +%m%d%H%M%Y.%S | tr -d '\r')" > /dev/null
wait_for "ready since" contains 45 || fail "the waitmark should light when the wait is up"
shot wait-ready
tap "ake the call"
wait_for "Ready since" contains 6 || fail "tapping the waitmark should make that task Now"
expect_text "dentist call"
shot wait-back
pass "the waitmark lights when the wait is up, and a tap brings the task back"
crash_check

# --- Markers ----------------------------------------------------------------------
# "all day" makes a marker: a bubble hanging off the Plan ahead panel. A tap
# slides its label out; a second tap opens its note, with Edit and Close.

home
quick_add "halloween today all day"
hide_keyboard
# The keyboard may capitalize the first letter, so match from the second.
wait_for "alloween, marker" || fail "an all-day line should make a marker bubble"
tap "alloween, marker"
wait_for "halloween" iexact || fail "a tap should slide the marker's label out"
shot marker-label
tap "alloween, marker"
wait_for "Edit" exact || fail "a second tap should open the marker's note"
shot marker-note
tap Close exact
wait_gone "Edit" exact || fail "Close should fold the marker's note"
pass "a marker shows as a bubble, its label, and its note"
crash_check

# --- The Log ----------------------------------------------------------------------
# Plan ahead opens the Log on today. It zooms to Week, Month, and Year, and the
# Year view flips to next year. Search finds done items. Back, and Back to
# board, return to the board.

home
tap "Plan ahead"
wait_for "Earlier" exact || fail "Plan ahead should open the Log"
expect_text "Today"
expect_text "dentist call"
shot log-day
# The day's report: each item with what became of it. Share opens Android's
# share menu. (Save PDF opens Android's print screen; check that by hand.)
tap "Day report" exact
wait_for "Day report ·" || fail "Day report should open the day's report"
expect_text "dentist call"
expect_text "Include notes"
shot report
tap Share exact
expect_front "intentresolver|ChooserActivity|ResolverActivity" "Share opens Android's share menu"
adb shell input keyevent KEYCODE_BACK
sleep 1
wait_for "Day report ·" || fail "Back from the share menu should return to the report"
tap Close exact
wait_for "Earlier" exact || fail "Close should return to the Log"
pass "the day's report opens, shares, and closes"
# Zooms, paging, Repeats, + Add, and search are checked in LogScreenTest; here,
# each view once for the screenshots and the TalkBack sweep.
tap Repeats exact
wait_for "Days" exact || fail "Repeats should show the series list"
shot repeats
tap Days exact
wait_for "Earlier" exact || fail "Days should bring the days back"
tap Week exact
expect_text "dentist call"
shot log-week
tap Month exact
expect_text "dentist call"
shot log-month
tap Year exact
year=$(adb shell date +%Y | tr -d '\r')
wait_for "$year" exact || fail "the Year view should show $year"
shot log-year
tap Day exact
wait_for "Day report" exact || fail "Day should bring the day back"
adb shell input keyevent KEYCODE_BACK
wait_gone "Back to board" exact || fail "Back should close the Log"
expect_text "Plan ahead"
tap "Plan ahead"
wait_for "Back to board" exact || fail "Plan ahead should open the Log again"
tap "Back to board" exact
wait_gone "Back to board" exact || fail "Back to board should close the Log"
pass "Back and Back to board leave the Log"
# At 200% font the Log keeps its way back and its Now bar in view.
adb shell settings put system font_scale 2.0
home
sleep 2
shot large-font-board
# Now's title must stay in view at 200% font, not squeezed out by the buttons.
read -r _ ty1 _ ty2 < <(node_bounds "dentist call") || fail "Now's title missing at 200% font"
[ $(( ty2 - ty1 )) -gt 40 ] || fail "Now's title squeezed out at 200% font"
pass "Now's title shows at 200% font"
tap "Plan ahead"
wait_for "Back to board" exact || fail "the Log should open at 200% font"
shot log-large-day
tap Week exact
shot log-large-week
tap Month exact
shot log-large-month
for label in "Back to board" "Now:"; do
  xy=$(find_node "$label") || fail "'$label' missing in the Log at 200% font"
  y=${xy#* }
  [ "$y" -lt 2400 ] || fail "'$label' off screen in the Log at 200% font"
done
pass "the Log works at 200% font"
adb shell input keyevent KEYCODE_BACK
adb shell settings put system font_scale 1.0
home
crash_check

finish
