package com.guavacare.ai.data.remote

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

sealed interface GoogleSignInOutcome {
    data class Success(val user: FirebaseUser) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Failure(
        val message: String,
        val isEmailCollision: Boolean = false
    ) : GoogleSignInOutcome
}

sealed interface PasswordResetOutcome {
    data object Success : PasswordResetOutcome
    data class Failure(val message: String) : PasswordResetOutcome
}

object GoogleAuthHelper {

    private const val TAG = "Auth"

    /**
     * Unwraps any ContextWrapper hierarchy (Compose / theme / locale wrappers) to find the host Activity.
     * Credential Manager and Firebase Auth OAuthProvider require an Activity context to launch UI flows.
     */
    internal tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    /**
     * Explicit interactive Google Sign-In button flow via Jetpack Credential Manager.
     * Primary path: single GetSignInWithGoogleOption request using R.string.default_web_client_id.
     * Automatic fallback: Firebase Auth OAuthProvider ("google.com") Custom Tab flow when Credential Manager
     * fails due to missing release SHA-1, NoCredentialException, or Play Services/OEM restrictions.
     */
    suspend fun signInWithGoogleButton(context: Context): GoogleSignInOutcome {
        if (runCatching { FirebaseApp.getApps(context).isEmpty() }.getOrDefault(false)) {
            return GoogleSignInOutcome.Failure(
                "Firebase is not initialized in this build. Please reinstall the latest app version."
            )
        }

        val activity = context.findActivity()
        val credentialContext: Context = activity ?: context

        val webClientId = runCatching {
            context.getString(R.string.default_web_client_id).trim()
        }.getOrDefault("")

        if (webClientId.isBlank()) {
            Log.w(TAG, "default_web_client_id is blank; attempting Firebase Web OAuth fallback.")
            return if (activity != null) {
                signInWithGoogleWebOAuth(activity)
            } else {
                GoogleSignInOutcome.Failure(
                    "Google Sign-In configuration is missing (default_web_client_id). Please update the app."
                )
            }
        }

        return try {
            val credentialManager = CredentialManager.create(credentialContext)
            val option = GetSignInWithGoogleOption.Builder(serverClientId = webClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val response = credentialManager.getCredential(
                context = credentialContext,
                request = request
            )
            exchangeGoogleCredential(response.credential)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            // On some OEMs or when a release APK's SHA-1 is not yet registered in Firebase Console,
            // Play Services reports config/reauth/cooldown errors inside GetCredentialCancellationException.
            if (activity != null && isPlayServicesConfigOrBlockedError(e.message)) {
                Log.w(TAG, "Detected Play Services/SHA-1 issue inside cancellation; falling back to Firebase Web OAuth.")
                signInWithGoogleWebOAuth(activity)
            } else {
                GoogleSignInOutcome.Cancelled
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential Manager failed (${e::class.java.simpleName}: ${e.message}); trying Firebase Web OAuth fallback.", e)
            if (activity != null) {
                val fallbackOutcome = signInWithGoogleWebOAuth(activity)
                if (fallbackOutcome is GoogleSignInOutcome.Failure) {
                    GoogleSignInOutcome.Failure(
                        message = fallbackOutcome.message,
                        isEmailCollision = fallbackOutcome.isEmailCollision
                    )
                } else {
                    fallbackOutcome
                }
            } else {
                GoogleSignInOutcome.Failure(mapCredentialManagerError(e))
            }
        } catch (e: FirebaseAuthUserCollisionException) {
            Log.w(TAG, "Google Sign-In blocked: email collision", e)
            GoogleSignInOutcome.Failure(
                message = mapFirebaseAuthError(e),
                isEmailCollision = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during Google Sign-In", e)
            GoogleSignInOutcome.Failure(mapFirebaseAuthError(e))
        }
    }

    /**
     * Official Firebase Auth federated Google OAuth fallback (Chrome Custom Tab).
     * Works seamlessly on devices where Play Services Credential Manager rejects an unregistered
     * release SHA-1, returns NoCredentialException, or has OEM (MIUI/HyperOS) credential restrictions.
     */
    private suspend fun signInWithGoogleWebOAuth(activity: Activity): GoogleSignInOutcome {
        return try {
            val pendingTask = Firebase.auth.pendingAuthResult
            val authResult = if (pendingTask != null) {
                pendingTask.await()
            } else {
                val provider = OAuthProvider.newBuilder("google.com")
                    .addCustomParameter("prompt", "select_account")
                    .build()
                Firebase.auth
                    .startActivityForSignInWithProvider(activity, provider)
                    .await()
            }
            val user = authResult.user
            if (user != null) {
                GoogleSignInOutcome.Success(user)
            } else {
                GoogleSignInOutcome.Failure("Unable to retrieve authenticated Google account.")
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: FirebaseAuthUserCollisionException) {
            Log.w(TAG, "Google Web OAuth blocked: email already in use", e)
            GoogleSignInOutcome.Failure(
                message = mapFirebaseAuthError(e),
                isEmailCollision = true
            )
        } catch (e: Exception) {
            if (isWebAuthCancelled(e)) {
                Log.w(TAG, "User cancelled Google Web OAuth flow", e)
                GoogleSignInOutcome.Cancelled
            } else {
                Log.e(TAG, "Google Web OAuth fallback failed", e)
                GoogleSignInOutcome.Failure(mapFirebaseAuthError(e))
            }
        }
    }

    /**
     * Email & Password sign-in using Firebase Authentication (enabled by project owner in Firebase Console).
     */
    suspend fun signInWithEmailPassword(
        email: String,
        password: String
    ): GoogleSignInOutcome {
        return try {
            val authResult = Firebase.auth
                .signInWithEmailAndPassword(email.trim(), password)
                .await()
            val user = authResult.user
            if (user != null) {
                GoogleSignInOutcome.Success(user)
            } else {
                GoogleSignInOutcome.Failure("Unable to retrieve signed-in account.")
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Email/Password sign-in failed", e)
            GoogleSignInOutcome.Failure(mapFirebaseAuthError(e))
        }
    }

    /**
     * Email & Password account creation with displayName update.
     */
    suspend fun registerWithEmailPassword(
        email: String,
        password: String,
        fullName: String
    ): GoogleSignInOutcome {
        return try {
            val authResult = Firebase.auth
                .createUserWithEmailAndPassword(email.trim(), password)
                .await()
            val user = authResult.user
            if (user != null) {
                val cleanName = fullName.trim()
                if (cleanName.isNotBlank()) {
                    runCatching {
                        val profileUpdate = UserProfileChangeRequest.Builder()
                            .setDisplayName(cleanName)
                            .build()
                        user.updateProfile(profileUpdate).await()
                    }
                }
                GoogleSignInOutcome.Success(user)
            } else {
                GoogleSignInOutcome.Failure("Account creation did not return a user.")
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: FirebaseAuthUserCollisionException) {
            Log.w(TAG, "Email/Password registration blocked: email already in use", e)
            GoogleSignInOutcome.Failure(
                message = mapFirebaseAuthError(e),
                isEmailCollision = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Email/Password registration failed", e)
            GoogleSignInOutcome.Failure(mapFirebaseAuthError(e))
        }
    }

    /**
     * Sends a password reset email via Firebase Authentication.
     */
    suspend fun sendPasswordResetEmail(email: String): PasswordResetOutcome {
        return try {
            val cleanEmail = email.trim()
            if (cleanEmail.isEmpty()) {
                return PasswordResetOutcome.Failure("Please enter your email address.")
            }
            Firebase.auth.sendPasswordResetEmail(cleanEmail).await()
            PasswordResetOutcome.Success
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Password reset email failed", e)
            PasswordResetOutcome.Failure(mapFirebaseAuthError(e))
        }
    }

    /**
     * Optional silent returning-user sign-in check using GetGoogleIdOption in its own request.
     */
    suspend fun attemptSilentAutoSignIn(context: Context): GoogleSignInOutcome {
        return try {
            val webClientId = context.getString(R.string.default_web_client_id).trim()
            if (webClientId.isBlank()) return GoogleSignInOutcome.Cancelled
            val credentialContext: Context = context.findActivity() ?: context
            val credentialManager = CredentialManager.create(credentialContext)
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(webClientId)
                .setFilterByAuthorizedAccounts(true)
                .setAutoSelectEnabled(true)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val response = credentialManager.getCredential(context = credentialContext, request = request)
            exchangeGoogleCredential(response.credential)
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Silent auto sign-in cancelled", e)
            GoogleSignInOutcome.Cancelled
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.d(TAG, "No authorized Google account available for silent sign-in: ${e.message}")
            GoogleSignInOutcome.Cancelled
        }
    }

    private suspend fun exchangeGoogleCredential(
        credential: androidx.credentials.Credential
    ): GoogleSignInOutcome {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
            val authResult = Firebase.auth.signInWithCredential(firebaseCred).await()
            val user = authResult.user
            return if (user != null) {
                GoogleSignInOutcome.Success(user)
            } else {
                GoogleSignInOutcome.Failure("Unable to retrieve authenticated Google account.")
            }
        }
        return GoogleSignInOutcome.Failure("Unsupported credential type returned by Google Sign-In.")
    }

    internal fun isPlayServicesConfigOrBlockedError(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val normalized = message.lowercase(Locale.US)
        return normalized.contains("28436") ||
            normalized.contains("[16]") ||
            normalized.contains("[10]") ||
            normalized.contains("reauth") ||
            normalized.contains("temporarily blocked") ||
            normalized.contains("developer console") ||
            normalized.contains("developer_error") ||
            normalized.contains("cannot find a matching credential") ||
            normalized.contains("no credential") ||
            normalized.contains("not authorized") ||
            normalized.contains("sha-1") ||
            normalized.contains("sha1")
    }

    internal fun isWebAuthCancelled(e: Throwable): Boolean {
        if (e is FirebaseAuthException && e.errorCode == "ERROR_WEB_CONTEXT_CANCELED") {
            return true
        }
        val msg = e.message?.lowercase(Locale.US).orEmpty()
        return msg.contains("web operation was canceled") ||
            msg.contains("web_context_canceled") ||
            msg.contains("cancelled by the user") ||
            msg.contains("canceled by the user")
    }

    internal fun mapCredentialManagerError(e: GetCredentialException): String {
        val raw = e.errorMessage?.toString().orEmpty().ifBlank { e.message.orEmpty() }
        val normalized = raw.lowercase(Locale.US)
        return when {
            normalized.contains("network") || normalized.contains("timeout") ->
                "Network error during Google Sign-In. Please check your internet connection and try again."
            isPlayServicesConfigOrBlockedError(raw) ->
                "Google Sign-In could not verify this device with Google Play Services. Please check your internet connection or try again."
            raw.isNotBlank() -> raw
            else -> "Google Sign-In could not be completed. Please try again."
        }
    }

    internal fun mapFirebaseAuthError(e: Exception): String = when (e) {
        is FirebaseAuthWeakPasswordException ->
            "Password is too weak. Please use at least 6 characters."
        is FirebaseAuthInvalidCredentialsException ->
            "Invalid email or password. Please check and try again."
        is FirebaseAuthInvalidUserException ->
            "No account found with this email. Please register first."
        is FirebaseAuthUserCollisionException ->
            "This email is already registered. Each email can only be used once — please sign in with your original method."
        is FirebaseNetworkException ->
            "Network error. Please check your internet connection and try again."
        is FirebaseAuthException -> when (e.errorCode) {
            "ERROR_OPERATION_NOT_ALLOWED" ->
                "Google Sign-In is currently disabled in Firebase Authentication settings."
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                "This email is already registered with another sign-in method. Please sign in with your original method."
            "ERROR_NETWORK_REQUEST_FAILED" ->
                "Network error. Please check your internet connection and try again."
            "ERROR_WEB_INTERNAL_ERROR", "ERROR_WEB_STORAGE_UNSUPPORTED" ->
                "Browser sign-in could not complete. Please ensure Chrome is enabled and try again."
            else -> e.localizedMessage ?: "Authentication failed. Please try again."
        }
        else ->
            e.localizedMessage ?: "Authentication failed. Please try again."
    }

    /**
     * Deletes a newly created duplicate Firebase Auth user (if applicable) and signs out cleanly.
     */
    suspend fun rollbackDuplicateAccountAndSignOut(
        context: Context?,
        user: FirebaseUser,
        deleteIfNewlyCreated: Boolean
    ) {
        withContext(NonCancellable) {
            if (deleteIfNewlyCreated) {
                val created = user.metadata?.creationTimestamp ?: 0L
                val lastSignIn = user.metadata?.lastSignInTimestamp ?: 0L
                val isBrandNew = created > 0L && kotlin.math.abs(lastSignIn - created) < 10_000L
                if (isBrandNew) {
                    runCatching { user.delete().await() }
                        .onFailure { err -> Log.w(TAG, "Unable to delete duplicate auth user", err) }
                }
            }
            if (context != null) {
                runCatching {
                    CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                }.onFailure { err ->
                    Log.w(TAG, "Failed to clear credential state during rollback", err)
                }
            }
            runCatching { Firebase.auth.signOut() }
                .onFailure { err -> Log.w(TAG, "Failed to sign out during rollback", err) }
        }
    }

    /**
     * Sign-out must clear Credential Manager state inside NonCancellable before Firebase.auth.signOut().
     * Caller should cancel active Firestore snapshot listeners BEFORE invoking this.
     */
    suspend fun signOutCleanly(context: Context) {
        withContext(NonCancellable) {
            runCatching {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            }.onFailure { err ->
                Log.w(TAG, "Failed to clear credential state on sign-out", err)
            }
            runCatching {
                Firebase.auth.signOut()
            }.onFailure { err ->
                Log.w(TAG, "Failed to sign out from Firebase Auth", err)
            }
        }
    }
}
