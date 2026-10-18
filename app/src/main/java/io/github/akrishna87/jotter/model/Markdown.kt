package io.github.akrishna87.jotter.model

/** One line (or, for code and tables, a run of lines) of a note, as the note view draws it. [line] is its first line number. */
sealed interface Block {
    val line: Int

    data class Heading(override val line: Int, val level: Int, val text: String) : Block
    data class Bullet(override val line: Int, val text: String, val indent: Int = 0) : Block
    data class Numbered(override val line: Int, val number: Int, val text: String, val indent: Int = 0) : Block
    data class Check(override val line: Int, val checked: Boolean, val text: String, val indent: Int = 0) : Block
    data class Quote(override val line: Int, val text: String) : Block
    /** A fenced code block, from the opening ``` on [line] to the closing one on [endLine]. */
    data class Code(override val line: Int, val endLine: Int, val text: String, val language: String = "") : Block
    /** A table: the first row is its header. [endLine] is its last line. */
    data class Table(override val line: Int, val endLine: Int, val rows: List<List<String>>) : Block
    /** An attachment or image on a line of its own: `![name](attachment:id)` or `![name](https://…)`. */
    data class Media(override val line: Int, val name: String, val target: String) : Block {
        val attachmentId: String? get() = target.removePrefix("attachment:").takeIf { target.startsWith("attachment:") }
    }
    data class Paragraph(override val line: Int, val text: String) : Block
    data class Divider(override val line: Int) : Block
    data class Blank(override val line: Int) : Block
}

/** A run of text with its inline styles. [link] is a web address, `note:<id>`, or `title:<note title>` for a `[[link]]`. */
data class Span(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val underline: Boolean = false,
    val highlight: Boolean = false,
    val code: Boolean = false,
    val link: String? = null,
)

/**
 * The Markdown notes are written in, and the editing help around it. Plain Kotlin, so it is unit tested.
 *
 * Lines: `#`, `##`, `###` headings; `- ` bullets; `1. ` numbered; `[ ] `/`[x] ` checklists (indent any list
 * with two spaces); `> ` quotes; ``` code blocks; `| a | b |` tables; `---` dividers; and
 * `![name](attachment:id)` attachments. Inline: `**bold**`, `*italic*`, `<u>underline</u>`, `~~strike~~`,
 * `==highlight==`, `` `code` ``, `[text](https://…)`, `[[Note title]]` and `[text](note:id)` note links, and
 * bare web addresses.
 */
object Markdown {
    private val checkRegex = Regex("^(\\s*)(?:[-*] )?\\[( |x|X)] ?(.*)$")
    private val bulletRegex = Regex("^(\\s*)[-*•] (.*)$")
    private val numberedRegex = Regex("^(\\s*)(\\d{1,3})[.)] (.*)$")
    private val headingRegex = Regex("^(#{1,3}) (.*)$")
    private val quoteRegex = Regex("^> ?(.*)$")
    private val mediaRegex = Regex("^!\\[([^\\]]*)]\\(([^)\\s]+)\\)$")
    private val tableSeparator = Regex("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?$")

    private fun indentOf(spaces: String): Int = (spaces.replace("\t", "  ").length / 2).coerceAtMost(4)

    private fun isTableRow(line: String): Boolean = line.trim().let { it.length > 1 && it.startsWith("|") && it.endsWith("|") }

    private fun cells(line: String): List<String> = line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

    fun parse(body: String): List<Block> {
        val lines = body.split('\n')
        val out = ArrayList<Block>()
        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trimEnd()
            // Code: everything up to the closing fence (or the end) as it is.
            if (line.trimStart().startsWith("```")) {
                val language = line.trimStart().removePrefix("```").trim()
                var j = i + 1
                while (j < lines.size && !lines[j].trimStart().startsWith("```")) j++
                out += Block.Code(i, minOf(j, lines.lastIndex), lines.subList(i + 1, minOf(j, lines.size)).joinToString("\n"), language)
                i = j + 1
                continue
            }
            // Tables: two or more rows in a row, the separator under the header dropped.
            if (isTableRow(line) && i + 1 < lines.size && isTableRow(lines[i + 1].trimEnd())) {
                var j = i
                val rows = ArrayList<List<String>>()
                while (j < lines.size && isTableRow(lines[j].trimEnd())) {
                    if (!tableSeparator.matches(lines[j].trim())) rows += cells(lines[j])
                    j++
                }
                val width = rows.maxOf { it.size }
                out += Block.Table(i, j - 1, rows.map { r -> r + List(width - r.size) { "" } })
                i = j
                continue
            }
            out += parseLine(i, line)
            i++
        }
        return out
    }

    private fun parseLine(i: Int, line: String): Block {
        checkRegex.matchEntire(line)?.let { return Block.Check(i, it.groupValues[2] != " ", it.groupValues[3], indentOf(it.groupValues[1])) }
        headingRegex.matchEntire(line)?.let { return Block.Heading(i, it.groupValues[1].length, it.groupValues[2]) }
        bulletRegex.matchEntire(line)?.let { return Block.Bullet(i, it.groupValues[2], indentOf(it.groupValues[1])) }
        numberedRegex.matchEntire(line)?.let { return Block.Numbered(i, it.groupValues[2].toInt(), it.groupValues[3], indentOf(it.groupValues[1])) }
        quoteRegex.matchEntire(line)?.let { return Block.Quote(i, it.groupValues[1]) }
        mediaRegex.matchEntire(line.trim())?.let { return Block.Media(i, it.groupValues[1], it.groupValues[2]) }
        return when {
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
        val indent = m.groupValues[1]
        val rest = l.substring(indent.length)
        val bullet = if (rest.startsWith("- ") || rest.startsWith("* ")) rest.substring(0, 2) else ""
        lines[line] = indent + bullet + (if (m.groupValues[2] == " ") "[x] " else "[ ] ") + m.groupValues[3]
        return lines.joinToString("\n")
    }

    /** "2/5" style progress over a note's checklist, or null if it has none. */
    fun checklist(body: String): Pair<Int, Int>? {
        val checks = parse(body).filterIsInstance<Block.Check>()
        if (checks.isEmpty()) return null
        return checks.count { it.checked } to checks.size
    }

    /** The checklist items not yet ticked, for the open checklists on Home. */
    fun openChecks(body: String): List<Block.Check> = parse(body).filterIsInstance<Block.Check>().filter { !it.checked && it.text.isNotBlank() }

    /** The note as plain text for a preview or a search: Markdown marks taken out, blank lines dropped. */
    fun plain(body: String, maxChars: Int = 240): String {
        val text = parse(body).mapNotNull { b ->
            when (b) {
                is Block.Heading -> b.text
                is Block.Bullet -> "• " + b.text
                is Block.Numbered -> "${b.number}. ${b.text}"
                is Block.Check -> (if (b.checked) "☑ " else "☐ ") + b.text
                is Block.Quote -> b.text
                is Block.Paragraph -> b.text
                is Block.Code -> return@mapNotNull b.text
                is Block.Table -> return@mapNotNull b.rows.joinToString("\n") { it.joinToString("  ") { c -> spansText(c) } }
                is Block.Media -> return@mapNotNull "[${b.name}]"
                is Block.Divider, is Block.Blank -> null
            }?.let { spansText(it) }
        }.joinToString("\n")
        return if (text.length > maxChars) text.take(maxChars).trimEnd() + "…" else text
    }

    private fun spansText(line: String): String = inline(line).joinToString("") { it.text }

    /** Words in the note, for the note info. */
    fun wordCount(body: String): Int = plain(body, Int.MAX_VALUE).split(Regex("\\s+")).count { w -> w.any { it.isLetterOrDigit() } }

    private val urlRegex = Regex("https?://[^\\s<>()\\[\\]]+[^\\s<>()\\[\\].,;:!?'\"]")

    /**
     * Splits a line into runs for **bold**, *italic* (or _italic_), <u>underline</u>, ~~strike~~,
     * ==highlight==, `code`, links and note links. Unclosed marks stay as text.
     */
    fun inline(text: String): List<Span> {
        val out = ArrayList<Span>()
        val buf = StringBuilder()
        var bold = false
        var italic = false
        var strike = false
        var underline = false
        var highlight = false
        fun style(t: String, code: Boolean = false, link: String? = null) =
            Span(t, bold, italic, strike, underline, highlight, code, link)
        fun flush() {
            if (buf.isNotEmpty()) out += style(buf.toString())
            buf.clear()
        }
        fun closes(mark: String, from: Int) = text.indexOf(mark, from) >= 0
        var i = 0
        while (i < text.length) {
            // Web addresses on their own become links.
            if ((text.startsWith("http://", i) || text.startsWith("https://", i)) && (i == 0 || !text[i - 1].isLetterOrDigit())) {
                val m = urlRegex.matchAt(text, i)
                if (m != null) {
                    flush(); out += style(m.value, link = m.value); i += m.value.length
                    continue
                }
            }
            when {
                text.startsWith("[[", i) && text.indexOf("]]", i + 2) > i + 2 -> {
                    val end = text.indexOf("]]", i + 2)
                    val title = text.substring(i + 2, end)
                    flush(); out += style(title, link = "title:$title"); i = end + 2
                }
                text[i] == '[' && linkAt(text, i) != null -> {
                    val (label, target, end) = linkAt(text, i)!!
                    flush()
                    for (s in inline(label)) out += s.copy(bold = s.bold || bold, italic = s.italic || italic, strike = s.strike || strike, underline = s.underline || underline, highlight = s.highlight || highlight, link = target)
                    i = end
                }
                text[i] == '`' && text.indexOf('`', i + 1) > i -> {
                    val end = text.indexOf('`', i + 1)
                    flush(); out += style(text.substring(i + 1, end), code = true); i = end + 1
                }
                text.startsWith("**", i) -> {
                    if (bold || closes("**", i + 2)) { flush(); bold = !bold } else buf.append("**")
                    i += 2
                }
                text.startsWith("~~", i) -> {
                    if (strike || closes("~~", i + 2)) { flush(); strike = !strike } else buf.append("~~")
                    i += 2
                }
                text.startsWith("==", i) -> {
                    if (highlight || closes("==", i + 2)) { flush(); highlight = !highlight } else buf.append("==")
                    i += 2
                }
                text.startsWith("<u>", i) && (underline || closes("</u>", i + 3)) -> { flush(); underline = true; i += 3 }
                text.startsWith("</u>", i) && underline -> { flush(); underline = false; i += 4 }
                (text[i] == '*' || text[i] == '_') && (italic || closes(text[i].toString(), i + 1)) &&
                    (italic || (i + 1 < text.length && text[i + 1] != ' ')) &&
                    // Underscores inside words (snake_case) aren't italics.
                    !(text[i] == '_' && !italic && i > 0 && text[i - 1].isLetterOrDigit()) -> { flush(); italic = !italic; i += 1 }
                else -> { buf.append(text[i]); i += 1 }
            }
        }
        flush()
        return out
    }

    /** A `[label](target)` link starting at [i]: its label, target and where it ends. */
    private fun linkAt(text: String, i: Int): Triple<String, String, Int>? {
        if (i > 0 && text[i - 1] == '!') return null
        val close = text.indexOf(']', i + 1).takeIf { it > i } ?: return null
        if (close + 1 >= text.length || text[close + 1] != '(') return null
        val end = text.indexOf(')', close + 2).takeIf { it > close } ?: return null
        val target = text.substring(close + 2, end).trim()
        if (target.isEmpty() || target.contains(' ')) return null
        return Triple(text.substring(i + 1, close), target, end + 1)
    }

    /** The list marker at the start of a line, with its indent ("  - ", "[ ] ", "3. "), or "" if it isn't a list item. */
    fun listPrefix(line: String): String {
        Regex("^\\s*(?:[-*] )?\\[[ xX]] ").find(line)?.let { return it.value }
        Regex("^\\s*[-*•] ").find(line)?.let { return it.value }
        Regex("^\\s*\\d{1,3}[.)] ").find(line)?.let { return it.value }
        Regex("^> ").find(line)?.let { return it.value }
        return ""
    }

    /**
     * Keeps lists going while typing. When a new line is typed after a list item, the new line gets
     * the next marker ("[ ] ", "- " or the next number) at the same indent. Pressing enter on an empty
     * item ends the list. Returns the text and cursor to use instead, or null to keep the edit as typed.
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
        val indent = prefix.takeWhile { it == ' ' || it == '\t' }
        val marker = prefix.substring(indent.length)
        val nextMarker = when {
            marker.contains('[') -> marker.replace(Regex("\\[[xX]]"), "[ ]")
            marker[0].isDigit() -> {
                val number = marker.takeWhile { it.isDigit() }.toInt()
                "${number + 1}${marker.dropWhile { it.isDigit() }}"
            }
            else -> marker
        }
        val text = new.substring(0, cursor) + indent + nextMarker + new.substring(cursor)
        return text to cursor + indent.length + nextMarker.length
    }

    /**
     * Gives the line under the cursor the marker [prefix] ("[ ] ", "- ", "1. ", "## ", "> "), or takes it
     * away if it already has it. A list item keeps its indent. Returns the new text and cursor.
     */
    fun toggleLinePrefix(text: String, cursor: Int, prefix: String): Pair<String, Int> {
        val c = cursor.coerceIn(0, text.length)
        val start = text.lastIndexOf('\n', c - 1) + 1
        val end = text.indexOf('\n', c).let { if (it < 0) text.length else it }
        val line = text.substring(start, end)
        val existing = listPrefix(line).ifEmpty { headingRegex.find(line)?.let { line.substring(0, it.groupValues[1].length + 1) } ?: "" }
        val indent = existing.takeWhile { it == ' ' || it == '\t' }
        val samePrefix = existing.isNotEmpty() && kind(existing.trimStart()) == kind(prefix) &&
            (kind(prefix) != 'h' || existing == prefix)
        val keepIndent = if (kind(prefix) in "cbn") indent else ""
        val newLine = if (samePrefix) indent + line.substring(existing.length) else keepIndent + prefix + line.substring(existing.length)
        val newText = text.substring(0, start) + newLine + text.substring(end)
        val newCursor = (c + newLine.length - line.length).coerceIn(start, start + newLine.length)
        return newText to newCursor
    }

    private fun kind(prefix: String): Char = when {
        prefix.contains('[') -> 'c'
        prefix.startsWith("#") -> 'h'
        prefix.startsWith(">") -> 'q'
        prefix[0].isDigit() -> 'n'
        else -> 'b'
    }

    /** Indents (or with [by] negative, outdents) the list item under the cursor by one level. */
    fun indent(text: String, cursor: Int, by: Int = 1): Pair<String, Int> {
        val c = cursor.coerceIn(0, text.length)
        val start = text.lastIndexOf('\n', c - 1) + 1
        val end = text.indexOf('\n', c).let { if (it < 0) text.length else it }
        val line = text.substring(start, end)
        val newLine = if (by > 0) "  $line" else line.removePrefix("  ").let { if (it == line) line.removePrefix("\t") else it }
        val newText = text.substring(0, start) + newLine + text.substring(end)
        return newText to (c + newLine.length - line.length).coerceIn(start, start + newLine.length)
    }

    /** Wraps the selection in a pair of marks like "**", or inserts them with the cursor between. */
    fun wrap(text: String, start: Int, end: Int, mark: String, close: String = mark): Triple<String, Int, Int> {
        val s = minOf(start, end).coerceIn(0, text.length)
        val e = maxOf(start, end).coerceIn(0, text.length)
        val newText = text.substring(0, s) + mark + text.substring(s, e) + close + text.substring(e)
        return Triple(newText, s + mark.length, e + mark.length)
    }

    /** Puts [block] on lines of its own at the cursor, and returns the text and the cursor after it. */
    fun insertBlock(text: String, cursor: Int, block: String): Pair<String, Int> {
        val c = cursor.coerceIn(0, text.length)
        val before = if (c > 0 && text[c - 1] != '\n') "\n" else ""
        val after = if (c < text.length && text[c] == '\n') "" else "\n"
        val insert = before + block + after
        return text.substring(0, c) + insert + text.substring(c) to c + insert.length
    }

    /** An empty table to start from. */
    fun table(columns: Int, rows: Int): String {
        val header = (1..columns).joinToString(" | ", "| ", " |") { "Column $it" }
        val sep = (1..columns).joinToString(" | ", "| ", " |") { "---" }
        val row = (1..columns).joinToString(" | ", "| ", " |") { " " }
        return (listOf(header, sep) + List(rows) { row }).joinToString("\n")
    }

    /** The line that shows an attachment. */
    fun mediaLine(a: Attachment): String = "![${a.name.replace("]", "")}](attachment:${a.id})"
}
