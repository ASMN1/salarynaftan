# Аудит кодовой базы Salarynaftan

## 1. КРИТИЧЕСКИЕ ОШИБКИ И БАГИ

### 1.1. `MonthlyNorms.kt` — небезопасное `!!` на карте норм
- **Локация**: `MonthlyNorms.kt`, строки 89, 92
- **Суть**: `NORMS_BY_YEAR[2026]!![monthIndex]` — если ключ `2026` когда-либо удалят из карты, `!!` бросит `NullPointerException`. Сейчас безопасно (2026 есть), но это хрупкий контракт.
- **К чему приведёт**: краш при удалении/переименовании ключа 2026 в будущем рефакторинге.
- **Решение**:
```kotlin
// Было:
?: NORMS_BY_YEAR[2026]!![monthIndex]
// Стало:
?: NORMS_BY_YEAR[2026]?.getOrNull(monthIndex) ?: 160.0
```

### 1.2. `WidgetScheduler` — конфликт `requestCode` с `BootReceiver`
- **Локация**: `WidgetScheduler.kt:91` (`MIDNIGHT_REQUEST_CODE = 1001`) и `BootReceiver.kt:39` (`notifyId = 1001`)
- **Суть**: `WidgetScheduler` использует `requestCode=1001` для `PendingIntent` полуночного обновления виджета. `BootReceiver` использует `notificationId=1001` для уведомления о точных будильниках. Это разные сущности (notification vs PendingIntent), но числовое совпадение усложняет отладку.
- **К чему приведёт**: путаница в логах, сложность отладки.
- **Решение**: вынести `MIDNIGHT_REQUEST_CODE` в изолированный диапазон (например, `800001`), как сделано для обычных будильников (`REQUEST_CODE_BASE = 1_000_000`).

### 1.3. `AlarmRingingActivity.snoozeAlarm` — нет проверки `canScheduleExactAlarms()`
- **Локация**: `AlarmRingingActivity.kt:137-143`
- **Суть**: `snoozeAlarm` проверяет `alarmManager.canScheduleExactAlarms()` только в `if`, но при `false` молча ничего не делает — пользователь думает, что отложил будильник, но он не сработает.
- **К чему приведёт**: пользователь откладывает будильник свайпом, но он не зазвонит через 5 минут — молчаливый отказ.
- **Решение**: показать пользователю уведомление/Snackbar, что точные будильники запрещены и отложить не получится.

### 1.4. `ScheduleImageExporter.createYearImage` — временные файлы не всегда удаляются
- **Локация**: `ScheduleImageExporter.kt:235-254`
- **Суть**: в `createYearImage` вызывается `createMonthImage` (создаёт временный PNG), затем `BitmapFactory.decodeFile`, и в `finally` — `temp.delete()`. Но если `createMonthImage` бросит исключение до возврата файла, `temp` будет null, и `delete()` не вызовется.
- **К чему приведёт**: накопление временных PNG-файлов в `cacheDir/exports` при ошибках экспорта за год.
- **Решение**: обернуть весь цикл в `try/finally`, удаляющий `temp` даже при исключении в `createMonthImage`.

### 1.5. `DataStoreManager.load` — unchecked cast `processCache[key] as T`
- **Локация**: `DataStoreManager.kt:386`
- **Суть**: `(processCache[key] as? T) ?: default` — если тип `T` не совпадёт с сохранённым (например, после миграции ключа с `Float` на `String`), `as? T` вернёт null и возьмётся default. Это безопасно, но `@Suppress("UNCHECKED_CAST")` скрывает потенциальную проблему: если в `processCache` лежит значение неправильного типа, оно молча заменится на default без логирования.
- **К чему приведёт**: тихая потеря данных при рассинхроне типов ключей между `DataStoreManager` и `ColorSettingsManager`/`SettingsManager`.
- **Решение**: добавить `Timber.w` при срабатывании fallback, чтобы диагностировать рассинхрон типов.

---

## 2. НЕСОВПАДЕНИЯ И КОНФЛИКТЫ

### 2.1. Двойная запись бригады: DataStore + SharedPreferences
- **Локация**: `SettingsManager.kt:150-151`
- **Суть**: `setBrigade` пишет бригаду и в DataStore, и в `SharedPreferences(PreferenceKeys.SETTINGS_PREFS)`. Комментарий говорит о «legacy widget migration window», но виджет уже читает из DataStore через `AppDependencies.settingsManager.getBrigade()`.
- **К чему приведёт**: рассинхрон источников, если один источник обновится, а другой нет (например, при сбое `apply()`).
- **Решение**: удалить зеркало в SharedPreferences после подтверждения, что виджет больше не читает `PreferenceKeys.BRIGADE_KEY` напрямую.

### 2.2. `AutoSilenceScheduler` — настройки в DataStore, временное состояние в SharedPreferences
- **Локация**: `SilentModeReceiver.kt:36` (`PreferenceKeys.AUTO_SILENCE_PREFS`) vs `SettingsManager.getAutoSilenceEnabled()` (DataStore)
- **Суть**: настройки авто-тишины (enabled/start/end) перенесены в DataStore, но временное состояние (`KEY_WAS_SILENCED_TODAY`, `KEY_SAVED_INTERRUPTION_FILTER`) всё ещё в SharedPreferences. Это не ошибка, но смешение двух хранилищ для одной фичи усложняет понимание.
- **К чему приведёт**: сложность отладки — нужно смотреть два хранилища.
- **Решение**: оставить как есть (временное состояние логически отделено), но задокументировать в `PreferenceKeys.kt`, что `AUTO_SILENCE_PREFS` — только для runtime-состояния.

### 2.3. `ExportStyle.IMG_*` константы не используются в `ScheduleImageExporter`
- **Локация**: `ExportStyle.kt:51-59` vs `ScheduleImageExporter.kt:44-54`
- **Суть**: `ExportStyle` объявляет `IMG_WIDTH=1400`, `IMG_HEIGHT=1900`, `IMG_MARGIN=70f`, `IMG_CELL_H=150f`, `IMG_GAP=10f`, но `ScheduleImageExporter` использует хардкоженные `width=1400`, `height=1900`, `margin=70f`, `cellH=150f`, `gap=10f` напрямую.
- **К чему приведёт**: рассинхрон, если константы в `ExportStyle` изменят — экспортёр продолжит использовать старые значения.
- **Решение**: заменить хардкоженные значения в `ScheduleImageExporter` на `ExportStyle.IMG_*`.

---

## 3. АРХИТЕКТУРА И ЧИСТОТА КОДА

### 3.1. `SalaryCalculator.calculate` — функция слишком длинная (~95 строк)
- **Локация**: `SalaryCalculator.kt:175-269`
- **Суть**: один метод `calculate` делает: расчёт смен, оклада, вредности, ночных, праздничных, премии за прошлый месяц, пенсии, ФСЗН, профсоюза, подоходного, аванса, итогов. Это нарушает SRP — сложно тестировать отдельные части и менять формулу.
- **К чему приведёт**: высокий риск регресса при изменении формулы, сложность ревью.
- **Решение**: разбить на приватные методы: `calculateOklad`, `calculateNightPay`, `calculatePremium`, `calculateDeductions`, `calculateTotals`. Это не меняет контракт, но улучшит читаемость.

### 3.2. `AppCoroutineScope` — глобальный object-scope без явного lifecycle
- **Локация**: `AppCoroutineScope.kt`
- **Суть**: `object AppCoroutineScope : CoroutineScope` — это синглтон-скоп, который никогда не отменяется. Используется в `SalaryCalculatorViewModel.onCleared()` для сохранения данных после уничтожения ViewModel. Комментарий объясняет, что это лучше `GlobalScope`, но это всё ещё неуправляемый скоп.
- **К чему приведёт**: корутины, запущенные в нём, живут вечно; при тестах сложно изолировать.
- **Решение**: уже частично решено через DI (`single<CoroutineScope> { (androidApplication() as App).applicationScope }`), но `AppCoroutineScope` остаётся как fallback в `SalaryCalculatorViewModel` (строка 23). Убрать fallback и всегда использовать DI-скоп.

### 3.3. `HistoryManager` — дублирование маппинга entity→record
- **Локация**: `SalaryRepository.kt:76-100`
- **Суть**: `getHistoryRecords()` и `getHistoryRecordsByYear()` содержат одинаковый маппинг `SalaryHistoryEntity → SalaryHistoryRecord`. Нарушение DRY.
- **К чему приведёт**: при изменении полей `SalaryHistoryRecord` нужно править два места.
- **Решение**:
```kotlin
private fun SalaryHistoryEntity.toRecord() = SalaryHistoryRecord(
    monthIndex = monthIndex, year = year, monthName = monthName,
    totalClean = totalClean, cleanToPay = cleanToPay, advance = advance
)
// Использование:
historyDao.getAllRecords().map { it.toRecord() }
```

### 3.4. `SettingsViewModel` — передача `AlarmScheduler` параметром в методы
- **Локация**: `SettingsViewModel.kt:86-108`
- **Суть**: `setBrigade(brigade, scheduler)` и `setScheduleType(type, scheduler)` принимают `AlarmScheduler` параметром, хотя `AlarmScheduler` зарегистрирован в DI как синглтон. ViewModel должна получать его через конструктор, а не через параметры методов.
- **К чему приведёт**: нарушение инверсии зависимостей, сложность тестирования (нужно мокать `scheduler` в каждом вызове).
- **Решение**: внедрить `AlarmScheduler` через конструктор `SettingsViewModel`.

---

## 4. ПОТЕНЦИАЛЬНЫЕ ТОЧКИ ОТКАЗА

### 4.1. `ScheduleViewModel.applyVacation` — корутины без отмены
- **Локация**: `ScheduleViewModel.kt:79-92`
- **Суть**: `applyVacation` запускает `viewModelScope.launch` для каждого месяца в `grouped.forEach`. Если пользователь быстро вызовет `applyVacation` повторно, предыдущие корутины не отменятся — возможна гонка записи в Room.
- **К чему приведёт**: перезапись vacation-дней при быстром повторном вызове.
- **Решение**: использовать один `Job` для `applyVacation` и отменять предыдущий перед новым запуском, либо использовать `saveMutex.withLock` для всей операции, а не для каждого месяца отдельно.

### 4.2. `CalendarSyncManager.getPrimaryCalendarId` — нет проверки прав записи
- **Локация**: `CalendarSyncManager.kt:175-204`
- **Суть**: `getPrimaryCalendarId` ищет календарь с `CALENDAR_ACCESS_LEVEL >= CAL_ACCESS_CONTRIBUTOR`, но `CAL_ACCESS_CONTRIBUTOR` позволяет добавлять события, но не всегда — удалять. `removeMonthFromCalendar` может не найти события, если они были добавлены в другой календарь (при повторной синхронизации с другим `calendarId`).
- **К чему приведёт**: дубликаты событий при повторной синхронизации, если пользователь сменил календарь по умолчанию.
- **Решение**: искать календарь с `CAL_ACCESS_OWNER` или сохранять `calendarId` в настройках, чтобы удаление шло по тому же календарю, что и добавление.

### 4.3. `SalaryCalculator` — нет защиты от NaN/Infinity во входных параметрах
- **Локация**: `SalaryCalculator.kt:200-245`
- **Суть**: `parseNonNegative` фильтрует `isFinite()`, но `inputs.okladBase`, `inputs.koefStazh`, `inputs.koefPrem` приходят из `SettingsManager` и не проверяются на `NaN`/`Infinity`. Если в DataStore попадёт `NaN` (через баг миграции), расчёт даст `NaN`, который распространится в UI.
- **К чему приведёт**: отображение `NaN` в полях зарплаты, невозможность расчёта.
- **Решение**: в начале `calculate` валидировать `inputs.okladBase`, `koefStazh`, `koefPrem` на `isFinite() && >= 0`, возвращать `error` при невалидных.

### 4.4. `WidgetScheduleModel.adjustedPayDate` — некорректный сдвиг для выходных
- **Локация**: `WidgetScheduleModel.kt:39-43`
- **Суть**: `adjustedPayDate(10, month)` — если 10-е число выпадает на субботу (dayOfWeek=6) или воскресенье (7), дата сдвигается назад (`minusDays(1)`), пока не станет будним. Но по ТК РБ зарплата выплачивается не ранее следующего рабочего дня, а не накануне. Сдвиг назад может показать зарплату раньше фактической даты.
- **К чему приведёт**: виджет показывает дату зарплаты на 1-2 дня раньше реальной.
- **Решение**: сдвигать вперёд (`plusDays(1)`) до ближайшего буднего дня, а не назад.

### 4.5. `BootReceiver` — нет проверки `canScheduleExactAlarms()` перед `rescheduleAllAfterBoot`
- **Локация**: `BootReceiver.kt:32-51`
- **Суть**: `rescheduleAllAfterBoot` вызывается безусловно, но внутри `ShiftAlarmScheduler.scheduleSingleShiftAlarm` проверяет `canScheduleExactAlarms()` и молча пропускает, если нет разрешения. Проверка `canScheduleExactAlarms()` в `BootReceiver` (строка 38) происходит после `rescheduleAllAfterBoot`, а не до.
- **К чему приведёт**: будильники молча не ставятся после загрузки, если нет разрешения; уведомление показывается, но пользователь может не понять, что будильники уже потеряны.
- **Решение**: порядок правильный (сначала перепланирование, потом уведомление), но стоит логировать, сколько будильников реально установлено, а не только факт вызова.

---

## 5. ЧТО ИЗМЕНИТЬ И ДОРАБОТАТЬ

### 5.1. `SalaryCalculator.calculate` — округление промежуточных значений
- **Локация**: `SalaryCalculator.kt:200-245`
- **Суть**: промежуточные значения (`okladReal`, `stazh`, `vrednost`, `nochPay`, `prem`) не округляются до копеек. Округляется только финальный результат через `MoneyFormatter`. По заводской формуле промежуточные суммы должны округляться до копеек на каждом шаге, иначе накопление погрешности даст расхождение с расчётным листком.
- **К чему приведёт**: расхождение расчёта приложения с расчётным листком завода на копейки/рубли.
- **Решение**: применить `MoneyFormatter.round()` к каждому промежуточному значению:
```kotlin
val okladReal = MoneyFormatter.round((inputs.okladBase / normVal) * factVal)
val stazh = MoneyFormatter.round(okladReal * inputs.koefStazh)
// ... и т.д.
```

### 5.2. `HistoryDao.getAllRecords` — нет пагинации
- **Локация**: `HistoryDao.kt:10-11`
- **Суть**: `getAllRecords(limit: Int = MAX_RECORDS)` загружает до 500 записей разом. При многолетнем использовании (10+ лет) список может вырасти, и загрузка 500 записей в `MutableStateFlow` при каждом `applyFilter` создаёт лишнюю нагрузку.
- **К чему приведёт**: замедление UI при открытии истории после нескольких лет использования.
- **Решение**: добавить пагинацию через `LIMIT/OFFSET` или `PagingSource`, загружать первые 50 записей, остальные — по требованию.

### 5.3. `ScheduleIcsExporter` — хардкод `Europe/Minsk`
- **Локация**: `ScheduleIcsExporter.kt:36, 64-65`
- **Суть**: `X-WR-TIMEZONE:Europe/Minsk` и `DTSTART;TZID=Europe/Minsk` захардкожены. Если пользователь в другой зоне, времена смен будут некорректны в импортированном календаре.
- **К чему приведёт**: некорректные времена событий при импорте .ics из другой часовой зоны.
- **Решение**: использовать `java.util.TimeZone.getDefault().id` вместо хардкода.

### 5.4. `PdfScheduleExporter` — нет `ExportRetry` при записи
- **Локация**: `PdfScheduleExporter.kt:183-186, 212-215`
- **Суть**: `createMonthPdf` и `createYearPdf` пишут PDF через `FileOutputStream(file).use { document.writeTo(out) }` без `ExportRetry`, в отличие от `SalaryPdfExporter` и `ScheduleImageExporter`, которые используют `ExportRetry.withRetry`.
- **К чему приведёт**: разовый сбой файловой системы роняет экспорт графика в PDF, хотя другие экспорты защищены retry.
- **Решение**: обернуть запись в `ExportRetry.withRetry(operationName = "PDF графика")`.

### 5.5. `HistoryExporter.exportHistoryToCsv` — нет `ExportRetry` и UTF-8 BOM
- **Локация**: `HistoryExporter.kt:29-31`
- **Суть**: `file.writeText(csvContent.toString())` без retry и без BOM. Excel на Windows не распознаёт UTF-8 без BOM, кириллица будет отображаться некорректно.
- **К чему приведёт**: кракозябры в Excel при открытии CSV; разовый сбой файловой системы роняет экспорт.
- **Решение**: добавить `ExportRetry` и писать BOM (`\uFEFF`) в начало файла.

---

## 6. ЧТО ДОПОЛНИТЬ И УЛУЧШИТЬ

### 6.1. Нет retry-механизма для `CalendarSyncManager`
- **Локация**: `CalendarSyncManager.kt:74-108`
- **Суть**: вставка событий в календарь выполняется без retry. `contentResolver.insert` может временно вернуть `null` (CalendarProvider занят), и синхронизация молча провалится.
- **Решение**: обернуть `insert` в `ExportRetry.withRetry` (2-3 попытки).

### 6.2. Нет валидации `anchorDateGraph2` при загрузке
- **Локация**: `App.kt:116-123` (`syncShiftScheduleAnchor`)
- **Суть**: `syncShiftScheduleAnchor` загружает только `anchorDate` (для Графика №1), но не `anchorDateGraph2`. Если `anchorDateGraph2` когда-либо будет вынесен в настройки, его нужно будет синхронизировать аналогично.
- **Решение**: пока `anchorDateGraph2` — константа, это не баг, но стоит добавить TODO/комментарий, что при вынесении в настройки нужна синхронизация.

### 6.3. `FileLogTree` — нет ротации по размеру каталога
- **Локация**: `FileLogTree.kt:66-75`
- **Суть**: `trimOldLogs` удаляет файлы сверх `MAX_LOG_FILES=5`, но не проверяет суммарный размер каталога. Если один файл достигнет 2 МБ, а потом ещё 5 файлов по 2 МБ — это 12 МБ логов.
- **Решение**: добавить проверку суммарного размера и удалять самые старые при превышении (например, 10 МБ).

### 6.4. Нет аналитики/метрик использования
- **Суть**: в приложении нет аналитики (Firebase Analytics / Yandex Metrica), поэтому невозможно понять, какие функции используются чаще, какие ошибки встречаются в проде.
- **Решение**: добавить анонимную аналитику ключевых событий (расчёт зарплаты, экспорт, синхронизация с календарём) — опционально, с согласия пользователя.

### 6.5. Нет unit-тестов для `AlarmSoundService`, `AutoSilenceScheduler`, `WidgetRenderer`
- **Локация**: `app/src/test/java/com/example/salarynaftan/`
- **Суть**: есть тесты для `SalaryCalculator`, `ShiftSchedule`, `MonthlyNorms`, `HistoryManager`, `SettingsManager`, `DataStoreManager`, `ScheduleViewModel`, но нет для `AlarmSoundService` (логика нарастания громкости), `AutoSilenceScheduler` (расчёт времени тишины), `WidgetRenderer` (цвета ячеек).
- **Решение**: добавить unit-тесты для расчёта времени в `AutoSilenceScheduler` (переход через полночь, невалидное время) и для `WidgetScheduleModel.adjustedPayDate` (см. п. 4.4).

### 6.6. `PermissionManager` — нет запроса `SCHEDULE_EXACT_ALARM`
- **Локация**: `PermissionManager.kt`
- **Суть**: `PermissionManager` запрашивает `POST_NOTIFICATIONS` и `USE_FULL_SCREEN_INTENT`, но не `SCHEDULE_EXACT_ALARM` (Android 12+). Приложение проверяет `canScheduleExactAlarms()` в коде, но не направляет пользователя в настройки, если разрешение отсутствует (только `BootReceiver` показывает уведомление после загрузки).
- **Решение**: добавить метод `ensureExactAlarmPermission()` в `PermissionManager`, направляющий в `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM` при отсутствии разрешения.

### 6.7. `SalaryCalculatorViewModel.saveToHistory` — нет валидации `result`
- **Локация**: `SalaryCalculatorViewModel.kt:260-271`
- **Суть**: `saveToHistory` берёт `state.calculationResult ?: return`, но не проверяет `result.error`. Если расчёт завершился с ошибкой (но `calculationResult` не null), в историю сохранится некорректная запись.
- **Решение**: добавить `if (result.error != null) return`.

### 6.8. Нет обработки `ActivityNotFoundException` для `shareFile`
- **Локация**: `AppUtils.kt:49`
- **Суть**: `context.startActivity(Intent.createChooser(intent, chooserTitle))` обёрнуто в `try/catch (e: Exception)`, но `ActivityNotFoundException` — частый случай (нет приложения для MIME-типа). Пользователь не получает понятного сообщения.
- **Решение**: перехватывать `ActivityNotFoundException` отдельно и показывать in-app уведомление через `AppNotifier.showError("Нет приложения для отправки файла")`.

---

## Замечание вне темы задачи (не по теме аудита, для отдельного обсуждения)

- **`AppCoroutineScope` (объект-синглтон) и DI-скоп `applicationScope` дублируют ответственность**. `SalaryCalculatorViewModel` принимает `appScope` через DI, но имеет fallback `AppCoroutineScope` в дефолтном параметре. Это создаёт два источника скопа — стоит убрать `AppCoroutineScope` после миграции всех вызовов на DI.
