package com.guavacare.ai.ai.explainability

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.set
import com.guavacare.ai.ai.classifier.LinearSVMClassifier
import com.guavacare.ai.ai.classifier.StandardScaler
import com.guavacare.ai.ai.featureextractor.TFLiteFeatureExtractor
import com.guavacare.ai.ai.preprocessing.ImagePreprocessor
import com.guavacare.ai.core.constants.ModelConstants
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ExplanationMethodType(
    val titleEn: String,
    val titleBn: String,
    val descriptionEn: String,
    val descriptionBn: String
) {
    SVM_WEIGHTED_FEATURE_MAP_CAM(
        titleEn = "SVM-Weighted MobileNetV2 Feature-Map CAM",
        titleBn = "SVM-ওয়েটেড MobileNetV2 ফিচার-ম্যাপ CAM",
        descriptionEn = "Mathematically exact projection of the scaled Linear SVM hyperplane weights (w_c / σ) onto MobileNetV2's final convolutional spatial feature map prior to global average pooling.",
        descriptionBn = "গ্লোবাল অ্যাভারেজ পুলিংয়ের আগে MobileNetV2-এর চূড়ান্ত কনভলিউশনাল ফিচার ম্যাপে স্কেলকৃত Linear SVM ওয়েটের (w_c / σ) গাণিতিক প্রজেকশন।"
    ),
    HYBRID_OCCLUSION_SENSITIVITY(
        titleEn = "Linear SVM Occlusion Sensitivity Attribution",
        titleBn = "Linear SVM অক্লুশন সংবেদনশীলতা মানচিত্র",
        descriptionEn = "Measured directly through the full MobileNetV2 + StandardScaler + Linear SVM pipeline by masking structured spatial patches and recording drops in the predicted class decision score.",
        descriptionBn = "সম্পূর্ণ MobileNetV2 + StandardScaler + Linear SVM মডেলের ভেতর দিয়ে ছবির বিভিন্ন অংশ ঢেকে ডিসিশন স্কোরের পরিবর্তন পরিমাপ করে তৈরি।"
    ),
    DEV_MOCK_VISUAL_CONTRAST(
        titleEn = "DEV MOCK — Leaf Lesion Contrast Map",
        titleBn = "ডেভ মক — পাতার রঙের বৈসাদৃশ্য মানচিত্র",
        descriptionEn = "Development mock preview highlighting local color and texture anomalies on the leaf image. Replace with real trained model assets for genuine SVM attribution.",
        descriptionBn = "এটি কেবল ডেভেলপমেন্ট মক প্রিভিউ যা পাতার রঙের অস্বাভাবিকতা দেখায়। প্রকৃত এআই ব্যাখ্যার জন্য প্রশিক্ষিত মডেল যুক্ত করুন।"
    )
}

data class ExplainabilityResult(
    val method: ExplanationMethodType,
    val heatmapBitmap: Bitmap,
    val gridWidth: Int,
    val gridHeight: Int,
    val generationTimeMs: Long
)

/**
 * Scientifically honest explainability engine tailored specifically for a
 * MobileNetV2 + StandardScaler + Linear SVM hybrid classifier.
 */
class HybridExplainabilityEngine(
    private val preprocessor: ImagePreprocessor = ImagePreprocessor()
) {

    /**
     * Path A: Exact linear CAM for GAP + StandardScaler + Linear SVM:
     *   M[i, j] = ReLU( sum_k ( (w_{c,k} / scale_k) * A[i, j, k] ) )
     */
    fun computeSvmWeightedFeatureMapCam(
        spatialMap: Array<Array<FloatArray>>,
        predictedClassIndex: Int,
        scaler: StandardScaler,
        svm: LinearSVMClassifier
    ): ExplainabilityResult {
        val startMs = System.currentTimeMillis()
        val height = spatialMap.size
        val width = spatialMap.first().size
        val channels = spatialMap.first().first().size

        val classWeights = svm.effectiveWeightsForClass(predictedClassIndex)
        val effectiveWeights = FloatArray(channels) { k ->
            val s = scaler.scale[k]
            if (s == 0f) classWeights[k] else classWeights[k] / s
        }

        val grid = Array(height) { FloatArray(width) }
        var maxVal = 0f
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0.0
                val pixelChannels = spatialMap[y][x]
                for (k in 0 until channels) {
                    sum += effectiveWeights[k].toDouble() * pixelChannels[k].toDouble()
                }
                val activated = max(0f, sum.toFloat())
                grid[y][x] = activated
                if (activated > maxVal) maxVal = activated
            }
        }

        val normalized = normalizeGrid(grid, maxVal)
        val heatmap = renderHeatmapBitmap(normalized, ModelConstants.INPUT_IMAGE_SIZE)
        return ExplainabilityResult(
            method = ExplanationMethodType.SVM_WEIGHTED_FEATURE_MAP_CAM,
            heatmapBitmap = heatmap,
            gridWidth = width,
            gridHeight = height,
            generationTimeMs = System.currentTimeMillis() - startMs
        )
    }

    /**
     * Path B: Optimized Real Perturbation / Occlusion Sensitivity over the full single-output
     * TFLite + StandardScaler + Linear SVM pipeline for the exact predicted class.
     *
     * Uses a single pre-normalized float array and a reusable direct ByteBuffer, mutating and
     * restoring only the occluded patch pixels per grid cell to avoid per-patch Bitmap/Canvas/Buffer churn.
     */
    fun computeHybridOcclusionSensitivity(
        sourceBitmap: Bitmap,
        baselinePredictedClassIndex: Int,
        baselineDecisionScore: Float,
        extractor: TFLiteFeatureExtractor,
        scaler: StandardScaler,
        svm: LinearSVMClassifier,
        gridSize: Int = 5
    ): ExplainabilityResult {
        val startMs = System.currentTimeMillis()
        val resized = preprocessor.resizeForModel(sourceBitmap)
        val size = ModelConstants.INPUT_IMAGE_SIZE
        val channels = ModelConstants.INPUT_CHANNELS
        val pixelCount = size * size
        val pixels = IntArray(pixelCount)
        resized.getPixels(pixels, 0, size, 0, 0, size, size)
        if (resized != sourceBitmap) resized.recycle()

        // Pre-normalize all pixels once and compute normalized mean RGB simultaneously
        val baseNormalized = FloatArray(pixelCount * channels)
        var rSum = 0.0
        var gSum = 0.0
        var bSum = 0.0
        var outIdx = 0
        for (p in pixels) {
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            rSum += r
            gSum += g
            bSum += b
            baseNormalized[outIdx++] = preprocessor.normalizeChannel(r)
            baseNormalized[outIdx++] = preprocessor.normalizeChannel(g)
            baseNormalized[outIdx++] = preprocessor.normalizeChannel(b)
        }

        val meanNormR = preprocessor.normalizeChannel((rSum / max(1, pixelCount)).toInt())
        val meanNormG = preprocessor.normalizeChannel((gSum / max(1, pixelCount)).toInt())
        val meanNormB = preprocessor.normalizeChannel((bSum / max(1, pixelCount)).toInt())

        // Allocate one reusable direct ByteBuffer & backing FloatBuffer
        val byteBuffer = java.nio.ByteBuffer
            .allocateDirect(pixelCount * channels * 4)
            .order(java.nio.ByteOrder.nativeOrder())
        val floatBuffer = byteBuffer.asFloatBuffer()
        floatBuffer.put(baseNormalized)

        val patchStep = size / gridSize
        val grid = Array(gridSize) { FloatArray(gridSize) }
        var maxDrop = 0f
        val targetClass = svm.classes[baselinePredictedClassIndex]

        for (gy in 0 until gridSize) {
            val top = gy * patchStep
            val bottom = if (gy == gridSize - 1) size else (gy + 1) * patchStep
            for (gx in 0 until gridSize) {
                val left = gx * patchStep
                val right = if (gx == gridSize - 1) size else (gx + 1) * patchStep

                // Mask patch directly in the reusable direct FloatBuffer
                for (y in top until bottom) {
                    var idx = (y * size + left) * channels
                    for (x in left until right) {
                        floatBuffer.put(idx, meanNormR)
                        floatBuffer.put(idx + 1, meanNormG)
                        floatBuffer.put(idx + 2, meanNormB)
                        idx += channels
                    }
                }

                byteBuffer.rewind()
                val feats = extractor.extractFeatures(byteBuffer, includeSpatialMap = false).featureVector
                val scaled = scaler.transform(feats)
                val pred = svm.predict(scaled)
                val occludedScore = pred.allScores[targetClass] ?: baselineDecisionScore

                // Restore patch directly from pre-normalized base array
                for (y in top until bottom) {
                    val startFloatIdx = (y * size + left) * channels
                    val rowFloatCount = (right - left) * channels
                    floatBuffer.position(startFloatIdx)
                    floatBuffer.put(baseNormalized, startFloatIdx, rowFloatCount)
                }

                // Importance = drop in predicted class SVM score when region is occluded
                val drop = max(0f, baselineDecisionScore - occludedScore)
                grid[gy][gx] = drop
                if (drop > maxDrop) maxDrop = drop
            }
        }

        val normalized = normalizeGrid(grid, maxDrop)
        val heatmap = renderHeatmapBitmap(normalized, size)
        return ExplainabilityResult(
            method = ExplanationMethodType.HYBRID_OCCLUSION_SENSITIVITY,
            heatmapBitmap = heatmap,
            gridWidth = gridSize,
            gridHeight = gridSize,
            generationTimeMs = System.currentTimeMillis() - startMs
        )
    }

    /**
     * Path C: Isolated Development Mock contrast map — ONLY invoked when explicit Dev Mock Mode
     * is enabled by the user while trained model files are absent.
     */
    fun computeDevMockContrastMap(sourceBitmap: Bitmap, gridSize: Int = 14): ExplainabilityResult {
        val startMs = System.currentTimeMillis()
        val resized = preprocessor.resizeForModel(sourceBitmap)
        val size = ModelConstants.INPUT_IMAGE_SIZE
        val pixels = IntArray(size * size)
        resized.getPixels(pixels, 0, size, 0, 0, size, size)
        if (resized != sourceBitmap) resized.recycle()

        // Compute image median-like green reference inside central leaf area
        var avgR = 0.0
        var avgG = 0.0
        var avgB = 0.0
        var count = 0
        for (y in size / 6 until (size * 5) / 6) {
            for (x in size / 6 until (size * 5) / 6) {
                val c = pixels[y * size + x]
                avgR += Color.red(c)
                avgG += Color.green(c)
                avgB += Color.blue(c)
                count++
            }
        }
        val refR = (avgR / max(1, count)).toFloat()
        val refG = (avgG / max(1, count)).toFloat()
        val refB = (avgB / max(1, count)).toFloat()

        val cell = size / gridSize
        val grid = Array(gridSize) { FloatArray(gridSize) }
        var maxScore = 0f

        for (gy in 0 until gridSize) {
            for (gx in 0 until gridSize) {
                var accum = 0f
                var n = 0
                val yStart = gy * cell
                val xStart = gx * cell
                val yEnd = min(size, yStart + cell)
                val xEnd = min(size, xStart + cell)
                for (y in yStart until yEnd) {
                    for (x in xStart until xEnd) {
                        val c = pixels[y * size + x]
                        val dr = abs(Color.red(c) - refR)
                        val dg = abs(Color.green(c) - refG)
                        val db = abs(Color.blue(c) - refB)
                        accum += (dr * 0.45f + dg * 0.35f + db * 0.20f)
                        n++
                    }
                }
                // Slight center-leaf spatial weighting so background corners stay calm
                val cy = (gy + 0.5f) / gridSize - 0.5f
                val cx = (gx + 0.5f) / gridSize - 0.5f
                val radial = max(0.25f, 1.0f - (cx * cx + cy * cy) * 1.8f)
                val score = (accum / max(1, n)) * radial
                grid[gy][gx] = score
                if (score > maxScore) maxScore = score
            }
        }

        val normalized = normalizeGrid(grid, maxScore)
        val heatmap = renderHeatmapBitmap(normalized, size)
        return ExplainabilityResult(
            method = ExplanationMethodType.DEV_MOCK_VISUAL_CONTRAST,
            heatmapBitmap = heatmap,
            gridWidth = gridSize,
            gridHeight = gridSize,
            generationTimeMs = System.currentTimeMillis() - startMs
        )
    }

    internal fun normalizeGrid(grid: Array<FloatArray>, maxVal: Float): Array<FloatArray> {
        val h = grid.size
        val w = grid.first().size
        return Array(h) { y ->
            FloatArray(w) { x ->
                if (maxVal <= 1e-7f) 0f else (grid[y][x] / maxVal).coerceIn(0f, 1f)
            }
        }
    }

    private fun renderHeatmapBitmap(normalizedGrid: Array<FloatArray>, outputSize: Int): Bitmap {
        val gh = normalizedGrid.size
        val gw = normalizedGrid.first().size
        val small = createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
        for (y in 0 until gh) {
            for (x in 0 until gw) {
                small[x, y] = mapIntensityToColor(normalizedGrid[y][x])
            }
        }
        val upscaled = small.scale(outputSize, outputSize)
        if (upscaled != small) small.recycle()
        return upscaled
    }

    /**
     * Smooth diagnostic color ramp (deep indigo -> teal -> emerald -> warm amber -> crimson)
     * with low-activation transparency so healthy background leaf veins remain readable.
     */
    internal fun mapIntensityToColor(value: Float): Int {
        val v = value.coerceIn(0f, 1f)
        val alpha = (35 + (v * 215)).toInt().coerceIn(0, 255)
        val r: Int
        val g: Int
        val b: Int
        when {
            v < 0.25f -> {
                val t = v / 0.25f
                r = lerp(24, 18, t)
                g = lerp(48, 128, t)
                b = lerp(120, 165, t)
            }
            v < 0.50f -> {
                val t = (v - 0.25f) / 0.25f
                r = lerp(18, 92, t)
                g = lerp(128, 198, t)
                b = lerp(165, 96, t)
            }
            v < 0.75f -> {
                val t = (v - 0.50f) / 0.25f
                r = lerp(92, 245, t)
                g = lerp(198, 174, t)
                b = lerp(96, 32, t)
            }
            else -> {
                val t = (v - 0.75f) / 0.25f
                r = lerp(245, 214, t)
                g = lerp(174, 34, t)
                b = lerp(32, 34, t)
            }
        }
        return Color.argb(alpha, r, g, b)
    }

    private fun lerp(a: Int, b: Int, t: Float): Int = (a + (b - a) * t).toInt().coerceIn(0, 255)
}
