package com.vaditim.gallery.media

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

// A grid cell asks for this instead of the photo itself. MediaProvider keeps a thumbnail cache on disk, so this is a small JPEG read instead of decoding a 50MP original for a 100px cell — the difference between a grid that fills as fast as you scroll and one that trails behind it.
data class Thumbnail(val uri: Uri, val sizePixels: Int)

class ThumbnailFetcher(private val thumbnail: Thumbnail, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bitmap = options.context.contentResolver.loadThumbnail(
            thumbnail.uri,
            Size(thumbnail.sizePixels, thumbnail.sizePixels),
            null,
        )
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<Thumbnail> {
        override fun create(data: Thumbnail, options: Options, imageLoader: ImageLoader): Fetcher = ThumbnailFetcher(data, options)
    }
}

class ThumbnailKeyer : Keyer<Thumbnail> {
    override fun key(data: Thumbnail, options: Options): String = "thumbnail:${data.uri}:${data.sizePixels}"
}
