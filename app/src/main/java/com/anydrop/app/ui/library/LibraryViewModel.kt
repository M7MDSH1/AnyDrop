package com.anydrop.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.data.repository.DeletionResult
import com.anydrop.app.data.repository.LibraryRepository
import com.anydrop.app.data.repository.MediaFilter
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

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _allItems = MutableStateFlow<List<MediaItem>>(emptyList())

    val filter: StateFlow<MediaFilter> = libraryRepository.filterMode

    val sortMode: StateFlow<SortMode> = libraryRepository.sortMode

    // Visible items = all items with the media-type filter + sort applied.
    val mediaItems: StateFlow<List<MediaItem>> =
        combine(_allItems, filter, sortMode) { items, filter, sort ->
            libraryRepository.orderedMedia(items, filter, sort)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedItem = MutableStateFlow<MediaItem?>(null)
    val selectedItem: StateFlow<MediaItem?> = _selectedItem.asStateFlow()

    init {
        viewModelScope.launch {
            libraryRepository.mediaItems.collect { items ->
                _allItems.value = items
            }
        }
        // Show the cached index instantly, then refresh in the background.
        viewModelScope.launch {
            libraryRepository.loadCachedIndex()
        }
        refresh()
    }

    fun setFilter(f: MediaFilter) {
        libraryRepository.setFilterMode(f)
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

    fun deleteItem(item: MediaItem): DeletionResult = deleteItems(listOf(item))

    fun deleteItems(items: List<MediaItem>): DeletionResult {
        val result = libraryRepository.deleteItems(items)
        refresh()
        return result
    }
}
