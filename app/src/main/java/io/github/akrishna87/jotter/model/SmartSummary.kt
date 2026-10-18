package io.github.akrishna87.jotter.model

/**
 * The language-model summary: what to ask the model, how to split a long meeting into parts it can
 * read, and how to read its answer back. The model itself runs elsewhere ([generate]).
 */
object SmartSummary {
    const val SYSTEM =
        "You summarise meeting transcripts for the person who recorded them. Use only what is in the " +
            "transcript; never invent names, numbers or dates. Transcripts come from speech recognition and " +
            "may contain mistakes. Write in English, in short plain sentences."

    private const val FORMAT =
        "Answer with exactly these four headings and nothing else:\n" +
            "OVERVIEW:\n<one or two sentences on what the meeting was about>\n" +
            "KEY POINTS:\n- <point>\n" +
            "DECISIONS:\n- <decision, or - None>\n" +
            "ACTION ITEMS:\n- <who: what, and by when if it was said; or - None>"

    /** The prompt for one part of a meeting (or the whole of a short one). */
    fun partPrompt(transcript: String, part: Int, parts: Int): String = buildString {
        if (parts > 1) append("This is part $part of $parts of a meeting transcript.\n\n") else append("This is a meeting transcript.\n\n")
        append("<transcript>\n").append(transcript.trim()).append("\n</transcript>\n\n")
        append(FORMAT)
        append("\nKeep each line short. List at most 8 key points.")
    }

    /** The prompt that combines the notes from each part into one summary. */
    fun combinePrompt(notes: List<String>): String = buildString {
        append("These are notes on consecutive parts of one meeting.\n\n")
        notes.forEachIndexed { i, n -> append("<part ").append(i + 1).append(">\n").append(n.trim()).append("\n</part>\n\n") }
        append("Combine them into one summary of the whole meeting. Merge repeats, keep every decision and action item.\n\n")
        append(FORMAT)
        append("\nList at most 10 key points.")
    }

    /**
     * Splits a transcript into parts of at most [budget] tokens, by whole segments, counting with
     * [tokens]. Returns each part's text.
     */
    fun split(t: Transcript, budget: Int, tokens: (String) -> Int): List<String> {
        val parts = ArrayList<String>()
        val current = StringBuilder()
        var used = 0
        for (seg in t.segments) {
            val line = "[" + clock(seg.startMs) + "] " + seg.text.trim() + "\n"
            val n = tokens(line)
            if (used + n > budget && current.isNotEmpty()) {
                parts += current.toString()
                current.clear()
                used = 0
            }
            current.append(line)
            used += n
        }
        if (current.isNotBlank()) parts += current.toString()
        return parts
    }

    /** Groups notes so each group fits in [budget] tokens, for combining in rounds when there are many. */
    fun group(notes: List<String>, budget: Int, tokens: (String) -> Int): List<List<String>> {
        val out = ArrayList<List<String>>()
        var cur = ArrayList<String>()
        var used = 0
        for (n in notes) {
            val c = tokens(n)
            if (used + c > budget && cur.isNotEmpty()) {
                out += cur
                cur = ArrayList()
                used = 0
            }
            cur += n
            used += c
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    private enum class Section { OVERVIEW, KEY, DECISIONS, ACTIONS }

    // A heading alone on its line, or followed by a colon and its first item.
    private val heading = Regex("""^[#*\s]*(overview|summary|key points?|main points?|decisions?|action items?|actions?|next steps?)[*\s]*(?::[*\s]*(.*))?$""", RegexOption.IGNORE_CASE)
    private val bullet = Regex("""^\s*(?:(?:[-*•–]|\d+[.)]|\[[ xX]?])\s*)+""")
    private val none = Regex("""^(none|n/?a|no decisions?( were made)?|no action items?|nothing)\.?$""", RegexOption.IGNORE_CASE)
    private val stamp = Regex("""^\[?\d{1,2}:\d{2}(:\d{2})?]?\s*""")

    /**
     * Reads the model's answer into a summary. Each line is matched to the transcript sentence it
     * most resembles, so it can still be played from the recording.
     */
    fun parse(answer: String, t: Transcript?): MeetingSummary {
        var section: Section? = null
        val overview = StringBuilder()
        val key = ArrayList<String>()
        val decisions = ArrayList<String>()
        val actions = ArrayList<String>()
        for (raw in answer.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val h = heading.matchEntire(line)
            if (h != null) {
                val name = h.groupValues[1].lowercase()
                section = when {
                    name.startsWith("overview") || name.startsWith("summary") -> Section.OVERVIEW
                    name.startsWith("key") || name.startsWith("main") -> Section.KEY
                    name.startsWith("decision") -> Section.DECISIONS
                    else -> Section.ACTIONS
                }
                val rest = h.groupValues[2].trim()
                if (rest.isEmpty()) continue
                handle(section, rest, overview, key, decisions, actions)
                continue
            }
            handle(section ?: Section.KEY, line, overview, key, decisions, actions)
        }
        val sentences = t?.let { Summarizer.sentences(it) }.orEmpty()
        fun lines(l: List<String>) = l.distinctBy { it.lowercase() }.map { MeetingSummary.Line(it, timeOf(it, sentences)) }
        return MeetingSummary(lines(key), lines(decisions), lines(actions), overview.toString().trim())
    }

    private fun handle(s: Section, line: String, overview: StringBuilder, key: MutableList<String>, decisions: MutableList<String>, actions: MutableList<String>) {
        val text = line.replace(bullet, "").replace(stamp, "").replace("**", "").trim()
        if (text.isEmpty() || none.matches(text) || text.startsWith("<")) return
        when (s) {
            Section.OVERVIEW -> overview.append(if (overview.isEmpty()) "" else " ").append(text)
            Section.KEY -> key += text
            Section.DECISIONS -> decisions += text
            Section.ACTIONS -> actions += text
        }
    }

    private fun words(s: String): Set<String> =
        s.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.length > 3 }.toSet()

    /** When the transcript says something most like [line]; -1 if nothing is close enough. */
    fun timeOf(line: String, sentences: List<MeetingSummary.Line>): Long {
        val w = words(line)
        if (w.isEmpty()) return -1
        var best = 0.0
        var at = -1L
        for (s in sentences) {
            val sw = words(s.text)
            if (sw.isEmpty()) continue
            val score = w.count { it in sw }.toDouble() / w.size
            if (score > best) { best = score; at = s.atMs }
        }
        return if (best >= 0.4) at else -1
    }

    private fun clock(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
}
