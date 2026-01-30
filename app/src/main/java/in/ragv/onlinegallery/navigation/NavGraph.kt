package `in`.ragv.onlinegallery.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import `in`.ragv.onlinegallery.ui.screens.AlbumListScreen
import `in`.ragv.onlinegallery.ui.screens.MediaGridScreen
import `in`.ragv.onlinegallery.ui.screens.MediaViewerScreen
import `in`.ragv.onlinegallery.ui.viewmodel.AlbumViewModel
import `in`.ragv.onlinegallery.ui.viewmodel.MediaViewModel

sealed class Screen(val route: String) {
    object AlbumList : Screen("album_list")
    object MediaGrid : Screen("media_grid/{albumId}/{albumName}") {
        fun createRoute(albumId: String, albumName: String) =
            "media_grid/$albumId/$albumName"
    }
    object MediaViewer : Screen("media_viewer/{albumId}/{albumName}/{mediaIndex}") {
        fun createRoute(albumId: String, albumName: String, mediaIndex: Int) =
            "media_viewer/$albumId/$albumName/$mediaIndex"
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    albumViewModel: AlbumViewModel,
    mediaViewModel: MediaViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.AlbumList.route
    ) {
        composable(Screen.AlbumList.route) {
            AlbumListScreen(
                viewModel = albumViewModel,
                onAlbumClick = { album ->
                    navController.navigate(
                        Screen.MediaGrid.createRoute(album.id, album.name)
                    )
                }
            )
        }

        composable(
            route = Screen.MediaGrid.route,
            arguments = listOf(
                navArgument("albumId") { type = NavType.StringType },
                navArgument("albumName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString("albumId") ?: ""
            val albumName = backStackEntry.arguments?.getString("albumName") ?: ""

            MediaGridScreen(
                viewModel = mediaViewModel,
                albumId = albumId,
                albumName = albumName,
                onMediaClick = { _, index ->
                    navController.navigate(
                        Screen.MediaViewer.createRoute(albumId, albumName, index)
                    )
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.MediaViewer.route,
            arguments = listOf(
                navArgument("albumId") { type = NavType.StringType },
                navArgument("albumName") { type = NavType.StringType },
                navArgument("mediaIndex") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString("albumId") ?: ""
            val albumName = backStackEntry.arguments?.getString("albumName") ?: ""
            val mediaIndex = backStackEntry.arguments?.getInt("mediaIndex") ?: 0

            val mediaItems = mediaViewModel.uiState.value.mediaItems

            if (mediaItems.isNotEmpty()) {
                MediaViewerScreen(
                    mediaItems = mediaItems,
                    initialIndex = mediaIndex,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
