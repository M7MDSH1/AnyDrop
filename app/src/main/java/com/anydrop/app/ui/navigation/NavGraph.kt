package com.anydrop.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.anydrop.app.ui.audio.AudioScreen
import com.anydrop.app.ui.library.LibraryScreen
import com.anydrop.app.ui.player.PlayerScreen
import com.anydrop.app.ui.settings.CookieImportScreen
import com.anydrop.app.ui.settings.SettingsScreen
import com.anydrop.app.ui.tape_deck.TapeDeckScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

object Routes {
    const val TAPE_DECK = "tape_deck"
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
    scrollToTopFlow: Flow<String> = emptyFlow()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.TAPE_DECK,
        modifier = modifier
    ) {
        composable(Routes.TAPE_DECK) {
            TapeDeckScreen()
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
                }
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
