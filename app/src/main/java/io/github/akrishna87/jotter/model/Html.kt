package io.github.akrishna87.jotter.model

/**
 * Notes as HTML: for exporting, printing and saving as PDF, and (with [enml]) for Evernote's ENEX
 * files. [media] gives the `src` (or, for ENML, the hash) to use for an attachment, or null to show
 * just its name.
 */
object Html {
    fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** A whole page for one or more notes, ready to print or save. */
    fun page(notes: List<Note>, media: (Attachment) -> String? = { null }): String = buildString {
        append("<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"><title>")
        append(escape(notes.singleOrNull()?.title?.ifBlank { "Untitled" } ?: "Jotter notes"))
        append("</title><style>")
        append(
            "body{font-family:sans-serif;line-height:1.5;max-width:46em;margin:2em auto;padding:0 1em;color:#1d1b20}" +
                "h1.title{font-size:1.7em;margin-bottom:.2em}.meta{color:#666;font-size:.85em;margin-bottom:1.5em}" +
                "blockquote{border-left:3px solid #ccc;margin:.5em 0;padding-left:1em;color:#555}" +
                "pre{background:#f4f4f4;padding:.8em;border-radius:6px;overflow:auto}code{background:#f4f4f4;padding:0 .2em}" +
                "table{border-collapse:collapse}td,th{border:1px solid #ccc;padding:.3em .6em}mark{background:#ffef9e}" +
                "ul.check{list-style:none;padding-left:1.2em}img{max-width:100%}.note{page-break-after:always}",
        )
        append("</style></head><body>\n")
        for (n in notes) {
            append("<div class=\"note\"><h1 class=\"title\">").append(escape(n.title.ifBlank { "Untitled" })).append("</h1>\n")
            if (n.tags.isNotEmpty()) append("<div class=\"meta\">").append(escape(n.tags.joinToString("  ") { "#$it" })).append("</div>\n")
            append(body(n.body, n.attachments, media))
            append("</div>\n")
        }
        append("</body></html>\n")
    }

    /** A note's text as HTML. With [enml], checklists become `<en-todo>` and attachments `<en-media>`, as Evernote writes them. */
    fun body(markdown: String, attachments: List<Attachment>, media: (Attachment) -> String? = { null }, enml: Boolean = false): String = buildString {
        var list: String? = null
        fun closeList() {
            if (list != null) append("</").append(list).append(">\n")
            list = null
        }
        fun openList(tag: String, cls: String = "") {
            if (list != tag + cls) {
                closeList()
                append("<").append(tag).append(if (cls.isNotEmpty() && !enml) " class=\"$cls\"" else "").append(">\n")
                list = tag + cls
            }
        }
        val br = if (enml) "<br/>" else "<br>"
        for (b in Markdown.parse(markdown.trimEnd())) {
            when (b) {
                is Block.Bullet -> { openList("ul"); append("<li>").append(inline(b.text, enml)).append("</li>\n"); continue }
                is Block.Numbered -> { openList("ol"); append("<li>").append(inline(b.text, enml)).append("</li>\n"); continue }
                else -> {}
            }
            if (b is Block.Check && !enml) {
                openList("ul", "check")
                append("<li>").append(if (b.checked) "☑ " else "☐ ").append(inline(b.text, false)).append("</li>\n")
                continue
            }
            closeList()
            when (b) {
                is Block.Heading -> append("<h${b.level}>").append(inline(b.text, enml)).append("</h${b.level}>\n")
                is Block.Check -> append("<div><en-todo checked=\"${b.checked}\"/>").append(inline(b.text, true)).append("</div>\n")
                is Block.Quote -> append("<blockquote>").append(inline(b.text, enml)).append("</blockquote>\n")
                is Block.Code -> append("<pre>").append(escape(b.text)).append("</pre>\n")
                is Block.Table -> {
                    append("<table>\n")
                    b.rows.forEachIndexed { r, row ->
                        append("<tr>")
                        val cell = if (r == 0 && !enml) "th" else "td"
                        for (c in row) append("<$cell>").append(inline(c, enml)).append("</$cell>")
                        append("</tr>\n")
                    }
                    append("</table>\n")
                }
                is Block.Media -> {
                    val a = b.attachmentId?.let { id -> attachments.firstOrNull { it.id == id } }
                    val src = a?.let(media)
                    when {
                        a != null && enml && src != null -> append("<div><en-media type=\"${escape(a.mime)}\" hash=\"$src\"/></div>\n")
                        a != null && a.isImage && src != null -> append("<p><img src=\"${escape(src)}\" alt=\"${escape(a.name)}\"></p>\n")
                        a != null && src != null -> append("<p><a href=\"${escape(src)}\">📎 ${escape(a.name)}</a></p>\n")
                        a == null && !b.target.startsWith("attachment:") -> append("<p><img src=\"${escape(b.target)}\" alt=\"${escape(b.name)}\"${if (enml) "/" else ""}></p>\n")
                        else -> append("<p>📎 ").append(escape(b.name)).append("</p>\n")
                    }
                }
                is Block.Paragraph -> append("<div>").append(inline(b.text, enml)).append("</div>\n")
                is Block.Divider -> append(if (enml) "<hr/>\n" else "<hr>\n")
                is Block.Blank -> append("<div>").append(br).append("</div>\n")
                else -> {}
            }
        }
        closeList()
    }

    /** One line's inline styles as HTML. */
    fun inline(text: String, enml: Boolean = false): String = Markdown.inline(text).joinToString("") { s ->
        var h = escape(s.text)
        if (s.code) h = "<code>$h</code>"
        if (s.bold) h = "<b>$h</b>"
        if (s.italic) h = "<i>$h</i>"
        if (s.underline) h = "<u>$h</u>"
        if (s.strike) h = "<s>$h</s>"
        if (s.highlight) h = if (enml) "<span style=\"background-color: rgb(255, 239, 158);\">$h</span>" else "<mark>$h</mark>"
        val link = s.link
        if (link != null && (link.startsWith("http://") || link.startsWith("https://") || link.startsWith("mailto:"))) h = "<a href=\"${escape(link)}\">$h</a>"
        h
    }
}
