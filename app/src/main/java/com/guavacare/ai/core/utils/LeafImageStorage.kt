package com.guavacare.ai.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Manages app-private internal storage for scanned guava leaf images,
 * ensuring proper EXIF upright rotation, atomic error cleanup, and safe file deletion.
 */
class LeafImageStorage(private val context: Context) {

    private val scansDir: File
        get() = File(context.filesDir, "leaf_scans").apply {
            if (!exists()) mkdirs()
        }

    fun createTempCaptureFile(): File {
        return File(scansDir, "capture_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
    }

    suspend fun importFromUri(uri: Uri): Result<Pair<String, Bitmap>> = withContext(Dispatchers.IO) {
        runCatching {
            val mime = context.contentResolver.getType(uri)
            if (mime != null && !mime.startsWith("image/")) {
                throw IllegalArgumentException("Unsupported file format: $mime. Please select a JPEG, PNG, or WebP leaf photo.")
            }
            val tempFile = createTempCaptureFile()
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Unable to read selected image.")

                val oriented = decodeAndOrientBitmap(tempFile.absolutePath)
                    ?: throw IllegalArgumentException("Unable to decode image. Format may be corrupted or unsupported.")

                // Re-save cleanly oriented, bounded JPEG to keep internal storage compact
                FileOutputStream(tempFile).use { fos ->
                    oriented.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                }
                tempFile.absolutePath to oriented
            } catch (t: Throwable) {
                runCatching { tempFile.delete() }
                throw t
            }
        }
    }

    suspend fun loadOrientedBitmap(path: String): Bitmap? = withContext(Dispatchers.IO) {
        decodeAndOrientBitmap(path)
    }

    fun deleteImageSafely(path: String) {
        runCatching {
            if (path.isBlank()) return
            val target = File(path)
            val basePrefix = scansDir.canonicalPath.trimEnd(File.separatorChar) + File.separator
            if (target.exists() && target.canonicalPath.startsWith(basePrefix)) {
                target.delete()
            }
        }
    }

    fun deleteAllScanImages() {
        runCatching {
            scansDir.listFiles()?.forEach { it.delete() }
        }
    }

    fun calculateStorageBytes(): Long {
        return scansDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun decodeAndOrientBitmap(filePath: String, maxDimension: Int = 1440): Bitmap? {
        if (filePath.isBlank()) return null
        val file = File(filePath)
        if (!file.exists() || !file.isFile || file.length() <= 0L) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension * 2 || bounds.outHeight / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(filePath, decodeOpts) ?: return null

        val orientation = runCatching {
            ExifInterface(filePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    postRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    postRotate(270f)
                    postScale(-1f, 1f)
                }
                else -> return decoded
            }
        }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated != decoded) decoded.recycle()
        return rotated
    }
}
