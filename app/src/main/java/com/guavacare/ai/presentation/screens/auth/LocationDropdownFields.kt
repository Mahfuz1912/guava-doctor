package com.guavacare.ai.presentation.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.guavacare.ai.core.common.AppStrings
import com.guavacare.ai.core.data.BangladeshLocations
import com.guavacare.ai.core.data.LocationOption
import com.guavacare.ai.domain.model.AppLanguage

/**
 * Reusable cascading Division -> District -> Upazila dropdown selector for Bangladesh.
 * Selecting a new Division resets District & Upazila; selecting a new District resets Upazila.
 */
@Composable
fun BangladeshLocationDropdownFields(
    strings: AppStrings,
    language: AppLanguage,
    selectedDivision: String,
    selectedDistrict: String,
    selectedUpazila: String,
    onDivisionSelected: (String) -> Unit,
    onDistrictSelected: (String) -> Unit,
    onUpazilaSelected: (String) -> Unit,
    divisionError: String? = null,
    districtError: String? = null,
    upazilaError: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val divisions = remember { BangladeshLocations.divisions }
    val districts = remember(selectedDivision) {
        if (selectedDivision.isBlank()) emptyList() else BangladeshLocations.districtsFor(selectedDivision)
    }
    val upazilas = remember(selectedDistrict) {
        if (selectedDistrict.isBlank()) emptyList() else BangladeshLocations.upazilasFor(selectedDistrict)
    }

    var showDivisionPicker by remember { mutableStateOf(false) }
    var showDistrictPicker by remember { mutableStateOf(false) }
    var showUpazilaPicker by remember { mutableStateOf(false) }

    val divisionDisplay = remember(selectedDivision, language) {
        BangladeshLocations.findDivision(selectedDivision)?.bilingualLabel(language)
            ?: selectedDivision
    }
    val districtDisplay = remember(selectedDivision, selectedDistrict, language) {
        BangladeshLocations.findDistrict(selectedDivision, selectedDistrict)?.bilingualLabel(language)
            ?: selectedDistrict
    }
    val upazilaDisplay = remember(selectedDistrict, selectedUpazila, language) {
        BangladeshLocations.findUpazila(selectedDistrict, selectedUpazila)?.bilingualLabel(language)
            ?: selectedUpazila
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Division Dropdown (বিভাগ)
        LocationDropdownTrigger(
            label = strings.registerDivisionLabel,
            value = divisionDisplay,
            placeholder = strings.registerDivisionPlaceholder,
            supportingHint = null,
            errorText = divisionError,
            enabled = enabled,
            testTag = "dropdown_division",
            onClick = { showDivisionPicker = true }
        )

        // 2. District Dropdown (জেলা) — enabled after Division is chosen
        val districtEnabled = enabled && selectedDivision.isNotBlank() && districts.isNotEmpty()
        LocationDropdownTrigger(
            label = strings.registerDistrictLabel,
            value = districtDisplay,
            placeholder = strings.registerDistrictPlaceholder,
            supportingHint = if (selectedDivision.isBlank()) strings.registerDistrictDisabledHint else null,
            errorText = districtError,
            enabled = districtEnabled,
            testTag = "dropdown_district",
            onClick = { showDistrictPicker = true }
        )

        // 3. Upazila Dropdown (উপজেলা) — enabled after District is chosen
        val upazilaEnabled = enabled && selectedDistrict.isNotBlank() && upazilas.isNotEmpty()
        LocationDropdownTrigger(
            label = strings.registerUpazilaLabel,
            value = upazilaDisplay,
            placeholder = strings.registerUpazilaPlaceholder,
            supportingHint = if (selectedDistrict.isBlank()) strings.registerUpazilaDisabledHint else null,
            errorText = upazilaError,
            enabled = upazilaEnabled,
            testTag = "dropdown_upazila",
            onClick = { showUpazilaPicker = true }
        )
    }

    if (showDivisionPicker) {
        LocationSearchPickerDialog(
            title = strings.registerDivisionLabel,
            searchPlaceholder = strings.locationSearchPlaceholder,
            emptyText = strings.locationNoMatches,
            options = divisions,
            selectedNameEn = selectedDivision,
            language = language,
            dialogTestTag = "picker_dialog_division",
            onSelect = { option ->
                if (option.nameEn != selectedDivision) {
                    onDivisionSelected(option.nameEn)
                    onDistrictSelected("")
                    onUpazilaSelected("")
                }
                showDivisionPicker = false
            },
            onDismiss = { showDivisionPicker = false }
        )
    }

    if (showDistrictPicker) {
        LocationSearchPickerDialog(
            title = strings.registerDistrictLabel,
            searchPlaceholder = strings.locationSearchPlaceholder,
            emptyText = strings.locationNoMatches,
            options = districts,
            selectedNameEn = selectedDistrict,
            language = language,
            dialogTestTag = "picker_dialog_district",
            onSelect = { option ->
                if (option.nameEn != selectedDistrict) {
                    onDistrictSelected(option.nameEn)
                    onUpazilaSelected("")
                }
                showDistrictPicker = false
            },
            onDismiss = { showDistrictPicker = false }
        )
    }

    if (showUpazilaPicker) {
        LocationSearchPickerDialog(
            title = strings.registerUpazilaLabel,
            searchPlaceholder = strings.locationSearchPlaceholder,
            emptyText = strings.locationNoMatches,
            options = upazilas,
            selectedNameEn = selectedUpazila,
            language = language,
            dialogTestTag = "picker_dialog_upazila",
            onSelect = { option ->
                onUpazilaSelected(option.nameEn)
                showUpazilaPicker = false
            },
            onDismiss = { showUpazilaPicker = false }
        )
    }
}

@Composable
private fun LocationDropdownTrigger(
    label: String,
    value: String,
    placeholder: String,
    supportingHint: String?,
    errorText: String?,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val borderColor = when {
        errorText != null -> MaterialTheme.colorScheme.error
        value.isNotBlank() && enabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.outline
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (errorText != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 54.dp)
                .alpha(if (enabled) 1f else 0.55f)
                .clip(RoundedCornerShape(14.dp))
                .clickable(
                    enabled = enabled,
                    role = Role.DropdownList,
                    onClick = onClick
                )
                .testTag(testTag),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(if (errorText != null) 1.5.dp else 1.dp, borderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = if (value.isNotBlank() && enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = value.ifBlank { supportingHint ?: placeholder },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (value.isNotBlank()) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun LocationSearchPickerDialog(
    title: String,
    searchPlaceholder: String,
    emptyText: String,
    options: List<LocationOption>,
    selectedNameEn: String,
    language: AppLanguage,
    dialogTestTag: String,
    onSelect: (LocationOption) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(options, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            options
        } else {
            options.filter {
                it.nameEn.lowercase().contains(q) || it.nameBn.lowercase().contains(q)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .testTag(dialogTestTag),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(searchPlaceholder) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${dialogTestTag}_search")
                )

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emptyText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(filtered, key = { it.nameEn }) { option ->
                            val isSelected = option.nameEn.equals(selectedNameEn, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelect(option) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                                    .testTag("location_option_${option.nameEn}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.localized(language),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = if (language == AppLanguage.BANGLA) option.nameEn else option.nameBn,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}
