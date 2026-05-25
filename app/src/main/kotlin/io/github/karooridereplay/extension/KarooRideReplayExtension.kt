package io.github.karooridereplay.extension

import io.hammerhead.karooext.extension.KarooExtension

/**
 * Karoo extension host. Currently exposes no data types — its job is to be a
 * service the Karoo OS knows about so virtual sensor Devices (Power, Heart
 * Rate, Cadence, Speed) can be registered during ride playback.
 *
 * The virtual sensor implementations land under `vdevice/`. The replay engine
 * (under `replay/`) coordinates: FIT-file parsing → sample emission →
 * virtual sensors publishing power/HR/cadence/speed + mock-location publishing
 * GPS coordinates at the FIT-recorded timing.
 */
class KarooRideReplayExtension : KarooExtension("ride-replay", "0.1.0-alpha") {

    override val types = emptyList<io.hammerhead.karooext.extension.DataTypeImpl>()

    override fun onCreate() {
        super.onCreate()
        // ReplayEngine wiring lands here in the next iteration.
    }

    override fun onDestroy() {
        super.onDestroy()
        // Tear-down for active virtual sensors + mock location.
    }
}
