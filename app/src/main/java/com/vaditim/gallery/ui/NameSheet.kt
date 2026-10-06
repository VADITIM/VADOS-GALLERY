package com.vaditim.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.MicroLabel
import com.vaditim.gallery.vas.Motion
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.glass
import com.vaditim.gallery.vas.pressable
import kotlinx.coroutines.launch

// One text field on a pane of glass, sitting on the keyboard. Used to name a new private group.
@Composable
fun NameSheet(label: String, action: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit, ground: Color = Palette.ground, initialName: String = "") {
    // A name being changed arrives selected whole, so typing replaces it at once.
    var field by remember { mutableStateOf(TextFieldValue(initialName, TextRange(0, initialName.length))) }
    val name = field.text
    val focusRequester = remember { FocusRequester() }
    // 0 is below the screen, 1 in place: the pane rises from the bottom on arrival and sinks back to it on leaving, with the keyboard.
    val rise = remember { Animatable(0f) }
    var isLeaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        rise.animateTo(1f, tween(Motion.OVERLAY_ENTER_MS, easing = Motion.backOut))
    }
    // What follows (the new name, or only closing) waits until the pane is out of sight.
    fun leave(then: () -> Unit) {
        if (isLeaving) return
        isLeaving = true
        keyboard?.hide()
        scope.launch {
            rise.animateTo(0f, tween(Motion.OVERLAY_LEAVE_MS, easing = Motion.powerTwoIn))
            then()
        }
    }
    val confirm = { if (name.isNotBlank()) leave { onConfirm(name.trim()) } }
    // The back gesture closes the sheet, not what lies behind it.
    BackHandler { leave(onDismiss) }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = SCRIM_ALPHA * rise.value.coerceIn(0f, 1f))) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { leave(onDismiss) }),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .graphicsLayer { translationY = (1f - rise.value) * size.height }
                .navigationBarsPadding()
                .imePadding()
                .padding(12.dp)
                .fillMaxWidth()
                .glass(Shapes.sheet, ground)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MicroLabel(label)
            BasicTextField(
                value = field,
                onValueChange = { field = it },
                singleLine = true,
                textStyle = Type.title,
                cursorBrush = SolidColor(LocalAccent.current),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier
                        .pressable(onClick = confirm)
                        .clip(Shapes.capsule)
                        .background(Palette.pressedWash)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    BasicText(action, style = Type.action.copy(color = if (name.isBlank()) Palette.textFaint else LocalAccent.current))
                }
            }
        }
    }
}

private const val SCRIM_ALPHA = 0.3f
