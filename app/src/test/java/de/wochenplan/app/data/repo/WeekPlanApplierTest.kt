package de.wochenplan.app.data.repo

import de.wochenplan.app.core.IsoWeek
import de.wochenplan.app.data.ai.PlanEntryKind
import de.wochenplan.app.data.ai.WeekPlanEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WeekPlanApplierTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val week = IsoWeek(2026, 39) // Montag ist der 21.09.2026

    private fun entry(
        kind: PlanEntryKind = PlanEntryKind.EVENT,
        weekday: Int? = 3,
        start: LocalTime? = LocalTime.of(9, 0),
        end: LocalTime? = LocalTime.of(10, 30),
    ) = WeekPlanEntry(
        id = "entry-0",
        title = "Konzept schreiben",
        kind = kind,
        weekday = weekday,
        start = start,
        end = end,
        plannedPomodoros = 2,
        reason = "vormittags am ruhigsten",
    )

    @Test
    fun `der Wochentag wird zum richtigen Datum`() {
        val draft = WeekPlanApplier.eventDraft(entry(), week, zone)
        assertNotNull(draft)
        // Wochentag 3 ist Mittwoch, also der 23.09.2026.
        assertEquals(LocalDate.of(2026, 9, 23), draft!!.startDate)
        assertEquals(LocalDate.of(2026, 9, 23), draft.endDate)
        assertEquals(LocalTime.of(9, 0), draft.startTime)
        assertEquals(LocalTime.of(10, 30), draft.endTime)
        assertEquals("Konzept schreiben", draft.summary)
        assertEquals(false, draft.allDay)
    }

    @Test
    fun `Montag und Sonntag liegen an den Raendern der Woche`() {
        assertEquals(
            LocalDate.of(2026, 9, 21),
            WeekPlanApplier.eventDraft(entry(weekday = 1), week, zone)!!.startDate,
        )
        assertEquals(
            LocalDate.of(2026, 9, 27),
            WeekPlanApplier.eventDraft(entry(weekday = 7), week, zone)!!.startDate,
        )
    }

    @Test
    fun `aus einer Aufgabe wird kein Termin`() {
        assertNull(WeekPlanApplier.eventDraft(entry(kind = PlanEntryKind.TASK), week, zone))
    }

    @Test
    fun `unvollstaendige Angaben ergeben keinen Termin`() {
        assertNull(WeekPlanApplier.eventDraft(entry(weekday = null), week, zone))
        assertNull(WeekPlanApplier.eventDraft(entry(start = null), week, zone))
        assertNull(WeekPlanApplier.eventDraft(entry(end = null), week, zone))
        assertNull(
            WeekPlanApplier.eventDraft(
                entry(start = LocalTime.of(11, 0), end = LocalTime.of(10, 0)),
                week,
                zone,
            )
        )
    }

    @Test
    fun `die Begruendung landet in den Notizen des Termins`() {
        val draft = WeekPlanApplier.eventDraft(entry(), week, zone)!!
        assertEquals("vormittags am ruhigsten", draft.description)
    }
}
