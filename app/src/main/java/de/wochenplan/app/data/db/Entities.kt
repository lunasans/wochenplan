package de.wochenplan.app.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Ein Kalender vom CalDAV-Server, samt lokaler Anzeigeeinstellungen. */
@Entity(tableName = "calendars")
data class CalendarEntity(
    @PrimaryKey val url: String,
    val displayName: String,
    val color: String?,
    val readOnly: Boolean,
    val visible: Boolean = true,
    val ctag: String? = null,
    val sortIndex: Int = 0,
)

/**
 * Zwischenspeicher fuer die Termine einer Kalenderwoche.
 *
 * Gespeichert wird das unveraenderte iCalendar-Objekt. Dadurch bleiben auch
 * Angaben erhalten, die diese App nicht auswertet, und Aenderungen lassen sich
 * schreiben, ohne fremde Daten zu verlieren. Ein Objekt, das mehrere Wochen
 * beruehrt, liegt je Woche einmal vor – das kostet wenig und macht das
 * Anzeigen ohne Netz einfach.
 */
@Entity(
    tableName = "cached_events",
    primaryKeys = ["weekKey", "href"],
    indices = [Index("weekKey"), Index("calendarUrl")],
)
data class CachedEventEntity(
    val weekKey: String,
    val href: String,
    val calendarUrl: String,
    val etag: String?,
    val ics: String,
    val fetchedAt: Long,
)

/** Eine Aufgabe, die zu einer Kalenderwoche geplant ist. Rein lokal. */
@Entity(tableName = "tasks", indices = [Index("weekKey")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekKey: String,
    val title: String,
    val notes: String? = null,
    /** 1 = Montag bis 7 = Sonntag, `null` fuer Aufgaben ohne festen Tag. */
    val weekday: Int? = null,
    val done: Boolean = false,
    val plannedPomodoros: Int = 1,
    val donePomodoros: Int = 0,
    val sortIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Ein abgeschlossener Abschnitt des Pomodoro-Timers, fuer die Statistik. */
@Entity(tableName = "focus_sessions", indices = [Index("startedAt")])
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long? = null,
    val taskTitle: String? = null,
    /** FOCUS, SHORT_BREAK oder LONG_BREAK. */
    val phase: String,
    val startedAt: Long,
    val finishedAt: Long,
    val plannedMinutes: Int,
    /** `false`, wenn der Abschnitt vorzeitig abgebrochen wurde. */
    val completed: Boolean,
)

/**
 * Eine Vorlage fuer wiederkehrende Aufgaben, z.B. "Standardwoche" oder
 * "Monatsabschluss". Eine Vorlage mit einem einzigen Eintrag ist die Vorlage
 * fuer eine einzelne Aufgabe.
 */
@Entity(tableName = "task_templates")
data class TaskTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val sortIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** Wann die Vorlage zuletzt angewendet wurde. */
    val lastUsedAt: Long? = null,
)

/** Eine Aufgabe innerhalb einer Vorlage. */
@Entity(
    tableName = "task_template_items",
    foreignKeys = [
        ForeignKey(
            entity = TaskTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("templateId")],
)
data class TaskTemplateItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val title: String,
    val notes: String? = null,
    /** 1 = Montag bis 7 = Sonntag, `null` fuer Aufgaben ohne festen Tag. */
    val weekday: Int? = null,
    val plannedPomodoros: Int = 1,
    val sortIndex: Int = 0,
)

/** Eine Vorlage mit ihren Eintraegen. */
data class TaskTemplateWithItems(
    @Embedded val template: TaskTemplateEntity,
    @Relation(parentColumn = "id", entityColumn = "templateId")
    val items: List<TaskTemplateItemEntity>,
) {
    /** Die Eintraege in der vom Benutzer festgelegten Reihenfolge. */
    val orderedItems: List<TaskTemplateItemEntity>
        get() = items.sortedWith(compareBy({ it.sortIndex }, { it.id }))

    val plannedPomodoros: Int get() = items.sumOf { it.plannedPomodoros }
}
