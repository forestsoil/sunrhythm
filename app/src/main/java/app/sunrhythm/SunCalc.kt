package app.sunrhythm

import java.time.LocalDate
import kotlin.math.*

/** NOAA solar position algorithm. Sunrise/sunset at -0.833 deg. Offline, no network. */
object SunCalc {
    class Sun(val sunrise: Long, val sunset: Long) // epoch millis

    private fun rad(d: Double) = Math.toRadians(d)

    /** Returns (equation of time in minutes, solar declination in radians) at epoch minute. */
    private fun solar(epochMin: Double): Pair<Double, Double> {
        val jd = epochMin / 1440.0 + 2440587.5
        val t = (jd - 2451545.0) / 36525.0
        val l0 = (280.46646 + t * (36000.76983 + t * 0.0003032)) % 360.0
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val c = sin(rad(m)) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
                sin(rad(2 * m)) * (0.019993 - 0.000101 * t) +
                sin(rad(3 * m)) * 0.000289
        val om = 125.04 - 1934.136 * t
        val lam = l0 + c - 0.00569 - 0.00478 * sin(rad(om))
        val sec = 21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))
        val eps = 23.0 + (26.0 + sec / 60.0) / 60.0 + 0.00256 * cos(rad(om))
        val decl = asin(sin(rad(eps)) * sin(rad(lam)))
        val y = tan(rad(eps / 2)).pow(2)
        val eq = 4.0 * Math.toDegrees(
            y * sin(2 * rad(l0)) - 2 * e * sin(rad(m)) +
                    4 * e * y * sin(rad(m)) * cos(2 * rad(l0)) -
                    0.5 * y * y * sin(4 * rad(l0)) - 1.25 * e * e * sin(2 * rad(m))
        )
        return Pair(eq, decl)
    }

    private fun hourAngle(lat: Double, decl: Double): Double? {
        val c = (cos(rad(90.833)) - sin(rad(lat)) * sin(decl)) / (cos(rad(lat)) * cos(decl))
        return if (c > 1.0 || c < -1.0) null else Math.toDegrees(acos(c))
    }

    /** Sunrise/sunset for the given local date at lat/lon; null in polar day/night. */
    fun sun(date: LocalDate, lat: Double, lon: Double): Sun? {
        val base = date.toEpochDay() * 1440.0
        fun solve(sign: Int): Double? {
            var t = base + 720.0 - 4.0 * lon
            repeat(3) {
                val (eq, decl) = solar(t)
                val h = hourAngle(lat, decl) ?: return null
                t = base + 720.0 - 4.0 * (lon + sign * h) - eq
            }
            return t
        }
        val rise = solve(+1) ?: return null
        val set = solve(-1) ?: return null
        return Sun((rise * 60000.0).toLong(), (set * 60000.0).toLong())
    }
}
