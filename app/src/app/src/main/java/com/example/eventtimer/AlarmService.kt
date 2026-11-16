package com.example.eventtimer

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private val h = Handler(Looper.getMainLooper())
    private var vol = 1f
    private val ramp = object : Runnable {
        override fun run() {
            vol = (vol + 0.07f).coerceAtMost(1f)
            try { player?.setVolume(vol, vol) } catch (_: Throwable) {}
            if (vol < 1f) h.postDelayed(this, 1000)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (i?.action == "STOP") { shutdown(); return START_NOT_STICKY }
        val name = i?.getStringExtra("name") ?: ""
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel("ring", "منبّه شغال", NotificationManager.IMPORTANCE_HIGH)
        ch.setSound(null, null)
        ch.enableVibration(true)
        ch.vibrationPattern = longArrayOf(0, 600, 300, 600)
        nm.createNotificationChannel(ch)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 2,
            Intent(this, AlarmService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        val act = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_pause), "إيقاف", stop).build()
        val n = Notification.Builder(this, "ring")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(name)
            .setContentText("خلص الوقت، استلم الجايزة")
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_ALARM)
            .addAction(act)
            .build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(999001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(999001, n)
        if (player == null) startPlayer()
        h.removeCallbacks(autoStop)
        h.postDelayed(autoStop, 120_000)
        return START_NOT_STICKY
    }

    private val autoStop = Runnable { shutdown() }

    private fun play(uri: Uri, gradual: Boolean): Boolean {
        return try {
            val p = MediaPlayer()
            p.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            p.setDataSource(this, uri)
            p.isLooping = true
            vol = if (gradual) 0.05f else 1f
            p.setVolume(vol, vol)
            p.prepare()
            p.start()
            player = p
            if (gradual) h.postDelayed(ramp, 1000)
            true
        } catch (e: Throwable) { false }
    }

    private fun startPlayer() {
        val sp = getSharedPreferences("t", Context.MODE_PRIVATE)
        val saved = sp.getString("tone", "") ?: ""
        val gradual = sp.getBoolean("gradual", true)
        val def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val ok = saved.isNotEmpty() && play(Uri.parse(saved), gradual)
        if (!ok) play(def, gradual)
    }

    private fun shutdown() {
        h.removeCallbacksAndMessages(null)
        try { player?.stop() } catch (_: Throwable) {}
        try { player?.release() } catch (_: Throwable) {}
        player = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        try { player?.release() } catch (_: Throwable) {}
        player = null
        super.onDestroy()
    }
}