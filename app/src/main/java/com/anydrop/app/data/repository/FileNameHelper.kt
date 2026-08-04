package com.anydrop.app.data.repository

/**
 * Pure helpers for duplicate-aware download file naming, extracted out of
 * [LibraryRepository] so they can be unit-tested on the JVM.
 */
object FileNameHelper {

    /** True when a file's name (without extension) is exactly `base` or `base_N`. */
    fun matchesCopyBase(nameWithoutExtension: String, base: String): Boolean {
        if (nameWithoutExtension == base) return true
        if (!nameWithoutExtension.startsWith("${base}_")) return false
        val tail = nameWithoutExtension.substring(base.length + 1)
        return tail.isNotBlank() && tail.all { it.isDigit() }
    }

    /**
     * The next free name for a duplicate copy: returns `base` if free, otherwise
     * `base_1`, `base_2`, ... picking the smallest index not already used (so a
     * gap left by a deleted copy gets reused).
     */
    fun nextFreeBase(base: String, occupied: List<String>): String {
        val used = occupied.toSet()
        if (base !in used) return base
        var n = 1
        while ("${base}_$n" in used) n++
        return "${base}_$n"
    }
}