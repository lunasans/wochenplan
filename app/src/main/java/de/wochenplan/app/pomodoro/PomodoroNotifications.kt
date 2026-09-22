package de.wochenplan.app.pomodoro

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import de.wochenplan.app.MainActivity
import de.wochenplan.app.R

/** Benachrichtigungen und Vibration fuer den Pomodoro-Timer. */
object PomodoroNotifications {

    const val CHANNEL_TIMER = "pomodoro_timer"
    const val CHANNEL_ALERTS = "pomodoro_alerts"
    const val NOTIFICATION_TIMER = 1001
    const val NOTIFICATION_ALERT = 1002

    fun createChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        val timerChannel = NotificationChannel(
            CHANNEL_TIMER,
            "Laufender Timer",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Zeigt den laufenden Fokus- oder Pausenabschnitt an."
            setShowBadge(false)
        }

        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "Abschnitt beendet",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Meldet das Ende eines Fokusabschnitts oder einer Pause."
            enableVibration(true)
        }

        manager.createNotificationChannel(timerChannel)
        manager.createNotificationChannel(alertChannel)
    }

    /** Die dauerhafte Benachrichtigung des laufenden Timers. */
    fun buildTimerNotification(context: Context, state: PomodoroState): android.app.Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(phaseTitle(state.phase))
            .setContentText(state.taskTitle ?: phaseSubtitle(state))
            .setContentIntent(openAppIntent(context))
            .setOngoing(state.running)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (state.running) {
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + state.remainingMillis)
            builder.addAction(0, "Pause", command(context, PomodoroService.ACTION_PAUSE))
        } else {
            builder.setUsesChronometer(false)
                .setContentText("${state.remainingText} – angehalten")
            builder.addAction(0, "Weiter", command(context, PomodoroService.ACTION_START))
        }

        builder.addAction(0, "Ueberspringen", command(context, PomodoroService.ACTION_SKIP))
        builder.addAction(0, "Beenden", command(context, PomodoroService.ACTION_STOP))
        return builder.build()
    }

    /** Meldung beim Wechsel des Abschnitts. */
    fun showPhaseFinished(context: Context, finished: PomodoroPhase, next: PomodoroPhase) {
        val title = when (finished) {
            PomodoroPhase.FOCUS -> "Fokusabschnitt geschafft"
            PomodoroPhase.SHORT_BREAK -> "Kurze Pause vorbei"
            PomodoroPhase.LONG_BREAK -> "Lange Pause vorbei"
        }
        val text = when (next) {
            PomodoroPhase.FOCUS -> "Weiter geht es mit dem naechsten Fokusabschnitt."
            PomodoroPhase.SHORT_BREAK -> "Zeit fuer eine kurze Pause."
            PomodoroPhase.LONG_BREAK -> "Zeit fuer eine lange Pause."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .build()

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ALERT, notification) }
        }
    }

    fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            context.getSystemService<Vibrator>()
        } ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 250, 400), -1))
    }

    fun phaseTitle(phase: PomodoroPhase): String = when (phase) {
        PomodoroPhase.FOCUS -> "Fokus"
        PomodoroPhase.SHORT_BREAK -> "Kurze Pause"
        PomodoroPhase.LONG_BREAK -> "Lange Pause"
    }

    private fun phaseSubtitle(state: PomodoroState): String =
        "Durchgang ${state.completedFocusInCycle + 1} von ${state.cyclesBeforeLongBreak}"

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun command(context: Context, action: String): PendingIntent {
        val intent = Intent(context, PomodoroService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
