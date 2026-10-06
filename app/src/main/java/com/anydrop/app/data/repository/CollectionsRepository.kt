package com.anydrop.app.data.repository

import android.content.Context
import com.anydrop.app.data.model.MediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A library item as favorites and collections remember it: the gallery id, plus the file
 * name so the entry survives the id changing (an AnyDrop download is known by its file
 * name until the gallery indexes it, then by the gallery's id).
 */
data class MediaRef(val id: String, val name: String) {
    companion object {
        fun of(item: MediaItem) = MediaRef(item.id, nameOf(item))
        fun nameOf(item: MediaItem): String = File(item.title).nameWithoutExtension
    }
}

/** A user-made album ("Family", "Trip"...). Files are not copied: it only lists them. */
data class MediaCollection(val id: String, val name: String, val items: List<MediaRef>)

/** Which part of the library is on screen. */
sealed interface LibraryScope {
    data object All : LibraryScope
    data object Favorites : LibraryScope
    data class Collection(val id: String) : LibraryScope
}

/** Fast "is this item in there" checks for a set of [MediaRef]s. */
class MediaRefIndex(refs: Collection<MediaRef>) {
    private val ids = refs.mapTo(HashSet()) { it.id }
    private val names = refs.mapNotNullTo(HashSet()) { it.name.takeIf(String::isNotBlank) }
    val isEmpty: Boolean get() = ids.isEmpty()
    operator fun contains(item: MediaItem): Boolean =
        item.id in ids || MediaRef.nameOf(item).let { it.isNotBlank() && it in names }
}

/**
 * Favorites and collections, kept in SharedPreferences as JSON. Library items are
 * matched by [MediaRef]; deleting an item through AnyDrop also removes it from here.
 */
@Singleton
class CollectionsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("anydrop_collections", Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow(loadRefs(prefs.getString(KEY_FAVORITES, null)))
    val favorites: StateFlow<List<MediaRef>> = _favorites.asStateFlow()

    private val _collections = MutableStateFlow(loadCollections(prefs.getString(KEY_COLLECTIONS, null)))
    val collections: StateFlow<List<MediaCollection>> = _collections.asStateFlow()

    fun isFavorite(item: MediaItem): Boolean = item in MediaRefIndex(_favorites.value)

    /** Adds [items] to the favorites, or removes them when they all already are. */
    fun toggleFavorite(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val index = MediaRefIndex(_favorites.value)
        saveFavorites(
            if (items.all { it in index }) _favorites.value.filterNot { ref -> items.any { ref.matches(it) } }
            else _favorites.value + items.filterNot { it in index }.map(MediaRef::of)
        )
    }

    fun createCollection(name: String, items: List<MediaItem> = emptyList()): MediaCollection {
        val collection = MediaCollection(UUID.randomUUID().toString(), name.trim(), items.map(MediaRef::of).distinct())
        saveCollections(_collections.value + collection)
        return collection
    }

    fun renameCollection(id: String, name: String) =
        saveCollections(_collections.value.map { if (it.id == id) it.copy(name = name.trim()) else it })

    fun deleteCollection(id: String) = saveCollections(_collections.value.filterNot { it.id == id })

    /** Adds [items] to the collection; ones already in it are left alone. Returns how many were new. */
    fun addToCollection(id: String, items: List<MediaItem>): Int {
        var added = 0
        saveCollections(_collections.value.map { collection ->
            if (collection.id != id) return@map collection
            val index = MediaRefIndex(collection.items)
            val fresh = items.filterNot { it in index }.map(MediaRef::of)
            added = fresh.size
            collection.copy(items = collection.items + fresh)
        })
        return added
    }

    fun removeFromCollection(id: String, items: List<MediaItem>) =
        saveCollections(_collections.value.map { collection ->
            if (collection.id != id) collection
            else collection.copy(items = collection.items.filterNot { ref -> items.any { ref.matches(it) } })
        })

    /** Deleted from the device: drop them from favorites and every collection. */
    fun forget(items: List<MediaItem>) {
        if (items.isEmpty()) return
        fun List<MediaRef>.without() = filterNot { ref -> items.any { ref.matches(it) } }
        saveFavorites(_favorites.value.without())
        saveCollections(_collections.value.map { it.copy(items = it.items.without()) })
    }

    /** The part of [items] the [scope] shows, in the order given. */
    fun filter(items: List<MediaItem>, scope: LibraryScope): List<MediaItem> = when (scope) {
        LibraryScope.All -> items
        LibraryScope.Favorites -> MediaRefIndex(_favorites.value).let { index -> items.filter { it in index } }
        is LibraryScope.Collection -> {
            val collection = _collections.value.firstOrNull { it.id == scope.id }
            if (collection == null) items else MediaRefIndex(collection.items).let { index -> items.filter { it in index } }
        }
    }

    private fun MediaRef.matches(item: MediaItem) =
        id == item.id || (name.isNotBlank() && name == MediaRef.nameOf(item))

    private fun saveFavorites(list: List<MediaRef>) {
        prefs.edit().putString(KEY_FAVORITES, refsToJson(list).toString()).apply()
        _favorites.value = list
    }

    private fun saveCollections(list: List<MediaCollection>) {
        val arr = JSONArray()
        list.forEach { c -> arr.put(JSONObject().put("id", c.id).put("name", c.name).put("items", refsToJson(c.items))) }
        prefs.edit().putString(KEY_COLLECTIONS, arr.toString()).apply()
        _collections.value = list
    }

    companion object {
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_COLLECTIONS = "collections"

        private fun refsToJson(refs: List<MediaRef>) = JSONArray().apply {
            refs.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) }
        }

        private fun refsFrom(arr: JSONArray?): List<MediaRef> = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
            val o = arr!!.optJSONObject(i) ?: return@mapNotNull null
            MediaRef(o.optString("id"), o.optString("name"))
        }

        fun loadRefs(json: String?): List<MediaRef> =
            json?.let { runCatching { refsFrom(JSONArray(it)) }.getOrNull() }.orEmpty()

        fun loadCollections(json: String?): List<MediaCollection> =
            json?.let { raw ->
                runCatching {
                    val arr = JSONArray(raw)
                    (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        MediaCollection(o.getString("id"), o.getString("name"), refsFrom(o.optJSONArray("items")))
                    }
                }.getOrNull()
            }.orEmpty()
    }
}
