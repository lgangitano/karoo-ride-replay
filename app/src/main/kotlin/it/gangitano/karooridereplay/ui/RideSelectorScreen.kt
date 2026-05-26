package it.gangitano.karooridereplay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.gangitano.karooridereplay.data.FitFileRepository.FitFileEntry

/**
 * Lists FIT files found on the Karoo's storage. Tap a row to select it for
 * replay. Implements Luigi's spec 2a — "load any recorded ride from the Karoo
 * itself and re-play it" — by scanning the well-known FitFiles/ folder.
 */
@Composable
fun RideSelectorScreen(
    viewModel: ReplayViewModel,
    onRideSelected: (FitFileEntry) -> Unit
) {
    val rides by viewModel.rideList.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.scanRides(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Select a ride to replay",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Scanning Karoo's FitFiles/ folder + Downloads",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        if (rides.isEmpty()) {
            Text(
                text = "No FIT files found yet. Grant storage permission in Settings if you haven't.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Button(
                onClick = { viewModel.scanRides(context) },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Rescan")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rides) { entry ->
                    RideRow(entry = entry, onClick = { onRideSelected(entry) })
                }
            }
        }
    }
}

@Composable
private fun RideRow(entry: FitFileEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.modifiedLabel,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = entry.sizeLabel,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
