package io.github.akrishna87.jotter.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Evernote's search syntax. Words must all match (anywhere in the title, text, tags or attachment
 * names); `"quoted words"` match together; `word*` matches the start of words; `-` in front of
 * anything leaves those notes out; and starting with `any:` matches notes with any of the terms.
 *
 * Terms: `intitle:`, `notebook:`, `stack:`, `tag:` (with nested tags; `tag:*` for any tag),
 * `created:` and `updated:` (`20261012`, `2026-10-12`, `day`, `day-1`, `week`, `week-2`, `month`,
 * `year`: on or after then), `todo:true` (unticked boxes), `todo:false` (ticked boxes), `todo:*`,
 * `reminder:true`, `reminder:done`, `resource:image` (or `resource:audio`, `resource:application/pdf`, any),
 * `source:`, `pinned:true` and `link:true` (notes linking to other notes).
 */
object Search {
    class Term(val negated: Boolean, val test: (Note, Data) -> Boolean)

    data class Query(val any: Boolean, val terms: List<Term>) {
        fun matches(n: Note, d: Data): Boolean =
            if (terms.isEmpty()) true
            else if (any) terms.any { it.negated != it.test(n, d) }
            else terms.all { it.negated != it.test(n, d) }
    }

    /** The notes (not in the trash) that [query] finds. */
    fun run(query: String, data: Data, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<Note> {
        val q = parse(query, today, zone)
        return data.liveNotes.filter { q.matches(it, data) }
    }

    fun parse(query: String, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Query {
        var tokens = tokenize(query)
        val any = tokens.firstOrNull()?.equals("any:", true) == true
        if (any) tokens = tokens.drop(1)
        return Query(any, tokens.mapNotNull { term(it, today, zone) })
    }

    /** The plain words and phrases in [query], for highlighting what was found. */
    fun words(query: String): List<String> = tokenize(query)
        .filter { !it.startsWith("-") && !it.contains(':') }
        .map { it.trim('"').removeSuffix("*") }
        .filter { it.isNotBlank() }

    private fun tokenize(query: String): List<String> {
        val out = ArrayList<String>()
        val buf = StringBuilder()
        var quoted = false
        for (c in query) {
            when {
                c == '"' -> { quoted = !quoted; buf.append(c) }
                c.isWhitespace() && !quoted -> { if (buf.isNotEmpty()) out += buf.toString(); buf.clear() }
                else -> buf.append(c)
            }
        }
        if (buf.isNotEmpty()) out += buf.toString()
        return out
    }

    private fun term(raw: String, today: LocalDate, zone: ZoneId): Term? {
        val negated = raw.startsWith("-") && raw.length > 1
        val t = if (negated) raw.substring(1) else raw
        val colon = t.indexOf(':')
        val key = if (colon > 0 && !t.startsWith("\"")) t.substring(0, colon).lowercase() else ""
        val value = if (key.isEmpty()) t else t.substring(colon + 1).trim('"')
        val test: (Note, Data) -> Boolean = when (key) {
            "intitle" -> { n, _ -> matchesText(n.title, value) }
            "notebook" -> { n, d -> nameMatches(d.notebook(n.notebookId).name, value) }
            "stack" -> { n, d -> d.notebook(n.notebookId).stack?.let { nameMatches(it, value) } ?: false }
            "tag" -> if (value == "*") { n, _ -> n.tags.isNotEmpty() } else { n, d ->
                val wanted = if (value.endsWith("*")) d.tags.filter { nameMatches(it, value) } else listOf(value)
                val all = wanted.flatMap { d.tagAndChildren(it) }.toSet()
                n.tags.any { it.lowercase() in all }
            }
            "created", "updated" -> {
                val from = since(value, today) ?: return null
                val millis = from.atStartOfDay(zone).toInstant().toEpochMilli()
                if (key == "created") { n, _ -> n.createdAt >= millis } else { n, _ -> n.updatedAt >= millis }
            }
            "todo" -> when (value.lowercase()) {
                "true" -> { n, _ -> Markdown.parse(n.body).any { it is Block.Check && !it.checked } }
                "false" -> { n, _ -> Markdown.parse(n.body).any { it is Block.Check && it.checked } }
                else -> { n, _ -> Markdown.parse(n.body).any { it is Block.Check } }
            }
            "reminder", "reminderorder", "remindertime" -> when (value.lowercase()) {
                "done" -> { n, _ -> n.hasReminder && n.reminderDone != 0L }
                "false" -> { n, _ -> !n.hasReminder }
                "true" -> { n, _ -> n.hasReminder && n.reminderDone == 0L }
                else -> { n, _ -> n.hasReminder }
            }
            "reminderdonetime" -> { n, _ -> n.reminderDone != 0L }
            "resource", "attachment" -> when {
                value == "*" || value.equals("true", true) -> { n, _ -> n.attachments.isNotEmpty() }
                value.equals("false", true) -> { n, _ -> n.attachments.isEmpty() }
                value.endsWith("/*") -> { n, _ -> n.attachments.any { it.mime.startsWith(value.removeSuffix("*"), true) } }
                else -> { n, _ -> n.attachments.any { it.mime.equals(value, true) } }
            }
            "source", "sourceurl" -> { n, _ -> if (value == "*") n.source.isNotBlank() else n.source.contains(value.removeSuffix("*"), true) }
            "pinned" -> { n, _ -> n.pinned == !value.equals("false", true) }
            "link" -> { n, _ -> (Links.targets(n.body).isNotEmpty() || Links.titles(n.body).isNotEmpty()) == !value.equals("false", true) }
            "" -> {
                if (value.isBlank()) return null
                return Term(negated) { n, _ -> matchesText(searchable(n), value) }
            }
            // Unknown keys are searched for as words, as Evernote does.
            else -> { n, _ -> matchesText(searchable(n), t) }
        }
        return Term(negated, test)
    }

    private fun searchable(n: Note): String =
        n.title + "\n" + Markdown.plain(n.body, Int.MAX_VALUE) + "\n" + n.tags.joinToString(" ") + "\n" + n.attachments.joinToString(" ") { it.name }

    /** A word (or "a phrase") in [text], ignoring case; `word*` matches words starting with it. */
    private fun matchesText(text: String, value: String): Boolean {
        val v = value.trim('"')
        if (v.endsWith("*")) {
            val stem = Regex.escape(v.removeSuffix("*"))
            return Regex("(?i)(^|[^\\p{L}\\p{N}])$stem").containsMatchIn(text)
        }
        return text.contains(v, ignoreCase = true)
    }

    private fun nameMatches(name: String, value: String): Boolean =
        if (value.endsWith("*")) name.startsWith(value.removeSuffix("*"), true) else name.equals(value, true)

    /** "20261012", "2026-10-12", "day", "day-3", "week", "week-1", "month", "month-2", "year", "year-1". */
    fun since(value: String, today: LocalDate): LocalDate? {
        val v = value.lowercase()
        Regex("(day|week|month|year)(?:-(\\d+))?").matchEntire(v)?.let { m ->
            val n = m.groupValues[2].ifEmpty { "0" }.toLong()
            return when (m.groupValues[1]) {
                "day" -> today.minusDays(n)
                "week" -> today.minusDays(((today.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7).toLong()).minusWeeks(n)
                "month" -> today.withDayOfMonth(1).minusMonths(n)
                else -> today.withDayOfYear(1).minusYears(n)
            }
        }
        return runCatching { LocalDate.parse(v.take(8), DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
            ?: runCatching { LocalDate.parse(v) }.getOrNull()
    }
}
