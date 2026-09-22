package de.wochenplan.app.ui.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.wochenplan.app.WochenplanApp
import de.wochenplan.app.core.IsoWeek
import de.wochenplan.app.data.db.CalendarEntity
import de.wochenplan.app.data.ical.EventDraft
import de.wochenplan.app.data.ical.RecurrenceRule
import de.wochenplan.app.data.prefs.SettingsStore
import de.wochenplan.app.data.repo.CalendarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** Die Wiederholungen, die sich in der Oberflaeche einstellen lassen. */
enum class RecurrenceOption(val label: String) {
    NONE("Einmalig"),
    DAILY("Taeglich"),
    WEEKDAYS("Jeden Werktag (Mo–Fr)"),
    WEEKLY("Woechentlich"),
    BIWEEKLY("Alle zwei Wochen"),
    MONTHLY("Monatlich"),
    YEARLY("Jaehrlich"),

    /** Eine Regel, die ein anderes Programm angelegt hat. Sie bleibt unveraendert. */
    CUSTOM("Eigene Regel (unveraendert)"),
}

data class EventEditUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val allDay: Boolean = false,
    val startDate: LocalDate = LocalDate.now(),
    val startTime: LocalTime = LocalTime.of(9, 0),
    val endDate: LocalDate = LocalDate.now(),
    val endTime: LocalTime = LocalTime.of(10, 0),
    val calendars: List<CalendarEntity> = emptyList(),
    val calendarUrl: String? = null,
    val recurrence: RecurrenceOption = RecurrenceOption.NONE,
    val saving: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
    val readOnly: Boolean = false,
    val isSeries: Boolean = false,
) {
    val canSave: Boolean
        get() = !saving && !readOnly && title.isNotBlank() && calendarUrl != null && endIsAfterStart

    private val endIsAfterStart: Boolean
        get() = if (allDay) {
            !endDate.isBefore(startDate)
        } else {
            endDate.atTime(endTime).isAfter(startDate.atTime(startTime))
        }
}

class EventEditViewModel(
    private val repository: CalendarRepository,
    private val settingsStore: SettingsStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val href: String? = savedStateHandle.get<String>(ARG_HREF)?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(EventEditUiState())
    val uiState: StateFlow<EventEditUiState> = _uiState.asStateFlow()

    /** UID und Herkunft des bearbeiteten Termins. */
    private var uid: String = "${UUID.randomUUID()}@wochenplan"
    private var sourceCalendarUrl: String? = null
    private var originalRecurrence: RecurrenceRule? = null

    init {
        val startDate = savedStateHandle.get<String>(ARG_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
        val startTime = savedStateHandle.get<String>(ARG_TIME)
            ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: LocalTime.of(9, 0)

        viewModelScope.launch { load(startDate, startTime) }
    }

    private suspend fun load(fallbackDate: LocalDate, fallbackTime: LocalTime) {
        val calendars = repository.observeCalendars().first()
        val settings = settingsStore.current()
        val existing = href?.let { repository.loadForEditing(it) }

        if (existing != null) {
            uid = existing.draft.uid
            sourceCalendarUrl = existing.calendarUrl
            originalRecurrence = existing.draft.recurrence

            _uiState.value = EventEditUiState(
                loading = false,
                isNew = false,
                title = existing.draft.summary,
                description = existing.draft.description.orEmpty(),
                location = existing.draft.location.orEmpty(),
                allDay = existing.draft.allDay,
                startDate = existing.draft.startDate,
                startTime = existing.draft.startTime,
                endDate = existing.draft.endDate,
                endTime = existing.draft.endTime,
                calendars = calendars,
                calendarUrl = existing.calendarUrl,
                recurrence = optionOf(existing.draft.recurrence),
                readOnly = existing.readOnly,
                isSeries = existing.isSeries,
            )
        } else {
            val writable = calendars.filter { !it.readOnly }
            _uiState.value = EventEditUiState(
                loading = false,
                isNew = true,
                startDate = fallbackDate,
                startTime = fallbackTime,
                endDate = fallbackDate,
                endTime = fallbackTime.plusHours(1),
                calendars = calendars,
                calendarUrl = settings.defaultCalendarUrl?.takeIf { url -> writable.any { it.url == url } }
                    ?: writable.firstOrNull()?.url,
                readOnly = writable.isEmpty(),
            )
        }
    }

    fun setTitle(value: String) = _uiState.update { it.copy(title = value) }

    fun setDescription(value: String) = _uiState.update { it.copy(description = value) }

    fun setLocation(value: String) = _uiState.update { it.copy(location = value) }

    fun setAllDay(value: Boolean) = _uiState.update { it.copy(allDay = value) }

    fun setCalendar(url: String) = _uiState.update { it.copy(calendarUrl = url) }

    fun setRecurrence(option: RecurrenceOption) = _uiState.update { it.copy(recurrence = option) }

    fun setStartDate(date: LocalDate) = _uiState.update { state ->
        // Das Ende wandert mit, damit die Dauer erhalten bleibt.
        val shift = java.time.temporal.ChronoUnit.DAYS.between(state.startDate, date)
        state.copy(startDate = date, endDate = state.endDate.plusDays(shift))
    }

    fun setStartTime(time: LocalTime) = _uiState.update { state ->
        val durationMinutes = java.time.Duration.between(
            state.startDate.atTime(state.startTime),
            state.endDate.atTime(state.endTime),
        ).toMinutes().coerceAtLeast(15)
        val newEnd = state.startDate.atTime(time).plusMinutes(durationMinutes)
        state.copy(startTime = time, endDate = newEnd.toLocalDate(), endTime = newEnd.toLocalTime())
    }

    fun setEndDate(date: LocalDate) = _uiState.update { it.copy(endDate = date) }

    fun setEndTime(time: LocalTime) = _uiState.update { it.copy(endTime = time) }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun save() {
        val state = _uiState.value
        val calendarUrl = state.calendarUrl ?: return
        if (!state.canSave) return

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }

            val draft = EventDraft(
                uid = uid,
                summary = state.title.trim(),
                description = state.description.trim().takeIf { it.isNotEmpty() },
                location = state.location.trim().takeIf { it.isNotEmpty() },
                allDay = state.allDay,
                startDate = state.startDate,
                startTime = state.startTime,
                endDate = state.endDate,
                endTime = state.endTime,
                recurrence = ruleFor(state.recurrence, state.startDate),
            )

            val week = IsoWeek.of(state.startDate)
            val result = if (href == null) {
                repository.createEvent(calendarUrl, draft, week)
            } else {
                repository.updateEvent(
                    href = href,
                    sourceCalendarUrl = sourceCalendarUrl ?: calendarUrl,
                    targetCalendarUrl = calendarUrl,
                    draft = draft,
                    week = week,
                )
            }

            result
                .onSuccess { _uiState.update { it.copy(saving = false, finished = true) } }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(saving = false, error = error.message ?: "Der Termin konnte nicht gespeichert werden.")
                    }
                }
        }
    }

    /** Baut die Regel zur gewaehlten Option; `CUSTOM` bleibt unveraendert. */
    private fun ruleFor(option: RecurrenceOption, startDate: LocalDate): RecurrenceRule? = when (option) {
        RecurrenceOption.NONE -> null
        RecurrenceOption.DAILY -> RecurrenceRule(RecurrenceRule.Freq.DAILY)
        RecurrenceOption.WEEKDAYS -> RecurrenceRule(
            freq = RecurrenceRule.Freq.WEEKLY,
            byDay = listOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
            ).map { de.wochenplan.app.data.ical.ByDay(null, it) },
        )
        RecurrenceOption.WEEKLY -> RecurrenceRule(
            freq = RecurrenceRule.Freq.WEEKLY,
            byDay = listOf(de.wochenplan.app.data.ical.ByDay(null, startDate.dayOfWeek)),
        )
        RecurrenceOption.BIWEEKLY -> RecurrenceRule(
            freq = RecurrenceRule.Freq.WEEKLY,
            interval = 2,
            byDay = listOf(de.wochenplan.app.data.ical.ByDay(null, startDate.dayOfWeek)),
        )
        RecurrenceOption.MONTHLY -> RecurrenceRule(RecurrenceRule.Freq.MONTHLY)
        RecurrenceOption.YEARLY -> RecurrenceRule(RecurrenceRule.Freq.YEARLY)
        RecurrenceOption.CUSTOM -> originalRecurrence
    }

    /** Erkennt, welche Option zu einer vorhandenen Regel passt. */
    private fun optionOf(rule: RecurrenceRule?): RecurrenceOption {
        if (rule == null) return RecurrenceOption.NONE
        if (!rule.isSupported || rule.count != null || rule.until != null ||
            rule.byMonthDay.isNotEmpty() || rule.byMonth.isNotEmpty()
        ) {
            return RecurrenceOption.CUSTOM
        }
        val weekdays = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        return when {
            rule.freq == RecurrenceRule.Freq.DAILY && rule.interval == 1 && rule.byDay.isEmpty() ->
                RecurrenceOption.DAILY

            rule.freq == RecurrenceRule.Freq.WEEKLY && rule.interval == 1 &&
                rule.byDay.map { it.day }.toSet() == weekdays -> RecurrenceOption.WEEKDAYS

            rule.freq == RecurrenceRule.Freq.WEEKLY && rule.interval == 1 && rule.byDay.size <= 1 ->
                RecurrenceOption.WEEKLY

            rule.freq == RecurrenceRule.Freq.WEEKLY && rule.interval == 2 && rule.byDay.size <= 1 ->
                RecurrenceOption.BIWEEKLY

            rule.freq == RecurrenceRule.Freq.MONTHLY && rule.interval == 1 && rule.byDay.isEmpty() ->
                RecurrenceOption.MONTHLY

            rule.freq == RecurrenceRule.Freq.YEARLY && rule.interval == 1 && rule.byDay.isEmpty() ->
                RecurrenceOption.YEARLY

            else -> RecurrenceOption.CUSTOM
        }
    }

    companion object {
        const val ARG_HREF = "href"
        const val ARG_DATE = "date"
        const val ARG_TIME = "time"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WochenplanApp
                EventEditViewModel(
                    repository = app.container.calendarRepository,
                    settingsStore = app.container.settingsStore,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
