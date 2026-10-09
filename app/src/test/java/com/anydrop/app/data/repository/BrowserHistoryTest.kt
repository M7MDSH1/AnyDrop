package com.anydrop.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserHistoryTest {

    private val a = HistoryEntry("https://a.com/", "A", 1)
    private val b = HistoryEntry("https://b.com/watch?v=1", "B", 2)

    @Test
    fun aNewPageGoesOnTop_andARevisitMovesUp() {
        val list = HistoryList.record(listOf(a), "https://b.com/watch?v=1", "B", 10)
        assertEquals(listOf("https://b.com/watch?v=1", "https://a.com/"), list.map { it.url })
        val again = HistoryList.record(list, "https://a.com", "", 20)
        assertEquals(listOf("https://a.com", "https://b.com/watch?v=1"), again.map { it.url })
        assertEquals("A", again.first().title) // a revisit without a title yet keeps the old one
        assertEquals(20, again.first().visitedAt)
    }

    @Test
    fun theTopPageOnlyGetsItsTitleUpdated() {
        val list = listOf(b, a)
        assertSame(list, HistoryList.record(list, b.url, "B", 99))
        assertSame(list, HistoryList.record(list, b.url, "", 99))
        val renamed = HistoryList.record(list, b.url, "B — full title", 99)
        assertEquals("B — full title", renamed.first().title)
        assertEquals(2, renamed.first().visitedAt)
    }

    @Test
    fun addressesUsedAsTitles_andNonWebPages_areIgnored() {
        val list = HistoryList.record(emptyList(), "https://c.com/x", "c.com/x", 5)
        assertEquals("", list.first().title)
        assertTrue(HistoryList.record(emptyList(), "about:blank", "Blank", 5).isEmpty())
    }

    @Test
    fun historyIsCapped() {
        var list = emptyList<HistoryEntry>()
        repeat(HistoryList.MAX + 20) { list = HistoryList.record(list, "https://s.com/$it", "S$it", it.toLong()) }
        assertEquals(HistoryList.MAX, list.size)
        assertEquals("https://s.com/${HistoryList.MAX + 19}", list.first().url)
    }

    @Test
    fun searchMatchesTitleOrAddress() {
        val list = listOf(b, a)
        assertEquals(listOf(b), HistoryList.search(list, "watch", 8))
        assertEquals(listOf(a), HistoryList.search(list, "a", 8).filter { it == a })
        assertEquals(listOf(b), HistoryList.search(list, "", 1))
    }

    @Test
    fun daysAgo_countsCalendarDays() {
        val day = 24L * 60 * 60 * 1000
        val noon = 20_000 * day + day / 2
        assertEquals(0, HistoryList.daysAgo(noon - 60_000, noon, 0))
        assertEquals(1, HistoryList.daysAgo(noon - day, noon, 0))
        // Just after midnight, the evening before is "yesterday".
        assertEquals(1, HistoryList.daysAgo(20_000 * day - 60_000, 20_000 * day + 60_000, 0))
    }

    @Test
    fun savedHistory_readsBack_andBadDataIsIgnored() {
        val json = """[{"url":"https://a.com/","title":"A","at":1},{"url":"javascript:x","title":"bad","at":2}]"""
        assertEquals(listOf(a), HistoryList.fromJson(json))
        assertTrue(HistoryList.fromJson("nope").isEmpty())
        assertTrue(HistoryList.fromJson(null).isEmpty())
    }
}
