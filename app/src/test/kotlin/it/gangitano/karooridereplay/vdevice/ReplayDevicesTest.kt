package it.gangitano.karooridereplay.vdevice

import it.gangitano.karooridereplay.replay.ReplayEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** What the Add Sensor scan offers, and which uids a connect accepts. */
class ReplayDevicesTest {

    private val engine = ReplayEngine()
    private val devices = ReplayDevices("ride-replay", engine)
    private val separateUids = listOf("replay-power", "replay-hr", "replay-cadence", "replay-speed")

    @After fun tearDown() = engine.destroy()

    @Test fun `switch off offers only the combined device`() {
        assertEquals(listOf("replay-all"), devices.offered(separateSensors = false).map { it.source.uid })
    }

    @Test fun `switch on offers only the four separate devices`() {
        assertEquals(separateUids, devices.offered(separateSensors = true).map { it.source.uid })
    }

    @Test fun `connect finds every device whichever way the switch is set`() {
        for (uid in listOf("replay-all") + separateUids) {
            assertNotNull(uid, devices.find(uid))
            assertEquals(uid, devices.find(uid)?.source?.uid)
        }
    }

    @Test fun `connect ignores an unknown uid`() {
        assertNull(devices.find("replay-gps"))
    }
}
