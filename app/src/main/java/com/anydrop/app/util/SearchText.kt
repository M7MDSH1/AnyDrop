package com.anydrop.app.util

import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.data.model.MediaSource
import java.io.File
import java.text.Normalizer
import java.util.Locale

/**
 * Forgiving text matching for the library search: case, accents and Arabic
 * spelling variants are ignored, and every typed word must appear somewhere in
 * the item's title, creator, platform or file name (in any order).
 */
object SearchText {

    private val combiningMarks = Regex("\\p{Mn}+")
    private val whitespace = Regex("\\s+")

    /** Lower-cases and strips accents / Arabic diacritics; أ إ آ fold to ا via NFD. */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(combiningMarks, "")
        val out = StringBuilder(decomposed.length)
        for (c in decomposed) {
            when (c) {
                'ة' -> out.append('ه')
                'ى' -> out.append('ي')
                'ـ' -> Unit // tatweel is decoration only
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    /** The normalized words of a query; empty when there is nothing to search for. */
    fun terms(query: String): List<String> =
        normalize(query).split(whitespace).filter { it.isNotEmpty() }

    fun matches(item: MediaItem, terms: List<String>): Boolean {
        if (terms.isEmpty()) return true
        val haystack = normalize(
            buildString {
                append(item.title).append(' ')
                append(item.creator).append(' ')
                if (item.source != MediaSource.UNKNOWN) append(item.source.displayName).append(' ')
                if (item.localFilePath.isNotEmpty()) append(File(item.localFilePath).name)
            }
        )
        return terms.all { haystack.contains(it) }
    }
}
