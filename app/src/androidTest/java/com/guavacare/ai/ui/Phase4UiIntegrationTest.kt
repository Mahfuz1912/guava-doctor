package com.guavacare.ai.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.MainActivity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class Phase4UiIntegrationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    private val primaryDir: File by lazy {
        File(targetContext.filesDir, "phase4_screenshots").apply { mkdirs() }
    }

    private val sdcardDir: File by lazy {
        File("/sdcard/Download/phase4_screenshots").apply { mkdirs() }
    }

    private fun takeScreenshot(filename: String) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(1200) // Ensure UI has stabilized
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val screenshot = uiAutomation.takeScreenshot()
        assertNotNull("Screenshot bitmap should not be null", screenshot)

        // Save to internal storage
        val f1 = File(primaryDir, filename)
        FileOutputStream(f1).use { out ->
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // Also try saving to public sdcard Download dir
        try {
            val f2 = File(sdcardDir, filename)
            FileOutputStream(f2).use { out ->
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (_: Throwable) {
            // Internal file is the guaranteed primary
        }
    }

    private fun loadSampleLeafFile(): File {
        val assetStream = testContext.assets.open("parity/images/healthy_healthy_0003.jpg")
        val cacheFile = File(targetContext.cacheDir, "test_preview_sample.jpg")
        FileOutputStream(cacheFile).use { out ->
            assetStream.copyTo(out)
        }
        return cacheFile
    }

    @Before
    fun setUp() {
        primaryDir.mkdirs()
    }

    @Test
    fun testCompletePhase4UiWorkflowAndCaptureScreenshots() {
        // 1. Home Dashboard Screen
        composeTestRule.waitForIdle()
        takeScreenshot("01_home_dashboard.png")
        composeTestRule.onNodeWithTag("open_settings_button").assertIsDisplayed()

        // 2. Settings & Diagnostics Screen
        composeTestRule.onNodeWithTag("open_settings_button").performClick()
        composeTestRule.waitForIdle()
        takeScreenshot("09_settings_diagnostics.png")

        // 3. About Research Screen (Scroll LazyColumn down to find the button)
        composeTestRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("settings_about_research_button"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_about_research_button").performClick()
        composeTestRule.waitForIdle()
        takeScreenshot("10_about_research.png")

        // Navigate Back to Settings, then Back to Home
        composeTestRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
        composeTestRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        // 4. Scan History Screen (Empty or existing state)
        composeTestRule.onNodeWithTag("nav_history").performClick()
        composeTestRule.waitForIdle()
        takeScreenshot("07_scan_history.png")

        // 5. Navigate to Scanner Screen
        composeTestRule.onNodeWithTag("nav_scan").performClick()
        composeTestRule.waitForIdle()
        takeScreenshot("02_camera_scanner.png")

        // 6. Stage Image Preview via ViewModel
        val sampleFile = loadSampleLeafFile()
        composeTestRule.activityRule.scenario.onActivity { activity ->
            val appContainer = (activity.application as com.guavacare.ai.GuavaCareApplication).appContainer
            val vm = androidx.lifecycle.ViewModelProvider(
                activity,
                com.guavacare.ai.presentation.MainViewModel.Factory(appContainer)
            )[com.guavacare.ai.presentation.MainViewModel::class.java]
            vm.stageCapturedFileForPreview(sampleFile)
        }
        composeTestRule.waitForIdle()
        takeScreenshot("03_image_preview.png")

        // Verify Image Preview UI controls
        composeTestRule.onNodeWithTag("preview_analyze_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("preview_retake_button").assertIsDisplayed()

        // 7. Confirm & Analyze Preview Image
        composeTestRule.onNodeWithTag("preview_analyze_button").performClick()
        // Wait 350ms for Processing state to mount and capture loading overlay
        Thread.sleep(350)
        takeScreenshot("04_analysis_loading.png")

        // 8. Wait for Diagnosis Screen to appear (inference + 5x5 occlusion sensitivity takes ~4-10s)
        val startTime = System.currentTimeMillis()
        var diagnosisReady = false
        var lastState = ""
        while (System.currentTimeMillis() - startTime < 60000) {
            composeTestRule.activityRule.scenario.onActivity { activity ->
                val appContainer = (activity.application as com.guavacare.ai.GuavaCareApplication).appContainer
                val vm = androidx.lifecycle.ViewModelProvider(
                    activity,
                    com.guavacare.ai.presentation.MainViewModel.Factory(appContainer)
                )[com.guavacare.ai.presentation.MainViewModel::class.java]
                lastState = vm.scanFlowState.value.javaClass.simpleName
            }
            if (lastState == "Ready") {
                diagnosisReady = true
                break
            }
            Thread.sleep(1000)
        }
        assertTrue("Diagnosis screen should be ready within 60 seconds (last state: $lastState)", diagnosisReady)
        composeTestRule.waitForIdle()
        takeScreenshot("05_diagnosis_result.png")

        // Verify Diagnosis components and scroll to Save button
        composeTestRule.onNodeWithTag("diagnosis_lazy_column")
            .performScrollToNode(hasTestTag("diagnosis_save_button"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("diagnosis_save_button").assertIsDisplayed()

        // 9. Interactive Explainability Visualization
        composeTestRule.onNodeWithTag("diagnosis_lazy_column")
            .performScrollToNode(hasTestTag("xai_mode_heatmap"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("xai_mode_heatmap").performClick()
        composeTestRule.waitForIdle()
        takeScreenshot("06_explainability_heatmap.png")

        composeTestRule.onNodeWithTag("xai_mode_overlay").performClick()
        composeTestRule.waitForIdle()
        takeScreenshot("06b_explainability_overlay.png")

        // 10. Fill Metadata and Save Scan
        composeTestRule.onNodeWithTag("diagnosis_lazy_column")
            .performScrollToNode(hasTestTag("diagnosis_orchard_input"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("diagnosis_orchard_input").performTextInput("Test Orchard A")
        composeTestRule.onNodeWithTag("diagnosis_plot_input").performTextInput("Block-4")
        composeTestRule.onNodeWithTag("diagnosis_notes_input").performTextInput("Field verification scan")

        composeTestRule.onNodeWithTag("diagnosis_lazy_column")
            .performScrollToNode(hasTestTag("diagnosis_save_button"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("diagnosis_save_button").performClick()
        composeTestRule.waitForIdle()

        // Navigate back from Diagnosis Screen to top-level navigation
        composeTestRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        // 11. Navigate to History & Open Details Dialog
        composeTestRule.onNodeWithTag("nav_history").performClick()
        composeTestRule.waitForIdle()
        takeScreenshot("07b_scan_history_with_record.png")

        // Click the first record in history
        composeTestRule.onNodeWithText("Test Orchard A", substring = true).performClick()
        composeTestRule.waitForIdle()
        takeScreenshot("08_scan_details_dialog.png")

        // Verify Reopen button in detail dialog
        composeTestRule.onNodeWithTag("history_reopen_button").assertIsDisplayed()
    }
}
