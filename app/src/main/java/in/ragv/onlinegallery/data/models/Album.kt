package `in`.ragv.onlinegallery.data.models

data class Album(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val itemCount: Int = 0
)
