package io.github.akrishna87.jotter

import io.github.akrishna87.jotter.model.Block
import io.github.akrishna87.jotter.model.Markdown
import io.github.akrishna87.jotter.model.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownTest {
    @Test
    fun blocks() {
        val blocks = Markdown.parse("# Title\n- one\n2. two\n[ ] todo\n[x] done\n- [ ] github style\n---\n\nplain")
        assertEquals(Block.Heading(0, 1, "Title"), blocks[0])
        assertEquals(Block.Bullet(1, "one"), blocks[1])
        assertEquals(Block.Numbered(2, 2, "two"), blocks[2])
        assertEquals(Block.Check(3, false, "todo"), blocks[3])
        assertEquals(Block.Check(4, true, "done"), blocks[4])
        assertEquals(Block.Check(5, false, "github style"), blocks[5])
        assertEquals(Block.Divider(6), blocks[6])
        assertEquals(Block.Blank(7), blocks[7])
        assertEquals(Block.Paragraph(8, "plain"), blocks[8])
    }

    @Test
    fun ticking() {
        assertEquals("a\n[x] b\nc", Markdown.toggleCheck("a\n[ ] b\nc", 1))
        assertEquals("[ ] b", Markdown.toggleCheck("[x] b", 0))
        assertEquals("- [x] b", Markdown.toggleCheck("- [ ] b", 0))
        assertEquals("not a check", Markdown.toggleCheck("not a check", 0))
        assertEquals(1 to 3, Markdown.checklist("[x] a\n[ ] b\n[ ] c\ntext"))
        assertNull(Markdown.checklist("no list"))
    }

    @Test
    fun inlineStyles() {
        assertEquals(listOf(Span("a "), Span("bold", bold = true), Span(" b")), Markdown.inline("a **bold** b"))
        assertEquals(listOf(Span("x "), Span("it", italic = true)), Markdown.inline("x *it*"))
        assertEquals(listOf(Span("gone", strike = true)), Markdown.inline("~~gone~~"))
        // An unclosed mark is just text, and so is a lone asterisk before a space.
        assertEquals(listOf(Span("2 * 3 **no")), Markdown.inline("2 * 3 **no"))
    }

    @Test
    fun plainPreview() {
        assertEquals("Shopping\n☐ milk\n☑ eggs\n• one", Markdown.plain("# Shopping\n[ ] milk\n[x] eggs\n- one"))
        assertEquals("Shopping\n☐ milk", Markdown.plain("# Shopping\n\n[ ] milk"))
        assertEquals("Note with bold", Markdown.plain("Note with **bold**"))
    }

    @Test
    fun listsContinueOnEnter() {
        // Enter after a checklist item starts a new unticked one.
        val old = "[x] milk"
        assertEquals("[x] milk\n[ ] " to 13, Markdown.continueList(old, "[x] milk\n", 9))
        assertEquals("- a\n- " to 6, Markdown.continueList("- a", "- a\n", 4))
        assertEquals("3. c\n4. " to 8, Markdown.continueList("3. c", "3. c\n", 5))
        // Enter on an empty item ends the list.
        assertEquals("- a\n" to 4, Markdown.continueList("- a\n- ", "- a\n- \n", 7))
        // Plain lines and other edits are left alone.
        assertNull(Markdown.continueList("plain", "plain\n", 6))
        assertNull(Markdown.continueList("- a", "- ab", 4))
    }

    @Test
    fun toolbar() {
        assertEquals("[ ] milk" to 6, Markdown.toggleLinePrefix("milk", 2, "[ ] "))
        assertEquals("milk" to 2, Markdown.toggleLinePrefix("[ ] milk", 6, "[ ] "))
        assertEquals("a\n- b" to 5, Markdown.toggleLinePrefix("a\n[ ] b", 7, "- "))
        assertEquals("## Title" to 8, Markdown.toggleLinePrefix("Title", 5, "## "))
        assertEquals(Triple("a **b** c", 4, 5), Markdown.wrap("a b c", 2, 3, "**"))
        assertEquals(Triple("****", 2, 2), Markdown.wrap("", 0, 0, "**"))
    }

    @Test
    fun selectedLinesBecomeAList() {
        val text = "Shopping\nMilk\nEggs\n\nBread\nDone"
        val s = text.indexOf("Milk")
        val e = text.indexOf("Bread") + 2 // part-way into the line still takes it in
        val (checks, cs, ce) = Markdown.toggleLinesPrefix(text, s, e, "[ ] ")
        assertEquals("Shopping\n[ ] Milk\n[ ] Eggs\n\n[ ] Bread\nDone", checks)
        assertEquals("[ ] Milk\n[ ] Eggs\n\n[ ] Bread", checks.substring(cs, ce))
        // A checklist turned into a numbered list counts up, skipping the blank line.
        val (numbered, ns, ne) = Markdown.toggleLinesPrefix(checks, cs, ce, "1. ")
        assertEquals("Shopping\n1. Milk\n2. Eggs\n\n3. Bread\nDone", numbered)
        // Asking again for the kind they all have takes it away.
        assertEquals(text, Markdown.toggleLinesPrefix(numbered, ns, ne, "1. ").first)
    }

    @Test
    fun selectionEndingAtALineStartLeavesThatLine() {
        val text = "one\ntwo\nthree"
        val (t, _, _) = Markdown.toggleLinesPrefix(text, 0, text.indexOf("three"), "- ")
        assertEquals("- one\n- two\nthree", t)
    }

    @Test
    fun mixedLinesAllGetTheMarker() {
        val (t, _, _) = Markdown.toggleLinesPrefix("[ ] a\nb\n  - c", 0, 13, "[ ] ")
        assertEquals("[ ] a\n[ ] b\n  [ ] c", t)
    }

    @Test
    fun noSelectionWorksOnTheCursorLine() {
        val (t, s, e) = Markdown.toggleLinesPrefix("a\nb", 2, 2, "[ ] ")
        assertEquals("a\n[ ] b", t)
        assertEquals(s, e)
    }
}
