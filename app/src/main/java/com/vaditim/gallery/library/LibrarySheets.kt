package com.vaditim.gallery.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vaditim.gallery.media.Album
import com.vaditim.gallery.vault.PrivateGroup

// The sheets the app itself opens — for a selection or for a long-pressed album. The viewer has its own.
enum class AppSheet {
    NONE,
    NEW_ALBUM,
    SETTINGS, TRASH_RESTORE, ALBUM_RENAME, GROUP_RENAME, CONFIRM_PRIVATE, SELECTION_MOVE, SELECTION_NEW_ALBUM, SELECTION_GROUP, SELECTION_NEW_GROUP,
    ALBUM_MENU, ALBUM_GROUP, ALBUM_NEW_GROUP,
    GROUP_MENU, GROUP_MOVE_OUT, GROUP_MOVE_OUT_NEW_ALBUM,
    PRIVATE_NEW_GROUP,
    STACK_MENU, STACK_RENAME, ALBUM_STACK, ALBUM_NEW_STACK,
    FAVORITE_NEW_ALBUM, FAVORITE_ALBUM_PICK, FAVORITE_SELECTION_NEW_ALBUM,
    NEW_GROUP_NAME, NEW_GROUP_ALBUMS, NEW_GROUP_MOVE_CHECK, GROUP_DELETE_CHECK,
}

// Which sheet is open and what it is about: the album, private group or album group long-pressed, and the drafts a sheet hands on to the next.
@Stable
class LibrarySheets(private val selection: LibrarySelection) {
    var sheet by mutableStateOf(AppSheet.NONE)
        private set
    var album by mutableStateOf<Album?>(null)
    var group by mutableStateOf<PrivateGroup?>(null)
    var stack by mutableStateOf<String?>(null)
    // Albums and Favorites each keep album groups; the menus and pickers for them act on the one they were opened from.
    var shelf by mutableStateOf(AlbumShelfKind.FOLDERS)
    // A group being made from New group: its name, its shelf, and the albums ticked for it, kept while asking about those that already belong to another group.
    var newGroupName by mutableStateOf("")
    var newGroupShelf by mutableStateOf(AlbumShelfKind.FOLDERS)
    var newGroupKeys by mutableStateOf(emptySet<String>())
    var groupDeleteCheck by mutableStateOf<GroupDeleteCheck?>(null)
    var pendingPrivate by mutableStateOf<PendingPrivate?>(null)

    fun isOpen(which: AppSheet): Boolean = sheet == which

    // Opening anything lets a delete waiting on Confirm go.
    fun show(which: AppSheet) {
        sheet = which
        if (which != AppSheet.NONE) selection.pendingDelete = null
    }

    fun dismiss() = show(AppSheet.NONE)

    fun showAlbumMenu(album: Album, kind: AlbumShelfKind) {
        this.album = album
        shelf = kind
        show(AppSheet.ALBUM_MENU)
    }

    fun showStackMenu(name: String, kind: AlbumShelfKind) {
        stack = name
        shelf = kind
        show(AppSheet.STACK_MENU)
    }

    fun showGroupMenu(group: PrivateGroup) {
        this.group = group
        show(AppSheet.GROUP_MENU)
    }

    fun startNewGroup(kind: AlbumShelfKind) {
        newGroupShelf = kind
        show(AppSheet.NEW_GROUP_NAME)
    }

    fun askPrivate(pending: PendingPrivate) {
        pendingPrivate = pending
        show(AppSheet.CONFIRM_PRIVATE)
    }

    // Deleting a group waits for Confirm as every delete does; one that still holds photos then asks again, naming how many.
    fun askDeleteGroup(name: String, photoCount: Int, delete: () -> Unit) {
        selection.confirmThen {
            if (photoCount > 0) {
                groupDeleteCheck = GroupDeleteCheck(name, photoCount, delete)
                show(AppSheet.GROUP_DELETE_CHECK)
            } else {
                delete()
            }
        }
    }
}
