package com.anydrop.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import com.anydrop.app.data.repository.DownloadQueue
import com.anydrop.app.ui.browser.rememberBrowserState
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.anydrop.app.R
import com.anydrop.app.ui.navigation.AnyDropNavGraph
import com.anydrop.app.ui.navigation.Routes
import com.anydrop.app.data.repository.DeckSessionRepository
import com.anydrop.app.engine.LinkList
import com.anydrop.app.data.repository.readAppLanguageSync
import com.anydrop.app.util.LocaleHelper
import com.anydrop.app.ui.settings.AppearanceViewModel
import com.anydrop.app.ui.update.UpdatePrompt
import com.anydrop.app.ui.update.UpdateViewModel
import com.anydrop.app.util.Constants
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.DarkGray
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.OnAccent
import com.anydrop.app.ui.theme.White
import com.anydrop.app.ui.theme.AnyDropTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Bridge so the active video player can tell MainActivity to enter PiP when — and
 * ONLY when — the user genuinely leaves the app (Home / recents). The player
 * registers a callback while a video is playing and clears it otherwise.
 */
object PipLeaveHandler {
    var onUserLeave: (() -> Unit)? = null
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var navController by mutableStateOf<NavController?>(null)

    // A share or a download notification wants the deck. Kept until navigation exists: after the
    // process was killed, the intent can arrive before the screen is composed.
    private var deckRequested by mutableStateOf(false)

    @Inject
    lateinit var deckSessionRepository: DeckSessionRepository

    @Inject
    lateinit var downloadQueue: DownloadQueue

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(
            LocaleHelper.wrapAppLocale(newBase, readAppLanguageSync(newBase))
        )
    }

    // Set when the "update available" notification opened (or re-opened) the app.
    private var showUpdateRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        extractUrlFromIntent(intent)?.let { pendingSharedUrl = it }
        showUpdateRequested = intent?.getBooleanExtra(Constants.EXTRA_SHOW_UPDATE, false) == true
        // A tapped download notification opens the deck's downloads list. The extra is dropped
        // once read, so a rotation doesn't open the list again.
        if (intent?.getBooleanExtra(Constants.EXTRA_SHOW_DOWNLOADS, false) == true) {
            intent.removeExtra(Constants.EXTRA_SHOW_DOWNLOADS)
            showDownloadsRequest.value = true
            deckRequested = true
        }
        // Reopen on the tab the user last left, unless a share or the downloads list is pending (DECK).
        val startTab = if (pendingSharedUrl != null || deckRequested) Routes.TAPE_DECK
        else deckSessionRepository.lastTab(Routes.TAPE_DECK)

        setContent {
            val appearanceViewModel: AppearanceViewModel = hiltViewModel()
            val darkMode by appearanceViewModel.darkMode.collectAsState()
            val updateViewModel: UpdateViewModel = hiltViewModel()
            LaunchedEffect(Unit) { updateViewModel.checkOnLaunch() }
            LaunchedEffect(showUpdateRequested) {
                if (showUpdateRequested) {
                    showUpdateRequested = false
                    updateViewModel.showUpdate()
                }
            }
            LaunchedEffect(deckRequested, navController) {
                val nav = navController ?: return@LaunchedEffect
                if (!deckRequested) return@LaunchedEffect
                deckRequested = false
                nav.navigate(Routes.TAPE_DECK) {
                    popUpTo(0) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            AnyDropTheme(darkTheme = darkMode) {
                AnyDropMainScreen(
                    onNavControllerReady = { navController = it },
                    startDestination = startTab,
                    deckSessionRepository = deckSessionRepository,
                    downloadQueue = downloadQueue
                )
                UpdatePrompt(updateViewModel)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Fires ONLY on genuine user-leave (Home/recents), NOT on back-press or
        // in-app navigation — so it won't trigger on skip or Back.
        PipLeaveHandler.onUserLeave?.invoke()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(Constants.EXTRA_SHOW_UPDATE, false)) showUpdateRequested = true
        if (intent.getBooleanExtra(Constants.EXTRA_SHOW_DOWNLOADS, false)) {
            showDownloadsRequest.value = true
            deckRequested = true
        }
        extractUrlFromIntent(intent)?.let { url ->
            pendingSharedUrl = url
            // Notify the running deck ViewModel so it pastes the URL immediately.
            sharedUrlFlow.value = url
            deckRequested = true
        }
    }

    private fun extractUrlFromIntent(intent: Intent?): String? {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            return intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                text.extractUrl()
            }
        }
        return null
    }

    /** The link shared, or all of them on one line when there are several (the deck lists them). */
    private fun String.extractUrl(): String {
        val links = LinkList.extract(this)
        return if (links.size >= 2) LinkList.oneLine(links) else links.firstOrNull() ?: this
    }
}

@Composable
private fun PermissionGate(onGranted: () -> Unit, onSkipped: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkGray)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.brand_wordmark),
            style = MaterialTheme.typography.displayLarge,
            color = Amber,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.permission_message),
            color = White.copy(alpha = 0.7f),
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Amber)
        ) {
            Text(stringResource(R.string.open_settings), color = OnAccent, fontFamily = FontFamily.Monospace)
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onSkipped) {
            Text(
                stringResource(R.string.skip_downloads_may_fail),
                color = White.copy(alpha = 0.4f),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private data class BottomNavItem(val route: String, @StringRes val labelRes: Int, val icon: ImageVector)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.TAPE_DECK, R.string.nav_deck, Icons.Filled.Videocam),
    BottomNavItem(Routes.BROWSER, R.string.nav_browser, Icons.Filled.Language),
    BottomNavItem(Routes.LIBRARY, R.string.nav_library, Icons.Filled.LibraryMusic),
    BottomNavItem(Routes.AUDIO, R.string.nav_audio, Icons.Filled.MusicNote),
    BottomNavItem(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings)
)

@Composable
fun AnyDropMainScreen(
    onNavControllerReady: (NavController) -> Unit = {},
    startDestination: String = Routes.TAPE_DECK,
    deckSessionRepository: DeckSessionRepository? = null,
    downloadQueue: DownloadQueue? = null
) {
    // Survives tab switches so the browser keeps its page and history.
    val browserState = rememberBrowserState()
    val runningDownloads = downloadQueue?.tasks?.collectAsState()?.value.orEmpty().count { it.isActive }
    val context = LocalContext.current
    // Downloads now save to app-specific external storage (getExternalFilesDir),
    // which needs NO storage permission on any Android version. The old
    // "All files access" gate is therefore no longer required.
    var showPermissionGate by rememberSaveable { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                showPermissionGate = false
            }
        }
    }

    if (showPermissionGate) {
        PermissionGate(
            onGranted = { showPermissionGate = false },
            onSkipped = { showPermissionGate = false }
        )
        return
    }

    val navController = rememberNavController()
    val scrollToTopFlow = remember { MutableSharedFlow<String>(extraBufferCapacity = 1) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(Unit) {
        onNavControllerReady(navController)
    }
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route in bottomNavItems.map { it.route }

    // Remember the active bottom tab across restarts.
    LaunchedEffect(currentDestination?.route) {
        val route = currentDestination?.route
        if (route != null && bottomNavItems.any { it.route == route }) {
            deckSessionRepository?.saveTab(route)
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Black, contentColor = White) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.route
                        } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (selected) {
                                    scrollToTopFlow.tryEmit(item.route)
                                } else {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                // DECK shows how many downloads are running right now.
                                if (item.route == Routes.TAPE_DECK && runningDownloads > 0) {
                                    BadgedBox(badge = { Badge(containerColor = Amber, contentColor = OnAccent) { Text("$runningDownloads") } }) {
                                        Icon(imageVector = item.icon, contentDescription = stringResource(item.labelRes))
                                    }
                                } else {
                                    Icon(imageVector = item.icon, contentDescription = stringResource(item.labelRes))
                                }
                            },
                            label = { Text(text = stringResource(item.labelRes), fontFamily = FontFamily.Monospace, fontSize = 9.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Amber,
                                selectedTextColor = Amber,
                                unselectedIconColor = White.copy(alpha = 0.4f),
                                unselectedTextColor = White.copy(alpha = 0.4f),
                                indicatorColor = MediumGray
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkGray)
                .padding(innerPadding)
        ) {
            AnyDropNavGraph(
                navController = navController,
                modifier = Modifier.fillMaxSize(),
                scrollToTopFlow = scrollToTopFlow,
                startDestination = startDestination,
                browserState = browserState
            )
        }
    }
}
