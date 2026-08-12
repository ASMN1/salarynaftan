package com.example.salarynaftan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Фоновый сервис проигрывания звука будильника.
 *
 * Звук вынесен из AlarmRingingActivity в отдельный Service, чтобы звонок
 * не обрывался, если приложение/активность будет закрыта системой.
 * Запускается с типом FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK, чтобы система
 * не убивала сервис посреди звонка.
 *
 * Управление: AlarmSoundService.start(context, uri, volume, rampSec) —
 * запустить звонок; AlarmSoundService.stop(context) — остановить.
 */
class AlarmSoundService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var volumeRampJob: Job? = null

    // Защита от повторного старта звонка: onNewIntent в AlarmRingingActivity
    // или повторный onStartCommand не должны перезапускать звук/вибрацию,
    // давая наложение/зацикливание. Сбрасывается в stopRinging().
    @Volatile private var isRinging = false

    override fun onDestroy() {
        super.onDestroy()
        // Восстанавливаем громкость на максимум при уничтожении сервиса,
        // чтобы при убийстве посреди нарастания громкость не осталась
        // на промежуточном уровне (п.4.8 аудита).
        try {
            mediaPlayer?.setVolume(1f, 1f)
        } catch (_: Exception) { }
        stopRinging()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRinging()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                if (isRinging) {
                    // Уже звеним — игнорируем повторный запуск (дубль).
                    return START_STICKY
                }
                val uri = intent?.getStringExtra(EXTRA_RINGTONE_URI)?.let {
                    try { android.net.Uri.parse(it) } catch (_: Exception) { null }
                } ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                val volume = intent?.getFloatExtra(EXTRA_VOLUME, 1f) ?: 1f
                val rampSec = intent?.getIntExtra(EXTRA_RAMP_SEC, 0) ?: 0
                startForegroundAndRing(uri, volume, rampSec)
                return START_STICKY
            }
        }
    }

    private fun startForegroundAndRing(uri: android.net.Uri, targetVolume: Float, rampSec: Int) {
        // Уведомление + запуск в foreground, чтобы сервис не убивался системой.
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_RINGING, "Звук будильника",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setSound(null, null) }
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle("⏰ Будильник")
            .setContentText("Смена началась")
            .setOngoing(true)
            .setLocalOnly(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        startRinging(uri, targetVolume, rampSec)
        // Флаг ставим ПОСЛЕ startRinging (он внутри сбрасывает isRinging
        // через stopRinging), чтобы guard «уже звеним» работал корректно.
        isRinging = true
    }

    private fun startRinging(uri: android.net.Uri, targetVolume: Float, rampSec: Int) {
        stopRinging()

        // Вибрация — независимо от звука.
        vibrator = vibrationService()
        val pattern = longArrayOf(0, 800, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }

        try {
            // Подготовка выполняется асинхронно (prepareAsync), чтобы не
            // блокировать Main-поток сервиса (п.1.2). Старт и нарастание
            // громкости происходят по готовности в setOnPreparedListener.
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(this@AlarmSoundService, uri)
                isLooping = true
                setVolume(0f, 0f)
                val localTarget = targetVolume
                val localRamp = rampSec
                setOnPreparedListener { prepared ->
                    // Если звонок уже остановлен (stopRinging) — не начинаем игру.
                    if (!isRinging) { try { prepared.release() } catch (_: Exception) {}; return@setOnPreparedListener }
                    try { prepared.start() } catch (_: Exception) {}
                    if (localRamp <= 0 || localTarget <= 0f) {
                        try { prepared.setVolume(localTarget, localTarget) } catch (_: Exception) {}
                    } else {
                        volumeRampJob = serviceScope.launch {
                            var cur = 0f
                            val steps = maxOf(1, localRamp * 2) // 2 шага в секунду
                            val step = localTarget / steps
                            repeat(steps) {
                                cur += step
                                try { prepared.setVolume(cur, cur) } catch (e: Exception) {
                                    Timber.w(e, "Сбой при нарастании громкости")
                                    return@launch
                                }
                                delay(500) // 500ms = 2 step/sec
                            }
                        }
                    }
                }
                setOnErrorListener { _, _, _ ->
                    Timber.e("MediaPlayer error при воспроизведении будильника")
                    stopRinging()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            Timber.e(e, "Не удалось запустить звук будильника в сервисе")
        }
    }

    private fun vibrationService(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private fun stopRinging() {
        volumeRampJob?.cancel()
        try { mediaPlayer?.let { if (it.isPlaying) it.stop(); it.release() } } catch (_: Exception) { }
        mediaPlayer = null
        vibrator?.cancel()
        isRinging = false
    }

    companion object {
        const val CHANNEL_RINGING = "alarm_ringing_channel"
        private const val NOTIFICATION_ID = 1002
        const val ACTION_STOP = "com.example.salarynaftan.STOP_RINGING"
        private const val EXTRA_RINGTONE_URI = "ringtone_uri"
        private const val EXTRA_VOLUME = "volume"
        private const val EXTRA_RAMP_SEC = "ramp_sec"

        fun start(context: Context, uri: android.net.Uri?, volume: Float, rampSec: Int) {
            val intent = Intent(context, AlarmSoundService::class.java).apply {
                action = ACTION_START
                uri?.let { putExtra(EXTRA_RINGTONE_URI, it.toString()) }
                putExtra(EXTRA_VOLUME, volume)
                putExtra(EXTRA_RAMP_SEC, rampSec)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Android 12+: запуск FGS из фона запрещён (без "иммунитета" будильника).
                // Не даём упасть — просто логируем, звук не удастся воспроизвести в фоне.
                Timber.e(e, "Не удалось запустить фоновый сервис звука будильника")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AlarmSoundService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }

        private const val ACTION_START = "com.example.salarynaftan.START_RINGING"
    }
}
