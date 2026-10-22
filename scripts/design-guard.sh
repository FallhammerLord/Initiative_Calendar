#!/usr/bin/env bash
# Fails when code bypasses the shared design system, wording, formatters, or
# services. See docs/spec.md, Standards. Run from the repo root; CI runs it on
# every push.
set -uo pipefail

APP=app/src/main/java/net/fallhammer/initiative
UI_SYSTEM="$APP/ui/Design.kt|$APP/ui/Theme.kt"
failed=0

# check <rule> <regex> <files allowed to match, as an egrep alternation or "">
check() {
  local rule=$1 pattern=$2 allowed=$3
  local hits
  hits=$(grep -rnE "$pattern" "$APP" --include=*.kt | grep -vE "^($allowed):" || true)
  if [ -n "$hits" ]; then
    echo "::error::$rule"
    echo "$hits" | sed 's/^/    /'
    failed=1
  else
    echo "ok   $rule"
  fi
}

check "Font sizes and line heights come from Type in Design.kt" \
  '[0-9]+(\.[0-9]+)?\.sp\b|fontSize *=' "$UI_SYSTEM"
check "Spacing comes from Space in Design.kt (zone, inside, and the steps)" \
  '(padding|spacedBy|Spacer\(Modifier\.(height|width))\([^)]*[0-9]+\.dp' "$UI_SYSTEM"
check "Colors come from the theme in Theme.kt" \
  'Color\(0x|Color\.(Black|White|Red|Gray|Yellow|Blue|Green)' "$APP/ui/Theme.kt"
check "Corner shapes come from the theme in Theme.kt" \
  '(Rounded|Cut)CornerShape\(' "$APP/ui/Theme.kt"
check "Wording lives in strings.xml, not in Text(\"...\")" \
  'Text\("[^"$]*[A-Za-z]' ""
check "Notification and dialog wording lives in strings.xml" \
  '(setContentTitle|setContentText|addAction\(0,) *\(?"' ""
check "Times and dates are formatted only in core Formats" \
  'ofPattern\(|DateTimeFormatter' ""
check "Wall labels come from Formats.wall" \
  '"(Soft|Firm|Hard)"' ""
check "Services come from AppGraph, never built directly" \
  'InitiativeDb\.get\(|(=|\(|return) *(Repository|Placement|Prefs|Setup|Apps)\(' "$APP/AppGraph.kt"
check "No all-caps labels (hard to read with dyslexia)" \
  'Text\("[A-Z]{3,}"|textTransform|uppercase\(\)' ""

check "Buttons come from Design.kt (each sits on the dark surface)" \
  '(^|[^A-Za-z.])(Button|TextButton|OutlinedButton|FilledTonalButton|ElevatedButton)\(' "$APP/ui/Design.kt"

# Every text field sits on the dark surface too: each OutlinedTextField passes fieldColors().
field_hits=""
for f in $(grep -rlE 'OutlinedTextField\(' "$APP" --include=*.kt); do
  fields=$(grep -cE 'OutlinedTextField\(' "$f")
  colored=$(grep -cE 'colors *= *fieldColors\(\)' "$f")
  [ "$colored" -lt "$fields" ] && field_hits="$field_hits    $f: $fields fields, $colored with fieldColors()\n"
done
if [ -n "$field_hits" ]; then
  echo "::error::Text fields use fieldColors(), so they sit on the dark surface"
  printf "$field_hits"
  failed=1
else
  echo "ok   Text fields use fieldColors(), so they sit on the dark surface"
fi

# The engine writes its own few English reason lines; it may not format times itself.
if grep -rnE 'ofPattern\(' core/src/main --include=*.kt | grep -v "core/src/main/kotlin/initiative/core/Formats.kt" ; then
  echo "::error::Engine times and dates are formatted only in Formats.kt"
  failed=1
else
  echo "ok   Engine times and dates are formatted only in Formats.kt"
fi

# Every label the emulator test taps or waits for still exists in the app.
if ! python3 scripts/emulator-labels.py .; then
  echo "::error::A label in emulator-smoke.sh no longer matches the app; update the test with the change"
  failed=1
fi

exit $failed
