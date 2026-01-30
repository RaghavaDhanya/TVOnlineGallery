package `in`.ragv.onlinegallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import `in`.ragv.onlinegallery.navigation.NavGraph
import `in`.ragv.onlinegallery.ui.theme.OnlineGalleryTheme
import `in`.ragv.onlinegallery.ui.viewmodel.AlbumViewModel
import `in`.ragv.onlinegallery.ui.viewmodel.MediaViewModel

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnlineGalleryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    val navController = rememberNavController()
                    val albumViewModel: AlbumViewModel = viewModel()
                    val mediaViewModel: MediaViewModel = viewModel()

                    NavGraph(
                        navController = navController,
                        albumViewModel = albumViewModel,
                        mediaViewModel = mediaViewModel
                    )
                }
            }
        }
    }
}