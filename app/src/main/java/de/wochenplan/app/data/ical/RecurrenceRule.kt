package de.wochenplan.app.data.ical

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Ein Eintrag in `BYDAY`, z.B. `MO` oder `-1SU` (letzter Sonntag). */
data class ByDay(val ordinal: Int?, val day: DayOfWeek) {
    fun render(): String = (ordinal?.toString() ?: "") + DAY_CODES.getValue(day)

    companion object {
        val DAY_CODES: Map<DayOfWeek, String> = mapOf(
            DayOfWeek.MONDAY to "MO",
            DayOfWeek.TUESDAY to "TU",
            DayOfWeek.WEDNESDAY to "WE",
            DayOfWeek.THURSDAY to "TH",
            DayOfWeek.FRIDAY to "FR",
            DayOfWeek.SATURDAY to "SA",
            DayOfWeek.SUNDAY to "SU",
        )
        private val CODE_TO_DAY: Map<String, DayOfWeek> = DAY_CODES.entries.associate { it.value to it.key }

        fun parse(token: String): ByDay? {
            val trimmed = token.trim().uppercase()
            if (trimmed.length < 2) return null
            val code = trimmed.takeLast(2)
            val day = CODE_TO_DAY[code] ?: return null
            val prefix = trimmed.dropLast(2)
            val ordinal = if (prefix.isEmpty()) null else prefix.toIntOrNull() ?: return null
            return ByDay(ordinal, day)
        }

        fun dayOf(code: String): DayOfWeek? = CODE_TO_DAY[code.trim().uppercase()]
    }
}

/**
 * Eine Wiederholungsregel (`RRULE`).
 *
 * Unterstuetzt werden FREQ, INTERVAL, COUNT, UNTIL, BYDAY, BYMONTHDAY, BYMONTH
 * und WKST. Das deckt die Regeln ab, die gaengige Kalender-Clients erzeugen.
 * Nicht unterstuetzte Teile werden in [unsupportedParts] aufbewahrt, damit die
 * Oberflaeche warnen und die Regel beim Speichern unveraendert lassen kann.
 */
data class RecurrenceRule(
    val freq: Freq,
    val interval: Int = 1,
    val count: Int? = null,
    val until: IcalTime? = null,
    val byDay: List<ByDay> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val byMonth: List<Int> = emptyList(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val unsupportedParts: Map<String, String> = emptyMap(),
) {

    enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

    val isSupported: Boolean get() = unsupportedParts.isEmpty()

    fun render(): String {
        val parts = mutableListOf("FREQ=${freq.name}")
        if (interval > 1) parts += "INTERVAL=$interval"
        count?.let { parts += "COUNT=$it" }
        until?.let { parts += "UNTIL=${it.render()}" }
        if (byDay.isNotEmpty()) parts += "BYDAY=" + byDay.joinToString(",") { it.render() }
        if (byMonthDay.isNotEmpty()) parts += "BYMONTHDAY=" + byMonthDay.joinToString(",")
        if (byMonth.isNotEmpty()) parts += "BYMONTH=" + byMonth.joinToString(",")
        if (weekStart != DayOfWeek.MONDAY) parts += "WKST=" + ByDay.DAY_CODES.getValue(weekStart)
        for ((key, value) in unsupportedParts) parts += "$key=$value"
        return parts.joinToString(";")
    }

    companion object {
        private val KNOWN_KEYS = setOf("FREQ", "INTERVAL", "COUNT", "UNTIL", "BYDAY", "BYMONTHDAY", "BYMONTH", "WKST")

        fun parse(rule: String, defaultZone: ZoneId): RecurrenceRule? {
            val parts = rule.split(';')
                .mapNotNull { part ->
                    val index = part.indexOf('=')
                    if (index <= 0) null else part.substring(0, index).trim().uppercase() to part.substring(index + 1).trim()
                }
                .toMap()

            val freq = when (parts["FREQ"]?.uppercase()) {
                "DAILY" -> Freq.DAILY
                "WEEKLY" -> Freq.WEEKLY
                "MONTHLY" -> Freq.MONTHLY
                "YEARLY" -> Freq.YEARLY
                else -> return null
            }

            return RecurrenceRule(
                freq = freq,
                interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                count = parts["COUNT"]?.toIntOrNull(),
                until = parts["UNTIL"]?.let { IcalTime.parse(it, null, null, defaultZone) },
                byDay = parts["BYDAY"]?.split(',')?.mapNotNull { ByDay.parse(it) }.orEmpty(),
                byMonthDay = parts["BYMONTHDAY"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty(),
                byMonth = parts["BYMONTH"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty(),
                weekStart = parts["WKST"]?.let { ByDay.dayOf(it) } ?: DayOfWeek.MONDAY,
                unsupportedParts = parts.filterKeys { it !in KNOWN_KEYS },
            )
        }
    }
}

/** Berechnet die Termine einer Serie. */
object RecurrenceExpander {

    private const val MAX_PERIODS = 20_000
    private const val MAX_EMPTY_PERIODS = 60

    /**
     * Liefert die Starttage aller Wiederholungen ab [start] bis einschliesslich
     * [maxDate]. Zurueckgegeben werden hoechstens [limit] Termine ab
     * [collectFrom] (oder ab [start], wenn nicht gesetzt).
     *
     * Die Uhrzeit bleibt dabei immer die des Ursprungstermins; `BYHOUR` und
     * verwandte Regeln kommen in der Praxis nicht vor und werden ignoriert.
     */
    fun expandDates(
        start: LocalDate,
        rule: RecurrenceRule,
        maxDate: LocalDate,
        untilDate: LocalDate? = null,
        collectFrom: LocalDate? = null,
        limit: Int = 1000,
    ): List<LocalDate> {
        val hardEnd = if (untilDate != null && untilDate.isBefore(maxDate)) untilDate else maxDate
        if (hardEnd.isBefore(start)) return emptyList()

        val result = mutableListOf<LocalDate>()
        val interval = rule.interval.coerceAtLeast(1)
        var period = 0L
        var emptyPeriods = 0
        var counted = 0

        while (period < MAX_PERIODS) {
            val candidates = candidatesForPeriod(start, rule, period, interval)
                .filter { !it.isBefore(start) }
                .distinct()
                .sorted()

            if (candidates.isEmpty()) {
                // Regeln wie "jeden 31." haben Monate ohne Treffer. Erst nach vielen
                // leeren Perioden hintereinander ist die Serie wirklich zu Ende.
                if (++emptyPeriods > MAX_EMPTY_PERIODS) break
                period++
                continue
            }
            emptyPeriods = 0

            for (candidate in candidates) {
                if (candidate.isAfter(hardEnd)) return result
                if (rule.count != null && counted >= rule.count) return result
                counted++
                // Termine vor [collectFrom] zaehlen fuer COUNT mit, werden aber
                // nicht zurueckgegeben: So bleibt [limit] fuer das sichtbare
                // Zeitfenster nutzbar, auch wenn die Serie vor Jahren begann.
                if (collectFrom != null && candidate.isBefore(collectFrom)) continue
                result.add(candidate)
                if (result.size >= limit) return result
            }
            period++
        }
        return result
    }

    private fun candidatesForPeriod(
        start: LocalDate,
        rule: RecurrenceRule,
        period: Long,
        interval: Int,
    ): List<LocalDate> {
        val step = period * interval
        return when (rule.freq) {
            RecurrenceRule.Freq.DAILY -> {
                val date = start.plusDays(step)
                if (rule.byDay.isEmpty() || rule.byDay.any { it.day == date.dayOfWeek }) listOf(date) else emptyList()
            }

            RecurrenceRule.Freq.WEEKLY -> {
                if (rule.byDay.isEmpty()) {
                    listOf(start.plusWeeks(step))
                } else {
                    val weekStart = start.with(TemporalAdjusters.previousOrSame(rule.weekStart)).plusWeeks(step)
                    rule.byDay.map { byDay ->
                        val offset = Math.floorMod(byDay.day.value - rule.weekStart.value, 7).toLong()
                        weekStart.plusDays(offset)
                    }
                }
            }

            RecurrenceRule.Freq.MONTHLY -> {
                val month = YearMonth.from(start).plusMonths(step)
                datesInMonth(month, start.dayOfMonth, rule)
            }

            RecurrenceRule.Freq.YEARLY -> {
                val year = start.year + step
                if (year > 9999 || year < 1) return emptyList()
                val months = if (rule.byMonth.isNotEmpty()) rule.byMonth else listOf(start.monthValue)
                months.filter { it in 1..12 }.flatMap { monthValue ->
                    datesInMonth(YearMonth.of(year.toInt(), monthValue), start.dayOfMonth, rule)
                }
            }
        }
    }

    private fun datesInMonth(month: YearMonth, defaultDayOfMonth: Int, rule: RecurrenceRule): List<LocalDate> = when {
        rule.byMonthDay.isNotEmpty() -> rule.byMonthDay.mapNotNull { day ->
            val resolved = if (day < 0) month.lengthOfMonth() + day + 1 else day
            if (resolved in 1..month.lengthOfMonth()) month.atDay(resolved) else null
        }

        rule.byDay.isNotEmpty() -> rule.byDay.flatMap { byDay -> weekdaysInMonth(month, byDay) }

        else -> if (defaultDayOfMonth <= month.lengthOfMonth()) listOf(month.atDay(defaultDayOfMonth)) else emptyList()
    }

    /** Alle Tage des Monats, die auf [byDay] passen (mit oder ohne Ordnungszahl). */
    private fun weekdaysInMonth(month: YearMonth, byDay: ByDay): List<LocalDate> {
        val all = generateSequence(month.atDay(1).with(TemporalAdjusters.nextOrSame(byDay.day))) { it.plusWeeks(1) }
            .takeWhile { it.month == month.month && it.year == month.year }
            .toList()
        val ordinal = byDay.ordinal ?: return all
        val index = if (ordinal > 0) ordinal - 1 else all.size + ordinal
        return listOfNotNull(all.getOrNull(index))
    }
}
