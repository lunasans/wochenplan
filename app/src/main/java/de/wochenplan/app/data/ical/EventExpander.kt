package de.wochenplan.app.data.ical

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Loest die VEVENTs eines iCalendar-Objekts in konkrete Termine eines Zeitraums auf.
 *
 * Beruecksichtigt werden Serien (`RRULE`), gestrichene Einzeltermine (`EXDATE`)
 * und abweichende Einzeltermine (`RECURRENCE-ID`) — auch dann, wenn ein solcher
 * Einzeltermin in eine andere Woche verschoben wurde.
 */
object EventExpander {

    private const val MAX_OCCURRENCES_PER_RANGE = 400

    fun occurrences(
        events: List<VEvent>,
        rangeStart: LocalDate,
        rangeEndExclusive: LocalDate,
        zone: ZoneId,
    ): List<EventOccurrence> {
        val master = events.firstOrNull { !it.isOverride }
        val overrides = events.filter { it.isOverride && it.recurrenceId != null }
        val result = mutableListOf<EventOccurrence>()

        val rangeStartInstant = rangeStart.atStartOfDay(zone).toInstant()
        val rangeEndInstant = rangeEndExclusive.atStartOfDay(zone).toInstant()

        fun addIfInRange(occurrence: EventOccurrence) {
            if (occurrence.endInstant > rangeStartInstant && occurrence.startInstant < rangeEndInstant) {
                result.add(occurrence)
            }
        }

        if (master != null && !master.isCancelled) {
            val overridesByKey = overrides.associateBy { it.recurrenceId!!.render() }
            if (master.rrule == null) {
                addIfInRange(toOccurrence(master, master.start, master.end, zone, recurring = false, recurrenceId = null))
            } else {
                for (occurrence in expandSeries(master, overridesByKey.keys, rangeStart, rangeEndExclusive, zone)) {
                    addIfInRange(occurrence)
                }
            }
        }

        for (override in overrides) {
            if (override.isCancelled) continue
            addIfInRange(
                toOccurrence(
                    event = override,
                    start = override.start,
                    end = override.end,
                    zone = zone,
                    recurring = true,
                    recurrenceId = override.recurrenceId!!.render(),
                )
            )
        }

        return result.sortedWith(compareBy({ it.startInstant }, { it.summary }))
    }

    private fun expandSeries(
        master: VEvent,
        overriddenKeys: Set<String>,
        rangeStart: LocalDate,
        rangeEndExclusive: LocalDate,
        zone: ZoneId,
    ): List<EventOccurrence> {
        val rule = master.rrule ?: return emptyList()
        val eventZone = (master.start as? IcalTime.Timed)?.value?.zone ?: zone
        val startDate = master.start.toLocalDate(eventZone)
        val startTime = master.start.localTimeOrMidnight(eventZone)

        val durationDays = allDayDurationInDays(master)
        val duration = timedDuration(master)

        // Ein Termin kann vor dem Zeitraum beginnen und in ihn hineinreichen.
        val lookBackDays = (if (master.allDay) durationDays else duration.toDays() + 1).coerceIn(1, 366)
        val untilDate = rule.until?.toLocalDate(eventZone)

        val dates = RecurrenceExpander.expandDates(
            start = startDate,
            rule = rule,
            maxDate = rangeEndExclusive.minusDays(1),
            untilDate = untilDate,
            collectFrom = rangeStart.minusDays(lookBackDays),
            limit = MAX_OCCURRENCES_PER_RANGE,
        )

        val excluded = master.exDates.map { it.render() }.toSet()
        val result = mutableListOf<EventOccurrence>()

        for (date in dates) {
            val occurrenceStart: IcalTime = if (master.allDay) {
                IcalTime.AllDay(date)
            } else {
                IcalTime.Timed(ZonedDateTime.of(date, startTime, eventZone))
            }
            val key = occurrenceStart.render()
            if (key in excluded) continue
            if (key in overriddenKeys) continue
            // UNTIL kann mitten am Tag liegen; dann faellt der letzte Termin weg.
            if (rule.until != null && occurrenceStart.toInstant(zone) > rule.until.toInstant(zone)) continue

            val occurrenceEnd: IcalTime = if (master.allDay) {
                IcalTime.AllDay(date.plusDays(durationDays))
            } else {
                (occurrenceStart as IcalTime.Timed).let { IcalTime.Timed(it.value.plus(duration)) }
            }
            result.add(toOccurrence(master, occurrenceStart, occurrenceEnd, zone, recurring = true, recurrenceId = key))
        }
        return result
    }

    /** Laenge eines ganztaegigen Termins in Tagen (DTEND ist exklusiv). */
    private fun allDayDurationInDays(event: VEvent): Long {
        val start = (event.start as? IcalTime.AllDay)?.date ?: return 1
        val end = (event.end as? IcalTime.AllDay)?.date ?: return 1
        return ChronoUnit.DAYS.between(start, end).coerceAtLeast(1)
    }

    private fun timedDuration(event: VEvent): Duration {
        val start = (event.start as? IcalTime.Timed)?.value ?: return Duration.ofHours(1)
        val end = (event.end as? IcalTime.Timed)?.value ?: return Duration.ofHours(1)
        val duration = Duration.between(start, end)
        return if (duration.isNegative || duration.isZero) Duration.ofMinutes(30) else duration
    }

    private fun toOccurrence(
        event: VEvent,
        start: IcalTime,
        end: IcalTime,
        zone: ZoneId,
        recurring: Boolean,
        recurrenceId: String?,
    ): EventOccurrence {
        val allDay = start.isAllDay
        val startLocal = if (allDay) start.toLocalDate(zone).atStartOfDay() else start.toLocalDateTime(zone)
        val endLocalRaw = if (allDay) end.toLocalDate(zone).atStartOfDay() else end.toLocalDateTime(zone)
        val endLocal = if (endLocalRaw.isAfter(startLocal)) endLocalRaw else startLocal.plusMinutes(30)

        return EventOccurrence(
            uid = event.uid,
            summary = event.summary.ifBlank { "(ohne Titel)" },
            description = event.description,
            location = event.location,
            allDay = allDay,
            start = startLocal,
            end = endLocal,
            startInstant = start.toInstant(zone),
            endInstant = end.toInstant(zone).coerceAtLeast(start.toInstant(zone).plusSeconds(60)),
            recurring = recurring,
            recurrenceId = recurrenceId,
        )
    }
}
