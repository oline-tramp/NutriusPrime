package com.example.kthealthsync.domain.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.kthealthsync.BuildConfig
import com.example.kthealthsync.PreferencesManager
import com.example.kthealthsync.data.remote.GitHubRelease
import com.example.kthealthsync.data.remote.GitHubReleaseService
import com.example.kthealthsync.domain.util.VersionComparator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface AppUpdateState {
    object Idle : AppUpdateState
    object Checking : AppUpdateState
    data class Available(
        val release: GitHubRelease,
        val displayVersion: String,
        val downloadUrl: String
    ) : AppUpdateState
    object UpToDate : AppUpdateState
    data class Error(val message: String?) : AppUpdateState
}

sealed interface DownloadState {
    object Idle : DownloadState
    data class Downloading(val progressPercent: Int) : DownloadState
    data class ReadyToInstall(val apkFile: File) : DownloadState
    data class Error(val message: String) : DownloadState
}

class AppUpdateManager(
    private val preferencesManager: PreferencesManager,
    private val releaseService: GitHubReleaseService = GitHubReleaseService.create(),
    private val currentVersion: String = BuildConfig.VERSION_NAME,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    private val _showPrompt = MutableStateFlow(false)
    val showPrompt: StateFlow<Boolean> = _showPrompt.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    init {
        scope.launch {
            try {
                val cachedTag = preferencesManager.cachedUpdateTagFlow.first()
                val cachedUrl = preferencesManager.cachedUpdateUrlFlow.first()
                val dismissedTag = preferencesManager.dismissedUpdateTagFlow.first()

                if (cachedTag.isNotBlank()) {
                    if (VersionComparator.isNewer(currentVersion, cachedTag)) {
                        val displayVer = VersionComparator.formatDisplayVersion(cachedTag)
                        _updateState.value = AppUpdateState.Available(
                            release = GitHubRelease(
                                tagName = cachedTag,
                                name = null,
                                body = null,
                                htmlUrl = cachedUrl,
                                publishedAt = null,
                                assets = null
                            ),
                            displayVersion = displayVer,
                            downloadUrl = cachedUrl
                        )
                        if (dismissedTag != cachedTag) {
                            _showPrompt.value = true
                        }
                    } else {
                        // Already updated to this version or newer! Clear cache and banner
                        Log.d(TAG, "Cached update $cachedTag already installed (current=$currentVersion). Clearing cache.")
                        preferencesManager.saveCachedUpdate("", "")
                        preferencesManager.saveDismissedUpdateTag("")
                        _updateState.value = AppUpdateState.UpToDate
                        _showPrompt.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load cached update", e)
            }
        }
    }

    companion object {
        private const val TAG = "AppUpdateManager"
        private const val CHECK_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes

        @Volatile
        private var instance: AppUpdateManager? = null

        fun getInstance(context: Context): AppUpdateManager {
            return instance ?: synchronized(this) {
                instance ?: AppUpdateManager(PreferencesManager(context.applicationContext)).also {
                    instance = it
                }
            }
        }
    }

    fun checkForUpdates(force: Boolean = false) {
        scope.launch {
            if (!force && _updateState.value !is AppUpdateState.Idle) {
                val lastCheckTime = preferencesManager.lastUpdateCheckTimeFlow.first()
                if (System.currentTimeMillis() - lastCheckTime < CHECK_INTERVAL_MS) {
                    Log.d(TAG, "Skipping update check: last check was ${(System.currentTimeMillis() - lastCheckTime) / 1000}s ago")
                    return@launch
                }
            }

            Log.d(TAG, "Starting update check (force=$force, currentVersion=$currentVersion)")
            _updateState.value = AppUpdateState.Checking
            try {
                val response = releaseService.getLatestRelease()
                if (response.isSuccessful) {
                    val release = response.body()
                    Log.d(TAG, "Received GitHub release: ${release?.tagName}")
                    preferencesManager.saveLastUpdateCheckTime(System.currentTimeMillis())

                    if (release != null && VersionComparator.isNewer(currentVersion, release.tagName)) {
                        val displayVer = VersionComparator.formatDisplayVersion(release.tagName)
                        val downloadUrl = release.apkDownloadUrl
                        Log.d(TAG, "Newer version available: $displayVer, downloadUrl=$downloadUrl")
                        preferencesManager.saveCachedUpdate(release.tagName, downloadUrl)
                        _updateState.value = AppUpdateState.Available(
                            release = release,
                            displayVersion = displayVer,
                            downloadUrl = downloadUrl
                        )

                        val dismissedTag = preferencesManager.dismissedUpdateTagFlow.first()
                        if (force || dismissedTag != release.tagName) {
                            Log.d(TAG, "Displaying update prompt banner")
                            _showPrompt.value = true
                        } else {
                            Log.d(TAG, "Update prompt dismissed previously for $dismissedTag")
                        }
                    } else {
                        Log.d(TAG, "App is up to date (current=$currentVersion, remote=${release?.tagName})")
                        preferencesManager.saveCachedUpdate("", "")
                        _updateState.value = AppUpdateState.UpToDate
                        _showPrompt.value = false
                    }
                } else if (response.code() == 404) {
                    Log.d(TAG, "GitHub release 404 (no releases found)")
                    preferencesManager.saveLastUpdateCheckTime(System.currentTimeMillis())
                    _updateState.value = AppUpdateState.UpToDate
                    _showPrompt.value = false
                } else {
                    Log.e(TAG, "GitHub release error HTTP ${response.code()}: ${response.message()}")
                    _updateState.value = AppUpdateState.Error("HTTP ${response.code()}: ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed: ${e.message}", e)
                _updateState.value = AppUpdateState.Error(e.localizedMessage ?: "Network error")
            }
        }
    }

    fun dismissPrompt() {
        _showPrompt.value = false
        val state = _updateState.value
        if (state is AppUpdateState.Available) {
            scope.launch {
                preferencesManager.saveDismissedUpdateTag(state.release.tagName)
            }
        }
    }

    fun downloadAndInstall(context: Context, downloadUrl: String, displayVersion: String) {
        if (_downloadState.value is DownloadState.Downloading) return

        scope.launch {
            _downloadState.value = DownloadState.Downloading(0)
            try {
                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                // Clean up any old downloaded APK files
                updatesDir.listFiles()?.forEach { it.delete() }

                val targetFile = File(updatesDir, "NutriusPrime-$displayVersion.apk")
                Log.d(TAG, "Downloading $downloadUrl to ${targetFile.absolutePath}")

                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()

                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "NutriusPrime-Android")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }
                    val body = response.body ?: throw IOException("Empty response body")
                    val contentLength = body.contentLength()

                    body.byteStream().use { input ->
                        targetFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var totalBytesRead = 0L
                            var lastReportedPercent = -1

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                                if (contentLength > 0) {
                                    val percent = ((totalBytesRead * 100) / contentLength).toInt().coerceIn(0, 100)
                                    if (percent != lastReportedPercent) {
                                        lastReportedPercent = percent
                                        _downloadState.value = DownloadState.Downloading(percent)
                                    }
                                }
                            }
                            output.flush()
                        }
                    }
                }

                Log.d(TAG, "Download finished successfully, launching installer")
                _downloadState.value = DownloadState.ReadyToInstall(targetFile)

                withContext(Dispatchers.Main) {
                    installApk(context, targetFile)
                    // Hide the prompt once installation is started
                    _showPrompt.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Native download failed", e)
                _downloadState.value = DownloadState.Error(e.localizedMessage ?: "Download failed")
                withContext(Dispatchers.Main) {
                    // Fallback to browser if native streaming failed
                    openUpdateInBrowser(context, downloadUrl)
                }
            }
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
        }
    }

    fun openUpdateInBrowser(context: Context, downloadUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
