package de.wochenplan.app.ui.focus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import de.wochenplan.app.pomodoro.PomodoroPhase
import de.wochenplan.app.pomodoro.PomodoroService
import de.wochenplan.app.ui.theme.TimerTextStyle

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    modifier: Modifier = Modifier,
    viewModel: FocusViewModel = viewModel(factory = FocusViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    // Auf Wunsch bleibt der Bildschirm waehrend eines Abschnitts an.
    DisposableEffect(state.keepScreenOn, state.timer.running) {
        val window = view.context.findActivity()?.window
        if (state.keepScreenOn && state.timer.running) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Fokus") }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            PhaseSelector(
                current = state.timer.phase,
                onSelect = viewModel::switchPhase,
            )

            TimerDial(
                progress = state.timer.progress,
                remainingText = state.timer.remainingText,
                phaseTitle = state.phaseTitle,
                color = phaseColor(state.timer.phase),
            )

            Text(
                text = state.phaseHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            CycleDots(
                completed = state.timer.completedFocusInCycle,
                total = state.timer.cyclesBeforeLongBreak,
                color = phaseColor(PomodoroPhase.FOCUS),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { PomodoroService.send(context, PomodoroService.ACTION_RESET) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Zuruecksetzen")
                }
                Button(
                    onClick = { PomodoroService.send(context, PomodoroService.ACTION_TOGGLE) },
                    colors = ButtonDefaults.buttonColors(containerColor = phaseColor(state.timer.phase)),
                    modifier = Modifier.height(56.dp),
                ) {
                    Icon(
                        imageVector = if (state.timer.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                    )
                    Text(
                        text = if (state.timer.running) "Pausieren" else "Starten",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedButton(onClick = { PomodoroService.send(context, PomodoroService.ACTION_SKIP) }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Ueberspringen")
                }
            }

            TaskSelector(
                tasks = state.openTasks,
                selectedTitle = state.timer.taskTitle,
                onSelect = viewModel::selectTask,
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Heute", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${state.completedToday} Fokusabschnitte · ${state.focusMinutesToday} Minuten",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Naechsten Abschnitt automatisch starten", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = state.autoStartNextPhase, onCheckedChange = viewModel::setAutoStart)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Sucht die Activity, auch wenn der Context in Wrapper eingepackt ist. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun phaseColor(phase: PomodoroPhase): Color = when (phase) {
    PomodoroPhase.FOCUS -> MaterialTheme.colorScheme.primary
    PomodoroPhase.SHORT_BREAK -> MaterialTheme.colorScheme.secondary
    PomodoroPhase.LONG_BREAK -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun PhaseSelector(current: PomodoroPhase, onSelect: (PomodoroPhase) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (phase in PomodoroPhase.entries) {
            FilterChip(
                selected = current == phase,
                onClick = { onSelect(phase) },
                label = {
                    Text(
                        when (phase) {
                            PomodoroPhase.FOCUS -> "Fokus"
                            PomodoroPhase.SHORT_BREAK -> "Kurz"
                            PomodoroPhase.LONG_BREAK -> "Lang"
                        }
                    )
                },
            )
        }
    }
}

@Composable
private fun TimerDial(
    progress: Float,
    remainingText: String,
    phaseTitle: String,
    color: Color,
) {
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "timer-progress")
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier.size(260.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = remainingText, style = TimerTextStyle, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = phaseTitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CycleDots(completed: Int, total: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (index in 0 until total.coerceIn(1, 12)) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < completed) color else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
    }
}

@Composable
private fun TaskSelector(
    tasks: List<de.wochenplan.app.data.db.TaskEntity>,
    selectedTitle: String?,
    onSelect: (de.wochenplan.app.data.db.TaskEntity?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selectedTitle ?: "Aufgabe verknuepfen (optional)")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Ohne Aufgabe") },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            if (tasks.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Keine offenen Aufgaben in dieser Woche") },
                    onClick = { expanded = false },
                    enabled = false,
                )
            }
            for (task in tasks) {
                DropdownMenuItem(
                    text = { Text(task.title) },
                    onClick = {
                        onSelect(task)
                        expanded = false
                    },
                )
            }
        }
    }
}
