package io.github.karooridereplay.replay

/**
 * One row of recorded ride data, lifted out of a FIT file's `record` message.
 *
 * Fields are nullable where the FIT message may genuinely omit them (e.g., a
 * rider without a power meter has no `power` field; a ride started without
 * GPS lock has no `position*` until lock acquires). Consumers should treat
 * null as "sensor unavailable at this tick," not "value is zero."
 *
 * Units match Karoo-extension convention:
 *   - lat/lng:    degrees (positive N / E)
 *   - altitude:   meters above sea level
 *   - speed:      meters per second
 *   - power:      watts
 *   - heartRate:  beats per minute
 *   - cadence:    revolutions per minute
 *   - distance:   meters from ride start (cumulative)
 *   - grade:      percent (already-decoded from FIT's signed-int grade if present)
 *   - temperature:degrees Celsius
 *   - timestampMs:wall-clock milliseconds since Unix epoch (1970-01-01 UTC)
 *
 * The size is ~80 bytes per record. A 5-hour ride at 1 Hz = 18000 records =
 * ~1.4 MB resident — acceptable on Karoo memory.
 */
data class FitRecord(
    val timestampMs: Long,
    val lat: Double? = null,
    val lng: Double? = null,
    val altitude: Double? = null,
    val speed: Double? = null,
    val power: Int? = null,
    val heartRate: Int? = null,
    val cadence: Int? = null,
    val distance: Double? = null,
    val grade: Double? = null,
    val temperature: Int? = null
) {
    /** True if this record has a usable GPS fix. */
    val hasPosition: Boolean get() = lat != null && lng != null

    /** Time elapsed (seconds) from [startTimestampMs] to this record. */
    fun elapsedSeconds(startTimestampMs: Long): Long =
        (timestampMs - startTimestampMs) / 1000L
}
