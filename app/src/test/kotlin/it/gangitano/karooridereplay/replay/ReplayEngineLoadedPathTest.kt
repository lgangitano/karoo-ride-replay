package it.gangitano.karooridereplay.replay

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The engine owns which file is loaded, so the picker and adb commands agree on
 * the active ride: each load replaces the path, and an empty ride clears it.
 */
class ReplayEngineLoadedPathTest {

    private val engine = ReplayEngine()
    private val ride = listOf(FitRecord(timestampMs = 0L), FitRecord(timestampMs = 1000L))

    @After fun tearDown() = engine.destroy()

    @Test fun `nothing is loaded at start`() {
        assertNull(engine.loadedPath.value)
    }

    @Test fun `loading a ride publishes its source`() {
        engine.load(ride, "/sdcard/FitFiles/a.fit")
        assertEquals("/sdcard/FitFiles/a.fit", engine.loadedPath.value)
        engine.load(ride, "/sdcard/FitFiles/b.fit")
        assertEquals("/sdcard/FitFiles/b.fit", engine.loadedPath.value)
    }

    @Test fun `loading without a source or an empty ride clears the path`() {
        engine.load(ride, "/sdcard/FitFiles/a.fit")
        engine.load(ride)
        assertNull(engine.loadedPath.value)
        engine.load(ride, "/sdcard/FitFiles/a.fit")
        engine.load(emptyList(), "/sdcard/FitFiles/a.fit")
        assertNull(engine.loadedPath.value)
    }
}
