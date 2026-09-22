package de.wochenplan.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.wochenplan.app.ui.common.CalendarColors
import de.wochenplan.app.ui.common.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("Einstellungen") }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AccountSection(state = state, viewModel = viewModel)
            HorizontalDivider()
            CalendarSection(state = state, viewModel = viewModel)
            HorizontalDivider()
            WeekViewSection(state = state, viewModel = viewModel)
            HorizontalDivider()
            PomodoroSection(state = state, viewModel = viewModel)
            HorizontalDivider()
            AboutSection(lastSyncAt = state.settings.lastSyncAt)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun AccountSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    var showPassword by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("CalDAV-Konto")

        OutlinedTextField(
            value = state.form.serverUrl,
            onValueChange = viewModel::setServerUrl,
            label = { Text("Server-Adresse") },
            placeholder = { Text("https://cloud.example.com/remote.php/dav") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.form.username,
            onValueChange = viewModel::setUsername,
            label = { Text("Benutzername") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.form.password,
            onValueChange = viewModel::setPassword,
            label = { Text(if (state.settings.hasPassword) "Neues Passwort (optional)" else "Passwort") },
            singleLine = true,
            visualTransformation = if (showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showPassword) "Passwort verbergen" else "Passwort anzeigen",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "Bei Nextcloud und ownCloud am besten ein App-Passwort verwenden. " +
                "Das Passwort wird verschluesselt im Android-Schluesselspeicher abgelegt.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = viewModel::connect, enabled = !state.busy) {
                Text(if (state.settings.isAccountConfigured) "Neu verbinden" else "Verbinden")
            }
            if (state.settings.isAccountConfigured) {
                OutlinedButton(onClick = viewModel::disconnect, enabled = !state.busy) { Text("Trennen") }
            }
            if (state.busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun CalendarSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionTitle("Kalender")
            IconButton(onClick = viewModel::refreshCalendars, enabled = !state.busy) {
                Icon(Icons.Filled.Refresh, contentDescription = "Kalenderliste aktualisieren")
            }
        }

        if (state.calendars.isEmpty()) {
            Text(
                text = "Noch keine Kalender geladen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        for (calendar in state.calendars) {
            Card {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(CalendarColors.resolve(calendar.color, calendar.url))
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 10.dp)
                        ) {
                            Text(calendar.displayName, style = MaterialTheme.typography.bodyLarge)
                            if (calendar.readOnly) {
                                Text(
                                    text = "schreibgeschuetzt",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(
                            checked = calendar.visible,
                            onCheckedChange = { viewModel.setCalendarVisible(calendar.url, it) },
                        )
                    }
                    if (!calendar.readOnly) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = state.settings.defaultCalendarUrl == calendar.url,
                                onClick = { viewModel.setDefaultCalendar(calendar.url) },
                            )
                            Text(
                                text = "Neue Termine hier anlegen",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekViewSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Wochenansicht")
        Text(
            text = "Angezeigter Tagesbereich: ${state.settings.dayStartHour}:00 bis ${state.settings.dayEndHour}:00",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Termine ausserhalb dieses Bereichs werden trotzdem angezeigt; das Raster waechst dann mit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NumberSetting(
            label = "Tag beginnt um",
            value = state.settings.dayStartHour,
            range = 0..22,
            suffix = "Uhr",
            onChange = { viewModel.setDayRange(it, state.settings.dayEndHour) },
        )
        NumberSetting(
            label = "Tag endet um",
            value = state.settings.dayEndHour,
            range = state.settings.dayStartHour + 1..24,
            suffix = "Uhr",
            onChange = { viewModel.setDayRange(state.settings.dayStartHour, it) },
        )
    }
}

@Composable
private fun PomodoroSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Pomodoro-Timer")
        NumberSetting(
            label = "Fokusabschnitt",
            value = state.settings.focusMinutes,
            range = 1..180,
            suffix = "Min.",
            step = 5,
            onChange = { viewModel.setPomodoro(focusMinutes = it) },
        )
        NumberSetting(
            label = "Kurze Pause",
            value = state.settings.shortBreakMinutes,
            range = 1..60,
            suffix = "Min.",
            onChange = { viewModel.setPomodoro(shortBreakMinutes = it) },
        )
        NumberSetting(
            label = "Lange Pause",
            value = state.settings.longBreakMinutes,
            range = 1..120,
            suffix = "Min.",
            step = 5,
            onChange = { viewModel.setPomodoro(longBreakMinutes = it) },
        )
        NumberSetting(
            label = "Lange Pause nach",
            value = state.settings.cyclesBeforeLongBreak,
            range = 2..12,
            suffix = "Abschnitten",
            onChange = { viewModel.setPomodoro(cyclesBeforeLongBreak = it) },
        )
        ToggleSetting(
            label = "Naechsten Abschnitt automatisch starten",
            checked = state.settings.autoStartNextPhase,
            onChange = { viewModel.setPomodoro(autoStartNextPhase = it) },
        )
        ToggleSetting(
            label = "Beim Wechsel vibrieren",
            checked = state.settings.vibrate,
            onChange = { viewModel.setPomodoro(vibrate = it) },
        )
        ToggleSetting(
            label = "Bildschirm waehrend des Timers anlassen",
            checked = state.settings.keepScreenOn,
            onChange = { viewModel.setPomodoro(keepScreenOn = it) },
        )
    }
}

@Composable
private fun AboutSection(lastSyncAt: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle("Ueber")
        Text("Wochenplan – Wochenplanung mit CalDAV", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "Letzte Aktualisierung: ${Formatters.relativeTime(lastSyncAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NumberSetting(
    label: String,
    value: Int,
    range: IntRange,
    suffix: String,
    step: Int = 1,
    onChange: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { onChange((value - step).coerceIn(range)) },
                enabled = value - step >= range.first,
            ) { Text("–") }
            Text(
                text = "$value $suffix",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = { onChange((value + step).coerceIn(range)) },
                enabled = value + step <= range.last,
            ) { Text("+") }
        }
    }
}

@Composable
private fun ToggleSetting(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
