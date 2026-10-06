package com.vaditim.gallery.settings

// The views that keep settings of their own, named as the settings sheet shows which one it is changing.
enum class SettingsView(val label: String) {
    RECENT("Recent"), ALBUMS("Albums"), FAVORITES("Favorites"), LOCATIONS("Locations"), TRASH("Trash"), PRIVATE("Private");

    // Only Albums and Favorites gather their albums into groups.
    val canGroup: Boolean get() = this == ALBUMS || this == FAVORITES
}

// What a photo grid is cut into; any of them at once, each under its own header. Without days or weeks the first photo of each day carries the day.
enum class DateGroup(val label: String) { DAYS("Days"), WEEKS("Weeks"), MONTHS("Months"), YEARS("Years") }

data class FavoriteAlbum(val name: String, val ids: List<Long>)

// Photos join a Favorites album, or one made for them; an album renamed onto another's name joins it.
fun List<FavoriteAlbum>.withPhotos(albumName: String, ids: List<Long>): List<FavoriteAlbum> {
    val name = AlbumStack.cleanName(albumName).ifEmpty { return this }
    // A photo lives in one Favorites album at a time, so adding it elsewhere moves it.
    val others = map { if (it.name == name) it else it.copy(ids = it.ids - ids.toSet()) }
    return if (any { it.name == name }) others.map { if (it.name == name) it.copy(ids = (it.ids + ids).distinct()) else it } else others + FavoriteAlbum(name, ids.distinct())
}

fun List<FavoriteAlbum>.renamedAlbum(old: String, new: String): List<FavoriteAlbum> {
    val name = AlbumStack.cleanName(new).ifEmpty { return this }
    val merged = LinkedHashMap<String, List<Long>>()
    for (album in this) {
        val target = if (album.name == old) name else album.name
        merged[target] = merged[target].orEmpty() + album.ids
    }
    return merged.map { (albumName, ids) -> FavoriteAlbum(albumName, ids.distinct()) }
}

data class AlbumStack(val name: String, val paths: List<String>) {
    // Tabs and line breaks hold the stored list together, so a name cannot carry them.
    companion object {
        fun cleanName(name: String): String = name.replace('\t', ' ').replace('\n', ' ').trim()
    }
}

// Putting an album in a group takes it out of any other; a group left empty is gone.
fun List<AlbumStack>.withAlbum(path: String, stackName: String): List<AlbumStack> {
    val name = AlbumStack.cleanName(stackName).ifEmpty { return this }
    val without = withoutAlbum(path)
    return if (without.any { it.name == name }) without.map { if (it.name == name) it.copy(paths = it.paths + path) else it } else without + AlbumStack(name, listOf(path))
}

// A group renamed onto another's name joins it, in the place of whichever came first.
fun List<AlbumStack>.renamed(old: String, new: String): List<AlbumStack> {
    val name = AlbumStack.cleanName(new).ifEmpty { return this }
    val merged = LinkedHashMap<String, List<String>>()
    for (stack in this) {
        val target = if (stack.name == old) name else stack.name
        merged[target] = merged[target].orEmpty() + stack.paths
    }
    return merged.map { (stackName, paths) -> AlbumStack(stackName, paths.distinct()) }
}

fun List<AlbumStack>.withoutAlbum(path: String): List<AlbumStack> =
    map { it.copy(paths = it.paths - path) }.filter { it.paths.isNotEmpty() }
