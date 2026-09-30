package com.anydrop.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.anydrop.app.data.repository.readAppLanguageSync
import com.anydrop.app.ui.player.PlayerScreen
import com.anydrop.app.ui.player.PlayerViewModel
import com.anydrop.app.ui.settings.AppearanceViewModel
import com.anydrop.app.ui.theme.AnyDropTheme
import com.anydrop.app.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint

/**
 * Plays videos other apps hand to AnyDrop: "Open with", or AnyDrop chosen as the
 * default video player (the manifest's VIEW filter for video files is what lists AnyDrop
 * there). It opens in the caller's task, so Back returns to the app the video
 * came from, and uses the same player as the library.
 */
@AndroidEntryPoint
class VideoViewerActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(
            LocaleHelper.wrapAppLocale(newBase, readAppLanguageSync(newBase))
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }
        val title = displayNameOf(uri)

        setContent {
            val appearanceViewModel: AppearanceViewModel = hiltViewModel()
            val darkMode by appearanceViewModel.darkMode.collectAsState()
            AnyDropTheme(darkTheme = darkMode) {
                val playerViewModel: PlayerViewModel = hiltViewModel()
                LaunchedEffect(uri) { playerViewModel.openExternal(uri, title) }
                PlayerScreen(
                    viewModel = playerViewModel,
                    onExit = ::finish,
                    externalFile = true
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Home / recents while playing → picture-in-picture, as in the main app.
        PipLeaveHandler.onUserLeave?.invoke()
    }

    /** The file's name as the sending app reports it, else the last path segment. */
    private fun displayNameOf(uri: Uri): String {
        val fromProvider = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: getString(R.string.app_name)
    }
}
