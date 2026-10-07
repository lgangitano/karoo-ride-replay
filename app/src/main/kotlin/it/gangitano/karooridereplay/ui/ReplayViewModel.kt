package it.gangitano.karooridereplay.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.gangitano.karooridereplay.data.FitFileRepository
import it.gangitano.karooridereplay.data.FitFileRepository.FitFileEntry
import it.gangitano.karooridereplay.extension.KarooRideReplayExtension
import it.gangitano.karooridereplay.replay.FitParser
import it.gangitano.karooridereplay.replay.FitRecord
import it.gangitano.karooridereplay.replay.ReplayEngine
import it.gangitano.karooridereplay.replay.Sensor
import it.gangitano.karooridereplay.replay.SensorState
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
class ReplayViewModel(app: Application) : AndroidViewModel(app) {

    private val parser = FitParser()

    /**
     * Starred rides persist in SharedPreferences, not ViewModel state. Going
     * "To ride" to record and reopening the app (Extensions → Open) builds a
     * fresh Activity + ViewModel; instance state would reset the stars to empty
     * even though the replay position survives (that lives in the extension
     * service). Prefs make the pins durable across ViewModel recreation and
     * process death — which is what "pin my favourite rides" should mean.
     */
    private val prefs = app.getSharedPreferences(PREFS_STARRED, Context.MODE_PRIVATE)

    sealed class LoadStatus {
        object Idle : LoadStatus()
        object Loading : LoadStatus()
        data class Loaded(
            val totalSeconds: Long,
            val ridePath: String,
        ) : LoadStatus()
        data class Error(val message: String) : LoadStatus()
    }

    /**
     * A lazily-parsed summary of a ride, shown on the picker rows.
     * A null [durationSeconds] marks a file that failed to parse — cached so
     * a corrupt FIT is parsed once, not retried on every recomposition.
     */
    data class RideSummary(val durationSeconds: Long?, val distanceMeters: Double?)

    private val _rideList = MutableStateFlow<List<FitFileEntry>>(emptyList())
    val rideList: StateFlow<List<FitFileEntry>> = _rideList.asStateFlow()

    private val _selectedRide = MutableStateFlow<FitFileEntry?>(null)
    val selectedRide: StateFlow<FitFileEntry?> = _selectedRide.asStateFlow()

    /** Per-file parsed summaries, keyed by absolute path. Filled lazily/cached. */
    private val _summaries = MutableStateFlow<Map<String, RideSummary>>(emptyMap())
    val summaries: StateFlow<Map<String, RideSummary>> = _summaries.asStateFlow()

    /** Absolute paths the user has starred, loaded from and written through to prefs. */
    private val _starred = MutableStateFlow(prefs.getStringSet(KEY_PATHS, emptySet())!!.toSet())
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
            totalSeconds = eng.totalSeconds,
            ridePath = path,
        )
    }

    // Inert defaults for when the extension service hasn't started yet (cold
    // boot before the service binds). Single stable instances: minting a fresh
    // MutableStateFlow inside each getter made collectAsState see a different
    // flow every recomposition, cancelling and restarting collection each time.
    private val inertState = MutableStateFlow(ReplayEngine.State.IDLE).asStateFlow()
    private val inertElapsed = MutableStateFlow(0L).asStateFlow()
    private val inertSpeed = MutableStateFlow(1.0).asStateFlow()
    private val inertRecord = MutableStateFlow<FitRecord?>(null).asStateFlow()
    private val inertMarkers = MutableStateFlow<List<Long>>(emptyList()).asStateFlow()
    private val inertLoop = MutableStateFlow(false).asStateFlow()
    private val inertSensorStates = MutableStateFlow(ReplayEngine.ALL_STREAMING).asStateFlow()

    // Passthrough state flows from the engine, falling back to the inert
    // defaults above so the UI never NPEs on cold boot.
    val state: StateFlow<ReplayEngine.State>
        get() = engine()?.state ?: inertState
    val elapsedSeconds: StateFlow<Long>
        get() = engine()?.elapsedSeconds ?: inertElapsed
    val playbackSpeed: StateFlow<Double>
        get() = engine()?.playbackSpeed ?: inertSpeed
    val currentRecord: StateFlow<FitRecord?>
        get() = engine()?.currentRecord ?: inertRecord
    val markers: StateFlow<List<Long>>
        get() = engine()?.markers ?: inertMarkers
    val loop: StateFlow<Boolean>
        get() = engine()?.loop ?: inertLoop
    val sensorStates: StateFlow<Map<Sensor, SensorState>>
        get() = engine()?.sensorStates ?: inertSensorStates
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
            _loadStatus.value = LoadStatus.Loaded(eng.totalSeconds, ridePath)
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
                _loadStatus.value = LoadStatus.Loaded(total, ridePath)
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
                        // Cache the failure (null duration) — leaving it uncached
                        // meant every recomposition of the row re-parsed the
                        // corrupt file, forever.
                        RideSummary(null, null)
                    }
                }
            }
            _summaries.value = _summaries.value + (key to summary)
            parsing -= key
        }
    }

    fun toggleStar(entry: FitFileEntry) {
        val key = entry.file.absolutePath
        val next = if (key in _starred.value) _starred.value - key else _starred.value + key
        _starred.value = next
        // Write through to prefs so the pin survives ViewModel recreation. Copy
        // the set: SharedPreferences must not be handed a set it keeps a
        // reference to and we later mutate.
        prefs.edit().putStringSet(KEY_PATHS, HashSet(next)).apply()
    }

    fun play() {
        engine()?.play()
        // Arm mock GPS only once a replay is actually playing (not at service
        // startup): registration succeeds after the user has set this app as the
        // mock-location app, and real GPS is used whenever nothing is replaying.
        KarooRideReplayExtension.instance?.armMockLocation()
    }
    fun pause() { engine()?.pause() }

    /**
     * Leave the replay: pause (keeping position for reopen-in-place) and disarm
     * mock GPS so the Karoo falls back to real GPS. This is the back-to-picker
     * path; "To ride" deliberately does NOT call it, so a replay keeps driving
     * the sensors while the rider records a ride.
     */
    fun exitReplay() {
        engine()?.pause()
        KarooRideReplayExtension.instance?.disarmMockLocation()
    }

    fun seek(seconds: Long) { engine()?.seek(seconds) }
    fun setSpeed(multiplier: Double) { engine()?.setSpeed(multiplier) }
    fun addMarker() { engine()?.addMarker() }
    fun clearMarkers() { engine()?.clearMarkers() }
    fun toggleLoop() { engine()?.toggleLoop() }
    fun cycleSensorState(sensor: Sensor) { engine()?.cycleSensorState(sensor) }

    companion object {
        /**
         * The last ride the user selected, kept in a process-lifetime static so a
         * freshly-created ViewModel can recover it. The engine persists in the
         * extension service for as long as the process lives; so does this. If the
         * process dies the engine dies too, so there is nothing to restore anyway.
         */
        private var lastSelectedRidePath: String? = null

        /** SharedPreferences file + key for the durable set of starred ride paths. */
        private const val PREFS_STARRED = "ride_replay_starred"
        private const val KEY_PATHS = "starred_paths"
    }
}
