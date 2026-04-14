package com.elysium.console.data.downloader

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class CoreDownloader(private val context: Context) {

    // Common cores from Libretro Buildbot (arm64-v8a)
    val availableCores = mapOf(
        "snes9x" to "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/snes9x_libretro_android.so.zip",
        "nestopia" to "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/nestopia_libretro_android.so.zip",
        "mgba" to "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/mgba_libretro_android.so.zip",
        "genesis_plus_gx" to "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/genesis_plus_gx_libretro_android.so.zip"
    )

    suspend fun isCoreInstalled(coreName: String): Boolean = withContext(Dispatchers.IO) {
        val coresDir = File(context.filesDir, "cores")
        if (!coresDir.exists()) return@withContext false
        
        val expectedFile = File(coresDir, "${coreName}_libretro_android.so")
        return@withContext expectedFile.exists()
    }

    suspend fun getCorePath(coreName: String): String? = withContext(Dispatchers.IO) {
        val file = File(File(context.filesDir, "cores"), "${coreName}_libretro_android.so")
        return@withContext if (file.exists()) file.absolutePath else null
    }

    sealed class DownloadState {
        object Connecting : DownloadState()
        data class Downloading(val progress: Float) : DownloadState()
        data class Extracting(val fileName: String) : DownloadState()
        object Success : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    fun downloadCore(coreName: String): Flow<DownloadState> = flow {
        val urlString = availableCores[coreName]
        if (urlString == null) {
            emit(DownloadState.Error("Core URL not found."))
            return@flow
        }

        try {
            emit(DownloadState.Connecting)
            
            val coresDir = File(context.filesDir, "cores")
            if (!coresDir.exists()) coresDir.mkdirs()

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "OmniElysiumDownloader/1.0")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                emit(DownloadState.Error("Server returned HTTP ${connection.responseCode}"))
                return@flow
            }

            val fileLength = connection.contentLength
            val zipFile = File(context.cacheDir, "${coreName}.zip")
            
            // Download phase
            connection.inputStream.use { input ->
                FileOutputStream(zipFile).use { output ->
                    val data = ByteArray(8192)
                    var total: Long = 0
                    var count: Int
                    
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        if (fileLength > 0) {
                            emit(DownloadState.Downloading((total * 100f) / fileLength))
                        }
                        output.write(data, 0, count)
                    }
                }
            }

            // Extraction phase
            emit(DownloadState.Extracting(zipFile.name))
            
            ZipInputStream(zipFile.inputStream()).use { zin ->
                var entry = zin.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".so")) { // We only care about the .so library
                        val outFile = File(coresDir, entry.name)
                        FileOutputStream(outFile).use { output ->
                            zin.copyTo(output)
                        }
                        // Ensure it's executable
                        outFile.setExecutable(true, false)
                    }
                    zin.closeEntry()
                    entry = zin.nextEntry
                }
            }

            // Cleanup
            zipFile.delete()

            emit(DownloadState.Success)

        } catch (e: Exception) {
            Log.e("CoreDownloader", "Failed to download core: ${e.message}", e)
            emit(DownloadState.Error(e.localizedMessage ?: "Unknown network error"))
        }
    }.flowOn(Dispatchers.IO)
}
