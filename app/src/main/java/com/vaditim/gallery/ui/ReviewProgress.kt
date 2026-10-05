package com.vaditim.gallery.ui

import android.content.Context
import com.vaditim.gallery.media.MediaItem

// How far a folder's review has got, kept on disk so a long folder can be reviewed over many sittings: the oldest photo reached (by date, then id, the order review walks in) and the photos let go but not yet deleted.
class ReviewProgress(context: Context) {
    private val preferences = context.getSharedPreferences("review", Context.MODE_PRIVATE)

    data class Saved(val furthestMillis: Long, val furthestId: Long, val markedIds: Set<Long>)

    fun read(key: String): Saved? {
        val furthest = preferences.getString("$key.furthest", null)?.split(':') ?: return null
        val millis = furthest.getOrNull(0)?.toLongOrNull() ?: return null
        val id = furthest.getOrNull(1)?.toLongOrNull() ?: return null
        val marked = preferences.getString("$key.marked", null)?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty().toSet()
        return Saved(millis, id, marked)
    }

    fun save(key: String, furthest: MediaItem?, marked: Collection<MediaItem>) {
        preferences.edit()
            .apply { if (furthest != null) putString("$key.furthest", "${furthest.timestampMillis}:${furthest.id}") }
            .putString("$key.marked", marked.joinToString(",") { it.id.toString() })
            .apply()
    }

    fun clear(key: String) {
        preferences.edit().remove("$key.furthest").remove("$key.marked").apply()
    }

    // How many of `newestFirst` lie at or before the furthest point, which is where continuing picks up.
    fun reachedCount(saved: Saved?, newestFirst: List<MediaItem>): Int {
        saved ?: return 0
        val index = newestFirst.indexOfFirst { it.timestampMillis < saved.furthestMillis || (it.timestampMillis == saved.furthestMillis && it.id < saved.furthestId) }
        return if (index < 0) newestFirst.size else index
    }
}
