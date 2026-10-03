package com.vaditim.gallery.vault

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import com.vaditim.gallery.media.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

data class PrivateGroup(val name: String, val directory: File, val items: List<MediaItem>, val coverFileName: String? = null) {
    val cover: MediaItem? get() = items.firstOrNull { File(it.absolutePath).name == coverFileName } ?: items.lastOrNull()
}

data class PrivateContents(val groups: List<PrivateGroup>, val favorites: List<MediaItem>)

// Private photos are ordinary files in a hidden folder at the root of the phone's storage, with a .nomedia marker so MediaStore — and so every gallery, Samsung's included — never indexes them. Plain files rather than app-internal storage, because app storage is wiped when the app is uninstalled and a sideloaded app gets uninstalled; they are not encrypted, so a file manager can still reach them.
class PrivateVault(private val context: Context) {

    // Favourites inside Private are kept here, one "group/file" per line, never in MediaStore: a photo that is private must not be findable through anything another app can query.
    private val favoritesFile get() = File(ROOT, ".favorites")
    private val favoritesLock = Mutex()

    suspend fun read(): PrivateContents = withContext(Dispatchers.IO) {
        ensureRoot()
        val favoriteKeys = readFavoriteKeys()
        val groups = ROOT.listFiles { file -> file.isDirectory }
            .orEmpty()
            .map { directory -> PrivateGroup(directory.name, directory, readItems(directory, favoriteKeys), File(directory, COVER_FILE).takeIf { it.isFile }?.readText()?.trim()) }
            .sortedBy { it.name.lowercase() }
        PrivateContents(groups, groups.flatMap { group -> group.items.filter { it.isFavorite } }.sortedBy { it.timestampMillis })
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

    suspend fun delete(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        val file = File(item.absolutePath)
        file.delete().also { if (it) updateFavorites { keys -> keys - keyOf(file) } }
    }

    // Deletes a group and everything in it. Final: private photos are outside the system trash.
    suspend fun deleteGroup(group: PrivateGroup): Boolean = withContext(Dispatchers.IO) {
        updateFavorites { keys -> keys.filterNot { it.startsWith("${group.directory.name}/") }.toSet() }
        group.directory.deleteRecursively()
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
        directory.listFiles { file -> file.isFile && !file.name.startsWith(".") }
            .orEmpty()
            .mapNotNull { file ->
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: return@mapNotNull null
                if (!mimeType.startsWith("image/") && !mimeType.startsWith("video/")) return@mapNotNull null
                MediaItem(
                    id = stableId(file),
                    uri = Uri.fromFile(file),
                    isVideo = mimeType.startsWith("video/"),
                    mimeType = mimeType,
                    name = file.name,
                    timestampMillis = file.lastModified(),
                    bucketId = stableId(directory),
                    bucketName = directory.name,
                    relativePath = "",
                    isFavorite = keyOf(file) in favoriteKeys,
                    durationMillis = 0,
                    width = 0,
                    height = 0,
                    sizeBytes = file.length(),
                    absolutePath = file.absolutePath,
                )
            }
            .sortedBy { it.timestampMillis }

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
    }
}
