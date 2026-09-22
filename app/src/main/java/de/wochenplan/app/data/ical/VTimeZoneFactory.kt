package de.wochenplan.app.data.ical

import java.time.Instant
import java.time.Year
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.zone.ZoneOffsetTransitionRule
import java.util.Locale
import kotlin.math.abs

/**
 * Erzeugt eine VTIMEZONE-Komponente zu einer [ZoneId].
 *
 * Termine mit Uhrzeit werden mit `TZID` geschrieben statt in UTC. Nur so bleibt
 * bei Serienterminen die Uhrzeit ueber die Zeitumstellung hinweg gleich – ein
 * woechentlicher Termin um 9:00 findet sonst nach der Umstellung um 10:00 statt.
 * CalDAV-Server erwarten dafuer die passende VTIMEZONE im selben Objekt.
 */
object VTimeZoneFactory {

    private val LOCAL_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.ROOT)

    fun create(zone: ZoneId): IcalComponent? {
        if (zone == ZoneOffset.UTC || zone.id == "UTC" || zone.id == "Z") return null

        val component = IcalComponent("VTIMEZONE")
        component.setProperty("TZID", zone.id)

        val rules = zone.rules
        val transitionRules = rules.transitionRules

        if (transitionRules.isEmpty()) {
            val offset = rules.getOffset(Instant.now())
            val standard = IcalComponent("STANDARD")
            standard.setProperty("DTSTART", "19700101T000000")
            standard.setProperty("TZOFFSETFROM", formatOffset(offset))
            standard.setProperty("TZOFFSETTO", formatOffset(offset))
            component.components.add(standard)
            return component
        }

        val year = Year.now().value
        for (rule in transitionRules) {
            val isDaylight = rule.offsetAfter.totalSeconds > rule.offsetBefore.totalSeconds
            val sub = IcalComponent(if (isDaylight) "DAYLIGHT" else "STANDARD")
            val transition = rule.createTransition(year)
            sub.setProperty("DTSTART", LOCAL_FORMAT.format(transition.dateTimeBefore))
            sub.setProperty("TZOFFSETFROM", formatOffset(rule.offsetBefore))
            sub.setProperty("TZOFFSETTO", formatOffset(rule.offsetAfter))
            sub.setProperty("RRULE", recurrenceFor(rule))
            component.components.add(sub)
        }
        return component
    }

    /** Wandelt eine Umstellungsregel in eine jaehrliche RRULE um. */
    private fun recurrenceFor(rule: ZoneOffsetTransitionRule): String {
        val month = rule.month.value
        val dayOfWeek = rule.dayOfWeek
            ?: return "FREQ=YEARLY;BYMONTH=$month;BYMONTHDAY=${rule.dayOfMonthIndicator}"

        val indicator = rule.dayOfMonthIndicator
        val lengthOfMonth = rule.month.length(false)
        // Der Indikator meint "der erste solche Wochentag ab diesem Tag". Passt
        // danach keine weitere Woche mehr in den Monat, ist es der letzte seiner
        // Art: Der 25. Maerz trifft so immer den letzten Sonntag (25 + 7 > 31).
        val ordinal = when {
            indicator < 0 -> -1
            indicator + 7 > lengthOfMonth -> -1
            else -> ((indicator - 1) / 7) + 1
        }
        val code = ByDay.DAY_CODES.getValue(dayOfWeek)
        return "FREQ=YEARLY;BYMONTH=$month;BYDAY=$ordinal$code"
    }

    /** Formatiert einen Offset als `+0200`. */
    fun formatOffset(offset: ZoneOffset): String {
        val total = offset.totalSeconds
        val sign = if (total < 0) "-" else "+"
        val absolute = abs(total)
        val hours = absolute / 3600
        val minutes = (absolute % 3600) / 60
        val seconds = absolute % 60
        return if (seconds == 0) {
            String.format(Locale.ROOT, "%s%02d%02d", sign, hours, minutes)
        } else {
            String.format(Locale.ROOT, "%s%02d%02d%02d", sign, hours, minutes, seconds)
        }
    }
}
