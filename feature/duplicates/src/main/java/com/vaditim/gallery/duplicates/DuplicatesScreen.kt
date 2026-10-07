package com.vaditim.gallery.duplicates

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vaditim.gallery.components.BackIcon
import com.vaditim.gallery.components.CheckIcon
import com.vaditim.gallery.components.ConfirmPill
import com.vaditim.gallery.components.FadingOverflow
import com.vaditim.gallery.components.IconButton
import com.vaditim.gallery.components.Segments
import com.vaditim.gallery.components.TrashIcon
import com.vaditim.gallery.components.formatSize
import com.vaditim.gallery.components.pendingMark
import com.vaditim.gallery.media.DuplicateFinder
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.media.Strictness
import com.vaditim.gallery.media.Thumbnail
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.pressable
import kotlinx.coroutines.delay

private const val TILES_PER_ROW = 3
private const val TILE_PIXELS = 384
private const val MARKED_SHADE = 0.45f
// Room under the list for the delete pill and the confirm above it.
private val BOTTOM_ROOM = 140.dp

// Every picture saved more than once, set by set, the copy worth keeping first. All but that one start marked; a tap marks or keeps a copy, a long press shows it large. Nothing is deleted until the pill at the bottom is confirmed, and then only to the trash.
@Composable
fun DuplicatesScreen(
    finder: DuplicateFinder,
    library: List<MediaItem>,
    strictness: Strictness,
    onStrictness: (Strictness) -> Unit,
    onDelete: (List<MediaItem>) -> Unit,
    onClose: () -> Unit,
) {
    // The library is read as it is when a search starts, so a photo arriving mid-search does not start it over. It starts once the strictness pill has settled, so the swap to the progress never lands mid-slide.
    val libraryNow by rememberUpdatedState(library)
    LaunchedEffect(strictness) {
        delay(Motion.STATE_MS.toLong())
        finder.find(libraryNow, strictness)
    }

    // A copy deleted elsewhere since the search leaves its set, and a set down to one picture is no longer a set.
    val present = remember(library) { library.mapTo(HashSet()) { it.id } }
    val groups = remember(finder.groups, present) { finder.groups.orEmpty().map { group -> group.filter { it.id in present } }.filter { it.size >= 2 } }
    // What the user changed; anything not in here is marked by its place, every copy after the first.
    val choices = remember(finder.groups) { mutableStateMapOf<Long, Boolean>() }
    fun isMarked(item: MediaItem, index: Int): Boolean = choices[item.id] ?: (index > 0)
    val marked = groups.flatMap { group -> group.filterIndexed { index, item -> isMarked(item, index) } }
    var pendingDelete by remember { mutableStateOf<(() -> Unit)?>(null) }
    var peek by remember { mutableStateOf<MediaItem?>(null) }
    val isSearching = finder.stage != DuplicateFinder.Stage.DONE

    BackHandler {
        when {
            peek != null -> peek = null
            pendingDelete != null -> pendingDelete = null
            else -> onClose()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.viewerGround)
            // Swallows taps so nothing behind it reacts.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { BackIcon(Palette.textBright) }
                BasicText("DUPLICATES", style = Type.cardTitle, modifier = Modifier.weight(1f))
                if (!isSearching && groups.isNotEmpty()) BasicText("${groups.size} SETS", style = Type.microLabel.copy(color = Palette.textMuted))
            }
            Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Segments(Strictness.entries.map { it.name }, Strictness.entries.indexOf(strictness)) { onStrictness(Strictness.entries[it]) }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isSearching -> SearchProgress(finder, Modifier.align(Alignment.Center))
                    groups.isEmpty() -> BasicText("NO DUPLICATES", style = Type.microLabel.copy(color = Palette.textMuted), modifier = Modifier.align(Alignment.Center))
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = BOTTOM_ROOM)) {
                        items(groups, key = { group -> group.first().id }) { group ->
                            DuplicateSet(group, isMarked = { item -> isMarked(item, group.indexOf(item)) }, onToggle = { item ->
                                choices[item.id] = !isMarked(item, group.indexOf(item))
                                pendingDelete = null
                            }, onPeek = { peek = it })
                        }
                    }
                }
            }
        }

        // The delete waits on Confirm, like every delete in the app; the photos go to the trash, with the pill's undo.
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ConfirmPill(pendingDelete, onDone = { pendingDelete = null })
            AnimatedVisibility(!isSearching && marked.isNotEmpty(), enter = fadeIn(tween(Motion.STATE_MS)) + scaleIn(tween(Motion.STATE_MS, easing = Motion.backOut), initialScale = 0f), exit = fadeOut(tween(Motion.STATE_MS)) + scaleOut(tween(Motion.STATE_MS, easing = Motion.backIn), targetScale = 0f)) {
                Row(
                    Modifier
                        .pressable(onClick = {
                            val picked = marked
                            pendingDelete = if (pendingDelete == null) { { onDelete(picked) } } else null
                        })
                        .pendingMark(pendingDelete != null)
                        .clip(Shapes.capsule)
                        .background(Palette.panelSolid)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TrashIcon(Palette.danger, size = 18.dp)
                    BasicText("${marked.size} · ${formatSize(marked.sumOf { it.sizeBytes })}", style = Type.action.copy(color = Palette.textBright))
                }
            }
        }

        // Held large on black, to tell two copies apart; a tap puts it back.
        AnimatedVisibility(peek != null, enter = fadeIn(tween(Motion.OVERLAY_ENTER_MS)) + scaleIn(tween(Motion.OVERLAY_ENTER_MS, easing = Motion.powerTwoOut), initialScale = 0.94f), exit = fadeOut(tween(Motion.OVERLAY_LEAVE_MS))) {
            val shown = remember { mutableStateOf(peek) }
            peek?.let { shown.value = it }
            Box(Modifier.fillMaxSize().background(Palette.viewerGround).pressable(onClick = { peek = null }, pressedScale = 1f)) {
                shown.value?.let { item ->
                    AsyncImage(model = item.uri, contentDescription = item.name, contentScale = ContentScale.Fit, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
                    Column(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(20.dp)) { Facts(item) }
                }
            }
        }
    }
}

// Reading every photo's print the first time takes a while; later searches only read what is new.
@Composable
private fun SearchProgress(finder: DuplicateFinder, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val shown by animateFloatAsState(finder.progress, tween(Motion.STATE_MS), label = "duplicates-progress")
    Column(modifier.fillMaxWidth().padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(if (finder.stage == DuplicateFinder.Stage.COMPARING) "COMPARING" else "READING", style = Type.microLabel.copy(color = accent))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText("${finder.readCount}", style = Type.title.copy(color = accent))
            BasicText("/ ${finder.totalCount}", style = Type.title.copy(color = Palette.textMuted))
        }
        Box(Modifier.fillMaxWidth().height(4.dp).clip(Shapes.capsule).background(Palette.borderStrong)) {
            Box(Modifier.fillMaxWidth(shown.coerceIn(0f, 1f)).height(4.dp).clip(Shapes.capsule).background(accent))
        }
    }
}

// One picture's copies in rows of three, the one to keep first, with how much deleting the marked ones frees.
@Composable
private fun DuplicateSet(group: List<MediaItem>, isMarked: (MediaItem) -> Boolean, onToggle: (MediaItem) -> Unit, onPeek: (MediaItem) -> Unit) {
    val freed = group.filter(isMarked).sumOf { it.sizeBytes }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText("${group.size} COPIES", style = Type.microLabel.copy(color = LocalAccent.current), modifier = Modifier.weight(1f))
            if (freed > 0) BasicText(formatSize(freed), style = Type.microLabel.copy(color = Palette.danger))
        }
        group.chunked(TILES_PER_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { item -> DuplicateTile(item, isMarked(item), { onToggle(item) }, { onPeek(item) }, Modifier.weight(1f)) }
                repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(1.dp).background(Palette.border))
    }
}

@Composable
private fun DuplicateTile(item: MediaItem, isMarked: Boolean, onToggle: () -> Unit, onPeek: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    val shade by animateFloatAsState(if (isMarked) MARKED_SHADE else 0f, tween(Motion.STATE_MS), label = "duplicate-shade")
    val markColor by animateColorAsState(if (isMarked) Palette.danger else accent, tween(Motion.STATE_MS), label = "duplicate-mark")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).pressable(onClick = onToggle, pressedScale = 0.95f, onLongClick = onPeek).clip(Shapes.tile).background(Palette.surface)) {
            // Private photos are not in MediaStore and have no cached thumbnail; they are decoded from the file at the tile's size.
            AsyncImage(model = if (item.uri.scheme == "content") Thumbnail.of(item, TILE_PIXELS) else item.uri, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = shade }.background(Palette.viewerGround))
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(Shapes.capsule).background(Palette.panelSolid).padding(horizontal = 8.dp, vertical = 5.dp)) {
                if (isMarked) TrashIcon(markColor, size = 14.dp) else CheckIcon(markColor, size = 14.dp)
            }
        }
        Facts(item)
    }
}

// The details that tell copies apart once they look the same: resolution, file size and the album it is in.
@Composable
private fun Facts(item: MediaItem) {
    val style = Type.value.copy(fontSize = 11.sp, color = Palette.textMuted)
    Column {
        if (item.width > 0 && item.height > 0) BasicText("${item.width}×${item.height}", style = style.copy(color = Palette.textBright), maxLines = 1)
        BasicText(formatSize(item.sizeBytes), style = style, maxLines = 1)
        FadingOverflow { BasicText(item.bucketName, style = style, maxLines = 1, softWrap = false) }
    }
}
