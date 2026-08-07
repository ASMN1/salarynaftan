package com.example.salarynaftan

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.graphics.toColorInt
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

class ShiftWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleMidnightUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelMidnightUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_WIDGET) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, ShiftWidgetProvider::class.java)
            )
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.example.salarynaftan.UPDATE_WIDGET"

        fun triggerUpdate(context: Context) {
            val intent = Intent(ACTION_UPDATE_WIDGET).apply {
                component = ComponentName(context, ShiftWidgetProvider::class.java)
            }
            context.sendBroadcast(intent)
        }

        private fun scheduleMidnightUpdate(context: Context) {
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

            alarmManager.setRepeating(
                AlarmManager.RTC,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
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

        /**
         * Returns the "adjusted" payday date: if the 10th or 25th falls on
         * Saturday or Sunday, it is shifted to the previous Friday.
         */
        private fun adjustedPayDate(dayOfMonth: Int, year: Int, month: Int): LocalDate? {
            val date = LocalDate.of(year, month, dayOfMonth)
            return when (date.dayOfWeek.value) {
                6 -> date.minusDays(1) // Saturday → Friday
                7 -> date.minusDays(2) // Sunday → Friday
                else -> date
            }
        }

        /**
         * Returns зарплата/аванс label for the given date if it is a payday (10th or 25th),
         * shifted to Friday when needed. Returns null otherwise.
         */
        private fun payLabelFor(date: LocalDate): String? {
            val todayPay = adjustedPayDate(10, date.year, date.month.value)
            val advancePay = adjustedPayDate(25, date.year, date.month.value)
            return when {
                todayPay != null && date == todayPay -> "зарплата"
                advancePay != null && date == advancePay -> "аванс"
                else -> null
            }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val prefs = context.getSharedPreferences(PreferenceKeys.SETTINGS_PREFS, Context.MODE_PRIVATE)
            val brigade = prefs.getInt(PreferenceKeys.BRIGADE_KEY, 1)

            val today = LocalDate.now()
            val days = listOf(
                today,
                today.plusDays(1),
                today.plusDays(2)
            )

            val dayOfWeekNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

            val colorSettings = ColorSettingsManager(context)

            val views = RemoteViews(context.packageName, R.layout.widget_shift)

            // Populate 3 day cells
            val containerIds = listOf(
                Triple(R.id.day1_container, R.id.day1_day_date, R.id.day1_shift_type),
                Triple(R.id.day2_container, R.id.day2_day_date, R.id.day2_shift_type),
                Triple(R.id.day3_container, R.id.day3_day_date, R.id.day3_shift_type)
            )
            val timeIds = listOf(R.id.day1_time, R.id.day2_time, R.id.day3_time)
            val payIds = listOf(R.id.day1_pay, R.id.day2_pay, R.id.day3_pay)

            for ((index, date) in days.withIndex()) {
                val shift = ShiftSchedule.shiftFor(date, brigade)
                val (containerId, dayDateId, shiftTypeId) = containerIds[index]
                val timeId = timeIds[index]
                val payId = payIds[index]

                // Day abbreviation + date
                val dayIdx = date.dayOfWeek.value - 1 // Monday = 0
                val dayName = if (dayIdx in dayOfWeekNames.indices) dayOfWeekNames[dayIdx] else "??"
                val monthName = date.month.getDisplayName(TextStyle.SHORT, Locale("ru"))
                val dateStr = "$dayName ${date.dayOfMonth} $monthName"

                // Shift type: short name (У/Д/Н/В) for compactness
                val shiftTypeStr = shift.shortName

                // Time range
                val timeStr = if (shift.startTime != null && shift.endTime != null) {
                    "${shift.startTime}–${shift.endTime}"
                } else {
                    "вых."
                }

                // зарплата/аванс indicator
                val payLabel = payLabelFor(date)
                val payStr = payLabel ?: ""

                // Colors
                val shiftColor = when (shift) {
                    ShiftType.MORNING -> colorSettings.getMorningColor()
                    ShiftType.DAY -> colorSettings.getDayColor()
                    ShiftType.NIGHT -> colorSettings.getNightColor()
                    ShiftType.OFF -> colorSettings.getOffColor()
                }

                val bgColor = with(shiftColor) {
                    "#${(alpha * 0.6f * 255).toInt().toString(16).padStart(2, '0')
                        .uppercase()}${
                        (red * 255).toInt().toString(16).padStart(2, '0').uppercase()
                    }${
                        (green * 255).toInt().toString(16).padStart(2, '0').uppercase()
                    }${
                        (blue * 255).toInt().toString(16).padStart(2, '0').uppercase()
                    }"
                }

                val textColor = when (shift) {
                    ShiftType.NIGHT -> "#FFFFFF"
                    ShiftType.OFF -> "#AAAAAA"
                    else -> "#222222"
                }

                val payColor = if (payLabel == "зарплата") "#4CAF50" else "#FFD700"

                // Set texts
                views.setTextViewText(dayDateId, dateStr)
                views.setTextViewText(shiftTypeId, shiftTypeStr)
                views.setTextViewText(timeId, timeStr)
                views.setTextViewText(payId, payStr)

                // Set container background
                views.setInt(containerId, "setBackgroundColor", bgColor.toColorInt())

                // Set text colors
                views.setTextColor(dayDateId, textColor.toColorInt())
                views.setTextColor(shiftTypeId, textColor.toColorInt())
                views.setTextColor(timeId, textColor.toColorInt())
                if (payLabel != null) {
                    views.setTextColor(payId, payColor.toColorInt())
                }
            }

            // Tap pending intent → open MainActivity with selected_tab = 0 (График)
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