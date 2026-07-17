package com.anydrop.app.ui.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anydrop.app.data.model.MediaItem
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
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            libraryRepository.refreshLibrary()
        }
    }

    fun deleteItem(item: MediaItem) {
        libraryRepository.deleteItem(item)
        refresh()
    }
}
