package com.elysium.console

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.rememberNavController
import com.elysium.console.bridge.ElysiumBridge
import com.elysium.console.navigation.ElysiumNavGraph
import com.elysium.console.ui.theme.ElysiumConsoleTheme

/**
 * Main activity for Elysium Console.
 *
 * Design decisions:
 * - Decoupled from the emulation lifecycle (handled by ViewModel)
 * - Supports foldable devices via WindowManager integration
 * - Uses edge-to-edge rendering for immersive experience
 * - Handles config changes (rotation/fold) without restart
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the native bridge with app-specific directories
        val systemDir = filesDir.resolve("system").absolutePath
        val saveDir = filesDir.resolve("saves").absolutePath
        ElysiumBridge.nativeInit(systemDir, saveDir)

        // Edge-to-edge immersive mode
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ElysiumConsoleTheme {
                val navController = rememberNavController()

                ElysiumNavGraph(
                    navController = navController,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    /**
     * Hides system bars for full immersive emulation experience.
     * Uses WindowInsetsControllerCompat for API 28+ compatibility.
     */
    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
