package it.gangitano.karooridereplay.extension

import it.gangitano.karooridereplay.mocklocation.MockLocationProvider
import it.gangitano.karooridereplay.replay.ReplayEngine
import it.gangitano.karooridereplay.vdevice.ReplayVirtualDevice
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
 *   - One [ReplayVirtualDevice] — the consolidated virtual sensor exposing
 *     Power, HR, Cadence, Speed, and the per-tick distance delta
 *     (`TYPE_SPD_DISTANCE_DIFF_ID`) through a single karoo-ext Device that
 *     the user pairs once
 *   - [MockLocationProvider] — pushes the engine's GPS coordinates into
 *     Android's `LocationManager` as test-provider locations
 *
 * Karoo-ext lifecycle:
 *   - [startScan] emits the single virtual device descriptor when the user
 *     opens Settings → Sensors → Add Sensor.
 *   - [connectDevice] dispatches the pair to [ReplayVirtualDevice.connect]
 *     when the user activates it.
 *
 * Other components access the running extension via the [instance]
 * companion (mirrors the pattern 7climb uses for its ClimbIntelligenceExtension).
 */
class KarooRideReplayExtension : KarooExtension(EXTENSION_ID, "0.1.0-alpha") {

    val replayEngine: ReplayEngine = ReplayEngine()

    private val virtualDevice: ReplayVirtualDevice by lazy {
        ReplayVirtualDevice(extension, replayEngine)
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

    override fun startScan(emitter: Emitter<Device>) {
        val scope = CoroutineScope(Dispatchers.IO)
        val job = scope.launch {
            // Brief "scanning" pause for UX — same as KPower
            delay(SCAN_ANNOUNCE_DELAY_MS)
            emitter.onNext(virtualDevice.source)
        }
        emitter.setCancellable { job.cancel() }
    }

    override fun connectDevice(uid: String, emitter: Emitter<DeviceEvent>) {
        if (uid != virtualDevice.source.uid) return
        val job = virtualDevice.connect(emitter)
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
