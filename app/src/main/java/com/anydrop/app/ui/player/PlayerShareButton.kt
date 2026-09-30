package com.anydrop.app.ui.player

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.anydrop.app.R
import com.anydrop.app.data.model.MediaItem
import com.anydrop.app.ui.components.shareMedia
import com.anydrop.app.ui.components.shareSourceLink
import com.anydrop.app.ui.components.sourceUrl

/**
 * Share from the player. When the video came from a web page the user picks what to
 * send, the video itself or the link to its original page; without a source it goes
 * straight to sharing the file.
 */
@Composable
fun PlayerShareButton(
    item: MediaItem,
    tint: Color,
    menuColor: Color,
    onMenuOpenChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val source = item.sourceUrl
    var menuOpen by remember { mutableStateOf(false) }
    fun setMenu(open: Boolean) {
        menuOpen = open
        onMenuOpenChange(open)
    }

    Box {
        IconButton(onClick = {
            if (source == null) shareMedia(context, item) else setMenu(true)
        }) {
            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.menu_share), tint = tint)
        }
        if (source != null) {
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { setMenu(false) },
                modifier = Modifier.background(menuColor)
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share_video), color = Color.White) },
                    leadingIcon = { Icon(Icons.Filled.Videocam, null, tint = Color.White) },
                    onClick = {
                        setMenu(false)
                        shareMedia(context, item)
                    }
                )
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(R.string.share_link), color = Color.White)
                            Text(
                                hostOf(source),
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(textDirection = TextDirection.Ltr)
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Filled.Link, null, tint = Color.White) },
                    onClick = {
                        setMenu(false)
                        shareSourceLink(context, item)
                    }
                )
            }
        }
    }
}

private fun hostOf(url: String): String =
    runCatching { Uri.parse(url).host }.getOrNull()?.removePrefix("www.") ?: url
