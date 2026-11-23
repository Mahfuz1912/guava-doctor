package com.guavacare.ai.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.guavacare.ai.core.constants.UpdateConstants
import com.guavacare.ai.core.utils.ApkInstallerHelper
import com.guavacare.ai.domain.model.AppReleaseInfo
import com.guavacare.ai.domain.model.AppVersionComparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface DownloadProgressEvent {
    data class Progress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progress: Float
    ) : DownloadProgressEvent

    data class Completed(val apkFile: File) : DownloadProgressEvent
}

class UpdateRepository(
    private val context: Context,
    private val checkClient: OkHttpClient = defaultCheckClient(),
    private val downloadClient: OkHttpClient = defaultDownloadClient()
) {
    private val updatesDir: File
        get() = File(context.cacheDir, UpdateConstants.UPDATES_CACHE_SUBDIR).apply { mkdirs() }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun fetchLatestRelease(feedUrl: String): Result<AppReleaseInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val targetUrl = feedUrl.trim()
                require(targetUrl.startsWith("https://") || targetUrl.startsWith("http://")) {
                    "Invalid update feed URL."
                }
                val request = Request.Builder()
                    .url(targetUrl)
                    .header("Accept", "application/json")
                    .header("User-Agent", UpdateConstants.USER_AGENT)
                    .get()
                    .build()

                checkClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: Unable to fetch update info.")
                    }
                    val body = response.body?.string()?.trim().orEmpty()
                    if (body.isEmpty()) {
                        throw IOException("Empty update feed response.")
                    }
                    parseReleaseJson(body)
                }
            }
        }

    fun downloadApk(release: AppReleaseInfo): Flow<DownloadProgressEvent> = flow {
        val targetUrl = release.apkUrl.trim()
        require(targetUrl.startsWith("https://") || targetUrl.startsWith("http://")) {
            "Invalid APK download URL."
        }

        val dir = updatesDir
        clearCachedUpdatesExcept(null)
        val tmpFile = File(dir, "${UpdateConstants.UPDATE_APK_FILE_NAME}.tmp")
        val finalFile = File(dir, UpdateConstants.UPDATE_APK_FILE_NAME)

        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", UpdateConstants.USER_AGENT)
            .get()
            .build()

        try {
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed with HTTP ${response.code}.")
                }
                val body = response.body ?: throw IOException("Empty APK response body.")
                val contentLength = body.contentLength().takeIf { it > 0L }
                    ?: (release.fileSizeBytes ?: -1L)

                var downloaded = 0L
                var lastEmitBytes = 0L
                val buffer = ByteArray(8192)

                body.byteStream().use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        while (true) {
                            if (!currentCoroutineContext().isActive) {
                                throw IOException("Download cancelled.")
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            if (downloaded - lastEmitBytes >= 49_152L ||
                                (contentLength > 0L && downloaded == contentLength)
                            ) {
                                lastEmitBytes = downloaded
                                val pct = if (contentLength > 0L) {
                                    (downloaded.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                                emit(
                                    DownloadProgressEvent.Progress(
                                        bytesDownloaded = downloaded,
                                        totalBytes = contentLength,
                                        progress = pct
                                    )
                                )
                            }
                        }
                        output.flush()
                    }
                }

                if (!ApkInstallerHelper.isValidApkArchive(tmpFile)) {
                    tmpFile.delete()
                    throw IOException("Downloaded file is not a valid Android APK package.")
                }

                if (finalFile.exists()) {
                    finalFile.delete()
                }
                if (!tmpFile.renameTo(finalFile)) {
                    tmpFile.copyTo(finalFile, overwrite = true)
                    tmpFile.delete()
                }

                val finalSize = finalFile.length()
                emit(
                    DownloadProgressEvent.Progress(
                        bytesDownloaded = finalSize,
                        totalBytes = finalSize,
                        progress = 1f
                    )
                )
                emit(DownloadProgressEvent.Completed(finalFile))
            }
        } catch (t: Throwable) {
            tmpFile.delete()
            throw t
        }
    }.flowOn(Dispatchers.IO)

    fun clearCachedUpdates() {
        clearCachedUpdatesExcept(null)
    }

    private fun clearCachedUpdatesExcept(keep: File?) {
        updatesDir.listFiles()?.forEach { child ->
            if (keep == null || child.canonicalPath != keep.canonicalPath) {
                child.delete()
            }
        }
    }

    internal fun parseReleaseJson(rawJson: String): AppReleaseInfo {
        val root = JSONObject(rawJson)
        val isGitHubRelease = root.has("tag_name") || root.has("assets")

        return if (isGitHubRelease) {
            parseGitHubRelease(root)
        } else {
            parseCustomVersionJson(root)
        }
    }

    private fun parseGitHubRelease(root: JSONObject): AppReleaseInfo {
        if (root.optBoolean("draft", false)) {
            throw IOException("Latest GitHub release is still a draft.")
        }
        val tagName = root.optString("tag_name").trim()
            .ifEmpty { root.optString("name").trim() }
        val cleanName = AppVersionComparator.cleanVersionName(tagName)
        val body = root.optString("body").trim()

        val embeddedVersionCode = VERSION_CODE_REGEX.find(body)
            ?.groupValues?.getOrNull(1)
            ?.toIntOrNull()
        val versionCode = root.optInt("versionCode", -1).takeIf { it > 0 } ?: embeddedVersionCode

        val forceOverride = FORCE_UPDATE_REGEX.find(body)
            ?.groupValues?.getOrNull(1)
            ?.lowercase()
            ?.let { it != "false" } ?: true

        val assets = root.optJSONArray("assets")
        var apkUrl = ""
        var apkSize: Long? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val assetName = asset.optString("name").trim()
                val contentType = asset.optString("content_type").trim()
                if (assetName.endsWith(".apk", ignoreCase = true) ||
                    contentType.equals("application/vnd.android.package-archive", ignoreCase = true)
                ) {
                    apkUrl = asset.optString("browser_download_url").trim()
                    apkSize = asset.optLong("size", -1L).takeIf { it > 0L }
                    if (apkUrl.isNotEmpty()) break
                }
            }
        }

        if (apkUrl.isEmpty()) {
            throw IOException("No .apk asset found in latest GitHub release.")
        }
        if (cleanName.isEmpty() && versionCode == null) {
            throw IOException("Release version tag missing in GitHub release.")
        }

        val (notesEn, notesBn) = com.guavacare.ai.domain.model.ReleaseNotesFormatter.splitBilingualBody(body)

        return AppReleaseInfo(
            versionCode = versionCode,
            versionName = cleanName.ifEmpty { versionCode.toString() },
            apkUrl = apkUrl,
            releaseNotesEn = notesEn,
            releaseNotesBn = notesBn,
            forceUpdate = forceOverride,
            fileSizeBytes = apkSize,
            publishedAt = root.optString("published_at").trim().ifEmpty { null }
        )
    }

    private fun parseCustomVersionJson(root: JSONObject): AppReleaseInfo {
        val versionCode = root.optInt("versionCode", -1).takeIf { it > 0 }
        val rawVersionName = root.optString("versionName").trim()
            .ifEmpty { root.optString("version").trim() }
        val cleanName = AppVersionComparator.cleanVersionName(rawVersionName)

        val apkUrl = root.optString("apkUrl").trim()
            .ifEmpty { root.optString("downloadUrl").trim() }
            .ifEmpty { root.optString("url").trim() }

        val notesEn = root.optString("releaseNotesEn").trim()
            .ifEmpty { root.optString("releaseNotes").trim() }
            .ifEmpty { root.optString("changelog").trim() }
        val notesBn = root.optString("releaseNotesBn").trim()

        val forceUpdate = if (root.has("forceUpdate")) {
            root.optBoolean("forceUpdate", true)
        } else {
            true
        }

        val sizeBytes = root.optLong("fileSizeBytes", -1L).takeIf { it > 0L }
            ?: root.optLong("size", -1L).takeIf { it > 0L }

        val publishedAt = root.optString("publishedAt").trim().ifEmpty { null }

        if (apkUrl.isEmpty()) {
            throw IOException("Missing apkUrl in update JSON.")
        }
        if (versionCode == null && cleanName.isEmpty()) {
            throw IOException("Missing versionCode / versionName in update JSON.")
        }

        return AppReleaseInfo(
            versionCode = versionCode,
            versionName = cleanName.ifEmpty { versionCode.toString() },
            apkUrl = apkUrl,
            releaseNotesEn = notesEn,
            releaseNotesBn = notesBn,
            forceUpdate = forceUpdate,
            fileSizeBytes = sizeBytes,
            publishedAt = publishedAt
        )
    }

    companion object {
        private val VERSION_CODE_REGEX =
            Regex("""(?:versionCode|version_code)\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
        private val FORCE_UPDATE_REGEX =
            Regex("""(?:forceUpdate|force_update)\s*[:=]\s*(true|false)""", RegexOption.IGNORE_CASE)

        private fun defaultCheckClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        private fun defaultDownloadClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
