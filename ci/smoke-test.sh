#!/usr/bin/env bash
# Runs inside the Android emulator job: writes a note with a checklist, makes a notebook, searches,
# moves a note to the trash and back, and switches to the dark theme.
# Usage: smoke-test.sh <apk> <output dir>
set -euo pipefail

APK="$1"
OUT="$2"
PKG=io.github.akrishna87.jotter
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"

fail() {
  echo "SMOKE TEST FAILED: $*"
  adb exec-out screencap -p > "$OUT/failure.png" || true
  if adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/failure.xml" > /dev/null 2>&1; then
    echo "On screen:"; grep -o 'text="[^"]\+"' "$OUT/failure.xml" | head -60 || true
    echo "Tappable:"; python3 - "$OUT/failure.xml" <<'PY' || true
import sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).iter("node"):
    if n.get("clickable") == "true":
        print(" ", n.get("class"), repr(n.get("text")), repr(n.get("content-desc")), n.get("bounds"))
PY
    adb shell wm size || true
  fi
  adb logcat -d > "$OUT/logcat.txt" || true
  echo "Crashes and errors from the app:"
  grep -A 40 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80 || true
  exit 1
}
dump() {
  local i
  for i in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/$1.xml" > /dev/null 2>&1 || { sleep 2; continue; }
    # The emulator's own apps sometimes freeze while it warms up; wave the "isn't responding"
    # popup away so it doesn't cover the app. Crashes of Jotter itself are caught from logcat.
    grep -q "isn&apos;t responding\|isn't responding" "$OUT/$1.xml" || return 0
    echo "(dismissing a system 'isn't responding' popup)"
    python3 "$HERE/find_text.py" "$OUT/$1.xml" "Wait" > /dev/null 2>&1 && adb shell input tap $(python3 "$HERE/find_text.py" "$OUT/$1.xml" "Wait")
    sleep 3
  done
}
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
show() { echo "  on screen: $(tr '\n' ' ' < "$OUT/$1.xml" | grep -o 'text="[^"]\+"' | sed 's/^text=//' | head -40 | tr '\n' ' ')"; }
tap() { # tap <dump name> <text> [first|last]
  local xy
  xy=$(python3 "$HERE/find_text.py" "$OUT/$1.xml" "$2" "${3:-first}") || fail "couldn't find '$2' on screen"
  adb shell input tap $xy
}
try_tap() { # like tap, but carries on if it isn't there
  local xy
  xy=$(python3 "$HERE/find_text.py" "$OUT/$1.xml" "$2" "${3:-first}" 2> /dev/null) || return 1
  adb shell input tap $xy
}
wait_for() { # wait_for <dump name> <grep -E pattern> <seconds> <what>
  local end=$((SECONDS + $3))
  while [ $SECONDS -lt $end ]; do
    dump "$1"
    grep -Eq "$2" "$OUT/$1.xml" && return 0
    sleep 2
  done
  fail "$4 (waited $3 s)"
}
hide_keyboard() { if adb shell dumpsys input_method | grep -q "mInputShown=true"; then adb shell input keyevent KEYCODE_BACK; sleep 1; fi; }

adb wait-for-device
adb install -r "$APK"
adb shell settings put global hide_error_dialogs 1 || true
adb logcat -c

close_panel() { hide_keyboard; adb shell input keyevent KEYCODE_BACK; sleep 1; }

echo "--- Launching the app (opens on the notes)"
adb shell am start -W -n "$PKG/.MainActivity"
wait_for notes 'text="Welcome to Jotter"' 30 "the welcome note is missing"
shot 1-notes
show notes
echo "PASS: notes list"

echo "--- A note with a checklist"
tap notes "New note"
wait_for note-new 'content-desc="Note text"' 10 "the note editor didn't open"
sleep 1
adb shell input text "Meeting%snotes"
tap note-new "Note text"
sleep 1
adb shell input text "Agenda"
adb shell input keyevent KEYCODE_ENTER
dump note-agenda
tap note-agenda "Checklist"
sleep 1
adb shell input text "Review%sbudget"
adb shell input keyevent KEYCODE_ENTER
adb shell input text "Book%sroom"
hide_keyboard
dump note-typed
tap note-typed "Done editing"
sleep 1
dump note-view
shot 2-note
grep -q 'content-desc="Tick Review budget"' "$OUT/note-view.xml" || fail "the checklist didn't render"
grep -q 'content-desc="Tick Book room"' "$OUT/note-view.xml" || fail "the checklist didn't continue on enter"
tap note-view "Tick Review budget"
sleep 1
dump note-ticked
grep -q 'content-desc="Untick Review budget"' "$OUT/note-ticked.xml" || fail "ticking a checklist item didn't work"
hide_keyboard
adb shell input keyevent KEYCODE_BACK
wait_for notes-after 'text="Meeting notes"' 10 "the new note isn't in the list"
grep -q 'text="1/2"' "$OUT/notes-after.xml" || fail "the note's checklist progress isn't shown"
shot 3-notes-list
echo "PASS: notes with checklists"

echo "--- Notebooks"
tap notes-after "New notebook"
sleep 2
adb shell input text "Work"
adb shell input keyevent KEYCODE_ENTER
wait_for notebook 'text="No notes here"' 10 "the new notebook didn't open empty"
tap notebook "New note"
wait_for nb-note 'content-desc="Note text"' 10 "the note editor didn't open in the notebook"
sleep 1
adb shell input text "Quarterly%splan"
hide_keyboard
adb shell input keyevent KEYCODE_BACK
wait_for notebook-note 'text="Quarterly plan"' 10 "the note isn't in its notebook"
grep -q 'text="Meeting notes"' "$OUT/notebook-note.xml" && fail "a note from another notebook shows in Work"
shot 4-notebook
tap notebook-note "All notes"
wait_for all-notes 'text="Meeting notes"' 10 "All notes doesn't show every note"
echo "PASS: notebooks"

echo "--- Search"
tap all-notes "Search"
sleep 2
adb shell input text "budget"
sleep 1
hide_keyboard
wait_for search 'text="Meeting notes"' 10 "search didn't find the note by its text"
shot 5-search
adb shell input keyevent KEYCODE_BACK
sleep 1
echo "PASS: search"

echo "--- Trash"
dump before-trash
tap before-trash "Quarterly plan"
wait_for trash-open 'content-desc="Note options"' 10 "the note didn't open"
tap trash-open "Note options"
sleep 1
dump trash-menu
tap trash-menu "Move to trash"
wait_for trash-confirm 'text="Move to trash?"' 10 "no confirmation before moving to trash"
tap trash-confirm "Move to trash" last
wait_for trashed 'text="Note moved to trash"' 10 "no message after moving to trash"
grep -q 'text="Quarterly plan"' "$OUT/trashed.xml" && fail "the trashed note is still listed"
tap trashed "Notes options"
sleep 1
dump notes-menu
tap notes-menu "Trash"
wait_for trash 'text="Quarterly plan"' 10 "the note isn't in the trash"
shot 6-trash
tap trash "Restore Quarterly plan"
sleep 1
adb shell input keyevent KEYCODE_BACK
wait_for restored 'text="Quarterly plan"' 10 "the restored note isn't back in the list"
echo "PASS: trash"

echo "--- Dark theme"
tap restored "Notes options"
sleep 1
dump menu-2
tap menu-2 "Settings"
wait_for settings 'Theme: Dark' 10 "Settings has no theme choice"
tap settings "Theme: Dark"
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 2
shot 7-notes-dark
echo "PASS: dark theme"

if adb logcat -d | grep -q "FATAL EXCEPTION"; then
  adb logcat -d > "$OUT/logcat.txt"
  fail "the app crashed (see logcat.txt)"
fi
echo "ALL SMOKE TESTS PASSED"
