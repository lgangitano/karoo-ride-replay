package io.github.karooridereplay.vdevice

import io.github.karooridereplay.replay.FitRecord
import io.github.karooridereplay.replay.ReplayEngine
import io.hammerhead.karooext.models.DataType

/**
 * Virtual cycling power meter sourced from the replay engine's `record.power`.
 *
 * Karoo pairs this from Settings → Sensors as if it were a real BLE/ANT+
 * power meter. Watts get published to `DataType.Type.POWER` consumers
 * (7climb's W' engine, Wattramp, Karoo's own ride recorder, etc.).
 */
class VirtualPowerSource(
    extensionId: String,
    replayEngine: ReplayEngine
) : VirtualReplaySource(
    extensionId = extensionId,
    uidSuffix = "power",
    sourceType = DataType.Source.POWER,
    displayName = "Replay Power",
    replayEngine = replayEngine
) {
    override val field: String = DataType.Field.POWER

    override fun extractValue(record: FitRecord): Double? =
        record.power?.toDouble()
}
