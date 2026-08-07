package com.example.salarynaftan

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Полномесячный виджет графика смен.
 *
 * Показывает весь текущий месяц (6x7 сетка), цвета смен, дни зарплаты/аванса
 * (со сдвигом на пятницу) и подсветку сегодняшнего дня. Обновляется:
 *  - при добавлении/перезагрузке (onUpdate),
 *  - раз в день в 00:01 (setExactAndAllowWhileIdle с self-reschedule),
 *  - при смене бригады (SettingsManager.setBrigade → triggerUpdate).
 */
class ShiftWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
        // Переустанавливаем ежедневный будильник обновления: однократные
        // setExact не переживают перезагрузку, а onUpdate вызывается после
        // boot для всех существующих виджетов.
        scheduleMidnightUpdate(context)
        // Резервный ежедневный обновлятель через WorkManager (п.4.2): если
        // будильник заблокирован Doze/производителем, WorkManager всё равно
        // сработает, гарантируя актуальный месяц в виджете.
        schedulePeriodicUpdate(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleMidnightUpdate(context)
        schedulePeriodicUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelMidnightUpdate(context)
        cancelPeriodicUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_UPDATE_WIDGET -> refresh(context)
            ACTION_MIDNIGHT_ALARM -> {
                // Ежедневное обновление в 00:01 сработало — перерисовываем месяц
                // (мог смениться и сам месяц, не только день) и ставим следующий.
                refresh(context)
                scheduleMidnightUpdate(context)
            }
        }
    }

    private fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, ShiftWidgetProvider::class.java)
        )
        for (id in ids) {
            updateWidget(context, manager, id)
        }
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.example.salarynaftan.UPDATE_WIDGET"
        const val ACTION_MIDNIGHT_ALARM = "com.example.salarynaftan.MIDNIGHT_ALARM"

        fun triggerUpdate(context: Context) {
            val intent = Intent(ACTION_UPDATE_WIDGET).apply {
                component = ComponentName(context, ShiftWidgetProvider::class.java)
            }
            context.sendBroadcast(intent)
        }

        /** Синхронное обновление всех виджетов (вызывается из WorkManager-работника). */
        fun refreshSync(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ShiftWidgetProvider::class.java)
            )
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }

        // ---- WorkManager: резервное ежедневное обновление ----

        private const val PERIODIC_WORK_NAME = "shift_widget_daily_update"

        private fun schedulePeriodicUpdate(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(24, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        private fun cancelPeriodicUpdate(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        /** Принудительное немедленное обновление через WorkManager (после смены бригады). */
        fun scheduleImmediateUpdate(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                PERIODIC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            schedulePeriodicUpdate(context)
        }

        private fun scheduleMidnightUpdate(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, ShiftWidgetProvider::class.java).apply {
                action = ACTION_MIDNIGHT_ALARM
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Schedule at 00:01 every day
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // If we are already past 00:01 today, schedule for tomorrow
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setRepeating(
                        AlarmManager.RTC,
                        calendar.timeInMillis,
                        AlarmManager.INTERVAL_DAY,
                        pendingIntent
                    )
                }
            } catch (e: Exception) {
                // Если точный будильник запрещён — откат на надёжный setRepeating.
                alarmManager.setRepeating(
                    AlarmManager.RTC,
                    calendar.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
                )
            }
        }

        private fun cancelMidnightUpdate(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ShiftWidgetProvider::class.java).apply {
                action = ACTION_UPDATE_WIDGET
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        /** Дата выплаты (10-е/25-е), сдвинутая на пятницу при выпадении на выходные. */
        private fun adjustedPayDate(dayOfMonth: Int, month: YearMonth): LocalDate {
            var d = month.atDay(dayOfMonth)
            while (d.dayOfWeek.value > 5) d = d.minusDays(1) // Сб=6, Вс=7 → пятница
            return d
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val prefs = context.getSharedPreferences(PreferenceKeys.SETTINGS_PREFS, Context.MODE_PRIVATE)
            val brigade = prefs.getInt(PreferenceKeys.BRIGADE_KEY, 1)

            val today = LocalDate.now()
            val colorSettings = ColorSettingsManager(context)

            val views = RemoteViews(context.packageName, R.layout.widget_shift)

            // Шапка: месяц/год + бригада
            val monthName = today.month.getDisplayName(TextStyle.FULL, Locale("ru"))
            views.setTextViewText(
                R.id.widget_month_title,
                monthName.substring(0, 1).uppercase() + monthName.substring(1) + " " + today.year
            )
            views.setTextViewText(R.id.widget_brigade, "Бр $brigade")

            // Дни зарплаты/аванса с учётом сдвига на пятницу
            val month = YearMonth.from(today)
            val salaryDate = adjustedPayDate(10, month)
            val advanceDate = adjustedPayDate(25, month)

            val firstDay = month.atDay(1)
            val emptyBefore = firstDay.dayOfWeek.value - 1 // Пн=0
            val daysInMonth = month.lengthOfMonth()

            for (row in 0 until 6) {
                for (col in 0 until 7) {
                    val cellId = context.resources.getIdentifier("cell_${row}_${col}", "id", context.packageName)
                    val numId = context.resources.getIdentifier("cell_${row}_${col}_num", "id", context.packageName)
                    val shiftId = context.resources.getIdentifier("cell_${row}_${col}_shift", "id", context.packageName)
                    if (cellId == 0 || numId == 0 || shiftId == 0) continue

                    val dayNumber = row * 7 + col - emptyBefore + 1
                    if (dayNumber !in 1..daysInMonth) {
                        // Пустая ячейка — скрываем
                        views.setInt(cellId, "setVisibility", android.view.View.GONE)
                        continue
                    }

                    views.setInt(cellId, "setVisibility", android.view.View.VISIBLE)

                    val date = month.atDay(dayNumber)
                    val shift = ShiftSchedule.shiftFor(date, brigade)
                    val isToday = date == today

                    val shiftColor = when (shift) {
                        ShiftType.MORNING -> colorSettings.getMorningColor()
                        ShiftType.DAY -> colorSettings.getDayColor()
                        ShiftType.NIGHT -> colorSettings.getNightColor()
                        ShiftType.OFF -> colorSettings.getOffColor()
                    }

                    // Фон ячейки: если сегодня — инвертируем (светлая), иначе цвет смены с прозрачностью.
                    val bgColor = if (isToday) {
                        "#FF00E676"
                    } else {
                        with(shiftColor) {
                            "#${(alpha * 0.55f * 255).toInt().toString(16).padStart(2, '0').uppercase()}${
                                (red * 255).toInt().toString(16).padStart(2, '0').uppercase()
                            }${
                                (green * 255).toInt().toString(16).padStart(2, '0').uppercase()
                            }${
                                (blue * 255).toInt().toString(16).padStart(2, '0').uppercase()
                            }"
                        }
                    }

                    // Текст: номер дня + подпись ЗП/АВ или тип смены
                    views.setTextViewText(numId, dayNumber.toString())
                    val textColor = when {
                        isToday -> "#000000"
                        shift == ShiftType.NIGHT -> "#FFFFFF"
                        shift == ShiftType.OFF -> "#CCCCCC"
                        else -> "#222222"
                    }
                    views.setTextColor(numId, Color.parseColor(textColor))

                    val shiftStr = when {
                        date == salaryDate -> "ЗП"
                        date == advanceDate -> "АВ"
                        else -> shift.shortName
                    }
                    val shiftColorHex = when {
                        date == salaryDate -> "#00C853"
                        date == advanceDate -> "#00BFA5"
                        isToday -> "#000000"
                        shift == ShiftType.NIGHT -> "#FFFFFF"
                        else -> "#222222"
                    }
                    views.setTextViewText(shiftId, shiftStr)
                    views.setTextColor(shiftId, Color.parseColor(shiftColorHex))

                    views.setInt(cellId, "setBackgroundColor", Color.parseColor(bgColor))
                }
            }

            // Тап по виджету → открыть MainActivity (вкладка График)
            val tapIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("selected_tab", 0)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val tapPendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, tapPendingIntent)

            manager.updateAppWidget(widgetId, views)
        }
    }
}
