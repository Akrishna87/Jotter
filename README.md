# 📝 Jotter for Android

Jotter is a **notes app** in the spirit of Evernote: notebooks and stacks, tags,
checklists, rich formatting, photos, scans, recordings, sketches, reminders and powerful
search. Everything stays on your phone: there are no accounts, no ads and no internet
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

Jotter aims to have what Evernote has, except the AI and the things that need a server
(see the end of this list).

- **Home**: buttons to start a note, checklist, photo, scan, recording, sketch or a note
  from a template; a **scratch pad** that becomes a note; pinned and recent notes;
  reminders; unticked checklist items from every note (tick them right there); shortcuts
  and tags. Settings chooses which of these show.
- **Writing**: notes open ready to read. Tap the text (or the pencil) to edit, with a
  toolbar for checklists, bulleted and numbered lists (indent and outdent them),
  headings, **bold**, *italic*, underline, strikethrough, highlight, code, quotes, code
  blocks, **tables**, web links, **links to other notes**, and dividers. Notes save as
  you type. Notes that link to the one you're reading are listed at the end of it.
- **Attachments**: photos from the gallery or the camera, **document scans** (made grey
  and high-contrast, like a scan), **audio recordings** with a player in the note,
  **sketches**, PDFs (with a preview of the first page) and any other file. Long-press
  one to open, share or remove it.
- **Organising**: notebooks, grouped into **stacks**; **nested tags**; a default
  notebook; **shortcuts** to notes, notebooks, stacks, tags and searches; and
  **templates** (eight built in, or save any note as one).
- **Reminders** on any note, with **Mark as done** and **Snooze** on the notification,
  and a list of what's coming up and what's done.
- **Search** in Evernote's syntax: `"exact words"`, `bud*`, `-word`, `any:`, `intitle:`,
  `notebook:`, `stack:`, `tag:` (with nested tags), `created:day-7`, `updated:week`,
  `todo:true`, `resource:image/*`, `reminder:true`, `source:` and more, with help on the
  search screen. **Save searches** to come back to.
- **Notes list** as snippets with a picture, cards or a simple list; sorted by date
  updated, date created, title or reminder; pinned notes first. Long-press notes to
  select several, then move, tag, pin, **merge**, print or trash them.
- **Note tools**: **note history** (earlier versions you can restore), note info (dates,
  words, size, links), duplicate, copy a link to the note, share, **print or save as
  PDF**, and export as HTML, Markdown or `.enex`.
- **Import** Evernote `.enex` files (with their attachments, checklists, tables and
  reminders), Markdown and text files. **Export** everything for Evernote or as a web
  page.
- **Passcode lock**, with fingerprint unlock and a choice of how soon it locks again.
- **Shortcuts outside the app**: share text, links, photos or PDFs to Jotter from any
  app; a Quick Settings tile; long-press the app icon for a new note, checklist or photo;
  and two widgets, **Quick note** and **Recent notes**.
- **Trash** keeps deleted notes until you restore them or empty it. **Light and dark
  themes** and four text sizes.
- **Backup**: Settings → Export backup saves every note, notebook and setting to a JSON
  file (attachments aren't included; use the `.enex` export for those), and Restore from
  backup reads one back. A **Daybook** backup restores too.

Left out, because they need an account and a server: syncing between devices, sharing and
working on notes together, Spaces, the web clipper, and searching text inside images.

## How it's built

Kotlin and Jetpack Compose with Material 3, and no libraries beyond AndroidX.

- `model/` holds the data and the logic. It is plain Kotlin (no Android imports) with
  unit tests, so a web version can reuse it later: `Markdown.kt` parses notes and powers
  the editing toolbar, `Search.kt` is the search syntax, `Enex.kt` and `Html.kt` read and
  write Evernote and web files, and `Templates.kt` has the built-in templates.
- `Store.kt` keeps everything in memory and saves it to one JSON file; `Files.kt` keeps
  attachments; `Recorder.kt`, `Reminders.kt`, `Exports.kt`, `Lock.kt` and `Widgets.kt` do
  what their names say; and `Backup.kt` exports and restores.
- `ui/` has the screens.
- `ci/smoke-test.sh` runs on an Android emulator for every build. It saves the scratch
  pad as a note, writes a note with a checklist, a tag and a reminder, checks Home, makes
  a notebook, searches in Evernote's syntax and saves the search, moves a note to the
  trash and back, starts a note from a template, and switches to the dark theme. Screenshots from that run are attached to each release.

Builds are made by the [Jotter APK workflow](.github/workflows/jotter-apk.yml) on
every push to this repo. Doable, Dayline and Jotter are signed with the same sideload key
(`signing/sideload.keystore`), so they can share data with each other later on.

Jotter started from the notes half of [Daybook](https://github.com/Akrishna87/daybook).
The two apps are independent and keep their data separately.
