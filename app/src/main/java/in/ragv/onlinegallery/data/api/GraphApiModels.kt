package `in`.ragv.onlinegallery.data.api

import com.google.gson.annotations.SerializedName

/**
 * Microsoft Graph API response models
 */

data class DriveItemListResponse(
    @SerializedName("value")
    val items: List<DriveItemResponse>,
    @SerializedName("@odata.nextLink")
    val nextLink: String? = null
)

data class DriveItemResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("size")
    val size: Long? = null,
    @SerializedName("webUrl")
    val webUrl: String? = null,
    @SerializedName("createdDateTime")
    val createdDateTime: String? = null,
    @SerializedName("lastModifiedDateTime")
    val lastModifiedDateTime: String? = null,
    @SerializedName("folder")
    val folder: FolderFacet? = null,
    @SerializedName("file")
    val file: FileFacet? = null,
    @SerializedName("image")
    val image: ImageFacet? = null,
    @SerializedName("photo")
    val photo: PhotoFacet? = null,
    @SerializedName("video")
    val video: VideoFacet? = null,
    @SerializedName("@microsoft.graph.downloadUrl")
    val downloadUrl: String? = null,
    @SerializedName("thumbnails")
    val thumbnails: List<ThumbnailSet>? = null
)

data class FolderFacet(
    @SerializedName("childCount")
    val childCount: Int = 0
)

data class FileFacet(
    @SerializedName("mimeType")
    val mimeType: String? = null,
    @SerializedName("hashes")
    val hashes: FileHashes? = null
)

data class FileHashes(
    @SerializedName("sha1Hash")
    val sha1Hash: String? = null,
    @SerializedName("quickXorHash")
    val quickXorHash: String? = null
)

data class ImageFacet(
    @SerializedName("width")
    val width: Int? = null,
    @SerializedName("height")
    val height: Int? = null
)

data class PhotoFacet(
    @SerializedName("takenDateTime")
    val takenDateTime: String? = null,
    @SerializedName("cameraMake")
    val cameraMake: String? = null,
    @SerializedName("cameraModel")
    val cameraModel: String? = null
)

data class VideoFacet(
    @SerializedName("duration")
    val duration: Long? = null,
    @SerializedName("width")
    val width: Int? = null,
    @SerializedName("height")
    val height: Int? = null
)

data class ThumbnailSet(
    @SerializedName("id")
    val id: String,
    @SerializedName("small")
    val small: Thumbnail? = null,
    @SerializedName("medium")
    val medium: Thumbnail? = null,
    @SerializedName("large")
    val large: Thumbnail? = null
)

data class Thumbnail(
    @SerializedName("url")
    val url: String,
    @SerializedName("width")
    val width: Int,
    @SerializedName("height")
    val height: Int
)

/**
 * OAuth token response
 */
data class TokenResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("token_type")
    val tokenType: String,
    @SerializedName("expires_in")
    val expiresIn: Int,
    @SerializedName("refresh_token")
    val refreshToken: String? = null,
    @SerializedName("scope")
    val scope: String? = null
)

/**
 * Error response from Graph API
 */
data class GraphErrorResponse(
    @SerializedName("error")
    val error: GraphError
)

data class GraphError(
    @SerializedName("code")
    val code: String,
    @SerializedName("message")
    val message: String
)
