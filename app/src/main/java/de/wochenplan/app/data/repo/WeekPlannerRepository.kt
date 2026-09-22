package de.wochenplan.app.data.repo

import de.wochenplan.app.core.IsoWeek
import de.wochenplan.app.data.ai.ContextEvent
import de.wochenplan.app.data.ai.ContextTask
import de.wochenplan.app.data.ai.PlanEntryKind
import de.wochenplan.app.data.ai.WeekPlanContext
import de.wochenplan.app.data.ai.WeekPlanEntry
import de.wochenplan.app.data.ai.WeekPlanException
import de.wochenplan.app.data.ai.WeekPlanProposal
import de.wochenplan.app.data.ai.WeekPlanner
import de.wochenplan.app.data.db.TaskDao
import de.wochenplan.app.data.ical.EventDraft
import de.wochenplan.app.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/** Was beim Uebernehmen eines Vorschlags tatsaechlich angelegt wurde. */
data class AppliedPlan(val tasks: Int, val events: Int) {
    val total: Int get() = tasks + events
}

/** Wandelt Vorschlaege in Aufgaben und Termine um. Ohne Android-Bezug, damit pruefbar. */
object WeekPlanApplier {

    /**
     * Baut aus einem Vorschlag einen Termin. Gibt `null` zurueck, wenn der
     * Vorschlag keinen vollstaendigen Zeitraum hat.
     */
    fun eventDraft(entry: WeekPlanEntry, week: IsoWeek, zone: ZoneId): EventDraft? {
        if (entry.kind != PlanEntryKind.EVENT) return null
        val weekday = entry.weekday?.takeIf { it in 1..7 } ?: return null
        val start = entry.start ?: return null
        val end = entry.end ?: return null
        if (!end.isAfter(start)) return null

        val date = week.monday.plusDays((weekday - 1).toLong())
        return EventDraft(
            summary = entry.title,
            description = entry.reason.takeIf { it.isNotEmpty() },
            allDay = false,
            startDate = date,
            startTime = start,
            endDate = date,
            endTime = end,
            zone = zone,
        )
    }
}

/**
 * Stellt der KI den Zustand der Woche bereit und uebernimmt danach genau die
 * Vorschlaege, die der Benutzer bestaetigt hat. Ohne Bestaetigung wird nichts
 * geschrieben – weder eine Aufgabe noch ein Termin im Kalender.
 */
class WeekPlannerRepository(
    private val planner: WeekPlanner,
    private val settingsStore: SettingsStore,
    private val calendarRepository: CalendarRepository,
    private val taskRepository: TaskRepository,
    private val taskDao: TaskDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun isConfigured(): Boolean = !settingsStore.aiApiKey().isNullOrBlank()

    /** Sammelt Termine, offene Aufgaben und Einstellungen der Woche. */
    suspend fun buildContext(week: IsoWeek, goals: String): WeekPlanContext {
        val settings = settingsStore.current()
        val events = calendarRepository.observeWeek(week).first()
        val openTasks = taskDao.openTasksOfWeek(week.key)

        return WeekPlanContext(
            week = week,
            goals = goals,
            dayStartHour = settings.dayStartHour,
            dayEndHour = settings.dayEndHour,
            focusMinutes = settings.focusMinutes,
            events = events.map { event ->
                ContextEvent(
                    title = event.title,
                    weekday = event.occurrence.startDate.dayOfWeek.value,
                    start = if (event.allDay) null else event.occurrence.start.toLocalTime(),
                    end = if (event.allDay) null else event.occurrence.end.toLocalTime(),
                    allDay = event.allDay,
                )
            },
            openTasks = openTasks.map { task ->
                ContextTask(
                    title = task.title,
                    weekday = task.weekday,
                    plannedPomodoros = task.plannedPomodoros,
                )
            },
        )
    }

    suspend fun plan(week: IsoWeek, goals: String): Result<WeekPlanProposal> = runCatching {
        val apiKey = settingsStore.aiApiKey()
        if (apiKey.isNullOrBlank()) {
            throw WeekPlanException(
                "Fuer die KI-Planung fehlt der API-Schluessel. Er laesst sich in den Einstellungen hinterlegen."
            )
        }
        planner.plan(buildContext(week, goals), apiKey)
    }

    /**
     * Uebernimmt die bestaetigten Vorschlaege. Aufgaben landen in der Wochenliste,
     * Termine im gewaehlten Kalender.
     */
    suspend fun apply(
        week: IsoWeek,
        entries: List<WeekPlanEntry>,
        calendarUrl: String?,
    ): Result<AppliedPlan> = runCatching {
        var tasks = 0
        var events = 0

        for (entry in entries) {
            val draft = WeekPlanApplier.eventDraft(entry, week, zone)
            if (draft != null && calendarUrl != null) {
                calendarRepository.createEvent(calendarUrl, draft, week).getOrThrow()
                events++
            } else {
                // Ohne Kalender oder ohne Zeitraum wird der Vorschlag zur Aufgabe.
                taskRepository.add(
                    week = week,
                    title = entry.title,
                    weekday = entry.weekday,
                    plannedPomodoros = entry.plannedPomodoros,
                )
                tasks++
            }
        }
        AppliedPlan(tasks = tasks, events = events)
    }
}
