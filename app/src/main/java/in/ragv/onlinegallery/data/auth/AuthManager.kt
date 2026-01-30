package `in`.ragv.onlinegallery.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Manages Microsoft OAuth authentication using Device Code Flow
 * This is ideal for TV/limited input devices
 */
class AuthManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val TAG = "AuthManager"
        private const val PREFS_NAME = "OnlineGalleryAuth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"

        // Microsoft consumer (personal accounts) endpoint
        // Use /consumers for personal Microsoft accounts (Outlook, Hotmail, Live, etc.)
        // Use /common for both work/school and personal accounts
        // Use /organizations for work/school accounts only
        private const val AUTHORITY = "https://login.microsoftonline.com/consumers"
        private const val DEVICE_CODE_ENDPOINT = "$AUTHORITY/oauth2/v2.0/devicecode"
        private const val TOKEN_ENDPOINT = "$AUTHORITY/oauth2/v2.0/token"

        // Required scopes for OneDrive access
        private const val SCOPES = "Files.Read Files.Read.All offline_access"

        /**
         * IMPORTANT: Replace this with your actual Azure AD Application Client ID
         * To get a Client ID:
         * 1. Go to https://portal.azure.com/
         * 2. Navigate to Azure Active Directory > App registrations
         * 3. Create a new registration (or use existing)
         * 4. Select "Mobile and desktop applications" platform
         * 5. Enable "Allow public client flows" in Authentication settings
         * 6. Copy the Application (client) ID
         */
        const val CLIENT_ID = "your-client-id-here"
    }

    /**
     * Check if user is authenticated with a valid token
     */
    fun isAuthenticated(): Boolean {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)

        val isValid = !token.isNullOrEmpty() && System.currentTimeMillis() < expiry

        // Debug logging
        if (!token.isNullOrEmpty()) {
            val dotCount = token.count { it == '.' }
            Log.d(TAG, "Token validation - length: ${token.length}, dots: $dotCount, expired: ${System.currentTimeMillis() >= expiry}")
        }

        return isValid
    }

    /**
     * Get the current access token
     */
    fun getAccessToken(): String? {
        return if (isAuthenticated()) {
            prefs.getString(KEY_ACCESS_TOKEN, null)
        } else {
            null
        }
    }

    /**
     * Start device code flow authentication
     * Returns device code info that should be shown to the user
     */
    suspend fun startDeviceCodeFlow(): Result<DeviceCodeInfo> = withContext(Dispatchers.IO) {
        Log.d(TAG, "startDeviceCodeFlow() called with CLIENT_ID: $CLIENT_ID")

        if (CLIENT_ID == "YOUR_CLIENT_ID_HERE") {
            Log.e(TAG, "CLIENT_ID not configured!")
            return@withContext Result.failure(
                IllegalStateException("Please configure CLIENT_ID in AuthManager.kt")
            )
        }

        try {
            val requestBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("scope", SCOPES)
                .build()

            val request = Request.Builder()
                .url(DEVICE_CODE_ENDPOINT)
                .post(requestBody)
                .build()

            Log.d(TAG, "Sending device code request to $DEVICE_CODE_ENDPOINT")
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                Log.d(TAG, "Device code response: code=${response.code}, body length=${body?.length ?: 0}")

                if (!response.isSuccessful || body == null) {
                    Log.e(TAG, "Failed to get device code: HTTP ${response.code}, body=$body")
                    return@withContext Result.failure(
                        IOException("Failed to get device code: HTTP ${response.code}")
                    )
                }

                val deviceCode = gson.fromJson(body, DeviceCodeResponse::class.java)
                Log.d(TAG, "Device code received: ${deviceCode.userCode}")
                Result.success(
                    DeviceCodeInfo(
                        userCode = deviceCode.userCode,
                        verificationUrl = deviceCode.verificationUrl,
                        message = deviceCode.message,
                        deviceCode = deviceCode.deviceCode,
                        expiresIn = deviceCode.expiresIn,
                        interval = deviceCode.interval
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in startDeviceCodeFlow", e)
            Result.failure(e)
        }
    }

    /**
     * Poll for authentication completion
     * Call this after showing the user the device code
     */
    suspend fun pollForToken(deviceCode: String, interval: Int = 5): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                // Poll for up to 15 minutes
                val maxAttempts = 180 / interval
                var attempts = 0

                while (attempts < maxAttempts) {
                    delay(interval * 1000L)
                    attempts++

                    val requestBody = FormBody.Builder()
                        .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                        .add("client_id", CLIENT_ID)
                        .add("device_code", deviceCode)
                        .build()

                    val request = Request.Builder()
                        .url(TOKEN_ENDPOINT)
                        .post(requestBody)
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val body = response.body?.string()

                    if (body == null) {
                        response.close()
                        continue
                    }

                    if (response.isSuccessful) {
                        val tokenResponse = gson.fromJson(body, TokenResponseData::class.java)
                        saveToken(tokenResponse)
                        response.close()
                        return@withContext Result.success(true)
                    } else {
                        // Check if we should continue polling
                        val error = try {
                            gson.fromJson(body, TokenErrorResponse::class.java)
                        } catch (e: Exception) {
                            null
                        }

                        response.close()

                        when (error?.error) {
                            "authorization_pending" -> {
                                // Keep polling
                            }
                            "slow_down" -> delay(interval * 1000L) // Wait extra time
                            "expired_token" -> return@withContext Result.failure(
                                IOException("Device code expired")
                            )
                            "access_denied" -> return@withContext Result.failure(
                                IOException("User denied access")
                            )
                            else -> return@withContext Result.failure(
                                IOException("Authentication failed: ${error?.errorDescription ?: "Unknown error"}")
                            )
                        }
                    }
                }

                Result.failure(IOException("Authentication timed out"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Refresh the access token using refresh token
     */
    suspend fun refreshToken(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
                ?: return@withContext Result.failure(IOException("No refresh token available"))

            val requestBody = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", CLIENT_ID)
                .add("refresh_token", refreshToken)
                .add("scope", SCOPES)
                .build()

            val request = Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()

                if (!response.isSuccessful || body == null) {
                    return@withContext Result.failure(
                        IOException("Token refresh failed: HTTP ${response.code}")
                    )
                }

                val tokenResponse = gson.fromJson(body, TokenResponseData::class.java)
                saveToken(tokenResponse)
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sign out and clear stored tokens
     */
    fun signOut() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRY)
            .apply()
    }

    private fun saveToken(tokenResponse: TokenResponseData) {
        val expiryTime = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000L)
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, tokenResponse.accessToken)
            .putString(KEY_REFRESH_TOKEN, tokenResponse.refreshToken)
            .putLong(KEY_TOKEN_EXPIRY, expiryTime)
            .apply()
    }

    // Data models for OAuth responses
    private data class DeviceCodeResponse(
        @SerializedName("device_code")
        val deviceCode: String,
        @SerializedName("user_code")
        val userCode: String,
        @SerializedName("verification_uri")
        val verificationUrl: String,
        @SerializedName("expires_in")
        val expiresIn: Int,
        @SerializedName("interval")
        val interval: Int,
        @SerializedName("message")
        val message: String
    )

    data class DeviceCodeInfo(
        val userCode: String,
        val verificationUrl: String,
        val message: String,
        val deviceCode: String,
        val expiresIn: Int,
        val interval: Int
    )

    private data class TokenResponseData(
        @SerializedName("access_token")
        val accessToken: String,
        @SerializedName("token_type")
        val tokenType: String,
        @SerializedName("expires_in")
        val expiresIn: Int,
        @SerializedName("refresh_token")
        val refreshToken: String?,
        @SerializedName("scope")
        val scope: String
    )

    private data class TokenErrorResponse(
        @SerializedName("error")
        val error: String,
        @SerializedName("error_description")
        val errorDescription: String?
    )
}
