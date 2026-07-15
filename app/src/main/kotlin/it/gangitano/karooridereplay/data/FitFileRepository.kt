package it.gangitano.karooridereplay.data

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lists FIT files on the Karoo's storage.
 *
 * The Karoo OS records every activity to `FitFiles/` under the device's
 * primary external storage. We scan that directory and also fall back to
 * common alternate locations (Hammerhead's app-private cache, app-private
 * inbox for sideloaded files) so a user who has imported a FIT from another
 * device can still pick it.
 *
 * Files are returned newest-first so the most recent rides surface at the
 * top of the picker UI.
 */
class FitFileRepository(private val context: Context) {

    data class FitFileEntry(
        val file: File,
        val displayName: String,
        val sizeBytes: Long,
        val modifiedMs: Long
    ) {
        val modifiedLabel: String
            get() = DATE_FORMAT.format(Date(modifiedMs))

        val sizeLabel: String
            get() = when {
                sizeBytes < 1024 -> "$sizeBytes B"
                sizeBytes < 1024 * 1024 -> "%.1f KB".format(sizeBytes / 1024.0)
                else -> "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
            }
    }

    /**
     * Scan known FIT-file directories. Returns entries sorted by modification
     * time descending. Empty list if no FIT files are reachable (e.g., storage
     * permission denied).
     */
    fun listAvailable(): List<FitFileEntry> {
        val candidates = buildList {
            // Karoo's primary recorded-activities folder
            add(File(Environment.getExternalStorageDirectory(), "FitFiles"))
            // App-private inbox — files sideloaded into our package's storage
            context.getExternalFilesDir(null)?.let { add(it) }
            // Common downloads location, in case the rider drag-and-drops via cable
            add(File(Environment.getExternalStorageDirectory(), "Download"))
        }

        return candidates
            .asSequence()
            .filter { it.exists() && it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().take(MAX_FILES_PER_DIR) }
            .filter { it.isFile && it.extension.equals("fit", ignoreCase = true) }
            // Hide near-empty recordings — a FIT with too little data to be a
            // useful replay (aborted/quick test recordings leave a tiny stub).
            // Size is a cheap, stable proxy: empty stubs are ~1 KB while the
            // smallest genuine ride observed is ~27 KB, so this floor only ever
            // drops files with no real ride in them, never a large one.
            .filter { it.length() >= MIN_RIDE_BYTES }
            .distinctBy { it.absolutePath }
            .map {
                FitFileEntry(
                    file = it,
                    displayName = it.nameWithoutExtension,
                    sizeBytes = it.length(),
                    modifiedMs = it.lastModified()
                )
            }
            .sortedByDescending { it.modifiedMs }
            .toList()
    }

    companion object {
        /** Cap so a runaway directory traversal doesn't stall the UI thread. */
        private const val MAX_FILES_PER_DIR = 500

        /**
         * Minimum FIT size to be listed as a ride. Below this a file has too
         * little data to replay usefully (empty/aborted recordings). Tunable:
         * raise it to hide more marginal recordings, lower it to show more.
         */
        private const val MIN_RIDE_BYTES = 10 * 1024L
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }
}
