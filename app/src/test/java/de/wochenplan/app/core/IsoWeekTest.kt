package de.wochenplan.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class IsoWeekTest {

    @Test
    fun `Montag der Woche ist korrekt`() {
        val week = IsoWeek(2026, 39)
        assertEquals(LocalDate.of(2026, 9, 21), week.monday)
        assertEquals(LocalDate.of(2026, 9, 27), week.sunday)
        assertEquals(7, week.days.size)
    }

    @Test
    fun `Jahreswechsel nach ISO 8601`() {
        // Der 31.12.2025 liegt bereits in KW 1 des Jahres 2026.
        val week = IsoWeek.of(LocalDate.of(2025, 12, 31))
        assertEquals(2026, week.year)
        assertEquals(1, week.week)
        assertEquals(LocalDate.of(2025, 12, 29), week.monday)
    }

    @Test
    fun `der 1 Januar kann noch zur Vorjahreswoche gehoeren`() {
        // 01.01.2022 war ein Samstag und zaehlt zu KW 52 von 2021.
        val week = IsoWeek.of(LocalDate.of(2022, 1, 1))
        assertEquals(2021, week.year)
        assertEquals(52, week.week)
    }

    @Test
    fun `Jahre mit 53 Wochen werden erkannt`() {
        assertEquals(53, IsoWeek.weeksInYear(2020))
        assertEquals(52, IsoWeek.weeksInYear(2026))
        assertEquals(52, IsoWeek.weeksInYear(2025))
    }

    @Test
    fun `Schluessel laesst sich schreiben und lesen`() {
        val week = IsoWeek(2026, 7)
        assertEquals("2026-W07", week.key)
        assertEquals(week, IsoWeek.parse("2026-W07"))
        assertNull(IsoWeek.parse("2026-W54"))
        assertNull(IsoWeek.parse("Unsinn"))
    }

    @Test
    fun `Wochen lassen sich verschieben`() {
        val week = IsoWeek(2026, 1)
        assertEquals(IsoWeek(2025, 52), week.minusWeeks(1))
        assertEquals(IsoWeek(2026, 5), week.plusWeeks(4))
        assertEquals(4L, week.weeksUntil(IsoWeek(2026, 5)))
    }

    @Test
    fun `contains prueft den Zeitraum`() {
        val week = IsoWeek(2026, 39)
        assertTrue(week.contains(LocalDate.of(2026, 9, 21)))
        assertTrue(week.contains(LocalDate.of(2026, 9, 27)))
        assertTrue(!week.contains(LocalDate.of(2026, 9, 28)))
    }
}
