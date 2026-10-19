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
def spoken(n):
    return any((m.get(a) or "").strip() for m in n.iter("node") for a in ("text", "content-desc", "hint"))
bad = [n for n in root.iter("node")
       if n.get("package") == pkg and (n.get("clickable") == "true" or n.get("long-clickable") == "true") and not spoken(n)]
for n in bad:
    print("    unlabelled", n.get("class"), n.get("bounds"))
sys.exit(1 if bad else 0)
PY
}

fail() {
  log "FAIL: $*"
  log "--- app log ---"
  adb logcat -d -s Initiative:V | tail -20 | tee -a "$OUT/steps.log"
  adb shell dumpsys notification --noredact > "$OUT/notifications.txt"
  adb exec-out screencap -p > "$OUT/zz-failure.png"
  ui > "$OUT/zz-failure-ui.xml" || true
  adb logcat -d > "$OUT/logcat.txt"
  exit 1
}

pass() { log "ok   $*"; }

ui() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb exec-out cat /sdcard/ui.xml
}

# Prints "x y" for the first node matching $1. Modes: contains (default), exact, class.
# Class mode takes the last match: the top-most layer, such as the open drawer.
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
if mode == "class":
    nodes.reverse()
for n in nodes:
    # Text boxes echo what was typed; never treat them as labels.
    if mode != "class" and n.get("class") == "android.widget.EditText":
        continue
    vals = [n.get("class") or ""] if mode == "class" else [n.get("text") or "", n.get("content-desc") or ""]
    for v in vals:
        if (v == q) if mode in ("exact", "class") else (q in v):
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
            print((x1 + x2) // 2, (y1 + y2) // 2)
            sys.exit(0)
sys.exit(1)
PY
}

see() { find_node "$1" "${2:-contains}" > /dev/null; }

expect_text() { see "$1" && pass "shows '$1'" || fail "expected '$1' on screen"; }

tap() {
  local xy
  xy=$(find_node "$1" "${2:-contains}") || fail "can't find '$1' to tap"
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
  adb shell input text "${1// /%s}"
  adb shell input keyevent KEYCODE_ENTER
  sleep 2
}

hide_keyboard() {
  if adb shell dumpsys input_method | grep -q "mInputShown=true"; then
    adb shell input keyevent KEYCODE_BACK
    sleep 1
  fi
}

# Swipe up on the board; the drawer must open.
open_drawer() {
  hide_keyboard
  adb shell input swipe 540 1700 540 500 250
  sleep 2
  see "Change home app" exact || fail "swipe up didn't open the app drawer"
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

crash_check() {
  if adb logcat -d -b crash | grep -q "$PKG"; then
    adb logcat -d -b crash > "$OUT/crash.txt"
    fail "app crashed (see crash.txt)"
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
  python3 - "$OUT/strictmode.txt" <<'PY' | tee -a "$OUT/steps.log" || fail "StrictMode: main-thread write, network, or leaked handle in app code (see strictmode.txt)"
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
for b in ours:
    print("    ", b["kind"], "via", next(f for f in b["frames"] if not f.startswith(PLATFORM)))
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
adb shell settings put global auto_time 0
adb shell date "$(date -u +%m%d)1200$(date -u +%Y).00" > /dev/null || log "warn: couldn't set the clock"
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
expect_text "Free time"
expect_text "Setup:"
expect_text "Today's line"
shot board-first-run

# Grant what a person grants through the setup line.
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell appops set $PKG USE_FULL_SCREEN_INTENT allow
adb shell dumpsys deviceidle whitelist +$PKG > /dev/null
home
if see "Setup:"; then fail "setup line still showing after every grant"; fi
pass "setup line clears once everything is granted"

quick_add "call landlord"
quick_add "taxes by fri high"
hide_keyboard
expect_text "taxes"
expect_text "High priority"
expect_text "Next: call landlord"
shot two-floats
expect_notification "Now: taxes" "lock-screen card shows the Now card"

tap Skip exact
expect_text "Back at"
expect_text "call landlord"
shot after-skip

tap Done exact
expect_text "Undo"
tap Undo exact
expect_text "Put back"
expect_text "call landlord"
pass "Undo puts the item back"
tap Done exact
expect_text "Free time"
shot after-done

quick_add "pickup 2pm hard"
hide_keyboard
expect_text "Next: pickup in"
shot anchor-in-ledger

quick_add "dentist thu"
expect_text "anchor or float"
shot ask-chips
tap Float exact
hide_keyboard
see "anchor or float" && fail "ask chips didn't close"
pass "ask chips close"

adb shell input keyevent KEYCODE_BACK
sleep 1
expect_front $PKG "Back on the board doesn't leave it"
crash_check

# --- Ways out to the normal phone ---------------------------------------------

open_drawer
expect_text "Change home app"
shot app-drawer

tap "Phone settings" exact
expect_front com.android.settings "Phone settings opens Settings"
home
expect_front $PKG "Home returns to the board from Settings"
adb shell dumpsys input_method | grep -q "mInputShown=true" && fail "keyboard opened by itself on returning home"
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
tap Settings exact
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
adb shell am start -a android.settings.SETTINGS > /dev/null
sleep 2
lock_screen
shot lock-screen
unlock_with_pin
expect_front $PKG "Unlock lands on the board"
adb logcat -d -s Initiative:V | tail -4 | tee -a "$OUT/steps.log"
shot after-unlock

# Switch it off: unlock should return to the last app instead.
open_drawer
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

# --- Alarms ---------------------------------------------------------------------

quick_add "dentist 12:40pm hard"
hide_keyboard
expect_text "Next: dentist"
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

# --- Large text -----------------------------------------------------------------

adb shell settings put system font_scale 2.0
home
sleep 2
shot large-font
for label in Done Skip Tomorrow; do
  xy=$(find_node "$label" exact) || fail "'$label' button missing at 200% font"
  y=${xy#* }
  [ "$y" -lt 2400 ] || fail "'$label' button off screen at 200% font"
done
pass "buttons fit at 200% font"
adb shell settings put system font_scale 1.0
home
crash_check

adb logcat -d > "$OUT/logcat.txt"
log "all checks passed"
