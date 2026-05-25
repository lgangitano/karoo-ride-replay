package io.github.karooridereplay.replay

import com.garmin.fit.Decode
import com.garmin.fit.FitDecoder
import com.garmin.fit.FitMessages
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.RecordMesg
import java.io.File
import java.io.InputStream

/**
 * Reads a Garmin-FIT file into a flat [List]<[FitRecord]> for the replay engine.
 *
 * Wraps the official `com.garmin:fit` SDK's broadcaster pattern: every
 * `RecordMesg` in the file becomes one [FitRecord] in the output. Other FIT
 * message types (Lap, Session, Event, etc.) are ignored — they're not the
 * tick-level data we replay.
 *
 * The FIT SDK exposes lat/long as "semicircles" — signed 32-bit ints over the
 * range `[-2^31, 2^31)` mapping to `[-180°, +180°)`. We convert to degrees
 * here so the rest of the codebase deals in human units.
 */
class FitParser {

    /** Parse a [File] off disk. Returns records ordered by timestamp ascending. */
    fun parse(file: File): List<FitRecord> = file.inputStream().use { parse(it) }

    /** Parse from an [InputStream]. The caller is responsible for closing it. */
    fun parse(input: InputStream): List<FitRecord> {
        val records = mutableListOf<FitRecord>()
        val decode = Decode()
        val broadcaster = MesgBroadcaster(decode)
        broadcaster.addListener(com.garmin.fit.RecordMesgListener { mesg ->
            toFitRecord(mesg)?.let { records += it }
        })
        broadcaster.run(input)
        return records
    }

    private fun toFitRecord(mesg: RecordMesg): FitRecord? {
        // Timestamp is required; without it the record has no place on the timeline
        val timestampMs = mesg.timestamp?.date?.time ?: return null
        return FitRecord(
            timestampMs = timestampMs,
            lat = mesg.positionLat?.let(::semicirclesToDegrees),
            lng = mesg.positionLong?.let(::semicirclesToDegrees),
            altitude = mesg.altitude?.toDouble(),
            speed = mesg.speed?.toDouble(),
            power = mesg.power,
            heartRate = mesg.heartRate?.toInt(),
            cadence = mesg.cadence?.toInt(),
            distance = mesg.distance?.toDouble(),
            grade = mesg.grade?.toDouble(),
            temperature = mesg.temperature?.toInt()
        )
    }

    companion object {
        /**
         * FIT semicircle units span [-2^31, 2^31) over [-180°, +180°).
         * 1 degree = 2^31 / 180 semicircles.
         */
        private const val SEMICIRCLE_TO_DEGREE = 180.0 / (1L shl 31)

        fun semicirclesToDegrees(semicircles: Int): Double =
            semicircles * SEMICIRCLE_TO_DEGREE
    }
}
