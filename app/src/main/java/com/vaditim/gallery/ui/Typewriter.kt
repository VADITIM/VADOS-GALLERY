package com.vaditim.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.vaditim.gallery.vas.Motion
import kotlinx.coroutines.delay

// Text that, when it changes, deletes back to what the old and new share and types the rest in, as VADOS Bubble does; the first text shows at once unless it is to type itself in.
@Composable
fun TypewriterText(text: String, style: TextStyle, modifier: Modifier = Modifier, isTypedIn: Boolean = false, isCaretShown: Boolean = true) {
    var shown by remember { mutableStateOf(if (isTypedIn) "" else text) }
    var isTyping by remember { mutableStateOf(false) }
    var isCaretOn by remember { mutableStateOf(true) }
    LaunchedEffect(text) {
        isTyping = shown != text
        while (shown != text) {
            if (shown.isNotEmpty() && !text.startsWith(shown)) {
                shown = shown.dropLast(1)
                delay(Motion.UNTYPE_MS)
            } else {
                shown = text.take(shown.length + 1)
                delay(Motion.TYPE_MS)
            }
        }
        isTyping = false
    }
    LaunchedEffect(isTyping) {
        isCaretOn = true
        while (isTyping) {
            delay(Motion.CARET_BLINK_MS)
            isCaretOn = !isCaretOn
        }
    }
    val fontSize = with(LocalDensity.current) { style.fontSize.toDp() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BasicText(shown, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
        // The block caret of a terminal, in the text's own colour.
        if (isTyping && isCaretShown) {
            Box(
                Modifier
                    .padding(start = fontSize * 0.12f)
                    .size(width = fontSize * 0.42f, height = fontSize)
                    .graphicsLayer { alpha = if (isCaretOn) 1f else 0f }
                    .background(style.color),
            )
        }
    }
}
