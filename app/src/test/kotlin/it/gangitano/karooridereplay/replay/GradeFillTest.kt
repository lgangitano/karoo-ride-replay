package it.gangitano.karooridereplay.replay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradeFillTest {

    @Test fun `recorded grades including spikes are kept as recorded`() {
        val records = listOf(
            FitRecord(timestampMs = 0L, grade = 5.0),
            FitRecord(timestampMs = 1000L, grade = 113.0),
            FitRecord(timestampMs = 2000L, grade = -8.0),
        )
        assertEquals(records, GradeFill.apply(records))
    }

    @Test fun `null gaps carry the last recorded grade and leading nulls stay null`() {
        val records = listOf(null, null, 5.0, null, null, -2.0, null).mapIndexed { index, grade ->
            FitRecord(timestampMs = index * 1000L, grade = grade)
        }
        assertEquals(
            listOf(null, null, 5.0, 5.0, 5.0, -2.0, -2.0),
            GradeFill.apply(records).map { it.grade },
        )
        assertNull(records[3].grade)
    }

    @Test fun `a five percent ramp computes only once fifty meters are covered`() {
        val records = (0..7).map { index ->
            FitRecord(timestampMs = index * 1000L, altitude = 100.0 + index * 0.5, distance = index * 10.0)
        }
        assertEquals(
            listOf(null, null, null, null, null, 5.0, 5.0, 5.0),
            GradeFill.apply(records).map { it.grade },
        )
    }

    @Test fun `computed grade is negative downhill`() {
        val records = listOf(
            FitRecord(timestampMs = 0L, altitude = 100.0, distance = 0.0),
            FitRecord(timestampMs = 1000L, altitude = 97.5, distance = 50.0),
        )
        assertEquals(-5.0, GradeFill.apply(records).last().grade!!, 0.0)
    }

    @Test fun `the window uses the latest eligible anchor and actual distance gap`() {
        val records = listOf(
            FitRecord(timestampMs = 0L, altitude = 0.0, distance = 0.0),
            FitRecord(timestampMs = 1000L, altitude = 10.0, distance = 20.0),
            FitRecord(timestampMs = 2000L, altitude = 5.0, distance = 50.0),
            FitRecord(timestampMs = 3000L, altitude = 17.0, distance = 90.0),
        )
        assertEquals(10.0, GradeFill.apply(records).last().grade!!, 0.0)
    }

    @Test fun `missing altitude or distance is skipped as an anchor and carries computed grade`() {
        val records = listOf(
            FitRecord(timestampMs = 0L, altitude = 100.0, distance = 0.0),
            FitRecord(timestampMs = 1000L, distance = 10.0),
            FitRecord(timestampMs = 2000L, altitude = 200.0),
            FitRecord(timestampMs = 3000L, altitude = 103.0, distance = 60.0),
            FitRecord(timestampMs = 4000L, distance = 70.0),
            FitRecord(timestampMs = 5000L, altitude = 300.0),
            FitRecord(timestampMs = 6000L, altitude = 106.0, distance = 120.0),
        )
        assertEquals(
            listOf(null, null, null, 5.0, 5.0, 5.0, 5.0),
            GradeFill.apply(records).map { it.grade },
        )
    }

    @Test fun `one recorded value prevents computation anywhere in the ride`() {
        val records = listOf(
            FitRecord(timestampMs = 0L, altitude = 100.0, distance = 0.0),
            FitRecord(timestampMs = 1000L, altitude = 102.5, distance = 50.0),
            FitRecord(timestampMs = 2000L, altitude = 105.0, distance = 100.0, grade = 7.0),
            FitRecord(timestampMs = 3000L, altitude = 107.5, distance = 150.0),
        )
        assertEquals(listOf(null, null, 7.0, 7.0), GradeFill.apply(records).map { it.grade })
    }

    @Test fun `distance going backwards still selects the latest eligible earlier record`() {
        val records = listOf(
            FitRecord(timestampMs = 0L, altitude = 0.0, distance = 0.0),
            FitRecord(timestampMs = 1000L, altitude = 3.0, distance = 60.0),
            FitRecord(timestampMs = 2000L, altitude = 10.0, distance = 20.0),
            FitRecord(timestampMs = 3000L, altitude = 12.5, distance = 70.0),
        )
        assertEquals(5.0, GradeFill.apply(records).last().grade!!, 0.0)
    }

    @Test fun `no usable altitude and distance leaves grade unavailable`() {
        assertEquals(emptyList<FitRecord>(), GradeFill.apply(emptyList()))
        val records = listOf(FitRecord(timestampMs = 0L), FitRecord(timestampMs = 1000L))
        assertEquals(records, GradeFill.apply(records))
    }
}
