package com.vaditim.gallery.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.vaditim.gallery.vas.MicroLabel
import com.vaditim.gallery.vas.Palette

// TODO(vaditim): when the uri is a MediaStore item, open it inside its album so swiping reaches its neighbours, the way the camera thumbnail does on iOS.
@Composable
fun ExternalViewer(uri: Uri, mimeType: String?) {
    Box(Modifier.fillMaxSize().background(Palette.viewerGround), contentAlignment = Alignment.Center) {
        AsyncImage(model = uri, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        if (mimeType?.startsWith("video/") == true) MicroLabel("VIDEO")
    }
}
