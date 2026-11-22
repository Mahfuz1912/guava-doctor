package com.guavacare.ai.ai.classifier

import org.json.JSONObject

/**
 * Exact Kotlin implementation of scikit-learn `StandardScaler.transform`:
 *   z[i] = (x[i] - mean[i]) / scale[i]  (with scale[i] == 0 guarded as 1.0, matching sklearn).
 */
class StandardScaler(
    val mean: FloatArray,
    val scale: FloatArray
) {
    init {
        require(mean.size == scale.size) {
            "StandardScaler dimension mismatch: mean=${mean.size}, scale=${scale.size}"
        }
        require(mean.isNotEmpty()) {
            "StandardScaler parameters cannot be empty."
        }
    }

    val featureDimension: Int get() = mean.size

    fun transform(features: FloatArray): FloatArray {
        require(features.size == mean.size) {
            "Expected ${mean.size}-D feature vector, received ${features.size}-D."
        }
        val output = FloatArray(features.size)
        for (i in features.indices) {
            val s = scale[i]
            output[i] = if (s == 0f) {
                features[i] - mean[i]
            } else {
                (features[i] - mean[i]) / s
            }
        }
        return output
    }

    companion object {
        fun fromJson(meanJson: String, scaleJson: String): StandardScaler {
            val meanArr = parseFloatVector(meanJson)
            val scaleArr = parseFloatVector(scaleJson)
            return StandardScaler(mean = meanArr, scale = scaleArr)
        }

        internal fun parseFloatVector(jsonText: String): FloatArray {
            val trimmed = jsonText.trim()
            val array = if (trimmed.startsWith("[")) {
                org.json.JSONArray(trimmed)
            } else {
                JSONObject(trimmed).getJSONArray("values")
            }
            return FloatArray(array.length()) { idx ->
                val v = array.getDouble(idx).toFloat()
                require(v.isFinite()) { "Non-finite scaler parameter at index $idx" }
                v
            }
        }
    }
}
