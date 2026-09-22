package de.wochenplan.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.wochenplan.app.WochenplanApp
import de.wochenplan.app.core.IsoWeek
import java.util.concurrent.TimeUnit

/**
 * Holt die laufende und die kommende Woche im Hintergrund.
 *
 * Damit sind die Termine schon da, wenn die App geoeffnet wird – auch ohne Netz.
 */
class WeekSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? WochenplanApp)?.container ?: return Result.success()
        if (!container.settingsStore.current().isAccountConfigured) return Result.success()

        val currentWeek = IsoWeek.current()
        val results = listOf(currentWeek, currentWeek.plusWeeks(1))
            .map { container.calendarRepository.syncWeek(it) }

        return if (results.any { it.isFailure }) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "wochenplan-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeekSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
