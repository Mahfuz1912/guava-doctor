package com.guavacare.ai.presentation.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.guavacare.ai.core.common.AppStrings
import com.guavacare.ai.core.data.BangladeshLocations
import com.guavacare.ai.domain.model.AppLanguage
import com.guavacare.ai.domain.model.AuthUiState
import com.guavacare.ai.domain.model.CloudSyncStatus
import com.guavacare.ai.domain.model.GUEST_FREE_SCAN_LIMIT
import com.guavacare.ai.domain.model.UserProfile
import com.guavacare.ai.presentation.screens.auth.BangladeshLocationDropdownFields

@Composable
fun AccountSettingsCard(
    strings: AppStrings,
    language: AppLanguage,
    authState: AuthUiState,
    remainingGuestScans: Int,
    cloudSyncStatus: CloudSyncStatus,
    isUpdatingProfile: Boolean,
    onOpenLogin: () -> Unit,
    onOpenRegister: () -> Unit,
    onUpdateProfile: (fullName: String, division: String, district: String, upazila: String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProfile: (() -> Unit)? = null
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_account_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.accountSectionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (authState is AuthUiState.Authenticated) {
                    CloudSyncPill(strings = strings, status = cloudSyncStatus)
                }
            }

            when (authState) {
                is AuthUiState.Authenticated -> {
                    val profile = authState.profile
                    AuthenticatedProfileContent(
                        strings = strings,
                        language = language,
                        profile = profile,
                        onEditClick = {
                            if (onOpenProfile != null) {
                                onOpenProfile()
                            } else {
                                showEditDialog = true
                            }
                        },
                        onSignOutClick = { showSignOutDialog = true }
                    )
                }

                else -> {
                    GuestAccountContent(
                        strings = strings,
                        remainingGuestScans = remainingGuestScans,
                        onOpenLogin = onOpenLogin,
                        onOpenRegister = onOpenRegister
                    )
                }
            }
        }
    }

    if (showEditDialog && authState is AuthUiState.Authenticated) {
        EditProfileModal(
            strings = strings,
            language = language,
            profile = authState.profile,
            isSaving = isUpdatingProfile,
            onSave = { name, division, district, upazila ->
                onUpdateProfile(name, division, district, upazila)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
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
                    modifier = Modifier.testTag("confirm_sign_out_button")
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
private fun AuthenticatedProfileContent(
    strings: AppStrings,
    language: AppLanguage,
    profile: UserProfile,
    onEditClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!profile.photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = profile.photoUrl,
                contentDescription = profile.fullName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = profile.fullName,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = profile.fullName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (profile.email.isNotBlank()) {
                Text(
                    text = profile.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val locationLine = BangladeshLocations.formatLocationSummary(
                divisionEn = profile.division,
                districtEn = profile.district,
                upazilaEn = profile.upazila,
                language = language
            )
            if (locationLine.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = locationLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilledTonalButton(
            onClick = onEditClick,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .testTag("settings_edit_profile_button")
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = strings.editProfileAction,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(
            onClick = onSignOutClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .testTag("settings_sign_out_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = strings.signOutAction,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun GuestAccountContent(
    strings: AppStrings,
    remainingGuestScans: Int,
    onOpenLogin: () -> Unit,
    onOpenRegister: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (remainingGuestScans == 0) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.guestModeTitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (remainingGuestScans == 0) {
                    strings.guestFreeScansExhausted
                } else {
                    "${strings.guestFreeScansLeft}: $remainingGuestScans/$GUEST_FREE_SCAN_LIMIT"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (remainingGuestScans == 0) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
    }

    Text(
        text = strings.guestModeSubtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onOpenLogin,
            shape = RoundedCornerShape(14.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .testTag("settings_login_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = strings.signInAction,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(
            onClick = onOpenRegister,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .testTag("settings_register_button")
        ) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = strings.createAccountAction,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CloudSyncPill(
    strings: AppStrings,
    status: CloudSyncStatus
) {
    val (icon, label, containerColor, contentColor) = when (status) {
        CloudSyncStatus.SYNCED -> Quad(
            Icons.Filled.CloudDone,
            strings.cloudSyncSynced,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary
        )
        CloudSyncStatus.SYNCING -> Quad(
            Icons.Filled.CloudSync,
            strings.cloudSyncSyncing,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        CloudSyncStatus.ERROR -> Quad(
            Icons.Filled.CloudOff,
            strings.cloudSyncError,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        CloudSyncStatus.IDLE -> Quad(
            Icons.Filled.CloudDone,
            strings.cloudSyncIdle,
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun EditProfileModal(
    strings: AppStrings,
    language: AppLanguage,
    profile: UserProfile,
    isSaving: Boolean,
    onSave: (fullName: String, division: String, district: String, upazila: String) -> Unit,
    onDismiss: () -> Unit
) {
    var fullName by rememberSaveable(profile.id) { mutableStateOf(profile.fullName) }
    var division by rememberSaveable(profile.id) { mutableStateOf(profile.division) }
    var district by rememberSaveable(profile.id) { mutableStateOf(profile.district) }
    var upazila by rememberSaveable(profile.id) { mutableStateOf(profile.upazila) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var divError by remember { mutableStateOf<String?>(null) }
    var distError by remember { mutableStateOf<String?>(null) }
    var upaError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("edit_profile_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = strings.editProfileAction,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        if (nameError != null && it.isNotBlank()) nameError = null
                    },
                    label = { Text(strings.registerFullNameLabel) },
                    isError = nameError != null,
                    supportingText = nameError?.let { msg -> { Text(msg) } },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_profile_name_input")
                )

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
                    enabled = !isSaving
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(strings.cancel)
                    }
                    Button(
                        onClick = {
                            val cleanName = fullName.trim()
                            nameError = if (cleanName.isEmpty()) strings.validationNameRequired else null
                            val anyLocationPicked = division.isNotBlank() || district.isNotBlank() || upazila.isNotBlank()
                            divError = if (anyLocationPicked && division.isBlank()) strings.validationDivisionRequired else null
                            distError = if (anyLocationPicked && district.isBlank()) strings.validationDistrictRequired else null
                            upaError = if (anyLocationPicked && upazila.isBlank()) strings.validationUpazilaRequired else null
                            if (nameError == null && divError == null && distError == null && upaError == null) {
                                onSave(cleanName, division, district, upazila)
                            }
                        },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("edit_profile_save_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(strings.saveChangesAction)
                        }
                    }
                }
            }
        }
    }
}
