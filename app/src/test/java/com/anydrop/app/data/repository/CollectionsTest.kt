package com.anydrop.app.data.repository

import com.anydrop.app.data.model.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionsTest {

    @Test
    fun refs_matchByIdOrByFileName() {
        // Saved while the download was only known by its file name...
        val index = MediaRefIndex(listOf(MediaRef("Beach day [vid2]", "Beach day [vid2]")))
        // ...and found again once the gallery indexed it under its own id.
        assertTrue(MediaItem(id = "vid_42", title = "Beach day [vid2].mp4") in index)
        assertTrue(MediaItem(id = "Beach day [vid2]", title = "Beach day [vid2]") in index)
        assertFalse(MediaItem(id = "vid_43", title = "Other.mp4") in index)
    }

    @Test
    fun aDottedFileName_matchesItsGalleryRow() {
        // What CollectionsRepository.addFile saves for "media.w3.org [480p].mp4".
        val index = MediaRefIndex(listOf(MediaRef("media.w3.org [480p]", "media.w3.org [480p]")))
        assertTrue(MediaItem(id = "vid_5", title = "media.w3.org [480p].mp4") in index)
        assertFalse(MediaItem(id = "vid_6", title = "media.w3.mp4") in index)
    }

    @Test
    fun blankNames_neverMatchEverything() {
        val index = MediaRefIndex(listOf(MediaRef("img_1", "")))
        assertFalse(MediaItem(id = "img_2", title = "") in index)
        assertTrue(MediaItem(id = "img_1", title = "") in index)
    }

    @Test
    fun refOf_usesTheNameWithoutExtension() {
        assertEquals(MediaRef("img_7", "Family dinner"), MediaRef.of(MediaItem(id = "img_7", title = "Family dinner.jpg")))
    }

    @Test
    fun savedCollections_readBack_andBadDataIsIgnored() {
        val json = """[{"id":"c1","name":"Family","items":[{"id":"vid_1","name":"a"},{"id":"img_2","name":"b"}]}]"""
        val collections = CollectionsRepository.loadCollections(json)
        assertEquals(1, collections.size)
        assertEquals("Family", collections[0].name)
        assertEquals(listOf(MediaRef("vid_1", "a"), MediaRef("img_2", "b")), collections[0].items)

        assertTrue(CollectionsRepository.loadCollections("not json").isEmpty())
        assertTrue(CollectionsRepository.loadCollections(null).isEmpty())
        assertEquals(listOf(MediaRef("x", "y")), CollectionsRepository.loadRefs("""[{"id":"x","name":"y"}]"""))
    }

    @Test
    fun removedFavorites_comeBackWithUndo_forFiveSeconds() {
        val a = MediaRef("vid_1", "Beach"); val b = MediaRef("img_2", "Dinner"); val c = MediaRef("vid_3", "Trip")
        val first = RemovedFavorites.after(null, listOf(a), now = 1_000)
        assertEquals(RemovedFavorites(listOf(a), until = 6_000), first)
        // Another removed while the pill shows: one Undo covers both, counted from the last.
        val both = RemovedFavorites.after(first, listOf(b), now = 4_000)
        assertEquals(RemovedFavorites(listOf(a, b), until = 9_000), both)
        // After the first one's time was up, only the new one can come back.
        assertEquals(listOf(b), RemovedFavorites.after(first, listOf(b), now = 6_000).refs)
        // Undo puts them back; one favorited again by hand meanwhile isn't doubled.
        assertEquals(listOf(c, a, b), both.restoredInto(listOf(c, a)))
    }
}
