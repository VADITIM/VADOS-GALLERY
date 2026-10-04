package com.vaditim.gallery

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// What the user can tune. Each value is Compose state, so changing one repaints whatever reads it at once; every change is written straight to preferences.
object Settings {
    const val MAX_BLUR_DP = 60f
    const val MIN_COLUMNS = 1
    const val MAX_COLUMNS = 5

    private const val DEFAULT_BLUR_DP = 26f
    private const val DEFAULT_OPACITY = 0.55f
    private const val DEFAULT_COLUMNS = 4
    const val MAX_ALBUM_COLUMNS = 4
    private const val DEFAULT_ALBUM_COLUMNS = 2

    private lateinit var preferences: SharedPreferences

    var blurDp by mutableFloatStateOf(DEFAULT_BLUR_DP)
        private set
    var glassOpacity by mutableFloatStateOf(DEFAULT_OPACITY)
        private set
    var defaultColumns by mutableIntStateOf(DEFAULT_COLUMNS)
        private set
    var showMonthHeaders by mutableStateOf(true)
        private set
    var autoplayVideos by mutableStateOf(true)
        private set
    var albumColumns by mutableIntStateOf(DEFAULT_ALBUM_COLUMNS)
        private set
    // Albums in the order the user arranged them, by folder path; albums not in it keep the default order after these.
    var albumOrder by mutableStateOf<List<String>>(emptyList())
        private set
    // The same for private groups, by name.
    var groupOrder by mutableStateOf<List<String>>(emptyList())
        private set
    var groupedAlbums by mutableStateOf(false)
        private set
    // The album groups in their order, each holding its albums by folder path; kept while grouping is off, so turning it back on restores them.
    var albumStacks by mutableStateOf<List<AlbumStack>>(emptyList())
        private set

    // Album folders kept out of Recent; they still open as albums.
    var hiddenFromRecent by mutableStateOf<Set<String>>(emptySet())
        private set

    fun init(context: Context) {
        preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        blurDp = preferences.getFloat("blur", DEFAULT_BLUR_DP)
        glassOpacity = preferences.getFloat("opacity", DEFAULT_OPACITY)
        defaultColumns = preferences.getInt("columns", DEFAULT_COLUMNS)
        showMonthHeaders = preferences.getBoolean("monthHeaders", true)
        autoplayVideos = preferences.getBoolean("autoplay", true)
        albumColumns = preferences.getInt("albumColumns", DEFAULT_ALBUM_COLUMNS)
        albumOrder = preferences.getString("albumOrder", null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
        groupOrder = preferences.getString("groupOrder", null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
        groupedAlbums = preferences.getBoolean("groupedAlbums", false)
        hiddenFromRecent = preferences.getString("hiddenFromRecent", null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty().toSet()
        albumStacks = preferences.getString("albumStacks", null)?.split('\n')?.filter { it.isNotEmpty() }?.map { line ->
            val parts = line.split('\t')
            AlbumStack(parts.first(), parts.drop(1).filter { it.isNotEmpty() })
        }.orEmpty()
    }

    fun updateBlur(value: Float) {
        blurDp = value.coerceIn(0f, MAX_BLUR_DP)
        preferences.edit().putFloat("blur", blurDp).apply()
    }

    fun updateGlassOpacity(value: Float) {
        glassOpacity = value.coerceIn(0f, 1f)
        preferences.edit().putFloat("opacity", glassOpacity).apply()
    }

    fun updateDefaultColumns(value: Int) {
        defaultColumns = value.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        preferences.edit().putInt("columns", defaultColumns).apply()
    }

    fun updateShowMonthHeaders(value: Boolean) {
        showMonthHeaders = value
        preferences.edit().putBoolean("monthHeaders", value).apply()
    }

    fun updateAlbumColumns(value: Int) {
        albumColumns = value.coerceIn(MIN_COLUMNS, MAX_ALBUM_COLUMNS)
        preferences.edit().putInt("albumColumns", albumColumns).apply()
    }

    fun updateGroupOrder(names: List<String>) {
        groupOrder = names
        preferences.edit().putString("groupOrder", names.joinToString("\n")).apply()
    }

    fun updateAlbumOrder(paths: List<String>) {
        albumOrder = paths
        preferences.edit().putString("albumOrder", paths.joinToString("\n")).apply()
    }

    fun updateHiddenFromRecent(paths: Set<String>) {
        hiddenFromRecent = paths
        preferences.edit().putString("hiddenFromRecent", paths.joinToString("\n")).apply()
    }

    fun updateGroupedAlbums(value: Boolean) {
        groupedAlbums = value
        preferences.edit().putBoolean("groupedAlbums", value).apply()
    }

    fun updateAlbumStacks(stacks: List<AlbumStack>) {
        albumStacks = stacks.filter { it.paths.isNotEmpty() }
        preferences.edit().putString("albumStacks", albumStacks.joinToString("\n") { (listOf(it.name) + it.paths).joinToString("\t") }).apply()
    }

    // An album renamed is a folder moved: its place in the order and in its group follow it.
    fun replaceAlbumPath(old: String, new: String) {
        if (old in hiddenFromRecent) updateHiddenFromRecent(hiddenFromRecent - old + new)
        if (old in albumOrder) updateAlbumOrder(albumOrder.map { if (it == old) new else it })
        if (albumStacks.any { old in it.paths }) updateAlbumStacks(albumStacks.map { stack -> stack.copy(paths = stack.paths.map { if (it == old) new else it }) })
    }

    fun updateAutoplayVideos(value: Boolean) {
        autoplayVideos = value
        preferences.edit().putBoolean("autoplay", value).apply()
    }
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
