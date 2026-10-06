package com.vaditim.gallery.library

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.vaditim.gallery.albums.AlbumMenuRows
import com.vaditim.gallery.albums.GroupMenuRows
import com.vaditim.gallery.albums.GroupRow
import com.vaditim.gallery.components.AlbumChoiceSheet
import com.vaditim.gallery.components.AlbumPickerSheet
import com.vaditim.gallery.components.CloseIcon
import com.vaditim.gallery.components.GroupPickerSheet
import com.vaditim.gallery.components.LockIcon
import com.vaditim.gallery.components.MoveIcon
import com.vaditim.gallery.components.NamePickerSheet
import com.vaditim.gallery.components.NameSheet
import com.vaditim.gallery.components.OverlaySheet
import com.vaditim.gallery.components.PlusIcon
import com.vaditim.gallery.components.RestoreIcon
import com.vaditim.gallery.components.SheetRow
import com.vaditim.gallery.components.StackPickerSheet
import com.vaditim.gallery.components.TrashIcon
import com.vaditim.gallery.media.newAlbumPath
import com.vaditim.gallery.settings.AlbumArrangement
import com.vaditim.gallery.settings.AlbumStack
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.Palette
import kotlinx.coroutines.launch

// Every sheet the library opens over itself. Each reads what it acts on from the controller and closes through it.
@Composable
internal fun LibrarySheetHost(controller: LibraryController, content: LibraryContent, screen: LibraryScreen) {
    SelectionSheets(controller, content, screen)
    AlbumSheets(controller, content, screen)
    NewGroupSheets(controller, content)
    PrivateGroupSheets(controller, content, screen)
    ConfirmPrivateSheet(controller)
    NameSheets(controller, screen)
}

// The selection's pickers. Moving out of Private goes to an album; moving within it goes to a group; restoring asks where to.
@Composable
private fun SelectionSheets(controller: LibraryController, content: LibraryContent, screen: LibraryScreen) {
    val sheets = controller.sheets
    val actions = controller.actions
    val selectedItems = screen.selectedItems
    val isInTrash = screen.place is AlbumsPlace.Trash
    OverlaySheet(visible = sheets.isOpen(AppSheet.TRASH_RESTORE), label = "RESTORE", onDismiss = sheets::dismiss) {
        Column {
            SheetRow("Restore", icon = { RestoreIcon(it) }) {
                actions.restore(selectedItems)
                controller.clearSelection()
            }
            SheetRow("Restore to album", icon = { MoveIcon(it) }) { sheets.show(AppSheet.SELECTION_MOVE) }
        }
    }
    AlbumPickerSheet(
        visible = sheets.isOpen(AppSheet.SELECTION_MOVE),
        label = when {
            screen.isPrivateMode -> "MOVE OUT TO"
            isInTrash -> "RESTORE TO"
            else -> "MOVE TO"
        },
        albums = content.albums,
        excludedAlbumId = screen.openAlbum?.id,
        onPick = { album ->
            when {
                screen.isPrivateMode -> actions.unhide(selectedItems, album)
                isInTrash -> actions.restoreTo(selectedItems, album)
                else -> actions.move(selectedItems, album)
            }
            controller.clearSelection()
        },
        onNewAlbum = { sheets.show(AppSheet.SELECTION_NEW_ALBUM) },
        onDismiss = sheets::dismiss,
    )
    GroupPickerSheet(
        visible = sheets.isOpen(AppSheet.SELECTION_GROUP),
        label = if (screen.isPrivateMode) "MOVE TO GROUP" else "MOVE TO PRIVATE",
        groups = content.privateContents.groups,
        excludedGroupName = screen.openPrivateGroup?.name,
        onPick = { name ->
            if (screen.isPrivateMode) {
                actions.moveToGroup(selectedItems, name)
                controller.clearSelection()
            } else {
                sheets.askPrivate(PendingPrivate(selectedItems, name, isSelection = true))
            }
        },
        onNewGroup = { sheets.show(AppSheet.SELECTION_NEW_GROUP) },
        onDismiss = sheets::dismiss,
    )
    // Favorites albums only gather favourites; moving between them never touches the photo's folder.
    NamePickerSheet(
        visible = sheets.isOpen(AppSheet.FAVORITE_ALBUM_PICK),
        label = "MOVE TO",
        choices = content.favoriteAlbums.filter { it.name != screen.openFavorite?.name }.map { it.name to it.items.size },
        newLabel = "+ New album",
        onPick = { name ->
            AlbumArrangement.favoriteAlbums.addPhotos(name, selectedItems.map { it.id })
            controller.clearSelection()
        },
        onNew = { sheets.show(AppSheet.FAVORITE_SELECTION_NEW_ALBUM) },
        onDismiss = sheets::dismiss,
    )
}

// A long-pressed album or album group, in Albums or in Favorites; the same menus for both, each shelf doing its own deleting and adding.
@Composable
private fun AlbumSheets(controller: LibraryController, content: LibraryContent, screen: LibraryScreen) {
    val sheets = controller.sheets
    val selection = controller.selection
    val shelf = controller.shelf(sheets.shelf)
    val targetAlbums = screen.selectedAlbums.ifEmpty { listOfNotNull(sheets.album) }
    val targetAlbumsLabel = targetAlbums.singleOrNull()?.name?.uppercase() ?: "${targetAlbums.size} ALBUMS"
    val startRearranging = {
        selection.isRearranging = true
        sheets.dismiss()
    }

    // Moving the whole folder into Private is what it is mostly for; deleting it waits for Confirm above the bar, as every delete does.
    OverlaySheet(visible = sheets.isOpen(AppSheet.ALBUM_MENU), label = sheets.album?.name?.uppercase().orEmpty(), onDismiss = sheets::dismiss) {
        val album = sheets.album
        val albumKey = album?.relativePath.orEmpty()
        AlbumMenuRows(
            onRename = { sheets.show(AppSheet.ALBUM_RENAME) },
            onSelect = {
                selection.covers = setOf(albumKey)
                sheets.dismiss()
            },
            onRearrange = startRearranging,
            group = if (shelf.isGrouped) GroupRow(
                name = shelf.stacks.holding(albumKey)?.name,
                onMove = { sheets.show(AppSheet.ALBUM_STACK) },
                onRemove = {
                    shelf.stacks.remove(listOf(albumKey))
                    sheets.dismiss()
                },
            ) else null,
            onPrivate = { sheets.show(AppSheet.ALBUM_GROUP) },
            onAddPhotos = {
                album?.let { controller.navigation.picker = shelf.pickerTarget(it) }
                sheets.dismiss()
            },
            onDelete = {
                sheets.dismiss()
                selection.confirmThen { album?.let { shelf.deleteAlbums(listOf(it)) } }
            },
        )
    }

    // Ungrouping only lays the group's albums back into the grid; no photo is touched.
    OverlaySheet(visible = sheets.isOpen(AppSheet.STACK_MENU), label = sheets.stack?.uppercase().orEmpty(), onDismiss = sheets::dismiss) {
        val stack = shelf.stacks.named(sheets.stack)
        val stackPaths = stack?.paths.orEmpty()
        GroupMenuRows(
            onDelete = {
                val name = sheets.stack.orEmpty()
                val photoCount = shelf.photoCountOf(stack, content)
                sheets.dismiss()
                if (stack != null) sheets.askDeleteGroup(name, photoCount) { shelf.deleteStack(stack, content) }
            },
            onRename = { sheets.show(AppSheet.STACK_RENAME) },
            onSelect = {
                selection.covers = stackPaths.toSet()
                sheets.dismiss()
            },
            onRearrange = startRearranging,
            groups = {
                SheetRow("Ungroup all", trailing = stackPaths.size.toString(), icon = { CloseIcon(it) }) {
                    shelf.stacks.delete(sheets.stack)
                    sheets.dismiss()
                }
            },
        )
    }

    StackPickerSheet(
        visible = sheets.isOpen(AppSheet.ALBUM_STACK),
        label = "MOVE $targetAlbumsLabel TO",
        stacks = shelf.stacks.all.filter { stack -> !targetAlbums.all { stack.paths.contains(it.relativePath) } },
        onPick = { name ->
            shelf.stacks.add(targetAlbums.map { it.relativePath }, name)
            controller.finishCoverAction(screen)
        },
        onNewStack = { sheets.show(AppSheet.ALBUM_NEW_STACK) },
        onDismiss = sheets::dismiss,
    )

    // Albums go into Private as all their photos, after one more confirmation.
    GroupPickerSheet(
        visible = sheets.isOpen(AppSheet.ALBUM_GROUP),
        label = "MOVE $targetAlbumsLabel TO",
        groups = content.privateContents.groups,
        excludedGroupName = null,
        onPick = { name -> sheets.askPrivate(PendingPrivate(targetAlbums.flatMap { it.items }, name, isSelection = screen.isSelectingCovers)) },
        onNewGroup = { sheets.show(AppSheet.ALBUM_NEW_GROUP) },
        onDismiss = sheets::dismiss,
    )
}

// New group: a name, then the albums ticked for it; an album already in another group would leave it, so that is asked first.
@Composable
private fun NewGroupSheets(controller: LibraryController, content: LibraryContent) {
    val sheets = controller.sheets
    val shelf = controller.shelf(sheets.newGroupShelf)
    AlbumChoiceSheet(
        visible = sheets.isOpen(AppSheet.NEW_GROUP_ALBUMS),
        label = sheets.newGroupName.uppercase(),
        initiallyTicked = sheets.newGroupKeys,
        choices = shelf.albums(content).map { it.relativePath to (it.name to it.items.size) },
        onCreate = { keys ->
            sheets.newGroupKeys = keys
            if (shelf.stacks.all.any { stack -> stack.name != sheets.newGroupName && stack.paths.any { it in keys } }) sheets.show(AppSheet.NEW_GROUP_MOVE_CHECK) else controller.createNewGroup()
        },
        onDismiss = sheets::dismiss,
    )
    val elsewhere = shelf.stacks.all.filter { stack -> stack.name != sheets.newGroupName && stack.paths.any { it in sheets.newGroupKeys } }
    val movedCount = elsewhere.sumOf { stack -> stack.paths.count { it in sheets.newGroupKeys } }
    OverlaySheet(
        visible = sheets.isOpen(AppSheet.NEW_GROUP_MOVE_CHECK),
        label = "${if (movedCount == 1) "ALBUM" else "$movedCount ALBUMS"} ALREADY IN ${elsewhere.joinToString(", ") { it.name }.uppercase()}",
        onDismiss = { sheets.show(AppSheet.NEW_GROUP_ALBUMS) },
    ) {
        SheetRow("Add anyway", trailing = sheets.newGroupKeys.size.toString()) { controller.createNewGroup() }
        SheetRow("Cancel", color = Palette.textMuted) { sheets.show(AppSheet.NEW_GROUP_ALBUMS) }
    }
    // After Confirm, a group that still holds photos asks once more, saying how many.
    var lastCheck by remember { mutableStateOf<GroupDeleteCheck?>(null) }
    sheets.groupDeleteCheck?.let { lastCheck = it }
    val closeCheck = {
        sheets.groupDeleteCheck = null
        sheets.dismiss()
    }
    OverlaySheet(
        visible = sheets.isOpen(AppSheet.GROUP_DELETE_CHECK),
        label = "${lastCheck?.photoCount ?: 0} PHOTOS IN ${lastCheck?.name?.uppercase().orEmpty()}",
        onDismiss = closeCheck,
    ) {
        SheetRow("Delete anyway", color = Palette.danger, icon = { TrashIcon(it) }) {
            sheets.groupDeleteCheck?.delete?.invoke()
            closeCheck()
        }
        SheetRow("Cancel", color = Palette.textMuted) { closeCheck() }
    }
}

// A long-pressed private group. Deleting one is final — private photos are outside the system trash — so it takes a second tap.
@Composable
private fun PrivateGroupSheets(controller: LibraryController, content: LibraryContent, screen: LibraryScreen) {
    val sheets = controller.sheets
    val selection = controller.selection
    val targetGroups = screen.selectedGroups.ifEmpty { listOfNotNull(sheets.group) }
    val targetGroupsLabel = targetGroups.singleOrNull()?.name?.uppercase() ?: "${targetGroups.size} GROUPS"
    OverlaySheet(visible = sheets.isOpen(AppSheet.GROUP_MENU), label = sheets.group?.name?.uppercase().orEmpty(), onDismiss = sheets::dismiss) {
        GroupMenuRows(
            onDelete = {
                val group = sheets.group
                sheets.dismiss()
                if (group != null) sheets.askDeleteGroup(group.name, group.items.size) { controller.actions.deleteGroup(group) }
            },
            onRename = { sheets.show(AppSheet.GROUP_RENAME) },
            onSelect = {
                sheets.group?.let { selection.covers = setOf(it.name) }
                sheets.dismiss()
            },
            onRearrange = {
                selection.isRearranging = true
                sheets.dismiss()
            },
            edit = {
                SheetRow("Move group out to album", trailing = sheets.group?.items?.size?.toString(), icon = { MoveIcon(it) }) { sheets.show(AppSheet.GROUP_MOVE_OUT) }
                SheetRow("Add photos", icon = { PlusIcon(it) }) {
                    sheets.group?.let { controller.navigation.picker = PickerTarget.IntoGroup(it.name) }
                    sheets.dismiss()
                }
            },
        )
    }
    AlbumPickerSheet(
        visible = sheets.isOpen(AppSheet.GROUP_MOVE_OUT),
        label = "MOVE $targetGroupsLabel OUT TO",
        albums = content.albums,
        excludedAlbumId = null,
        onPick = { album ->
            targetGroups.forEach { group -> controller.actions.unhide(group.items, album) { controller.viewModel.vault.removeGroupIfEmpty(group.name) } }
            controller.finishCoverAction(screen)
        },
        onNewAlbum = { sheets.show(AppSheet.GROUP_MOVE_OUT_NEW_ALBUM) },
        onDismiss = sheets::dismiss,
    )
}

// Going into Private moves files out of every other app's reach, so it is confirmed once more.
@Composable
private fun ConfirmPrivateSheet(controller: LibraryController) {
    val sheets = controller.sheets
    val closeConfirm = {
        sheets.pendingPrivate = null
        sheets.dismiss()
    }
    OverlaySheet(
        visible = sheets.isOpen(AppSheet.CONFIRM_PRIVATE),
        label = "PRIVATE · ${sheets.pendingPrivate?.groupName?.uppercase().orEmpty()}",
        onDismiss = sheets::dismiss,
    ) {
        val count = sheets.pendingPrivate?.items?.size ?: 0
        SheetRow(if (count == 1) "Move 1 photo to Private" else "Move $count photos to Private", color = LocalAccent.current, icon = { LockIcon(it) }) {
            sheets.pendingPrivate?.let { pending ->
                controller.actions.hide(pending.items, pending.groupName)
                if (pending.isSelection) controller.clearSelection()
            }
            closeConfirm()
        }
        SheetRow("Cancel", color = Palette.textMuted, icon = { CloseIcon(it) }) { closeConfirm() }
    }
}

// The sheets that ask for a name: renaming, and naming a new album or group before what goes into it is chosen.
@Composable
private fun NameSheets(controller: LibraryController, screen: LibraryScreen) {
    val sheets = controller.sheets
    val actions = controller.actions
    val navigation = controller.navigation
    val scope = rememberCoroutineScope()
    val shelf = controller.shelf(sheets.shelf)
    val selectedItems = screen.selectedItems
    val targetAlbums = screen.selectedAlbums.ifEmpty { listOfNotNull(sheets.album) }
    val targetGroups = screen.selectedGroups.ifEmpty { listOfNotNull(sheets.group) }
    val isInTrash = screen.place is AlbumsPlace.Trash
    when (sheets.sheet) {
        AppSheet.ALBUM_RENAME -> NameSheet(
            label = "RENAME ALBUM",
            action = "RENAME",
            initialName = sheets.album?.name.orEmpty(),
            onConfirm = { name ->
                sheets.album?.let { shelf.rename(it, name) }
                sheets.dismiss()
            },
            onDismiss = sheets::dismiss,
        )
        AppSheet.GROUP_RENAME -> NameSheet(
            label = "RENAME GROUP",
            action = "RENAME",
            initialName = sheets.group?.name.orEmpty(),
            onConfirm = { name ->
                sheets.group?.let { if (name != it.name) actions.renameGroup(it, name) }
                sheets.dismiss()
            },
            onDismiss = sheets::dismiss,
        )
        AppSheet.STACK_RENAME -> NameSheet(
            label = "RENAME GROUP",
            action = "RENAME",
            initialName = sheets.stack.orEmpty(),
            onConfirm = { name ->
                sheets.stack?.let { shelf.stacks.rename(it, name) }
                sheets.dismiss()
            },
            onDismiss = sheets::dismiss,
        )
        AppSheet.ALBUM_NEW_STACK -> NameSheet(
            label = "NEW GROUP",
            action = "CREATE",
            onConfirm = { name ->
                shelf.stacks.add(targetAlbums.map { it.relativePath }, name)
                controller.finishCoverAction(screen)
            },
            onDismiss = sheets::dismiss,
        )
        AppSheet.FAVORITE_NEW_ALBUM -> NameSheet(
            label = "NEW ALBUM",
            action = "CHOOSE PHOTOS",
            onConfirm = { name ->
                sheets.dismiss()
                navigation.picker = PickerTarget.IntoFavoriteAlbum(AlbumStack.cleanName(name))
            },
            onDismiss = sheets::dismiss,
        )
        AppSheet.FAVORITE_SELECTION_NEW_ALBUM -> NameSheet(
            label = "NEW ALBUM",
            action = "ADD HERE",
            onConfirm = { name ->
                AlbumArrangement.favoriteAlbums.addPhotos(name, selectedItems.map { it.id })
                controller.clearSelection()
            },
            onDismiss = sheets::dismiss,
        )
        AppSheet.NEW_GROUP_NAME -> NameSheet(
            label = "NEW GROUP",
            action = "CHOOSE ALBUMS",
            onConfirm = { name ->
                sheets.newGroupName = AlbumStack.cleanName(name)
                sheets.newGroupKeys = emptySet()
                sheets.show(if (sheets.newGroupName.isEmpty()) AppSheet.NONE else AppSheet.NEW_GROUP_ALBUMS)
            },
            onDismiss = sheets::dismiss,
        )
        AppSheet.NEW_ALBUM -> NameSheet(
            label = "NEW ALBUM",
            action = "CHOOSE PHOTOS",
            onConfirm = { name ->
                sheets.dismiss()
                navigation.picker = PickerTarget.IntoAlbum(newAlbumPath(name), name)
            },
            onDismiss = sheets::dismiss,
        )
        AppSheet.SELECTION_NEW_ALBUM -> NameSheet(
            label = "NEW ALBUM",
            action = if (isInTrash) "RESTORE HERE" else "MOVE HERE",
            onConfirm = { name ->
                when {
                    screen.isPrivateMode -> actions.unhide(selectedItems, newAlbumPath(name), name)
                    isInTrash -> actions.restoreTo(selectedItems, newAlbumPath(name), name)
                    else -> actions.move(selectedItems, newAlbumPath(name), name)
                }
                controller.clearSelection()
            },
            onDismiss = sheets::dismiss,
        )
        AppSheet.GROUP_MOVE_OUT_NEW_ALBUM -> NameSheet(
            label = "NEW ALBUM",
            action = "MOVE HERE",
            initialName = targetGroups.singleOrNull()?.name.orEmpty(),
            onConfirm = { name ->
                targetGroups.forEach { group -> actions.unhide(group.items, newAlbumPath(name), name) { controller.viewModel.vault.removeGroupIfEmpty(group.name) } }
                controller.finishCoverAction(screen)
            },
            onDismiss = sheets::dismiss,
        )
        AppSheet.SELECTION_NEW_GROUP -> NameSheet(
            label = "NEW PRIVATE GROUP",
            action = "MOVE HERE",
            onConfirm = { name ->
                if (screen.isPrivateMode) {
                    actions.moveToGroup(selectedItems, name)
                    controller.clearSelection()
                } else {
                    sheets.askPrivate(PendingPrivate(selectedItems, name, isSelection = true))
                }
            },
            onDismiss = sheets::dismiss,
        )
        AppSheet.ALBUM_NEW_GROUP -> NameSheet(
            label = "NEW PRIVATE GROUP",
            action = "MOVE HERE",
            initialName = targetAlbums.singleOrNull()?.name.orEmpty(),
            onConfirm = { name -> sheets.askPrivate(PendingPrivate(targetAlbums.flatMap { it.items }, name, isSelection = screen.isSelectingCovers)) },
            onDismiss = sheets::dismiss,
        )
        AppSheet.PRIVATE_NEW_GROUP -> NameSheet(
            label = "NEW PRIVATE GROUP",
            action = "CREATE",
            onConfirm = { name ->
                sheets.dismiss()
                scope.launch {
                    controller.viewModel.vault.createGroup(name)
                    controller.viewModel.refreshPrivate()
                }
            },
            onDismiss = sheets::dismiss,
        )
        else -> Unit
    }
}
