package it.gangitano.karooridereplay.data

import android.content.Context

/**
 * Durable user settings. The UI writes them; the extension service reads them
 * at scan time. Both run in the same process, so SharedPreferences' in-memory
 * copy keeps them in step.
 */
class ReplaySettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Publish one virtual device per sensor instead of the combined one. */
    var separateSensors: Boolean
        get() = prefs.getBoolean(KEY_SEPARATE_SENSORS, false)
        set(value) = prefs.edit().putBoolean(KEY_SEPARATE_SENSORS, value).apply()

    private companion object {
        const val PREFS = "ride_replay_settings"
        const val KEY_SEPARATE_SENSORS = "separate_sensors"
    }
}
