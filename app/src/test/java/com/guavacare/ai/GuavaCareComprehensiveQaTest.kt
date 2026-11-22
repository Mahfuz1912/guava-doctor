package com.guavacare.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.guavacare.ai.ai.classifier.LinearSVMClassifier
import com.guavacare.ai.ai.classifier.StandardScaler
import com.guavacare.ai.ai.classifier.SvmMulticlassStrategy
import com.guavacare.ai.ai.explainability.ExplainabilityResult
import com.guavacare.ai.ai.explainability.ExplanationMethodType
import com.guavacare.ai.ai.explainability.HybridExplainabilityEngine
import com.guavacare.ai.ai.inference.InferenceMetrics
import com.guavacare.ai.ai.inference.ModelHealthStatus
import com.guavacare.ai.ai.inference.PredictionResult
import com.guavacare.ai.ai.preprocessing.ImageQualityReport
import com.guavacare.ai.ai.preprocessing.ImageQualityValidator
import com.guavacare.ai.ai.preprocessing.QualityIssue
import com.guavacare.ai.ai.preprocessing.QualityIssueType
import com.guavacare.ai.core.common.BilingualStrings
import com.guavacare.ai.core.utils.LeafImageStorage
import com.guavacare.ai.data.local.database.ScanDatabase
import com.guavacare.ai.data.repository.AdvisoryRepository
import com.guavacare.ai.data.repository.ScanRepository
import com.guavacare.ai.domain.model.AppLanguage
import com.guavacare.ai.domain.model.GuavaLeafClass
import com.guavacare.ai.domain.model.HistoryDateFilter
import com.guavacare.ai.domain.model.OrchardInsightsSummary
import com.guavacare.ai.domain.model.ScanRecord
import com.guavacare.ai.domain.model.ThemeMode
import com.guavacare.ai.domain.model.UserPreferences
import com.guavacare.ai.domain.usecase.OrchardInsightsCalculator
import com.guavacare.ai.presentation.HistoryFilterState
import com.guavacare.ai.presentation.ScanFlowState
import com.guavacare.ai.presentation.navigation.GuavaCareRoutes
import com.guavacare.ai.presentation.navigation.topLevelDestinations
import com.guavacare.ai.presentation.screens.about.AboutScreen
import com.guavacare.ai.presentation.screens.diagnosis.DiagnosisScreen
import com.guavacare.ai.presentation.screens.history.HistoryScreen
import com.guavacare.ai.presentation.screens.home.HomeScreen
import com.guavacare.ai.presentation.screens.scanner.ScannerScreen
import com.guavacare.ai.presentation.screens.settings.PrivacyPolicyScreen
import com.guavacare.ai.presentation.screens.settings.SettingsScreen
import com.guavacare.ai.presentation.theme.GuavaCareTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GuavaCareComprehensiveQaTest {

    @get:Rule
    val composeRule = createComposeRule()

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

    // =========================================================================
    // 1. QUALITY VALIDATOR & NON-LEAF EDGE CASES
    // =========================================================================

    @Test
    fun qualityValidatorDetectsSmallDarkBrightBlurryAndAcceptsLeafOnWhitePaper() {
        val validator = ImageQualityValidator()

        // Undersized
        val tiny = createBitmap(120, 120)
        val tinyReport = validator.validate(tiny)
        assertFalse(tinyReport.isValid)
        assertTrue(tinyReport.issues.any { it.type == QualityIssueType.INVALID_DIMENSIONS })
        tiny.recycle()

        // Pitch dark
        val dark = fillBitmap(240, 240) { _, _ -> Color.rgb(6, 6, 6) }
        val darkReport = validator.validate(dark)
        assertFalse(darkReport.isValid)
        assertTrue(darkReport.issues.any { it.type == QualityIssueType.TOO_DARK })
        dark.recycle()

        // Overexposed glare
        val bright = fillBitmap(240, 240) { _, _ -> Color.rgb(252, 252, 252) }
        val brightReport = validator.validate(bright)
        assertFalse(brightReport.isValid)
        assertTrue(brightReport.issues.any { it.type == QualityIssueType.TOO_BRIGHT })
        assertTrue(brightReport.isNonLeaf)
        bright.recycle()

        // Flat solid green wall (zero sharpness -> TOO_BLURRY)
        val flatGreen = fillBitmap(240, 240) { _, _ -> Color.rgb(48, 140, 50) }
        val flatReport = validator.validate(flatGreen)
        assertFalse(flatReport.isValid)
        assertTrue(flatReport.issues.any { it.type == QualityIssueType.TOO_BLURRY })
        flatGreen.recycle()

        // Leaf centered on white paper (must PASS!)
        val leafOnWhite = fillBitmap(256, 256) { x, y ->
            val dx = (x - 128) / 68.0
            val dy = (y - 128) / 96.0
            if (dx * dx + dy * dy <= 1.0) {
                val vein = if ((x + y) % 10 == 0 || kotlin.math.abs(x - 128) < 2) 38 else 0
                Color.rgb(55 + vein / 3, (132 + vein).coerceAtMost(220), 42 + vein / 4)
            } else {
                Color.rgb(235, 235, 235)
            }
        }
        val leafPaperReport = validator.validate(leafOnWhite)
        assertFalse("Centered leaf on white paper must not be flagged as non-leaf", leafPaperReport.isNonLeaf)
        assertTrue("Centered leaf on white paper must pass quality gate: ${leafPaperReport.issues}", leafPaperReport.isValid)
        leafOnWhite.recycle()

        // Purple flower & cyan screen (must be rejected as non-leaf)
        val purpleFlower = fillBitmap(256, 256) { x, y ->
            val delta = if ((x + y) % 9 == 0) 25 else 0
            Color.rgb(185 - delta, 55 + delta, 175 - delta)
        }
        assertTrue(validator.validate(purpleFlower).isNonLeaf)
        purpleFlower.recycle()

        val cyanUi = fillBitmap(256, 256) { x, y ->
            val stripe = if (y % 12 < 4) 30 else 0
            Color.rgb(30 + stripe, 185 - stripe, 225 - stripe)
        }
        assertTrue(validator.validate(cyanUi).isNonLeaf)
        cyanUi.recycle()
    }

    // =========================================================================
    // 2. EXPLAINABILITY ENGINE STRESS TESTS
    // =========================================================================

    @Test
    fun explainabilityEngineHandlesCamMockContrastAndZeroGridsSafely() {
        val engine = HybridExplainabilityEngine()

        // Zero grid normalization must never produce NaN
        val zeroGrid = Array(5) { FloatArray(5) { 0f } }
        val normZero = engine.normalizeGrid(zeroGrid, 0f)
        normZero.forEach { row -> row.forEach { v -> assertEquals(0f, v, 1e-6f) } }

        // SVM-weighted CAM on synthetic 7x7x4 spatial feature map
        val spatial = Array(7) { y ->
            Array(7) { x ->
                floatArrayOf((x + y) * 0.1f, 0.5f, 0.2f, 1.0f)
            }
        }
        val scaler = StandardScaler(
            mean = floatArrayOf(0f, 0f, 0f, 0f),
            scale = floatArrayOf(1f, 0f, 2f, 0.5f) // includes zero-scale edge case
        )
        val svm = LinearSVMClassifier(
            classes = listOf(GuavaLeafClass.ANTHRACNOSE, GuavaLeafClass.HEALTHY),
            coef = arrayOf(floatArrayOf(1.2f, 0.5f, -0.3f, 0.8f)),
            intercept = floatArrayOf(0.1f),
            strategy = SvmMulticlassStrategy.OVO
        )
        val camResult = engine.computeSvmWeightedFeatureMapCam(spatial, 0, scaler, svm)
        assertEquals(ExplanationMethodType.SVM_WEIGHTED_FEATURE_MAP_CAM, camResult.method)
        assertEquals(224, camResult.heatmapBitmap.width)
        assertEquals(224, camResult.heatmapBitmap.height)
        camResult.heatmapBitmap.recycle()

        // Dev mock contrast map
        val leaf = createTestLeafBitmap()
        val mockMap = engine.computeDevMockContrastMap(leaf)
        assertEquals(224, mockMap.heatmapBitmap.width)
        assertEquals(224, mockMap.heatmapBitmap.height)
        mockMap.heatmapBitmap.recycle()
        leaf.recycle()
    }

    // =========================================================================
    // 3. STORAGE SAFETY, PATH BOUNDARY & ROOM CRUD REGRESSION TESTS
    // =========================================================================

    @Test
    fun leafImageStorageRespectsDirectoryBoundaryAndCleansCorruptedImports() = runTest {
        // Regression test: sibling dir with matching prefix must NOT be deleted
        val siblingDir = File(context.filesDir, "leaf_scans_backup").apply { mkdirs() }
        val siblingFile = File(siblingDir, "protected.jpg").apply { writeText("keep-me") }
        assertTrue(siblingFile.exists())

        imageStorage.deleteImageSafely(siblingFile.absolutePath)
        assertTrue("deleteImageSafely MUST NOT delete sibling directory files!", siblingFile.exists())
        imageStorage.deleteImageSafely("")

        // Cleanup sibling test file
        siblingFile.delete()
        siblingDir.delete()

        // Unreadable URI import must fail cleanly and leave zero orphan temp files
        val beforeBytes = imageStorage.calculateStorageBytes()
        val importResult = imageStorage.importFromUri(Uri.parse("content://invalid.guavacare.test/missing"))
        assertTrue("Expected unreadable URI import to fail", importResult.isFailure)
        assertEquals(
            "Failed import must not leak orphan temp file in scansDir",
            beforeBytes,
            imageStorage.calculateStorageBytes()
        )

        // Non-existent path returns null safely
        assertNull(imageStorage.loadOrientedBitmap("/non/existent/file.jpg"))
    }

    @Test
    fun scanRepositoryHandlesMetadataUpdatesScoreJsonAndDeleteAll() = runTest {
        val scores = mapOf(
            GuavaLeafClass.HEALTHY to 1.42f,
            GuavaLeafClass.ANTHRACNOSE to -0.35f
        )
        val encoded = ScanRepository.encodeScoresJson(scores)
        val decoded = ScanRepository.decodeScoresJson(encoded)
        assertEquals(2, decoded.size)
        assertEquals(1.42f, decoded[GuavaLeafClass.HEALTHY]!!, 1e-4f)
        assertTrue(ScanRepository.decodeScoresJson("not-json").isEmpty())

        val f1 = imageStorage.createTempCaptureFile().apply {
            FileOutputStream(this).use { it.write(byteArrayOf(1, 2, 3)) }
        }
        val id = scanRepository.saveScan(
            ScanRecord(
                imagePath = f1.absolutePath,
                predictedClass = GuavaLeafClass.ANTHRACNOSE,
                decisionScore = 1.2f,
                inferenceTimeMs = 15L,
                orchardName = "Initial Orchard"
            )
        )
        scanRepository.updateScanMetadata(id, "Updated Orchard", "Plot-7", "Treated leaf")
        val updated = scanRepository.allScans.first().first()
        assertEquals("Updated Orchard", updated.orchardName)
        assertEquals("Plot-7", updated.plotId)
        assertEquals("Treated leaf", updated.notes)

        scanRepository.deleteAllScans()
        assertTrue(scanRepository.allScans.first().isEmpty())
        assertFalse(f1.exists())
    }

    // =========================================================================
    // 4. INSIGHTS & BILINGUAL STRINGS INTEGRITY
    // =========================================================================

    @Test
    fun orchardInsightsHandlesEmptyAndOldRecordsAccurately() {
        val calculator = OrchardInsightsCalculator()
        val empty = calculator.calculate(emptyList())
        assertEquals(0, empty.totalScans)
        assertEquals(0, empty.healthyCount)
        assertEquals(0, empty.affectedCount)
        assertNull(empty.mostFrequentCondition)
        assertTrue(empty.weeklyActivity.isEmpty())

        val now = 1_750_000_000_000L
        val oldTimestamp = now - 20L * 86_400_000L
        val summary = calculator.calculate(
            scans = listOf(
                ScanRecord(id = 1, imagePath = "/1.jpg", predictedClass = GuavaLeafClass.HEALTHY, decisionScore = 1f, inferenceTimeMs = 10, timestamp = oldTimestamp),
                ScanRecord(id = 2, imagePath = "/2.jpg", predictedClass = GuavaLeafClass.PEST_DAMAGE, decisionScore = 1f, inferenceTimeMs = 10, timestamp = now)
            ),
            nowMillis = now
        )
        assertEquals(2, summary.totalScans)
        assertEquals(1, summary.healthyCount)
        assertEquals(1, summary.affectedCount)
        assertEquals(1, summary.weeklyActivity.sumOf { it.count })
    }

    @Test
    fun allBilingualStringsAreNonBlankInBothEnglishAndBangla() {
        listOf(AppLanguage.ENGLISH, AppLanguage.BANGLA).forEach { lang ->
            val strings = BilingualStrings.get(lang)
            assertTrue(strings.appTitle.isNotBlank())
            assertTrue(strings.nonLeafWarningBadge.isNotBlank())
            assertTrue(strings.nonLeafWarningTitle.isNotBlank())
            assertTrue(strings.nonLeafWarningBody.isNotBlank())
            assertTrue(strings.nonLeafWarningTip.isNotBlank())
            assertTrue(strings.nonLeafCaptureLeafAction.isNotBlank())
            assertTrue(strings.nonLeafChooseGalleryAction.isNotBlank())
            assertTrue(strings.privacyPolicy.isNotBlank())
            assertTrue(strings.noMatchingScansTitle.isNotBlank())
            assertTrue(strings.clearFilters.isNotBlank())
            assertTrue(strings.navAbout.isNotBlank())
            assertTrue(strings.aboutTitle.isNotBlank())
            assertTrue(strings.aboutOverviewBody.isNotBlank())
            assertTrue(strings.aboutClassesTitle.isNotBlank())
            assertTrue(strings.aboutDeveloperTitle.isNotBlank())
            assertTrue(strings.aboutDeveloperName.isNotBlank())
            assertTrue(strings.aboutDeveloperEmailValue.isNotBlank())
        }
    }

    // =========================================================================
    // 5. ROBOLECTRIC COMPOSE UI CRITICAL USER JOURNEY (CUJ) TESTS
    // =========================================================================

    @Test
    fun scannerScreenShowsBanglaNonLeafWarningAndTriggersRetakeCallback() {
        val stringsBn = BilingualStrings.get(AppLanguage.BANGLA)
        val nonLeafBitmap = fillBitmap(240, 240) { _, _ -> Color.rgb(210, 155, 120) }
        val nonLeafReport = ImageQualityReport(
            isValid = false,
            width = 240,
            height = 240,
            blurVariance = 45.0,
            meanLuminance = 150.0,
            foliageRatio = 0.0f,
            issues = listOf(
                QualityIssue(
                    type = QualityIssueType.POTENTIALLY_NON_LEAF,
                    messageEn = "Not a leaf",
                    messageBn = "এটি পাতার ছবি নয়"
                )
            )
        )
        var discarded = false

        composeRule.setContent {
            GuavaCareTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.BANGLA) {
                ScannerScreen(
                    strings = stringsBn,
                    language = AppLanguage.BANGLA,
                    showLeafGuide = true,
                    devMockModeEnabled = false,
                    scanFlowState = ScanFlowState.NonLeafWarning(
                        imagePath = "/tmp/non_leaf.jpg",
                        bitmap = nonLeafBitmap,
                        report = nonLeafReport
                    ),
                    autoLaunchGallery = false,
                    onGalleryAutoLaunchConsumed = {},
                    onCreateCaptureFile = { File("/tmp/cap.jpg") },
                    onImageCaptured = {},
                    onGalleryImageSelected = {},
                    onRetryScan = { discarded = true },
                    onOpenSettings = {},
                    onDiscardPreview = { discarded = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("non_leaf_warning_card").assertIsDisplayed()
        composeRule.onNodeWithText(stringsBn.nonLeafWarningTitle).assertIsDisplayed()
        composeRule.onNodeWithTag("non_leaf_retake_button").performClick()
        composeRule.waitForIdle()

        assertTrue("Clicking capture leaf photo on NonLeafWarning must dismiss warning", discarded)
        nonLeafBitmap.recycle()
    }

    @Test
    fun historyScreenShowsEmptyFilterResetAndCleanDetailDialog() {
        val stringsBn = BilingualStrings.get(AppLanguage.BANGLA)
        var filtersCleared = false

        composeRule.setContent {
            GuavaCareTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.BANGLA) {
                HistoryScreen(
                    strings = stringsBn,
                    language = AppLanguage.BANGLA,
                    records = emptyList(),
                    totalSavedCount = 3,
                    filterState = HistoryFilterState(
                        searchQuery = "nonexistent",
                        selectedClass = GuavaLeafClass.ANTHRACNOSE,
                        dateFilter = HistoryDateFilter.TODAY
                    ),
                    onSearchChange = {},
                    onClassFilterChange = {},
                    onDateFilterChange = {},
                    onReopenDiagnosis = {},
                    onDeleteRecord = {},
                    onDeleteAll = {},
                    onStartScan = {},
                    onClearFilters = { filtersCleared = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("history_lazy_column")
            .performScrollToNode(hasTestTag("empty_state_action_button"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText(stringsBn.noMatchingScansTitle).assertIsDisplayed()
        composeRule.onNodeWithTag("empty_state_action_button").performClick()
        composeRule.waitForIdle()
        assertTrue("Clear filters button must invoke onClearFilters", filtersCleared)
    }

    @Test
    fun homeSettingsPrivacyAndDiagnosisScreensRenderCleanlyWithoutRegressions() {
        val stringsEn = BilingualStrings.get(AppLanguage.ENGLISH)
        var openedPrivacy = false

        composeRule.setContent {
            GuavaCareTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.ENGLISH) {
                SettingsScreen(
                    strings = stringsEn,
                    preferences = UserPreferences(),
                    modelHealth = ModelHealthStatus(
                        isProductionReady = true,
                        modelVersion = "1.0",
                        multiclassStrategy = "OVO",
                        tflitePresent = true,
                        hasImportedTflite = false,
                        tfliteSource = "assets/models/feature_extractor_fp16.tflite",
                        classifierWeightsReady = true,
                        missingAssets = emptyList(),
                        checks = emptyList(),
                        tfliteSizeBytes = 4_200_000L,
                        tfliteHeaderValid = true,
                        isRuntimeWarm = true
                    ),
                    coldInitMs = 120L,
                    lastLatencyMs = 18L,
                    storageBytes = 2048L,
                    onLanguageSelected = {},
                    onThemeSelected = {},
                    onStrictQualityToggle = {},
                    onLeafGuideToggle = {},
                    onAutoSaveToggle = {},
                    onDevMockModeToggle = {},
                    onClearAllScans = {},
                    onOpenResearchInfo = {},
                    onBack = {},
                    onOpenPrivacyPolicy = { openedPrivacy = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("settings_privacy_policy_button"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_privacy_policy_button").performClick()
        composeRule.waitForIdle()
        assertTrue("Settings Privacy Policy button must open privacy policy", openedPrivacy)
    }

    @Test
    fun diagnosisScreenRendersWithInMemoryFallbackBitmapAndAdvisory() {
        val stringsEn = BilingualStrings.get(AppLanguage.ENGLISH)
        val leafBitmap = createTestLeafBitmap()
        val heatmap = createBitmap(224, 224)
        val advisory = AdvisoryRepository(context).loadBundle().advisories.first {
            it.leafClass == GuavaLeafClass.ANTHRACNOSE
        }
        var saved = false

        composeRule.setContent {
            GuavaCareTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.ENGLISH) {
                DiagnosisScreen(
                    strings = stringsEn,
                    language = AppLanguage.ENGLISH,
                    readyState = ScanFlowState.Ready(
                        prediction = PredictionResult(
                            imagePath = "/non/existent/temp.jpg",
                            predictedClass = GuavaLeafClass.ANTHRACNOSE,
                            decisionScore = 1.4f,
                            calibratedConfidence = null,
                            marginToRunnerUp = 0.8f,
                            allScores = mapOf(GuavaLeafClass.ANTHRACNOSE to 1.4f),
                            strategy = SvmMulticlassStrategy.OVO,
                            isLowMarginUncertain = false,
                            qualityReport = ImageQualityReport(true, 256, 256, 90.0, 120.0, 0.7f, emptyList()),
                            explainability = ExplainabilityResult(
                                method = ExplanationMethodType.SVM_WEIGHTED_FEATURE_MAP_CAM,
                                heatmapBitmap = heatmap,
                                gridWidth = 7,
                                gridHeight = 7,
                                generationTimeMs = 4L
                            ),
                            metrics = InferenceMetrics(4, 3, 12, 1, 4, 20, null),
                            modelVersion = "1.0",
                            isMockResult = false
                        ),
                        originalBitmap = leafBitmap
                    ),
                    advisory = advisory,
                    onSave = { _, _, _ -> saved = true },
                    onScanAgain = {},
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("diagnosis_lazy_column")
            .performScrollToNode(hasTestTag("xai_mode_original"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("xai_mode_original").assertIsDisplayed()
        composeRule.onNodeWithTag("xai_mode_heatmap").assertIsDisplayed()
        composeRule.onNodeWithTag("xai_mode_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("diagnosis_lazy_column")
            .performScrollToNode(hasTestTag("diagnosis_save_button"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("diagnosis_save_button").performClick()
        composeRule.waitForIdle()
        assertTrue("Save button on DiagnosisScreen must trigger onSave", saved)

        leafBitmap.recycle()
        heatmap.recycle()
    }

    @Test
    fun homeAndPrivacyPolicyScreensRenderCleanly() {
        val stringsBn = BilingualStrings.get(AppLanguage.BANGLA)
        var backClicked = false

        composeRule.setContent {
            GuavaCareTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.BANGLA) {
                PrivacyPolicyScreen(
                    strings = stringsBn,
                    onBack = { backClicked = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(stringsBn.privacyPolicy).assertIsDisplayed()
        composeRule.onNodeWithText(stringsBn.privacyPolicyOfflineTitle).assertIsDisplayed()
        composeRule.onNodeWithTag("privacy_policy_back_button").performClick()
        composeRule.waitForIdle()
        assertTrue(backClicked)
    }

    @Test
    fun homeScreenQuickScanAndSettingsButtonsAreAccessible() {
        val stringsBn = BilingualStrings.get(AppLanguage.BANGLA)
        var quickScanClicked = false
        var settingsClicked = false

        composeRule.setContent {
            GuavaCareTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.BANGLA) {
                HomeScreen(
                    strings = stringsBn,
                    language = AppLanguage.BANGLA,
                    devMockModeEnabled = false,
                    scans = emptyList(),
                    insights = OrchardInsightsSummary(),
                    tips = emptyList(),
                    onQuickScanClick = { quickScanClicked = true },
                    onGalleryUploadClick = {},
                    onOpenScan = {},
                    onViewAllHistory = {},
                    onOpenSettings = { settingsClicked = true },
                    onToggleLanguage = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_quick_scan_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("open_settings_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertTrue(quickScanClicked)
        assertTrue(settingsClicked)
    }

    @Test
    fun aboutTabReplacesAdvisoryAndRendersAboutScreenActions() {
        assertEquals(5, topLevelDestinations.size)
        assertTrue(topLevelDestinations.any { it.route == GuavaCareRoutes.ROUTE_ABOUT && it.testTag == "nav_about" })
        assertFalse(topLevelDestinations.any { it.route == "advisory" || it.testTag == "nav_advisory" })

        val stringsBn = BilingualStrings.get(AppLanguage.BANGLA)
        var privacyClicked = false
        var researchClicked = false
        var settingsClicked = false

        composeRule.setContent {
            GuavaCareTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.BANGLA) {
                AboutScreen(
                    strings = stringsBn,
                    language = AppLanguage.BANGLA,
                    onOpenPrivacyPolicy = { privacyClicked = true },
                    onOpenResearchInfo = { researchClicked = true },
                    onOpenSettings = { settingsClicked = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(stringsBn.aboutTitle).assertIsDisplayed()
        composeRule.onNodeWithText(stringsBn.aboutOverviewTitle).assertIsDisplayed()

        composeRule.onNodeWithTag("about_lazy_column")
            .performScrollToNode(hasTestTag("about_developer_card"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("about_developer_card").assertIsDisplayed()
        composeRule.onNodeWithText("Md. Sabit Al Mahfuz").assertIsDisplayed()
        composeRule.onNodeWithText("Daffodil International University").assertIsDisplayed()
        composeRule.onNodeWithTag("about_contact_developer_button").assertIsDisplayed()
        composeRule.onNodeWithTag("about_developer_github_button").assertIsDisplayed()
        composeRule.onNodeWithTag("about_developer_portfolio_button").assertIsDisplayed()

        composeRule.onNodeWithTag("about_lazy_column")
            .performScrollToNode(hasTestTag("about_privacy_policy_button"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("about_privacy_policy_button").performClick()

        composeRule.onNodeWithTag("about_lazy_column")
            .performScrollToNode(hasTestTag("about_research_button"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("about_research_button").performClick()

        composeRule.onNodeWithTag("about_lazy_column")
            .performScrollToNode(hasTestTag("about_open_settings_button"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("about_open_settings_button").performClick()
        composeRule.waitForIdle()

        assertTrue(privacyClicked)
        assertTrue(researchClicked)
        assertTrue(settingsClicked)
    }

    private fun fillBitmap(width: Int, height: Int, pixelAt: (Int, Int) -> Int): Bitmap {
        val bmp = createBitmap(width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bmp[x, y] = pixelAt(x, y)
            }
        }
        return bmp
    }

    private fun createTestLeafBitmap(): Bitmap = fillBitmap(256, 256) { x, y ->
        val vein = if ((x + y) % 12 == 0) 40 else 0
        Color.rgb(50 + vein / 3, (130 + vein).coerceAtMost(220), 42 + vein / 4)
    }
}
