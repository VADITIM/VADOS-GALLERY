package com.vaditim.gallery.vault

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import com.vaditim.gallery.media.MediaItem
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class PrivateGroup(val name: String, val directory: File, val items: List<MediaItem>, val coverFileName: String? = null) {
    val cover: MediaItem? get() = items.firstOrNull { File(it.absolutePath).name == coverFileName } ?: items.lastOrNull()
}

// `trash` holds the private photos deleted in the last 30 days, each with when it goes for good.
data class PrivateContents(val groups: List<PrivateGroup>, val favorites: List<MediaItem>, val trash: List<MediaItem> = emptyList())

// Private photos are ordinary files in a hidden folder at the root of the phone's storage, with a .nomedia marker so MediaStore — and so every gallery, Samsung's included — never indexes them. Plain files rather than app-internal storage, because app storage is wiped when the app is uninstalled and a sideloaded app gets uninstalled; they are not encrypted, so a file manager can still reach them.
class PrivateVault(private val context: Context) {

    // Favourites inside Private are kept here, one "group/file" per line, never in MediaStore: a photo that is private must not be findable through anything another app can query.
    private val favoritesFile get() = File(ROOT, ".favorites")
    private val favoritesLock = Mutex()

    // Deleted private photos wait in a dot folder inside Private for 30 days, as the system trash keeps everything else; they never pass through MediaStore's trash, which would hand them back to the library. The index says, per file, which album it left, when, and whether it was a favourite.
    private val trashIndex get() = File(TRASH, ".index")
    private val trashLock = Mutex()

    private class TrashEntry(val group: String, val trashedMillis: Long, val wasFavorite: Boolean)

    suspend fun read(): PrivateContents = withContext(Dispatchers.IO) {
        ensureRoot()
        val favoriteKeys = readFavoriteKeys()
        // Dot folders (the trash) are Private's own bookkeeping, never albums.
        val groups = ROOT.listFiles { file -> file.isDirectory && !file.name.startsWith(".") }
            .orEmpty()
            .map { directory -> PrivateGroup(directory.name, directory, readItems(directory, favoriteKeys), File(directory, COVER_FILE).takeIf { it.isFile }?.readText()?.trim()) }
            .sortedBy { it.name.lowercase() }
        PrivateContents(groups, groups.flatMap { group -> group.items.filter { it.isFavorite } }.sortedBy { it.timestampMillis }, readTrash())
    }

    // The chosen cover is a one-line dot file inside the group, so it travels with the group and is never listed as a photo.
    suspend fun setCover(groupName: String, item: MediaItem) = withContext(Dispatchers.IO) {
        File(ROOT, groupName).takeIf { it.isDirectory }?.let { File(it, COVER_FILE).writeText(File(item.absolutePath).name) }
        Unit
    }

    suspend fun createGroup(name: String): File = withContext(Dispatchers.IO) {
        ensureRoot()
        File(ROOT, sanitize(name)).apply { mkdirs() }
    }

    // A rename on the same volume: instant, and the file keeps its modified time. The scan afterwards makes MediaStore drop the old row; a favourite comes along as a private favourite.
    suspend fun hide(item: MediaItem, groupName: String): Boolean = withContext(Dispatchers.IO) {
        val source = File(item.absolutePath)
        val target = uniqueFile(createGroup(groupName), source.name)
        val isMoved = moveFile(source, target)
        if (isMoved) {
            scan(source)
            if (item.isFavorite) updateFavorites { it + keyOf(target) }
        }
        isMoved
    }

    suspend fun moveToGroup(item: MediaItem, groupName: String): Boolean = withContext(Dispatchers.IO) {
        val source = File(item.absolutePath)
        val target = uniqueFile(createGroup(groupName), source.name)
        val isMoved = moveFile(source, target)
        if (isMoved && item.isFavorite) updateFavorites { it - keyOf(source) + keyOf(target) }
        isMoved
    }

    // Back out into a normal album folder. Returns the item's new MediaStore uri once the scanner has indexed it (it reads DATE_TAKEN back out of the EXIF), so the caller can hand a favourite back to MediaStore.
    suspend fun unhide(item: MediaItem, relativePath: String): Uri? = withContext(Dispatchers.IO) {
        val source = File(item.absolutePath)
        val directory = File(Environment.getExternalStorageDirectory(), relativePath).apply { mkdirs() }
        val target = uniqueFile(directory, source.name)
        if (!moveFile(source, target)) return@withContext null
        updateFavorites { it - keyOf(source) }
        scanForUri(target)
    }

    suspend fun setFavorite(item: MediaItem, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        val key = keyOf(File(item.absolutePath))
        updateFavorites { if (isFavorite) it + key else it - key }
    }

    // Into Private's trash; the file it now is, so the move can be taken back, or null when it could not be moved.
    suspend fun trash(item: MediaItem): File? = withContext(Dispatchers.IO) {
        val source = File(item.absolutePath)
        val target = uniqueFile(TRASH.apply { mkdirs() }, source.name)
        val wasFavorite = keyOf(source) in readFavoriteKeys()
        if (!moveFile(source, target)) return@withContext null
        if (wasFavorite) updateFavorites { it - keyOf(source) }
        trashLock.withLock { writeTrashEntries(readTrashEntries() + (target.name to TrashEntry(source.parentFile?.name.orEmpty(), System.currentTimeMillis(), wasFavorite))) }
        target
    }

    suspend fun restore(item: MediaItem, groupName: String? = null): Boolean = restoreFile(File(item.absolutePath), groupName)

    // Back into the album it left, or into `groupName`; an album since deleted is made again. A favourite comes back a favourite.
    suspend fun restoreFile(file: File, groupName: String? = null): Boolean = withContext(Dispatchers.IO) {
        val entry = trashLock.withLock { readTrashEntries()[file.name] }
        val target = uniqueFile(createGroup(groupName ?: entry?.group?.takeIf { it.isNotBlank() } ?: RESTORED_GROUP), file.name)
        if (!moveFile(file, target)) return@withContext false
        trashLock.withLock { writeTrashEntries(readTrashEntries() - file.name) }
        if (entry?.wasFavorite == true) updateFavorites { it + keyOf(target) }
        true
    }

    // Out of Private's trash for good.
    suspend fun deleteFromTrash(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        val file = File(item.absolutePath)
        val isDeleted = file.delete() || !file.exists()
        if (isDeleted) trashLock.withLock { writeTrashEntries(readTrashEntries() - file.name) }
        isDeleted
    }

    // A group's photos go to Private's trash, each remembering the group, so restoring them makes it again; the folder and its cover go. The files they now are, for taking it back.
    suspend fun deleteGroup(group: PrivateGroup): List<File> = withContext(Dispatchers.IO) {
        val trashed = group.items.mapNotNull { trash(it) }
        if (trashed.size == group.items.size) group.directory.deleteRecursively()
        trashed
    }

    // The folder is renamed in place; favourites are keyed by folder name, so their keys follow it.
    suspend fun renameGroup(group: PrivateGroup, newName: String): Boolean = withContext(Dispatchers.IO) {
        val target = File(ROOT, sanitize(newName))
        if (target.exists() || !group.directory.renameTo(target)) return@withContext false
        val oldPrefix = "${group.directory.name}/"
        updateFavorites { keys -> keys.map { if (it.startsWith(oldPrefix)) "${target.name}/" + it.removePrefix(oldPrefix) else it }.toSet() }
        true
    }

    suspend fun removeGroupIfEmpty(name: String) = withContext(Dispatchers.IO) {
        val directory = File(ROOT, name)
        if (directory.listFiles().isNullOrEmpty()) directory.delete()
    }

    // A rename where the system allows one — instant, same disk. Some moves between top-level folders are refused by the storage layer, so the fallback is copy, check, then delete the original; the original is only removed once the copy is complete.
    private fun moveFile(source: File, target: File): Boolean {
        if (!source.exists()) return false
        target.parentFile?.mkdirs()
        if (source.renameTo(target)) return true
        return try {
            source.copyTo(target, overwrite = false)
            target.setLastModified(source.lastModified())
            if (target.length() == source.length() && source.delete()) {
                true
            } else {
                target.delete()
                false
            }
        } catch (exception: Exception) {
            target.delete()
            false
        }
    }

    private fun readItems(directory: File, favoriteKeys: Set<String>): List<MediaItem> =
        mediaFilesIn(directory)
            .mapNotNull { file -> itemOf(file, directory.name, stableId(directory), isFavorite = keyOf(file) in favoriteKeys) }
            .sortedBy { it.timestampMillis }

    // What is in Private's trash, each named after the album it left and dated when it goes for good. Anything past its 30 days is deleted here, as the app does for the system trash.
    private suspend fun readTrash(): List<MediaItem> = trashLock.withLock {
        val entries = readTrashEntries()
        val now = System.currentTimeMillis()
        val items = mediaFilesIn(TRASH).mapNotNull { file ->
            val entry = entries[file.name] ?: TrashEntry(RESTORED_GROUP, file.lastModified(), false)
            val expires = entry.trashedMillis + TRASH_KEEP_MS
            if (expires <= now) {
                file.delete()
                null
            } else {
                itemOf(file, entry.group, stableId(TRASH), isFavorite = false)?.copy(expiresMillis = expires, trashedMillis = entry.trashedMillis)
            }
        }
        val kept = entries.filterKeys { name -> items.any { File(it.absolutePath).name == name } }
        if (kept.size != entries.size) writeTrashEntries(kept)
        items.sortedBy { it.timestampMillis }
    }

    private fun mediaFilesIn(directory: File): List<File> = directory.listFiles { file -> file.isFile && !file.name.startsWith(".") }.orEmpty().toList()

    private fun itemOf(file: File, groupName: String, bucketId: Long, isFavorite: Boolean): MediaItem? {
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: return null
        if (!mimeType.startsWith("image/") && !mimeType.startsWith("video/")) return null
        return MediaItem(
            id = stableId(file),
            uri = Uri.fromFile(file),
            isVideo = mimeType.startsWith("video/"),
            mimeType = mimeType,
            name = file.name,
            timestampMillis = file.lastModified(),
            bucketId = bucketId,
            bucketName = groupName,
            relativePath = "",
            isFavorite = isFavorite,
            durationMillis = 0,
            width = 0,
            height = 0,
            sizeBytes = file.length(),
            absolutePath = file.absolutePath,
        )
    }

    // One line per trashed file: its name in the trash, the album it left, when, and 1 if it was a favourite.
    private fun readTrashEntries(): Map<String, TrashEntry> =
        if (!trashIndex.exists()) emptyMap() else trashIndex.readLines().mapNotNull { line ->
            val parts = line.split('\t')
            val millis = parts.getOrNull(2)?.toLongOrNull() ?: return@mapNotNull null
            parts[0] to TrashEntry(parts.getOrNull(1).orEmpty(), millis, parts.getOrNull(3) == "1")
        }.toMap()

    private fun writeTrashEntries(entries: Map<String, TrashEntry>) {
        TRASH.mkdirs()
        trashIndex.writeText(entries.entries.joinToString("\n") { (name, entry) -> "$name\t${entry.group}\t${entry.trashedMillis}\t${if (entry.wasFavorite) 1 else 0}" })
    }

    private fun readFavoriteKeys(): Set<String> =
        if (favoritesFile.exists()) favoritesFile.readLines().filter { it.isNotBlank() }.toSet() else emptySet()

    private suspend fun updateFavorites(change: (Set<String>) -> Set<String>) = favoritesLock.withLock {
        favoritesFile.writeText(change(readFavoriteKeys()).joinToString("\n"))
    }

    private fun keyOf(file: File): String = "${file.parentFile?.name}/${file.name}"

    private fun ensureRoot() {
        ROOT.mkdirs()
        File(ROOT, ".nomedia").let { if (!it.exists()) it.createNewFile() }
    }

    private fun scan(file: File) {
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
    }

    private suspend fun scanForUri(file: File): Uri? = suspendCancellableCoroutine { continuation ->
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null) { _, uri ->
            if (continuation.isActive) continuation.resume(uri)
        }
    }

    private fun uniqueFile(directory: File, name: String): File {
        var candidate = File(directory, name)
        var counter = 1
        while (candidate.exists()) {
            candidate = File(directory, "${name.substringBeforeLast('.')} ($counter).${name.substringAfterLast('.', "")}".removeSuffix("."))
            counter++
        }
        return candidate
    }

    private fun sanitize(name: String): String = name.trim().replace('/', ' ').trimStart('.').ifBlank { "Private" }

    private fun stableId(file: File): Long = (file.absolutePath.hashCode().toLong() shl 32) or (file.absolutePath.length.toLong() and 0xFFFFFFFFL)

    companion object {
        private const val COVER_FILE = ".cover"
        val ROOT = File(Environment.getExternalStorageDirectory(), ".vados-private")
        val TRASH = File(ROOT, ".trash")
        // Where a photo goes back to when the album it left is not known.
        private const val RESTORED_GROUP = "Restored"
        private const val TRASH_KEEP_MS = 30L * 24 * 60 * 60 * 1000
    }
}
