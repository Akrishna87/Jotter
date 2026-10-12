package io.github.akrishna87.jotter

import io.github.akrishna87.jotter.model.Dates
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

class DatesTest {
    private val today = LocalDate.of(2026, 10, 3)

    private fun millis(d: LocalDate) = d.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun shortDates() {
        assertEquals("Today", Dates.shortDate(millis(today), today, Locale.ENGLISH, ZoneOffset.UTC))
        assertEquals("Yesterday", Dates.shortDate(millis(today.minusDays(1)), today, Locale.ENGLISH, ZoneOffset.UTC))
        assertEquals("20 Sep", Dates.shortDate(millis(LocalDate.of(2026, 9, 20)), today, Locale.ENGLISH, ZoneOffset.UTC))
        assertEquals("5 Jan 2025", Dates.shortDate(millis(LocalDate.of(2025, 1, 5)), today, Locale.ENGLISH, ZoneOffset.UTC))
    }
}
