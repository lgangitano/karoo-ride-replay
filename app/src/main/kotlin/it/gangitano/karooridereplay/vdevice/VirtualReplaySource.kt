package it.gangitano.karooridereplay.vdevice

import it.gangitano.karooridereplay.replay.FitRecord
import it.gangitano.karooridereplay.replay.ReplayEngine
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.BatteryStatus
import io.hammerhead.karooext.models.ConnectionStatus
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import io.hammerhead.karooext.models.OnBatteryStatus
import io.hammerhead.karooext.models.OnConnectionStatus
import io.hammerhead.karooext.models.OnDataPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Base class for virtual sensor sources backed by [ReplayEngine].
 *
 * Each concrete subclass binds one karoo-ext `DataType.Source` (power, HR,
 * cadence, speed) to one extractor over a [FitRecord]. The base class owns
 * the KPower-style lifecycle:
 *
 *   SEARCHING → CONNECTED → battery GOOD → stream `OnDataPoint`s as the
 *   `ReplayEngine.currentRecord` flow emits.
 *
 * Subclasses provide: the data-type Source string, the Field key for the
 * emitted DataPoint, and an extractor function `(FitRecord) -> Double?`.
 *
 * Lifecycle: callers invoke [connect] when the Karoo binds the Device.
 * The returned [Job] cancels all coroutines for tear-down.
 */
abstract class VirtualReplaySource(
    extensionId: String,
    uidSuffix: String,
    sourceType: String,
    displayName: String,
    protected val replayEngine: ReplayEngine
) {

    /** Karoo-ext [Device] descriptor. Registered when the extension starts. */
    val source: Device = Device(
        extension = extensionId,
        uid = "replay-$uidSuffix",
        dataTypes = listOf(sourceType),
        displayName = displayName
    )

    /** Field constant used as the key in the DataPoint values map. */
    protected abstract val field: String

    /**
     * Return the value to publish for this tick, or null if the FIT record
     * doesn't carry a usable value (e.g., HR field missing on a no-strap ride).
     */
    protected abstract fun extractValue(record: FitRecord): Double?

    /**
     * Subscribe to the [ReplayEngine] and publish ticks to the Karoo via [emitter].
     * Returns the [Job] managing the collection coroutine so callers can cancel
     * on Device unbind / extension shutdown.
     */
    fun connect(emitter: Emitter<DeviceEvent>): Job {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return scope.launch {
            // Lifecycle: announce searching → connected → battery, then stream
            emitter.onNext(OnConnectionStatus(ConnectionStatus.SEARCHING))
            delay(SIM_SEARCH_DELAY_MS)
            emitter.onNext(OnConnectionStatus(ConnectionStatus.CONNECTED))
            emitter.onNext(OnBatteryStatus(BatteryStatus.GOOD))

            // Stream as the replay engine advances
            replayEngine.currentRecord.collect { record ->
                val value = record?.let { extractValue(it) } ?: return@collect
                emitter.onNext(
                    OnDataPoint(
                        DataPoint(
                            dataTypeId = source.dataTypes.first(),
                            values = mapOf(field to value),
                            sourceId = source.uid
                        )
                    )
                )
            }
        }
    }

    companion object {
        /** Brief simulated "scanning for the sensor" delay — matches KPower's UX. */
        private const val SIM_SEARCH_DELAY_MS = 800L
    }
}
