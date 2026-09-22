package de.wochenplan.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Abstrakte Klassen statt Schnittstellen: Room kann Methoden mit @Transaction
// und eigenem Rumpf nur dort zuverlaessig um eine Transaktion legen.
@Dao
abstract class CalendarDao {

    @Query("SELECT * FROM calendars ORDER BY sortIndex, displayName COLLATE NOCASE")
    abstract fun observeAll(): Flow<List<CalendarEntity>>

    @Query("SELECT * FROM calendars WHERE visible = 1 ORDER BY sortIndex, displayName COLLATE NOCASE")
    abstract suspend fun visibleCalendars(): List<CalendarEntity>

    @Query("SELECT * FROM calendars ORDER BY sortIndex, displayName COLLATE NOCASE")
    abstract suspend fun allCalendars(): List<CalendarEntity>

    @Query("SELECT * FROM calendars WHERE url = :url")
    abstract suspend fun byUrl(url: String): CalendarEntity?

    @Upsert
    abstract suspend fun upsert(calendars: List<CalendarEntity>)

    @Query("UPDATE calendars SET visible = :visible WHERE url = :url")
    abstract suspend fun setVisible(url: String, visible: Boolean)

    @Query("DELETE FROM calendars WHERE url NOT IN (:keepUrls)")
    abstract suspend fun deleteMissing(keepUrls: List<String>)

    @Query("DELETE FROM calendars")
    abstract suspend fun deleteAll()

    /** Ersetzt die Kalenderliste, behaelt dabei die Sichtbarkeitseinstellung. */
    @Transaction
    open suspend fun replaceAll(calendars: List<CalendarEntity>) {
        val known = allCalendars().associateBy { it.url }
        val merged = calendars.map { calendar ->
            calendar.copy(visible = known[calendar.url]?.visible ?: true)
        }
        upsert(merged)
        if (merged.isEmpty()) deleteAll() else deleteMissing(merged.map { it.url })
    }
}

@Dao
abstract class EventCacheDao {

    @Query("SELECT * FROM cached_events WHERE weekKey = :weekKey")
    abstract fun observeWeek(weekKey: String): Flow<List<CachedEventEntity>>

    @Query("SELECT * FROM cached_events WHERE href = :href LIMIT 1")
    abstract suspend fun byHref(href: String): CachedEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(events: List<CachedEventEntity>)

    @Query("DELETE FROM cached_events WHERE weekKey = :weekKey AND calendarUrl = :calendarUrl")
    abstract suspend fun deleteWeekOfCalendar(weekKey: String, calendarUrl: String)

    @Query("DELETE FROM cached_events WHERE href = :href")
    abstract suspend fun deleteByHref(href: String)

    @Query("DELETE FROM cached_events WHERE calendarUrl NOT IN (:keepUrls)")
    abstract suspend fun deleteOrphans(keepUrls: List<String>)

    @Query("DELETE FROM cached_events")
    abstract suspend fun deleteAll()

    /** Schreibt das Ergebnis einer Abfrage fuer genau eine Woche und einen Kalender. */
    @Transaction
    open suspend fun replaceWeek(weekKey: String, calendarUrl: String, events: List<CachedEventEntity>) {
        deleteWeekOfCalendar(weekKey, calendarUrl)
        if (events.isNotEmpty()) insertAll(events)
    }
}

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE weekKey = :weekKey ORDER BY done, weekday IS NULL, weekday, sortIndex, id")
    fun observeWeek(weekKey: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE weekKey = :weekKey AND done = 0 ORDER BY weekday IS NULL, weekday, sortIndex, id")
    suspend fun openTasksOfWeek(weekKey: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: Long): TaskEntity?

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE tasks SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("UPDATE tasks SET donePomodoros = donePomodoros + 1 WHERE id = :id")
    suspend fun incrementDonePomodoros(id: Long)

    @Query("SELECT COALESCE(MAX(sortIndex), 0) FROM tasks WHERE weekKey = :weekKey")
    suspend fun maxSortIndex(weekKey: String): Int
}

@Dao
interface FocusSessionDao {

    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    @Query("SELECT * FROM focus_sessions WHERE startedAt >= :from ORDER BY startedAt DESC")
    fun observeSince(from: Long): Flow<List<FocusSessionEntity>>

    @Query(
        "SELECT COUNT(*) FROM focus_sessions " +
            "WHERE phase = 'FOCUS' AND completed = 1 AND startedAt >= :from AND startedAt < :to"
    )
    fun observeCompletedFocusCount(from: Long, to: Long): Flow<Int>

    @Query(
        "SELECT COALESCE(SUM(plannedMinutes), 0) FROM focus_sessions " +
            "WHERE phase = 'FOCUS' AND completed = 1 AND startedAt >= :from AND startedAt < :to"
    )
    fun observeFocusMinutes(from: Long, to: Long): Flow<Int>
}
