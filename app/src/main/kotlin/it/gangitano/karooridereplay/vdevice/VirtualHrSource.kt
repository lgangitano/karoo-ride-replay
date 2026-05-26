package it.gangitano.karooridereplay.vdevice

import it.gangitano.karooridereplay.replay.FitRecord
import it.gangitano.karooridereplay.replay.ReplayEngine
import io.hammerhead.karooext.models.DataType

/**
 * Virtual heart-rate sensor sourced from the replay engine's `record.heartRate`.
 */
class VirtualHrSource(
    extensionId: String,
    replayEngine: ReplayEngine
) : VirtualReplaySource(
    extensionId = extensionId,
    uidSuffix = "hr",
    sourceType = DataType.Source.HEART_RATE,
    displayName = "Replay HR",
    replayEngine = replayEngine
) {
    override val field: String = DataType.Field.HEART_RATE

    override fun extractValue(record: FitRecord): Double? =
        record.heartRate?.toDouble()
}
