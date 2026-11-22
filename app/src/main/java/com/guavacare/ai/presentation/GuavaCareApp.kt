package com.guavacare.ai.presentation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.guavacare.ai.core.common.BilingualStrings
import com.guavacare.ai.domain.model.isForceUpdateGateActive
import com.guavacare.ai.presentation.navigation.GuavaCareRoutes
import com.guavacare.ai.presentation.navigation.topLevelDestinations
import com.guavacare.ai.presentation.screens.about.AboutScreen
import com.guavacare.ai.presentation.screens.diagnosis.DiagnosisScreen
import com.guavacare.ai.presentation.screens.history.HistoryScreen
import com.guavacare.ai.presentation.screens.home.HomeScreen
import com.guavacare.ai.presentation.screens.insights.InsightsScreen
import com.guavacare.ai.presentation.screens.scanner.ScannerScreen
import com.guavacare.ai.presentation.screens.settings.AboutResearchScreen
import com.guavacare.ai.presentation.screens.settings.PrivacyPolicyScreen
import com.guavacare.ai.presentation.screens.settings.SettingsScreen
import com.guavacare.ai.presentation.screens.update.ForceUpdateScreen
import com.guavacare.ai.presentation.theme.GuavaCareTheme

@Composable
fun GuavaCareApp(viewModel: MainViewModel) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val allScans by viewModel.allScans.collectAsStateWithLifecycle()
    val filteredHistory by viewModel.filteredHistory.collectAsStateWithLifecycle()
    val historyFilter by viewModel.historyFilter.collectAsStateWithLifecycle()
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val scanFlowState by viewModel.scanFlowState.collectAsStateWithLifecycle()
    val modelHealth by viewModel.modelHealth.collectAsStateWithLifecycle()
    val storageBytes by viewModel.storageBytes.collectAsStateWithLifecycle()
    val snackbarText by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val isImportingModel by viewModel.isImportingModel.collectAsStateWithLifecycle()
    val isVerifyingModel by viewModel.isVerifyingModel.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val strings = remember(prefs.language) { BilingualStrings.get(prefs.language) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: GuavaCareRoutes.ROUTE_HOME

    val snackbarHostState = remember { SnackbarHostState() }
    var autoLaunchGallery by remember { mutableStateOf(false) }

    LaunchedEffect(snackbarText) {
        if (snackbarText != null) {
            snackbarHostState.showSnackbar(snackbarText!!)
            viewModel.clearSnackbar()
        }
    }

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val showPrimaryNav = currentRoute in topLevelDestinations.map { it.route }

    GuavaCareTheme(
        themeMode = prefs.themeMode,
        language = prefs.language
    ) {
        if (updateState.isForceUpdateGateActive()) {
            ForceUpdateScreen(
                strings = strings,
                language = prefs.language,
                updateState = updateState,
                onToggleLanguage = { viewModel.toggleLanguage() },
                onStartDownload = { viewModel.startUpdateDownload() },
                onCancelDownload = { viewModel.cancelUpdateDownload() },
                onInstallApk = {
                    val installIntent = viewModel.prepareInstallIntent()
                    if (installIntent != null) {
                        runCatching { context.startActivity(installIntent) }
                    }
                },
                onOpenInstallPermissionSettings = {
                    val settingsIntent = viewModel.createUnknownSourcesSettingsIntent()
                    runCatching { context.startActivity(settingsIntent) }
                },
                onCheckInstallPermission = { viewModel.checkInstallPermissionAndAdvance() },
                onRetry = { viewModel.retryUpdateOrCheck() }
            )
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 680.dp

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (showPrimaryNav && !useNavRail) {
                        Column {
                            HorizontalDivider(
                                thickness = 0.75.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 0.dp
                            ) {
                                topLevelDestinations.forEach { dest ->
                                    val selected = currentRoute == dest.route
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { navigateTopLevel(dest.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.labelProvider(strings)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = dest.labelProvider(strings),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f)
                                        ),
                                        modifier = Modifier.testTag(dest.testTag)
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (showPrimaryNav && useNavRail) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            topLevelDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopLevel(dest.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.labelProvider(strings)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = dest.labelProvider(strings),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f)
                                    ),
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = GuavaCareRoutes.ROUTE_HOME,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        composable(GuavaCareRoutes.ROUTE_HOME) {
                            HomeScreen(
                                strings = strings,
                                language = prefs.language,
                                devMockModeEnabled = prefs.devMockModeEnabled,
                                scans = allScans,
                                insights = insights,
                                tips = viewModel.advisoryBundle.tips,
                                onQuickScanClick = {
                                    viewModel.resetScanState()
                                    navigateTopLevel(GuavaCareRoutes.ROUTE_SCANNER)
                                },
                                onGalleryUploadClick = {
                                    viewModel.resetScanState()
                                    autoLaunchGallery = true
                                    navigateTopLevel(GuavaCareRoutes.ROUTE_SCANNER)
                                },
                                onOpenScan = { record ->
                                    viewModel.reopenSavedScan(record) {
                                        navController.navigate(GuavaCareRoutes.ROUTE_DIAGNOSIS)
                                    }
                                },
                                onViewAllHistory = { navigateTopLevel(GuavaCareRoutes.ROUTE_HISTORY) },
                                onOpenSettings = {
                                    viewModel.refreshDiagnostics()
                                    navController.navigate(GuavaCareRoutes.ROUTE_SETTINGS)
                                },
                                onToggleLanguage = { viewModel.toggleLanguage() },
                                modelHealth = modelHealth,
                                isVerifyingModel = isVerifyingModel,
                                onRefreshModelStatus = { viewModel.verifyAndPreloadModel() }
                            )
                        }

                        composable(GuavaCareRoutes.ROUTE_SCANNER) {
                            ScannerScreen(
                                strings = strings,
                                language = prefs.language,
                                showLeafGuide = prefs.showLeafGuide,
                                devMockModeEnabled = prefs.devMockModeEnabled,
                                scanFlowState = scanFlowState,
                                autoLaunchGallery = autoLaunchGallery,
                                onGalleryAutoLaunchConsumed = { autoLaunchGallery = false },
                                onCreateCaptureFile = { viewModel.createCaptureFile() },
                                onImageCaptured = { file ->
                                    viewModel.stageCapturedFileForPreview(file)
                                },
                                onGalleryImageSelected = { uri ->
                                    viewModel.stageGalleryUriForPreview(uri)
                                },
                                onConfirmPreview = {
                                    viewModel.confirmPreviewAndAnalyze {
                                        navController.navigate(GuavaCareRoutes.ROUTE_DIAGNOSIS)
                                    }
                                },
                                onDiscardPreview = { viewModel.discardPreview() },
                                onRetryScan = { viewModel.resetScanState() },
                                onOpenSettings = {
                                    viewModel.refreshDiagnostics()
                                    navController.navigate(GuavaCareRoutes.ROUTE_SETTINGS)
                                },
                                modelHealth = modelHealth,
                                isVerifyingModel = isVerifyingModel,
                                isImportingModel = isImportingModel,
                                onImportTfliteModel = { uri ->
                                    viewModel.importTfliteModel(uri, strings.modelImportSuccess)
                                }
                            )
                        }

                        composable(GuavaCareRoutes.ROUTE_DIAGNOSIS) {
                            val ready = scanFlowState as? ScanFlowState.Ready
                            if (ready != null) {
                                val advisory = viewModel.advisoryBundle.advisories.firstOrNull {
                                    it.leafClass == ready.prediction.predictedClass
                                }
                                DiagnosisScreen(
                                    strings = strings,
                                    language = prefs.language,
                                    readyState = ready,
                                    advisory = advisory,
                                    onSave = { orchard, plot, notes ->
                                        viewModel.saveOrUpdateCurrentDiagnosis(
                                            orchardName = orchard,
                                            plotId = plot,
                                            notes = notes,
                                            successToast = strings.savedSuccess
                                        )
                                    },
                                    onScanAgain = {
                                        viewModel.resetScanState()
                                        navController.popBackStack()
                                        navigateTopLevel(GuavaCareRoutes.ROUTE_SCANNER)
                                    },
                                    onBack = { navController.popBackStack() }
                                )
                            } else {
                                LaunchedEffect(Unit) {
                                    navController.popBackStack()
                                }
                            }
                        }

                        composable(GuavaCareRoutes.ROUTE_HISTORY) {
                            HistoryScreen(
                                strings = strings,
                                language = prefs.language,
                                records = filteredHistory,
                                totalSavedCount = allScans.size,
                                filterState = historyFilter,
                                onSearchChange = { viewModel.updateSearchQuery(it) },
                                onClassFilterChange = { viewModel.updateClassFilter(it) },
                                onDateFilterChange = { viewModel.updateDateFilter(it) },
                                onReopenDiagnosis = { record ->
                                    viewModel.reopenSavedScan(record) {
                                        navController.navigate(GuavaCareRoutes.ROUTE_DIAGNOSIS)
                                    }
                                },
                                onDeleteRecord = { viewModel.deleteScan(it) },
                                onDeleteAll = { viewModel.deleteAllScans() },
                                onStartScan = { navigateTopLevel(GuavaCareRoutes.ROUTE_SCANNER) },
                                onClearFilters = { viewModel.clearHistoryFilters() }
                            )
                        }

                        composable(GuavaCareRoutes.ROUTE_INSIGHTS) {
                            InsightsScreen(
                                strings = strings,
                                language = prefs.language,
                                insights = insights,
                                onStartScan = { navigateTopLevel(GuavaCareRoutes.ROUTE_SCANNER) }
                            )
                        }

                        composable(GuavaCareRoutes.ROUTE_ABOUT) {
                            AboutScreen(
                                strings = strings,
                                language = prefs.language,
                                onOpenPrivacyPolicy = {
                                    navController.navigate(GuavaCareRoutes.ROUTE_PRIVACY_POLICY)
                                },
                                onOpenResearchInfo = {
                                    navController.navigate(GuavaCareRoutes.ROUTE_RESEARCH)
                                },
                                onOpenSettings = {
                                    viewModel.refreshDiagnostics()
                                    navController.navigate(GuavaCareRoutes.ROUTE_SETTINGS)
                                },
                                updateState = updateState,
                                onCheckForUpdates = { viewModel.checkForUpdates(isManual = true) }
                            )
                        }

                        composable(GuavaCareRoutes.ROUTE_SETTINGS) {
                            SettingsScreen(
                                strings = strings,
                                preferences = prefs,
                                modelHealth = modelHealth,
                                coldInitMs = viewModel.coldInitLatencyMs(),
                                lastLatencyMs = viewModel.lastInferenceLatencyMs(),
                                storageBytes = storageBytes,
                                onLanguageSelected = { viewModel.setLanguage(it) },
                                onThemeSelected = { viewModel.setThemeMode(it) },
                                onStrictQualityToggle = { viewModel.setStrictQualityGate(it) },
                                onLeafGuideToggle = { viewModel.setShowLeafGuide(it) },
                                onAutoSaveToggle = { viewModel.setAutoSaveScans(it) },
                                onDevMockModeToggle = { viewModel.setDevMockMode(it) },
                                onClearAllScans = { viewModel.deleteAllScans() },
                                onOpenResearchInfo = { navController.navigate(GuavaCareRoutes.ROUTE_RESEARCH) },
                                onOpenPrivacyPolicy = { navController.navigate(GuavaCareRoutes.ROUTE_PRIVACY_POLICY) },
                                onBack = { navController.popBackStack() },
                                isVerifyingModel = isVerifyingModel,
                                onVerifyModel = { viewModel.verifyAndPreloadModel() },
                                isImportingModel = isImportingModel,
                                onImportTfliteModel = { uri ->
                                    viewModel.importTfliteModel(uri, strings.modelImportSuccess)
                                },
                                onRemoveImportedTflite = {
                                    viewModel.removeImportedTfliteModel(strings.modelRemovedSuccess)
                                },
                                updateState = updateState,
                                onCheckForUpdates = { viewModel.checkForUpdates(isManual = true) },
                                onSaveUpdateFeedUrl = { url -> viewModel.setUpdateFeedUrl(url, andCheck = true) },
                                onResetUpdateFeedUrl = { viewModel.resetUpdateFeedUrl(andCheck = true) }
                            )
                        }

                        composable(GuavaCareRoutes.ROUTE_PRIVACY_POLICY) {
                            PrivacyPolicyScreen(
                                strings = strings,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(GuavaCareRoutes.ROUTE_RESEARCH) {
                            AboutResearchScreen(
                                strings = strings,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
        }
    }
}
