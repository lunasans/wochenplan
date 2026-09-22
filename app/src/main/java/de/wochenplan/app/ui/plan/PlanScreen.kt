package de.wochenplan.app.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.wochenplan.app.data.ai.PlanEntryKind
import de.wochenplan.app.data.ai.WeekPlanEntry
import de.wochenplan.app.data.ai.WeekPlanProposal
import de.wochenplan.app.ui.common.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanViewModel = viewModel(factory = PlanViewModel.Factory),
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Woche planen lassen", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${state.week.label} · ${state.week.rangeLabel()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurueck")
                    }
                },
            )
        },
        bottomBar = {
            val proposal = state.proposal
            if (proposal != null && !proposal.isEmpty) {
                ApplyBar(
                    acceptedCount = state.acceptedCount,
                    applying = state.applying,
                    onApply = viewModel::applyAccepted,
                    onDiscard = viewModel::discard,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.hasApiKey) {
                item { MissingKeyCard(onOpenSettings = onOpenSettings) }
                return@LazyColumn
            }

            val proposal = state.proposal
            if (proposal == null) {
                item {
                    GoalsInput(
                        goals = state.goals,
                        enabled = state.canPlan,
                        loading = state.loading,
                        onGoals = viewModel::setGoals,
                        onGenerate = viewModel::generate,
                    )
                }
                return@LazyColumn
            }

            item { ProposalHeader(proposal = proposal, onAll = viewModel::selectAll, onNone = viewModel::selectNone) }

            if (state.hasTimedEntries && state.writableCalendars.isNotEmpty()) {
                item {
                    CalendarChoice(
                        calendars = state.writableCalendars,
                        selectedUrl = state.targetCalendarUrl,
                        onSelect = viewModel::setTargetCalendar,
                    )
                }
            }

            items(proposal.entries, key = { it.id }) { entry ->
                EntryCard(
                    entry = entry,
                    accepted = entry.id in state.accepted,
                    onToggle = { viewModel.toggleEntry(entry.id) },
                )
            }
        }
    }
}

@Composable
private fun MissingKeyCard(onOpenSettings: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "KI-Planung noch nicht eingerichtet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "Fuer diese Funktion brauchst du einen eigenen API-Schluessel von Anthropic. " +
                    "Er wird verschluesselt auf dem Geraet abgelegt, und die Abrechnung laeuft ueber dein Konto.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Button(onClick = onOpenSettings) { Text("Zu den Einstellungen") }
        }
    }
}

@Composable
private fun GoalsInput(
    goals: String,
    enabled: Boolean,
    loading: Boolean,
    onGoals: (String) -> Unit,
    onGenerate: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Was nimmst du dir fuer diese Woche vor? Die KI beruecksichtigt deine " +
                "bereits eingetragenen Termine und die offenen Aufgaben der Woche.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = goals,
            onValueChange = onGoals,
            label = { Text("Ziele der Woche") },
            placeholder = { Text("z.B. Steuererklaerung fertig machen, dreimal Sport, Vortrag vorbereiten") },
            minLines = 4,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )

        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = "Die KI plant deine Woche. Das dauert einen Moment.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        } else {
            Button(onClick = onGenerate, enabled = enabled) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Vorschlag erstellen", Modifier.padding(start = 8.dp))
            }
        }

        Text(
            text = "Hinweis: Dafuer werden die Titel deiner Termine und Aufgaben dieser Woche " +
                "an Anthropic uebertragen. Es wird nichts gespeichert oder geaendert, " +
                "bevor du den Vorschlag bestaetigst.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProposalHeader(
    proposal: WeekPlanProposal,
    onAll: () -> Unit,
    onNone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (proposal.summary.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    text = proposal.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        for (warning in proposal.warnings) {
            Text(
                text = "Hinweis: $warning",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onAll) { Text("Alle") }
            TextButton(onClick = onNone) { Text("Keine") }
        }
    }
}

@Composable
private fun CalendarChoice(
    calendars: List<de.wochenplan.app.data.db.CalendarEntity>,
    selectedUrl: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = calendars.firstOrNull { it.url == selectedUrl }

    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Termine anlegen in: ${selected?.displayName ?: "kein Kalender"}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (calendar in calendars) {
                DropdownMenuItem(
                    text = { Text(calendar.displayName) },
                    onClick = {
                        onSelect(calendar.url)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EntryCard(
    entry: WeekPlanEntry,
    accepted: Boolean,
    onToggle: () -> Unit,
) {
    Card {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = accepted, onCheckedChange = { onToggle() })

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(entry.title, style = MaterialTheme.typography.bodyLarge)

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = onToggle,
                        label = {
                            Text(
                                text = when {
                                    entry.kind == PlanEntryKind.EVENT && entry.start != null && entry.end != null ->
                                        "${dayLabel(entry)} ${Formatters.time(entry.start)}–${Formatters.time(entry.end)}"

                                    else -> dayLabel(entry)
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                    if (entry.kind == PlanEntryKind.TASK && entry.plannedPomodoros > 0) {
                        AssistChip(
                            onClick = onToggle,
                            label = {
                                Text("${entry.plannedPomodoros} 🍅", style = MaterialTheme.typography.labelSmall)
                            },
                        )
                    }
                }

                if (entry.reason.isNotEmpty()) {
                    Text(
                        text = entry.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun dayLabel(entry: WeekPlanEntry): String =
    entry.weekday?.let { Formatters.weekdayName(it) } ?: "Ohne festen Tag"

@Composable
private fun ApplyBar(
    acceptedCount: Int,
    applying: Boolean,
    onApply: () -> Unit,
    onDiscard: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onDiscard, enabled = !applying) { Text("Verwerfen") }
        Button(
            onClick = onApply,
            enabled = acceptedCount > 0 && !applying,
            modifier = Modifier.weight(1f),
        ) {
            if (applying) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("$acceptedCount uebernehmen")
            }
        }
    }
}
