package com.guavacare.ai.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.guavacare.ai.data.remote.OperationType
import com.guavacare.ai.data.remote.handleFirestoreError
import com.guavacare.ai.domain.model.AUTH_PROVIDER_GOOGLE
import com.guavacare.ai.domain.model.AUTH_PROVIDER_PASSWORD
import com.guavacare.ai.domain.model.CloudScanRecord
import com.guavacare.ai.domain.model.EmailUniquenessCheckResult
import com.guavacare.ai.domain.model.ScanRecord
import com.guavacare.ai.domain.model.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.Locale

/**
 * Cloud persistence repository for /users/{uid} and /users/{uid}/scans/{scanId}.
 * Always uses the provisioned named Firestore database via injected firestoreProvider.
 * Never issues Firestore queries or listeners unless an authenticated uid is provided.
 */
class CloudAccountRepository(
    private val firestoreProvider: () -> FirebaseFirestore
) {
    private val db: FirebaseFirestore
        get() = firestoreProvider()

    private fun userDocRef(uid: String) = db.collection("users").document(uid)
    private fun scansCollectionRef(uid: String) = userDocRef(uid).collection("scans")
    private fun emailLockDocRef(emailHash: String) = db.collection("email_registry").document(emailHash)

    /**
     * Enforces strict one-email-one-account uniqueness across Google Sign-In and Email/Password.
     * Uses an immutable SHA-256(lowercase email) lock document at /email_registry/{emailHash}
     * protected by Firestore Security Rules (isolated to request.auth.token.email).
     */
    suspend fun claimOrVerifyUniqueEmail(
        uid: String,
        email: String,
        provider: String
    ): EmailUniquenessCheckResult {
        val normalized = email.trim().lowercase(Locale.US)
        if (normalized.isBlank()) {
            return EmailUniquenessCheckResult.Failure("Account email address is missing.")
        }
        val hash = sha256EmailHash(normalized)
        val path = "email_registry/$hash"
        val lockRef = emailLockDocRef(hash)

        return try {
            val existingSnap = lockRef.get().await()
            if (existingSnap.exists()) {
                val lockedUid = existingSnap.getString("userId").orEmpty()
                val lockedProvider = existingSnap.getString("provider").orEmpty()
                return when {
                    lockedUid == uid && (lockedProvider == provider || lockedProvider.isBlank()) ->
                        EmailUniquenessCheckResult.Allowed
                    lockedUid == uid ->
                        EmailUniquenessCheckResult.AlreadyUsedByOtherProvider(lockedProvider)
                    else ->
                        EmailUniquenessCheckResult.AlreadyUsedByAnotherAccount(lockedProvider)
                }
            }

            // Also check existing /users/{uid} profile in case an account predated email_registry
            val existingProfileSnap = runCatching { userDocRef(uid).get().await() }.getOrNull()
            val existingProfileProvider = existingProfileSnap?.getString("authProvider").orEmpty()
            if (existingProfileProvider.isNotBlank() && existingProfileProvider != provider) {
                return EmailUniquenessCheckResult.AlreadyUsedByOtherProvider(existingProfileProvider)
            }

            // Atomically claim the immutable email lock (only succeeds if doc does not exist)
            val payload = mapOf<String, Any>(
                "userId" to uid,
                "provider" to provider,
                "createdAt" to FieldValue.serverTimestamp()
            )
            try {
                lockRef.set(payload).await()
                EmailUniquenessCheckResult.Allowed
            } catch (writeErr: FirebaseFirestoreException) {
                // Re-read in case a concurrent sign-in just claimed this email
                val afterSnap = runCatching { lockRef.get().await() }.getOrNull()
                if (afterSnap != null && afterSnap.exists()) {
                    val lockedUid = afterSnap.getString("userId").orEmpty()
                    val lockedProvider = afterSnap.getString("provider").orEmpty()
                    when {
                        lockedUid == uid && lockedProvider == provider ->
                            EmailUniquenessCheckResult.Allowed
                        lockedUid == uid ->
                            EmailUniquenessCheckResult.AlreadyUsedByOtherProvider(lockedProvider)
                        else ->
                            EmailUniquenessCheckResult.AlreadyUsedByAnotherAccount(lockedProvider)
                    }
                } else {
                    handleFirestoreError(writeErr, OperationType.CREATE, path)
                    EmailUniquenessCheckResult.Failure(
                        writeErr.localizedMessage ?: "Unable to verify email uniqueness."
                    )
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            // Even if email_registry lookup fails (e.g., transient network/rules lag), still check /users/{uid}
            val existingProfileSnap = runCatching { userDocRef(uid).get().await() }.getOrNull()
            val existingProfileProvider = existingProfileSnap?.getString("authProvider").orEmpty()
            if (existingProfileProvider.isNotBlank() && existingProfileProvider != provider) {
                EmailUniquenessCheckResult.AlreadyUsedByOtherProvider(existingProfileProvider)
            } else {
                EmailUniquenessCheckResult.Failure(
                    e.localizedMessage ?: "Unable to verify email uniqueness. Please check your connection."
                )
            }
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> =
        userDocRef(uid)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) {
                    null
                } else {
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { cause ->
                if (cause is CancellationException) throw cause
                val ex = (cause as? Exception) ?: Exception(cause)
                handleFirestoreError(ex, OperationType.GET, "users/$uid")
                if (cause is FirebaseFirestoreException) throw cause
                emit(null)
            }

    suspend fun getUserProfile(uid: String): Result<UserProfile?> {
        val path = "users/$uid"
        return try {
            val snap = userDocRef(uid).get().await()
            val profile = if (snap.exists()) {
                snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
            Result.success(profile)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    /**
     * Ensures a Firestore profile exists immediately for direct Google Sign-In (no Register screen needed).
     * Preserves any existing Division/District/Upazila if the user already has a profile.
     */
    suspend fun ensureDirectSignInProfile(
        uid: String,
        displayName: String,
        email: String,
        photoUrl: String?,
        authProvider: String = AUTH_PROVIDER_GOOGLE
    ): Result<UserProfile> {
        val path = "users/$uid"
        return try {
            val docRef = userDocRef(uid)
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                val existing = existingSnap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (existing != null) {
                    return Result.success(existing)
                }
            }
            val fallbackName = displayName.trim()
                .ifBlank { email.substringBefore("@").trim() }
                .ifBlank { "Guava Grower" }
                .take(100)
            val cleanEmail = email.trim().take(200)
            val cleanPhoto = photoUrl?.trim()?.take(500)?.takeIf { it.isNotBlank() }

            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "fullName" to fallbackName,
                "division" to "",
                "district" to "",
                "upazila" to "",
                "email" to cleanEmail,
                "authProvider" to authProvider,
                "photoUrl" to cleanPhoto,
                "profileCompleted" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()

            val created = docRef.get().await()
                .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(
                    id = uid,
                    userId = uid,
                    fullName = fallbackName,
                    division = "",
                    district = "",
                    upazila = "",
                    email = cleanEmail,
                    authProvider = authProvider,
                    photoUrl = cleanPhoto,
                    profileCompleted = true
                )
            Result.success(created)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun createOrCompleteUserProfile(
        uid: String,
        fullName: String,
        division: String,
        district: String,
        upazila: String,
        email: String,
        photoUrl: String?,
        authProvider: String = AUTH_PROVIDER_PASSWORD
    ): Result<UserProfile> {
        val path = "users/$uid"
        return try {
            val docRef = userDocRef(uid)
            val existing = docRef.get().await()
            val cleanName = fullName.trim().take(100)
            val cleanDivision = division.trim().take(80)
            val cleanDistrict = district.trim().take(80)
            val cleanUpazila = upazila.trim().take(80)
            val cleanEmail = email.trim().take(200)
            val cleanPhoto = photoUrl?.trim()?.take(500)?.takeIf { it.isNotBlank() }

            if (existing.exists()) {
                val updates = mutableMapOf<String, Any?>(
                    "fullName" to cleanName,
                    "division" to cleanDivision,
                    "district" to cleanDistrict,
                    "upazila" to cleanUpazila,
                    "email" to cleanEmail,
                    "authProvider" to authProvider,
                    "photoUrl" to cleanPhoto,
                    "profileCompleted" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updates).await()
            } else {
                val payload = mapOf<String, Any?>(
                    "userId" to uid,
                    "fullName" to cleanName,
                    "division" to cleanDivision,
                    "district" to cleanDistrict,
                    "upazila" to cleanUpazila,
                    "email" to cleanEmail,
                    "authProvider" to authProvider,
                    "photoUrl" to cleanPhoto,
                    "profileCompleted" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            }

            val saved = docRef.get().await()
                .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(
                    id = uid,
                    userId = uid,
                    fullName = cleanName,
                    division = cleanDivision,
                    district = cleanDistrict,
                    upazila = cleanUpazila,
                    email = cleanEmail,
                    authProvider = authProvider,
                    photoUrl = cleanPhoto,
                    profileCompleted = true
                )
            Result.success(saved)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserProfile(
        uid: String,
        fullName: String,
        division: String,
        district: String,
        upazila: String
    ): Result<Unit> {
        val path = "users/$uid"
        return try {
            val updates = mapOf<String, Any>(
                "fullName" to fullName.trim().take(100),
                "division" to division.trim().take(80),
                "district" to district.trim().take(80),
                "upazila" to upazila.trim().take(80),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userDocRef(uid).update(updates).await()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeCloudScans(uid: String): Flow<List<CloudScanRecord>> =
        scansCollectionRef(uid)
            .orderBy("scannedAtMillis", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    doc.toObject(CloudScanRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }
            .catch { cause ->
                if (cause is CancellationException) throw cause
                val ex = (cause as? Exception) ?: Exception(cause)
                handleFirestoreError(ex, OperationType.LIST, "users/$uid/scans")
                if (cause is FirebaseFirestoreException) throw cause
                emit(emptyList())
            }

    /**
     * Idempotently uploads a local ScanRecord to /users/{uid}/scans/{scanDocId}.
     * Uses deterministic clientScanKey so re-migrating guest scans never creates duplicates.
     */
    suspend fun uploadScanIfAbsent(
        uid: String,
        record: ScanRecord,
        clientScanKey: String = deterministicScanKey(record)
    ): Result<String> {
        val scanDocId = sanitizeDocId(clientScanKey)
        val path = "users/$uid/scans/$scanDocId"
        return try {
            val docRef = scansCollectionRef(uid).document(scanDocId)
            val existing = docRef.get().await()
            if (existing.exists()) {
                return Result.success(scanDocId)
            }
            val parsedScores = ScanRepository.decodeScoresJson(record.allScoresJson)
                .entries
                .take(10)
                .associate { (leafClass, score) ->
                    leafClass.canonicalName to score.toDouble()
                }
                .ifEmpty {
                    mapOf(record.predictedClass.canonicalName to record.decisionScore.toDouble())
                }
            val safeConfidence = record.calibratedConfidence?.toDouble()?.coerceIn(0.0, 1.0)
            val safeInferenceMs = record.inferenceTimeMs.coerceIn(0L, 600_000L)
            val safeTimestamp = record.timestamp.coerceAtLeast(1L)

            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "clientScanKey" to scanDocId,
                "predictedClass" to record.predictedClass.canonicalName,
                "decisionScore" to record.decisionScore.toDouble(),
                "calibratedConfidence" to safeConfidence,
                "marginToRunnerUp" to record.marginToRunnerUp.toDouble(),
                "allScores" to parsedScores,
                "inferenceTimeMs" to safeInferenceMs,
                "scannedAtMillis" to safeTimestamp,
                "orchardName" to record.orchardName.trim().take(120),
                "plotId" to record.plotId.trim().take(80),
                "notes" to record.notes.trim().take(1000),
                "explanationMethod" to record.explanationMethod.trim().take(80),
                "mockResult" to record.isMockResult,
                "localImagePath" to record.imagePath.trim().take(500).takeIf { it.isNotBlank() },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()
            Result.success(scanDocId)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCloudScanMetadata(
        uid: String,
        scanDocId: String,
        orchardName: String,
        plotId: String,
        notes: String
    ): Result<Unit> {
        val safeDocId = sanitizeDocId(scanDocId)
        val path = "users/$uid/scans/$safeDocId"
        return try {
            val updates = mapOf<String, Any>(
                "orchardName" to orchardName.trim().take(120),
                "plotId" to plotId.trim().take(80),
                "notes" to notes.trim().take(1000),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            scansCollectionRef(uid).document(safeDocId).update(updates).await()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCloudScan(uid: String, scanDocId: String): Result<Unit> {
        val safeDocId = sanitizeDocId(scanDocId)
        val path = "users/$uid/scans/$safeDocId"
        return try {
            scansCollectionRef(uid).document(safeDocId).delete().await()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun migrateLocalScansToCloud(
        uid: String,
        localScans: List<ScanRecord>
    ): Result<Int> {
        var uploaded = 0
        var lastError: Throwable? = null
        for (scan in localScans) {
            val res = uploadScanIfAbsent(uid, scan)
            if (res.isSuccess) {
                uploaded++
            } else {
                lastError = res.exceptionOrNull()
            }
        }
        return if (lastError != null && uploaded == 0 && localScans.isNotEmpty()) {
            Result.failure(lastError)
        } else {
            Result.success(uploaded)
        }
    }

    companion object {
        fun deterministicScanKey(record: ScanRecord): String =
            "scan_${record.timestamp.coerceAtLeast(1L)}_${record.id.coerceAtLeast(0L)}"

        fun sanitizeDocId(raw: String): String {
            val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
            return cleaned.ifBlank { "scan_${System.currentTimeMillis()}" }
        }

        fun sha256EmailHash(email: String): String {
            val normalized = email.trim().lowercase(Locale.US)
            val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
