package it.gangitano.karooridereplay.replay

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Per-sensor simulated connection state: the tap cycle, independence between
 * sensors, and the reset a new ride brings.
 */
class ReplayEngineSensorStateTest {

    private val engine = ReplayEngine()

    @After fun tearDown() = engine.destroy()

    @Test fun `every sensor starts streaming`() {
        assertEquals(ReplayEngine.ALL_STREAMING, engine.sensorStates.value)
        assertEquals(Sensor.entries.toSet(), engine.sensorStates.value.keys)
    }

    @Test fun `cycling walks streaming, searching, missing and back`() {
        engine.cycleSensorState(Sensor.HEART_RATE)
        assertEquals(SensorState.SEARCHING, engine.sensorStates.value[Sensor.HEART_RATE])
        engine.cycleSensorState(Sensor.HEART_RATE)
        assertEquals(SensorState.MISSING, engine.sensorStates.value[Sensor.HEART_RATE])
        engine.cycleSensorState(Sensor.HEART_RATE)
        assertEquals(SensorState.STREAMING, engine.sensorStates.value[Sensor.HEART_RATE])
    }

    @Test fun `cycling one sensor leaves the others streaming`() {
        engine.cycleSensorState(Sensor.POWER)
        assertEquals(
            ReplayEngine.ALL_STREAMING + (Sensor.POWER to SensorState.SEARCHING),
            engine.sensorStates.value,
        )
    }

    @Test fun `loading a ride resets every sensor to streaming`() {
        engine.cycleSensorState(Sensor.HEART_RATE)
        engine.cycleSensorState(Sensor.SPEED)
        engine.cycleSensorState(Sensor.SPEED)
        engine.load(listOf(FitRecord(timestampMs = 0L), FitRecord(timestampMs = 1000L)))
        assertEquals(ReplayEngine.ALL_STREAMING, engine.sensorStates.value)
    }
}
