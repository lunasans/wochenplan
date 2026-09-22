package de.wochenplan.app.data.ical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class IcalWriterTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    private fun draft(
        summary: String = "Besprechung",
        allDay: Boolean = false,
        recurrence: RecurrenceRule? = null,
    ) = EventDraft(
        uid = "test-uid@wochenplan",
        summary = summary,
        description = "Mit Notiz; und Semikolon",
        location = "Raum 3",
        allDay = allDay,
        startDate = LocalDate.of(2026, 9, 22),
        startTime = LocalTime.of(9, 0),
        endDate = LocalDate.of(2026, 9, 22),
        endTime = LocalTime.of(10, 30),
        recurrence = recurrence,
        zone = zone,
    )

    @Test
    fun `neuer Termin enthaelt alle Pflichtangaben`() {
        val ics = IcalWriter.newEvent(draft())
        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("VERSION:2.0"))
        assertTrue(ics.contains("UID:test-uid@wochenplan"))
        assertTrue(ics.contains("DTSTART;TZID=Europe/Berlin:20260922T090000"))
        assertTrue(ics.contains("DTEND;TZID=Europe/Berlin:20260922T103000"))
        assertTrue(ics.contains("SUMMARY:Besprechung"))
        assertTrue(ics.contains("DESCRIPTION:Mit Notiz\; und Semikolon"))
        assertTrue(ics.contains("DTSTAMP:"))
    }

    @Test
    fun `zu einem Termin mit Zeitzone gehoert eine VTIMEZONE`() {
        val ics = IcalWriter.newEvent(draft())
        assertTrue(ics.contains("BEGIN:VTIMEZONE"))
        assertTrue(ics.contains("TZID:Europe/Berlin"))
        assertTrue(ics.contains("BEGIN:DAYLIGHT"))
        assertTrue(ics.contains("BEGIN:STANDARD"))
        // Die europaeische Umstellung ist jeweils am letzten Sonntag.
        assertTrue(ics.contains("BYDAY=-1SU"))
        // Die VTIMEZONE muss vor dem Termin stehen.
        assertTrue(ics.indexOf("BEGIN:VTIMEZONE") < ics.indexOf("BEGIN:VEVENT"))
    }

    @Test
    fun `ganztaegiger Termin nutzt Datumswerte und exklusives Ende`() {
        val ics = IcalWriter.newEvent(draft(allDay = true))
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20260922"))
        assertTrue(ics.contains("DTEND;VALUE=DATE:20260923"))
        assertTrue(!ics.contains("BEGIN:VTIMEZONE"))
    }

    @Test
    fun `Serie wird mit RRULE geschrieben`() {
        val ics = IcalWriter.newEvent(
            draft(recurrence = RecurrenceRule(RecurrenceRule.Freq.WEEKLY, interval = 2))
        )
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;INTERVAL=2"))
    }

    @Test
    fun `beim Aendern bleiben fremde Angaben erhalten`() {
        val original = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Fremde App//DE
            BEGIN:VEVENT
            UID:test-uid@wochenplan
            SUMMARY:Alter Titel
            DTSTART;TZID=Europe/Berlin:20260922T090000
            DTEND;TZID=Europe/Berlin:20260922T100000
            SEQUENCE:4
            ATTENDEE;CN=Hans:mailto:hans@example.com
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-PT10M
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val updated = IcalWriter.updateEvent(original, draft(summary = "Neuer Titel"))
        assertNotNull(updated)
        assertTrue(updated!!.contains("SUMMARY:Neuer Titel"))
        assertTrue(!updated.contains("SUMMARY:Alter Titel"))
        assertTrue(updated.contains("ATTENDEE;CN=Hans:mailto:hans@example.com"))
        assertTrue(updated.contains("BEGIN:VALARM"))
        assertTrue(updated.contains("SEQUENCE:5"))
    }

    @Test
    fun `einzelner Termin wird per EXDATE gestrichen`() {
        val original = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:serie
            SUMMARY:Standup
            DTSTART;TZID=Europe/Berlin:20260921T093000
            DTEND;TZID=Europe/Berlin:20260921T094500
            RRULE:FREQ=WEEKLY;BYDAY=MO
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val updated = IcalWriter.excludeOccurrence(original, "20260928T093000", zone)
        assertNotNull(updated)
        assertTrue(updated!!.contains("EXDATE;TZID=Europe/Berlin:20260928T093000"))

        // Die Serie selbst bleibt bestehen.
        val calendar = IcalParser.parse(updated)!!
        val event = VEventMapper.allEvents(calendar, zone).first()
        assertEquals(1, event.exDates.size)
        assertNotNull(event.rrule)
    }

    @Test
    fun `abweichender Einzeltermin wird beim Streichen entfernt`() {
        val original = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:serie
            DTSTART;TZID=Europe/Berlin:20260921T093000
            DTEND;TZID=Europe/Berlin:20260921T094500
            RRULE:FREQ=WEEKLY;BYDAY=MO
            END:VEVENT
            BEGIN:VEVENT
            UID:serie
            RECURRENCE-ID;TZID=Europe/Berlin:20260928T093000
            DTSTART;TZID=Europe/Berlin:20260928T140000
            DTEND;TZID=Europe/Berlin:20260928T150000
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val updated = IcalWriter.excludeOccurrence(original, "20260928T093000", zone)!!
        assertTrue(!updated.contains("RECURRENCE-ID"))
        assertEquals(1, IcalParser.parse(updated)!!.components("VEVENT").size)
    }
}
