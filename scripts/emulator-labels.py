#!/usr/bin/env python3
"""
Checks that every label the emulator test taps or waits for still exists:
in the app's wording (strings.xml), in what the test itself types, or in a
short list of Android's own labels. A label renamed in the app but not in
the test fails here, in a second, instead of in CI. Run before every push.
"""
import re
import sys
import xml.etree.ElementTree as ET

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
# Comment lines describe steps in words; only the steps themselves count.
script = "\n".join(l for l in open(f"{ROOT}/scripts/emulator-smoke.sh").read().split("\n") if not l.lstrip().startswith("#"))

# The app's wording, with %1$s and the like as "anything".
def unescape(t):
    return re.sub(r"\\(.)", r"\1", t.strip('"'))

patterns = []
for el in ET.parse(f"{ROOT}/app/src/main/res/values/strings.xml").getroot().iter():
    if el.tag in ("string", "item") and el.text:
        text = unescape(el.text)
        parts = re.split(r"%\d*\$?[sd]|%%", text)
        # Wording that's only a filled-in value, like "%1$s", says nothing to check against.
        if not "".join(parts).strip(" .,:·%"):
            continue
        patterns.append((".+?".join(re.escape(p) for p in parts), text))

# The engine's own wording (reason lines, times, notices), from core's string
# literals, with each ${...} as "anything".
import glob
for path in glob.glob(f"{ROOT}/core/src/main/kotlin/**/*.kt", recursive=True):
    for lit in re.findall(r'"((?:[^"\\\n]|\\.)*)"', open(path).read()):
        parts = re.split(r"\$\{[^}]*\}|\$\w+", lit)
        if "".join(parts).strip(" .,:·") and len("".join(parts)) > 2:
            patterns.append((".+?".join(re.escape(p) for p in parts), lit))

# What the test types or makes: quick-add lines and typed text, which show up as task names.
tasks = set(re.findall(r'quick_add "([^"]+)"', script))
typed = set(tasks)
typed |= {t.replace("%s", " ") for t in re.findall(r"input text \"?([A-Za-z%][^\"\s]*)", script)}

# Android's own screens and the test's set-up, not the app's wording.
ANDROID = {"Allow", "Don't allow", "Deny", "Clock", "Partner, ", "Now: taxes",
           "Focus: taxes", "Time to move", "turn on Bluetooth"}

# tap / wait_for / expect_text / scroll_to / wait_gone / see, with an optional mode.
# Only calls: at a line's start, or after if, !, ||, &&, or ; (never words inside a message).
calls = re.findall(r'(?:^\s*|\bif\s+!?\s*|\|\|\s*|&&\s*|;\s*|!\s+)(tap|wait_for|expect_text|scroll_to|wait_gone|see|expect_notification)\s+("([^"]*)"|([A-Za-z][\w-]*))(?:\s+(exact|iexact|lastexact|contains))?', script, re.M)

misses = []
for fn, _, quoted, bare, mode in calls:
    label = quoted or bare
    # Numbers the test set itself, like a slider's "50%", need no wording.
    if not label or "$" in label or label in ANDROID or re.fullmatch(r"[\d%:. ]+", label):
        continue
    exact = mode in ("exact", "iexact", "lastexact")
    flags = re.S | (re.I if mode == "iexact" else 0)

    def hit(text_pattern, text):
        if exact:
            return re.fullmatch(text_pattern, label, flags) is not None
        # "contains": the label is part of some wording, or a whole filled-in wording.
        return label in text or re.search(text_pattern, label, flags) is not None
    # A task name the test typed, run together with wording: "dentist at 12:40 PM".
    # Take out the names, times, and lengths; what's left must be in some wording.
    rest = label
    for t in sorted(tasks, key=len, reverse=True):
        rest = re.sub(re.escape(t.split()[0]), " ", rest, flags=re.I) if t.split() else rest
    rest = re.sub(r"\d{1,2}:\d{2}\s?[AP]M|\d+\s?(?:h|m|min)\b|\d+", " ", rest).strip()
    mixed = rest != label.strip() and (not rest or any(rest.lower() in t.lower() for _, t in patterns))
    found = mixed or any(hit(p, t) for p, t in patterns) or any(label.lower() == t.lower() if exact else label.lower() in t.lower() for t in typed)
    if not found:
        misses.append(f"{fn} {label!r}" + (f" ({mode})" if mode else ""))

if misses:
    print("Labels in emulator-smoke.sh not found in the app's wording or the test's own data:")
    print("\n".join("  " + m for m in sorted(set(misses))))
    sys.exit(1)
print(f"ok   {len(calls)} labels in the emulator test exist in the app")
