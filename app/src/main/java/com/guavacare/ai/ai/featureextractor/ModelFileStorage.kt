package com.guavacare.ai.ai.featureextractor

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class TFLiteImportSummary(
    val fileSizeBytes: Long,
    val inputShape: IntArray,
    val outputFeatureShape: IntArray,
    val spatialMapShape: IntArray?
)

/**
 * Manages app-private storage for a user-imported MobileNetV2 FP16 `.tflite` model
 * imported via Android's zero-permission Storage Access Framework (`OpenDocument`).
 *
 * This lets researchers and developers load `feature_extractor_fp16.tflite` directly
 * on device even when platform upload limits prevent bundling the binary at build time.
 */
class ModelFileStorage(private val context: Context) {

    private val modelsDir: File
        get() = File(context.filesDir, "models").apply {
            if (!exists()) mkdirs()
        }

    fun importedModelFile(): File = File(modelsDir, MODEL_FILE_NAME)

    fun hasImportedModel(): Boolean {
        val file = importedModelFile()
        return file.exists() && file.isFile && file.length() > MIN_MODEL_BYTES && hasValidTfliteMagic(file)
    }

    fun importedModelSizeBytes(): Long {
        val file = importedModelFile()
        return if (file.exists() && file.isFile) file.length() else 0L
    }

    fun importTfliteFromUri(uri: Uri): Result<TFLiteImportSummary> = runCatching {
        val targetFile = importedModelFile()
        val tempFile = File(modelsDir, "$MODEL_FILE_NAME.tmp")
        if (tempFile.exists()) tempFile.delete()

        val resolver = context.contentResolver
        val bytesCopied = resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_MODEL_BYTES) {
                        throw IllegalArgumentException(
                            "Selected file exceeds the 128 MB limit for an on-device TFLite model."
                        )
                    }
                    output.write(buffer, 0, read)
                }
                output.flush()
                total
            }
        } ?: throw IllegalArgumentException("Unable to read the selected file from Android storage.")

        try {
            require(bytesCopied > MIN_MODEL_BYTES) {
                "Selected file is too small ($bytesCopied bytes) to be a valid MobileNetV2 .tflite model."
            }
            require(hasValidTfliteMagic(tempFile)) {
                "Selected file is not a valid TensorFlow Lite (.tflite) binary (missing TFL3 FlatBuffer header)."
            }

            // Validate actual TFLite interpreter graph and [1,224,224,3] -> [1,1280] tensor contract
            val metadata = TFLiteFeatureExtractor.validateModelFile(tempFile)

            if (targetFile.exists()) targetFile.delete()
            val moved = tempFile.renameTo(targetFile)
            if (!moved) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            TFLiteImportSummary(
                fileSizeBytes = targetFile.length(),
                inputShape = metadata.inputShape,
                outputFeatureShape = metadata.outputFeatureShape,
                spatialMapShape = metadata.spatialMapShape
            )
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        }
    }

    fun deleteImportedModel(): Boolean {
        val file = importedModelFile()
        return if (file.exists()) file.delete() else false
    }

    companion object {
        const val MODEL_FILE_NAME = "feature_extractor_fp16.tflite"
        private const val MIN_MODEL_BYTES = 1024L
        private const val MAX_MODEL_BYTES = 128L * 1024L * 1024L

        fun hasValidTfliteMagic(file: File): Boolean {
            if (!file.exists() || file.length() < 8L) return false
            return runCatching {
                FileInputStream(file).use { fis ->
                    val header = ByteArray(8)
                    val read = fis.read(header)
                    read == 8 &&
                        header[4] == 'T'.code.toByte() &&
                        header[5] == 'F'.code.toByte() &&
                        header[6] == 'L'.code.toByte() &&
                        header[7] == '3'.code.toByte()
                }
            }.getOrDefault(false)
        }
    }
}
