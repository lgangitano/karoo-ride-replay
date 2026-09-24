package it.gangitano.karooridereplay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import it.gangitano.karooridereplay.ui.PlaybackScreen
import it.gangitano.karooridereplay.ui.ReplayViewModel
import it.gangitano.karooridereplay.ui.RideSelectorScreen
import it.gangitano.karooridereplay.ui.SettingsScreen
import it.gangitano.karooridereplay.ui.theme.KarooTheme

/**
 * Single-activity host for the Compose screens: ride picker → playback
 * control (the former replay-config step is folded into playback), plus
 * settings from the picker. ViewModel
 * scoped to the activity so both screens share the same state.
 *
 * On launch, requests storage-read permission so [it.gangitano.karooridereplay
 * .data.FitFileRepository] can scan the Karoo's `FitFiles/` folder.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: ReplayViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Result is consumed by the picker rescan; no separate handling needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestStoragePermission()

        setContent {
            KarooTheme {
                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val nav = rememberNavController()
                    // Two screens now: pick a ride → replay it. The former
                    // "Configure replay" step is folded into PlaybackScreen.
                    NavHost(navController = nav, startDestination = "rides") {
                        composable("rides") {
                            RideSelectorScreen(
                                viewModel = viewModel,
                                onRideSelected = {
                                    viewModel.selectRide(it)
                                    nav.navigate("playback")
                                },
                                onOpenSettings = { nav.navigate("settings") { launchSingleTop = true } },
                            )
                        }
                        composable("playback") {
                            PlaybackScreen(
                                viewModel = viewModel,
                                onBack = {
                                    // Exit the replay: pause (keep position so re-selecting
                                    // the ride reopens in place, not at 0:00) AND disarm mock
                                    // GPS so the Karoo returns to real GPS. Distinct from
                                    // "To ride", which keeps replaying while the rider records.
                                    viewModel.exitReplay()
                                    nav.popBackStack("rides", inclusive = false)
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(viewModel = viewModel, onBack = { nav.popBackStack() })
                        }
                    }
                    // Reopened (e.g. Extensions → Open) while a replay is still
                    // live in the extension service: jump straight to playback so
                    // the running ride survives the fresh Activity instead of
                    // dropping the user on an empty picker. "rides" stays the root
                    // so Back still returns to the picker.
                    // singleTop: this effect re-runs on Activity recreation and
                    // would otherwise stack duplicate "playback" entries.
                    LaunchedEffect(Unit) {
                        if (viewModel.hasActiveRide()) {
                            nav.navigate("playback") { launchSingleTop = true }
                        }
                    }
                }
            }
        }
    }

    private fun maybeRequestStoragePermission() {
        // Karoo's FitFiles/ folder is OUTSIDE this app's scoped-storage sandbox.
        // On Android 11+ (R+) reading it requires MANAGE_EXTERNAL_STORAGE — the
        // "All files access" toggle in system Settings. That's a special permission
        // that can't be granted via the standard runtime-permission flow; the user
        // has to be sent to the system Settings page for it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:$packageName")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                } catch (e: Exception) {
                    // Fallback to the global "All files access" list — user picks
                    // our app manually
                    try {
                        startActivity(
                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    } catch (_: Exception) {
                        // Last resort: do nothing. The RideSelectorScreen still shows
                        // a "Rescan" button so the user can retry once they grant
                        // access manually.
                    }
                }
            }
            return
        }
        // Pre-Android 11: standard runtime READ_EXTERNAL_STORAGE permission.
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}
