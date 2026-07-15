package it.gangitano.karooridereplay.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** `1:24:35` — the timeline / hero clock form. */
internal fun formatHHMMSS(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%d:%02d:%02d".format(h, m, s)
}

/** `Mon 12 Aug · 12:03` — the ride row's human write-time (primary line). */
internal fun formatWriteTime(epochMs: Long): String =
    WRITE_TIME_FORMAT.format(Date(epochMs))

private val WRITE_TIME_FORMAT = SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault())

/** `48.3 km` — parsed ride distance, or `—` when the FIT carried none. */
internal fun formatDistance(meters: Double?): String =
    if (meters == null) "—" else "%.1f km".format(meters / 1000.0)

/**
 * Middle-truncate a GUID filename to `8f3a1c9e…0b3d61`. Short names pass through.
 */
internal fun truncateGuid(name: String): String =
    if (name.length <= 16) name else "${name.take(8)}…${name.takeLast(6)}"
