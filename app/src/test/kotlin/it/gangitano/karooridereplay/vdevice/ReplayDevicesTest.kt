package it.gangitano.karooridereplay.vdevice

import it.gangitano.karooridereplay.replay.ReplayEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** What the Add Sensor scan offers, and which uids a connect accepts. */
class ReplayDevicesTest {

    private val engine = ReplayEngine()
    private val devices = ReplayDevices("ride-replay", engine)

    @After fun tearDown() = engine.destroy()

    @Test fun `scan offers the four per-sensor devices with their v0_1_2 uids`() {
        assertEquals(
            listOf("replay-power", "replay-hr", "replay-cadence", "replay-speed"),
            devices.all.map { it.source.uid },
        )
    }

    @Test fun `connect finds each offered device by uid`() {
        for (device in devices.all) {
            assertSame(device, devices.find(device.source.uid))
        }
    }

    @Test fun `connect ignores the retired combined uid and unknown uids`() {
        assertNull(devices.find("replay-all"))
        assertNull(devices.find("replay-gps"))
    }
}
