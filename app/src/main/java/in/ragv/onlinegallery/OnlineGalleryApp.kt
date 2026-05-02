package `in`.ragv.onlinegallery

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Provides a Coil [ImageLoader] tuned for SharePoint thumbnail URLs.
 *
 * SharePoint signs each thumbnail URL with a short-lived `tempauth` JWT
 * and returns a `Cache-Control` header that makes Coil revalidate after
 * a few hours. Once the URL expires the revalidation request returns 401
 * and Coil refuses to serve the cached bytes — so cards go gray until
 * the next API refresh hands us a new signed URL.
 *
 * `respectCacheHeaders(false)` opts out of that behavior: Coil treats
 * the disk entry as fresh forever and only evicts under LRU pressure.
 * Cache keys are stable per-album/per-item (set in the AsyncImage call
 * sites), so the bytes survive URL rotation.
 */
class OnlineGalleryApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .build()
}
