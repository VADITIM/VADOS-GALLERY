package com.vaditim.gallery.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Effect
import androidx.media3.effect.Crop
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

// Crops and trims are written as a new file beside the original, which is never touched: undoing an edit is deleting the copy.
class MediaEditor(private val context: Context) {

    // `crop` is the kept part of the picture as fractions of its width and height once turned a quarter clockwise `quarterTurns` times; the strokes are drawn on the upright picture before it turns.
    suspend fun cropImage(item: MediaItem, crop: RectF, quarterTurns: Int = 0, strokes: List<DrawnStroke> = emptyList()): File = withContext(Dispatchers.IO) {
        val source = if (item.uri.scheme == "content") ImageDecoder.createSource(context.contentResolver, item.uri) else ImageDecoder.createSource(File(item.uri.path!!))
        val turns = Math.floorMod(quarterTurns, 4)
        // A plain crop lets the decoder cut, so only the kept part is ever in memory; drawing and turning need the whole picture.
        val isPlainCrop = turns == 0 && strokes.isEmpty()
        // The decoder turns the picture upright before anything else, so the fractions match what was shown.
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            if (isPlainCrop) decoder.crop = pixelsOf(crop, info.size.width, info.size.height) else decoder.isMutableRequired = true
        }
        val bitmap = if (isPlainCrop) {
            decoded
        } else {
            drawStrokes(decoded, strokes)
            val region = pixelsOf(crop.unturned(turns), decoded.width, decoded.height)
            val turn = Matrix().apply { postRotate(90f * turns) }
            Bitmap.createBitmap(decoded, region.left, region.top, region.width(), region.height(), turn, true).also { if (it !== decoded) decoded.recycle() }
        }
        val isPng = item.mimeType == "image/png"
        val output = uniqueFile(outputFolder(item), item.name, "crop", if (isPng) "png" else "jpg")
        output.outputStream().use { bitmap.compress(if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (isPng) 100 else JPEG_QUALITY, it) }
        bitmap.recycle()
        if (!isPng) copyExif(item, output)
        output.setLastModified(item.timestampMillis)
        output
    }

    // The frame nearest to `positionMs`, as a full-size JPEG beside the video.
    suspend fun saveFrame(item: MediaItem, positionMs: Long): File = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            if (item.uri.scheme == "content") retriever.setDataSource(context, item.uri) else retriever.setDataSource(item.uri.path)
            val bitmap = retriever.getFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST) ?: error("No frame at that time")
            val output = uniqueFile(outputFolder(item), item.name, "frame", "jpg")
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            bitmap.recycle()
            output.setLastModified(item.timestampMillis + positionMs)
            output
        } finally {
            retriever.release()
        }
    }

    // `endMs` is C.TIME_END_OF_SOURCE to keep the video to its end.
    @OptIn(UnstableApi::class)
    suspend fun editVideo(item: MediaItem, crop: RectF, startMs: Long, endMs: Long, onProgress: (Float) -> Unit, quarterTurns: Int = 0): File {
        val output = withContext(Dispatchers.IO) { uniqueFile(outputFolder(item), item.name, "edit", "mp4") }
        val clipping = PlayerMediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs)
            .apply { if (endMs != C.TIME_END_OF_SOURCE) setEndPositionMs(endMs) }
            .build()
        val media = PlayerMediaItem.Builder().setUri(item.uri).setClippingConfiguration(clipping).build()
        val turns = Math.floorMod(quarterTurns, 4)
        // Turned first, so the crop, given on the turned picture, cuts the frame as it was shown; the rotation counts anticlockwise, the crop from the centre, -1 to 1, with up being positive.
        val videoEffects = buildList<Effect> {
            if (turns != 0) add(ScaleAndRotateTransformation.Builder().setRotationDegrees(360f - 90f * turns).build())
            if (crop != RectF(0f, 0f, 1f, 1f)) add(Crop(crop.left * 2f - 1f, crop.right * 2f - 1f, 1f - crop.bottom * 2f, 1f - crop.top * 2f))
        }
        val effects = if (videoEffects.isEmpty()) Effects.EMPTY else Effects(emptyList(), videoEffects)
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

    private fun pixelsOf(crop: RectF, width: Int, height: Int) = Rect(
        (crop.left * width).roundToInt().coerceIn(0, width - 1),
        (crop.top * height).roundToInt().coerceIn(0, height - 1),
        (crop.right * width).roundToInt().coerceIn(1, width),
        (crop.bottom * height).roundToInt().coerceIn(1, height),
    )

    private fun drawStrokes(bitmap: Bitmap, strokes: List<DrawnStroke>) {
        if (strokes.isEmpty()) return
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        strokes.forEach { stroke ->
            paint.color = stroke.color
            paint.strokeWidth = stroke.width * width
            val first = stroke.points.firstOrNull() ?: return@forEach
            if (stroke.points.size == 1) {
                canvas.drawPoint(first.x * width, first.y * height, paint)
            } else {
                val path = Path().apply {
                    moveTo(first.x * width, first.y * height)
                    stroke.points.drop(1).forEach { lineTo(it.x * width, it.y * height) }
                }
                canvas.drawPath(path, paint)
            }
        }
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
