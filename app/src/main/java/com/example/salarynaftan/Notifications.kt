package com.example.salarynaftan

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat

/**
 * Единый помощник для показа уведомлений приложения.
 * Централизует фирменный стиль (иконка, брендовый цвет, заголовки, каналы),
 * чтобы «Срабатывание будильника», «Авто-тишина» и любые другие системные
 * сообщения выглядели одинаково (единый стиль уведомлений).
 */
object Notifications {

    /** Фирменный акцентный цвет для полосы/подсветки уведомления. */
    private const val BRAND_COLOR = 0xFF00E676.toInt()

    /** Составление PendingIntent (Activity) с общими гарантированными флагами. */
    private fun activityPendingIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Базовый билдер с фирменной иконкой, брендовым цветом и общими полями. */
    private fun baseBuilder(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, App.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setColor(BRAND_COLOR)
            .setColorized(true)
            .setLocalOnly(true)

    /**
     * Полноэкранное уведомление срабатывания будильника, запускающее AlarmRingingActivity.
     * @return notification id, чтобы вызывающий мог показать его через notify.
     */
    fun alarm(context: Context, title: String, notificationId: Int, ringIntent: Intent): NotificationCompat.Builder {
        val pendingIntent = activityPendingIntent(context, notificationId, ringIntent)
        return baseBuilder(context)
            .setContentTitle("⏰ $title")
            .setContentText("Время просыпаться!")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Время просыпаться! Нажмите, чтобы открыть будильник"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
    }

    /**
     * Простое информационное уведомление (например, о состоянии авто-тишины).
     * Стиль совпадает с будильником, чтобы все уведомления выглядели одинаково.
     */
    fun info(
        context: Context,
        title: String,
        text: String,
        notificationId: Int,
        icon: String = "🔔"
    ): NotificationCompat.Builder =
        baseBuilder(context)
            .setContentTitle("$icon $title")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
}
