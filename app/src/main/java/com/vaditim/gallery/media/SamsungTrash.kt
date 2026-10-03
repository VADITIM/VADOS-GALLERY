package com.vaditim.gallery.media

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Samsung Gallery does not use Android's trash: it moves a deleted file into its own folder and keeps where it came from in a database only it can read. The files are reachable with All files access, so they are listed here; the original folder is not, so a restore lands in one album.
class SamsungTrash(private val context: Context) {

    suspend fun read(): List<MediaItem> = withContext(Dispatchers.IO) {
        ROOT.walkTopDown().maxDepth(MAX_DEPTH)
            .filter { it.isFile && it.length() > 0 }
            .mapNotNull { file -> sniff(file)?.let { mimeType -> itemOf(file, mimeType) } }
            .toList()
    }

    suspend fun restore(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        val source = File(item.absolutePath)
        val directory = File(Environment.getExternalStorageDirectory(), RESTORED_PATH).apply { mkdirs() }
        val target = uniqueFile(directory, restoredName(source, item.mimeType))
        val isMoved = source.renameTo(target) || (runCatching { source.copyTo(target) }.isSuccess && source.delete())
        if (isMoved) {
            target.setLastModified(source.lastModified().takeIf { it > 0 } ?: item.timestampMillis)
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
        }
        isMoved
    }

    suspend fun delete(item: MediaItem): Boolean = withContext(Dispatchers.IO) { File(item.absolutePath).delete() }

    private fun itemOf(file: File, mimeType: String) = MediaItem(
        // Negative, so it can never collide with a MediaStore id in a selection.
        id = -1L - (file.absolutePath.hashCode().toLong() and 0xffffffffL),
        uri = Uri.fromFile(file),
        isVideo = mimeType.startsWith("video/"),
        mimeType = mimeType,
        name = file.name,
        timestampMillis = file.lastModified(),
        bucketId = 0,
        bucketName = "",
        relativePath = "",
        isFavorite = false,
        durationMillis = 0,
        width = 0,
        height = 0,
        sizeBytes = file.length(),
        absolutePath = file.absolutePath,
    )

    // The files often carry no extension, so the type is read from their first bytes.
    private fun sniff(file: File): String? {
        val header = ByteArray(16)
        val count = runCatching { file.inputStream().use { it.read(header) } }.getOrDefault(-1)
        if (count < 12) return null
        fun at(offset: Int, text: String) = text.indices.all { header[offset + it] == text[it].code.toByte() }
        return when {
            header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte() -> "image/jpeg"
            header[0] == 0x89.toByte() && at(1, "PNG") -> "image/png"
            at(0, "GIF8") -> "image/gif"
            at(0, "RIFF") && at(8, "WEBP") -> "image/webp"
            at(4, "ftyp") -> when {
                at(8, "heic") || at(8, "heix") || at(8, "mif1") || at(8, "msf1") || at(8, "hevc") -> "image/heic"
                at(8, "avif") -> "image/avif"
                at(8, "qt  ") -> "video/quicktime"
                else -> "video/mp4"
            }
            at(0, "\u001AEß£") -> "video/webm"
            else -> null
        }
    }

    private fun restoredName(source: File, mimeType: String): String {
        val extension = EXTENSIONS[mimeType] ?: "bin"
        val name = source.name.trimStart('.')
        val known = name.substringAfterLast('.', "").lowercase()
        if (name.isNotEmpty() && known in EXTENSIONS.values) return name
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(source.lastModified()))
        return "${if (mimeType.startsWith("video/")) "VID" else "IMG"}_$stamp.$extension"
    }

    private fun uniqueFile(directory: File, name: String): File {
        var candidate = File(directory, name)
        var counter = 1
        while (candidate.exists()) {
            candidate = File(directory, "${name.substringBeforeLast('.')} ($counter).${name.substringAfterLast('.')}")
            counter++
        }
        return candidate
    }

    companion object {
        val ROOT = File(Environment.getExternalStorageDirectory(), "Android/.Trash")
        const val RESTORED_PATH = "Pictures/Restored/"
        private const val MAX_DEPTH = 4
        private val EXTENSIONS = mapOf(
            "image/jpeg" to "jpg", "image/png" to "png", "image/gif" to "gif", "image/webp" to "webp",
            "image/heic" to "heic", "image/avif" to "avif", "video/mp4" to "mp4", "video/quicktime" to "mov", "video/webm" to "webm",
        )
    }
}
