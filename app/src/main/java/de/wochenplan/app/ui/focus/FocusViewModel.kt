package de.wochenplan.app.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.wochenplan.app.AppContainer
import de.wochenplan.app.WochenplanApp
import de.wochenplan.app.core.IsoWeek
import de.wochenplan.app.data.db.TaskEntity
import de.wochenplan.app.data.prefs.AppSettings
import de.wochenplan.app.pomodoro.PomodoroPhase
import de.wochenplan.app.pomodoro.PomodoroState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FocusUiState(
    val timer: PomodoroState = PomodoroState(),
    val week: IsoWeek = IsoWeek.current(),
    val openTasks: List<TaskEntity> = emptyList(),
    val completedToday: Int = 0,
    val focusMinutesToday: Int = 0,
    val keepScreenOn: Boolean = false,
    val autoStartNextPhase: Boolean = true,
) {
    val phaseTitle: String
        get() = when (timer.phase) {
            PomodoroPhase.FOCUS -> "Fokus"
            PomodoroPhase.SHORT_BREAK -> "Kurze Pause"
            PomodoroPhase.LONG_BREAK -> "Lange Pause"
        }

    val phaseHint: String
        get() = when (timer.phase) {
            PomodoroPhase.FOCUS -> timer.taskTitle ?: "Eine Aufgabe, ohne Ablenkung."
            PomodoroPhase.SHORT_BREAK -> "Kurz aufstehen, Augen entspannen."
            PomodoroPhase.LONG_BREAK -> "Laenger durchatmen – der Durchgang ist geschafft."
        }
}

class FocusViewModel(private val container: AppContainer) : ViewModel() {

    private val engine = container.pomodoroEngine

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<FocusUiState> = combine(
        engine.state,
        container.selectedWeek,
        container.selectedWeek.flatMapLatest { week ->
            container.taskRepository.observeWeek(week).map { tasks -> tasks.filter { !it.done } }
        },
        container.focusRepository.observeCompletedFocusToday(),
        combine(
            container.focusRepository.observeFocusMinutesToday(),
            container.settingsStore.settings,
        ) { minutes, settings -> minutes to settings },
    ) { timer, week, tasks, completedToday, (minutesToday, settings) ->
        FocusUiState(
            timer = timer,
            week = week,
            openTasks = tasks,
            completedToday = completedToday,
            focusMinutesToday = minutesToday,
            keepScreenOn = settings.keepScreenOn,
            autoStartNextPhase = settings.autoStartNextPhase,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    fun selectTask(task: TaskEntity?) {
        engine.selectTask(task?.id, task?.title)
    }

    fun switchPhase(phase: PomodoroPhase) {
        engine.switchTo(phase)
    }

    fun setAutoStart(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setPomodoro(autoStartNextPhase = enabled) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WochenplanApp
                FocusViewModel(app.container)
            }
        }
    }
}
