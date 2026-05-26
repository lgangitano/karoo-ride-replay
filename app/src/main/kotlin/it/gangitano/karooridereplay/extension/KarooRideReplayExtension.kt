package it.gangitano.karooridereplay.extension

import it.gangitano.karooridereplay.mocklocation.MockLocationProvider
import it.gangitano.karooridereplay.replay.ReplayEngine
import it.gangitano.karooridereplay.vdevice.VirtualCadenceSource
import it.gangitano.karooridereplay.vdevice.VirtualHrSource
import it.gangitano.karooridereplay.vdevice.VirtualPowerSource
import it.gangitano.karooridereplay.vdevice.VirtualReplaySource
import it.gangitano.karooridereplay.vdevice.VirtualSpeedSource
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Karoo extension service for `ride-replay`.
 *
 * Hosts the long-lived singletons:
 *   - [ReplayEngine] — the playback state machine, driven by the UI
 *   - 4 [VirtualReplaySource] instances (Power, HR, Cadence, Speed) — the
 *     virtual sensors the Karoo pairs to in Settings → Sensors
 *   - [MockLocationProvider] — pushes the engine's GPS coordinates into
 *     Android's `LocationManager` as test-provider locations
 *
 * Karoo-ext lifecycle:
 *   - [startScan] is called when the user opens Settings → Sensors → Add
 *     Sensor. We emit the four virtual device descriptors so they're all
 *     pairable.
 *   - [connectDevice] is called when the user activates one of our devices.
 *     We look up the source by UID and call its connect() method.
 *
 * Other components access the running extension via the [instance]
 * companion (mirrors the pattern 7climb uses for its ClimbIntelligenceExtension).
 */
class KarooRideReplayExtension : KarooExtension(EXTENSION_ID, "0.1.0-alpha") {

    val replayEngine: ReplayEngine = ReplayEngine()

    private val virtualSources: List<VirtualReplaySource> by lazy {
        listOf(
            VirtualPowerSource(extension, replayEngine),
            VirtualHrSource(extension, replayEngine),
            VirtualCadenceSource(extension, replayEngine),
            VirtualSpeedSource(extension, replayEngine)
        )
    }

    private var mockLocation: MockLocationProvider? = null

    override val types: List<DataTypeImpl> = emptyList()

    override fun onCreate() {
        super.onCreate()
        instance = this
        // MockLocationProvider always-on while the extension lives. It installs
        // test providers + subscribes to ReplayEngine.currentRecord; while the
        // engine is idle, no emissions occur.
        mockLocation = MockLocationProvider(applicationContext, replayEngine).also { it.start() }
    }

    override fun onDestroy() {
        mockLocation?.destroy()
        mockLocation = null
        replayEngine.destroy()
        instance = null
        super.onDestroy()
    }

    /**
     * Sensor scan: emit all four virtual devices so the user sees them in
     * Settings → Sensors → Add Sensor. Mirrors the KPower pattern (KPower
     * emits one device per scan; we emit four because we have four sensor
     * types to expose).
     */
    override fun startScan(emitter: Emitter<Device>) {
        val scope = CoroutineScope(Dispatchers.IO)
        val job = scope.launch {
            // Brief "scanning" pause for UX — same as KPower
            delay(SCAN_ANNOUNCE_DELAY_MS)
            virtualSources.forEach { source -> emitter.onNext(source.source) }
        }
        emitter.setCancellable { job.cancel() }
    }

    /**
     * Device pair: look up our source by UID and start it streaming.
     * Returns silently if the UID doesn't match — Karoo OS may call this
     * with a UID we don't recognize during transitions.
     */
    override fun connectDevice(uid: String, emitter: Emitter<DeviceEvent>) {
        val source = virtualSources.firstOrNull { it.source.uid == uid } ?: return
        val job = source.connect(emitter)
        emitter.setCancellable { job.cancel() }
    }

    companion object {
        const val EXTENSION_ID = "ride-replay"
        private const val SCAN_ANNOUNCE_DELAY_MS = 500L

        /**
         * The running extension instance. UI binds to this for play/pause/seek
         * control. Mirrors 7climb's ClimbIntelligenceExtension.instance pattern.
         */
        @Volatile
        var instance: KarooRideReplayExtension? = null
            private set
    }
}
