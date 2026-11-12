package com.anydrop.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipRangeTest {

    @Test
    fun timesAreRead() {
        assertEquals(65, ClipRange.parse("1:05"))
        assertEquals(3723, ClipRange.parse(" 1:02:03 "))
        assertEquals(75, ClipRange.parse("75"))
        assertEquals(5400, ClipRange.parse("90:00"))
        assertEquals(0, ClipRange.parse("0:00"))
    }

    @Test
    fun whatIsNotATimeIsRefused() {
        for (text in listOf("", "abc", "1:", ":30", "1:75", "1:2:3:4", "-5", "1.5", "999999:00")) {
            assertNull(text, ClipRange.parse(text))
        }
    }

    @Test
    fun timesAreWrittenBackTheSameWay() {
        assertEquals("1:05", ClipRange.format(65))
        assertEquals("1:02:03", ClipRange.format(3723))
        assertEquals("0:00", ClipRange.format(0))
        assertEquals("clip1m05to2m10", ClipRange.label(65, 130))
        assertEquals("clip0m00to1h02m03", ClipRange.label(0, 3723))
        assertEquals("*65-130", ClipRange.section(65, 130))
    }

    @Test
    fun aClipMustFitTheVideo() {
        assertTrue(ClipRange.isValid(10, 20, 60))
        assertTrue(ClipRange.isValid(0, 60, 60))
        assertTrue(ClipRange.isValid(10, 20, 0))
        assertFalse(ClipRange.isValid(20, 20, 60))
        assertFalse(ClipRange.isValid(30, 10, 60))
        assertFalse(ClipRange.isValid(10, 90, 60))
        assertFalse(ClipRange.isValid(null, 20, 60))
    }
}
