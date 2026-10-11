package it.gangitano.karooridereplay.replay

/**
 * Fill grade gaps without changing what the Karoo recorded.
 *
 * If any recorded grade exists, keep those values (including spikes) and carry
 * the last one forward over gaps; records before the first value stay null.
 * Only files with no recorded grade use altitude and distance: compare each
 * usable record with the latest earlier usable record at least 50 m behind,
 * then carry computed values forward over gaps in the same way. Never mix the
 * two sources within a ride, so replay stays faithful to the recorded grade.
 */
object GradeFill {

    private const val WINDOW_M = 50.0

    fun apply(records: List<FitRecord>): List<FitRecord> {
        val hasRecordedGrade = records.any { it.grade != null }
        val anchors = mutableListOf<FitRecord>()
        var lastGrade: Double? = null

        return records.map { record ->
            val grade = if (hasRecordedGrade) {
                record.grade
            } else {
                val altitude = record.altitude
                val distance = record.distance
                if (altitude == null || distance == null) {
                    null
                } else {
                    // Retained anchors increase in distance and record order,
                    // so binary search finds the latest eligible earlier record.
                    var lo = 0
                    var hi = anchors.size
                    while (lo < hi) {
                        val mid = (lo + hi) ushr 1
                        if (anchors[mid].distance!! <= distance - WINDOW_M) {
                            lo = mid + 1
                        } else {
                            hi = mid
                        }
                    }
                    val anchor = anchors.getOrNull(lo - 1)
                    val computed = anchor?.let {
                        (altitude - it.altitude!!) / (distance - it.distance!!) * 100.0
                    }
                    // A newer anchor at a lower/equal distance supersedes older
                    // ones for every future lookup, even if distance goes backwards.
                    // Each anchor is added/removed once: O(n log n) overall.
                    while (anchors.isNotEmpty() && anchors.last().distance!! >= distance) {
                        anchors.removeAt(anchors.lastIndex)
                    }
                    anchors.add(record)
                    computed
                }
            }
            if (grade != null) lastGrade = grade
            record.copy(grade = lastGrade)
        }
    }
}
