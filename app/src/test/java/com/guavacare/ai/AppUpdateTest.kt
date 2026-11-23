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
        val nextCode = com.example.BuildConfig.VERSION_CODE + 1
        val nextName = "$nextCode.0"
        val release = AppReleaseInfo(
            versionCode = nextCode,
            versionName = nextName,
            apkUrl = "https://example.com/app-v$nextName.apk",
            releaseNotesEn = "New v$nextName diagnosis speed boost.",
            releaseNotesBn = "নতুন সংস্করণ ২.০ তে দ্রুত রোগ নির্ণয় সুবিধা।",
            forceUpdate = true,
            fileSizeBytes = 47_185_920L
        )
        assertTrue(AppUpdateState.UpdateAvailable(release).isForceUpdateGateActive())
        assertFalse(AppUpdateState.UpToDate(1000L).isForceUpdateGateActive())

        var downloadClicked = false
        val fakeApk = File(context.cacheDir, "fake-next.apk")
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
        // Version pill shows clean versionName without versionCode
        composeRule.onNodeWithText("v$nextName").assertIsDisplayed()
        composeRule.onNodeWithText("v$nextName ($nextCode)").assertDoesNotExist()

        // What's New starts collapsed as a dropdown; expands on tap and shows ONLY active language (Bangla)
        composeRule.onNodeWithText("নতুন সংস্করণ ২.০ তে দ্রুত রোগ নির্ণয় সুবিধা।").assertDoesNotExist()
        composeRule.onNodeWithTag("force_update_whats_new_dropdown")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("নতুন সংস্করণ ২.০ তে দ্রুত রোগ নির্ণয় সুবিধা।")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("New v$nextName diagnosis speed boost.").assertDoesNotExist()

        composeRule.onNodeWithTag("force_update_download_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertTrue(downloadClicked)

        // Switching to English shows ONLY English release notes inside the expanded dropdown
        langHolder.value = AppLanguage.ENGLISH
        composeRule.waitForIdle()
        composeRule.onNodeWithText("New v$nextName diagnosis speed boost.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("নতুন সংস্করণ ২.০ তে দ্রুত রোগ নির্ণয় সুবিধা।").assertDoesNotExist()

        // Transition to Downloading state in English
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
        var shareCount = 0
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
                    onCheckForUpdates = { checkTriggered = true },
                    onShareApp = { shareCount++ }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("about_header_share_button").assertDoesNotExist()

        composeRule.onNodeWithTag("about_lazy_column")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("about_check_updates_button"))
        composeRule.onNodeWithTag("about_share_app_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(1, shareCount)

        composeRule.onNodeWithTag("about_update_status_text").assertIsDisplayed()
        composeRule.onNodeWithTag("about_check_updates_button").performClick()
        composeRule.waitForIdle()
        assertTrue(checkTriggered)
    }

    @Test
    fun releaseNotesFormatterStripsNoiseAndSplitsBilingualSections() {
        val rawGitHubBody = """
            ### English
            - Modern Home Page: Redesigned card-based layout with Health Tools.
            - Google Sign-In Fix: Resolved OAuth web client issue.

            ### বাংলা
            - আধুনিক হোম পেজ: নতুন কার্ড-ভিত্তিক ডিজাইন যুক্ত হয়েছে।
            - Google সাইন-ইন সমাধান: লগিন ত্রুটি দূর করা হয়েছে।

            Version: v2.2
            versionCode: 4

            Signed Android APK with release keystore.

            **Full Changelog**:
            https://github.com/Mahfuz1912/guava-doctor/compare/v2.1...v2.2
        """.trimIndent()

        val (enSection, bnSection) = com.guavacare.ai.domain.model.ReleaseNotesFormatter.splitBilingualBody(rawGitHubBody)
        assertTrue(enSection.contains("Modern Home Page"))
        assertFalse(enSection.contains("versionCode"))
        assertTrue(bnSection.contains("আধুনিক হোম পেজ"))

        val enHighlights = com.guavacare.ai.domain.model.ReleaseNotesFormatter.parseHighlights(enSection)
        assertEquals(2, enHighlights.size)
        assertEquals("Modern Home Page", enHighlights[0].title)
        assertEquals("Redesigned card-based layout with Health Tools.", enHighlights[0].detail)

        val bnHighlights = com.guavacare.ai.domain.model.ReleaseNotesFormatter.parseHighlights(bnSection)
        assertEquals(2, bnHighlights.size)
        assertEquals("আধুনিক হোম পেজ", bnHighlights[0].title)

        // Legacy single-line summary with boilerplate should expand cleanly and drop noise
        val legacyBody = """
            Guava Doctor v2.1 — Google & Email/Password login, cloud-synced history, Profile & Forgot Password, and updated Privacy Policy.

            Version: v2.1
            versionCode: 3

            Signed Android APK with release keystore.

            **Full Changelog**:
            https://github.com/Mahfuz1912/guava-doctor/compare/v2.0...v2.1
        """.trimIndent()
        val legacyHighlights = com.guavacare.ai.domain.model.ReleaseNotesFormatter.parseHighlights(legacyBody)
        assertEquals(4, legacyHighlights.size)
        assertTrue(legacyHighlights.none { it.title.contains("Full Changelog") || it.title.contains("versionCode") || it.title.contains("https://") })
    }
}
