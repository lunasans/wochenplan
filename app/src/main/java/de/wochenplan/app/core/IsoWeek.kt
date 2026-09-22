package de.wochenplan.app.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Eine Kalenderwoche nach ISO 8601: Die Woche beginnt am Montag und die erste
 * Woche eines Jahres ist die Woche, die den 4. Januar enthaelt.
 *
 * [year] ist das *wochenbasierte* Jahr. Der 31.12.2025 liegt zum Beispiel in
 * KW 1 des Jahres 2026.
 */
data class IsoWeek(val year: Int, val week: Int) : Comparable<IsoWeek> {

    init {
        require(week in 1..53) { "Ungueltige Kalenderwoche: $week" }
    }

    /** Montag dieser Kalenderwoche. */
    val monday: LocalDate
        get() = LocalDate.of(year, 1, 4)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks((week - 1).toLong())

    /** Sonntag dieser Kalenderwoche. */
    val sunday: LocalDate get() = monday.plusDays(6)

    /** Die sieben Tage der Woche, von Montag bis Sonntag. */
    val days: List<LocalDate> get() = (0L..6L).map { monday.plusDays(it) }

    /** Stabiler Schluessel fuer Datenbank und Einstellungen, z.B. `2026-W39`. */
    val key: String get() = String.format(Locale.ROOT, "%04d-W%02d", year, week)

    /** Kurzbezeichnung fuer die Oberflaeche, z.B. `KW 39`. */
    val label: String get() = "KW $week"

    /** Datumsspanne fuer die Oberflaeche, z.B. `22.09. – 28.09.2026`. */
    fun rangeLabel(): String {
        val start = monday.format(DAY_MONTH)
        val end = sunday.format(DAY_MONTH_YEAR)
        return "$start – $end"
    }

    fun plusWeeks(amount: Long): IsoWeek = of(monday.plusWeeks(amount))

    fun minusWeeks(amount: Long): IsoWeek = of(monday.minusWeeks(amount))

    fun contains(date: LocalDate): Boolean = !date.isBefore(monday) && !date.isAfter(sunday)

    /** Anzahl der Wochen zwischen dieser und [other] (positiv, wenn [other] spaeter liegt). */
    fun weeksUntil(other: IsoWeek): Long = java.time.temporal.ChronoUnit.WEEKS.between(monday, other.monday)

    override fun compareTo(other: IsoWeek): Int = monday.compareTo(other.monday)

    override fun toString(): String = key

    companion object {
        private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.", Locale.GERMAN)
        private val DAY_MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)

        fun of(date: LocalDate): IsoWeek = IsoWeek(
            year = date.get(IsoFields.WEEK_BASED_YEAR),
            week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
        )

        fun current(clock: java.time.Clock = java.time.Clock.systemDefaultZone()): IsoWeek =
            of(LocalDate.now(clock))

        /** Anzahl der Kalenderwochen (52 oder 53) im wochenbasierten Jahr [year]. */
        fun weeksInYear(year: Int): Int =
            LocalDate.of(year, 12, 28).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

        /** Parst einen Schluessel der Form `2026-W39`, sonst `null`. */
        fun parse(key: String): IsoWeek? {
            val match = Regex("^(\\d{4})-W(\\d{1,2})$").find(key) ?: return null
            val year = match.groupValues[1].toIntOrNull() ?: return null
            val week = match.groupValues[2].toIntOrNull() ?: return null
            if (week < 1 || week > weeksInYear(year)) return null
            return IsoWeek(year, week)
        }
    }
}
