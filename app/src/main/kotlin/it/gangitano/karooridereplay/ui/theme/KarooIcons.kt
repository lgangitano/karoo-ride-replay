package it.gangitano.karooridereplay.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The handful of single-color glyphs the redesign needs, drawn as [ImageVector]s
 * on a 24×24 viewport so an `Icon(tint = …)` recolors them to `currentColor`.
 *
 * The Karoo brand SVG set isn't bundled in this repo, so these are faithful
 * stand-ins built in-tree — no emoji, no unicode glyphs, no extra dependency.
 * The filled play / up-triangle nav match the `▶` / `▲` glyphs in the handoff
 * mockups directly.
 */
object KarooIcons {

    val Play: ImageVector = filled("play") {
        moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close()
    }

    val Pause: ImageVector = filled("pause") {
        moveTo(6f, 5f); lineTo(10f, 5f); lineTo(10f, 19f); lineTo(6f, 19f); close()
        moveTo(14f, 5f); lineTo(18f, 5f); lineTo(18f, 19f); lineTo(14f, 19f); close()
    }

    /** Up-triangle — the "▲ To ride" glyph. */
    val Nav: ImageVector = filled("nav") {
        moveTo(12f, 5f); lineTo(20f, 17f); lineTo(4f, 17f); close()
    }

    /** Map-pin: filled head + point. */
    val Pin: ImageVector = filled("pin") {
        // head
        moveTo(12f, 3f)
        arcTo(5f, 5f, 0f, true, true, 11.99f, 3f)
        close()
        // point
        moveTo(8f, 11f); lineTo(16f, 11f); lineTo(12f, 21f); close()
    }

    /** Five-point star (pinned ride marker). */
    val Star: ImageVector = filled("star") {
        moveTo(12f, 3f)
        lineTo(14.12f, 9.09f); lineTo(20.56f, 9.22f); lineTo(15.42f, 13.11f)
        lineTo(17.29f, 19.28f); lineTo(12f, 15.6f); lineTo(6.71f, 19.28f)
        lineTo(8.58f, 13.11f); lineTo(3.44f, 9.22f); lineTo(9.88f, 9.09f)
        close()
    }

    /** Left chevron — back-to-picker affordance (bottom-left hardware button). */
    val ChevronLeft: ImageVector = stroked("chevronLeft") {
        moveTo(15f, 6f); lineTo(9f, 12f); lineTo(15f, 18f)
    }

    val Clear: ImageVector = stroked("clear") {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }

    /** Loop: near-full circle with a small arrowhead at the top. */
    val Loop: ImageVector = stroked("loop") {
        moveTo(19f, 12f)
        arcTo(7f, 7f, 0f, true, false, 12f, 19f)
        // arrowhead pointing into the gap at 3 o'clock
        moveTo(15.5f, 9f); lineTo(19f, 12f); lineTo(20f, 8.2f)
    }

    /** Settings gear: a ring and hub with eight teeth. */
    val Gear: ImageVector = stroked("gear") {
        // ring (r = 6) and hub (r = 2.5)
        moveTo(12f, 6f); arcTo(6f, 6f, 0f, true, true, 11.99f, 6f)
        moveTo(12f, 9.5f); arcTo(2.5f, 2.5f, 0f, true, true, 11.99f, 9.5f)
        // teeth: eight radial strokes from the ring outward
        for (i in 0 until 8) {
            val a = i * PI / 4
            moveTo(12f + 6f * cos(a).toFloat(), 12f + 6f * sin(a).toFloat())
            lineTo(12f + 9f * cos(a).toFloat(), 12f + 9f * sin(a).toFloat())
        }
    }

    private fun filled(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White), pathBuilder = block)
        }.build()

    private fun stroked(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                pathBuilder = block,
            )
        }.build()
}
