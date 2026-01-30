package `in`.ragv.onlinegallery.data.api

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client for Microsoft Graph API calls
 */
class GraphApiClient(private val accessToken: String) {

    private val gson = Gson()
    private val baseUrl = "https://graph.microsoft.com/v1.0"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Get children of a folder by path
     * @param path Path relative to root (e.g., "Gallery" or "Gallery/Vacation2024")
     * @return List of drive items
     */
    suspend fun getFolderChildren(path: String = ""): Result<DriveItemListResponse> =
        withContext(Dispatchers.IO) {
            try {
                val encodedPath = path.trim('/').ifEmpty { "" }
                val url = if (encodedPath.isEmpty()) {
                    "$baseUrl/me/drive/root/children?select=id,name,size,folder,file,image,photo,video,@microsoft.graph.downloadUrl,webUrl,createdDateTime,lastModifiedDateTime&expand=thumbnails"
                } else {
                    "$baseUrl/me/drive/root:/$encodedPath:/children?select=id,name,size,folder,file,image,photo,video,@microsoft.graph.downloadUrl,webUrl,createdDateTime,lastModifiedDateTime&expand=thumbnails"
                }

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string()

                    if (!response.isSuccessful) {
                        val errorMessage = if (body != null) {
                            try {
                                val error = gson.fromJson(body, GraphErrorResponse::class.java)
                                error.error.message
                            } catch (e: Exception) {
                                body
                            }
                        } else {
                            "HTTP ${response.code}"
                        }
                        return@withContext Result.failure(
                            IOException("Graph API error: $errorMessage")
                        )
                    }

                    if (body == null) {
                        return@withContext Result.failure(IOException("Empty response body"))
                    }

                    val result = gson.fromJson(body, DriveItemListResponse::class.java)
                    Result.success(result)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Get children of a folder by item ID
     * @param itemId The ID of the folder
     * @return List of drive items
     */
    suspend fun getFolderChildrenById(itemId: String): Result<DriveItemListResponse> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/me/drive/items/$itemId/children?select=id,name,size,folder,file,image,photo,video,@microsoft.graph.downloadUrl,webUrl,createdDateTime,lastModifiedDateTime&expand=thumbnails"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string()

                    if (!response.isSuccessful) {
                        val errorMessage = if (body != null) {
                            try {
                                val error = gson.fromJson(body, GraphErrorResponse::class.java)
                                error.error.message
                            } catch (e: Exception) {
                                body
                            }
                        } else {
                            "HTTP ${response.code}"
                        }
                        return@withContext Result.failure(
                            IOException("Graph API error: $errorMessage")
                        )
                    }

                    if (body == null) {
                        return@withContext Result.failure(IOException("Empty response body"))
                    }

                    val result = gson.fromJson(body, DriveItemListResponse::class.java)
                    Result.success(result)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Get a specific item with download URL
     * @param itemId The ID of the item
     * @return Drive item with download URL
     */
    suspend fun getItem(itemId: String): Result<DriveItemResponse> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$baseUrl/me/drive/items/$itemId?select=id,name,size,folder,file,image,photo,video,@microsoft.graph.downloadUrl,webUrl,createdDateTime,lastModifiedDateTime&expand=thumbnails"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string()

                    if (!response.isSuccessful) {
                        val errorMessage = if (body != null) {
                            try {
                                val error = gson.fromJson(body, GraphErrorResponse::class.java)
                                error.error.message
                            } catch (e: Exception) {
                                body
                            }
                        } else {
                            "HTTP ${response.code}"
                        }
                        return@withContext Result.failure(
                            IOException("Graph API error: $errorMessage")
                        )
                    }

                    if (body == null) {
                        return@withContext Result.failure(IOException("Empty response body"))
                    }

                    val result = gson.fromJson(body, DriveItemResponse::class.java)
                    Result.success(result)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Test the access token by getting user's drive info
     */
    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/me/drive")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
