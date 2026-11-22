package com.guavacare.ai.presentation.screens.settings

import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.BuildConfig
import com.guavacare.ai.ai.inference.ModelHealthStatus
import com.guavacare.ai.core.common.AppStrings
import com.guavacare.ai.core.constants.ModelConstants
import com.guavacare.ai.domain.model.AppLanguage
import com.guavacare.ai.domain.model.AppUpdateState
import com.guavacare.ai.domain.model.GuavaLeafClass
import com.guavacare.ai.domain.model.ThemeMode
import com.guavacare.ai.domain.model.UserPreferences
import com.guavacare.ai.presentation.theme.JetBrainsMonoFamily
import java.util.Locale

@Composable
fun SettingsScreen(
    strings: AppStrings,
    preferences: UserPreferences,
    modelHealth: ModelHealthStatus,
    coldInitMs: Long?,
    lastLatencyMs: Long?,
    storageBytes: Long,
    onLanguageSelected: (AppLanguage) -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onStrictQualityToggle: (Boolean) -> Unit,
    onLeafGuideToggle: (Boolean) -> Unit,
    onAutoSaveToggle: (Boolean) -> Unit,
    onDevMockModeToggle: (Boolean) -> Unit,
    onClearAllScans: () -> Unit,
    onOpenResearchInfo: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPrivacyPolicy: () -> Unit = {},
    isVerifyingModel: Boolean = false,
    onVerifyModel: () -> Unit = {},
    isImportingModel: Boolean = false,
    onImportTfliteModel: (Uri) -> Unit = {},
    onRemoveImportedTflite: () -> Unit = {},
    updateState: AppUpdateState = AppUpdateState.Idle,
    onCheckForUpdates: () -> Unit = {}
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .testTag("settings_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back
                        )
                    }
                    Text(
                        text = strings.settings,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Language & Theme
            item {
                SettingsGroupCard(title = "${strings.languageLabel} & ${strings.themeLabel}") {
                    Text(
                        text = strings.languageLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = preferences.language == lang,
                                onClick = { onLanguageSelected(lang) },
                                label = { Text(lang.nativeLabel) },
                                modifier = Modifier.testTag("settings_lang_${lang.code}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.themeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val themes = listOf(
                            ThemeMode.LIGHT to strings.themeLight,
                            ThemeMode.DARK to strings.themeDark,
                            ThemeMode.SYSTEM to strings.themeSystem
                        )
                        themes.forEach { (mode, label) ->
                            FilterChip(
                                selected = preferences.themeMode == mode,
                                onClick = { onThemeSelected(mode) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("settings_theme_${mode.key}")
                            )
                        }
                    }
                }
            }

            // Scanner & Quality Preferences
            item {
                SettingsGroupCard(title = strings.cameraPreferences) {
                    SettingsSwitchRow(
                        title = strings.strictQualityGateLabel,
                        subtitle = strings.strictQualityGateSub,
                        checked = preferences.strictQualityGate,
                        onCheckedChange = onStrictQualityToggle,
                        testTag = "settings_quality_gate_switch"
                    )
                    SettingsSwitchRow(
                        title = strings.leafGuideLabel,
                        subtitle = null,
                        checked = preferences.showLeafGuide,
                        onCheckedChange = onLeafGuideToggle,
                        testTag = "settings_leaf_guide_switch"
                    )
                    SettingsSwitchRow(
                        title = strings.autoSaveLabel,
                        subtitle = null,
                        checked = preferences.autoSaveScans,
                        onCheckedChange = onAutoSaveToggle,
                        testTag = "settings_auto_save_switch"
                    )
                    SettingsSwitchRow(
                        title = strings.devMockModeLabel,
                        subtitle = strings.devMockModeSub,
                        checked = preferences.devMockModeEnabled,
                        onCheckedChange = onDevMockModeToggle,
                        testTag = "settings_dev_mock_switch"
                    )
                }
            }

            // Local Data Management
            item {
                SettingsGroupCard(title = strings.dataManagement) {
                    val mb = storageBytes / (1024.0 * 1024.0)
                    DiagnosticMonoLine("Local Leaf Images", "${"%.2f".format(Locale.US, mb)} MB")
                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("settings_clear_data_button")
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.clearAllScans)
                    }
                }
            }

            // App Updates
            item {
                SettingsGroupCard(title = strings.appUpdatesSectionTitle) {
                    DiagnosticMonoLine(
                        label = strings.aboutVersionLabel,
                        value = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                    )
                    val statusMsg = when (updateState) {
                        is AppUpdateState.Checking -> strings.checkingForUpdates
                        is AppUpdateState.UpToDate -> strings.upToDateStatus
                        is AppUpdateState.Error -> updateState.message
                        else -> null
                    }
                    if (statusMsg != null) {
                        Text(
                            text = statusMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (updateState is AppUpdateState.Error) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.testTag("settings_update_status_text")
                        )
                    }
                    val isChecking = updateState is AppUpdateState.Checking
                    FilledTonalButton(
                        onClick = onCheckForUpdates,
                        enabled = !isChecking,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("settings_check_updates_button")
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isChecking) strings.checkingForUpdates else strings.checkForUpdates)
                    }
                }
            }

            // Privacy Policy & About Research
            item {
                SettingsGroupCard(title = strings.privacyPolicy) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.privacyBody,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalButton(
                        onClick = onOpenPrivacyPolicy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("settings_privacy_policy_button")
                    ) {
                        Icon(Icons.Filled.Policy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.privacyPolicy)
                    }

                    OutlinedButton(
                        onClick = onOpenResearchInfo,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("settings_about_research_button")
                    ) {
                        Icon(Icons.Filled.School, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.aboutResearch)
                    }

                    DiagnosticMonoLine("App Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(strings.deleteAllTitle) },
            text = { Text(strings.deleteAllConfirm) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearAllScans()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(strings.confirmDelete)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
fun PrivacyPolicyScreen(
    strings: AppStrings,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("privacy_policy_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back
                        )
                    }
                    Text(
                        text = strings.privacyPolicy,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Policy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = strings.appTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = strings.privacyPolicyUpdated,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            val sections = listOf(
                strings.privacyPolicyOfflineTitle to strings.privacyPolicyOfflineBody,
                strings.privacyPolicyPermissionsTitle to strings.privacyPolicyPermissionsBody,
                strings.privacyPolicyStorageTitle to strings.privacyPolicyStorageBody,
                strings.privacyPolicyNoTrackingTitle to strings.privacyPolicyNoTrackingBody,
                strings.privacyPolicyDisclaimerTitle to strings.privacyPolicyDisclaimerBody
            )

            sections.forEach { (title, body) ->
                item {
                    SettingsGroupCard(title = title) {
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun AboutResearchScreen(
    strings: AppStrings,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("research_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.back
                        )
                    }
                    Text(
                        text = strings.aboutResearch,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            item {
                SettingsGroupCard(title = "Research Foundation") {
                    Text(
                        text = ModelConstants.RESEARCH_TITLE,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Built from real outdoor guava orchard imagery collected in Bangladesh using smartphone cameras under natural field conditions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SettingsGroupCard(title = "Hybrid Classification Architecture") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MobileNetV2 (FP16 TFLite) → StandardScaler → Linear SVM",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DiagnosticMonoLine("Input Tensor", "224 × 224 × 3 RGB, normalized [-1, 1]")
                    DiagnosticMonoLine("Deep Features", "1280-D MobileNetV2 global embedding")
                    DiagnosticMonoLine("Feature Scaling", "scikit-learn StandardScaler (mean / scale)")
                    DiagnosticMonoLine("Classifier", "Linear SVM (exact OvR / OvO decision function)")
                    DiagnosticMonoLine("Explainability", "SVM-Weighted Feature-Map CAM / Occlusion Attribution")
                }
            }

            item {
                SettingsGroupCard(title = "Reference Paper Measurements (Not Guaranteed on All Devices)") {
                    DiagnosticMonoLine("Reference Test Accuracy", ModelConstants.REFERENCE_TEST_ACCURACY)
                    DiagnosticMonoLine("Feature Extractor Size", ModelConstants.REFERENCE_FEATURE_EXTRACTOR_SIZE)
                    DiagnosticMonoLine("Total Hybrid Model Size", ModelConstants.REFERENCE_TOTAL_MODEL_SIZE)
                    DiagnosticMonoLine("Reported Benchmark Time", ModelConstants.REFERENCE_INFERENCE_TIME)
                    Text(
                        text = "Actual Android inference latency depends on device CPU/SoC, thermal state, and active explainability mode. Live device latency is measured per scan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SettingsGroupCard(title = "Five Supported Diagnostic Classes") {
                    GuavaLeafClass.entries.forEachIndexed { idx, leafClass ->
                        Text(
                            text = "${idx + 1}. ${leafClass.displayNameEn} — ${leafClass.displayNameBn}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun DiagnosticMonoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
