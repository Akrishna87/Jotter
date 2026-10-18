package io.github.akrishna87.jotter

import io.github.akrishna87.jotter.model.MeetingSummary
import io.github.akrishna87.jotter.model.SmartSummary
import io.github.akrishna87.jotter.model.Transcript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSummaryTest {
    private val t = Transcript(
        "a1",
        listOf(
            Transcript.Segment(0, 4000, " Today we're planning the mobile app launch."),
            Transcript.Segment(5000, 9000, " After some discussion we agreed to move fifty thousand from design to marketing."),
            Transcript.Segment(65000, 70000, " Priya, can you update the budget sheet by Friday?"),
        ),
    )

    @Test
    fun readsTheFourSections() {
        val answer = """
            OVERVIEW:
            The team planned the mobile app launch budget.
            KEY POINTS:
            - Marketing budget is too small for the March launch
            - App store review could delay the launch
            DECISIONS:
            - Move fifty thousand from design to marketing
            ACTION ITEMS:
            - Priya: update the budget sheet by Friday
        """.trimIndent()
        val s = SmartSummary.parse(answer, t)
        assertEquals("The team planned the mobile app launch budget.", s.overview)
        assertEquals(2, s.keyPoints.size)
        assertEquals(listOf("Move fifty thousand from design to marketing"), s.decisions.map { it.text })
        assertEquals(listOf("Priya: update the budget sheet by Friday"), s.actions.map { it.text })
        // Matched back to when it was said.
        assertEquals(5000L, s.decisions[0].atMs)
        assertEquals(65000L, s.actions[0].atMs)
    }

    @Test
    fun copesWithMarkdownAndNone() {
        val answer = """
            **Overview:** A short check-in.
            **Key points:**
            1. Everything is on track
            * Nothing is blocked
            **Decisions:**
            - None
            ## Action items
            - [ ] Send notes to the team
            - None.
        """.trimIndent()
        val s = SmartSummary.parse(answer, null)
        assertEquals("A short check-in.", s.overview)
        assertEquals(listOf("Everything is on track", "Nothing is blocked"), s.keyPoints.map { it.text })
        assertTrue(s.decisions.isEmpty())
        assertEquals(listOf("Send notes to the team"), s.actions.map { it.text })
        assertEquals(-1L, s.actions[0].atMs)
    }

    @Test
    fun aSentenceStartingWithAHeadingWordIsNotAHeading() {
        val s = SmartSummary.parse("KEY POINTS:\n- Budget\nDecisions were postponed to next week.", null)
        assertEquals(listOf("Budget", "Decisions were postponed to next week."), s.keyPoints.map { it.text })
    }

    @Test
    fun splitsByBudget() {
        val parts = SmartSummary.split(t, budget = 20) { it.length / 4 }
        assertTrue(parts.toString(), parts.size >= 2)
        assertTrue(parts[0].startsWith("[0:00] Today"))
        assertTrue(parts.joinToString("").contains("[1:05] Priya"))
        assertEquals(1, SmartSummary.split(t, budget = 10_000) { it.length / 4 }.size)
        assertEquals(listOf(listOf("aa", "bb"), listOf("cc")), SmartSummary.group(listOf("aa", "bb", "cc"), 2) { 1 })
    }

    @Test
    fun jsonKeepsTheSmartSummary() {
        val smart = MeetingSummary(listOf(MeetingSummary.Line("A", 10)), emptyList(), listOf(MeetingSummary.Line("B", -1)), "Overview")
        val withSmart = t.copy(smart = smart)
        assertEquals(withSmart, Transcript.fromJson(withSmart.toJson()))
        assertTrue(smart.toMarkdown().startsWith("## Meeting summary\nOverview\n- A"))
    }
}

class SummaryModelTest {
    @Test
    fun picksByMemory() {
        val gb = 1_000_000_000L
        assertEquals(io.github.akrishna87.jotter.model.SummaryModel.LITE, io.github.akrishna87.jotter.model.SummaryModel.forRam(2 * gb))
        assertEquals(io.github.akrishna87.jotter.model.SummaryModel.LITE, io.github.akrishna87.jotter.model.SummaryModel.forRam(3_800_000_000L))
        assertEquals(io.github.akrishna87.jotter.model.SummaryModel.STANDARD, io.github.akrishna87.jotter.model.SummaryModel.forRam(5_700_000_000L))
        assertEquals(io.github.akrishna87.jotter.model.SummaryModel.BEST, io.github.akrishna87.jotter.model.SummaryModel.forRam(7_600_000_000L))
        assertEquals(io.github.akrishna87.jotter.model.SummaryModel.BEST, io.github.akrishna87.jotter.model.SummaryModel.forRam(16 * gb))
        assertEquals("1.1 GB", io.github.akrishna87.jotter.model.SummaryModel.STANDARD.sizeLabel)
        assertEquals("400 MB", io.github.akrishna87.jotter.model.SummaryModel.LITE.sizeLabel)
    }
}
