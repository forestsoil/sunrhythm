package app.sunrhythm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDate

enum class Kind { WAKE, HOURLY, SLEEP, WALK }
data class Event(val at: Long, val kind: Kind)

object Schedule {
    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN

    /**
     * WAKE  = sunrise(d) - wakeBefore
     * WALK  = sunset(d)  - walkBefore
     * SLEEP = wake(d+1)  - sleepMin   (bedtime that yields sleepMin of sleep before next wake)
     * HOURLY = wake(d)+1h, +2h ... while < SLEEP
     */
    fun events(s: Settings, today: LocalDate = LocalDate.now()): List<Event> {
        val lat = s.lat ?: return emptyList()
        val lon = s.lon ?: return emptyList()
        val sun = (-1..4).associateWith { SunCalc.sun(today.plusDays(it.toLong()), lat, lon) }
        val out = ArrayList<Event>()
        for (d in -1..3) {
            val cur = sun[d] ?: continue
            val wake = cur.sunrise - s.wakeBefore * MIN
            out += Event(wake, Kind.WAKE)
            out += Event(cur.sunset - s.walkBefore * MIN, Kind.WALK)
            val nxt = sun[d + 1] ?: continue
            val sleep = nxt.sunrise - s.wakeBefore * MIN - s.sleepMin * MIN
            out += Event(sleep, Kind.SLEEP)
            var t = wake + HOUR
            while (t < sleep) { out += Event(t, Kind.HOURLY); t += HOUR }
        }
        return out.sortedBy { it.at }
    }

    fun next(s: Settings, after: Long): Event? = events(s).firstOrNull { it.at > after }

    fun sleepDeadline(s: Settings, after: Long): Long? =
        events(s).firstOrNull { it.kind == Kind.SLEEP && it.at > after }?.at
}

object Scheduler {
    private fun pending(c: Context, e: Event?): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java)
        if (e != null) i.putExtra("kind", e.kind.name).putExtra("at", e.at)
        return PendingIntent.getBroadcast(
            c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Arms a single alarm for the next event after [after]. Each firing re-arms the next. */
    fun schedule(c: Context, after: Long = System.currentTimeMillis()) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(pending(c, null))
        val s = Settings.load(c)
        if (!s.enabled) return
        val ev = Schedule.next(s, after) ?: return
        val show = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        // setAlarmClock: exact, Doze-exempt, needs no special permission.
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(ev.at, show), pending(c, ev))
        } catch (x: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ev.at, pending(c, ev))
        }
    }
}
