package de.wochenplan.app.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.wochenplan.app.core.IsoWeek
import de.wochenplan.app.data.db.TaskTemplateWithItems
import de.wochenplan.app.ui.common.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TemplateViewModel = viewModel(factory = TemplateViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val editor = state.editor
    if (editor != null) {
        TemplateEditor(
            state = editor,
            onName = viewModel::setName,
            onDescription = viewModel::setDescription,
            onAddItem = viewModel::addItem,
            onUpdateItem = viewModel::updateItem,
            onRemoveItem = viewModel::removeItem,
            onCancel = viewModel::cancelEditing,
            onSave = viewModel::saveEditor,
            modifier = modifier,
        )
        return
    }

    var templateToDelete by remember { mutableStateOf<TaskTemplateWithItems?>(null) }
    var showSaveWeekDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Aufgaben-Vorlagen") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurueck")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::startNewTemplate,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Vorlage") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Vorlagen fuegen einen festen Satz Aufgaben in eine Kalenderwoche ein – " +
                            "etwa eine Standardwoche oder den Monatsabschluss.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { showSaveWeekDialog = true }) {
                        Text("${state.week.label} als Vorlage speichern")
                    }
                }
            }

            if (state.templates.isEmpty()) {
                item {
                    Text(
                        text = "Noch keine Vorlagen. Lege eine an oder uebernimm die Aufgaben " +
                            "der aktuellen Woche.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }

            items(state.templates, key = { it.template.id }) { template ->
                TemplateCard(
                    template = template,
                    week = state.week,
                    onApply = {
                        viewModel.applyToCurrentWeek(template.template.id, template.template.name)
                    },
                    onEdit = { viewModel.startEditing(template) },
                    onDelete = { templateToDelete = template },
                )
            }
        }
    }

    templateToDelete?.let { template ->
        AlertDialog(
            onDismissRequest = { templateToDelete = null },
            title = { Text("Vorlage loeschen") },
            text = { Text("Soll „${template.template.name}“ geloescht werden? Bereits eingefuegte Aufgaben bleiben erhalten.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(template.template.id, template.template.name)
                    templateToDelete = null
                }) { Text("Loeschen") }
            },
            dismissButton = {
                TextButton(onClick = { templateToDelete = null }) { Text("Abbrechen") }
            },
        )
    }

    if (showSaveWeekDialog) {
        NameDialog(
            title = "${state.week.label} als Vorlage",
            initialName = "Vorlage ${state.week.label}",
            onDismiss = { showSaveWeekDialog = false },
            onConfirm = { name ->
                viewModel.saveCurrentWeekAsTemplate(name)
                showSaveWeekDialog = false
            },
        )
    }
}

@Composable
private fun TemplateCard(
    template: TaskTemplateWithItems,
    week: IsoWeek,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(template.template.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${template.items.size} Aufgaben · ${template.plannedPomodoros} Pomodoros",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Bearbeiten")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Loeschen")
                }
            }

            for (item in template.orderedItems.take(4)) {
                Text(
                    text = buildString {
                        item.weekday?.let { append(Formatters.weekdayName(it).take(2)).append(" · ") }
                        append(item.title)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (template.items.size > 4) {
                Text(
                    text = "und ${template.items.size - 4} weitere",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = onApply,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text("In ${week.label} einfuegen")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TemplateEditor(
    state: TemplateEditorState,
    onName: (String) -> Unit,
    onDescription: (String) -> Unit,
    onAddItem: () -> Unit,
    onUpdateItem: (Long, (EditorItem) -> EditorItem) -> Unit,
    onRemoveItem: (Long) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Neue Vorlage" else "Vorlage bearbeiten") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "Abbrechen")
                    }
                },
                actions = {
                    IconButton(onClick = onSave, enabled = state.canSave) {
                        Icon(Icons.Filled.Check, contentDescription = "Speichern")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = onName,
                label = { Text("Name der Vorlage") },
                placeholder = { Text("z.B. Standardwoche") },
                singleLine = true,
                isError = state.name.isBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = onDescription,
                label = { Text("Notiz (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Aufgaben", style = MaterialTheme.typography.titleSmall)

            for (item in state.items) {
                EditorItemCard(
                    item = item,
                    onChange = { transform -> onUpdateItem(item.key, transform) },
                    onRemove = { onRemoveItem(item.key) },
                )
            }

            OutlinedButton(onClick = onAddItem, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Aufgabe hinzufuegen", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorItemCard(
    item: EditorItem,
    onChange: ((EditorItem) -> EditorItem) -> Unit,
    onRemove: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = item.title,
                    onValueChange = { value -> onChange { it.copy(title = value) } },
                    label = { Text("Aufgabe") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Eintrag entfernen")
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = item.weekday == null,
                    onClick = { onChange { it.copy(weekday = null) } },
                    label = { Text("Offen") },
                )
                for (day in 1..7) {
                    FilterChip(
                        selected = item.weekday == day,
                        onClick = { onChange { it.copy(weekday = day) } },
                        label = { Text(Formatters.weekdayName(day).take(2)) },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Geplante Pomodoros: ${item.plannedPomodoros}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onChange { it.copy(plannedPomodoros = (it.plannedPomodoros - 1).coerceAtLeast(0)) } },
                ) { Text("–") }
                TextButton(
                    onClick = { onChange { it.copy(plannedPomodoros = (it.plannedPomodoros + 1).coerceAtMost(16)) } },
                ) { Text("+") }
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Speichern")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
