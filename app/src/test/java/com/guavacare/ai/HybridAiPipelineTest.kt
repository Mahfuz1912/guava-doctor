package com.guavacare.ai

import com.guavacare.ai.ai.classifier.LinearSVMClassifier
import com.guavacare.ai.ai.classifier.StandardScaler
import com.guavacare.ai.ai.classifier.SvmMulticlassStrategy
import com.guavacare.ai.ai.preprocessing.ImagePreprocessor
import com.guavacare.ai.ai.preprocessing.ImageQualityValidator
import com.guavacare.ai.domain.model.GuavaLeafClass
import com.guavacare.ai.domain.model.ScanRecord
import com.guavacare.ai.domain.usecase.OrchardInsightsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridAiPipelineTest {

    @Test
    fun mobilenetV2PreprocessingNormalizesToMinusOneToOne() {
        val preprocessor = ImagePreprocessor()
        assertEquals(-1.0f, preprocessor.normalizeChannel(0), 1e-5f)
        assertEquals(0.0f, preprocessor.normalizeChannel(127.5f.toInt()), 0.01f)
        assertEquals(1.0f, preprocessor.normalizeChannel(255), 1e-5f)

        val rgbPixels = intArrayOf(0xFF007FFF.toInt()) // R=0, G=127, B=255
        val normalized = preprocessor.preprocessPixelsToFloatArray(rgbPixels)
        assertEquals(3, normalized.size)
        assertEquals(-1.0f, normalized[0], 1e-5f)
        assertEquals((127f / 127.5f) - 1f, normalized[1], 1e-5f)
        assertEquals(1.0f, normalized[2], 1e-5f)
    }

    @Test
    fun standardScalerMatchesSklearnAndHandlesZeroScale() {
        val scaler = StandardScaler(
            mean = floatArrayOf(2.0f, 5.0f, 10.0f),
            scale = floatArrayOf(2.0f, 0.0f, 5.0f)
        )
        val result = scaler.transform(floatArrayOf(6.0f, 8.0f, 0.0f))
        assertEquals(2.0f, result[0], 1e-5f)
        assertEquals(3.0f, result[1], 1e-5f)
        assertEquals(-2.0f, result[2], 1e-5f)
    }

    @Test
    fun linearSvmOvrComputesExactDecisionScoresAndMargin() {
        val classes = GuavaLeafClass.entries
        val coef = Array(5) { row ->
            FloatArray(3) { col -> if (row == 0 && col == 0) 2.0f else if (row == 1 && col == 1) 1.5f else 0.1f }
        }
        val intercept = floatArrayOf(0.5f, -0.2f, -1.0f, -1.0f, -1.0f)
        val svm = LinearSVMClassifier(classes, coef, intercept, SvmMulticlassStrategy.OVR)

        val pred = svm.predict(floatArrayOf(1.0f, 1.0f, 0.0f))
        assertEquals(GuavaLeafClass.ANTHRACNOSE, pred.predictedClass)
        assertEquals(2.6f, pred.decisionScore, 1e-4f)
        assertEquals(1.4f, pred.allScores[GuavaLeafClass.HEALTHY]!!, 1e-4f)
        assertEquals(1.2f, pred.marginToRunnerUp, 1e-4f)
        assertNull(pred.calibratedConfidence)
        assertFalse(pred.isLowMarginUncertain)
    }

    @Test
    fun linearSvmOvoVotesAndTieBreakingSelectsWinningClass() {
        val classes = listOf(
            GuavaLeafClass.ANTHRACNOSE,
            GuavaLeafClass.HEALTHY,
            GuavaLeafClass.NUTRIENT_STRESS
        )
        // 3 pairs: (0,1), (0,2), (1,2)
        // Make class 1 (Healthy) beat class 0 (negative score on pair 0) and beat class 2 (positive score on pair 2)
        val coef = arrayOf(
            floatArrayOf(-1.0f, 0.0f),
            floatArrayOf(0.5f, 0.0f),
            floatArrayOf(1.0f, 0.0f)
        )
        val intercept = floatArrayOf(-1.0f, 0.0f, 1.0f)
        val svm = LinearSVMClassifier(classes, coef, intercept, SvmMulticlassStrategy.OVO)

        val pred = svm.predict(floatArrayOf(1.0f, 0.0f))
        assertEquals(GuavaLeafClass.HEALTHY, pred.predictedClass)
        assertEquals(SvmMulticlassStrategy.OVO, pred.strategy)
        assertTrue(pred.decisionScore > 2.0f)
    }

    @Test
    fun foliagePixelHeuristicAcceptsGreenAndYellowLeafColorsAndRejectsBlueSky() {
        val validator = ImageQualityValidator()
        assertTrue(validator.isPotentialLeafPixel(55, 138, 48))    // Healthy guava green
        assertTrue(validator.isPotentialLeafPixel(175, 170, 52))   // Nutrient-stressed chlorotic yellow-green
        assertTrue(validator.isPotentialLeafPixel(118, 112, 85))   // Sooty-mold olive-brown leaf
        assertFalse(validator.isPotentialLeafPixel(40, 120, 225))  // Blue sky
        assertFalse(validator.isPotentialLeafPixel(240, 240, 240)) // White blank wall
        assertFalse(validator.isPotentialLeafPixel(210, 155, 120)) // Human skin tone
        assertFalse(validator.isPotentialLeafPixel(210, 45, 50))   // Red artificial object
        assertTrue(validator.isLikelySkinTonePixel(210, 155, 120))
        assertFalse(validator.isLikelySkinTonePixel(55, 138, 48))
    }

    @Test
    fun orchardInsightsCalculatorSummarizesRecordsAccurately() {
        val now = 1_700_000_000_000L
        val records = listOf(
            ScanRecord(id = 1, imagePath = "/a.jpg", predictedClass = GuavaLeafClass.HEALTHY, decisionScore = 1.2f, inferenceTimeMs = 18, timestamp = now),
            ScanRecord(id = 2, imagePath = "/b.jpg", predictedClass = GuavaLeafClass.ANTHRACNOSE, decisionScore = 1.5f, inferenceTimeMs = 20, timestamp = now),
            ScanRecord(id = 3, imagePath = "/c.jpg", predictedClass = GuavaLeafClass.ANTHRACNOSE, decisionScore = 0.9f, inferenceTimeMs = 19, timestamp = now - 86_400_000L)
        )
        val summary = OrchardInsightsCalculator().calculate(records, nowMillis = now)
        assertEquals(3, summary.totalScans)
        assertEquals(1, summary.healthyCount)
        assertEquals(2, summary.affectedCount)
        assertEquals(GuavaLeafClass.ANTHRACNOSE, summary.mostFrequentCondition)
        assertEquals(7, summary.weeklyActivity.size)
        assertEquals(2, summary.weeklyActivity.last().count)
    }
}
