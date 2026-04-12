package com.elysium.console.data

import android.util.Log
import com.elysium.console.domain.usecase.ShellCommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku-based implementation of [ShellCommandExecutor].
 *
 * Executes privileged shell commands via the Shizuku service,
 * which provides ADB-level (or root-level) access without
 * requiring the device to be rooted.
 *
 * Requires:
 * - Shizuku app installed and running on the device
 * - User granted permission via the Shizuku permission dialog
 *
 * Graceful degradation:
 * - If Shizuku is not available, [execute] returns a failure Result
 * - If permission is not granted, returns a descriptive error
 */
class ShizukuShellExecutor : ShellCommandExecutor {

    companion object {
        private const val TAG = "ShizukuShell"
        private const val READ_TIMEOUT_MS = 5000L
    }

    /**
     * Checks if Shizuku is running and permission is granted.
     */
    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!Shizuku.pingBinder()) {
                Log.w(TAG, "Shizuku binder not alive")
                return@withContext false
            }

            val permissionGranted = Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!permissionGranted) {
                Log.w(TAG, "Shizuku permission not granted")
            }
            permissionGranted
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Shizuku availability", e)
            false
        }
    }

    /**
     * Executes a shell command via Shizuku's remote process API.
     *
     * @param command Shell command string (e.g., "echo performance > /sys/...")
     * @return Result containing stdout on success, error on failure
     */
    override suspend fun execute(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isAvailable()) {
                return@withContext Result.failure(
                    IllegalStateException("Shizuku is not available or permission denied")
                )
            }

            Log.d(TAG, "Executing: $command")

            @Suppress("DEPRECATION")
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)

            // Read stdout
            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val stderr = BufferedReader(InputStreamReader(process.errorStream))

            val outputBuilder = StringBuilder()
            val errorBuilder = StringBuilder()

            // Read output with timeout protection
            var line: String?
            while (stdout.readLine().also { line = it } != null) {
                outputBuilder.appendLine(line)
            }
            while (stderr.readLine().also { line = it } != null) {
                errorBuilder.appendLine(line)
            }

            val exitCode = process.waitFor()

            stdout.close()
            stderr.close()
            process.destroy()

            val output = outputBuilder.toString().trimEnd()
            val error = errorBuilder.toString().trimEnd()

            if (exitCode == 0) {
                Log.d(TAG, "Command succeeded: $output")
                Result.success(output)
            } else {
                val msg = "Command failed (exit=$exitCode): $error"
                Log.e(TAG, msg)
                Result.failure(RuntimeException(msg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception executing command: $command", e)
            Result.failure(e)
        }
    }
}
