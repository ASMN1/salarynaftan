package com.example.salarynaftan

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.example.salarynaftan.data.DataStoreManager
import com.example.salarynaftan.di.AppDependencies
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** AppWidget lifecycle/orchestration boundary; scheduling and rendering are delegated. */
class ShiftWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
        AppDependencies.widgetScheduler.scheduleAll()
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        AppDependencies.widgetScheduler.scheduleAll()
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        AppDependencies.widgetScheduler.cancelAll()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_UPDATE_WIDGET -> refresh(context)
            ACTION_MIDNIGHT_ALARM -> {
                refresh(context)
                AppDependencies.widgetScheduler.scheduleAll()
            }
        }
    }

    private fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        manager.getAppWidgetIds(ComponentName(context, ShiftWidgetProvider::class.java))
            .forEach { updateWidget(context, manager, it) }
    }

    companion object {
        const val ACTION_UPDATE_WIDGET = "com.example.salarynaftan.UPDATE_WIDGET"
        const val ACTION_MIDNIGHT_ALARM = "com.example.salarynaftan.MIDNIGHT_ALARM"
        private const val COLOR_WEEKEND = 0xFFFF5252.toInt()
        private val cellIds = Array(6) { IntArray(7) }
        private val cellNumIds = Array(6) { IntArray(7) }
        private val cellShiftIds = Array(6) { IntArray(7) }

        @SuppressLint("DiscouragedApi")
        private fun ensureCellIds(context: Context) {
            if (cellIds[0][0] != 0) return
            for (row in 0 until 6) for (col in 0 until 7) {
                cellIds[row][col] = context.resources.getIdentifier("cell_${row}_${col}", "id", context.packageName)
                cellNumIds[row][col] = context.resources.getIdentifier("cell_${row}_${col}_num", "id", context.packageName)
                cellShiftIds[row][col] = context.resources.getIdentifier("cell_${row}_${col}_shift", "id", context.packageName)
            }
        }

        fun triggerUpdate(context: Context) {
            context.sendBroadcast(Intent(ACTION_UPDATE_WIDGET).apply {
                component = ComponentName(context, ShiftWidgetProvider::class.java)
            })
        }

        fun refreshSync(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, ShiftWidgetProvider::class.java))
                .forEach { updateWidget(context, manager, it) }
        }

        fun scheduleImmediateUpdate(context: Context) {
            AppDependencies.widgetScheduler.scheduleImmediate()
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val settings = AppDependencies.settingsManager
            val today = LocalDate.now()
            val brigade = settings.getBrigade()
            val scheduleType = settings.getScheduleType()
            val colorSettings = AppDependencies.colorSettingsManager
            val primary = DataStoreManager.getInstance(context).getPrimaryColor()
            val views = RemoteViews(context.packageName, R.layout.widget_shift)
            val monthName = today.month.getDisplayName(TextStyle.FULL, Locale("ru"))
            views.setTextViewText(R.id.widget_month_title, "${monthName.replaceFirstChar { it.uppercase() }} ${today.year}")
            views.setTextViewText(R.id.widget_brigade, "Бр $brigade")
            views.setTextColor(R.id.widget_wd_sat, COLOR_WEEKEND)
            views.setTextColor(R.id.widget_wd_sun, COLOR_WEEKEND)
            ensureCellIds(context)
            AppDependencies.widgetRenderer.renderCells(
                views = views,
                models = WidgetScheduleModel.forMonth(today, brigade, scheduleType),
                cellIds = cellIds,
                cellNumIds = cellNumIds,
                cellShiftIds = cellShiftIds,
                colors = WidgetColors(
                    morning = colorSettings.getMorningColor().toArgb(),
                    day = colorSettings.getDayColor().toArgb(),
                    night = colorSettings.getNightColor().toArgb(),
                    off = colorSettings.getOffColor().toArgb(),
                    primary = primary
                )
            )
            val tapIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("selected_tab", 0)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, widgetId, tapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            manager.updateAppWidget(widgetId, views)
        }
    }
}