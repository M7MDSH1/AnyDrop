package com.anydrop.app.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.data.repository.DeletionResult
import com.anydrop.app.data.repository.CollectionsRepository
import com.anydrop.app.data.repository.LibraryRepository
import com.anydrop.app.data.repository.PlaybackProgressRepository
import com.anydrop.app.data.repository.RenameResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AudioViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val collectionsRepository: CollectionsRepository,
    private val progressRepository: PlaybackProgressRepository
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

    /**
     * Renames [item] (see [LibraryRepository.renameItem]); favorites, collections and the
     * saved playback position follow it. [onResult] runs on the main thread.
     */
    fun rename(item: MediaItem, name: String, onResult: (RenameResult) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { libraryRepository.renameItem(item, name) }
            if (result is RenameResult.Done) {
                collectionsRepository.rename(item, result.item)
                if (result.item.id != item.id) {
                    progressRepository.getPosition(item.id)?.let { position ->
                        progressRepository.savePosition(result.item.id, position)
                        progressRepository.clearPosition(item.id)
                    }
                }
                libraryRepository.refreshLibrary()
            }
            onResult(result)
        }
    }

    fun deleteItem(item: MediaItem): DeletionResult = deleteItems(listOf(item))

    fun deleteItems(items: List<MediaItem>): DeletionResult {
        val result = libraryRepository.deleteItems(items)
        refresh()
        return result
    }
}
