package com.guavacare.ai.presentation.screens.diagnosis

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.guavacare.ai.core.common.AppStrings
import com.guavacare.ai.domain.model.AdvisoryArticle
import com.guavacare.ai.domain.model.AppLanguage
import com.guavacare.ai.presentation.ScanFlowState
import com.guavacare.ai.presentation.components.AdvisoryWarningBanner
import com.guavacare.ai.presentation.components.DevMockModeBanner
import com.guavacare.ai.presentation.components.leafClassIcon
import com.guavacare.ai.presentation.theme.LocalIsDarkTheme
import com.guavacare.ai.presentation.theme.leafClassPalette
import java.io.File

private enum class ExplainabilityViewMode {
    ORIGINAL,
    HEATMAP,
    OVERLAY
}

@Composable
fun DiagnosisScreen(
    strings: AppStrings,
    language: AppLanguage,
    readyState: ScanFlowState.Ready,
    advisory: AdvisoryArticle?,
    onSave: (orchardName: String, plotId: String, notes: String) -> Unit,
    onScanAgain: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val pred = readyState.prediction
    val palette = leafClassPalette(pred.predictedClass, isDark)

    var viewMode by remember { mutableStateOf(ExplainabilityViewMode.OVERLAY) }
    var overlayOpacity by remember { mutableFloatStateOf(0.55f) }

    var orchardName by rememberSaveable(readyState.savedRecordId, pred.imagePath) {
        mutableStateOf(readyState.orchardName)
    }
    var plotId by rememberSaveable(readyState.savedRecordId, pred.imagePath) {
        mutableStateOf(readyState.plotId)
    }
    var notes by rememberSaveable(readyState.savedRecordId, pred.imagePath) {
        mutableStateOf(readyState.notes)
    }

    val heatmapImage = remember(pred.explainability.heatmapBitmap) {
        pred.explainability.heatmapBitmap.asImageBitmap()
    }

    val primaryClassName = if (language == AppLanguage.ENGLISH) {
        pred.predictedClass.displayNameEn
    } else {
        pred.predictedClass.displayNameBn
    }
    val secondaryClassName = if (language == AppLanguage.ENGLISH) {
        pred.predictedClass.displayNameBn
    } else {
        pred.predictedClass.displayNameEn
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .testTag("diagnosis_lazy_column"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Clean Top Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .testTag("diagnosis_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = strings.back
                            )
                        }
                        Text(
                            text = strings.diagnosisTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        IconButton(
                            onClick = {
                                val bullets = if (advisory != null) {
                                    if (language == AppLanguage.ENGLISH) advisory.managementEn else advisory.managementBn
                                } else {
                                    emptyList()
                                }
                                val summary = buildString {
                                    appendLine(strings.appTitle)
                                    if (pred.isMockResult) appendLine("[DEV MOCK MODE]")
                                    appendLine("${pred.predictedClass.displayNameEn} (${pred.predictedClass.displayNameBn})")
                                    if (orchardName.isNotBlank()) appendLine("Orchard: $orchardName")
                                    if (plotId.isNotBlank()) appendLine("Plot: $plotId")
                                    if (notes.isNotBlank()) appendLine("Notes: $notes")
                                    if (bullets.isNotEmpty()) {
                                        appendLine()
                                        appendLine("${strings.generalManagement}:")
                                        bullets.take(3).forEach { appendLine("- $it") }
                                    }
                                    appendLine()
                                    appendLine(strings.aiScreeningNotice)
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "${strings.appTitle}: ${pred.predictedClass.displayNameEn}")
                                    putExtra(Intent.EXTRA_TEXT, summary)
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(shareIntent, strings.shareDiagnosis))
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("diagnosis_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = strings.shareDiagnosis,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (pred.isMockResult) {
                item { DevMockModeBanner(text = strings.devMockBanner) }
            }

            // Clean Farmer-Friendly Result Hero Card (Disease Name + Status only)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = palette.container),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = leafClassIcon(pred.predictedClass),
                                    contentDescription = primaryClassName,
                                    tint = palette.accent,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = primaryClassName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.onContainer
                            )
                            Text(
                                text = secondaryClassName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.onContainer.copy(alpha = 0.80f)
                            )
                            if (advisory != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f)
                                ) {
                                    Text(
                                        text = if (language == AppLanguage.ENGLISH) {
                                            advisory.severityEn
                                        } else {
                                            advisory.severityBn
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.accent,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (pred.isLowMarginUncertain) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Uncertain prediction",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = strings.lowMarginWarning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            // Clean Leaf Visual Focus Viewer
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = strings.explainabilityTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF101712))
                        ) {
                            if (viewMode == ExplainabilityViewMode.ORIGINAL || viewMode == ExplainabilityViewMode.OVERLAY) {
                                val imageFile = remember(pred.imagePath) { File(pred.imagePath) }
                                val fallbackImage = remember(readyState.originalBitmap) {
                                    readyState.originalBitmap?.asImageBitmap()
                                }
                                if (imageFile.exists()) {
                                    AsyncImage(
                                        model = imageFile,
                                        contentDescription = "Scanned guava leaf",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (fallbackImage != null) {
                                    Image(
                                        bitmap = fallbackImage,
                                        contentDescription = "Scanned guava leaf",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            if (viewMode == ExplainabilityViewMode.HEATMAP || viewMode == ExplainabilityViewMode.OVERLAY) {
                                Image(
                                    bitmap = heatmapImage,
                                    contentDescription = "Affected region highlight",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(if (viewMode == ExplainabilityViewMode.HEATMAP) 1f else overlayOpacity)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = viewMode == ExplainabilityViewMode.ORIGINAL,
                                onClick = { viewMode = ExplainabilityViewMode.ORIGINAL },
                                label = { Text(strings.viewOriginal) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("xai_mode_original")
                            )
                            FilterChip(
                                selected = viewMode == ExplainabilityViewMode.HEATMAP,
                                onClick = { viewMode = ExplainabilityViewMode.HEATMAP },
                                label = { Text(strings.viewHeatmap) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("xai_mode_heatmap")
                            )
                            FilterChip(
                                selected = viewMode == ExplainabilityViewMode.OVERLAY,
                                onClick = { viewMode = ExplainabilityViewMode.OVERLAY },
                                label = { Text(strings.viewOverlay) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("xai_mode_overlay")
                            )
                        }

                        if (viewMode == ExplainabilityViewMode.OVERLAY) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = strings.overlayOpacity,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${(overlayOpacity * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Slider(
                                    value = overlayOpacity,
                                    onValueChange = { overlayOpacity = it },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.testTag("xai_opacity_slider")
                                )
                            }
                        }
                    }
                }
            }

            // Practical Agronomic Advice + Full Advisory Button
            if (advisory != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = strings.generalManagement,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val bullets = if (language == AppLanguage.ENGLISH) {
                                advisory.managementEn
                            } else {
                                advisory.managementBn
                            }
                            bullets.forEach { point ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(top = 3.dp)
                                            .size(16.dp)
                                    )
                                    Text(
                                        text = point,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Clean Orchard Notes & Save Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = orchardName,
                            onValueChange = { orchardName = it },
                            label = { Text(strings.orchardNameLabel) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("diagnosis_orchard_input")
                        )
                        OutlinedTextField(
                            value = plotId,
                            onValueChange = { plotId = it },
                            label = { Text(strings.plotIdLabel) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("diagnosis_plot_input")
                        )
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text(strings.notesLabel) },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("diagnosis_notes_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onSave(orchardName, plotId, notes) },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .defaultMinSize(minHeight = 52.dp)
                                    .testTag("diagnosis_save_button"),
                                shape = RoundedCornerShape(14.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                            ) {
                                Icon(
                                    imageVector = if (readyState.savedRecordId != null) Icons.Filled.Check else Icons.Filled.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = strings.saveResult)
                            }
                            OutlinedButton(
                                onClick = onScanAgain,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 52.dp)
                                    .testTag("diagnosis_scan_again_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = strings.scanAgain)
                            }
                        }
                    }
                }
            }

            item {
                AdvisoryWarningBanner(text = strings.aiScreeningNotice)
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
