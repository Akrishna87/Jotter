#!/usr/bin/env bash
# Runs inside the Android emulator job: Home and the scratch pad, a note with a checklist, tag and
# reminder, notebooks, Evernote-style search, the trash, templates, and the dark theme.
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
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb shell pm grant "$PKG" android.permission.RECORD_AUDIO || true
adb logcat -c

close_panel() { hide_keyboard; adb shell input keyevent KEYCODE_BACK; sleep 1; }
scroll_until() { # scroll_until <dump name> <grep -E pattern> <tries> <what>
  local i
  for i in $(seq 1 "$3"); do
    dump "$1"
    grep -Eq "$2" "$OUT/$1.xml" && return 0
    adb shell input swipe 540 1500 540 700 300
    sleep 1
  done
  fail "$4"
}

echo "--- Launching the app (opens on Home)"
adb shell am start -W -n "$PKG/.MainActivity"
wait_for home 'text="Welcome to Jotter"' 30 "the welcome note isn't on Home"
grep -q 'text="Scratch pad"' "$OUT/home.xml" || fail "Home has no scratch pad"
shot 1-home
show home
echo "PASS: Home"

echo "--- The scratch pad becomes a note"
tap home "Scratch pad" last
sleep 1
adb shell input text "Call%sthe%splumber"
hide_keyboard
wait_for scratch 'text="Save as note"' 10 "the scratch pad has no Save as note"
tap scratch "Save as note"
wait_for scratch-note 'content-desc="Note options"' 10 "the scratch pad didn't open as a note"
grep -q 'text="Call the plumber"' "$OUT/scratch-note.xml" || fail "the scratch pad note has the wrong title"
adb shell input keyevent KEYCODE_BACK
sleep 1
echo "PASS: scratch pad"

echo "--- A note with a checklist, a tag and a reminder"
dump home-2
tap home-2 "New note"
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
tap note-ticked "Add tag"
wait_for tag-dialog 'text="New tag"' 10 "the tag dialog didn't open"
tap tag-dialog "New tag"
sleep 1
adb shell input text "work"
dump tag-typed
tap tag-typed "Add"
sleep 1
dump tag-added
tap tag-added "Done"
wait_for tagged 'text="#work"' 10 "the tag isn't on the note"
tap tagged "Add reminder"
wait_for reminder 'text="Tomorrow"' 10 "the reminder choices didn't open"
tap reminder "Tomorrow"
wait_for reminded 'content-desc="Reminder"' 10 "the reminder isn't on the note"
shot 3-note-tagged
adb shell input keyevent KEYCODE_BACK
wait_for home-3 'text="Meeting notes"' 10 "the new note isn't on Home"
scroll_until home-tasks 'content-desc="Tick Book room"' 4 "Home doesn't list the open checklist item"
shot 4-home-tasks
echo "PASS: checklist, tag, reminder and Home"

echo "--- Notes and notebooks"
tap home-tasks "Notes" last
wait_for notes 'text="Meeting notes"' 10 "the Notes tab doesn't list the note"
grep -q 'text="1/2"' "$OUT/notes.xml" || fail "the note's checklist progress isn't shown"
tap notes "New notebook"
sleep 2
adb shell input text "Work"
adb shell input keyevent KEYCODE_ENTER
wait_for notebook 'text="No notes here"' 10 "the new notebook didn't open empty"
tap notebook "New note" last
wait_for nb-note 'content-desc="Note text"' 10 "the note editor didn't open in the notebook"
sleep 1
adb shell input text "Quarterly%splan"
hide_keyboard
adb shell input keyevent KEYCODE_BACK
wait_for notebook-note 'text="Quarterly plan"' 10 "the note isn't in its notebook"
grep -q 'text="Meeting notes"' "$OUT/notebook-note.xml" && fail "a note from another notebook shows in Work"
shot 5-notebook
tap notebook-note "All notes"
wait_for all-notes 'text="Meeting notes"' 10 "All notes doesn't show every note"
echo "PASS: notebooks"

echo "--- The Notebooks tab"
tap all-notes "Notebooks" last
wait_for notebooks-tab 'content-desc="Notebook Work, 1 note"' 10 "the Notebooks tab doesn't show Work with its note"
shot 5b-notebooks
tap notebooks-tab "Notebook Work, 1 note"
wait_for work-book 'text="Quarterly plan"' 10 "opening a notebook card doesn't list its notes"
adb shell input keyevent KEYCODE_BACK
sleep 1
dump back-to-books
tap back-to-books "Notes" last
wait_for all-notes 'text="Meeting notes"' 10 "the Notes tab didn't come back"
echo "PASS: Notebooks tab"

echo "--- Search, Evernote style, and a saved search"
tap all-notes "Search"
sleep 2
adb shell input text "tag:work%sbud*"
sleep 1
hide_keyboard
wait_for search 'text="Meeting notes"' 10 "search didn't find the note by tag and word"
grep -q 'text="Quarterly plan"' "$OUT/search.xml" && fail "search found a note it shouldn't"
shot 6-search
tap search "Save search"
wait_for save-search 'text="Save search"' 10 "the save search dialog didn't open"
tap save-search "Save" last
sleep 1
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
tap trashed "Browse" last
wait_for browse 'text="Saved searches"' 10 "Browse doesn't list the saved search"
shot 7-browse
scroll_until browse-trash 'text="Trash"' 4 "Browse has no Trash"
tap browse-trash "Trash"
wait_for trash 'text="Quarterly plan"' 10 "the note isn't in the trash"
shot 8-trash
tap trash "Restore Quarterly plan"
sleep 1
adb shell input keyevent KEYCODE_BACK
echo "PASS: trash"

echo "--- A note from a template"
scroll_until browse-tpl 'text="Templates"' 4 "Browse has no Templates"
tap browse-tpl "Templates"
wait_for templates 'text="Meeting notes"' 10 "the templates aren't listed"
tap templates "Weekly planner"
wait_for from-template 'text="Top priorities"' 10 "the template's text isn't in the new note"
shot 9-template
adb shell input keyevent KEYCODE_BACK
sleep 1
echo "PASS: templates"

echo "--- Dark theme"
dump before-settings
tap before-settings "Settings"
wait_for settings 'Theme: Dark' 10 "Settings has no theme choice"
tap settings "Theme: Dark"
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 1
dump browse-dark
tap browse-dark "Home" last
sleep 2
shot 10-home-dark
echo "PASS: dark theme"

echo "--- A recording, transcribed on the phone and summarised"
dump home-dark
tap home-dark "New note" last
wait_for rec-note 'content-desc="Note text"' 10 "the new note didn't open"
hide_keyboard
dump rec-note
tap rec-note "Record audio"
wait_for recording 'content-desc="Stop recording"' 15 "recording didn't start"
sleep 4
dump recording
tap recording "Stop recording"
sleep 2
dump rec-stopped
try_tap rec-stopped "Done editing" || true
wait_for rec-view 'content-desc="Transcribe recording"' 15 "the recording has no Transcribe button"
shot 11-recording
tap rec-view "Transcribe recording"
wait_for rec-download 'text="Download"' 10 "no prompt to download the speech model"
tap rec-download "Download" last
# The model is downloaded (about 60 MB) and run on the emulator's CPU.
wait_for rec-summary 'text="Summary"|Couldn|isn.t available|stopped part-way' 900 "the recording wasn't transcribed"
grep -q 'text="Summary"' "$OUT/rec-summary.xml" || fail "transcription failed: $(grep -o 'text="[^"]*Couldn[^"]*"\|text="[^"]*available[^"]*"\|text="[^"]*stopped[^"]*"' "$OUT/rec-summary.xml" | head -1)"
shot 12-transcribed
echo "PASS: transcription"

echo "--- A spoken meeting: transcript, then a smart summary written on the phone"
espeak-ng -s 140 -w "$OUT/meeting.wav" "Good morning everyone. Today we are planning the launch of the mobile app. \
The main issue is that the marketing budget is too small for the launch in March. \
After some discussion, we agreed to move fifty thousand dollars from design to marketing. \
Priya, can you update the budget sheet by Friday? \
I will send the revised launch plan to the whole team tomorrow. Thanks everyone."
adb push "$OUT/meeting.wav" /sdcard/Music/meeting.wav > /dev/null
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Music/meeting.wav > /dev/null
media_id=""
for i in $(seq 1 15); do
  media_id=$(adb shell content query --uri content://media/external/audio/media --projection _id --where "_display_name=\'meeting.wav\'" | grep -o '_id=[0-9]*' | head -1 | cut -d= -f2)
  [ -n "$media_id" ] && break
  sleep 2
done
[ -n "$media_id" ] || fail "the test recording didn't reach the media store"
adb shell am start -a android.intent.action.SEND -t audio/wav --eu android.intent.extra.STREAM "content://media/external/audio/media/$media_id" --grant-read-uri-permission -n "$PKG/.MainActivity" > /dev/null
wait_for shared-audio 'content-desc="Transcribe recording"' 20 "the shared recording didn't open as a note"
tap shared-audio "Transcribe recording"
wait_for meeting-quick 'content-desc="Make smart summary"|No speech was heard|Couldn' 600 "the meeting wasn't transcribed"
grep -q 'content-desc="Make smart summary"' "$OUT/meeting-quick.xml" || fail "no speech was found in the spoken meeting"
grep -qi 'budget' "$OUT/meeting-quick.xml" || fail "the quick summary doesn't mention the budget"
shot 13-meeting-quick
tap meeting-quick "Make smart summary"
wait_for smart-download 'text="Download"' 10 "no prompt to download the summary model"
grep -o 'The [A-Za-z]* model (picked for this phone)' "$OUT/smart-download.xml" || true
tap smart-download "Download" last
# The Lite model (about 400 MB) is downloaded and run on the emulator's CPU.
wait_for smart 'text="Smart summary"|usable answer|Couldn|stopped part-way|t available' 1500 "no smart summary was written"
grep -q 'text="Smart summary"' "$OUT/smart.xml" || fail "the smart summary failed: $(grep -o 'text="[^"]*\(usable\|Couldn\|stopped\|available\)[^"]*"' "$OUT/smart.xml" | head -1)"
shot 14-smart-summary
echo "Smart summary on screen:"
grep -o 'text="[^"]*"' "$OUT/smart.xml" | sed -n '/Smart summary/,$p' | head -20
echo "PASS: smart summary"

# Only Jotter's own crashes count (the screen dump tool sometimes crashes on its own).
if adb logcat -d | grep -A2 "FATAL EXCEPTION" | grep -q "Process: $PKG"; then
  adb logcat -d > "$OUT/logcat.txt"
  fail "the app crashed (see logcat.txt)"
fi
echo "ALL SMOKE TESTS PASSED"
