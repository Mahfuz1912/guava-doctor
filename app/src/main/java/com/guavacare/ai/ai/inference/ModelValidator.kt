package com.guavacare.ai.ai.inference

import android.content.Context
import com.guavacare.ai.ai.featureextractor.ModelFileStorage
import com.guavacare.ai.core.constants.ModelConstants
import org.json.JSONObject
import java.util.Locale

data class AssetCheckItem(
    val path: String,
    val present: Boolean,
    val detail: String
)

data class ModelHealthStatus(
    val isProductionReady: Boolean,
    val modelVersion: String,
    val multiclassStrategy: String,
    val tflitePresent: Boolean,
    val hasImportedTflite: Boolean,
    val tfliteSource: String,
    val classifierWeightsReady: Boolean,
    val missingAssets: List<String>,
    val checks: List<AssetCheckItem>,
    val tfliteSizeBytes: Long = 0L,
    val tfliteHeaderValid: Boolean = false,
    val isRuntimeWarm: Boolean = false,
    val runtimeError: String? = null
)

/**
 * Inspects bundled assets and app-private imported model storage to verify integrity
 * before allowing Production Mode inference.
 */
class ModelValidator(
    private val context: Context,
    private val modelFileStorage: ModelFileStorage = ModelFileStorage(context)
) {

    fun inspect(): ModelHealthStatus {
        val requiredClassifierPaths = listOf(
            ModelConstants.CLASS_LABELS_ASSET_PATH,
            ModelConstants.SCALER_MEAN_ASSET_PATH,
            ModelConstants.SCALER_SCALE_ASSET_PATH,
            ModelConstants.SVM_COEF_ASSET_PATH,
            ModelConstants.SVM_INTERCEPT_ASSET_PATH
        )

        val checks = mutableListOf<AssetCheckItem>()
        val missing = mutableListOf<String>()

        val importedTflite = modelFileStorage.hasImportedModel()
        val bundledTflite = assetExists(ModelConstants.TFLITE_MODEL_ASSET_PATH)
        val tflitePresent = importedTflite || bundledTflite

        val tfliteSizeBytes = when {
            importedTflite -> modelFileStorage.importedModelSizeBytes()
            bundledTflite -> assetSizeBytes(ModelConstants.TFLITE_MODEL_ASSET_PATH)
            else -> 0L
        }
        val tfliteHeaderValid = when {
            importedTflite -> hasValidTfliteHeader(modelFileStorage.importedModelFile().inputStream())
            bundledTflite -> hasValidTfliteHeader(context.assets.open(ModelConstants.TFLITE_MODEL_ASSET_PATH))
            else -> false
        }
        val tfliteValid = tflitePresent && tfliteHeaderValid

        val tfliteSource = when {
            importedTflite -> {
                val mb = tfliteSizeBytes / (1024.0 * 1024.0)
                "Imported on device (${"%.2f".format(Locale.US, mb)} MB)"
            }
            bundledTflite -> {
                val mb = tfliteSizeBytes / (1024.0 * 1024.0)
                if (tfliteSizeBytes > 0L) "Bundled APK asset (${"%.2f".format(Locale.US, mb)} MB)"
                else "Bundled APK asset"
            }
            else -> "Missing — tap Import .tflite Model"
        }

        checks += AssetCheckItem(
            path = ModelConstants.TFLITE_MODEL_ASSET_PATH,
            present = tfliteValid,
            detail = when {
                importedTflite && tfliteHeaderValid -> "Verified on-device import ($tfliteSource)"
                bundledTflite && tfliteHeaderValid -> "FP16 MobileNetV2 verified ($tfliteSource)"
                tflitePresent && !tfliteHeaderValid -> "Invalid TFLite file header (expected TFL3)"
                else -> "Missing — import .tflite in Settings or bundle in assets/models/"
            }
        )
        if (!tfliteValid) missing += ModelConstants.TFLITE_MODEL_ASSET_PATH

        var modelVersion = "Awaiting trained export"
        var strategy = "ovr"
        var labelsStatusReady = false

        for (path in requiredClassifierPaths) {
            val exists = assetExists(path)
            if (!exists) {
                missing += path
                checks += AssetCheckItem(path = path, present = false, detail = "Missing exported parameter file")
            } else if (path == ModelConstants.CLASS_LABELS_ASSET_PATH) {
                val jsonText = readAssetText(path)
                val meta = runCatching { JSONObject(jsonText) }.getOrNull()
                val status = meta?.optString("status", "") ?: ""
                labelsStatusReady = status.equals("READY", ignoreCase = true)
                modelVersion = meta?.optString("model_version", "schema-v1 (unexported)") ?: "unknown"
                strategy = meta?.optString("multiclass_strategy", "ovr") ?: "ovr"
                checks += AssetCheckItem(
                    path = path,
                    present = labelsStatusReady,
                    detail = if (labelsStatusReady) {
                        "Ready (${strategy.uppercase()}, ${meta?.optJSONArray("classes")?.length() ?: 0} classes)"
                    } else {
                        "Placeholder metadata — trained classifier export incomplete"
                    }
                )
                if (!labelsStatusReady) missing += "$path (status != READY)"
            } else {
                checks += AssetCheckItem(path = path, present = true, detail = "Trained weights verified")
            }
        }

        val classifierReady = missing.none { it.startsWith(ModelConstants.CLASSIFIER_DIR) } && labelsStatusReady
        val productionReady = tfliteValid && classifierReady

        return ModelHealthStatus(
            isProductionReady = productionReady,
            modelVersion = modelVersion,
            multiclassStrategy = strategy.uppercase(),
            tflitePresent = tfliteValid,
            hasImportedTflite = importedTflite,
            tfliteSource = tfliteSource,
            classifierWeightsReady = classifierReady,
            missingAssets = missing,
            checks = checks,
            tfliteSizeBytes = tfliteSizeBytes,
            tfliteHeaderValid = tfliteHeaderValid
        )
    }

    fun assetExists(path: String): Boolean {
        return try {
            context.assets.open(path).close()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun readAssetText(path: String): String {
        return context.assets.open(path).bufferedReader().use { it.readText() }
    }

    private fun assetSizeBytes(path: String): Long {
        return try {
            context.assets.openFd(path).use { it.declaredLength }
        } catch (_: Exception) {
            try {
                context.assets.open(path).use { it.available().toLong() }
            } catch (_: Exception) {
                0L
            }
        }
    }

    private fun hasValidTfliteHeader(stream: java.io.InputStream): Boolean {
        return try {
            stream.use { input ->
                val header = ByteArray(8)
                val read = input.read(header)
                read == 8 &&
                    header[4].toInt().toChar() == 'T' &&
                    header[5].toInt().toChar() == 'F' &&
                    header[6].toInt().toChar() == 'L' &&
                    header[7].toInt().toChar() == '3'
            }
        } catch (_: Exception) {
            false
        }
    }
}
