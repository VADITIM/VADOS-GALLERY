package com.vaditim.gallery.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vaditim.gallery.components.Section

// Where the user stands: the section, the place inside Albums, Private, and the overlays opened over them.
@Stable
class LibraryNavigation {
    var section by mutableStateOf(Section.RECENT)
    var albumsPlace by mutableStateOf<AlbumsPlace>(AlbumsPlace.Folders)
    // Inside Private the bar's sections show Private's own photos: Recent all of them, Favorites its favourites, Albums its groups. Entered from Albums, left by backing out of the groups or by locking.
    var isPrivateMode by mutableStateOf(false)
        private set
    // The Favorites album open, and the private group opened from Private's Favorites as a grid of only its favourites.
    var openFavoriteAlbum by mutableStateOf<String?>(null)
    var openPrivateFavoriteGroup by mutableStateOf<String?>(null)
    // Album groups left open stay open while an album from one of them is looked at.
    var openAlbumStacks by mutableStateOf(emptySet<String>())
    var openFavoriteStacks by mutableStateOf(emptySet<String>())
    // The folder being reviewed one photo at a time, and where the photo picker puts what is picked.
    var review by mutableStateOf<ViewerSource?>(null)
    var picker by mutableStateOf<PickerTarget?>(null)
    // The duplicates found across the whole library, over everything but the viewer.
    var isFindingDuplicates by mutableStateOf(false)
    // Tapping the section already shown asks its grid to scroll back to the newest photo.
    var scrollToNewestRequest by mutableIntStateOf(0)
        private set

    // The place inside Albums, only while Albums is the section shown.
    val place: AlbumsPlace? get() = if (section == Section.ALBUMS) albumsPlace else null

    // Recent and Favorites inside Private are their own screens, so going in or out of Private changes them as a section change does.
    val isPrivateSection: Boolean get() = isPrivateMode && section != Section.ALBUMS

    fun enterPrivate() {
        isPrivateMode = true
        albumsPlace = AlbumsPlace.PrivateGroups
    }

    fun leavePrivate() {
        isPrivateMode = false
        openPrivateFavoriteGroup = null
        albumsPlace = AlbumsPlace.Folders
    }

    // Out of Private; out of Locations entirely, from a location as from the list; out of the trash.
    fun leavePlace() {
        if (isPrivateMode) leavePrivate() else albumsPlace = AlbumsPlace.Folders
    }

    // Locking drops anyone standing in Private back to the albums list, and closes what showed private photos.
    fun onPrivateLocked() {
        isPrivateMode = false
        openPrivateFavoriteGroup = null
        if (albumsPlace.isPrivate) albumsPlace = AlbumsPlace.Folders
        if (review.isPrivate) review = null
    }

    // Inside Private, back from its Recent or Favorites goes to its groups, and from a group opened in its Favorites back to those.
    fun backInsidePrivate() {
        if (section == Section.FAVORITES && openPrivateFavoriteGroup != null) {
            openPrivateFavoriteGroup = null
        } else {
            section = Section.ALBUMS
            albumsPlace = AlbumsPlace.PrivateGroups
        }
    }

    // Tapping the section already shown goes back to the start of the place it is in, not out to the albums, and from there scrolls to the newest.
    fun select(selected: Section) {
        if (selected == section) {
            when {
                selected == Section.ALBUMS -> {
                    val start = when {
                        isPrivateMode -> AlbumsPlace.PrivateGroups
                        albumsPlace.isInLocations -> AlbumsPlace.Locations
                        albumsPlace == AlbumsPlace.Trash -> AlbumsPlace.Trash
                        else -> AlbumsPlace.Folders
                    }
                    if (albumsPlace == start) scrollToNewestRequest++
                    albumsPlace = start
                }
                selected == Section.FAVORITES && isPrivateMode && openPrivateFavoriteGroup != null -> openPrivateFavoriteGroup = null
                selected == Section.FAVORITES && !isPrivateMode && openFavoriteAlbum != null -> openFavoriteAlbum = null
                else -> scrollToNewestRequest++
            }
        }
        section = selected
    }
}
