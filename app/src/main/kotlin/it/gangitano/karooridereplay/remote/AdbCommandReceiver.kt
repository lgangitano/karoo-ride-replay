package it.gangitano.karooridereplay.remote

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import it.gangitano.karooridereplay.extension.KarooRideReplayExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/** Receives the exported, adb-only replay control broadcasts. */
class AdbCommandReceiver(
    private val extension: KarooRideReplayExtension,
    private val scope: CoroutineScope,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ordered = isOrderedBroadcast
        when (val parsed = parseAdbCommand(intent.action) { rawExtra(intent, it) }) {
            is Parsed.Invalid -> replyError(intent.action, parsed.message, ordered)
            is Parsed.Ok -> execute(parsed.command, intent.action, ordered)
        }
    }

    private fun execute(command: AdbCommand, action: String?, ordered: Boolean) {
        val engine = extension.replayEngine
        when (command) {
            is AdbCommand.Load -> {
                val storageRoot = Environment.getExternalStorageDirectory()
                val path = resolveRidePath(command.path, storageRoot.absolutePath)
                val resolved = File(path)
                if (!resolved.exists()) {
                    replyError(action, "file does not exist: $path", ordered)
                    return
                }
                if (!resolved.isFile) {
                    replyError(action, "not a file: $path", ordered)
                    return
                }
                val file = File(
                    inStorageRootForm(
                        resolved.canonicalPath,
                        storageRoot.canonicalPath,
                        storageRoot.absolutePath,
                    )
                )

                val pendingResult = goAsync()
                scope.launch {
                    try {
                        extension.loadRide(file)
                        val line = currentStatus()
                        Log.i(TAG, "$action $line")
                        if (ordered) {
                            pendingResult.resultCode = Activity.RESULT_OK
                            pendingResult.resultData = line
                        }
                    } catch (e: Exception) {
                        val message = e.message ?: "failed to load $path"
                        Log.w(TAG, "$action error: $message", e)
                        if (ordered) {
                            pendingResult.resultCode = RESULT_ERROR
                            pendingResult.resultData = "error: $message"
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            AdbCommand.Play -> {
                if (!extension.play()) {
                    replyError(action, "no ride loaded", ordered)
                    return
                }
                replySuccess(action, ordered)
            }
            AdbCommand.Pause -> {
                extension.pause()
                replySuccess(action, ordered)
            }
            is AdbCommand.Seek -> {
                if (engine.currentRecord.value == null) {
                    replyError(action, "no ride loaded", ordered)
                    return
                }
                engine.seek(command.seconds)
                replySuccess(action, ordered)
            }
            is AdbCommand.Speed -> {
                engine.setSpeed(command.multiplier)
                replySuccess(action, ordered)
            }
            AdbCommand.Status -> replySuccess(action, ordered)
            AdbCommand.Exit -> {
                extension.pause()
                extension.disarmMockLocation()
                replySuccess(action, ordered)
            }
        }
    }

    private fun replySuccess(action: String?, ordered: Boolean) {
        val line = currentStatus()
        Log.i(TAG, "$action $line")
        if (ordered) setResult(Activity.RESULT_OK, line, null)
    }

    private fun replyError(action: String?, message: String, ordered: Boolean) {
        Log.w(TAG, "$action error: $message")
        if (ordered) setResult(RESULT_ERROR, "error: $message", null)
    }

    private fun currentStatus(): String {
        val engine = extension.replayEngine
        return statusLine(
            state = engine.state.value,
            elapsed = engine.elapsedSeconds.value,
            total = engine.totalSeconds,
            speed = engine.playbackSpeed.value,
            file = engine.loadedPath.value,
        )
    }

    @Suppress("DEPRECATION")
    private fun rawExtra(intent: Intent, key: String): Any? = intent.extras?.get(key)

    private companion object {
        const val TAG = "AdbCommand"
        const val RESULT_ERROR = 1
    }
}
