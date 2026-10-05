package com.vaditim.gallery.ui

import androidx.compose.runtime.Composable
import com.vaditim.gallery.vas.Palette

// What a long-pressed group of albums can put in its menu besides renaming, selecting and rearranging.
class GroupRow(val name: String?, val onMove: () -> Unit, val onRemove: () -> Unit)

// The long-press menu of an album, the same in Albums and in Favorites: alternatives share a row, the main one in words, the other as its icon. What it does to the album first, then changing it, and selecting last.
@Composable
fun AlbumMenuRows(
    onRename: () -> Unit,
    onSelect: () -> Unit,
    onRearrange: () -> Unit,
    group: GroupRow?,
    onPrivate: () -> Unit,
    isHiddenFromRecent: Boolean,
    onToggleRecent: () -> Unit,
    onAddPhotos: () -> Unit,
    isDeleteArmed: Boolean,
    armedDeleteText: String,
    onDelete: () -> Unit,
) {
    SheetHeader("Albums")
    // Once armed the whole row is the delete, so the second tap cannot land on the other half.
    if (isDeleteArmed) {
        SheetRow(armedDeleteText, color = Palette.danger, icon = { TrashIcon(it) }, onClick = onDelete)
    } else {
        SplitSheetRow(
            if (isHiddenFromRecent) "Show in Recent" else "Hide from Recent",
            icon = { EyeIcon(it, isCrossed = !isHiddenFromRecent) },
            onClick = onToggleRecent,
            sideIcon = { TrashIcon(it) },
            onSide = onDelete,
            sideColor = Palette.danger,
        )
    }
    if (group?.name != null) SheetRow("Remove from group", trailing = group.name, icon = { CloseIcon(it) }, onClick = group.onRemove)
    SheetRow("Add photos", icon = { PlusIcon(it) }, onClick = onAddPhotos)
    SheetHeader("Edit")
    SheetRow("Rename", icon = { PenIcon(it) }, onClick = onRename)
    if (group != null) {
        SplitSheetRow(if (group.name == null) "Add to group" else "Move to group", icon = { MoveIcon(it) }, onClick = group.onMove, sideIcon = { LockIcon(it) }, onSide = onPrivate)
    } else {
        SheetRow("Move album to private", icon = { LockIcon(it) }, onClick = onPrivate)
    }
    SelectRow(onSelect, onRearrange)
}

// The long-press menu of a group, of albums or of private photos, laid out as an album's: what it does to the groups, changing this one, and selecting last.
@Composable
fun GroupMenuRows(onRename: () -> Unit, onSelect: () -> Unit, onRearrange: () -> Unit, groups: @Composable () -> Unit, edit: @Composable () -> Unit = {}) {
    SheetHeader("Groups")
    groups()
    SheetHeader("Edit")
    SheetRow("Rename", icon = { PenIcon(it) }, onClick = onRename)
    edit()
    SelectRow(onSelect, onRearrange)
}

// Selecting and rearranging share one row, at the bottom of every cover grid's menu.
@Composable
private fun SelectRow(onSelect: () -> Unit, onRearrange: () -> Unit) =
    SplitSheetRow("Select", icon = { CheckIcon(it) }, onClick = onSelect, sideIcon = { GripIcon(it) }, onSide = onRearrange)
