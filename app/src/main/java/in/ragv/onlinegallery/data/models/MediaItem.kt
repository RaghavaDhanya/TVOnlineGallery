package `in`.ragv.onlinegallery.data.models

data class MediaItem(
    val id: String,
    val name: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val isVideo: Boolean = false,
    val mimeType: String? = null
)
