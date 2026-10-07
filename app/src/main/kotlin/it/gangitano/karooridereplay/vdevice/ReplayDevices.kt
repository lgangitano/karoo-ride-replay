package it.gangitano.karooridereplay.vdevice

import it.gangitano.karooridereplay.replay.ReplayEngine
import it.gangitano.karooridereplay.replay.Sensor

/**
 * The four virtual devices the extension publishes, one per [Sensor]. Every
 * Add Sensor scan offers all four; a connect looks its uid up in a map built
 * once, so unknown uids (e.g. the retired combined `replay-all`) are ignored.
 */
class ReplayDevices(extensionId: String, engine: ReplayEngine) {

    /** In [Sensor] order: power, heart rate, cadence, speed. */
    val all: List<ReplayVirtualDevice> = Sensor.entries.map {
        ReplayVirtualDevice(extensionId, it, engine.currentRecord, engine.sensorStates)
    }

    private val byUid: Map<String, ReplayVirtualDevice> = all.associateBy { it.source.uid }

    fun find(uid: String): ReplayVirtualDevice? = byUid[uid]
}
