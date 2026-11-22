package com.guavacare.ai.ai.inference

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import com.guavacare.ai.ai.classifier.LinearSVMClassifier
import com.guavacare.ai.ai.classifier.StandardScaler
import com.guavacare.ai.ai.classifier.SvmMulticlassStrategy
import com.guavacare.ai.ai.explainability.HybridExplainabilityEngine
import com.guavacare.ai.ai.featureextractor.ModelFileStorage
import com.guavacare.ai.ai.featureextractor.TFLiteFeatureExtractor
import com.guavacare.ai.ai.featureextractor.TFLiteImportSummary
import com.guavacare.ai.ai.preprocessing.ImagePreprocessor
import com.guavacare.ai.ai.preprocessing.ImageQualityValidator
import com.guavacare.ai.core.constants.ModelConstants
import com.guavacare.ai.domain.model.GuavaLeafClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Reusable hybrid inference orchestrator executing:
 *   Image Quality Gate -> 224x224 [-1, 1] RGB Preprocessing ->
 *   MobileNetV2 FP16 TFLite (1280-D) -> StandardScaler -> Linear SVM ->
 *   SVM-Weighted CAM / Occlusion Attribution.
 *
 * Production Mode refuses to return a diagnosis if trained model assets are missing or invalid.
 * Development Mock Mode is strictly opt-in, isolated, and visibly flagged on every result.
 */
class HybridInferenceEngine(
    private val context: Context,
    private val qualityValidator: ImageQualityValidator = ImageQualityValidator(),
    private val preprocessor: ImagePreprocessor = ImagePreprocessor(),
    private val explainabilityEngine: HybridExplainabilityEngine = HybridExplainabilityEngine(preprocessor),
    private val modelFileStorage: ModelFileStorage = ModelFileStorage(context),
    private val modelValidator: ModelValidator = ModelValidator(context, modelFileStorage),
    private val occlusionGridSize: Int = 5
) : AutoCloseable {

    private val loadLock = Any()
    private var featureExtractor: TFLiteFeatureExtractor? = null
    private var standardScaler: StandardScaler? = null
    private var svmClassifier: LinearSVMClassifier? = null
    private var loadedModelVersion: String = "unloaded"

    @Volatile
    var lastMeasuredInferenceMs: Long? = null
        private set

    fun isPipelineLoadedInMemory(): Boolean =
        featureExtractor != null && standardScaler != null && svmClassifier != null

    fun inspectModelHealth(): ModelHealthStatus =
        modelValidator.inspect().copy(isRuntimeWarm = isPipelineLoadedInMemory())

    fun currentColdInitMs(): Long? = featureExtractor?.currentMetadata()?.coldInitTimeMs

    /**
     * Verifies bundled/imported model assets and preloads LiteRT + StandardScaler + Linear SVM
     * into memory so the UI can report live inference readiness.
     * Gracefully handles UnsatisfiedLinkError on JVM test hosts (e.g. Robolectric).
     */
    suspend fun preloadAndVerifyPipeline(): ModelHealthStatus = withContext(Dispatchers.Default) {
        val baseHealth = modelValidator.inspect()
        if (!baseHealth.isProductionReady) {
            return@withContext baseHealth.copy(isRuntimeWarm = false, runtimeError = null)
        }
        return@withContext try {
            ensureProductionPipelineLoaded(baseHealth.modelVersion)
            baseHealth.copy(isRuntimeWarm = true, runtimeError = null)
        } catch (t: Throwable) {
            if (isUnsatisfiedLinkCause(t)) {
                // On JVM host unit tests without Android native .so libs, still verify SVM & scaler weights
                try {
                    val scaler = StandardScaler.fromJson(
                        meanJson = modelValidator.readAssetText(ModelConstants.SCALER_MEAN_ASSET_PATH),
                        scaleJson = modelValidator.readAssetText(ModelConstants.SCALER_SCALE_ASSET_PATH)
                    )
                    val svm = LinearSVMClassifier.fromJson(
                        labelsJson = modelValidator.readAssetText(ModelConstants.CLASS_LABELS_ASSET_PATH),
                        coefJson = modelValidator.readAssetText(ModelConstants.SVM_COEF_ASSET_PATH),
                        interceptJson = modelValidator.readAssetText(ModelConstants.SVM_INTERCEPT_ASSET_PATH)
                    )
                    require(scaler.featureDimension == ModelConstants.FEATURE_DIMENSION)
                    require(svm.featureDimension == ModelConstants.FEATURE_DIMENSION)
                    baseHealth.copy(isRuntimeWarm = false, runtimeError = null)
                } catch (weightErr: Throwable) {
                    baseHealth.copy(
                        isProductionReady = false,
                        classifierWeightsReady = false,
                        isRuntimeWarm = false,
                        runtimeError = weightErr.message ?: "Failed to parse classifier weights"
                    )
                }
            } else {
                baseHealth.copy(
                    isProductionReady = false,
                    isRuntimeWarm = false,
                    runtimeError = t.message ?: "Failed to initialize inference pipeline"
                )
            }
        }
    }

    private fun isUnsatisfiedLinkCause(t: Throwable?): Boolean {
        var cur: Throwable? = t
        while (cur != null) {
            if (cur is UnsatisfiedLinkError || cur is NoClassDefFoundError) return true
            cur = cur.cause
        }
        return false
    }

    suspend fun importTfliteModel(uri: Uri): Result<Pair<TFLiteImportSummary, ModelHealthStatus>> =
        withContext(Dispatchers.IO) {
            modelFileStorage.importTfliteFromUri(uri).map { summary ->
                close()
                summary to modelValidator.inspect()
            }
        }

    suspend fun removeImportedTfliteModel(): ModelHealthStatus =
        withContext(Dispatchers.IO) {
            modelFileStorage.deleteImportedModel()
            close()
            modelValidator.inspect()
        }

    suspend fun checkImageQuality(bitmap: Bitmap): com.guavacare.ai.ai.preprocessing.ImageQualityReport =
        withContext(Dispatchers.Default) {
            qualityValidator.validate(bitmap)
        }

    suspend fun analyzeLeaf(
        bitmap: Bitmap,
        imagePath: String,
        enforceQualityGate: Boolean,
        allowDevMockMode: Boolean,
        onStageChanged: (String) -> Unit = {}
    ): PredictionOutcome = withContext(Dispatchers.Default) {
        try {
            onStageChanged("quality")
            val qStart = SystemClock.elapsedRealtime()
            val qualityReport = qualityValidator.validate(bitmap)
            val qualityMs = SystemClock.elapsedRealtime() - qStart

            if (qualityReport.isNonLeaf || (enforceQualityGate && !qualityReport.isValid)) {
                return@withContext PredictionOutcome.QualityRejected(
                    imagePath = imagePath,
                    report = qualityReport
                )
            }

            val health = modelValidator.inspect()
            if (!health.isProductionReady) {
                if (!allowDevMockMode) {
                    return@withContext PredictionOutcome.ModelAssetsMissing(
                        imagePath = imagePath,
                        healthStatus = health
                    )
                }
                return@withContext runIsolatedDevMockPipeline(
                    bitmap = bitmap,
                    imagePath = imagePath,
                    qualityReport = qualityReport,
                    qualityMs = qualityMs,
                    onStageChanged = onStageChanged
                )
            }

            ensureProductionPipelineLoaded(health.modelVersion)
            val extractor = requireNotNull(featureExtractor)
            val scaler = requireNotNull(standardScaler)
            val svm = requireNotNull(svmClassifier)

            onStageChanged("inference")
            val preStart = SystemClock.elapsedRealtime()
            val inputBuffer = preprocessor.preprocessToByteBuffer(bitmap)
            val preMs = SystemClock.elapsedRealtime() - preStart

            val extraction = extractor.extractFeatures(inputBuffer, includeSpatialMap = true)

            val svmStart = SystemClock.elapsedRealtime()
            val scaledFeatures = scaler.transform(extraction.featureVector)
            val svmPred = svm.predict(scaledFeatures)
            val svmMs = SystemClock.elapsedRealtime() - svmStart

            onStageChanged("explainability")
            val spatialMap = extraction.spatialFeatureMap
            val explainability = if (spatialMap != null) {
                explainabilityEngine.computeSvmWeightedFeatureMapCam(
                    spatialMap = spatialMap,
                    predictedClassIndex = svmPred.predictedIndex,
                    scaler = scaler,
                    svm = svm
                )
            } else {
                explainabilityEngine.computeHybridOcclusionSensitivity(
                    sourceBitmap = bitmap,
                    baselinePredictedClassIndex = svmPred.predictedIndex,
                    baselineDecisionScore = svmPred.decisionScore,
                    extractor = extractor,
                    scaler = scaler,
                    svm = svm,
                    gridSize = occlusionGridSize
                )
            }

            val coreInferenceMs = preMs + extraction.inferenceTimeMs + svmMs
            lastMeasuredInferenceMs = coreInferenceMs

            PredictionOutcome.Success(
                PredictionResult(
                    imagePath = imagePath,
                    predictedClass = svmPred.predictedClass,
                    decisionScore = svmPred.decisionScore,
                    calibratedConfidence = svmPred.calibratedConfidence,
                    marginToRunnerUp = svmPred.marginToRunnerUp,
                    allScores = svmPred.allScores,
                    strategy = svmPred.strategy,
                    isLowMarginUncertain = svmPred.isLowMarginUncertain,
                    qualityReport = qualityReport,
                    explainability = explainability,
                    metrics = InferenceMetrics(
                        qualityCheckMs = qualityMs,
                        preprocessingMs = preMs,
                        featureExtractionMs = extraction.inferenceTimeMs,
                        svmClassificationMs = svmMs,
                        explainabilityMs = explainability.generationTimeMs,
                        totalInferenceMs = coreInferenceMs,
                        coldInitTimeMs = extractor.currentMetadata()?.coldInitTimeMs
                    ),
                    modelVersion = loadedModelVersion,
                    isMockResult = false
                )
            )
        } catch (t: Throwable) {
            PredictionOutcome.Error(
                message = t.message ?: "Unexpected inference failure",
                cause = t
            )
        }
    }

    private fun ensureProductionPipelineLoaded(version: String) {
        if (featureExtractor != null && standardScaler != null && svmClassifier != null) return
        synchronized(loadLock) {
            if (featureExtractor != null && standardScaler != null && svmClassifier != null) return
            val extractor = TFLiteFeatureExtractor(
                context = context,
                modelFileStorage = modelFileStorage
            )
            extractor.ensureInitialized()

            val scaler = StandardScaler.fromJson(
                meanJson = modelValidator.readAssetText(ModelConstants.SCALER_MEAN_ASSET_PATH),
                scaleJson = modelValidator.readAssetText(ModelConstants.SCALER_SCALE_ASSET_PATH)
            )
            val svm = LinearSVMClassifier.fromJson(
                labelsJson = modelValidator.readAssetText(ModelConstants.CLASS_LABELS_ASSET_PATH),
                coefJson = modelValidator.readAssetText(ModelConstants.SVM_COEF_ASSET_PATH),
                interceptJson = modelValidator.readAssetText(ModelConstants.SVM_INTERCEPT_ASSET_PATH)
            )

            require(scaler.featureDimension == ModelConstants.FEATURE_DIMENSION) {
                "Scaler feature dim (${scaler.featureDimension}) does not match MobileNetV2 (${ModelConstants.FEATURE_DIMENSION})."
            }
            require(svm.featureDimension == ModelConstants.FEATURE_DIMENSION) {
                "SVM feature dim (${svm.featureDimension}) does not match MobileNetV2 (${ModelConstants.FEATURE_DIMENSION})."
            }

            featureExtractor = extractor
            standardScaler = scaler
            svmClassifier = svm
            loadedModelVersion = version
        }
    }

    /**
     * Strictly isolated Development Mock pipeline for testing end-to-end UI, explainability overlays,
     * Room history, and Insights before trained model assets are bundled.
     * Uses deterministic color-statistics heuristics on the input image and marks `isMockResult = true`.
     */
    private fun runIsolatedDevMockPipeline(
        bitmap: Bitmap,
        imagePath: String,
        qualityReport: com.guavacare.ai.ai.preprocessing.ImageQualityReport,
        qualityMs: Long,
        onStageChanged: (String) -> Unit
    ): PredictionOutcome.Success {
        onStageChanged("inference")
        val preStart = SystemClock.elapsedRealtime()
        val resized = preprocessor.resizeForModel(bitmap)
        val pixels = IntArray(resized.width * resized.height)
        resized.getPixels(pixels, 0, resized.width, 0, 0, resized.width, resized.height)
        if (resized != bitmap) resized.recycle()
        val preMs = SystemClock.elapsedRealtime() - preStart

        val scoreStart = SystemClock.elapsedRealtime()
        var greenPx = 0
        var yellowPx = 0
        var brownPx = 0
        var darkPx = 0
        var brightHolePx = 0
        for (p in pixels) {
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            val lum = (0.299f * r + 0.587f * g + 0.114f * b)
            when {
                lum < 48f -> darkPx++
                r > 130 && g > 125 && b < 95 && r >= g - 15 -> yellowPx++
                r > 90 && g in 45..115 && b < 75 && r > g + 15 -> brownPx++
                g > r + 10 && g > b + 12 -> greenPx++
                lum > 215f -> brightHolePx++
            }
        }
        val n = max(1, pixels.size).toFloat()
        val scores = linkedMapOf(
            GuavaLeafClass.ANTHRACNOSE to (-0.65f + (brownPx / n) * 4.2f),
            GuavaLeafClass.HEALTHY to (-0.30f + (greenPx / n) * 2.4f - (brownPx / n) * 2.0f - (darkPx / n) * 1.6f),
            GuavaLeafClass.NUTRIENT_STRESS to (-0.55f + (yellowPx / n) * 3.8f),
            GuavaLeafClass.PEST_DAMAGE to (-0.70f + (brightHolePx / n) * 2.6f + (brownPx / n) * 1.1f),
            GuavaLeafClass.SOOTY_MOLD to (-0.60f + (darkPx / n) * 3.6f)
        )
        val sorted = scores.entries.sortedByDescending { it.value }
        val top = sorted[0]
        val runnerUp = sorted[1]
        val margin = top.value - runnerUp.value
        val scoreMs = SystemClock.elapsedRealtime() - scoreStart

        onStageChanged("explainability")
        val explainability = explainabilityEngine.computeDevMockContrastMap(bitmap)
        val totalMs = preMs + scoreMs
        lastMeasuredInferenceMs = totalMs

        return PredictionOutcome.Success(
            PredictionResult(
                imagePath = imagePath,
                predictedClass = top.key,
                decisionScore = top.value,
                calibratedConfidence = null,
                marginToRunnerUp = margin,
                allScores = scores,
                strategy = SvmMulticlassStrategy.OVR,
                isLowMarginUncertain = margin < ModelConstants.LOW_MARGIN_WARNING_THRESHOLD,
                qualityReport = qualityReport,
                explainability = explainability,
                metrics = InferenceMetrics(
                    qualityCheckMs = qualityMs,
                    preprocessingMs = preMs,
                    featureExtractionMs = 0L,
                    svmClassificationMs = scoreMs,
                    explainabilityMs = explainability.generationTimeMs,
                    totalInferenceMs = totalMs,
                    coldInitTimeMs = null
                ),
                modelVersion = "DEV-MOCK (No Trained Weights Loaded)",
                isMockResult = true
            )
        )
    }

    override fun close() {
        synchronized(loadLock) {
            featureExtractor?.close()
            featureExtractor = null
            standardScaler = null
            svmClassifier = null
        }
    }
}
