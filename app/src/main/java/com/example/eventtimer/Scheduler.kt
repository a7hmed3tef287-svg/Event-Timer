package com.example.eventtimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

object Scheduler {
    private fun pi(c: Context, id: Int, name: String): PendingIntent = PendingIntent.getBroadcast(
        c, id, Intent(c, AlarmReceiver::class.java).putExtra("name", name).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(c: Context, id: Int, name: String, endAt: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(endAt, show), pi(c, id, name))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi(c, id, name))
        }
    }

    fun cancel(c: Context, id: Int, name: String) {
        c.getSystemService(AlarmManager::class.java).cancel(pi(c, id, name))
    }
}