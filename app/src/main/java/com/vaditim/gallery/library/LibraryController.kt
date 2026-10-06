package com.vaditim.gallery.library

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.vaditim.gallery.components.MediaActions
import com.vaditim.gallery.components.rememberMediaActions
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.vas.Haptics
import com.vaditim.gallery.vault.PrivateLock

// The library's one owner of state: where the user is, what is picked, which sheet is open and the viewer's flight. Screens read from it and call into it; none of them keeps state of its own that another needs.
@Stable
class LibraryController(
    private val context: Context,
    val viewModel: GalleryViewModel,
    val memories: GridMemories,
) {
    // The photo actions hold the activity's result launcher, so they are handed in afresh by every composition rather than kept from the first.
    lateinit var actions: MediaActions

    val navigation = LibraryNavigation()
    val selection = LibrarySelection(onTick = { Haptics.tick(context) })
    val sheets = LibrarySheets(selection)
    val viewer = ViewerTransition()

    val folders = AlbumShelf.Folders { actions }
    val favoriteShelf = AlbumShelf.Favorites(
        onCoverMoved = viewModel::moveAlbumCover,
        onRenamed = { old, new -> if (navigation.openFavoriteAlbum == old) navigation.openFavoriteAlbum = new },
    )

    fun shelf(kind: AlbumShelfKind): AlbumShelf = if (kind == AlbumShelfKind.FOLDERS) folders else favoriteShelf

    // Letting go of a selection closes whatever sheet it had open.
    fun clearSelection() {
        selection.clear()
        sheets.dismiss()
    }

    // A sheet acting on picked covers lets them go when it is done; one opened from a single long-press only closes.
    fun finishCoverAction(screen: LibraryScreen) {
        if (screen.isSelectingCovers) clearSelection() else sheets.dismiss()
    }

    fun openPrivate() {
        if (viewModel.isPrivateUnlocked.value) {
            navigation.enterPrivate()
        } else {
            PrivateLock.unlock(context) {
                viewModel.unlockPrivate()
                navigation.enterPrivate()
            }
        }
    }

    fun openViewer(source: ViewerSource, index: Int) = viewer.open(source, index)

    fun setCover(source: ViewerSource, item: MediaItem, content: LibraryContent) {
        when (source) {
            is ViewerSource.InAlbum -> viewModel.setAlbumCover(source.albumId, item)
            is ViewerSource.InPrivateGroup -> viewModel.setGroupCover(source.name, item)
            is ViewerSource.InFavoriteAlbum -> content.favoriteAlbum(source.name)?.let { viewModel.setAlbumCover(it.id, item) }
            else -> return
        }
        actions.announce("Set as cover")
    }

    // Makes the group from New group with the albums ticked for it, taking each out of any group it was in.
    fun createNewGroup() {
        shelf(sheets.newGroupShelf).stacks.add(sheets.newGroupKeys, sheets.newGroupName)
        sheets.dismiss()
    }
}

@Composable
fun rememberLibraryController(viewModel: GalleryViewModel): LibraryController {
    val context = LocalContext.current
    val actions = rememberMediaActions(viewModel.repository, viewModel.vault, onPrivateChanged = { viewModel.refreshPrivate() }, samsungTrash = viewModel.samsungTrash, onTrashChanged = { viewModel.refreshTrash() })
    val memories = rememberGridMemories()
    return remember { LibraryController(context, viewModel, memories) }.also { it.actions = actions }
}
