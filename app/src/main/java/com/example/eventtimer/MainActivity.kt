package com.example.eventtimer

import android.Manifest
import android.app.*
import android.content.*
import android.graphics.Color
import android.os.*
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {
    data class Item(val id: Int, val name: String, val durMs: Long, var endAt: Long)

    private val items = mutableListOf<Item>()
    private lateinit var list: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val zone = ZoneId.of("Africa/Cairo")
    private val fmt = DateTimeFormatter.ofPattern("hh:mm a  (d/M)")
    private val tick = object : Runnable {
        override fun run() { render(); handler.postDelayed(this, 1000) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        load()
        buildUi()
    }

    override fun onResume() { super.onResume(); handler.post(tick) }
    override fun onPause() { super.onPause(); handler.removeCallbacks(tick) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(40), dp(16), dp(16))
        }
        val name = EditText(this).apply { hint = "اسم الحساب / الإيميل"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val hh = EditText(this).apply { hint = "ساعات"; inputType = InputType.TYPE_CLASS_NUMBER }
        val mm = EditText(this).apply { hint = "دقايق"; inputType = InputType.TYPE_CLASS_NUMBER }
        row.addView(hh, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(mm, LinearLayout.LayoutParams(0, -2, 1f))
        val add = Button(this).apply { text = "إضافة وبدء العد" }
        add.setOnClickListener {
            val n = name.text.toString().trim()
            val mins = (hh.text.toString().toLongOrNull() ?: 0) * 60 + (mm.text.toString().toLongOrNull() ?: 0)
            if (n.isEmpty() || mins <= 0) { Toast.makeText(this, "اكتب الاسم والوقت", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val dur = mins * 60_000
            val it = Item((System.currentTimeMillis() % 1_000_000_000).toInt(), n, dur, System.currentTimeMillis() + dur)
            items.add(it); schedule(it); save(); name.text.clear(); hh.text.clear(); mm.text.clear(); render()
        }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sv = ScrollView(this).apply { addView(list) }
        root.addView(name); root.addView(row); root.addView(add); root.addView(sv)
        setContentView(root)
    }

    private fun render() {
        list.removeAllViews()
        val now = System.currentTimeMillis()
        items.sortedBy { it.endAt }.forEach { it0 ->
            val left = it0.endAt - now
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setBackgroundColor(if (left <= 0) Color.parseColor("#1F6B3A") else Color.parseColor("#2A2A2A"))
            }
            val lp = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
            card.addView(TextView(this).apply { text = it0.name; textSize = 18f })
            val endStr = fmt.format(Instant.ofEpochMilli(it0.endAt).atZone(zone))
