package com.example.kthealthsync.domain.update

import android.content.Context
import android.content.Intent
import android.net.Uri
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

    companion object {
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours

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
            if (!force) {
                val lastCheckTime = preferencesManager.lastUpdateCheckTimeFlow.first()
                if (System.currentTimeMillis() - lastCheckTime < CHECK_INTERVAL_MS) {
                    return@launch
                }
            }

            _updateState.value = AppUpdateState.Checking
            try {
                val response = releaseService.getLatestRelease()
                if (response.isSuccessful) {
                    val release = response.body()
                    preferencesManager.saveLastUpdateCheckTime(System.currentTimeMillis())

                    if (release != null && VersionComparator.isNewer(currentVersion, release.tagName)) {
                        val displayVer = VersionComparator.formatDisplayVersion(release.tagName)
                        val downloadUrl = release.apkDownloadUrl
                        _updateState.value = AppUpdateState.Available(
                            release = release,
                            displayVersion = displayVer,
                            downloadUrl = downloadUrl
                        )

                        val dismissedTag = preferencesManager.dismissedUpdateTagFlow.first()
                        if (force || dismissedTag != release.tagName) {
                            _showPrompt.value = true
                        }
                    } else {
                        _updateState.value = AppUpdateState.UpToDate
                        _showPrompt.value = false
                    }
                } else if (response.code() == 404) {
                    // No releases published yet on the repo
                    preferencesManager.saveLastUpdateCheckTime(System.currentTimeMillis())
                    _updateState.value = AppUpdateState.UpToDate
                    _showPrompt.value = false
                } else {
                    _updateState.value = AppUpdateState.Error("HTTP ${response.code()}: ${response.message()}")
                }
            } catch (e: Exception) {
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

    fun openUpdate(context: Context, downloadUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
