package it.gangitano.karooridereplay

import android.app.Application

/**
 * Application entry point.
 *
 * Holds the long-lived singletons used by both the UI host and the extension
 * service — once those land. For the scaffold this is just a placeholder.
 */
class KarooRideReplayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Singletons get wired here once the ReplayEngine + FitParser land.
    }

    companion object {
        @Volatile
        var instance: KarooRideReplayApplication? = null
            private set
    }

    init {
        @Suppress("LeakingThis")
        instance = this
    }
}
