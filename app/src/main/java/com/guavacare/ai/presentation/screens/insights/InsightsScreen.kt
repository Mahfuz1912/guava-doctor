package com.guavacare.ai.presentation.screens.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guavacare.ai.core.common.AppStrings
import com.guavacare.ai.domain.model.AppLanguage
import com.guavacare.ai.domain.model.GuavaLeafClass
import com.guavacare.ai.domain.model.OrchardInsightsSummary
import com.guavacare.ai.domain.model.ScanRecord
import com.guavacare.ai.domain.usecase.OrchardInsightsCalculator
import com.guavacare.ai.presentation.components.AdvisoryWarningBanner
import com.guavacare.ai.presentation.components.EmptyStateCard
import com.guavacare.ai.presentation.components.MetricStatCard
import com.guavacare.ai.presentation.theme.LocalIsDarkTheme
import com.guavacare.ai.presentation.theme.leafClassPalette

private enum class InsightsRange(val daysBack: Int?, val tag: String) {
    ALL(null, "all"),
    LAST_7_DAYS(7, "7d"),
    LAST_30_DAYS(30, "30d")
}

@Composable
fun InsightsScreen(
    strings: AppStrings,
    language: AppLanguage,
    insights: OrchardInsightsSummary,
    onStartScan: () -> Unit,
    modifier: Modifier = Modifier,
    scans: List<ScanRecord> = emptyList(),
    onOpenHistoryWithClass: (GuavaLeafClass?) -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val isDark = LocalIsDarkTheme.current
    var selectedRange by remember { mutableStateOf(InsightsRange.ALL) }
    var selectedLeafClass by remember { mutableStateOf<GuavaLeafClass?>(null) }

    val calculator = remember { OrchardInsightsCalculator() }
    val filteredScans = remember(scans, selectedRange) {
        val days = selectedRange.daysBack
        if (days == null) {
            scans
        } else {
            val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
            scans.filter { it.timestamp >= cutoff }
        }
    }

    val activeInsights = remember(scans, filteredScans, insights, selectedRange) {
        if (scans.isEmpty() && selectedRange == InsightsRange.ALL) {
            insights
        } else {
            calculator.calculate(filteredScans)
        }
    }

    val avgConfidencePct = remember(filteredScans) {
        val confidences = filteredScans.mapNotNull { it.calibratedConfidence }
        if (confidences.isEmpty()) null else ((confidences.average() * 100.0).toInt()).coerceIn(0, 100)
    }

    val orchardStats = remember(filteredScans) {
        filteredScans
            .filter { it.orchardName.isNotBlank() }
            .groupBy { it.orchardName.trim() }
            .map { (name, records) ->
                val healthy = records.count { it.predictedClass == GuavaLeafClass.HEALTHY }
                OrchardGroupStat(
                    orchardName = name,
                    total = records.size,
                    healthy = healthy,
                    affected = records.size - healthy
                )
            }
            .sortedByDescending { it.total }
            .take(5)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .testTag("insights_lazy_column"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with back navigation and quick scan action
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("insights_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = strings.back
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = strings.insightsTitle,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = strings.insightsSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (activeInsights.totalScans > 0) {
                        Spacer(modifier = Modifier.width(12.dp))
                        FilledTonalButton(
                            onClick = onStartScan,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("insights_quick_scan_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strings.insightsScanNowAction,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // Time-range Filter Chips (only when scans exist)
            if (scans.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val ranges = listOf(
                            InsightsRange.ALL to strings.filterAllTime,
                            InsightsRange.LAST_7_DAYS to strings.filter7Days,
                            InsightsRange.LAST_30_DAYS to strings.filter30Days
                        )
                        ranges.forEach { (range, label) ->
                            FilterChip(
                                selected = selectedRange == range,
                                onClick = {
                                    selectedRange = range
                                    selectedLeafClass = null
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag("insights_range_${range.tag}")
                            )
                        }
                    }
                }
            }

            // Orchard Health Hero Card with Animated Radial Gauge
            item {
                OrchardHealthHeroCard(
                    strings = strings,
                    insights = activeInsights,
                    avgConfidencePct = avgConfidencePct,
                    isDark = isDark
                )
            }

            // 3-Column Key Metric Counters
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        label = strings.totalScans,
                        value = activeInsights.totalScans.toString(),
                        icon = Icons.Filled.QrCodeScanner,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        label = strings.healthyLeaves,
                        value = activeInsights.healthyCount.toString(),
                        icon = Icons.Filled.CheckCircle,
                        accentColor = leafClassPalette(GuavaLeafClass.HEALTHY, isDark).accent,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        label = strings.affectedLeaves,
                        value = activeInsights.affectedCount.toString(),
                        icon = Icons.Filled.HealthAndSafety,
                        accentColor = leafClassPalette(GuavaLeafClass.ANTHRACNOSE, isDark).accent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Most Frequently Detected Condition Spotlight
            activeInsights.mostFrequentCondition?.let { topCondition ->
                item {
                    TopFindingSpotlightCard(
                        strings = strings,
                        language = language,
                        topCondition = topCondition,
                        count = activeInsights.classDistribution[topCondition] ?: 0,
                        total = activeInsights.totalScans,
                        isDark = isDark,
                        onViewInHistory = { onOpenHistoryWithClass(topCondition) }
                    )
                }
            }

            // Render Charts when sufficient data exists (>= 2 scans)
            if (activeInsights.totalScans < 2) {
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
                // Multi-Segment Donut Chart & Interactive Ranked Breakdown
                item {
                    CategoryDistributionCard(
                        strings = strings,
                        language = language,
                        insights = activeInsights,
                        isDark = isDark,
                        selectedClass = selectedLeafClass,
                        onSelectClass = { clicked ->
                            selectedLeafClass = if (selectedLeafClass == clicked) null else clicked
                        },
                        onOpenClassInHistory = { cls ->
                            onOpenHistoryWithClass(cls)
                        }
                    )
                }

                // Interactive 7-Day Scanning Activity Bar Chart
                item {
                    WeeklyActivityCard(
                        strings = strings,
                        activity = activeInsights.weeklyActivity
                    )
                }
            }

            // Orchard & Plot Breakdown (when user has tagged scans with orchard names)
            if (orchardStats.isNotEmpty()) {
                item {
                    OrchardBreakdownCard(
                        strings = strings,
                        orchards = orchardStats,
                        isDark = isDark
                    )
                }
            }

            // Mandatory Sampling vs Prevalence Disclaimer
            item {
                AdvisoryWarningBanner(
                    text = "${strings.insightsDisclaimerTitle}: ${strings.insightsDisclaimerBody}"
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
