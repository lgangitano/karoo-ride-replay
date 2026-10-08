package it.gangitano.karooridereplay.remote

import it.gangitano.karooridereplay.replay.ReplayEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the Android-free adb command parser and reply formatting. */
class AdbCommandsTest {

    @Test fun `each supported action parses`() {
        assertEquals(
            Parsed.Ok(AdbCommand.Load("FitFiles/ride.fit")),
            parse(ACTION_LOAD, EXTRA_FILE to "FitFiles/ride.fit"),
        )
        assertEquals(Parsed.Ok(AdbCommand.Play), parse(ACTION_PLAY))
        assertEquals(Parsed.Ok(AdbCommand.Pause), parse(ACTION_PAUSE))
        assertEquals(
            Parsed.Ok(AdbCommand.Seek(1800L)),
            parse(ACTION_SEEK, EXTRA_SECONDS to 1800L),
        )
        assertEquals(
            Parsed.Ok(AdbCommand.Speed(5.0)),
            parse(ACTION_SPEED, EXTRA_MULTIPLIER to 5.0),
        )
        assertEquals(Parsed.Ok(AdbCommand.Status), parse(ACTION_STATUS))
        assertEquals(Parsed.Ok(AdbCommand.Exit), parse(ACTION_EXIT))
    }

    @Test fun `numeric extras accept adb number types and strings`() {
        assertEquals(5.0, numericExtra(5)!!, 0.0)
        assertEquals(5.0, numericExtra(5L)!!, 0.0)
        assertEquals(5.5, numericExtra(5.5f)!!, 0.0)
        assertEquals(5.5, numericExtra(5.5)!!, 0.0)
        assertEquals(5.5, numericExtra(" 5.5 ")!!, 0.0)
    }

    @Test fun `numeric extras reject unsupported and non-finite values`() {
        assertNull(numericExtra(null))
        assertNull(numericExtra("not-a-number"))
        assertNull(numericExtra(Double.NaN))
        assertNull(numericExtra(Double.POSITIVE_INFINITY))
    }

    @Test fun `seek rejects missing garbage and negative values`() {
        assertInvalid(parse(ACTION_SEEK))
        assertInvalid(parse(ACTION_SEEK, EXTRA_SECONDS to "later"))
        assertInvalid(parse(ACTION_SEEK, EXTRA_SECONDS to -1L))
    }

    @Test fun `seek truncates fractional seconds toward zero`() {
        assertEquals(
            Parsed.Ok(AdbCommand.Seek(12L)),
            parse(ACTION_SEEK, EXTRA_SECONDS to 12.9),
        )
    }

    @Test fun `speed rejects missing garbage zero and negative values`() {
        assertInvalid(parse(ACTION_SPEED))
        assertInvalid(parse(ACTION_SPEED, EXTRA_MULTIPLIER to "fast"))
        assertInvalid(parse(ACTION_SPEED, EXTRA_MULTIPLIER to 0))
        assertInvalid(parse(ACTION_SPEED, EXTRA_MULTIPLIER to -2.0))
    }

    @Test fun `load rejects a missing or blank file`() {
        assertInvalid(parse(ACTION_LOAD))
        assertInvalid(parse(ACTION_LOAD, EXTRA_FILE to "  "))
    }

    @Test fun `unknown action is rejected`() {
        assertInvalid(parse("${ACTION_PREFIX}BOGUS"))
        assertInvalid(parse(null))
    }

    @Test fun `relative path resolves below external storage root`() {
        assertEquals(
            "/storage/emulated/0/FitFiles/ride.fit",
            resolveRidePath("FitFiles/ride.fit", "/storage/emulated/0"),
        )
    }

    @Test fun `absolute path remains unchanged`() {
        assertEquals(
            "/data/local/tmp/ride.fit",
            resolveRidePath("/data/local/tmp/ride.fit", "/storage/emulated/0"),
        )
    }

    @Test fun `symlinked storage paths take the picker's form`() {
        assertEquals(
            "/storage/emulated/0/FitFiles/ride.fit",
            inStorageRootForm(
                "/data/media/0/FitFiles/ride.fit",
                "/data/media/0",
                "/storage/emulated/0",
            ),
        )
        assertEquals(
            "/storage/emulated/0/FitFiles/ride.fit",
            inStorageRootForm(
                "/storage/emulated/0/FitFiles/ride.fit",
                "/storage/emulated/0",
                "/storage/emulated/0",
            ),
        )
    }

    @Test fun `paths outside storage stay canonical`() {
        assertEquals(
            "/data/local/tmp/ride.fit",
            inStorageRootForm("/data/local/tmp/ride.fit", "/storage/emulated/0", "/storage/emulated/0"),
        )
        // A sibling directory sharing the root's name prefix is not inside it.
        assertEquals(
            "/storage/emulated/01/ride.fit",
            inStorageRootForm("/storage/emulated/01/ride.fit", "/storage/emulated/0", "/storage/emulated/0"),
        )
    }

    @Test fun `status line includes file and dot decimal speed`() {
        assertEquals(
            "state=PLAYING elapsed=1800 total=5018 speed=2.5 " +
                "file=/storage/emulated/0/FitFiles/ride.fit",
            statusLine(
                state = ReplayEngine.State.PLAYING,
                elapsed = 1800,
                total = 5018,
                speed = 2.5,
                file = "/storage/emulated/0/FitFiles/ride.fit",
            ),
        )
    }

    @Test fun `status line keeps fractional speed exact`() {
        assertEquals(
            "state=PAUSED elapsed=0 total=60 speed=0.25 file=-",
            statusLine(ReplayEngine.State.PAUSED, 0, 60, 0.25, null),
        )
    }

    @Test fun `status line uses dash without a file`() {
        assertEquals(
            "state=IDLE elapsed=0 total=0 speed=1.0 file=-",
            statusLine(ReplayEngine.State.IDLE, 0, 0, 1.0, null),
        )
    }

    private fun parse(action: String?, vararg extras: Pair<String, Any?>): Parsed {
        val values = extras.toMap()
        return parseAdbCommand(action) { values[it] }
    }

    private fun assertInvalid(parsed: Parsed) {
        assertTrue("Expected Invalid but was $parsed", parsed is Parsed.Invalid)
    }
}
