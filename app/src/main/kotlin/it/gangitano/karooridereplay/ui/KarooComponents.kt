package it.gangitano.karooridereplay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.gangitano.karooridereplay.ui.theme.Karoo

private val PillShape = RoundedCornerShape(50)

/**
 * A Karoo control pill. Solid fills with [color] (black content); outline draws
 * a 2px [color] border with [color] content. Press state darkens.
 */
@Composable
fun KarooPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
    color: Color = Karoo.White,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    fontSize: Int = 15,
    heightDp: Int = 44,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val effAlpha = if (enabled) 1f else 0.35f
    val bg = when {
        !solid -> if (pressed) Karoo.PressDark else Color.Transparent
        pressed && color == Karoo.Yellow -> Karoo.YellowPressed
        pressed -> color.copy(alpha = 0.85f)
        else -> color
    }
    val content = if (solid) Karoo.Bg else color

    Row(
        modifier = modifier
            .height(heightDp.dp)
            .clip(PillShape)
            .background(bg.copy(alpha = bg.alpha * effAlpha))
            .let { if (!solid) it.border(2.dp, color.copy(alpha = effAlpha), PillShape) else it }
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content.copy(alpha = effAlpha),
                modifier = Modifier.size((fontSize + 3).dp),
            )
        }
        Text(
            text = label,
            style = Karoo.dataSm.copy(
                fontSize = fontSize.sp,
                fontWeight = FontWeight.Medium,
                color = content.copy(alpha = effAlpha),
            ),
        )
    }
}

/** A selectable Karoo tag (speed picker). Filled when [selected], outline otherwise. */
@Composable
fun KarooTag(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(PillShape)
            .background(if (selected) Karoo.White else Color.Transparent)
            .let { if (!selected) it.border(2.dp, Karoo.Grey5, PillShape) else it }
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = Karoo.dataSm.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) Karoo.Bg else Karoo.Grey1,
            ),
        )
    }
}
