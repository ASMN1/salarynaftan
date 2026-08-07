package com.example.salarynaftan

import android.app.Application

/**
 * Тестовый Application для Robolectric-тестов (Settings/DataStore).
 * Не запускает Koin и не создаёт каналов уведомлений —
 * в противном случае App.onCreate() бросал бы KoinApplicationAlreadyStartedException
 * между тестами внутри одного JVM.
 */
class TestApp : Application()
