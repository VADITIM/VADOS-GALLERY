package com.vaditim.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.media.MediaItem
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.LocalHazeState
import com.vaditim.gallery.vas.MicroLabel
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.fadingGlass
import com.vaditim.gallery.vas.glass
import com.vaditim.gallery.vas.pressable
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

// Choosing photos to put somewhere: the whole library as the usual grid, newest at the bottom, where every tap picks. Used to fill a new album, add to an existing one, or add to a private group.
@Composable
fun PickerScreen(title: String, items: List<MediaItem>, action: String, onDone: (List<MediaItem>) -> Unit, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)
    var pickedIds by remember { mutableStateOf(emptySet<Long>()) }
    val memory = remember { GridMemory() }
    val hazeState = rememberHazeState()
    val selection = Selection(pickedIds, isAlwaysActive = true) { item ->
        pickedIds = if (item.id in pickedIds) pickedIds - item.id else pickedIds + item.id
    }
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(Modifier.fillMaxSize().background(Palette.ground)) {
            MediaGrid(
                items = items,
                memory = memory,
                onOpen = { },
                contentPadding = PaddingValues(
                    top = statusBarHeight + 64.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
                ),
                selection = selection,
                modifier = Modifier.hazeSource(hazeState),
            )
            Box(Modifier.fillMaxWidth().height(statusBarHeight + 88.dp).fadingGlass())
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.pressable(onClick = onCancel).glass(Shapes.capsule).padding(horizontal = 16.dp, vertical = 11.dp)) {
                    BasicText("Cancel", style = Type.cardTitle.copy(color = Palette.textBody))
                }
                Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    MicroLabel(title)
                }
                val picked = items.filter { it.id in pickedIds }
                Box(
                    Modifier
                        .pressable(onClick = { if (picked.isNotEmpty()) onDone(picked) })
                        .glass(Shapes.capsule)
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                ) {
                    BasicText(
                        if (picked.isEmpty()) action else "$action ${picked.size}",
                        style = Type.cardTitle.copy(color = if (picked.isEmpty()) Palette.textFaint else LocalAccent.current),
                    )
                }
            }
        }
    }
}
