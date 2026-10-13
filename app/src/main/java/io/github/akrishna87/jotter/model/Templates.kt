package io.github.akrishna87.jotter.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The templates Jotter comes with. Saved templates (Note options → Save as template) are in [Data.templates]. */
object Templates {
    val builtIn: List<Template> = listOf(
        Template(
            "tpl-meeting", "Meeting notes", "Meeting: {date}",
            """
            **Date:** {date}
            **Attendees:**

            ## Agenda
            1.

            ## Notes
            -

            ## Decisions
            -

            ## Action items
            [ ]
            """.trimIndent(),
        ),
        Template(
            "tpl-todo", "To-do list", "To do",
            """
            ## Must do today
            [ ]
            [ ]

            ## Should do
            [ ]

            ## Could do
            [ ]
            """.trimIndent(),
        ),
        Template(
            "tpl-project", "Project plan", "Project: ",
            """
            ## Goal


            ## Milestones
            | Milestone | Owner | Due | Status |
            | --- | --- | --- | --- |
            |  |  |  |  |
            |  |  |  |  |

            ## Tasks
            [ ]

            ## Risks
            -

            ## Links
            -
            """.trimIndent(),
        ),
        Template(
            "tpl-weekly", "Weekly planner", "Week of {week}",
            """
            ## Top priorities
            1.
            2.
            3.

            ## Monday
            [ ]
            ## Tuesday
            [ ]
            ## Wednesday
            [ ]
            ## Thursday
            [ ]
            ## Friday
            [ ]
            ## Weekend
            [ ]

            ## Notes

            """.trimIndent(),
        ),
        Template(
            "tpl-journal", "Daily journal", "{date}",
            """
            ## Grateful for
            -

            ## Today


            ## What went well
            -

            ## Tomorrow
            [ ]
            """.trimIndent(),
        ),
        Template(
            "tpl-lecture", "Lecture notes", "Lecture: ",
            """
            **Course:**
            **Date:** {date}

            ## Key points
            -

            ## Notes


            ## Questions
            -

            ## Summary

            """.trimIndent(),
        ),
        Template(
            "tpl-recipe", "Recipe", "Recipe: ",
            """
            **Serves:**
            **Time:**

            ## Ingredients
            [ ]
            [ ]

            ## Method
            1.
            2.

            ## Notes

            """.trimIndent(),
        ),
        Template(
            "tpl-travel", "Trip planner", "Trip to ",
            """
            **Dates:**
            **Stay:**

            ## Bookings
            | What | Reference | When |
            | --- | --- | --- |
            |  |  |  |

            ## Packing
            [ ] Passport
            [ ] Chargers
            [ ]

            ## Plan
            -
            """.trimIndent(),
        ),
    )

    /** A template filled in for [today]: `{date}` and `{week}` become dates. */
    fun fill(t: Template, today: LocalDate, locale: Locale = Locale.getDefault()): Pair<String, String> {
        val date = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale).format(today)
        val week = DateTimeFormatter.ofPattern("d MMMM yyyy", locale).format(today.minusDays((today.dayOfWeek.value - 1).toLong()))
        fun f(s: String) = s.replace("{date}", date).replace("{week}", week)
        return f(t.title) to f(t.body)
    }
}
