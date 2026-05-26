package it.gangitano.karooridereplay.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.gangitano.karooridereplay.extension.KarooRideReplayExtension
import it.gangitano.karooridereplay.replay.FitRecord
import it.gangitano.karooridereplay.replay.ReplayEngine

/**
 * Active playback control: play / pause / stop / scrub + live record summary.
 * The record summary is the immediate feedback loop — riders can see whether
 * the FIT's values look sensible before they start trusting the simulator.
 *
 * The two "exit" paths are deliberately distinct:
 *  - System back gesture → [onBack]: stop the engine and pop the nav stack
 *    back to the ride picker, so the rider can choose a different FIT.
 *  - "To ride →" button → minimize-while-playing via moveTaskToBack, so the
 *    rider lands on the Karoo home with the simulator still streaming.
 */
@Composable
fun PlaybackScreen(viewModel: ReplayViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val elapsedSeconds by viewModel.elapsedSeconds.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val totalSeconds = viewModel.totalSeconds
    val currentRecord by (KarooRideReplayExtension.instance?.replayEngine?.currentRecord
        ?: kotlinx.coroutines.flow.MutableStateFlow<FitRecord?>(null)).collectAsState()

    // Karoo screens are narrow — wrap in scroll so transport buttons stay
    // reachable when the live-record summary card grows.
    val scrollState = rememberScrollState()

    // System back: return to the ride picker so the rider can choose a
    // different FIT. The "To ride →" button (below) still calls
    // moveTaskToBack for the keep-playing-and-minimize path.
    val activity = LocalContext.current as? Activity
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("Replay", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = { activity?.moveTaskToBack(true) }) {
                Text("To ride →")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Status: $state — ${playbackSpeed.toInt()}×",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${formatHHMMSS(elapsedSeconds)} / ${formatHHMMSS(totalSeconds)}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
                LinearProgressIndicator(
                    progress = { progress.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        }

        // Live record summary
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Current record", style = MaterialTheme.typography.titleMedium)
                RecordSummary(currentRecord)
            }
        }

        // Scrub bar — relabels in real time
        if (totalSeconds > 0) {
            Text("Scrub", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = elapsedSeconds.toFloat(),
                onValueChange = { viewModel.seek(it.toLong()) },
                valueRange = 0f..totalSeconds.toFloat()
            )
        }

        // Transport
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (state) {
                ReplayEngine.State.PLAYING -> {
                    OutlinedButton(
                        onClick = { viewModel.pause() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Pause") }
                }
                else -> {
                    Button(
                        onClick = { viewModel.play() },
                        modifier = Modifier.weight(1f)
                    ) { Text("Play") }
                }
            }
            OutlinedButton(
                onClick = { viewModel.stop() },
                modifier = Modifier.weight(1f)
            ) { Text("Stop") }
        }
    }
}

@Composable
private fun RecordSummary(record: FitRecord?) {
    if (record == null) {
        Text("No data — playback hasn't started.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column {
        SummaryRow("Position", record.lat?.let { "%.5f, %.5f".format(it, record.lng ?: 0.0) } ?: "—")
        SummaryRow("Altitude", record.altitude?.let { "%.1f m".format(it) } ?: "—")
        SummaryRow("Speed",    record.speed?.let { "%.1f m/s (%.1f km/h)".format(it, it * 3.6) } ?: "—")
        SummaryRow("Power",    record.power?.let { "$it W" } ?: "—")
        SummaryRow("Heart rate", record.heartRate?.let { "$it bpm" } ?: "—")
        SummaryRow("Cadence",  record.cadence?.let { "$it rpm" } ?: "—")
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}
