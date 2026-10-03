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

    fun init(context: Context) {
        preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        blurDp = preferences.getFloat("blur", DEFAULT_BLUR_DP)
        glassOpacity = preferences.getFloat("opacity", DEFAULT_OPACITY)
        defaultColumns = preferences.getInt("columns", DEFAULT_COLUMNS)
        showMonthHeaders = preferences.getBoolean("monthHeaders", true)
        autoplayVideos = preferences.getBoolean("autoplay", true)
        albumColumns = preferences.getInt("albumColumns", DEFAULT_ALBUM_COLUMNS)
        albumOrder = preferences.getString("albumOrder", null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
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

    fun updateAlbumOrder(paths: List<String>) {
        albumOrder = paths
        preferences.edit().putString("albumOrder", paths.joinToString("\n")).apply()
    }

    fun updateAutoplayVideos(value: Boolean) {
        autoplayVideos = value
        preferences.edit().putBoolean("autoplay", value).apply()
    }
}
