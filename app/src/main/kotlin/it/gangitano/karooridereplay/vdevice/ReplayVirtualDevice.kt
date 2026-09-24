package it.gangitano.karooridereplay.vdevice

import it.gangitano.karooridereplay.replay.FitRecord
import it.gangitano.karooridereplay.replay.Sensor
import it.gangitano.karooridereplay.replay.SensorState
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * A virtual device that publishes one or more replayed sensors — built either
 * as the single combined device (all four sensors, the default) or as one
 * device per sensor (the "Separate sensors" setting).
 *
 * Why separate devices at all: karoo-ext connection status is per device, not
 * per data type. Only a device of its own can show HR searching while power
 * keeps streaming. The combined device stays the default because pairing four
 * entries in Settings → Sensors is friction when every channel should just be
 * populated from the FIT (timklge's review, awesome-karoo PR #43).
 *
 * Why `TYPE_SPD_DISTANCE_DIFF_ID`: Karoo has special-case handling for speed
 * sensors. Emitting just `DataType.Source.SPEED` does not make Karoo's ride
 * engine accumulate distance from the sensor — distance still falls back to
 * GPS. Pairing the SPEED emission with a per-tick distance-delta on
 * `TYPE_SPD_DISTANCE_DIFF_ID` is the documented Hammerhead pattern (used in
 * timklge's karoo-wattspeed) for "treat my virtual device as a real
 * speedometer." Any device carrying [Sensor.SPEED] carries the delta too.
 *
 * Lifecycle (KPower-style): SEARCHING for 800 ms, then follow [state]:
 * streaming → CONNECTED + battery GOOD + data points as [records] emits;
 * searching → SEARCHING; missing → DISCONNECTED. Cancelling the returned
 * [Job] (or its scope) tears the connection down.
 */
class ReplayVirtualDevice private constructor(
    extensionId: String,
    uid: String,
    displayName: String,
    private val sensors: List<Sensor>,
    private val records: Flow<FitRecord?>,
    private val state: Flow<SensorState>,
) {

    val source: Device = Device(
        extension = extensionId,
        uid = uid,
        dataTypes = sensors.map { it.dataTypeId } +
            if (Sensor.SPEED in sensors) listOf(TYPE_SPD_DISTANCE_DIFF_ID) else emptyList(),
        displayName = displayName,
    )

    fun connect(emitter: Emitter<DeviceEvent>, scope: CoroutineScope): Job = scope.launch {
        emitter.onNext(OnConnectionStatus(ConnectionStatus.SEARCHING))
        delay(SIM_SEARCH_DELAY_MS)
        // collectLatest cancels the previous state's block (the streaming
        // collection) before running the next, so no data point follows a
        // change away from streaming.
        state.collectLatest { current ->
            when (current) {
                SensorState.STREAMING -> {
                    emitter.onNext(OnConnectionStatus(ConnectionStatus.CONNECTED))
                    emitter.onNext(OnBatteryStatus(BatteryStatus.GOOD))
                    stream(emitter)
                }
                SensorState.SEARCHING ->
                    emitter.onNext(OnConnectionStatus(ConnectionStatus.SEARCHING))
                SensorState.MISSING ->
                    emitter.onNext(OnConnectionStatus(ConnectionStatus.DISCONNECTED))
            }
        }
    }

    /**
     * Publish data points until cancelled. The distance baseline is local, so
     * it starts fresh on every entry: resuming after a dropout sends no
     * catch-up delta (GPS covers the gap, as with a real sensor).
     */
    private suspend fun stream(emitter: Emitter<DeviceEvent>) {
        // State for computing distance delta between successive records.
        // Prefer the FIT's own cumulative distance (most accurate — what
        // the original ride recorded); fall back to integrating speed × dt
        // when distance is missing (e.g., an indoor ride without GPS).
        var prevDistanceM: Double? = null
        var prevTimeMs: Long? = null

        records.collect { record ->
            if (record == null) return@collect

            for (sensor in sensors) {
                sensor.valueIn(record)?.let { emit(emitter, sensor.dataTypeId, sensor.field, it) }
            }

            if (Sensor.SPEED in sensors) {
                val distanceDiffM = computeDistanceDiff(record, prevDistanceM, prevTimeMs)
                if (distanceDiffM > 0.0) {
                    emit(emitter, TYPE_SPD_DISTANCE_DIFF_ID, DataType.Field.DISTANCE, distanceDiffM)
                }
            }

            prevDistanceM = record.distance ?: prevDistanceM
            prevTimeMs = record.timestampMs
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
        const val COMBINED_UID = "replay-all"
        /** Karoo's special-case distance-delta data type id for speed sensors. */
        const val TYPE_SPD_DISTANCE_DIFF_ID = "TYPE_SPD_DISTANCE_DIFF_ID"
        internal const val SIM_SEARCH_DELAY_MS = 800L

        /** All four sensors on one device; ignores sensor states and always streams. */
        fun combined(extensionId: String, records: Flow<FitRecord?>) = ReplayVirtualDevice(
            extensionId = extensionId,
            uid = COMBINED_UID,
            displayName = "Karoo Ride Replay",
            sensors = Sensor.entries,
            records = records,
            state = flowOf(SensorState.STREAMING),
        )

        /** One sensor on its own device, following that sensor's entry in [states]. */
        fun separate(
            extensionId: String,
            sensor: Sensor,
            records: Flow<FitRecord?>,
            states: Flow<Map<Sensor, SensorState>>,
        ) = ReplayVirtualDevice(
            extensionId = extensionId,
            uid = sensor.deviceUid,
            displayName = sensor.displayName,
            sensors = listOf(sensor),
            records = records,
            state = states.map { it.getValue(sensor) }.distinctUntilChanged(),
        )
    }
}

private val Sensor.dataTypeId: String
    get() = when (this) {
        Sensor.POWER -> DataType.Source.POWER
        Sensor.HEART_RATE -> DataType.Source.HEART_RATE
        Sensor.CADENCE -> DataType.Source.CADENCE
        Sensor.SPEED -> DataType.Source.SPEED
    }

private val Sensor.field: String
    get() = when (this) {
        Sensor.POWER -> DataType.Field.POWER
        Sensor.HEART_RATE -> DataType.Field.HEART_RATE
        Sensor.CADENCE -> DataType.Field.CADENCE
        Sensor.SPEED -> DataType.Field.SPEED
    }

/** The uid the Karoo stores when this sensor's own device is paired. */
internal val Sensor.deviceUid: String
    get() = when (this) {
        Sensor.POWER -> "replay-power"
        Sensor.HEART_RATE -> "replay-hr"
        Sensor.CADENCE -> "replay-cadence"
        Sensor.SPEED -> "replay-speed"
    }

private val Sensor.displayName: String
    get() = when (this) {
        Sensor.POWER -> "Replay Power"
        Sensor.HEART_RATE -> "Replay HR"
        Sensor.CADENCE -> "Replay Cadence"
        Sensor.SPEED -> "Replay Speed"
    }
