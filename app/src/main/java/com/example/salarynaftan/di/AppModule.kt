package com.example.salarynaftan.di

import com.example.salarynaftan.*
import com.example.salarynaftan.data.SalaryRepository
import com.example.salarynaftan.export.HistoryExporter
import com.example.salarynaftan.ui.SalaryCalculatorViewModel
import com.example.salarynaftan.ui.SettingsViewModel
import com.example.salarynaftan.ui.ScheduleDataCoordinator
import com.example.salarynaftan.ui.LegacyYearRecoveryViewModel
import com.example.salarynaftan.ui.ScheduleViewModel
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidApplication
import kotlinx.coroutines.CoroutineScope
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

val appModule = module {
    // Scope принадлежит экземпляру Application, а не процессному object;
    // это сохраняет корректный lifecycle и позволяет тестам заменить scope.
    single<CoroutineScope> { (androidApplication() as App).applicationScope }
    single { SettingsManager(get()) }
    single { AlarmScheduler(get(), get()) }
    single { HistoryExporter(androidContext()) }
    single { HistoryManager(get(), get()) }
    single { ColorSettingsManager(get()) }
    single { WidgetRenderer() }
    single { WidgetScheduler(androidContext()) }
    single { SalaryRepository(androidContext()) }
    single { ScheduleDataCoordinator(get()) }
    // HistoryManager и AlarmScheduler уже зарегистрированы выше как синглтоны (п.3.2),
    // чтобы ViewModel могли получать их через конструктор, а не параметром.

    viewModel {
        SalaryCalculatorViewModel(
            savedStateHandle = get(),
            settingsManager = get(),
            salaryRepository = get(),
            appScope = get()
        )
    }

    viewModel {
        SettingsViewModel(
            application = androidApplication(),
            settingsManager = get(),
            colorSettings = get(),
            alarmScheduler = get()
        )
    }

    viewModel { LegacyYearRecoveryViewModel(get()) }
    viewModel { ScheduleViewModel(get(), get(), get()) }
}

/**
 * Entry point for Android framework components which are constructed by the
 * OS (receivers, workers and widgets), not by Koin itself.
 */
object AppDependencies : KoinComponent {
    val settingsManager: SettingsManager by inject()
    val alarmScheduler: AlarmScheduler by inject()
    val colorSettingsManager: ColorSettingsManager by inject()
    val widgetRenderer: WidgetRenderer by inject()
    val widgetScheduler: WidgetScheduler by inject()
}