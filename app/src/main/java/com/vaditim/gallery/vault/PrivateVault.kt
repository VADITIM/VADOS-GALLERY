package com.vaditim.gallery.vault

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import com.vaditim.gallery.media.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class PrivateGroup(val name: String, val directory: File, val items: List<MediaItem>) {
    val cover: MediaItem? get() = items.lastOrNull()
}

// Private photos are ordinary files in a hidden folder at the root of the phone's storage, with a .nomedia marker so MediaStore — and so every gallery, Samsung's included — never indexes them. Plain files rather than app-internal storage, because app storage is wiped when the app is uninstalled and a sideloaded app gets uninstalled; they are not encrypted, so a file manager can still reach them.
class PrivateVault(private val context: Context) {

    private val root = File(Environment.getExternalStorageDirectory(), ".vados-private")

    suspend fun readGroups(): List<PrivateGroup> = withContext(Dispatchers.IO) {
        ensureRoot()
        root.listFiles { file -> file.isDirectory }
            .orEmpty()
            .map { directory -> PrivateGroup(directory.name, directory, readItems(directory)) }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun createGroup(name: String): File = withContext(Dispatchers.IO) {
        ensureRoot()
        File(root, sanitize(name)).apply { mkdirs() }
    }

    // A rename on the same volume: instant, and the file keeps its modified time. The scan afterwards makes MediaStore drop the old row.
    suspend fun hide(item: MediaItem, groupName: String): Boolean = withContext(Dispatchers.IO) {
        val source = File(item.absolutePath)
        val target = uniqueFile(createGroup(groupName), source.name)
        source.renameTo(target).also { if (it) scan(source, target) }
    }

    suspend fun moveToGroup(item: MediaItem, groupName: String): Boolean = withContext(Dispatchers.IO) {
        val source = File(item.absolutePath)
        source.renameTo(uniqueFile(createGroup(groupName), source.name))
    }

    // Back out into a normal album folder; the scan makes MediaStore index it again, and it reads DATE_TAKEN back out of the file's EXIF.
    suspend fun unhide(item: MediaItem, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val source = File(item.absolutePath)
        val directory = File(Environment.getExternalStorageDirectory(), relativePath).apply { mkdirs() }
        val target = uniqueFile(directory, source.name)
        source.renameTo(target).also { if (it) scan(target) }
    }

    suspend fun delete(item: MediaItem): Boolean = withContext(Dispatchers.IO) { File(item.absolutePath).delete() }

    private fun readItems(directory: File): List<MediaItem> =
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
                    isFavorite = false,
                    durationMillis = 0,
                    width = 0,
                    height = 0,
                    sizeBytes = file.length(),
                    absolutePath = file.absolutePath,
                )
            }
            .sortedBy { it.timestampMillis }

    private fun ensureRoot() {
        root.mkdirs()
        File(root, ".nomedia").let { if (!it.exists()) it.createNewFile() }
    }

    private fun scan(vararg files: File) {
        MediaScannerConnection.scanFile(context, files.map { it.absolutePath }.toTypedArray(), null, null)
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
}
