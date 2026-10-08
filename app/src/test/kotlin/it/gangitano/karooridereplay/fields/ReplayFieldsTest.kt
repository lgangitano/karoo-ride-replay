package it.gangitano.karooridereplay.fields

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayFieldsTest {

    @Test
    fun `nextSpeed advances through every configured step and wraps`() {
        assertEquals(2.0, nextSpeed(1.0), 0.0)
        assertEquals(5.0, nextSpeed(2.0), 0.0)
        assertEquals(10.0, nextSpeed(5.0), 0.0)
        assertEquals(1.0, nextSpeed(10.0), 0.0)
    }

    @Test
    fun `nextSpeed advances from off-step values`() {
        assertEquals(1.0, nextSpeed(0.5), 0.0)
        assertEquals(5.0, nextSpeed(3.0), 0.0)
        assertEquals(10.0, nextSpeed(7.5), 0.0)
        assertEquals(1.0, nextSpeed(50.0), 0.0)
    }

    @Test
    fun `nextSpeed treats floating point noise as the matching step`() {
        assertEquals(5.0, nextSpeed(2.0 - 0.5e-6), 0.0)
        assertEquals(5.0, nextSpeed(2.0 + 0.5e-6), 0.0)
    }

    @Test
    fun `formatSpeed trims decimal zeroes and keeps two decimal places at most`() {
        assertEquals("1×", formatSpeed(1.0))
        assertEquals("10×", formatSpeed(10.0))
        assertEquals("0.5×", formatSpeed(0.5))
        assertEquals("2.5×", formatSpeed(2.5))
        assertEquals("2.25×", formatSpeed(2.25))
    }

    @Test
    fun `control bar is used only above half of the 60 column grid`() {
        assertFalse(isControlBar(15))
        assertFalse(isControlBar(30))
        assertTrue(isControlBar(31))
        assertTrue(isControlBar(60))
    }

    @Test
    fun `skip target clamps and handles normal ten second steps`() {
        assertEquals(0L, skipTarget(5L, -10L, 100L))
        assertEquals(100L, skipTarget(95L, 10L, 100L))
        assertEquals(40L, skipTarget(50L, -10L, 100L))
        assertEquals(60L, skipTarget(50L, 10L, 100L))
        assertEquals(0L, skipTarget(0L, 10L, 0L))
    }

    @Test
    fun `control bar clock fits the width left by the tap targets`() {
        // Karoo 3/2: 256 dp wide leaves 96 dp for 7 glyphs -> ~22.9 sp.
        val sp = controlBarClockSp(256f, 80f, 50)
        assertEquals(22.9f, sp, 0.1f)
        assertTrue(sp * 7 * 0.6f <= 256f - 4 * 40)
    }

    @Test
    fun `clock is capped by text size, 28 sp and height, floored at 10 sp`() {
        assertEquals(28f, controlBarClockSp(600f, 200f, 40), 0f)
        // One-row full-width cell on a Karoo 3 (78 dp): the bar still gets ~22 sp.
        assertTrue(controlBarClockSp(256f, 78f, 50) >= 20f)
        assertEquals(16f, controlBarClockSp(600f, 200f, 16), 0f)
        assertEquals(10f, controlBarClockSp(150f, 80f, 30), 0f)
        // A short row limits the size before the width does (34 dp header above the bar).
        assertEquals((60f - 34) / 1.7f, controlBarClockSp(600f, 60f, 40), 0.01f)
    }

    @Test
    fun `half-width clock fits the Karoo 3 cell instead of the native text size`() {
        // Measured half-width cell: 240x147 px at 300 dpi = 128x78 dp; native size ~50 sp.
        val sp = narrowClockSp(128f, 78f, 50)
        assertTrue("got $sp", sp in 18f..24f)
        assertTrue(sp * 7 * 0.6f <= 128f - 38)
    }

    @Test
    fun `skipTarget never throws on a negative total`() {
        assertEquals(0L, skipTarget(5, 10, -3))
        assertEquals(0L, skipTarget(5, -10, -3))
    }

    @Test
    fun `control bar clock shrinks for rides of 10 hours or more`() {
        val longRide = controlBarClockSp(256f, 80f, 50, clockGlyphs = 8)
        assertTrue(longRide < controlBarClockSp(256f, 80f, 50))
        assertTrue(longRide * 8 * 0.6f <= 256f - 4 * 40)
    }
}
