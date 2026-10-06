package com.vaditim.gallery.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.vas.Motion
import kotlinx.coroutines.delay

// Past this many items the rest of a screenful lands together, so the cascade stays short however many columns there are.
private const val MAX_RANK = 16
private val RISE = 14.dp
private const val START_SCALE = 0.94f

// When a grid comes on screen (a folder opened, a section switched to) its first screenful lands as a short cascade, in the order the items were composed. Anything composed later, by scrolling, is simply there.
class Entrance {
    val openedAt = SystemClock.uptimeMillis()
    private var composed = 0

    // Each item's place in the cascade, or null once the grid has been on screen long enough that items no longer make an entrance.
    fun nextRank(): Int? =
        if (SystemClock.uptimeMillis() - openedAt > Motion.ENTRANCE_WINDOW_MS) null else composed++.coerceAtMost(MAX_RANK)
}

private val LocalEntrance = staticCompositionLocalOf<Entrance?> { null }

// Every photo grid and cover grid wraps its items in this, so each one of them opens the same way.
@Composable
fun ProvideEntrance(content: @Composable () -> Unit) {
    val entrance = remember { Entrance() }
    CompositionLocalProvider(LocalEntrance provides entrance, content = content)
}

@Composable
fun Modifier.entrance(): Modifier {
    val entrance = LocalEntrance.current ?: return this
    val rank = remember { entrance.nextRank() }
    val progress = remember { Animatable(if (rank == null) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (rank == null) return@LaunchedEffect
        delay(rank * Motion.ENTRANCE_STAGGER_MS.toLong())
        progress.animateTo(1f, tween(Motion.ENTRANCE_MS, easing = Motion.powerTwoOut))
    }
    val rise = with(LocalDensity.current) { RISE.toPx() }
    return graphicsLayer {
        val shown = progress.value
        alpha = shown
        translationY = (1f - shown) * rise
        scaleX = START_SCALE + (1f - START_SCALE) * shown
        scaleY = scaleX
    }
}
