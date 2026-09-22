package de.wochenplan.app.data.ical

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Die Eingaben aus der Terminbearbeitung. */
data class EventDraft(
    val uid: String = "${UUID.randomUUID()}@wochenplan",
    val summary: String,
    val description: String? = null,
    val location: String? = null,
    val allDay: Boolean = false,
    val startDate: LocalDate,
    val startTime: LocalTime = LocalTime.of(9, 0),
    /** Letzter Tag des Termins, bei ganztaegigen Terminen einschliesslich. */
    val endDate: LocalDate,
    val endTime: LocalTime = LocalTime.of(10, 0),
    val recurrence: RecurrenceRule? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    fun startValue(): IcalTime =
        if (allDay) IcalTime.AllDay(startDate) else IcalTime.Timed(ZonedDateTime.of(startDate, startTime, zone))

    /** DTEND ist exklusiv: Ein eintaegiger Termin endet am Folgetag. */
    fun endValue(): IcalTime =
        if (allDay) IcalTime.AllDay(endDate.plusDays(1)) else IcalTime.Timed(ZonedDateTime.of(endDate, endTime, zone))

    fun isValid(): Boolean {
        val start = startValue()
        val end = endValue()
        return when {
            summary.isBlank() -> false
            start is IcalTime.AllDay && end is IcalTime.AllDay -> end.date.isAfter(start.date)
            start is IcalTime.Timed && end is IcalTime.Timed -> end.value.isAfter(start.value)
            else -> false
        }
    }
}

/** Schreibt iCalendar-Objekte fuer den CalDAV-Server. */
object IcalWriter {

    const val PRODID = "-//Wochenplan//Android Wochenplaner//DE"

    /** Ein neues iCalendar-Objekt mit genau einem Termin. */
    fun newEvent(draft: EventDraft): String {
        val calendar = IcalComponent("VCALENDAR")
        calendar.setProperty("VERSION", "2.0")
        calendar.setProperty("PRODID", PRODID)
        calendar.setProperty("CALSCALE", "GREGORIAN")

        val event = IcalComponent("VEVENT")
        event.setProperty("UID", draft.uid)
        event.setProperty("CREATED", nowStamp())
        calendar.components.add(event)

        applyDraft(event, draft)
        ensureTimeZone(calendar, draft.zone)
        return calendar.serialize()
    }

    /**
     * Uebernimmt die Aenderungen in ein bestehendes Objekt. Unbekannte
     * Eigenschaften (Teilnehmer, Erinnerungen, X-Properties) bleiben erhalten.
     *
     * Verschiebt sich der Serienstart, verlieren abweichende Einzeltermine und
     * Ausnahmen ihren Bezug; sie werden dann entfernt.
     */
    fun updateEvent(existingIcs: String, draft: EventDraft): String? {
        val calendar = IcalParser.parse(existingIcs) ?: return null
        val master = masterEvent(calendar) ?: return null
        val previousStart = master.findProperty("DTSTART")?.value

        applyDraft(master, draft)

        if (previousStart != null && previousStart != draft.startValue().render()) {
            master.removeProperty("EXDATE")
            calendar.components.removeAll { it.name.equals("VEVENT", true) && it.findProperty("RECURRENCE-ID") != null }
        }
        ensureTimeZone(calendar, draft.zone)
        return calendar.serialize()
    }

    /**
     * Streicht einen einzelnen Termin aus einer Serie, indem ein EXDATE ergaenzt
     * und ein eventuell vorhandener abweichender Einzeltermin entfernt wird.
     *
     * @param occurrenceKey der Wert aus [EventOccurrence.recurrenceId]
     */
    fun excludeOccurrence(existingIcs: String, occurrenceKey: String, defaultZone: ZoneId): String? {
        val calendar = IcalParser.parse(existingIcs) ?: return null
        val master = masterEvent(calendar) ?: return null
        val dtStart = master.findProperty("DTSTART") ?: return null

        val params = linkedMapOf<String, String>()
        dtStart.param("TZID")?.let { params["TZID"] = it }
        dtStart.param("VALUE")?.let { params["VALUE"] = it }
        if (occurrenceKey.length == 8) params["VALUE"] = "DATE"

        val alreadyExcluded = master.findProperties("EXDATE")
            .any { property -> property.value.split(',').any { it.trim() == occurrenceKey } }
        if (!alreadyExcluded) {
            master.addProperty(IcalProperty("EXDATE", params, occurrenceKey))
        }

        calendar.components.removeAll { component ->
            component.name.equals("VEVENT", true) &&
                component.findProperty("RECURRENCE-ID")?.let { recurrenceProperty ->
                    IcalTime.parse(recurrenceProperty, defaultZone)?.render() == occurrenceKey
                } == true
        }

        touch(master)
        return calendar.serialize()
    }

    /** Das VEVENT der Serie selbst, also das ohne RECURRENCE-ID. */
    fun masterEvent(calendar: IcalComponent): IcalComponent? =
        calendar.components("VEVENT").firstOrNull { it.findProperty("RECURRENCE-ID") == null }
            ?: calendar.components("VEVENT").firstOrNull()

    private fun applyDraft(event: IcalComponent, draft: EventDraft) {
        event.setProperty("UID", draft.uid)
        event.setTextProperty("SUMMARY", draft.summary)
        event.setTextProperty("DESCRIPTION", draft.description)
        event.setTextProperty("LOCATION", draft.location)

        val start = draft.startValue()
        val end = draft.endValue()
        event.setProperty("DTSTART", start.render(), start.renderParams())
        event.setProperty("DTEND", end.render(), end.renderParams())
        event.removeProperty("DURATION")

        if (draft.recurrence != null) {
            event.setProperty("RRULE", draft.recurrence.render())
        } else {
            event.removeProperty("RRULE")
        }
        touch(event)
    }

    /** Aktualisiert die Zeitstempel und erhoeht SEQUENCE. */
    private fun touch(event: IcalComponent) {
        val sequence = event.value("SEQUENCE")?.toIntOrNull() ?: 0
        event.setProperty("SEQUENCE", (sequence + 1).toString())
        event.setProperty("DTSTAMP", nowStamp())
        event.setProperty("LAST-MODIFIED", nowStamp())
    }

    /** Ergaenzt fehlende VTIMEZONE-Komponenten fuer alle verwendeten TZIDs. */
    private fun ensureTimeZone(calendar: IcalComponent, zone: ZoneId) {
        val usedIds = calendar.components("VEVENT")
            .flatMap { it.properties }
            .mapNotNull { it.param("TZID") }
            .toMutableSet()
        if (usedIds.isEmpty()) return
        usedIds.add(zone.id)

        val existing = calendar.components("VTIMEZONE").mapNotNull { it.value("TZID") }.toSet()
        for (id in usedIds - existing) {
            val resolved = IcalTime.resolveZone(id) ?: continue
            VTimeZoneFactory.create(resolved)?.let { timeZone ->
                // VTIMEZONE muss vor den Terminen stehen.
                calendar.components.add(0, timeZone)
            }
        }
    }

    private fun nowStamp(): String = IcalTime.utc(Instant.now().truncatedTo(ChronoUnit.SECONDS)).render()
}
