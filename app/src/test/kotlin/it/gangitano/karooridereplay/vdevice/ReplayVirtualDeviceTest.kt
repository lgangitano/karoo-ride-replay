package it.gangitano.karooridereplay.vdevice

import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.DeviceEvent
import io.hammerhead.karooext.models.OnBatteryStatus
import io.hammerhead.karooext.models.OnConnectionStatus
import io.hammerhead.karooext.models.OnDataPoint
import it.gangitano.karooridereplay.replay.FitRecord
import it.gangitano.karooridereplay.replay.ReplayEngine
import it.gangitano.karooridereplay.replay.Sensor
import it.gangitano.karooridereplay.replay.SensorState
import it.gangitano.karooridereplay.vdevice.ReplayVirtualDevice.Companion.SIM_SEARCH_DELAY_MS
import it.gangitano.karooridereplay.vdevice.ReplayVirtualDevice.Companion.TYPE_SPD_DISTANCE_DIFF_ID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a virtual device sends the Karoo, as a compact trace: connection
 * statuses by name, "battery", and "<dataTypeId>=<value>" per data point.
 * Records and sensor states are driven by hand on a virtual clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplayVirtualDeviceTest {

    private class RecordingEmitter : Emitter<DeviceEvent> {
        val events = mutableListOf<DeviceEvent>()
        override fun onNext(t: DeviceEvent) { events += t }
        override fun onError(t: Throwable) { throw t }
        override fun onComplete() = Unit
        override fun setCancellable(cancellable: () -> Unit) = Unit
        override fun cancel() = Unit
    }

    private val records = MutableStateFlow<FitRecord?>(null)
    private val states = MutableStateFlow(ReplayEngine.ALL_STREAMING)
    private val emitter = RecordingEmitter()

    private val hr = DataType.Source.HEART_RATE
    private val spd = DataType.Source.SPEED
    private val dist = TYPE_SPD_DISTANCE_DIFF_ID

    private fun trace(): List<String> = emitter.events.map { event ->
        when (event) {
            is OnConnectionStatus -> event.status.name
            is OnBatteryStatus -> "battery"
            is OnDataPoint -> "${event.dataPoint.dataTypeId}=${event.dataPoint.values.values.single()}"
            else -> event.toString()
        }
    }

    private fun set(sensor: Sensor, state: SensorState) {
        states.value = states.value + (sensor to state)
    }

    /** Connect [device] and let the 800 ms search delay elapse. */
    private fun TestScope.connect(device: ReplayVirtualDevice) {
        device.connect(emitter, backgroundScope)
        advanceTimeBy(SIM_SEARCH_DELAY_MS)
        runCurrent()
    }

    private fun separate(sensor: Sensor) =
        ReplayVirtualDevice.separate(EXTENSION_ID, sensor, records, states)

    @Test fun `heart rate walks streaming, searching, missing and back`() = runTest {
        records.value = FitRecord(timestampMs = 0L, heartRate = 120)
        connect(separate(Sensor.HEART_RATE))

        set(Sensor.HEART_RATE, SensorState.SEARCHING); runCurrent()
        records.value = FitRecord(timestampMs = 1000L, heartRate = 121); runCurrent()
        set(Sensor.HEART_RATE, SensorState.MISSING); runCurrent()
        records.value = FitRecord(timestampMs = 2000L, heartRate = 122); runCurrent()
        set(Sensor.HEART_RATE, SensorState.STREAMING); runCurrent()

        assertEquals(
            listOf(
                "SEARCHING", "CONNECTED", "battery", "$hr=120.0",
                "SEARCHING",
                "DISCONNECTED",
                "CONNECTED", "battery", "$hr=122.0",
            ),
            trace(),
        )
    }

    @Test fun `connecting while missing goes straight to disconnected`() = runTest {
        set(Sensor.HEART_RATE, SensorState.MISSING)
        records.value = FitRecord(timestampMs = 0L, heartRate = 120)
        connect(separate(Sensor.HEART_RATE))
        records.value = FitRecord(timestampMs = 1000L, heartRate = 121); runCurrent()

        assertEquals(listOf("SEARCHING", "DISCONNECTED"), trace())
    }

    @Test fun `rapid taps land on the latest state`() = runTest {
        records.value = FitRecord(timestampMs = 0L, heartRate = 120)
        connect(separate(Sensor.HEART_RATE))

        set(Sensor.HEART_RATE, SensorState.SEARCHING)
        set(Sensor.HEART_RATE, SensorState.MISSING)
        runCurrent()
        records.value = FitRecord(timestampMs = 1000L, heartRate = 121); runCurrent()

        assertEquals(
            listOf("SEARCHING", "CONNECTED", "battery", "$hr=120.0", "DISCONNECTED"),
            trace(),
        )
    }

    @Test fun `another sensor's state leaves this device alone`() = runTest {
        records.value = FitRecord(timestampMs = 0L, heartRate = 120)
        connect(separate(Sensor.HEART_RATE))
        set(Sensor.POWER, SensorState.MISSING); runCurrent()
        records.value = FitRecord(timestampMs = 1000L, heartRate = 121); runCurrent()

        assertEquals(
            listOf("SEARCHING", "CONNECTED", "battery", "$hr=120.0", "$hr=121.0"),
            trace(),
        )
    }

    @Test fun `streaming with no value sends status but no data`() = runTest {
        records.value = FitRecord(timestampMs = 0L, power = 200)
        connect(separate(Sensor.HEART_RATE))
        records.value = FitRecord(timestampMs = 1000L, power = 210); runCurrent()

        assertEquals(listOf("SEARCHING", "CONNECTED", "battery"), trace())
    }

    @Test fun `first speed tick after a dropout sends no distance delta`() = runTest {
        records.value = FitRecord(timestampMs = 0L, speed = 5.0, distance = 0.0)
        connect(separate(Sensor.SPEED))
        records.value = FitRecord(timestampMs = 1000L, speed = 5.0, distance = 5.0); runCurrent()

        set(Sensor.SPEED, SensorState.SEARCHING); runCurrent()
        records.value = FitRecord(timestampMs = 2000L, speed = 5.0, distance = 10.0); runCurrent()
        records.value = FitRecord(timestampMs = 3000L, speed = 5.0, distance = 15.0); runCurrent()

        set(Sensor.SPEED, SensorState.STREAMING); runCurrent()
        records.value = FitRecord(timestampMs = 4000L, speed = 5.0, distance = 20.0); runCurrent()

        assertEquals(
            listOf(
                "SEARCHING", "CONNECTED", "battery", "$spd=5.0",
                "$spd=5.0", "$dist=5.0",
                "SEARCHING",
                // Resume: no catch-up delta for the 10 m ridden while searching.
                "CONNECTED", "battery", "$spd=5.0",
                "$spd=5.0", "$dist=5.0",
            ),
            trace(),
        )
    }

    @Test fun `combined device streams every sensor`() = runTest {
        records.value = FitRecord(timestampMs = 0L, distance = 0.0)
        connect(ReplayVirtualDevice.combined(EXTENSION_ID, records))
        records.value = FitRecord(
            timestampMs = 1000L, power = 200, heartRate = 120, cadence = 90,
            speed = 5.0, distance = 5.0,
        )
        runCurrent()

        assertEquals(
            listOf(
                "SEARCHING", "CONNECTED", "battery",
                "${DataType.Source.POWER}=200.0",
                "$hr=120.0",
                "${DataType.Source.CADENCE}=90.0",
                "$spd=5.0",
                "$dist=5.0",
            ),
            trace(),
        )
    }

    @Test fun `each device declares its own data types and uid`() {
        assertEquals(listOf(hr), separate(Sensor.HEART_RATE).source.dataTypes)
        assertEquals("replay-hr", separate(Sensor.HEART_RATE).source.uid)
        assertEquals(listOf(spd, dist), separate(Sensor.SPEED).source.dataTypes)
        val combined = ReplayVirtualDevice.combined(EXTENSION_ID, records).source
        assertEquals(ReplayVirtualDevice.COMBINED_UID, combined.uid)
        assertEquals(
            listOf(DataType.Source.POWER, hr, DataType.Source.CADENCE, spd, dist),
            combined.dataTypes,
        )
    }

    private companion object {
        const val EXTENSION_ID = "ride-replay"
    }
}
