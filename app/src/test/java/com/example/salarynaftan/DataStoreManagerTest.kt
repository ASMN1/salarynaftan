package com.example.salarynaftan

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.salarynaftan.data.DataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты DataStoreManager: значения по умолчанию, кэш (запись→чтение),
 * граничные значения (clamping) и персистентность на диск.
 *
 * Особенность реализации: кэш инициализируется из DataStore ровно один раз
 * при первом чтении (ensure), а запись уходит в фоновый IO-scope. Поэтому
 * паттерн тестов соответствует реальному использованию: сначала читаем
 * (кэш загружается), затем сохраняем — повторное чтение идёт из кэша.
 * Для проверки факта записи на диск читаем «свежим» менеджером после паузы.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class DataStoreManagerTest {

    private lateinit var context: Context
    private lateinit var manager: DataStoreManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Robolectric переиспользует одну файловую систему в рамках JVM —
        // очищаем файл DataStore, чтобы каждый тест начинал с «чистых» дефолтов.
        DataStoreManagerTest.clearDataStore(context)
        manager = DataStoreManager(context)
    }

    @After
    fun tearDown() {
        // Новый менеджер в каждом тесте изолирован; контекст Robolectric
        // пересоздаётся между тестами.
    }

    companion object {
        /** Удаляет файлы DataStore (settings.preferences_pb) для изолированности тестов. */
        fun clearDataStore(context: Context) {
            val dir = java.io.File(context.filesDir, "datastore")
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    // ===== Кэш: первое чтение загружает кэш, затем запись→чтение из кэша =====

    @Test
    fun `save then get returns cached value for volume`() {
        manager.getVolume() // загружаем кэш
        manager.saveVolume(0.5f)
        assertEquals(0.5f, manager.getVolume(), 0.0001f)
    }

    @Test
    fun `save then get returns cached value for ringtone`() {
        manager.getRingtoneUri() // загружаем кэш
        manager.saveRingtoneUri("content://ringtone/1")
        assertEquals("content://ringtone/1", manager.getRingtoneUri())
    }

    @Test
    fun `save ringtone null reads back as null`() {
        manager.getRingtoneUri() // загружаем кэш
        manager.saveRingtoneUri("content://ringtone/1")
        manager.saveRingtoneUri(null)
        assertNull(manager.getRingtoneUri())
    }

    @Test
    fun `salary stores full double precision without float drift`() {
        manager.getSalary() // загружаем кэш
        manager.saveSalary(1607.93)
        assertEquals(1607.93, manager.getSalary(), 0.0000001)
        // Классический float-дрейф: 1607.93f→Double даёт 1607.9299316…
        assertTrue(manager.getSalary().toString().startsWith("1607.93"))
    }

    @Test
    fun `brigade round-trips`() {
        manager.getBrigade() // загружаем кэш
        manager.setBrigade(4)
        assertEquals(4, manager.getBrigade())
    }

    @Test
    fun `primary color round-trips`() {
        manager.getPrimaryColor() // загружаем кэш
        manager.savePrimaryColor(0xFF00E676.toInt())
        assertEquals(0xFF00E676.toInt(), manager.getPrimaryColor())
    }

    @Test
    fun `shift colors round-trip`() {
        manager.getMorningColor(); manager.getDayColor()
        manager.getNightColor(); manager.getOffColor() // загружаем кэш
        manager.saveMorningColor(0xFF112233.toInt())
        manager.saveDayColor(0xFF445566.toInt())
        manager.saveNightColor(0xFF778899.toInt())
        manager.saveOffColor(0xFFAABBCC.toInt())
        assertEquals(0xFF112233.toInt(), manager.getMorningColor())
        assertEquals(0xFF445566.toInt(), manager.getDayColor())
        assertEquals(0xFF778899.toInt(), manager.getNightColor())
        assertEquals(0xFFAABBCC.toInt(), manager.getOffColor())
    }

    @Test
    fun `theme and dynamic colors round-trip`() {
        manager.isDarkTheme(); manager.getUseDynamicColors() // загружаем кэш
        manager.saveTheme(false)
        assertFalse(manager.isDarkTheme())
        manager.saveUseDynamicColors(true)
        assertTrue(manager.getUseDynamicColors())
    }

    // ===== Граничные значения (clamping) =====

    @Test
    fun `ui scale is clamped to 0_7 to 1_5`() {
        manager.getUiScale() // загружаем кэш
        manager.saveUiScale(0.2f)
        assertEquals(0.7f, manager.getUiScale(), 0.0001f)

        manager.saveUiScale(9f)
        assertEquals(1.5f, manager.getUiScale(), 0.0001f)

        manager.saveUiScale(1.1f)
        assertEquals(1.1f, manager.getUiScale(), 0.0001f)
    }

    @Test
    fun `volume ramp sec is clamped to 2 to 30`() {
        manager.getVolumeRampSec() // загружаем кэш
        manager.saveVolumeRampSec(0)
        assertEquals(2, manager.getVolumeRampSec())

        manager.saveVolumeRampSec(99)
        assertEquals(30, manager.getVolumeRampSec())

        manager.saveVolumeRampSec(15)
        assertEquals(15, manager.getVolumeRampSec())
    }

    @Test
    fun `brigade is clamped to 1 to 5`() {
        manager.getBrigade() // загружаем кэш
        manager.setBrigade(0)
        assertEquals(1, manager.getBrigade())

        manager.setBrigade(12)
        assertEquals(5, manager.getBrigade())
    }

    @Test
    fun `selected month is clamped to 0 to 11`() {
        manager.getSelectedMonthIndex() // загружаем кэш
        manager.saveSelectedMonthIndex(-3)
        assertEquals(0, manager.getSelectedMonthIndex())

        manager.saveSelectedMonthIndex(44)
        assertEquals(11, manager.getSelectedMonthIndex())
    }

    @Test
    fun `pps percent is clamped to 0 to 100`() {
        manager.getPpsPercent() // загружаем кэш
        manager.savePpsPercent(-10f)
        assertEquals(0f, manager.getPpsPercent(), 0.0001f)

        manager.savePpsPercent(250f)
        assertEquals(100f, manager.getPpsPercent(), 0.0001f)
    }

    @Test
    fun `resetAllColors restores defaults`() {
        manager.getPrimaryColor(); manager.getMorningColor()
        manager.getDayColor(); manager.getNightColor(); manager.getOffColor() // загружаем кэш

        manager.savePrimaryColor(0xFF123456.toInt())
        manager.saveMorningColor(0xFF111111.toInt())
        manager.saveNightColor(0xFF222222.toInt())
        manager.resetAllColors()

        assertEquals(ShiftType.MORNING.defaultColorArgb, manager.getMorningColor())
        assertEquals(ShiftType.DAY.defaultColorArgb, manager.getDayColor())
        assertEquals(ShiftType.NIGHT.defaultColorArgb, manager.getNightColor())
        assertEquals(ShiftType.OFF.defaultColorArgb, manager.getOffColor())
        assertEquals(0xFF00E676.toInt(), manager.getPrimaryColor())
    }

    // ===== Персистентность на диск =====

    @Test
    fun `saved value persists to disk and fresh manager reads it`() {
        manager.saveSalary(12345.67)
        manager.saveVolumeRampSec(20)
        waitForDiskWrite()

        // Новый менеджер того же контекста читает уже записанное значение.
        val fresh = DataStoreManager(context)
        assertEquals(12345.67, fresh.getSalary(), 0.0001)
        assertEquals(20, fresh.getVolumeRampSec())
    }

    /** Даёт фоновому writeScope время дойти до диска. */
    private fun waitForDiskWrite() {
        runBlocking {
            withContext(Dispatchers.IO) { delay(300) }
        }
    }
}
