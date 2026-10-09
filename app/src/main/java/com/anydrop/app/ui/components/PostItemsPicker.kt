package com.anydrop.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anydrop.app.R
import com.anydrop.app.engine.YtDlpEngine.FormatInfo
import com.anydrop.app.engine.clockText
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.OnAccent
import com.anydrop.app.ui.theme.White
import com.anydrop.app.ui.theme.tracked

/**
 * Pick what to download from a multi-photo/video post: every slide in the post's order,
 * photos and videos alike, all selected to start with. One button downloads the choice,
 * each slide as its own file.
 */
@Composable
fun PostItemsPicker(
    items: List<FormatInfo>,
    onDownload: (List<FormatInfo>) -> Unit,
    modifier: Modifier = Modifier,
    maxRows: Int = 3
) {
    // Selection by position in [items]; a new post starts with everything selected.
    var selected by remember(items) { mutableStateOf(items.indices.toSet()) }
    val videos = items.count { !it.isImage }
    val photos = items.size - videos
    val allSelected = selected.size == items.size

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    stringResource(R.string.post_items_count, items.size),
                    color = Amber,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    letterSpacing = tracked(1f)
                )
                Text(
                    stringResource(R.string.post_items_mix, videos, photos),
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
                    .clickable { selected = if (allSelected) emptySet() else items.indices.toSet() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(10.dp))

        // The grid gets an exact height (it may sit in a scrolling column): up to
        // [maxRows] rows of square tiles, plus a peek of the next row when it scrolls.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val spacing = 6.dp
            val tile = (maxWidth - spacing * 2) / 3
            val rows = (items.size + 2) / 3
            val shownRows = rows.coerceAtMost(maxRows)
            val gridHeight = tile * shownRows + spacing * (shownRows - 1) + if (rows > maxRows) tile * 0.4f else 0.dp
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(gridHeight),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                items(items.indices.toList()) { index ->
                    val isSelected = index in selected
                    PostItemTile(
                        item = items[index],
                        number = index + 1,
                        selected = isSelected,
                        onClick = { selected = if (isSelected) selected - index else selected + index }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected.isEmpty()) MediumGray else Amber)
                .clickable(enabled = selected.isNotEmpty()) {
                    onDownload(selected.sorted().map { items[it] })
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
private fun PostItemTile(item: FormatInfo, number: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(Black)
            .then(if (selected) Modifier.border(2.dp, Amber, shape) else Modifier)
            .clickable(onClick = onClick)
    ) {
        val preview = item.previewUrl.ifBlank { item.imageUrl }
        if (preview.isNotBlank()) {
            AsyncImage(
                model = preview,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (!selected) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))

        // Videos are marked with a play badge (and a big play sign when there is no cover).
        if (!item.isImage) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.post_item_video),
                tint = Color.White,
                modifier = Modifier
                    .align(if (preview.isBlank()) Alignment.Center else Alignment.BottomStart)
                    .padding(5.dp)
                    .size(if (preview.isBlank()) 34.dp else 20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(2.dp)
            )
        }
        val clock = clockText(item.durationSec)
        if (!item.isImage && clock.isNotEmpty()) DurationBadge(clock, Modifier.align(Alignment.BottomEnd).padding(5.dp))
        Text(
            "$number",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(5.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 6.dp, vertical = 1.dp)
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = OnAccent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Amber)
                    .padding(2.dp)
            )
        }
    }
}
