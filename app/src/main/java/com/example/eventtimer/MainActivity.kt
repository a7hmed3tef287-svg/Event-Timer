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

class MainActivity : Activity(), SensorEventListener {
    data class Item(val id: Int, val name: String, val durMs: Long, var endAt: Long)
    private class Holder(
        val item: Item, val card: LinearLayout, val end: TextView, val time: TextView,
        val bar: ProgressBar, val restart: Button, var ready: Boolean
    )

    private val items = mutableListOf<Item>()
    private val accounts = mutableListOf<String>()
    private val colors = HashMap<String, Int>()
    private val holders = mutableListOf<Holder>()
    private lateinit var list: LinearLayout
    private lateinit var soundBtn: Button
    private lateinit var nameF: EditText
    private lateinit var hhF: EditText
    private lateinit var mmF: EditText
    private lateinit var accRow: LinearLayout
    private lateinit var addPanel: LinearLayout
    private lateinit var soundPanel: LinearLayout
    private lateinit var addToggle: TextView
    private lateinit var soundToggle: TextView
    private lateinit var gradBtn: Button
    private lateinit var toneLabel: TextView
    private var gradual = true
    private val links = HashMap<String, String>()
    private lateinit var gameLabel: TextView
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

    private val palette = listOf(
        Pair("#FF6B6B", "🔴 أحمر"), Pair("#4DABF7", "🔵 أزرق"),
        Pair("#51CF66", "🟢 أخضر"), Pair("#FFD43B", "🟡 أصفر"),
        Pair("#B197FC", "🟣 بنفسجي"), Pair("#FF922B", "🟠 برتقالي"),
        Pair("#F783AC", "🌸 وردي"), Pair("#22D3EE", "🩵 سماوي")
    )

    private fun colorOf(n: String): Int =
        colors[n] ?: Color.parseColor(palette[Math.abs(n.hashCode() % palette.size)].first)

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

    private fun chip(t: String, bg: Int): TextView {
        val c = TextView(this)
        c.text = t
        c.setTextColor(Color.WHITE)
        c.textSize = 13f
        c.typeface = Typeface.DEFAULT_BOLD
        c.gravity = Gravity.CENTER
        c.setPadding(dp(14), dp(8), dp(14), dp(8))
        c.background = rounded(bg, 20)
        return c
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

    private fun setAddOpen(o: Boolean) {
        addPanel.visibility = if (o) View.VISIBLE else View.GONE
        addToggle.text = if (o) "✖" else "➕"
    }

    private fun refreshSoundBtn() {
        soundBtn.text = if (soundOn) "🔔 الصوت شغال" else "🔕 الصوت مقفول"
        style3d(soundBtn, Color.parseColor(if (soundOn) "#2EC4B6" else "#6B7280"))
        soundToggle.text = if (soundOn) "🔊" else "🔇"
    }

    private fun pickColor(n: String) {
        AlertDialog.Builder(this).setTitle("لون: $n")
            .setItems(palette.map { it.second }.toTypedArray()) { _, i ->
                colors[n] = Color.parseColor(palette[i].first)
                saveColors(); refreshAccounts(); rebuild()
            }.show()
    }

    private fun accountMenu(n: String) {
        AlertDialog.Builder(this).setTitle(n)
            .setItems(arrayOf("🎨 تغيير اللون", "🔗 ضبط الرابط", "🗑 حذف من القائمة")) { _, i ->
                when (i) {
                    0 -> pickColor(n)
                    1 -> setLink(n)
                    else -> { accounts.remove(n); saveAccounts(); refreshAccounts() }
                }
            }.show()
    }

    private fun setLink(n: String) {
        val e = EditText(this)
        e.setText(links[n] ?: "")
        e.hint = "https://..."
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        e.setPadding(dp(20), dp(12), dp(20), dp(12))
        AlertDialog.Builder(this).setTitle("رابط $n").setView(e)
            .setPositiveButton("حفظ") { _, _ ->
                val v = e.text.toString().trim()
                if (v.isEmpty()) links.remove(n) else links[n] = v
                saveLinks(); rebuild()
            }
            .setNegativeButton("إلغاء", null).show()
    }

    private fun fixUrl(s: String): String = if (s.contains("://")) s else "https://$s"

    private fun openLink(n: String) {
        val l = links[n]
        if (l.isNullOrBlank()) { openGame(); return }
        val url = fixUrl(l.trim())
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            i.setPackage(gamePkg())
            startActivity(i)
        } catch (e: Throwable) {
            try {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("link", url))
                Toast.makeText(this, "التطبيق ما فتحش الرابط مباشرة، اتنسخ الرابط، الصقه فيه", Toast.LENGTH_LONG).show()
            } catch (_: Throwable) {}
            openGame()
        }
    }

    private fun gamePkg(): String =
        getSharedPreferences("t", MODE_PRIVATE).getString("game", "com.wan.wartuneh5") ?: ""

    private fun gameName(): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(gamePkg(), 0)).toString()
    } catch (_: Throwable) { "مش متحدد" }

    private fun refreshGameUi() { gameLabel.text = "🎮 التطبيق: " + gameName() }

    private fun openGame() {
        val i = packageManager.getLaunchIntentForPackage(gamePkg())
        if (i == null) {
            Toast.makeText(this, "تطبيق اللعبة مش موجود، اختاره من لوحة 🔊", Toast.LENGTH_LONG).show()
            return
        }
        try { startActivity(i) } catch (e: Throwable) { showError(android.util.Log.getStackTraceString(e)) }
    }

    private fun pickGame() {
        try {
            val pm = packageManager
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(q, 0)
                .map { Pair(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .distinctBy { it.second }
                .sortedBy { it.first.lowercase() }
            AlertDialog.Builder(this).setTitle("اختار تطبيق اللعبة")
                .setItems(apps.map { it.first }.toTypedArray()) { _, k ->
                    getSharedPreferences("t", MODE_PRIVATE).edit().putString("game", apps[k].second).apply()
                    refreshGameUi()
                }.show()
        } catch (e: Throwable) { showError(android.util.Log.getStackTraceString(e)) }
    }

    private fun refreshAccounts() {
        accRow.removeAllViews()
        if (accounts.isEmpty()) {
            val h = TextView(this)
            h.text = "الحسابات المحفوظة هتظهر هنا"
            h.setTextColor(cGray)
            h.textSize = 12f
            accRow.addView(h)
            return
        }
        accounts.toList().forEach { n ->
            val c = chip("", Color.parseColor("#3A4070"))
            val sp = SpannableString("● $n")
            sp.setSpan(ForegroundColorSpan(colorOf(n)), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            c.text = sp
            c.setOnClickListener { nameF.setText(n) }
            c.setOnLongClickListener { accountMenu(n); true }
            accRow.addView(c, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(6) })
        }
    }

    private fun toneUri(): Uri {
        val s = getSharedPreferences("t", MODE_PRIVATE).getString("tone", "") ?: ""
        return if (s.isNotEmpty()) Uri.parse(s) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    }

    private fun toneName(): String = try {
        RingtoneManager.getRingtone(this, toneUri()).getTitle(this)
    } catch (_: Throwable) { "الافتراضية" }

    private fun refreshToneUi() {
        toneLabel.text = "🎵 النغمة: " + toneName()
        gradBtn.text = if (gradual) "📈 رفع تدريجي: شغال" else "📈 رفع تدريجي: مقفول"
        style3d(gradBtn, Color.parseColor(if (gradual) "#2EC4B6" else "#6B7280"))
    }

    private fun pickTone() {
        try {
            val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE or RingtoneManager.TYPE_NOTIFICATION)
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "اختار نغمة المنبّه")
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, toneUri())
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            startActivityForResult(i, 77)
        } catch (e: Throwable) { showError(android.util.Log.getStackTraceString(e)) }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 77 && res == RESULT_OK) {
            val u: Uri? = data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            getSharedPreferences("t", MODE_PRIVATE).edit().putString("tone", u?.toString() ?: "").apply()
            refreshToneUi()
        }
    }

    private fun testSound() {
        try {
            testR?.stop()
            val r = RingtoneManager.getRingtone(this, toneUri())
            r.audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            r.play()
            testR = r
            handler.postDelayed({ try { r.stop() } catch (_: Throwable) {} }, 3000)
        } catch (e: Throwable) { showError(android.util.Log.getStackTraceString(e)) }
    }

    private fun stopSound() {
        try { testR?.stop() } catch (_: Throwable) {}
        try { startService(Intent(this, AlarmService::class.java).setAction("STOP")) } catch (_: Throwable) {}
        getSystemService(NotificationManager::class.java).cancelAll()
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

        // ---- الهيدر
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
        sub.text = "بتوقيت القاهرة 🇪🇬"
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

        // ---- لوحة الإضافة
        addPanel = panel()
        nameF = field("👤 اسم الحساب / الإيميل", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        addPanel.addView(nameF)

        accRow = LinearLayout(this)
        accRow.orientation = LinearLayout.HORIZONTAL
        val accScroll = HorizontalScrollView(this)
        accScroll.isHorizontalScrollBarEnabled = false
        accScroll.addView(accRow)
        addPanel.addView(accScroll, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val quickRow = LinearLayout(this)
        quickRow.orientation = LinearLayout.HORIZONTAL
        val quick = listOf("30 د" to 30, "50 د" to 50, "ساعة" to 60, "ساعة و20" to 80, "ساعتين" to 120)
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
                val mins = (hhF.text.toString().toLongOrNull() ?: 0) * 60 + (mmF.text.toString().toLongOrNull() ?: 0)
                if (n.isEmpty() || mins <= 0) {
                    Toast.makeText(this, "اكتب الاسم والوقت", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val dur = mins * 60_000
                val item = Item((System.currentTimeMillis() % 1_000_000_000).toInt(), n, dur, System.currentTimeMillis() + dur)
                items.add(item); save(); schedule(item)
                if (n !in accounts) { accounts.add(n); saveAccounts(); refreshAccounts() }
                lastAddedId = item.id
                nameF.text.clear(); hhF.text.clear(); mmF.text.clear()
                getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(nameF.windowToken, 0