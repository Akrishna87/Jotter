package io.github.akrishna87.jotter.model

import org.w3c.dom.Element
import org.w3c.dom.Node as DomNode
import java.io.StringReader
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/** Evernote's export format (.enex): notes, their tags, reminders and attachments, with the text in ENML. */
object Enex {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    /** A note read from an .enex file, with the files of its attachments. */
    data class Imported(val note: Note, val files: List<Pair<Attachment, ByteArray>>)

    fun md5(bytes: ByteArray): String = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    /** [notes] as an .enex file. [bytes] reads an attachment's file; attachments it can't read are left out. */
    fun export(notes: List<Note>, bytes: (Attachment) -> ByteArray?, now: Long = System.currentTimeMillis()): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<!DOCTYPE en-export SYSTEM \"http://xml.evernote.com/pub/evernote-export4.dtd\">\n")
        append("<en-export export-date=\"${stamp.format(Instant.ofEpochMilli(now))}\" application=\"Jotter\" version=\"1\">\n")
        for (n in notes) {
            val files = n.attachments.mapNotNull { a -> bytes(a)?.let { a to it } }
            val hashes = files.associate { (a, b) -> a.id to md5(b) }
            val enml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" +
                "<!DOCTYPE en-note SYSTEM \"http://xml.evernote.com/pub/enml2.dtd\">" +
                "<en-note>" + Html.body(n.body, n.attachments, { hashes[it.id] }, enml = true) + "</en-note>"
            append("<note>\n")
            append("<title>").append(Html.escape(n.title)).append("</title>\n")
            append("<created>").append(stamp.format(Instant.ofEpochMilli(n.createdAt))).append("</created>\n")
            append("<updated>").append(stamp.format(Instant.ofEpochMilli(n.updatedAt))).append("</updated>\n")
            for (t in n.tags) append("<tag>").append(Html.escape(t)).append("</tag>\n")
            append("<note-attributes>")
            if (n.source.isNotBlank()) append("<source-url>").append(Html.escape(n.source)).append("</source-url>")
            n.reminderAt?.let { append("<reminder-order>").append(it).append("</reminder-order><reminder-time>").append(stamp.format(Instant.ofEpochMilli(it))).append("</reminder-time>") }
            if (n.reminderDone != 0L) append("<reminder-done-time>").append(stamp.format(Instant.ofEpochMilli(n.reminderDone))).append("</reminder-done-time>")
            append("</note-attributes>\n")
            append("<content><![CDATA[").append(enml.replace("]]>", "]]]]><![CDATA[>")).append("]]></content>\n")
            for ((a, b) in files) {
                append("<resource><data encoding=\"base64\">")
                append(Base64.getMimeEncoder(76, "\n".toByteArray()).encodeToString(b))
                append("</data><mime>").append(Html.escape(a.mime)).append("</mime>")
                append("<resource-attributes><file-name>").append(Html.escape(a.name)).append("</file-name></resource-attributes></resource>\n")
            }
            append("</note>\n")
        }
        append("</en-export>\n")
    }

    /** Reads an .enex file into notes for [notebookId]. Throws if it isn't one. */
    fun import(xml: String, notebookId: String): List<Imported> {
        val doc = parseXml(xml)
        val notes = doc.getElementsByTagName("note")
        return List(notes.length) { i -> readNote(notes.item(i) as Element, notebookId) }
    }

    private fun readNote(e: Element, notebookId: String): Imported {
        fun text(parent: Element, name: String): String? = parent.getElementsByTagName(name).let { if (it.length > 0) it.item(0).textContent else null }
        fun time(s: String?): Long? = s?.trim()?.let { runCatching { Instant.from(stamp.parse(it)).toEpochMilli() }.getOrNull() }
        val files = ArrayList<Pair<Attachment, ByteArray>>()
        val byHash = HashMap<String, Attachment>()
        val resources = e.getElementsByTagName("resource")
        for (i in 0 until resources.length) {
            val r = resources.item(i) as Element
            val data = text(r, "data") ?: continue
            val bytes = runCatching { Base64.getMimeDecoder().decode(data.trim()) }.getOrNull() ?: continue
            val mime = text(r, "mime")?.trim().orEmpty().ifEmpty { "application/octet-stream" }
            val name = text(r, "file-name")?.trim().orEmpty().ifEmpty { "Attachment ${i + 1}" + extension(mime) }
            val a = Attachment(name = name, mime = mime, size = bytes.size.toLong())
            files += a to bytes
            byHash[md5(bytes)] = a
        }
        val created = time(text(e, "created")) ?: System.currentTimeMillis()
        val content = text(e, "content").orEmpty()
        var body = runCatching { enmlToMarkdown(content, byHash) }.getOrElse { stripTags(content) }
        // Attachments the text doesn't show go at the end, so none are lost.
        val shown = Markdown.parse(body).filterIsInstance<Block.Media>().mapNotNull { it.attachmentId }.toSet()
        val unshown = files.map { it.first }.filter { it.id !in shown }
        if (unshown.isNotEmpty()) body = (body.trimEnd() + "\n\n" + unshown.joinToString("\n") { Markdown.mediaLine(it) }).trim()
        val attrs = e.getElementsByTagName("note-attributes").item(0) as? Element
        val note = Note(
            title = text(e, "title")?.trim().orEmpty(),
            body = body,
            notebookId = notebookId,
            tags = e.getElementsByTagName("tag").let { t -> List(t.length) { t.item(it).textContent.trim() } }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() },
            createdAt = created,
            updatedAt = time(text(e, "updated")) ?: created,
            reminderAt = attrs?.let { time(text(it, "reminder-time")) },
            reminderDone = attrs?.let { time(text(it, "reminder-done-time")) } ?: 0L,
            attachments = files.map { it.first },
            source = attrs?.let { text(it, "source-url") }?.trim().orEmpty(),
        )
        return Imported(note, files)
    }

    private fun extension(mime: String): String = when (mime) {
        "image/png" -> ".png"
        "image/jpeg" -> ".jpg"
        "image/gif" -> ".gif"
        "application/pdf" -> ".pdf"
        "audio/mpeg" -> ".mp3"
        "audio/mp4", "audio/m4a" -> ".m4a"
        "audio/wav" -> ".wav"
        else -> ""
    }

    private fun parseXml(xml: String): org.w3c.dom.Document {
        val cleaned = xml.replace(Regex("<!DOCTYPE[^>]*>"), "")
        val f = DocumentBuilderFactory.newInstance()
        runCatching { f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { f.isExpandEntityReferences = false }
        return f.newDocumentBuilder().parse(InputSource(StringReader(cleaned)))
    }

    private val entities = mapOf(
        "nbsp" to 160, "copy" to 169, "reg" to 174, "trade" to 8482, "mdash" to 8212, "ndash" to 8211, "hellip" to 8230,
        "lsquo" to 8216, "rsquo" to 8217, "ldquo" to 8220, "rdquo" to 8221, "bull" to 8226, "middot" to 183, "deg" to 176,
        "times" to 215, "divide" to 247, "euro" to 8364, "pound" to 163, "yen" to 165, "cent" to 162, "sect" to 167,
        "para" to 182, "laquo" to 171, "raquo" to 187, "eacute" to 233, "egrave" to 232, "aacute" to 225, "agrave" to 224,
        "uuml" to 252, "ouml" to 246, "auml" to 228, "szlig" to 223, "ccedil" to 231, "ntilde" to 241, "rarr" to 8594, "larr" to 8592,
    )

    /** ENML (Evernote's XHTML) as Markdown. [byHash] finds the attachment an `<en-media>` shows. */
    fun enmlToMarkdown(enml: String, byHash: Map<String, Attachment>): String {
        var x = enml.replace(Regex("<\\?xml[^>]*\\?>"), "").replace(Regex("<!DOCTYPE[^>]*>"), "").trim()
        // HTML's named entities aren't XML's: write them as numbers, and escape any other stray &.
        x = Regex("&([a-zA-Z][a-zA-Z0-9]*);").replace(x) { m ->
            when (val name = m.groupValues[1]) {
                "amp", "lt", "gt", "quot", "apos" -> m.value
                else -> entities[name]?.let { "&#$it;" } ?: "&amp;$name;"
            }
        }
        if (x.isEmpty()) return ""
        val root = parseXml(x).documentElement
        val c = Converter(byHash)
        c.children(root)
        c.flush()
        return c.lines.fold(ArrayList<String>()) { acc, l ->
            if (!(l.isBlank() && acc.size >= 2 && acc[acc.size - 1].isBlank() && acc[acc.size - 2].isBlank())) acc += l
            acc
        }.joinToString("\n").trim('\n').trimEnd()
    }

    private fun stripTags(html: String): String =
        html.replace(Regex("<br\\s*/?>|</div>|</p>|</li>|</h\\d>"), "\n").replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").lines().joinToString("\n") { it.trim() }.trim()

    private class Converter(val byHash: Map<String, Attachment>) {
        val lines = ArrayList<String>()
        val cur = StringBuilder()
        var linePrefix = ""
        var quote = 0
        /** Open lists: ordered or not, a checklist or not, and the number of the last item. */
        val lists = ArrayList<IntArray>()

        fun flush(blankIfEmpty: Boolean = false) {
            val content = cur.toString().replace(Regex("[ \\t\\u00a0]+"), " ").trim()
            cur.clear()
            if (content.isEmpty()) {
                // A list marker waits for its text (an <li> usually wraps it in a <div>).
                if (blankIfEmpty && linePrefix.isEmpty()) lines += "> ".repeat(quote).trimEnd()
                return
            }
            lines += "> ".repeat(quote) + linePrefix + content
            linePrefix = ""
        }

        fun children(e: DomNode) {
            var c = e.firstChild
            while (c != null) {
                node(c)
                c = c.nextSibling
            }
        }

        private fun style(e: Element): String = e.getAttribute("style").lowercase().replace(" ", "")

        private fun wrapped(e: Element, open: String, close: String = open) {
            if (e.textContent.isBlank()) return children(e)
            cur.append(open)
            children(e)
            cur.append(close)
        }

        fun node(n: DomNode) {
            if (n.nodeType == DomNode.TEXT_NODE || n.nodeType == DomNode.CDATA_SECTION_NODE) {
                cur.append(n.nodeValue.replace(Regex("\\s+"), " "))
                return
            }
            if (n.nodeType != DomNode.ELEMENT_NODE) return
            val e = n as Element
            val st = style(e)
            when (val tag = e.tagName.lowercase()) {
                "br" -> if (cur.isBlank() && linePrefix.isEmpty()) flush(blankIfEmpty = true) else flush()
                "hr" -> { flush(); lines += "---" }
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    flush(); linePrefix = "#".repeat(minOf(tag[1].digitToInt(), 3)) + " "; children(e); flush(); linePrefix = ""
                }
                "blockquote" -> { flush(); quote++; children(e); flush(); quote-- }
                "pre" -> { flush(); code(innerText(e)) }
                "ul", "ol" -> {
                    flush()
                    lists += intArrayOf(if (tag == "ol") 1 else 0, if (st.contains("--en-todo:true")) 1 else 0, 0)
                    children(e)
                    flush()
                    lists.removeAt(lists.lastIndex)
                }
                "li" -> {
                    flush()
                    val l = lists.lastOrNull() ?: intArrayOf(0, 0, 0)
                    val indent = "  ".repeat((lists.size - 1).coerceAtLeast(0))
                    linePrefix = indent + when {
                        l[1] == 1 -> if (st.contains("--en-checked:true")) "[x] " else "[ ] "
                        l[0] == 1 -> { l[2]++; "${l[2]}. " }
                        else -> "- "
                    }
                    children(e)
                    flush()
                    linePrefix = ""
                }
                "en-todo" -> {
                    if (cur.isNotBlank()) flush()
                    linePrefix = (linePrefix.takeWhile { it == ' ' }) + if (e.getAttribute("checked") == "true") "[x] " else "[ ] "
                }
                "en-media" -> {
                    flush()
                    byHash[e.getAttribute("hash").lowercase()]?.let { lines += Markdown.mediaLine(it) }
                }
                "img" -> {
                    val src = e.getAttribute("src")
                    if (src.startsWith("http")) { flush(); lines += "![${e.getAttribute("alt").replace("]", "")}]($src)" }
                }
                "en-crypt" -> { flush(); lines += "(Encrypted text from Evernote can't be shown here.)" }
                "table" -> { flush(); table(e) }
                "div", "p", "section", "article", "header", "footer", "center" -> {
                    if (st.contains("-en-codeblock:true")) { flush(); code(innerText(e)); return }
                    flush(); children(e); flush()
                }
                "b", "strong" -> wrapped(e, "**")
                "i", "em" -> wrapped(e, "*")
                "u" -> wrapped(e, "<u>", "</u>")
                "s", "strike", "del" -> wrapped(e, "~~")
                "code", "tt" -> wrapped(e, "`")
                "a" -> {
                    val href = e.getAttribute("href")
                    if (href.startsWith("http") || href.startsWith("mailto:")) {
                        val label = e.textContent.replace(Regex("\\s+"), " ").trim()
                        cur.append(if (label.isEmpty() || label == href) href else "[${label.replace("]", "")}]($href)")
                    } else children(e)
                }
                "span", "font" -> when {
                    st.contains("background-color") && !st.contains("background-color:rgb(255,255,255)") && !st.contains("background-color:#fff") -> wrapped(e, "==")
                    st.contains("font-weight:bold") || st.contains("font-weight:700") -> wrapped(e, "**")
                    st.contains("text-decoration:underline") -> wrapped(e, "<u>", "</u>")
                    st.contains("text-decoration:line-through") -> wrapped(e, "~~")
                    st.contains("font-style:italic") -> wrapped(e, "*")
                    else -> children(e)
                }
                "script", "style", "head", "title" -> {}
                else -> children(e)
            }
        }

        private fun code(text: String) {
            lines += "```"
            lines += text.trimEnd('\n').lines()
            lines += "```"
        }

        /** Text with line breaks where `<br>`, `<div>` and `<p>` make them. */
        private fun innerText(e: DomNode): String {
            val sb = StringBuilder()
            fun walk(n: DomNode) {
                if (n.nodeType == DomNode.TEXT_NODE || n.nodeType == DomNode.CDATA_SECTION_NODE) { sb.append(n.nodeValue); return }
                if (n.nodeType != DomNode.ELEMENT_NODE) return
                val tag = (n as Element).tagName.lowercase()
                if (tag == "br") { sb.append('\n'); return }
                val block = tag == "div" || tag == "p"
                if (block && sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
                var c = n.firstChild
                while (c != null) { walk(c); c = c.nextSibling }
                if (block && sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
            }
            var c = e.firstChild
            while (c != null) { walk(c); c = c.nextSibling }
            return sb.toString()
        }

        private fun table(t: Element) {
            val rows = ArrayList<List<String>>()
            val trs = t.getElementsByTagName("tr")
            for (i in 0 until trs.length) {
                val tr = trs.item(i) as Element
                val cells = ArrayList<String>()
                var c = tr.firstChild
                while (c != null) {
                    if (c is Element && (c.tagName.equals("td", true) || c.tagName.equals("th", true))) {
                        val sub = Converter(byHash)
                        sub.children(c)
                        sub.flush()
                        cells += sub.lines.filter { it.isNotBlank() }.joinToString(" ").replace("|", "/")
                    }
                    c = c.nextSibling
                }
                if (cells.isNotEmpty()) rows += cells
            }
            if (rows.isEmpty()) return
            val width = rows.maxOf { it.size }
            fun row(r: List<String>) = (r + List(width - r.size) { "" }).joinToString(" | ", "| ", " |")
            lines += row(rows[0])
            lines += List(width) { "---" }.joinToString(" | ", "| ", " |")
            rows.drop(1).forEach { lines += row(it) }
        }
    }
}
