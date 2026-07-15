package it.gangitano.karooridereplay.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The Hammerhead Karoo Visual Data Field System, expressed as Compose tokens.
 *
 * Values are lifted verbatim from the design handoff's Design Tokens table
 * (`~/Downloads/design_handoff_replay_redesign/README.md`). The screens pull
 * colors and text styles from here directly rather than styling ad hoc, so the
 * whole app inherits the Karoo look.
 *
 * Fonts: the real device faces (Ping, Hammerhead Relative Mono) are licensed
 * and not bundled in this repo. Per the handoff, we fall back to the platform
 * families — [FontFamily.Monospace] for all numerals / data / pill text (the
 * load-bearing typographic signal on a Karoo) and [FontFamily.Default] for UI
 * prose. Drop the real faces in [uiFont] / [dataFont] when they're available.
 */
object Karoo {
    // ── Color (handoff Design Tokens) ────────────────────────────────────────
    val Bg = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)

    /** Labels & icons. */
    val PowderBlue = Color(0xFFA0B4BE)

    // Muted grey ramp, light → dark.
    val Grey1 = Color(0xFFBCBCBC)
    val Grey2 = Color(0xFF979797)
    val Grey3 = Color(0xFF7D7D7D) // empty / no-sensor / muted default
    val Grey4 = Color(0xFF636363)
    val Grey5 = Color(0xFF484848)
    val Divider = Color(0xFF2A2A2A)
    val Grey7 = Color(0xFF1A1A1A)

    /** Live data / play. */
    val Green = Color(0xFF0EFF00)

    /** Routing / nav / pause / markers. */
    val Yellow = Color(0xFFFFE900)
    val YellowPressed = Color(0xFFE4D420)

    /** Error / negative. */
    val Red = Color(0xFFFF5252)

    /** Elevation. */
    val Aegean = Color(0xFF214559)

    /** Press / tile-dark. */
    val PressDark = Color(0xFF141414)

    // ── Type families (fallbacks; see class doc) ─────────────────────────────
    val uiFont = FontFamily.Default
    val dataFont = FontFamily.Monospace

    // ── Text styles (roles from the handoff) ─────────────────────────────────

    /** Section label: mono-adjacent uppercase, tracked. */
    val sectionLabel = TextStyle(
        fontFamily = uiFont,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.12.em,
        color = PowderBlue,
    )

    /** Primary ride time — Ping 20px. */
    val ridePrimary = TextStyle(
        fontFamily = uiFont,
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium,
        color = White,
    )

    /** Row enrichment / small data lines. */
    val dataSm = TextStyle(fontFamily = dataFont, fontSize = 14.sp, color = Grey2)

    /** Demoted GUID line. */
    val dataXs = TextStyle(fontFamily = dataFont, fontSize = 12.sp, color = Grey4)

    /** Hero replay time. */
    val hero = TextStyle(
        fontFamily = dataFont,
        fontSize = 84.sp,
        fontWeight = FontWeight.Medium,
        color = White,
        letterSpacing = (-0.02).em,
    )
}

/**
 * App theme wrapper. Uses a dark Material scheme keyed to the Karoo palette so
 * any residual Material 3 component (ripples, text-field internals) inherits the
 * black/green/yellow look, and sets a mono-default Typography for numerals.
 */
@Composable
fun KarooTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Karoo.Green,
        onPrimary = Karoo.Bg,
        secondary = Karoo.Yellow,
        onSecondary = Karoo.Bg,
        background = Karoo.Bg,
        onBackground = Karoo.White,
        surface = Karoo.Bg,
        onSurface = Karoo.White,
        error = Karoo.Red,
        outline = Karoo.Divider,
    )
    MaterialTheme(
        colorScheme = colors,
        shapes = MaterialTheme.shapes.copy(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(8.dp),
        ),
        typography = Typography(),
        content = content,
    )
}
