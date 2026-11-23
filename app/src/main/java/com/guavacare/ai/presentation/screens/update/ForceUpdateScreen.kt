package com.guavacare.ai.presentation.screens.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.BuildConfig
import com.example.R
import com.guavacare.ai.core.common.AppStrings
import com.guavacare.ai.domain.model.AppLanguage
import com.guavacare.ai.domain.model.AppReleaseInfo
import com.guavacare.ai.domain.model.AppUpdateState
import com.guavacare.ai.domain.model.ReleaseNotesFormatter
import java.util.Locale

@Composable
fun ForceUpdateScreen(
    strings: AppStrings,
    language: AppLanguage,
    updateState: AppUpdateState,
    onToggleLanguage: () -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstallApk: () -> Unit,
    onOpenInstallPermissionSettings: () -> Unit,
    onCheckInstallPermission: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Prevent hardware back from dismissing mandatory update gate
    BackHandler(enabled = true) {}

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (updateState is AppUpdateState.PermissionRequired) {
            onCheckInstallPermission()
        }
    }

    var autoLaunchedApkPath by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(updateState) {
        if (updateState is AppUpdateState.ReadyToInstall) {
            val path = updateState.apkFile.absolutePath
            if (autoLaunchedApkPath != path) {
                autoLaunchedApkPath = path
                onInstallApk()
            }
        }
    }

    val release: AppReleaseInfo? = when (updateState) {
        is AppUpdateState.UpdateAvailable -> updateState.release
        is AppUpdateState.Downloading -> updateState.release
        is AppUpdateState.ReadyToInstall -> updateState.release
        is AppUpdateState.PermissionRequired -> updateState.release
        is AppUpdateState.Error -> updateState.release
        else -> null
    }

    val currentVersionText = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    val remoteVersionText = release?.let { rel ->
        val codePart = rel.versionCode?.let { " ($it)" } ?: ""
        "v${rel.versionName}$codePart"
    } ?: "v${BuildConfig.VERSION_NAME}"

    val rawReleaseNotes = remember(release, language, strings) {
        if (release == null) {
            strings.defaultReleaseNotes
        } else if (language == AppLanguage.BANGLA) {
            release.releaseNotesBn.ifBlank { release.releaseNotesEn }.ifBlank { strings.defaultReleaseNotes }
        } else {
            release.releaseNotesEn.ifBlank { release.releaseNotesBn }.ifBlank { strings.defaultReleaseNotes }
        }
    }

    val highlights = remember(rawReleaseNotes, strings) {
        ReleaseNotesFormatter.parseHighlights(rawReleaseNotes)
            .ifEmpty { ReleaseNotesFormatter.parseHighlights(strings.defaultReleaseNotes) }
    }

    val sizeMb = release?.fileSizeBytes?.takeIf { it > 0L }?.let { bytes ->
        "${"%.1f".format(Locale.US, bytes / (1024.0 * 1024.0))} MB"
    }
    val publishedDate = release?.publishedAt?.takeIf { it.length >= 10 }?.take(10)

    val backdropBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background
        )
    )

    val heroBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        )
    )

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("force_update_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backdropBrush)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Floating Modal Update Popup Sheet
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        0.8.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    ),
                    shadowElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Compact Top Header: Brand + Update Ready Pill + Language Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_guava_doctor_icon_1795288631112),
                                    contentDescription = strings.appTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(
                                        text = strings.appTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = strings.updateReadyBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Surface(
                                onClick = onToggleLanguage,
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    0.6.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("force_update_lang_toggle")
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
                                        text = if (language == AppLanguage.ENGLISH) "বাংলা" else "EN",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Polished Hero Upgrade Banner
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            border = BorderStroke(
                                0.8.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(heroBrush)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.SystemUpdate,
                                                contentDescription = strings.updateAvailableTitle,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = strings.updateAvailableTitle,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                letterSpacing = (-0.2).sp
                                            ),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = strings.forceUpdateSubtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                            lineHeight = 18.sp
                                        )
                                    }
                                }

                                UpdateVersionTransitionBar(
                                    currentLabel = strings.currentVersionLabel,
                                    currentVersionText = currentVersionText,
                                    newLabel = strings.newVersionLabel,
                                    newVersionText = remoteVersionText
                                )

                                UpdateMetadataChipsRow(
                                    sizeMb = sizeMb,
                                    publishedDate = publishedDate,
                                    verifiedLabel = strings.updateVerifiedApkLabel
                                )
                            }
                        }

                        // Structured, Clean "What's New" Highlights List
                        UpdateHighlightsCard(
                            title = strings.whatsNewTitle,
                            highlights = highlights,
                            fallbackText = strings.defaultReleaseNotes
                        )

                        HorizontalDivider(
                            thickness = 0.6.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )

                        // Dynamic Action Footer (Update / Progress / Permission / Install / Retry)
                        UpdateActionFooter(
                            strings = strings,
                            updateState = updateState,
                            onStartDownload = onStartDownload,
                            onCancelDownload = onCancelDownload,
                            onInstallApk = onInstallApk,
                            onOpenInstallPermissionSettings = onOpenInstallPermissionSettings,
                            onRetry = onRetry
                        )
                    }
                }
            }
        }
    }
}
