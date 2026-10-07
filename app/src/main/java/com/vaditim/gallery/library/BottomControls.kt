package com.vaditim.gallery.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.vaditim.gallery.viewer.CHROME_PULL_SHARE
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaditim.gallery.components.BackIcon
import com.vaditim.gallery.components.CheckIcon
import com.vaditim.gallery.components.CloseIcon
import com.vaditim.gallery.components.CropIcon
import com.vaditim.gallery.components.MoreIcon
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.components.ConfirmPill
import com.vaditim.gallery.components.FavoriteHeart
import com.vaditim.gallery.components.HeartIcon
import com.vaditim.gallery.components.IconButton
import com.vaditim.gallery.components.ImageIcon
import com.vaditim.gallery.components.LocalButtonPop
import com.vaditim.gallery.components.LockIcon
import com.vaditim.gallery.components.OptionalButton
import com.vaditim.gallery.components.MoveIcon
import com.vaditim.gallery.components.RestoreIcon
import com.vaditim.gallery.components.SectionBar
import com.vaditim.gallery.components.ShareIcon
import com.vaditim.gallery.components.TrashIcon
import com.vaditim.gallery.components.TypedLabel
import com.vaditim.gallery.components.TypewriterText
import com.vaditim.gallery.components.VisibleMonth
import com.vaditim.gallery.components.pendingMark
import com.vaditim.gallery.components.rememberOwnAccent
import com.vaditim.gallery.components.Section
import com.vaditim.gallery.settings.Settings
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.Motion
import kotlinx.coroutines.delay
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.glass
import com.vaditim.gallery.vas.pressable
import kotlin.math.roundToInt

private val FOLDER_LABEL_MAX_WIDTH = 240.dp

// The nav bar's size and the screen's width, so what sits beside the nav (the count in the corner) can stand centred in the room right of it.
@Stable
class BarMetrics {
    var navigationHeight by mutableIntStateOf(0)
    var navigationWidth by mutableIntStateOf(0)
    var screenWidth by mutableIntStateOf(0)
}

// In the nav's row, centred in the room right of the nav: the favourites-only heart, and the count small under it, both straight on the photos with a shadow.
@Composable
internal fun BoxScope.FavoritesCorner(controller: LibraryController, screen: LibraryScreen, month: VisibleMonth, metrics: BarMetrics) {
    val selection = controller.selection
    // The month's photos out of the whole view's; kept while it hides, so it leaves showing what it had.
    var lastMonthCount by remember { mutableIntStateOf(0) }
    var lastTotal by remember { mutableIntStateOf(0) }
    val isCountShown = month.label.isNotEmpty()
    if (isCountShown) {
        lastMonthCount = month.count
        lastTotal = screen.gridItems.size
    }
    val canNarrow = screen.canNarrowToFavorites
    val navigationRight = (metrics.screenWidth + metrics.navigationWidth) / 2f
    AnimatedVisibility(
        (canNarrow || isCountShown) && controller.shownBar(screen) == BottomBar.NAVIGATION && metrics.navigationWidth > 0,
        enter = TOP_ENTER,
        exit = TOP_EXIT,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { placeable.place(((navigationRight + metrics.screenWidth) / 2f - placeable.width / 2f).roundToInt(), 0) }
            },
    ) {
        Box(Modifier.height(with(LocalDensity.current) { metrics.navigationHeight.toDp() }), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // No pill: the heart stands on the photos with a black shadow under it. Where there is nothing to narrow it still holds its room, unseen, so the count never climbs into its place.
                Box(
                    Modifier
                        .then(if (canNarrow) Modifier.pressable(onClick = { selection.isFavoritesOnly = !selection.isFavoritesOnly }) else Modifier)
                        .graphicsLayer { alpha = if (canNarrow) 1f else 0f }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.offset(y = 1.dp).blur(3.dp, BlurredEdgeTreatment.Unbounded)) { HeartIcon(isFilled = selection.isFavoritesOnly, color = Color.Black, size = 18.dp) }
                    HeartIcon(isFilled = selection.isFavoritesOnly, color = if (selection.isFavoritesOnly) Palette.favorite else Palette.textBright, size = 18.dp)
                }
                if (isCountShown) PhotoCount(lastMonthCount, lastTotal)
            }
        }
    }
}

// Prime component (VAS components/19-pop-bar.md): change the entry there first, then this.
// Under the content: Confirm for a waiting delete, the pills that name the place, and one glass bar that is the nav or a selection's actions.
@Composable
internal fun BottomControls(controller: LibraryController, content: LibraryContent, screen: LibraryScreen, metrics: BarMetrics, modifier: Modifier) {
    val selection = controller.selection
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ConfirmPill(selection.pendingDelete, onDone = { selection.pendingDelete = null })
        // The pills above the nav go with it, popping away as a selection's bar comes.
        AnimatedVisibility(controller.shownBar(screen) == BottomBar.NAVIGATION, enter = TOP_ENTER, exit = TOP_EXIT) {
            PlacePills(controller, content, screen)
        }
        // Private's red reaches the nav on the cut, once the outgoing view has left, like every other accent.
        var isPrivateInk by remember { mutableStateOf(screen.isPrivateMode) }
        LaunchedEffect(screen.isPrivateMode) {
            delay(Motion.SECTION_LEAVE_MS.toLong())
            isPrivateInk = screen.isPrivateMode
        }
        // The nav's wash fades with its buttons, coming back only once they pop in.
        val washAlpha by animateFloatAsState(
            if (controller.shownBar(screen) == BottomBar.NAVIGATION) 1f else 0f,
            tween(Motion.STATE_MS, delayMillis = if (controller.shownBar(screen) == BottomBar.NAVIGATION) Motion.STATE_MS else 0),
            label = "nav-wash",
        )
        val viewerBar = controller.viewer.bar
        val viewerPhoto = rememberViewerPhoto(controller, content)
        // A tap on the open photo sends the bar off the bottom edge and back, a swipe on it shrinks the bar with the finger.
        AnimatedVisibility(
            !(controller.viewer.isOpen && !viewerBar.isShown),
            enter = fadeIn(tween(Motion.OVERLAY_ENTER_MS, Motion.CHROME_STAGGER_MS, Motion.powerTwoOut)) +
                slideInVertically(tween(Motion.OVERLAY_ENTER_MS, Motion.CHROME_STAGGER_MS, Motion.backOut)) { it },
            exit = fadeOut(tween(Motion.OVERLAY_LEAVE_MS, Motion.CHROME_STAGGER_MS, Motion.powerTwoIn)) +
                slideOutVertically(tween(Motion.OVERLAY_LEAVE_MS, Motion.CHROME_STAGGER_MS, Motion.powerTwoIn)) { it },
        ) {
        // One glass pill for every kind of bar, the open photo's too: the old buttons pop away and the new ones pop in, each on its own, while the pill's width follows from one to the other.
        // The one bar's change of kind, played through when the kind changes, and with a photo open driven by a swipe down: its buttons shrink away, the width follows and the buttons of the bar under the photo grow in, all with the finger.
        val wantedBar = controller.shownBar(screen)
        val barState = remember { SeekableTransitionState(wantedBar) }
        LaunchedEffect(wantedBar) { barState.animateTo(wantedBar) }
        val barUnderPhoto by rememberUpdatedState(screen.bottomBar)
        LaunchedEffect(barState) {
            snapshotFlow { viewerBar.pull }.collect { pull ->
                if (!controller.viewer.isOpen || barState.currentState != BottomBar.VIEWER) return@collect
                if (pull > 0f) {
                    barState.seekTo((pull / CHROME_PULL_SHARE).coerceIn(0f, 1f), barUnderPhoto)
                } else if (barState.targetState != BottomBar.VIEWER) {
                    barState.snapTo(BottomBar.VIEWER)
                }
            }
        }
        val barTransition = rememberTransition(barState, label = "bottomBar")
        // Over a settled photo the pill is the viewer's black: the blur samples a radius past its edges, so a photo standing just above it would tint it.
        val isOverSettledPhoto = controller.viewer.isOpen && !controller.viewer.isShrunk
        Box(if (isOverSettledPhoto) Modifier.clip(Shapes.capsule).background(Palette.viewerGround) else Modifier.glass(Shapes.capsule)) {
            barTransition.AnimatedContent(
                transitionSpec = { EnterTransition.None.togetherWith(ExitTransition.None).using(SizeTransform(clip = false) { _, _ -> tween(Motion.STATE_MS * 2, easing = Motion.powerThreeInOut) }) },
                contentAlignment = Alignment.Center,
            ) { shownBar ->
                val pop = Modifier.animateEnterExit(enter = TOP_POP_IN, exit = TOP_POP_OUT)
                // A bar on its way out keeps the selection it had, so no button of it vanishes before it pops away.
                val isLeaving = transition.targetState == EnterExitState.PostExit
                CompositionLocalProvider(LocalButtonPop provides pop) {
                    when (shownBar) {
                        BottomBar.REARRANGING -> Box(pop.pressable(onClick = { selection.isRearranging = false }).padding(horizontal = 22.dp, vertical = 13.dp)) {
                            CheckIcon(LocalAccent.current)
                        }
                        BottomBar.COVERS -> CoverActions(controller, screen, isLeaving)
                        BottomBar.PHOTOS -> PhotoActions(controller, content, screen, isLeaving)
                        // The empty pill keeps the nav's size, so the viewer's bar grows out of it and the nav's buttons pop back into it.
                        BottomBar.VIEWER -> viewerPhoto?.let { (item, source) -> ViewerActions(controller, item, source) }
                        BottomBar.NAVIGATION -> SectionBar(
                            hasGlass = false,
                            itemModifier = pop,
                            washAlpha = { washAlpha },
                            // Inside Private, Recent and Favorites show only Private's own photos.
                            isMarked = { screen.isPrivateMode && it != Section.ALBUMS },
                            modifier = Modifier.onSizeChanged {
                                metrics.navigationHeight = it.height
                                metrics.navigationWidth = it.width
                            },
                            active = screen.section,
                            accentOf = { shown -> screen.accentOf(shown, isPrivateInk) },
                            albumsGlyph = screen.albumsGlyph,
                            onSelect = controller.navigation::select,
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun PlacePills(controller: LibraryController, content: LibraryContent, screen: LibraryScreen) {
    val selection = controller.selection
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val placePill = screen.placePill
        var lastPill by remember { mutableStateOf(placePill ?: "") }
        if (placePill != null) lastPill = placePill
        val pillAccent = rememberOwnAccent(placePill != null)
        // Where you are, in the section's colour; not a control, so no arrow and no press.
        var lastFolderName by remember { mutableStateOf(screen.folderName.orEmpty()) }
        var lastFolderAccent by remember { mutableStateOf(screen.accentTarget) }
        if (screen.isFolderLabelShown) {
            lastFolderName = screen.folderName.orEmpty()
            lastFolderAccent = screen.accentTarget
        }
        AnimatedVisibility(screen.isFolderLabelShown, enter = TOP_ENTER, exit = TOP_EXIT) {
            TypedLabel(lastFolderName, lastFolderAccent, Modifier.padding(bottom = 8.dp), maxWidth = FOLDER_LABEL_MAX_WIDTH)
        }
        // Empties the whole trash for good, so it waits for Confirm.
        AnimatedVisibility(screen.place is AlbumsPlace.Trash && content.trash.isNotEmpty(), enter = TOP_ENTER, exit = TOP_EXIT) {
            Box(
                Modifier.padding(bottom = 8.dp)
                    .pressable(onClick = { selection.confirmThen { controller.actions.deleteForever(content.trash) } })
                    .glass(Shapes.capsule)
                    .pendingMark(selection.pendingDelete != null)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                BasicText("DELETE NOW", style = Type.microLabel.copy(color = Palette.danger))
            }
        }
        // The label is the way out as much as the arrow beside it.
        val leavePlace = controller.navigation::leavePlace
        AnimatedVisibility(placePill != null, enter = TOP_ENTER, exit = TOP_EXIT) {
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // The arrow in the label's own colours, ink on the place colour, so the two read as one way out.
                Box(Modifier.pressable(onClick = leavePlace).background(pillAccent, Shapes.capsule).padding(horizontal = 10.dp, vertical = 5.dp)) { BackIcon(Palette.sunkenDeep, size = 16.dp) }
                Box(Modifier.pressable(onClick = leavePlace).background(pillAccent, Shapes.capsule).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    BasicText(lastPill, style = Type.microLabel.copy(color = Palette.sunkenDeep))
                }
            }
        }
    }
}

// The open photo and where it came from, known from the moment it is asked for, so the bar changes into its buttons at once; kept after it closes, so they pop away showing what they had.
@Composable
private fun rememberViewerPhoto(controller: LibraryController, content: LibraryContent): Pair<MediaItem, ViewerSource>? {
    val viewer = controller.viewer
    val kept = remember { arrayOfNulls<Pair<MediaItem, ViewerSource>>(1) }
    val request = viewer.request
    if (request != null) {
        val items = content.itemsFor(request.source)
        // The photo swiped to once the viewer is up; until then, the one tapped.
        val item = (if (viewer.shown == request) items.firstOrNull { it.id == viewer.currentId } else null) ?: items.getOrNull(request.startIndex)
        if (item != null) kept[0] = item to request.source
    }
    return kept[0]
}

// The open photo's buttons: share, then restore and delete in the trash, elsewhere favourite, crop, delete and more.
@Composable
private fun ViewerActions(controller: LibraryController, item: MediaItem, source: ViewerSource) {
    val bar = controller.viewer.bar
    val actions = controller.actions
    val deleteModifier = Modifier.pendingMark(bar.isPendingDelete)
    Row(Modifier.padding(5.dp)) {
        IconButton(onClick = { actions.share(listOf(item)) }) { ShareIcon(Palette.textBody) }
        if (source == ViewerSource.Trash) {
            IconButton(onClick = { actions.restore(listOf(item)) }) { RestoreIcon(LocalAccent.current) }
            IconButton(onClick = { bar.onDelete() }, modifier = deleteModifier) { TrashIcon(Palette.danger) }
            return@Row
        }
        IconButton(onClick = { actions.toggleFavorite(item) }) { FavoriteHeart(item.isFavorite, if (item.isFavorite) Palette.favorite else Palette.textBody) }
        IconButton(onClick = { bar.onCrop() }) { CropIcon(Palette.textBody) }
        IconButton(onClick = { bar.onDelete() }, modifier = deleteModifier) { TrashIcon(Palette.danger) }
        IconButton(onClick = { bar.onMore() }) { MoreIcon(Palette.textBody) }
    }
}

// Picked albums or private groups: group them, take them out of their group, move them into or out of Private, or delete them.
@Composable
private fun CoverActions(controller: LibraryController, screen: LibraryScreen, isLeaving: Boolean) {
    val selection = controller.selection
    val sheets = controller.sheets
    val selectedGroups = rememberKept(screen.selectedGroups, isLeaving)
    val selectedAlbums = rememberKept(screen.selectedAlbums, isLeaving)
    Row(Modifier.padding(5.dp)) {
        val deleteModifier = Modifier.pendingMark(selection.pendingDelete != null)
        if (selectedGroups.isNotEmpty()) {
            IconButton(onClick = { sheets.show(AppSheet.GROUP_MOVE_OUT) }) { LockIcon(Palette.textBody, isOpen = true) }
            // Private groups are outside the system trash, so deleting them waits for Confirm.
            IconButton(onClick = {
                selection.confirmThen {
                    selectedGroups.forEach { controller.actions.deleteGroup(it) }
                    controller.clearSelection()
                }
            }, modifier = deleteModifier) { TrashIcon(Palette.danger) }
        } else {
            val shelf = controller.shelf(if (screen.section == Section.FAVORITES) AlbumShelfKind.FAVORITES else AlbumShelfKind.FOLDERS)
            if (Settings.groupedAlbumsInView) {
                IconButton(onClick = {
                    sheets.shelf = shelf.kind
                    sheets.show(AppSheet.ALBUM_STACK)
                }) { MoveIcon(Palette.textBody) }
            }
            // Only the selected albums that sit in a group can leave one.
            val groupedPaths = selectedAlbums.map { it.relativePath }.filter { path -> shelf.stacks.holding(path) != null }
            if (Settings.groupedAlbumsInView) {
                OptionalButton(groupedPaths.isNotEmpty()) {
                    IconButton(onClick = {
                        shelf.stacks.remove(groupedPaths)
                        controller.clearSelection()
                    }) { CloseIcon(Palette.textBody) }
                }
            }
            IconButton(onClick = { sheets.show(AppSheet.ALBUM_GROUP) }) { LockIcon(Palette.textBody) }
            IconButton(onClick = {
                selection.confirmThen {
                    shelf.deleteAlbums(selectedAlbums)
                    controller.clearSelection()
                }
            }, modifier = deleteModifier) { TrashIcon(Palette.danger) }
        }
    }
}

// Picked photos: in the trash, restore or delete for good; elsewhere share, favourite, set as cover, move, take into or out of Private, or delete.
@Composable
private fun PhotoActions(controller: LibraryController, content: LibraryContent, screen: LibraryScreen, isLeaving: Boolean) {
    val selection = controller.selection
    val sheets = controller.sheets
    val actions = controller.actions
    val selectedItems = rememberKept(screen.selectedItems, isLeaving)
    // Every delete waits for Confirm.
    val confirmDelete: (() -> Unit) -> Unit = { delete ->
        selection.confirmThen {
            delete()
            controller.clearSelection()
        }
    }
    Row(Modifier.padding(5.dp)) {
        if (screen.place is AlbumsPlace.Trash) {
            IconButton(onClick = { sheets.show(AppSheet.TRASH_RESTORE) }) { RestoreIcon(LocalAccent.current) }
            // Out of the trash there is no coming back.
            IconButton(onClick = { confirmDelete { actions.deleteForever(selectedItems) } }, modifier = Modifier.pendingMark(selection.pendingDelete != null)) { TrashIcon(Palette.danger) }
            return@Row
        }
        IconButton(onClick = { actions.share(selectedItems) }) { ShareIcon(Palette.textBody) }
        // Favourites every selected photo, or takes them all out once all of them are favourites.
        // An emptied selection counts as none, or the heart flashes red while the bar leaves.
        val isAllFavorite = selectedItems.isNotEmpty() && selectedItems.all { it.isFavorite }
        IconButton(onClick = {
            actions.setFavorite(selectedItems, !isAllFavorite)
            controller.clearSelection()
        }) { FavoriteHeart(isAllFavorite, if (isAllFavorite) Palette.favorite else Palette.textBody, size = 22.dp) }
        val coverSource = screen.gridSource?.takeIf { it is ViewerSource.InAlbum || it is ViewerSource.InPrivateGroup || it is ViewerSource.InFavoriteAlbum }
        if (coverSource != null) {
            OptionalButton(selectedItems.size == 1) {
                IconButton(onClick = {
                    selectedItems.firstOrNull()?.let { controller.setCover(coverSource, it, content) }
                    controller.clearSelection()
                }) { ImageIcon(Palette.textBody) }
            }
        }
        if (screen.isPrivateMode) {
            // Private's Recent mixes every private album, so its photos only leave Private from there.
            if (screen.gridSource != ViewerSource.PrivateRecent) IconButton(onClick = { sheets.show(AppSheet.SELECTION_GROUP) }) { MoveIcon(Palette.textBody) }
            IconButton(onClick = { sheets.show(AppSheet.SELECTION_MOVE) }) { LockIcon(Palette.textBody, isOpen = true) }
            IconButton(onClick = { confirmDelete { actions.deletePrivate(selectedItems) } }, modifier = Modifier.pendingMark(selection.pendingDelete != null)) { TrashIcon(Palette.danger) }
        } else {
            // The same bar as in albums; inside Favorites, moving goes between its own albums.
            IconButton(onClick = { sheets.show(if (screen.section == Section.FAVORITES) AppSheet.FAVORITE_ALBUM_PICK else AppSheet.SELECTION_MOVE) }) { MoveIcon(Palette.textBody) }
            IconButton(onClick = { sheets.show(AppSheet.SELECTION_GROUP) }) { LockIcon(Palette.textBody) }
            IconButton(onClick = { confirmDelete { actions.trash(selectedItems) } }, modifier = Modifier.pendingMark(selection.pendingDelete != null)) { TrashIcon(Palette.danger) }
        }
    }
}

// The value a bar was showing, held while the bar leaves, so it pops away as it was rather than as what came after it.
@Composable
private fun <T> rememberKept(value: T, isLeaving: Boolean): T {
    val kept = remember { arrayOfNulls<Any>(1).also { it[0] = value } }
    if (!isLeaving) kept[0] = value
    @Suppress("UNCHECKED_CAST")
    return kept[0] as T
}

// The count small: the month's number in a slot of its own against the slash, the total in one after it, both as wide as the total's digits, so a number gaining or losing a digit never moves the slash or the other number. Each types itself over when it changes, as the month does.
@Composable
private fun PhotoCount(monthCount: Int, total: Int, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val digits = total.toString().length
    val slotWidth = with(LocalDensity.current) { remember(digits) { measurer.measure("0".repeat(digits), COUNT_STYLE).size.width }.toDp() }
    val numberStyle = COUNT_STYLE.copy(color = Palette.textBright, shadow = Type.dropShadow)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(slotWidth), contentAlignment = Alignment.CenterEnd) { TypewriterText(monthCount.toString(), numberStyle, isCaretShown = false) }
        BasicText("/", style = COUNT_STYLE.copy(color = Palette.textMuted, shadow = Type.dropShadow), modifier = Modifier.padding(horizontal = 2.dp))
        Box(Modifier.width(slotWidth), contentAlignment = Alignment.CenterStart) { TypewriterText(total.toString(), numberStyle, isCaretShown = false) }
    }
}

// Every digit as wide as every other, so a number's width depends only on how many digits it has.
private val COUNT_STYLE = Type.microLabel.copy(fontSize = 8.sp, letterSpacing = 0.sp, fontFeatureSettings = "tnum")

// With a photo open the bar holds the photo's buttons, changing into them as it does when a selection begins, and back when the photo closes.
internal fun LibraryController.shownBar(screen: LibraryScreen): BottomBar = if (viewer.isOpen) BottomBar.VIEWER else screen.bottomBar
