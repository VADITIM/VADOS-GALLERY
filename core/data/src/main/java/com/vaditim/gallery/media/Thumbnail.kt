package com.vaditim.gallery.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

// A grid cell asks for this instead of the photo itself. MediaProvider keeps a thumbnail cache on disk, so this is a small JPEG read instead of decoding a 50MP original for a 100px cell — the difference between a grid that fills as fast as you scroll and one that trails behind it.
// `storedDegrees` is the turn MediaStore records for a photo (null for a video), and `width` and `height` its upright size as MediaStore has it; they tell a thumbnail turned the wrong way.
data class Thumbnail(val uri: Uri, val sizePixels: Int, val storedDegrees: Int? = null, val width: Int = 0, val height: Int = 0) {
    companion object {
        fun of(item: MediaItem, sizePixels: Int) = Thumbnail(item.uri, sizePixels, if (item.isVideo) null else item.orientation, item.width, item.height)
    }
}

// Whether each photo's cached thumbnail stands the right way up, worked out once a session, so a photo is checked only the first time it scrolls by.
private val isUprightByUri = ConcurrentHashMap<Uri, Boolean>()

// A photo off by less than this share is near enough square that its thumbnail's shape tells nothing.
private const val SQUARE_SLACK = 1.15f

class ThumbnailFetcher(private val thumbnail: Thumbnail, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val resolver = options.context.contentResolver
        val bitmap = if (isUprightByUri[thumbnail.uri] == false) {
            decodeSmall(resolver)
        } else {
            val cached = resolver.loadThumbnail(thumbnail.uri, Size(thumbnail.sizePixels, thumbnail.sizePixels), null)
            val isUpright = isUpright(resolver, cached)
            isUprightByUri[thumbnail.uri] = isUpright
            if (isUpright) cached else decodeSmall(resolver)
        }
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    // MediaProvider turns its cached thumbnail by the orientation in its own database, which for some photos no longer matches the file (turned by another app, restored, synced back), and the cache itself can be older than the file. The photo is decoded by its file's own EXIF, so a thumbnail of another shape, or one whose database turn disagrees with the file, would lie turned in the grid.
    private fun isUpright(resolver: ContentResolver, cached: Bitmap): Boolean {
        val storedDegrees = thumbnail.storedDegrees ?: return true
        if (isUprightByUri[thumbnail.uri] == true) return true
        if (thumbnail.width > 0 && thumbnail.height > 0) {
            val expected = thumbnail.width.toFloat() / thumbnail.height
            val actual = cached.width.toFloat() / cached.height.coerceAtLeast(1)
            val isOtherShape = (expected > SQUARE_SLACK && actual < 1f / SQUARE_SLACK) || (expected < 1f / SQUARE_SLACK && actual > SQUARE_SLACK)
            if (isOtherShape) return false
        }
        // An unreadable file is given the benefit of the doubt.
        val fileDegrees = fileDegrees(resolver) ?: return true
        return fileDegrees == storedDegrees
    }

    private fun fileDegrees(resolver: ContentResolver): Int? = runCatching {
        resolver.openFileDescriptor(thumbnail.uri, "r")?.use { descriptor ->
            val exif = ExifInterface(descriptor.fileDescriptor)
            if (exif.getAttribute(ExifInterface.TAG_ORIENTATION) == null) return@use null
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
                ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
                ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
                else -> 0
            }
        }
    }.getOrNull()

    // The photo itself, decoded sampled down to fill the cell and upright by its own EXIF, for the few whose cached thumbnail cannot be trusted.
    private fun decodeSmall(resolver: ContentResolver): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, thumbnail.uri)) { decoder, info, _ ->
            val scale = min(1f, thumbnail.sizePixels.toFloat() / min(info.size.width, info.size.height).coerceAtLeast(1))
            decoder.setTargetSize(max(1, (info.size.width * scale).toInt()), max(1, (info.size.height * scale).toInt()))
        }

    class Factory : Fetcher.Factory<Thumbnail> {
        override fun create(data: Thumbnail, options: Options, imageLoader: ImageLoader): Fetcher = ThumbnailFetcher(data, options)
    }
}

class ThumbnailKeyer : Keyer<Thumbnail> {
    override fun key(data: Thumbnail, options: Options): String = "thumbnail:${data.uri}:${data.sizePixels}"
}
