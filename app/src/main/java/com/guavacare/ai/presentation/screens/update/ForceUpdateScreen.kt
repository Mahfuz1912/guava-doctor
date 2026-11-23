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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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

    val currentVersionText = "v${BuildConfig.VERSION_NAME}"
    val remoteVersionText = release?.let { rel ->
        "v${rel.versionName}"
    } ?: "v${BuildConfig.VERSION_NAME}"

    val rawReleaseNotes = remember(release, language, strings) {
        if (release == null) {
            strings.defaultReleaseNotes
        } else {
            ReleaseNotesFormatter.extractForLanguage(
                releaseNotesEn = release.releaseNotesEn,
                releaseNotesBn = release.releaseNotesBn,
                language = language,
                fallback = strings.defaultReleaseNotes
            )
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

    val primaryOrbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val secondaryOrbColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)
    val heroAccentRing = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)

    val backdropBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.surfaceContainerLowest,
            MaterialTheme.colorScheme.background
        )
    )

    val heroBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f)
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
                .drawBehind {
                    drawCircle(
                        color = primaryOrbColor,
                        radius = size.minDimension * 0.55f,
                        center = Offset(size.width * 0.18f, size.height * 0.12f)
                    )
                    drawCircle(
                        color = secondaryOrbColor,
                        radius = size.minDimension * 0.42f,
                        center = Offset(size.width * 0.88f, size.height * 0.84f)
                    )
                }
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Full-bleed Hero Modal Update Popup Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        0.8.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    ),
                    shadowElevation = 10.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Full-Bleed Botanical Gradient Header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(heroBrush)
                                .drawBehind {
                                    drawCircle(
                                        color = heroAccentRing,
                                        radius = size.width * 0.36f,
                                        center = Offset(size.width * 0.92f, size.height * 0.18f)
                                    )
                                    drawCircle(
                                        color = heroAccentRing,
                                        radius = size.width * 0.22f,
                                        center = Offset(size.width * 0.08f, size.height * 0.88f)
                                    )
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Subtle Modal Sheet Handle Pill
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .width(36.dp)
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.22f)
                                        )
                                )

                                // Top Identity Bar + Compact Language Toggle
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(11.dp),
                                            shadowElevation = 2.dp
                                        ) {
                                            Image(
                                                painter = painterResource(
                                                    id = R.drawable.img_guava_doctor_icon_1795288631112
                                                ),
                                                contentDescription = strings.appTitle,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(34.dp)
                                            )
                                        }
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = strings.appTitle,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primary)
                                                )
                                                Text(
                                                    text = strings.updateReadyBadge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        onClick = onToggleLanguage,
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                                        border = BorderStroke(
                                            0.6.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("force_update_lang_toggle")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Translate,
                                                contentDescription = strings.languageLabel,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(15.dp)
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

                                // Hero Headline + Layered Update Emblem
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(14.dp))
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
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                letterSpacing = (-0.3).sp
                                            ),
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = strings.forceUpdateSubtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.86f),
                                            lineHeight = 18.sp
                                        )
                                    }
                                }

                                // Segmented Version Upgrade Capsule
                                UpdateVersionCapsule(
                                    currentLabel = strings.currentVersionLabel,
                                    currentVersionText = currentVersionText,
                                    newLabel = strings.newVersionLabel,
                                    newVersionText = remoteVersionText
                                )
                            }
                        }

                        // Body Content: Metadata Strip, Structured Highlights & Animated Action Dock
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            UpdateSpecStrip(
                                sizeMb = sizeMb,
                                publishedDate = publishedDate,
                                verifiedLabel = strings.updateVerifiedApkLabel
                            )

                            UpdateWhatsNewDropdown(
                                title = strings.whatsNewTitle,
                                collapsedHint = strings.updateTapToViewChanges,
                                expandedHint = strings.updateHideChanges,
                                highlights = highlights,
                                fallbackText = strings.defaultReleaseNotes
                            )

                            HorizontalDivider(
                                thickness = 0.6.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            UpdateActionFooter(
                                strings = strings,
                                updateState = updateState,
                                sizeMb = sizeMb,
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
}
