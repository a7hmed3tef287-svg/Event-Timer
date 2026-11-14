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
import android.os.*
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : Activity(), SensorEventListener {
    data class Item(val id: Int, val name: String, val durMs: Long, var endAt: Long)
    private class Holder(
        val item: Item, val card: LinearLayout, val end: TextView, val time: TextView,
        val bar: ProgressBar, val restart: Button, var ready: Boolean
    )

    private val items = mutableListOf<Item>()
    private val holders = mutableListOf<Holder>()
    private lateinit var list: LinearLayout
    private lateinit var soundBtn: Button
    private var lastAddedId = -1
    private var soundOn = true
    private var testR: Ringtone? = null
    private var tx = 0f
    private var ty = 0f
    private val handler = Handler(Looper.getMainLooper())
    private val zone = ZoneId.of("Africa/Cairo")
    private val fmt = DateTimeFormatter.ofPattern("hh:mm a  (d/M)")
    private val tick = object : Runnable {
        override fun run() { update(); handler.postDelayed(this, 1000) }
    }

    private val cCard = Color.parseColor("#232846")
    private val cField = Color.parseColor("#2E3459")
    private val cGold = Color.parseColor("#FFD166")
    private val cGray = Color.parseColor("#8890B5")
    private val cGreen = Color.parseColor("#1F8A4C")
    private val cRed = Color.parseColor("#5A2233")

    private fun showError(msg: String) {
        AlertDialog.Builder(this).setTitle("حصل خطأ").setMessage(msg.take(1500))
            .setPositiveButton("تمام", null).show()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val sp = getSharedPreferences("t", MODE_PRIVATE)
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            sp.edit().putString("crash", android.util.Log.getStackTraceString(e)).commit()
            Process.killProcess(Process.myPid())
        }
        soundOn = sp.getBoolean("sound", true)
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        try { load() } catch (e: Throwable) { items.clear() }
        buildUi()
        rebuild()
        sp.getString("crash", null)?.let {
            sp.edit().remove("crash").apply()
            showError(it)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
        val sm = getSystemService(SensorManager::class.java)
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        getSystemService(SensorManager::class.java).unregisterListener(this)
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    override fun onSensorChanged(e: SensorEvent) {
        val ry = (-e.values[0] * 1.2f).coerceIn(-9f, 9f)
        val rx = ((e.values[1] - 6f) * 1.2f).coerceIn(-9f, 9f)
        tx += (rx - tx) * 0.1f
        ty += (ry - ty) * 0.1f
        holders.forEach { it.card.rotationX = tx; it.card.rotationY = ty }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat()
    }

    private fun darken(c: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[2] *= 0.6f
        return Color.HSVToColor(hsv)
    }

    private fun style3d(b: Button, color: Int) {
        val ld = LayerDrawable(arrayOf(rounded(darken(color), 14), rounded(color, 14)))
        ld.setLayerInset(1, 0, 0, 0, dp(5))
        b.background = ld
        b.setPadding(dp(6), 0, dp(6), dp(5))
    }

    private fun btn(t: String, hex: String): Button {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.textSize = 14f
        b.setTextColor(Color.WHITE)
        b.typeface = Typeface.DEFAULT_BOLD
        b.stateListAnimator = null
        style3d(b, Color.parseColor(hex))
        b.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> v.animate().translationY(dp(3).toFloat()).setDuration(60).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().translationY(0f).setDuration(100).start()
            }
            false
        }
        return b
    }

    private fun field(h: String, type: Int): EditText {
        val e = EditText(this)
        e.hint = h
        e.inputType = type
        e.setTextColor(Color.WHITE)
        e.setHintTextColor(cGray)
        e.background = rounded(cField, 14)
        e.setPadding(dp(14), dp(12), dp(14), dp(12))
        return e
    }

    private fun panel(): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL
        p.setPadding(dp(14), dp(14), dp(14), dp(14))
        val bg = rounded(cCard, 20)
        bg.setStroke(dp(1), Color.parseColor("#3A4070"))
        p.background = bg
        p.elevation = dp(8).toFloat()
        return p
    }

    private fun refreshSoundBtn() {
        soundBtn.text = if (soundOn) "🔔 الصوت شغال" else "🔕 الصوت مقفول"
        style3d(soundBtn, Color.parseColor(if (soundOn) "#2EC4B6" else "#6B7280"))
    }

    private fun testSound() {
        try {
            testR?.stop()
            val r = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            r.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            r.play()
            testR = r
            handler.postDelayed({ try { r.stop() } catch (_: Throwable) {} }, 3000)
        } catch (e: Throwable) { showError(android.util.Log.getStackTraceString(e)) }
    }

    private fun stopSound() {
        try { testR?.stop() } catch (_: Throwable) {}
        getSystemService(NotificationManager::class.java).cancelAll()
    }

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setPadding(dp(16), dp(44), dp(16), dp(16))
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#0F1226"), Color.parseColor("#1B1F3B"))
        )

        val title = TextView(this)
        title.text = "⚔️ مؤقت الإيفنت"
        title.textSize = 26f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(cGold)
        title.setShadowLayer(10f, 0f, 6f, Color.BLACK)
        root.addView(title)
        val sub = TextView(this)
        sub.text = "بتوقيت القاهرة 🇪🇬"
        sub.setTextColor(cGray)
        root.addView(sub)

        // ---- صندوق الإضافة
        val box = panel()
        val name = field("👤 اسم الحساب / الإيميل", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val hh = field("ساعات", InputType.TYPE_CLASS_NUMBER)
        val mm = field("دقايق", InputType.TYPE_CLASS_NUMBER)
        val add = btn("➕ إضافة وبدء العد", "#FF7A59")
        val gap = LinearLayout.LayoutParams(0, -2, 1f)
        gap.marginEnd = dp(8)
        row.addView(hh, gap)
        row.addView(mm, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(name)
        box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        box.addView(add, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(10) })
        root.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        add.setOnClickListener {
            try {
                val n = name.text.toString().trim()
                val mins = (hh.text.toString().toLongOrNull() ?: 0) * 60 + (mm.text.toString().toLongOrNull() ?: 0)
                if (n.isEmpty() || mins <= 0) {
                    Toast.makeText(this, "اكتب الاسم والوقت", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val dur = mins * 60_000
                val item = Item((System.currentTimeMillis() % 1_000_000_000).toInt(), n, dur, System.currentTimeMillis() + dur)
                items.add(item); save(); schedule(item)
                lastAddedId = item.id
                name.text.clear(); hh.text.clear(); mm.text.clear()
                rebuild()
            } catch (e: Throwable) { showError(android.util.Log.getStackTraceString(e)) }
        }

        // ---- صندوق التحكم في الصوت
        val sbox = panel()
        val srow = LinearLayout(this)
        srow.orientation = LinearLayout.HORIZONTAL
        soundBtn = btn("", "#2EC4B6")
        soundBtn.textSize = 13f
        val test = btn("▶ جرّب", "#7C5CFF")
        test.textSize = 13f
        val stop = btn("⏹ إيقاف", "#E5484D")
        stop.textSize = 13f
        refreshSoundBtn()
        soundBtn.setOnClickListener {
            soundOn = !soundOn
            getSharedPreferences("t", MODE_PRIVATE).edit().putBoolean("sound", soundOn).apply()
            refreshSoundBtn()
            if (!soundOn) stopSound()
        }
        test.setOnClickListener { testSound() }
        stop.setOnClickListener { stopSound() }
        val m = LinearLayout.LayoutParams(0, dp(48), 1f)
        m.marginEnd = dp(6)
        val m2 = LinearLayout.LayoutParams(0, dp(48), 1f)
        m2.marginEnd = dp(6)
        srow.addView(soundBtn, m)
        srow.addView(test, m2)
        srow.addView(stop, LinearLayout.LayoutParams(0, dp(48), 1f))
        sbox.addView(srow)

        val vl = TextView(this)
        vl.text = "🔊 مستوى صوت المنبّه"
        vl.setTextColor(cGray)
        vl.setPadding(0, dp(10), 0, 0)
        sbox.addView(vl)
        val am = getSystemService(AudioManager::class.java)
        val seek = SeekBar(this)
        seek.max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        seek.progress = am.getStreamVolume(AudioManager.STREAM_ALARM)
        seek.progressTintList = ColorStateList.valueOf(cGold)
        seek.thumbTintList = ColorStateList.valueOf(cGold)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) try { am.setStreamVolume(AudioManager.STREAM_ALARM, p, 0) } catch (_: Throwable) {}
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) { testSound() }
        })
        sbox.addView(seek)
        root.addView(sbox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(this)
        sv.clipToPadding = false
        sv.addView(list)
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun rebuild() {
        try {
            list.removeAllViews()
            holders.clear()
            if (items.isEmpty()) {
                val e = TextView(this)
                e.text = "🎮 مفيش مؤقتات لسه\nضيف أول حساب من فوق"
                e.gravity = Gravity.CENTER
                e.textSize = 16f
                e.setTextColor(cGray)
                e.setPadding(0, dp(40), 0, 0)
                list.addView(e)
            }
            items.sortedBy { it.endAt }.forEach { addCard(it) }
            update()
        } catch (e: Throwable) { showError(android.util.Log.getStackTraceString(e)) }
    }

    private fun addCard(it0: Item) {
        val card = panel()
        card.setPadding(dp(18), dp(14), dp(18), dp(14))
        card.cameraDistance = resources.displayMetrics.density * 8000f

        val nm = TextView(this)
        nm.text = "🎮 " + it0.name
        nm.textSize = 18f
        nm.typeface = Typeface.DEFAULT_BOLD
        nm.setTextColor(Color.WHITE)
        val end = TextView(this)
        end.textSize = 13f
        end.setTextColor(cGray)
        val time = TextView(this)
        time.textSize = 34f
        time.typeface = Typeface.MONOSPACE
        time.gravity = Gravity.CENTER
        time.setShadowLayer(8f, 0f, 6f, Color.BLACK)
        time.setPadding(0, dp(8), 0, dp(8))
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        bar.max = 1000
        bar.progressTintList = ColorStateList.valueOf(cGold)
        bar.progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#3A4070"))

        val restart = btn("🔁 ابدأ تاني", "#2EC4B6")
        restart.setOnClickListener {
            it0.endAt = System.currentTimeMillis() + it0.durMs
            save(); schedule(it0); rebuild()
        }
        val del = btn("🗑 حذف", "#E5484D")
        del.setOnClickListener {
            cancel(it0); items.remove(it0); save(); rebuild()
        }
        val btns = LinearLayout(this)
        btns.orientation = LinearLayout.HORIZONTAL
        val p1 = LinearLayout.LayoutParams(0, dp(48), 1f)
        p1.marginEnd = dp(6)
        btns.addView(restart, p1)
        btns.addView(del, LinearLayout.LayoutParams(0, dp(48), 1f))

        card.addView(nm)
        card.addView(end)
        card.addView(time)
        card.addView(bar, LinearLayout.LayoutParams(-1, dp(8)))
        card.addView(btns, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(4) })
        holders.add(Holder(it0, card, end, time, bar, restart, it0.endAt <= System.currentTimeMillis()))

        if (it0.id == lastAddedId) {
            card.alpha = 0f
            card.translationY = dp(30).toFloat()
            card.animate().alpha(1f).translationY(0f).setDuration(350).start()
            lastAddedId = -1
        }
    }

    private fun update() {
        try {
            val now = System.currentTimeMillis()
            holders.forEach { h ->
                val left = h.item.endAt - now
                h.end.text = "⏰ يخلص: " + fmt.format(Instant.ofEpochMilli(h.item.endAt).atZone(zone))
                val ready = left <= 0
                val bg = h.card.background as GradientDrawable
                if (ready) {
                    h.time.text = "جاهز للاستلام ✅"
                    h.time.textSize = 24f
                    h.time.setTextColor(Color.WHITE)
                    h.bar.progress = 1000
                    bg.setColor(cGreen)
                } else {
                    val t = left / 1000
                    h.time.text = String.format("%02d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60)
                    h.time.textSize = 34f
                    val soon = left < 60_000
                    h.time.setTextColor(if (soon) Color.parseColor("#FF6B6B") else cGold)
                    bg.setColor(if (soon) cRed else cCard)
                    h.bar.progress = (((h.item.durMs - left) * 1000) / h.item.durMs).toInt().coerceIn(0, 1000)
                }
                h.restart.visibility = if (ready) View.VISIBLE else View.GONE
                if (ready && !h.ready) {
                    h.card.animate().scaleX(1.04f).scaleY(1.04f).setDuration(180).withEndAction {
                        h.card.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
                    }.start()
                }
                h.ready = ready
            }
        } catch (e: Throwable) {
            handler.removeCallbacks(tick)
            showError(android.util.Log.getStackTraceString(e))
        }
    }

    private fun pi(it: Item): PendingIntent = PendingIntent.getBroadcast(
        this, it.id, Intent(this, AlarmReceiver::class.java).putExtra("name", it.name).putExtra("id", it.id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun schedule(it: Item) {
        val am = getSystemService(AlarmManager::class.java)
        val show = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(it.endAt, show), pi(it))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.endAt, pi(it))
        }
    }

    private fun cancel(it: Item) {
        getSystemService(AlarmManager::class.java).cancel(pi(it))
        getSystemService(NotificationManager::class.java).cancel(it.id)
    }

    private fun save() {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("id", it.id).put("n", it.name).put("d", it.durMs).put("e", it.endAt)) }
        getSharedPreferences("t", MODE_PRIVATE).edit().putString("items", a.toString()).apply()
    }

    private fun load() {
        val s = getSharedPreferences("t", MODE_PRIVATE).getString("items", "[]") ?: "[]"
        val a = JSONArray(s)
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            items.add(Item(o.getInt("id"), o.getString("n"), o.getLong("d"), o.getLong("e")))
        }
    }
}