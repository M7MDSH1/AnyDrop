package com.anydrop.app.engine

import com.anydrop.app.engine.YtDlpEngine.FormatInfo

/**
 * Several links pasted or shared at once (a note with one per line, a chat message…): each is
 * listed as its own video, downloaded as a video or as its sound only.
 */
object LinkList {

    /** [FormatInfo.group] prefix of a pasted link ("links:" + the link). */
    const val GROUP = "links:"

    private val URL = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /** The web links in [text], in order, each once (trailing punctuation from the sentence left off). */
    fun extract(text: String): List<String> =
        URL.findAll(text).map { it.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '>') }
            .filter { it.length > "https://".length }
            .distinct()
            .toList()

    /** [text] holds two or more links: it's a list, not one link. */
    fun isList(text: String): Boolean = extract(text).size >= 2

    /** The links as the link field shows them: on one line. */
    fun oneLine(links: List<String>): String = links.joinToString(" ")

    /**
     * A name for a link until its real title is known: "youtube.com · watch?v=aqz-KE-bpKQ".
     * Also the file name if it's downloaded before then.
     */
    fun displayName(url: String): String {
        val noScheme = url.substringAfter("://")
        val host = noScheme.substringBefore('/').removePrefix("www.").removePrefix("m.")
        val rest = noScheme.substringAfter('/', "").trimEnd('/')
        return if (rest.isBlank()) host else "$host · ${rest.take(60)}"
    }

    /** Each link as a linked video (its qualities are looked up when it downloads). */
    fun entries(links: List<String>): List<FormatInfo> = links.map { url ->
        FormatInfo(
            id = "",
            extension = "mp4",
            resolution = "Best",
            filesize = 0L,
            vcodec = FormatInfo.LINK_CODEC,
            acodec = FormatInfo.LINK_CODEC,
            title = displayName(url),
            group = GROUP + url,
            pageUrl = url
        )
    }
}

/** Links pasted together (see [LinkList]). */
val List<FormatInfo>.isLinkList: Boolean get() = any { it.group.startsWith(LinkList.GROUP) }
