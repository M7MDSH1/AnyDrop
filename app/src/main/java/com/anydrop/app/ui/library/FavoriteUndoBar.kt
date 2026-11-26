package com.anydrop.app.ui.library

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anydrop.app.R
import com.anydrop.app.data.repository.CollectionsRepository
import com.anydrop.app.ui.components.PillButton
import com.anydrop.app.ui.components.PillStyle
import com.anydrop.app.ui.theme.AccentText
import com.anydrop.app.ui.theme.Hairline
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.White
import kotlinx.coroutines.delay

/**
 * "Removed from favorites · Undo", for a few seconds after favorites are removed in the
 * Library. The app's main screen shows it over whichever tab is open; it takes touches
 * only on itself, so everything around it keeps working.
 */
@Composable
fun FavoriteUndoBar(collections: CollectionsRepository, modifier: Modifier = Modifier) {
    val removed = collections.removedFavorites.collectAsState().value ?: return
    // Its time is kept with it, so leaving and reopening the screen doesn't start it over.
    LaunchedEffect(removed) {
        delay((removed.until - SystemClock.elapsedRealtime()).coerceAtLeast(0))
        collections.dismissRemovedFavorites(removed)
    }
    Row(
        modifier
            .clip(CircleShape)
            .background(MediumGray)
            .border(1.dp, Hairline, CircleShape)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.FavoriteBorder, null, tint = AccentText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (removed.refs.size > 1) stringResource(R.string.favorites_removed, removed.refs.size)
            else stringResource(R.string.favorite_removed),
            color = White,
            fontSize = 14.sp
        )
        Spacer(Modifier.width(10.dp))
        PillButton(
            text = stringResource(R.string.undo),
            onClick = collections::undoRemovedFavorites,
            style = PillStyle.Ghost,
            small = true
        )
    }
}
