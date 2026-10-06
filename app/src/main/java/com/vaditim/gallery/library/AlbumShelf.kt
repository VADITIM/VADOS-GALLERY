package com.vaditim.gallery.library

import com.vaditim.gallery.components.MediaActions
import com.vaditim.gallery.media.Album
import com.vaditim.gallery.settings.AlbumArrangement
import com.vaditim.gallery.settings.AlbumStack
import com.vaditim.gallery.settings.AlbumStacks
import com.vaditim.gallery.settings.Settings
import com.vaditim.gallery.settings.SettingsView

enum class AlbumShelfKind { FOLDERS, FAVORITES }

// The two places that hold albums and their groups: the folders in Albums and the albums made inside Favorites. The menus are the same for both; what deleting, renaming or adding does is each shelf's own.
// An album is keyed by its path everywhere; a Favorites album's path is its name.
sealed class AlbumShelf(val kind: AlbumShelfKind, val view: SettingsView, val stacks: AlbumStacks) {
    val isGrouped: Boolean get() = Settings.groupedAlbumsIn(view)

    abstract fun albums(content: LibraryContent): List<Album>

    abstract fun pickerTarget(album: Album): PickerTarget

    // Whole albums at once, so it waits for Confirm even though the trash can give them back; a Favorites album only lets its photos go, but cannot be brought back.
    abstract fun deleteAlbums(albums: List<Album>)

    abstract fun rename(album: Album, name: String)

    fun photoCountOf(stack: AlbumStack?, content: LibraryContent): Int = albumsOf(stack, content).sumOf { it.items.size }

    // Deleting a group of folders sends their photos to the trash; deleting a group of Favorites albums lets the albums go, and their photos stay favourites.
    abstract fun deleteStack(stack: AlbumStack, content: LibraryContent)

    protected fun albumsOf(stack: AlbumStack?, content: LibraryContent): List<Album> {
        val paths = stack?.paths.orEmpty().toSet()
        return albums(content).filter { it.relativePath in paths }
    }

    class Folders(private val actionsProvider: () -> MediaActions) : AlbumShelf(AlbumShelfKind.FOLDERS, SettingsView.ALBUMS, AlbumArrangement.albumStacks) {
        private val actions get() = actionsProvider()

        override fun albums(content: LibraryContent) = content.albums

        override fun pickerTarget(album: Album) = PickerTarget.IntoAlbum(album.relativePath, album.name)

        override fun deleteAlbums(albums: List<Album>) = actions.trash(albums.flatMap { it.items })

        override fun rename(album: Album, name: String) {
            if (name != album.name) actions.renameAlbum(album, name)
        }

        override fun deleteStack(stack: AlbumStack, content: LibraryContent) {
            val photos = albumsOf(stack, content).flatMap { it.items }
            if (photos.isNotEmpty()) actions.trash(photos)
            stacks.delete(stack.name)
        }
    }

    class Favorites(
        private val onCoverMoved: (from: Long, to: Long) -> Unit,
        private val onRenamed: (old: String, new: String) -> Unit,
    ) : AlbumShelf(AlbumShelfKind.FAVORITES, SettingsView.FAVORITES, AlbumArrangement.favoriteStacks) {
        override fun albums(content: LibraryContent) = content.favoriteAlbums

        override fun pickerTarget(album: Album) = PickerTarget.IntoFavoriteAlbum(album.name)

        override fun deleteAlbums(albums: List<Album>) = AlbumArrangement.favoriteAlbums.delete(albums.map { it.name }.toSet())

        // The album's place in its group and in the order, and its cover, follow the new name.
        override fun rename(album: Album, name: String) {
            val old = album.name
            val new = AlbumStack.cleanName(name)
            if (new.isEmpty() || new == old) return
            AlbumArrangement.favoriteAlbums.rename(old, new)
            stacks.renameAlbum(old, new)
            AlbumArrangement.favoriteAlbumOrder.rename(old, new)
            onCoverMoved(old.hashCode().toLong(), new.hashCode().toLong())
            onRenamed(old, new)
        }

        override fun deleteStack(stack: AlbumStack, content: LibraryContent) {
            AlbumArrangement.favoriteAlbums.delete(stack.paths.toSet())
            stacks.delete(stack.name)
        }
    }
}
