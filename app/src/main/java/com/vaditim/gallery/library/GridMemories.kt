package com.vaditim.gallery.library

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.vaditim.gallery.components.GridMemory
import com.vaditim.gallery.settings.Settings
import com.vaditim.gallery.settings.SettingsView

// Every grid and list the user can come back to keeps its scroll and columns here, above the screens that come and go.
@Stable
class GridMemories(
    val albumsList: LazyGridState,
    val privateGroupsList: LazyGridState,
    val locationsList: LazyGridState,
    val favoriteAlbumsList: LazyGridState,
    val privateFavoriteGroupsList: LazyGridState,
) {
    val recent = GridMemory(SettingsView.RECENT)
    val favorites = GridMemory(SettingsView.FAVORITES)
    val privateFavorites = GridMemory(SettingsView.PRIVATE)
    val privateRecent = GridMemory(SettingsView.PRIVATE)
    // The trash keeps every shot on its own, since each one there is about to go.
    val trash = GridMemory(SettingsView.TRASH, isStacking = false, isTrash = true)
    val privateTrash = GridMemory(SettingsView.PRIVATE, isStacking = false, isTrash = true)
    private val albums = mutableMapOf<Long, GridMemory>()
    private val locations = mutableMapOf<String, GridMemory>()
    private val privateGroups = mutableMapOf<String, GridMemory>()
    private val favoriteAlbums = mutableMapOf<String, GridMemory>()
    private val privateFavoriteGroups = mutableMapOf<String, GridMemory>()

    fun album(id: Long) = albums.getOrPut(id) { GridMemory(SettingsView.ALBUMS, folder = ViewerSource.InAlbum(id).folderKey) }
    fun location(key: String) = locations.getOrPut(key) { GridMemory(SettingsView.LOCATIONS) }
    fun privateGroup(name: String) = privateGroups.getOrPut(name) { GridMemory(SettingsView.PRIVATE, folder = ViewerSource.InPrivateGroup(name).folderKey) }
    fun favoriteAlbum(name: String) = favoriteAlbums.getOrPut(name) { GridMemory(SettingsView.FAVORITES, folder = ViewerSource.InFavoriteAlbum(name).folderKey) }
    fun privateFavoriteGroup(name: String) = privateFavoriteGroups.getOrPut(name) { GridMemory(SettingsView.PRIVATE) }

    // Only the grids whose setting changed take the new count: the open album's alone, or the view's, and with Recent's every album that follows it.
    fun setColumns(view: SettingsView, folder: String?, columns: Int) {
        (listOf(recent, favorites, privateFavorites, privateRecent, trash, privateTrash) + albums.values + privateGroups.values + favoriteAlbums.values + privateFavoriteGroups.values + locations.values)
            .filter { memory ->
                when {
                    folder != null -> memory.folder == folder
                    memory.folder == null -> memory.view == view
                    else -> view == SettingsView.RECENT && !Settings.hasOwnSettings(memory.folder)
                }
            }
            .forEach { it.columns = columns }
    }
}

@Composable
fun rememberGridMemories(): GridMemories {
    val albumsList = rememberLazyGridState()
    val privateGroupsList = rememberLazyGridState()
    val locationsList = rememberLazyGridState()
    val favoriteAlbumsList = rememberLazyGridState()
    val privateFavoriteGroupsList = rememberLazyGridState()
    return remember { GridMemories(albumsList, privateGroupsList, locationsList, favoriteAlbumsList, privateFavoriteGroupsList) }
}
