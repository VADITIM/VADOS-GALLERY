package com.vaditim.gallery.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.BaseColumns
import android.provider.MediaStore
import com.vaditim.gallery.vault.PrivateVault
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MediaRepository(private val context: Context) {

    private val resolver: ContentResolver = context.contentResolver

    // Re-queried whole on every change. A library of tens of thousands of rows is a few megabytes of metadata and a query well under a second, which is cheaper than keeping a diff correct; revisit only if a real library says otherwise.
    fun observeLibrary(): Flow<List<MediaItem>> = observe(isTrashed = false)

    // What is in the system trash: Android keeps trashed photos for 30 days, hidden from every normal query.
    fun observeTrash(): Flow<List<MediaItem>> = observe(isTrashed = true)

    private fun observe(isTrashed: Boolean): Flow<List<MediaItem>> =
        callbackFlow {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }
            }
            resolver.registerContentObserver(FILES, true, observer)
            trySend(Unit)
            awaitClose { resolver.unregisterContentObserver(observer) }
        }
            .conflate()
            .map { query(isTrashed) }
            .flowOn(Dispatchers.IO)

    // Moving is a change of RELATIVE_PATH; MediaProvider moves the file on disk itself. The caller must already hold write access to the item (All files access, or a granted write request).
    // If MediaProvider refuses (some folder pairs it will not move between), the file is renamed directly — this app has All files access — and both paths are rescanned so the library follows.
    suspend fun moveTo(item: MediaItem, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath) }
        val isUpdated = runCatching { resolver.update(item.uri, values, null, null) > 0 }.getOrDefault(false)
        if (isUpdated) return@withContext true
        val source = File(item.absolutePath)
        val directory = File(Environment.getExternalStorageDirectory(), relativePath).apply { mkdirs() }
        val target = File(directory, source.name)
        if (target.exists() || !source.renameTo(target)) return@withContext false
        MediaScannerConnection.scanFile(context, arrayOf(source.absolutePath, target.absolutePath), null, null)
        true
    }

    // Android empties expired trash only during its idle maintenance, which can lag days behind; this app removes what is past its date itself. All files access lets it delete the rows directly.
    suspend fun deleteExpired(trash: List<MediaItem>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        trash.filter { it.expiresMillis in 1 until now }.forEach { item ->
            val isDeleted = runCatching { resolver.delete(item.uri, null, null) > 0 }.getOrDefault(false)
            if (!isDeleted && item.absolutePath.isNotEmpty() && File(item.absolutePath).delete()) {
                MediaScannerConnection.scanFile(context, arrayOf(item.absolutePath), null, null)
            }
        }
    }

    private fun query(isTrashed: Boolean): List<MediaItem> {
        val projection = arrayOf(
            BaseColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.IS_FAVORITE,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.ORIENTATION,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_EXPIRES,
        )
        // The private folder is excluded by path as well as by its .nomedia marker: if a stale row ever survives a move into Private, it still never reaches Recent or Favorites.
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?) AND ${MediaStore.MediaColumns.DATA} NOT LIKE ?"
        val arguments = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            "%/${PrivateVault.ROOT.name}/%",
        )
        val items = ArrayList<MediaItem>()
        val queryArguments = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arguments)
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, if (isTrashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE)
        }
        resolver.query(FILES, projection, queryArguments, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(BaseColumns._ID)
            val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val favoriteColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val orientationColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            // DATA is deprecated for apps without file access; this app has All files access, and hiding a photo into Private is a plain file move that needs the real path.
            val pathOnDiskColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val expiresColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_EXPIRES)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val isVideo = cursor.getInt(typeColumn) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val taken = cursor.getLong(takenColumn)
                // Stored sizes are as the sensor wrote them; a photo turned a quarter is taller than wide, and the viewer frame must know before it decodes.
                val isTurned = cursor.getInt(orientationColumn) % 180 == 90
                items += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(if (isVideo) VIDEOS else IMAGES, id),
                    isVideo = isVideo,
                    mimeType = cursor.getString(mimeColumn) ?: if (isVideo) "video/*" else "image/*",
                    name = cursor.getString(nameColumn) ?: "",
                    // Screenshots and downloads often carry no DATE_TAKEN; the file's own time is the honest fallback, DATE_ADDED is when the scanner noticed it.
                    timestampMillis = if (taken > 0) taken else cursor.getLong(modifiedColumn) * 1000,
                    bucketId = cursor.getLong(bucketIdColumn),
                    bucketName = cursor.getString(bucketNameColumn) ?: "",
                    relativePath = cursor.getString(pathColumn) ?: "",
                    isFavorite = cursor.getInt(favoriteColumn) == 1,
                    durationMillis = cursor.getLong(durationColumn),
                    width = cursor.getInt(if (isTurned) heightColumn else widthColumn),
                    height = cursor.getInt(if (isTurned) widthColumn else heightColumn),
                    sizeBytes = cursor.getLong(sizeColumn),
                    absolutePath = cursor.getString(pathOnDiskColumn) ?: "",
                    expiresMillis = if (isTrashed) cursor.getLong(expiresColumn) * 1000 else 0,
                )
            }
        }
        items.sortWith(compareBy<MediaItem> { it.timestampMillis }.thenBy { it.id })
        return items
    }

    private companion object {
        val FILES: Uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val IMAGES: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val VIDEOS: Uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    }
}
