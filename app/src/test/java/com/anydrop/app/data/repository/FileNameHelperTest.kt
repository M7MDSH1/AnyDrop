package com.anydrop.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameHelperTest {

    @Test
    fun matchesCopyBase_matchesBaseAndNumberedCopies() {
        val base = "My Video [1080p60]"
        assertTrue(FileNameHelper.matchesCopyBase(base, base))
        assertTrue(FileNameHelper.matchesCopyBase("${base}_1", base))
        assertTrue(FileNameHelper.matchesCopyBase("${base}_10", base))
        assertTrue(FileNameHelper.matchesCopyBase("${base}_123", base))
    }

    @Test
    fun matchesCopyBase_rejectsNonCopies() {
        val base = "My Video [1080p60]"
        assertFalse(FileNameHelper.matchesCopyBase("Other Video [1080p60]", base))
        assertFalse(FileNameHelper.matchesCopyBase("${base}_", base))
        assertFalse(FileNameHelper.matchesCopyBase("${base}_x", base))
        assertFalse(FileNameHelper.matchesCopyBase("${base}_1x", base))
        assertFalse(FileNameHelper.matchesCopyBase("My Video [1080p60]_a", base))
        assertFalse(FileNameHelper.matchesCopyBase("", base))
    }

    @Test
    fun nextFreeBase_returnsBaseWhenFree() {
        assertEquals("My Video [720p]", FileNameHelper.nextFreeBase("My Video [720p]", listOf("Other")))
        assertEquals("base", FileNameHelper.nextFreeBase("base", emptyList()))
    }

    @Test
    fun nextFreeBase_countsUpSequentially() {
        val base = "base"
        assertEquals("base_1", FileNameHelper.nextFreeBase(base, listOf(base)))
        assertEquals("base_2", FileNameHelper.nextFreeBase(base, listOf(base, "${base}_1")))
        assertEquals("base_3", FileNameHelper.nextFreeBase(base, listOf(base, "${base}_1", "${base}_2")))
    }

    @Test
    fun nextFreeBase_reusesGaps() {
        val base = "base"
        // _1 was deleted; base is still used.
        assertEquals("base_1", FileNameHelper.nextFreeBase(base, listOf(base, "${base}_2")))
        assertEquals("base_2", FileNameHelper.nextFreeBase(base, listOf(base, "${base}_1", "${base}_3")))
    }

    @Test
    fun nextFreeBase_ignoresUnrelatedNames() {
        val base = "base"
        assertEquals(base, FileNameHelper.nextFreeBase(base, listOf("base_1x", "base_x", "other_1")))
    }
}
