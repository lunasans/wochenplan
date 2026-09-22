package de.wochenplan.app.ui.week

import de.wochenplan.app.data.repo.PlannedEvent
import java.time.LocalDate

/**
 * Ein Termin mit seiner Position im Tagesraster.
 *
 * [column] und [columnCount] verteilen sich ueberschneidende Termine
 * nebeneinander, wie man es von Kalenderansichten kennt.
 */
data class PositionedEvent(
    val event: PlannedEvent,
    val startMinute: Int,
    val endMinute: Int,
    val column: Int,
    val columnCount: Int,
) {
    val durationMinutes: Int get() = (endMinute - startMinute).coerceAtLeast(1)
}

object EventLayout {

    /** Kuerzeste Darstellung, damit auch 5-Minuten-Termine antippbar bleiben. */
    const val MIN_VISIBLE_MINUTES = 20

    /**
     * Ordnet die Termine eines Tages an. Termine ueber mehrere Tage werden auf
     * den jeweiligen Tag zugeschnitten.
     */
    fun layoutDay(events: List<PlannedEvent>, day: LocalDate): List<PositionedEvent> {
        val dayStart = day.atStartOfDay()
        val dayEnd = day.plusDays(1).atStartOfDay()

        val spans = events
            .filter { !it.allDay }
            .mapNotNull { event ->
                val occurrence = event.occurrence
                // Der Termin beruehrt den Tag, wenn er davor endet und danach beginnt.
                if (!occurrence.start.isBefore(dayEnd)) return@mapNotNull null
                if (!occurrence.end.isAfter(dayStart)) return@mapNotNull null

                val startMinute = if (occurrence.start.isBefore(dayStart)) {
                    0
                } else {
                    occurrence.start.toLocalTime().toSecondOfDay() / 60
                }
                val endMinute = if (occurrence.end.isAfter(dayEnd)) {
                    24 * 60
                } else {
                    (occurrence.end.toLocalTime().toSecondOfDay() / 60).let { if (it == 0) 24 * 60 else it }
                }
                if (endMinute <= startMinute) return@mapNotNull null
                Span(event, startMinute, endMinute)
            }
            .sortedWith(compareBy({ it.startMinute }, { -it.endMinute }))

        return assignColumns(spans)
    }

    /** Teilt sich ueberschneidende Termine in Spalten auf. */
    private fun assignColumns(spans: List<Span>): List<PositionedEvent> {
        val result = mutableListOf<PositionedEvent>()
        var cluster = mutableListOf<Pair<Span, Int>>()
        var columnEnds = mutableListOf<Int>()
        var clusterEnd = Int.MIN_VALUE

        fun flush() {
            if (cluster.isEmpty()) return
            val columnCount = columnEnds.size
            for ((span, column) in cluster) {
                result.add(
                    PositionedEvent(
                        event = span.event,
                        startMinute = span.startMinute,
                        endMinute = span.endMinute,
                        column = column,
                        columnCount = columnCount,
                    )
                )
            }
            cluster = mutableListOf()
            columnEnds = mutableListOf()
            clusterEnd = Int.MIN_VALUE
        }

        for (span in spans) {
            // Der sichtbare Block ist mindestens MIN_VISIBLE_MINUTES lang und
            // bestimmt daher auch, was sich optisch ueberschneidet.
            val visibleEnd = maxOf(span.endMinute, span.startMinute + MIN_VISIBLE_MINUTES)
            if (span.startMinute >= clusterEnd) flush()

            // Erste Spalte, die zu diesem Zeitpunkt wieder frei ist, sonst eine neue.
            val freeColumn = columnEnds.indexOfFirst { it <= span.startMinute }
            val column = if (freeColumn >= 0) {
                freeColumn
            } else {
                columnEnds.add(visibleEnd)
                columnEnds.lastIndex
            }
            columnEnds[column] = visibleEnd

            cluster.add(span to column)
            clusterEnd = maxOf(clusterEnd, visibleEnd)
        }
        flush()
        return result
    }

    private data class Span(val event: PlannedEvent, val startMinute: Int, val endMinute: Int)
}
