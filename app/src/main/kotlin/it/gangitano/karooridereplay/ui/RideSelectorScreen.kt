package it.gangitano.karooridereplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.gangitano.karooridereplay.data.FitFileRepository.FitFileEntry
import it.gangitano.karooridereplay.ui.theme.Karoo
import it.gangitano.karooridereplay.ui.theme.KarooIcons

/**
 * The Karoo-native ride picker. Each row leads with the FIT's human write-time,
 * a parsed `duration · distance · size` line, and a demoted GUID filename; the
 * loaded ride gets a full green row, and a star pins a ride. Near-empty files
 * are filtered out upstream ([FitFileRepository]), so every row is a real ride.
 * Tapping a row selects it and goes straight to Replay.
 */
@Composable
fun RideSelectorScreen(
    viewModel: ReplayViewModel,
    onRideSelected: (FitFileEntry) -> Unit,
) {
    val rides by viewModel.rideList.collectAsState()
    val summaries by viewModel.summaries.collectAsState()
    val starred by viewModel.starred.collectAsState()
    val selected by viewModel.selectedRide.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.scanRides(context) }

    // Starred rides pin to the top of the list; the rest follow. `rides` is
    // already newest-first from the repository, and partition preserves order,
    // so each group stays newest-first.
    val orderedRides = remember(rides, starred) {
        val (pinned, others) = rides.partition { it.file.absolutePath in starred }
        pinned + others
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Karoo.Bg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("SELECT RIDE", style = Karoo.sectionLabel)
            Text(
                text = "${rides.size} RIDES",
                style = Karoo.dataSm.copy(fontSize = 14.sp, color = Karoo.Grey3),
            )
        }

        if (rides.isEmpty()) {
            Text(
                text = "No FIT files found. Grant All-files access in Settings, then reopen.",
                style = Karoo.dataSm.copy(color = Karoo.Grey2),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(orderedRides, key = { it.file.absolutePath }) { entry ->
                    LaunchedEffect(entry.file.absolutePath) { viewModel.ensureSummary(entry) }
                    RideRow(
                        entry = entry,
                        summary = summaries[entry.file.absolutePath],
                        isLoaded = selected?.file?.absolutePath == entry.file.absolutePath,
                        isStarred = entry.file.absolutePath in starred,
                        onClick = { onRideSelected(entry) },
                        onToggleStar = {
                            val willPin = entry.file.absolutePath !in starred
                            viewModel.toggleStar(entry)
                            // Pinning moves the ride to index 0; LazyColumn keeps
                            // its scroll anchored to the old top item, so scroll
                            // up to reveal the newly-pinned ride instead of it
                            // slipping above the viewport.
                            if (willPin) scope.launch { listState.animateScrollToItem(0) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RideRow(
    entry: FitFileEntry,
    summary: ReplayViewModel.RideSummary?,
    isLoaded: Boolean,
    isStarred: Boolean,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    // The loaded ride gets a full green-tinted row, not just a dot.
                    isLoaded -> Karoo.Green.copy(alpha = 0.16f)
                    pressed -> Karoo.PressDark
                    else -> Karoo.Bg
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .heightIn(min = 76.dp)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Line 1 — write-time (primary), loaded dot, star.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (isLoaded) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Karoo.Green),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = formatWriteTime(entry.modifiedMs),
                style = Karoo.ridePrimary.copy(color = Karoo.White),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            // The star occupies an 18dp layout slot but its touch target is a
            // 44dp requiredSize box centered on it (Compose hit-tests children
            // beyond parent bounds). An 18dp clickable was well under the 48dp
            // accessibility minimum and flaked under real fingers on-device.
            Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .requiredSize(44.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleStar,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = KarooIcons.Star,
                        contentDescription = if (isStarred) "Unstar" else "Star",
                        tint = if (isStarred) Karoo.Yellow else Karoo.Grey5,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Line 2 — enrichment: duration · distance · size (duration highlighted white).
        // "…" = parse pending, "—" = the file failed to parse (cached, not retried).
        Row {
            Text(
                text = when {
                    summary == null -> "…"
                    summary.durationSeconds == null -> "—"
                    else -> formatHHMMSS(summary.durationSeconds)
                },
                style = Karoo.dataSm.copy(color = Karoo.White),
            )
            Text(
                text = "  ·  ${summary?.let { formatDistance(it.distanceMeters) } ?: "—"}  ·  ${entry.sizeLabel}",
                style = Karoo.dataSm.copy(color = Karoo.Grey2),
            )
        }

        // Line 3 — demoted GUID filename.
        Text(text = truncateGuid(entry.displayName), style = Karoo.dataXs)
    }
    // 1px hairline divider between rows.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Karoo.Divider),
    )
}
