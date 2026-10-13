package io.github.akrishna87.jotter.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
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

    fun time(t: LocalTime, is24Hour: Boolean, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale).format(t)

    /** "Today 18:00", "Tomorrow 9:00 AM", "Mon 12 Oct 9:00". */
    fun reminder(millis: Long, today: LocalDate, is24Hour: Boolean, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String {
        val at = Instant.ofEpochMilli(millis).atZone(zone)
        val day = at.toLocalDate()
        val d = when (day) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> DateTimeFormatter.ofPattern(if (day.year == today.year) "EEE d MMM" else "EEE d MMM yyyy", locale).format(day)
        }
        return d + " " + time(at.toLocalTime(), is24Hour, locale)
    }

    /** A date and time in full, for note info: "Monday 12 October 2026, 14:05". */
    fun full(millis: Long, is24Hour: Boolean, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String {
        val at = Instant.ofEpochMilli(millis).atZone(zone)
        return DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale).format(at) + ", " + time(at.toLocalTime(), is24Hour, locale)
    }
}
