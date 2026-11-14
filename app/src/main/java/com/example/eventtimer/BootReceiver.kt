package com.example.eventtimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        try {
            val s = c.getSharedPreferences("t", Context.MODE_PRIVATE).getString("items", "[]") ?: "[]"
            val a = JSONArray(s)
            val now = System.currentTimeMillis()
            for (k in 0 until a.length()) {
                val o = a.getJSONObject(k)
                val e = o.getLong("e")
                if (e > now) Scheduler.schedule(c, o.getInt("id"), o.getString("n"), e)
            }
        } catch (_: Throwable) {}
    }
}