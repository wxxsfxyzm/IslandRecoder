package com.island.recorder.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileManager(private val context: Context) {

    companion object {
        const val DEFAULT_STORAGE_PATH = "/storage/emulated/0/DCIM/screenrecorder"
        private const val FILE_PREFIX = "Screenrecorder-"
        private const val FILE_EXTENSION = ".mp4"
    }

    private val prefsManager = PreferencesManager(context)

    fun getRecordingsDirectory(): File {
        val path = prefsManager.getStoragePath()
        val dir = File(path)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun createRecordingFile(): File {
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(Date())
        val fileName = "$FILE_PREFIX$timestamp$FILE_EXTENSION"
        return File(getRecordingsDirectory(), fileName)
    }

    fun getAllRecordings(): List<File> {
        val dir = getRecordingsDirectory()
        if (!dir.exists()) return emptyList()
        return dir.listFiles { file ->
            file.isFile && file.extension == "mp4"
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun deleteRecording(file: File): Boolean {
        return file.delete()
    }

    fun getAvailableSpace(): Long {
        val dir = getRecordingsDirectory()
        return dir.usableSpace
    }

    fun hasEnoughSpace(estimatedDurationMinutes: Int, bitrate: Int): Boolean {
        val estimatedSizeBytes = (estimatedDurationMinutes * 60L * bitrate) / 8
        val requiredSpace = (estimatedSizeBytes * 1.2).toLong()
        return getAvailableSpace() > requiredSpace
    }

    /**
     * Copy recorded video to public DCIM directory for gallery visibility
     * Returns the public file on success, null if using scoped storage
     */
    fun copyToPublicGallery(sourceFile: File): File? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ uses MediaStore (Scoped Storage)
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return null

            resolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            null // Return null to indicate scoped storage is used
        } else {
            // Android 9 and below - copy to public DCIM directory
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            val destFile = File(publicDir, sourceFile.name)
            sourceFile.copyTo(destFile, overwrite = true)
            destFile
        }
    }
}
