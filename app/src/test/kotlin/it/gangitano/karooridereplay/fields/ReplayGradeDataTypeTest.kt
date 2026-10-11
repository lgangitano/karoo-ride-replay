package it.gangitano.karooridereplay.fields

import it.gangitano.karooridereplay.extension.KarooRideReplayExtension.Companion.EXTENSION_ID
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplayGradeDataTypeTest {

    private val dataTypeId = DataType.dataTypeId(EXTENSION_ID, ReplayGradeDataType.TYPE_ID)

    @Test fun `inactive replay is unavailable even when a grade is stored`() {
        assertEquals(StreamState.NotAvailable, gradeStreamState(false, 5.0, dataTypeId))
        assertEquals(StreamState.NotAvailable, gradeStreamState(false, null, dataTypeId))
    }

    @Test fun `active replay without a grade is searching`() {
        assertEquals(StreamState.Searching, gradeStreamState(true, null, dataTypeId))
    }

    @Test fun `active replay streams only the single field with the public data type id`() {
        val state = gradeStreamState(true, 5.0, dataTypeId) as StreamState.Streaming
        assertEquals("TYPE_EXT::ride-replay::grade", state.dataPoint.dataTypeId)
        assertEquals(mapOf(DataType.Field.SINGLE to 5.0), state.dataPoint.values)
    }
}
