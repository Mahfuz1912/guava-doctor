package com.guavacare.ai.data.remote

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    LIST,
    GET,
    WRITE
}

/**
 * Emits a structured diagnostic JSON log for every Firestore failure with auth context.
 */
fun handleFirestoreError(
    exception: Exception,
    operationType: OperationType,
    path: String?
) {
    val currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
    val providerArray = JSONArray().apply {
        currentUser?.providerData?.forEach { info ->
            put(
                JSONObject().apply {
                    put("providerId", info.providerId)
                    put("displayName", info.displayName)
                    put("email", info.email)
                    put("photoUrl", info.photoUrl?.toString())
                }
            )
        }
    }
    val errInfo = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.name.lowercase())
        put("path", path ?: JSONObject.NULL)
        put(
            "authInfo",
            JSONObject().apply {
                put("userId", currentUser?.uid ?: JSONObject.NULL)
                put("email", currentUser?.email ?: JSONObject.NULL)
                put("emailVerified", currentUser?.isEmailVerified ?: false)
                put("isAnonymous", currentUser?.isAnonymous ?: false)
                put("providerInfo", providerArray)
            }
        )
    }
    Log.e("FirestoreError", errInfo.toString(), exception)
}
