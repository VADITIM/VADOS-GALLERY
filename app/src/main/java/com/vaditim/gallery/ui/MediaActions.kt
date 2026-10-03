package com.vaditim.gallery.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
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
import androidx.core.content.FileProvider
import com.vaditim.gallery.media.Album
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.media.MediaRepository
import com.vaditim.gallery.vault.PrivateGroup
import com.vaditim.gallery.vault.PrivateVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

// Every action takes a list: one photo from the viewer, a selection from a grid, or a whole album are the same call. Changes to MediaStore go through its requests — with media management granted Android approves them without a popup, without it the user sees one confirmation for the whole batch.
class MediaActions(
    private val context: Context,
    private val repository: MediaRepository,
    private val vault: PrivateVault,
    private val onPrivateChanged: () -> Unit,
    private val scope: CoroutineScope,
    private val startRequest: (PendingIntent, (Boolean) -> Unit) -> Unit,
) {
    fun share(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val uris = ArrayList(items.map { shareableUri(it) })
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.setType(if (items.all { it.isVideo }) "video/*" else if (items.none { it.isVideo }) "image/*" else "*/*")
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
        if (isPrivate(item)) {
            runVault(if (item.isFavorite) "Removed from private favorites" else "Added to private favorites", "Could not change favorite") {
                vault.setFavorite(item, !item.isFavorite)
                true
            }
        } else {
            startRequest(MediaStore.createFavoriteRequest(context.contentResolver, listOf(item.uri), !item.isFavorite)) { }
        }
    }

    fun trash(items: List<MediaItem>) {
        if (items.isEmpty()) return
        startRequest(MediaStore.createTrashRequest(context.contentResolver, items.map { it.uri }, true)) { isDone ->
            notify(if (isDone) summary(items.size, items.size, "Moved to trash") else "Delete was not allowed")
        }
    }

    fun move(items: List<MediaItem>, album: Album) = move(items, album.relativePath, album.name)

    // Into any folder, existing or not — a new album is just a folder that a first photo is moved into.
    fun move(items: List<MediaItem>, relativePath: String, albumName: String) {
        if (items.isEmpty()) return
        val moveAll = {
            scope.launch {
                val moved = items.count { runCatching { repository.moveTo(it, relativePath) }.getOrDefault(false) }
                notify(summary(moved, items.size, "Moved to $albumName"))
            }
        }
        // With All files access the move needs nobody's permission, so it skips the request — and the popup a request can bring with it.
        if (Environment.isExternalStorageManager()) {
            moveAll()
        } else {
            startRequest(MediaStore.createWriteRequest(context.contentResolver, items.map { it.uri })) { isGranted ->
                if (isGranted) moveAll() else notify("Move was not allowed")
            }
        }
    }

    fun hide(items: List<MediaItem>, groupName: String) =
        runVaultBatch(items, "Moved to Private · $groupName") { vault.hide(it, groupName) }

    fun moveToGroup(items: List<MediaItem>, groupName: String) =
        runVaultBatch(items, "Moved to $groupName") { vault.moveToGroup(it, groupName) }

    // Back out to an album; anything that was a private favourite becomes an ordinary favourite again once MediaStore has indexed it.
    fun unhide(items: List<MediaItem>, album: Album, afterwards: suspend () -> Unit = {}) = unhide(items, album.relativePath, album.name, afterwards)

    fun unhide(items: List<MediaItem>, relativePath: String, albumName: String, afterwards: suspend () -> Unit = {}) {
        if (items.isEmpty()) return
        scope.launch {
            val restored = items.map { item -> item to runCatching { vault.unhide(item, relativePath) }.getOrNull() }
            afterwards()
            onPrivateChanged()
            notify(summary(restored.count { it.second != null }, items.size, "Moved to $albumName"))
            val favoriteUris = restored.filter { (item, uri) -> item.isFavorite && uri != null }.mapNotNull { it.second }
            if (favoriteUris.isNotEmpty()) startRequest(MediaStore.createFavoriteRequest(context.contentResolver, favoriteUris, true)) { }
        }
    }

    fun deleteGroup(group: PrivateGroup) =
        runVault("Deleted ${group.name}", "Could not delete ${group.name}") { vault.deleteGroup(group) }

    fun deletePrivate(items: List<MediaItem>) =
        runVaultBatch(items, "Deleted") { vault.delete(it) }

    private fun runVaultBatch(items: List<MediaItem>, success: String, operation: suspend (MediaItem) -> Boolean) {
        if (items.isEmpty()) return
        scope.launch {
            val done = items.count { runCatching { operation(it) }.getOrDefault(false) }
            onPrivateChanged()
            notify(summary(done, items.size, success))
        }
    }

    private fun runVault(success: String, failure: String, operation: suspend () -> Boolean) {
        scope.launch {
            val isDone = runCatching { operation() }.getOrDefault(false)
            onPrivateChanged()
            notify(if (isDone) success else failure)
        }
    }

    private fun shareableUri(item: MediaItem): Uri =
        if (isPrivate(item)) FileProvider.getUriForFile(context, "${context.packageName}.files", File(item.absolutePath)) else item.uri

    private fun isPrivate(item: MediaItem): Boolean = item.uri.scheme == "file"

    private fun summary(done: Int, total: Int, success: String): String = when {
        done == total -> if (total == 1) success else "$success · $total"
        done == 0 -> "Could not complete that"
        else -> "$success · $done of $total"
    }

    private fun notify(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun rememberMediaActions(repository: MediaRepository, vault: PrivateVault, onPrivateChanged: () -> Unit): MediaActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending = remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        pending.value?.invoke(result.resultCode == Activity.RESULT_OK)
        pending.value = null
    }
    return remember(repository, vault, launcher) {
        MediaActions(context, repository, vault, onPrivateChanged, scope) { pendingIntent, onResult ->
            pending.value = onResult
            launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        }
    }
}
