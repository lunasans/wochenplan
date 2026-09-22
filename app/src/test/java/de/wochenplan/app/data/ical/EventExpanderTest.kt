package de.wochenplan.app.data.ical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class EventExpanderTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val monday: LocalDate = LocalDate.of(2026, 9, 21)

    private fun expand(ics: String): List<EventOccurrence> {
        val calendar = IcalParser.parse(ics)!!
        return EventExpander.occurrences(
            events = VEventMapper.allEvents(calendar, zone),
            rangeStart = monday,
            rangeEndExclusive = monday.plusDays(7),
            zone = zone,
        )
    }

    @Test
    fun `einfacher Termin wird uebernommen`() {
        val occurrences = expand(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:eins
            SUMMARY:Zahnarzt
            DTSTART;TZID=Europe/Berlin:20260922T090000
            DTEND;TZID=Europe/Berlin:20260922T094500
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()
        )
        assertEquals(1, occurrences.size)
        assertEquals("Zahnarzt", occurrences[0].summary)
        assertEquals(LocalDateTime.of(2026, 9, 22, 9, 0), occurrences[0].start)
        assertEquals(LocalDateTime.of(2026, 9, 22, 9, 45), occurrences[0].end)
    }

    @Test
    fun `Termine ausserhalb der Woche kommen nicht vor`() {
        val occurrences = expand(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:zwei
            SUMMARY:Naechste Woche
            DTSTART;TZID=Europe/Berlin:20260930T090000
            DTEND;TZID=Europe/Berlin:20260930T100000
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()
        )
        assertTrue(occurrences.isEmpty())
    }

    @Test
    fun `Serie wird auf die Woche aufgeloest`() {
        val occurrences = expand(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:serie
            SUMMARY:Standup
            DTSTART;TZID=Europe/Berlin:20260105T093000
            DTEND;TZID=Europe/Berlin:20260105T094500
            RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()
        )
        assertEquals(3, occurrences.size)
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 21),
                LocalDate.of(2026, 9, 23),
                LocalDate.of(2026, 9, 25),
            ),
            occurrences.map { it.startDate },
        )
        assertTrue(occurrences.all { it.recurring })
    }

    @Test
    fun `EXDATE streicht einen einzelnen Termin`() {
        val occurrences = expand(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:serie
            SUMMARY:Standup
            DTSTART;TZID=Europe/Berlin:20260105T093000
            DTEND;TZID=Europe/Berlin:20260105T094500
            RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR
            EXDATE;TZID=Europe/Berlin:20260923T093000
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()
        )
        assertEquals(2, occurrences.size)
        assertTrue(occurrences.none { it.startDate == LocalDate.of(2026, 9, 23) })
    }

    @Test
    fun `abweichender Einzeltermin ersetzt die Wiederholung`() {
        val occurrences = expand(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:serie
            SUMMARY:Standup
            DTSTART;TZID=Europe/Berlin:20260105T093000
            DTEND;TZID=Europe/Berlin:20260105T094500
            RRULE:FREQ=WEEKLY;BYDAY=MO
            END:VEVENT
            BEGIN:VEVENT
            UID:serie
            SUMMARY:Standup (verschoben)
            RECURRENCE-ID;TZID=Europe/Berlin:20260921T093000
            DTSTART;TZID=Europe/Berlin:20260921T140000
            DTEND;TZID=Europe/Berlin:20260921T150000
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()
        )
        assertEquals(1, occurrences.size)
        assertEquals("Standup (verschoben)", occurrences[0].summary)
        assertEquals(LocalDateTime.of(2026, 9, 21, 14, 0), occurrences[0].start)
    }

    @Test
    fun `Uhrzeit bleibt ueber die Zeitumstellung gleich`() {
        // Die Umstellung auf Winterzeit ist am 25.10.2026.
        val calendar = IcalParser.parse(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:serie
            SUMMARY:Sport
            DTSTART;TZID=Europe/Berlin:20261019T180000
            DTEND;TZID=Europe/Berlin:20261019T190000
            RRULE:FREQ=WEEKLY;BYDAY=MO
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()
        )!!
        val laterWeek = LocalDate.of(2026, 10, 26)
        val occurrences = EventExpander.occurrences(
            events = VEventMapper.allEvents(calendar, zone),
            rangeStart = laterWeek,
            rangeEndExclusive = laterWeek.plusDays(7),
            zone = zone,
        )
        assertEquals(1, occurrences.size)
        assertEquals(LocalDateTime.of(2026, 10, 26, 18, 0), occurrences[0].start)
    }

    @Test
    fun `ganztaegiger Termin ueber mehrere Tage`() {
        val occurrences = expand(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:urlaub
            SUMMARY:Urlaub
            DTSTART;VALUE=DATE:20260923
            DTEND;VALUE=DATE:20260926
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()
        )
        assertEquals(1, occurrences.size)
        val occurrence = occurrences[0]
        assertTrue(occurrence.allDay)
        assertEquals(LocalDate.of(2026, 9, 23), occurrence.startDate)
        // DTEND ist exklusiv: Der letzte sichtbare Tag ist der 25.
        assertEquals(LocalDate.of(2026, 9, 25), occurrence.lastVisibleDate)
        assertTrue(occurrence.spansMultipleDays)
    }

    @Test
    fun `ganztaegiger Termin ohne DTEND dauert einen Tag`() {
        val occurrences = expand(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:feiertag
            SUMMARY:Feiertag
            DTSTART;VALUE=DATE:20260924
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()
        )
        assertEquals(1, occurrences.size)
        assertEquals(LocalDate.of(2026, 9, 24), occurrences[0].lastVisibleDate)
        assertTrue(!occurrences[0].spansMultipleDays)
    }
}
