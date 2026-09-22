package de.wochenplan.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.wochenplan.app.AppContainer
import de.wochenplan.app.WochenplanApp
import de.wochenplan.app.data.db.CalendarEntity
import de.wochenplan.app.data.prefs.AppSettings
import de.wochenplan.app.data.repo.CalendarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val calendars: List<CalendarEntity> = emptyList(),
    val form: AccountForm = AccountForm(),
    val busy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

data class AccountForm(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    /** Wurde das Formular schon mit den gespeicherten Werten gefuellt? */
    val initialized: Boolean = false,
)

class SettingsViewModel(
    private val repository: CalendarRepository,
    private val container: AppContainer,
) : ViewModel() {

    private val form = MutableStateFlow(AccountForm())
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<Pair<String, Boolean>?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        container.settingsStore.settings,
        repository.observeCalendars(),
        form,
        busy,
        message,
    ) { settings, calendars, accountForm, isBusy, currentMessage ->
        SettingsUiState(
            settings = settings,
            calendars = calendars,
            form = accountForm,
            busy = isBusy,
            message = currentMessage?.first,
            isError = currentMessage?.second ?: false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        // Das Formular einmalig mit den gespeicherten Zugangsdaten fuellen.
        viewModelScope.launch {
            val settings = container.settingsStore.current()
            form.value = AccountForm(
                serverUrl = settings.serverUrl,
                username = settings.username,
                password = "",
                initialized = true,
            )
        }
    }

    fun setServerUrl(value: String) = form.update { it.copy(serverUrl = value, initialized = true) }

    fun setUsername(value: String) = form.update { it.copy(username = value, initialized = true) }

    fun setPassword(value: String) = form.update { it.copy(password = value, initialized = true) }

    fun clearMessage() = message.update { null }

    /** Prueft die Zugangsdaten und speichert sie bei Erfolg. */
    fun connect() {
        val current = form.value
        viewModelScope.launch {
            busy.value = true
            message.value = null

            val password = current.password.ifEmpty { container.settingsStore.password().orEmpty() }
            if (current.serverUrl.isBlank() || current.username.isBlank() || password.isEmpty()) {
                message.value = "Bitte Adresse, Benutzername und Passwort ausfuellen." to true
                busy.value = false
                return@launch
            }

            repository.connect(current.serverUrl, current.username, password)
                .onSuccess { calendars ->
                    form.update { it.copy(password = "") }
                    message.value = "Verbunden. ${calendars.size} Kalender gefunden." to false
                }
                .onFailure { error ->
                    message.value = (error.message ?: "Die Verbindung ist fehlgeschlagen.") to true
                }
            busy.value = false
        }
    }

    fun refreshCalendars() {
        viewModelScope.launch {
            busy.value = true
            repository.refreshCalendars()
                .onSuccess { message.value = "Kalenderliste aktualisiert." to false }
                .onFailure { message.value = (it.message ?: "Aktualisieren fehlgeschlagen.") to true }
            busy.value = false
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            busy.value = true
            repository.disconnect()
            form.value = AccountForm(initialized = true)
            message.value = "Konto entfernt. Lokale Aufgaben bleiben erhalten." to false
            busy.value = false
        }
    }

    fun setCalendarVisible(url: String, visible: Boolean) {
        viewModelScope.launch { repository.setCalendarVisible(url, visible) }
    }

    fun setDefaultCalendar(url: String) {
        viewModelScope.launch { container.settingsStore.setDefaultCalendar(url) }
    }

    fun setDayRange(startHour: Int, endHour: Int) {
        viewModelScope.launch { container.settingsStore.setDayRange(startHour, endHour) }
    }

    fun setPomodoro(
        focusMinutes: Int? = null,
        shortBreakMinutes: Int? = null,
        longBreakMinutes: Int? = null,
        cyclesBeforeLongBreak: Int? = null,
        vibrate: Boolean? = null,
        keepScreenOn: Boolean? = null,
        autoStartNextPhase: Boolean? = null,
    ) {
        viewModelScope.launch {
            container.settingsStore.setPomodoro(
                focusMinutes = focusMinutes,
                shortBreakMinutes = shortBreakMinutes,
                longBreakMinutes = longBreakMinutes,
                cyclesBeforeLongBreak = cyclesBeforeLongBreak,
                vibrate = vibrate,
                keepScreenOn = keepScreenOn,
                autoStartNextPhase = autoStartNextPhase,
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WochenplanApp
                SettingsViewModel(app.container.calendarRepository, app.container)
            }
        }
    }
}
