package de.wochenplan.app.data.ical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class RecurrenceTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    @Test
    fun `woechentliche Regel mit mehreren Tagen`() {
        val rule = RecurrenceRule.parse("FREQ=WEEKLY;BYDAY=MO,WE,FR", zone)
        assertNotNull(rule)
        val dates = RecurrenceExpander.expandDates(
            start = LocalDate.of(2026, 9, 21), // Montag
            rule = rule!!,
            maxDate = LocalDate.of(2026, 9, 27),
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 21),
                LocalDate.of(2026, 9, 23),
                LocalDate.of(2026, 9, 25),
            ),
            dates,
        )
    }

    @Test
    fun `Intervall von zwei Wochen ueberspringt eine Woche`() {
        val rule = RecurrenceRule.parse("FREQ=WEEKLY;INTERVAL=2;BYDAY=TU", zone)!!
        val dates = RecurrenceExpander.expandDates(
            start = LocalDate.of(2026, 9, 22),
            rule = rule,
            maxDate = LocalDate.of(2026, 10, 20),
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 22),
                LocalDate.of(2026, 10, 6),
                LocalDate.of(2026, 10, 20),
            ),
            dates,
        )
    }

    @Test
    fun `COUNT begrenzt die Anzahl`() {
        val rule = RecurrenceRule.parse("FREQ=DAILY;COUNT=3", zone)!!
        val dates = RecurrenceExpander.expandDates(
            start = LocalDate.of(2026, 9, 21),
            rule = rule,
            maxDate = LocalDate.of(2026, 12, 31),
        )
        assertEquals(3, dates.size)
        assertEquals(LocalDate.of(2026, 9, 23), dates.last())
    }

    @Test
    fun `COUNT zaehlt auch Termine vor dem sichtbaren Zeitraum`() {
        val rule = RecurrenceRule.parse("FREQ=DAILY;COUNT=5", zone)!!
        val dates = RecurrenceExpander.expandDates(
            start = LocalDate.of(2026, 9, 21),
            rule = rule,
            maxDate = LocalDate.of(2026, 12, 31),
            collectFrom = LocalDate.of(2026, 9, 24),
        )
        // Termine am 21., 22. und 23. liegen vor dem Fenster, zaehlen aber mit.
        assertEquals(listOf(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25)), dates)
    }

    @Test
    fun `UNTIL beendet die Serie`() {
        // UNTIL steht in UTC; entscheidend ist der Tag in der Zeitzone des Termins.
        val rule = RecurrenceRule.parse("FREQ=DAILY;UNTIL=20260923T120000Z", zone)!!
        val dates = RecurrenceExpander.expandDates(
            start = LocalDate.of(2026, 9, 21),
            rule = rule,
            maxDate = LocalDate.of(2026, 12, 31),
            untilDate = rule.until?.toLocalDate(zone),
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 21),
                LocalDate.of(2026, 9, 22),
                LocalDate.of(2026, 9, 23),
            ),
            dates,
        )
    }

    @Test
    fun `UNTIL kurz nach Mitternacht UTC zaehlt noch zum Folgetag in Berlin`() {
        // 23:59 UTC am 23. ist in Berlin bereits der 24. um 01:59.
        val rule = RecurrenceRule.parse("FREQ=DAILY;UNTIL=20260923T235900Z", zone)!!
        val dates = RecurrenceExpander.expandDates(
            start = LocalDate.of(2026, 9, 21),
            rule = rule,
            maxDate = LocalDate.of(2026, 12, 31),
            untilDate = rule.until?.toLocalDate(zone),
        )
        assertEquals(LocalDate.of(2026, 9, 24), dates.last())
    }

    @Test
    fun `monatlich am 31 laesst kurze Monate aus`() {
        val rule = RecurrenceRule.parse("FREQ=MONTHLY;BYMONTHDAY=31", zone)!!
        val dates = RecurrenceExpander.expandDates(
            start = LocalDate.of(2026, 1, 31),
            rule = rule,
            maxDate = LocalDate.of(2026, 6, 30),
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 5, 31),
            ),
            dates,
        )
    }

    @Test
    fun `monatlich am letzten Freitag`() {
        val rule = RecurrenceRule.parse("FREQ=MONTHLY;BYDAY=-1FR", zone)!!
        val dates = RecurrenceExpander.expandDates(
            start = LocalDate.of(2026, 9, 25),
            rule = rule,
            maxDate = LocalDate.of(2026, 11, 30),
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 25),
                LocalDate.of(2026, 10, 30),
                LocalDate.of(2026, 11, 27),
            ),
            dates,
        )
    }

    @Test
    fun `Regel wird unveraendert zurueckgeschrieben`() {
        val rule = RecurrenceRule(
            freq = RecurrenceRule.Freq.WEEKLY,
            interval = 2,
            byDay = listOf(ByDay(null, DayOfWeek.MONDAY), ByDay(null, DayOfWeek.THURSDAY)),
        )
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TH", rule.render())
    }

    @Test
    fun `unbekannte Regelteile werden als nicht unterstuetzt erkannt`() {
        val rule = RecurrenceRule.parse("FREQ=WEEKLY;BYSETPOS=2;BYDAY=MO", zone)!!
        assertTrue(!rule.isSupported)
        assertTrue(rule.render().contains("BYSETPOS=2"))
    }
}
