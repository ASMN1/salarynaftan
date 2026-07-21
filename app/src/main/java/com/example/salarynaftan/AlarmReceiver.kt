package com.example.salarynaftan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("alarm_title") ?: "Смена"
        val shiftTypeName = intent.getStringExtra("shift_type_name")
        val alarmIndex = intent.getIntExtra("alarm_index", -1)
        val brigade = intent.getIntExtra("brigade", 1)

        // Запуск экрана звонка будильника
        val ringIntent = Intent(context, AlarmRingingActivity::class.java).apply {
            putExtra("alarm_title", title)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        context.startActivity(ringIntent)

        // Если это сменный будильник, автоматически перепланируем его на следующий раз для текущей бригады
        if (shiftTypeName != null) {
            try {
                val shiftType = ShiftType.valueOf(shiftTypeName)
                val scheduler = AlarmScheduler(context)

                val times = scheduler.getAlarmTimesForShift(shiftType, brigade)

                if (alarmIndex in times.indices) {
                    scheduler.scheduleSingleShiftAlarm(
                        type = shiftType,
                        brigade = brigade,
                        index = alarmIndex,
                        timeStr = times[alarmIndex]
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}