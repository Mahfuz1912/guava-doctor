package com.guavacare.ai.ai.classifier

import com.guavacare.ai.core.constants.ModelConstants
import com.guavacare.ai.domain.model.GuavaLeafClass
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

enum class SvmMulticlassStrategy {
    OVR,
    OVO;

    companion object {
        fun fromKey(key: String?): SvmMulticlassStrategy =
            if (key.equals("ovo", ignoreCase = true)) OVO else OVR
    }
}

data class SvmPrediction(
    val predictedClass: GuavaLeafClass,
    val predictedIndex: Int,
    val decisionScore: Float,
    val marginToRunnerUp: Float,
    val allScores: Map<GuavaLeafClass, Float>,
    val calibratedConfidence: Float?,
    val strategy: SvmMulticlassStrategy,
    val isLowMarginUncertain: Boolean
)

/**
 * Exact Kotlin Linear SVM classifier supporting both scikit-learn:
 *  - One-vs-Rest (`LinearSVC`, K weight rows)
 *  - One-vs-One (`SVC(kernel='linear')`, K*(K-1)/2 pairwise weight rows with sklearn tie-breaking)
 *
 * Strictly preserves decision scores as signed SVM hyperplane scores—never fabricating probabilities.
 */
class LinearSVMClassifier(
    val classes: List<GuavaLeafClass>,
    val coef: Array<FloatArray>,
    val intercept: FloatArray,
    val strategy: SvmMulticlassStrategy = SvmMulticlassStrategy.OVR
) {
    init {
        require(classes.size >= 2) { "LinearSVMClassifier requires at least 2 classes." }
        val expectedRows = when (strategy) {
            SvmMulticlassStrategy.OVR -> classes.size
            SvmMulticlassStrategy.OVO -> classes.size * (classes.size - 1) / 2
        }
        require(coef.size == expectedRows) {
            "Expected $expectedRows weight rows for $strategy with ${classes.size} classes, got ${coef.size}."
        }
        require(intercept.size == expectedRows) {
            "Expected $expectedRows intercepts, got ${intercept.size}."
        }
        val featureDim = coef.first().size
        require(coef.all { it.size == featureDim }) {
            "Inconsistent weight row dimensions in svm_coef."
        }
    }

    val featureDimension: Int get() = coef.first().size

    fun predict(scaledFeatures: FloatArray): SvmPrediction {
        require(scaledFeatures.size == featureDimension) {
            "Expected $featureDimension-D scaled feature vector, received ${scaledFeatures.size}-D."
        }

        val rawHyperplaneScores = FloatArray(coef.size) { row ->
            dotProduct(coef[row], scaledFeatures) + intercept[row]
        }

        val classScores: FloatArray = when (strategy) {
            SvmMulticlassStrategy.OVR -> rawHyperplaneScores
            SvmMulticlassStrategy.OVO -> aggregateOvoToOvr(rawHyperplaneScores, classes.size)
        }

        var bestIdx = 0
        var secondBestScore = Float.NEGATIVE_INFINITY
        for (i in classScores.indices) {
            if (classScores[i] > classScores[bestIdx]) {
                secondBestScore = classScores[bestIdx]
                bestIdx = i
            } else if (i != bestIdx && classScores[i] > secondBestScore) {
                secondBestScore = classScores[i]
            }
        }
        if (bestIdx == 0 && classScores.size > 1) {
            secondBestScore = (1 until classScores.size).maxOf { classScores[it] }
        }

        val bestScore = classScores[bestIdx]
        val margin = bestScore - secondBestScore
        val scoresMap = linkedMapOf<GuavaLeafClass, Float>()
        classes.forEachIndexed { index, leafClass ->
            scoresMap[leafClass] = classScores[index]
        }

        return SvmPrediction(
            predictedClass = classes[bestIdx],
            predictedIndex = bestIdx,
            decisionScore = bestScore,
            marginToRunnerUp = margin,
            allScores = scoresMap,
            calibratedConfidence = null,
            strategy = strategy,
            isLowMarginUncertain = margin < ModelConstants.LOW_MARGIN_WARNING_THRESHOLD
        )
    }

    /**
     * Returns the effective linear weight vector for class `classIndex` in scaled feature space.
     * - For OvR: exact hyperplane weights `coef[classIndex]`.
     * - For OvO: signed sum of all pairwise hyperplanes involving `classIndex` (+w for (c, j), -w for (i, c)).
     */
    fun effectiveWeightsForClass(classIndex: Int): FloatArray {
        if (strategy == SvmMulticlassStrategy.OVR) {
            return coef[classIndex].copyOf()
        }
        val combined = FloatArray(featureDimension)
        var pairIdx = 0
        val k = classes.size
        for (i in 0 until k) {
            for (j in i + 1 until k) {
                if (i == classIndex) {
                    val w = coef[pairIdx]
                    for (d in 0 until featureDimension) combined[d] += w[d]
                } else if (j == classIndex) {
                    val w = coef[pairIdx]
                    for (d in 0 until featureDimension) combined[d] -= w[d]
                }
                pairIdx++
            }
        }
        return combined
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var acc = 0.0
        for (i in a.indices) {
            acc += a[i].toDouble() * b[i].toDouble()
        }
        return acc.toFloat()
    }

    /**
     * Mirrors scikit-learn's `sklearn.utils.multiclass._ovr_decision_function`:
     *   votes + sum_conf / (3 * (|sum_conf| + 1))
     */
    private fun aggregateOvoToOvr(pairwiseScores: FloatArray, numClasses: Int): FloatArray {
        val votes = DoubleArray(numClasses)
        val sumConf = DoubleArray(numClasses)
        var pairIdx = 0
        for (i in 0 until numClasses) {
            for (j in i + 1 until numClasses) {
                val score = pairwiseScores[pairIdx++].toDouble()
                if (score > 0.0) {
                    votes[i] += 1.0
                } else {
                    votes[j] += 1.0
                }
                sumConf[i] += score
                sumConf[j] -= score
            }
        }
        return FloatArray(numClasses) { c ->
            val transformed = sumConf[c] / (3.0 * (abs(sumConf[c]) + 1.0))
            (votes[c] + transformed).toFloat()
        }
    }

    companion object {
        fun fromJson(
            labelsJson: String,
            coefJson: String,
            interceptJson: String
        ): LinearSVMClassifier {
            val meta = JSONObject(labelsJson)
            val classArr = meta.getJSONArray("classes")
            val classes = (0 until classArr.length()).map {
                GuavaLeafClass.fromLabel(classArr.getString(it))
            }
            val strategy = SvmMulticlassStrategy.fromKey(meta.optString("multiclass_strategy", "ovr"))

            val coefTrimmed = coefJson.trim()
            val coefRowsJson = if (coefTrimmed.startsWith("[")) {
                JSONArray(coefTrimmed)
            } else {
                JSONObject(coefTrimmed).getJSONArray("values")
            }
            val coef = Array(coefRowsJson.length()) { r ->
                val row = coefRowsJson.getJSONArray(r)
                FloatArray(row.length()) { c ->
                    val v = row.getDouble(c).toFloat()
                    require(v.isFinite()) { "Non-finite SVM weight at [$r, $c]" }
                    v
                }
            }

            val intercept = StandardScaler.parseFloatVector(interceptJson)
            return LinearSVMClassifier(
                classes = classes,
                coef = coef,
                intercept = intercept,
                strategy = strategy
            )
        }
    }
}
