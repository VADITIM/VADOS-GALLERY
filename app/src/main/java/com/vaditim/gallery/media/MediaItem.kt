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
)

// An album is a folder, the way MediaStore buckets them. Items run oldest to newest, like every list in this app.
data class Album(
    val id: Long,
    val name: String,
    val relativePath: String,
    val items: List<MediaItem>,
) {
    val cover: MediaItem get() = items.last()
}

fun groupIntoAlbums(library: List<MediaItem>): List<Album> =
    library
        .groupBy { it.bucketId }
        .map { (bucketId, items) -> Album(bucketId, items.first().bucketName, items.first().relativePath, items) }
        .sortedWith(compareByDescending<Album> { it.relativePath.startsWith("DCIM/Camera") }.thenByDescending { it.cover.timestampMillis })
