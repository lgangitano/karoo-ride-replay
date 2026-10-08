package it.gangitano.karooridereplay.fields

import java.util.Locale

/** Playback multipliers offered by both the app UI and the ride-screen replay field. */
val SPEED_STEPS = listOf(1.0, 2.0, 5.0, 10.0)

const val STEP_BACK_LABEL = "‹ 10s"
const val STEP_FORWARD_LABEL = "10s ›"
const val SKIP_SECONDS = 10L

// Karoo's ride-profile grid is 60 columns wide, so 30 is exactly half width.
private const val HALF_GRID_WIDTH = 30

private const val SPEED_TOLERANCE = 1e-6
private val DATA_LOCALE = Locale.US

/** Return the next configured speed strictly above [current], wrapping to 1×. */
fun nextSpeed(current: Double): Double =
    SPEED_STEPS.firstOrNull { it > current + SPEED_TOLERANCE } ?: SPEED_STEPS.first()

/** Use the control bar only when the field is wider than half of Karoo's 60-column grid. */
fun isControlBar(gridWidth: Int): Boolean = gridWidth > HALF_GRID_WIDTH

// Clock sizing. Measured on a Karoo 3 (480x800 px, 300 dpi = 256x427 dp).
private const val CONTROL_BAR_TARGETS_DP = 4 * 40 // ‹ 10s, play/pause, 10s ›, speed
private const val NARROW_CHROME_DP = 8 + 24 + 6 // horizontal padding + action icon + its margin
private const val HEADER_DP = 34f // Karoo-drawn field header: ~60 px = 32 dp on a Karoo 3, plus margin
private const val MONO_ADVANCE_EM = 0.6f // Android's monospace digit advance
private const val CLOCK_BLOCK_EM = 1.7f // clock line + the ~0.45x state line beneath it
private const val CLOCK_MAX_SP = 28f
private const val CLOCK_MIN_SP = 10f

/**
 * Largest clock text size that fits [widthDp] x [heightDp] together with the
 * small state line under it, capped at [maxSp]. The Karoo's own text size is
 * sized for ~4 glyphs ("121 W"); the 7-8 glyph clock needs its own fit.
 */
fun fitClockSp(widthDp: Float, heightDp: Float, clockGlyphs: Int, maxSp: Float): Float {
    val byWidth = widthDp / (clockGlyphs * MONO_ADVANCE_EM)
    val byHeight = heightDp / CLOCK_BLOCK_EM
    return minOf(byWidth, byHeight, maxSp).coerceAtLeast(CLOCK_MIN_SP)
}

/**
 * Clock size in the full-width control bar: the width the four tap targets
 * leave (~96 dp on a 256 dp screen) and the height below the Karoo-drawn header,
 * capped at the field's text size and 28 sp.
 * [clockGlyphs] is the longest clock the ride will show (8 from 10 h).
 */
fun controlBarClockSp(fieldWidthDp: Float, fieldHeightDp: Float, fieldTextSizeSp: Int, clockGlyphs: Int = 7): Float =
    fitClockSp(
        widthDp = fieldWidthDp - CONTROL_BAR_TARGETS_DP,
        heightDp = fieldHeightDp - HEADER_DP,
        clockGlyphs = clockGlyphs,
        maxSp = minOf(fieldTextSizeSp.toFloat(), CLOCK_MAX_SP),
    )

/**
 * Clock size in the half-width layout, below the Karoo-drawn header and beside
 * the action icon. Without this the Karoo's native text size clipped the clock
 * to "0:" in a half-width cell (seen on a Karoo 3).
 */
fun narrowClockSp(fieldWidthDp: Float, fieldHeightDp: Float, fieldTextSizeSp: Int, clockGlyphs: Int = 7): Float =
    fitClockSp(
        widthDp = fieldWidthDp - NARROW_CHROME_DP,
        heightDp = fieldHeightDp - HEADER_DP,
        clockGlyphs = clockGlyphs,
        maxSp = fieldTextSizeSp.toFloat(),
    )

/** Apply a relative seek while keeping the result within the loaded ride. */
fun skipTarget(elapsed: Long, delta: Long, total: Long): Long =
    // Not coerceIn(0, total): it throws when total < 0, which a FIT whose last
    // record predates its first would produce — on a tap, on the main thread.
    (elapsed + delta).coerceAtMost(total).coerceAtLeast(0)

/** Format a playback multiplier with at most two dot-decimal places. */
fun formatSpeed(multiplier: Double): String =
    "%.2f".format(DATA_LOCALE, multiplier).trimEnd('0').trimEnd('.') + "×"

/** Format elapsed seconds as a ride clock such as `1:24:35`. */
fun formatHHMMSS(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%d:%02d:%02d".format(DATA_LOCALE, h, m, s)
}
