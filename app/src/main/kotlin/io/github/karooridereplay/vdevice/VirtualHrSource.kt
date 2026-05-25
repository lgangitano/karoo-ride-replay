package io.github.karooridereplay.vdevice

import io.github.karooridereplay.replay.FitRecord
import io.github.karooridereplay.replay.ReplayEngine
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
