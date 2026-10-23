package com.anydrop.app.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.anydrop.app.R
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.data.repository.LibraryScope
import com.anydrop.app.data.repository.MediaCollection
import com.anydrop.app.data.repository.MediaFilter
import com.anydrop.app.data.repository.SortMode
import com.anydrop.app.ui.components.DeleteConfirmDialog
import com.anydrop.app.ui.components.MediaInfoDialog
import com.anydrop.app.ui.components.MediaItemDropdown
import com.anydrop.app.ui.components.MediaRenameHost
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.DarkGray
import com.anydrop.app.ui.theme.LedRed
import com.anydrop.app.ui.theme.LightGray
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.OnAccent
import com.anydrop.app.ui.theme.White
import kotlinx.coroutines.flow.Flow
import com.anydrop.app.ui.theme.tracked

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onPlayMedia: (MediaItem) -> Unit = {},
    scrollToTopFlow: Flow<String>? = null
) {
    val mediaItems by viewModel.mediaItems.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val unsearchedCount by viewModel.unsearchedCount.collectAsState()
    val scope by viewModel.scope.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val scopeCounts by viewModel.scopeCounts.collectAsState()
    val favoriteIndex by viewModel.favoriteIndex.collectAsState()
    val listState = rememberLazyGridState()
    val focusManager = LocalFocusManager.current

    // Multi-select (long-press) + dialog state.
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val selectionMode = selectedIds.isNotEmpty()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var infoItem by remember { mutableStateOf<MediaItem?>(null) }
    var renameTarget by remember { mutableStateOf<MediaItem?>(null) }
    // Collections: items waiting for a collection to be picked, the name dialog
    // (new collection, possibly for those items, or a rename), the one to delete.
    var addTargets by remember { mutableStateOf<List<MediaItem>?>(null) }
    var nameDialog by remember { mutableStateOf<NameDialog?>(null) }
    var collectionToDelete by remember { mutableStateOf<MediaCollection?>(null) }
    val openCollection = (scope as? LibraryScope.Collection)?.let { open -> collections.firstOrNull { it.id == open.id } }

    val context = LocalContext.current

    fun toggleSelect(item: MediaItem) {
        selectedIds = if (selectedIds.contains(item.id)) selectedIds - item.id else selectedIds + item.id
    }

    fun selectedItems() = mediaItems.filter { selectedIds.contains(it.id) }

    fun addTo(collection: MediaCollection, items: List<MediaItem>) {
        viewModel.addToCollection(collection.id, items)
        Toast.makeText(context, context.getString(R.string.added_to_collection, collection.name), Toast.LENGTH_SHORT).show()
        selectedIds = emptySet()
    }

    // A search that hides selected items drops them from the selection, so the
    // count and DELETE only ever cover what is on screen.
    LaunchedEffect(mediaItems) {
        if (selectedIds.isNotEmpty()) {
            val visible = mediaItems.mapTo(HashSet()) { it.id }
            selectedIds = selectedIds.filterTo(HashSet()) { it in visible }
        }
    }

    // Scrolling the results puts the keyboard away.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) focusManager.clearFocus()
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
    MediaRenameHost(target = renameTarget, onDismiss = { renameTarget = null }, rename = viewModel::rename)
    infoItem?.let { item ->
        MediaInfoDialog(item = item, onDismiss = { infoItem = null })
    }
    addTargets?.let { targets ->
        AddToCollectionDialog(
            collections = collections,
            counts = scopeCounts,
            onPick = { collection -> addTargets = null; addTo(collection, targets) },
            onNew = { addTargets = null; nameDialog = NameDialog.Create(targets) },
            onDismiss = { addTargets = null }
        )
    }
    nameDialog?.let { dialog ->
        when (dialog) {
            is NameDialog.Create -> CollectionNameDialog(
                title = stringResource(R.string.new_collection_title),
                initial = "",
                confirmLabel = stringResource(R.string.btn_create),
                onConfirm = { name ->
                    nameDialog = null
                    val created = viewModel.createCollection(name)
                    if (dialog.items.isNotEmpty()) addTo(created, dialog.items)
                    else viewModel.setScope(LibraryScope.Collection(created.id))
                },
                onDismiss = { nameDialog = null }
            )
            is NameDialog.Rename -> CollectionNameDialog(
                title = stringResource(R.string.rename_collection_title),
                initial = dialog.collection.name,
                confirmLabel = stringResource(R.string.btn_save),
                onConfirm = { name -> nameDialog = null; viewModel.renameCollection(dialog.collection.id, name) },
                onDismiss = { nameDialog = null }
            )
        }
    }
    collectionToDelete?.let { collection ->
        DeleteCollectionDialog(
            name = collection.name,
            onConfirm = { collectionToDelete = null; viewModel.deleteCollection(collection.id) },
            onDismiss = { collectionToDelete = null }
        )
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
            // Selection action bar: cancel, count, then favorite / add to (or remove from)
            // a collection / delete for everything selected.
            val selection = selectedItems()
            val allFavorite = selection.isNotEmpty() && selection.all { it in favoriteIndex }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { selectedIds = emptySet() }) {
                    Text(stringResource(R.string.btn_cancel), color = White.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                Text(
                    stringResource(R.string.selected_count, selectedIds.size),
                    color = Amber,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.toggleFavorite(selection) }) {
                    Icon(
                        if (allFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(if (allFavorite) R.string.favorite_remove else R.string.favorite_add),
                        tint = if (allFavorite) LedRed else White
                    )
                }
                if (openCollection != null) {
                    IconButton(onClick = {
                        viewModel.removeFromCollection(openCollection.id, selection)
                        selectedIds = emptySet()
                    }) {
                        Icon(Icons.Filled.FolderOff, contentDescription = stringResource(R.string.remove_from_collection), tint = White)
                    }
                } else {
                    IconButton(onClick = { addTargets = selection }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = stringResource(R.string.add_to_collection), tint = White)
                    }
                }
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

        Spacer(modifier = Modifier.height(12.dp))

        LibrarySearchField(
            query = searchQuery,
            onQueryChange = viewModel::setSearchQuery
        )

        Spacer(modifier = Modifier.height(10.dp))

        ScopeChips(
            scope = scope,
            collections = collections,
            counts = scopeCounts,
            onSelect = { selectedIds = emptySet(); viewModel.setScope(it) },
            onNewCollection = { nameDialog = NameDialog.Create(emptyList()) },
            onRename = { nameDialog = NameDialog.Rename(it) },
            onDelete = { collectionToDelete = it }
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (searchQuery.isBlank()) {
                    stringResource(R.string.tapes_count, mediaItems.size)
                } else {
                    stringResource(R.string.tapes_count_filtered, mediaItems.size, unsearchedCount)
                },
                color = White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = tracked(2f)
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

        if (mediaItems.isEmpty() && searchQuery.isNotBlank()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.SearchOff,
                    contentDescription = null,
                    tint = White.copy(alpha = 0.25f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.library_no_results, searchQuery.trim()),
                    color = White.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = { viewModel.setSearchQuery("") }) {
                    Text(
                        stringResource(R.string.library_clear_search),
                        color = Amber,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        letterSpacing = tracked(1f)
                    )
                }
            }
        } else if (mediaItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(
                        when (scope) {
                            LibraryScope.All -> R.string.library_empty
                            LibraryScope.Favorites -> R.string.favorites_empty
                            is LibraryScope.Collection -> R.string.collection_empty
                        }
                    ),
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
                        favorite = item in favoriteIndex,
                        onClick = {
                            if (selectionMode) toggleSelect(item) else onPlayMedia(item)
                        },
                        onLongClick = { toggleSelect(item) },
                        onToggleFavorite = { viewModel.toggleFavorite(listOf(item)) },
                        onAddToCollection = { addTargets = listOf(item) },
                        onDelete = { selectedIds = setOf(item.id); showDeleteConfirm = true },
                        onRename = { renameTarget = item },
                        onInfo = { infoItem = item }
                    )
                }
            }
        }
    }
}

/** The collection name dialog: a new collection (for [Create.items], if any) or a rename. */
private sealed interface NameDialog {
    data class Create(val items: List<MediaItem>) : NameDialog
    data class Rename(val collection: MediaCollection) : NameDialog
}

/**
 * Library search. Filters as you type (title, creator, platform, file name);
 * the keyboard's search key or a scroll of the grid puts the keyboard away.
 */
@Composable
private fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        if (focused) Amber else LightGray,
        animationSpec = tween(180),
        label = "searchBorder"
    )
    val shape = RoundedCornerShape(10.dp)

    BasicTextField(
        value = query,
        onValueChange = { onQueryChange(it.replace("\n", "")) },
        singleLine = true,
        textStyle = TextStyle(color = White, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
        cursorBrush = SolidColor(Amber),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(shape)
                    .background(Black)
                    .border(1.dp, borderColor, shape)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = if (focused || query.isNotEmpty()) Amber else White.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.library_search_hint),
                            color = White.copy(alpha = 0.4f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn(tween(120)) + scaleIn(tween(120), initialScale = 0.6f),
                    exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.6f)
                ) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.search_clear_desc),
                            tint = White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    )
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
            letterSpacing = tracked(1f)
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
    favorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onAddToCollection: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRename: () -> Unit = {},
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
                    // Titles read in their own direction ("@name …" stays intact in Arabic).
                    style = TextStyle(textDirection = TextDirection.Content),
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp)
                )
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add),
                        tint = if (favorite) LedRed else White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu_desc), tint = White.copy(alpha = 0.6f))
                    }
                    MediaItemDropdown(
                        item = item,
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        onDelete = onDelete,
                        onInfo = onInfo,
                        onAddToCollection = onAddToCollection,
                        onRename = onRename
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
