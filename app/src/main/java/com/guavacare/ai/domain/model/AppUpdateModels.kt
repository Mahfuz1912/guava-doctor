package com.guavacare.ai.domain.model

import java.io.File

data class AppReleaseInfo(
    val versionCode: Int?,
    val versionName: String,
    val apkUrl: String,
    val releaseNotesEn: String = "",
    val releaseNotesBn: String = "",
    val forceUpdate: Boolean = true,
    val fileSizeBytes: Long? = null,
    val publishedAt: String? = null
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data class Checking(val isManual: Boolean) : AppUpdateState
    data class UpToDate(val checkedAtMillis: Long) : AppUpdateState
    data class UpdateAvailable(val release: AppReleaseInfo) : AppUpdateState
    data class Downloading(
        val release: AppReleaseInfo,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progress: Float
    ) : AppUpdateState

    data class ReadyToInstall(
        val release: AppReleaseInfo,
        val apkFile: File
    ) : AppUpdateState

    data class PermissionRequired(
        val release: AppReleaseInfo,
        val apkFile: File
    ) : AppUpdateState

    data class Error(
        val message: String,
        val release: AppReleaseInfo? = null,
        val canRetryDownload: Boolean = false
    ) : AppUpdateState
}

fun AppUpdateState.isForceUpdateGateActive(): Boolean = when (this) {
    is AppUpdateState.UpdateAvailable -> release.forceUpdate
    is AppUpdateState.Downloading -> release.forceUpdate
    is AppUpdateState.ReadyToInstall -> release.forceUpdate
    is AppUpdateState.PermissionRequired -> release.forceUpdate
    is AppUpdateState.Error -> release?.forceUpdate == true
    AppUpdateState.Idle,
    is AppUpdateState.Checking,
    is AppUpdateState.UpToDate -> false
}

object AppVersionComparator {

    fun cleanVersionName(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V").trim()

    fun parseSemanticParts(raw: String): List<Int> {
        val core = cleanVersionName(raw)
            .substringBefore("+")
            .substringBefore("-")
            .trim()
        if (core.isEmpty()) return emptyList()
        val segments = core.split(".")
        val parsed = segments.mapNotNull { segment ->
            val digits = segment.takeWhile { it.isDigit() }
            digits.toIntOrNull()
        }
        return if (parsed.size == segments.size) parsed else emptyList()
    }

    /**
     * Returns > 0 if [remoteVersion] > [currentVersion], 0 if equal, < 0 if older or invalid.
     */
    fun compareSemanticVersions(remoteVersion: String, currentVersion: String): Int {
        val remoteParts = parseSemanticParts(remoteVersion)
        if (remoteParts.isEmpty()) return -1
        val currentParts = parseSemanticParts(currentVersion)
        if (currentParts.isEmpty()) return 1

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (idx in 0 until maxLen) {
            val r = remoteParts.getOrElse(idx) { 0 }
            val c = currentParts.getOrElse(idx) { 0 }
            if (r != c) return r.compareTo(c)
        }
        return 0
    }

    fun isRemoteNewer(
        remoteVersionCode: Int?,
        remoteVersionName: String,
        currentVersionCode: Int,
        currentVersionName: String
    ): Boolean {
        if (remoteVersionCode != null && remoteVersionCode > 0) {
            if (remoteVersionCode != currentVersionCode) {
                return remoteVersionCode > currentVersionCode
            }
        }
        return compareSemanticVersions(remoteVersionName, currentVersionName) > 0
    }
}
