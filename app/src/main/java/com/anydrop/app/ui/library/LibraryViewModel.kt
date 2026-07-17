package com.anydrop.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.data.repository.LibraryRepository
import com.anydrop.app.data.repository.SortMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Media-type filter for the library grid. */
enum class MediaFilter { ALL, VIDEOS, IMAGES }

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _allItems = MutableStateFlow<List<MediaItem>>(emptyList())

    private val _filter = MutableStateFlow(MediaFilter.ALL)
    val filter: StateFlow<MediaFilter> = _filter.asStateFlow()

    val sortMode: StateFlow<SortMode> = libraryRepository.sortMode

    // Visible items = all items with the media-type filter + sort applied.
    val mediaItems: StateFlow<List<MediaItem>> =
        combine(_allItems, _filter, sortMode) { items, filter, sort ->
            val filtered = when (filter) {
                MediaFilter.ALL -> items
                MediaFilter.VIDEOS -> items.filter { !it.isImage && !it.isAudio }
                MediaFilter.IMAGES -> items.filter { it.isImage }
            }
            when (sort) {
                SortMode.DURATION -> filtered.sortedByDescending { it.durationMs }
                SortMode.DATE -> filtered.sortedByDescending { it.downloadedAt }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedItem = MutableStateFlow<MediaItem?>(null)
    val selectedItem: StateFlow<MediaItem?> = _selectedItem.asStateFlow()

    init {
        viewModelScope.launch {
            libraryRepository.mediaItems.collect { items ->
                _allItems.value = items
            }
        }
        refresh()
    }

    fun setFilter(f: MediaFilter) {
        _filter.value = f
    }

    fun refresh() {
        viewModelScope.launch {
            libraryRepository.refreshLibrary()
        }
    }

    fun setSortMode(mode: SortMode) {
        libraryRepository.setSortMode(mode)
        refresh()
    }

    fun selectItem(item: MediaItem) {
        _selectedItem.value = item
    }

    fun deleteItem(item: MediaItem) {
        libraryRepository.deleteItem(item)
        refresh()
    }
}
