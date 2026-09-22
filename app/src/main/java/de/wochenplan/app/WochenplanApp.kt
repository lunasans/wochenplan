package de.wochenplan.app

import android.app.Application
import android.content.Context
import android.os.SystemClock
import de.wochenplan.app.core.IsoWeek
import de.wochenplan.app.data.db.WochenplanDatabase
import de.wochenplan.app.data.prefs.SettingsStore
import de.wochenplan.app.data.repo.CalendarRepository
import de.wochenplan.app.data.repo.FocusRepository
import de.wochenplan.app.data.repo.TaskRepository
import de.wochenplan.app.data.repo.TaskTemplateRepository
import de.wochenplan.app.pomodoro.PomodoroEngine
import de.wochenplan.app.pomodoro.PomodoroNotifications
import de.wochenplan.app.work.WeekSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Einfache, manuelle Abhaengigkeitsverwaltung.
 *
 * Die App hat wenige, klar getrennte Bausteine; ein Framework dafuer waere mehr
 * Aufwand als Nutzen.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Die gerade betrachtete Kalenderwoche.
     *
     * Wochenansicht und Aufgabenliste teilen sich diese Auswahl: Wer in der
     * Wochenansicht blaettert, sieht danach in den Aufgaben dieselbe Woche.
     */
    val selectedWeek = MutableStateFlow(IsoWeek.current())

    val database: WochenplanDatabase by lazy { WochenplanDatabase.create(appContext) }

    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }

    val calendarRepository: CalendarRepository by lazy {
        CalendarRepository(
            settingsStore = settingsStore,
            calendarDao = database.calendarDao(),
            eventCacheDao = database.eventCacheDao(),
        )
    }

    val taskRepository: TaskRepository by lazy { TaskRepository(database.taskDao()) }

    val focusRepository: FocusRepository by lazy { FocusRepository(database.focusSessionDao()) }

    val taskTemplateRepository: TaskTemplateRepository by lazy {
        TaskTemplateRepository(database.taskTemplateDao(), database.taskDao())
    }

    val pomodoroEngine: PomodoroEngine by lazy {
        PomodoroEngine(
            scope = applicationScope,
            settingsStore = settingsStore,
            focusRepository = focusRepository,
            taskRepository = taskRepository,
            elapsedRealtime = { SystemClock.elapsedRealtime() },
        )
    }
}

class WochenplanApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        PomodoroNotifications.createChannels(this)
        WeekSyncWorker.schedule(this)
    }
}
