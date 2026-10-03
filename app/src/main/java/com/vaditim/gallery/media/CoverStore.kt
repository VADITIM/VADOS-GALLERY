package com.vaditim.gallery.media

import android.content.Context

// Which photo an album shows as its cover. Private groups keep theirs inside the vault instead, next to the photos.
class CoverStore(context: Context) {
    private val preferences = context.getSharedPreferences("covers", Context.MODE_PRIVATE)

    fun read(): Map<Long, Long> = preferences.all.mapNotNull { (key, value) ->
        val albumId = key.toLongOrNull() ?: return@mapNotNull null
        (value as? Long)?.let { albumId to it }
    }.toMap()

    fun set(albumId: Long, mediaId: Long) {
        preferences.edit().putLong(albumId.toString(), mediaId).apply()
    }
}
