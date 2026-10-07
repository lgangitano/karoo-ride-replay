package it.gangitano.karooridereplay.extension

import it.gangitano.karooridereplay.BuildConfig
import it.gangitano.karooridereplay.mocklocation.MockLocationProvider
import it.gangitano.karooridereplay.replay.ReplayEngine
import it.gangitano.karooridereplay.vdevice.ReplayDevices
import it.gangitano.karooridereplay.vdevice.ReplayVirtualDevice
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Karoo extension service for `ride-replay`.
 *
 * Hosts the long-lived singletons:
 *   - [ReplayEngine] — the playback state machine, driven by the UI
 *   - [ReplayDevices] — one virtual device per sensor (Power, HR, Cadence,
 *     Speed; the speed device also carries the per-tick distance delta
 *     `TYPE_SPD_DISTANCE_DIFF_ID`), so each can drop out on its own
 *   - [MockLocationProvider] — pushes the engine's GPS coordinates into
 *     Android's `LocationManager` as test-provider locations
 *
 * Karoo-ext lifecycle:
 *   - [startScan] emits the four devices when the user opens Add Sensor.
 *   - [connectDevice] dispatches the pair to [ReplayVirtualDevice.connect]
 *     by uid; unknown uids are ignored.
 *
 * Other components access the running extension via the [instance]
 * companion (the common Karoo-extension singleton pattern).
 */
// The version reported to Karoo OS tracks the app version via BuildConfig —
// a hardcoded string here drifted (stuck at 0.1.0-alpha while the app shipped 0.1.4).
class KarooRideReplayExtension : KarooExtension(EXTENSION_ID, BuildConfig.VERSION_NAME) {

    val replayEngine: ReplayEngine = ReplayEngine()

    private val devices: ReplayDevices by lazy { ReplayDevices(extension, replayEngine) }

    private var mockLocation: MockLocationProvider? = null

    override val types: List<DataTypeImpl> = emptyList()

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Create the provider but do NOT install test providers yet. Installing
        // at startup failed silently before the app was picked as the device's
        // mock-location app and forced an app restart (issue #1); it also
        // hijacked real GPS when no replay was running. The UI arms it via
        // [armMockLocation] when a replay starts and [disarmMockLocation] when
        // the user exits, so real GPS is used whenever nothing is replaying.
        mockLocation = MockLocationProvider(applicationContext, replayEngine)
    }

    /** Install mock GPS + start streaming. Called by the UI when a replay starts. */
    fun armMockLocation() = mockLocation?.arm()

    /** Remove mock GPS so real GPS returns. Called by the UI when the replay is exited. */
    fun disarmMockLocation() = mockLocation?.disarm()

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
            devices.all.forEach { emitter.onNext(it.source) }
        }
        emitter.setCancellable { job.cancel() }
    }

    override fun connectDevice(uid: String, emitter: Emitter<DeviceEvent>) {
        val device = devices.find(uid) ?: return
        // One scope per connection; cancelling it tears this connection down.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        device.connect(emitter, scope)
        emitter.setCancellable { scope.cancel() }
    }

    companion object {
        const val EXTENSION_ID = "ride-replay"
        private const val SCAN_ANNOUNCE_DELAY_MS = 500L

        /**
         * The running extension instance. UI binds to this for play/pause/seek
         * control. (Standard Karoo-extension singleton pattern.)
         */
        @Volatile
        var instance: KarooRideReplayExtension? = null
            private set
    }
}
