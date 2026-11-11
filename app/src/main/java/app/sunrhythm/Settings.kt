package app.sunrhythm

import android.content.Context

data class Settings(
    val lat: Double? = null,
    val lon: Double? = null,
    val wakeBefore: Int = 15,   // min before sunrise (0-999)
    val sleepMin: Int = 420,    // sleep duration, min (0-999)
    val walkBefore: Int = 15,   // min before sunset (0-99)
    val walkReq: Int = 5,       // min of walking to stop buzz (5-15)
    val enabled: Boolean = true
) {
    fun save(c: Context) {
        val e = c.getSharedPreferences("s", Context.MODE_PRIVATE).edit()
        if (lat != null && lon != null) {
            e.putString("lat", lat.toString()); e.putString("lon", lon.toString())
        }
        e.putInt("wake", wakeBefore).putInt("sleep", sleepMin)
            .putInt("walk", walkBefore).putInt("req", walkReq).putBoolean("on", enabled).apply()
    }

    companion object {
        fun load(c: Context): Settings {
            val p = c.getSharedPreferences("s", Context.MODE_PRIVATE)
            return Settings(
                p.getString("lat", null)?.toDoubleOrNull(),
                p.getString("lon", null)?.toDoubleOrNull(),
                p.getInt("wake", 15), p.getInt("sleep", 420),
                p.getInt("walk", 15), p.getInt("req", 5), p.getBoolean("on", true)
            )
        }
    }
}
