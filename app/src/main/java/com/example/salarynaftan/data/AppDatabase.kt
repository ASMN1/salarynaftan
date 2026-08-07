package com.example.salarynaftan.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MonthSalaryEntity::class, SalaryHistoryEntity::class], version = 4, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun monthSalaryDao(): MonthSalaryDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // v1 -> v2: добавляем колонки year и vacationDays, делаем составной первичный ключ.
        // Существующие данные переносим с year = текущий год устройства, чтобы не потерять невыходы/нормы.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `month_salary_new` (`year` INTEGER NOT NULL, `monthIndex` INTEGER NOT NULL, `normHours` TEXT NOT NULL, `prazdnHours` TEXT NOT NULL, `zaOtsutstvuushego` TEXT NOT NULL, `kvartalka` TEXT NOT NULL, `gazetaInput` TEXT NOT NULL, `pozhertvovanjaInput` TEXT NOT NULL, `subbotnikInput` TEXT NOT NULL, `mmDetiCountInput` TEXT NOT NULL, `childrenCountInput` TEXT NOT NULL, `stravitaInput` TEXT NOT NULL, `missedDays` TEXT NOT NULL, `vacationDays` TEXT NOT NULL, PRIMARY KEY(`year`, `monthIndex`))")
                db.execSQL("INSERT INTO `month_salary_new` (`year`, `monthIndex`, `normHours`, `prazdnHours`, `zaOtsutstvuushego`, `kvartalka`, `gazetaInput`, `pozhertvovanjaInput`, `subbotnikInput`, `mmDetiCountInput`, `childrenCountInput`, `stravitaInput`, `missedDays`, `vacationDays`) SELECT ${java.time.Year.now().value}, `monthIndex`, `normHours`, `prazdnHours`, `zaOtsutstvuushego`, `kvartalka`, `gazetaInput`, `pozhertvovanjaInput`, `subbotnikInput`, `mmDetiCountInput`, `childrenCountInput`, `stravitaInput`, `missedDays`, '' FROM `month_salary`")
                db.execSQL("DROP TABLE `month_salary`")
                db.execSQL("ALTER TABLE `month_salary_new` RENAME TO `month_salary`")
                // salary_history: добавить колонку year (существующие записи → текущий год)
                db.execSQL("CREATE TABLE IF NOT EXISTS `salary_history_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `year` INTEGER NOT NULL, `monthIndex` INTEGER NOT NULL, `monthName` TEXT NOT NULL, `totalClean` REAL NOT NULL, `cleanToPay` REAL NOT NULL, `advance` REAL NOT NULL, `timestamp` INTEGER NOT NULL)")
                db.execSQL("INSERT INTO `salary_history_new` (`id`, `year`, `monthIndex`, `monthName`, `totalClean`, `cleanToPay`, `advance`, `timestamp`) SELECT `id`, ${java.time.Year.now().value}, `monthIndex`, `monthName`, `totalClean`, `cleanToPay`, `advance`, `timestamp` FROM `salary_history`")
                db.execSQL("DROP TABLE `salary_history`")
                db.execSQL("ALTER TABLE `salary_history_new` RENAME TO `salary_history`")
            }
        }

        // v2 -> v3: убираем дубликаты в salary_history (по year+monthIndex) и
        // добавляем уникальный индекс, чтобы повторное сохранение за тот же
        // месяц обновляло запись, а не плодило дубликаты.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Удаляем дубли, оставляя самую свежую запись каждой пары (year, monthIndex).
                db.execSQL(
                    "DELETE FROM `salary_history` WHERE `id` NOT IN (" +
                            "SELECT MAX(`id`) FROM `salary_history` GROUP BY `year`, `monthIndex`)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_salary_history_year_monthIndex` ON `salary_history` (`year`, `monthIndex`)")
            }
        }

        // v3 -> v4: добавляем индекс на timestamp для ускорения ORDER BY timestamp DESC.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_salary_history_timestamp` ON `salary_history` (`timestamp`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "salarynaftan.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { INSTANCE = it }
            }
        }
    }
}