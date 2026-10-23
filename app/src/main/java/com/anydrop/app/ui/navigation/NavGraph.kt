package com.anydrop.app.ui.navigation

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.anydrop.app.sharedUrlFlow
import com.anydrop.app.ui.audio.AudioScreen
import com.anydrop.app.ui.browser.BrowserScreen
import com.anydrop.app.ui.browser.BrowserState
import com.anydrop.app.ui.library.LibraryScreen
import com.anydrop.app.ui.player.PlayerScreen
import com.anydrop.app.ui.settings.CookieImportScreen
import com.anydrop.app.ui.settings.SettingsScreen
import com.anydrop.app.ui.tape_deck.TapeDeckScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

object Routes {
    const val TAPE_DECK = "tape_deck"
    const val BROWSER = "browser"
    const val LIBRARY = "library"
    const val AUDIO = "audio"
    const val PLAYER = "player/{mediaId}"
    const val SETTINGS = "settings"
    const val COOKIE_IMPORT = "cookie_import"
}

@Composable
fun AnyDropNavGraph(
    navController: NavHostController,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    scrollToTopFlow: Flow<String> = emptyFlow(),
    startDestination: String = Routes.TAPE_DECK,
    browserState: BrowserState? = null
) {
    // The player's rotate button locks the screen's orientation; leaving the player
    // (Back included) hands rotation back to the phone.
    val activity = LocalContext.current as? Activity
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    LaunchedEffect(route) {
        if (route != null && route != Routes.PLAYER) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Routes.TAPE_DECK) {
            TapeDeckScreen()
        }

        composable(Routes.BROWSER) {
            if (browserState != null) {
                BrowserScreen(
                    state = browserState,
                    // "Analyze this page": hand the URL to the deck the same way a share does.
                    onAnalyze = { url ->
                        sharedUrlFlow.value = url
                        navController.navigate(Routes.TAPE_DECK) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }

        composable(Routes.LIBRARY) {
            LibraryScreen(
                onPlayMedia = { mediaItem ->
                    navController.navigate("player/${mediaItem.id}")
                },
                scrollToTopFlow = scrollToTopFlow
            )
        }

        composable(Routes.AUDIO) {
            AudioScreen(
                onPlayMedia = { mediaItem ->
                    navController.navigate("player/${mediaItem.id}")
                },
                scrollToTopFlow = scrollToTopFlow
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(navArgument("mediaId") { type = NavType.StringType })
        ) {
            PlayerScreen(
                onNavigateToMedia = { targetId ->
                    navController.navigate("player/$targetId") {
                        popUpTo("player/{mediaId}") { inclusive = true }
                    }
                },
                onExit = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onCookieImport = { navController.navigate(Routes.COOKIE_IMPORT) }
            )
        }

        composable(Routes.COOKIE_IMPORT) {
            CookieImportScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
