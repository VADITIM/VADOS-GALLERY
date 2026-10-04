package com.vaditim.gallery.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import androidx.media3.common.MediaItem as PlayerMediaItem

// Crops and trims are written as a new file beside the original, which is never touched: undoing an edit is deleting the copy.
class MediaEditor(private val context: Context) {

    // `crop` is the kept part of the picture as fractions of its upright width and height.
    suspend fun cropImage(item: MediaItem, crop: RectF): File = withContext(Dispatchers.IO) {
        val source = if (item.uri.scheme == "content") ImageDecoder.createSource(context.contentResolver, item.uri) else ImageDecoder.createSource(File(item.uri.path!!))
        // The decoder turns the picture upright before it crops, so the fractions match what was shown.
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            decoder.crop = Rect(
                (crop.left * width).roundToInt().coerceIn(0, width - 1),
                (crop.top * height).roundToInt().coerceIn(0, height - 1),
                (crop.right * width).roundToInt().coerceIn(1, width),
                (crop.bottom * height).roundToInt().coerceIn(1, height),
            )
        }
        val isPng = item.mimeType == "image/png"
        val output = uniqueFile(outputFolder(item), item.name, "crop", if (isPng) "png" else "jpg")
        output.outputStream().use { bitmap.compress(if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (isPng) 100 else JPEG_QUALITY, it) }
        bitmap.recycle()
        if (!isPng) copyExif(item, output)
        output.setLastModified(item.timestampMillis)
        output
    }

    // `endMs` is C.TIME_END_OF_SOURCE to keep the video to its end.
    @OptIn(UnstableApi::class)
    suspend fun editVideo(item: MediaItem, crop: RectF, startMs: Long, endMs: Long, onProgress: (Float) -> Unit): File {
        val output = withContext(Dispatchers.IO) { uniqueFile(outputFolder(item), item.name, "edit", "mp4") }
        val clipping = PlayerMediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs)
            .apply { if (endMs != C.TIME_END_OF_SOURCE) setEndPositionMs(endMs) }
            .build()
        val media = PlayerMediaItem.Builder().setUri(item.uri).setClippingConfiguration(clipping).build()
        // The crop effect counts from the centre, -1 to 1, with up being positive.
        val effects = if (crop == RectF(0f, 0f, 1f, 1f)) Effects.EMPTY else Effects(emptyList(), listOf(Crop(crop.left * 2f - 1f, crop.right * 2f - 1f, 1f - crop.bottom * 2f, 1f - crop.top * 2f)))
        val edited = EditedMediaItem.Builder(media).setEffects(effects).build()
        // The transformer lives on the thread that made it; the main thread is the one with a looper to hand.
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val handler = Handler(Looper.getMainLooper())
                val transformer = Transformer.Builder(context)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            output.delete()
                            if (continuation.isActive) continuation.resumeWithException(exportException)
                        }
                    })
                    .build()
                transformer.start(edited, output.absolutePath)
                val holder = ProgressHolder()
                val poll = object : Runnable {
                    override fun run() {
                        if (!continuation.isActive) return
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                        handler.postDelayed(this, PROGRESS_POLL_MS)
                    }
                }
                handler.post(poll)
                continuation.invokeOnCancellation {
                    handler.post {
                        transformer.cancel()
                        output.delete()
                    }
                }
            }
        }
        output.setLastModified(item.timestampMillis)
        return output
    }

    private fun outputFolder(item: MediaItem): File =
        item.absolutePath.takeIf { it.isNotEmpty() }?.let { File(it).parentFile }
            ?: File(Environment.getExternalStorageDirectory(), item.relativePath.ifEmpty { if (item.isVideo) "Movies/" else "Pictures/" })

    private fun uniqueFile(folder: File, name: String, tag: String, extension: String): File {
        folder.mkdirs()
        val base = name.substringBeforeLast('.')
        var file = File(folder, "${base}_$tag.$extension")
        var number = 2
        while (file.exists()) {
            file = File(folder, "${base}_$tag$number.$extension")
            number++
        }
        return file
    }

    // The copy keeps when and where the photo was taken, so it sorts beside the original and stays on the map.
    private fun copyExif(item: MediaItem, output: File) {
        runCatching {
            val resolver = context.contentResolver
            val original = if (item.uri.scheme == "content") runCatching { resolver.openInputStream(MediaStore.setRequireOriginal(item.uri)) }.getOrNull() else null
            val source = (original ?: resolver.openInputStream(item.uri))?.use { ExifInterface(it) } ?: return
            val target = ExifInterface(output.absolutePath)
            KEPT_EXIF.forEach { tag -> source.getAttribute(tag)?.let { target.setAttribute(tag, it) } }
            target.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            target.saveAttributes()
        }
    }

    private companion object {
        const val JPEG_QUALITY = 95
        const val PROGRESS_POLL_MS = 100L
        val KEPT_EXIF = listOf(
            ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF, ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
        )
    }
}
