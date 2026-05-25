package io.github.karooridereplay.vdevice

import io.github.karooridereplay.replay.FitRecord
import io.github.karooridereplay.replay.ReplayEngine
import io.hammerhead.karooext.models.DataType

/**
 * Virtual cadence sensor sourced from the replay engine's `record.cadence`.
 */
class VirtualCadenceSource(
    extensionId: String,
    replayEngine: ReplayEngine
) : VirtualReplaySource(
    extensionId = extensionId,
    uidSuffix = "cadence",
    sourceType = DataType.Source.CADENCE,
    displayName = "Replay Cadence",
    replayEngine = replayEngine
) {
    override val field: String = DataType.Field.CADENCE

    override fun extractValue(record: FitRecord): Double? =
        record.cadence?.toDouble()
}
