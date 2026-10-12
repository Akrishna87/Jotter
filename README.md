# 📝 Jotter for Android

Jotter is a **notes app**: notebooks, tags, checklists and light formatting, Evernote
style. Everything stays on your phone: there are no accounts, no ads and no internet
access.

It is one of three separate apps that work alike:
[**Doable**](https://github.com/Akrishna87/doable) (tasks),
[**Dayline**](https://github.com/Akrishna87/dayline) (calendar) and **Jotter** (notes).

## Installing it

1. On your Android phone, open the
   [latest build](https://github.com/Akrishna87/jotter/releases/tag/jotter-latest)
   and tap **Jotter.apk** to download it.
2. Open the downloaded file. Android will ask you to allow installing apps
   from your browser (or Files app). Allow it, then tap **Install**.
   Google Play Protect may warn that it doesn't recognise the app, because
   it isn't from the Play Store. Tap **More details → Install anyway**.
3. Open **Jotter**.

To update, install a newer `Jotter.apk` the same way. It installs over the old one and
keeps everything.

## What it does

- **Notebooks**: every note lives in a notebook. Make one with **New notebook**, and
  rename or delete it from the menu while it's open. Filter by notebook or **#tag**.
- **Writing**: notes open ready to read. Tap the text (or the pencil) to edit, with a
  toolbar for **checklists**, bulleted and numbered lists, headings, bold, italic,
  strikethrough and dividers. Lists carry on when you press enter, and pressing enter on
  an empty item ends the list. Notes save as you type.
- **Checklists** tick off right in the note, and the list shows progress like `2/5`.
- **Pin** notes to the top, **sort** by date updated, date created or title, **share** a
  note as text, and **Trash** (in the menu) keeps deleted notes until you restore them
  or empty it.
- **Search** finds notes by title, text or tag.
- **Light and dark themes**, following the phone or chosen in Settings.
- **Backup**: Settings → Export backup saves everything to a JSON file, and Restore from
  backup reads one back. A **Daybook** backup restores too: Jotter takes its notes and
  notebooks and leaves the tasks.

## How it's built

Kotlin and Jetpack Compose with Material 3, and no libraries beyond AndroidX.

- `model/` holds the data and the logic. It is plain Kotlin (no Android imports) with
  unit tests, so a web version can reuse it later: `Markdown.kt` parses notes and powers
  the editing toolbar.
- `Store.kt` keeps everything in memory and saves it to one JSON file, and `Backup.kt`
  exports and restores.
- `ui/` has the screens.
- `ci/smoke-test.sh` runs on an Android emulator for every build. It writes a note with a
  checklist, makes a notebook, searches, moves a note to the trash and back, and switches
  to the dark theme. Screenshots from that run are attached to each release.

Builds are made by the [Jotter APK workflow](.github/workflows/jotter-apk.yml) on
every push to this repo. Doable, Dayline and Jotter are signed with the same sideload key
(`signing/sideload.keystore`), so they can share data with each other later on.

Jotter started from the notes half of [Daybook](https://github.com/Akrishna87/daybook).
The two apps are independent and keep their data separately.
