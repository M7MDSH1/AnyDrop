package com.anydrop.app.ui.player

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.data.repository.CollectionsRepository
import com.anydrop.app.data.repository.DeckSessionRepository
import com.anydrop.app.data.repository.DeletionResult
import com.anydrop.app.data.repository.LibraryRepository
import com.anydrop.app.data.repository.MediaRefIndex
import com.anydrop.app.data.repository.PlaybackProgressRepository
import com.anydrop.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val libraryRepository: LibraryRepository,
    private val collectionsRepository: CollectionsRepository,
    private val progressRepository: PlaybackProgressRepository,
    private val deckSessionRepository: DeckSessionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _currentMedia = MutableStateFlow<MediaItem?>(null)
    val currentMedia: StateFlow<MediaItem?> = _currentMedia.asStateFlow()

    private val _allMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    val allMedia: StateFlow<List<MediaItem>> = _allMedia.asStateFlow()

    private val _nextMediaId = MutableStateFlow<String?>(null)
    val nextMediaId: StateFlow<String?> = _nextMediaId.asStateFlow()

    private val _previousMediaId = MutableStateFlow<String?>(null)
    val previousMediaId: StateFlow<String?> = _previousMediaId.asStateFlow()

    /** Whether the item on screen is a favorite (the heart in the player). */
    val isFavorite: StateFlow<Boolean> =
        combine(_currentMedia, collectionsRepository.favorites) { media, favorites ->
            media != null && media in MediaRefIndex(favorites)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun toggleFavorite() {
        _currentMedia.value?.let { collectionsRepository.toggleFavorite(listOf(it)) }
    }

    /** Movie mode (see [SettingsRepository.movieMode]); it stays on for the next video too. */
    private val _movieMode = MutableStateFlow(settingsRepository.movieMode)
    val movieMode: StateFlow<Boolean> = _movieMode.asStateFlow()

    fun toggleMovieMode() {
        val on = !_movieMode.value
        settingsRepository.movieMode = on
        _movieMode.value = on
    }

    // ---- Per-video session state. All of it is reset on every mediaId change
    // so nothing (resume position, save tick anchor, completion flag, restore
    // flag) leaks from one video into the next if this VM is ever reused.
    private val _resumePosition = MutableStateFlow(0L)
    val resumePosition: StateFlow<Long> = _resumePosition.asStateFlow()
    private var lastSavedPosition = 0L
    private var completionHandled = false
    private var restoreAttempted = false
    private var fallbackAttempted = false

    init {
        val mediaId = savedStateHandle.get<String>("mediaId")
        viewModelScope.launch {
            // The player navigation order mirrors the library's current view:
            // all / favorites / a collection, media type filter (ALL / VIDEOS / IMAGES),
            // search and sort (DATE / LENGTH), with audio tracks appended at the end
            // (newest first) so swiping from the Audio tab still works. Shared via the
            // repositories so the player can never disagree with the Library screen.
            val scope = combine(
                libraryRepository.scope,
                collectionsRepository.favorites,
                collectionsRepository.collections
            ) { scope, _, _ -> scope }
            val media = combine(
                libraryRepository.mediaItems,
                libraryRepository.filterMode,
                libraryRepository.sortMode,
                libraryRepository.searchQuery,
                scope
            ) { items, filter, sort, query, scope ->
                collectionsRepository.filter(libraryRepository.orderedMedia(items, filter, sort, query), scope)
            }
            combine(media, libraryRepository.audioItems) { items, audios ->
                items + audios.sortedByDescending { it.downloadedAt }
            }
                .collect { items ->
                    _allMedia.value = items
                    if (mediaId != null) {
                        val resolved = items.find { it.id == mediaId }
                        if (resolved != null && resolved.id != _currentMedia.value?.id) {
                            startSession(resolved)
                        } else if (resolved == null && !fallbackAttempted && items.isNotEmpty()) {
                            // The requested media no longer resolves (e.g. deleted or the
                            // scan is empty) — fall back to the last played media instead
                            // of leaving the player stuck on "NO MEDIA LOADED".
                            fallbackAttempted = true
                            val lastPlayed = deckSessionRepository.lastPlayedMediaId()
                            val fallback = lastPlayed.takeIf { it.isNotBlank() && it != mediaId }
                                ?.let { id -> items.find { it.id == id } }
                            if (fallback != null && fallback.id != _currentMedia.value?.id) {
                                startSession(fallback)
                            }
                        }
                    }
                    updateNavigation()
                }
        }
    }

    /**
     * Play a video another app handed to AnyDrop ("Open with", or AnyDrop as the
     * default video player). It is not in the library, so there is no next or
     * previous, but watch progress is kept under its URI: reopening the same file
     * resumes where the user stopped.
     */
    fun openExternal(uri: Uri, title: String) {
        if (_currentMedia.value?.localUri == uri) return
        startSession(MediaItem(id = uri.toString(), title = title, localUri = uri), fromLibrary = false)
    }

    /** Begin a watch session for [media], resetting every per-video piece of state. */
    private fun startSession(media: MediaItem, fromLibrary: Boolean = true) {
        _currentMedia.value = media
        lastSavedPosition = 0L
        completionHandled = false
        restoreAttempted = false
        fallbackAttempted = false
        if (fromLibrary) deckSessionRepository.saveLastPlayedMediaId(media.id)
        viewModelScope.launch {
            _resumePosition.value = progressRepository.getPosition(media.id) ?: 0L
        }
    }

    /**
     * Called on STATE_READY for the current video. Returns the position to seek
     * to before playback starts, or 0 to start from the beginning. Only the
     * FIRST STATE_READY counts — later READY events (post-rebuffer) are no-ops,
     * so a mid-playback glitch can never yank the user backwards.
     */
    fun onPlayerReady(durationMs: Long): Long {
        val media = _currentMedia.value
        if (media == null || media.isImage || media.isAudio) return 0L
        if (restoreAttempted) return 0L
        restoreAttempted = true
        val saved = _resumePosition.value
        if (saved <= 10_000L || durationMs <= 0L || saved >= durationMs - 3_000L) return 0L
        lastSavedPosition = saved
        return saved
    }

    /**
     * Periodic playback heartbeat (the player polls ~every 500ms):
     *  - marks the video completed when within the last 3 seconds (whichever
     *    comes first with STATE_ENDED) — suppressed while [loopMode] is on so
     *    repeat-one looping doesn't clear the user's watch progress;
     *  - otherwise saves every 5 seconds of playback.
     */
    fun onPlaybackTick(isPlaying: Boolean, positionMs: Long, durationMs: Long, loopMode: Boolean = false) {
        val media = _currentMedia.value
        if (media == null || media.isImage || media.isAudio) return
        if (durationMs <= 0L) return
        if (!loopMode && !completionHandled && positionMs >= durationMs - 3_000L) {
            complete()
            return
        }
        if (!isPlaying) return
        if (positionMs - lastSavedPosition >= 5_000L) {
            lastSavedPosition = positionMs
            saveProgress(positionMs)
        }
    }

    /** Save immediately (user seek or activity pause). */
    fun onSeek(positionMs: Long) {
        saveProgress(positionMs)
    }

    private fun saveProgress(positionMs: Long) {
        val media = _currentMedia.value
        if (media == null || media.isImage || media.isAudio) return
        // Never record the first 10 seconds — too early to be meaningful.
        if (positionMs < 10_000L) return
        viewModelScope.launch {
            progressRepository.savePosition(media.id, positionMs)
        }
    }

    /** Mark the video completed and drop its saved position (starts from 0 next time). */
    fun complete() {
        if (completionHandled) return
        completionHandled = true
        val media = _currentMedia.value ?: return
        viewModelScope.launch {
            progressRepository.clearPosition(media.id)
        }
    }

    /**
     * Delete the currently playing/displayed item (MediaStore consent handled
     * by the caller via [PlayerDeleteResult.consent]) and refresh the library
     * so it disappears from the queue and the list screens. The neighbouring
     * item to advance to is captured atomically from the in-memory queue so a
     * delete can never race the navigation-target computation.
     */
    fun deleteCurrent(): PlayerDeleteResult {
        val media = _currentMedia.value ?: return PlayerDeleteResult(DeletionResult(), null)
        val all = _allMedia.value
        val index = all.indexOfFirst { it.id == media.id }
        val advanceTargetId = if (index >= 0) {
            all.getOrNull(index + 1)?.id ?: all.getOrNull(index - 1)?.id
        } else {
            null
        }
        return PlayerDeleteResult(
            consent = libraryRepository.deleteItems(listOf(media)),
            advanceTargetId = advanceTargetId
        )
    }

    fun refreshLibrary() {
        viewModelScope.launch { libraryRepository.refreshLibrary() }
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

/** Result of deleting the current player item: consent info + where to go next. */
data class PlayerDeleteResult(
    val consent: DeletionResult,
    val advanceTargetId: String?
)
