package com.vaditim.gallery

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// What the user can tune. Each value is Compose state, so changing one repaints whatever reads it at once; every change is written straight to preferences.
object Settings {
    const val MAX_BLUR_DP = 60f
    const val MIN_COLUMNS = 1
    const val MAX_COLUMNS = 6

    private const val DEFAULT_BLUR_DP = 50f
    private const val DEFAULT_OPACITY = 0.85f
    private const val DEFAULT_COLUMNS = 3
    // Recent is the whole library, so it starts denser than a folder.
    private const val DEFAULT_RECENT_COLUMNS = 5
    const val MAX_ALBUM_COLUMNS = 4
    private const val DEFAULT_ALBUM_COLUMNS = 3
    // The ground's grey, as a share of the lightest it may go.
    const val MAX_GROUND_LEVEL = 64f
    private const val DEFAULT_GROUND_BRIGHTNESS = 17.5f / MAX_GROUND_LEVEL
    private val DEFAULT_DATE_GROUPS = setOf(DateGroup.DAYS, DateGroup.MONTHS, DateGroup.YEARS)

    private lateinit var preferences: SharedPreferences

    var blurDp by mutableFloatStateOf(DEFAULT_BLUR_DP)
        private set
    var glassOpacity by mutableFloatStateOf(DEFAULT_OPACITY)
        private set
    // The view on screen, whose own settings the sheet shows and changes; every view keeps its grid and album settings apart.
    var view by mutableStateOf(SettingsView.RECENT)
    private val columnsByView = mutableStateMapOf<SettingsView, Int>()
    private val dateGroupsByView = mutableStateMapOf<SettingsView, Set<DateGroup>>()
    private val headersByView = mutableStateMapOf<SettingsView, Boolean>()
    private val stackSimilarByView = mutableStateMapOf<SettingsView, Boolean>()
    private val groupedAlbumsByView = mutableStateMapOf<SettingsView, Boolean>()
    private val albumColumnsByView = mutableStateMapOf<SettingsView, Int>()

    fun columnsIn(view: SettingsView): Int = columnsByView[view] ?: defaultColumnsIn(view)
    private fun defaultColumnsIn(view: SettingsView): Int = if (view == SettingsView.RECENT) DEFAULT_RECENT_COLUMNS else DEFAULT_COLUMNS
    // Empty is the layout without any cut: one run of photos.
    fun dateGroupsIn(view: SettingsView): Set<DateGroup> = dateGroupsByView[view] ?: DEFAULT_DATE_GROUPS
    // Off, the grid has no cuts at all; the layout picked stays stored for when they come back on.
    fun headersIn(view: SettingsView): Boolean = headersByView[view] ?: true
    fun activeDateGroupsIn(view: SettingsView): Set<DateGroup> = if (headersIn(view)) dateGroupsIn(view) else emptySet()
    fun stackSimilarIn(view: SettingsView): Boolean = stackSimilarByView[view] ?: false
    fun groupedAlbumsIn(view: SettingsView): Boolean = groupedAlbumsByView[view] ?: (view == SettingsView.FAVORITES)
    fun albumColumnsIn(view: SettingsView): Int = albumColumnsByView[view] ?: DEFAULT_ALBUM_COLUMNS

    val defaultColumns: Int get() = columnsIn(view)
    val dateGroups: Set<DateGroup> get() = dateGroupsIn(view)
    val headers: Boolean get() = headersIn(view)
    var autoplayVideos by mutableStateOf(true)
        private set
    // The day pill at the top left of a day's first photo, everywhere at once.
    var dayStamps by mutableStateOf(false)
        private set
    // The open folder's name above the nav; off, it takes the month's place in the top pill.
    var folderLabel by mutableStateOf(false)
        private set
    // The random private favourite at the top of Private's groups.
    var todaysSelection by mutableStateOf(true)
        private set
    val albumColumns: Int get() = albumColumnsIn(view)
    // Albums in the order the user arranged them, by folder path; albums not in it keep the default order after these.
    var albumOrder by mutableStateOf<List<String>>(emptyList())
        private set
    // The same for private groups, by name.
    var groupOrder by mutableStateOf<List<String>>(emptyList())
        private set
    val groupedAlbums: Boolean get() = groupedAlbumsIn(view)
    // The album groups in their order, each holding its albums by folder path; kept while grouping is off, so turning it back on restores them.
    var albumStacks by mutableStateOf<List<AlbumStack>>(emptyList())
        private set

    val stackSimilar: Boolean get() = stackSimilarIn(view)

    var groundBrightness by mutableFloatStateOf(DEFAULT_GROUND_BRIGHTNESS)
        private set

    // Albums made inside Favorites only, each holding favourited photos by id; they never touch the folders.
    var favoriteAlbums by mutableStateOf<List<FavoriteAlbum>>(emptyList())
        private set
    // Groups of those albums, by album name, and the albums' arranged order.
    var favoriteStacks by mutableStateOf<List<AlbumStack>>(emptyList())
        private set
    var favoriteAlbumOrder by mutableStateOf<List<String>>(emptyList())
        private set
    var favoritesAsAlbums by mutableStateOf(false)
    // Private's Favorites keeps its own choice between one grid and its groups, apart from the Favorites outside.
    var privateFavoritesAsGroups by mutableStateOf(false)
        private set

    // An album's name as shown, by folder path; renaming only ever changes this, never the folder, so nothing that saves into it is thrown off.
    var albumNames by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    fun init(context: Context) {
        preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        blurDp = preferences.getFloat("blur", DEFAULT_BLUR_DP)
        glassOpacity = preferences.getFloat("opacity", DEFAULT_OPACITY)
        // Each view starts from what the single setting was before views kept their own.
        for (each in SettingsView.entries) {
            columnsByView[each] = preferences.getInt("columns.${each.name}", preferences.getInt("columns", defaultColumnsIn(each)))
            // Before the groups could be combined, a view had months (with or without their headers) or weeks inside months.
            val stored = preferences.getString("dateGroups.${each.name}", null)
            val hasLegacyLayout = listOf("monthHeaders.${each.name}", "monthHeaders", "photoLayout.${each.name}", "photoLayout").any { preferences.contains(it) }
            dateGroupsByView[each] = if (stored != null) {
                stored.split(',').mapNotNull { name -> DateGroup.entries.firstOrNull { it.name == name } }.toSet()
            } else if (!hasLegacyLayout) {
                DEFAULT_DATE_GROUPS
            } else {
                val hasMonths = preferences.getBoolean("monthHeaders.${each.name}", preferences.getBoolean("monthHeaders", true))
                val hasWeeks = (preferences.getString("photoLayout.${each.name}", null) ?: preferences.getString("photoLayout", null)) == "WEEKS"
                setOfNotNull(DateGroup.MONTHS.takeIf { hasMonths }, DateGroup.WEEKS.takeIf { hasWeeks })
            }
            headersByView[each] = preferences.getBoolean("headers.${each.name}", true)
            stackSimilarByView[each] = preferences.getBoolean("stackSimilar.${each.name}", preferences.getBoolean("stackSimilar", false))
            // Favorites always showed its groups before it had the setting, so it starts with them on.
            groupedAlbumsByView[each] = preferences.getBoolean("groupedAlbums.${each.name}", if (each == SettingsView.FAVORITES) true else preferences.getBoolean("groupedAlbums", false))
            albumColumnsByView[each] = preferences.getInt("albumColumns.${each.name}", preferences.getInt("albumColumns", DEFAULT_ALBUM_COLUMNS))
        }
        autoplayVideos = preferences.getBoolean("autoplay", true)
        dayStamps = preferences.getBoolean("dayStamps", false)
        folderLabel = preferences.getBoolean("folderLabel", false)
        todaysSelection = preferences.getBoolean("todaysSelection", true)
        albumOrder = preferences.getString("albumOrder", null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
        groupOrder = preferences.getString("groupOrder", null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
        albumNames = preferences.getString("albumNames", null)?.split('\n')?.mapNotNull { line -> line.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.orEmpty().toMap()
        groundBrightness = preferences.getFloat("groundBrightness", DEFAULT_GROUND_BRIGHTNESS)
        favoriteAlbums = preferences.getString("favoriteAlbums", null)?.split('\n')?.filter { it.isNotEmpty() }?.map { line ->
            val parts = line.split('\t')
            FavoriteAlbum(parts.first(), parts.drop(1).mapNotNull { it.toLongOrNull() })
        }.orEmpty()
        favoriteStacks = readStacks("favoriteStacks")
        favoriteAlbumOrder = preferences.getString("favoriteAlbumOrder", null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
        favoritesAsAlbums = preferences.getBoolean("favoritesAsAlbums", false)
        privateFavoritesAsGroups = preferences.getBoolean("privateFavoritesAsGroups", false)
        albumStacks = preferences.getString("albumStacks", null)?.split('\n')?.filter { it.isNotEmpty() }?.map { line ->
            val parts = line.split('\t')
            AlbumStack(parts.first(), parts.drop(1).filter { it.isNotEmpty() })
        }.orEmpty()
    }

    private fun readStacks(key: String): List<AlbumStack> =
        preferences.getString(key, null)?.split('\n')?.filter { it.isNotEmpty() }?.map { line ->
            val parts = line.split('\t')
            AlbumStack(parts.first(), parts.drop(1).filter { it.isNotEmpty() })
        }.orEmpty()

    // With Grouped albums on, the albums lie as rows beside the groups, so the column count only applies with it off.
    val coverColumns: Int get() = coverColumnsIn(view)
    fun coverColumnsIn(view: SettingsView): Int = if (groupedAlbumsIn(view) && view.canGroup) 1 else albumColumnsIn(view)

    fun updateGroundBrightness(value: Float) {
        groundBrightness = value.coerceIn(0f, 1f)
        preferences.edit().putFloat("groundBrightness", groundBrightness).apply()
    }

    fun updateFavoriteAlbums(albums: List<FavoriteAlbum>) {
        favoriteAlbums = albums.filter { it.ids.isNotEmpty() }
        preferences.edit().putString("favoriteAlbums", favoriteAlbums.joinToString("\n") { (listOf(it.name) + it.ids.map(Long::toString)).joinToString("\t") }).apply()
        // A group keeps only albums that still exist.
        val names = favoriteAlbums.map { it.name }.toSet()
        if (favoriteStacks.any { stack -> stack.paths.any { it !in names } }) updateFavoriteStacks(favoriteStacks.map { stack -> stack.copy(paths = stack.paths.filter { it in names }) })
    }

    fun updateFavoriteStacks(stacks: List<AlbumStack>) {
        favoriteStacks = stacks.filter { it.paths.isNotEmpty() }
        preferences.edit().putString("favoriteStacks", favoriteStacks.joinToString("\n") { (listOf(it.name) + it.paths).joinToString("\t") }).apply()
    }

    fun updateFavoriteAlbumOrder(names: List<String>) {
        favoriteAlbumOrder = names
        preferences.edit().putString("favoriteAlbumOrder", names.joinToString("\n")).apply()
    }

    fun updateFavoritesAsAlbums(value: Boolean) {
        favoritesAsAlbums = value
        preferences.edit().putBoolean("favoritesAsAlbums", value).apply()
    }

    fun updatePrivateFavoritesAsGroups(value: Boolean) {
        privateFavoritesAsGroups = value
        preferences.edit().putBoolean("privateFavoritesAsGroups", value).apply()
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
        columnsByView[view] = value.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        preferences.edit().putInt("columns.${view.name}", defaultColumns).apply()
    }

    fun updateDateGroups(value: Set<DateGroup>) {
        dateGroupsByView[view] = value
        preferences.edit().putString("dateGroups.${view.name}", value.joinToString(",") { it.name }).apply()
    }

    fun updateHeaders(value: Boolean) {
        headersByView[view] = value
        preferences.edit().putBoolean("headers.${view.name}", value).apply()
    }

    fun updateAlbumColumns(value: Int) {
        albumColumnsByView[view] = value.coerceIn(MIN_COLUMNS, MAX_ALBUM_COLUMNS)
        preferences.edit().putInt("albumColumns.${view.name}", albumColumns).apply()
    }

    fun updateGroupOrder(names: List<String>) {
        groupOrder = names
        preferences.edit().putString("groupOrder", names.joinToString("\n")).apply()
    }

    fun updateAlbumOrder(paths: List<String>) {
        albumOrder = paths
        preferences.edit().putString("albumOrder", paths.joinToString("\n")).apply()
    }

    fun updateStackSimilar(value: Boolean) {
        stackSimilarByView[view] = value
        preferences.edit().putBoolean("stackSimilar.${view.name}", value).apply()
    }

    fun updateGroupedAlbums(value: Boolean) {
        groupedAlbumsByView[view] = value
        preferences.edit().putBoolean("groupedAlbums.${view.name}", value).apply()
    }

    fun updateAlbumStacks(stacks: List<AlbumStack>) {
        albumStacks = stacks.filter { it.paths.isNotEmpty() }
        preferences.edit().putString("albumStacks", albumStacks.joinToString("\n") { (listOf(it.name) + it.paths).joinToString("\t") }).apply()
    }

    // A name equal to the folder's own clears the override, so the album follows the folder again.
    fun updateAlbumName(path: String, folderName: String, name: String) {
        val clean = AlbumStack.cleanName(name).ifEmpty { return }
        albumNames = if (clean == folderName) albumNames - path else albumNames + (path to clean)
        preferences.edit().putString("albumNames", albumNames.entries.joinToString("\n") { "${it.key}\t${it.value}" }).apply()
    }

    fun updateDayStamps(value: Boolean) {
        dayStamps = value
        preferences.edit().putBoolean("dayStamps", value).apply()
    }

    fun updateFolderLabel(value: Boolean) {
        folderLabel = value
        preferences.edit().putBoolean("folderLabel", value).apply()
    }

    fun updateTodaysSelection(value: Boolean) {
        todaysSelection = value
        preferences.edit().putBoolean("todaysSelection", value).apply()
    }

    fun updateAutoplayVideos(value: Boolean) {
        autoplayVideos = value
        preferences.edit().putBoolean("autoplay", value).apply()
    }
}

// The views that keep settings of their own, named as the settings sheet shows which one it is changing.
enum class SettingsView(val label: String) {
    RECENT("Recent"), ALBUMS("Albums"), FAVORITES("Favorites"), LOCATIONS("Locations"), TRASH("Trash"), PRIVATE("Private");

    // Only Albums and Favorites gather their albums into groups.
    val canGroup: Boolean get() = this == ALBUMS || this == FAVORITES
}

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

// What a photo grid is cut into; any of them at once, each under its own header. Without days or weeks the first photo of each day carries the day.
enum class DateGroup(val label: String) { DAYS("Days"), WEEKS("Weeks"), MONTHS("Months"), YEARS("Years") }
