package de.wochenplan.app.ui.week

import de.wochenplan.app.data.ical.EventOccurrence
import de.wochenplan.app.data.repo.PlannedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class EventLayoutTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val day: LocalDate = LocalDate.of(2026, 9, 22)

    private fun event(
        title: String,
        start: LocalDateTime,
        end: LocalDateTime,
        allDay: Boolean = false,
    ): PlannedEvent = PlannedEvent(
        occurrence = EventOccurrence(
            uid = title,
            summary = title,
            description = null,
            location = null,
            allDay = allDay,
            start = start,
            end = end,
            startInstant = start.atZone(zone).toInstant(),
            endInstant = end.atZone(zone).toInstant(),
            recurring = false,
            recurrenceId = null,
        ),
        calendarUrl = "https://example.com/cal/",
        calendarName = "Test",
        colorHex = null,
        href = "https://example.com/cal/$title.ics",
        etag = null,
        readOnly = false,
    )

    @Test
    fun `Termine ohne Ueberschneidung belegen je eine Spalte`() {
        val events = listOf(
            event("A", day.atTime(9, 0), day.atTime(10, 0)),
            event("B", day.atTime(11, 0), day.atTime(12, 0)),
        )
        val layout = EventLayout.layoutDay(events, day)
        assertEquals(2, layout.size)
        assertTrue(layout.all { it.columnCount == 1 && it.column == 0 })
    }

    @Test
    fun `sich ueberschneidende Termine stehen nebeneinander`() {
        val events = listOf(
            event("A", day.atTime(9, 0), day.atTime(11, 0)),
            event("B", day.atTime(10, 0), day.atTime(12, 0)),
        )
        val layout = EventLayout.layoutDay(events, day).sortedBy { it.event.title }
        assertEquals(2, layout.size)
        assertTrue(layout.all { it.columnCount == 2 })
        assertEquals(setOf(0, 1), layout.map { it.column }.toSet())
    }

    @Test
    fun `drei gleichzeitige Termine ergeben drei Spalten`() {
        val events = listOf(
            event("A", day.atTime(9, 0), day.atTime(10, 0)),
            event("B", day.atTime(9, 15), day.atTime(10, 0)),
            event("C", day.atTime(9, 30), day.atTime(10, 0)),
        )
        val layout = EventLayout.layoutDay(events, day)
        assertEquals(3, layout.size)
        assertTrue(layout.all { it.columnCount == 3 })
    }

    @Test
    fun `eine freigewordene Spalte wird wiederverwendet`() {
        val events = listOf(
            event("Lang", day.atTime(9, 0), day.atTime(13, 0)),
            event("Kurz1", day.atTime(9, 0), day.atTime(10, 0)),
            event("Kurz2", day.atTime(11, 0), day.atTime(12, 0)),
        )
        val layout = EventLayout.layoutDay(events, day)
        assertEquals(3, layout.size)
        // Alle liegen im selben Block, es reichen aber zwei Spalten.
        assertTrue(layout.all { it.columnCount == 2 })
        assertEquals(
            layout.first { it.event.title == "Kurz1" }.column,
            layout.first { it.event.title == "Kurz2" }.column,
        )
    }

    @Test
    fun `Termine ueber mehrere Tage werden auf den Tag zugeschnitten`() {
        val events = listOf(
            event("Reise", day.minusDays(1).atTime(22, 0), day.plusDays(1).atTime(6, 0)),
        )
        val layout = EventLayout.layoutDay(events, day)
        assertEquals(1, layout.size)
        assertEquals(0, layout[0].startMinute)
        assertEquals(24 * 60, layout[0].endMinute)
    }

    @Test
    fun `ganztaegige Termine gehoeren nicht ins Zeitraster`() {
        val events = listOf(
            event("Urlaub", day.atStartOfDay(), day.plusDays(1).atStartOfDay(), allDay = true),
        )
        assertTrue(EventLayout.layoutDay(events, day).isEmpty())
    }
}
