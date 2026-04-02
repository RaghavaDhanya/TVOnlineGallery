# Setup & Development Guide

## Quick Setup

### 1. Register App in Azure Portal

1. Go to [Azure Portal](https://portal.azure.com/)
2. Navigate to **Azure Active Directory** > **App registrations**
3. Click **+ New registration**
4. Configure:
   - **Name**: `Online Gallery`
   - **Supported account types**:
     - For personal OneDrive: "Microsoft personal accounts only"
     - For both: "Any organizational directory and personal accounts"
   - **Redirect URI**: Leave empty (not needed for device code flow)
5. Click **Register**
6. Copy the **Application (client) ID**

### 2. Enable Public Client Flow

1. In your app registration, go to **Authentication**
2. Scroll to **Advanced settings**
3. Toggle **Allow public client flows** to **Yes**
4. Click **Save**

### 3. Configure API Permissions

1. Go to **API permissions**
2. Click **+ Add a permission** > **Microsoft Graph** > **Delegated permissions**
3. Add these permissions:
   - `Files.Read`
   - `Files.Read.All`
   - `offline_access`
4. Click **Add permissions**
5. (Optional) Click **Grant admin consent** if available

### 4. Configure Client ID

1. Copy `local.properties.example` to `local.properties`:
   ```bash
   cp local.properties.example local.properties
   ```
2. Open `local.properties` and replace `your-client-id-here` with your Application (client) ID:
   ```properties
   CLIENT_ID=your-actual-client-id-from-azure
   ```

3. **IMPORTANT**: Match the endpoint to your Azure configuration:
   - If Azure app supports **personal accounts only**: Keep `/consumers` (already set)
   - If Azure app supports **both personal and work accounts**: Change to `/common`

   Update `AUTHORITY` around line 39:
   ```kotlin
   // For personal accounts only:
   private const val AUTHORITY = "https://login.microsoftonline.com/consumers"

   // For both personal and work accounts:
   private const val AUTHORITY = "https://login.microsoftonline.com/common"
   ```

4. (Optional) Configure OneDrive folder path in `data/repository/OneDriveRepository.kt` (line 32):
   ```kotlin
   const val ROOT_FOLDER_PATH = "Gallery"  // Change to your folder path
   ```
   - `"Gallery"` - looks in /Gallery folder
   - `"Pictures/Gallery"` - nested path
   - `""` - uses OneDrive root

### 5. Build and Run

```bash
# Build the app
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug
```

Or in Android Studio: Click **Run** button

## Setting Up Android TV Emulator

1. In Android Studio: **Tools** > **Device Manager**
2. Click **Create Device**
3. Select **TV** category
4. Choose **Television (1080p)** or any TV profile
5. Select a system image (API 21+)
6. Launch the emulator and run the app

## Using the App

### Authentication (First Time)

1. Click **"Sign In with Microsoft"**
2. The TV displays:
   - A URL (e.g., `https://microsoft.com/devicelogin`)
   - A code (e.g., `ABC-DEF-GHI`)
3. On your phone or computer:
   - Visit the URL
   - Enter the code
   - Sign in with your Microsoft account
   - Grant permissions
4. Return to TV - authentication completes automatically
5. Your albums load from OneDrive!

### OneDrive Folder Structure

The app treats folders as albums:

```
OneDrive/
└── Gallery/                    (your ROOT_FOLDER_PATH)
    ├── Vacation 2024/          (Album 1)
    │   ├── photo1.jpg
    │   ├── photo2.png
    │   └── video1.mp4
    ├── Birthday Party/         (Album 2)
    │   ├── cake.jpg
    │   └── celebration.mp4
    └── Holiday 2023/           (Album 3)
        └── family.jpg
```

## Architecture

- **MVVM Pattern**: ViewModels manage UI state and business logic
- **Repository Pattern**: OneDriveRepository handles data operations
- **Navigation**: Jetpack Navigation Compose for screen transitions
- **Image Loading**: Coil for efficient image loading and caching
- **Video Playback**: VLC (libvlc-android) for comprehensive codec support
- **Authentication**: OAuth 2.0 Device Code Flow via Microsoft Graph API

## Project Structure

```
app/src/main/java/in/ragv/onlinegallery/
├── data/
│   ├── api/
│   │   ├── GraphApiModels.kt      # Microsoft Graph API DTOs
│   │   └── GraphApiClient.kt      # HTTP client for Graph API
│   ├── auth/
│   │   └── AuthManager.kt         # Device Code Flow implementation
│   ├── cache/
│   │   ├── CacheManager.kt        # Album/media caching
│   │   └── VideoPositionManager.kt # Remember video pause positions
│   ├── models/
│   │   ├── Album.kt               # Album data model
│   │   └── MediaItem.kt           # Media item data model
│   └── repository/
│       └── OneDriveRepository.kt  # OneDrive API integration
├── ui/
│   ├── screens/
│   │   ├── AlbumListScreen.kt     # Album browsing + sign-in
│   │   ├── MediaGridScreen.kt     # Media grid view
│   │   └── MediaViewerScreen.kt   # Full-screen viewer
│   ├── viewmodel/
│   │   ├── AlbumViewModel.kt      # Album list + auth logic
│   │   └── MediaViewModel.kt      # Media items logic
│   └── theme/                     # App theming
├── navigation/
│   └── NavGraph.kt                # Navigation setup
└── MainActivity.kt                # App entry point
```

## Navigation Flow

```
AlbumListScreen (Browse albums + Sign in)
    ↓
MediaGridScreen (View photos/videos in album)
    ↓
MediaViewerScreen (Full-screen view with navigation)
```

## Configuration Options

### Change Root Folder Path

Edit `OneDriveRepository.kt` (line 32):
```kotlin
const val ROOT_FOLDER_PATH = "Pictures/MyGallery"
```

### Modify OAuth Scopes

Edit `AuthManager.kt` (line 27):
```kotlin
private const val SCOPES = "Files.Read Files.Read.All offline_access"
```

### Adjust HTTP Timeouts

Edit `GraphApiClient.kt` (lines 28-30):
```kotlin
.connectTimeout(30, TimeUnit.SECONDS)
.readTimeout(30, TimeUnit.SECONDS)
```

## Key Dependencies

```gradle
// Jetpack Compose for TV
implementation(libs.androidx.tv.foundation)
implementation(libs.androidx.tv.material)

// Navigation
implementation("androidx.navigation:navigation-compose:2.7.6")

// Image loading
implementation("io.coil-kt:coil-compose:2.5.0")
implementation("io.coil-kt:coil-video:2.5.0")

// Video playback
implementation("org.videolan.android:libvlc-all:3.6.5")

// HTTP client
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// JSON parsing
implementation("com.google.code.gson:gson:2.10.1")
```

## Troubleshooting

### "Nothing happens when clicking Sign In"

Check Android Studio Logcat:
```bash
adb logcat -s AlbumViewModel AuthManager OneDriveRepository
```

Common causes:
- CLIENT_ID not configured
- No internet connection on emulator
- Firewall blocking OAuth endpoints

### HTTP 400: "Application is configured for use by Microsoft Account users only"

**Solution**: Your Azure app is configured for personal accounts only, but the code uses `/common` endpoint.

In `AuthManager.kt`, change:
```kotlin
private const val AUTHORITY = "https://login.microsoftonline.com/consumers"
```

### Albums not loading

1. Check that folders exist in your OneDrive at the configured path
2. Verify `ROOT_FOLDER_PATH` matches your OneDrive structure
3. Check logs: `adb logcat -s OneDriveRepository GraphApiClient`
4. Test manually with [Graph Explorer](https://developer.microsoft.com/en-us/graph/graph-explorer)

### Videos not playing

- VLC supports a wide range of codecs (MP4, MKV, AVI, WebM, MOV, etc.)
- Download URLs expire after ~1 hour - reload the album if stale
- Check network connection for streaming
- Check logs: `adb logcat -s VLCVideoPlayer`

### Authentication timeout

Device codes expire after 15 minutes. If timeout occurs:
1. Return to sign-in screen
2. Click **"Sign In with Microsoft"** again
3. Get a new code and complete within 15 minutes

## Development Commands

```bash
# Clean build
./gradlew clean assembleDebug

# View logs
adb logcat -s AlbumViewModel AuthManager OneDriveRepository GraphApiClient

# Check network connectivity
adb shell ping -c 3 8.8.8.8

# Test Graph API manually
curl -H "Authorization: Bearer YOUR_TOKEN" \
  "https://graph.microsoft.com/v1.0/me/drive/root:/Gallery:/children"
```

## Resources

- [Microsoft Graph API Documentation](https://learn.microsoft.com/en-us/graph/api/overview)
- [Device Code Flow Guide](https://learn.microsoft.com/en-us/azure/active-directory/develop/v2-oauth2-device-code)
- [Graph Explorer](https://developer.microsoft.com/en-us/graph/graph-explorer) - Test API calls
- [Azure Portal](https://portal.azure.com/) - Manage app registrations
