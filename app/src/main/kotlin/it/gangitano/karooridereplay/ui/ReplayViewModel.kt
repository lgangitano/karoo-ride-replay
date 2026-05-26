package it.gangitano.karooridereplay.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.gangitano.karooridereplay.data.FitFileRepository
import it.gangitano.karooridereplay.data.FitFileRepository.FitFileEntry
import it.gangitano.karooridereplay.extension.KarooRideReplayExtension
import it.gangitano.karooridereplay.replay.FitParser
import it.gangitano.karooridereplay.replay.ReplayEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        data class Loaded(val recordCount: Int, val totalSeconds: Long) : LoadStatus()
        data class Error(val message: String) : LoadStatus()
    }

    private val _rideList = MutableStateFlow<List<FitFileEntry>>(emptyList())
    val rideList: StateFlow<List<FitFileEntry>> = _rideList.asStateFlow()

    private val _selectedRide = MutableStateFlow<FitFileEntry?>(null)
    val selectedRide: StateFlow<FitFileEntry?> = _selectedRide.asStateFlow()

    private val _loadStatus = MutableStateFlow<LoadStatus>(LoadStatus.Idle)
    val loadStatus: StateFlow<LoadStatus> = _loadStatus.asStateFlow()

    private fun engine(): ReplayEngine? = KarooRideReplayExtension.instance?.replayEngine

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
        _selectedRide.value = entry
        _loadStatus.value = LoadStatus.Loading
        viewModelScope.launch {
            try {
                val records = withContext(Dispatchers.IO) { parser.parse(entry.file) }
                engine()?.load(records)
                val total = if (records.isEmpty()) 0L
                    else (records.last().timestampMs - records.first().timestampMs) / 1000L
                _loadStatus.value = LoadStatus.Loaded(records.size, total)
            } catch (e: Exception) {
                _loadStatus.value = LoadStatus.Error(e.message ?: "Parse failed")
            }
        }
    }

    fun play() { engine()?.play() }
    fun pause() { engine()?.pause() }
    fun stop() { engine()?.stop() }
    fun seek(seconds: Long) { engine()?.seek(seconds) }
    fun setSpeed(multiplier: Double) { engine()?.setSpeed(multiplier) }
}
