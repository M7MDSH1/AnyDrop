package com.anydrop.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkPreviewTest {

    @Test
    fun knownSitesGetTheirPreviewAddress() {
        assertEquals(
            "https://www.youtube.com/oembed?format=json&url=https%3A%2F%2Fyoutu.be%2Fabc123",
            LinkPreview.endpointFor(" https://youtu.be/abc123 ")
        )
        assertEquals(
            "https://www.youtube.com/oembed?format=json&url=https%3A%2F%2Fm.youtube.com%2Fwatch%3Fv%3Dabc%26t%3D5",
            LinkPreview.endpointFor("https://m.youtube.com/watch?v=abc&t=5")
        )
        assertEquals(
            "https://vimeo.com/api/oembed.json?url=https%3A%2F%2Fvimeo.com%2F1084537",
            LinkPreview.endpointFor("https://vimeo.com/1084537")
        )
    }

    @Test
    fun otherSitesAndLookalikesGetNone() {
        assertNull(LinkPreview.endpointFor("https://example.com/video"))
        assertNull(LinkPreview.endpointFor("https://notyoutube.com/watch?v=abc"))
        assertNull(LinkPreview.endpointFor("not a link"))
    }
}
