package com.anydrop.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRepositoryTest {

    private val release = """
        {
          "tag_name": "v1.3.0",
          "draft": false,
          "prerelease": false,
          "html_url": "https://github.com/M7MDSH1/AnyDrop/releases/tag/v1.3.0",
          "body": "## What's new\n- Thing\n\n## ما الجديد\n- شيء\n\n## Install\nDownload it.",
          "assets": [
            {"name": "app-armeabi-v7a-release.apk", "size": 60, "browser_download_url": "https://example.com/v7a.apk"},
            {"name": "app-arm64-v8a-release.apk", "size": 66, "browser_download_url": "https://example.com/arm64.apk"},
            {"name": "notes.txt", "size": 1, "browser_download_url": "https://example.com/notes.txt"}
          ]
        }
    """.trimIndent()

    @Test
    fun isNewer_comparesNumerically() {
        assertTrue(UpdateRepository.isNewer("1.3.0", "1.2.0"))
        assertTrue(UpdateRepository.isNewer("v1.10.0", "1.9.9"))
        assertTrue(UpdateRepository.isNewer("1.2.1", "1.2"))
        assertFalse(UpdateRepository.isNewer("1.2.0", "1.2.0"))
        assertFalse(UpdateRepository.isNewer("1.2", "1.2.0"))
        assertFalse(UpdateRepository.isNewer("1.1.9", "1.2.0"))
        assertFalse(UpdateRepository.isNewer("1.2.0-beta", "1.2.0"))
    }

    @Test
    fun parseRelease_picksTheApkForThePhonesCpu() {
        val arm64 = UpdateRepository.parseRelease(release, arrayOf("arm64-v8a", "armeabi-v7a", "armeabi"))!!
        assertEquals("1.3.0", arm64.version)
        assertEquals("https://example.com/arm64.apk", arm64.apkUrl)
        assertEquals(66L, arm64.apkSize)
        assertEquals("https://github.com/M7MDSH1/AnyDrop/releases/tag/v1.3.0", arm64.pageUrl)

        val old = UpdateRepository.parseRelease(release, arrayOf("armeabi-v7a", "armeabi"))!!
        assertEquals("https://example.com/v7a.apk", old.apkUrl)

        // No APK for this CPU: fall back to any APK rather than none.
        val other = UpdateRepository.parseRelease(release, arrayOf("x86_64"))!!
        assertTrue(other.apkUrl!!.endsWith(".apk"))
    }

    @Test
    fun parseRelease_withoutApkStillReportsTheVersion() {
        val bare = """{"tag_name": "v2.0.0", "html_url": "https://x", "body": "", "assets": []}"""
        val update = UpdateRepository.parseRelease(bare, arrayOf("arm64-v8a"))!!
        assertEquals("2.0.0", update.version)
        assertNull(update.apkUrl)
    }

    @Test
    fun parseRelease_ignoresDraftsAndPrereleases() {
        assertNull(UpdateRepository.parseRelease("""{"tag_name": "v9.0.0", "prerelease": true}""", arrayOf("arm64-v8a")))
        assertNull(UpdateRepository.parseRelease("""{"tag_name": "v9.0.0", "draft": true}""", arrayOf("arm64-v8a")))
    }

    @Test
    fun notesFor_showsTheSectionForTheLanguage() {
        val notes = "## What's new\n- Thing\n\n## ما الجديد\n- شيء\n\n## Install\nDownload it."
        assertEquals("- شيء", UpdateRepository.notesFor(notes, arabic = true))
        val english = UpdateRepository.notesFor(notes, arabic = false)
        assertTrue(english.contains("- Thing"))
        assertTrue(english.contains("## Install"))
        assertFalse(english.contains("شيء"))
        // Notes without an Arabic section are shown as they are.
        assertEquals("- Only English", UpdateRepository.notesFor("- Only English", arabic = true))
    }
}
