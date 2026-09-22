package de.wochenplan.app.data.ai

import de.wochenplan.app.core.IsoWeek
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalTime

/** Was die KI vorschlagen darf. */
enum class PlanEntryKind {
    /** Eine Aufgabe in der Wochenliste. */
    TASK,

    /** Ein Termin mit Uhrzeit, der in den Kalender geschrieben werden kann. */
    EVENT,
}

/** Ein einzelner Vorschlag, den der Benutzer annehmen oder verwerfen kann. */
data class WeekPlanEntry(
    /** Stabile Kennung fuer die Auswahl in der Oberflaeche. */
    val id: String,
    val title: String,
    val kind: PlanEntryKind,
    /** 1 = Montag bis 7 = Sonntag, `null` fuer "ohne festen Tag". */
    val weekday: Int?,
    val start: LocalTime?,
    val end: LocalTime?,
    val plannedPomodoros: Int,
    val reason: String,
) {
    val hasTimes: Boolean get() = start != null && end != null
}

/** Der komplette Vorschlag fuer eine Woche. Wird erst nach Bestaetigung uebernommen. */
data class WeekPlanProposal(
    val summary: String,
    val entries: List<WeekPlanEntry>,
    val warnings: List<String>,
) {
    val isEmpty: Boolean get() = entries.isEmpty()
}

/** Ein bereits vorhandener Termin, als Randbedingung fuer die Planung. */
data class ContextEvent(
    val title: String,
    val weekday: Int,
    val start: LocalTime?,
    val end: LocalTime?,
    val allDay: Boolean,
)

/** Eine bereits vorhandene, offene Aufgabe der Woche. */
data class ContextTask(
    val title: String,
    val weekday: Int?,
    val plannedPomodoros: Int,
)

/** Alles, was die KI ueber die Woche wissen muss. */
data class WeekPlanContext(
    val week: IsoWeek,
    /** Was sich der Benutzer fuer die Woche vorgenommen hat. */
    val goals: String,
    val dayStartHour: Int,
    val dayEndHour: Int,
    val focusMinutes: Int,
    val events: List<ContextEvent>,
    val openTasks: List<ContextTask>,
)

// --- Das Format, das die KI zurueckliefert ------------------------------

@Serializable
internal data class WeekPlanDto(
    val summary: String = "",
    val entries: List<WeekPlanEntryDto> = emptyList(),
    val warnings: List<String> = emptyList(),
)

@Serializable
internal data class WeekPlanEntryDto(
    val title: String = "",
    val kind: String = "task",
    val weekday: Int = 0,
    @SerialName("startTime") val startTime: String? = null,
    @SerialName("endTime") val endTime: String? = null,
    val plannedPomodoros: Int = 1,
    val reason: String = "",
)

/** Fehler beim Planen, mit einer Meldung fuer die Oberflaeche. */
class WeekPlanException(message: String, cause: Throwable? = null) : Exception(message, cause)
