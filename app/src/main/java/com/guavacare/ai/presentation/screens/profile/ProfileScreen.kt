package com.guavacare.ai.presentation.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.guavacare.ai.core.common.AppStrings
import com.guavacare.ai.core.data.BangladeshLocations
import com.guavacare.ai.domain.model.AppLanguage
import com.guavacare.ai.domain.model.AuthUiState
import com.guavacare.ai.domain.model.CloudSyncStatus
import com.guavacare.ai.domain.model.OrchardInsightsSummary
import com.guavacare.ai.domain.model.UserProfile
import com.guavacare.ai.presentation.screens.auth.BangladeshLocationDropdownFields
import com.guavacare.ai.presentation.screens.auth.ForgotPasswordDialog

@Composable
fun ProfileScreen(
    strings: AppStrings,
    language: AppLanguage,
    authState: AuthUiState,
    insights: OrchardInsightsSummary,
    cloudSyncStatus: CloudSyncStatus,
    isSavingProfile: Boolean,
    isSendingPasswordReset: Boolean,
    passwordResetError: String?,
    passwordResetSent: Boolean,
    onUpdateProfile: (fullName: String, division: String, district: String, upazila: String) -> Unit,
    onSendPasswordResetEmail: (email: String) -> Unit,
    onClearPasswordResetState: () -> Unit,
    onSignOut: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenRegister: () -> Unit,
    onToggleLanguage: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showResetPasswordDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("profile_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top bar: Back + Title + Language toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("profile_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back
                        )
                    }
                    Text(
                        text = strings.profileTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    onClick = onToggleLanguage,
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.testTag("profile_language_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Translate,
                            contentDescription = strings.languageLabel,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (language == AppLanguage.BANGLA) "English" else "বাংলা",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            when (authState) {
                is AuthUiState.Authenticated -> {
                    val profile = authState.profile
                    AuthenticatedProfileBody(
                        strings = strings,
                        language = language,
                        profile = profile,
                        insights = insights,
                        cloudSyncStatus = cloudSyncStatus,
                        isSavingProfile = isSavingProfile,
                        onUpdateProfile = onUpdateProfile,
                        onOpenResetPassword = {
                            onClearPasswordResetState()
                            showResetPasswordDialog = true
                        },
                        onOpenSignOut = { showSignOutDialog = true }
                    )

                    if (showResetPasswordDialog) {
                        ForgotPasswordDialog(
                            strings = strings,
                            initialEmail = profile.email,
                            isSending = isSendingPasswordReset,
                            errorMessage = passwordResetError,
                            isSent = passwordResetSent,
                            onSendResetEmail = onSendPasswordResetEmail,
                            onDismiss = {
                                showResetPasswordDialog = false
                                onClearPasswordResetState()
                            }
                        )
                    }
                }

                else -> {
                    GuestProfileFallbackCard(
                        strings = strings,
                        onOpenLogin = onOpenLogin,
                        onOpenRegister = onOpenRegister
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(strings.signOutConfirmTitle) },
            text = { Text(strings.signOutConfirmBody) },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_profile_sign_out_button")
                ) {
                    Text(strings.signOutAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun AuthenticatedProfileBody(
    strings: AppStrings,
    language: AppLanguage,
    profile: UserProfile,
    insights: OrchardInsightsSummary,
    cloudSyncStatus: CloudSyncStatus,
    isSavingProfile: Boolean,
    onUpdateProfile: (fullName: String, division: String, district: String, upazila: String) -> Unit,
    onOpenResetPassword: () -> Unit,
    onOpenSignOut: () -> Unit
) {
    var fullName by rememberSaveable(profile.userId) { mutableStateOf(profile.fullName) }
    var division by rememberSaveable(profile.userId) { mutableStateOf(profile.division) }
    var district by rememberSaveable(profile.userId) { mutableStateOf(profile.district) }
    var upazila by rememberSaveable(profile.userId) { mutableStateOf(profile.upazila) }

    // Keep form synchronized when Firestore snapshot updates
    LaunchedEffect(profile.fullName, profile.division, profile.district, profile.upazila) {
        fullName = profile.fullName
        division = profile.division
        district = profile.district
        upazila = profile.upazila
    }

    var nameError by remember { mutableStateOf<String?>(null) }
    var divError by remember { mutableStateOf<String?>(null) }
    var distError by remember { mutableStateOf<String?>(null) }
    var upaError by remember { mutableStateOf<String?>(null) }

    val hasChanges = fullName.trim() != profile.fullName.trim() ||
        division != profile.division ||
        district != profile.district ||
        upazila != profile.upazila

    fun validateAndSave() {
        val cleanName = fullName.trim()
        nameError = if (cleanName.isEmpty()) strings.validationNameRequired else null
        val anyLocationPicked = division.isNotBlank() || district.isNotBlank() || upazila.isNotBlank()
        divError = if (anyLocationPicked && division.isBlank()) strings.validationDivisionRequired else null
        distError = if (anyLocationPicked && district.isBlank()) strings.validationDistrictRequired else null
        upaError = if (anyLocationPicked && upazila.isBlank()) strings.validationUpazilaRequired else null

        if (nameError == null && divError == null && distError == null && upaError == null) {
            onUpdateProfile(cleanName, division, district, upazila)
        }
    }

    val savedLocationSummary = BangladeshLocations.formatLocationSummary(
        divisionEn = profile.division,
        districtEn = profile.district,
        upazilaEn = profile.upazila,
        language = language
    )

    // 1. Botanical Profile Hero Card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_hero_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF14452F),
                            Color(0xFF1F6342),
                            Color(0xFF2B7A53)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Eco,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.TopEnd)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Avatar with crisp ring
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.16f),
                        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.65f))
                    ) {
                        if (!profile.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = profile.photoUrl,
                                contentDescription = profile.fullName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                val initials = profile.fullName
                                    .trim()
                                    .split(Regex("\\s+"))
                                    .take(2)
                                    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                                    .joinToString("")
                                if (initials.isNotEmpty()) {
                                    Text(
                                        text = initials,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Person,
                                        contentDescription = profile.fullName,
                                        tint = Color.White,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = profile.fullName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("profile_hero_name")
                        )
                        if (profile.email.isNotBlank()) {
                            Text(
                                text = profile.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        ProfileCloudSyncBadge(strings = strings, status = cloudSyncStatus)
                    }
                }

                // Saved Location Summary Banner inside Hero
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_saved_location_summary"),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFFB7F4C8),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = savedLocationSummary.ifBlank { strings.locationNotSetLabel },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (savedLocationSummary.isNotBlank()) FontWeight.Medium else FontWeight.Normal,
                            color = Color.White.copy(alpha = if (savedLocationSummary.isNotBlank()) 0.96f else 0.80f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    // 2. Orchard Activity Stat Strip
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ProfileStatChip(
            label = strings.totalScans,
            value = insights.totalScans.toString(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            valueColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        ProfileStatChip(
            label = strings.healthyLeaves,
            value = insights.healthyCount.toString(),
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            valueColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        ProfileStatChip(
            label = strings.affectedLeaves,
            value = insights.affectedCount.toString(),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
            valueColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f)
        )
    }

    // 3. Editable Personal & Location Details Card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_info_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = strings.personalInfoSectionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = strings.profileSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Full Name Input
            OutlinedTextField(
                value = fullName,
                onValueChange = {
                    fullName = it
                    if (nameError != null && it.isNotBlank()) nameError = null
                },
                label = { Text(strings.registerFullNameLabel) },
                placeholder = { Text(strings.registerFullNamePlaceholder) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                isError = nameError != null,
                supportingText = nameError?.let { msg -> { Text(msg) } },
                singleLine = true,
                enabled = !isSavingProfile,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_name_input")
            )

            // Read-Only Email Field
            if (profile.email.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_email_display"),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Email,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = strings.emailReadOnlyHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = profile.email,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Cascading Division -> District -> Upazila Dropdowns
            BangladeshLocationDropdownFields(
                strings = strings,
                language = language,
                selectedDivision = division,
                selectedDistrict = district,
                selectedUpazila = upazila,
                onDivisionSelected = {
                    division = it
                    if (it.isNotBlank()) divError = null
                },
                onDistrictSelected = {
                    district = it
                    if (it.isNotBlank()) distError = null
                },
                onUpazilaSelected = {
                    upazila = it
                    if (it.isNotBlank()) upaError = null
                },
                divisionError = divError,
                districtError = distError,
                upazilaError = upaError,
                enabled = !isSavingProfile
            )

            // Save / Reset Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (hasChanges && !isSavingProfile) {
                    OutlinedButton(
                        onClick = {
                            fullName = profile.fullName
                            division = profile.division
                            district = profile.district
                            upazila = profile.upazila
                            nameError = null
                            divError = null
                            distError = null
                            upaError = null
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 52.dp)
                            .testTag("profile_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.discardChangesAction)
                    }
                }

                Button(
                    onClick = { validateAndSave() },
                    enabled = !isSavingProfile && hasChanges,
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 52.dp)
                        .testTag("profile_save_button")
                ) {
                    if (isSavingProfile) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = strings.savingProfileStatus,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.saveChangesAction,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // 4. Account & Security Card (Reset Password + Sign Out)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = strings.accountSecuritySectionTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (profile.email.isNotBlank()) {
                OutlinedButton(
                    onClick = onOpenResetPassword,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("profile_reset_password_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.LockReset,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.forgotPasswordTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            OutlinedButton(
                onClick = onOpenSignOut,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("profile_sign_out_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.signOutAction,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ProfileStatChip(
    label: String,
    value: String,
    containerColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProfileCloudSyncBadge(
    strings: AppStrings,
    status: CloudSyncStatus
) {
    val (icon, label) = when (status) {
        CloudSyncStatus.SYNCED -> Icons.Filled.CloudDone to strings.cloudSyncSynced
        CloudSyncStatus.SYNCING -> Icons.Filled.CloudSync to strings.cloudSyncSyncing
        CloudSyncStatus.ERROR -> Icons.Filled.CloudOff to strings.cloudSyncError
        CloudSyncStatus.IDLE -> Icons.Filled.CloudDone to strings.cloudSyncIdle
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.18f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFFB7F4C8),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun GuestProfileFallbackCard(
    strings: AppStrings,
    onOpenLogin: () -> Unit,
    onOpenRegister: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_guest_fallback"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
            Text(
                text = strings.guestModeTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = strings.guestModeSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onOpenLogin,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.signInAction)
            }
            OutlinedButton(
                onClick = onOpenRegister,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(strings.createAccountAction)
            }
        }
    }
}
