package com.example.eventtimer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import org.json.JSONArray

class TimerWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        refresh(c)
    }

    companion object {
        fun refresh(c: Context) {
            try {
                val m = AppWidgetManager.getInstance(c)
                val ids = m.getAppWidgetIds(ComponentName(c, TimerWidget::class.java))
                if (ids.isEmpty()) return
                val now = System.currentTimeMillis()
                val list = mutableListOf<Pair<String, Long>>()
                val s = c.getSharedPreferences("t", Context.MODE_PRIVATE).getString("items", "[]") ?: "[]"
                val a = JSONArray(s)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val e = o.getLong("e")
                    if (e > now) list.add(Pair(o.getString("n"), e))
                }
                list.sortBy { it.second }

                val rows = listOf(
                    intArrayOf(R.id.r1, R.id.n1, R.id.c1),
                    intArrayOf(R.id.r2, R.id.n2, R.id.c2),
                    intArrayOf(R.id.r3, R.id.n3, R.id.c3)
                )
                val v = RemoteViews(c.packageName, R.layout.widget)
                rows.forEachIndexed { idx, r ->
                    if (idx < list.size) {
                        v.setViewVisibility(r[0], View.VISIBLE)
                        v.setTextViewText(r[1], "🎮 " + list[idx].first)
                        v.setChronometerCountDown(r[2], true)
                        v.setChronometer(r[2], SystemClock.elapsedRealtime() + (list[idx].second - now), null, true)
                    } else {
                        v.setViewVisibility(r[0], View.GONE)
                    }
                }
                v.setViewVisibility(R.id.empty, if (list.isEmpty()) View.VISIBLE else View.GONE)
                val open = PendingIntent.getActivity(
                    c, 1, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                v.setOnClickPendingIntent(R.id.root, open)
                m.updateAppWidget(ids, v)
            } catch (_: Throwable) {}
        }
    }
}