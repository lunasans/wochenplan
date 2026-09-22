package de.wochenplan.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.wochenplan.app.AppContainer
import de.wochenplan.app.WochenplanApp
import de.wochenplan.app.core.IsoWeek
import de.wochenplan.app.data.db.TaskEntity
import de.wochenplan.app.data.repo.FocusRepository
import de.wochenplan.app.data.repo.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TaskUiState(
    val week: IsoWeek = IsoWeek.current(),
    val tasks: List<TaskEntity> = emptyList(),
    val focusMinutesThisWeek: Int = 0,
) {
    val openTasks: Int get() = tasks.count { !it.done }
    val doneTasks: Int get() = tasks.count { it.done }
    val plannedPomodoros: Int get() = tasks.filter { !it.done }.sumOf { it.plannedPomodoros }

    /** Aufgaben nach Wochentag, 0 steht fuer "ohne festen Tag". */
    val byWeekday: Map<Int, List<TaskEntity>>
        get() = tasks.groupBy { it.weekday ?: 0 }.toSortedMap(
            compareBy { if (it == 0) 8 else it },
        )
}

class TaskViewModel(
    private val taskRepository: TaskRepository,
    focusRepository: FocusRepository,
    private val container: AppContainer,
) : ViewModel() {

    private val week = container.selectedWeek

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TaskUiState> = combine(
        week,
        week.flatMapLatest { taskRepository.observeWeek(it) },
        week.flatMapLatest { focusRepository.observeFocusMinutesOfWeek(it) },
    ) { currentWeek, tasks, focusMinutes ->
        TaskUiState(week = currentWeek, tasks = tasks, focusMinutesThisWeek = focusMinutes)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskUiState())

    fun selectWeek(value: IsoWeek) {
        week.value = value
    }

    fun goToRelativeWeek(offset: Long) {
        week.value = week.value.plusWeeks(offset)
    }

    fun goToToday() {
        week.value = IsoWeek.current()
    }

    fun addTask(title: String, weekday: Int?, plannedPomodoros: Int = 1) {
        viewModelScope.launch { taskRepository.add(week.value, title, weekday, plannedPomodoros) }
    }

    fun setDone(task: TaskEntity, done: Boolean) {
        viewModelScope.launch { taskRepository.setDone(task.id, done) }
    }

    fun setWeekday(task: TaskEntity, weekday: Int?) {
        viewModelScope.launch { taskRepository.update(task.copy(weekday = weekday)) }
    }

    fun setPlannedPomodoros(task: TaskEntity, planned: Int) {
        viewModelScope.launch { taskRepository.update(task.copy(plannedPomodoros = planned.coerceIn(0, 16))) }
    }

    fun rename(task: TaskEntity, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { taskRepository.update(task.copy(title = trimmed)) }
    }

    fun delete(task: TaskEntity) {
        viewModelScope.launch { taskRepository.delete(task.id) }
    }

    /** Verschiebt alle offenen Aufgaben in die naechste Woche. */
    fun carryOverToNextWeek() {
        viewModelScope.launch {
            taskRepository.carryOverOpenTasks(week.value, week.value.plusWeeks(1))
        }
    }

    /** Uebernimmt eine Aufgabe in den Pomodoro-Timer. */
    fun startFocusOn(task: TaskEntity) {
        container.pomodoroEngine.selectTask(task.id, task.title)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WochenplanApp
                TaskViewModel(
                    taskRepository = app.container.taskRepository,
                    focusRepository = app.container.focusRepository,
                    container = app.container,
                )
            }
        }
    }
}
