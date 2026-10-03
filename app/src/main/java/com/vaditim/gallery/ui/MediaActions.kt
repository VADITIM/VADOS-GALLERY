package com.vaditim.gallery.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.vaditim.gallery.media.Album
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.media.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Every change to a photo goes through a MediaStore request. With media management granted Android approves it without a popup; without it, the user sees one system confirmation. Either way the request is the one code path.
class MediaActions(
    private val context: Context,
    private val repository: MediaRepository,
    private val scope: CoroutineScope,
    private val startRequest: (PendingIntent, (Boolean) -> Unit) -> Unit,
) {
    fun share(item: MediaItem) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(item.mimeType)
            .putExtra(Intent.EXTRA_STREAM, item.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null))
    }

    fun edit(item: MediaItem) {
        val edit = Intent(Intent.ACTION_EDIT)
            .setDataAndType(item.uri, item.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(edit, null))
        } catch (exception: ActivityNotFoundException) {
            notify("No editor installed")
        }
    }

    fun toggleFavorite(item: MediaItem) {
        startRequest(MediaStore.createFavoriteRequest(context.contentResolver, listOf(item.uri), !item.isFavorite)) { }
    }

    fun trash(item: MediaItem) {
        startRequest(MediaStore.createTrashRequest(context.contentResolver, listOf(item.uri), true)) { }
    }

    fun move(item: MediaItem, album: Album) {
        startRequest(MediaStore.createWriteRequest(context.contentResolver, listOf(item.uri))) { isGranted ->
            if (isGranted) {
                scope.launch {
                    val isMoved = runCatching { repository.moveTo(item, album.relativePath) }.getOrDefault(false)
                    notify(if (isMoved) "Moved to ${album.name}" else "Could not move to ${album.name}")
                }
            }
        }
    }

    private fun notify(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun rememberMediaActions(repository: MediaRepository): MediaActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending = remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        pending.value?.invoke(result.resultCode == Activity.RESULT_OK)
        pending.value = null
    }
    return remember(repository, launcher) {
        MediaActions(context, repository, scope) { pendingIntent, onResult ->
            pending.value = onResult
            launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        }
    }
}
