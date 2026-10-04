package com.vaditim.gallery.ui

import com.vaditim.gallery.media.MediaEditor
import kotlinx.coroutines.Job
import android.media.MediaScannerConnection
import android.graphics.RectF
import android.content.ContentValues
import com.vaditim.gallery.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.vaditim.gallery.media.Album
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.media.MediaRepository
import com.vaditim.gallery.media.SamsungTrash
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
    private val samsungTrash: SamsungTrash,
    private val onTrashChanged: () -> Unit,
    private val scope: CoroutineScope,
    private val startRequest: (PendingIntent, (Boolean) -> Unit) -> Unit,
) {
    private val editor = MediaEditor(context)

    // The last delete or move, while it can still be reversed; a newer one replaces it.
    var undoOffer by mutableStateOf<UndoOffer?>(null)
        private set

    fun undo(offer: UndoOffer) {
        if (undoOffer !== offer) return
        undoOffer = null
        offer.revert()
    }

    fun expireUndo(offer: UndoOffer) {
        if (undoOffer === offer) undoOffer = null
    }

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

    // A crop or trim saved as a copy beside the original. The returned job is cancelled when the editor is left mid-save.
    fun crop(item: MediaItem, crop: RectF, startMs: Long, endMs: Long, onProgress: (Float) -> Unit, onFinished: (Boolean) -> Unit): Job = scope.launch {
        val result = runCatching { if (item.isVideo) editor.editVideo(item, crop, startMs, endMs, onProgress) else editor.cropImage(item, crop) }
        result.onSuccess { file ->
            if (isPrivate(item)) {
                onPrivateChanged()
            } else {
                // The scan files it in MediaStore; the date taken is set after, so the copy sorts beside its original even when the file carries no date of its own.
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null) { _, uri ->
                    if (uri != null) runCatching { context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DATE_TAKEN, item.timestampMillis) }, null, null) }
                }
            }
            notify("Saved as a copy")
        }
        result.exceptionOrNull()?.let { if (it !is kotlinx.coroutines.CancellationException) notify("Could not save: ${it.message.orEmpty().take(80)}") }
        onFinished(result.isSuccess)
    }

    // A still taken out of a video, dated at the moment it shows, so it sorts beside the video.
    fun saveFrame(item: MediaItem, positionMs: Long) {
        scope.launch {
            val result = runCatching { editor.saveFrame(item, positionMs) }
            result.onSuccess { file ->
                if (isPrivate(item)) {
                    onPrivateChanged()
                } else {
                    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null) { _, uri ->
                        if (uri != null) runCatching { context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DATE_TAKEN, item.timestampMillis + positionMs) }, null, null) }
                    }
                }
                Haptics.confirm(context)
                notify("Frame saved")
            }
            result.exceptionOrNull()?.let { notify("Could not save the frame") }
        }
    }

    fun toggleFavorite(item: MediaItem) {
        if (isPrivate(item)) {
            runVault(if (item.isFavorite) "Removed from private favorites" else "Added to private favorites", "Could not change favorite") {
                vault.setFavorite(item, !item.isFavorite)
                Haptics.confirm(context)
                true
            }
        } else {
            startRequest(MediaStore.createFavoriteRequest(context.contentResolver, listOf(item.uri), !item.isFavorite)) { isDone -> if (isDone) Haptics.confirm(context) }
        }
    }

    fun trash(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val uris = items.map { it.uri }
        startRequest(MediaStore.createTrashRequest(context.contentResolver, uris, true)) { isDone ->
            if (isDone) {
                Haptics.confirm(context)
                undoOffer = UndoOffer(summary(items.size, items.size, "Moved to trash")) {
                    startRequest(MediaStore.createTrashRequest(context.contentResolver, uris, false)) { isRestored -> if (isRestored) Haptics.confirm(context) }
                }
            } else {
                notify("Delete was not allowed")
            }
        }
    }

    // The trash holds Android's trashed rows and Samsung Gallery's trashed files side by side; the files are plain file moves, the rows go through MediaStore.
    fun restore(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val (samsungItems, systemItems) = items.partition { isSamsungTrash(it) }
        runSamsungTrash(samsungItems, "Moved to Restored") { samsungTrash.restore(it) }
        if (systemItems.isEmpty()) return
        startRequest(MediaStore.createTrashRequest(context.contentResolver, systemItems.map { it.uri }, false)) { isDone ->
            if (isDone) Haptics.confirm(context)
            notify(if (isDone) summary(systemItems.size, systemItems.size, "Restored") else "Restore was not allowed")
        }
    }

    fun deleteForever(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val (samsungItems, systemItems) = items.partition { isSamsungTrash(it) }
        runSamsungTrash(samsungItems, "Deleted") { samsungTrash.delete(it) }
        if (systemItems.isEmpty()) return
        startRequest(MediaStore.createDeleteRequest(context.contentResolver, systemItems.map { it.uri })) { isDone ->
            if (isDone) Haptics.confirm(context)
            notify(if (isDone) summary(systemItems.size, systemItems.size, "Deleted") else "Delete was not allowed")
        }
    }

    private fun isSamsungTrash(item: MediaItem): Boolean = item.absolutePath.startsWith(SamsungTrash.ROOT.absolutePath + "/")

    private fun runSamsungTrash(items: List<MediaItem>, success: String, operation: suspend (MediaItem) -> Boolean) {
        if (items.isEmpty()) return
        scope.launch {
            val done = items.count { runCatching { operation(it) }.getOrDefault(false) }
            onTrashChanged()
            if (done > 0) Haptics.confirm(context)
            notify(summary(done, items.size, success))
        }
    }

    // Renaming an album is moving every photo into a sibling folder with the new name; MediaStore keeps each row, so favourites survive. The old folder goes once it is empty.
    fun renameAlbum(album: Album, newName: String) {
        val parent = album.relativePath.trimEnd('/').substringBeforeLast('/', "")
        val cleanName = newName.trim().replace('/', ' ').trimStart('.').ifBlank { return }
        val relativePath = if (parent.isEmpty()) "$cleanName/" else "$parent/$cleanName/"
        scope.launch {
            val moved = album.items.count { runCatching { repository.moveTo(it, relativePath) }.getOrDefault(false) }
            if (moved == album.items.size) Settings.replaceAlbumPath(album.relativePath, relativePath)
            File(Environment.getExternalStorageDirectory(), album.relativePath).let { folder -> if (folder.listFiles().isNullOrEmpty()) folder.delete() }
            notify(summary(moved, album.items.size, "Renamed to $cleanName"))
        }
    }

    fun renameGroup(group: PrivateGroup, newName: String) =
        runVault("Renamed to ${newName.trim()}", "Could not rename ${group.name}") { vault.renameGroup(group, newName) }

    fun move(items: List<MediaItem>, album: Album) = move(items, album.relativePath, album.name)

    // Into any folder, existing or not — a new album is just a folder that a first photo is moved into.
    fun move(items: List<MediaItem>, relativePath: String, albumName: String) {
        if (items.isEmpty()) return
        val moveAll = {
            scope.launch {
                val moved = items.filter { runCatching { repository.moveTo(it, relativePath) }.getOrDefault(false) }
                if (moved.isEmpty()) {
                    notify(summary(0, items.size, "Moved to $albumName"))
                    return@launch
                }
                Haptics.confirm(context)
                undoOffer = UndoOffer(summary(moved.size, items.size, "Moved to $albumName")) { moveBack(moved, relativePath) }
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

    // Each photo goes back to the folder it came from; the row is the same, only where its file now lies has changed.
    private fun moveBack(moved: List<MediaItem>, movedTo: String) {
        scope.launch {
            val folder = File(Environment.getExternalStorageDirectory(), movedTo)
            val returned = moved.count { item ->
                val there = item.copy(relativePath = movedTo, absolutePath = File(folder, File(item.absolutePath).name).path)
                runCatching { repository.moveTo(there, item.relativePath) }.getOrDefault(false)
            }
            if (returned > 0) Haptics.confirm(context)
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
            if (restored.any { it.second != null }) Haptics.confirm(context)
            notify(summary(restored.count { it.second != null }, items.size, "Moved to $albumName"))
            val favoriteUris = restored.filter { (item, uri) -> item.isFavorite && uri != null }.mapNotNull { it.second }
            if (favoriteUris.isNotEmpty()) startRequest(MediaStore.createFavoriteRequest(context.contentResolver, favoriteUris, true)) { }
        }
    }

    fun deleteGroup(group: PrivateGroup) =
        runVault("Deleted ${group.name}", "Could not delete ${group.name}") { vault.deleteGroup(group).also { if (it) Haptics.confirm(context) } }

    fun deletePrivate(items: List<MediaItem>) =
        runVaultBatch(items, "Deleted") { vault.delete(it) }

    private fun runVaultBatch(items: List<MediaItem>, success: String, operation: suspend (MediaItem) -> Boolean) {
        if (items.isEmpty()) return
        scope.launch {
            val done = items.count { runCatching { operation(it) }.getOrDefault(false) }
            onPrivateChanged()
            if (done > 0) Haptics.confirm(context)
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
fun rememberMediaActions(repository: MediaRepository, vault: PrivateVault, onPrivateChanged: () -> Unit, samsungTrash: SamsungTrash, onTrashChanged: () -> Unit): MediaActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pending = remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        pending.value?.invoke(result.resultCode == Activity.RESULT_OK)
        pending.value = null
    }
    return remember(repository, vault, launcher) {
        MediaActions(context, repository, vault, onPrivateChanged, samsungTrash, onTrashChanged, scope) { pendingIntent, onResult ->
            pending.value = onResult
            launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        }
    }
}
