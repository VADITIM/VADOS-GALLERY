package com.vaditim.gallery.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.access.AccessState
import com.vaditim.gallery.access.StorageAccess
import com.vaditim.gallery.vas.LocalAccent
import com.vaditim.gallery.vas.Palette
import com.vaditim.gallery.vas.Panel
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.Type
import com.vaditim.gallery.vas.pressable

@Composable
fun AccessScreen(access: AccessState) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        AccessPanel(
            label = "FILES",
            caption = "Gallery reads and moves your photos and videos directly. Grant All files access on the next page.",
            isGranted = access.hasFileAccess,
            action = "GRANT ACCESS",
            onAction = { context.startActivity(StorageAccess.fileAccessIntent(context)) },
        )
        StorageAccess.mediaManagementIntent(context)?.let { intent ->
            AccessPanel(
                label = "MEDIA MANAGEMENT",
                caption = "Optional. Lets favourite, move and delete happen without a confirmation popup each time.",
                isGranted = access.canManageMedia,
                action = "ALLOW",
                onAction = { context.startActivity(intent) },
            )
        }
    }
}

@Composable
private fun AccessPanel(label: String, caption: String, isGranted: Boolean, action: String, onAction: () -> Unit) {
    Panel(Modifier.fillMaxWidth(), label = label) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BasicText(caption, style = Type.caption)
            if (isGranted) {
                BasicText("GRANTED", style = Type.action.copy(color = LocalAccent.current))
            } else {
                Box(
                    Modifier
                        .pressable(onClick = onAction)
                        .clip(Shapes.capsule)
                        .background(Palette.pressedWash)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    BasicText(action, style = Type.action.copy(color = LocalAccent.current))
                }
            }
        }
    }
}
