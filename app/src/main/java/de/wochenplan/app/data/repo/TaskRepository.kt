package de.wochenplan.app.data.repo

import de.wochenplan.app.core.IsoWeek
import de.wochenplan.app.data.db.TaskDao
import de.wochenplan.app.data.db.TaskEntity
import kotlinx.coroutines.flow.Flow

/** Aufgabenliste je Kalenderwoche. Diese Daten bleiben auf dem Geraet. */
class TaskRepository(private val taskDao: TaskDao) {

    fun observeWeek(week: IsoWeek): Flow<List<TaskEntity>> = taskDao.observeWeek(week.key)

    suspend fun add(week: IsoWeek, title: String, weekday: Int?, plannedPomodoros: Int = 1): Long {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return -1
        return taskDao.insert(
            TaskEntity(
                weekKey = week.key,
                title = trimmed,
                weekday = weekday?.takeIf { it in 1..7 },
                plannedPomodoros = plannedPomodoros.coerceIn(0, 16),
                sortIndex = taskDao.maxSortIndex(week.key) + 1,
            )
        )
    }

    suspend fun update(task: TaskEntity) = taskDao.update(task)

    suspend fun setDone(id: Long, done: Boolean) = taskDao.setDone(id, done)

    suspend fun delete(id: Long) = taskDao.delete(id)

    suspend fun byId(id: Long): TaskEntity? = taskDao.byId(id)

    suspend fun countPomodoro(taskId: Long) = taskDao.incrementDonePomodoros(taskId)

    /**
     * Uebernimmt alle offenen Aufgaben einer Woche in die naechste.
     * Gibt die Anzahl der verschobenen Aufgaben zurueck.
     */
    suspend fun carryOverOpenTasks(from: IsoWeek, to: IsoWeek): Int {
        val open = taskDao.openTasksOfWeek(from.key)
        var offset = taskDao.maxSortIndex(to.key)
        for (task in open) {
            taskDao.insert(
                task.copy(
                    id = 0,
                    weekKey = to.key,
                    donePomodoros = 0,
                    sortIndex = ++offset,
                    createdAt = System.currentTimeMillis(),
                )
            )
            taskDao.delete(task.id)
        }
        return open.size
    }
}
