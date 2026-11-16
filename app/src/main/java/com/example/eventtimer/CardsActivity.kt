package com.example.eventtimer

import android.Manifest
import android.app.*
import android.content.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

abstract class CardsActivity : ToolsActivity() {
    private val zone = ZoneId.of("Africa/Cairo")

    private val fmt = DateTimeFormatter.ofPattern("hh:mm a (d/M)")

    val tick = object : Runnable {
        override fun run() { update(); handler.postDelayed(this, 1000) }
    }

    override fun rebuild() {
        try {
            list.removeAllViews()
            holders.clear()
            if (items.isEmpty()) {
                val e = TextView(this)
                e.text = "🎮\nمفيش مؤقتات لسه\nدوس ➕ فوق وضيف أول حساب"
                e.gravity = Gravity.CENTER
                e.textSize = 16f
                e.setTextColor(cGray)
                e.setPadding(0, dp(40), 0, 0)
                list.addView(e)
            }
            items.sortedBy { it.endAt }.forEach { addCard(it) }
            update()
        } catch (e: Throwable) {
            showError(android.util.Log.getStackTraceString(e))
        }
    }

    fun addCard(it0: TimerItem) {
        val col = colorOf(it0.name)
        val card = LinearLayout(this)
        card.orientation = LinearLayout.HORIZONTAL
        val bg = rounded(cCard, 18)
        bg.setStroke(dp(1), Color.parseColor("#3A4070"))
        card.background = bg
        card.elevation = dp(6).toFloat()
        card.cameraDistance = resources.displayMetrics.density * 8000f

        val stripe = View(this)
        stripe.background = rounded(col, 4)
        card.addView(stripe, LinearLayout.LayoutParams(dp(6), -1).apply {
            setMargins(dp(8), dp(10), dp(4), dp(10))
        })

        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(10), dp(10), dp(14), dp(10))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val nm = TextView(this)
        nm.text = it0.name
        nm.textSize = 17f
        nm.typeface = Typeface.DEFAULT_BOLD
        nm.setTextColor(col)
        nm.maxLines = 1
        nm.setOnClickListener { openLink(it0.name) }
        nm.setOnLongClickListener { accountMenu(it0.name); true }
        val time = TextView(this)
        time.textSize = 24f
        time.typeface = Typeface.MONOSPACE
        time.setShadowLayer(6f, 0f, 4f, Color.BLACK)
        top.addView(nm, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(time, LinearLayout.LayoutParams(-2, -2))

        val end = TextView(this)
        end.textSize = 12f
        end.setTextColor(cGray)
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 1000
        bar.progressTintList = ColorStateList.valueOf(col)
        bar.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#3A4070"))

        val restart = btn("⏱ وقت جديد", "#2EC4B6")
        restart.textSize = 13f
        restart.setOnClickListener { newTime(it0) }
        val del = btn("🗑 حذف", "#E5484D")
        del.textSize = 13f
        del.setOnClickListener {
            cancel(it0); items.remove(it0); save(); rebuild()
        }
        val btns = LinearLayout(this)
        btns.orientation = LinearLayout.HORIZONTAL
        val p1 = LinearLayout.LayoutParams(0, dp(42), 1f)
        p1.marginEnd = dp(6)
        val gameB = btn("🎮 افتح", "#7C5CFF")
        gameB.textSize = 13f
        gameB.setOnClickListener { openLink(it0.name) }
        btns.addView(gameB, LinearLayout.LayoutParams(0, dp(42), 2f).apply { marginEnd = dp(6) })
        if (!links[it0.name].isNullOrBlank()) {
            val linkB = btn("🔗", "#4DABF7")
            linkB.textSize = 13f
            linkB.setOnClickListener { openLink(it0.name) }
            btns.addView(linkB, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
        }
        p1.weight = 2f
        btns.addView(restart, p1)
        btns.addView(del, LinearLayout.LayoutParams(0, dp(42), 1f))

        body.addView(top)
        body.addView(end)
        body.addView(bar, LinearLayout.LayoutParams(-1, dp(6)).apply { topMargin = dp(6) })
        body.addView(btns, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        card.addView(body, LinearLayout.LayoutParams(0, -2, 1f))

        list.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8); bottomMargin = dp(4)
        })
        holders.add(CardHolder(it0, card, end, time, bar, restart, it0.endAt <= System.currentTimeMillis()))

        if (it0.id == lastAddedId) {
            card.alpha = 0f
            card.translationY = dp(30).toFloat()
            card.animate().alpha(1f).translationY(0f).setDuration(350).start()
            lastAddedId = -1
        }
    }

    fun newTime(it0: TimerItem) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        box.setPadding(dp(20), dp(12), dp(20), dp(0))
        val hF = EditText(this)
        hF.hint = "ساعات"
        hF.inputType = InputType.TYPE_CLASS_NUMBER
        val mF = EditText(this)
        mF.hint = "دقايق"
        mF.inputType = InputType.TYPE_CLASS_NUMBER
        val prev = it0.durMs / 60_000L
        if (prev / 60L > 0L) hF.setText((prev / 60L).toString())
        if (prev % 60L > 0L) mF.setText((prev % 60L).toString())
        box.addView(hF, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        box.addView(mF, LinearLayout.LayoutParams(0, -2, 1f))
        AlertDialog.Builder(this).setTitle("وقت جديد: " + it0.name).setView(box)
            .setPositiveButton("ابدأ") { _, _ ->
                val hh = hF.text.toString().toLongOrNull() ?: 0L
                val mm = mF.text.toString().toLongOrNull() ?: 0L
                val mins = hh * 60L + mm
                if (mins <= 0L) {
                    Toast.makeText(this, "اكتب الوقت", Toast.LENGTH_SHORT).show()
                } else {
                    it0.durMs = mins * 60_000L
                    it0.endAt = System.currentTimeMillis() + it0.durMs
                    save(); schedule(it0); rebuild()
                }
            }
            .setNegativeButton("إلغاء", null).show()
    }

    fun update() {
        try {
            val now = System.currentTimeMillis()
            holders.forEach { h ->
                val left = h.item.endAt - now
                h.end.text = "⏰ يخلص: " +
                    fmt.format(Instant.ofEpochMilli(h.item.endAt).atZone(zone))
                val ready = left <= 0
                val bg = h.card.background as GradientDrawable
                if (ready) {
                    h.time.text = "✅ جاهز"
                    h.time.textSize = 18f
                    h.time.setTextColor(Color.WHITE)
                    h.bar.progress = 1000
                    bg.setColor(cGreen)
                } else {
                    val t = left / 1000
                    h.time.text = String.format("%02d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60)
                    h.time.textSize = 24f
                    val soon = left < 60_000
                    h.time.setTextColor(if (soon) Color.parseColor("#FF6B6B") else cGold)
                    bg.setColor(if (soon) cRed else cCard)
                    h.bar.progress =
                        (((h.item.durMs - left) * 1000) / h.item.durMs).toInt().coerceIn(0, 1000)
                }
                h.restart.visibility = if (ready) View.VISIBLE else View.GONE
                if (ready && !h.ready) {
                    h.card.animate().scaleX(1.04f).scaleY(1.04f).setDuration(180)
                        .withEndAction {
                            h.card.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
                        }.start()
                    TimerWidget.refresh(this)
                }
                h.ready = ready
            }
        } catch (e: Throwable) {
            handler.removeCallbacks(tick)
            showError(android.util.Log.getStackTraceString(e))
        }
    }

    fun schedule(x: TimerItem) = Scheduler.schedule(this, x.id, x.name, x.endAt)

    fun cancel(x: TimerItem) {
        Scheduler.cancel(this, x.id, x.name)
        getSystemService(NotificationManager::class.java).cancel(x.id)
    }
}
