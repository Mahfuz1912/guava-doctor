package com.guavacare.ai.domain.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

const val GUEST_FREE_SCAN_LIMIT: Int = 3
const val AUTH_PROVIDER_GOOGLE: String = "google"
const val AUTH_PROVIDER_PASSWORD: String = "password"

sealed interface EmailUniquenessCheckResult {
    data object Allowed : EmailUniquenessCheckResult
    data class AlreadyUsedByOtherProvider(val existingProvider: String) : EmailUniquenessCheckResult
    data class AlreadyUsedByAnotherAccount(val existingProvider: String) : EmailUniquenessCheckResult
    data class Failure(val message: String) : EmailUniquenessCheckResult
}

/**
 * Firestore user profile document at /users/{userId}.
 * Field names match firestore.rules and firebase-blueprint.json 1:1.
 * No `is`-prefixed Boolean fields to prevent Firestore bean-naming mismatch.
 */
@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val division: String = "",
    val district: String = "",
    val upazila: String = "",
    val email: String = "",
    val authProvider: String = "",
    val photoUrl: String? = null,
    val profileCompleted: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

/**
 * Firestore synced scan document at /users/{userId}/scans/{scanId}.
 * Field names and types match firestore.rules and firebase-blueprint.json 1:1.
 */
@Keep
@IgnoreExtraProperties
data class CloudScanRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val clientScanKey: String = "",
    val predictedClass: String = GuavaLeafClass.HEALTHY.canonicalName,
    val decisionScore: Double = 0.0,
    val calibratedConfidence: Double? = null,
    val marginToRunnerUp: Double = 0.0,
    val allScores: Map<String, Double> = emptyMap(),
    val inferenceTimeMs: Long = 0L,
    val scannedAtMillis: Long = 0L,
    val orchardName: String = "",
    val plotId: String = "",
    val notes: String = "",
    val explanationMethod: String = "",
    val mockResult: Boolean = false,
    val localImagePath: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

sealed interface AuthUiState {
    data object Guest : AuthUiState
    data class CheckingProfile(
        val uid: String,
        val email: String,
        val suggestedName: String,
        val photoUrl: String?
    ) : AuthUiState

    data class NeedsRegistration(
        val uid: String,
        val email: String,
        val suggestedName: String,
        val photoUrl: String?
    ) : AuthUiState

    data class Authenticated(
        val profile: UserProfile
    ) : AuthUiState
}

enum class AuthGateReason {
    OPTIONAL,
    SCAN_LIMIT_REACHED
}

enum class CloudSyncStatus {
    IDLE,
    SYNCING,
    SYNCED,
    ERROR
}
