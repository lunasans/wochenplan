package de.wochenplan.app.pomodoro

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.wochenplan.app.WochenplanApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

/**
 * Haelt den Pomodoro-Timer als Vordergrunddienst am Leben.
 *
 * Ohne Vordergrunddienst beendet Android die App im Hintergrund, und der Timer
 * laeuft nicht zu Ende. Die Benachrichtigung zaehlt selbst herunter, deshalb
 * muss sie nicht im Sekundentakt neu geschrieben werden.
 */
class PomodoroService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    private val engine: PomodoroEngine
        get() = (application as WochenplanApp).container.pomodoroEngine

    override fun onCreate() {
        super.onCreate()
        PomodoroNotifications.createChannels(this)
        observeState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> engine.start()
            ACTION_PAUSE -> engine.pause()
            ACTION_TOGGLE -> engine.toggle()
            ACTION_SKIP -> engine.skip()
            ACTION_RESET -> engine.reset()
            ACTION_STOP -> {
                engine.pause()
                stopTimerNotification()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        // Sofort in den Vordergrund gehen: Android erwartet das innerhalb weniger Sekunden.
        startForegroundWithState()
        return START_STICKY
    }

    private fun observeState() {
        observeJob?.cancel()
        observeJob = serviceScope.launch {
            launch {
                // Der Timer laeuft im 250-ms-Takt; die Benachrichtigung zaehlt selbst
                // herunter und muss nur bei echten Zustandswechseln neu gebaut werden.
                engine.state
                    .map { state ->
                        NotificationKey(
                            phase = state.phase,
                            running = state.running,
                            taskTitle = state.taskTitle,
                            endElapsedRealtime = state.endElapsedRealtime,
                            remainingWhilePaused = if (state.running) 0L else state.remainingMillis,
                        )
                    }
                    .distinctUntilChanged()
                    .collect { key ->
                        if (key.running) startForegroundWithState() else updateNotification()
                    }
            }
            launch {
                engine.events.collectLatest { event ->
                    if (event is PomodoroEvent.PhaseFinished) {
                        PomodoroNotifications.showPhaseFinished(
                            this@PomodoroService,
                            event.finished,
                            event.next,
                        )
                        if (event.completed) PomodoroNotifications.vibrate(this@PomodoroService)
                    }
                }
            }
        }
    }

    private fun startForegroundWithState() {
        val notification = PomodoroNotifications.buildTimerNotification(this, engine.state.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                PomodoroNotifications.NOTIFICATION_TIMER,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(PomodoroNotifications.NOTIFICATION_TIMER, notification)
        }
    }

    private fun updateNotification() {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        runCatching {
            NotificationManagerCompat.from(this).notify(
                PomodoroNotifications.NOTIFICATION_TIMER,
                PomodoroNotifications.buildTimerNotification(this, engine.state.value),
            )
        }
    }

    private fun stopTimerNotification() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        observeJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Nur diese Angaben veraendern die Benachrichtigung. */
    private data class NotificationKey(
        val phase: PomodoroPhase,
        val running: Boolean,
        val taskTitle: String?,
        val endElapsedRealtime: Long?,
        val remainingWhilePaused: Long,
    )

    companion object {
        const val ACTION_START = "de.wochenplan.app.action.START"
        const val ACTION_PAUSE = "de.wochenplan.app.action.PAUSE"
        const val ACTION_TOGGLE = "de.wochenplan.app.action.TOGGLE"
        const val ACTION_SKIP = "de.wochenplan.app.action.SKIP"
        const val ACTION_RESET = "de.wochenplan.app.action.RESET"
        const val ACTION_STOP = "de.wochenplan.app.action.STOP"

        fun send(context: Context, action: String) {
            val intent = Intent(context, PomodoroService::class.java).setAction(action)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
