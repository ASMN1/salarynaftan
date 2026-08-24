package com.example.salarynaftan

import android.Manifest
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.delay
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class AlarmRingingActivity : ComponentActivity(), KoinComponent {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Флаги для отображения поверх заблокированного экрана
        // На Android 12+ setShowWhenLocked + window flags ВМЕСТЕ,
        // т.к. только API без window flags может не сработать на Samsung/Xiaomi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
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

        // Убираем уведомление из шторки, если оно было показано (заблокированный
        // экран через fullScreenIntent). Иначе остаётся дублирующее уведомление.
        val notificationId = intent.getIntExtra("notification_id", -1)
        if (notificationId >= 0) {
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.cancel(notificationId)
            } catch (_: Exception) { }
        }

        // Сохраняем сменные параметры для snooze, чтобы после отложенного
        // срабатывания будильник корректно перепланировался (НЕДОЧЁТ-5).
        pendingShiftType = intent.getStringExtra("shift_type_name")
        pendingBrigade = intent.getIntExtra("brigade", 1)
        pendingAlarmIndex = intent.getIntExtra("alarm_index", -1)
        pendingAlarmTime = intent.getStringExtra("alarm_time")

        // Запускаем звук/вибрацию в фоновом сервисе — он не оборвётся,
        // если активность будет закрыта системой.
        AlarmSoundService.start(
            context = this,
            uri = settings.getRingtoneUri(),
            volume = settings.getVolume(),
            rampSec = settings.getVolumeRampSec()
        )

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
            // Передаём сменные параметры, чтобы AlarmReceiver перепланировал
            // смену на следующий день после snooze (НЕДОЧЁТ-5).
            pendingShiftType?.let { putExtra("shift_type_name", it) }
            putExtra("brigade", pendingBrigade)
            putExtra("alarm_index", pendingAlarmIndex)
            pendingAlarmTime?.let { putExtra("alarm_time", it) }
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
            } else {
                // Точные будильники запрещены — отложить не получится.
                // Показываем уведомление, чтобы пользователь понимал,
                // что snooze не сработает (п.1.3 аудита).
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val notif = Notifications.info(
                    context = this,
                    title = "Нельзя отложить будильник",
                    text = "Разрешите точные будильники в настройках, чтобы функция «Отложить» работала.",
                    notificationId = 1003
                ).build()
                nm.notify(1003, notif)
            }
        } catch (_: Exception) { }
    }

    private val isFinishing = java.util.concurrent.atomic.AtomicBoolean(false)
    private fun stopAndFinish() {
        if (!isFinishing.compareAndSet(false, true)) return
        // Останавливаем звонок в фоновом сервисе.
        AlarmSoundService.stop(this)
        finish()
    }

    // Сменные параметры исходного сигнала (для корректного snooze).
    private var pendingShiftType: String? = null
    private var pendingBrigade: Int = 1
    private var pendingAlarmIndex: Int = -1
    private var pendingAlarmTime: String? = null

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        // При сворачивании не останавливаем звук — его держит фоновый сервис,
        // чтобы звонок не оборвался, если активность закроют.
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

    // Нижние системные insets (панель навигации), чтобы контент не накладывался.
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Анимации пульсации кольца
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ringScale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "ringAlpha"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    // Свайп: смещение в пикселях
    var offsetX by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val threshold = screenWidthPx * 0.30f
    val progress = (kotlin.math.abs(offsetX) / threshold).coerceIn(0f, 1f)
    val isSnoozeSide = offsetX > 0

    val dismissAction = rememberUpdatedState(onDismiss)
    val snoozeAction = rememberUpdatedState(onSnooze)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0A14), Color(0xFF1B1B33), Color(0xFF0D0D1A))
                )
            )
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > threshold) snoozeAction.value()
                        else if (offsetX < -threshold) dismissAction.value()
                        offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX = (offsetX + dragAmount).coerceIn(-screenWidthPx, screenWidthPx)
                    }
                )
            }
    ) {
        // Амбиентное свечение снизу, меняет цвет по направлению свайпа
        val ambientColor = when {
            progress > 0.02f && isSnoozeSide -> Color(0xFF00E676)
            progress > 0.02f -> Color(0xFFFF5252)
            else -> Color(0xFF00E676)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(
                    colors = listOf(ambientColor.copy(alpha = 0.25f * progress + 0.03f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(0.5f, 1f),
                    radius = 900f
                ))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(bottom = bottomInset + 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Крупные часы
            Text(
                text = currentTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                fontSize = 84.sp,
                fontWeight = FontWeight.Thin,
                color = Color.White.copy(alpha = 0.95f),
                letterSpacing = 6.sp
            )
            Text(
                text = currentTime.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru"))),
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.55f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            // ===== БОЛЬШОЙ КРУГ В ЦЕНТРЕ — его перетаскиваешь свайпом =====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentAlignment = Alignment.Center
            ) {
                // Подписи действий по бокам (появляются при свайпе)
                Text(
                    text = "😴 Отложить",
                    color = Color(0xFF00E676),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .alpha(if (isSnoozeSide) progress else 0.3f)
                )
                Text(
                    text = "Выключить ⏹",
                    color = Color(0xFFFF5252),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .alpha(if (!isSnoozeSide) progress else 0.3f)
                )

                // Круг, который двигается вместе со свайпом
                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                        .size(200.dp)
                        .graphicsLayer {
                            rotationZ = offsetX / 20f
                            scaleX = 1f + progress * 0.05f
                            scaleY = 1f + progress * 0.05f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Расширяющееся кольцо
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .scale(ringScale)
                            .alpha(ringAlpha)
                            .border(2.dp, Color(0xFF00E676).copy(alpha = 0.6f), CircleShape)
                    )
                    // Свечение
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .scale(1.1f)
                            .alpha(glowAlpha)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676).copy(alpha = 0.18f))
                    )
                    // Основной круг
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF00E676).copy(alpha = 0.45f),
                                        Color(0xFF009688).copy(alpha = 0.15f)
                                    )
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⏰", fontSize = 72.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Подсказка направления
            Text(
                text = "Свайп вправо — отложить · влево — выключить",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Прогресс-бар свайпа
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (offsetX > 0) Brush.horizontalGradient(listOf(Color(0xFF00E676), Color(0xFF00BFA5)))
                            else Brush.horizontalGradient(listOf(Color(0xFFFF5252), Color(0xFFD50000)))
                        )
                )
            }
        }
    }
}
