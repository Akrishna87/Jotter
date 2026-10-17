package io.github.akrishna87.jotter

import io.github.akrishna87.jotter.model.Summarizer
import io.github.akrishna87.jotter.model.Transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummarizerTest {
    private fun transcript(vararg lines: String) = Transcript(
        "a1",
        lines.mapIndexed { i, s -> Transcript.Segment(i * 5000L, i * 5000L + 4000, s) },
    )

    private val meeting = transcript(
        " Okay, let's start. This meeting is about the budget for the mobile app launch.",
        " The main issue is that the budget for marketing is too small for the launch in March.",
        " Yeah.",
        " [BLANK_AUDIO]",
        " We looked at three options for the launch budget and the marketing plan.",
        " After some discussion we agreed to move fifty thousand from design to marketing.",
        " Priya, can you update the budget sheet by Friday?",
        " I'll send the revised launch plan to the whole team tomorrow.",
        " The weather has been nice lately.",
        " The risk is that the app store review could delay the launch by two weeks.",
        " The risk is that the app store review could delay the launch by two weeks.",
        " We need to book the launch event venue before the end of the month.",
        " Thanks everyone, see you next week.",
    )

    @Test
    fun picksDecisionsAndActions() {
        val s = Summarizer.summarize(meeting)
        assertEquals(listOf("After some discussion we agreed to move fifty thousand from design to marketing."), s.decisions.map { it.text })
        val actions = s.actions.map { it.text }
        assertTrue(actions.toString(), "Priya, can you update the budget sheet by Friday?" in actions)
        assertTrue(actions.toString(), "I'll send the revised launch plan to the whole team tomorrow." in actions)
        assertTrue(actions.toString(), "We need to book the launch event venue before the end of the month." in actions)
        assertFalse(actions.toString(), actions.any { it.startsWith("Thanks everyone") })
    }

    @Test
    fun keyPointsAreShortAndOnTopic() {
        val s = Summarizer.summarize(meeting)
        val points = s.keyPoints.map { it.text }
        assertTrue(points.toString(), points.size in 2..4)
        assertTrue(points.toString(), points.any { "budget" in it })
        assertFalse(points.toString(), points.any { "weather" in it })
        // In the order they were said.
        assertEquals(points, points.sortedBy { p -> meeting.segments.indexOfFirst { p in it.text } })
    }

    @Test
    fun dropsRepeatsAndSoundTags() {
        val lines = Summarizer.sentences(meeting).map { it.text }
        assertEquals(1, lines.count { it.startsWith("The risk is") })
        assertFalse(lines.any { "BLANK" in it })
        assertEquals(5000L, Summarizer.sentences(meeting)[2].atMs)
    }

    @Test
    fun sentencesAcrossSegments() {
        val t = transcript(" We should probably", " move the release to June. Everyone agreed.")
        assertEquals(listOf("We should probably move the release to June.", "Everyone agreed."), Summarizer.sentences(t).map { it.text })
    }

    @Test
    fun emptyAndSilent() {
        assertTrue(Summarizer.summarize(transcript(" [BLANK_AUDIO]", " (silence)")).isEmpty)
        assertTrue(Summarizer.summarize(transcript()).isEmpty)
    }

    @Test
    fun markdownHasChecklist() {
        val md = Summarizer.summarize(meeting).toMarkdown()
        assertTrue(md, md.startsWith("## Meeting summary\n- "))
        assertTrue(md, "### Action items\n[ ] " in md)
        assertTrue(md, "### Decisions\n- After some discussion" in md)
    }

    @Test
    fun jsonRoundTrip() {
        val t = meeting.copy(language = "en", translated = true)
        assertEquals(t, Transcript.fromJson(t.toJson()))
    }
}
