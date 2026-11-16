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

class MainActivity : CardsActivity(), SensorEventListener {
    private val tick = object : Runnable {
        override fun run() { update(); handler.postDelayed(this, 1000) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val sp = getSharedPreferences("t", MODE_PRIVATE)
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            sp.edit().putString("crash", android.util.Log.getStackTraceString(e)).commit()
            Process.killProcess(Process.myPid())
        }
        soundOn = sp.getBoolean("sound", true)
        gradual = sp.getBoolean("gradual", true)
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        try { load() } catch (e: Throwable) { items.clear() }
        try { loadAccounts() } catch (e: Throwable) { accounts.clear() }
        try { loadColors() } catch (e: Throwable) { colors.clear() }
        try { loadLinks() } catch (e: Throwable) { links.clear() }
        items.forEach { if (it.name !in accounts) accounts.add(it.name) }
        buildUi()
        setAddOpen(items.isEmpty())
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
        TimerWidget.refresh(this)
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

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setPadding(dp(16), dp(40), dp(16), dp(8))
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#0F1226"), Color.parseColor("#1B1F3B"))
        )

        // ---- header
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val tcol = LinearLayout(this)
        tcol.orientation = LinearLayout.VERTICAL
        val title = TextView(this)
        title.text = "⚔️ مؤقت الإيفنت"
        title.textSize = 22f
        title.typeface = Typeface.DEFAULT_BOLD
        title.setTextColor(cGold)
        title.setShadowLayer(10f, 0f, 6f, Color.BLACK)
        val sub = TextView(this)
        sub.text = "🇪🇬 بتوقيت القاهرة"
        sub.textSize = 12f
        sub.setTextColor(cGray)
        tcol.addView(title)
        tcol.addView(sub)
        addToggle = chip("➕", Color.parseColor("#FF7A59"))
        addToggle.textSize = 18f
        soundToggle = chip("🔊", Color.parseColor("#4B3F8F"))
        soundToggle.textSize = 18f
        head.addView(tcol, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(soundToggle, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        head.addView(addToggle, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        root.addView(head)

        // ---- add panel
        addPanel = panel()
        nameF = field(
            "👤 اسم الحساب / الإيميل",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        addPanel.addView(nameF)

        accRow = LinearLayout(this)
        accRow.orientation = LinearLayout.HORIZONTAL
        val accScroll = HorizontalScrollView(this)
        accScroll.isHorizontalScrollBarEnabled = false
        accScroll.addView(accRow)
        addPanel.addView(accScroll, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val quickRow = LinearLayout(this)
        quickRow.orientation = LinearLayout.HORIZONTAL
        val quick = listOf(
            "30 د" to 30, "50 د" to 50, "ساعة" to 60, "ساعة و20" to 80, "ساعتين" to 120
        )
        hhF = field("ساعات", InputType.TYPE_CLASS_NUMBER)
        mmF = field("دقايق", InputType.TYPE_CLASS_NUMBER)
        quick.forEach { (label, m) ->
            val c = chip("⏱ $label", Color.parseColor("#4B3F8F"))
            c.setOnClickListener {
                hhF.setText(if (m / 60 > 0) "${m / 60}" else "")
                mmF.setText(if (m % 60 > 0) "${m % 60}" else "")
            }
            quickRow.addView(c, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        }
        val quickScroll = HorizontalScrollView(this)
        quickScroll.isHorizontalScrollBarEnabled = false
        quickScroll.addView(quickRow)
        addPanel.addView(quickScroll, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val gap = LinearLayout.LayoutParams(0, -2, 1f)
        gap.marginEnd = dp(8)
        row.addView(hhF, gap)
        row.addView(mmF, LinearLayout.LayoutParams(0, -2, 1f))
        addPanel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val add = btn("➕ إضافة وبدء العد", "#FF7A59")
        addPanel.addView(add, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })

        addToggle.setOnClickListener { setAddOpen(addPanel.visibility != View.VISIBLE) }

        add.setOnClickListener {
            try {
                val n = nameF.text.toString().trim()
                val hours = hhF.text.toString().toLongOrNull() ?: 0L
                val minsPart = mmF.text.toString().toLongOrNull() ?: 0L
                val mins = hours * 60L + minsPart
                if (n.isEmpty() || mins <= 0L) {
                    Toast.makeText(this, "اكتب الاسم والوقت", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val dur = mins * 60_000L
                val now = System.currentTimeMillis()
                val item = Item((now % 1_000_000_000L).toInt(), n, dur, now + dur)
                items.add(item); save(); schedule(item)
                if (n !in accounts) { accounts.add(n); saveAccounts(); refreshAccounts() }
                lastAddedId = item.id
                nameF.text.clear(); hhF.text.clear(); mmF.text.clear()
                val imm = getSystemService(InputMethodManager::class.java)
                imm.hideSoftInputFromWindow(nameF.windowToken, 0)
                setAddOpen(false)
                rebuild()
            } catch (e: Throwable) {
                showError(android.util.Log.getStackTraceString(e))
            }
        }

        buildSoundPanel()

        // ---- scrollable content
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.addView(addPanel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        content.addView(soundPanel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        content.addView(list)
        val sv = ScrollView(this)
        sv.clipToPadding = false
        sv.addView(content)
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refreshAccounts()
    }
}
