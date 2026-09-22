package de.wochenplan.app.pomodoro

import de.wochenplan.app.data.db.FocusSessionEntity
import de.wochenplan.app.data.prefs.AppSettings
import de.wochenplan.app.data.prefs.SettingsStore
import de.wochenplan.app.data.repo.FocusRepository
import de.wochenplan.app.data.repo.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/** Die drei Abschnitte der Pomodoro-Technik. */
enum class PomodoroPhase {
    /** Konzentrierte Arbeit. */
    FOCUS,

    /** Kurze Pause nach einem Arbeitsabschnitt. */
    SHORT_BREAK,

    /** Laengere Pause nach mehreren Arbeitsabschnitten. */
    LONG_BREAK;

    val isBreak: Boolean get() = this != FOCUS
}

data class PomodoroState(
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val running: Boolean = false,
    val remainingMillis: Long = 25 * 60_000L,
    val totalMillis: Long = 25 * 60_000L,
    /** Abgeschlossene Arbeitsabschnitte seit der letzten langen Pause. */
    val completedFocusInCycle: Int = 0,
    val cyclesBeforeLongBreak: Int = 4,
    val taskId: Long? = null,
    val taskTitle: String? = null,
    /** Zeitpunkt des Phasenendes auf der Geraete-Uhr, fuer die Benachrichtigung. */
    val endElapsedRealtime: Long? = null,
) {
    /** Noch nicht gestartet und noch nichts abgelaufen. */
    val isIdle: Boolean get() = !running && remainingMillis >= totalMillis

    val progress: Float
        get() = if (totalMillis <= 0) 0f else ((totalMillis - remainingMillis).toFloat() / totalMillis).coerceIn(0f, 1f)

    /** Verbleibende Zeit als `MM:SS`. */
    val remainingText: String
        get() {
            val totalSeconds = ((remainingMillis + 999) / 1000).coerceAtLeast(0)
            return String.format(Locale.GERMAN, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
        }
}

sealed interface PomodoroEvent {
    /** Ein Abschnitt ist zu Ende. [completed] ist `false`, wenn uebersprungen wurde. */
    data class PhaseFinished(
        val finished: PomodoroPhase,
        val next: PomodoroPhase,
        val completed: Boolean,
    ) : PomodoroEvent
}

/**
 * Der Pomodoro-Timer.
 *
 * Gerechnet wird mit der monotonen Geraete-Uhr, nicht mit der Anzahl der Ticks.
 * So bleibt der Timer genau, auch wenn Android die App im Hintergrund drosselt.
 */
class PomodoroEngine(
    private val scope: CoroutineScope,
    private val settingsStore: SettingsStore,
    private val focusRepository: FocusRepository,
    private val taskRepository: TaskRepository,
    private val elapsedRealtime: () -> Long,
    private val wallClock: () -> Long = { System.currentTimeMillis() },
) {

    private val _state = MutableStateFlow(PomodoroState())
    val state: StateFlow<PomodoroState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PomodoroEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PomodoroEvent> = _events.asSharedFlow()

    private var ticker: Job? = null
    private var settings: AppSettings = AppSettings()
    private var phaseStartedAt: Long = 0L

    init {
        scope.launch {
            settingsStore.settings.collect { updated ->
                settings = updated
                // Geaenderte Laengen wirken erst, wenn gerade nichts laeuft.
                if (_state.value.isIdle) {
                    _state.update { current ->
                        val total = durationOf(current.phase)
                        current.copy(
                            totalMillis = total,
                            remainingMillis = total,
                            cyclesBeforeLongBreak = updated.cyclesBeforeLongBreak,
                        )
                    }
                }
            }
        }
    }

    fun toggle() {
        if (_state.value.running) pause() else start()
    }

    fun start() {
        val current = _state.value
        if (current.running) return
        val remaining = if (current.remainingMillis <= 0) durationOf(current.phase) else current.remainingMillis
        if (current.isIdle || phaseStartedAt == 0L) phaseStartedAt = wallClock()

        _state.update {
            it.copy(
                running = true,
                remainingMillis = remaining,
                endElapsedRealtime = elapsedRealtime() + remaining,
            )
        }
        startTicker()
    }

    fun pause() {
        val current = _state.value
        if (!current.running) return
        ticker?.cancel()
        ticker = null
        val remaining = current.endElapsedRealtime?.let { (it - elapsedRealtime()).coerceAtLeast(0) }
            ?: current.remainingMillis
        _state.update { it.copy(running = false, remainingMillis = remaining, endElapsedRealtime = null) }
    }

    /** Bricht den laufenden Abschnitt ab und beginnt ihn von vorn. */
    fun reset() {
        scope.launch {
            val current = _state.value
            ticker?.cancel()
            ticker = null
            if (!current.isIdle) recordSession(current, completed = false)
            phaseStartedAt = 0L
            applyPhase(current.phase, current.completedFocusInCycle, autoStart = false)
        }
    }

    /** Springt zum naechsten Abschnitt, ohne den aktuellen abzuschliessen. */
    fun skip() {
        scope.launch { finishPhase(completed = false) }
    }

    /** Verknuepft den Timer mit einer Aufgabe der Wochenplanung. */
    fun selectTask(taskId: Long?, taskTitle: String?) {
        _state.update { it.copy(taskId = taskId, taskTitle = taskTitle) }
    }

    /** Stellt einen Abschnitt direkt ein, z.B. ueber die Auswahl in der Oberflaeche. */
    fun switchTo(phase: PomodoroPhase) {
        scope.launch {
            val current = _state.value
            ticker?.cancel()
            ticker = null
            if (!current.isIdle) recordSession(current, completed = false)
            phaseStartedAt = 0L
            applyPhase(phase, current.completedFocusInCycle, autoStart = false)
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val end = _state.value.endElapsedRealtime ?: break
                val remaining = (end - elapsedRealtime()).coerceAtLeast(0L)
                _state.update { it.copy(remainingMillis = remaining) }
                if (remaining <= 0L) {
                    finishPhase(completed = true)
                    break
                }
                delay(TICK_MILLIS)
            }
        }
    }

    private suspend fun finishPhase(completed: Boolean) {
        val current = _state.value
        ticker?.cancel()
        ticker = null

        recordSession(current, completed)
        if (current.phase == PomodoroPhase.FOCUS && completed) {
            current.taskId?.let { taskRepository.countPomodoro(it) }
        }

        val focusInCycle = when {
            current.phase != PomodoroPhase.FOCUS -> current.completedFocusInCycle
            completed -> current.completedFocusInCycle + 1
            else -> current.completedFocusInCycle
        }
        val next = nextPhase(current.phase, focusInCycle)
        // Nach der langen Pause beginnt ein neuer Durchgang.
        val carriedCycle = if (current.phase == PomodoroPhase.LONG_BREAK) 0 else focusInCycle

        _events.tryEmit(PomodoroEvent.PhaseFinished(current.phase, next, completed))
        phaseStartedAt = 0L
        applyPhase(next, carriedCycle, autoStart = settings.autoStartNextPhase)
    }

    private fun applyPhase(phase: PomodoroPhase, focusInCycle: Int, autoStart: Boolean) {
        val total = durationOf(phase)
        _state.update {
            it.copy(
                phase = phase,
                running = false,
                remainingMillis = total,
                totalMillis = total,
                completedFocusInCycle = focusInCycle,
                cyclesBeforeLongBreak = settings.cyclesBeforeLongBreak,
                endElapsedRealtime = null,
            )
        }
        if (autoStart) start()
    }

    private fun nextPhase(phase: PomodoroPhase, focusInCycle: Int): PomodoroPhase = when {
        phase != PomodoroPhase.FOCUS -> PomodoroPhase.FOCUS
        focusInCycle > 0 && focusInCycle % settings.cyclesBeforeLongBreak == 0 -> PomodoroPhase.LONG_BREAK
        else -> PomodoroPhase.SHORT_BREAK
    }

    private suspend fun recordSession(state: PomodoroState, completed: Boolean) {
        val startedAt = phaseStartedAt.takeIf { it > 0L } ?: return
        val elapsedMinutes = ((state.totalMillis - state.remainingMillis) / 60_000L).toInt()
        // Sehr kurze Abschnitte sind fuer die Statistik ohne Aussage.
        if (!completed && elapsedMinutes < 1) return

        focusRepository.record(
            FocusSessionEntity(
                taskId = state.taskId,
                taskTitle = state.taskTitle,
                phase = state.phase.name,
                startedAt = startedAt,
                finishedAt = wallClock(),
                plannedMinutes = if (completed) (state.totalMillis / 60_000L).toInt() else elapsedMinutes,
                completed = completed,
            )
        )
    }

    private fun durationOf(phase: PomodoroPhase): Long = when (phase) {
        PomodoroPhase.FOCUS -> settings.focusMinutes * 60_000L
        PomodoroPhase.SHORT_BREAK -> settings.shortBreakMinutes * 60_000L
        PomodoroPhase.LONG_BREAK -> settings.longBreakMinutes * 60_000L
    }

    private companion object {
        const val TICK_MILLIS = 250L
    }
}
