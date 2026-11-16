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
    private val fmt = DateTimeFormatter.ofPattern("hh:mm a (d/M)")
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
        Pair("#F783AC", "🌸 وردي"), Pair("#22D3EE", "💧 سماوي")
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
                MotionEvent.ACTION_DOWN ->
                    v.animate().translationY(dp(3).toFloat()).setDuration(60).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().translationY(0f).setDuration(100).start()
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
                val cm = getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("link", url))
                Toast.makeText(
                    this,
                    "التطبيق ما فتحش الرابط مباشرة، اتنسخ الرابط، الصقه فيه",
                    Toast.LENGTH_LONG
                ).show()
            } catch (_: Throwable) {}
            openGame()
        }
    }

    private fun gamePkg(): String =
        getSharedPreferences("t", MODE_PRIVATE).getString("game", "com.wan.wartuneh5") ?: ""

    private fun gameName(): String = try {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(gamePkg(), 0)
        ).toString()
    } catch (_: Throwable) { "مش محدد" }

    private fun refreshGameUi() { gameLabel.text = "🎮 التطبيق: " + gameName() }

    private fun openGame() {
        val i = packageManager.getLaunchIntentForPackage(gamePkg())
        if (i == null) {
            Toast.makeText(this, "تطبيق اللعبة مش موجود، اختاره من لوحة 🔊", Toast.LENGTH_LONG).show()
            return
        }
        try { startActivity(i) } catch (e: Throwable) {
            showError(android.util.Log.getStackTraceString(e))
        }
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
                    getSharedPreferences("t", MODE_PRIVATE).edit()
                        .putString("game", apps[k].second).apply()
                    refreshGameUi()
                }.show()
        } catch (e: Throwable) {
            showError(android.util.Log.getStackTraceString(e))
        }
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
        return if (s.isNotEmpty()) Uri.parse(s)
        else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
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
            i.putExtra(
                RingtoneManager.EXTRA_RINGTONE_TYPE,
                RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE or
                    RingtoneManager.TYPE_NOTIFICATION
            )
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "اختار نغمة المنبّه")
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, toneUri())
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            startActivityForResult(i, 77)
        } catch (e: Throwable) {
            showError(android.util.Log.getStackTraceString(e))
        }
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 77 && res == RESULT_OK) {
            val u: Uri? = data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            getSharedPreferences("t", MODE_PRIVATE).edit()
                .putString("tone", u?.toString() ?: "").apply()
            refreshToneUi()
        }
    }

    private fun testSound() {
        try {
            testR?.stop()
            val r = RingtoneManager.getRingtone(this, toneUri())
            r.audioAttributes =
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            r.play()
            testR = r
            handler.postDelayed({ try { r.stop() } catch (_: Throwable) {} }, 3000)
        } catch (e: Throwable) {
            showError(android.util.Log.getStackTraceString(e))
        }
    }

    private fun stopSound() {
        try { testR?.stop() } catch (_: Throwable) {}
        try {
            startService(Intent(this, AlarmService::class.java).setAction("STOP"))
        } catch (_: Throwable) {}
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

        // ---- sound panel
        soundPanel = panel()
        soundPanel.visibility = View.GONE
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
        soundPanel.addView(srow)

        val toneBtn = btn("🎵 اختيار النغمة", "#7C5CFF")
        toneBtn.textSize = 13f
        gradBtn = btn("", "#2EC4B6")
        gradBtn.textSize = 13f
        toneLabel = TextView(this)
        toneLabel.setTextColor(cGray)
        toneLabel.textSize = 12f
        toneLabel.setPadding(0, dp(8), 0, 0)
        val row2 = LinearLayout(this)
        row2.orientation = LinearLayout.HORIZONTAL
        val q1 = LinearLayout.LayoutParams(0, dp(48), 1f)
        q1.marginEnd = dp(6)
        row2.addView(toneBtn, q1)
        row2.addView(gradBtn, LinearLayout.LayoutParams(0, dp(48), 1f))
        soundPanel.addView(row2, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        soundPanel.addView(toneLabel)
        toneBtn.setOnClickListener { pickTone() }
        gradBtn.setOnClickListener {
            gradual = !gradual
            getSharedPreferences("t", MODE_PRIVATE).edit().putBoolean("gradual", gradual).apply()
            refreshToneUi()
        }
        refreshToneUi()

        val gameBtn = btn("🎮 اختيار تطبيق اللعبة", "#FF922B")
        gameBtn.textSize = 13f
        gameLabel = TextView(this)
        gameLabel.setTextColor(cGray)
        gameLabel.textSize = 12f
        gameLabel.setPadding(0, dp(8), 0, 0)
        soundPanel.addView(gameBtn, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        soundPanel.addView(gameLabel)
        gameBtn.setOnClickListener { pickGame() }
        refreshGameUi()

        val vl = TextView(this)
        vl.text = "🔊 مستوى صوت المنبّه"
        vl.setTextColor(cGray)
        vl.setPadding(0, dp(10), 0, 0)
        soundPanel.addView(vl)
        val am = getSystemService(AudioManager::class.java)
        val seek = SeekBar(this)
        seek.max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        seek.progress = am.getStreamVolume(AudioManager.STREAM_ALARM)
        seek.progressTintList = ColorStateList.valueOf(cGold)
        seek.thumbTintList = ColorStateList.valueOf(cGold)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    try { am.setStreamVolume(AudioManager.STREAM_ALARM, p, 0) } catch (_: Throwable) {}
                }
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) { testSound() }
        })
        soundPanel.addView(seek)
        soundToggle.setOnClickListener {
            soundPanel.visibility =
                if (soundPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

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

    private fun rebuild() {
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

    private fun addCard(it0: Item) {
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

        val restart = btn("🔁 ابدأ تاني", "#2EC4B6")
        restart.textSize = 13f
        restart.setOnClickListener {
            it0.endAt = System.currentTimeMillis() + it0.durMs
            save(); schedule(it0); rebuild()
        }
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

    private fun schedule(x: Item) = Scheduler.schedule(this, x.id, x.name, x.endAt)

    private fun cancel(x: Item) {
        Scheduler.cancel(this, x.id, x.name)
        getSystemService(NotificationManager::class.java).cancel(x.id)
    }

    private fun save() {
        val a = JSONArray()
        items.forEach {
            a.put(JSONObject().put("id", it.id).put("n", it.name).put("d", it.durMs).put("e", it.endAt))
        }
        getSharedPreferences("t", MODE_PRIVATE).edit().putString("items", a.toString()).apply()
        TimerWidget.refresh(this)
    }

    private fun load() {
        val s = getSharedPreferences("t", MODE_PRIVATE).getString("items", "[]") ?: "[]"
        val a = JSONArray(s)
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            items.add(Item(o.getInt("id"), o.getString("n"), o.getLong("d"), o.getLong("e")))
        }
    }

    private fun saveAccounts() {
        val a = JSONArray()
        accounts.forEach { a.put(it) }
        getSharedPreferences("t", MODE_PRIVATE).edit().putString("accounts", a.toString()).apply()
    }

    private fun loadAccounts() {
        val s = getSharedPreferences("t", MODE_PRIVATE).getString("accounts", "[]") ?: "[]"
        val a = JSONArray(s)
        for (i in 0 until a.length()) accounts.add(a.getString(i))
    }

    private fun saveLinks() {
        val o = JSONObject()
        links.forEach { (k, v) -> o.put(k, v) }
        getSharedPreferences("t", MODE_PRIVATE).edit().putString("links", o.toString()).apply()
    }

    private fun loadLinks() {
        val o = JSONObject(getSharedPreferences("t", MODE_PRIVATE).getString("links", "{}") ?: "{}")
        o.keys().forEach { links[it] = o.getString(it) }
    }

    private fun saveColors() {
        val o = JSONObject()
        colors.forEach { (k, v) -> o.put(k, v) }
        getSharedPreferences("t", MODE_PRIVATE).edit().putString("colors", o.toString()).apply()
    }

    private fun loadColors() {
        val o = JSONObject(getSharedPreferences("t", MODE_PRIVATE).getString("colors", "{}") ?: "{}")
        o.keys().forEach { colors[it] = o.getInt(it) }
    }
}
