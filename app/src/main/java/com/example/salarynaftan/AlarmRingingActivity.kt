package com.example.salarynaftan

import android.Manifest
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class AlarmRingingActivity : ComponentActivity(), KoinComponent {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Флаги для отображения поверх заблокированного экрана
        // На Android 12+ setShowWhenLocked + window flags ВМЕСТЕ,
        // т.к. только API без window flags может не сработать на Samsung/Xiaomi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            try {
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                keyguardManager.requestDismissKeyguard(this, null)
            } catch (_: Exception) { }
        }

        // Window flags — не deprecated, а дополняют setShowWhenLocked на OEM-прошивках
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        handleAlarmIntent(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* блокируем */ }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAlarmIntent(intent)
    }

    private fun handleAlarmIntent(intent: Intent) {
        val alarmTitle = intent.getStringExtra("alarm_title") ?: "Смена"
        val settings: SettingsManager = get()

        // Перезапускаем звук/вибрацию при каждом новом intent
        releaseMedia()
        startAudioAndVibration(settings)

        setContent {
            MaterialTheme {
                AlarmScreenUI(
                    title = alarmTitle,
                    onDismiss = { stopAndFinish() },
                    onSnooze = {
                        snoozeAlarm(alarmTitle)
                        stopAndFinish()
                    }
                )
            }
        }
    }

    private fun snoozeAlarm(title: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = System.currentTimeMillis() + 5 * 60 * 1000

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("alarm_title", title)
        }
        val requestCode = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                    pendingIntent
                )
            }
        } catch (_: Exception) { }
    }

    private fun startAudioAndVibration(settings: SettingsManager) {
        val uri = settings.getRingtoneUri()
            ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
        val targetVolume = settings.getVolume()

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 800, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmRingingActivity, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                setVolume(0f, 0f)
                prepare()
                start()
            }
            lifecycleScope.launch {
                var cur = 0f
                val step = targetVolume / 20
                repeat(20) {
                    cur += step
                    mediaPlayer?.setVolume(cur, cur)
                    delay(500)
                }
            }
        } catch (_: Exception) { }
    }

    private var isFinishing = false
    private fun stopAndFinish() {
        if (isFinishing) return
        isFinishing = true
        releaseMedia()
        finish()
    }

    private fun releaseMedia() {
        try { mediaPlayer?.let { if (it.isPlaying) it.stop(); it.release() } } catch (_: Exception) { }
        mediaPlayer = null
        vibrator?.cancel()
    }

    override fun onDestroy() {
        releaseMedia()
        super.onDestroy()
    }
}

@Composable
fun AlarmScreenUI(title: String, onDismiss: () -> Unit, onSnooze: () -> Unit) {
    // Текущее время
    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalDateTime.now()
            delay(1000)
        }
    }

    // Анимации пульсации
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val arrowBounce by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "arrow"
    )

    // Свайп: смещение в пикселях
    var offsetX by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val threshold = screenWidthPx * 0.30f

    // Параметры для gesture-блока (не @Composable)
    val dismissAction = rememberUpdatedState(onDismiss)
    val snoozeAction = rememberUpdatedState(onSnooze)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0D0D),
                        Color(0xFF1A1A2E),
                        Color(0xFF0D0D0D)
                    )
                )
            )
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > threshold) {
                            snoozeAction.value()
                        } else if (offsetX < -threshold) {
                            dismissAction.value()
                        }
                        offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX = (offsetX + dragAmount).coerceIn(-screenWidthPx, screenWidthPx)
                    }
                )
            }
    ) {
        // Фоновая подсветка при свайпе
        val progress = (kotlin.math.abs(offsetX) / threshold).coerceIn(0f, 1f)
        if (progress > 0.02f) {
            val bgColor = if (offsetX > 0) {
                Color(0xFF00E676).copy(alpha = progress * 0.3f)
            } else {
                Color(0xFFFF5252).copy(alpha = progress * 0.3f)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            // Текущее время
            Text(
                text = currentTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                fontSize = 72.sp,
                fontWeight = FontWeight.Thin,
                color = Color.White.copy(alpha = 0.9f),
                letterSpacing = 4.sp
            )
            Text(
                text = currentTime.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru"))),
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Пульсирующий круг с иконкой
            Box(contentAlignment = Alignment.Center) {
                // Свечение
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .scale(pulseScale)
                        .alpha(glowAlpha)
                        .clip(CircleShape)
                        .background(Color(0xFF00E676).copy(alpha = 0.15f))
                )
                // Основной круг
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF00E676).copy(alpha = 0.3f),
                                    Color(0xFF00E676).copy(alpha = 0.05f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⏰", fontSize = 56.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "ПОРА ВСТАВАТЬ",
                color = Color(0xFF00E676),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            // Индикаторы свайпа
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Отложить → свайп вправо
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.alpha(if (offsetX > 0) (offsetX / threshold).coerceIn(0.3f, 1f) else 0.3f)
                ) {
                    Text(
                        text = "›",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676),
                        modifier = Modifier.offset { IntOffset(arrowBounce.roundToInt(), 0) }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text("Отложить", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                        Text("+5 мин", fontSize = 11.sp, color = Color(0xFF00E676).copy(alpha = 0.7f))
                    }
                }

                // Выключить ← свайп влево
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.alpha(if (offsetX < 0) (-offsetX / threshold).coerceIn(0.3f, 1f) else 0.3f)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Выключить", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                        Text("свайп влево", fontSize = 11.sp, color = Color(0xFFFF5252).copy(alpha = 0.7f))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "‹",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5252),
                        modifier = Modifier.offset { IntOffset(-arrowBounce.roundToInt(), 0) }
                    )
                }
            }

            // Прогресс-бар свайпа
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (offsetX > 0) Color(0xFF00E676) else Color(0xFFFF5252)
                        )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
