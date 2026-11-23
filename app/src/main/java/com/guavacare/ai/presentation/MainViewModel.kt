package com.guavacare.ai.presentation

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.guavacare.ai.ai.classifier.SvmMulticlassStrategy
import com.guavacare.ai.ai.explainability.ExplainabilityResult
import com.guavacare.ai.ai.explainability.ExplanationMethodType
import com.guavacare.ai.ai.explainability.HybridExplainabilityEngine
import com.guavacare.ai.ai.inference.InferenceMetrics
import com.guavacare.ai.ai.inference.ModelHealthStatus
import com.guavacare.ai.ai.inference.PredictionOutcome
import com.guavacare.ai.ai.inference.PredictionResult
import com.guavacare.ai.ai.preprocessing.ImageQualityReport
import com.guavacare.ai.core.common.BilingualStrings
import com.guavacare.ai.core.constants.ModelConstants
import com.guavacare.ai.core.constants.UpdateConstants
import com.guavacare.ai.data.remote.GoogleAuthHelper
import com.guavacare.ai.data.remote.GoogleSignInOutcome
import com.guavacare.ai.data.remote.PasswordResetOutcome
import com.guavacare.ai.data.repository.CloudAccountRepository
import com.guavacare.ai.data.repository.DownloadProgressEvent
import com.guavacare.ai.data.repository.ScanRepository
import com.guavacare.ai.di.AppContainer
import com.guavacare.ai.domain.model.AUTH_PROVIDER_GOOGLE
import com.guavacare.ai.domain.model.AUTH_PROVIDER_PASSWORD
import com.guavacare.ai.domain.model.AdvisoryBundle
import com.guavacare.ai.domain.model.AppLanguage
import com.guavacare.ai.domain.model.AppReleaseInfo
import com.guavacare.ai.domain.model.AppUpdateState
import com.guavacare.ai.domain.model.AppVersionComparator
import com.guavacare.ai.domain.model.AuthGateReason
import com.guavacare.ai.domain.model.AuthUiState
import com.guavacare.ai.domain.model.CloudScanRecord
import com.guavacare.ai.domain.model.CloudSyncStatus
import com.guavacare.ai.domain.model.EmailUniquenessCheckResult
import com.guavacare.ai.domain.model.GUEST_FREE_SCAN_LIMIT
import com.guavacare.ai.domain.model.GuavaLeafClass
import com.guavacare.ai.domain.model.HistoryDateFilter
import com.guavacare.ai.domain.model.OrchardInsightsSummary
import com.guavacare.ai.domain.model.ScanRecord
import com.guavacare.ai.domain.model.ThemeMode
import com.guavacare.ai.domain.model.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

sealed interface ScanFlowState {
    data object Idle : ScanFlowState
    data class Preview(val imagePath: String, val bitmap: Bitmap) : ScanFlowState
    data class NonLeafWarning(
        val imagePath: String,
        val bitmap: Bitmap,
        val report: ImageQualityReport
    ) : ScanFlowState
    data class Processing(val stageKey: String) : ScanFlowState
    data class QualityRejected(val imagePath: String, val report: ImageQualityReport) : ScanFlowState
    data class ModelMissing(val imagePath: String, val healthStatus: ModelHealthStatus) : ScanFlowState
    data class Error(val message: String) : ScanFlowState
    data class Ready(
        val prediction: PredictionResult,
        val originalBitmap: Bitmap?,
        val savedRecordId: Long? = null,
        val orchardName: String = "",
        val plotId: String = "",
        val notes: String = ""
    ) : ScanFlowState
}

data class HistoryFilterState(
    val searchQuery: String = "",
    val selectedClass: GuavaLeafClass? = null,
    val dateFilter: HistoryDateFilter = HistoryDateFilter.ALL
)

class MainViewModel(
    private val container: AppContainer
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = container.preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferences()
        )

    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Guest)
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val _authLoading = MutableStateFlow(false)
    val authLoading: StateFlow<Boolean> = _authLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _authGateReason = MutableStateFlow(AuthGateReason.OPTIONAL)
    val authGateReason: StateFlow<AuthGateReason> = _authGateReason.asStateFlow()

    private val _passwordResetLoading = MutableStateFlow(false)
    val passwordResetLoading: StateFlow<Boolean> = _passwordResetLoading.asStateFlow()

    private val _passwordResetError = MutableStateFlow<String?>(null)
    val passwordResetError: StateFlow<String?> = _passwordResetError.asStateFlow()

    private val _passwordResetSent = MutableStateFlow(false)
    val passwordResetSent: StateFlow<Boolean> = _passwordResetSent.asStateFlow()

    private val _cloudSyncStatus = MutableStateFlow(CloudSyncStatus.IDLE)
    val cloudSyncStatus: StateFlow<CloudSyncStatus> = _cloudSyncStatus.asStateFlow()

    private val _cloudScans = MutableStateFlow<List<CloudScanRecord>>(emptyList())

    val remainingGuestScans: StateFlow<Int> = combine(preferences, _authState) { prefs, auth ->
        if (auth is AuthUiState.Authenticated) {
            GUEST_FREE_SCAN_LIMIT
        } else {
            (GUEST_FREE_SCAN_LIMIT - prefs.guestScanCount).coerceAtLeast(0)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GUEST_FREE_SCAN_LIMIT
    )

    val isScanLimitReached: StateFlow<Boolean> = combine(preferences, _authState) { prefs, auth ->
        auth !is AuthUiState.Authenticated && prefs.guestScanCount >= GUEST_FREE_SCAN_LIMIT
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    val allScans: StateFlow<List<ScanRecord>> = combine(
        container.scanRepository.allScans,
        _cloudScans,
        _authState
    ) { localScans, cloudScans, auth ->
        if (auth !is AuthUiState.Authenticated || cloudScans.isEmpty()) {
            localScans
        } else {
            mergeLocalAndCloudScans(localScans, cloudScans)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val insights: StateFlow<OrchardInsightsSummary> = allScans
        .map { container.insightsCalculator.calculate(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OrchardInsightsSummary()
        )

    val advisoryBundle: AdvisoryBundle = container.advisoryRepository.loadBundle()

    private val _modelHealth = MutableStateFlow(container.inferenceEngine.inspectModelHealth())
    val modelHealth: StateFlow<ModelHealthStatus> = _modelHealth.asStateFlow()

    private val _scanFlowState = MutableStateFlow<ScanFlowState>(ScanFlowState.Idle)
    val scanFlowState: StateFlow<ScanFlowState> = _scanFlowState.asStateFlow()

    private val _historyFilter = MutableStateFlow(HistoryFilterState())
    val historyFilter: StateFlow<HistoryFilterState> = _historyFilter.asStateFlow()

    val filteredHistory: StateFlow<List<ScanRecord>> = combine(allScans, _historyFilter) { scans, filter ->
        val now = System.currentTimeMillis()
        val startOfToday = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayMs = 24L * 60L * 60L * 1000L

        scans.filter { record ->
            val matchesClass = filter.selectedClass == null || record.predictedClass == filter.selectedClass
            val matchesDate = when (filter.dateFilter) {
                HistoryDateFilter.ALL -> true
                HistoryDateFilter.TODAY -> record.timestamp >= startOfToday
                HistoryDateFilter.LAST_7_DAYS -> record.timestamp >= now - 7L * dayMs
                HistoryDateFilter.LAST_30_DAYS -> record.timestamp >= now - 30L * dayMs
            }
            val q = filter.searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                record.predictedClass.displayNameEn.lowercase().contains(q) ||
                record.predictedClass.displayNameBn.lowercase().contains(q) ||
                record.orchardName.lowercase().contains(q) ||
                record.plotId.lowercase().contains(q) ||
                record.notes.lowercase().contains(q)

            matchesClass && matchesDate && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _storageBytes = MutableStateFlow(container.scanRepository.storageUsageBytes())
    val storageBytes: StateFlow<Long> = _storageBytes.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isImportingModel = MutableStateFlow(false)
    val isImportingModel: StateFlow<Boolean> = _isImportingModel.asStateFlow()

    private val _isVerifyingModel = MutableStateFlow(false)
    val isVerifyingModel: StateFlow<Boolean> = _isVerifyingModel.asStateFlow()

    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    private var updateCheckJob: Job? = null
    private var updateDownloadJob: Job? = null
    private var cloudProfileJob: Job? = null
    private var cloudScansJob: Job? = null
    private var activeObservedUid: String? = null
    private var migratedForUid: String? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private val saveMutex = Mutex()
    private val syntheticCloudDocIds = mutableMapOf<Long, String>()

    init {
        verifyAndPreloadModel()
        checkForUpdates(isManual = false)
        attachFirebaseAuthObserverIfAvailable()
    }

    private fun attachFirebaseAuthObserverIfAvailable() {
        if (!container.isFirebaseInitialized()) return
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            handleFirebaseAuthUserChange(firebaseAuth.currentUser)
        }
        authStateListener = listener
        runCatching { Firebase.auth.addAuthStateListener(listener) }
            .onFailure { Log.w("Auth", "Unable to attach FirebaseAuth listener", it) }
    }

    private fun handleFirebaseAuthUserChange(user: FirebaseUser?) {
        if (user == null) {
            cancelCloudListeners()
            activeObservedUid = null
            migratedForUid = null
            _authState.value = AuthUiState.Guest
            return
        }

        if (user.uid == activeObservedUid && _authState.value is AuthUiState.Authenticated) {
            return
        }

        // Wait while an explicit registration/sign-in coroutine is creating the profile
        if (_authLoading.value) return

        cancelCloudListeners()
        activeObservedUid = user.uid
        _authState.value = AuthUiState.CheckingProfile(
            uid = user.uid,
            email = user.email.orEmpty(),
            suggestedName = user.displayName.orEmpty(),
            photoUrl = user.photoUrl?.toString()
        )

        cloudProfileJob = viewModelScope.launch {
            val detectedProvider = when {
                user.providerData.any { it.providerId == "password" } -> AUTH_PROVIDER_PASSWORD
                user.providerData.any { it.providerId == "google.com" } -> AUTH_PROVIDER_GOOGLE
                else -> AUTH_PROVIDER_GOOGLE
            }
            val userEmail = user.email.orEmpty()
            if (userEmail.isNotBlank()) {
                val lockCheck = container.cloudAccountRepository.claimOrVerifyUniqueEmail(
                    uid = user.uid,
                    email = userEmail,
                    provider = detectedProvider
                )
                if (lockCheck is EmailUniquenessCheckResult.AlreadyUsedByOtherProvider ||
                    lockCheck is EmailUniquenessCheckResult.AlreadyUsedByAnotherAccount
                ) {
                    val existingProvider = when (lockCheck) {
                        is EmailUniquenessCheckResult.AlreadyUsedByOtherProvider -> lockCheck.existingProvider
                        is EmailUniquenessCheckResult.AlreadyUsedByAnotherAccount -> lockCheck.existingProvider
                        else -> ""
                    }
                    GoogleAuthHelper.rollbackDuplicateAccountAndSignOut(
                        context = null,
                        user = user,
                        deleteIfNewlyCreated = false
                    )
                    activeObservedUid = null
                    _authState.value = AuthUiState.Guest
                    _authError.value = formatEmailConflictMessage(existingProvider)
                    return@launch
                }
            }

            val profileResult = container.cloudAccountRepository.ensureDirectSignInProfile(
                uid = user.uid,
                displayName = user.displayName.orEmpty(),
                email = userEmail,
                photoUrl = user.photoUrl?.toString(),
                authProvider = detectedProvider
            )
            val profile = profileResult.getOrElse {
                com.guavacare.ai.domain.model.UserProfile(
                    id = user.uid,
                    userId = user.uid,
                    fullName = user.displayName?.takeIf { it.isNotBlank() }
                        ?: userEmail.substringBefore("@").ifBlank { "Guava Grower" },
                    email = userEmail,
                    authProvider = detectedProvider,
                    photoUrl = user.photoUrl?.toString(),
                    profileCompleted = true
                )
            }
            _authState.value = AuthUiState.Authenticated(profile)
            startAuthenticatedCloudSync(user.uid)
        }
    }

    private fun startAuthenticatedCloudSync(uid: String) {
        cloudProfileJob?.cancel()
        cloudScansJob?.cancel()
        _cloudSyncStatus.value = CloudSyncStatus.SYNCING

        // 1. Live profile listener (authenticated uid only)
        cloudProfileJob = viewModelScope.launch {
            try {
                container.cloudAccountRepository.observeUserProfile(uid).collect { profile ->
                    if (profile != null && profile.profileCompleted) {
                        _authState.value = AuthUiState.Authenticated(profile)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: FirebaseFirestoreException) {
                _cloudSyncStatus.value = CloudSyncStatus.ERROR
            } catch (e: Exception) {
                _cloudSyncStatus.value = CloudSyncStatus.ERROR
            }
        }

        // 2. Migrate any local guest scans (scans 1..3) + listen to cloud scans
        cloudScansJob = viewModelScope.launch {
            if (migratedForUid != uid) {
                val localScans = runCatching { container.scanRepository.allScans.first() }
                    .getOrDefault(emptyList())
                if (localScans.isNotEmpty()) {
                    val migrationResult = container.cloudAccountRepository.migrateLocalScansToCloud(uid, localScans)
                    if (migrationResult.isSuccess) {
                        migratedForUid = uid
                    }
                } else {
                    migratedForUid = uid
                }
            }

            try {
                container.cloudAccountRepository.observeCloudScans(uid).collect { scans ->
                    _cloudScans.value = scans
                    _cloudSyncStatus.value = CloudSyncStatus.SYNCED
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: FirebaseFirestoreException) {
                _cloudSyncStatus.value = CloudSyncStatus.ERROR
            } catch (e: Exception) {
                _cloudSyncStatus.value = CloudSyncStatus.ERROR
            }
        }
    }

    private fun cancelCloudListeners() {
        cloudProfileJob?.cancel()
        cloudProfileJob = null
        cloudScansJob?.cancel()
        cloudScansJob = null
        _cloudScans.value = emptyList()
        syntheticCloudDocIds.clear()
        _cloudSyncStatus.value = CloudSyncStatus.IDLE
    }

    fun setAuthGateReason(reason: AuthGateReason) {
        _authGateReason.value = reason
        _authError.value = null
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun clearPasswordResetState() {
        _passwordResetError.value = null
        _passwordResetSent.value = false
    }

    fun sendPasswordResetEmail(
        email: String,
        successToast: String
    ) {
        if (_passwordResetLoading.value) return
        viewModelScope.launch {
            _passwordResetLoading.value = true
            _passwordResetError.value = null
            _passwordResetSent.value = false
            when (val outcome = GoogleAuthHelper.sendPasswordResetEmail(email)) {
                PasswordResetOutcome.Success -> {
                    _passwordResetLoading.value = false
                    _passwordResetSent.value = true
                    _snackbarMessage.value = successToast
                }
                is PasswordResetOutcome.Failure -> {
                    _passwordResetLoading.value = false
                    _passwordResetError.value = outcome.message
                }
            }
        }
    }

    private fun formatEmailConflictMessage(existingProvider: String): String {
        val currentStrings = BilingualStrings.get(preferences.value.language)
        return when (existingProvider) {
            AUTH_PROVIDER_PASSWORD -> currentStrings.emailAlreadyUsedWithPasswordError
            AUTH_PROVIDER_GOOGLE -> currentStrings.emailAlreadyUsedWithGoogleError
            else -> currentStrings.emailAlreadyRegisteredError
        }
    }

    private suspend fun completeDirectAuthenticatedSession(
        user: FirebaseUser,
        resolvedEmail: String,
        authProvider: String,
        welcomeBackToast: String,
        onAuthenticated: () -> Unit
    ) {
        val profileResult = container.cloudAccountRepository.ensureDirectSignInProfile(
            uid = user.uid,
            displayName = user.displayName.orEmpty(),
            email = resolvedEmail,
            photoUrl = user.photoUrl?.toString(),
            authProvider = authProvider
        )
        val profile = profileResult.getOrElse {
            com.guavacare.ai.domain.model.UserProfile(
                id = user.uid,
                userId = user.uid,
                fullName = user.displayName?.takeIf { it.isNotBlank() }
                    ?: resolvedEmail.substringBefore("@").ifBlank { "Guava Grower" },
                email = resolvedEmail,
                authProvider = authProvider,
                photoUrl = user.photoUrl?.toString(),
                profileCompleted = true
            )
        }
        activeObservedUid = user.uid
        _authState.value = AuthUiState.Authenticated(profile)
        startAuthenticatedCloudSync(user.uid)
        _authLoading.value = false
        _snackbarMessage.value = welcomeBackToast
        onAuthenticated()
    }

    /**
     * Direct one-tap Google Sign-In — no Register form required.
     * Enforces one-email-one-account uniqueness across Google and Email/Password.
     */
    fun signInWithGoogle(
        activityContext: Context,
        welcomeBackToast: String,
        onAuthenticated: () -> Unit
    ) {
        if (_authLoading.value) return
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            when (val outcome = GoogleAuthHelper.signInWithGoogleButton(activityContext)) {
                is GoogleSignInOutcome.Success -> {
                    val user = outcome.user
                    val email = user.email.orEmpty()
                    when (
                        val lockCheck = container.cloudAccountRepository.claimOrVerifyUniqueEmail(
                            uid = user.uid,
                            email = email,
                            provider = AUTH_PROVIDER_GOOGLE
                        )
                    ) {
                        EmailUniquenessCheckResult.Allowed -> {
                            completeDirectAuthenticatedSession(
                                user = user,
                                resolvedEmail = email,
                                authProvider = AUTH_PROVIDER_GOOGLE,
                                welcomeBackToast = welcomeBackToast,
                                onAuthenticated = onAuthenticated
                            )
                        }

                        is EmailUniquenessCheckResult.AlreadyUsedByOtherProvider -> {
                            cancelCloudListeners()
                            GoogleAuthHelper.rollbackDuplicateAccountAndSignOut(
                                context = activityContext,
                                user = user,
                                deleteIfNewlyCreated = false
                            )
                            _authState.value = AuthUiState.Guest
                            _authLoading.value = false
                            _authError.value = formatEmailConflictMessage(lockCheck.existingProvider)
                        }

                        is EmailUniquenessCheckResult.AlreadyUsedByAnotherAccount -> {
                            cancelCloudListeners()
                            GoogleAuthHelper.rollbackDuplicateAccountAndSignOut(
                                context = activityContext,
                                user = user,
                                deleteIfNewlyCreated = true
                            )
                            _authState.value = AuthUiState.Guest
                            _authLoading.value = false
                            _authError.value = formatEmailConflictMessage(lockCheck.existingProvider)
                        }

                        is EmailUniquenessCheckResult.Failure -> {
                            // Firebase Auth already succeeded with Google; do not destroy the user's
                            // session on a transient Firestore/network/email_registry error.
                            Log.w(
                                "Auth",
                                "Proceeding with authenticated Google session after transient email_registry error: ${lockCheck.message}"
                            )
                            completeDirectAuthenticatedSession(
                                user = user,
                                resolvedEmail = email,
                                authProvider = AUTH_PROVIDER_GOOGLE,
                                welcomeBackToast = welcomeBackToast,
                                onAuthenticated = onAuthenticated
                            )
                        }
                    }
                }
                GoogleSignInOutcome.Cancelled -> {
                    _authLoading.value = false
                }
                is GoogleSignInOutcome.Failure -> {
                    _authLoading.value = false
                    _authError.value = if (outcome.isEmailCollision) {
                        BilingualStrings.get(preferences.value.language).emailAlreadyRegisteredError
                    } else {
                        outcome.message
                    }
                }
            }
        }
    }

    /**
     * Email & Password sign-in on LoginScreen.
     * Enforces one-email-one-account lock so a Google-bound email cannot be reused as a separate account.
     */
    fun signInWithEmailPassword(
        email: String,
        password: String,
        welcomeBackToast: String,
        onAuthenticated: () -> Unit
    ) {
        if (_authLoading.value) return
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null
            when (val outcome = GoogleAuthHelper.signInWithEmailPassword(email, password)) {
                is GoogleSignInOutcome.Success -> {
                    val user = outcome.user
                    val resolvedEmail = user.email ?: email.trim()
                    when (
                        val lockCheck = container.cloudAccountRepository.claimOrVerifyUniqueEmail(
                            uid = user.uid,
                            email = resolvedEmail,
                            provider = AUTH_PROVIDER_PASSWORD
                        )
                    ) {
                        EmailUniquenessCheckResult.Allowed -> {
                            completeDirectAuthenticatedSession(
                                user = user,
                                resolvedEmail = resolvedEmail,
                                authProvider = AUTH_PROVIDER_PASSWORD,
                                welcomeBackToast = welcomeBackToast,
                                onAuthenticated = onAuthenticated
                            )
                        }

                        is EmailUniquenessCheckResult.AlreadyUsedByOtherProvider -> {
                            cancelCloudListeners()
                            GoogleAuthHelper.rollbackDuplicateAccountAndSignOut(
                                context = null,
                                user = user,
                                deleteIfNewlyCreated = false
                            )
                            _authState.value = AuthUiState.Guest
                            _authLoading.value = false
                            _authError.value = formatEmailConflictMessage(lockCheck.existingProvider)
                        }

                        is EmailUniquenessCheckResult.AlreadyUsedByAnotherAccount -> {
                            cancelCloudListeners()
                            GoogleAuthHelper.rollbackDuplicateAccountAndSignOut(
                                context = null,
                                user = user,
                                deleteIfNewlyCreated = false
                            )
                            _authState.value = AuthUiState.Guest
                            _authLoading.value = false
                            _authError.value = formatEmailConflictMessage(lockCheck.existingProvider)
                        }

                        is EmailUniquenessCheckResult.Failure -> {
                            Log.w(
                                "Auth",
                                "Proceeding with authenticated password session after transient email_registry error: ${lockCheck.message}"
                            )
                            completeDirectAuthenticatedSession(
                                user = user,
                                resolvedEmail = resolvedEmail,
                                authProvider = AUTH_PROVIDER_PASSWORD,
                                welcomeBackToast = welcomeBackToast,
                                onAuthenticated = onAuthenticated
                            )
                        }
                    }
                }
                GoogleSignInOutcome.Cancelled -> {
                    _authLoading.value = false
                }
                is GoogleSignInOutcome.Failure -> {
                    _authLoading.value = false
                    _authError.value = outcome.message
                }
            }
        }
    }

    /**
     * Email & Password registration — collects Name, Email, Password, and Division/District/Upazila.
     * Strictly guarantees one email can only be used once across both Google and Email/Password.
     */
    fun registerWithEmailPassword(
        fullName: String,
        email: String,
        password: String,
        division: String,
        district: String,
        upazila: String,
        successToast: String,
        onRegistrationComplete: () -> Unit
    ) {
        if (_authLoading.value) return
        viewModelScope.launch {
            _authLoading.value = true
            _authError.value = null

            val authedUser: FirebaseUser = when (
                val outcome = GoogleAuthHelper.registerWithEmailPassword(email, password, fullName)
            ) {
                is GoogleSignInOutcome.Success -> outcome.user
                GoogleSignInOutcome.Cancelled -> {
                    _authLoading.value = false
                    return@launch
                }
                is GoogleSignInOutcome.Failure -> {
                    _authLoading.value = false
                    _authError.value = if (outcome.isEmailCollision) {
                        BilingualStrings.get(preferences.value.language).emailAlreadyRegisteredError
                    } else {
                        outcome.message
                    }
                    return@launch
                }
            }

            val resolvedEmail = authedUser.email ?: email.trim()
            val lockCheck = container.cloudAccountRepository.claimOrVerifyUniqueEmail(
                uid = authedUser.uid,
                email = resolvedEmail,
                provider = AUTH_PROVIDER_PASSWORD
            )
            when (lockCheck) {
                EmailUniquenessCheckResult.Allowed -> Unit
                is EmailUniquenessCheckResult.AlreadyUsedByOtherProvider -> {
                    GoogleAuthHelper.rollbackDuplicateAccountAndSignOut(
                        context = null,
                        user = authedUser,
                        deleteIfNewlyCreated = true
                    )
                    _authState.value = AuthUiState.Guest
                    _authLoading.value = false
                    _authError.value = formatEmailConflictMessage(lockCheck.existingProvider)
                    return@launch
                }
                is EmailUniquenessCheckResult.AlreadyUsedByAnotherAccount -> {
                    GoogleAuthHelper.rollbackDuplicateAccountAndSignOut(
                        context = null,
                        user = authedUser,
                        deleteIfNewlyCreated = true
                    )
                    _authState.value = AuthUiState.Guest
                    _authLoading.value = false
                    _authError.value = formatEmailConflictMessage(lockCheck.existingProvider)
                    return@launch
                }
                is EmailUniquenessCheckResult.Failure -> {
                    GoogleAuthHelper.rollbackDuplicateAccountAndSignOut(
                        context = null,
                        user = authedUser,
                        deleteIfNewlyCreated = true
                    )
                    _authState.value = AuthUiState.Guest
                    _authLoading.value = false
                    _authError.value = lockCheck.message
                    return@launch
                }
            }

            val saveResult = container.cloudAccountRepository.createOrCompleteUserProfile(
                uid = authedUser.uid,
                fullName = fullName,
                division = division,
                district = district,
                upazila = upazila,
                email = resolvedEmail,
                photoUrl = authedUser.photoUrl?.toString(),
                authProvider = AUTH_PROVIDER_PASSWORD
            )

            saveResult.onSuccess { profile ->
                activeObservedUid = authedUser.uid
                _authState.value = AuthUiState.Authenticated(profile)
                startAuthenticatedCloudSync(authedUser.uid)
                _authLoading.value = false
                _snackbarMessage.value = successToast
                onRegistrationComplete()
            }.onFailure { err ->
                _authLoading.value = false
                _authError.value = err.localizedMessage
                    ?: "Could not save your profile. Please check your connection and try again."
            }
        }
    }

    fun updateUserProfile(
        fullName: String,
        division: String,
        district: String,
        upazila: String,
        successToast: String
    ) {
        val currentAuth = _authState.value as? AuthUiState.Authenticated ?: return
        viewModelScope.launch {
            _authLoading.value = true
            val result = container.cloudAccountRepository.updateUserProfile(
                uid = currentAuth.profile.userId,
                fullName = fullName,
                division = division,
                district = district,
                upazila = upazila
            )
            _authLoading.value = false
            result.onSuccess {
                _authState.value = AuthUiState.Authenticated(
                    currentAuth.profile.copy(
                        fullName = fullName.trim(),
                        division = division.trim(),
                        district = district.trim(),
                        upazila = upazila.trim()
                    )
                )
                _snackbarMessage.value = successToast
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to update profile."
            }
        }
    }

    fun signOut(activityContext: Context, signedOutToast: String) {
        viewModelScope.launch {
            // Detach all active Firestore snapshot listeners BEFORE signing out
            cancelCloudListeners()
            activeObservedUid = null
            migratedForUid = null
            GoogleAuthHelper.signOutCleanly(activityContext)
            _authState.value = AuthUiState.Guest
            _snackbarMessage.value = signedOutToast
        }
    }

    private fun mergeLocalAndCloudScans(
        localScans: List<ScanRecord>,
        cloudScans: List<CloudScanRecord>
    ): List<ScanRecord> {
        val localKeys = localScans.map { CloudAccountRepository.deterministicScanKey(it) }.toSet()
        val localTimestamps = localScans.map { it.timestamp }.toSet()
        val merged = localScans.toMutableList()

        cloudScans.forEach { cloud ->
            val existsLocally = cloud.clientScanKey in localKeys ||
                cloud.id in localKeys ||
                localTimestamps.any { kotlin.math.abs(it - cloud.scannedAtMillis) < 1_000L }
            if (!existsLocally) {
                val syntheticId = -kotlin.math.abs(cloud.id.hashCode().toLong()).let {
                    if (it == 0L) 1L else it
                }
                syntheticCloudDocIds[syntheticId] = cloud.id.ifBlank { cloud.clientScanKey }
                val scoreMap = cloud.allScores.entries.associate { (k, v) ->
                    GuavaLeafClass.fromLabel(k) to v.toFloat()
                }
                merged += ScanRecord(
                    id = syntheticId,
                    imagePath = cloud.localImagePath.orEmpty(),
                    predictedClass = GuavaLeafClass.fromLabel(cloud.predictedClass),
                    decisionScore = cloud.decisionScore.toFloat(),
                    calibratedConfidence = cloud.calibratedConfidence?.toFloat(),
                    marginToRunnerUp = cloud.marginToRunnerUp.toFloat(),
                    allScoresJson = ScanRepository.encodeScoresJson(scoreMap),
                    inferenceTimeMs = cloud.inferenceTimeMs,
                    timestamp = cloud.scannedAtMillis,
                    orchardName = cloud.orchardName,
                    plotId = cloud.plotId,
                    notes = cloud.notes,
                    explanationMethod = cloud.explanationMethod,
                    isMockResult = cloud.mockResult
                )
            }
        }
        return merged.sortedByDescending { it.timestamp }
    }

    fun verifyAndPreloadModel() {
        viewModelScope.launch {
            _isVerifyingModel.value = true
            _modelHealth.value = container.inferenceEngine.preloadAndVerifyPipeline()
            _isVerifyingModel.value = false
        }
    }

    fun refreshDiagnostics() {
        _modelHealth.value = container.inferenceEngine.inspectModelHealth()
        _storageBytes.value = container.scanRepository.storageUsageBytes()
        if (!_modelHealth.value.isRuntimeWarm && _modelHealth.value.isProductionReady) {
            verifyAndPreloadModel()
        }
    }

    fun importTfliteModel(uri: Uri, successMessage: String) {
        viewModelScope.launch {
            _isImportingModel.value = true
            val result = container.inferenceEngine.importTfliteModel(uri)
            _isImportingModel.value = false
            result.onSuccess { (_, health) ->
                _modelHealth.value = health
                if (_scanFlowState.value is ScanFlowState.ModelMissing && health.isProductionReady) {
                    _scanFlowState.value = ScanFlowState.Idle
                }
                _snackbarMessage.value = successMessage
                verifyAndPreloadModel()
            }.onFailure { err ->
                _snackbarMessage.value = err.message ?: "Unable to import .tflite model."
            }
        }
    }

    fun removeImportedTfliteModel(removedMessage: String) {
        viewModelScope.launch {
            val health = container.inferenceEngine.removeImportedTfliteModel()
            _modelHealth.value = health
            _snackbarMessage.value = removedMessage
            verifyAndPreloadModel()
        }
    }

    fun lastInferenceLatencyMs(): Long? = container.inferenceEngine.lastMeasuredInferenceMs
    fun coldInitLatencyMs(): Long? = container.inferenceEngine.currentColdInitMs()

    fun createCaptureFile(): File = container.imageStorage.createTempCaptureFile()

    private fun cleanupTransientImageIfAny() {
        when (val current = _scanFlowState.value) {
            is ScanFlowState.Preview -> container.imageStorage.deleteImageSafely(current.imagePath)
            is ScanFlowState.NonLeafWarning -> container.imageStorage.deleteImageSafely(current.imagePath)
            else -> Unit
        }
    }

    fun resetScanState() {
        cleanupTransientImageIfAny()
        _scanFlowState.value = ScanFlowState.Idle
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun stageGalleryUriForPreview(uri: Uri) {
        viewModelScope.launch {
            cleanupTransientImageIfAny()
            _scanFlowState.value = ScanFlowState.Processing("quality")
            val imported = container.imageStorage.importFromUri(uri)
            imported.onFailure { err ->
                _scanFlowState.value = ScanFlowState.Error(
                    err.message ?: "Unable to open selected image."
                )
            }.onSuccess { (path, bitmap) ->
                val report = container.inferenceEngine.checkImageQuality(bitmap)
                if (report.isNonLeaf) {
                    _scanFlowState.value = ScanFlowState.NonLeafWarning(
                        imagePath = path,
                        bitmap = bitmap,
                        report = report
                    )
                } else {
                    _scanFlowState.value = ScanFlowState.Preview(imagePath = path, bitmap = bitmap)
                }
            }
        }
    }

    fun stageCapturedFileForPreview(file: File) {
        viewModelScope.launch {
            cleanupTransientImageIfAny()
            _scanFlowState.value = ScanFlowState.Processing("quality")
            val bitmap = container.imageStorage.loadOrientedBitmap(file.absolutePath)
            if (bitmap == null) {
                container.imageStorage.deleteImageSafely(file.absolutePath)
                _scanFlowState.value = ScanFlowState.Error("Unable to decode captured leaf image.")
                return@launch
            }
            val report = container.inferenceEngine.checkImageQuality(bitmap)
            if (report.isNonLeaf) {
                _scanFlowState.value = ScanFlowState.NonLeafWarning(
                    imagePath = file.absolutePath,
                    bitmap = bitmap,
                    report = report
                )
            } else {
                _scanFlowState.value = ScanFlowState.Preview(imagePath = file.absolutePath, bitmap = bitmap)
            }
        }
    }

    fun confirmPreviewAndAnalyze(onReadyNavigate: () -> Unit) {
        val current = _scanFlowState.value as? ScanFlowState.Preview ?: return
        viewModelScope.launch {
            _scanFlowState.value = ScanFlowState.Processing("quality")
            runInferenceOnBitmap(current.imagePath, current.bitmap, onReadyNavigate)
        }
    }

    fun discardPreview() {
        resetScanState()
    }

    fun analyzeGalleryUri(uri: Uri, onReadyNavigate: () -> Unit) {
        viewModelScope.launch {
            _scanFlowState.value = ScanFlowState.Processing("quality")
            val imported = container.imageStorage.importFromUri(uri)
            imported.onFailure { err ->
                _scanFlowState.value = ScanFlowState.Error(
                    err.message ?: "Unable to open selected image."
                )
            }.onSuccess { (path, bitmap) ->
                runInferenceOnBitmap(path, bitmap, onReadyNavigate)
            }
        }
    }

    fun analyzeCapturedFile(file: File, onReadyNavigate: () -> Unit) {
        viewModelScope.launch {
            _scanFlowState.value = ScanFlowState.Processing("quality")
            val bitmap = container.imageStorage.loadOrientedBitmap(file.absolutePath)
            if (bitmap == null) {
                container.imageStorage.deleteImageSafely(file.absolutePath)
                _scanFlowState.value = ScanFlowState.Error("Unable to decode captured leaf image.")
                return@launch
            }
            runInferenceOnBitmap(file.absolutePath, bitmap, onReadyNavigate)
        }
    }

    private suspend fun runInferenceOnBitmap(
        imagePath: String,
        bitmap: Bitmap,
        onReadyNavigate: () -> Unit
    ) {
        val prefs = preferences.value
        val outcome = container.inferenceEngine.analyzeLeaf(
            bitmap = bitmap,
            imagePath = imagePath,
            enforceQualityGate = prefs.strictQualityGate,
            allowDevMockMode = false,
            onStageChanged = { stage ->
                _scanFlowState.value = ScanFlowState.Processing(stage)
            }
        )

        when (outcome) {
            is PredictionOutcome.Success -> {
                val isAuthenticated = _authState.value is AuthUiState.Authenticated
                if (!isAuthenticated) {
                    container.preferencesRepository.incrementGuestScanCount()
                }
                // Auto-save guest scans (scans 1..3) so they migrate seamlessly to Firestore upon sign-in,
                // and auto-save authenticated scans when autoSaveScans is enabled.
                var savedId: Long? = null
                if (!isAuthenticated || prefs.autoSaveScans) {
                    savedId = persistPrediction(outcome.result, "", "", "")
                }
                _scanFlowState.value = ScanFlowState.Ready(
                    prediction = outcome.result,
                    originalBitmap = bitmap,
                    savedRecordId = savedId
                )
                refreshDiagnostics()
                onReadyNavigate()
            }
            is PredictionOutcome.QualityRejected -> {
                if (outcome.report.isNonLeaf) {
                    _scanFlowState.value = ScanFlowState.NonLeafWarning(
                        imagePath = imagePath,
                        bitmap = bitmap,
                        report = outcome.report
                    )
                } else {
                    container.imageStorage.deleteImageSafely(imagePath)
                    _scanFlowState.value = ScanFlowState.QualityRejected(imagePath, outcome.report)
                }
            }
            is PredictionOutcome.ModelAssetsMissing -> {
                container.imageStorage.deleteImageSafely(imagePath)
                _modelHealth.value = outcome.healthStatus
                _scanFlowState.value = ScanFlowState.ModelMissing(imagePath, outcome.healthStatus)
            }
            is PredictionOutcome.Error -> {
                container.imageStorage.deleteImageSafely(imagePath)
                _scanFlowState.value = ScanFlowState.Error(outcome.message)
            }
        }
    }

    fun saveOrUpdateCurrentDiagnosis(
        orchardName: String,
        plotId: String,
        notes: String,
        successToast: String
    ) {
        viewModelScope.launch {
            saveMutex.withLock {
                val current = _scanFlowState.value as? ScanFlowState.Ready ?: return@withLock
                val savedId = if (current.savedRecordId == null) {
                    persistPrediction(current.prediction, orchardName, plotId, notes)
                } else {
                    if (current.savedRecordId > 0L) {
                        container.scanRepository.updateScanMetadata(
                            id = current.savedRecordId,
                            orchardName = orchardName,
                            plotId = plotId,
                            notes = notes
                        )
                        syncUpdatedMetadataToCloudIfAuthenticated(
                            localId = current.savedRecordId,
                            orchardName = orchardName,
                            plotId = plotId,
                            notes = notes
                        )
                    } else {
                        val cloudDocId = syntheticCloudDocIds[current.savedRecordId]
                        val uid = (_authState.value as? AuthUiState.Authenticated)?.profile?.userId
                        if (cloudDocId != null && uid != null) {
                            container.cloudAccountRepository.updateCloudScanMetadata(
                                uid = uid,
                                scanDocId = cloudDocId,
                                orchardName = orchardName,
                                plotId = plotId,
                                notes = notes
                            )
                        }
                    }
                    current.savedRecordId
                }
                _scanFlowState.value = current.copy(
                    savedRecordId = savedId,
                    orchardName = orchardName.trim(),
                    plotId = plotId.trim(),
                    notes = notes.trim()
                )
                refreshDiagnostics()
                _snackbarMessage.value = successToast
            }
        }
    }

    private suspend fun persistPrediction(
        prediction: PredictionResult,
        orchardName: String,
        plotId: String,
        notes: String
    ): Long {
        val record = ScanRecord(
            imagePath = prediction.imagePath,
            predictedClass = prediction.predictedClass,
            decisionScore = prediction.decisionScore,
            calibratedConfidence = prediction.calibratedConfidence,
            marginToRunnerUp = prediction.marginToRunnerUp,
            allScoresJson = ScanRepository.encodeScoresJson(prediction.allScores),
            inferenceTimeMs = prediction.metrics.totalInferenceMs,
            timestamp = System.currentTimeMillis(),
            orchardName = orchardName.trim(),
            plotId = plotId.trim(),
            notes = notes.trim(),
            explanationMethod = prediction.explainability.method.name,
            isMockResult = prediction.isMockResult
        )
        val localId = container.scanRepository.saveScan(record)
        val uid = (_authState.value as? AuthUiState.Authenticated)?.profile?.userId
        if (uid != null) {
            val savedRecord = record.copy(id = localId)
            viewModelScope.launch {
                container.cloudAccountRepository.uploadScanIfAbsent(uid, savedRecord)
            }
        }
        return localId
    }

    private suspend fun syncUpdatedMetadataToCloudIfAuthenticated(
        localId: Long,
        orchardName: String,
        plotId: String,
        notes: String
    ) {
        val uid = (_authState.value as? AuthUiState.Authenticated)?.profile?.userId ?: return
        val localRecord = container.scanRepository.getScanById(localId) ?: return
        val docId = CloudAccountRepository.deterministicScanKey(localRecord)
        val updateResult = container.cloudAccountRepository.updateCloudScanMetadata(
            uid = uid,
            scanDocId = docId,
            orchardName = orchardName,
            plotId = plotId,
            notes = notes
        )
        if (updateResult.isFailure) {
            container.cloudAccountRepository.uploadScanIfAbsent(uid, localRecord, docId)
        }
    }

    fun reopenSavedScan(record: ScanRecord, onReadyNavigate: () -> Unit) {
        viewModelScope.launch {
            val bitmap = container.imageStorage.loadOrientedBitmap(record.imagePath)
            val explainability = withContext(Dispatchers.Default) {
                val fallbackMap = if (bitmap != null) {
                    HybridExplainabilityEngine().computeDevMockContrastMap(bitmap)
                } else {
                    ExplainabilityResult(
                        method = ExplanationMethodType.DEV_MOCK_VISUAL_CONTRAST,
                        heatmapBitmap = createBitmap(
                            ModelConstants.INPUT_IMAGE_SIZE,
                            ModelConstants.INPUT_IMAGE_SIZE,
                            Bitmap.Config.ARGB_8888
                        ),
                        gridWidth = 1,
                        gridHeight = 1,
                        generationTimeMs = 0L
                    )
                }
                val resolvedMethod = runCatching {
                    ExplanationMethodType.valueOf(record.explanationMethod)
                }.getOrDefault(fallbackMap.method)
                fallbackMap.copy(method = resolvedMethod)
            }

            val scores = ScanRepository.decodeScoresJson(record.allScoresJson)
                .ifEmpty { mapOf(record.predictedClass to record.decisionScore) }

            val prediction = PredictionResult(
                imagePath = record.imagePath,
                predictedClass = record.predictedClass,
                decisionScore = record.decisionScore,
                calibratedConfidence = record.calibratedConfidence,
                marginToRunnerUp = record.marginToRunnerUp,
                allScores = scores,
                strategy = SvmMulticlassStrategy.OVR,
                isLowMarginUncertain = record.marginToRunnerUp < ModelConstants.LOW_MARGIN_WARNING_THRESHOLD,
                qualityReport = ImageQualityReport(
                    isValid = true,
                    width = bitmap?.width ?: 0,
                    height = bitmap?.height ?: 0,
                    blurVariance = 0.0,
                    meanLuminance = 0.0,
                    foliageRatio = 0f,
                    issues = emptyList()
                ),
                explainability = explainability,
                metrics = InferenceMetrics(
                    qualityCheckMs = 0L,
                    preprocessingMs = 0L,
                    featureExtractionMs = record.inferenceTimeMs,
                    svmClassificationMs = 0L,
                    explainabilityMs = explainability.generationTimeMs,
                    totalInferenceMs = record.inferenceTimeMs,
                    coldInitTimeMs = null
                ),
                modelVersion = if (record.isMockResult) "DEV-MOCK (Saved Record)" else "Saved Record",
                isMockResult = record.isMockResult
            )

            _scanFlowState.value = ScanFlowState.Ready(
                prediction = prediction,
                originalBitmap = bitmap,
                savedRecordId = record.id,
                orchardName = record.orchardName,
                plotId = record.plotId,
                notes = record.notes
            )
            onReadyNavigate()
        }
    }

    fun updateSearchQuery(query: String) {
        _historyFilter.value = _historyFilter.value.copy(searchQuery = query)
    }

    fun updateClassFilter(leafClass: GuavaLeafClass?) {
        _historyFilter.value = _historyFilter.value.copy(selectedClass = leafClass)
    }

    fun updateDateFilter(filter: HistoryDateFilter) {
        _historyFilter.value = _historyFilter.value.copy(dateFilter = filter)
    }

    fun clearHistoryFilters() {
        _historyFilter.value = HistoryFilterState()
    }

    fun deleteScan(id: Long) {
        viewModelScope.launch {
            val uid = (_authState.value as? AuthUiState.Authenticated)?.profile?.userId
            if (id > 0L) {
                val existing = container.scanRepository.getScanById(id)
                container.scanRepository.deleteScan(id)
                if (uid != null && existing != null) {
                    val docId = CloudAccountRepository.deterministicScanKey(existing)
                    container.cloudAccountRepository.deleteCloudScan(uid, docId)
                }
            } else {
                val cloudDocId = syntheticCloudDocIds.remove(id)
                if (uid != null && cloudDocId != null) {
                    container.cloudAccountRepository.deleteCloudScan(uid, cloudDocId)
                }
            }
            refreshDiagnostics()
        }
    }

    fun deleteAllScans() {
        viewModelScope.launch {
            val uid = (_authState.value as? AuthUiState.Authenticated)?.profile?.userId
            if (uid != null) {
                val currentCloud = _cloudScans.value
                currentCloud.forEach { cloud ->
                    val docId = cloud.id.ifBlank { cloud.clientScanKey }
                    if (docId.isNotBlank()) {
                        container.cloudAccountRepository.deleteCloudScan(uid, docId)
                    }
                }
            }
            container.scanRepository.deleteAllScans()
            refreshDiagnostics()
        }
    }

    fun toggleLanguage() {
        val next = if (preferences.value.language == AppLanguage.ENGLISH) {
            AppLanguage.BANGLA
        } else {
            AppLanguage.ENGLISH
        }
        setLanguage(next)
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { container.preferencesRepository.setLanguage(language) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.preferencesRepository.setThemeMode(mode) }
    }

    fun setStrictQualityGate(enabled: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setStrictQualityGate(enabled) }
    }

    fun setShowLeafGuide(enabled: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setShowLeafGuide(enabled) }
    }

    fun setAutoSaveScans(enabled: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setAutoSaveScans(enabled) }
    }

    fun setDevMockMode(enabled: Boolean) {
        viewModelScope.launch { container.preferencesRepository.setDevMockMode(enabled) }
    }

    fun checkForUpdates(isManual: Boolean) {
        if (updateDownloadJob?.isActive == true) return
        val current = _updateState.value
        if (!isManual && (current is AppUpdateState.UpdateAvailable ||
                current is AppUpdateState.ReadyToInstall ||
                current is AppUpdateState.PermissionRequired)
        ) {
            return
        }

        updateCheckJob?.cancel()
        updateCheckJob = viewModelScope.launch {
            _updateState.value = AppUpdateState.Checking(isManual = isManual)
            val feedUrl = runCatching {
                container.preferencesRepository.preferencesFlow.first().updateFeedUrl
            }.getOrDefault(UpdateConstants.DEFAULT_UPDATE_FEED_URL)

            if (!container.updateRepository.isNetworkAvailable()) {
                _updateState.value = if (isManual) {
                    AppUpdateState.Error("No internet connection. Connect and try again.")
                } else {
                    AppUpdateState.Idle
                }
                return@launch
            }

            val result = container.updateRepository.fetchLatestRelease(feedUrl)
            result.onSuccess { release ->
                val hasNewer = AppVersionComparator.isRemoteNewer(
                    remoteVersionCode = release.versionCode,
                    remoteVersionName = release.versionName,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    currentVersionName = BuildConfig.VERSION_NAME
                )
                _updateState.value = if (hasNewer) {
                    AppUpdateState.UpdateAvailable(release)
                } else {
                    AppUpdateState.UpToDate(System.currentTimeMillis())
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                _updateState.value = if (isManual) {
                    AppUpdateState.Error(err.message ?: "Unable to check for updates.")
                } else {
                    AppUpdateState.Idle
                }
            }
        }
    }

    fun setUpdateFeedUrl(url: String, andCheck: Boolean = true) {
        viewModelScope.launch {
            container.preferencesRepository.setUpdateFeedUrl(url)
            if (andCheck) {
                checkForUpdates(isManual = true)
            }
        }
    }

    fun resetUpdateFeedUrl(andCheck: Boolean = true) {
        setUpdateFeedUrl(UpdateConstants.DEFAULT_UPDATE_FEED_URL, andCheck = andCheck)
    }

    fun startUpdateDownload() {
        val release = extractReleaseFromState(_updateState.value) ?: return
        if (updateDownloadJob?.isActive == true) return

        updateDownloadJob = viewModelScope.launch {
            _updateState.value = AppUpdateState.Downloading(
                release = release,
                bytesDownloaded = 0L,
                totalBytes = release.fileSizeBytes ?: -1L,
                progress = 0f
            )
            try {
                container.updateRepository.downloadApk(release).collect { event ->
                    when (event) {
                        is DownloadProgressEvent.Progress -> {
                            _updateState.value = AppUpdateState.Downloading(
                                release = release,
                                bytesDownloaded = event.bytesDownloaded,
                                totalBytes = event.totalBytes,
                                progress = event.progress
                            )
                        }
                        is DownloadProgressEvent.Completed -> {
                            val canInstall = container.apkInstallerHelper.canRequestPackageInstalls()
                            _updateState.value = if (canInstall) {
                                AppUpdateState.ReadyToInstall(release = release, apkFile = event.apkFile)
                            } else {
                                AppUpdateState.PermissionRequired(release = release, apkFile = event.apkFile)
                            }
                        }
                    }
                }
            } catch (ce: CancellationException) {
                _updateState.value = AppUpdateState.UpdateAvailable(release)
                throw ce
            } catch (t: Throwable) {
                _updateState.value = AppUpdateState.Error(
                    message = t.message ?: "Failed to download update APK.",
                    release = release,
                    canRetryDownload = true
                )
            }
        }
    }

    fun cancelUpdateDownload() {
        val currentRelease = extractReleaseFromState(_updateState.value)
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        container.updateRepository.clearCachedUpdates()
        _updateState.value = if (currentRelease != null) {
            AppUpdateState.UpdateAvailable(currentRelease)
        } else {
            AppUpdateState.Idle
        }
    }

    fun retryUpdateOrCheck() {
        val current = _updateState.value
        if (current is AppUpdateState.Error && current.release != null && current.canRetryDownload) {
            startUpdateDownload()
        } else {
            checkForUpdates(isManual = true)
        }
    }

    fun checkInstallPermissionAndAdvance() {
        val current = _updateState.value
        if (current is AppUpdateState.PermissionRequired &&
            container.apkInstallerHelper.canRequestPackageInstalls()
        ) {
            _updateState.value = AppUpdateState.ReadyToInstall(
                release = current.release,
                apkFile = current.apkFile
            )
        }
    }

    fun prepareInstallIntent(): Intent? {
        val (release, apkFile) = when (val current = _updateState.value) {
            is AppUpdateState.ReadyToInstall -> current.release to current.apkFile
            is AppUpdateState.PermissionRequired -> current.release to current.apkFile
            else -> return null
        }

        if (!apkFile.exists()) {
            _updateState.value = AppUpdateState.Error(
                message = "Downloaded APK missing. Please download again.",
                release = release,
                canRetryDownload = true
            )
            return null
        }

        if (!container.apkInstallerHelper.canRequestPackageInstalls()) {
            _updateState.value = AppUpdateState.PermissionRequired(release = release, apkFile = apkFile)
            return null
        }

        return container.apkInstallerHelper.createInstallApkIntent(apkFile)
    }

    fun createUnknownSourcesSettingsIntent(): Intent =
        container.apkInstallerHelper.createUnknownAppSourcesSettingsIntent()

    private fun extractReleaseFromState(state: AppUpdateState): AppReleaseInfo? = when (state) {
        is AppUpdateState.UpdateAvailable -> state.release
        is AppUpdateState.Downloading -> state.release
        is AppUpdateState.ReadyToInstall -> state.release
        is AppUpdateState.PermissionRequired -> state.release
        is AppUpdateState.Error -> state.release
        else -> null
    }

    override fun onCleared() {
        authStateListener?.let { listener ->
            runCatching {
                if (container.isFirebaseInitialized()) {
                    Firebase.auth.removeAuthStateListener(listener)
                }
            }
        }
        cancelCloudListeners()
        updateCheckJob?.cancel()
        updateDownloadJob?.cancel()
        super.onCleared()
        container.release()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(container) as T
        }
    }
}
