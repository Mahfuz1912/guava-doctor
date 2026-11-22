package com.guavacare.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.guavacare.ai.ai.inference.HybridInferenceEngine
import com.guavacare.ai.ai.inference.ModelValidator
import com.guavacare.ai.ai.inference.PredictionOutcome
import com.guavacare.ai.core.utils.LeafImageStorage
import com.guavacare.ai.data.local.database.ScanDatabase
import com.guavacare.ai.data.repository.AdvisoryRepository
import com.guavacare.ai.data.repository.ScanRepository
import com.guavacare.ai.domain.model.GuavaLeafClass
import com.guavacare.ai.domain.model.ScanRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GuavaCareRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: ScanDatabase
    private lateinit var imageStorage: LeafImageStorage
    private lateinit var scanRepository: ScanRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ScanDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        imageStorage = LeafImageStorage(context)
        scanRepository = ScanRepository(database.scanDao(), imageStorage)
    }

    @After
    fun tearDown() {
        database.close()
        imageStorage.deleteAllScanImages()
    }

    @Test
    fun advisoryAssetLoadsAllFiveClassesInEnglishAndBangla() {
        val bundle = AdvisoryRepository(context).loadBundle()
        assertEquals(5, bundle.advisories.size)
        assertTrue(bundle.tips.isNotEmpty())
        GuavaLeafClass.entries.forEach { leafClass ->
            val article = bundle.advisories.firstOrNull { it.leafClass == leafClass }
            assertNotNull("Missing advisory for $leafClass", article)
            assertTrue(article!!.overviewEn.isNotBlank())
            assertTrue(article.overviewBn.isNotBlank())
            assertTrue(article.preventionBn.isNotEmpty())
        }
    }

    @Test
    fun realTrainedClassifierAssetsLoadAndComputeFiniteOvoScores() {
        val validator = ModelValidator(context)
        val health = validator.inspect()
        assertTrue("Expected real bundled classifier weights to be READY", health.classifierWeightsReady)
        assertTrue("Expected bundled feature_extractor_fp16.tflite to be detected", health.tflitePresent)
        assertTrue("Expected full Production Mode readiness with bundled .tflite + SVM weights", health.isProductionReady)
        assertEquals("OVO", health.multiclassStrategy)

        val scaler = com.guavacare.ai.ai.classifier.StandardScaler.fromJson(
            meanJson = validator.readAssetText(com.guavacare.ai.core.constants.ModelConstants.SCALER_MEAN_ASSET_PATH),
            scaleJson = validator.readAssetText(com.guavacare.ai.core.constants.ModelConstants.SCALER_SCALE_ASSET_PATH)
        )
        val svm = com.guavacare.ai.ai.classifier.LinearSVMClassifier.fromJson(
            labelsJson = validator.readAssetText(com.guavacare.ai.core.constants.ModelConstants.CLASS_LABELS_ASSET_PATH),
            coefJson = validator.readAssetText(com.guavacare.ai.core.constants.ModelConstants.SVM_COEF_ASSET_PATH),
            interceptJson = validator.readAssetText(com.guavacare.ai.core.constants.ModelConstants.SVM_INTERCEPT_ASSET_PATH)
        )

        assertEquals(1280, scaler.featureDimension)
        assertEquals(1280, svm.featureDimension)
        assertEquals(com.guavacare.ai.ai.classifier.SvmMulticlassStrategy.OVO, svm.strategy)

        val sampleFeatures = FloatArray(1280) { idx -> (idx % 17) * 0.05f }
        val scaled = scaler.transform(sampleFeatures)
        val prediction = svm.predict(scaled)
        assertEquals(5, prediction.allScores.size)
        prediction.allScores.values.forEach { score ->
            assertTrue("Expected finite SVM score, got $score", score.isFinite())
        }
        assertTrue("Expected valid predicted class", prediction.predictedClass in GuavaLeafClass.entries)
    }

    @Test
    fun modelFileStorageRejectsNonTfliteBinaryFiles() {
        val storage = com.guavacare.ai.ai.featureextractor.ModelFileStorage(context)
        storage.deleteImportedModel()
        assertFalse(storage.hasImportedModel())

        val fakeFile = File(context.cacheDir, "not_a_tflite.bin")
        fakeFile.writeText("INVALID_HEADER_DATA_NOT_A_TFLITE_MODEL".repeat(64))
        assertFalse(com.guavacare.ai.ai.featureextractor.ModelFileStorage.hasValidTfliteMagic(fakeFile))

        val result = storage.importTfliteFromUri(android.net.Uri.fromFile(fakeFile))
        assertTrue("Expected invalid file to fail import validation", result.isFailure)
        assertFalse(storage.hasImportedModel())
        fakeFile.delete()
    }

    @Test
    fun productionModeNeverSilentlyReturnsMockPredictionsWhenModelIsPresent() = runTest {
        val validator = ModelValidator(context)
        val health = validator.inspect()

        val engine = HybridInferenceEngine(context)
        val leafBitmap = createTexturedGreenLeafBitmap(256, 256)
        val productionOutcome = engine.analyzeLeaf(
            bitmap = leafBitmap,
            imagePath = "/tmp/test_leaf.jpg",
            enforceQualityGate = true,
            allowDevMockMode = false
        )
        if (health.isProductionReady) {
            if (productionOutcome is PredictionOutcome.Success) {
                assertFalse("Must never return mock prediction when real model assets are present", productionOutcome.result.isMockResult)
            } else {
                assertTrue("Expected native-unavailable Error on JVM Robolectric, got $productionOutcome", productionOutcome is PredictionOutcome.Error)
            }
        } else {
            assertTrue(
                "Production Mode must return ModelAssetsMissing rather than mock when .tflite is absent, got $productionOutcome",
                productionOutcome is PredictionOutcome.ModelAssetsMissing
            )
        }
        engine.close()
    }

    @Test
    fun roomPersistenceSavesRetrievesAndSafelyDeletesAssociatedImageFile() = runTest {
        val file = imageStorage.createTempCaptureFile()
        FileOutputStream(file).use { it.write(byteArrayOf(1, 2, 3, 4)) }
        assertTrue(file.exists())

        val id = scanRepository.saveScan(
            ScanRecord(
                imagePath = file.absolutePath,
                predictedClass = GuavaLeafClass.SOOTY_MOLD,
                decisionScore = 1.15f,
                marginToRunnerUp = 0.62f,
                inferenceTimeMs = 24L,
                orchardName = "Swarupkathi Block A",
                plotId = "P-04",
                notes = "Lower shaded canopy"
            )
        )

        val savedList = scanRepository.allScans.first()
        assertEquals(1, savedList.size)
        assertEquals("Swarupkathi Block A", savedList.first().orchardName)

        scanRepository.deleteScan(id)
        val afterDelete = scanRepository.allScans.first()
        assertTrue(afterDelete.isEmpty())
        assertFalse(File(file.absolutePath).exists())
    }

    @Test
    fun allFiveRealGuavaLeafParityImagesPassLeafValidator() {
        val validator = com.guavacare.ai.ai.preprocessing.ImageQualityValidator()
        val sampleNames = listOf(
            "anthracnose_anthracnose_0003.jpg",
            "healthy_healthy_0003.jpg",
            "nutrient_stress_nutrient_stress_0003.jpg",
            "pest_damage_pest_damage_0003.jpg",
            "sooty_mold_sooty_mold_0003.jpg"
        )
        val baseDir = listOf(
            File("src/androidTest/assets/parity/images"),
            File("app/src/androidTest/assets/parity/images")
        ).firstOrNull { it.exists() }
        assertNotNull("Expected parity sample images directory to exist", baseDir)

        sampleNames.forEach { fileName ->
            val file = File(baseDir!!, fileName)
            assertTrue("Missing parity sample: ${file.absolutePath}", file.exists())
            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            assertNotNull("Failed to decode $fileName", bitmap)
            val report = validator.validate(bitmap!!)
            assertFalse(
                "Real guava leaf '$fileName' was falsely flagged as non-leaf " +
                    "(full=${report.foliageRatio}, center=${report.centerFoliageRatio}, issues=${report.issues})",
                report.isNonLeaf
            )
            assertTrue(
                "Real guava leaf '$fileName' should pass full quality validation (issues=${report.issues})",
                report.isValid
            )
            bitmap.recycle()
        }
    }

    @Test
    fun goldenOliveAndDarkSootyGuavaLeavesPassLeafValidator() {
        val validator = com.guavacare.ai.ai.preprocessing.ImageQualityValidator()

        // Golden-olive / amber chlorotic leaf on dark neutral studio background
        val goldenOliveLeaf = createPatternBitmap(256, 256) { x, y ->
            val dx = (x - 128) / 86.0
            val dy = (y - 128) / 96.0
            if (dx * dx + dy * dy <= 1.0) {
                val vein = if ((x + y) % 10 == 0 || kotlin.math.abs(x - 128) < 2) 26 else 0
                Color.rgb(
                    (139 + vein / 2).coerceAtMost(240),
                    (111 + vein).coerceAtMost(230),
                    21 + vein / 3
                )
            } else {
                Color.rgb(46, 48, 46)
            }
        }
        val goldenReport = validator.validate(goldenOliveLeaf)
        assertFalse(
            "Golden-olive diseased guava leaf must NOT trigger isNonLeaf (issues=${goldenReport.issues})",
            goldenReport.isNonLeaf
        )
        assertTrue(
            "Golden-olive diseased guava leaf should pass quality validation (issues=${goldenReport.issues})",
            goldenReport.isValid
        )
        goldenOliveLeaf.recycle()

        // Dark sooty-mold leaf with yellow-amber margin
        val sootyLeaf = createPatternBitmap(256, 256) { x, y ->
            val dx = (x - 128) / 82.0
            val dy = (y - 128) / 92.0
            if (dx * dx + dy * dy <= 1.0) {
                val texture = if ((x * 3 + y * 5) % 9 == 0) 24 else 0
                Color.rgb(42 + texture, 48 + texture, 26 + texture / 2)
            } else {
                Color.rgb(218, 220, 218)
            }
        }
        val sootyReport = validator.validate(sootyLeaf)
        assertFalse(
            "Dark sooty-mold guava leaf must NOT trigger isNonLeaf (issues=${sootyReport.issues})",
            sootyReport.isNonLeaf
        )
        sootyLeaf.recycle()
    }

    @Test
    fun nonLeafImagesAreFlaggedAndRejectedEvenWhenStrictGateIsDisabled() = runTest {
        val validator = com.guavacare.ai.ai.preprocessing.ImageQualityValidator()
        val nonLeafCases = mapOf(
            "portrait_skin_tone" to createPatternBitmap(256, 256) { x, y ->
                val delta = if ((x + y) % 10 == 0) 20 else 0
                Color.rgb((210 - delta).coerceIn(0, 255), (155 - delta).coerceIn(0, 255), (120 - delta).coerceIn(0, 255))
            },
            "white_text_document" to createPatternBitmap(256, 256) { x, y ->
                val isTextLine = (y % 18 in 4..7) && (x in 24..232) && ((x / 6) % 2 == 0)
                if (isTextLine) Color.rgb(35, 35, 35) else Color.rgb(236, 236, 236)
            },
            "blue_sky_scene" to createPatternBitmap(256, 256) { x, y ->
                val cloud = if ((x / 20 + y / 20) % 2 == 0) 45 else 0
                Color.rgb((55 + cloud).coerceAtMost(255), (135 + cloud).coerceAtMost(255), (225 + cloud / 3).coerceAtMost(255))
            },
            "red_object" to createPatternBitmap(256, 256) { x, y ->
                val stripe = if (x % 14 < 7) 25 else 0
                Color.rgb((215 - stripe).coerceIn(0, 255), (48 + stripe).coerceIn(0, 255), (52 + stripe).coerceIn(0, 255))
            },
            "outdoor_non_leaf_center_with_green_border" to createPatternBitmap(256, 256) { x, y ->
                val inCenter = x in 42..214 && y in 42..214
                if (inCenter) {
                    val v = if ((x + y) % 10 == 0) 24 else 0
                    Color.rgb(190 - v, 140 - v, 112 - v) // Person / warm building in center
                } else {
                    val v = if ((x + y) % 8 == 0) 25 else 0
                    Color.rgb(48 + v, 138 + v, 44 + v) // Green bushes along edges only
                }
            },
            "wooden_table" to createPatternBitmap(256, 256) { x, y ->
                val grain = if (y % 9 == 0) 22 else 0
                Color.rgb(162 - grain, 108 - grain, 64 - grain)
            }
        )

        nonLeafCases.forEach { (name, bmp) ->
            val report = validator.validate(bmp)
            assertTrue(
                "Non-leaf image '$name' MUST trigger isNonLeaf warning " +
                    "(full=${report.foliageRatio}, center=${report.centerFoliageRatio})",
                report.isNonLeaf
            )
            assertFalse("Non-leaf image '$name' must not be valid", report.isValid)
        }

        // Verify HybridInferenceEngine always blocks non-leaf photos even if strictQualityGate is false
        val engine = HybridInferenceEngine(context)
        val portrait = nonLeafCases.getValue("portrait_skin_tone")
        val outcome = engine.analyzeLeaf(
            bitmap = portrait,
            imagePath = "/tmp/non_leaf_portrait.jpg",
            enforceQualityGate = false,
            allowDevMockMode = true
        )
        assertTrue(
            "Expected non-leaf photo to be rejected with QualityRejected even when strictQualityGate=false, got $outcome",
            outcome is PredictionOutcome.QualityRejected && outcome.report.isNonLeaf
        )
        engine.close()
        nonLeafCases.values.forEach { it.recycle() }
    }

    private fun createPatternBitmap(
        width: Int,
        height: Int,
        pixelAt: (Int, Int) -> Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bitmap.setPixel(x, y, pixelAt(x, y))
            }
        }
        return bitmap
    }

    private fun createTexturedGreenLeafBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                // High-contrast organic vein pattern so Laplacian blur check passes cleanly
                val vein = if ((x + y) % 12 == 0 || (x - y + width) % 16 == 0) 45 else 0
                val g = (128 + vein).coerceAtMost(230)
                val r = (52 + vein / 3).coerceAtMost(200)
                val b = (42 + vein / 4).coerceAtMost(180)
                bitmap.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        return bitmap
    }
}
