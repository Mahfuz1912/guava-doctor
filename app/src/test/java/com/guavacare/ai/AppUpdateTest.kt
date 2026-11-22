package com.guavacare.ai

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.guavacare.ai.core.common.BilingualStrings
import com.guavacare.ai.core.utils.ApkInstallerHelper
import com.guavacare.ai.data.repository.UpdateRepository
import com.guavacare.ai.domain.model.AppLanguage
import com.guavacare.ai.domain.model.AppReleaseInfo
import com.guavacare.ai.domain.model.AppUpdateState
import com.guavacare.ai.domain.model.AppVersionComparator
import com.guavacare.ai.domain.model.ThemeMode
import com.guavacare.ai.domain.model.isForceUpdateGateActive
import com.guavacare.ai.presentation.screens.about.AboutScreen
import com.guavacare.ai.presentation.screens.update.ForceUpdateScreen
import com.guavacare.ai.presentation.theme.GuavaCareTheme
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppUpdateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var repository: UpdateRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = UpdateRepository(context)
    }

    @Test
    fun versionComparatorDetectsNewerVersionCodeAndSemverAccurately() {
        assertTrue(
            AppVersionComparator.isRemoteNewer(
                remoteVersionCode = 2,
                remoteVersionName = "2.0",
                currentVersionCode = 1,
                currentVersionName = "1.0"
            )
        )
        assertTrue(
            AppVersionComparator.isRemoteNewer(
                remoteVersionCode = null,
                remoteVersionName = "v2.0",
                currentVersionCode = 1,
                currentVersionName = "1.0"
            )
        )
        assertTrue(
            AppVersionComparator.isRemoteNewer(
                remoteVersionCode = null,
                remoteVersionName = "v1.0.1",
                currentVersionCode = 1,
                currentVersionName = "1.0"
            )
        )
        assertFalse(
            AppVersionComparator.isRemoteNewer(
                remoteVersionCode = 1,
                remoteVersionName = "1.0",
                currentVersionCode = 1,
                currentVersionName = "1.0"
            )
        )
        assertFalse(
            AppVersionComparator.isRemoteNewer(
                remoteVersionCode = 1,
                remoteVersionName = "v2.0",
                currentVersionCode = 2,
                currentVersionName = "2.0"
            )
        )
        assertFalse(
            AppVersionComparator.isRemoteNewer(
                remoteVersionCode = null,
                remoteVersionName = "invalid-tag",
                currentVersionCode = 1,
                currentVersionName = "1.0"
            )
        )
    }

    @Test
    fun parseReleaseJsonHandlesCustomVersionJson() {
        val customJson = """
            {
              "versionCode": 2,
              "versionName": "2.0",
              "apkUrl": "https://example.com/releases/guava-doctor-v2.apk",
              "releaseNotesEn": "Improved anthracnose precision and faster camera startup.",
              "releaseNotesBn": "রোগ নির্ণয়ের নির্ভুলতা ও ক্যামেরা স্পিড বৃদ্ধি করা হয়েছে।",
              "forceUpdate": true,
              "fileSizeBytes": 49152000,
              "publishedAt": "2026-11-22T10:00:00Z"
            }
        """.trimIndent()

        val info = repository.parseReleaseJson(customJson)
        assertEquals(2, info.versionCode)
        assertEquals("2.0", info.versionName)
        assertEquals("https://example.com/releases/guava-doctor-v2.apk", info.apkUrl)
        assertTrue(info.forceUpdate)
        assertEquals(49152000L, info.fileSizeBytes)
        assertTrue(info.releaseNotesBn.contains("ক্যামেরা"))
    }

    @Test
    fun parseReleaseJsonHandlesGitHubLatestReleaseWithApkAsset() {
        val githubJson = """
            {
              "tag_name": "v2.0",
              "draft": false,
              "published_at": "2026-11-22T09:00:00Z",
              "body": "Version 2.0 release notes\nversionCode: 2\nAdded new leaf diagnostics.",
              "assets": [
                {
                  "name": "checksums.txt",
                  "size": 128,
                  "browser_download_url": "https://github.com/Mahfuz1912/guava-doctor/releases/download/v2.0/checksums.txt"
                },
                {
                  "name": "guava-doctor-v2.0.apk",
                  "content_type": "application/vnd.android.package-archive",
                  "size": 48234496,
                  "browser_download_url": "https://github.com/Mahfuz1912/guava-doctor/releases/download/v2.0/guava-doctor-v2.0.apk"
                }
              ]
            }
        """.trimIndent()

        val info = repository.parseReleaseJson(githubJson)
        assertEquals(2, info.versionCode)
        assertEquals("2.0", info.versionName)
        assertEquals(
            "https://github.com/Mahfuz1912/guava-doctor/releases/download/v2.0/guava-doctor-v2.0.apk",
            info.apkUrl
        )
        assertEquals(48234496L, info.fileSizeBytes)
        assertTrue(info.forceUpdate)

        // GitHub release without embedded versionCode still parses cleanly
        val noCodeJson = githubJson.replace("versionCode: 2\\n", "")
        val infoNoCode = repository.parseReleaseJson(noCodeJson)
        assertNull(infoNoCode.versionCode)
        assertTrue(
            AppVersionComparator.isRemoteNewer(
                remoteVersionCode = infoNoCode.versionCode,
                remoteVersionName = infoNoCode.versionName,
                currentVersionCode = 1,
                currentVersionName = "1.0"
            )
        )
    }

    @Test
    fun apkArchiveMagicValidatorRejectsHtmlAndAcceptsZipApk() {
        val tempDir = File(context.cacheDir, "apk_test").apply { mkdirs() }
        val htmlFile = File(tempDir, "not_an_apk.apk").apply {
            writeText("<html><body>404</body></html>")
        }
        val validApk = File(tempDir, "valid.apk").apply {
            writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00))
        }

        assertFalse(ApkInstallerHelper.isValidApkArchive(htmlFile))
        assertTrue(ApkInstallerHelper.isValidApkArchive(validApk))

        htmlFile.delete()
        validApk.delete()
        tempDir.delete()
    }

    @Test
    fun forceUpdateGateAndUiStatesRenderInEnglishAndBangla() {
        val release = AppReleaseInfo(
            versionCode = 2,
            versionName = "2.0",
            apkUrl = "https://example.com/app-v2.apk",
            releaseNotesEn = "New v2 diagnosis speed boost.",
            releaseNotesBn = "নতুন সংস্করণ ২.০ তে দ্রুত রোগ নির্ণয় সুবিধা।",
            forceUpdate = true,
            fileSizeBytes = 47_185_920L
        )
        assertTrue(AppUpdateState.UpdateAvailable(release).isForceUpdateGateActive())
        assertFalse(AppUpdateState.UpToDate(1000L).isForceUpdateGateActive())

        var downloadClicked = false
        val fakeApk = File(context.cacheDir, "fake-v2.apk")
        val stateHolder = androidx.compose.runtime.mutableStateOf<AppUpdateState>(
            AppUpdateState.UpdateAvailable(release)
        )
        val langHolder = androidx.compose.runtime.mutableStateOf(AppLanguage.BANGLA)

        composeRule.setContent {
            val lang = langHolder.value
            GuavaCareTheme(themeMode = ThemeMode.LIGHT, language = lang) {
                ForceUpdateScreen(
                    strings = BilingualStrings.get(lang),
                    language = lang,
                    updateState = stateHolder.value,
                    onToggleLanguage = {},
                    onStartDownload = { downloadClicked = true },
                    onCancelDownload = {},
                    onInstallApk = {},
                    onOpenInstallPermissionSettings = {},
                    onCheckInstallPermission = {},
                    onRetry = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("force_update_screen").assertIsDisplayed()
        composeRule.onNodeWithText("v2.0 (2)").assertIsDisplayed()
        composeRule.onNodeWithText("নতুন সংস্করণ ২.০ তে দ্রুত রোগ নির্ণয় সুবিধা।")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("force_update_download_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertTrue(downloadClicked)

        // Transition to Downloading state in English
        langHolder.value = AppLanguage.ENGLISH
        stateHolder.value = AppUpdateState.Downloading(
            release = release,
            bytesDownloaded = 20_971_520L,
            totalBytes = 41_943_040L,
            progress = 0.5f
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("force_update_progress_bar").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("50%").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("force_update_cancel_button").performScrollTo().assertIsDisplayed()

        // Transition to PermissionRequired state
        stateHolder.value = AppUpdateState.PermissionRequired(release = release, apkFile = fakeApk)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("force_update_permission_button").performScrollTo().assertIsDisplayed()

        // Transition to Error retry state
        stateHolder.value = AppUpdateState.Error(
            message = "Connection lost",
            release = release,
            canRetryDownload = true
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Connection lost").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("force_update_retry_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aboutScreenDisplaysCheckUpdatesButtonAndUpToDateStatus() {
        var checkTriggered = false
        val strings = BilingualStrings.get(AppLanguage.ENGLISH)

        composeRule.setContent {
            GuavaCareTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.ENGLISH) {
                AboutScreen(
                    strings = strings,
                    language = AppLanguage.ENGLISH,
                    onOpenPrivacyPolicy = {},
                    onOpenResearchInfo = {},
                    onOpenSettings = {},
                    updateState = AppUpdateState.UpToDate(System.currentTimeMillis()),
                    onCheckForUpdates = { checkTriggered = true }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("about_lazy_column")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("about_check_updates_button"))
        composeRule.onNodeWithTag("about_update_status_text").assertIsDisplayed()
        composeRule.onNodeWithTag("about_check_updates_button").performClick()
        composeRule.waitForIdle()
        assertTrue(checkTriggered)
    }
}
