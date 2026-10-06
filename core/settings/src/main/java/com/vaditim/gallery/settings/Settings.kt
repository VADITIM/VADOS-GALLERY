package com.vaditim.gallery.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// What the user can tune about how things look. Each value is Compose state, so changing one repaints whatever reads it at once; every change is written straight to preferences.
object Settings {
    const val MAX_BLUR_DP = 60f
    const val MIN_COLUMNS = 1
    const val MAX_COLUMNS = 6
    const val MAX_ALBUM_COLUMNS = 4
    // The ground's grey, as a share of the lightest it may go.
    const val MAX_GROUND_LEVEL = 64f

    private const val DEFAULT_BLUR_DP = 50f
    private const val DEFAULT_OPACITY = 0.85f
    private const val DEFAULT_COLUMNS = 3
    // Recent is the whole library, so it starts denser than a folder.
    private const val DEFAULT_RECENT_COLUMNS = 5
    private const val DEFAULT_ALBUM_COLUMNS = 3
    private const val DEFAULT_GROUND_BRIGHTNESS = 17.5f / MAX_GROUND_LEVEL
    private val DEFAULT_DATE_GROUPS = setOf(DateGroup.DAYS, DateGroup.MONTHS, DateGroup.YEARS)

    private lateinit var store: PreferenceStore

    // The view on screen, whose own settings the sheet shows and changes; every view keeps its grid and album settings apart.
    var view by mutableStateOf(SettingsView.RECENT)

    // Each view starts from what the single setting was before views kept their own.
    private val columns = PerViewSetting(
        "columns",
        default = ::defaultColumnsIn,
        read = { view, key -> getInt(key, getInt("columns", defaultColumnsIn(view))) },
        write = { key, value -> putInt(key, value) },
    )
    private val dateGroups = PerViewSetting(
        "dateGroups",
        default = { DEFAULT_DATE_GROUPS },
        read = { view, key -> readDateGroups(view, key) },
        write = { key, value -> putString(key, value.joinToString(",") { it.name }) },
    )
    // Off, the grid has no cuts at all; the layout picked stays stored for when they come back on.
    private val headers = PerViewSetting(
        "headers",
        default = { true },
        read = { _, key -> getBoolean(key, true) },
        write = { key, value -> putBoolean(key, value) },
    )
    private val stackSimilar = PerViewSetting(
        "stackSimilar",
        default = { false },
        read = { _, key -> getBoolean(key, getBoolean("stackSimilar", false)) },
        write = { key, value -> putBoolean(key, value) },
    )
    // Favorites always showed its groups before it had the setting, so it starts with them on.
    private val groupedAlbums = PerViewSetting(
        "groupedAlbums",
        default = { it == SettingsView.FAVORITES },
        read = { view, key -> getBoolean(key, if (view == SettingsView.FAVORITES) true else getBoolean("groupedAlbums", false)) },
        write = { key, value -> putBoolean(key, value) },
    )
    private val albumColumns = PerViewSetting(
        "albumColumns",
        default = { DEFAULT_ALBUM_COLUMNS },
        read = { _, key -> getInt(key, getInt("albumColumns", DEFAULT_ALBUM_COLUMNS)) },
        write = { key, value -> putInt(key, value) },
    )

    var blurDp by mutableFloatStateOf(DEFAULT_BLUR_DP)
        private set
    var glassOpacity by mutableFloatStateOf(DEFAULT_OPACITY)
        private set
    var groundBrightness by mutableFloatStateOf(DEFAULT_GROUND_BRIGHTNESS)
        private set
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
    var favoritesAsAlbums by mutableStateOf(false)
        private set
    // Private's Favorites keeps its own choice between one grid and its groups, apart from the Favorites outside.
    var privateFavoritesAsGroups by mutableStateOf(false)
        private set

    fun init(store: PreferenceStore) {
        this.store = store
        val preferences = store.preferences
        listOf(columns, dateGroups, headers, stackSimilar, groupedAlbums, albumColumns).forEach { it.load(store) }
        blurDp = preferences.getFloat("blur", DEFAULT_BLUR_DP)
        glassOpacity = preferences.getFloat("opacity", DEFAULT_OPACITY)
        groundBrightness = preferences.getFloat("groundBrightness", DEFAULT_GROUND_BRIGHTNESS)
        autoplayVideos = preferences.getBoolean("autoplay", true)
        dayStamps = preferences.getBoolean("dayStamps", false)
        folderLabel = preferences.getBoolean("folderLabel", false)
        todaysSelection = preferences.getBoolean("todaysSelection", true)
        favoritesAsAlbums = preferences.getBoolean("favoritesAsAlbums", false)
        privateFavoritesAsGroups = preferences.getBoolean("privateFavoritesAsGroups", false)
    }

    // Before the groups could be combined, a view had months (with or without their headers) or weeks inside months.
    private fun android.content.SharedPreferences.readDateGroups(view: SettingsView, key: String): Set<DateGroup> {
        val stored = getString(key, null)
        val hasLegacyLayout = listOf("monthHeaders.${view.name}", "monthHeaders", "photoLayout.${view.name}", "photoLayout").any { contains(it) }
        return when {
            stored != null -> stored.split(',').mapNotNull { name -> DateGroup.entries.firstOrNull { it.name == name } }.toSet()
            !hasLegacyLayout -> DEFAULT_DATE_GROUPS
            else -> {
                val hasMonths = getBoolean("monthHeaders.${view.name}", getBoolean("monthHeaders", true))
                val hasWeeks = (getString("photoLayout.${view.name}", null) ?: getString("photoLayout", null)) == "WEEKS"
                setOfNotNull(DateGroup.MONTHS.takeIf { hasMonths }, DateGroup.WEEKS.takeIf { hasWeeks })
            }
        }
    }

    private fun defaultColumnsIn(view: SettingsView): Int = if (view == SettingsView.RECENT) DEFAULT_RECENT_COLUMNS else DEFAULT_COLUMNS

    fun columnsIn(view: SettingsView): Int = columns[view]
    // Empty is the layout without any cut: one run of photos.
    fun dateGroupsIn(view: SettingsView): Set<DateGroup> = dateGroups[view]
    fun headersIn(view: SettingsView): Boolean = headers[view]
    fun activeDateGroupsIn(view: SettingsView): Set<DateGroup> = if (headersIn(view)) dateGroupsIn(view) else emptySet()
    fun stackSimilarIn(view: SettingsView): Boolean = stackSimilar[view]
    fun groupedAlbumsIn(view: SettingsView): Boolean = groupedAlbums[view]
    fun albumColumnsIn(view: SettingsView): Int = albumColumns[view]
    // With Grouped albums on, the albums lie as rows beside the groups, so the column count only applies with it off.
    fun coverColumnsIn(view: SettingsView): Int = if (groupedAlbumsIn(view) && view.canGroup) 1 else albumColumnsIn(view)

    val defaultColumns: Int get() = columnsIn(view)
    val dateGroupsInView: Set<DateGroup> get() = dateGroupsIn(view)
    val headersInView: Boolean get() = headersIn(view)
    val stackSimilarInView: Boolean get() = stackSimilarIn(view)
    val groupedAlbumsInView: Boolean get() = groupedAlbumsIn(view)
    val albumColumnsInView: Int get() = albumColumnsIn(view)
    val coverColumns: Int get() = coverColumnsIn(view)

    fun updateDefaultColumns(value: Int) = columns.set(store, view, value.coerceIn(MIN_COLUMNS, MAX_COLUMNS))
    fun updateDateGroups(value: Set<DateGroup>) = dateGroups.set(store, view, value)
    fun updateHeaders(value: Boolean) = headers.set(store, view, value)
    fun updateStackSimilar(value: Boolean) = stackSimilar.set(store, view, value)
    fun updateGroupedAlbums(value: Boolean) = groupedAlbums.set(store, view, value)
    fun updateAlbumColumns(value: Int) = albumColumns.set(store, view, value.coerceIn(MIN_COLUMNS, MAX_ALBUM_COLUMNS))

    fun updateBlur(value: Float) {
        blurDp = value.coerceIn(0f, MAX_BLUR_DP)
        store.edit { putFloat("blur", blurDp) }
    }

    fun updateGlassOpacity(value: Float) {
        glassOpacity = value.coerceIn(0f, 1f)
        store.edit { putFloat("opacity", glassOpacity) }
    }

    fun updateGroundBrightness(value: Float) {
        groundBrightness = value.coerceIn(0f, 1f)
        store.edit { putFloat("groundBrightness", groundBrightness) }
    }

    fun updateAutoplayVideos(value: Boolean) {
        autoplayVideos = value
        store.edit { putBoolean("autoplay", value) }
    }

    fun updateDayStamps(value: Boolean) {
        dayStamps = value
        store.edit { putBoolean("dayStamps", value) }
    }

    fun updateFolderLabel(value: Boolean) {
        folderLabel = value
        store.edit { putBoolean("folderLabel", value) }
    }

    fun updateTodaysSelection(value: Boolean) {
        todaysSelection = value
        store.edit { putBoolean("todaysSelection", value) }
    }

    fun updateFavoritesAsAlbums(value: Boolean) {
        favoritesAsAlbums = value
        store.edit { putBoolean("favoritesAsAlbums", value) }
    }

    fun updatePrivateFavoritesAsGroups(value: Boolean) {
        privateFavoritesAsGroups = value
        store.edit { putBoolean("privateFavoritesAsGroups", value) }
    }
}
