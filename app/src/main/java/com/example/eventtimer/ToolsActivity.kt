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

abstract class ToolsActivity : BaseActivity() {
    fun pickColor(n: String) {
        AlertDialog.Builder(this).setTitle("لون: $n")
            .setItems(palette.map { it.second }.toTypedArray()) { _, i ->
                colors[n] = Color.parseColor(palette[i].first)
                saveColors(); refreshAccounts(); rebuild()
            }.show()
    }

    fun accountMenu(n: String) {
        AlertDialog.Builder(this).setTitle(n)
            .setItems(arrayOf("🎨 تغيير اللون", "🔗 ضبط الرابط", "🗑 حذف من القائمة")) { _, i ->
                when (i) {
                    0 -> pickColor(n)
                    1 -> setLink(n)
                    else -> { accounts.remove(n); saveAccounts(); refreshAccounts() }
                }
            }.show()
    }

    fun setLink(n: String) {
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

    fun fixUrl(s: String): String = if (s.contains("://")) s else "https://$s"

    fun openLink(n: String) {
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

    fun gamePkg(): String =
        getSharedPreferences("t", MODE_PRIVATE).getString("game", "com.wan.wartuneh5") ?: ""

    fun gameName(): String = try {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(gamePkg(), 0)
        ).toString()
    } catch (_: Throwable) { "مش محدد" }

    fun refreshGameUi() { gameLabel.text = "🎮 التطبيق: " + gameName() }

    fun openGame() {
        val i = packageManager.getLaunchIntentForPackage(gamePkg())
        if (i == null) {
            Toast.makeText(this, "تطبيق اللعبة مش موجود، اختاره من لوحة 🔊", Toast.LENGTH_LONG).show()
            return
        }
        try { startActivity(i) } catch (e: Throwable) {
            showError(android.util.Log.getStackTraceString(e))
        }
    }

    fun pickGame() {
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

    fun refreshAccounts() {
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

    fun toneUri(): Uri {
        val s = getSharedPreferences("t", MODE_PRIVATE).getString("tone", "") ?: ""
        return if (s.isNotEmpty()) Uri.parse(s)
        else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    }

    fun toneName(): String = try {
        RingtoneManager.getRingtone(this, toneUri()).getTitle(this)
    } catch (_: Throwable) { "الافتراضية" }

    fun refreshToneUi() {
        toneLabel.text = "🎵 النغمة: " + toneName()
        gradBtn.text = if (gradual) "📈 رفع تدريجي: شغال" else "📈 رفع تدريجي: مقفول"
        style3d(gradBtn, Color.parseColor(if (gradual) "#2EC4B6" else "#6B7280"))
    }

    fun pickTone() {
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

    fun testSound() {
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

    fun stopSound() {
        try { testR?.stop() } catch (_: Throwable) {}
        try {
            startService(Intent(this, AlarmService::class.java).setAction("STOP"))
        } catch (_: Throwable) {}
        getSystemService(NotificationManager::class.java).cancelAll()
    }

    fun buildSoundPanel() {
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
    }
}
