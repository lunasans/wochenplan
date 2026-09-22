package de.wochenplan.app.data.ical

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Ein VEVENT, so wie diese App es interpretiert. */
data class VEvent(
    val uid: String,
    val summary: String,
    val description: String?,
    val location: String?,
    val start: IcalTime,
    val end: IcalTime,
    val rrule: RecurrenceRule?,
    val exDates: List<IcalTime>,
    /** Gesetzt, wenn dieses VEVENT eine einzelne abweichende Wiederholung ist. */
    val recurrenceId: IcalTime?,
    val status: String?,
    val sequence: Int,
) {
    val isRecurring: Boolean get() = rrule != null
    val isOverride: Boolean get() = recurrenceId != null
    val isCancelled: Boolean get() = status.equals("CANCELLED", ignoreCase = true)
    val allDay: Boolean get() = start.isAllDay
}

/** Ein konkreter Termin im Kalender, also eine aufgeloeste Wiederholung. */
data class EventOccurrence(
    val uid: String,
    val summary: String,
    val description: String?,
    val location: String?,
    val allDay: Boolean,
    val start: LocalDateTime,
    /** Ende, exklusiv. Bei ganztaegigen Terminen Mitternacht des Folgetags. */
    val end: LocalDateTime,
    val startInstant: Instant,
    val endInstant: Instant,
    /** Gehoert der Termin zu einer Serie? */
    val recurring: Boolean,
    /** Kennung dieser Wiederholung, zum Loeschen oder Aendern einzelner Termine. */
    val recurrenceId: String?,
) {
    val startDate: LocalDate get() = start.toLocalDate()

    /** Letzter Tag, an dem der Termin sichtbar ist (bei ganztaegig inklusive). */
    val lastVisibleDate: LocalDate
        get() {
            val endDate = end.toLocalDate()
            return if (end.toLocalTime() == java.time.LocalTime.MIDNIGHT && endDate.isAfter(startDate)) {
                endDate.minusDays(1)
            } else {
                endDate
            }
        }

    val spansMultipleDays: Boolean get() = lastVisibleDate.isAfter(startDate)
}

object VEventMapper {

    /** Liest ein VEVENT aus dem Komponentenbaum. */
    fun fromComponent(component: IcalComponent, defaultZone: ZoneId): VEvent? {
        if (!component.name.equals("VEVENT", ignoreCase = true)) return null
        val dtStartProperty = component.findProperty("DTSTART") ?: return null
        val start = IcalTime.parse(dtStartProperty, defaultZone) ?: return null
        val end = resolveEnd(component, start, defaultZone)

        return VEvent(
            uid = component.value("UID").orEmpty(),
            summary = component.textValue("SUMMARY").orEmpty(),
            description = component.textValue("DESCRIPTION")?.takeIf { it.isNotBlank() },
            location = component.textValue("LOCATION")?.takeIf { it.isNotBlank() },
            start = start,
            end = end,
            rrule = component.value("RRULE")?.let { RecurrenceRule.parse(it, defaultZone) },
            exDates = component.findProperties("EXDATE").flatMap { IcalTime.parseList(it, defaultZone) },
            recurrenceId = component.findProperty("RECURRENCE-ID")?.let { IcalTime.parse(it, defaultZone) },
            status = component.value("STATUS"),
            sequence = component.value("SEQUENCE")?.toIntOrNull() ?: 0,
        )
    }

    /** Alle VEVENTs eines iCalendar-Objekts. */
    fun allEvents(calendar: IcalComponent, defaultZone: ZoneId): List<VEvent> =
        calendar.components("VEVENT").mapNotNull { fromComponent(it, defaultZone) }

    private fun resolveEnd(component: IcalComponent, start: IcalTime, defaultZone: ZoneId): IcalTime {
        component.findProperty("DTEND")?.let { property ->
            IcalTime.parse(property, defaultZone)?.let { return it }
        }
        component.value("DURATION")?.let { raw ->
            parseDuration(raw)?.let { duration -> return start.plus(duration) }
        }
        return when (start) {
            // Ein ganztaegiger Termin ohne DTEND dauert genau einen Tag.
            is IcalTime.AllDay -> IcalTime.AllDay(start.date.plusDays(1))
            is IcalTime.Timed -> start
        }
    }

    /** Liest eine Dauer im Format `P1DT2H30M`. */
    fun parseDuration(raw: String): Duration? {
        val match = DURATION_PATTERN.find(raw.trim().uppercase()) ?: return null
        val (sign, weeks, days, hours, minutes, seconds) = match.destructured
        var duration = Duration.ZERO
        if (weeks.isNotEmpty()) duration = duration.plusDays(weeks.toLong() * 7)
        if (days.isNotEmpty()) duration = duration.plusDays(days.toLong())
        if (hours.isNotEmpty()) duration = duration.plusHours(hours.toLong())
        if (minutes.isNotEmpty()) duration = duration.plusMinutes(minutes.toLong())
        if (seconds.isNotEmpty()) duration = duration.plusSeconds(seconds.toLong())
        return if (sign == "-") duration.negated() else duration
    }

    private val DURATION_PATTERN =
        Regex("^([+-])?P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$")
}

/** Verschiebt einen Zeitpunkt um [duration]. */
fun IcalTime.plus(duration: Duration): IcalTime = when (this) {
    is IcalTime.AllDay -> IcalTime.AllDay(date.plusDays(duration.toDays()))
    is IcalTime.Timed -> IcalTime.Timed(value.plus(duration))
}
