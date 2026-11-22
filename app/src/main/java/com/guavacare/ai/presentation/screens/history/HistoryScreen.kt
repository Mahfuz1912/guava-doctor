package com.guavacare.ai.presentation.screens.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.guavacare.ai.core.common.AppStrings
import com.guavacare.ai.domain.model.AppLanguage
import com.guavacare.ai.domain.model.GuavaLeafClass
import com.guavacare.ai.domain.model.HistoryDateFilter
import com.guavacare.ai.domain.model.ScanRecord
import com.guavacare.ai.presentation.HistoryFilterState
import com.guavacare.ai.presentation.components.ClassStatusBadge
import com.guavacare.ai.presentation.components.EmptyStateCard
import com.guavacare.ai.presentation.components.formatTimestamp
import java.io.File

@Composable
fun HistoryScreen(
    strings: AppStrings,
    language: AppLanguage,
    records: List<ScanRecord>,
    totalSavedCount: Int,
    filterState: HistoryFilterState,
    onSearchChange: (String) -> Unit,
    onClassFilterChange: (GuavaLeafClass?) -> Unit,
    onDateFilterChange: (HistoryDateFilter) -> Unit,
    onReopenDiagnosis: (ScanRecord) -> Unit,
    onDeleteRecord: (Long) -> Unit,
    onDeleteAll: () -> Unit,
    onStartScan: () -> Unit,
    modifier: Modifier = Modifier,
    onClearFilters: () -> Unit = {}
) {
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<ScanRecord?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .testTag("history_lazy_column"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Minimal Header with Icon Badge & Count Pill
            item {
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
                            modifier = Modifier.size(38.dp),
                            shape = RoundedCornerShape(11.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Text(
                            text = strings.historyTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (totalSavedCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = if (records.size == totalSavedCount) {
                                        "$totalSavedCount"
                                    } else {
                                        "${records.size}/$totalSavedCount"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (totalSavedCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            IconButton(
                                onClick = { showDeleteAllDialog = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("history_delete_all_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = strings.deleteAllTitle,
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            // Crisp White Rounded Search Bar
            item {
                OutlinedTextField(
                    value = filterState.searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = {
                        Text(
                            text = strings.searchHistoryHint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (filterState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_search_input")
                )
            }

            // Segmented Date Filter Pill Bar
            item {
                val dateOptions = listOf(
                    HistoryDateFilter.ALL to strings.filterAllTime,
                    HistoryDateFilter.TODAY to strings.filterToday,
                    HistoryDateFilter.LAST_7_DAYS to strings.filter7Days,
                    HistoryDateFilter.LAST_30_DAYS to strings.filter30Days
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        dateOptions.forEach { (filter, label) ->
                            val selected = filterState.dateFilter == filter
                            Surface(
                                onClick = { onDateFilterChange(filter) },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 40.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.surfaceContainer
                                } else {
                                    Color.Transparent
                                },
                                shadowElevation = if (selected) 1.dp else 0.dp
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Soft Rounded Disease Class Filter Chips
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        HistoryPillFilterChip(
                            label = strings.allClasses,
                            selected = filterState.selectedClass == null,
                            onClick = { onClassFilterChange(null) }
                        )
                    }
                    items(GuavaLeafClass.entries) { leafClass ->
                        HistoryPillFilterChip(
                            label = leafClass.localizedName(language),
                            selected = filterState.selectedClass == leafClass,
                            onClick = {
                                onClassFilterChange(if (filterState.selectedClass == leafClass) null else leafClass)
                            }
                        )
                    }
                }
            }

            if (records.isEmpty()) {
                item {
                    if (totalSavedCount == 0) {
                        EmptyStateCard(
                            icon = Icons.Filled.History,
                            title = strings.noScansYetTitle,
                            body = strings.noScansYetBody,
                            actionLabel = strings.startFirstScan,
                            onAction = onStartScan
                        )
                    } else {
                        EmptyStateCard(
                            icon = Icons.Filled.Search,
                            title = strings.noMatchingScansTitle,
                            body = strings.noMatchingScansBody,
                            actionLabel = strings.clearFilters,
                            onAction = onClearFilters
                        )
                    }
                }
            } else {
                items(records, key = { it.id }) { record ->
                    HistoryRecordCard(
                        record = record,
                        language = language,
                        mockLabel = strings.devMockTag,
                        onOpenDetail = { selectedRecord = record },
                        onDelete = { onDeleteRecord(record.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    // Clean Detail Dialog for Inspecting / Reopening / Deleting a Scan
    selectedRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { selectedRecord = null },
            shape = RoundedCornerShape(26.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = {
                ClassStatusBadge(
                    leafClass = record.predictedClass,
                    language = language,
                    isMock = record.isMockResult,
                    mockLabel = strings.devMockTag
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(
                        model = File(record.imagePath),
                        contentDescription = record.predictedClass.localizedName(language),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(196.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = formatTimestamp(record.timestamp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (record.orchardName.isNotBlank() || record.plotId.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = listOf(record.orchardName, record.plotId)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" • "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    if (record.notes.isNotBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Text(
                                text = record.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val target = record
                            selectedRecord = null
                            onReopenDiagnosis(target)
                        },
                        shape = RoundedCornerShape(14.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("history_reopen_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.reopenDiagnosis)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onDeleteRecord(record.id)
                                selectedRecord = null
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 46.dp)
                        ) {
                            Text(strings.confirmDelete)
                        }
                        TextButton(
                            onClick = { selectedRecord = null },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 46.dp)
                        ) {
                            Text(strings.cancel)
                        }
                    }
                }
            }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = {
                Text(
                    text = strings.deleteAllTitle,
                    fontWeight = FontWeight.Bold
                )
            },
            text = { Text(strings.deleteAllConfirm) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllDialog = false
                        onDeleteAll()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("history_confirm_delete_all")
                ) {
                    Text(strings.confirmDelete)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun HistoryPillFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        shape = RoundedCornerShape(999.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.primary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent,
            borderWidth = 0.8.dp
        )
    )
}

@Composable
private fun HistoryRecordCard(
    record: ScanRecord,
    language: AppLanguage,
    mockLabel: String,
    onOpenDetail: () -> Unit,
    onDelete: () -> Unit
) {
    val locationLabel = listOf(record.orchardName, record.plotId)
        .filter { it.isNotBlank() }
        .joinToString(" • ")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpenDetail)
            .testTag("history_record_card_${record.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = File(record.imagePath),
                contentDescription = record.predictedClass.localizedName(language),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(66.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ClassStatusBadge(
                    leafClass = record.predictedClass,
                    language = language,
                    isMock = record.isMockResult,
                    mockLabel = mockLabel
                )
                if (locationLabel.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = locationLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = formatTimestamp(record.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (record.notes.isNotBlank()) {
                    Text(
                        text = record.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("history_delete_item_${record.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete record",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }
    }
}
