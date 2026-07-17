package com.anydrop.app

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow

// Legacy one-shot (still read on cold start).
var pendingSharedUrl: String? = null

/**
 * Reactive stream of shared URLs. MainActivity.onNewIntent emits here; the deck
 * ViewModel collects it, so a share into an ALREADY-RUNNING app (singleTask) still
 * pastes the URL — the ViewModel's init{} only runs once and can't catch later shares.
 */
val sharedUrlFlow = MutableStateFlow<String?>(null)

/**
 * App-lifetime coroutine scope for downloads. Downloads must NOT run in a
 * viewModelScope — from the share bottom sheet the ViewModel is cleared as soon as
 * ShareActivity finishes, which would cancel the download mid-flight (before the
 * MediaStore export), leaving files invisible to other apps. This scope survives
 * the Activity/ViewModel so the download + public export always completes.
 */
val appDownloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@HiltAndroidApp
class AnyDropApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .build()
        Coil.setImageLoader(imageLoader)
    }
}
