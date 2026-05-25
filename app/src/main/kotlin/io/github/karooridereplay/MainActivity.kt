package io.github.karooridereplay

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.karooridereplay.ui.PlaybackScreen
import io.github.karooridereplay.ui.ReplayConfigScreen
import io.github.karooridereplay.ui.ReplayViewModel
import io.github.karooridereplay.ui.RideSelectorScreen

/**
 * Single-activity host for the three Compose screens: ride picker → replay
 * config → playback control. ViewModel scoped to the activity so all three
 * screens share the same state.
 *
 * On launch, requests storage-read permission so [io.github.karooridereplay
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
            MaterialTheme {
                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "rides") {
                        composable("rides") {
                            RideSelectorScreen(
                                viewModel = viewModel,
                                onRideSelected = {
                                    viewModel.selectRide(it)
                                    nav.navigate("config")
                                }
                            )
                        }
                        composable("config") {
                            ReplayConfigScreen(
                                viewModel = viewModel,
                                onBeginPlayback = { nav.navigate("playback") }
                            )
                        }
                        composable("playback") {
                            PlaybackScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    private fun maybeRequestStoragePermission() {
        // Android 13+ uses scoped storage; READ_EXTERNAL_STORAGE is the path for
        // older API levels (Karoo OS may still be on a target where this matters).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
}
