package de.wochenplan.app.data.repo

import de.wochenplan.app.core.IsoWeek
import de.wochenplan.app.data.db.FocusSessionDao
import de.wochenplan.app.data.db.FocusSessionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

/** Auswertung der abgeschlossenen Pomodoro-Abschnitte. */
class FocusRepository(
    private val focusSessionDao: FocusSessionDao,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun record(session: FocusSessionEntity): Long = focusSessionDao.insert(session)

    fun observeCompletedFocusToday(today: LocalDate = LocalDate.now(zone)): Flow<Int> {
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return focusSessionDao.observeCompletedFocusCount(from, to)
    }

    fun observeFocusMinutesToday(today: LocalDate = LocalDate.now(zone)): Flow<Int> {
        val from = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return focusSessionDao.observeFocusMinutes(from, to)
    }

    fun observeFocusMinutesOfWeek(week: IsoWeek): Flow<Int> {
        val from = week.monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = week.monday.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        return focusSessionDao.observeFocusMinutes(from, to)
    }

    fun observeRecentSessions(days: Long = 7): Flow<List<FocusSessionEntity>> {
        val from = LocalDate.now(zone).minusDays(days).atStartOfDay(zone).toInstant().toEpochMilli()
        return focusSessionDao.observeSince(from)
    }
}
