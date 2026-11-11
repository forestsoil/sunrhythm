package app.sunrhythm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val kind = Kind.valueOf(i.getStringExtra("kind") ?: return)
        val at = i.getLongExtra("at", 0L)
        Scheduler.schedule(c, maxOf(at, System.currentTimeMillis()))
        val s = Settings.load(c)
        when (kind) {
            Kind.WAKE -> Notifier.alert(c, 11, "Wake up", "Sunrise soon. Get up.")
            Kind.SLEEP -> Notifier.alert(c, 12, "Go to sleep", "Bedtime for ${s.sleepMin / 60}h ${s.sleepMin % 60}m of sleep.")
            Kind.WALK -> Notifier.alert(c, 13, "Evening walk", "Sunset in ${s.walkBefore} min. Go for a walk.")
            Kind.HOURLY -> {
                val until = Schedule.sleepDeadline(s, at) ?: return
                c.startForegroundService(
                    Intent(c, NudgeService::class.java)
                        .putExtra("until", until).putExtra("req", s.walkReq)
                )
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Scheduler.schedule(c)
}
