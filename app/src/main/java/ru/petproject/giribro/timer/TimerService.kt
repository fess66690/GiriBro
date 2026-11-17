package ru.petproject.giribro.timer

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.petproject.giribro.MainActivity
import ru.petproject.giribro.R
import ru.petproject.giribro.data.SetCaptureMode
import ru.petproject.giribro.vision.RepExercise
import java.util.Locale
import kotlin.math.ceil

class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null
    private var phaseEndElapsed = 0L
    private var pausedRemainingMs = 0L
    private var tone: ToneGenerator? = null

    override fun onCreate() {
        super.onCreate()
        TimerNotifications.ensureChannel(this)
        tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 85) }.getOrNull()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val plan = readPlan(intent)
                if (plan == null || plan.steps.isEmpty()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val first = plan.steps.first()
                try {
                    startForeground(
                        TimerNotifications.NOTIFICATION_ID,
                        buildNotification(
                            TimerState(
                                phase = first.phase,
                                remainingSec = first.seconds,
                                stepIndex = 0,
                                running = true,
                                plan = plan
                            )
                        )
                    )
                    begin(plan)
                } catch (e: Exception) {
                    Log.e(TAG, "Не удалось запустить таймер", e)
                    stopTimer()
                }
            }

            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_COMPLETE -> completeManual()
            ACTION_STOP -> stopTimer()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun begin(plan: TimerPlan) {
        startStep(plan, 0, announce = false)
    }

    private fun startStep(plan: TimerPlan, index: Int, announce: Boolean = true) {
        if (index >= plan.steps.size) {
            finish()
            return
        }
        val step = plan.steps[index]
        if (step.manual) {
            // Подход без времени: стоим на нём, пока пользователь не нажмёт «Выполнено».
            tickJob?.cancel()
            tickJob = null
            phaseEndElapsed = 0L
            _state.value = TimerState(
                phase = step.phase,
                remainingSec = 0,
                stepIndex = index,
                running = true,
                plan = plan
            )
            updateNotification()
            if (announce) {
                beep()
                vibrate(180)
            }
            return
        }
        if (step.seconds <= 0) {
            // Нулевые отрезки пропускаем, чтобы не застрять на них.
            _state.value = _state.value.copy(
                plan = plan,
                stepIndex = index,
                phase = step.phase,
                remainingSec = 0
            )
            startStep(plan, index + 1, announce)
            return
        }

        _state.value = TimerState(
            phase = step.phase,
            remainingSec = step.seconds,
            stepIndex = index,
            running = true,
            plan = plan
        )
        phaseEndElapsed = SystemClock.elapsedRealtime() + step.seconds * 1000L
        startTicking()
        updateNotification()
        if (announce) {
            beep()
            vibrate(180)
        }
    }

    private fun advance() {
        val state = _state.value
        val plan = state.plan ?: run {
            finish()
            return
        }
        startStep(plan, state.stepIndex + 1)
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                delay(100)
                val state = _state.value
                if (!state.running) break
                val leftMs = phaseEndElapsed - SystemClock.elapsedRealtime()
                if (leftMs <= 0L) {
                    advance()
                } else {
                    val seconds = ceil(leftMs / 1000.0).toInt()
                    if (seconds != state.remainingSec) {
                        _state.value = state.copy(remainingSec = seconds)
                        updateNotification()
                    }
                }
            }
        }
    }

    private fun pause() {
        val state = _state.value
        if (!state.running || state.isManual) return
        pausedRemainingMs = (phaseEndElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        tickJob?.cancel()
        tickJob = null
        _state.value = state.copy(running = false)
        updateNotification()
    }

    private fun resume() {
        val state = _state.value
        if (state.running || !state.isActive || state.isManual) return
        phaseEndElapsed = SystemClock.elapsedRealtime() + pausedRemainingMs
        _state.value = state.copy(running = true)
        startTicking()
        updateNotification()
    }

    private fun completeManual() {
        val state = _state.value
        if (!state.isManual) return
        vibrate(120)
        advance()
    }

    private fun finish() {
        tickJob?.cancel()
        tickJob = null
        _state.value = _state.value.copy(phase = TimerPhase.DONE, remainingSec = 0, running = false)
        beep(times = 3)
        vibrate(600)
        getSystemService(NotificationManager::class.java)?.cancel(TimerNotifications.NOTIFICATION_ID)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopTimer() {
        tickJob?.cancel()
        tickJob = null
        _state.value = TimerState()
        getSystemService(NotificationManager::class.java)?.cancel(TimerNotifications.NOTIFICATION_ID)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.notify(TimerNotifications.NOTIFICATION_ID, buildNotification(_state.value))
        }.onFailure {
            Log.w(TAG, "Не удалось обновить уведомление таймера", it)
        }
    }

    private fun buildNotification(state: TimerState): Notification {
        val manual = state.isManual
        val title = when {
            manual -> state.step?.label ?: "Задание"
            state.phase == TimerPhase.IDLE -> "Интервальный таймер"
            state.phase == TimerPhase.PREP -> "Подготовка"
            state.phase == TimerPhase.WORK -> state.step?.label ?: "Работа"
            state.phase == TimerPhase.REST -> state.step?.label ?: "Отдых"
            else -> "Тренировка завершена"
        }
        val text = when {
            manual -> buildString {
                append(state.step?.task?.takeIf { it.isNotBlank() } ?: "Выполни подход")
                if (state.counterLabel.isNotEmpty()) {
                    append(" · ")
                    append(state.counterLabel)
                }
                append(" · нажми «Выполнено»")
            }

            state.phase == TimerPhase.IDLE -> "Работа, отдых и раунды"
            state.phase == TimerPhase.PREP ||
                state.phase == TimerPhase.WORK ||
                state.phase == TimerPhase.REST -> buildString {
                append(state.remainingSec.clock())
                if (state.counterLabel.isNotEmpty()) {
                    append(" · ")
                    append(state.counterLabel)
                }
            }

            else -> "Хорошая работа"
        }

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, TimerNotifications.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_kettlebell)
            .setOngoing(state.running)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (state.isActive) {
            if (manual) {
                builder.addAction(0, "Выполнено", actionIntent(ACTION_COMPLETE))
            } else {
                builder.addAction(
                    0,
                    if (state.running) "Пауза" else "Продолжить",
                    actionIntent(if (state.running) ACTION_PAUSE else ACTION_RESUME)
                )
            }
            builder.addAction(0, "Стоп", actionIntent(ACTION_STOP))
        }
        return builder.build()
    }

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, TimerService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun beep(times: Int = 1) {
        scope.launch {
            repeat(times) {
                runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 160) }
                delay(240)
            }
        }
    }

    private fun vibrate(millis: Long) {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        runCatching {
            vibrator.vibrate(
                VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }
    }

    override fun onDestroy() {
        tickJob?.cancel()
        scope.cancel()
        runCatching { tone?.release() }
        tone = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GiriBroTimer"
        private const val ACTION_START = "ru.petproject.giribro.timer.START"
        private const val ACTION_PAUSE = "ru.petproject.giribro.timer.PAUSE"
        private const val ACTION_RESUME = "ru.petproject.giribro.timer.RESUME"
        private const val ACTION_COMPLETE = "ru.petproject.giribro.timer.COMPLETE"
        private const val ACTION_STOP = "ru.petproject.giribro.timer.STOP"

        private const val EXTRA_PLAN_TITLE = "plan_title"
        private const val EXTRA_PLAN_SOURCE = "plan_source"
        private const val EXTRA_PLAN_LABELS = "plan_labels"
        private const val EXTRA_PLAN_KINDS = "plan_kinds"
        private const val EXTRA_PLAN_SECONDS = "plan_seconds"
        private const val EXTRA_PLAN_ROUNDS = "plan_rounds"
        private const val EXTRA_PLAN_TOTALS = "plan_totals"
        private const val EXTRA_PLAN_MANUAL = "plan_manual"
        private const val EXTRA_PLAN_TASKS = "plan_tasks"
        private const val EXTRA_PLAN_COUNT = "plan_count"
        private const val EXTRA_PLAN_EXERCISES = "plan_exercises"
        private const val EXTRA_PLAN_CAPTURE_MODES = "plan_capture_modes"

        private val _state = MutableStateFlow(TimerState())
        val state: StateFlow<TimerState> = _state.asStateFlow()

        fun start(context: Context, config: TimerConfig): Boolean =
            startPlan(context, config.toPlan())

        fun startPlan(context: Context, plan: TimerPlan): Boolean {
            if (plan.steps.isEmpty()) return false
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PLAN_TITLE, plan.title)
                putExtra(EXTRA_PLAN_SOURCE, plan.source.name)
                putStringArrayListExtra(EXTRA_PLAN_LABELS, ArrayList(plan.steps.map { it.label }))
                putStringArrayListExtra(EXTRA_PLAN_KINDS, ArrayList(plan.steps.map { it.kind.name }))
                putExtra(EXTRA_PLAN_SECONDS, plan.steps.map { it.seconds }.toIntArray())
                putExtra(EXTRA_PLAN_ROUNDS, plan.steps.map { it.round }.toIntArray())
                putExtra(EXTRA_PLAN_TOTALS, plan.steps.map { it.rounds }.toIntArray())
                putExtra(EXTRA_PLAN_MANUAL, plan.steps.map { it.manual }.toBooleanArray())
                putStringArrayListExtra(EXTRA_PLAN_TASKS, ArrayList(plan.steps.map { it.task }))
                putExtra(EXTRA_PLAN_COUNT, plan.steps.map { it.countReps }.toBooleanArray())
                putStringArrayListExtra(
                    EXTRA_PLAN_EXERCISES,
                    ArrayList(plan.steps.map { it.exercise?.name.orEmpty() })
                )
                putStringArrayListExtra(
                    EXTRA_PLAN_CAPTURE_MODES,
                    ArrayList(plan.steps.map { it.captureMode.name })
                )
            }
            return runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure {
                Log.e(TAG, "Не удалось запустить foreground-сервис таймера", it)
            }.isSuccess
        }

        fun pause(context: Context) = command(context, ACTION_PAUSE)

        fun resume(context: Context) = command(context, ACTION_RESUME)

        fun complete(context: Context) = command(context, ACTION_COMPLETE)

        fun stop(context: Context) = command(context, ACTION_STOP)

        private fun command(context: Context, action: String) {
            runCatching {
                context.startService(Intent(context, TimerService::class.java).setAction(action))
            }
        }

        private fun readPlan(intent: Intent): TimerPlan? {
            val labels = intent.getStringArrayListExtra(EXTRA_PLAN_LABELS) ?: return null
            val seconds = intent.getIntArrayExtra(EXTRA_PLAN_SECONDS) ?: return null
            if (labels.size != seconds.size || labels.isEmpty()) return null

            val kinds = intent.getStringArrayListExtra(EXTRA_PLAN_KINDS)
            val rounds = intent.getIntArrayExtra(EXTRA_PLAN_ROUNDS)
            val totals = intent.getIntArrayExtra(EXTRA_PLAN_TOTALS)
            val manuals = intent.getBooleanArrayExtra(EXTRA_PLAN_MANUAL)
            val tasks = intent.getStringArrayListExtra(EXTRA_PLAN_TASKS)
            val counts = intent.getBooleanArrayExtra(EXTRA_PLAN_COUNT)
            val exercises = intent.getStringArrayListExtra(EXTRA_PLAN_EXERCISES)
            val captureModes = intent.getStringArrayListExtra(EXTRA_PLAN_CAPTURE_MODES)
            val source = runCatching {
                TimerPlanSource.valueOf(intent.getStringExtra(EXTRA_PLAN_SOURCE) ?: "")
            }.getOrNull() ?: TimerPlanSource.INTERVAL

            val steps = labels.indices.map { index ->
                val kind = runCatching {
                    TimerStepKind.valueOf(kinds?.getOrNull(index) ?: "")
                }.getOrNull() ?: TimerStepKind.WORK
                TimerStep(
                    kind = kind,
                    seconds = seconds[index],
                    label = labels[index],
                    round = rounds?.getOrNull(index) ?: 0,
                    rounds = totals?.getOrNull(index) ?: 0,
                    manual = manuals?.getOrNull(index) ?: false,
                    task = tasks?.getOrNull(index).orEmpty(),
                    countReps = counts?.getOrNull(index) ?: false,
                    exercise = exercises?.getOrNull(index)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { name ->
                            runCatching { RepExercise.valueOf(name) }.getOrNull()
                        },
                    captureMode = SetCaptureMode.fromName(captureModes?.getOrNull(index))
                )
            }
            if (steps.isEmpty()) return null

            return TimerPlan(
                title = intent.getStringExtra(EXTRA_PLAN_TITLE) ?: "Интервальный таймер",
                source = source,
                steps = steps
            )
        }
    }
}

private fun Int.clock(): String =
    String.format(Locale.US, "%d:%02d", this / 60, this % 60)
