package com.example.eventtimer

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.RingtoneManager

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val name = i.getStringExtra("name") ?: ""
        val id = i.getIntExtra("id", 0)
        val soundOn = c.getSharedPreferences("t", Context.MODE_PRIVATE).getBoolean("sound", true)
        val nm = c.getSystemService(NotificationManager::class.java)
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val loud = NotificationChannel("done", "انتهاء المؤقت (بصوت)", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600)
        }
        val quiet = NotificationChannel("quiet", "انتهاء المؤقت (صامت)", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600)
        }
        nm.createNotificationChannel(loud)
        nm.createNotificationChannel(quiet)
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

        fun post(channel: String, insistent: Boolean) {
            val n = Notification.Builder(c, channel)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(name)
                .setContentText("خلص الوقت، استلم الجايزة")
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .build()
            if (insistent) n.flags = n.flags or Notification.FLAG_INSISTENT
            nm.notify(id, n)
        }

        if (soundOn) {
            try {
                c.startForegroundService(Intent(c, AlarmService::class.java).putExtra("name", name))
                post("quiet", false)
            } catch (e: Throwable) {
                post("done", true)
            }
        } else {
            post("quiet", false)
        }
        TimerWidget.refresh(c)
    }
}