package it.gangitano.karooridereplay.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import it.gangitano.karooridereplay.ui.theme.Karoo
import it.gangitano.karooridereplay.ui.theme.KarooIcons

/**
 * Settings, reached from the gear on the ride picker. One switch today:
 * "Separate sensors" publishes one virtual device per sensor, so each can be
 * set searching or missing from the Replay screen's sensor strip.
 */
@Composable
fun SettingsScreen(viewModel: ReplayViewModel, onBack: () -> Unit) {
    val separateSensors by viewModel.separateSensors.collectAsState()
    BackHandler { onBack() }

    Column(modifier = Modifier.fillMaxSize().background(Karoo.Bg)) {
        Text(
            "SETTINGS",
            style = Karoo.sectionLabel,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp),
        )
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Karoo.Divider))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.setSeparateSensors(!separateSensors) }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Separate sensors", style = Karoo.ridePrimary)
            Switch(
                checked = separateSensors,
                onCheckedChange = viewModel::setSeparateSensors,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Karoo.Bg,
                    checkedTrackColor = Karoo.Green,
                    uncheckedThumbColor = Karoo.Grey1,
                    uncheckedTrackColor = Karoo.Grey5,
                    uncheckedBorderColor = Karoo.Grey5,
                ),
            )
        }
        Text(
            text = "Pair the four Replay sensors and unpair Karoo Ride Replay " +
                "(or the reverse), or the Karoo sees two sources.",
            style = Karoo.dataSm.copy(color = Karoo.Grey2),
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        // Back to the picker — bottom-left chevron, as on the Replay screen.
        Box(
            modifier = Modifier
                .padding(start = 6.dp, bottom = 10.dp)
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
    }
}
