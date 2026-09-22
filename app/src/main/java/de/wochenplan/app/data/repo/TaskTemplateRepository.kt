package de.wochenplan.app.data.repo

import de.wochenplan.app.core.IsoWeek
import de.wochenplan.app.data.db.TaskDao
import de.wochenplan.app.data.db.TaskEntity
import de.wochenplan.app.data.db.TaskTemplateDao
import de.wochenplan.app.data.db.TaskTemplateEntity
import de.wochenplan.app.data.db.TaskTemplateItemEntity
import de.wochenplan.app.data.db.TaskTemplateWithItems
import kotlinx.coroutines.flow.Flow

/**
 * Wandelt zwischen Vorlagen und Aufgaben um.
 *
 * Bewusst ohne Datenbank- oder Android-Bezug, damit sich die Regeln ohne Geraet
 * pruefen lassen.
 */
object TaskTemplateMapper {

    /** Aus den Eintraegen einer Vorlage werden Aufgaben einer Woche. */
    fun toTasks(
        items: List<TaskTemplateItemEntity>,
        weekKey: String,
        startSortIndex: Int = 0,
        now: Long = System.currentTimeMillis(),
    ): List<TaskEntity> = items
        .sortedWith(compareBy({ it.sortIndex }, { it.id }))
        .filter { it.title.isNotBlank() }
        .mapIndexed { index, item ->
            TaskEntity(
                weekKey = weekKey,
                title = item.title.trim(),
                notes = item.notes?.trim()?.takeIf { it.isNotEmpty() },
                weekday = item.weekday?.takeIf { it in 1..7 },
                // Eine frisch eingefuegte Aufgabe ist immer offen und ohne
                // bereits geleistete Pomodoros.
                done = false,
                plannedPomodoros = item.plannedPomodoros.coerceIn(0, 16),
                donePomodoros = 0,
                sortIndex = startSortIndex + index + 1,
                createdAt = now,
            )
        }

    /** Aus den Aufgaben einer Woche wird eine Vorlage. */
    fun toItems(tasks: List<TaskEntity>, templateId: Long = 0): List<TaskTemplateItemEntity> = tasks
        .sortedWith(compareBy({ it.weekday ?: 8 }, { it.sortIndex }, { it.id }))
        .filter { it.title.isNotBlank() }
        .mapIndexed { index, task ->
            TaskTemplateItemEntity(
                templateId = templateId,
                title = task.title.trim(),
                notes = task.notes?.trim()?.takeIf { it.isNotEmpty() },
                weekday = task.weekday?.takeIf { it in 1..7 },
                plannedPomodoros = task.plannedPomodoros.coerceIn(0, 16),
                sortIndex = index,
            )
        }
}

/** Vorlagen fuer wiederkehrende Aufgaben. Die Daten bleiben auf dem Geraet. */
class TaskTemplateRepository(
    private val templateDao: TaskTemplateDao,
    private val taskDao: TaskDao,
) {

    fun observeTemplates(): Flow<List<TaskTemplateWithItems>> = templateDao.observeAll()

    suspend fun byId(id: Long): TaskTemplateWithItems? = templateDao.byId(id)

    /**
     * Fuegt die Aufgaben einer Vorlage in [week] ein und gibt ihre Anzahl zurueck.
     * Vorhandene Aufgaben der Woche bleiben unberuehrt.
     */
    suspend fun applyToWeek(templateId: Long, week: IsoWeek): Int {
        val template = templateDao.byId(templateId) ?: return 0
        val tasks = TaskTemplateMapper.toTasks(
            items = template.items,
            weekKey = week.key,
            startSortIndex = taskDao.maxSortIndex(week.key),
        )
        for (task in tasks) taskDao.insert(task)
        templateDao.markUsed(templateId, System.currentTimeMillis())
        return tasks.size
    }

    /** Speichert die Aufgaben einer Woche als neue Vorlage. */
    suspend fun saveWeekAsTemplate(week: IsoWeek, name: String): Long {
        val tasks = taskDao.tasksOfWeek(week.key)
        if (tasks.isEmpty()) return -1
        return templateDao.saveWithItems(
            template = TaskTemplateEntity(
                name = name.trim().ifEmpty { "Vorlage ${week.label}" },
                description = "Erstellt aus ${week.label} (${week.rangeLabel()})",
                sortIndex = templateDao.maxSortIndex() + 1,
            ),
            items = TaskTemplateMapper.toItems(tasks),
        )
    }

    /** Legt eine Vorlage an oder aendert sie. [id] von 0 bedeutet "neu". */
    suspend fun save(
        id: Long,
        name: String,
        description: String?,
        items: List<TaskTemplateItemEntity>,
    ): Long {
        val existing = if (id == 0L) null else templateDao.byId(id)?.template
        return templateDao.saveWithItems(
            template = TaskTemplateEntity(
                id = id,
                name = name.trim().ifEmpty { "Ohne Namen" },
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                sortIndex = existing?.sortIndex ?: (templateDao.maxSortIndex() + 1),
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                lastUsedAt = existing?.lastUsedAt,
            ),
            items = items.filter { it.title.isNotBlank() },
        )
    }

    suspend fun delete(id: Long) = templateDao.deleteTemplate(id)
}
