package com.example.salarynaftan.di

import android.app.Application
import com.example.salarynaftan.SalaryCalculatorViewModel
import com.example.salarynaftan.SettingsManager
import com.example.salarynaftan.AlarmScheduler
import com.example.salarynaftan.HistoryManager
import com.example.salarynaftan.ColorSettingsManager
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel

val appModule = module {
    single { SettingsManager(get()) }
    single { AlarmScheduler(get(), get()) }
    single { HistoryManager(get()) }
    single { ColorSettingsManager(get()) }

    viewModel {
        SalaryCalculatorViewModel(
            savedStateHandle = get(),
            appContext = get<Application>(),
            settingsManager = get()
        )
    }
}