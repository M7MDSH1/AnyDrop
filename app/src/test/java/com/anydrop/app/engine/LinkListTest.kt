package com.anydrop.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkListTest {

    @Test
    fun linksInANote_areFoundInOrder_eachOnce() {
        val note = """
            Videos for tonight:
            1. https://www.youtube.com/watch?v=aqz-KE-bpKQ
            2. https://www.instagram.com/reel/Ddy2r3XqT_p/?xtok=abc, and this one (https://vimeo.com/1084537).
            again: https://www.youtube.com/watch?v=aqz-KE-bpKQ
        """.trimIndent()
        assertEquals(
            listOf(
                "https://www.youtube.com/watch?v=aqz-KE-bpKQ",
                "https://www.instagram.com/reel/Ddy2r3XqT_p/?xtok=abc",
                "https://vimeo.com/1084537"
            ),
            LinkList.extract(note)
        )
        assertTrue(LinkList.isList(note))
    }

    @Test
    fun oneLink_isNoList_evenWithALinkInsideIt() {
        assertFalse(LinkList.isList("https://www.youtube.com/watch?v=aqz-KE-bpKQ"))
        // A redirect carrying another address is still one link.
        assertFalse(LinkList.isList("https://l.facebook.com/l.php?u=https://example.com/video"))
        assertFalse(LinkList.isList("magnet:?xt=urn:btih:abc https://example.com/a"))
    }

    @Test
    fun aLinkGetsAReadableNameUntilItsTitleIsKnown() {
        assertEquals("youtube.com · watch?v=aqz-KE-bpKQ", LinkList.displayName("https://www.youtube.com/watch?v=aqz-KE-bpKQ"))
        assertEquals("vimeo.com", LinkList.displayName("https://vimeo.com/"))
        val entries = LinkList.entries(listOf("https://vimeo.com/1084537", "https://youtu.be/abc"))
        assertTrue(entries.isLinkList)
        assertTrue(entries.all { it.isLink })
        assertEquals("https://youtu.be/abc", entries[1].pageUrl)
    }
}
