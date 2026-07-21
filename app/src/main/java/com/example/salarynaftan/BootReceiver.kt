package com.example.salarynaftan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {

            val pendingResult = goAsync()

            CoroutineScope(Dispatchers.IO).launch {

                try {
                    val scheduler = AlarmScheduler(context)
                    scheduler.rescheduleAllAfterBoot()

                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}