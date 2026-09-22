package de.wochenplan.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoveDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.wochenplan.app.data.db.TaskEntity
import de.wochenplan.app.ui.common.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(
    onOpenFocus: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskViewModel = viewModel(factory = TaskViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<TaskEntity?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Aufgaben ${state.week.label}", style = MaterialTheme.typography.titleLarge)
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
                    IconButton(onClick = viewModel::goToToday) {
                        Icon(Icons.Filled.Today, contentDescription = "Aktuelle Woche")
                    }
                    IconButton(onClick = { viewModel.goToRelativeWeek(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Naechste Woche")
                    }
                },
            )
        },
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Aufgabe hinzufuegen")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                WeekSummary(
                    openTasks = state.openTasks,
                    doneTasks = state.doneTasks,
                    plannedPomodoros = state.plannedPomodoros,
                    focusMinutes = state.focusMinutesThisWeek,
                    onCarryOver = viewModel::carryOverToNextWeek,
                )
            }

            if (state.tasks.isEmpty()) {
                item {
                    Text(
                        text = "Noch keine Aufgaben fuer diese Woche. Mit dem Plus unten rechts geht es los.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            for ((weekday, tasks) in state.byWeekday) {
                item(key = "header-$weekday") {
                    Text(
                        text = if (weekday == 0) "Ohne festen Tag" else Formatters.weekdayName(weekday),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(tasks, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        onToggle = { viewModel.setDone(task, it) },
                        onWeekdayChange = { viewModel.setWeekday(task, it) },
                        onPomodoroChange = { viewModel.setPlannedPomodoros(task, it) },
                        onStartFocus = {
                            viewModel.startFocusOn(task)
                            onOpenFocus()
                        },
                        onDelete = { taskToDelete = task },
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddTaskDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, weekday, pomodoros ->
                viewModel.addTask(title, weekday, pomodoros)
                showAddDialog = false
            },
        )
    }

    taskToDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Aufgabe loeschen") },
            text = { Text("Soll „${task.title}“ geloescht werden?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(task)
                    taskToDelete = null
                }) { Text("Loeschen") }
            },
            dismissButton = { TextButton(onClick = { taskToDelete = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun WeekSummary(
    openTasks: Int,
    doneTasks: Int,
    plannedPomodoros: Int,
    focusMinutes: Int,
    onCarryOver: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "$openTasks offen, $doneTasks erledigt",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "Geplant: $plannedPomodoros Pomodoros · Fokuszeit diese Woche: $focusMinutes Min.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (openTasks > 0) {
                TextButton(onClick = onCarryOver) {
                    Icon(Icons.Filled.MoveDown, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Offene in naechste Woche", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskRow(
    task: TaskEntity,
    onToggle: (Boolean) -> Unit,
    onWeekdayChange: (Int?) -> Unit,
    onPomodoroChange: (Int) -> Unit,
    onStartFocus: () -> Unit,
    onDelete: () -> Unit,
) {
    var showWeekdayMenu by remember { mutableStateOf(false) }

    Card {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = task.done, onCheckedChange = onToggle)

            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box {
                        AssistChip(
                            onClick = { showWeekdayMenu = true },
                            label = {
                                Text(
                                    text = task.weekday?.let(Formatters::weekdayName) ?: "Tag waehlen",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                        DropdownMenu(expanded = showWeekdayMenu, onDismissRequest = { showWeekdayMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Ohne festen Tag") },
                                onClick = {
                                    onWeekdayChange(null)
                                    showWeekdayMenu = false
                                },
                            )
                            for (day in 1..7) {
                                DropdownMenuItem(
                                    text = { Text(Formatters.weekdayName(day)) },
                                    onClick = {
                                        onWeekdayChange(day)
                                        showWeekdayMenu = false
                                    },
                                )
                            }
                        }
                    }
                    PomodoroCounter(
                        planned = task.plannedPomodoros,
                        done = task.donePomodoros,
                        onChange = onPomodoroChange,
                    )
                }
            }

            if (!task.done) {
                IconButton(onClick = onStartFocus) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Fokus starten")
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Loeschen")
            }
        }
    }
}

@Composable
private fun PomodoroCounter(planned: Int, done: Int, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
            .padding(horizontal = 4.dp),
    ) {
        Text(
            text = "–",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .clickable { onChange(planned - 1) },
        )
        Text(
            text = "$done/$planned 🍅",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "+",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .clickable { onChange(planned + 1) },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int?, Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var weekday by remember { mutableStateOf<Int?>(null) }
    var pomodoros by remember { mutableIntStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Aufgabe") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Was steht an?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Tag", style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = weekday == null,
                        onClick = { weekday = null },
                        label = { Text("Offen") },
                    )
                    for (day in 1..7) {
                        FilterChip(
                            selected = weekday == day,
                            onClick = { weekday = day },
                            label = { Text(Formatters.weekdayName(day).take(2)) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Geplante Pomodoros: $pomodoros", style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { pomodoros = (pomodoros - 1).coerceAtLeast(0) }) { Text("–") }
                    IconButton(onClick = { pomodoros = (pomodoros + 1).coerceAtMost(16) }) { Text("+") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, weekday, pomodoros) },
                enabled = title.isNotBlank(),
            ) { Text("Hinzufuegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
