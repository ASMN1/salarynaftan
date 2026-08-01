package com.example.salarynaftan

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.graphics.toColorInt
import java.time.LocalDate
import java.time.format.TextStyle
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

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val prefs = context.getSharedPreferences(PreferenceKeys.SETTINGS_PREFS, Context.MODE_PRIVATE)
            val brigade = prefs.getInt(PreferenceKeys.BRIGADE_KEY, 1)

            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)

            val todayShift = ShiftSchedule.shiftFor(today, brigade)
            val tomorrowShift = ShiftSchedule.shiftFor(tomorrow, brigade)

            val dayName = today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru"))
            val monthName = today.month.getDisplayName(TextStyle.SHORT, Locale("ru"))
            val dateStr = "${today.dayOfMonth} $monthName, $dayName"

            val timeStr = if (todayShift.startTime != null && todayShift.endTime != null) {
                "${todayShift.startTime} — ${todayShift.endTime}"
            } else {
                "Выходной день"
            }

            val nextStr = if (tomorrowShift == ShiftType.OFF) {
                "Завтра: выходной"
            } else {
                "Завтра: ${tomorrowShift.displayName.lowercase()} ${tomorrowShift.startTime}–${tomorrowShift.endTime}"
            }

            val colorSettings = ColorSettingsManager(context)
            val shiftColor = when (todayShift) {
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

            val textColor = when (todayShift) {
                ShiftType.NIGHT -> "#FFFFFF"
                ShiftType.OFF -> "#AAAAAA"
                else -> "#222222"
            }

            val views = RemoteViews(context.packageName, R.layout.widget_shift).apply {
                setTextViewText(R.id.tvWidgetTitle, "Бригада $brigade · $dateStr")
                setTextViewText(R.id.tvWidgetShiftType, todayShift.displayName)
                setTextViewText(R.id.tvWidgetShiftTime, timeStr)
                setTextViewText(R.id.tvWidgetNext, nextStr)

                setInt(R.id.widget_root, "setBackgroundColor", bgColor.toColorInt())
                setTextColor(R.id.tvWidgetTitle, "#88FFFFFF".toColorInt())
                setTextColor(R.id.tvWidgetShiftType, textColor.toColorInt())
                setTextColor(R.id.tvWidgetShiftTime, textColor.toColorInt())
                setTextColor(R.id.tvWidgetNext, "#88FFFFFF".toColorInt())
            }

            manager.updateAppWidget(widgetId, views)
        }
    }
}
