package de.wochenplan.app.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.wochenplan.app.AppContainer
import de.wochenplan.app.WochenplanApp
import de.wochenplan.app.core.IsoWeek
import de.wochenplan.app.data.ai.PlanEntryKind
import de.wochenplan.app.data.ai.WeekPlanProposal
import de.wochenplan.app.data.db.CalendarEntity
import de.wochenplan.app.data.repo.WeekPlannerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlanUiState(
    val week: IsoWeek = IsoWeek.current(),
    val goals: String = "",
    val hasApiKey: Boolean = false,
    val loading: Boolean = false,
    val applying: Boolean = false,
    val proposal: WeekPlanProposal? = null,
    /** Kennungen der Vorschlaege, die uebernommen werden sollen. */
    val accepted: Set<String> = emptySet(),
    val calendars: List<CalendarEntity> = emptyList(),
    val targetCalendarUrl: String? = null,
    val message: String? = null,
    val finished: Boolean = false,
) {
    val acceptedCount: Int get() = accepted.size

    val canPlan: Boolean get() = hasApiKey && !loading && !applying

    /** Enthaelt der Vorschlag Eintraege mit Uhrzeit, die in den Kalender koennten? */
    val hasTimedEntries: Boolean
        get() = proposal?.entries?.any { it.kind == PlanEntryKind.EVENT } == true

    val writableCalendars: List<CalendarEntity> get() = calendars.filter { !it.readOnly }
}

class PlanViewModel(
    private val repository: WeekPlannerRepository,
    private val container: AppContainer,
) : ViewModel() {

    /** Alles, was nur diesen Bildschirm betrifft, in einem Zustand. */
    private data class LocalState(
        val goals: String = "",
        val loading: Boolean = false,
        val applying: Boolean = false,
        val proposal: WeekPlanProposal? = null,
        val accepted: Set<String> = emptySet(),
        val targetCalendar: String? = null,
        val message: String? = null,
        val finished: Boolean = false,
    )

    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<PlanUiState> = combine(
        container.selectedWeek,
        container.settingsStore.settings,
        container.calendarRepository.observeCalendars(),
        local,
    ) { week, settings, calendars, state ->
        PlanUiState(
            week = week,
            goals = state.goals,
            hasApiKey = settings.hasAiKey,
            loading = state.loading,
            applying = state.applying,
            proposal = state.proposal,
            accepted = state.accepted,
            calendars = calendars,
            targetCalendarUrl = state.targetCalendar
                ?: settings.defaultCalendarUrl
                ?: calendars.firstOrNull { !it.readOnly }?.url,
            message = state.message,
            finished = state.finished,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    fun setGoals(value: String) = local.update { it.copy(goals = value) }

    fun setTargetCalendar(url: String) = local.update { it.copy(targetCalendar = url) }

    fun clearMessage() = local.update { it.copy(message = null) }

    /** Fragt die KI nach einem Vorschlag. Geschrieben wird dabei nichts. */
    fun generate() {
        if (local.value.loading) return
        viewModelScope.launch {
            local.update { it.copy(loading = true, message = null) }

            repository.plan(container.selectedWeek.value, local.value.goals)
                .onSuccess { result ->
                    local.update { state ->
                        state.copy(
                            proposal = result,
                            // Alles vorausgewaehlt; abwaehlen ist weniger Arbeit als auswaehlen.
                            accepted = result.entries.map { entry -> entry.id }.toSet(),
                            message = if (result.isEmpty) "Die KI hat nichts vorgeschlagen." else null,
                        )
                    }
                }
                .onFailure { error ->
                    local.update {
                        it.copy(message = error.message ?: "Die Planung ist fehlgeschlagen.")
                    }
                }
            local.update { it.copy(loading = false) }
        }
    }

    fun toggleEntry(id: String) = local.update { state ->
        val selection = state.accepted
        state.copy(accepted = if (id in selection) selection - id else selection + id)
    }

    fun selectAll() = local.update { state ->
        state.copy(accepted = state.proposal?.entries?.map { it.id }?.toSet().orEmpty())
    }

    fun selectNone() = local.update { it.copy(accepted = emptySet()) }

    fun discard() = local.update { it.copy(proposal = null, accepted = emptySet()) }

    /** Uebernimmt genau die angehakten Vorschlaege. */
    fun applyAccepted() {
        val state = local.value
        val current = state.proposal ?: return
        val selection = current.entries.filter { it.id in state.accepted }
        if (selection.isEmpty() || state.applying) return

        viewModelScope.launch {
            local.update { it.copy(applying = true) }
            repository.apply(
                week = container.selectedWeek.value,
                entries = selection,
                calendarUrl = uiState.value.targetCalendarUrl,
            )
                .onSuccess { applied ->
                    local.update {
                        it.copy(
                            proposal = null,
                            accepted = emptySet(),
                            message = buildString {
                                append("Uebernommen: ${applied.tasks} Aufgaben")
                                if (applied.events > 0) append(", ${applied.events} Termine")
                                append(".")
                            },
                            finished = true,
                        )
                    }
                }
                .onFailure { error ->
                    local.update {
                        it.copy(message = error.message ?: "Das Uebernehmen ist fehlgeschlagen.")
                    }
                }
            local.update { it.copy(applying = false) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WochenplanApp
                PlanViewModel(app.container.weekPlannerRepository, app.container)
            }
        }
    }
}
