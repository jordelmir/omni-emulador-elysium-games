package com.elysium.console.data.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream

/**
 * NUCLEUS FILE PROVISIONER
 * 
 * Bridges Android's Scoped Storage (SAF) with the native filesystem.
 * This utility "shadows" external firmware/asset files into an internal directory
 * so that C++ emulation cores can access them via standard Posix file paths.
 */
class NucleusFileProvisioner(private val context: Context) {

    companion object {
        private const val TAG = "NucleusProvisioner"
        private const val NUCLEUS_DIR = "system"
    }

    /**
     * Internal directory where shadowed files are stored.
     * /data/user/0/com.elysium.console/files/nucleus
     */
    val internalNucleusDir: File by lazy {
        val dir = File(context.filesDir, NUCLEUS_DIR)
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    /**
     * Copies a file from a SAF URI to the internal nucleus directory.
     * @param uri The content:// URI selected by the user.
     * @param targetFileName The desired name in the internal storage, or null to auto-extract original name.
     * @return Absolute path to the internal shadow copy, or null if failed.
     */
    fun provisionSystemFile(uri: Uri, targetFileName: String?): String? {
        return try {
            val fileName = targetFileName ?: getFileNameFromUri(uri) ?: "unknown_asset.bin"
            val targetFile = File(internalNucleusDir, fileName)
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            Log.i(TAG, "Successfully provisioned system file: $targetFileName at ${targetFile.absolutePath}")
            
            // Critical for drivers: ensure the file is executable so it can be loaded by dlopen / Vulkan loader
            targetFile.setExecutable(true, false)
            
            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to provision system file: ${e.message}", e)
            null
        }
    }

    /**
     * Verifies if a provisioned file exists and returns its path.
     */
    fun getProvisionedPath(fileName: String): String? {
        val file = File(internalNucleusDir, fileName)
        return if (file.exists()) file.absolutePath else null
    }

    /**
     * Extracts the original filename from a SAF URI.
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        name = cursor.getString(index)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                if (cut != -1) path.substring(cut + 1) else path
            }
        }
        return name
    }

    /**
     * Clears all provisioned shadow files to free up internal space.
     */
    fun clearNucleus() {
        internalNucleusDir.deleteRecursively()
        internalNucleusDir.mkdirs()
    }
}
