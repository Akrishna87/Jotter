package io.github.akrishna87.jotter

import io.github.akrishna87.jotter.model.Attachment
import io.github.akrishna87.jotter.model.Block
import io.github.akrishna87.jotter.model.Data
import io.github.akrishna87.jotter.model.Enex
import io.github.akrishna87.jotter.model.HomeSection
import io.github.akrishna87.jotter.model.Html
import io.github.akrishna87.jotter.model.Links
import io.github.akrishna87.jotter.model.ListStyle
import io.github.akrishna87.jotter.model.Markdown
import io.github.akrishna87.jotter.model.Note
import io.github.akrishna87.jotter.model.Notebook
import io.github.akrishna87.jotter.model.SavedSearch
import io.github.akrishna87.jotter.model.Search
import io.github.akrishna87.jotter.model.Settings
import io.github.akrishna87.jotter.model.Shortcut
import io.github.akrishna87.jotter.model.ShortcutKind
import io.github.akrishna87.jotter.model.Span
import io.github.akrishna87.jotter.model.TagInfo
import io.github.akrishna87.jotter.model.Template
import io.github.akrishna87.jotter.model.Templates
import io.github.akrishna87.jotter.model.Version
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class EvernoteTest {
    private val utc = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 12)
    private fun at(d: LocalDate) = d.atStartOfDay(utc).toInstant().toEpochMilli()

    @Test
    fun richBlocks() {
        val b = Markdown.parse("> quoted\n  - nested\n    [x] deeper\n```kotlin\nval a = 1\n\nval b = 2\n```\n| A | B |\n|---|---|\n| 1 | 2 |\n| 3 |\n![photo.jpg](attachment:abc)\nend")
        assertEquals(Block.Quote(0, "quoted"), b[0])
        assertEquals(Block.Bullet(1, "nested", indent = 1), b[1])
        assertEquals(Block.Check(2, true, "deeper", indent = 2), b[2])
        assertEquals(Block.Code(3, 7, "val a = 1\n\nval b = 2", "kotlin"), b[3])
        assertEquals(Block.Table(8, 11, listOf(listOf("A", "B"), listOf("1", "2"), listOf("3", ""))), b[4])
        assertEquals(Block.Media(12, "photo.jpg", "attachment:abc"), b[5])
        assertEquals("abc", (b[5] as Block.Media).attachmentId)
        assertEquals(Block.Paragraph(13, "end"), b[6])
        // Ticking keeps the indent.
        assertEquals("  - [x] a", Markdown.toggleCheck("  - [ ] a", 0))
    }

    @Test
    fun richInline() {
        assertEquals(
            listOf(Span("a "), Span("u", underline = true), Span(" "), Span("hi", highlight = true), Span(" "), Span("x()", code = true)),
            Markdown.inline("a <u>u</u> ==hi== `x()`"),
        )
        assertEquals(listOf(Span("see "), Span("site", link = "https://example.com"), Span(".")), Markdown.inline("see [site](https://example.com)."))
        assertEquals(listOf(Span("go "), Span("https://a.org/x", link = "https://a.org/x"), Span(".")), Markdown.inline("go https://a.org/x."))
        assertEquals(listOf(Span("Plan", link = "title:Plan")), Markdown.inline("[[Plan]]"))
        assertEquals(listOf(Span("bold link", bold = true, link = "note:n1")), Markdown.inline("**[bold link](note:n1)**"))
        // snake_case isn't italic, and C++ is just text.
        assertEquals(listOf(Span("my_var_name and C++")), Markdown.inline("my_var_name and C++"))
    }

    @Test
    fun editingHelp() {
        // Lists keep their indent.
        assertEquals("  - a\n  - " to 10, Markdown.continueList("  - a", "  - a\n", 6))
        assertEquals("> q\n> " to 6, Markdown.continueList("> q", "> q\n", 4))
        assertEquals("  - a" to 5, Markdown.indent("- a", 3))
        assertEquals("- a" to 1, Markdown.indent("  - a", 3, -1))
        assertEquals("  [ ] a" to 7, Markdown.toggleLinePrefix("  - a", 5, "[ ] "))
        assertEquals("> a" to 3, Markdown.toggleLinePrefix("a", 1, "> "))
        assertEquals("# T" to 3, Markdown.toggleLinePrefix("## T", 4, "# "))
        assertEquals(Triple("<u>x</u>", 3, 4), Markdown.wrap("x", 0, 1, "<u>", "</u>"))
        assertEquals("a\n---\nb" to 6, Markdown.insertBlock("a\nb", 2, "---"))
        assertEquals("a\n---\n" to 6, Markdown.insertBlock("a", 1, "---"))
        assertEquals(Block.Table(0, 3, listOf(listOf("Column 1", "Column 2"), listOf("", ""), listOf("", ""))), Markdown.parse(Markdown.table(2, 2)).single())
        assertEquals(5, Markdown.wordCount("# One two\n[ ] three\n**four** five"))
    }

    @Test
    fun historyKeepsSettledVersions() {
        val minute = 60_000L
        val n = Note(title = "T", body = "one", createdAt = 0, updatedAt = 0)
        // Edited long after: the old version is kept.
        val a = n.edited(n.copy(body = "two", updatedAt = 60 * minute), now = 60 * minute)
        assertEquals(listOf(Version(0, "T", "one")), a.history)
        // Edited again a minute later: same burst, nothing new kept.
        val b = a.edited(a.copy(body = "three", updatedAt = 61 * minute), now = 61 * minute)
        assertEquals(a.history, b.history)
        // Unchanged text keeps nothing.
        assertEquals(b.history, b.edited(b.copy(pinned = true), now = 999 * minute).history)
    }

    @Test
    fun tagsNestAndNotebooksStack() {
        val d = Data(
            notes = listOf(Note(tags = listOf("Meetings")), Note(tags = listOf("Home"))),
            tagInfo = listOf(TagInfo("Meetings", "Work"), TagInfo("Standups", "Meetings")),
            notebooks = listOf(Notebook.Default, Notebook(name = "Clients", stack = "Work"), Notebook(name = "Ideas", stack = "Work")),
        )
        assertEquals(listOf("Home", "Meetings", "Standups", "Work"), d.tags)
        assertEquals(listOf("Home", "Work"), d.childrenOf(null))
        assertEquals(setOf("work", "meetings", "standups"), d.tagAndChildren("Work"))
        assertEquals(listOf("Work"), d.stacks)
        assertEquals(listOf("Clients", "Ideas"), d.notebooksIn("work").map { it.name })
        assertEquals(listOf(Notebook.Default), d.notebooksIn(null))
    }

    @Test
    fun linksAndBacklinks() {
        val target = Note(id = "t", title = "Project Plan")
        val byId = Note(id = "a", body = "See ${Links.to(target)}")
        val byTitle = Note(id = "b", body = "Also [[project plan]]")
        val other = Note(id = "c", body = "nothing")
        val d = Data(notes = listOf(target, byId, byTitle, other))
        assertEquals("[Project Plan](note:t)", Links.to(target))
        assertEquals(listOf("a", "b"), d.backlinks("t").map { it.id })
        assertEquals(target, d.linked("note:t"))
        assertEquals(target, d.linked("title:Project plan"))
        assertNull(d.linked("https://example.com"))
    }

    @Test
    fun evernoteSearch() {
        val work = Notebook(id = "w", name = "Work", stack = "Office")
        val photo = Attachment(name = "board.jpg", mime = "image/jpeg")
        val notes = listOf(
            Note(id = "1", title = "Weekly sync", body = "[ ] send minutes\nBudget review", notebookId = "w", tags = listOf("Standups"), createdAt = at(today), updatedAt = at(today)),
            Note(id = "2", title = "Groceries", body = "[x] milk", tags = listOf("home"), createdAt = at(today.minusDays(40)), updatedAt = at(today.minusDays(40))),
            Note(id = "3", title = "Whiteboard", body = Markdown.mediaLine(photo), attachments = listOf(photo), reminderAt = 5, createdAt = at(today.minusDays(3)), updatedAt = at(today.minusDays(3)), source = "https://example.com/page"),
        )
        val d = Data(notes = notes, notebooks = listOf(Notebook.Default, work), tagInfo = listOf(TagInfo("Standups", "Meetings"), TagInfo("Meetings", "Work")))
        fun ids(q: String) = Search.run(q, d, today, utc).map { it.id }
        assertEquals(listOf("1"), ids("budget"))
        assertEquals(listOf("1"), ids("\"budget review\""))
        assertEquals(listOf<String>(), ids("\"review budget\""))
        assertEquals(listOf("1"), ids("bud*"))
        assertEquals(listOf("2", "3"), ids("-budget"))
        assertEquals(listOf("1"), ids("notebook:work"))
        assertEquals(listOf("1"), ids("stack:Office"))
        assertEquals(listOf("1"), ids("tag:work"))
        assertEquals(listOf("1", "2"), ids("tag:*"))
        assertEquals(listOf("3"), ids("-tag:*"))
        assertEquals(listOf("1"), ids("todo:true"))
        assertEquals(listOf("2"), ids("todo:false"))
        assertEquals(listOf("1", "2"), ids("todo:*"))
        assertEquals(listOf("3"), ids("resource:image/*"))
        assertEquals(listOf("3"), ids("board"))
        assertEquals(listOf("3"), ids("reminder:true"))
        assertEquals(listOf("3"), ids("source:example.com"))
        assertEquals(listOf("1", "3"), ids("created:day-7"))
        assertEquals(listOf("1"), ids("updated:day"))
        assertEquals(listOf("2"), ids("created:20260101 -created:20260910"))
        assertEquals(listOf("1"), ids("intitle:sync"))
        assertEquals(listOf("1", "2"), ids("any: milk budget"))
        assertEquals(listOf("budget review", "bud"), Search.words("\"budget review\" bud* tag:x -no"))
        assertEquals(LocalDate.of(2026, 10, 5), Search.since("week-1", today))
        assertEquals(LocalDate.of(2026, 9, 1), Search.since("month-1", today))
    }

    @Test
    fun htmlExport() {
        val html = Html.body("# Title\n- a\n- b\n[x] done\n> q\n**b** ==h==\n| A |\n| 1 |", emptyList())
        assertTrue(html, html.contains("<h1>Title</h1>"))
        assertTrue(html, html.contains("<ul>\n<li>a</li>\n<li>b</li>\n</ul>"))
        assertTrue(html, html.contains("☑ done"))
        assertTrue(html, html.contains("<blockquote>q</blockquote>"))
        assertTrue(html, html.contains("<b>b</b> <mark>h</mark>"))
        assertTrue(html, html.contains("<th>A</th>"))
        assertTrue(Html.page(listOf(Note(title = "A <b>"))).contains("<title>A &lt;b&gt;</title>"))
    }

    @Test
    fun enexRoundTrip() {
        val png = byteArrayOf(1, 2, 3, 4, 5)
        val a = Attachment(id = "img", name = "dot.png", mime = "image/png", size = 5)
        val note = Note(
            title = "Trip & plans",
            body = "# Packing\n[x] Passport\n[ ] Charger\n- bullet **bold** <u>under</u>\n1. first\n> quote\n| A | B |\n| --- | --- |\n| 1 | 2 |\n${Markdown.mediaLine(a)}\nLink [site](https://example.com) and ==mark==\n```\ncode <x>\n```",
            tags = listOf("travel", "2026"),
            createdAt = at(today), updatedAt = at(today) + 60_000,
            reminderAt = at(today.plusDays(1)),
            attachments = listOf(a),
            source = "https://example.com",
        )
        val xml = Enex.export(listOf(note), { if (it.id == "img") png else null })
        assertTrue(xml.contains("<en-media type=\"image/png\" hash=\"${Enex.md5(png)}\"/>"))
        val back = Enex.import(xml, "nb").single()
        val n = back.note
        assertEquals("Trip & plans", n.title)
        assertEquals(listOf("travel", "2026"), n.tags)
        assertEquals(note.createdAt, n.createdAt)
        assertEquals(note.updatedAt, n.updatedAt)
        assertEquals(note.reminderAt, n.reminderAt)
        assertEquals("https://example.com", n.source)
        assertEquals("nb", n.notebookId)
        val (att, bytes) = back.files.single()
        assertEquals("dot.png", att.name)
        assertEquals("image/png", att.mime)
        assertTrue(png.contentEquals(bytes))
        val expected = listOf(
            "# Packing", "[x] Passport", "[ ] Charger", "- bullet **bold** <u>under</u>", "1. first", "> quote",
            "| A | B |", "| --- | --- |", "| 1 | 2 |", Markdown.mediaLine(att), "Link [site](https://example.com) and ==mark==",
            "```", "code <x>", "```",
        )
        assertEquals(expected.joinToString("\n"), n.body)
    }

    @Test
    fun readsWhatEvernoteWrites() {
        val img = byteArrayOf(9, 8, 7)
        val hash = Enex.md5(img)
        val data = java.util.Base64.getEncoder().encodeToString(img)
        val enex = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE en-export SYSTEM "http://xml.evernote.com/pub/evernote-export4.dtd">
            <en-export export-date="20261001T120000Z" application="Evernote" version="10.0">
              <note>
                <title>Shopping</title>
                <created>20261001T090000Z</created>
                <tag>home</tag>
                <content>
                  <![CDATA[<?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <!DOCTYPE en-note SYSTEM "http://xml.evernote.com/pub/enml2.dtd">
            <en-note><div>Buy&nbsp;these:</div><ul style="--en-todo:true;"><li style="--en-checked:true;"><div>Milk</div></li><li style="--en-checked:false;"><div>Eggs</div></li></ul>
            <div><span style="font-weight: bold;">Note</span> — <a href="https://shop.example">shop</a></div><div><br/></div>
            <ol><li><div>one</div><ul><li><div>sub</div></li></ul></li><li><div>two</div></li></ol>
            <div style="--en-codeblock:true;"><div>let x = 1</div><div>let y = 2</div></div>
            <en-media hash="$hash" type="image/png"/><div><en-crypt>xyz</en-crypt></div></en-note>]]>
                </content>
                <resource><data encoding="base64">$data</data><mime>image/png</mime></resource>
              </note>
            </en-export>
        """.trimIndent()
        val back = Enex.import(enex, "nb").single()
        val att = back.files.single().first
        assertEquals("Attachment 1.png", att.name)
        assertEquals(
            listOf(
                "Buy these:", "[x] Milk", "[ ] Eggs", "**Note** — [shop](https://shop.example)", "",
                "1. one", "  - sub", "2. two", "```", "let x = 1", "let y = 2", "```", Markdown.mediaLine(att),
                "(Encrypted text from Evernote can't be shown here.)",
            ).joinToString("\n"),
            back.note.body,
        )
    }

    @Test
    fun templatesFillInDates() {
        val (title, body) = Templates.fill(Templates.builtIn.first { it.name == "Meeting notes" }, today, Locale.ENGLISH)
        assertEquals("Meeting: Monday 12 October 2026", title)
        assertTrue(body.startsWith("**Date:** Monday 12 October 2026"))
        assertEquals("Week of 12 October 2026", Templates.fill(Templates.builtIn.first { it.id == "tpl-weekly" }, today.plusDays(3), Locale.ENGLISH).first)
    }

    @Test
    fun everythingNewSurvivesSaving() {
        val a = Attachment(name = "x.pdf", mime = "application/pdf", size = 10, createdAt = 3)
        val d = Data(
            notes = listOf(Note(title = "n", body = "b", reminderAt = 9, reminderDone = 10, attachments = listOf(a), history = listOf(Version(1, "t", "b")), source = "s")),
            notebooks = listOf(Notebook.Default, Notebook(name = "In stack", stack = "S")),
            settings = Settings(listStyle = ListStyle.CARDS, defaultNotebookId = "x", homeSections = listOf(HomeSection.RECENT), scratchPad = "jot", passcode = "h", lockAfterMinutes = 5, fingerprint = false),
            tagInfo = listOf(TagInfo("a", "b")),
            searches = listOf(SavedSearch(name = "Open", query = "todo:true")),
            shortcuts = listOf(Shortcut(ShortcutKind.TAG, "a")),
            templates = listOf(Template(name = "Mine", body = "x")),
        )
        assertEquals(d, Data.fromJson(JSONObject(d.toJson().toString())))
    }
}
