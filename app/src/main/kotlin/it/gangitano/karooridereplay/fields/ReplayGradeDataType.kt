package it.gangitano.karooridereplay.fields

import android.content.Context
import android.util.Log
import it.gangitano.karooridereplay.extension.KarooRideReplayExtension
import it.gangitano.karooridereplay.extension.KarooRideReplayExtension.Companion.EXTENSION_ID
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateNumericConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Opt-in replay grade for numeric ride fields and other extensions. */
class ReplayGradeDataType(
    private val service: KarooRideReplayExtension,
) : DataTypeImpl(EXTENSION_ID, TYPE_ID) {

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.Default).launch {
            try {
                combine(service.replayActive, service.replayEngine.currentRecord) { active, record ->
                    gradeStreamState(active, record?.grade, dataTypeId)
                }.distinctUntilChanged().collect { state ->
                    emitter.onNext(state)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A dead consumer's binder call must not take playback down.
                Log.w(TAG, "Stopping grade stream: ${e.message}", e)
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    /**
     * Render like the Karoo's own Grade field (one decimal). Without this the
     * ride app formats an extension's numeric value as an integer, so 8.4 % shows
     * as 8. Display only: the streamed value keeps full precision.
     */
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateNumericConfig(formatDataTypeId = DataType.Type.ELEVATION_GRADE))
    }

    companion object {
        const val TYPE_ID = "grade"
        private const val TAG = "ReplayGrade"
    }
}

/** Gate grade on an open replay so a real ride cannot consume a stale value. */
internal fun gradeStreamState(replayActive: Boolean, grade: Double?, dataTypeId: String): StreamState =
    when {
        !replayActive -> StreamState.NotAvailable
        grade == null -> StreamState.Searching
        // SINGLE only: the ride app rejects an extension data point carrying any
        // extra field ("noExtraFields=false" in its log), so no ELEVATION_GRADE key.
        else -> StreamState.Streaming(
            DataPoint(
                dataTypeId = dataTypeId,
                values = mapOf(DataType.Field.SINGLE to grade),
            ),
        )
    }
