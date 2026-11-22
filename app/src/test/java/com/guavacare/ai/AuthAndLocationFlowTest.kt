package com.guavacare.ai

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.guavacare.ai.core.common.BilingualStrings
import com.guavacare.ai.core.data.BangladeshLocations
import com.guavacare.ai.data.local.PreferencesRepository
import com.guavacare.ai.domain.model.AppLanguage
import com.guavacare.ai.domain.model.AuthGateReason
import com.guavacare.ai.domain.model.AuthUiState
import com.guavacare.ai.domain.model.CloudSyncStatus
import com.guavacare.ai.domain.model.GUEST_FREE_SCAN_LIMIT
import com.guavacare.ai.domain.model.OrchardInsightsSummary
import com.guavacare.ai.domain.model.ThemeMode
import com.guavacare.ai.domain.model.UserProfile
import com.guavacare.ai.presentation.screens.auth.LoginScreen
import com.guavacare.ai.presentation.screens.auth.RegisterScreen
import com.guavacare.ai.presentation.screens.profile.ProfileScreen
import com.guavacare.ai.presentation.screens.settings.AccountSettingsCard
import com.guavacare.ai.presentation.theme.GuavaCareTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        composeRule.onNodeWithTag("login_limit_reached_banner").assertIsDisplayed()

        // Test Email/Password login flow
        composeRule.onNodeWithTag("login_email_input").performTextInput("farmer@example.com")
        composeRule.onNodeWithTag("login_password_input").performTextInput("secret123")
        composeRule.onNodeWithTag("login_email_submit_button").performClick()
        assertEquals("farmer@example.com" to "secret123", emailLoginSubmitted)

        // Test Direct Google Sign-In button ("Sign in with Google")
        composeRule.onNodeWithText("Sign in with Google").assertIsDisplayed()
        composeRule.onNodeWithTag("login_google_button").performClick()
        assertTrue(googleClicked)

        // Test navigation to separate Register page
        composeRule.onNodeWithTag("go_to_register_button").performClick()
        assertTrue(registerClicked)
    }

    @Test
    fun registerScreenCollectsEmailPasswordAndCascadingDivisionDistrictUpazilaDropdowns() {
        val strings = BilingualStrings.get(AppLanguage.ENGLISH)
        var submitted: List<String>? = null
        var googleClicked = false

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
                    onGoogleSignInClick = { googleClicked = true },
                    onNavigateToLogin = {},
                    onToggleLanguage = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("register_screen").assertIsDisplayed()

        // Enter Full Name, Email, Password, Confirm Password
        composeRule.onNodeWithTag("register_name_input").performTextInput("Md. Karim Mia")
        composeRule.onNodeWithTag("register_email_input").performTextInput("karim@example.com")
        composeRule.onNodeWithTag("register_password_input").performTextInput("guava2026")
        composeRule.onNodeWithTag("register_confirm_password_input").performTextInput("guava2026")

        // 1. Select Division: Barishal
        composeRule.onNodeWithTag("dropdown_division").performClick()
        composeRule.onNodeWithTag("picker_dialog_division").assertIsDisplayed()
        composeRule.onNodeWithTag("location_option_Barishal").performClick()

        // 2. Select District: Pirojpur
        composeRule.onNodeWithTag("dropdown_district").performClick()
        composeRule.onNodeWithTag("picker_dialog_district").assertIsDisplayed()
        composeRule.onNodeWithTag("location_option_Pirojpur").performClick()

        // 3. Select Upazila: Pirojpur Sadar
        composeRule.onNodeWithTag("dropdown_upazila").performClick()
        composeRule.onNodeWithTag("picker_dialog_upazila").assertIsDisplayed()
        composeRule.onNodeWithTag("location_option_Pirojpur Sadar").performClick()

        // Submit Email/Password Registration
        composeRule.onNodeWithTag("register_submit_button").performClick()

        assertEquals(
            listOf("Md. Karim Mia", "karim@example.com", "guava2026", "Barishal", "Pirojpur", "Pirojpur Sadar"),
            submitted
        )

        // Verify direct Google Sign-In shortcut also exists on RegisterScreen
        composeRule.onNodeWithTag("register_google_button").performClick()
        assertTrue(googleClicked)
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

        composeRule.onNodeWithTag("login_email_input").performTextInput("grower@example.com")
        composeRule.onNodeWithTag("login_forgot_password_button").performClick()
        composeRule.onNodeWithTag("forgot_password_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("forgot_password_submit_button").performClick()
        assertEquals("grower@example.com", resetEmailRequested)
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
        composeRule.onNodeWithTag("profile_name_input").performTextClearance()
        composeRule.onNodeWithTag("profile_name_input").performTextInput("Md. Rahim Mia")

        composeRule.onNodeWithTag("dropdown_division").performClick()
        composeRule.onNodeWithTag("location_option_Barishal").performClick()
        composeRule.onNodeWithTag("dropdown_district").performClick()
        composeRule.onNodeWithTag("location_option_Pirojpur").performClick()
        composeRule.onNodeWithTag("dropdown_upazila").performClick()
        composeRule.onNodeWithTag("location_option_Pirojpur Sadar").performClick()

        composeRule.onNodeWithTag("profile_save_button").performClick()
        assertEquals(
            listOf("Md. Rahim Mia", "Barishal", "Pirojpur", "Pirojpur Sadar"),
            updated
        )
    }
}
