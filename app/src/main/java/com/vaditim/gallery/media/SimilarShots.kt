package com.vaditim.gallery.media

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

// Shots taken seconds apart that look alike (a burst, ten tries at the same picture) are stacked into one tile. Each candidate's look is boiled down to a 64-bit difference hash of its thumbnail, read once and remembered on disk.
object SimilarShots {
    // Two shots this close in time can be one stack; a run keeps growing while each next shot is this close to the one before.
    private const val WINDOW_MS = 8_000L
    // How many of the 64 hash bits may differ for two shots to count as the same picture.
    private const val MAX_DIFFERENCE = 12

    // Media id to its hash; read by every grid, so a new batch of hashes restacks what is on screen.
    var hashes by mutableStateOf<Map<Long, Long>>(emptyMap())
        internal set

    // Runs of at least two neighbouring items that look alike, as index ranges into `items`.
    fun runsOf(items: List<MediaItem>, known: Map<Long, Long> = hashes): List<IntRange> {
        if (known.isEmpty()) return emptyList()
        val runs = ArrayList<IntRange>()
        var start = 0
        for (index in 1..items.size) {
            val isLinked = index < items.size && isAlike(items[index - 1], items[index], known)
            if (!isLinked) {
                if (index - start >= 2) runs += start until index
                start = index
            }
        }
        return runs
    }

    private fun isAlike(first: MediaItem, second: MediaItem, known: Map<Long, Long>): Boolean {
        if (first.isVideo || second.isVideo) return false
        if (second.timestampMillis - first.timestampMillis > WINDOW_MS) return false
        val firstHash = known[first.id] ?: return false
        val secondHash = known[second.id] ?: return false
        return java.lang.Long.bitCount(firstHash xor secondHash) <= MAX_DIFFERENCE
    }

    // Only photos with another photo within the window are worth hashing.
    fun candidatesOf(items: List<MediaItem>): List<MediaItem> =
        items.filterIndexed { index, item ->
            !item.isVideo && item.uri.scheme == "content" &&
                (isClose(items.getOrNull(index - 1), item) || isClose(item, items.getOrNull(index + 1)))
        }

    private fun isClose(first: MediaItem?, second: MediaItem?): Boolean =
        first != null && second != null && !first.isVideo && !second.isVideo && second.timestampMillis - first.timestampMillis <= WINDOW_MS
}

class SimilarIndex(private val context: Context) {
    private val file = File(context.filesDir, "similar.tsv")
    private val known = HashMap<Long, Long>()
    private var isLoaded = false
    private val lock = Mutex()

    suspend fun index(items: List<MediaItem>): Unit = withContext(Dispatchers.IO) { lock.withLock {
        load()
        SimilarShots.hashes = HashMap(known)
        val unread = SimilarShots.candidatesOf(items).filter { it.id !in known }
        unread.chunked(BATCH).forEach { batch ->
            batch.forEach { item -> hashOf(item)?.let { known[item.id] = it } }
            save()
            SimilarShots.hashes = HashMap(known)
        }
    } }

    // A difference hash: the thumbnail shrunk to 9×8 greys, one bit per pair of side-by-side pixels saying which is brighter. Light, crop and small moves barely change it; a different picture changes it a lot.
    private fun hashOf(item: MediaItem): Long? = try {
        val thumbnail = context.contentResolver.loadThumbnail(item.uri, Size(THUMBNAIL_PIXELS, THUMBNAIL_PIXELS), null)
        // The system may hand back a hardware bitmap, whose pixels cannot be read.
        val readable = if (thumbnail.config == Bitmap.Config.HARDWARE) thumbnail.copy(Bitmap.Config.ARGB_8888, false) else thumbnail
        val small = Bitmap.createScaledBitmap(readable, 9, 8, true)
        var hash = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                hash = (hash shl 1) or if (grey(small.getPixel(x, y)) > grey(small.getPixel(x + 1, y))) 1L else 0L
            }
        }
        if (small !== readable) small.recycle()
        if (readable !== thumbnail) readable.recycle()
        thumbnail.recycle()
        hash
    } catch (_: Exception) {
        null
    }

    private fun grey(pixel: Int): Int = ((pixel shr 16 and 0xFF) * 299 + (pixel shr 8 and 0xFF) * 587 + (pixel and 0xFF) * 114) / 1000

    private fun load() {
        if (isLoaded) return
        isLoaded = true
        if (file.exists()) file.forEachLine { line ->
            val parts = line.split('\t')
            val id = parts.getOrNull(0)?.toLongOrNull() ?: return@forEachLine
            val hash = parts.getOrNull(1)?.toLongOrNull() ?: return@forEachLine
            known[id] = hash
        }
    }

    private fun save() {
        file.writeText(buildString { known.forEach { (id, hash) -> append(id).append('\t').append(hash).append('\n') } })
    }

    private companion object {
        const val BATCH = 200
        const val THUMBNAIL_PIXELS = 96
    }
}
