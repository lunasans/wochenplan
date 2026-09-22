package de.wochenplan.app.ui.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.wochenplan.app.core.IsoWeek
import de.wochenplan.app.data.repo.PlannedEvent
import de.wochenplan.app.ui.common.CalendarColors
import de.wochenplan.app.ui.common.Formatters
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

private val HOUR_HEIGHT = 62.dp
private val TIME_AXIS_WIDTH = 44.dp

/**
 * Das Wochenraster: Montag bis Sonntag nebeneinander, Stunden untereinander.
 *
 * Ganztaegige Termine stehen in einem eigenen Streifen oben, damit sie das
 * Zeitraster nicht auseinanderziehen.
 */
@Composable
fun WeekGrid(
    week: IsoWeek,
    events: List<PlannedEvent>,
    hourRange: IntRange,
    today: LocalDate,
    now: LocalTime,
    onEventClick: (PlannedEvent) -> Unit,
    onSlotClick: (LocalDate, LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = remember(week) { week.days }
    val allDayEvents = remember(events) { events.filter { it.allDay } }
    val startHour = hourRange.first
    val endHour = hourRange.last
    val hourCount = (endHour - startHour).coerceAtLeast(1)

    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // Beim Oeffnen auf eine sinnvolle Stunde springen statt auf Mitternacht.
    androidx.compose.runtime.LaunchedEffect(week, startHour) {
        val focusHour = if (week.contains(today)) now.hour - 1 else 8
        val offset = ((focusHour - startHour).coerceAtLeast(0)) * with(density) { HOUR_HEIGHT.toPx() }
        scrollState.scrollTo(offset.toInt())
    }

    Column(modifier) {
        DayHeaderRow(days = days, today = today)
        if (allDayEvents.isNotEmpty()) {
            AllDayStrip(days = days, events = allDayEvents, onEventClick = onEventClick)
        }
        HorizontalDivider()

        Row(
            Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            TimeAxis(startHour = startHour, hourCount = hourCount)

            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .height(HOUR_HEIGHT * hourCount)
            ) {
                val columnWidth = maxWidth / 7
                val hourHeightPx = with(density) { HOUR_HEIGHT.toPx() }
                val columnWidthPx = with(density) { columnWidth.toPx() }

                GridBackground(
                    hourCount = hourCount,
                    columnWidthPx = columnWidthPx,
                    hourHeightPx = hourHeightPx,
                    lineColor = MaterialTheme.colorScheme.outlineVariant,
                    weekendColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                )

                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(week, startHour) {
                            detectTapGestures { offset ->
                                val dayIndex = (offset.x / columnWidthPx).toInt().coerceIn(0, 6)
                                val rawMinutes = startHour * 60 + (offset.y / hourHeightPx * 60f).toInt()
                                val rounded = ((rawMinutes / 15) * 15).coerceIn(0, 23 * 60 + 45)
                                onSlotClick(days[dayIndex], LocalTime.of(rounded / 60, rounded % 60))
                            }
                        }
                )

                if (week.contains(today)) {
                    NowIndicator(
                        dayIndex = today.dayOfWeek.value - 1,
                        now = now,
                        startHour = startHour,
                        endHour = endHour,
                        columnWidth = columnWidth,
                    )
                }

                days.forEachIndexed { dayIndex, day ->
                    val positioned = remember(events, day) { EventLayout.layoutDay(events, day) }
                    for (item in positioned) {
                        val topMinutes = (item.startMinute - startHour * 60).coerceAtLeast(0)
                        val visibleMinutes = maxOf(item.durationMinutes, EventLayout.MIN_VISIBLE_MINUTES)
                        val slotWidth = columnWidth / item.columnCount

                        EventChip(
                            event = item.event,
                            showTime = visibleMinutes >= 45,
                            onClick = { onEventClick(item.event) },
                            modifier = Modifier
                                .offset(
                                    x = columnWidth * dayIndex + slotWidth * item.column,
                                    y = HOUR_HEIGHT * (topMinutes / 60f),
                                )
                                .width(slotWidth)
                                .height(HOUR_HEIGHT * (visibleMinutes / 60f))
                                .padding(end = 2.dp, bottom = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeaderRow(days: List<LocalDate>, today: LocalDate) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Spacer(Modifier.width(TIME_AXIS_WIDTH))
        for (day in days) {
            val isToday = day == today
            val isWeekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = Formatters.dayShort.format(day),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isWeekend) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AllDayStrip(
    days: List<LocalDate>,
    events: List<PlannedEvent>,
    onEventClick: (PlannedEvent) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(vertical = 3.dp)
    ) {
        Spacer(Modifier.width(TIME_AXIS_WIDTH))
        for (day in days) {
            val ofDay = events.filter { event ->
                !day.isBefore(event.occurrence.startDate) && !day.isAfter(event.occurrence.lastVisibleDate)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (event in ofDay.take(3)) {
                    val color = CalendarColors.resolve(event.colorHex, event.calendarUrl)
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = CalendarColors.contentColorOn(color),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                            .clickable { onEventClick(event) }
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                }
                if (ofDay.size > 3) {
                    Text(
                        text = "+${ofDay.size - 3}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeAxis(startHour: Int, hourCount: Int) {
    Column(Modifier.width(TIME_AXIS_WIDTH)) {
        for (index in 0 until hourCount) {
            Box(
                modifier = Modifier
                    .height(HOUR_HEIGHT)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopEnd,
            ) {
                Text(
                    text = "%02d:00".format(startHour + index),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .offset(y = (-6).dp)
                        .padding(end = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun GridBackground(
    hourCount: Int,
    columnWidthPx: Float,
    hourHeightPx: Float,
    lineColor: Color,
    weekendColor: Color,
) {
    Canvas(Modifier.fillMaxSize()) {
        // Samstag und Sonntag leicht hinterlegen.
        drawRect(
            color = weekendColor,
            topLeft = Offset(columnWidthPx * 5, 0f),
            size = Size(columnWidthPx * 2, size.height),
        )
        for (index in 0..hourCount) {
            val y = index * hourHeightPx
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        for (index in 1 until 7) {
            val x = index * columnWidthPx
            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        }
    }
}

@Composable
private fun NowIndicator(
    dayIndex: Int,
    now: LocalTime,
    startHour: Int,
    endHour: Int,
    columnWidth: Dp,
) {
    val minutes = now.hour * 60 + now.minute
    if (minutes < startHour * 60 || minutes > endHour * 60) return

    Box(
        Modifier
            .offset(
                x = columnWidth * dayIndex,
                y = HOUR_HEIGHT * ((minutes - startHour * 60) / 60f) - 1.dp,
            )
            .width(columnWidth)
            .height(2.dp)
            .background(MaterialTheme.colorScheme.error)
    )
}

@Composable
private fun EventChip(
    event: PlannedEvent,
    showTime: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = CalendarColors.resolve(event.colorHex, event.calendarUrl)
    val contentColor = CalendarColors.contentColorOn(color)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = event.title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (showTime) {
            Text(
                text = Formatters.time(event.occurrence.start),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.85f),
                maxLines = 1,
            )
        }
    }
}
