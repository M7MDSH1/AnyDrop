package com.anydrop.app.util

import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.data.model.MediaSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {

    private fun item(
        title: String,
        creator: String = "",
        source: MediaSource = MediaSource.UNKNOWN,
        path: String = ""
    ) = MediaItem(id = title, title = title, creator = creator, source = source, localFilePath = path)

    private fun matches(item: MediaItem, query: String) = SearchText.matches(item, SearchText.terms(query))

    @Test
    fun blankQuery_matchesEverything() {
        assertTrue(SearchText.terms("   ").isEmpty())
        assertTrue(matches(item("Anything"), ""))
    }

    @Test
    fun ignoresCaseAndAccents() {
        assertTrue(matches(item("Café Tour"), "CAFE"))
        assertTrue(matches(item("naïve"), "naive"))
    }

    @Test
    fun everyWordMustMatch_inAnyOrder() {
        val video = item("Cooking pasta at home", creator = "Chef Rami")
        assertTrue(matches(video, "pasta rami"))
        assertTrue(matches(video, "home   cooking"))
        assertFalse(matches(video, "pasta pizza"))
    }

    @Test
    fun searchesCreatorPlatformAndFileName() {
        assertTrue(matches(item("Clip", creator = "@daisy"), "daisy"))
        assertTrue(matches(item("Clip", source = MediaSource.INSTAGRAM), "instagram"))
        assertTrue(matches(item("Clip", path = "/storage/Movies/AnyDrop/holiday_2024.mp4"), "holiday"))
        // "Unknown" is a placeholder, not something the user would search for.
        assertFalse(matches(item("Clip"), "unknown"))
    }

    @Test
    fun arabicSpellingVariantsFold() {
        // Hamza forms of alef, teh marbuta / heh, alef maksura / yeh, diacritics, tatweel.
        assertEquals(SearchText.normalize("احمد"), SearchText.normalize("أحمد"))
        assertEquals(SearchText.normalize("اسلام"), SearchText.normalize("إسلام"))
        assertEquals(SearchText.normalize("مدرسه"), SearchText.normalize("مدرسة"))
        assertEquals(SearchText.normalize("مصطفي"), SearchText.normalize("مصطفى"))
        assertEquals(SearchText.normalize("محمد"), SearchText.normalize("مُحَمَّد"))
        assertEquals(SearchText.normalize("جميل"), SearchText.normalize("جمـــيل"))
        assertTrue(matches(item("رحلة إلى القاهرة"), "رحله الي"))
    }
}
