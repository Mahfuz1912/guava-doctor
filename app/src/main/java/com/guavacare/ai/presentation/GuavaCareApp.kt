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
import com.guavacare.ai.domain.model.AuthGateReason
import com.guavacare.ai.domain.model.AuthUiState
import com.guavacare.ai.domain.model.isForceUpdateGateActive
import com.guavacare.ai.presentation.navigation.GuavaCareRoutes
import com.guavacare.ai.presentation.navigation.topLevelDestinations
import com.guavacare.ai.presentation.screens.about.AboutScreen
import com.guavacare.ai.presentation.screens.auth.LoginScreen
import com.guavacare.ai.presentation.screens.auth.RegisterScreen
import com.guavacare.ai.presentation.screens.diagnosis.DiagnosisScreen
import com.guavacare.ai.presentation.screens.history.HistoryScreen
import com.guavacare.ai.presentation.screens.home.HomeScreen
import com.guavacare.ai.presentation.screens.insights.InsightsScreen
import com.guavacare.ai.presentation.screens.profile.ProfileScreen
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
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val authLoading by viewModel.authLoading.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val authGateReason by viewModel.authGateReason.collectAsStateWithLifecycle()
    val passwordResetLoading by viewModel.passwordResetLoading.collectAsStateWithLifecycle()
    val passwordResetError by viewModel.passwordResetError.collectAsStateWithLifecycle()
    val passwordResetSent by viewModel.passwordResetSent.collectAsStateWithLifecycle()
    val cloudSyncStatus by viewModel.cloudSyncStatus.collectAsStateWithLifecycle()
    val remainingGuestScans by viewModel.remainingGuestScans.collectAsStateWithLifecycle()
    val isScanLimitReached by viewModel.isScanLimitReached.collectAsStateWithLifecycle()

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
        if (route == GuavaCareRoutes.ROUTE_SCANNER && isScanLimitReached) {
            viewModel.resetScanState()
            viewModel.setAuthGateReason(AuthGateReason.SCAN_LIMIT_REACHED)
            navController.navigate(GuavaCareRoutes.ROUTE_LOGIN) {
                launchSingleTop = true
            }
            return
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openLogin(reason: AuthGateReason = AuthGateReason.OPTIONAL) {
        viewModel.setAuthGateReason(reason)
        navController.navigate(GuavaCareRoutes.ROUTE_LOGIN) {
            launchSingleTop = true
        }
    }

    fun openRegister() {
        viewModel.clearAuthError()
        navController.navigate(GuavaCareRoutes.ROUTE_REGISTER) {
            launchSingleTop = true
        }
    }

    fun openProfile() {
        viewModel.clearPasswordResetState()
        navController.navigate(GuavaCareRoutes.ROUTE_PROFILE) {
            launchSingleTop = true
        }
    }

    fun navigateWithScanQuotaGate(onAllowed: () -> Unit) {
        if (isScanLimitReached) {
            viewModel.resetScanState()
            openLogin(AuthGateReason.SCAN_LIMIT_REACHED)
        } else {
            onAllowed()
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
                                    navigateWithScanQuotaGate {
                                        viewModel.resetScanState()
                                        navigateTopLevel(GuavaCareRoutes.ROUTE_SCANNER)
                                    }
                                },
                                onGalleryUploadClick = {
                                    navigateWithScanQuotaGate {
                                        viewModel.resetScanState()
                                        autoLaunchGallery = true
                                        navigateTopLevel(GuavaCareRoutes.ROUTE_SCANNER)
                                    }
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
                                onRefreshModelStatus = { viewModel.verifyAndPreloadModel() },
                                authState = authState,
                                remainingGuestScans = remainingGuestScans,
                                onOpenLogin = { openLogin(AuthGateReason.OPTIONAL) },
                                onOpenRegister = { openRegister() },
                                onOpenProfile = { openProfile() }
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
                                    navigateWithScanQuotaGate {
                                        viewModel.stageCapturedFileForPreview(file)
                                    }
                                },
                                onGalleryImageSelected = { uri ->
                                    navigateWithScanQuotaGate {
                                        viewModel.stageGalleryUriForPreview(uri)
                                    }
                                },
                                onConfirmPreview = {
                                    navigateWithScanQuotaGate {
                                        viewModel.confirmPreviewAndAnalyze {
                                            navController.navigate(GuavaCareRoutes.ROUTE_DIAGNOSIS)
                                        }
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
                                },
                                isAuthenticated = authState is AuthUiState.Authenticated,
                                remainingGuestScans = remainingGuestScans,
                                onOpenLoginForLimit = { openLogin(AuthGateReason.OPTIONAL) }
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
                                scans = allScans,
                                onStartScan = { navigateTopLevel(GuavaCareRoutes.ROUTE_SCANNER) },
                                onOpenHistoryWithClass = { leafClass ->
                                    viewModel.clearHistoryFilters()
                                    if (leafClass != null) {
                                        viewModel.updateClassFilter(leafClass)
                                    }
                                    navigateTopLevel(GuavaCareRoutes.ROUTE_HISTORY)
                                }
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
                                onCheckForUpdates = { viewModel.checkForUpdates(isManual = true) },
                                advisories = viewModel.advisoryBundle.advisories
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
                                authState = authState,
                                remainingGuestScans = remainingGuestScans,
                                cloudSyncStatus = cloudSyncStatus,
                                isAuthLoading = authLoading,
                                onOpenLogin = { openLogin(AuthGateReason.OPTIONAL) },
                                onOpenRegister = { openRegister() },
                                onUpdateProfile = { name, division, district, upazila ->
                                    viewModel.updateUserProfile(
                                        fullName = name,
                                        division = division,
                                        district = district,
                                        upazila = upazila,
                                        successToast = strings.profileUpdatedSuccess
                                    )
                                },
                                onSignOut = {
                                    viewModel.signOut(
                                        activityContext = context,
                                        signedOutToast = strings.signedOutToast
                                    )
                                },
                                onOpenProfile = { openProfile() }
                            )
                        }

                        composable(GuavaCareRoutes.ROUTE_PROFILE) {
                            ProfileScreen(
                                strings = strings,
                                language = prefs.language,
                                authState = authState,
                                insights = insights,
                                cloudSyncStatus = cloudSyncStatus,
                                isSavingProfile = authLoading,
                                isSendingPasswordReset = passwordResetLoading,
                                passwordResetError = passwordResetError,
                                passwordResetSent = passwordResetSent,
                                onUpdateProfile = { name, division, district, upazila ->
                                    viewModel.updateUserProfile(
                                        fullName = name,
                                        division = division,
                                        district = district,
                                        upazila = upazila,
                                        successToast = strings.profileUpdatedSuccess
                                    )
                                },
                                onSendPasswordResetEmail = { email ->
                                    viewModel.sendPasswordResetEmail(
                                        email = email,
                                        successToast = strings.passwordResetSentSuccess
                                    )
                                },
                                onClearPasswordResetState = { viewModel.clearPasswordResetState() },
                                onSignOut = {
                                    viewModel.signOut(
                                        activityContext = context,
                                        signedOutToast = strings.signedOutToast
                                    )
                                    navController.popBackStack(GuavaCareRoutes.ROUTE_HOME, inclusive = false)
                                },
                                onOpenLogin = { openLogin(AuthGateReason.OPTIONAL) },
                                onOpenRegister = { openRegister() },
                                onToggleLanguage = { viewModel.toggleLanguage() },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(GuavaCareRoutes.ROUTE_LOGIN) {
                            LoginScreen(
                                strings = strings,
                                language = prefs.language,
                                gateReason = authGateReason,
                                isLoading = authLoading,
                                errorMessage = authError,
                                onEmailSignInClick = { email, password ->
                                    viewModel.signInWithEmailPassword(
                                        email = email,
                                        password = password,
                                        welcomeBackToast = strings.welcomeBackToast,
                                        onAuthenticated = {
                                            navController.popBackStack(GuavaCareRoutes.ROUTE_HOME, inclusive = false)
                                        }
                                    )
                                },
                                onGoogleSignInClick = {
                                    viewModel.signInWithGoogle(
                                        activityContext = context,
                                        welcomeBackToast = strings.welcomeBackToast,
                                        onAuthenticated = {
                                            navController.popBackStack(GuavaCareRoutes.ROUTE_HOME, inclusive = false)
                                        }
                                    )
                                },
                                onNavigateToRegister = { openRegister() },
                                onToggleLanguage = { viewModel.toggleLanguage() },
                                onBack = { navController.popBackStack() },
                                isSendingPasswordReset = passwordResetLoading,
                                passwordResetError = passwordResetError,
                                passwordResetSent = passwordResetSent,
                                onSendPasswordResetEmail = { email ->
                                    viewModel.sendPasswordResetEmail(
                                        email = email,
                                        successToast = strings.passwordResetSentSuccess
                                    )
                                },
                                onClearPasswordResetState = { viewModel.clearPasswordResetState() }
                            )
                        }

                        composable(GuavaCareRoutes.ROUTE_REGISTER) {
                            RegisterScreen(
                                strings = strings,
                                language = prefs.language,
                                isLoading = authLoading,
                                errorMessage = authError,
                                onSubmitEmailRegistration = { fullName, email, password, division, district, upazila ->
                                    viewModel.registerWithEmailPassword(
                                        fullName = fullName,
                                        email = email,
                                        password = password,
                                        division = division,
                                        district = district,
                                        upazila = upazila,
                                        successToast = strings.accountCreatedAndSyncedToast,
                                        onRegistrationComplete = {
                                            navController.popBackStack(GuavaCareRoutes.ROUTE_HOME, inclusive = false)
                                        }
                                    )
                                },
                                onGoogleSignInClick = {
                                    viewModel.signInWithGoogle(
                                        activityContext = context,
                                        welcomeBackToast = strings.welcomeBackToast,
                                        onAuthenticated = {
                                            navController.popBackStack(GuavaCareRoutes.ROUTE_HOME, inclusive = false)
                                        }
                                    )
                                },
                                onNavigateToLogin = { openLogin(authGateReason) },
                                onToggleLanguage = { viewModel.toggleLanguage() },
                                onBack = { navController.popBackStack() }
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
