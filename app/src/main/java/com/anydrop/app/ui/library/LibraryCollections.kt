package com.anydrop.app.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anydrop.app.R
import com.anydrop.app.data.repository.LibraryScope
import com.anydrop.app.data.repository.MediaCollection
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.Hairline
import com.anydrop.app.ui.theme.LedRed
import com.anydrop.app.ui.theme.LightGray
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.OnAccent
import com.anydrop.app.ui.theme.White

/**
 * What part of the library to show: everything, the favorites, or one of the user's
 * collections; the last chip makes a new collection. Long-press a collection to rename
 * or delete it.
 */
@Composable
fun ScopeChips(
    scope: LibraryScope,
    collections: List<MediaCollection>,
    counts: Map<String, Int>,
    onSelect: (LibraryScope) -> Unit,
    onNewCollection: () -> Unit,
    onRename: (MediaCollection) -> Unit,
    onDelete: (MediaCollection) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            ScopeChip(
                label = stringResource(R.string.scope_all),
                selected = scope == LibraryScope.All,
                onClick = { onSelect(LibraryScope.All) }
            )
        }
        item {
            ScopeChip(
                label = stringResource(R.string.favorites),
                count = counts[LibraryViewModel.FAVORITES_KEY],
                icon = Icons.Filled.Favorite,
                selected = scope == LibraryScope.Favorites,
                onClick = { onSelect(LibraryScope.Favorites) }
            )
        }
        items(collections, key = { it.id }) { collection ->
            var menu by remember { mutableStateOf(false) }
            Box {
                ScopeChip(
                    label = collection.name,
                    count = counts[collection.id],
                    icon = Icons.Filled.FolderOpen,
                    selected = scope == LibraryScope.Collection(collection.id),
                    onClick = { onSelect(LibraryScope.Collection(collection.id)) },
                    onLongClick = { menu = true }
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.collection_rename)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menu = false; onRename(collection) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.collection_delete), color = LedRed) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = LedRed) },
                        onClick = { menu = false; onDelete(collection) }
                    )
                }
            }
        }
        item {
            ScopeChip(
                label = stringResource(R.string.new_collection),
                icon = Icons.Filled.Add,
                selected = false,
                accentOutline = true,
                onClick = onNewCollection
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScopeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    count: Int? = null,
    icon: ImageVector? = null,
    accentOutline: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(50)
    val content = when {
        selected -> OnAccent
        accentOutline -> Amber
        else -> White.copy(alpha = 0.85f)
    }
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(shape)
            .background(if (selected) Amber else Black)
            .border(1.dp, if (selected) Amber else if (accentOutline) Amber.copy(alpha = 0.6f) else Hairline, shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { long ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        long()
                    }
                }
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) OnAccent else if (icon == Icons.Filled.Favorite) LedRed else content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        // Long collection names stay one line on the chip.
        Text(label, color = content, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text("$count", color = content.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}

/** Pick the collection to add the chosen items to, or make a new one. */
@Composable
fun AddToCollectionDialog(
    collections: List<MediaCollection>,
    counts: Map<String, Int>,
    onPick: (MediaCollection) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MediumGray,
        title = { Text(stringResource(R.string.add_to_collection_title), color = Amber, fontFamily = FontFamily.Monospace) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(collections, key = { it.id }) { collection ->
                    PickerRow(
                        icon = Icons.Filled.FolderOpen,
                        label = collection.name,
                        trailing = counts[collection.id]?.toString(),
                        onClick = { onPick(collection) }
                    )
                }
                item {
                    PickerRow(icon = Icons.Filled.Add, label = stringResource(R.string.new_collection), accent = true, onClick = onNew)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
            }
        }
    )
}

@Composable
private fun PickerRow(icon: ImageVector, label: String, onClick: () -> Unit, trailing: String? = null, accent: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Amber, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            color = if (accent) Amber else White,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) Text(trailing, color = White.copy(alpha = 0.5f), fontSize = 13.sp)
    }
}

/** Name a new collection, or rename one. */
@Composable
fun CollectionNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val valid = name.isNotBlank()
    val confirm: () -> Unit = { if (name.isNotBlank()) onConfirm(name.trim()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MediumGray,
        title = { Text(title, color = Amber, fontFamily = FontFamily.Monospace) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.replace("\n", "").take(40) },
                    placeholder = { Text(stringResource(R.string.collection_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Amber,
                        unfocusedBorderColor = LightGray,
                        cursorColor = Amber,
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        focusedPlaceholderColor = White.copy(alpha = 0.35f),
                        unfocusedPlaceholderColor = White.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = confirm, enabled = valid) {
                Text(confirmLabel, color = if (valid) Amber else White.copy(alpha = 0.3f), fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
            }
        }
    )
}

@Composable
fun DeleteCollectionDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MediumGray,
        title = { Text(stringResource(R.string.collection_delete_title), color = LedRed, fontFamily = FontFamily.Monospace) },
        text = { Text(stringResource(R.string.collection_delete_message, name), color = White, fontSize = 13.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.collection_delete), color = LedRed, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
            }
        }
    )
}
