package com.photoeditor.photoeffect.utils

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.*
import java.net.URL
import java.security.MessageDigest
import java.text.DecimalFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.experimental.and

object FileUtils {
    private const val TAG = "FileUtils"
    private const val DEBUG = false

    // Scoped storage fix: Use internal/external cache instead of absolute root path
    fun getTempFolder(context: Context): String {
        val folder = File(context.externalCacheDir, "Temp")
        if (!folder.exists()) folder.mkdirs()
        return folder.absolutePath
    }

    // Comparison logic for sorting files alphabetically
    var sComparator: Comparator<File> = Comparator { f1, f2 ->
        f1.name.lowercase().compareTo(f2.name.lowercase())
    }

    // Filter to return only non-hidden files
    var sFileFilter = FileFilter { file ->
        file.isFile && !file.name.startsWith(".")
    }

    /**
     * Gets the extension of a file (e.g., ".jpg")
     */
    fun getExtension(uri: String?): String? {
        if (uri == null) return null
        val dot = uri.lastIndexOf(".")
        return if (dot >= 0) uri.substring(dot) else ""
    }

    /**
     * Checks if a Uri points to a MediaStore provider
     */
    fun isMediaUri(uri: Uri): Boolean {
        return "media".equals(uri.authority, ignoreCase = true)
    }

    /**
     * Returns the MimeType for a file based on its extension
     */
    fun getMimeType(file: File): String {
        val extension = getExtension(file.name)
        return if (!extension.isNullOrEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.substring(1)) ?: "application/octet-stream"
        } else "application/octet-stream"
    }

    /**
     * FIXED: Stream saving logic.
     * Uses Kotlin's .use and .copyTo for safety and to prevent data corruption.
     */
    fun saveToFile(inputStream: InputStream, dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            dest.outputStream().use { output ->
                inputStream.use { input ->
                    input.copyTo(output) // This replaces the buggy manual while loop
                }
            }
            true
        } catch (e: IOException) {
            Log.e(TAG, "saveToFile: ", e)
            false
        }
    }

    /**
     * Get a file path from a Uri. (Supports DocumentProvider and MediaStore)
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    fun getPath(context: Context, uri: Uri): String? {
        val isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT

        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                if ("primary".equals(split[0], ignoreCase = true)) {
                    return "${Environment.getExternalStorageDirectory()}/${split[1]}"
                }
            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"), id.toLong()
                )
                return getDataColumn(context, contentUri, null, null)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                val contentUri = when (split[0]) {
                    "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    else -> null
                }
                return getDataColumn(context, contentUri, "_id=?", arrayOf(split[1]))
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            return if (isGooglePhotosUri(uri)) uri.lastPathSegment
            else getDataColumn(context, uri, null, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    /**
     * Helper to query MediaStore columns
     */
    private fun getDataColumn(context: Context, uri: Uri?, selection: String?, selectionArgs: Array<String>?): String? {
        val column = "_data"
        val projection = arrayOf(column)
        context.contentResolver.query(uri ?: return null, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(column))
            }
        }
        return null
    }

    /**
     * Formats bytes into human-readable size (KB, MB, GB)
     */
    fun getReadableFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    /**
     * FIXED: ZIP extraction logic
     */
    fun unzip(zipFilePath: String, outFolder: String) {
        val destination = File(outFolder)
        if (!destination.exists()) destination.mkdirs()

        ZipFile(File(zipFilePath)).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val destFile = File(outFolder, entry.name)
                destFile.parentFile?.mkdirs()
                if (!entry.isDirectory) {
                    zip.getInputStream(entry).use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    /**
     * Saves a Bitmap to a specific path as PNG
     */
    fun saveBitmapToFile(bitmap: Bitmap, outPath: String): String? {
        val outFile = File(outPath)
        return try {
            outFile.parentFile?.mkdirs()
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            outFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "saveBitmapToFile: ", e)
            null
        }
    }

    /**
     * Generates an MD5 hash for an InputStream
     */
    fun generateMD5(inputStream: InputStream?): String? {
        if (inputStream == null) return null
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(8192)
            inputStream.use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            byteArrayToHexString(digest.digest())
        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("DefaultLocale")
    fun byteArrayToHexString(b: ByteArray): String {
        return b.joinToString("") { "%02x".format(it) }
    }

    // URI Authority Check Helpers
    fun isExternalStorageDocument(uri: Uri) = uri.authority == "com.android.externalstorage.documents"
    fun isDownloadsDocument(uri: Uri) = uri.authority == "com.android.providers.downloads.documents"
    fun isMediaDocument(uri: Uri) = uri.authority == "com.android.providers.media.documents"
    fun isGooglePhotosUri(uri: Uri) = uri.authority == "com.google.android.apps.photos.content"
}