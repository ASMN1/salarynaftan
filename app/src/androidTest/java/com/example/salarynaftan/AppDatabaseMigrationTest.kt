package com.example.salarynaftan

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.salarynaftan.data.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private lateinit var helper: MigrationTestHelper

    @Before
    fun setUp() {
        helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java
        )
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migrate1To2_keepsLegacyYearUnknown() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                "INSERT INTO month_salary " +
                    "(monthIndex, normHours, prazdnHours, zaOtsutstvuushego, kvartalka, " +
                    "gazetaInput, pozhertvovanjaInput, subbotnikInput, mmDetiCountInput, " +
                    "childrenCountInput, stravitaInput, missedDays) " +
                    "VALUES (3, '168', '8', '1', '2', '3', '4', '5', '6', '7', '8', '9')"
            )
            execSQL(
                "INSERT INTO salary_history " +
                    "(monthIndex, monthName, totalClean, cleanToPay, advance, timestamp) " +
                    "VALUES (3, 'Апрель', 100.0, 90.0, 30.0, 1234)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            AppDatabase.MIGRATION_1_2
        )
        migrated.query("SELECT year, monthIndex, normHours, vacationDays FROM month_salary").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(AppDatabase.UNKNOWN_YEAR, cursor.getInt(0))
            assertEquals(3, cursor.getInt(1))
            assertEquals("168", cursor.getString(2))
            assertEquals("", cursor.getString(3))
        }
        migrated.query("SELECT year, monthIndex, totalClean FROM salary_history").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(AppDatabase.UNKNOWN_YEAR, cursor.getInt(0))
            assertEquals(3, cursor.getInt(1))
            assertEquals(100.0, cursor.getDouble(2), 0.0)
        }
        migrated.close()
    }

    @Test
    fun migrate4To5_removesHolidayHoursAndPreservesData() {
        helper.createDatabase(TEST_DATABASE, 4).apply {
            execSQL(
                "INSERT INTO month_salary " +
                    "(year, monthIndex, normHours, prazdnHours, zaOtsutstvuushego, " +
                    "kvartalka, gazetaInput, pozhertvovanjaInput, subbotnikInput, " +
                    "mmDetiCountInput, childrenCountInput, stravitaInput, missedDays, vacationDays) " +
                    "VALUES (2026, 0, '168', '8', '0', '0', '0', '0', '0', '0', '0', '0', '', '')"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            5,
            true,
            AppDatabase.MIGRATION_4_5
        )
        migrated.query("PRAGMA table_info(month_salary)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            var hasHolidayHours = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameColumn) == "prazdnHours") {
                    hasHolidayHours = true
                    break
                }
            }
            assertFalse(hasHolidayHours)
        }
        migrated.query(
            "SELECT year, monthIndex, normHours FROM month_salary WHERE year = 2026 AND monthIndex = 0"
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(2026, cursor.getInt(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals("168", cursor.getString(2))
        }
        migrated.close()
    }

    companion object {
        private const val TEST_DATABASE = "migration-test.db"
    }
}