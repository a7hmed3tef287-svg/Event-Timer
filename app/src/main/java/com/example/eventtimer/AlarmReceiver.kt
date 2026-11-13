package com.example.eventtimer

import android.app.*
import android.content.*
import android.media.AudioAttributes
import android.media.RingtoneManager

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val name = i.getStringExtra("name") ?: ""
        val id = i.getIntExtra("id", 0)
        val nm = c.getSystemService(NotificationManager::class.java)
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val ch = NotificationChannel("done", "انتهاء المؤقت", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600)
        }
        nm.createNotificationChannel(ch)
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(c, "done")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(name)
            .setContentText("خلص الوقت، استلم الجايزة")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        nm.notify(id, n)
    }
}
