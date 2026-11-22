package com.guavacare.ai.presentation.screens.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.guavacare.ai.core.common.AppStrings
import com.guavacare.ai.domain.model.AppLanguage
import com.guavacare.ai.domain.model.DailyScanActivity
import com.guavacare.ai.domain.model.GuavaLeafClass
import com.guavacare.ai.domain.model.OrchardInsightsSummary
import com.guavacare.ai.presentation.components.AdvisoryWarningBanner
import com.guavacare.ai.presentation.components.ClassStatusBadge
import com.guavacare.ai.presentation.components.EmptyStateCard
import com.guavacare.ai.presentation.components.MetricStatCard
import com.guavacare.ai.presentation.theme.JetBrainsMonoFamily
import com.guavacare.ai.presentation.theme.LocalIsDarkTheme
import com.guavacare.ai.presentation.theme.leafClassPalette

@Composable
fun InsightsScreen(
    strings: AppStrings,
    language: AppLanguage,
    insights: OrchardInsightsSummary,
    onStartScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = strings.insightsTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Key Stat Counters
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        label = strings.totalScans,
                        value = insights.totalScans.toString(),
                        icon = Icons.Filled.QrCodeScanner,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        label = strings.healthyLeaves,
                        value = insights.healthyCount.toString(),
                        icon = Icons.Filled.CheckCircle,
                        accentColor = leafClassPalette(GuavaLeafClass.HEALTHY, isDark).accent,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        label = strings.affectedLeaves,
                        value = insights.affectedCount.toString(),
                        icon = Icons.Filled.HealthAndSafety,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Most Frequently Detected Condition
            insights.mostFrequentCondition?.let { topCondition ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = strings.mostFrequentCondition,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                ClassStatusBadge(
                                    leafClass = topCondition,
                                    language = language
                                )
                            }
                            Text(
                                text = "${insights.classDistribution[topCondition] ?: 0}/${insights.totalScans}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Render Charts ONLY when sufficient data exists (>= 2 scans)
            if (insights.totalScans < 2) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.BarChart,
                        title = strings.insightsTitle,
                        body = strings.insufficientDataCharts,
                        actionLabel = strings.startFirstScan,
                        onAction = onStartScan
                    )
                }
            } else {
                // Category Distribution Breakdown
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = strings.categoryDistribution,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val total = insights.totalScans.coerceAtLeast(1)
                            GuavaLeafClass.entries.forEach { leafClass ->
                                val count = insights.classDistribution[leafClass] ?: 0
                                val ratio = count.toFloat() / total.toFloat()
                                val pct = (ratio * 100f).toInt()
                                val palette = leafClassPalette(leafClass, isDark)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = leafClass.localizedName(language),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "$count ($pct%)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { ratio },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(999.dp)),
                                        color = palette.accent,
                                        trackColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                }
                            }
                        }
                    }
                }

                // Weekly Scanning Activity Chart
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = strings.weeklyActivity,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            WeeklyActivityBarChart(activity = insights.weeklyActivity)
                        }
                    }
                }
            }

            // Mandatory Sampling vs Prevalence Disclaimer
            item {
                AdvisoryWarningBanner(
                    text = "${strings.insightsDisclaimerTitle}: ${strings.insightsDisclaimerBody}"
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun WeeklyActivityBarChart(
    activity: List<DailyScanActivity>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val maxCount = (activity.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            if (activity.isEmpty()) return@Canvas
            val slotWidth = size.width / activity.size
            val barWidth = (slotWidth * 0.46f).coerceAtMost(32.dp.toPx())
            val radius = CornerRadius(8.dp.toPx(), 8.dp.toPx())

            activity.forEachIndexed { idx, day ->
                val centerX = slotWidth * idx + slotWidth / 2f
                val left = centerX - barWidth / 2f
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = radius
                )
                if (day.count > 0) {
                    val barHeight = (day.count.toFloat() / maxCount.toFloat()) * size.height
                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, barHeight.coerceAtLeast(10.dp.toPx())),
                        cornerRadius = radius
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            activity.forEach { day ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = day.count.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = day.dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
