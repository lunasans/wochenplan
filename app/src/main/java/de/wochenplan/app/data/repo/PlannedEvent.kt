package de.wochenplan.app.data.repo

import de.wochenplan.app.data.ical.EventOccurrence

/** Ein Termin, wie ihn die Oberflaeche anzeigt: Wiederholung plus Kalenderbezug. */
data class PlannedEvent(
    val occurrence: EventOccurrence,
    val calendarUrl: String,
    val calendarName: String,
    val colorHex: String?,
    /** Adresse der .ics-Ressource auf dem Server. */
    val href: String,
    val etag: String?,
    val readOnly: Boolean,
) {
    /** Eindeutig auch dann, wenn mehrere Wiederholungen derselben Serie sichtbar sind. */
    val id: String get() = href + "#" + (occurrence.recurrenceId ?: "single")

    val title: String get() = occurrence.summary
    val allDay: Boolean get() = occurrence.allDay
}

/** Was beim Loeschen eines Serientermins passieren soll. */
enum class DeleteScope {
    /** Nur diesen einen Termin streichen (EXDATE). */
    SINGLE_OCCURRENCE,

    /** Die gesamte Serie beziehungsweise den Einzeltermin loeschen. */
    WHOLE_SERIES,
}
