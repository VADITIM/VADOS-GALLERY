package com.vaditim.gallery

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder
import com.vaditim.gallery.diagnostics.CrashLog
import com.vaditim.gallery.media.ThumbnailFetcher
import com.vaditim.gallery.media.ThumbnailKeyer
import com.vaditim.gallery.settings.AlbumArrangement
import com.vaditim.gallery.settings.PreferenceStore
import com.vaditim.gallery.settings.Settings

class GalleryApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        val store = PreferenceStore(this)
        Settings.init(store)
        AlbumArrangement.init(store)
        CrashLog.install(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ThumbnailKeyer())
                add(ThumbnailFetcher.Factory())
                add(VideoFrameDecoder.Factory())
            }
            .build()
}
