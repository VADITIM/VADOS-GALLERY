package com.vaditim.gallery.media

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val mimeType: String,
    val name: String,
    val timestampMillis: Long,
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String,
    val isFavorite: Boolean,
    val durationMillis: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val absolutePath: String,
    // When Android empties a trashed item for good; 0 for anything not in the trash.
    val expiresMillis: Long = 0,
    // When it was moved to the trash; 0 for anything not in the trash.
    val trashedMillis: Long = 0,
    // The quarter turns MediaStore records for a photo, in degrees; `width` and `height` are already turned by it.
    val orientation: Int = 0,
)

// An album is a folder, the way MediaStore buckets them. Items run oldest to newest, like every list in this app.
data class Album(
    val id: Long,
    val name: String,
    val relativePath: String,
    val items: List<MediaItem>,
    val coverId: Long? = null,
    // The folder's own name on disk, which a renamed album keeps under its shown name.
    val folderName: String = name,
) {
    // The chosen cover while it is still in the album, else the newest photo.
    val cover: MediaItem get() = items.firstOrNull { it.id == coverId } ?: items.last()
}

fun groupIntoAlbums(library: List<MediaItem>, coverIds: Map<Long, Long> = emptyMap()): List<Album> =
    library
        .groupBy { it.bucketId }
        .map { (bucketId, items) -> Album(bucketId, items.first().bucketName, items.first().relativePath, items, coverIds[bucketId], folderName = items.first().bucketName) }
        .sortedWith(compareByDescending<Album> { it.relativePath.startsWith("DCIM/Camera") }.thenByDescending { it.items.last().timestampMillis })

// Where a new album lives: a folder under Pictures, which MediaStore accepts both photos and videos into.
fun newAlbumPath(name: String): String = "Pictures/${name.trim().replace('/', ' ').trimStart('.').ifBlank { "Album" }}/"
