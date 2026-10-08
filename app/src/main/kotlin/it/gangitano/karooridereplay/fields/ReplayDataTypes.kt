package it.gangitano.karooridereplay.fields

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import it.gangitano.karooridereplay.R
import it.gangitano.karooridereplay.extension.KarooRideReplayExtension
import it.gangitano.karooridereplay.extension.KarooRideReplayExtension.Companion.EXTENSION_ID
import it.gangitano.karooridereplay.replay.ReplayEngine
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** Adaptive ride-screen field showing replay status and, when wide, all transport controls. */
class ReplayDataType(
    private val service: KarooRideReplayExtension,
) : DataTypeImpl(EXTENSION_ID, TYPE_ID) {

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val appContext = service.applicationContext
        val controlBar = isControlBar(config.gridSize.first)
        startViewUpdates(emitter, config, snapshot = ::snapshot) {
            replayView(appContext, config, controlBar)
        }
    }

    /** Everything the field shows; a change triggers a redraw. */
    private fun snapshot(): Any? {
        val engine = KarooRideReplayExtension.instance?.replayEngine ?: return null
        return listOf(
            engine.currentRecord.value == null,
            engine.state.value,
            engine.elapsedSeconds.value,
            engine.totalSeconds,
            engine.playbackSpeed.value,
        )
    }

    private fun replayView(
        context: Context,
        config: ViewConfig,
        controlBar: Boolean,
    ): RemoteViews {
        val layout = if (controlBar) R.layout.field_replay_control_bar else R.layout.field_replay
        val view = RemoteViews(context.packageName, layout)
        val engine = KarooRideReplayExtension.instance?.replayEngine
        configureCommonView(
            view,
            config,
            controlBar,
            context.resources.displayMetrics.density,
            clockGlyphs = formatHHMMSS(engine?.totalSeconds ?: 0L).length,
        )

        if (!config.preview && engine?.currentRecord?.value == null) {
            // "No ride" is its own grey TextView rather than a recoloured
            // field_value: if the ride app reapplies updates onto the same view,
            // a recolour would stick to the clock once a ride loads.
            view.setViewVisibility(R.id.field_content, View.GONE)
            view.setViewVisibility(R.id.field_no_ride, View.VISIBLE)
            clearClickHandlers(view, controlBar)
            return view
        }

        view.setViewVisibility(R.id.field_content, View.VISIBLE)
        view.setViewVisibility(R.id.field_no_ride, View.GONE)
        val state = if (config.preview) ReplayEngine.State.PLAYING else engine!!.state.value
        val elapsed = if (config.preview) PREVIEW_ELAPSED_SECONDS else engine!!.elapsedSeconds.value
        val speed = if (config.preview) PREVIEW_SPEED else engine!!.playbackSpeed.value

        view.setImageViewResource(R.id.field_state_icon, actionIcon(state))
        view.setTextViewText(R.id.field_value, formatHHMMSS(elapsed))
        if (controlBar) {
            configureControlBar(view, context, config, state, speed)
        } else {
            view.setTextViewText(
                R.id.field_secondary,
                "${stateLabel(state)} · ${formatSpeed(speed)}",
            )
            if (!config.preview) {
                view.setOnClickPendingIntent(
                    R.id.field_root,
                    fieldPendingIntent(
                        context,
                        FieldTapReceiver.ACTION_TOGGLE_PLAY,
                        PLAY_REQUEST_CODE,
                    ),
                )
            }
        }
        return view
    }

    private fun configureControlBar(
        view: RemoteViews,
        context: Context,
        config: ViewConfig,
        state: ReplayEngine.State,
        speed: Double,
    ) {
        view.setTextViewText(R.id.field_skip_back, STEP_BACK_LABEL)
        view.setTextViewText(R.id.field_skip_forward, STEP_FORWARD_LABEL)
        view.setTextViewText(R.id.field_secondary, stateLabel(state))
        view.setTextViewText(R.id.field_speed, formatSpeed(speed))

        if (config.preview) return
        view.setOnClickPendingIntent(
            R.id.field_skip_back,
            fieldPendingIntent(context, FieldTapReceiver.ACTION_SKIP_BACK, BACK_REQUEST_CODE),
        )
        view.setOnClickPendingIntent(
            R.id.field_state_icon,
            fieldPendingIntent(
                context,
                FieldTapReceiver.ACTION_TOGGLE_PLAY,
                PLAY_REQUEST_CODE,
            ),
        )
        view.setOnClickPendingIntent(
            R.id.field_skip_forward,
            fieldPendingIntent(context, FieldTapReceiver.ACTION_SKIP_FORWARD, FORWARD_REQUEST_CODE),
        )
        view.setOnClickPendingIntent(
            R.id.field_speed,
            fieldPendingIntent(context, FieldTapReceiver.ACTION_CYCLE_SPEED, SPEED_REQUEST_CODE),
        )
    }

    /** The icon shows the action a tap will perform, following media-player convention. */
    private fun actionIcon(state: ReplayEngine.State): Int =
        if (state == ReplayEngine.State.PLAYING) {
            R.drawable.ic_field_pause
        } else {
            R.drawable.ic_field_play
        }

    private fun stateLabel(state: ReplayEngine.State): String = when (state) {
        ReplayEngine.State.IDLE -> "READY"
        else -> state.name
    }

    /** Clear listeners too: Karoo may apply this frame onto a previously interactive view. */
    private fun clearClickHandlers(view: RemoteViews, controlBar: Boolean) {
        if (controlBar) {
            view.setOnClickPendingIntent(R.id.field_skip_back, null)
            view.setOnClickPendingIntent(R.id.field_state_icon, null)
            view.setOnClickPendingIntent(R.id.field_skip_forward, null)
            view.setOnClickPendingIntent(R.id.field_speed, null)
        } else {
            view.setOnClickPendingIntent(R.id.field_root, null)
        }
    }

    companion object {
        private const val TYPE_ID = "replay"
        private const val PREVIEW_ELAPSED_SECONDS = 42L * 60L + 17L
        private const val PREVIEW_SPEED = 2.0
        private const val BACK_REQUEST_CODE = 1
        private const val PLAY_REQUEST_CODE = 2
        private const val FORWARD_REQUEST_CODE = 3
        private const val SPEED_REQUEST_CODE = 4
    }
}

private const val POLL_INTERVAL_MS = 100L
// ViewEmitter.updateView silently drops calls made <900 ms after the previous
// one; keep a margin so a redraw is never lost.
private const val MIN_UPDATE_GAP_MS = 950L
private const val TAG = "ReplayField"

/**
 * Show the Karoo-drawn header (field icon + name, like every native field, so
 * the field is recognisable on a page), emit the first frame immediately, then
 * redraw whenever [snapshot] changes, as soon as the SDK's rate limit allows
 * (in ride; a preview is static). Polling every 100 ms instead of redrawing on a
 * blind 1 Hz tick means a tap on a paused replay shows within ~0.1 s; while
 * playing the running clock redraws every ~0.95 s anyway, so a tap shows at the
 * next allowed frame. Tap delivery itself measured 57-76 ms on a Karoo 3.
 *
 * `updateView` is a synchronous binder call into the ride app, so it throws if
 * that process has died without stopping the view. Catch it and stop this field's
 * updates: an uncaught exception here would take down the whole extension, and
 * with it playback, the virtual sensors and mock GPS.
 */
private fun startViewUpdates(
    emitter: ViewEmitter,
    config: ViewConfig,
    snapshot: () -> Any?,
    render: () -> RemoteViews,
) {
    emitter.onNext(UpdateGraphicConfig(showHeader = true))
    val job = CoroutineScope(Dispatchers.Default).launch {
        var shown: Any? = Unit // never equal to a real snapshot, so the first frame renders
        var shownAt = 0L
        while (isActive) {
            val current = snapshot()
            val now = System.currentTimeMillis() // the clock ViewEmitter's limiter uses
            if (current != shown && now - shownAt >= MIN_UPDATE_GAP_MS) {
                try {
                    emitter.updateView(render())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Stopping field updates: ${e.message}", e)
                    break
                }
                shown = current
                shownAt = now
                if (config.preview) break
            }
            delay(POLL_INTERVAL_MS)
        }
    }
    emitter.setCancellable { job.cancel() }
}

/**
 * Sizing and alignment shared by both layouts. Text colour is deliberately NOT
 * set here: the layouts reference `@color/field_text`, which the ride app resolves
 * in its own day/night configuration when it inflates the view. A colour looked
 * up in this process could be the other mode's.
 */
private fun configureCommonView(
    view: RemoteViews,
    config: ViewConfig,
    controlBar: Boolean,
    density: Float,
    clockGlyphs: Int,
) {
    view.setInt(R.id.field_root, "setGravity", config.alignment.toGravity())
    if (!controlBar) {
        view.setInt(R.id.field_content, "setGravity", config.alignment.toGravity())
    }
    val widthDp = config.viewSize.first / density
    val heightDp = config.viewSize.second / density
    val valueSize = if (controlBar) {
        controlBarClockSp(widthDp, heightDp, config.textSize, clockGlyphs)
    } else {
        narrowClockSp(widthDp, heightDp, config.textSize, clockGlyphs)
    }
    view.setTextViewTextSize(R.id.field_value, TypedValue.COMPLEX_UNIT_SP, valueSize)
    view.setTextViewTextSize(R.id.field_no_ride, TypedValue.COMPLEX_UNIT_SP, valueSize)
    view.setTextViewTextSize(
        R.id.field_secondary,
        TypedValue.COMPLEX_UNIT_SP,
        max(10f, valueSize * if (controlBar) 0.4f else 0.45f),
    )
    if (controlBar) {
        view.setTextViewTextSize(
            R.id.field_speed,
            TypedValue.COMPLEX_UNIT_SP,
            min(config.textSize, 18).toFloat(),
        )
    }
}

private fun ViewConfig.Alignment.toGravity(): Int = when (this) {
    ViewConfig.Alignment.LEFT -> Gravity.START or Gravity.CENTER_VERTICAL
    ViewConfig.Alignment.CENTER -> Gravity.CENTER
    ViewConfig.Alignment.RIGHT -> Gravity.END or Gravity.CENTER_VERTICAL
}

private fun fieldPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, FieldTapReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
