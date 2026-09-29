package com.anydrop.app.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.data.repository.DeletionResult
import com.anydrop.app.data.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AudioViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _audioItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val audioItems: StateFlow<List<MediaItem>> = _audioItems.asStateFlow()

    init {
        viewModelScope.launch {
            libraryRepository.audioItems.collect { items ->
                _audioItems.value = items
            }
        }
        // Show the cached audio list instantly, then refresh in the background.
        viewModelScope.launch {
            libraryRepository.loadCachedIndex()
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            libraryRepository.refreshLibrary()
        }
    }

    fun deleteItem(item: MediaItem): DeletionResult = deleteItems(listOf(item))

    fun deleteItems(items: List<MediaItem>): DeletionResult {
        val result = libraryRepository.deleteItems(items)
        refresh()
        return result
    }
}
