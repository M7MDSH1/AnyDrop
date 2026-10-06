package com.anydrop.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.data.repository.CollectionsRepository
import com.anydrop.app.data.repository.DeletionResult
import com.anydrop.app.data.repository.LibraryRepository
import com.anydrop.app.data.repository.LibraryScope
import com.anydrop.app.data.repository.MediaCollection
import com.anydrop.app.data.repository.MediaFilter
import com.anydrop.app.data.repository.MediaRef
import com.anydrop.app.data.repository.MediaRefIndex
import com.anydrop.app.data.repository.SortMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val collectionsRepository: CollectionsRepository
) : ViewModel() {

    private val _allItems = MutableStateFlow<List<MediaItem>>(emptyList())

    val filter: StateFlow<MediaFilter> = libraryRepository.filterMode

    val sortMode: StateFlow<SortMode> = libraryRepository.sortMode

    val searchQuery: StateFlow<String> = libraryRepository.searchQuery

    val scope: StateFlow<LibraryScope> = libraryRepository.scope

    val favorites: StateFlow<List<MediaRef>> = collectionsRepository.favorites

    val collections: StateFlow<List<MediaCollection>> = collectionsRepository.collections

    /** Fast favorite checks for the grid's hearts. */
    val favoriteIndex: StateFlow<MediaRefIndex> = favorites
        .map { MediaRefIndex(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MediaRefIndex(emptyList()))

    /**
     * How many library items each chip holds: [FAVORITES_KEY] for favorites, otherwise a
     * collection id. Counted against the library, so files deleted elsewhere don't count.
     */
    val scopeCounts: StateFlow<Map<String, Int>> =
        combine(_allItems, favorites, collections) { items, favs, cols ->
            buildMap {
                MediaRefIndex(favs).let { index -> put(FAVORITES_KEY, items.count { it in index }) }
                cols.forEach { c -> MediaRefIndex(c.items).let { index -> put(c.id, items.count { it in index }) } }
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // The scope, re-emitted whenever favorites or collections change what it contains.
    private val scopeContents: Flow<LibraryScope> =
        combine(scope, favorites, collections) { scope, _, _ -> scope }

    // Items in the current scope (all / favorites / a collection) with the type filter and sort.
    private val scoped: Flow<List<MediaItem>> =
        combine(_allItems, filter, sortMode, scopeContents) { items, filter, sort, scope ->
            collectionsRepository.filter(libraryRepository.orderedMedia(items, filter, sort), scope)
        }

    // Visible items = the scoped items narrowed by the search.
    val mediaItems: StateFlow<List<MediaItem>> =
        combine(_allItems, filter, sortMode, searchQuery, scopeContents) { items, filter, sort, query, scope ->
            collectionsRepository.filter(libraryRepository.orderedMedia(items, filter, sort, query), scope)
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // How many items the current view shows before the search narrows them ("3 / 120").
    val unsearchedCount: StateFlow<Int> = scoped
        .map { it.size }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _selectedItem = MutableStateFlow<MediaItem?>(null)
    val selectedItem: StateFlow<MediaItem?> = _selectedItem.asStateFlow()

    init {
        viewModelScope.launch {
            libraryRepository.mediaItems.collect { items ->
                _allItems.value = items
            }
        }
        // A deleted collection can't stay on screen: fall back to everything.
        viewModelScope.launch {
            combine(scope, collections) { scope, list -> scope to list }.collect { (scope, list) ->
                if (scope is LibraryScope.Collection && list.none { it.id == scope.id }) {
                    libraryRepository.setScope(LibraryScope.All)
                }
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

    fun setSearchQuery(query: String) {
        libraryRepository.setSearchQuery(query)
    }

    fun setScope(scope: LibraryScope) {
        libraryRepository.setScope(scope)
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

    fun toggleFavorite(items: List<MediaItem>) = collectionsRepository.toggleFavorite(items)

    /** Creates a collection (with [items] in it, if any) and returns it. */
    fun createCollection(name: String, items: List<MediaItem> = emptyList()): MediaCollection =
        collectionsRepository.createCollection(name, items)

    fun renameCollection(id: String, name: String) = collectionsRepository.renameCollection(id, name)

    fun deleteCollection(id: String) = collectionsRepository.deleteCollection(id)

    /** Returns how many of [items] were not in the collection yet. */
    fun addToCollection(id: String, items: List<MediaItem>): Int = collectionsRepository.addToCollection(id, items)

    fun removeFromCollection(id: String, items: List<MediaItem>) = collectionsRepository.removeFromCollection(id, items)

    fun deleteItem(item: MediaItem): DeletionResult = deleteItems(listOf(item))

    fun deleteItems(items: List<MediaItem>): DeletionResult {
        val result = libraryRepository.deleteItems(items)
        // Items that still need the system's consent may stay; only forget the deleted ones.
        collectionsRepository.forget(items.filter { it.localUri == null || it.localUri !in result.consentUris })
        refresh()
        return result
    }

    companion object {
        const val FAVORITES_KEY = "favorites"
    }
}
