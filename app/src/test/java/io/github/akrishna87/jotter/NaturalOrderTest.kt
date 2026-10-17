package io.github.akrishna87.jotter

import io.github.akrishna87.jotter.model.NaturalOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalOrderTest {
    @Test
    fun numbersCountInSequence() {
        val titles = listOf("App1 – Isaialai", "App10 – Folio", "App11 – My Videos", "App12 – Vaasi", "App2 – Jotter", "App3 – Vanalai", "App9 – X")
        assertEquals(
            listOf("App1 – Isaialai", "App2 – Jotter", "App3 – Vanalai", "App9 – X", "App10 – Folio", "App11 – My Videos", "App12 – Vaasi"),
            titles.sortedWith(NaturalOrder),
        )
    }

    @Test
    fun caseAndLeadingZeros() {
        assertEquals(listOf("apple", "Banana", "cherry"), listOf("cherry", "Banana", "apple").sortedWith(NaturalOrder))
        assertTrue(NaturalOrder.compare("Week 02", "Week 10") < 0)
        assertTrue(NaturalOrder.compare("v1.9", "v1.10") < 0)
        assertTrue(NaturalOrder.compare("Note", "Note 2") < 0)
        assertTrue(NaturalOrder.compare("12345678901234567890", "99") > 0)
    }
}
