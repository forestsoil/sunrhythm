package app.sunrhythm

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.sqrt

/**
 * Buzzes (400 ms on / 1600 ms off) until the phone has been carried while walking for [reqMs].
 * Walking = >= 4 accelerometer step peaks in the last 6 s (counted only in the vibration-off
 * windows, so the motor's own shake is ignored). Gives up at the sleep deadline.
 */
class NudgeService : Service(), SensorEventListener {
    private val h = Handler(Looper.getMainLooper())
    private var started = false
    private var until = 0L
    private var reqMs = 5 * 60_000L
    private var walked = 0L
    private var ticks = 0
    private var vibStart = 0L
    private var lp = 9.81f
    private var lastStep = 0L
    private val steps = ArrayDeque<Long>()
    private var wl: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        until = i?.getLongExtra("until", 0L) ?: 0L
        if (started) return START_NOT_STICKY // already nudging; keep progress, new deadline
        started = true
        reqMs = (i?.getIntExtra("req", 5) ?: 5) * 60_000L
        Notifier.channels(this)
        val n = Notifier.build(this, Notifier.NUDGE, "Move!", "Walk around with your phone: 0:00 / ${fmt(reqMs)}")
        if (Build.VERSION.SDK_INT >= 34) startForeground(21, n, 1 shl 30) // FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        else startForeground(21, n)

        wl = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sunrhythm:nudge").apply { acquire(70 * 60_000L) }
        vibStart = SystemClock.elapsedRealtime()
        vibrator().vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 400, 1600), 0),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        )
        val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
        h.postDelayed(tick, 1000)
        return START_NOT_STICKY
    }

    private fun vibrator(): Vibrator =
        if (Build.VERSION.SDK_INT >= 31)
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)

    private fun fmt(ms: Long) = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)

    override fun onSensorChanged(e: SensorEvent) {
        val t = SystemClock.elapsedRealtime()
        if ((t - vibStart) % 2000 < 600) return // motor on (+guard): ignore
        val mag = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
        lp += 0.05f * (mag - lp)
        if (mag - lp > 1.6f && t - lastStep > 280) { lastStep = t; steps.addLast(t) }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            while (steps.isNotEmpty() && now - steps.first() > 6000) steps.removeFirst()
            if (steps.size >= 4) walked += 1000
            if (walked >= reqMs) return finish(true)
            if (System.currentTimeMillis() >= until) return finish(false)
            if (++ticks % 5 == 0) {
                getSystemService(NotificationManager::class.java).notify(
                    21, Notifier.build(this@NudgeService, Notifier.NUDGE, "Move!",
                        "Keep walking: ${fmt(walked)} / ${fmt(reqMs)}")
                )
            }
            h.postDelayed(this, 1000)
        }
    }

    private fun finish(done: Boolean) {
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (done) Notifier.quiet(this, 22, "Done", "Walk complete. Buzzing stopped.")
        stopSelf()
    }

    private fun cleanup() {
        h.removeCallbacks(tick)
        vibrator().cancel()
        (getSystemService(Context.SENSOR_SERVICE) as SensorManager).unregisterListener(this)
        wl?.let { if (it.isHeld) it.release() }
        wl = null
    }

    override fun onDestroy() { cleanup(); started = false; super.onDestroy() }
}
