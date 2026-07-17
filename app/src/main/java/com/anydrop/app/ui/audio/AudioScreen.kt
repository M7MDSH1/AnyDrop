package com.anydrop.app.ui.audio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.ui.components.DeleteConfirmDialog
import com.anydrop.app.ui.components.MediaInfoDialog
import com.anydrop.app.ui.components.MediaItemDropdown
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.DarkGray
import com.anydrop.app.ui.theme.LedRed
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.White
import kotlinx.coroutines.flow.Flow

@Composable
fun AudioScreen(
    viewModel: AudioViewModel = hiltViewModel(),
    onPlayMedia: (MediaItem) -> Unit = {},
    scrollToTopFlow: Flow<String>? = null
) {
    val audioItems by viewModel.audioItems.collectAsState()
    val listState = rememberLazyListState()

    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectionMode = selectedIds.isNotEmpty()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var infoItem by remember { mutableStateOf<MediaItem?>(null) }

    fun toggleSelect(item: MediaItem) {
        selectedIds = if (selectedIds.contains(item.id)) selectedIds - item.id else selectedIds + item.id
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            count = selectedIds.size,
            onConfirm = {
                audioItems.filter { selectedIds.contains(it.id) }.forEach { viewModel.deleteItem(it) }
                selectedIds = emptySet()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
    infoItem?.let { item -> MediaInfoDialog(item = item, onDismiss = { infoItem = null }) }

    // Request audio-read permission so we can list all device audio, then refresh.
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.refresh()
    }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) viewModel.refresh() else permLauncher.launch(permission)
    }

    LaunchedEffect(scrollToTopFlow) {
        scrollToTopFlow?.collect { route ->
            if (route == "audio") {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { selectedIds = emptySet() }) {
                    Text("CANCEL", color = White.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                Text("${selectedIds.size} SELECTED", color = Amber, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LedRed)
                }
            }
        } else {
            Text(
                text = "AUDIO",
                style = MaterialTheme.typography.headlineMedium,
                color = Amber,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "TRACKS: ${audioItems.size}",
            color = White.copy(alpha = 0.4f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (audioItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No audio tracks yet.\nToggle \"AUDIO ONLY\" on the deck to rip audio!",
                    color = White.copy(alpha = 0.3f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = listState
            ) {
                items(audioItems, key = { it.id }) { item ->
                    AudioRow(
                        item = item,
                        selected = selectedIds.contains(item.id),
                        selectionMode = selectionMode,
                        onClick = { if (selectionMode) toggleSelect(item) else onPlayMedia(item) },
                        onLongClick = { toggleSelect(item) },
                        onDeleteMenu = { selectedIds = setOf(item.id); showDeleteConfirm = true },
                        onInfo = { infoItem = item }
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AudioRow(
    item: MediaItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteMenu: () -> Unit,
    onInfo: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.border(2.dp, Amber, RoundedCornerShape(6.dp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MediumGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Amber,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = item.title,
                color = White,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (selectionMode) {
                Text(
                    text = if (selected) "✓" else "○",
                    color = if (selected) Amber else White.copy(alpha = 0.5f),
                    fontSize = 16.sp,
                    modifier = Modifier.size(32.dp).padding(6.dp)
                )
            } else {
                IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Amber)
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = White.copy(alpha = 0.6f))
                    }
                    MediaItemDropdown(
                        item = item,
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        onDelete = onDeleteMenu,
                        onInfo = onInfo
                    )
                }
            }
        }
    }
}
