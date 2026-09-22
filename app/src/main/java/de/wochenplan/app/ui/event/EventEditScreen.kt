package de.wochenplan.app.ui.event

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import de.wochenplan.app.ui.common.CalendarColors
import de.wochenplan.app.ui.common.Formatters
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EventEditViewModel = viewModel(factory = EventEditViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.finished) {
        if (state.finished) onClose()
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Neuer Termin" else "Termin bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurueck")
                    }
                },
                actions = {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = viewModel::save, enabled = state.canSave) {
                            Icon(Icons.Filled.Check, contentDescription = "Speichern")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.readOnly) {
                Text(
                    text = "Dieser Kalender ist schreibgeschuetzt. Aenderungen sind nicht moeglich.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                label = { Text("Titel") },
                singleLine = true,
                enabled = !state.readOnly,
                isError = state.title.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )

            CalendarSelector(
                calendars = state.calendars.filter { !it.readOnly },
                selectedUrl = state.calendarUrl,
                enabled = !state.readOnly,
                onSelect = viewModel::setCalendar,
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Ganztaegig", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = state.allDay,
                    onCheckedChange = viewModel::setAllDay,
                    enabled = !state.readOnly,
                )
            }

            DateTimeRow(
                label = "Beginn",
                date = state.startDate,
                time = state.startTime,
                showTime = !state.allDay,
                enabled = !state.readOnly,
                onDateChange = viewModel::setStartDate,
                onTimeChange = viewModel::setStartTime,
            )

            DateTimeRow(
                label = "Ende",
                date = state.endDate,
                time = state.endTime,
                showTime = !state.allDay,
                enabled = !state.readOnly,
                onDateChange = viewModel::setEndDate,
                onTimeChange = viewModel::setEndTime,
            )

            RecurrenceSelector(
                selected = state.recurrence,
                isSeries = state.isSeries,
                enabled = !state.readOnly,
                onSelect = viewModel::setRecurrence,
            )

            if (state.isSeries) {
                Text(
                    text = "Aenderungen gelten fuer die gesamte Serie.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = state.location,
                onValueChange = viewModel::setLocation,
                label = { Text("Ort") },
                singleLine = true,
                enabled = !state.readOnly,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::setDescription,
                label = { Text("Notizen") },
                minLines = 3,
                enabled = !state.readOnly,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarSelector(
    calendars: List<de.wochenplan.app.data.db.CalendarEntity>,
    selectedUrl: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = calendars.firstOrNull { it.url == selectedUrl }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = selected?.displayName ?: "Kein Kalender verfuegbar",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Kalender") },
            leadingIcon = { selected?.let { calendar -> CalendarDot(calendar) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            for (calendar in calendars) {
                DropdownMenuItem(
                    text = { Text(calendar.displayName) },
                    leadingIcon = { CalendarDot(calendar) },
                    onClick = {
                        onSelect(calendar.url)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Der Farbpunkt eines Kalenders. */
@Composable
private fun CalendarDot(calendar: de.wochenplan.app.data.db.CalendarEntity) {
    Box(
        Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(CalendarColors.resolve(calendar.color, calendar.url))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurrenceSelector(
    selected: RecurrenceOption,
    isSeries: Boolean,
    enabled: Boolean,
    onSelect: (RecurrenceOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = RecurrenceOption.entries.filter {
        it != RecurrenceOption.CUSTOM || selected == RecurrenceOption.CUSTOM
    }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Wiederholung") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeRow(
    label: String,
    date: LocalDate,
    time: LocalTime,
    showTime: Boolean,
    enabled: Boolean,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { showDatePicker = true },
                enabled = enabled,
                modifier = Modifier.weight(if (showTime) 1.4f else 1f),
            ) {
                Text(Formatters.date(date))
            }
            if (showTime) {
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(Formatters.time(time))
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("Uebernehmen") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = true,
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(LocalTime.of(pickerState.hour, pickerState.minute))
                    showTimePicker = false
                }) { Text("Uebernehmen") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Abbrechen") }
            },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = pickerState)
                }
            },
        )
    }
}
