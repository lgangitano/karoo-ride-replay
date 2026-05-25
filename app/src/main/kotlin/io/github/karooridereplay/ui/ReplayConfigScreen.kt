package io.github.karooridereplay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Configures the replay before starting playback. Spec 2b — set the start
 * point as hh:mm:ss so the user can skip directly to the interesting climb —
 * plus a playback-speed picker so a 5-hour ride can be exercised in an hour.
 */
@Composable
fun ReplayConfigScreen(
    viewModel: ReplayViewModel,
    onBeginPlayback: () -> Unit
) {
    val selectedRide by viewModel.selectedRide.collectAsState()
    val loadStatus by viewModel.loadStatus.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()

    var startHours by remember { mutableStateOf("0") }
    var startMinutes by remember { mutableStateOf("0") }
    var startSeconds by remember { mutableStateOf("0") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Configure replay", style = MaterialTheme.typography.titleLarge)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = selectedRide?.displayName ?: "(no ride selected)",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = when (val s = loadStatus) {
                        is ReplayViewModel.LoadStatus.Idle -> "Pick a ride first"
                        is ReplayViewModel.LoadStatus.Loading -> "Loading…"
                        is ReplayViewModel.LoadStatus.Loaded ->
                            "${s.recordCount} records, ${formatHHMMSS(s.totalSeconds)} total"
                        is ReplayViewModel.LoadStatus.Error -> "Error: ${s.message}"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Text("Start at (hh : mm : ss)", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = startHours,
                onValueChange = { startHours = it.filter(Char::isDigit).take(2) },
                label = { Text("hh") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = startMinutes,
                onValueChange = { startMinutes = it.filter(Char::isDigit).take(2) },
                label = { Text("mm") },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = startSeconds,
                onValueChange = { startSeconds = it.filter(Char::isDigit).take(2) },
                label = { Text("ss") },
                modifier = Modifier.weight(1f)
            )
        }

        Text("Playback speed: %.1f×".format(playbackSpeed),
            style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1.0, 2.0, 5.0, 10.0).forEach { multiplier ->
                Button(
                    onClick = { viewModel.setSpeed(multiplier) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("${multiplier.toInt()}×")
                }
            }
        }

        Button(
            onClick = {
                val seek = (startHours.toLongOrNull() ?: 0L) * 3600L +
                    (startMinutes.toLongOrNull() ?: 0L) * 60L +
                    (startSeconds.toLongOrNull() ?: 0L)
                viewModel.seek(seek)
                onBeginPlayback()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = loadStatus is ReplayViewModel.LoadStatus.Loaded
        ) {
            Text("Begin playback")
        }
    }
}

internal fun formatHHMMSS(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%d:%02d:%02d".format(h, m, s)
}
