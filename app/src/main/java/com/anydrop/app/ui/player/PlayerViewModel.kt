package com.anydrop.app.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.data.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _currentMedia = MutableStateFlow<MediaItem?>(null)
    val currentMedia: StateFlow<MediaItem?> = _currentMedia.asStateFlow()

    private val _allMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    val allMedia: StateFlow<List<MediaItem>> = _allMedia.asStateFlow()

    private val _nextMediaId = MutableStateFlow<String?>(null)
    val nextMediaId: StateFlow<String?> = _nextMediaId.asStateFlow()

    private val _previousMediaId = MutableStateFlow<String?>(null)
    val previousMediaId: StateFlow<String?> = _previousMediaId.asStateFlow()

    init {
        val mediaId = savedStateHandle.get<String>("mediaId")
        viewModelScope.launch {
            // Combine video + audio items so the player can resolve and navigate
            // between both. Audio ids are prefixed "aud_" (see LibraryRepository).
            combine(
                libraryRepository.mediaItems,
                libraryRepository.audioItems
            ) { videos, audios -> videos + audios }
                .collect { items ->
                    _allMedia.value = items
                    if (mediaId != null && _currentMedia.value == null) {
                        _currentMedia.value = items.find { it.id == mediaId }
                    }
                    updateNavigation()
                }
        }
    }

    private fun updateNavigation() {
        val current = _currentMedia.value ?: return
        val all = _allMedia.value
        if (all.isEmpty()) return

        val index = all.indexOfFirst { it.id == current.id }
        if (index < 0) return

        _previousMediaId.value = if (index > 0) all[index - 1].id else null
        _nextMediaId.value = if (index < all.size - 1) all[index + 1].id else null
    }
}
