package com.vaditim.gallery.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

// A motion photo is a still with a short MP4 appended to the same file (Samsung's and Google's format alike). The still is all any other app sees; the clip is found by reading the file itself.
object MotionPhoto {
    // The XMP that marks a motion photo sits near the start of the file, so the grid only reads this much to know.
    private const val HEAD_BYTES = 128 * 1024
    // Samsung writes this name right before the clip.
    private val SAMSUNG_MARKER = "MotionPhoto_Data".toByteArray()
    private val MARKS = listOf("MotionPhoto".toByteArray(), "MicroVideo".toByteArray())
    private val FILE_TYPE = "ftyp".toByteArray()

    private val known = ConcurrentHashMap<Long, Boolean>()

    fun knownFor(item: MediaItem): Boolean? = if (item.isVideo) false else known[item.id]

    suspend fun isMotion(context: Context, item: MediaItem): Boolean {
        if (item.isVideo) return false
        known[item.id]?.let { return it }
        val result = withContext(Dispatchers.IO) {
            val head = runCatching {
                context.contentResolver.openInputStream(item.uri)?.use { stream -> readHead(stream) }
            }.getOrNull() ?: return@withContext false
            MARKS.any { indexOf(head, it, 0) >= 0 }
        }
        known[item.id] = result
        return result
    }

    private fun readHead(stream: java.io.InputStream): ByteArray {
        val buffer = ByteArray(HEAD_BYTES)
        var filled = 0
        while (filled < HEAD_BYTES) {
            val read = stream.read(buffer, filled, HEAD_BYTES - filled)
            if (read < 0) break
            filled += read
        }
        return buffer.copyOf(filled)
    }

    // Where the clip lies inside the file: its first byte and its length.
    data class Clip(val start: Long, val length: Long)

    suspend fun findClip(context: Context, item: MediaItem): Clip? = withContext(Dispatchers.IO) {
        if (item.isVideo) return@withContext null
        val bytes = runCatching { context.contentResolver.openInputStream(item.uri)?.use { it.readBytes() } }.getOrNull() ?: return@withContext null
        val afterMarker = indexOf(bytes, SAMSUNG_MARKER, 0).let { if (it >= 0) it + SAMSUNG_MARKER.size else -1 }
        // The clip starts with an MP4 "ftyp" box; a HEIC still opens with its own at byte 4, so the search starts past that.
        val start = if (afterMarker >= 0 && isFileTypeAt(bytes, afterMarker)) afterMarker else firstClipBox(bytes)
        if (start < 0) return@withContext null
        val length = boxesLength(bytes, start)
        if (length <= 0) null else Clip(start.toLong(), length)
    }

    private fun isFileTypeAt(bytes: ByteArray, start: Int): Boolean =
        start + 8 <= bytes.size && (0 until 4).all { bytes[start + 4 + it] == FILE_TYPE[it] }

    private fun firstClipBox(bytes: ByteArray): Int {
        var from = 16
        while (true) {
            val found = indexOf(bytes, FILE_TYPE, from)
            if (found < 0) return -1
            val size = readUnsigned(bytes, found - 4)
            if (size in 8..64) return found - 4
            from = found + 1
        }
    }

    // Walks the MP4's top-level boxes; whatever follows the last whole box (Samsung's own trailer) is not part of the clip.
    private fun boxesLength(bytes: ByteArray, start: Int): Long {
        var position = start.toLong()
        while (position + 8 <= bytes.size) {
            val at = position.toInt()
            val type = bytes.copyOfRange(at + 4, at + 8)
            if (!type.all { it in 0x20..0x7E || it == 0xA9.toByte() }) break
            var size = readUnsigned(bytes, at)
            if (size == 1L && at + 16 <= bytes.size) size = (readUnsigned(bytes, at + 8) shl 32) or readUnsigned(bytes, at + 12)
            if (size == 0L) size = bytes.size - position
            if (size < 8 || position + size > bytes.size) break
            position += size
        }
        return position - start
    }

    private fun readUnsigned(bytes: ByteArray, at: Int): Long =
        if (at < 0 || at + 4 > bytes.size) -1 else (0 until 4).fold(0L) { value, index -> (value shl 8) or (bytes[at + index].toLong() and 0xFF) }

    private fun indexOf(bytes: ByteArray, pattern: ByteArray, from: Int): Int {
        val first = pattern[0]
        var index = from
        val last = bytes.size - pattern.size
        while (index <= last) {
            if (bytes[index] == first) {
                var matched = 1
                while (matched < pattern.size && bytes[index + matched] == pattern[matched]) matched++
                if (matched == pattern.size) return index
            }
            index++
        }
        return -1
    }
}

// Reads only the clip's bytes out of the photo's file, so the player sees a plain MP4 and nothing is copied to disk — private photos included.
@OptIn(UnstableApi::class)
class ClipDataSource(private val inner: DataSource, private val clip: MotionPhoto.Clip) : DataSource by inner {
    override fun open(dataSpec: DataSpec): Long {
        val length = if (dataSpec.length == C.LENGTH_UNSET.toLong()) clip.length - dataSpec.position else dataSpec.length
        return inner.open(dataSpec.buildUpon().setPosition(clip.start + dataSpec.position).setLength(length).build())
    }
}
