package it.gangitano.karooridereplay.replay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Coroutine-driven sample-by-sample playback over a loaded [List]<[FitRecord]>.
 *
 * Owns the timeline of replay. The virtual sensor sources and mock-location
 * provider both observe [currentRecord] and act on each emission — the engine
 * itself doesn't talk to Karoo OS.
 *
 * Public surface:
 *   - [load] sets a new ride. Resets state to IDLE at index 0.
 *   - [play] / [pause] / [stop] standard playback controls.
 *   - [seek] jumps to an elapsed-seconds offset from ride start (Luigi's 2b).
 *   - [setSpeed] adjusts playback multiplier (1×, 2×, 5×, 10×, …).
 *   - [currentRecord], [state], [progress], [elapsedSeconds] are observable.
 *
 * Timing model: each tick delays by the real inter-record gap divided by the
 * speed multiplier. So at 1× a 1-Hz-recorded ride plays back at one sample
 * per second; at 5× it plays back at five samples per second.
 */
class ReplayEngine {

    enum class State { IDLE, PLAYING, PAUSED, FINISHED }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _currentRecord = MutableStateFlow<FitRecord?>(null)
    val currentRecord: StateFlow<FitRecord?> = _currentRecord.asStateFlow()

    /** 0.0–1.0 fraction through the loaded ride. */
    private val _progress = MutableStateFlow(0.0)
    val progress: StateFlow<Double> = _progress.asStateFlow()

    /** Elapsed seconds from ride start to the currently-emitted record. */
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0)
    val playbackSpeed: StateFlow<Double> = _playbackSpeed.asStateFlow()

    /** Bookmark times (elapsed seconds from ride start), ascending as added. */
    private val _markers = MutableStateFlow<List<Long>>(emptyList())
    val markers: StateFlow<List<Long>> = _markers.asStateFlow()

    /** When true and ≥2 markers exist, playback loops between the outer two. */
    private val _loop = MutableStateFlow(false)
    val loop: StateFlow<Boolean> = _loop.asStateFlow()

    private var samples: List<FitRecord> = emptyList()
    private var rideStartMs: Long = 0L
    private var rideEndMs: Long = 0L

    @Volatile
    private var currentIndex: Int = 0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playbackJob: Job? = null

    /** Total length of the loaded ride in seconds. 0 if no ride loaded. */
    val totalSeconds: Long
        get() = if (samples.isEmpty()) 0L else (rideEndMs - rideStartMs) / 1000L

    /** Load a new ride. Resets state to IDLE, current record to the first sample. */
    fun load(records: List<FitRecord>) {
        playbackJob?.cancel()
        playbackJob = null
        samples = records
        if (records.isEmpty()) {
            rideStartMs = 0L
            rideEndMs = 0L
            _currentRecord.value = null
        } else {
            rideStartMs = records.first().timestampMs
            rideEndMs = records.last().timestampMs
            _currentRecord.value = records.first()
        }
        currentIndex = 0
        _progress.value = 0.0
        _elapsedSeconds.value = 0L
        _state.value = State.IDLE
        // Markers are per-ride; a fresh ride starts with none.
        _markers.value = emptyList()
        _loop.value = false
    }

    /**
     * Begin (or resume) playback. No-op if no ride loaded or already playing.
     * The launched coroutine emits samples until the ride ends or stop/pause
     * is requested.
     */
    fun play() {
        if (samples.isEmpty() || _state.value == State.PLAYING) return
        if (currentIndex >= samples.lastIndex) {
            // seek() preserves the existing seek semantics and emits immediately,
            // so observers never retain the finished position until the first tick.
            seek(restartElapsedSeconds(_markers.value, _loop.value))
        }
        _state.value = State.PLAYING
        playbackJob?.cancel()
        playbackJob = scope.launch {
            // Intentionally no wall-clock timeout: the loaded ride's recorded gaps
            // and selected speed define playback duration. pause/stop/destroy cancel it.
            while (isActive && currentIndex < samples.lastIndex) {
                val current = samples[currentIndex]
                val next = samples[currentIndex + 1]
                val realGapMs = (next.timestampMs - current.timestampMs).coerceAtLeast(0L)
                val delayMs = (realGapMs / _playbackSpeed.value).toLong().coerceAtLeast(1L)
                delay(delayMs)
                if (_state.value != State.PLAYING) break
                currentIndex++
                emitCurrent()
                // Loop-between-markers: when enabled with ≥2 markers, jump back
                // to the lower marker the moment we reach the upper one.
                loopWrapTarget(_elapsedSeconds.value, _markers.value, _loop.value)
                    ?.let { seek(it) }
            }
            if (currentIndex >= samples.lastIndex && _state.value == State.PLAYING) {
                _state.value = State.FINISHED
            }
        }
    }

    /** Pause playback. Position is preserved; [play] resumes from here. */
    fun pause() {
        if (_state.value != State.PLAYING) return
        _state.value = State.PAUSED
        playbackJob?.cancel()
    }

    /** Stop playback and reset to the start of the loaded ride. */
    fun stop() {
        playbackJob?.cancel()
        currentIndex = 0
        _currentRecord.value = samples.firstOrNull()
        _progress.value = 0.0
        _elapsedSeconds.value = 0L
        _state.value = State.IDLE
    }

    /**
     * Jump to an elapsed-seconds offset from ride start. Clamped to the ride
     * range; preserves play/pause state. Use this for Luigi's "skip to the
     * interesting climb" workflow.
     */
    fun seek(elapsedSeconds: Long) {
        if (samples.isEmpty()) return
        // Seeking OUT of the loop window exits loop mode, so the user can always
        // scrub past the markers. Without this, an active loop yanks the position
        // back to the lower marker the instant it reaches the upper one, making
        // the slider feel "stuck" between the two marks. The loop's own internal
        // wrap targets the lower marker (inside the window), so it never trips this.
        if (_loop.value && seekExitsLoop(elapsedSeconds, _markers.value)) {
            _loop.value = false
        }
        val targetMs = rideStartMs + elapsedSeconds.coerceAtLeast(0L) * 1000L
        currentIndex = findIndexAtOrAfter(targetMs).coerceIn(0, samples.lastIndex)
        emitCurrent()
    }

    /** Set the playback multiplier (e.g., 1.0, 2.0, 5.0, 10.0). Coerced to a sane range. */
    fun setSpeed(multiplier: Double) {
        _playbackSpeed.value = multiplier.coerceIn(0.1, 100.0)
    }

    /** Drop a bookmark at the current elapsed position (kept sorted, de-duplicated). */
    fun addMarker() {
        if (samples.isEmpty()) return
        val at = _elapsedSeconds.value
        if (_markers.value.contains(at)) return
        _markers.value = (_markers.value + at).sorted()
    }

    /** Remove all bookmarks and turn loop off. */
    fun clearMarkers() {
        _markers.value = emptyList()
        _loop.value = false
    }

    /** Toggle loop. No-op unless ≥2 markers exist (nothing to loop between). */
    fun toggleLoop() {
        if (_markers.value.size < 2) {
            _loop.value = false
            return
        }
        _loop.value = !_loop.value
    }

    /** Cancel all coroutines. Call on extension shutdown. */
    fun destroy() {
        playbackJob?.cancel()
        scope.cancel()
    }

    // ─── internals ─────────────────────────────────────────────────────────

    private fun emitCurrent() {
        val record = samples.getOrNull(currentIndex) ?: return
        _currentRecord.value = record
        _elapsedSeconds.value = (record.timestampMs - rideStartMs) / 1000L
        _progress.value = if (samples.size <= 1) 1.0
            else currentIndex.toDouble() / samples.lastIndex
    }

    /**
     * Binary search for the first index whose timestamp is >= [targetMs].
     * Returns `samples.size` if all samples are earlier than the target.
     */
    private fun findIndexAtOrAfter(targetMs: Long): Int {
        var lo = 0
        var hi = samples.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (samples[mid].timestampMs < targetMs) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        /**
         * Elapsed-time target used when [play] is invoked from the end of a ride.
         * An active loop restarts at its lower marker; all other cases restart at 0.
         */
        fun restartElapsedSeconds(markers: List<Long>, loop: Boolean): Long {
            if (!loop || markers.size < 2) return 0L
            return markers.minOrNull() ?: 0L
        }

        /**
         * The loop-between-markers decision, kept pure so it can be unit-tested
         * without the playback coroutine.
         *
         * Returns the elapsed-seconds offset to jump back to (the lower of the
         * outer two markers) when looping is active, at least two distinct
         * markers exist, and playback has reached the upper marker. Returns
         * `null` otherwise — meaning "keep advancing normally."
         *
         * With more than two markers we loop the whole span (min..max); the
         * handoff's "loops between the two" describes the intended two-marker
         * case, and the span is the natural generalization.
         */
        fun loopWrapTarget(elapsedSeconds: Long, markers: List<Long>, loop: Boolean): Long? {
            if (!loop || markers.size < 2) return null
            val lower = markers.minOrNull() ?: return null
            val upper = markers.maxOrNull() ?: return null
            if (lower >= upper) return null
            return if (elapsedSeconds >= upper) lower else null
        }

        /**
         * True when a seek to [elapsedSeconds] lands OUTSIDE the marker window
         * (before the lower marker or after the upper one) — the signal that the
         * user is scrubbing out of the loop and wants loop mode to release.
         * The window is inclusive, so the loop's own wrap to the lower marker
         * does not count as exiting.
         */
        fun seekExitsLoop(elapsedSeconds: Long, markers: List<Long>): Boolean {
            if (markers.size < 2) return false
            val lower = markers.minOrNull() ?: return false
            val upper = markers.maxOrNull() ?: return false
            return elapsedSeconds < lower || elapsedSeconds > upper
        }
    }
}
