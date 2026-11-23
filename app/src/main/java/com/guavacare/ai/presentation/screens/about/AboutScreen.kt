package com.guavacare.ai.presentation.screens.about

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.BuildConfig
import com.guavacare.ai.core.common.AppStrings
import com.guavacare.ai.core.constants.UpdateConstants
import com.guavacare.ai.domain.model.AdvisoryArticle
import com.guavacare.ai.domain.model.AppLanguage
import com.guavacare.ai.domain.model.AppUpdateState
import com.guavacare.ai.domain.model.GuavaLeafClass
import com.guavacare.ai.presentation.components.AdvisoryWarningBanner
import com.guavacare.ai.presentation.theme.LocalIsDarkTheme

@Composable
fun AboutScreen(
    strings: AppStrings,
    language: AppLanguage,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenResearchInfo: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    updateState: AppUpdateState = AppUpdateState.Idle,
    onCheckForUpdates: () -> Unit = {},
    advisories: List<AdvisoryArticle> = emptyList(),
    onShareApp: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    var expandedClass by remember { mutableStateOf<GuavaLeafClass?>(null) }
    val advisoryByClass = remember(advisories) { advisories.associateBy { it.leafClass } }

    val triggerShareApp: () -> Unit = {
        if (onShareApp != null) {
            onShareApp()
        } else {
            val shareText = String.format(
                strings.aboutShareMessageBody,
                BuildConfig.VERSION_NAME,
                UpdateConstants.APP_SHARE_DOWNLOAD_URL
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, strings.aboutShareChooserTitle)
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            val chooser = Intent.createChooser(sendIntent, strings.aboutShareChooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(chooser) }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .testTag("about_lazy_column"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = strings.aboutTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = strings.aboutSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Editorial Botanical Hero Banner
            item {
                AboutHeroBannerCard(strings = strings)
            }

            // 3-Column At-a-Glance Highlights
            item {
                AboutHighlightsStrip(strings = strings)
            }

            // Overview + 3-Step "How It Works" Pipeline
            item {
                AboutSectionCard(title = strings.aboutOverviewTitle) {
                    Text(
                        text = strings.aboutOverviewBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(
                        thickness = 0.6.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                    )

                    Text(
                        text = strings.aboutHowItWorksTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Column(modifier = Modifier.fillMaxWidth()) {
                        AboutWorkflowStepRow(
                            stepNumber = 1,
                            icon = Icons.Filled.CameraAlt,
                            title = strings.aboutStep1Title,
                            body = strings.aboutStep1Body,
                            isLast = false
                        )
                        AboutWorkflowStepRow(
                            stepNumber = 2,
                            icon = Icons.Filled.Memory,
                            title = strings.aboutStep2Title,
                            body = strings.aboutStep2Body,
                            isLast = false
                        )
                        AboutWorkflowStepRow(
                            stepNumber = 3,
                            icon = Icons.Filled.HealthAndSafety,
                            title = strings.aboutStep3Title,
                            body = strings.aboutStep3Body,
                            isLast = true
                        )
                    }
                }
            }

            // Interactive Expandable Field Guide (5 Leaf Conditions)
            item {
                AboutSectionCard(
                    title = strings.aboutClassesTitle,
                    subtitle = if (advisories.isNotEmpty()) strings.aboutTapToInspectHint else null
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GuavaLeafClass.entries.forEachIndexed { index, leafClass ->
                            val isExpanded = expandedClass == leafClass
                            ExpandableLeafConditionItem(
                                leafClass = leafClass,
                                advisory = advisoryByClass[leafClass],
                                language = language,
                                strings = strings,
                                isDark = isDark,
                                expanded = isExpanded,
                                onToggle = {
                                    expandedClass = if (isExpanded) null else leafClass
                                }
                            )
                            if (index < GuavaLeafClass.entries.lastIndex) {
                                HorizontalDivider(
                                    thickness = 0.6.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                                )
                            }
                        }
                    }
                }
            }

            // Photography Tips for Best Scan Results
            item {
                AboutSectionCard(title = strings.aboutHowToTitle) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AboutTipRow(
                            icon = Icons.Filled.WbSunny,
                            text = strings.aboutTipDaylight
                        )
                        AboutTipRow(
                            icon = Icons.Filled.CenterFocusStrong,
                            text = strings.aboutTipSingleLeaf
                        )
                        AboutTipRow(
                            icon = Icons.Filled.Eco,
                            text = strings.aboutTipLeafOnly
                        )
                    }
                }
            }

            // Developer Information Card
            item {
                AboutDeveloperCard(
                    strings = strings,
                    onEmailClick = {
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = "mailto:${strings.aboutDeveloperEmailValue}".toUri()
                            putExtra(
                                Intent.EXTRA_SUBJECT,
                                "${strings.appTitle} Feedback (v${BuildConfig.VERSION_NAME})"
                            )
                        }
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(emailIntent, strings.aboutContactDeveloper)
                            )
                        }
                    },
                    onGithubClick = {
                        val githubIntent = Intent(
                            Intent.ACTION_VIEW,
                            strings.aboutDeveloperGithubUrl.toUri()
                        )
                        runCatching { context.startActivity(githubIntent) }
                    },
                    onPortfolioClick = {
                        val portfolioIntent = Intent(
                            Intent.ACTION_VIEW,
                            strings.aboutDeveloperPortfolioUrl.toUri()
                        )
                        runCatching { context.startActivity(portfolioIntent) }
                    }
                )
            }

            // Quick Info & Grouped Material 3 Actions
            item {
                AboutSectionCard(title = strings.aboutQuickLinksTitle) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AboutMetaRow(
                                label = strings.aboutVersionLabel,
                                value = "v${BuildConfig.VERSION_NAME}"
                            )
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                            )
                            AboutMetaRow(
                                label = strings.aboutProcessingLabel,
                                value = strings.aboutProcessingValue
                            )
                        }
                    }

                    val updateStatusText = when (updateState) {
                        is AppUpdateState.Checking -> strings.checkingForUpdates
                        is AppUpdateState.UpToDate -> strings.upToDateStatus
                        is AppUpdateState.Error -> updateState.message
                        else -> null
                    }
                    if (updateStatusText != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (updateState is AppUpdateState.Error) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            }
                        ) {
                            Text(
                                text = updateStatusText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (updateState is AppUpdateState.Error) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("about_update_status_text")
                            )
                        }
                    }

                    val isChecking = updateState is AppUpdateState.Checking
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        AboutActionRow(
                            icon = Icons.Filled.Share,
                            title = strings.aboutShareAppTitle,
                            subtitle = strings.aboutShareAppSubtitle,
                            onClick = triggerShareApp,
                            testTagName = "about_share_app_button",
                            iconTint = MaterialTheme.colorScheme.primary,
                            iconContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                        )
                        HorizontalDivider(
                            thickness = 0.6.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                        AboutActionRow(
                            icon = Icons.Filled.SystemUpdate,
                            title = if (isChecking) strings.checkingForUpdates else strings.checkForUpdates,
                            subtitle = strings.aboutUpdatesSubtitle,
                            onClick = onCheckForUpdates,
                            testTagName = "about_check_updates_button",
                            enabled = !isChecking,
                            isLoading = isChecking
                        )
                        HorizontalDivider(
                            thickness = 0.6.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                        AboutActionRow(
                            icon = Icons.Filled.Policy,
                            title = strings.privacyPolicy,
                            subtitle = strings.aboutPrivacySubtitle,
                            onClick = onOpenPrivacyPolicy,
                            testTagName = "about_privacy_policy_button"
                        )
                        HorizontalDivider(
                            thickness = 0.6.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                        AboutActionRow(
                            icon = Icons.Filled.School,
                            title = strings.aboutResearch,
                            subtitle = strings.aboutResearchSubtitle,
                            onClick = onOpenResearchInfo,
                            testTagName = "about_research_button",
                            iconTint = MaterialTheme.colorScheme.secondary,
                            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.78f)
                        )
                    }
                }
            }

            // Screening Notice
            item {
                AdvisoryWarningBanner(text = strings.aiScreeningNotice)
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
