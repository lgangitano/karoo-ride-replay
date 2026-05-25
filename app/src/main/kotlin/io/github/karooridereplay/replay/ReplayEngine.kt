package io.github.karooridereplay.replay

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
    }

    /**
     * Begin (or resume) playback. No-op if no ride loaded or already playing.
     * The launched coroutine emits samples until the ride ends or stop/pause
     * is requested.
     */
    fun play() {
        if (samples.isEmpty() || _state.value == State.PLAYING) return
        if (currentIndex >= samples.lastIndex) {
            // At the end already — restart from the beginning rather than no-op
            currentIndex = 0
            _currentRecord.value = samples.firstOrNull()
        }
        _state.value = State.PLAYING
        playbackJob?.cancel()
        playbackJob = scope.launch {
            while (isActive && currentIndex < samples.lastIndex) {
                val current = samples[currentIndex]
                val next = samples[currentIndex + 1]
                val realGapMs = (next.timestampMs - current.timestampMs).coerceAtLeast(0L)
                val delayMs = (realGapMs / _playbackSpeed.value).toLong().coerceAtLeast(1L)
                delay(delayMs)
                if (_state.value != State.PLAYING) break
                currentIndex++
                emitCurrent()
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
        val targetMs = rideStartMs + elapsedSeconds.coerceAtLeast(0L) * 1000L
        currentIndex = findIndexAtOrAfter(targetMs).coerceIn(0, samples.lastIndex)
        emitCurrent()
    }

    /** Set the playback multiplier (e.g., 1.0, 2.0, 5.0, 10.0). Coerced to a sane range. */
    fun setSpeed(multiplier: Double) {
        _playbackSpeed.value = multiplier.coerceIn(0.1, 100.0)
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
}
