package com.guavacare.ai.presentation.screens.insights

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guavacare.ai.core.common.AppStrings
import com.guavacare.ai.domain.model.AppLanguage
import com.guavacare.ai.domain.model.DailyScanActivity
import com.guavacare.ai.domain.model.GuavaLeafClass
import com.guavacare.ai.domain.model.OrchardInsightsSummary
import com.guavacare.ai.presentation.components.ClassStatusBadge
import com.guavacare.ai.presentation.components.leafClassIcon
import com.guavacare.ai.presentation.theme.JetBrainsMonoFamily
import com.guavacare.ai.presentation.theme.leafClassPalette

internal data class OrchardGroupStat(
    val orchardName: String,
    val total: Int,
    val healthy: Int,
    val affected: Int
)

@Composable
internal fun OrchardHealthHeroCard(
    strings: AppStrings,
    insights: OrchardInsightsSummary,
    avgConfidencePct: Int?,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val total = insights.totalScans
    val healthyRatio = if (total > 0) insights.healthyCount.toFloat() / total.toFloat() else 0f
    val healthyPct = (healthyRatio * 100f).toInt()
    val animatedRatio by animateFloatAsState(
        targetValue = healthyRatio,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "health_rate_gauge"
    )

    val healthyPalette = leafClassPalette(GuavaLeafClass.HEALTHY, isDark)
    val monitorPalette = leafClassPalette(GuavaLeafClass.NUTRIENT_STRESS, isDark)
    val attentionPalette = leafClassPalette(GuavaLeafClass.ANTHRACNOSE, isDark)

    val (statusLabel, statusAccent, statusContainer, statusIcon) = when {
        total == 0 -> Quadruple(
            strings.insightsStatusEmpty,
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer,
            Icons.Filled.AutoGraph
        )
        healthyPct >= 80 -> Quadruple(
            strings.insightsStatusExcellent,
            healthyPalette.accent,
            healthyPalette.container,
            Icons.Filled.CheckCircle
        )
        healthyPct >= 50 -> Quadruple(
            strings.insightsStatusMonitor,
            monitorPalette.accent,
            monitorPalette.container,
            Icons.Filled.WarningAmber
        )
        else -> Quadruple(
            strings.insightsStatusAttention,
            attentionPalette.accent,
            attentionPalette.container,
            Icons.Filled.HealthAndSafety
        )
    }

    val cardBgStart = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.34f else 0.52f)
    val cardBgEnd = MaterialTheme.colorScheme.surfaceContainer

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("insights_health_hero_card"),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, statusAccent.copy(alpha = 0.24f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(cardBgStart, cardBgEnd)))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Radial Health Gauge
                Box(
                    modifier = Modifier.size(104.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    Canvas(modifier = Modifier.size(104.dp)) {
                        val strokePx = 11.dp.toPx()
                        val inset = strokePx / 2f
                        val arcSize = Size(size.width - strokePx, size.height - strokePx)
                        drawArc(
                            color = trackColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                        if (total > 0 && animatedRatio > 0f) {
                            drawArc(
                                color = statusAccent,
                                startAngle = -90f,
                                sweepAngle = (360f * animatedRatio).coerceAtLeast(8f),
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = Stroke(width = strokePx, cap = StrokeCap.Round)
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (total > 0) "$healthyPct%" else "—",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = strings.healthyLeaves,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Right Summary Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = statusContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = statusAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Text(
                        text = strings.insightsHealthRateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Split Ratio Visual Bar
                    if (total > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        ) {
                            if (insights.healthyCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(insights.healthyCount.toFloat())
                                        .height(7.dp)
                                        .background(healthyPalette.accent)
                                )
                            }
                            if (insights.affectedCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(insights.affectedCount.toFloat())
                                        .height(7.dp)
                                        .background(attentionPalette.accent)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${insights.healthyCount} ${strings.healthyLeaves} • ${insights.affectedCount} ${strings.affectedLeaves}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (avgConfidencePct != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                            ) {
                                Text(
                                    text = "$avgConfidencePct%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TopFindingSpotlightCard(
    strings: AppStrings,
    language: AppLanguage,
    topCondition: GuavaLeafClass,
    count: Int,
    total: Int,
    isDark: Boolean,
    onViewInHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = leafClassPalette(topCondition, isDark)
    val sharePct = ((count.toFloat() / total.coerceAtLeast(1).toFloat()) * 100f).toInt()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("insights_top_finding_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.28f)),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = palette.container
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = leafClassIcon(topCondition),
                                contentDescription = topCondition.localizedName(language),
                                tint = palette.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$sharePct%",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = palette.accent
                    )
                    Text(
                        text = "$count/$total",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FilledTonalButton(
                onClick = onViewInHistory,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("insights_view_top_history_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = strings.insightsViewInHistory)
            }
        }
    }
}

@Composable
internal fun CategoryDistributionCard(
    strings: AppStrings,
    language: AppLanguage,
    insights: OrchardInsightsSummary,
    isDark: Boolean,
    selectedClass: GuavaLeafClass?,
    onSelectClass: (GuavaLeafClass) -> Unit,
    onOpenClassInHistory: (GuavaLeafClass) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = insights.totalScans.coerceAtLeast(1)
    val orderedClasses = remember(insights.classDistribution) {
        GuavaLeafClass.entries.sortedByDescending { insights.classDistribution[it] ?: 0 }
    }
    val nonZeroSegments = orderedClasses.mapNotNull { cls ->
        val count = insights.classDistribution[cls] ?: 0
        if (count > 0) cls to count else null
    }
    val activeCenterClass = selectedClass ?: insights.mostFrequentCondition
    val centerCount = activeCenterClass?.let { insights.classDistribution[it] ?: 0 } ?: 0
    val centerPct = ((centerCount.toFloat() / total.toFloat()) * 100f).toInt()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("insights_distribution_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = strings.categoryDistribution,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = strings.insightsTapClassHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Multi-Segment Donut Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val emptyRingColor = MaterialTheme.colorScheme.surfaceContainerHighest
                val segmentColors = nonZeroSegments.map { (cls, _) ->
                    val base = leafClassPalette(cls, isDark).accent
                    if (selectedClass != null && selectedClass != cls) base.copy(alpha = 0.30f) else base
                }
                Box(
                    modifier = Modifier.size(168.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(168.dp)) {
                        val strokePx = 22.dp.toPx()
                        val inset = strokePx / 2f
                        val arcSize = Size(size.width - strokePx, size.height - strokePx)
                        drawArc(
                            color = emptyRingColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokePx)
                        )
                        val gapDeg = if (nonZeroSegments.size > 1) 3.5f else 0f
                        var currentAngle = -90f
                        nonZeroSegments.forEachIndexed { idx, (_, count) ->
                            val rawSweep = (count.toFloat() / total.toFloat()) * 360f
                            val sweep = (rawSweep - gapDeg).coerceAtLeast(2f)
                            drawArc(
                                color = segmentColors[idx],
                                startAngle = currentAngle + gapDeg / 2f,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                            )
                            currentAngle += rawSweep
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Text(
                            text = "$centerPct%",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = activeCenterClass?.localizedName(language) ?: strings.totalScans,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            HorizontalDivider(
                thickness = 0.6.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            // Ranked Class Breakdown Rows
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                orderedClasses.forEach { leafClass ->
                    val count = insights.classDistribution[leafClass] ?: 0
                    val ratio = count.toFloat() / total.toFloat()
                    val pct = (ratio * 100f).toInt()
                    val palette = leafClassPalette(leafClass, isDark)
                    val isSelected = selectedClass == leafClass

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) palette.container.copy(alpha = 0.45f) else Color.Transparent
                            )
                            .clickable {
                                if (isSelected && count > 0) {
                                    onOpenClassInHistory(leafClass)
                                } else {
                                    onSelectClass(leafClass)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("insights_class_row_${leafClass.name.lowercase()}"),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(palette.accent)
                            )
                            Text(
                                text = leafClass.localizedName(language),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected || count > 0) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$count ($pct%)",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (count > 0) palette.accent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (count > 0) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = strings.insightsViewInHistory,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { onOpenClassInHistory(leafClass) }
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = palette.accent,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun WeeklyActivityCard(
    strings: AppStrings,
    activity: List<DailyScanActivity>,
    modifier: Modifier = Modifier
) {
    val weekTotal = activity.sumOf { it.count }
    val peakDay = activity.maxByOrNull { it.count }
    val peakIndex = if ((peakDay?.count ?: 0) > 0) activity.indexOf(peakDay) else -1
    var selectedDayIndex by remember(activity) {
        mutableIntStateOf(if (peakIndex >= 0) peakIndex else activity.lastIndex)
    }
    val activeDay = activity.getOrNull(selectedDayIndex)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("insights_weekly_activity_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = strings.weeklyActivity,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${strings.insightsSevenDayTotalLabel}: $weekTotal" +
                            if (peakDay != null && peakDay.count > 0) {
                                " • ${strings.insightsPeakDayLabel}: ${peakDay.dayLabel} (${peakDay.count})"
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (activeDay != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${activeDay.dayLabel}: ${activeDay.count}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            WeeklyActivityBarChart(
                activity = activity,
                selectedIndex = selectedDayIndex,
                peakIndex = peakIndex,
                onSelectDay = { selectedDayIndex = it }
            )
        }
    }
}

@Composable
private fun WeeklyActivityBarChart(
    activity: List<DailyScanActivity>,
    selectedIndex: Int,
    peakIndex: Int,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val maxCount = (activity.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
        ) {
            if (activity.isEmpty()) return@Canvas

            // Subtle horizontal dashed guidelines
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
                val y = size.height * fraction
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dashEffect
                )
            }

            val slotWidth = size.width / activity.size
            val barWidth = (slotWidth * 0.48f).coerceAtMost(34.dp.toPx())
            val radius = CornerRadius(10.dp.toPx(), 10.dp.toPx())

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
                    val barColor = if (idx == selectedIndex || idx == peakIndex) primaryColor else secondaryColor
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, barHeight.coerceAtLeast(12.dp.toPx())),
                        cornerRadius = radius
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            activity.forEachIndexed { idx, day ->
                val isSelected = idx == selectedIndex
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            else Color.Transparent
                        )
                        .clickable { onSelectDay(idx) }
                        .defaultMinSize(minWidth = 40.dp, minHeight = 48.dp)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("insights_day_slot_$idx"),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = day.count.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = day.dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun OrchardBreakdownCard(
    strings: AppStrings,
    orchards: List<OrchardGroupStat>,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val healthyColor = leafClassPalette(GuavaLeafClass.HEALTHY, isDark).accent
    val affectedColor = leafClassPalette(GuavaLeafClass.ANTHRACNOSE, isDark).accent

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("insights_orchard_breakdown_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Agriculture,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = strings.insightsOrchardBreakdownTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            orchards.forEachIndexed { index, stat ->
                val healthyRatio = stat.healthy.toFloat() / stat.total.coerceAtLeast(1).toFloat()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocalFlorist,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stat.orchardName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${stat.healthy}/${stat.total} ${strings.healthyLeaves}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinearProgressIndicator(
                        progress = { healthyRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = healthyColor,
                        trackColor = affectedColor.copy(alpha = 0.28f)
                    )
                }
                if (index < orchards.lastIndex) {
                    HorizontalDivider(
                        thickness = 0.6.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
