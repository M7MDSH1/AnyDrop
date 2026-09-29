package com.anydrop.app.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.anydrop.app.R
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.data.repository.MediaFilter
import com.anydrop.app.data.repository.SortMode
import com.anydrop.app.ui.components.DeleteConfirmDialog
import com.anydrop.app.ui.components.MediaInfoDialog
import com.anydrop.app.ui.components.MediaItemDropdown
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.DarkGray
import com.anydrop.app.ui.theme.LedRed
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.OnAccent
import com.anydrop.app.ui.theme.White
import kotlinx.coroutines.flow.Flow

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onPlayMedia: (MediaItem) -> Unit = {},
    scrollToTopFlow: Flow<String>? = null
) {
    val mediaItems by viewModel.mediaItems.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val listState = rememberLazyGridState()

    // Multi-select (long-press) + dialog state.
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectionMode = selectedIds.isNotEmpty()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var infoItem by remember { mutableStateOf<MediaItem?>(null) }

    val context = LocalContext.current

    fun toggleSelect(item: MediaItem) {
        selectedIds = if (selectedIds.contains(item.id)) selectedIds - item.id else selectedIds + item.id
    }

    // Launcher for the OS delete-permission flow (MediaStore.createDeleteRequest
    // on Android 11+, RecoverableSecurityException sender on Android 10). Only
    // fires when deleting media that belongs to OTHER apps.
    val deleteConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        viewModel.refresh()
    }

    // Dialogs
    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            count = selectedIds.size,
            onConfirm = {
                val selected = mediaItems.filter { selectedIds.contains(it.id) }
                val result = viewModel.deleteItems(selected)
                selectedIds = emptySet()
                showDeleteConfirm = false
                if (result.consentUris.isNotEmpty()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val pending = MediaStore.createDeleteRequest(context.contentResolver, result.consentUris)
                        deleteConsentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    } else if (result.consentSender != null) {
                        deleteConsentLauncher.launch(
                            IntentSenderRequest.Builder(result.consentSender).build()
                        )
                    }
                }
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
    infoItem?.let { item ->
        MediaInfoDialog(item = item, onDismiss = { infoItem = null })
    }

    // Request media-read permissions so we can list ALL device videos & images,
    // then refresh. On Android 13+ these are READ_MEDIA_VIDEO / READ_MEDIA_IMAGES.
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) viewModel.refresh()
    }
    LaunchedEffect(Unit) {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val allGranted = perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) viewModel.refresh() else permLauncher.launch(perms)
    }

    LaunchedEffect(scrollToTopFlow) {
        scrollToTopFlow?.collect { route ->
            if (route == "library") {
                listState.animateScrollToItem(0)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkGray)
            .padding(16.dp)
    ) {
        if (selectionMode) {
            // Selection action bar: count + trash + cancel.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { selectedIds = emptySet() }) {
                    Text(stringResource(R.string.btn_cancel), color = White.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                Text(
                    stringResource(R.string.selected_count, selectedIds.size),
                    color = Amber,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_desc), tint = LedRed)
                }
            }
        } else {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Amber,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.tapes_count, mediaItems.size),
                color = White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                SortButton(
                    label = stringResource(R.string.sort_date),
                    active = sortMode == SortMode.DATE,
                    onClick = { viewModel.setSortMode(SortMode.DATE) }
                )
                SortButton(
                    label = stringResource(R.string.sort_length),
                    active = sortMode == SortMode.DURATION,
                    onClick = { viewModel.setSortMode(SortMode.DURATION) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Media-type filter: All / Videos / Images.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            SortButton(
                label = stringResource(R.string.filter_all),
                active = filter == MediaFilter.ALL,
                onClick = { viewModel.setFilter(MediaFilter.ALL) }
            )
            SortButton(
                label = stringResource(R.string.filter_videos),
                active = filter == MediaFilter.VIDEOS,
                onClick = { viewModel.setFilter(MediaFilter.VIDEOS) }
            )
            SortButton(
                label = stringResource(R.string.filter_images),
                active = filter == MediaFilter.IMAGES,
                onClick = { viewModel.setFilter(MediaFilter.IMAGES) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (mediaItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.library_empty),
                    color = White.copy(alpha = 0.3f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = listState
            ) {
                items(mediaItems, key = { it.id }) { item ->
                    LibraryCard(
                        item = item,
                        showDuration = sortMode == SortMode.DURATION,
                        selected = selectedIds.contains(item.id),
                        selectionMode = selectionMode,
                        onClick = {
                            if (selectionMode) toggleSelect(item) else onPlayMedia(item)
                        },
                        onLongClick = { toggleSelect(item) },
                        onDelete = { selectedIds = setOf(item.id); showDeleteConfirm = true },
                        onInfo = { infoItem = item }
                    )
                }
            }
        }
    }
}

@Composable
private fun SortButton(label: String, active: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.height(28.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            color = if (active) Amber else White.copy(alpha = 0.4f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LibraryCard(
    item: MediaItem,
    showDuration: Boolean = false,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    onInfo: () -> Unit = {}
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) Modifier.border(2.dp, Amber, RoundedCornerShape(6.dp)) else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Black)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(MediumGray),
                contentAlignment = Alignment.Center
            ) {
                if (item.localUri != null) {
                    AsyncImage(
                        model = item.localUri,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (item.localFilePath.isNotEmpty()) {
                    AsyncImage(
                        model = item.localFilePath,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("\uD83C\uDF9E", fontSize = 24.sp)
                }
                if (showDuration && item.durationMs > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(Black.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = formatDuration(item.durationMs),
                            color = White,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                // Selection tick overlay.
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(if (selected) Amber else Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (selected) "✓" else "○",
                            color = if (selected) OnAccent else White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    color = White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                )
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu_desc), tint = White.copy(alpha = 0.6f))
                    }
                    MediaItemDropdown(
                        item = item,
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        onDelete = onDelete,
                        onInfo = onInfo
                    )
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val mins = (totalSec % 3600) / 60
    val secs = totalSec % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, mins, secs)
    else "%02d:%02d".format(mins, secs)
}
