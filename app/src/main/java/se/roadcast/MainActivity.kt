package se.roadcast

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import se.roadcast.feature.debug.DebugScreen
import se.roadcast.feature.debug.DebugViewModel
import se.roadcast.feature.history.HistoryScreen
import se.roadcast.feature.history.HistoryViewModel
import se.roadcast.feature.player.PlayerScreen
import se.roadcast.feature.player.PlayerViewModel
import se.roadcast.feature.settings.SettingsScreen
import se.roadcast.feature.settings.SettingsViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RoadcastApp() }
    }
}

private enum class Destination(val route: String, val label: String, val glyph: String) {
    Player("player", "Player", "▶"),
    History("history", "History", "↺"),
    Settings("settings", "Settings", "⚙"),
    Debug("debug", "Debug", "⌁"),
}

private val RoadcastColors = lightColorScheme(
    primary = Color(0xFF006A62),
    onPrimary = Color.White,
    secondary = Color(0xFF4B635F),
    background = Color(0xFFF7F8F2),
    surface = Color(0xFFF7F8F2),
    surfaceVariant = Color(0xFFDAE5E1),
)

@Composable
private fun RoadcastApp() {
    MaterialTheme(colorScheme = RoadcastColors) {
        val navController = rememberNavController()
        val entry by navController.currentBackStackEntryAsState()
        val selectedRoute = entry?.destination?.route
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = selectedRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(Destination.Player.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(destination.glyph) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Destination.Player.route,
                modifier = Modifier.padding(padding),
            ) {
                composable(Destination.Player.route) {
                    val viewModel: PlayerViewModel = hiltViewModel()
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    LocationPermissionEffect(
                        requests = viewModel.permissionRequests,
                        onResult = viewModel::onPermissionResult,
                    )
                    PlayerScreen(
                        state = state,
                        onToggleJourney = viewModel::toggleJourney,
                        onPlayPause = viewModel::playOrPause,
                        onReplay = viewModel::replay,
                        onSkip = viewModel::skip,
                        onAsk = viewModel::openAsk,
                        onTellMeMore = viewModel::tellMeMore,
                        onAskQuestionChange = viewModel::updateAskQuestion,
                        onSuggestedQuestion = viewModel::useSuggestedQuestion,
                        onSubmitAsk = viewModel::submitAsk,
                        onCancelAsk = viewModel::cancelAsk,
                        onOpenDebug = { navController.navigate(Destination.Debug.route) },
                    )
                }
                composable(Destination.History.route) {
                    val viewModel: HistoryViewModel = hiltViewModel()
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    HistoryScreen(state)
                }
                composable(Destination.Settings.route) {
                    val viewModel: SettingsViewModel = hiltViewModel()
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    LocationPermissionEffect(
                        requests = viewModel.permissionRequests,
                        onResult = viewModel::onPermissionResult,
                    )
                    SettingsScreen(
                        state = state,
                        onAutoPlay = viewModel::setAutoPlay,
                        onSimulationEnabled = viewModel::setSimulationEnabled,
                        onRemoteEnabled = viewModel::setRemoteEnabled,
                    )
                }
                composable(Destination.Debug.route) {
                    val viewModel: DebugViewModel = hiltViewModel()
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    DebugScreen(
                        state = state,
                        onToggle = viewModel::toggle,
                        onReset = viewModel::reset,
                        onJump = viewModel::jump,
                        onSpeed = viewModel::setSpeed,
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationPermissionEffect(
    requests: kotlinx.coroutines.flow.SharedFlow<Unit>,
    onResult: (Boolean) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        onResult(granted)
    }
    LaunchedEffect(requests) {
        requests.collectLatest {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }
}
