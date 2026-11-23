package com.guavacare.ai

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.guavacare.ai.core.common.BilingualStrings
import com.guavacare.ai.core.data.BangladeshLocations
import com.guavacare.ai.data.local.PreferencesRepository
import com.guavacare.ai.data.remote.GoogleAuthHelper
import com.guavacare.ai.data.remote.GoogleAuthHelper.findActivity
import com.guavacare.ai.domain.model.AppLanguage
import com.guavacare.ai.domain.model.AuthGateReason
import com.guavacare.ai.domain.model.AuthUiState
import com.guavacare.ai.domain.model.CloudSyncStatus
import com.guavacare.ai.domain.model.GUEST_FREE_SCAN_LIMIT
import com.guavacare.ai.domain.model.OrchardInsightsSummary
import com.guavacare.ai.domain.model.ThemeMode
import com.guavacare.ai.domain.model.UserProfile
import com.guavacare.ai.presentation.navigation.GuavaCareRoutes
import com.guavacare.ai.presentation.navigation.visibleTopLevelDestinations
import com.guavacare.ai.presentation.screens.auth.LoginScreen
import com.guavacare.ai.presentation.screens.auth.RegisterScreen
import com.guavacare.ai.presentation.screens.home.HomeScreen
import com.guavacare.ai.presentation.screens.insights.InsightsScreen
import com.guavacare.ai.presentation.screens.profile.ProfileScreen
import com.guavacare.ai.presentation.screens.settings.AccountSettingsCard
import com.guavacare.ai.presentation.theme.GuavaCareTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthAndLocationFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun selectLocationOption(
        dropdownTag: String,
        dialogTag: String,
        optionNameEn: String
    ) {
        composeRule.onNodeWithTag(dropdownTag)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(dialogTag).assertIsDisplayed()
        composeRule.onNodeWithTag("${dialogTag}_list")
            .performScrollToNode(hasTestTag("location_option_$optionNameEn"))
        composeRule.onNodeWithTag("location_option_$optionNameEn")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun bangladeshDirectoryContainsAll8Divisions64DistrictsAndUpazilas() {
        val divisions = BangladeshLocations.divisions
        assertEquals("Bangladesh must have exactly 8 divisions", 8, divisions.size)

        var totalDistricts = 0
        divisions.forEach { division ->
            assertTrue(division.nameEn.isNotBlank() && division.nameBn.isNotBlank())
            val districts = BangladeshLocations.districtsFor(division.nameEn)
            assertTrue("Division ${division.nameEn} must have districts", districts.isNotEmpty())
            totalDistricts += districts.size

            districts.forEach { district ->
                assertTrue(district.nameEn.isNotBlank() && district.nameBn.isNotBlank())
                val upazilas = BangladeshLocations.upazilasFor(district.nameEn)
                assertTrue(
                    "District ${district.nameEn} must have at least one upazila",
                    upazilas.isNotEmpty()
                )
                upazilas.forEach { upa ->
                    assertTrue(upa.nameEn.isNotBlank() && upa.nameBn.isNotBlank())
                }
            }
        }
        assertEquals("Bangladesh must have exactly 64 districts", 64, totalDistricts)

        val formattedBn = BangladeshLocations.formatLocationSummary(
            divisionEn = "Barishal",
            districtEn = "Pirojpur",
            upazilaEn = "Nesarabad (Swarupkathi)",
            language = AppLanguage.BANGLA
        )
        assertTrue(formattedBn.contains("বরিশাল"))
        assertTrue(formattedBn.contains("পিরোজপুর"))
    }

    @Test
    fun guestScanCountPersistsAndReachesThreeScanLimit() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = PreferencesRepository(context)
        repo.setGuestScanCount(0)
        assertEquals(0, repo.preferencesFlow.first().guestScanCount)

        repeat(GUEST_FREE_SCAN_LIMIT) { expectedBefore ->
            val after = repo.incrementGuestScanCount()
            assertEquals(expectedBefore + 1, after)
        }
        val finalCount = repo.preferencesFlow.first().guestScanCount
        assertEquals(3, finalCount)
        assertTrue(finalCount >= GUEST_FREE_SCAN_LIMIT)
        repo.setGuestScanCount(0)
    }

    @Test
    fun loginScreenShowsQuotaBannerEmailSignInAndDirectGoogleButton() {
        val strings = BilingualStrings.get(AppLanguage.BANGLA)
        var registerClicked = false
        var googleClicked = false
        var emailLoginSubmitted: Pair<String, String>? = null

        composeRule.setContent {
            GuavaCareTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.BANGLA) {
                LoginScreen(
                    strings = strings,
                    language = AppLanguage.BANGLA,
                    gateReason = AuthGateReason.SCAN_LIMIT_REACHED,
                    isLoading = false,
                    errorMessage = null,
                    onEmailSignInClick = { email, password ->
                        emailLoginSubmitted = email to password
                    },
                    onGoogleSignInClick = { googleClicked = true },
                    onNavigateToRegister = { registerClicked = true },
                    onToggleLanguage = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("login_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("login_limit_reached_banner").performScrollTo().assertIsDisplayed()

        // Test Email/Password login flow
        composeRule.onNodeWithTag("login_email_input")
            .performScrollTo()
            .performTextInput("farmer@example.com")
        composeRule.onNodeWithTag("login_password_input")
            .performScrollTo()
            .performTextInput("secret123")
        composeRule.onNodeWithTag("login_email_submit_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertEquals("farmer@example.com" to "secret123", emailLoginSubmitted)

        // Test Direct Google Sign-In button ("Sign in with Google")
        composeRule.onNodeWithTag("login_google_button").performScrollTo()
        composeRule.onNodeWithText("Sign in with Google").assertIsDisplayed()
        composeRule.onNodeWithTag("login_google_button").performClick()
        composeRule.waitForIdle()
        assertTrue(googleClicked)

        // Test navigation to separate Register page
        composeRule.onNodeWithTag("go_to_register_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertTrue(registerClicked)
    }

    @Test
    fun registerScreenCollectsEmailPasswordAndCascadingDivisionDistrictUpazilaDropdowns() {
        val strings = BilingualStrings.get(AppLanguage.ENGLISH)
        var submitted: List<String>? = null

        composeRule.setContent {
            GuavaCareTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.ENGLISH) {
                RegisterScreen(
                    strings = strings,
                    language = AppLanguage.ENGLISH,
                    isLoading = false,
                    errorMessage = null,
                    onSubmitEmailRegistration = { name, email, password, division, district, upazila ->
                        submitted = listOf(name, email, password, division, district, upazila)
                    },
                    onNavigateToLogin = {},
                    onToggleLanguage = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("register_screen").assertIsDisplayed()

        // Enter Full Name, Email, Password, Confirm Password
        composeRule.onNodeWithTag("register_name_input")
            .performScrollTo()
            .performTextInput("Md. Karim Mia")
        composeRule.onNodeWithTag("register_email_input")
            .performScrollTo()
            .performTextInput("karim@example.com")
        composeRule.onNodeWithTag("register_password_input")
            .performScrollTo()
            .performTextInput("guava2026")
        composeRule.onNodeWithTag("register_confirm_password_input")
            .performScrollTo()
            .performTextInput("guava2026")

        // 1. Select Division: Barishal
        selectLocationOption("dropdown_division", "picker_dialog_division", "Barishal")

        // 2. Select District: Pirojpur
        selectLocationOption("dropdown_district", "picker_dialog_district", "Pirojpur")

        // 3. Select Upazila: Pirojpur Sadar
        selectLocationOption("dropdown_upazila", "picker_dialog_upazila", "Pirojpur Sadar")

        // Submit Email/Password Registration
        composeRule.onNodeWithTag("register_submit_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf("Md. Karim Mia", "karim@example.com", "guava2026", "Barishal", "Pirojpur", "Pirojpur Sadar"),
            submitted
        )

        // Verify Google Sign-In button is not shown on RegisterScreen
        composeRule.onNodeWithTag("register_google_button").assertDoesNotExist()
    }

    @Test
    fun registerScreenCascadingLocationDropdownsResetDependentSelections() {
        val strings = BilingualStrings.get(AppLanguage.ENGLISH)
        var submitted: List<String>? = null

        composeRule.setContent {
            GuavaCareTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.ENGLISH) {
                RegisterScreen(
                    strings = strings,
                    language = AppLanguage.ENGLISH,
                    isLoading = false,
                    errorMessage = null,
                    onSubmitEmailRegistration = { name, email, password, division, district, upazila ->
                        submitted = listOf(name, email, password, division, district, upazila)
                    },
                    onNavigateToLogin = {},
                    onToggleLanguage = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("register_name_input")
            .performScrollTo()
            .performTextInput("Amina Khatun")
        composeRule.onNodeWithTag("register_email_input")
            .performScrollTo()
            .performTextInput("amina@example.com")
        composeRule.onNodeWithTag("register_password_input")
            .performScrollTo()
            .performTextInput("guava2026")
        composeRule.onNodeWithTag("register_confirm_password_input")
            .performScrollTo()
            .performTextInput("guava2026")

        // Initially district and upazila dropdowns are disabled until parent is selected
        composeRule.onNodeWithTag("dropdown_district").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("dropdown_upazila").performScrollTo().assertIsNotEnabled()

        // Select Dhaka -> Gazipur -> Sreepur
        selectLocationOption("dropdown_division", "picker_dialog_division", "Dhaka")
        selectLocationOption("dropdown_district", "picker_dialog_district", "Gazipur")
        selectLocationOption("dropdown_upazila", "picker_dialog_upazila", "Sreepur")

        // Changing Division to Barishal must reset District and Upazila
        selectLocationOption("dropdown_division", "picker_dialog_division", "Barishal")
        composeRule.onNodeWithTag("dropdown_district").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("dropdown_upazila").performScrollTo().assertIsNotEnabled()

        // Submitting without re-selecting District & Upazila must be blocked by validation
        composeRule.onNodeWithTag("register_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertNull(submitted)

        // Pick Barishal district -> Bakerganj upazila, then change district to Pirojpur (must reset upazila)
        selectLocationOption("dropdown_district", "picker_dialog_district", "Barishal")
        selectLocationOption("dropdown_upazila", "picker_dialog_upazila", "Bakerganj")
        selectLocationOption("dropdown_district", "picker_dialog_district", "Pirojpur")

        // Submitting without re-selecting Upazila must be blocked because changing District cleared Upazila
        composeRule.onNodeWithTag("register_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertNull(submitted)

        // Pick Pirojpur Sadar and submit
        selectLocationOption("dropdown_upazila", "picker_dialog_upazila", "Pirojpur Sadar")
        composeRule.onNodeWithTag("register_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf("Amina Khatun", "amina@example.com", "guava2026", "Barishal", "Pirojpur", "Pirojpur Sadar"),
            submitted
        )
    }

    @Test
    fun accountSettingsCardRendersAuthenticatedProfileAndLocation() {
        val strings = BilingualStrings.get(AppLanguage.ENGLISH)
        val profile = UserProfile(
            id = "uid-123",
            userId = "uid-123",
            fullName = "Sadia Akter",
            division = "Dhaka",
            district = "Gazipur",
            upazila = "Sreepur",
            email = "sadia@example.com",
            profileCompleted = true
        )

        composeRule.setContent {
            GuavaCareTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.ENGLISH) {
                AccountSettingsCard(
                    strings = strings,
                    language = AppLanguage.ENGLISH,
                    authState = AuthUiState.Authenticated(profile),
                    remainingGuestScans = 3,
                    cloudSyncStatus = CloudSyncStatus.SYNCED,
                    isUpdatingProfile = false,
                    onOpenLogin = {},
                    onOpenRegister = {},
                    onUpdateProfile = { _, _, _, _ -> },
                    onSignOut = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_account_card").assertIsDisplayed()
        composeRule.onNodeWithText("Sadia Akter").assertIsDisplayed()
        composeRule.onNodeWithText("Sreepur, Gazipur, Dhaka").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_edit_profile_button").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_sign_out_button").assertIsDisplayed()
    }

    @Test
    fun loginScreenForgotPasswordDialogValidatesAndSubmitsResetEmail() {
        val strings = BilingualStrings.get(AppLanguage.ENGLISH)
        var resetEmailRequested: String? = null

        composeRule.setContent {
            GuavaCareTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.ENGLISH) {
                LoginScreen(
                    strings = strings,
                    language = AppLanguage.ENGLISH,
                    gateReason = AuthGateReason.OPTIONAL,
                    isLoading = false,
                    errorMessage = null,
                    onEmailSignInClick = { _, _ -> },
                    onGoogleSignInClick = {},
                    onNavigateToRegister = {},
                    onToggleLanguage = {},
                    onBack = {},
                    onSendPasswordResetEmail = { email -> resetEmailRequested = email }
                )
            }
        }

        composeRule.onNodeWithTag("login_email_input")
            .performScrollTo()
            .performTextInput("grower@example.com")
        composeRule.onNodeWithTag("login_forgot_password_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("forgot_password_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("forgot_password_email_input")
            .assertTextContains("grower@example.com")
        composeRule.onNodeWithTag("forgot_password_submit_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertEquals("grower@example.com", resetEmailRequested)

        // Invalid email must be rejected by dialog validation
        resetEmailRequested = null
        composeRule.onNodeWithTag("forgot_password_email_input")
            .performScrollTo()
            .performTextClearance()
        composeRule.onNodeWithTag("forgot_password_email_input")
            .performTextInput("invalid-email")
        composeRule.onNodeWithTag("forgot_password_submit_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertNull(resetEmailRequested)
    }

    @Test
    fun profileScreenDisplaysAndUpdatesSavedNameAndLocation() {
        val strings = BilingualStrings.get(AppLanguage.ENGLISH)
        val profile = UserProfile(
            id = "uid-456",
            userId = "uid-456",
            fullName = "Md. Rahim Uddin",
            division = "Dhaka",
            district = "Gazipur",
            upazila = "Sreepur",
            email = "rahim@example.com",
            profileCompleted = true
        )
        var updated: List<String>? = null

        composeRule.setContent {
            GuavaCareTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.ENGLISH) {
                ProfileScreen(
                    strings = strings,
                    language = AppLanguage.ENGLISH,
                    authState = AuthUiState.Authenticated(profile),
                    insights = OrchardInsightsSummary(totalScans = 5, healthyCount = 3, affectedCount = 2),
                    cloudSyncStatus = CloudSyncStatus.SYNCED,
                    isSavingProfile = false,
                    isSendingPasswordReset = false,
                    passwordResetError = null,
                    passwordResetSent = false,
                    onUpdateProfile = { name, div, dist, upa ->
                        updated = listOf(name, div, dist, upa)
                    },
                    onSendPasswordResetEmail = {},
                    onClearPasswordResetState = {},
                    onSignOut = {},
                    onOpenLogin = {},
                    onOpenRegister = {},
                    onToggleLanguage = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("profile_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_hero_name").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_name_input")
            .performScrollTo()
            .performTextClearance()
        composeRule.onNodeWithTag("profile_name_input")
            .performScrollTo()
            .performTextInput("Md. Rahim Mia")

        selectLocationOption("dropdown_division", "picker_dialog_division", "Barishal")
        selectLocationOption("dropdown_district", "picker_dialog_district", "Pirojpur")
        selectLocationOption("dropdown_upazila", "picker_dialog_upazila", "Pirojpur Sadar")

        composeRule.onNodeWithTag("profile_save_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(
            listOf("Md. Rahim Mia", "Barishal", "Pirojpur", "Pirojpur Sadar"),
            updated
        )
    }

    @Test
    fun historyAndProfileTabsShowOnlyWhenAuthenticatedAndInsightsIsRemovedFromBottomNav() {
        val guestTabs = visibleTopLevelDestinations(isAuthenticated = false)
        assertEquals(3, guestTabs.size)
        assertEquals(
            listOf(
                GuavaCareRoutes.ROUTE_HOME,
                GuavaCareRoutes.ROUTE_SCANNER,
                GuavaCareRoutes.ROUTE_ABOUT
            ),
            guestTabs.map { it.route }
        )

        val signedInTabs = visibleTopLevelDestinations(isAuthenticated = true)
        assertEquals(5, signedInTabs.size)
        assertEquals(
            listOf(
                GuavaCareRoutes.ROUTE_HOME,
                GuavaCareRoutes.ROUTE_SCANNER,
                GuavaCareRoutes.ROUTE_HISTORY,
                GuavaCareRoutes.ROUTE_PROFILE,
                GuavaCareRoutes.ROUTE_ABOUT
            ),
            signedInTabs.map { it.route }
        )
        assertEquals("nav_profile", signedInTabs[3].testTag)
        assertFalse(signedInTabs.any { it.route == GuavaCareRoutes.ROUTE_INSIGHTS })
    }

    @Test
    fun homeScreenHasSettingsOnlyTopBarAndClickableInsightsCardAboveRecentScans() {
        val strings = BilingualStrings.get(AppLanguage.ENGLISH)
        val profile = UserProfile(
            id = "uid-789",
            userId = "uid-789",
            fullName = "Nusrat Jahan",
            division = "Dhaka",
            district = "Gazipur",
            upazila = "Sreepur",
            email = "nusrat@example.com",
            profileCompleted = true
        )
        var loginClicked = false
        var insightsClicked = false
        val authState = androidx.compose.runtime.mutableStateOf<AuthUiState>(AuthUiState.Guest)

        composeRule.setContent {
            GuavaCareTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.ENGLISH) {
                HomeScreen(
                    strings = strings,
                    language = AppLanguage.ENGLISH,
                    devMockModeEnabled = false,
                    scans = emptyList(),
                    insights = OrchardInsightsSummary(totalScans = 4, healthyCount = 3, affectedCount = 1),
                    tips = emptyList(),
                    onQuickScanClick = {},
                    onGalleryUploadClick = {},
                    onOpenScan = {},
                    onViewAllHistory = {},
                    onOpenSettings = {},
                    onToggleLanguage = {},
                    authState = authState.value,
                    onOpenLogin = { loginClicked = true },
                    onOpenInsights = { insightsClicked = true }
                )
            }
        }

        // Top bar keeps only Settings icon (no language toggle or profile icon on Home)
        composeRule.onNodeWithTag("open_settings_button").assertIsDisplayed()
        composeRule.onNodeWithTag("home_language_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("home_profile_button").assertDoesNotExist()

        // Guest: History & Insights summary hidden; unlock prompt shown
        composeRule.onNodeWithTag("home_hero_card").assertIsDisplayed()
        composeRule.onNodeWithTag("home_orchard_summary_card").assertDoesNotExist()
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("home_login_unlock_card"))
        composeRule.onNodeWithTag("home_login_unlock_card").assertIsDisplayed()
        composeRule.onNodeWithTag("home_unlock_sign_in_button").performClick()
        composeRule.waitForIdle()
        assertTrue(loginClicked)

        // Authenticated: Orchard Insights card with CTA shown above Recent Scans; no duplicate small Insights tile
        authState.value = AuthUiState.Authenticated(profile)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_language_toggle").assertDoesNotExist()
        composeRule.onNodeWithTag("home_profile_button").assertDoesNotExist()
        composeRule.onNodeWithTag("home_open_insights_button").assertDoesNotExist()
        composeRule.onNodeWithTag("home_login_unlock_card").assertDoesNotExist()
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("home_orchard_summary_card"))
        composeRule.onNodeWithTag("home_insights_cta_row", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("home_orchard_summary_card")
            .assertIsDisplayed()
            .assertTextContains(strings.homeViewFullInsightsAction)
            .performClick()
        composeRule.waitForIdle()
        assertTrue(insightsClicked)
    }

    @Test
    fun insightsScreenShowsBackButtonWhenOpenedFromHomeCard() {
        val strings = BilingualStrings.get(AppLanguage.ENGLISH)
        var backClicked = false

        composeRule.setContent {
            GuavaCareTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.ENGLISH) {
                InsightsScreen(
                    strings = strings,
                    language = AppLanguage.ENGLISH,
                    insights = OrchardInsightsSummary(totalScans = 3, healthyCount = 2, affectedCount = 1),
                    onStartScan = {},
                    onBack = { backClicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("insights_back_button")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
        assertTrue(backClicked)
    }

    @Test
    fun settingsScreenOmitsGrowerAccountCard() {
        val strings = BilingualStrings.get(AppLanguage.ENGLISH)
        val modelHealth = com.guavacare.ai.ai.inference.ModelHealthStatus(
            isProductionReady = true,
            modelVersion = "1.0",
            multiclassStrategy = "OvO",
            tflitePresent = true,
            tfliteSource = "bundled",
            hasImportedTflite = false,
            classifierWeightsReady = true,
            missingAssets = emptyList(),
            checks = emptyList()
        )

        composeRule.setContent {
            GuavaCareTheme(themeMode = ThemeMode.LIGHT, language = AppLanguage.ENGLISH) {
                com.guavacare.ai.presentation.screens.settings.SettingsScreen(
                    strings = strings,
                    preferences = com.guavacare.ai.domain.model.UserPreferences(),
                    modelHealth = modelHealth,
                    coldInitMs = 45L,
                    lastLatencyMs = 28L,
                    storageBytes = 2048L,
                    onLanguageSelected = {},
                    onThemeSelected = {},
                    onStrictQualityToggle = {},
                    onLeafGuideToggle = {},
                    onAutoSaveToggle = {},
                    onDevMockModeToggle = {},
                    onClearAllScans = {},
                    onOpenResearchInfo = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_lazy_column").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_account_card").assertDoesNotExist()
    }

    @Test
    fun googleAuthHelperUnwrapsNestedContextWrapperToActivity() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        assertNull(appContext.findActivity())

        val activity = org.robolectric.Robolectric.buildActivity(Activity::class.java).create().get()
        val wrappedOnce = ContextWrapper(activity)
        val wrappedTwice = ContextWrapper(wrappedOnce)

        assertEquals(activity, activity.findActivity())
        assertEquals(activity, wrappedOnce.findActivity())
        assertEquals(activity, wrappedTwice.findActivity())
    }

    @Test
    fun googleAuthHelperClassifiesPlayServicesConfigAndWebCancelErrorsAccurately() {
        // Genuine user cancellation should NOT trigger fallback
        assertFalse(GoogleAuthHelper.isPlayServicesConfigOrBlockedError(null))
        assertFalse(GoogleAuthHelper.isPlayServicesConfigOrBlockedError(""))
        assertFalse(GoogleAuthHelper.isPlayServicesConfigOrBlockedError("activity is cancelled by the user."))

        // SHA-1 / Play Services / cooldown errors SHOULD trigger fallback
        assertTrue(
            GoogleAuthHelper.isPlayServicesConfigOrBlockedError(
                "[28436] Caller has been temporarily blocked due to too many canceled sign-in prompts."
            )
        )
        assertTrue(GoogleAuthHelper.isPlayServicesConfigOrBlockedError("[16] Account reauth failed."))
        assertTrue(GoogleAuthHelper.isPlayServicesConfigOrBlockedError("Cannot find a matching credential."))
        assertTrue(GoogleAuthHelper.isPlayServicesConfigOrBlockedError("Developer console is not set up correctly."))

        // Web OAuth cancellation detection
        assertTrue(GoogleAuthHelper.isWebAuthCancelled(RuntimeException("The web operation was canceled by the user.")))
        assertFalse(GoogleAuthHelper.isWebAuthCancelled(RuntimeException("A network error occurred.")))
    }
}
