package com.anydrop.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anydrop.app.R
import com.anydrop.app.engine.TorrentFile
import com.anydrop.app.engine.TorrentFileKind
import com.anydrop.app.engine.TorrentMeta
import com.anydrop.app.engine.sizeLabel
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.Hairline
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.OnAccent
import com.anydrop.app.ui.theme.White
import com.anydrop.app.ui.theme.tracked

/**
 * Pick what to download from a torrent: its videos, music and pictures with their sizes.
 * The videos start ticked (minus small extras like a "sample" clip). Other files in the
 * torrent (subtitles, text) aren't offered; the note under the list says how many.
 */
@Composable
fun TorrentPicker(
    name: String,
    files: List<TorrentFile>,
    onDownload: (List<TorrentFile>) -> Unit,
    modifier: Modifier = Modifier,
    maxListHeight: Dp = Dp.Unspecified
) {
    val meta = remember(files) { TorrentMeta("", name, files.sumOf { it.size }, files) }
    val shown = remember(files) { meta.mediaFiles }
    val hidden = files.size - shown.size
    var selected by remember(files) { mutableStateOf(meta.defaultSelection()) }
    val allSelected = shown.isNotEmpty() && selected.size == shown.size
    val chosen = shown.filter { it.index in selected }

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Amber.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.DeviceHub, contentDescription = null, tint = Amber, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.torrent_title),
                    color = Amber,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = tracked(1.5f)
                )
                Text(name, color = White, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.torrent_summary, files.size, ltr(sizeLabel(meta.totalSize))),
                    color = White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (shown.size > 1) {
                Text(
                    stringResource(if (allSelected) R.string.clear_all else R.string.select_all),
                    color = Amber,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { selected = if (allSelected) emptySet() else shown.map { it.index }.toSet() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        if (shown.isEmpty()) {
            Text(stringResource(R.string.torrent_no_media), color = White.copy(alpha = 0.75f), fontSize = 13.sp, lineHeight = 19.sp)
        } else {
            val list: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    shown.forEach { file ->
                        val isSelected = file.index in selected
                        TorrentFileRow(file, isSelected) {
                            selected = if (isSelected) selected - file.index else selected + file.index
                        }
                    }
                }
            }
            if (maxListHeight == Dp.Unspecified) list()
            else Box(Modifier.fillMaxWidth().heightIn(max = maxListHeight).verticalScroll(rememberScrollState())) { list() }
        }
        if (hidden > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                pluralStringResource(R.plurals.torrent_other_files, hidden, hidden),
                color = White.copy(alpha = 0.45f),
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.torrent_note),
            color = White.copy(alpha = 0.45f),
            fontSize = 11.sp,
            lineHeight = 16.sp
        )

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (chosen.isEmpty()) MediumGray else Amber)
                .clickable(enabled = chosen.isNotEmpty()) { onDownload(chosen) }
                .padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                if (chosen.isEmpty()) stringResource(R.string.share_record_count, 0)
                else stringResource(R.string.torrent_record, chosen.size, ltr(sizeLabel(chosen.sumOf { it.size }))),
                color = if (chosen.isEmpty()) White.copy(alpha = 0.4f) else OnAccent,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = tracked(2f)
            )
        }
    }
}

/** Keeps "129 MB" in one piece inside right-to-left text. */
private fun ltr(text: String) = "\u2066$text\u2069"

@Composable
private fun TorrentFileRow(file: TorrentFile, selected: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Amber.copy(alpha = 0.10f) else Black)
            .border(1.dp, if (selected) Amber else Hairline, shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            when (file.kind) {
                TorrentFileKind.AUDIO -> Icons.Filled.MusicNote
                TorrentFileKind.IMAGE -> Icons.Filled.Image
                else -> Icons.Filled.Movie
            },
            contentDescription = null,
            tint = Amber,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                file.name,
                color = White,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // A file name reads in its own direction ("__thumb.jpg" stays as is in Arabic).
                style = TextStyle(textDirection = TextDirection.Content)
            )
            Text(
                listOf(file.extension.uppercase(), sizeLabel(file.size)).filter { it.isNotEmpty() }.joinToString(" · "),
                color = White.copy(alpha = 0.45f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
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
