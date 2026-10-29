package com.anydrop.app.data.repository

import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import org.json.JSONObject

/** [FormatInfo] as JSON, for what's kept across app restarts (the deck session, the downloads list). */
internal object FormatJson {

    fun toJson(f: FormatInfo): JSONObject = JSONObject().apply {
        put("id", f.id)
        put("extension", f.extension)
        put("resolution", f.resolution)
        put("filesize", f.filesize)
        put("vcodec", f.vcodec)
        put("acodec", f.acodec)
        put("isAudioOnly", f.isAudioOnly)
        put("abr", f.abr)
        put("isImage", f.isImage)
        put("imageUrl", f.imageUrl)
        // Media found on a web page: without these a restored entry couldn't download it.
        put("directUrl", f.directUrl)
        put("userAgent", f.userAgent)
        // Carousel slides, so a restored deck still offers the whole post.
        put("slide", f.slide)
        put("previewUrl", f.previewUrl)
        // Lengths, per-video titles and whole-page grouping, so a restored deck looks the same.
        put("durationSec", f.durationSec)
        put("title", f.title)
        put("group", f.group)
        put("pageUrl", f.pageUrl)
    }

    fun fromJson(o: JSONObject): FormatInfo = FormatInfo(
        id = o.optString("id"),
        extension = o.optString("extension"),
        resolution = o.optString("resolution"),
        filesize = o.optLong("filesize"),
        vcodec = o.optString("vcodec"),
        acodec = o.optString("acodec"),
        isAudioOnly = o.optBoolean("isAudioOnly"),
        abr = o.optInt("abr"),
        isImage = o.optBoolean("isImage"),
        imageUrl = o.optString("imageUrl"),
        directUrl = o.optString("directUrl"),
        userAgent = o.optString("userAgent"),
        slide = o.optInt("slide", -1),
        previewUrl = o.optString("previewUrl"),
        durationSec = o.optInt("durationSec"),
        title = o.optString("title"),
        group = o.optString("group"),
        pageUrl = o.optString("pageUrl")
    )
}
