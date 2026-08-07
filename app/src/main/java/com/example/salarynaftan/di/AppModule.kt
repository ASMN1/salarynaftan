package com.example.salarynaftan.di

import com.example.salarynaftan.*
import com.example.salarynaftan.data.SalaryRepository
import com.example.salarynaftan.export.HistoryExporter
import com.example.salarynaftan.ui.SalaryCalculatorViewModel
import com.example.salarynaftan.ui.SettingsViewModel
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidApplication

val appModule = module {
    single { SettingsManager(get()) }
    single { AlarmScheduler(get()) }
    single { HistoryExporter(androidContext()) }
    single { HistoryManager(get(), get()) }
    single { ColorSettingsManager(get()) }
    single { SalaryRepository(androidContext()) }

    viewModel {
        SalaryCalculatorViewModel(
            savedStateHandle = get(),
            settingsManager = get(),
            salaryRepository = get()
        )
    }

    viewModel {
        SettingsViewModel(
            application = androidApplication(),
            settingsManager = get(),
            colorSettings = get()
        )
    }
}