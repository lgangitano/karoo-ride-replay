package it.gangitano.karooridereplay.replay

import it.gangitano.karooridereplay.replay.ReplayEngine.Companion.loopWrapTarget
import it.gangitano.karooridereplay.replay.ReplayEngine.Companion.restartElapsedSeconds
import it.gangitano.karooridereplay.replay.ReplayEngine.Companion.seekExitsLoop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure loop-between-markers decision. This is the one piece
 * of genuinely new engine logic the redesign adds, so it earns a direct test
 * independent of the playback coroutine.
 */
class ReplayEngineLoopTest {

    private val span = listOf(100L, 300L)

    @Test fun `no wrap when loop is off`() {
        assertNull(loopWrapTarget(elapsedSeconds = 350, markers = span, loop = false))
    }

    @Test fun `no wrap with fewer than two markers`() {
        assertNull(loopWrapTarget(elapsedSeconds = 350, markers = listOf(100L), loop = true))
        assertNull(loopWrapTarget(elapsedSeconds = 350, markers = emptyList(), loop = true))
    }

    @Test fun `no wrap before reaching the upper marker`() {
        assertNull(loopWrapTarget(elapsedSeconds = 299, markers = span, loop = true))
    }

    @Test fun `wraps to the lower marker at the upper marker`() {
        assertEquals(100L, loopWrapTarget(elapsedSeconds = 300, markers = span, loop = true))
    }

    @Test fun `wraps to the lower marker past the upper marker`() {
        assertEquals(100L, loopWrapTarget(elapsedSeconds = 999, markers = span, loop = true))
    }

    @Test fun `uses outer span with more than two markers`() {
        val markers = listOf(300L, 100L, 220L) // unsorted; span is 100..300
        assertEquals(100L, loopWrapTarget(elapsedSeconds = 300, markers = markers, loop = true))
        assertNull(loopWrapTarget(elapsedSeconds = 250, markers = markers, loop = true))
    }

    @Test fun `no wrap when the two markers coincide`() {
        assertNull(loopWrapTarget(elapsedSeconds = 500, markers = listOf(100L, 100L), loop = true))
    }

    @Test fun `restart target is zero without an active two-marker loop`() {
        assertEquals(0L, restartElapsedSeconds(markers = span, loop = false))
        assertEquals(0L, restartElapsedSeconds(markers = listOf(100L), loop = true))
        assertEquals(0L, restartElapsedSeconds(markers = emptyList(), loop = true))
    }

    @Test fun `restart target is the lower marker for an active loop`() {
        assertEquals(100L, restartElapsedSeconds(markers = listOf(300L, 100L), loop = true))
    }

    @Test fun `restart from end emits the ride start immediately`() {
        val engine = ReplayEngine()
        try {
            engine.load(recordsAtSeconds(0L, 10L, 30L, 60L))
            engine.seek(60L)

            engine.play()

            assertEquals(0L, engine.elapsedSeconds.value)
            assertEquals(BASE_TIMESTAMP_MS, engine.currentRecord.value?.timestampMs)
        } finally {
            engine.destroy()
        }
    }

    @Test fun `seekExitsLoop is true only outside the inclusive marker window`() {
        val m = listOf(100L, 300L)
        assertFalse(seekExitsLoop(100L, m))  // at lower — inside
        assertFalse(seekExitsLoop(300L, m))  // at upper — inside
        assertFalse(seekExitsLoop(200L, m))  // within
        assertTrue(seekExitsLoop(99L, m))    // before lower
        assertTrue(seekExitsLoop(301L, m))   // past upper
        assertFalse(seekExitsLoop(50L, listOf(100L))) // fewer than two markers
    }

    @Test fun `seeking out of the loop window disables loop and inside keeps it`() {
        val engine = ReplayEngine()
        try {
            engine.load(recordsAtSeconds(0L, 10L, 30L, 60L))
            engine.seek(10L); engine.addMarker()
            engine.seek(30L); engine.addMarker()
            engine.toggleLoop()
            assertTrue("loop arms with two markers", engine.loop.value)

            engine.seek(20L) // inside [10,30]
            assertTrue("inside-window seek keeps loop", engine.loop.value)

            engine.seek(60L) // past the upper marker
            assertFalse("out-of-window seek exits loop", engine.loop.value)
        } finally {
            engine.destroy()
        }
    }

    private fun recordsAtSeconds(vararg seconds: Long): List<FitRecord> =
        seconds.map { FitRecord(timestampMs = BASE_TIMESTAMP_MS + it * 1_000L) }

    companion object {
        private const val BASE_TIMESTAMP_MS = 1_000_000L
    }
}
