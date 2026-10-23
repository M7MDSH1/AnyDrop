package com.anydrop.app.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anydrop.app.R
import com.anydrop.app.engine.PageItem
import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import com.anydrop.app.engine.clockText
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.Hairline
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.OnAccent
import com.anydrop.app.ui.theme.White
import com.anydrop.app.ui.theme.tracked

/** A cover with the video's length on it; a plain sign stands in when there's no cover. */
@Composable
fun MediaThumb(cover: String, durationSec: Int, isVideo: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(MediumGray)) {
        if (cover.isNotBlank()) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                if (isVideo) Icons.Filled.PlayArrow else Icons.Filled.Image,
                contentDescription = null,
                tint = White.copy(alpha = 0.35f),
                modifier = Modifier.align(Alignment.Center).size(22.dp)
            )
        }
        val clock = clockText(durationSec)
        if (clock.isNotEmpty()) DurationBadge(clock, Modifier.align(Alignment.BottomEnd).padding(4.dp))
    }
}

/** "3:05" in a dark pill, for the corner of a cover. */
@Composable
fun DurationBadge(clock: String, modifier: Modifier = Modifier) {
    Text(
        clock,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

/** The fetched video at a glance: its cover, length and title, above its qualities. */
@Composable
fun MediaSummaryCard(cover: String, durationSec: Int, title: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Black)
            .border(1.dp, Hairline, shape)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MediaThumb(cover, durationSec, isVideo = true, modifier = Modifier.width(128.dp).aspectRatio(16f / 9f))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (title.isNotBlank()) {
                Text(title, color = White, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            val clock = clockText(durationSec)
            if (clock.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.media_length, clock),
                    color = White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/** Shown when the link is one video of a playlist: one tap lists the whole playlist. */
@Composable
fun PlaylistOffer(onShowPlaylist: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Amber.copy(alpha = 0.10f))
            .border(1.dp, Amber.copy(alpha = 0.5f), shape)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = Amber, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.playlist_offer),
            color = White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.playlist_offer_button),
            color = Amber,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onShowPlaylist)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        )
    }
}

/** What a link fetches: just its video, or everything on its page. */
@Composable
fun FetchModeToggle(wholePage: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(shape)
                .background(Black)
                .border(1.dp, Hairline, shape)
                .padding(3.dp)
        ) {
            ModeSegment(Icons.Filled.OndemandVideo, stringResource(R.string.fetch_this_video), !wholePage, { onChange(false) }, Modifier.weight(1f))
            ModeSegment(Icons.Filled.Web, stringResource(R.string.fetch_whole_page), wholePage, { onChange(true) }, Modifier.weight(1f))
        }
        if (wholePage) {
            Text(
                stringResource(R.string.fetch_whole_page_hint),
                color = White.copy(alpha = 0.45f),
                fontSize = 10.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 5.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
private fun ModeSegment(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Amber else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) OnAccent else White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            color = if (selected) OnAccent else White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else null,
            letterSpacing = tracked(1f),
            maxLines = 1
        )
    }
}

/**
 * Pick from everything a page holds: its own video (marked), the other videos it plays, its
 * photos and the videos it links to. Each row shows the cover, length and quality, so the real
 * video stands out from ads and previews. The page's video starts selected; a linked video's
 * qualities are looked up when its quality is tapped (otherwise the best is taken).
 */
@Composable
fun PageMediaPicker(
    items: List<PageItem>,
    pageTitle: String,
    pageUrl: String,
    resolving: Set<String>,
    onResolve: (String) -> Unit,
    onDownload: (List<FormatInfo>) -> Unit,
    modifier: Modifier = Modifier,
    maxListHeight: Dp = Dp.Unspecified,
    /**
     * A playlist: every video starts selected and they can be saved as sound only, which
     * [onDownloadAudio] receives instead of [onDownload].
     */
    playlist: Boolean = false,
    onDownloadAudio: (List<FormatInfo>) -> Unit = {}
) {
    val keys = items.map { it.key }
    // A playlist is downloaded as a whole by default. On a page, its own video starts
    // selected; without one, the best video found playing on it.
    var selected by remember(keys) {
        if (playlist) return@remember mutableStateOf(keys.toSet())
        val start = items.firstOrNull { it.isMain } ?: items.firstOrNull { !it.isLink && !it.isImage }
        mutableStateOf(setOfNotNull(start?.key))
    }
    var audioOnly by remember(keys) { mutableStateOf(false) }
    // The quality chosen per item (by [choiceKey]); none = its best.
    val chosen = remember(keys) { mutableStateMapOf<String, String>() }
    fun formatFor(item: PageItem): FormatInfo = item.formats.firstOrNull { it.choiceKey() == chosen[item.key] } ?: item.first
    val allSelected = selected.size == items.size
    val pageHost = hostOf(pageUrl)

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (playlist) stringResource(R.string.playlist_count, items.size)
                    else stringResource(R.string.page_items_count, items.size),
                    color = Amber,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    letterSpacing = tracked(1f)
                )
                Text(
                    stringResource(if (playlist) R.string.playlist_hint else R.string.page_items_hint),
                    color = White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }
            Text(
                stringResource(if (allSelected) R.string.clear_all else R.string.select_all),
                color = Amber,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { selected = if (allSelected) emptySet() else keys.toSet() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        if (playlist) {
            // Music mixes are often wanted as songs: save every pick as sound only.
            val shape = RoundedCornerShape(10.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(shape)
                    .background(Black)
                    .border(1.dp, Hairline, shape)
                    .padding(3.dp)
            ) {
                ModeSegment(Icons.Filled.OndemandVideo, stringResource(R.string.playlist_as_video), !audioOnly, { audioOnly = false }, Modifier.weight(1f))
                ModeSegment(Icons.Filled.MusicNote, stringResource(R.string.playlist_as_audio), audioOnly, { audioOnly = true }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }

        val list: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEach { item ->
                    val isSelected = item.key in selected
                    PageItemRow(
                        item = item,
                        format = formatFor(item),
                        audioOnly = audioOnly,
                        pageTitle = pageTitle,
                        pageHost = pageHost,
                        selected = isSelected,
                        resolving = item.key in resolving,
                        onToggle = { selected = if (isSelected) selected - item.key else selected + item.key },
                        onPickQuality = { f ->
                            chosen[item.key] = f.choiceKey()
                            selected = selected + item.key
                        },
                        onResolve = { onResolve(item.key) }
                    )
                }
            }
        }
        if (maxListHeight == Dp.Unspecified) {
            list()
        } else {
            Box(Modifier.fillMaxWidth().heightIn(max = maxListHeight).verticalScroll(rememberScrollState())) { list() }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected.isEmpty()) MediumGray else Amber)
                .clickable(enabled = selected.isNotEmpty()) {
                    val picks = items.filter { it.key in selected }.map(::formatFor)
                    if (audioOnly) onDownloadAudio(picks) else onDownload(picks)
                }
                .padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                stringResource(R.string.share_record_count, selected.size),
                color = if (selected.isEmpty()) White.copy(alpha = 0.4f) else OnAccent,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = tracked(2f)
            )
        }
    }
}

@Composable
private fun PageItemRow(
    item: PageItem,
    format: FormatInfo,
    audioOnly: Boolean,
    pageTitle: String,
    pageHost: String,
    selected: Boolean,
    resolving: Boolean,
    onToggle: () -> Unit,
    onPickQuality: (FormatInfo) -> Unit,
    onResolve: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Amber.copy(alpha = 0.10f) else Black)
            .border(1.dp, if (selected) Amber else Hairline, shape)
            .clickable(onClick = onToggle)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MediaThumb(item.cover, item.durationSec, isVideo = !item.isImage, modifier = Modifier.width(112.dp).aspectRatio(16f / 9f))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (item.isMain) {
                Text(
                    stringResource(R.string.page_item_main),
                    color = Amber,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = tracked(1.5f)
                )
            }
            val linkHost = hostOf(format.pageUrl)
            Text(
                item.title(pageTitle).ifBlank { linkHost.ifBlank { pageTitle } },
                color = White,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Saved as sound, a video's picture quality doesn't matter.
                if (audioOnly) {
                    Text(
                        "M4A",
                        color = White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, Hairline, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                } else {
                    QualityChip(item, format, resolving, onPickQuality, onResolve)
                }
                val details = buildList {
                    if (!item.isLink && !item.isImage) add(format.extension.uppercase())
                    sizeText(format.filesize)?.let(::add)
                    // A linked video on another site is often an ad; say where it goes.
                    if (linkHost.isNotBlank() && linkHost != pageHost) add(linkHost)
                }.joinToString(" · ")
                if (details.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        details,
                        color = White.copy(alpha = 0.45f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) Amber else Color.Transparent)
                .border(1.5.dp, if (selected) Amber else White.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = OnAccent, modifier = Modifier.size(16.dp))
        }
    }
}

/** The item's quality: a menu of its qualities, or (linked video) a tap that looks them up. */
@Composable
private fun QualityChip(
    item: PageItem,
    format: FormatInfo,
    resolving: Boolean,
    onPick: (FormatInfo) -> Unit,
    onResolve: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    val choosable = item.isLink || item.formats.size > 1
    val label = when {
        item.isImage -> stringResource(R.string.page_item_photo)
        item.isLink -> stringResource(R.string.page_item_best)
        else -> format.resolution
    }
    Box {
        Row(
            Modifier
                .clip(shape)
                .border(1.dp, if (choosable) Amber.copy(alpha = 0.6f) else Hairline, shape)
                .then(
                    if (choosable && !resolving) Modifier.clickable { if (item.isLink) onResolve() else menu = true }
                    else Modifier
                )
                .padding(start = 7.dp, end = if (choosable) 2.dp else 7.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = if (choosable) Amber else White.copy(alpha = 0.8f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
            if (resolving) {
                Spacer(Modifier.width(6.dp))
                CircularProgressIndicator(color = Amber, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(5.dp))
            } else if (choosable) {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.page_item_choose_quality), tint = Amber, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            item.formats.forEach { f ->
                DropdownMenuItem(
                    text = {
                        Text(
                            listOfNotNull(f.resolution, f.extension.uppercase(), sizeText(f.filesize)).joinToString("  ·  "),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    },
                    trailingIcon = if (f.choiceKey() == format.choiceKey()) {
                        { Icon(Icons.Filled.Check, contentDescription = null, tint = Amber) }
                    } else null,
                    onClick = { menu = false; onPick(f) }
                )
            }
        }
    }
}

/** Identity of one quality within an item. */
private fun FormatInfo.choiceKey(): String = "$id|$resolution|$directUrl|$imageUrl"

private fun hostOf(url: String): String =
    runCatching { Uri.parse(url).host }.getOrNull().orEmpty().removePrefix("www.").removePrefix("m.")

private fun sizeText(bytes: Long): String? = when {
    bytes <= 0 -> null
    bytes >= 1_000_000_000 -> "%.1fGB".format(java.util.Locale.ROOT, bytes / 1e9)
    bytes >= 1_000_000 -> "%.0fMB".format(java.util.Locale.ROOT, bytes / 1e6)
    else -> "%.0fKB".format(java.util.Locale.ROOT, bytes / 1e3)
}
