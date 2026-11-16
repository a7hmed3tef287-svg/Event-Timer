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

data class TimerItem(val id: Int, val name: String, var durMs: Long, var endAt: Long)

class CardHolder(
    val item: TimerItem, val card: LinearLayout, val end: TextView, val time: TextView,
    val bar: ProgressBar, val restart: Button, var ready: Boolean
)

abstract class BaseActivity : Activity() {
    val items = mutableListOf<TimerItem>()

    val accounts = mutableListOf<String>()

    val colors = HashMap<String, Int>()

    val holders = mutableListOf<CardHolder>()

    lateinit var list: LinearLayout

    lateinit var soundBtn: Button

    lateinit var nameF: EditText

    lateinit var hhF: EditText

    lateinit var mmF: EditText

    lateinit var accRow: LinearLayout

    lateinit var addPanel: LinearLayout

    lateinit var soundPanel: LinearLayout

    lateinit var addToggle: TextView

    lateinit var soundToggle: TextView

    lateinit var gradBtn: Button

    lateinit var toneLabel: TextView

    var gradual = true

    val links = HashMap<String, String>()

    lateinit var gameLabel: TextView

    var lastAddedId = -1

    var soundOn = true

    var testR: Ringtone? = null

    var tx = 0f

    var ty = 0f

    val handler = Handler(Looper.getMainLooper())

    val cCard = Color.parseColor("#232846")

    val cField = Color.parseColor("#2E3459")

    val cGold = Color.parseColor("#FFD166")

    val cGray = Color.parseColor("#8890B5")

    val cGreen = Color.parseColor("#1F8A4C")

    val cRed = Color.parseColor("#5A2233")

    val palette = listOf(
        Pair("#FF6B6B", "🔴 أحمر"), Pair("#4DABF7", "🔵 أزرق"),
        Pair("#51CF66", "🟢 أخضر"), Pair("#FFD43B", "🟡 أصفر"),
        Pair("#B197FC", "🟣 بنفسجي"), Pair("#FF922B", "🟠 برتقالي"),
        Pair("#F783AC", "🌸 وردي"), Pair("#22D3EE", "💧 سماوي")
    )

    fun colorOf(n: String): Int =
        colors[n] ?: Color.parseColor(palette[Math.abs(n.hashCode() % palette.size)].first)

    fun showError(msg: String) {
        AlertDialog.Builder(this).setTitle("حصل خطأ").setMessage(msg.take(1500))
            .setPositiveButton("تمام", null).show()
    }

    fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    fun rounded(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat()
    }

    fun darken(c: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[2] *= 0.6f
        return Color.HSVToColor(hsv)
    }

    fun style3d(b: Button, color: Int) {
        val ld = LayerDrawable(arrayOf(rounded(darken(color), 14), rounded(color, 14)))
        ld.setLayerInset(1, 0, 0, 0, dp(5))
        b.background = ld
        b.setPadding(dp(6), 0, dp(6), dp(5))
    }

    fun btn(t: String, hex: String): Button {
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

    fun chip(t: String, bg: Int): TextView {
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

    fun field(h: String, type: Int): EditText {
        val e = EditText(this)
        e.hint = h
        e.inputType = type
        e.setTextColor(Color.WHITE)
        e.setHintTextColor(cGray)
        e.background = rounded(cField, 14)
        e.setPadding(dp(14), dp(12), dp(14), dp(12))
        return e
    }

    fun panel(): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL
        p.setPadding(dp(14), dp(14), dp(14), dp(14))
        val bg = rounded(cCard, 20)
        bg.setStroke(dp(1), Color.parseColor("#3A4070"))
        p.background = bg
        p.elevation = dp(8).toFloat()
        return p
    }

    fun setAddOpen(o: Boolean) {
        addPanel.visibility = if (o) View.VISIBLE else View.GONE
        addToggle.text = if (o) "✖" else "➕"
    }

    fun refreshSoundBtn() {
        soundBtn.text = if (soundOn) "🔔 الصوت شغال" else "🔕 الصوت مقفول"
        style3d(soundBtn, Color.parseColor(if (soundOn) "#2EC4B6" else "#6B7280"))
        soundToggle.text = if (soundOn) "🔊" else "🔇"
    }

    fun save() {
        val a = JSONArray()
        items.forEach {
            a.put(JSONObject().put("id", it.id).put("n", it.name).put("d", it.durMs).put("e", it.endAt))
        }
        getSharedPreferences("t", MODE_PRIVATE).edit().putString("items", a.toString()).apply()
        TimerWidget.refresh(this)
    }

    fun load() {
        val s = getSharedPreferences("t", MODE_PRIVATE).getString("items", "[]") ?: "[]"
        val a = JSONArray(s)
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            items.add(TimerItem(o.getInt("id"), o.getString("n"), o.getLong("d"), o.getLong("e")))
        }
    }

    fun saveAccounts() {
        val a = JSONArray()
        accounts.forEach { a.put(it) }
        getSharedPreferences("t", MODE_PRIVATE).edit().putString("accounts", a.toString()).apply()
    }

    fun loadAccounts() {
        val s = getSharedPreferences("t", MODE_PRIVATE).getString("accounts", "[]") ?: "[]"
        val a = JSONArray(s)
        for (i in 0 until a.length()) accounts.add(a.getString(i))
    }

    fun saveLinks() {
        val o = JSONObject()
        links.forEach { (k, v) -> o.put(k, v) }
        getSharedPreferences("t", MODE_PRIVATE).edit().putString("links", o.toString()).apply()
    }

    fun loadLinks() {
        val o = JSONObject(getSharedPreferences("t", MODE_PRIVATE).getString("links", "{}") ?: "{}")
        o.keys().forEach { links[it] = o.getString(it) }
    }

    fun saveColors() {
        val o = JSONObject()
        colors.forEach { (k, v) -> o.put(k, v) }
        getSharedPreferences("t", MODE_PRIVATE).edit().putString("colors", o.toString()).apply()
    }

    fun loadColors() {
        val o = JSONObject(getSharedPreferences("t", MODE_PRIVATE).getString("colors", "{}") ?: "{}")
        o.keys().forEach { colors[it] = o.getInt(it) }
    }

    abstract fun rebuild()
}
