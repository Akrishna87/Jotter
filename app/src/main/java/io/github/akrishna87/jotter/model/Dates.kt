package io.github.akrishna87.jotter.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How dates read in the notes list and editor. Plain Kotlin, so it is unit tested. */
object Dates {
    /** "Today", "Yesterday", otherwise "9 Oct" (with the year if it's another year). */
    fun shortDate(millis: Long, today: LocalDate, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String {
        val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        return when (day) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> DateTimeFormatter.ofPattern(if (day.year == today.year) "d MMM" else "d MMM yyyy", locale).format(day)
        }
    }
}
