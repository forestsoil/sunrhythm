package app.sunrhythm

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {
    private var s = Settings()
    private lateinit var locText: TextView
    private lateinit var sunText: TextView
    private lateinit var planText: TextView
    private lateinit var wakeIn: EditText
    private lateinit var sleepIn: EditText
    private lateinit var walkIn: EditText
    private lateinit var reqIn: EditText
    private lateinit var on: Switch
    private val dt = DateTimeFormatter.ofPattern("EEE d MMM HH:mm")
    private val tm = DateTimeFormatter.ofPattern("HH:mm")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun tv(t: String, sp: Float = 16f, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = sp; if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(6), 0, dp(6))
    }
    private fun num(v: Int) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER; setText(v.toString()); gravity = Gravity.END
        layoutParams = LinearLayout.LayoutParams(dp(90), LinearLayout.LayoutParams.WRAP_CONTENT)
    }
    private fun row(label: String, f: EditText) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(tv(label).apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        addView(f)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        s = Settings.load(this)
        locText = tv(""); sunText = tv(""); planText = tv("", 15f)
        wakeIn = num(s.wakeBefore); sleepIn = num(s.sleepMin); walkIn = num(s.walkBefore); reqIn = num(s.walkReq)
        on = Switch(this).apply { text = "Alarms on"; isChecked = s.enabled; textSize = 16f }

        val locBtn = Button(this).apply { text = "Get location"; setOnClickListener { getLocation() } }
        val saveBtn = Button(this).apply { text = "Save & schedule"; setOnClickListener { save() } }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(28), dp(20), dp(28))
            addView(tv("SunRhythm", 26f, true))
            addView(locText); addView(locBtn); addView(sunText)
            addView(row("Wake: min before sunrise (0-999)", wakeIn))
            addView(row("Sleep duration, min (0-999)", sleepIn))
            addView(row("Walk: min before sunset (0-99)", walkIn))
            addView(row("Walk to stop buzz, min (5-15)", reqIn))
            addView(on); addView(saveBtn); addView(planText)
        }
        setContentView(ScrollView(this).apply { addView(col) })
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        Notifier.channels(this)
        Scheduler.schedule(this)
        refresh()
    }

    private fun clamp(f: EditText, def: Int, lo: Int, hi: Int): Int {
        val v = (f.text.toString().toIntOrNull() ?: def).coerceIn(lo, hi)
        f.setText(v.toString()); return v
    }

    private fun save() {
        s = s.copy(
            wakeBefore = clamp(wakeIn, 15, 0, 999), sleepMin = clamp(sleepIn, 420, 0, 999),
            walkBefore = clamp(walkIn, 15, 0, 99), walkReq = clamp(reqIn, 5, 5, 15),
            enabled = on.isChecked
        )
        s.save(this); Scheduler.schedule(this); refresh()
    }

    private fun getLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), 2)
            return
        }
        val lm = getSystemService(LocationManager::class.java)
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { lm.isProviderEnabled(it) }
        fun lastKnown(): Location? = try {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        } catch (e: SecurityException) { null }
        if (provider == null) { applyLocation(lastKnown()); return }
        locText.text = "Locating..."
        try {
            lm.getCurrentLocation(provider, null, mainExecutor) { loc -> applyLocation(loc ?: lastKnown()) }
        } catch (e: SecurityException) { applyLocation(lastKnown()) }
    }

    private fun applyLocation(l: Location?) {
        if (l == null) { Toast.makeText(this, "No location. Turn on Location and retry.", Toast.LENGTH_LONG).show(); refresh(); return }
        s = s.copy(lat = l.latitude, lon = l.longitude)
        s.save(this); Scheduler.schedule(this); refresh()
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        if (code == 2 && r.any { it == PackageManager.PERMISSION_GRANTED }) getLocation()
    }

    private fun refresh() {
        val z = ZoneId.systemDefault()
        fun t(ms: Long, f: DateTimeFormatter = dt) = f.format(Instant.ofEpochMilli(ms).atZone(z))
        val lat = s.lat; val lon = s.lon
        if (lat == null || lon == null) {
            locText.text = "Location: not set"; sunText.text = ""; planText.text = ""; return
        }
        locText.text = "Location: %.4f, %.4f".format(lat, lon)
        val sun = SunCalc.sun(LocalDate.now(), lat, lon)
        sunText.text = if (sun == null) "No sunrise/sunset today (polar)."
        else "Today  sunrise ${t(sun.sunrise, tm)}   sunset ${t(sun.sunset, tm)}"
        val now = System.currentTimeMillis()
        val ev = Schedule.events(s).filter { it.at > now }
        fun nx(k: Kind) = ev.firstOrNull { it.kind == k }?.let { t(it.at) } ?: "-"
        planText.text = if (!s.enabled) "Alarms off." else
            "Next wake:   ${nx(Kind.WAKE)}\nNext sleep:  ${nx(Kind.SLEEP)}\n" +
            "Next walk:   ${nx(Kind.WALK)}\nNext buzz:   ${nx(Kind.HOURLY)}"
    }
}
