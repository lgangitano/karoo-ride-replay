package it.gangitano.karooridereplay.ui

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.gangitano.karooridereplay.replay.ReplayEngine
import it.gangitano.karooridereplay.replay.Sensor
import it.gangitano.karooridereplay.replay.SensorState
import it.gangitano.karooridereplay.ui.theme.Karoo
import it.gangitano.karooridereplay.ui.theme.KarooIcons
import kotlin.math.roundToLong

internal const val STEP_BACK_LABEL = "‹ 10s"
internal const val STEP_FORWARD_LABEL = "10s ›"

/**
 * The merged Replay screen (playback control + the former Configure step).
 *
 * Playback is the hero: a big current-time readout, a draggable/tappable
 * timeline carrying yellow bookmark ticks, transport (‹10s / Play-Pause / 10s›),
 * a Mark/Loop/Clear row, a speed selector, a sensor strip (tap-to-cycle each
 * sensor's state when Separate sensors is on),
 * and a persistent full-width yellow To-ride bar.
 *
 * Two distinct exit paths, unchanged from before the redesign:
 *  - **To ride** → `moveTaskToBack`: minimize while the engine keeps streaming.
 *  - **Hardware back / bottom-left chevron** → [onBack]: pause the engine (keeping
 *    position) and pop to the ride picker, so re-selecting the ride reopens in place.
 */
@Composable
fun PlaybackScreen(viewModel: ReplayViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val markers by viewModel.markers.collectAsState()
    val loop by viewModel.loop.collectAsState()
    val currentRecord by viewModel.currentRecord.collectAsState()
    val sensorStates by viewModel.sensorStates.collectAsState()
    val separateSensors by viewModel.separateSensors.collectAsState()
    val selectedRide by viewModel.selectedRide.collectAsState()
    val loadStatus by viewModel.loadStatus.collectAsState()
    val totalSeconds = viewModel.totalSeconds
    val selectedPath = selectedRide?.file?.absolutePath
    val playbackReady = isPlaybackReady(loadStatus, selectedPath)

    val activity = LocalContext.current as? Activity
    BackHandler { onBack() }

    Column(modifier = Modifier.fillMaxSize().background(Karoo.Bg)) {
        // ── State header: REPLAY + state chip · short GUID ───────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("REPLAY", style = Karoo.sectionLabel)
                Spacer(Modifier.width(12.dp))
                StateChip(state)
            }
        }

        // ── Control area (single pane, no scroll — sized to the ~403dp canvas) ─
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Hero current time.
            Text(
                text = formatHHMMSS(elapsedSeconds),
                style = Karoo.hero.copy(fontSize = 46.sp),
            )
            Text(
                text = "of ${formatHHMMSS(totalSeconds)} · ${formatSpeed(playbackSpeed)}",
                style = Karoo.dataSm.copy(fontSize = 13.sp, color = Karoo.Grey3),
            )
            when (val status = loadStatus) {
                ReplayViewModel.LoadStatus.Idle -> Unit // Ride selection owns the idle prompt.
                is ReplayViewModel.LoadStatus.Loading -> Text(
                    text = "Loading ride…",
                    style = Karoo.dataSm.copy(color = Karoo.Grey2),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                is ReplayViewModel.LoadStatus.Loaded -> Unit // The hero shows loaded duration.
                is ReplayViewModel.LoadStatus.Error -> Text(
                    text = status.message,
                    style = Karoo.dataSm.copy(color = Karoo.Yellow),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }

            Timeline(
                elapsedSeconds = elapsedSeconds,
                totalSeconds = totalSeconds,
                markers = markers,
                onSeek = { viewModel.seek(it) },
                modifier = Modifier.padding(horizontal = 18.dp),
            )

            // Transport: ‹10s / Play-Pause / 10s›
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                KarooPill(
                    label = STEP_BACK_LABEL,
                    icon = null,
                    onClick = { viewModel.seek((elapsedSeconds - 10).coerceAtLeast(0)) },
                    color = Karoo.Grey1,
                    fontSize = 14,
                    heightDp = 38,
                )
                if (state == ReplayEngine.State.PLAYING) {
                    KarooPill(
                        label = "Pause",
                        icon = KarooIcons.Pause,
                        onClick = { viewModel.pause() },
                        solid = true,
                        color = Karoo.Yellow,
                        fontSize = 16,
                        heightDp = 42,
                    )
                } else {
                    KarooPill(
                        label = "Play",
                        icon = KarooIcons.Play,
                        onClick = { viewModel.play() },
                        solid = true,
                        color = Karoo.Green,
                        fontSize = 16,
                        heightDp = 42,
                        enabled = playbackReady,
                    )
                }
                KarooPill(
                    label = STEP_FORWARD_LABEL,
                    onClick = { viewModel.seek((elapsedSeconds + 10).coerceAtMost(totalSeconds)) },
                    color = Karoo.Grey1,
                    fontSize = 14,
                    heightDp = 38,
                )
            }

            // Markers: Mark / Loop / Clear
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KarooPill(
                    label = "Mark",
                    icon = KarooIcons.Pin,
                    onClick = { viewModel.addMarker() },
                    color = Karoo.Yellow,
                    fontSize = 14,
                    heightDp = 34,
                )
                KarooPill(
                    label = "Loop",
                    icon = KarooIcons.Loop,
                    onClick = { viewModel.toggleLoop() },
                    solid = loop,
                    color = Karoo.Green,
                    enabled = markers.size >= 2,
                    fontSize = 14,
                    heightDp = 34,
                )
                KarooPill(
                    label = "Clear",
                    icon = KarooIcons.Clear,
                    onClick = { viewModel.clearMarkers() },
                    color = Karoo.Grey1,
                    enabled = markers.isNotEmpty(),
                    fontSize = 14,
                    heightDp = 34,
                )
            }

            // Speed
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SPEED", style = Karoo.sectionLabel.copy(fontSize = 13.sp))
                listOf(1.0, 2.0, 5.0, 10.0).forEach { mult ->
                    KarooTag(
                        label = "${mult.toInt()}×",
                        selected = playbackSpeed == mult,
                        onClick = { viewModel.setSpeed(mult) },
                    )
                }
            }
        }

        // ── Streaming confirmation strip ─────────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Karoo.Divider))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Separate sensors: each readout is its sensor's control. Combined:
            // display-only, and always streaming (the combined device ignores states).
            fun stateOf(sensor: Sensor) =
                if (separateSensors) sensorStates.getValue(sensor) else SensorState.STREAMING
            fun cycle(sensor: Sensor): (() -> Unit)? =
                if (separateSensors) ({ viewModel.cycleSensorState(sensor) }) else null
            StreamStat("PWR", currentRecord?.power?.let { "$it" }, stateOf(Sensor.POWER), cycle(Sensor.POWER))
            StreamStat("HR", currentRecord?.heartRate?.let { "$it" }, stateOf(Sensor.HEART_RATE), cycle(Sensor.HEART_RATE))
            StreamStat("SPD", currentRecord?.speed?.let { formatOneDecimal(it * 3.6) }, stateOf(Sensor.SPEED), cycle(Sensor.SPEED))
            StreamStat("CAD", currentRecord?.cadence?.let { "$it" }, stateOf(Sensor.CADENCE), cycle(Sensor.CADENCE))
        }

        // ── To-ride bar (persistent primary action) ──────────────────────────
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Karoo.Divider))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Back to the picker — a bottom-left chevron corresponding to the
            // Karoo's bottom-left hardware button. Stops the engine and returns
            // to the ride list (distinct from "To ride", which minimizes and
            // keeps streaming). Mirrors the hardware-back path (onBack).
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KarooIcons.ChevronLeft,
                    contentDescription = "Back to rides",
                    tint = Karoo.Grey1,
                    modifier = Modifier.size(26.dp),
                )
            }
            KarooPill(
                label = "To ride",
                icon = KarooIcons.Nav,
                onClick = { activity?.moveTaskToBack(true) },
                solid = true,
                color = Karoo.Yellow,
                fontSize = 18,
                heightDp = 46,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StateChip(state: ReplayEngine.State) {
    val (color, word) = when (state) {
        ReplayEngine.State.PLAYING -> Karoo.Green to "PLAYING"
        ReplayEngine.State.PAUSED -> Karoo.Grey2 to "PAUSED"
        ReplayEngine.State.FINISHED -> Karoo.Yellow to "FINISHED"
        ReplayEngine.State.IDLE -> Karoo.Grey2 to "READY"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            text = word,
            style = Karoo.dataSm.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color),
        )
    }
}

/**
 * One sensor readout. Streaming shows the value; searching an amber pulsing
 * "···"; missing a grey "--". Tappable when [onTap] is set (Separate sensors).
 */
@Composable
private fun StreamStat(label: String, value: String?, state: SensorState, onTap: (() -> Unit)?) {
    Row(
        modifier = if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = Karoo.dataSm.copy(fontSize = 15.sp, color = Karoo.Grey3))
        when (state) {
            SensorState.STREAMING ->
                Text(value ?: "—", style = Karoo.dataSm.copy(fontSize = 15.sp, color = Karoo.White))
            SensorState.SEARCHING -> {
                val alpha by rememberInfiniteTransition(label = "searching").animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                    label = "searchingAlpha",
                )
                Text("···", style = Karoo.dataSm.copy(fontSize = 15.sp, color = Karoo.Yellow.copy(alpha = alpha)))
            }
            SensorState.MISSING ->
                Text("--", style = Karoo.dataSm.copy(fontSize = 15.sp, color = Karoo.Grey4))
        }
    }
}

/**
 * Draggable / tap-to-seek timeline. Fill, handle, marker ticks, and pointer seek
 * all use elapsed seconds divided by [totalSeconds]. The whole 44px-tall area
 * is the hit target.
 */
@Composable
private fun Timeline(
    elapsedSeconds: Long,
    totalSeconds: Long,
    markers: List<Long>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(46.dp)) {
        val widthPx = with(density) { maxWidth.toPx() }

        // While dragging, the handle follows the finger from LOCAL state (cheap),
        // and seeks are pushed to the engine only at a throttled cadence plus one
        // final seek on release. A raw per-touch-move seek pushed engine
        // StateFlows on every event → full-screen recomposition + a downstream
        // sensor-emission storm on the main thread (Choreographer "skipped N
        // frames" → ANR/crash), worst with markers on-screen. Throttling caps it.
        var scrubFraction by remember { mutableStateOf<Float?>(null) }
        var lastSeekMs by remember { mutableStateOf(0L) }
        val displayFraction = scrubFraction ?: timelineFraction(elapsedSeconds, totalSeconds)
        fun fracToSeconds(f: Float): Long = (f.coerceIn(0f, 1f) * totalSeconds).roundToLong()

        // Marker labels row (above the track).
        markers.forEachIndexed { i, sec ->
            val frac = timelineFraction(sec, totalSeconds)
            val xDp = with(density) { (frac * widthPx).toDp() }
            Text(
                text = "M${i + 1}",
                style = Karoo.dataSm.copy(fontSize = 11.sp, color = Karoo.Yellow),
                modifier = Modifier.offset(x = xDp - 8.dp, y = 0.dp),
            )
        }

        // The track + fill + ticks + handle, vertically centered in the hit area.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .align(Alignment.BottomCenter)
                .pointerInput(totalSeconds, widthPx) {
                    detectTapGestures { onSeek(timelineSeekSeconds(it.x, widthPx, totalSeconds)) }
                }
                .pointerInput(totalSeconds, widthPx) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            scrubFraction = (pos.x / widthPx).coerceIn(0f, 1f)
                            lastSeekMs = 0L
                        },
                        onDragEnd = {
                            scrubFraction?.let { onSeek(fracToSeconds(it)) }
                            scrubFraction = null
                        },
                        onDragCancel = { scrubFraction = null },
                    ) { change, _ ->
                        change.consume()
                        val f = (change.position.x / widthPx).coerceIn(0f, 1f)
                        scrubFraction = f
                        // Monotonic clock: wall time (currentTimeMillis) can step
                        // under NTP and stall or burst the throttle.
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastSeekMs >= 120L) {
                            onSeek(fracToSeconds(f))
                            lastSeekMs = now
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            // background track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Karoo.Divider),
            )
            // green fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(displayFraction)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Karoo.Green),
            )
            // marker ticks
            markers.forEach { sec ->
                val frac = timelineFraction(sec, totalSeconds)
                val xDp = with(density) { (frac * widthPx).toDp() }
                Box(
                    modifier = Modifier
                        .offset(x = xDp - 1.dp)
                        .width(2.dp)
                        .height(20.dp)
                        .background(Karoo.Yellow),
                )
            }
            // handle
            val handleDp = with(density) { (displayFraction * widthPx).toDp() }
            Box(
                modifier = Modifier
                    .offset(x = handleDp - 8.dp)
                    .size(width = 16.dp, height = 28.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
                    .background(Karoo.White),
            )
        }
    }
}

internal fun timelineFraction(elapsedSeconds: Long, totalSeconds: Long): Float {
    if (totalSeconds <= 0L) return 0f
    return (elapsedSeconds.toDouble() / totalSeconds.toDouble()).coerceIn(0.0, 1.0).toFloat()
}

internal fun timelineSeekSeconds(positionPx: Float, widthPx: Float, totalSeconds: Long): Long {
    if (widthPx <= 0f || totalSeconds <= 0L) return 0L
    val fraction = (positionPx / widthPx).coerceIn(0f, 1f)
    return (fraction * totalSeconds.toDouble()).roundToLong()
}

internal fun isPlaybackReady(
    loadStatus: ReplayViewModel.LoadStatus,
    selectedRidePath: String?,
): Boolean = loadStatus is ReplayViewModel.LoadStatus.Loaded &&
    loadStatus.ridePath == selectedRidePath

private fun formatSpeed(speed: Double): String =
    if (speed == speed.toLong().toDouble()) "${speed.toLong()}×" else "${formatOneDecimal(speed)}×"
