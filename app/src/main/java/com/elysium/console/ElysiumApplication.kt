package com.elysium.console

import android.app.Application
import android.util.Log

/**
 * Elysium Console Application class.
 * Handles global initialization of native bridges and system paths.
 */
class ElysiumApplication : Application() {

    companion object {
        private const val TAG = "ElysiumApp"
    }

    override fun onCreate() {
        super.onCreate()

        // Ensure system directories exist
        val systemDir = filesDir.resolve("system")
        val saveDir = filesDir.resolve("saves")
        val coresDir = filesDir.resolve("cores")

        systemDir.mkdirs()
        saveDir.mkdirs()
        coresDir.mkdirs()

        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "  OMNI EMULADOR ELYSIUM GAMES v1.0.0")
        Log.i(TAG, "  System: ${systemDir.absolutePath}")
        Log.i(TAG, "  Saves:  ${saveDir.absolutePath}")
        Log.i(TAG, "  Cores:  ${coresDir.absolutePath}")
        Log.i(TAG, "═══════════════════════════════════════")
    }
}
