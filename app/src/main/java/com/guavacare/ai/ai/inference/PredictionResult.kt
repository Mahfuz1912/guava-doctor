package com.guavacare.ai.ai.inference

import android.graphics.Bitmap
import com.guavacare.ai.ai.classifier.SvmMulticlassStrategy
import com.guavacare.ai.ai.explainability.ExplainabilityResult
import com.guavacare.ai.ai.preprocessing.ImageQualityReport
import com.guavacare.ai.domain.model.GuavaLeafClass

data class InferenceMetrics(
    val qualityCheckMs: Long,
    val preprocessingMs: Long,
    val featureExtractionMs: Long,
    val svmClassificationMs: Long,
    val explainabilityMs: Long,
    val totalInferenceMs: Long,
    val coldInitTimeMs: Long?
)

data class PredictionResult(
    val imagePath: String,
    val predictedClass: GuavaLeafClass,
    val decisionScore: Float,
    val calibratedConfidence: Float?,
    val marginToRunnerUp: Float,
    val allScores: Map<GuavaLeafClass, Float>,
    val strategy: SvmMulticlassStrategy,
    val isLowMarginUncertain: Boolean,
    val qualityReport: ImageQualityReport,
    val explainability: ExplainabilityResult,
    val metrics: InferenceMetrics,
    val modelVersion: String,
    val isMockResult: Boolean
)

sealed interface PredictionOutcome {
    data class Success(val result: PredictionResult) : PredictionOutcome
    data class QualityRejected(
        val imagePath: String,
        val report: ImageQualityReport
    ) : PredictionOutcome
    data class ModelAssetsMissing(
        val imagePath: String,
        val healthStatus: ModelHealthStatus
    ) : PredictionOutcome
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : PredictionOutcome
}
