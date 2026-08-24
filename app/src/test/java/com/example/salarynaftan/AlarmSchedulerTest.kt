package com.example.salarynaftan

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Тесты планировщиков будильников. После разбиения AlarmScheduler (п.3.3)
 * сменная логика живет в ShiftAlarmScheduler, а обычная — в RegularAlarmScheduler.
 * Проверяем их напрямую.
 */
class AlarmSchedulerTest {

    private val context = mockk<Context>()
    private val alarmManager = mockk<AlarmManager>(relaxed = true)
    private val pendingIntent = mockk<PendingIntent>()
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val settings = mockk<SettingsManager>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(PendingIntent::class)
        mockkConstructor(Intent::class)
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns pendingIntent
        val relaxedIntent = mockk<Intent>(relaxed = true)
        every { anyConstructed<Intent>().setAction(any()) } returns relaxedIntent
        // putExtra имеет много перегрузок; типы указываем явно, чтобы MockK
        // мог выбрать нужную (в планировщиках используются String и Int).
        every { anyConstructed<Intent>().putExtra(any<String>(), any<String>()) } returns relaxedIntent
        every { anyConstructed<Intent>().putExtra(any<String>(), any<Int>()) } returns relaxedIntent
        every { context.getSystemService(Context.ALARM_SERVICE) } returns alarmManager
        every { context.getSharedPreferences(PreferenceKeys.ALARM_PREFS, Context.MODE_PRIVATE) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putString(any(), any()) } returns editor
    }

    private fun shiftSched(): ShiftAlarmScheduler {
        val s = spyk(ShiftAlarmScheduler(context, settings))
        // Методы, которые обращаются к Android-заглушкам (PendingIntent/Intent),
        // недоступны в unit-тесте — заменяем их заглушками.
        every { s.scheduleSingleShiftAlarm(any(), any(), any(), any()) } just Runs
        return s
    }

    private fun regularSched(): RegularAlarmScheduler {
        val s = spyk(RegularAlarmScheduler(context))
        every { s.scheduleSingleRegularAlarm(any()) } just Runs
        every { s.cancelSingleRegularAlarm(any()) } just Runs
        return s
    }

    // ===== Сменные времена будильников =====

    @Test
    fun `getAlarmTimesForShift returns default when nothing saved`() {
        every { prefs.getString(any(), any()) } returns null
        val times = shiftSched().getAlarmTimesForShift(ShiftType.MORNING, 1)
        assertEquals(listOf("06:00"), times)
    }

    @Test
    fun `getAlarmTimesForShift returns saved comma-separated times`() {
        every { prefs.getString(any(), any()) } returns "06:00,07:30"
        val times = shiftSched().getAlarmTimesForShift(ShiftType.NIGHT, 1)
        assertEquals(listOf("06:00", "07:30"), times)
    }

    @Test
    fun `saveAlarmTimesForShift persists joined times under prefixed key`() {
        shiftSched().saveAlarmTimesForShift(ShiftType.DAY, listOf("14:00", "16:00"), 2)
        val expectedKey = "${PreferenceKeys.SHIFT_TIMES_PREFIX}2_DAY"
        verify { editor.putString(expectedKey, "14:00,16:00") }
        verify { editor.apply() }
    }

    @Test
    fun `scheduleAlarmsForShift sets enabled flag and returns count`() {
        every { prefs.getString(any(), any()) } returns "06:00,07:30"
        val n = shiftSched().scheduleAlarmsForShift(ShiftType.MORNING, 1)

        assertEquals(2, n)
        verify { editor.putBoolean("${PreferenceKeys.SHIFT_ALARM_ENABLED_PREFIX}1_MORNING", true) }
    }

    @Test
    fun `cancelAlarmsForShift clears enabled flag`() {
        shiftSched().cancelAlarmsForShift(ShiftType.NIGHT, 3)
        verify { editor.putBoolean("${PreferenceKeys.SHIFT_ALARM_ENABLED_PREFIX}3_NIGHT", false) }
    }

    @Test
    fun `isAlarmScheduledForShift reads enabled flag`() {
        every { prefs.getBoolean(any(), any()) } returns true
        assertTrue(shiftSched().isAlarmScheduledForShift(ShiftType.DAY, 1))
        every { prefs.getBoolean(any(), any()) } returns false
        assertFalse(shiftSched().isAlarmScheduledForShift(ShiftType.DAY, 1))
    }

    @Test
    fun `rescheduleShiftAlarmAfterRing reschedules when enabled`() {
        every { prefs.getBoolean(any(), any()) } returns true
        val s = shiftSched()
        s.rescheduleShiftAlarmAfterRing(ShiftType.DAY, 1, 0, "14:00")
        verify { s.scheduleSingleShiftAlarm(ShiftType.DAY, 1, 0, "14:00") }
    }

    @Test
    fun `rescheduleShiftAlarmAfterRing does nothing when disabled`() {
        every { prefs.getBoolean(any(), any()) } returns false
        val s = shiftSched()
        s.rescheduleShiftAlarmAfterRing(ShiftType.DAY, 1, 0, "14:00")
        verify(exactly = 0) { s.scheduleSingleShiftAlarm(ShiftType.DAY, 1, 0, "14:00") }
    }

    // ===== Обычные будильники: сериализация =====

    @Test
    fun `getRegularAlarms returns defaults when nothing saved`() {
        every { prefs.getString(any(), any()) } returns null
        val alarms = regularSched().getRegularAlarms()
        assertEquals(
            listOf(
                RegularAlarm(1L, "07:30", false, "Утренний"),
                RegularAlarm(2L, "21:00", false, "Вечерний")
            ),
            alarms
        )
    }

    @Test
    fun `getRegularAlarms parses serialized alarms`() {
        every { prefs.getString(any(), any()) } returns "10|06:00|true|Будильник"
        val alarms = regularSched().getRegularAlarms()
        assertEquals(listOf(RegularAlarm(10L, "06:00", true, "Будильник")), alarms)
    }

    @Test
    fun `regular alarm label with separators round-trips through serialization`() {
        val tricky = RegularAlarm(5L, "08:00", false, "Утро;вечер|день\\ночь")
        every { prefs.getString(any(), any()) } returns null

        val s = regularSched()
        s.saveRegularAlarms(listOf(tricky))

        val serialized = slot<String>()
        verify { editor.putString(PreferenceKeys.REGULAR_ALARMS, capture(serialized)) }

        // При чтении закодированная строка должна восстановить исходную метку.
        every { prefs.getString(any(), any()) } returns serialized.captured
        val parsed = s.getRegularAlarms()
        assertEquals(tricky.label, parsed.single().label)
    }

    @Test
    fun `saveRegularAlarms schedules enabled and cancels disabled`() {
        val alarms = listOf(
            RegularAlarm(1L, "07:30", true, "Утро"),
            RegularAlarm(2L, "21:00", false, "Вечер")
        )
        val s = regularSched()
        s.saveRegularAlarms(alarms)

        verify { editor.putString(PreferenceKeys.REGULAR_ALARMS, any()) }
        verify { s.scheduleSingleRegularAlarm(alarms[0]) }
        verify { s.cancelSingleRegularAlarm(2L) }
    }
}
