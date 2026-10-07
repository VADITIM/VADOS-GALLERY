package com.vaditim.gallery.media

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// Finds the same picture saved more than once, by its pixels alone (ImagePrint.kt). Every photo's print is read once from its thumbnail and kept on disk; only pictures the prints pair up are looked at closely, from a larger thumbnail held in memory for the session.
class DuplicateFinder(private val context: Context) {
    enum class Stage { READING, COMPARING, DONE }

    var stage by mutableStateOf(Stage.DONE)
        private set
    // How far through reading the prints, 0 to 1.
    var progress by mutableFloatStateOf(0f)
        private set
    var readCount by mutableIntStateOf(0)
        private set
    var totalCount by mutableIntStateOf(0)
        private set
    // How alike two pictures must be, kept while the app runs.
    var strictness by mutableStateOf(Strictness.CLOSE)

    // Each set ordered best first: the copy worth keeping leads. Null until the first search finishes.
    var groups by mutableStateOf<List<List<MediaItem>>?>(null)
        private set

    private val file = File(context.filesDir, "duplicates.bin")
    private val prints = ConcurrentHashMap<Long, StoredPrint>()
    private val fines = ConcurrentHashMap<Long, ByteArray>()
    private var isLoaded = false
    private val lock = Mutex()

    private class StoredPrint(val sizeBytes: Long, val print: ImagePrint)

    suspend fun find(library: List<MediaItem>, strictness: Strictness): Unit = lock.withLock {
        val photos = library.filter { !it.isVideo && it.uri.scheme == "content" }
        withContext(Dispatchers.IO) {
            load()
            // A file whose size changed was edited in place, so its old print no longer says what it shows.
            val unread = photos.filter { prints[it.id]?.sizeBytes != it.sizeBytes }
            totalCount = photos.size
            val done = AtomicInteger(photos.size - unread.size)
            readCount = done.get()
            progress = if (photos.isEmpty()) 1f else done.get().toFloat() / photos.size
            stage = Stage.READING
            unread.chunked(SAVE_EVERY).forEach { batch ->
                coroutineScope {
                    batch.chunked((batch.size + WORKERS - 1) / WORKERS).map { share ->
                        async {
                            share.forEach { item ->
                                ensureActive()
                                printOf(item)?.let { prints[item.id] = StoredPrint(item.sizeBytes, it) }
                                val count = done.incrementAndGet()
                                if (count % PROGRESS_STEP == 0) {
                                    readCount = count
                                    progress = count.toFloat() / photos.size
                                }
                            }
                        }
                    }.awaitAll()
                }
                save(photos)
            }
            readCount = photos.size
            progress = 1f
        }
        stage = Stage.COMPARING
        groups = withContext(Dispatchers.Default) {
            ImagePrints.groupsOfSame(photos, { prints[it.id]?.print }, { item -> ratioOf(item) }, { fineOf(it) }, strictness)
                .map { group -> group.sortedWith(BEST_FIRST) }
                // The sets with the most to free come first.
                .sortedByDescending { group -> group.drop(1).sumOf { it.sizeBytes } }
        }
        stage = Stage.DONE
    }

    private fun ratioOf(item: MediaItem): Float? =
        if (item.width > 0 && item.height > 0) maxOf(item.width, item.height).toFloat() / minOf(item.width, item.height) else null

    private fun printOf(item: MediaItem): ImagePrint? = pixelsOf(item, PRINT_PIXELS)?.let { (pixels, width, height) -> ImagePrints.of(pixels, width, height) }

    private fun fineOf(item: MediaItem): ByteArray? =
        fines[item.id] ?: pixelsOf(item, FINE_PIXELS)?.let { (pixels, width, height) -> ImagePrints.fineOf(pixels, width, height) }?.also { fines[item.id] = it }

    // The system's thumbnail, which MediaProvider keeps cached, read into plain pixels.
    private fun pixelsOf(item: MediaItem, box: Int): Triple<IntArray, Int, Int>? = try {
        val thumbnail = context.contentResolver.loadThumbnail(item.uri, Size(box, box), null)
        // The system may hand back a hardware bitmap, whose pixels cannot be read.
        val readable = if (thumbnail.config == Bitmap.Config.HARDWARE) thumbnail.copy(Bitmap.Config.ARGB_8888, false) else thumbnail
        val pixels = IntArray(readable.width * readable.height)
        readable.getPixels(pixels, 0, readable.width, 0, 0, readable.width, readable.height)
        val result = Triple(pixels, readable.width, readable.height)
        if (readable !== thumbnail) readable.recycle()
        thumbnail.recycle()
        result
    } catch (_: Exception) {
        null
    }

    private fun load() {
        if (isLoaded) return
        isLoaded = true
        if (!file.exists()) return
        runCatching {
            DataInputStream(file.inputStream().buffered()).use { input ->
                if (input.readInt() != FORMAT) return
                repeat(input.readInt()) {
                    val id = input.readLong()
                    val sizeBytes = input.readLong()
                    val shapes = LongArray(4) { input.readLong() }
                    val colours = IntArray(input.readInt()) { input.readInt() }
                    val detail = ByteArray(input.readInt()).also { input.readFully(it) }
                    val ratio = input.readFloat()
                    prints[id] = StoredPrint(sizeBytes, ImagePrint(shapes, colours, detail, ratio))
                }
            }
        }
    }

    // Only photos still in the library are written back, so deleted ones drop out of the file.
    private fun save(photos: List<MediaItem>) {
        val kept = photos.mapNotNull { item -> prints[item.id]?.let { item.id to it } }
        val temporary = File(file.path + ".new")
        DataOutputStream(temporary.outputStream().buffered()).use { output ->
            output.writeInt(FORMAT)
            output.writeInt(kept.size)
            kept.forEach { (id, stored) ->
                output.writeLong(id)
                output.writeLong(stored.sizeBytes)
                stored.print.shapes.forEach(output::writeLong)
                output.writeInt(stored.print.colours.size)
                stored.print.colours.forEach(output::writeInt)
                output.writeInt(stored.print.detail.size)
                output.write(stored.print.detail)
                output.writeFloat(stored.print.ratio)
            }
        }
        temporary.renameTo(file)
    }

    companion object {
        // Raised whenever ImagePrints.of changes what it reads, so old prints are read again rather than compared with new ones.
        private const val FORMAT = 1
        private const val PRINT_PIXELS = 128
        private const val FINE_PIXELS = 512
        private const val WORKERS = 4
        private const val SAVE_EVERY = 400
        private const val PROGRESS_STEP = 20

        // The copy to keep: the most pixels, then the largest file (the least compressed), then one from the camera, then the oldest, which is most likely the original.
        val BEST_FIRST: Comparator<MediaItem> = compareByDescending<MediaItem> { it.width.toLong() * it.height }
            .thenByDescending { it.sizeBytes }
            .thenByDescending { it.relativePath.startsWith("DCIM/Camera") }
            .thenBy { it.timestampMillis }
    }
}
