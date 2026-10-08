package it.gangitano.karooridereplay.remote

import it.gangitano.karooridereplay.replay.ReplayEngine
import java.io.File

internal const val ACTION_PREFIX = "it.gangitano.karooridereplay."
internal const val ACTION_LOAD = "${ACTION_PREFIX}LOAD"
internal const val ACTION_PLAY = "${ACTION_PREFIX}PLAY"
internal const val ACTION_PAUSE = "${ACTION_PREFIX}PAUSE"
internal const val ACTION_SEEK = "${ACTION_PREFIX}SEEK"
internal const val ACTION_SPEED = "${ACTION_PREFIX}SPEED"
internal const val ACTION_STATUS = "${ACTION_PREFIX}STATUS"
internal const val ACTION_EXIT = "${ACTION_PREFIX}EXIT"

internal const val EXTRA_FILE = "file"
internal const val EXTRA_SECONDS = "seconds"
internal const val EXTRA_MULTIPLIER = "multiplier"

/** A command accepted by the adb broadcast interface. */
sealed interface AdbCommand {
    data class Load(val path: String) : AdbCommand
    data object Play : AdbCommand
    data object Pause : AdbCommand
    data class Seek(val seconds: Long) : AdbCommand
    data class Speed(val multiplier: Double) : AdbCommand
    data object Status : AdbCommand
    data object Exit : AdbCommand
}

/** Result of parsing an adb action and its untyped extras. */
sealed interface Parsed {
    data class Ok(val command: AdbCommand) : Parsed
    data class Invalid(val message: String) : Parsed
}

/** Parse an adb action without depending on Android framework types. */
fun parseAdbCommand(action: String?, extras: (String) -> Any?): Parsed = when (action) {
    ACTION_LOAD -> {
        val path = extras(EXTRA_FILE) as? String
        if (path.isNullOrBlank()) Parsed.Invalid("LOAD requires a non-empty file extra")
        else Parsed.Ok(AdbCommand.Load(path))
    }
    ACTION_PLAY -> Parsed.Ok(AdbCommand.Play)
    ACTION_PAUSE -> Parsed.Ok(AdbCommand.Pause)
    ACTION_SEEK -> {
        val seconds = numericExtra(extras(EXTRA_SECONDS))
        if (seconds == null || seconds < 0.0) {
            Parsed.Invalid("SEEK requires a non-negative seconds extra")
        } else {
            Parsed.Ok(AdbCommand.Seek(seconds.toLong()))
        }
    }
    ACTION_SPEED -> {
        val multiplier = numericExtra(extras(EXTRA_MULTIPLIER))
        if (multiplier == null || multiplier <= 0.0) {
            Parsed.Invalid("SPEED requires a positive multiplier extra")
        } else {
            Parsed.Ok(AdbCommand.Speed(multiplier))
        }
    }
    ACTION_STATUS -> Parsed.Ok(AdbCommand.Status)
    ACTION_EXIT -> Parsed.Ok(AdbCommand.Exit)
    else -> Parsed.Invalid("unknown action: ${action ?: "null"}")
}

/** Convert an adb numeric extra to a finite [Double]. */
fun numericExtra(value: Any?): Double? {
    val number = when (value) {
        is Number -> value.toDouble()
        is String -> value.trim().toDoubleOrNull()
        else -> null
    }
    return number?.takeIf { it.isFinite() }
}

/** Resolve [arg] against [externalRoot] unless it is already absolute. */
fun resolveRidePath(arg: String, externalRoot: String): String {
    val file = File(arg)
    return if (file.isAbsolute) file.path else File(externalRoot, arg).path
}

/**
 * Rewrite a canonical ride path into the form the picker uses, rooted at the
 * un-resolved [storageRoot] (`/storage/emulated/0`). `/sdcard/…` and other
 * symlinked spellings of the same file then match the picker's path, so the
 * picker highlights an adb-loaded ride and re-selecting it reopens in place.
 * Paths outside the storage root are returned canonical, unchanged.
 */
fun inStorageRootForm(canonicalPath: String, canonicalStorageRoot: String, storageRoot: String): String {
    val prefix = canonicalStorageRoot.trimEnd('/') + "/"
    return if (canonicalPath.startsWith(prefix)) {
        storageRoot.trimEnd('/') + "/" + canonicalPath.removePrefix(prefix)
    } else {
        canonicalPath
    }
}

/**
 * Format the single-line reply returned by successful adb commands. Speed uses
 * [Double.toString], which is locale-independent (always a dot decimal) and
 * keeps values like 0.25 exact instead of rounding them.
 */
fun statusLine(
    state: ReplayEngine.State,
    elapsed: Long,
    total: Long,
    speed: Double,
    file: String?,
): String = "state=$state elapsed=$elapsed total=$total " +
    "speed=$speed file=${file ?: "-"}"
