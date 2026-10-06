package com.vaditim.gallery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.vaditim.gallery.library.GalleryApp
import com.vaditim.gallery.viewer.ExternalViewer

// A FragmentActivity only because BiometricPrompt needs one to unlock Private.
class MainActivity : FragmentActivity() {

    // A photo handed in by another app (the camera's thumbnail, a file manager, a chat) opens on its own, without the library around it.
    private var externalUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        externalUri = readExternalUri(intent)
        setContent {
            val uri = externalUri
            if (uri != null) {
                ExternalViewer(uri = uri, mimeType = intent.type)
            } else {
                GalleryApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalUri = readExternalUri(intent)
    }

    private fun readExternalUri(intent: Intent): Uri? =
        if (intent.action == Intent.ACTION_MAIN) null else intent.data
}
