package de.wochenplan.app.ui.week

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.wochenplan.app.core.IsoWeek
import de.wochenplan.app.data.repo.DeleteScope
import de.wochenplan.app.data.repo.PlannedEvent
import de.wochenplan.app.ui.common.CalendarColors
import de.wochenplan.app.ui.common.Formatters
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(
    onCreateEvent: (LocalDate, LocalTime) -> Unit,
    onEditEvent: (PlannedEvent) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeekViewModel = viewModel(factory = WeekViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedEvent by remember { mutableStateOf<PlannedEvent?>(null) }
    var showWeekPicker by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PlannedEvent?>(null) }

    val today = LocalDate.now()
    val now by produceState(initialValue = LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(30_000)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable { showWeekPicker = true }) {
                        Text(state.week.label, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = state.week.rangeLabel(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.goToRelativeWeek(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Vorige Woche")
                    }
                    IconButton(onClick = { viewModel.goToToday() }) {
                        Icon(Icons.Filled.Today, contentDescription = "Aktuelle Woche")
                    }
                    IconButton(onClick = { viewModel.goToRelativeWeek(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Naechste Woche")
                    }
                    IconButton(onClick = { viewModel.refresh() }, enabled = !state.syncing) {
                        if (state.syncing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.accountConfigured && state.writableCalendars.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val day = if (state.week.contains(today)) today else state.week.monday
                        onCreateEvent(day, LocalTime.of(now.hour, 0).plusHours(1))
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Termin") },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (!state.accountConfigured) {
                SetupHint(onOpenSettings = onOpenSettings)
            } else if (state.calendars.isEmpty()) {
                EmptyCalendarsHint(onOpenSettings = onOpenSettings)
            }

            WeekGrid(
                week = state.week,
                events = state.events,
                hourRange = state.visibleHourRange,
                today = today,
                now = now,
                onEventClick = { selectedEvent = it },
                onSlotClick = { day, time ->
                    if (state.writableCalendars.isNotEmpty()) onCreateEvent(day, time)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    selectedEvent?.let { event ->
        ModalBottomSheet(
            onDismissRequest = { selectedEvent = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            EventDetails(
                event = event,
                onEdit = {
                    selectedEvent = null
                    onEditEvent(event)
                },
                onDelete = {
                    selectedEvent = null
                    pendingDelete = event
                },
            )
        }
    }

    pendingDelete?.let { event ->
        DeleteDialog(
            event = event,
            onDismiss = { pendingDelete = null },
            onConfirm = { scope ->
                viewModel.deleteEvent(event, scope)
                pendingDelete = null
            },
        )
    }

    if (showWeekPicker) {
        WeekPickerDialog(
            current = state.week,
            onDismiss = { showWeekPicker = false },
            onSelect = { week ->
                viewModel.selectWeek(week)
                showWeekPicker = false
            },
        )
    }
}

@Composable
private fun SetupHint(onOpenSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Noch kein Kalender verbunden",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = "Verbinde deinen CalDAV-Server, dann erscheinen deine Termine hier in der Wochenansicht.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        FilledTonalButton(onClick = onOpenSettings) { Text("Jetzt einrichten") }
    }
}

@Composable
private fun EmptyCalendarsHint(onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Keine Kalender geladen",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        TextButton(onClick = onOpenSettings) { Text("Einstellungen") }
    }
}

@Composable
private fun EventDetails(
    event: PlannedEvent,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val color = CalendarColors.resolve(event.colorHex, event.calendarUrl)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = event.calendarName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Text(event.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = Formatters.dateLong.format(event.occurrence.startDate),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(Formatters.range(event.occurrence), style = MaterialTheme.typography.bodyMedium)

        event.occurrence.location?.let { Text("Ort: $it", style = MaterialTheme.typography.bodyMedium) }
        event.occurrence.description?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
        if (event.occurrence.recurring) {
            Text(
                text = "Teil einer Terminserie",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 6.dp))

        if (event.readOnly) {
            Text(
                text = "Dieser Kalender ist schreibgeschuetzt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Bearbeiten", Modifier.padding(start = 8.dp))
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Loeschen", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun DeleteDialog(
    event: PlannedEvent,
    onDismiss: () -> Unit,
    onConfirm: (DeleteScope) -> Unit,
) {
    val isSeries = event.occurrence.recurring && event.occurrence.recurrenceId != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isSeries) "Serientermin loeschen" else "Termin loeschen") },
        text = {
            Text(
                if (isSeries) {
                    "„${event.title}“ gehoert zu einer Serie. Soll nur dieser Termin oder die ganze Serie geloescht werden?"
                } else {
                    "Soll „${event.title}“ wirklich geloescht werden?"
                }
            )
        },
        confirmButton = {
            if (isSeries) {
                TextButton(onClick = { onConfirm(DeleteScope.SINGLE_OCCURRENCE) }) { Text("Nur dieser") }
            } else {
                TextButton(onClick = { onConfirm(DeleteScope.WHOLE_SERIES) }) { Text("Loeschen") }
            }
        },
        dismissButton = {
            if (isSeries) {
                TextButton(onClick = { onConfirm(DeleteScope.WHOLE_SERIES) }) { Text("Ganze Serie") }
            } else {
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}

@Composable
private fun WeekPickerDialog(
    current: IsoWeek,
    onDismiss: () -> Unit,
    onSelect: (IsoWeek) -> Unit,
) {
    var year by remember { mutableStateOf(current.year) }
    val weeks = remember(year) { (1..IsoWeek.weeksInYear(year)).toList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { year -= 1 }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Vorheriges Jahr")
                }
                Text(year.toString(), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { year += 1 }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Naechstes Jahr")
                }
            }
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.heightIn(max = 320.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(weeks) { week ->
                    val isCurrent = year == current.year && week == current.week
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCurrent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .clickable { onSelect(IsoWeek(year, week)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = week.toString(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(IsoWeek.current()) }) { Text("Heute") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
