package it.gangitano.karooridereplay.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.gangitano.karooridereplay.data.FitFileRepository
import it.gangitano.karooridereplay.data.FitFileRepository.FitFileEntry
import it.gangitano.karooridereplay.extension.KarooRideReplayExtension
import it.gangitano.karooridereplay.replay.FitParser
import it.gangitano.karooridereplay.replay.FitRecord
import it.gangitano.karooridereplay.replay.ReplayEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * UI state holder + bridge to the [KarooRideReplayExtension]'s [ReplayEngine].
 *
 * The extension service hosts the long-lived engine; this VM provides the
 * Compose screens with snapshot-able state and callable actions. When the
 * extension isn't running (e.g., during cold boot before the service binds),
 * state-flow getters fall back to inert defaults so the UI never NPEs.
 */
class ReplayViewModel : ViewModel() {

    private val parser = FitParser()

    sealed class LoadStatus {
        object Idle : LoadStatus()
        object Loading : LoadStatus()
        data class Loaded(
            val recordCount: Int,
            val totalSeconds: Long,
            val ridePath: String,
        ) : LoadStatus()
        data class Error(val message: String) : LoadStatus()
    }

    /** A lazily-parsed summary of a ride, shown on the picker rows. */
    data class RideSummary(val durationSeconds: Long, val distanceMeters: Double?)

    private val _rideList = MutableStateFlow<List<FitFileEntry>>(emptyList())
    val rideList: StateFlow<List<FitFileEntry>> = _rideList.asStateFlow()

    private val _selectedRide = MutableStateFlow<FitFileEntry?>(null)
    val selectedRide: StateFlow<FitFileEntry?> = _selectedRide.asStateFlow()

    /** Per-file parsed summaries, keyed by absolute path. Filled lazily/cached. */
    private val _summaries = MutableStateFlow<Map<String, RideSummary>>(emptyMap())
    val summaries: StateFlow<Map<String, RideSummary>> = _summaries.asStateFlow()

    /** Absolute paths the user has starred this session. */
    private val _starred = MutableStateFlow<Set<String>>(emptySet())
    val starred: StateFlow<Set<String>> = _starred.asStateFlow()

    /** Paths currently being parsed, so we never launch a second parse for one. */
    private val parsing = mutableSetOf<String>()
    private val summaryParseSemaphore = Semaphore(permits = 3)

    private val _loadStatus = MutableStateFlow<LoadStatus>(LoadStatus.Idle)
    val loadStatus: StateFlow<LoadStatus> = _loadStatus.asStateFlow()
    private var loadJob: Job? = null

    private fun engine(): ReplayEngine? = KarooRideReplayExtension.instance?.replayEngine

    init {
        // Reconnect to a still-running replay after the Activity was recreated —
        // e.g. reopened from the Karoo Extensions list ("Open") while the
        // extension service kept the engine playing. The engine is the source of
        // truth; this restores the UI's view of it so the ride/playback survive
        // the round-trip instead of resetting to an empty picker.
        restoreActiveRide()
    }

    /** True when the engine still holds a loaded ride and we know which file it was. */
    fun hasActiveRide(): Boolean =
        (engine()?.totalSeconds ?: 0L) > 0L && lastSelectedRidePath != null

    private fun restoreActiveRide() {
        val eng = engine() ?: return
        if (eng.totalSeconds <= 0L) return
        val path = lastSelectedRidePath ?: return
        val file = File(path)
        if (!file.exists()) return
        _selectedRide.value = FitFileEntry(
            file = file,
            displayName = file.nameWithoutExtension,
            sizeBytes = file.length(),
            modifiedMs = file.lastModified(),
        )
        _loadStatus.value = LoadStatus.Loaded(
            recordCount = 0,
            totalSeconds = eng.totalSeconds,
            ridePath = path,
        )
    }

    // Passthrough state flows from the engine. Use inert default flows when the
    // extension service hasn't started yet (UI doesn't crash on cold boot).
    val state: StateFlow<ReplayEngine.State>
        get() = engine()?.state ?: MutableStateFlow(ReplayEngine.State.IDLE).asStateFlow()
    val progress: StateFlow<Double>
        get() = engine()?.progress ?: MutableStateFlow(0.0).asStateFlow()
    val elapsedSeconds: StateFlow<Long>
        get() = engine()?.elapsedSeconds ?: MutableStateFlow(0L).asStateFlow()
    val playbackSpeed: StateFlow<Double>
        get() = engine()?.playbackSpeed ?: MutableStateFlow(1.0).asStateFlow()
    val currentRecord: StateFlow<FitRecord?>
        get() = engine()?.currentRecord ?: MutableStateFlow<FitRecord?>(null).asStateFlow()
    val markers: StateFlow<List<Long>>
        get() = engine()?.markers ?: MutableStateFlow<List<Long>>(emptyList()).asStateFlow()
    val loop: StateFlow<Boolean>
        get() = engine()?.loop ?: MutableStateFlow(false).asStateFlow()
    val totalSeconds: Long
        get() = engine()?.totalSeconds ?: 0L

    fun scanRides(context: Context) {
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) {
                FitFileRepository(context).listAvailable()
            }
            _rideList.value = files
        }
    }

    fun selectRide(entry: FitFileEntry) {
        val ridePath = entry.file.absolutePath
        val eng = engine()
        // Re-selecting the ride that's already loaded must NOT reset playback to
        // 0:00 — reopen it in place (preserving position/state). Only a different
        // ride triggers a fresh parse + load.
        if (ridePath == lastSelectedRidePath && eng != null && eng.totalSeconds > 0L) {
            _selectedRide.value = entry
            _loadStatus.value = LoadStatus.Loaded(0, eng.totalSeconds, ridePath)
            return
        }
        loadJob?.cancel()
        _selectedRide.value = entry
        _loadStatus.value = LoadStatus.Loading
        lastSelectedRidePath = ridePath
        loadJob = viewModelScope.launch {
            try {
                val records = withContext(Dispatchers.IO) { parser.parse(entry.file) }
                if (_selectedRide.value?.file?.absolutePath != ridePath) return@launch
                engine()?.load(records)
                val total = if (records.isEmpty()) 0L
                    else (records.last().timestampMs - records.first().timestampMs) / 1000L
                _loadStatus.value = LoadStatus.Loaded(records.size, total, ridePath)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_selectedRide.value?.file?.absolutePath == ridePath) {
                    _loadStatus.value = LoadStatus.Error(e.message ?: "Parse failed")
                }
            }
        }
    }

    /**
     * Parse a ride's duration + distance for the picker row, lazily and once.
     * Renders nothing until it resolves; the row already shows file time + size
     * immediately. Off the main thread; results cached in [summaries].
     */
    fun ensureSummary(entry: FitFileEntry) {
        val key = entry.file.absolutePath
        if (_summaries.value.containsKey(key) || key in parsing) return
        parsing += key
        viewModelScope.launch {
            val summary = withContext(Dispatchers.IO) {
                summaryParseSemaphore.withPermit {
                    try {
                        val records = parser.parse(entry.file)
                        if (records.isEmpty()) RideSummary(0L, null)
                        else RideSummary(
                            durationSeconds =
                                (records.last().timestampMs - records.first().timestampMs) / 1000L,
                            distanceMeters = records.lastOrNull { it.distance != null }?.distance,
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            if (summary != null) _summaries.value = _summaries.value + (key to summary)
            parsing -= key
        }
    }

    fun toggleStar(entry: FitFileEntry) {
        val key = entry.file.absolutePath
        _starred.value =
            if (key in _starred.value) _starred.value - key else _starred.value + key
    }

    fun play() { engine()?.play() }
    fun pause() { engine()?.pause() }
    fun stop() { engine()?.stop() }
    fun seek(seconds: Long) { engine()?.seek(seconds) }
    fun setSpeed(multiplier: Double) { engine()?.setSpeed(multiplier) }
    fun addMarker() { engine()?.addMarker() }
    fun clearMarkers() { engine()?.clearMarkers() }
    fun toggleLoop() { engine()?.toggleLoop() }

    companion object {
        /**
         * The last ride the user selected, kept in a process-lifetime static so a
         * freshly-created ViewModel can recover it. The engine persists in the
         * extension service for as long as the process lives; so does this. If the
         * process dies the engine dies too, so there is nothing to restore anyway.
         */
        private var lastSelectedRidePath: String? = null
    }
}
