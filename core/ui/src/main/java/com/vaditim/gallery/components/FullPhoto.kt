package com.vaditim.gallery.components

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.size.Size

// A photo at full quality: drawn screen-sized with smooth filtering, and once it is zoomed the full-resolution decode lays itself over it, so the pixels hold up close.
@Composable
// `placeholderKey`: the tile it was opened from, drawn until the photo itself is decoded.
fun FullPhoto(uri: Uri, contentDescription: String?, isZoomed: Boolean, modifier: Modifier = Modifier, placeholderKey: MemoryCache.Key? = null, onRatio: (Float) -> Unit = {}) {
    val context = LocalContext.current
    val screenRequest = remember(uri) { ImageRequest.Builder(context).data(uri).placeholderMemoryCacheKey(placeholderKey).build() }
    // Once asked for, the sharp copy stays, so zooming back out and in again does not flicker.
    var wantsOriginal by remember(uri) { mutableStateOf(false) }
    if (isZoomed) wantsOriginal = true
    Box(modifier) {
        AsyncImage(
            model = screenRequest,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
            onSuccess = { success -> onRatio(success.result.image.width.toFloat() / success.result.image.height) },
            modifier = Modifier.fillMaxSize(),
        )
        if (wantsOriginal) {
            val originalRequest = remember(uri) { ImageRequest.Builder(context).data(uri).size(Size.ORIGINAL).build() }
            AsyncImage(model = originalRequest, contentDescription = null, contentScale = ContentScale.Fit, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
        }
    }
}

