package com.guavacare.ai.presentation.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.guavacare.ai.ai.inference.ModelHealthStatus
import com.guavacare.ai.core.common.AppStrings
import com.guavacare.ai.domain.model.AppLanguage
import com.guavacare.ai.domain.model.AuthUiState
import com.guavacare.ai.domain.model.GUEST_FREE_SCAN_LIMIT
import com.guavacare.ai.domain.model.OrchardInsightsSummary
import com.guavacare.ai.domain.model.OrchardTip
import com.guavacare.ai.domain.model.ScanRecord
import com.guavacare.ai.presentation.components.ClassStatusBadge
import com.guavacare.ai.presentation.components.DevMockModeBanner
import com.guavacare.ai.presentation.components.formatTimestamp
import java.io.File

@Composable
fun HomeScreen(
    strings: AppStrings,
    language: AppLanguage,
    devMockModeEnabled: Boolean,
    scans: List<ScanRecord>,
    insights: OrchardInsightsSummary,
    tips: List<OrchardTip>,
    onQuickScanClick: () -> Unit,
    onGalleryUploadClick: () -> Unit,
    onOpenScan: (ScanRecord) -> Unit,
    onViewAllHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleLanguage: () -> Unit,
    modifier: Modifier = Modifier,
    modelHealth: ModelHealthStatus? = null,
    isVerifyingModel: Boolean = false,
    onRefreshModelStatus: () -> Unit = {},
    authState: AuthUiState = AuthUiState.Guest,
    remainingGuestScans: Int = GUEST_FREE_SCAN_LIMIT,
    onOpenLogin: () -> Unit = {},
    onOpenRegister: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenInsights: () -> Unit = {},
    onOpenAbout: () -> Unit = {}
) {
    val isAuthenticated = authState is AuthUiState.Authenticated

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .testTag("home_lazy_column"),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Clean Top Header Bar (Settings only on the right)
            item {
                HomeTopBar(
                    strings = strings,
                    authState = authState,
                    onOpenSettings = onOpenSettings
                )
            }

            if (devMockModeEnabled) {
                item {
                    DevMockModeBanner(text = strings.devMockBanner)
                }
            }

            // 2. Editorial Hero Card (Preserving exact orchard hero artwork)
            item {
                HomeEditorialHeroCard(
                    strings = strings,
                    authState = authState,
                    remainingGuestScans = remainingGuestScans,
                    onOpenLogin = onOpenLogin,
                    onOpenProfile = onOpenProfile
                )
            }

            // 3. Health Tools Card Section
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeSectionHeader(
                        icon = Icons.Filled.HealthAndSafety,
                        title = strings.homeHealthToolsTitle
                    )

                    ModernQuickScanCta(
                        title = strings.quickScan,
                        subtitle = strings.homeQuickScanSubtitle,
                        onClick = onQuickScanClick
                    )

                    HomeHealthToolsRow(
                        strings = strings,
                        onGalleryUploadClick = onGalleryUploadClick,
                        onOpenAbout = onOpenAbout
                    )
                }
            }

            // 4. Auth-Aware Lower Card Stack
            if (isAuthenticated) {
                item {
                    HomeOrchardSnapshotCard(
                        strings = strings,
                        insights = insights,
                        onOpenInsights = onOpenInsights
                    )
                }

                item {
                    HomeSectionHeader(
                        icon = Icons.Filled.History,
                        title = strings.recentHistory,
                        trailingAction = if (scans.isNotEmpty()) {
                            {
                                TextButton(
                                    onClick = onViewAllHistory,
                                    modifier = Modifier.testTag("home_view_all_history_button")
                                ) {
                                    Text(text = strings.viewAll)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else {
                            null
                        }
                    )
                }

                if (scans.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Eco,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = strings.noScansYetTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = strings.noScansYetBody,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(scans.take(4), key = { it.id }) { record ->
                        RecentScanRowCard(
                            record = record,
                            language = language,
                            mockLabel = strings.devMockTag,
                            onClick = { onOpenScan(record) }
                        )
                    }
                }
            } else {
                // Guest: Single clean Unlock History & Insights card + unified Quick Scan Tips card
                item {
                    HomeGuestUnlockCard(
                        strings = strings,
                        onOpenLogin = onOpenLogin,
                        onOpenRegister = onOpenRegister
                    )
                }

                item {
                    HomeFieldTipsCard(strings = strings)
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
fun RecentScanRowCard(
    record: ScanRecord,
    language: AppLanguage,
    mockLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("scan_item_card_${record.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = File(record.imagePath),
                contentDescription = record.predictedClass.localizedName(language),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ClassStatusBadge(
                    leafClass = record.predictedClass,
                    language = language,
                    isMock = record.isMockResult,
                    mockLabel = mockLabel
                )
                val meta = listOf(record.orchardName, record.plotId)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
                Text(
                    text = if (meta.isNotEmpty()) "$meta • ${formatTimestamp(record.timestamp)}" else formatTimestamp(record.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
