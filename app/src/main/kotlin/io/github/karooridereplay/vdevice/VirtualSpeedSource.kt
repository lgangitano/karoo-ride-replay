package io.github.karooridereplay.vdevice

import io.github.karooridereplay.replay.FitRecord
import io.github.karooridereplay.replay.ReplayEngine
import io.hammerhead.karooext.models.DataType

/**
 * Virtual speed sensor sourced from the replay engine's `record.speed` (m/s).
 *
 * Critical for our test rig: Karoo's auto-pause uses sensor speed when paired,
 * with priority over GPS-derived speed. Pairing this Device makes the ride
 * keep recording while replay is active (no auto-pause), even with FakeTraveler-
 * style mock GPS whose hardcoded speed=0.01 would otherwise trigger pause.
 */
class VirtualSpeedSource(
    extensionId: String,
    replayEngine: ReplayEngine
) : VirtualReplaySource(
    extensionId = extensionId,
    uidSuffix = "speed",
    sourceType = DataType.Source.SPEED,
    displayName = "Replay Speed",
    replayEngine = replayEngine
) {
    override val field: String = DataType.Field.SPEED

    override fun extractValue(record: FitRecord): Double? = record.speed
}
