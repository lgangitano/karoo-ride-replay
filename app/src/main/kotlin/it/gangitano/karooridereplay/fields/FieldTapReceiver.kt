package it.gangitano.karooridereplay.fields

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import it.gangitano.karooridereplay.extension.KarooRideReplayExtension

/** Receives explicit, app-private taps from the ride-screen replay field. */
class FieldTapReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val extension = KarooRideReplayExtension.instance
        if (extension == null) {
            Log.w(TAG, "${intent.action}: extension is not running")
            return
        }

        when (intent.action) {
            ACTION_TOGGLE_PLAY -> extension.togglePlayPause()
            ACTION_SKIP_BACK -> extension.skip(-SKIP_SECONDS)
            ACTION_SKIP_FORWARD -> extension.skip(SKIP_SECONDS)
            ACTION_CYCLE_SPEED -> extension.cycleSpeed()
            else -> {
                Log.w(TAG, "Ignoring unknown action: ${intent.action}")
                return
            }
        }
        Log.i(
            TAG,
            "${intent.action}: state=${extension.replayEngine.state.value}, " +
                "speed=${formatSpeed(extension.replayEngine.playbackSpeed.value)}",
        )
    }

    companion object {
        internal const val ACTION_TOGGLE_PLAY =
            "it.gangitano.karooridereplay.field.TOGGLE_PLAY"
        internal const val ACTION_SKIP_BACK =
            "it.gangitano.karooridereplay.field.SKIP_BACK"
        internal const val ACTION_SKIP_FORWARD =
            "it.gangitano.karooridereplay.field.SKIP_FORWARD"
        internal const val ACTION_CYCLE_SPEED =
            "it.gangitano.karooridereplay.field.CYCLE_SPEED"

        private const val TAG = "ReplayField"
    }
}
