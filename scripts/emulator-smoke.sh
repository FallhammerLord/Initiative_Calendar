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
}

fail() {
  log "FAIL: $*"
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
find_node() {
  ui > "$OUT/ui.xml"
  python3 - "$OUT/ui.xml" "$1" "${2:-contains}" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, q, mode = sys.argv[1:4]
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(1)
for n in root.iter("node"):
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

crash_check() {
  if adb logcat -d -b crash | grep -q "$PKG"; then
    adb logcat -d -b crash > "$OUT/crash.txt"
    fail "app crashed (see crash.txt)"
  fi
  pass "no crashes so far"
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
expect_text "Display over other apps"
shot board-first-run

adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
home
see "Display over other apps" && fail "permission prompt still showing after grant"
pass "permission prompt clears once granted"

quick_add "call landlord"
quick_add "taxes by fri high"
hide_keyboard
expect_text "taxes"
expect_text "High priority"
expect_text "Next: call landlord"
shot two-floats

tap Skip exact
expect_text "Back at"
expect_text "call landlord"
shot after-skip

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
crash_check

# --- Lock screen and board after unlock ----------------------------------------

adb shell locksettings set-pin 1234 > /dev/null || fail "couldn't set a test PIN"
adb shell am start -a android.settings.SETTINGS > /dev/null
sleep 2
adb shell input keyevent KEYCODE_SLEEP
sleep 2
adb shell input keyevent KEYCODE_WAKEUP
sleep 2
shot lock-screen
adb shell input swipe 540 1800 540 500 250
sleep 1
adb shell input text 1234
adb shell input keyevent KEYCODE_ENTER
sleep 3
expect_front $PKG "PIN unlock works and lands on the board"
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
adb shell input keyevent KEYCODE_SLEEP
sleep 2
adb shell input keyevent KEYCODE_WAKEUP
sleep 2
adb shell input swipe 540 1800 540 500 250
sleep 1
adb shell input text 1234
adb shell input keyevent KEYCODE_ENTER
sleep 3
expect_front com.android.settings "With the switch off, unlock returns to the last app"
adb shell locksettings clear --old 1234 > /dev/null
home
crash_check

adb logcat -d > "$OUT/logcat.txt"
log "all checks passed"
