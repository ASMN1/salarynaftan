package com.example.salarynaftan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-side smoke tests. Run this class on each configured API 31..35 emulator.
 * Calendar writes are deliberately skipped when the test device granted calendar
 * permission, so the test suite never modifies a user's real calendar.
 */
@RunWith(AndroidJUnit4::class)
class AndroidComponentSmokeTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun widgetProjection_hasStableSixBySevenShape() {
        val cells = WidgetScheduleModel.forMonth(LocalDate.of(2026, 1, 15), 1, ScheduleType.GRAPH_1)
        assertEquals(42, cells.size)
        assertTrue(cells.count { it != null } in 28..42)
    }

    @Test
    fun widgetProviderActions_areExplicitAndAddressable() {
        val update = Intent(ShiftWidgetProvider.ACTION_UPDATE_WIDGET)
            .setClass(context, ShiftWidgetProvider::class.java)
        val midnight = Intent(ShiftWidgetProvider.ACTION_MIDNIGHT_ALARM)
            .setClass(context, ShiftWidgetProvider::class.java)
        assertEquals(ShiftWidgetProvider::class.java.name, update.component?.className)
        assertEquals(ShiftWidgetProvider::class.java.name, midnight.component?.className)
    }

    @Test
    fun calendarSync_withoutWritePermission_returnsFailure() {
        assumeTrue(
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) !=
                PackageManager.PERMISSION_GRANTED
        )
        val result = kotlinx.coroutines.runBlocking {
            CalendarSyncCoordinator.syncMonth(
                context, java.time.YearMonth.of(2026, 1), 1, ScheduleType.GRAPH_1
            )
        }
        assertEquals(CalendarSyncResult.Failed, result)
    }

    @Test
    fun renderer_canBuildRemoteViews() {
        val views = RemoteViews(context.packageName, R.layout.widget_shift)
        val ids = Array(6) { row -> IntArray(7) { col ->
            context.resources.getIdentifier("cell_${row}_${col}", "id", context.packageName)
        } }
        val nums = Array(6) { row -> IntArray(7) { col ->
            context.resources.getIdentifier("cell_${row}_${col}_num", "id", context.packageName)
        } }
        val shifts = Array(6) { row -> IntArray(7) { col ->
            context.resources.getIdentifier("cell_${row}_${col}_shift", "id", context.packageName)
        } }
        WidgetRenderer().renderCells(
            views,
            WidgetScheduleModel.forMonth(LocalDate.of(2026, 1, 15), 1, ScheduleType.GRAPH_1),
            ids,
            nums,
            shifts,
            WidgetColors(0xFF00FF00.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF0000.toInt())
        )
    }
}