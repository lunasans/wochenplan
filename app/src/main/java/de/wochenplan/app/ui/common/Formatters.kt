package de.wochenplan.app.ui.common

import de.wochenplan.app.data.ical.EventOccurrence
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Einheitliche deutsche Datums- und Zeitformate. */
object Formatters {

    val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
    val dayShort: DateTimeFormatter = DateTimeFormatter.ofPattern("EE", Locale.GERMAN)
    val dayNumber: DateTimeFormatter = DateTimeFormatter.ofPattern("d", Locale.GERMAN)
    val dateShort: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)
    val dateLong: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)

    fun time(value: LocalTime): String = time.format(value)

    fun time(value: LocalDateTime): String = time.format(value)

    fun date(value: LocalDate): String = dateShort.format(value)

    /** Zeitspanne eines Termins, z.B. `09:00 – 10:30` oder `ganztaegig`. */
    fun range(occurrence: EventOccurrence): String = when {
        occurrence.allDay && occurrence.spansMultipleDays ->
            "ganztaegig, ${dateShort.format(occurrence.startDate)} – ${dateShort.format(occurrence.lastVisibleDate)}"

        occurrence.allDay -> "ganztaegig"

        occurrence.spansMultipleDays ->
            "${dateShort.format(occurrence.startDate)} ${time.format(occurrence.start)} – " +
                "${dateShort.format(occurrence.end.toLocalDate())} ${time.format(occurrence.end)}"

        else -> "${time.format(occurrence.start)} – ${time.format(occurrence.end)}"
    }

    /** `vor 3 Minuten`, `vor 2 Stunden`, sonst Datum und Uhrzeit. */
    fun relativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        if (epochMillis <= 0L) return "noch nie"
        val minutes = (now - epochMillis) / 60_000
        return when {
            minutes < 1 -> "gerade eben"
            minutes < 60 -> "vor $minutes Min."
            minutes < 24 * 60 -> "vor ${minutes / 60} Std."
            else -> {
                val dateTime = java.time.Instant.ofEpochMilli(epochMillis)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime()
                "${dateShort.format(dateTime)} ${time.format(dateTime)}"
            }
        }
    }

    /** `Mo`, `Di`, ... fuer 1 bis 7. */
    fun weekdayName(weekday: Int): String = when (weekday) {
        1 -> "Montag"
        2 -> "Dienstag"
        3 -> "Mittwoch"
        4 -> "Donnerstag"
        5 -> "Freitag"
        6 -> "Samstag"
        7 -> "Sonntag"
        else -> "Ohne Tag"
    }
}
