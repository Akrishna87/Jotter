package io.github.akrishna87.jotter.model

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

/** What was said in a recording, in timed pieces, as the speech model heard it. */
data class Transcript(
    val attachmentId: String,
    val segments: List<Segment>,
    /** The language heard, as a code ("en", "ta"); empty if unknown. */
    val language: String = "",
    val translated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** The summary written by the on-phone language model, once it's been made. */
    val smart: MeetingSummary? = null,
) {
    data class Segment(val startMs: Long, val endMs: Long, val text: String)

    val text: String get() = segments.joinToString(" ") { it.text.trim() }.trim()
    val durationMs: Long get() = segments.maxOfOrNull { it.endMs } ?: 0L

    fun toJson(): JSONObject = JSONObject()
        .put("attachmentId", attachmentId)
        .put("language", language)
        .put("translated", translated)
        .put("createdAt", createdAt)
        .put("segments", JSONArray(segments.map { JSONArray().put(it.startMs).put(it.endMs).put(it.text) }))
        .apply { smart?.let { put("smart", it.toJson()) } }

    companion object {
        fun fromJson(o: JSONObject): Transcript {
            val arr = o.optJSONArray("segments") ?: JSONArray()
            val segs = (0 until arr.length()).mapNotNull { i ->
                val s = arr.optJSONArray(i) ?: return@mapNotNull null
                Segment(s.optLong(0), s.optLong(1), s.optString(2))
            }
            return Transcript(
                attachmentId = o.optString("attachmentId"),
                segments = segs,
                language = o.optString("language"),
                translated = o.optBoolean("translated"),
                createdAt = o.optLong("createdAt"),
                smart = o.optJSONObject("smart")?.let(MeetingSummary::fromJson),
            )
        }
    }
}

/** A short version of a meeting: its main points, what was decided and what people said they'd do. */
data class MeetingSummary(
    val keyPoints: List<Line>,
    val decisions: List<Line>,
    val actions: List<Line>,
    /** A sentence or two on what the meeting was about; only the language model writes one. */
    val overview: String = "",
) {
    /** A sentence and when it was said (-1 if unknown), so tapping it can play that moment. */
    data class Line(val text: String, val atMs: Long)

    val isEmpty: Boolean get() = keyPoints.isEmpty() && decisions.isEmpty() && actions.isEmpty() && overview.isBlank()

    fun toJson(): JSONObject {
        fun lines(l: List<Line>) = JSONArray(l.map { JSONArray().put(it.text).put(it.atMs) })
        return JSONObject().put("overview", overview).put("keyPoints", lines(keyPoints)).put("decisions", lines(decisions)).put("actions", lines(actions))
    }

    /** The summary as note text: headings, bullets and a checklist of the action items. */
    fun toMarkdown(title: String = "Meeting summary"): String = buildString {
        append("## ").append(title).append('\n')
        if (overview.isNotBlank()) append(overview.trim()).append('\n')
        keyPoints.forEach { append("- ").append(it.text).append('\n') }
        if (decisions.isNotEmpty()) {
            append("### Decisions\n")
            decisions.forEach { append("- ").append(it.text).append('\n') }
        }
        if (actions.isNotEmpty()) {
            append("### Action items\n")
            actions.forEach { append("[ ] ").append(it.text).append('\n') }
        }
    }.trimEnd()

    companion object {
        fun fromJson(o: JSONObject): MeetingSummary {
            fun lines(name: String): List<Line> {
                val a = o.optJSONArray(name) ?: return emptyList()
                return (0 until a.length()).mapNotNull { i -> a.optJSONArray(i)?.let { Line(it.optString(0), it.optLong(1, -1)) } }
            }
            return MeetingSummary(lines("keyPoints"), lines("decisions"), lines("actions"), o.optString("overview"))
        }
    }
}

/**
 * Summarises a transcript without a language model: it keeps the sentences that carry the most of
 * the meeting's recurring words, and picks out decisions and commitments by how they're phrased
 * ("we agreed…", "I'll send…", "can you… by Friday"). Nothing is rewritten; every line is
 * something that was said.
 */
object Summarizer {
    private const val MIN_WORDS = 4
    private const val MAX_ACTIONS = 12
    private const val MAX_DECISIONS = 6

    private val noise = Regex("""\[[^\]]*]|\([^)]*\)|\*[^*]*\*|♪+""")
    private val sentenceEnd = Regex("""(?<=[.!?।])\s+""")

    private val actionCue = Regex(
        """\b(i'll|i will|we'll|we will|you'll|you will|he'll|she'll|they'll|i'm going to|we're going to|""" +
            """i am going to|we are going to|need to|needs to|have to|has to|must|should|let's|let us|""" +
            """can you|could you|would you|will you|please|make sure|follow up|follow-up|action item|""" +
            """to-?do|take care of|be responsible|assign(ed)? to|send (the|an|a|it|them|over)|""" +
            """by (monday|tuesday|wednesday|thursday|friday|saturday|sunday|tomorrow|tonight|next week|""" +
            """the end of|end of|eod|eow))\b""",
        RegexOption.IGNORE_CASE,
    )
    private val decisionCue = Regex(
        """\b(decided|decide to|agreed|agree to|we'll go with|we will go with|going with|go ahead with|""" +
            """approved|signed off|finali[sz]ed|final decision|conclusion|settled on|confirmed)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val emphasisCue = Regex(
        """\b(important|key|main|priority|problem|issue|risk|goal|plan|budget|deadline|summary|overall|""" +
            """in short|the point is|bottom line|next step|next steps)\b""",
        RegexOption.IGNORE_CASE,
    )
    // Small talk that sounds like an action but isn't one.
    private val notAction = Regex("""\b(thank you|thanks|see you|bye|good morning|good night|you know|i mean)\b""", RegexOption.IGNORE_CASE)

    private val stopWords = """
        a about above after again against all am an and any are aren't as at be because been before being
        below between both but by can can't cannot could couldn't did didn't do does doesn't doing don't down
        during each few for from further get got had hadn't has hasn't have haven't having he he'd he'll he's
        her here here's hers herself him himself his how how's i i'd i'll i'm i've if in into is isn't it it's
        its itself just let's like me more most mustn't my myself no nor not now of off on once only or other
        ought our ours ourselves out over own really right same shan't she she'd she'll she's should shouldn't
        so some such than that that's the their theirs them themselves then there there's these they they'd
        they'll they're they've thing things think this those through to too um uh under until up us very was
        wasn't we we'd we'll we're we've well were weren't what what's when when's where where's which while
        who who's whom why why's will with won't would wouldn't yeah yes you you'd you'll you're you've your
        yours yourself yourselves okay ok also going gonna want wanna kind sort maybe actually basically
        literally said say says one two lot lots something anything everything
    """.trim().split(Regex("""\s+""")).toSet()

    /** The sentences of a transcript, each with when it started. Repeats and sound tags are dropped. */
    fun sentences(t: Transcript): List<MeetingSummary.Line> {
        val out = ArrayList<MeetingSummary.Line>()
        var pending = StringBuilder()
        var pendingAt = -1L
        val seen = HashSet<String>()
        fun flush() {
            val s = pending.toString().replace(Regex("""\s+"""), " ").trim()
            pending = StringBuilder()
            val at = pendingAt
            pendingAt = -1L
            if (s.isEmpty()) return
            val key = s.lowercase().replace(Regex("""[^\p{L}\p{N} ]"""), "")
            // Speech models sometimes repeat a line over silence; keep it once.
            if (key.isBlank() || !seen.add(key)) return
            out += MeetingSummary.Line(s, at)
        }
        for (seg in t.segments) {
            val clean = seg.text.replace(noise, " ").trim()
            if (clean.isEmpty()) continue
            val parts = clean.split(sentenceEnd)
            parts.forEachIndexed { i, p ->
                if (pendingAt < 0) pendingAt = seg.startMs
                pending.append(' ').append(p)
                val ends = i < parts.lastIndex || p.trimEnd().lastOrNull()?.let { it in ".!?।" } == true
                if (ends) flush()
            }
        }
        flush()
        return out
    }

    private fun words(s: String): List<String> =
        s.lowercase().split(Regex("""[^\p{L}\p{N}']+""")).filter { it.length > 1 }

    private fun contentWords(s: String): List<String> = words(s).filter { it !in stopWords && it.length > 2 }

    fun summarize(t: Transcript): MeetingSummary {
        val all = sentences(t)
        val usable = all.filter { words(it.text).size >= MIN_WORDS }
        if (usable.isEmpty()) return MeetingSummary(all.filter { words(it.text).isNotEmpty() }.take(3), emptyList(), emptyList())

        val decisions = usable.filter { decisionCue.containsMatchIn(it.text) }.take(MAX_DECISIONS)
        val actions = usable
            .filter { it !in decisions && actionCue.containsMatchIn(it.text) && !isSmallTalk(it.text) }
            .take(MAX_ACTIONS)

        // How often each meaningful word comes up across the whole meeting.
        val freq = HashMap<String, Int>()
        usable.forEach { s -> contentWords(s.text).distinct().forEach { w -> freq[w] = (freq[w] ?: 0) + 1 } }
        val top = (freq.values.maxOrNull() ?: 1).toDouble()

        val taken = (decisions + actions).toSet()
        val candidates = usable.filter { it !in taken }
        val target = when {
            usable.size <= 4 -> usable.size
            else -> (usable.size * 0.15).toInt().coerceIn(3, 10)
        }
        val scored = candidates.mapIndexed { i, s ->
            val cw = contentWords(s.text)
            var score = if (cw.isEmpty()) 0.0 else cw.sumOf { (freq[it] ?: 0) / top } / sqrt(cw.size.toDouble())
            if (emphasisCue.containsMatchIn(s.text)) score *= 1.4
            if (s.text.endsWith("?")) score *= 0.7
            // Openings and wrap-ups tend to say what the meeting is about.
            if (i < 2 || i >= candidates.size - 2) score *= 1.15
            s to score
        }
        val keyPoints = scored.sortedByDescending { it.second }
            .take((target - decisions.size).coerceAtLeast(if (candidates.isEmpty()) 0 else 2))
            .map { it.first }
            .sortedBy { usable.indexOf(it) }
        return MeetingSummary(keyPoints, decisions, actions)
    }

    private fun isSmallTalk(s: String): Boolean = notAction.containsMatchIn(s) && words(s).size < 8
}
