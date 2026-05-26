package it.gangitano.karooridereplay.vdevice

import it.gangitano.karooridereplay.replay.FitRecord
import it.gangitano.karooridereplay.replay.ReplayEngine
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.BatteryStatus
import io.hammerhead.karooext.models.ConnectionStatus
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import io.hammerhead.karooext.models.OnBatteryStatus
import io.hammerhead.karooext.models.OnConnectionStatus
import io.hammerhead.karooext.models.OnDataPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Single virtual device that publishes ALL four sensor streams from a replay:
 * power, heart rate, cadence, and speed — plus the speed-specific distance
 * delta type Karoo uses to integrate sensor speed into total ride distance.
 *
 * Why one device instead of four (the prior shape): pairing four entries in
 * Settings → Sensors is friction for no gain in our use case — a developer
 * running a replay wants every channel populated from the FIT. timklge
 * (awesome-karoo maintainer) recommended the consolidation 2026-05-26.
 *
 * Why `TYPE_SPD_DISTANCE_DIFF_ID`: Karoo has special-case handling for speed
 * sensors. Emitting just `DataType.Source.SPEED` does not make Karoo's ride
 * engine accumulate distance from the sensor — distance still falls back to
 * GPS. Pairing the SPEED emission with a per-tick distance-delta on
 * `TYPE_SPD_DISTANCE_DIFF_ID` is the documented Hammerhead pattern (used in
 * timklge's karoo-wattspeed) for "treat my virtual device as a real
 * speedometer."
 *
 * Lifecycle (KPower-style): SEARCHING → CONNECTED → battery GOOD → stream
 * data points as `ReplayEngine.currentRecord` emits. Cancelling the returned
 * [Job] tears down the collection coroutine on device unbind.
 */
class ReplayVirtualDevice(
    private val extensionId: String,
    private val replayEngine: ReplayEngine
) {

    val source: Device = Device(
        extension = extensionId,
        uid = DEVICE_UID,
        dataTypes = listOf(
            DataType.Source.POWER,
            DataType.Source.HEART_RATE,
            DataType.Source.CADENCE,
            DataType.Source.SPEED,
            TYPE_SPD_DISTANCE_DIFF_ID,
        ),
        displayName = "Karoo Ride Replay"
    )

    fun connect(emitter: Emitter<DeviceEvent>): Job {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return scope.launch {
            emitter.onNext(OnConnectionStatus(ConnectionStatus.SEARCHING))
            delay(SIM_SEARCH_DELAY_MS)
            emitter.onNext(OnConnectionStatus(ConnectionStatus.CONNECTED))
            emitter.onNext(OnBatteryStatus(BatteryStatus.GOOD))

            // State for computing distance delta between successive records.
            // Prefer the FIT's own cumulative distance (most accurate — what
            // the original ride recorded); fall back to integrating speed × dt
            // when distance is missing (e.g., an indoor ride without GPS).
            var prevDistanceM: Double? = null
            var prevTimeMs: Long? = null

            replayEngine.currentRecord.collect { record ->
                if (record == null) return@collect

                record.power?.let {
                    emit(emitter, DataType.Source.POWER, DataType.Field.POWER, it.toDouble())
                }
                record.heartRate?.let {
                    emit(emitter, DataType.Source.HEART_RATE, DataType.Field.HEART_RATE, it.toDouble())
                }
                record.cadence?.let {
                    emit(emitter, DataType.Source.CADENCE, DataType.Field.CADENCE, it.toDouble())
                }
                record.speed?.let { speedMs ->
                    emit(emitter, DataType.Source.SPEED, DataType.Field.SPEED, speedMs)
                }

                val distanceDiffM = computeDistanceDiff(record, prevDistanceM, prevTimeMs)
                if (distanceDiffM > 0.0) {
                    emit(emitter, TYPE_SPD_DISTANCE_DIFF_ID, DataType.Field.DISTANCE, distanceDiffM)
                }

                prevDistanceM = record.distance ?: prevDistanceM
                prevTimeMs = record.timestampMs
            }
        }
    }

    private fun computeDistanceDiff(
        record: FitRecord,
        prevDistanceM: Double?,
        prevTimeMs: Long?
    ): Double {
        // 1. FIT's cumulative distance — diff between consecutive samples.
        val recDist = record.distance
        if (recDist != null && prevDistanceM != null) {
            return (recDist - prevDistanceM).coerceAtLeast(0.0)
        }
        // 2. Integrate speed over the elapsed wall-clock interval.
        val speed = record.speed
        if (speed != null && prevTimeMs != null) {
            val dtSeconds = (record.timestampMs - prevTimeMs).coerceAtLeast(0L) / 1000.0
            return speed * dtSeconds
        }
        return 0.0
    }

    private fun emit(
        emitter: Emitter<DeviceEvent>,
        dataTypeId: String,
        field: String,
        value: Double
    ) {
        emitter.onNext(
            OnDataPoint(
                DataPoint(
                    dataTypeId = dataTypeId,
                    values = mapOf(field to value),
                    sourceId = source.uid
                )
            )
        )
    }

    companion object {
        const val DEVICE_UID = "replay-all"
        /** Karoo's special-case distance-delta data type id for speed sensors. */
        const val TYPE_SPD_DISTANCE_DIFF_ID = "TYPE_SPD_DISTANCE_DIFF_ID"
        private const val SIM_SEARCH_DELAY_MS = 800L
    }
}
