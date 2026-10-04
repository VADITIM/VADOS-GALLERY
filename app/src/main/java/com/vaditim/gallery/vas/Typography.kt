package com.vaditim.gallery.vas

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.vaditim.gallery.R

// Declared once here and nowhere else (dna/02-typography.md). The display role (Wosker / Striker) is not shipped yet; see docs/DESIGN.md.
object Faces {
    val mono = FontFamily(Font(R.font.mono))
    val heading = FontFamily(Font(R.font.audiowide))
}

object Type {
    // The micro-label: 0.63rem mono, uppercase, 0.19rem tracking — tracking kept as the same share of the size.
    val microLabel = TextStyle(fontFamily = Faces.mono, fontSize = 10.sp, letterSpacing = 3.sp, color = Palette.textLabel)
    val title = TextStyle(fontFamily = Faces.heading, fontSize = 28.sp, letterSpacing = 0.5.sp, color = Palette.textBright)
    val navigation = TextStyle(fontFamily = Faces.mono, fontSize = 11.sp, letterSpacing = 2.sp)
    val cardTitle = TextStyle(fontFamily = Faces.heading, fontSize = 13.sp, letterSpacing = 0.5.sp, color = Palette.textBright)
    val value = TextStyle(fontFamily = Faces.mono, fontSize = 13.sp, color = Palette.textMuted)
    val caption = TextStyle(fontFamily = Faces.mono, fontSize = 13.sp, lineHeight = 19.sp, color = Palette.textBody)
    val action = TextStyle(fontFamily = Faces.mono, fontSize = 11.sp, letterSpacing = 2.sp)
}
