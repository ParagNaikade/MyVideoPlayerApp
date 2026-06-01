package com.youtube.mini.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.youtube.mini.data.LocalVideoRepository
import com.youtube.mini.data.ParentPrefs
import com.youtube.mini.data.VideoItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MiniTubeUiState(
    val videos: List<VideoItem> = emptyList(),
    val blockedIds: Set<Long> = emptySet(),
    val allowedIds: Set<Long> = emptySet(),
    val useAllowListMode: Boolean = false,
    val searchQuery: String = "",
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val selectedVideo: VideoItem? = null,
    val activeTab: BottomTab = BottomTab.Home,
    val hasPin: Boolean = false,
    val pinPromptVisible: Boolean = false,
    val parentPanelVisible: Boolean = false,
    val parentAuthError: String? = null,
    val failedAttempts: Int = 0,
    val lockoutUntilMs: Long = 0L,
    val sourceFolders: Set<String> = emptySet(),
)

data class ContinueWatchingItem(
    val video: VideoItem,
    val positionMs: Long,
    val updatedAtMs: Long,
)

enum class BottomTab {
    Home,
    Library,
    Parent,
}

class MiniTubeViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = LocalVideoRepository(application)
    private val parentPrefs = ParentPrefs(application)

    private var parentPanelAutoLockJob: Job? = null
    private var watchProgress: Map<Long, ParentPrefs.WatchProgressEntry> = emptyMap()

    private val _uiState = MutableStateFlow(MiniTubeUiState())
        private var sourceFolders: Set<String> = emptySet()
    val uiState: StateFlow<MiniTubeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            parentPrefs.sourceFolders.collectLatest { folders ->
                sourceFolders = folders
                _uiState.update { it.copy(sourceFolders = folders) }
                refreshVideos()
            }
        }
        viewModelScope.launch {
            parentPrefs.blockedVideoIds.collectLatest { blocked ->
                _uiState.update { it.copy(blockedIds = blocked) }
                recomputeContinueWatching()
            }
        }
        viewModelScope.launch {
            parentPrefs.allowedVideoIds.collectLatest { allowed ->
                _uiState.update { it.copy(allowedIds = allowed) }
                recomputeContinueWatching()
            }
        }
        viewModelScope.launch {
            parentPrefs.useAllowListMode.collectLatest { enabled ->
                _uiState.update { it.copy(useAllowListMode = enabled) }
                recomputeContinueWatching()
            }
        }
        viewModelScope.launch {
            parentPrefs.pinHash.collectLatest { hash ->
                _uiState.update { it.copy(hasPin = !hash.isNullOrBlank()) }
            }
        }
        viewModelScope.launch {
            parentPrefs.failedAttempts.collectLatest { attempts ->
                _uiState.update { it.copy(failedAttempts = attempts) }
            }
        }
        viewModelScope.launch {
            parentPrefs.lockoutUntilMs.collectLatest { until ->
                _uiState.update { it.copy(lockoutUntilMs = until) }
            }
        }
        viewModelScope.launch {
            parentPrefs.watchProgress.collectLatest { progress ->
                watchProgress = progress
                recomputeContinueWatching()
            }
        }
    }

    fun refreshVideos() {
        viewModelScope.launch {
                val videos = repo.getVideos(sourceFolders)
            _uiState.update { it.copy(videos = videos) }
            recomputeContinueWatching()
        }
    }

    fun play(video: VideoItem) {
        if (isVisibleToChild(video.id)) {
            _uiState.update { it.copy(selectedVideo = video) }
        }
    }

    fun closePlayer(positionMs: Long) {
        val video = _uiState.value.selectedVideo
        _uiState.update { it.copy(selectedVideo = null) }
        if (video != null && positionMs > 0L) {
            viewModelScope.launch {
                parentPrefs.saveWatchProgress(
                    videoId = video.id,
                    positionMs = positionMs.coerceAtMost(video.durationMs),
                    updatedAtMs = System.currentTimeMillis(),
                )
            }
        }
    }

    fun requestParentPanel() {
        val nowMs = System.currentTimeMillis()
        if (_uiState.value.lockoutUntilMs > nowMs) {
            _uiState.update { it.copy(parentAuthError = lockMessage(_uiState.value.lockoutUntilMs - nowMs)) }
            return
        }
        _uiState.update { it.copy(parentAuthError = null, activeTab = BottomTab.Parent) }
        if (_uiState.value.hasPin) {
            _uiState.update { it.copy(pinPromptVisible = true, parentPanelVisible = false) }
        } else {
            _uiState.update { it.copy(parentPanelVisible = true) }
        }
    }

    fun verifyParentPin(pin: String) {
        viewModelScope.launch {
            val nowMs = System.currentTimeMillis()
            if (_uiState.value.lockoutUntilMs > nowMs) {
                _uiState.update { it.copy(parentAuthError = lockMessage(_uiState.value.lockoutUntilMs - nowMs)) }
                return@launch
            }
            val valid = parentPrefs.isPinValid(pin)
            if (valid) {
                parentPrefs.clearFailedAttempts()
                _uiState.update {
                    it.copy(pinPromptVisible = false, parentPanelVisible = true, parentAuthError = null)
                }
                startParentPanelAutoLockTimer()
            } else {
                val lockoutUntil = parentPrefs.recordFailedAttempt(System.currentTimeMillis())
                _uiState.update {
                    val lockMsg = if (lockoutUntil > 0L) {
                        lockMessage(lockoutUntil - System.currentTimeMillis())
                    } else {
                        "Wrong PIN"
                    }
                    it.copy(
                        pinPromptVisible = false,
                        parentPanelVisible = false,
                        parentAuthError = lockMsg,
                    )
                }
            }
        }
    }

    fun createPin(pin: String) {
        viewModelScope.launch {
            parentPrefs.setPin(pin)
            _uiState.update { it.copy(parentPanelVisible = true, parentAuthError = null) }
            startParentPanelAutoLockTimer()
        }
    }

    fun setParentPanelVisible(visible: Boolean) {
        _uiState.update {
            it.copy(parentPanelVisible = visible, pinPromptVisible = false, parentAuthError = null)
        }
        if (visible) {
            startParentPanelAutoLockTimer()
        } else {
            parentPanelAutoLockJob?.cancel()
            parentPanelAutoLockJob = null
        }
    }

    fun selectTab(tab: BottomTab) {
        _uiState.update { it.copy(activeTab = tab) }
        if (tab == BottomTab.Parent) {
            requestParentPanel()
        }
    }

    fun setPinPromptVisible(visible: Boolean) {
        _uiState.update { it.copy(pinPromptVisible = visible, parentAuthError = null) }
    }

    fun setBlocked(videoId: Long, blocked: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value.blockedIds.toMutableSet()
            if (blocked) {
                current.add(videoId)
            } else {
                current.remove(videoId)
            }
            parentPrefs.setBlockedVideoIds(current)
            startParentPanelAutoLockTimer()
        }
    }

    fun setAllowed(videoId: Long, allowed: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value.allowedIds.toMutableSet()
            if (allowed) {
                current.add(videoId)
            } else {
                current.remove(videoId)
            }
            parentPrefs.setAllowedVideoIds(current)
            startParentPanelAutoLockTimer()
        }
    }

    fun setAllowListMode(enabled: Boolean) {
        viewModelScope.launch {
            parentPrefs.setUseAllowListMode(enabled)
            startParentPanelAutoLockTimer()
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSourceFolders(paths: Set<String>) {
        viewModelScope.launch {
            parentPrefs.setSourceFolders(paths)
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MiniTubeViewModel(application) as T
                }
            }
    }

    private fun lockMessage(remainingMs: Long): String {
        val remainingSec = (remainingMs / 1000L).coerceAtLeast(1L)
        val minutes = remainingSec / 60L
        val seconds = remainingSec % 60L
        return if (minutes > 0) {
            "Too many attempts. Try again in ${minutes}m ${seconds}s"
        } else {
            "Too many attempts. Try again in ${seconds}s"
        }
    }

    private fun startParentPanelAutoLockTimer() {
        parentPanelAutoLockJob?.cancel()
        parentPanelAutoLockJob = viewModelScope.launch {
            delay(90_000L)
            _uiState.update { it.copy(parentPanelVisible = false, pinPromptVisible = false) }
        }
    }

    private fun recomputeContinueWatching() {
        val state = _uiState.value
        val videosById = state.videos.associateBy { it.id }
        val visibleSet = state.videos.asSequence()
            .map { it.id }
            .filter { isVisibleToChild(it) }
            .toSet()

        val items = watchProgress.entries
            .asSequence()
            .filter { it.key in visibleSet }
            .mapNotNull { (id, progress) ->
                val video = videosById[id] ?: return@mapNotNull null
                if (progress.positionMs <= 0L || progress.positionMs >= video.durationMs) return@mapNotNull null
                ContinueWatchingItem(video, progress.positionMs, progress.updatedAtMs)
            }
            .sortedByDescending { it.updatedAtMs }
            .take(8)
            .toList()

        _uiState.update { it.copy(continueWatching = items) }
    }

    private fun isVisibleToChild(videoId: Long): Boolean {
        val state = _uiState.value
        return if (state.useAllowListMode) {
            videoId in state.allowedIds
        } else {
            videoId !in state.blockedIds
        }
    }
}
