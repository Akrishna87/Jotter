package io.github.akrishna87.jotter.model

/** One line of a note, as the note view draws it. [line] is its line number in the note's text. */
sealed interface Block {
    val line: Int

    data class Heading(override val line: Int, val level: Int, val text: String) : Block
    data class Bullet(override val line: Int, val text: String) : Block
    data class Numbered(override val line: Int, val number: Int, val text: String) : Block
    data class Check(override val line: Int, val checked: Boolean, val text: String) : Block
    data class Paragraph(override val line: Int, val text: String) : Block
    data class Divider(override val line: Int) : Block
    data class Blank(override val line: Int) : Block
}

/** A run of text with its inline styles. */
data class Span(val text: String, val bold: Boolean = false, val italic: Boolean = false, val strike: Boolean = false)

/** The small Markdown dialect notes are written in, and the editing help around it. Plain Kotlin, so it is unit tested. */
object Markdown {
    private val checkRegex = Regex("^(?:[-*] )?\\[( |x|X)] ?(.*)$")
    private val bulletRegex = Regex("^[-*•] (.*)$")
    private val numberedRegex = Regex("^(\\d{1,3})[.)] (.*)$")
    private val headingRegex = Regex("^(#{1,3}) (.*)$")

    fun parse(body: String): List<Block> = body.split('\n').mapIndexed { i, raw ->
        val line = raw.trimEnd()
        checkRegex.matchEntire(line)?.let { return@mapIndexed Block.Check(i, it.groupValues[1] != " ", it.groupValues[2]) }
        headingRegex.matchEntire(line)?.let { return@mapIndexed Block.Heading(i, it.groupValues[1].length, it.groupValues[2]) }
        bulletRegex.matchEntire(line)?.let { return@mapIndexed Block.Bullet(i, it.groupValues[1]) }
        numberedRegex.matchEntire(line)?.let { return@mapIndexed Block.Numbered(i, it.groupValues[1].toInt(), it.groupValues[2]) }
        when {
            line == "---" || line == "***" -> Block.Divider(i)
            line.isBlank() -> Block.Blank(i)
            else -> Block.Paragraph(i, line)
        }
    }

    /** Ticks or unticks the checklist item on [line]. */
    fun toggleCheck(body: String, line: Int): String {
        val lines = body.split('\n').toMutableList()
        val l = lines.getOrNull(line) ?: return body
        val m = checkRegex.matchEntire(l.trimEnd()) ?: return body
        val bullet = if (l.startsWith("- ") || l.startsWith("* ")) l.substring(0, 2) else ""
        lines[line] = bullet + (if (m.groupValues[1] == " ") "[x] " else "[ ] ") + m.groupValues[2]
        return lines.joinToString("\n")
    }

    /** "2/5" style progress over a note's checklist, or null if it has none. */
    fun checklist(body: String): Pair<Int, Int>? {
        val checks = parse(body).filterIsInstance<Block.Check>()
        if (checks.isEmpty()) return null
        return checks.count { it.checked } to checks.size
    }

    /** The note as plain text for a preview: Markdown marks taken out, blank lines dropped. */
    fun plain(body: String, maxChars: Int = 240): String {
        val text = parse(body).mapNotNull { b ->
            when (b) {
                is Block.Heading -> b.text
                is Block.Bullet -> "• " + b.text
                is Block.Numbered -> "${b.number}. ${b.text}"
                is Block.Check -> (if (b.checked) "☑ " else "☐ ") + b.text
                is Block.Paragraph -> b.text
                is Block.Divider, is Block.Blank -> null
            }
        }.joinToString("\n") { line -> inline(line).joinToString("") { it.text } }
        return if (text.length > maxChars) text.take(maxChars).trimEnd() + "…" else text
    }

    /** Splits a line into runs for **bold**, *italic* (or _italic_) and ~~strikethrough~~. Unclosed marks stay as text. */
    fun inline(text: String): List<Span> {
        val out = ArrayList<Span>()
        val buf = StringBuilder()
        var bold = false
        var italic = false
        var strike = false
        fun flush() {
            if (buf.isNotEmpty()) out += Span(buf.toString(), bold, italic, strike)
            buf.clear()
        }
        fun closes(mark: String, from: Int) = text.indexOf(mark, from) >= 0
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    if (bold || closes("**", i + 2)) { flush(); bold = !bold } else buf.append("**")
                    i += 2
                }
                text.startsWith("~~", i) -> {
                    if (strike || closes("~~", i + 2)) { flush(); strike = !strike } else buf.append("~~")
                    i += 2
                }
                (text[i] == '*' || text[i] == '_') && (italic || closes(text[i].toString(), i + 1)) &&
                    (italic || (i + 1 < text.length && text[i + 1] != ' ')) -> { flush(); italic = !italic; i += 1 }
                else -> { buf.append(text[i]); i += 1 }
            }
        }
        flush()
        return out
    }

    /** The list marker at the start of a line ("- ", "[ ] ", "3. "), or "" if it isn't a list item. */
    fun listPrefix(line: String): String {
        Regex("^(?:[-*] )?\\[[ xX]] ").find(line)?.let { return it.value }
        Regex("^[-*•] ").find(line)?.let { return it.value }
        Regex("^\\d{1,3}[.)] ").find(line)?.let { return it.value }
        return ""
    }

    /**
     * Keeps lists going while typing. When a new line is typed after a list item, the new line gets
     * the next marker ("[ ] ", "- " or the next number). Pressing enter on an empty item ends the list.
     * Returns the text and cursor to use instead, or null to keep the edit as typed.
     */
    fun continueList(old: String, new: String, cursor: Int): Pair<String, Int>? {
        if (new.length != old.length + 1 || cursor < 1 || cursor > new.length || new[cursor - 1] != '\n') return null
        if (old.substring(0, cursor - 1) != new.substring(0, cursor - 1) || old.substring(cursor - 1) != new.substring(cursor)) return null
        val lineStart = old.lastIndexOf('\n', cursor - 2) + 1
        val line = old.substring(lineStart, cursor - 1)
        val prefix = listPrefix(line)
        if (prefix.isEmpty()) return null
        if (line.substring(prefix.length).isBlank()) {
            // Enter on an empty item: drop its marker instead of starting another.
            val text = old.substring(0, lineStart) + old.substring(cursor - 1)
            return text to lineStart
        }
        val nextPrefix = when {
            prefix.contains('[') -> prefix.replace(Regex("\\[[xX]]"), "[ ]")
            prefix[0].isDigit() -> {
                val number = prefix.takeWhile { it.isDigit() }.toInt()
                "${number + 1}${prefix.dropWhile { it.isDigit() }}"
            }
            else -> prefix
        }
        val text = new.substring(0, cursor) + nextPrefix + new.substring(cursor)
        return text to cursor + nextPrefix.length
    }

    /**
     * Gives the line under the cursor the list marker [prefix] ("[ ] ", "- ", "1. ", "# "), or takes it
     * away if it already has it. Returns the new text and cursor.
     */
    fun toggleLinePrefix(text: String, cursor: Int, prefix: String): Pair<String, Int> {
        val c = cursor.coerceIn(0, text.length)
        val start = text.lastIndexOf('\n', c - 1) + 1
        val end = text.indexOf('\n', c).let { if (it < 0) text.length else it }
        val line = text.substring(start, end)
        val existing = listPrefix(line).ifEmpty { headingRegex.find(line)?.let { line.substring(0, it.groupValues[1].length + 1) } ?: "" }
        val samePrefix = existing.isNotEmpty() && kind(existing) == kind(prefix)
        val newLine = if (samePrefix) line.substring(existing.length) else prefix + line.substring(existing.length)
        val newText = text.substring(0, start) + newLine + text.substring(end)
        val newCursor = (c + newLine.length - line.length).coerceIn(start, start + newLine.length)
        return newText to newCursor
    }

    private fun kind(prefix: String): Char = when {
        prefix.contains('[') -> 'c'
        prefix.startsWith("#") -> 'h'
        prefix[0].isDigit() -> 'n'
        else -> 'b'
    }

    /** Wraps the selection in a mark like "**", or inserts a pair of marks with the cursor between them. */
    fun wrap(text: String, start: Int, end: Int, mark: String): Triple<String, Int, Int> {
        val s = minOf(start, end).coerceIn(0, text.length)
        val e = maxOf(start, end).coerceIn(0, text.length)
        val newText = text.substring(0, s) + mark + text.substring(s, e) + mark + text.substring(e)
        return Triple(newText, s + mark.length, e + mark.length)
    }
}
