package de.wochenplan.app.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.wochenplan.app.AppContainer
import de.wochenplan.app.WochenplanApp
import de.wochenplan.app.core.IsoWeek
import de.wochenplan.app.data.db.CalendarEntity
import de.wochenplan.app.data.repo.CalendarRepository
import de.wochenplan.app.data.repo.DeleteScope
import de.wochenplan.app.data.repo.PlannedEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class WeekUiState(
    val week: IsoWeek = IsoWeek.current(),
    val events: List<PlannedEvent> = emptyList(),
    val calendars: List<CalendarEntity> = emptyList(),
    val accountConfigured: Boolean = false,
    val syncing: Boolean = false,
    val error: String? = null,
    val lastSyncAt: Long = 0L,
    val dayStartHour: Int = 7,
    val dayEndHour: Int = 21,
    val defaultCalendarUrl: String? = null,
) {
    val writableCalendars: List<CalendarEntity> get() = calendars.filter { !it.readOnly }

    /**
     * Der angezeigte Stundenbereich, erweitert um Termine, die frueher beginnen
     * oder spaeter enden als eingestellt. Sonst waeren sie unsichtbar.
     */
    val visibleHourRange: IntRange
        get() {
            var first = dayStartHour
            var last = dayEndHour
            for (event in events) {
                if (event.allDay) continue
                first = minOf(first, event.occurrence.start.hour)
                val endHour = event.occurrence.end.let { if (it.minute > 0) it.hour + 1 else it.hour }
                last = maxOf(last, endHour)
            }
            return first.coerceIn(0, 23)..last.coerceIn(1, 24)
        }
}

class WeekViewModel(
    private val calendarRepository: CalendarRepository,
    private val container: AppContainer,
) : ViewModel() {

    private val selectedWeek = container.selectedWeek
    private val syncing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    /** Wochen, die in dieser Sitzung schon geladen wurden, mit Zeitpunkt. */
    private val syncedWeeks = mutableMapOf<String, Long>()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val events = selectedWeek.flatMapLatest { calendarRepository.observeWeek(it) }

    val uiState: StateFlow<WeekUiState> = combine(
        selectedWeek,
        events,
        container.settingsStore.settings,
        calendarRepository.observeCalendars(),
        combine(syncing, error) { isSyncing, message -> isSyncing to message },
    ) { week, weekEvents, settings, calendars, (isSyncing, message) ->
        WeekUiState(
            week = week,
            events = weekEvents,
            calendars = calendars,
            accountConfigured = settings.isAccountConfigured,
            syncing = isSyncing,
            error = message,
            lastSyncAt = settings.lastSyncAt,
            dayStartHour = settings.dayStartHour,
            dayEndHour = settings.dayEndHour,
            defaultCalendarUrl = settings.defaultCalendarUrl ?: calendars.firstOrNull { !it.readOnly }?.url,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekUiState())

    init {
        // Die zuletzt betrachtete Woche beibehalten und nur nachladen.
        selectWeek(selectedWeek.value)
    }

    /** Wechselt die Woche und laedt sie, falls noetig. */
    fun selectWeek(week: IsoWeek) {
        selectedWeek.value = week
        val lastSync = syncedWeeks[week.key]
        if (lastSync == null || System.currentTimeMillis() - lastSync > STALE_AFTER_MILLIS) {
            sync(week)
        }
    }

    fun goToToday() = selectWeek(IsoWeek.current())

    fun goToRelativeWeek(offset: Long) = selectWeek(selectedWeek.value.plusWeeks(offset))

    fun refresh() = sync(selectedWeek.value, force = true)

    private fun sync(week: IsoWeek, force: Boolean = false) {
        viewModelScope.launch {
            if (!container.settingsStore.current().isAccountConfigured) return@launch
            if (syncing.value && !force) return@launch

            syncing.value = true
            calendarRepository.syncWeek(week)
                .onSuccess {
                    syncedWeeks[week.key] = System.currentTimeMillis()
                    error.value = null
                }
                .onFailure { throwable ->
                    error.value = throwable.message ?: "Die Termine konnten nicht geladen werden."
                }
            syncing.value = false
        }
    }

    fun deleteEvent(event: PlannedEvent, scope: DeleteScope) {
        viewModelScope.launch {
            syncing.value = true
            calendarRepository.deleteEvent(event, scope, selectedWeek.value)
                .onFailure { error.value = it.message ?: "Der Termin konnte nicht geloescht werden." }
            syncing.value = false
        }
    }

    fun clearError() = error.update { null }

    /** Der Tag, auf dem beim Oeffnen der Woche der Fokus liegen soll. */
    fun focusDay(week: IsoWeek, today: LocalDate = LocalDate.now()): LocalDate =
        if (week.contains(today)) today else week.monday

    companion object {
        private const val STALE_AFTER_MILLIS = 2 * 60 * 1000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WochenplanApp
                WeekViewModel(app.container.calendarRepository, app.container)
            }
        }
    }
}
