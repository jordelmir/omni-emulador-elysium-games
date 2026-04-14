package com.elysium.console.navigation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.elysium.console.data.CoreRepositoryImpl
import com.elysium.console.data.SystemTelemetryProvider
import com.elysium.console.domain.model.ExecutionType
import com.elysium.console.domain.model.Platform
import com.elysium.console.domain.model.RomFile
import com.elysium.console.domain.usecase.SelectCoreUseCase
import com.elysium.console.ui.screen.DashboardScreen
import com.elysium.console.ui.screen.PlayerScreen
import com.elysium.console.ui.screen.SettingsScreen
import com.elysium.console.viewmodel.DashboardViewModel
import com.elysium.console.viewmodel.EmulationViewModel
import com.elysium.console.viewmodel.SettingsViewModel
import com.elysium.console.data.SettingsManager
import kotlinx.coroutines.launch
import com.elysium.console.data.RomRepositoryImpl
import com.elysium.console.data.util.NucleusFileProvisioner
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Navigation routes for the Elysium Console app.
 */
object Routes {
    const val DASHBOARD = "dashboard"
    const val PLAYER = "player/{romPath}"
    const val SETTINGS = "settings"

    fun playerRoute(romPath: String): String {
        val encoded = URLEncoder.encode(romPath, "UTF-8")
        return "player/$encoded"
    }
}

/**
 * Main navigation graph with two destinations:
 * - "dashboard" → ROM library grid with telemetry
 * - "player/{romPath}" → Emulation screen
 */
@Composable
fun ElysiumNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hardwareMonitor = SystemTelemetryProvider(context)
    val coreRepository = CoreRepositoryImpl()
    val settingsManager = SettingsManager(context)
    val romRepository = RomRepositoryImpl(context)
    val provisioner = NucleusFileProvisioner(context)
    val selectCoreUseCase = SelectCoreUseCase(coreRepository)

    // Manual DI for ViewModels
    val dashboardFactory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(hardwareMonitor, settingsManager, romRepository) as T
        }
    }

    val settingsFactory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(settingsManager, provisioner) as T
        }
    }

    val emulationFactory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return EmulationViewModel(context, hardwareMonitor, selectCoreUseCase) as T
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        modifier = modifier
    ) {
        composable(Routes.DASHBOARD) {
            val dashboardViewModel: DashboardViewModel = viewModel(factory = dashboardFactory)
            DashboardScreen(
                viewModel = dashboardViewModel,
                onRomClick = { romPath ->
                    scope.launch {
                        // Create a dummy ROM entity to resolve the core
                        val dummyRom = RomFile(
                            id = "temp", name = "temp", path = romPath,
                            platform = Platform.ARCADE, fileSizeBytes = 0L, playCount = 0
                        )
                        val core = selectCoreUseCase(dummyRom)

                        if (core?.executionType == ExecutionType.EXTERNAL_INTENT && core.androidPackageName != null) {
                            // ═══════════════════════════════════════════════════════════════
                            // EXTERNAL PIPELINE: Launch Intent (Hybrid Orchestrator)
                            // ═══════════════════════════════════════════════════════════════
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setClassName(core.androidPackageName!!, core.androidActivityName ?: "")
                                data = Uri.parse(romPath)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(
                                    context,
                                    "App no instalada: ${core.name}\nPor favor instala el APK.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            // ═══════════════════════════════════════════════════════════════
                            // INTERNAL PIPELINE: Jetpack Compose + C++ Libretro
                            // ═══════════════════════════════════════════════════════════════
                            navController.navigate(Routes.playerRoute(romPath))
                        }
                    }
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(Routes.SETTINGS) {
            val settingsViewModel: SettingsViewModel = viewModel(factory = settingsFactory)
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("romPath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("romPath").orEmpty()
            val romPath = URLDecoder.decode(encodedPath, "UTF-8")
            val emulationViewModel: EmulationViewModel = viewModel(factory = emulationFactory)

            PlayerScreen(
                romPath = romPath,
                viewModel = emulationViewModel,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
