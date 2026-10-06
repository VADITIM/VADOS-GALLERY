package com.vaditim.gallery.viewer

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vaditim.gallery.components.FullPhoto
import com.vaditim.gallery.components.HighRangeWindow
import com.vaditim.gallery.vas.MicroLabel
import com.vaditim.gallery.vas.Palette

// TODO(vaditim): when the uri is a MediaStore item, open it inside its album so swiping reaches its neighbours, the way the camera thumbnail does on iOS.
@Composable
fun ExternalViewer(uri: Uri, mimeType: String?) {
    HighRangeWindow()
    Box(Modifier.fillMaxSize().background(Palette.viewerGround), contentAlignment = Alignment.Center) {
        FullPhoto(uri, contentDescription = null, isZoomed = false, modifier = Modifier.fillMaxSize())
        if (mimeType?.startsWith("video/") == true) MicroLabel("VIDEO")
    }
}
