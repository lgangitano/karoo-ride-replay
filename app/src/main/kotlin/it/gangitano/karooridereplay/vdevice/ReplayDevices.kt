package it.gangitano.karooridereplay.vdevice

import it.gangitano.karooridereplay.replay.ReplayEngine
import it.gangitano.karooridereplay.replay.Sensor

/**
 * The five virtual devices the extension can publish: the combined one and one
 * per sensor. The "Separate sensors" switch decides only what the Add Sensor
 * scan offers; a connect accepts all five, because the Karoo remembers pairings
 * on its own and flipping the switch must never strand a paired sensor.
 */
class ReplayDevices(extensionId: String, engine: ReplayEngine) {

    private val combined = ReplayVirtualDevice.combined(extensionId, engine.currentRecord)

    private val separate = Sensor.entries.map {
        ReplayVirtualDevice.separate(extensionId, it, engine.currentRecord, engine.sensorStates)
    }

    fun offered(separateSensors: Boolean): List<ReplayVirtualDevice> =
        if (separateSensors) separate else listOf(combined)

    fun find(uid: String): ReplayVirtualDevice? =
        (listOf(combined) + separate).firstOrNull { it.source.uid == uid }
}
