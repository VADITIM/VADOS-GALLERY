package com.vaditim.gallery.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.vaditim.gallery.vas.Shapes
import com.vaditim.gallery.vas.pressable

// Drawn rather than taken from an icon pack: four glyphs do not justify a library, and drawing keeps them in the app's line weight. Each is laid out on a 24-unit grid and scaled to the box.

// Glyphs taken from SVG Repo as path data and filled in the given colour; the viewBox is 24 units unless noted.
private const val HEART_OUTLINE = "M8.96173 18.9109L9.42605 18.3219L8.96173 18.9109ZM12 5.50063L11.4596 6.02073C11.601 6.16763 11.7961 6.25063 12 6.25063C12.2039 6.25063 12.399 6.16763 12.5404 6.02073L12 5.50063ZM15.0383 18.9109L15.5026 19.4999L15.0383 18.9109ZM9.42605 18.3219C7.91039 17.1271 6.25307 15.9603 4.93829 14.4798C3.64922 13.0282 2.75 11.3345 2.75 9.1371H1.25C1.25 11.8026 2.3605 13.8361 3.81672 15.4758C5.24723 17.0866 7.07077 18.3752 8.49742 19.4999L9.42605 18.3219ZM2.75 9.1371C2.75 6.98623 3.96537 5.18252 5.62436 4.42419C7.23607 3.68748 9.40166 3.88258 11.4596 6.02073L12.5404 4.98053C10.0985 2.44352 7.26409 2.02539 5.00076 3.05996C2.78471 4.07292 1.25 6.42503 1.25 9.1371H2.75ZM8.49742 19.4999C9.00965 19.9037 9.55954 20.3343 10.1168 20.6599C10.6739 20.9854 11.3096 21.25 12 21.25V19.75C11.6904 19.75 11.3261 19.6293 10.8736 19.3648C10.4213 19.1005 9.95208 18.7366 9.42605 18.3219L8.49742 19.4999ZM15.5026 19.4999C16.9292 18.3752 18.7528 17.0866 20.1833 15.4758C21.6395 13.8361 22.75 11.8026 22.75 9.1371H21.25C21.25 11.3345 20.3508 13.0282 19.0617 14.4798C17.7469 15.9603 16.0896 17.1271 14.574 18.3219L15.5026 19.4999ZM22.75 9.1371C22.75 6.42503 21.2153 4.07292 18.9992 3.05996C16.7359 2.02539 13.9015 2.44352 11.4596 4.98053L12.5404 6.02073C14.5983 3.88258 16.7639 3.68748 18.3756 4.42419C20.0346 5.18252 21.25 6.98623 21.25 9.1371H22.75ZM14.574 18.3219C14.0479 18.7366 13.5787 19.1005 13.1264 19.3648C12.6739 19.6293 12.3096 19.75 12 19.75V21.25C12.6904 21.25 13.3261 20.9854 13.8832 20.6599C14.4405 20.3343 14.9903 19.9037 15.5026 19.4999L14.574 18.3219Z"
private const val HEART_SOLID = "M2 9.1371C2 14 6.01943 16.5914 8.96173 18.9109C10 19.7294 11 20.5 12 20.5C13 20.5 14 19.7294 15.0383 18.9109C17.9806 16.5914 22 14 22 9.1371C22 4.27416 16.4998 0.825464 12 5.50063C7.50016 0.825464 2 4.27416 2 9.1371Z"
private const val INFO = "M22 12C22 17.5228 17.5228 22 12 22C6.47715 22 2 17.5228 2 12C2 6.47715 6.47715 2 12 2C17.5228 2 22 6.47715 22 12ZM12 17.75C12.4142 17.75 12.75 17.4142 12.75 17V11C12.75 10.5858 12.4142 10.25 12 10.25C11.5858 10.25 11.25 10.5858 11.25 11V17C11.25 17.4142 11.5858 17.75 12 17.75ZM12 7C12.5523 7 13 7.44772 13 8C13 8.55228 12.5523 9 12 9C11.4477 9 11 8.55228 11 8C11 7.44772 11.4477 7 12 7Z"
private const val MOVE = "M22 11.7979V14C22 17.7712 22 19.6569 20.8284 20.8284C19.6569 22 17.7713 22 14 22H10C6.22878 22 4.34315 22 3.17157 20.8284C2.19725 19.8541 2.03321 18.3859 2.00559 15.7501H10.6937L8.43392 17.3935C8.09893 17.6371 8.02487 18.1062 8.26849 18.4412C8.51212 18.7762 8.98118 18.8502 9.31617 18.6066L13.4412 15.6066C13.6352 15.4655 13.75 15.24 13.75 15.0001C13.75 14.7601 13.6352 14.5346 13.4412 14.3935L9.31617 11.3935C8.98118 11.1499 8.51212 11.2239 8.26849 11.5589C8.02487 11.8939 8.09893 12.363 8.43392 12.6066L10.6937 14.2501H2.00001L2 14L2.00003 6.94975C2.00003 6.06725 2.00003 5.62594 2.06938 5.25839C2.37467 3.64031 3.64033 2.37464 5.25841 2.06935C5.62597 2 6.06724 2 6.94977 2C7.33644 2 7.52978 2 7.71559 2.01738C8.51667 2.09229 9.27654 2.40704 9.89596 2.92051C10.0396 3.03961 10.1763 3.17633 10.4498 3.44975L11 4C11.8158 4.81578 12.2237 5.22367 12.7121 5.49543C12.9805 5.64471 13.2651 5.7626 13.5604 5.84678C14.0979 6 14.6748 6 15.8284 6H16.2021C18.8345 6 20.1507 6 21.0062 6.76946C21.0849 6.84024 21.1598 6.91514 21.2306 6.99383C22 7.84935 22 9.16554 22 11.7979Z"
private const val TRASH_LID = "M3 6.52381C3 6.12932 3.32671 5.80952 3.72973 5.80952H8.51787C8.52437 4.9683 8.61554 3.81504 9.45037 3.01668C10.1074 2.38839 11.0081 2 12 2C12.9919 2 13.8926 2.38839 14.5496 3.01668C15.3844 3.81504 15.4756 4.9683 15.4821 5.80952H20.2703C20.6733 5.80952 21 6.12932 21 6.52381C21 6.9183 20.6733 7.2381 20.2703 7.2381H3.72973C3.32671 7.2381 3 6.9183 3 6.52381Z"
private const val TRASH_BIN = "M11.5956 22H12.4044C15.1871 22 16.5785 22 17.4831 21.1141C18.3878 20.2281 18.4803 18.7749 18.6654 15.8685L18.9321 11.6806C19.0326 10.1036 19.0828 9.31511 18.6289 8.81545C18.1751 8.31579 17.4087 8.31579 15.876 8.31579H8.12404C6.59127 8.31579 5.82488 8.31579 5.37105 8.81545C4.91722 9.31511 4.96744 10.1036 5.06788 11.6806L5.33459 15.8685C5.5197 18.7749 5.61225 20.2281 6.51689 21.1141C7.42153 22 8.81289 22 11.5956 22ZM10.2463 12.1885C10.2051 11.7546 9.83753 11.4381 9.42537 11.4815C9.01321 11.5249 8.71251 11.9117 8.75372 12.3456L9.25372 17.6087C9.29494 18.0426 9.66247 18.3591 10.0746 18.3157C10.4868 18.2724 10.7875 17.8855 10.7463 17.4516L10.2463 12.1885ZM14.5746 11.4815C14.9868 11.5249 15.2875 11.9117 15.2463 12.3456L14.7463 17.6087C14.7051 18.0426 14.3375 18.3591 13.9254 18.3157C13.5132 18.2724 13.2125 17.8855 13.2537 17.4516L13.7537 12.1885C13.7949 11.7546 14.1625 11.4381 14.5746 11.4815Z"
private const val SHARE = "M505.705,421.851c0,49.528-40.146,89.649-89.637,89.649c-49.527,0-89.662-40.121-89.662-89.649 c0-1.622,0.148-3.206,0.236-4.815l-177.464-90.474c-14.883,11.028-33.272,17.641-53.221,17.641 c-49.528,0-89.662-40.134-89.662-89.649s40.134-89.649,89.662-89.649c22.169,0,42.429,8.097,58.086,21.433l172.774-88.09 c-0.25-2.682-0.412-5.364-0.412-8.097c0-49.503,40.135-89.649,89.662-89.649c49.49,0,89.637,40.146,89.637,89.649 c0,49.516-40.146,89.65-89.637,89.65c-22.082,0-42.242-8.009-57.861-21.221l-172.999,88.215c0.224,2.558,0.387,5.14,0.387,7.76 c0,4.653-0.474,9.182-1.148,13.648l171.389,87.379c15.92-14.472,37.004-23.379,60.232-23.379 C465.559,332.201,505.705,372.348,505.705,421.851z"
private const val ALBUM_0 = "M17.2905 11.9687C17.2905 12.7073 16.6984 13.3062 15.9679 13.3062C15.2374 13.3062 14.6453 12.7073 14.6453 11.9687C14.6453 11.23 15.2374 10.6311 15.9679 10.6311C16.6984 10.6311 17.2905 11.23 17.2905 11.9687Z"
private const val ALBUM_1 = "M18.1316 7.40799C17.2832 7.28732 16.1897 7.28734 14.8267 7.28736H9.17326C7.81031 7.28734 6.7168 7.28732 5.86839 7.40799C4.99062 7.53283 4.25955 7.80072 3.71603 8.42851C3.17252 9.05629 3.00655 9.82451 3.00019 10.7209C2.99404 11.5872 3.13858 12.6834 3.31873 14.0496L3.68419 16.8214C3.825 17.8895 3.93897 18.7539 4.11616 19.4309C4.3006 20.1355 4.57289 20.7197 5.08383 21.172C5.59477 21.6244 6.20337 21.8201 6.91841 21.9119C7.60534 22 8.46777 22 9.53332 22H14.4667C15.5322 22 16.3947 22 17.0816 21.9119C17.7966 21.8201 18.4052 21.6244 18.9162 21.172C19.4271 20.7197 19.6994 20.1355 19.8838 19.4309C20.061 18.7539 20.175 17.8894 20.3158 16.8213L20.6813 14.0496C20.8614 12.6834 21.006 11.5872 20.9998 10.7209C20.9934 9.82451 20.8275 9.05629 20.284 8.42851C19.7404 7.80072 19.0094 7.53283 18.1316 7.40799ZM6.05259 8.73247C5.32568 8.83585 4.95802 9.02442 4.71116 9.30956C4.4643 9.5947 4.32805 9.98816 4.32278 10.7305C4.31738 11.4918 4.44802 12.4945 4.63662 13.9249L4.68663 14.3042L5.05822 14.032C6.0171 13.3297 7.43388 13.3643 8.34576 14.1275L11.7301 16.9603C12.0499 17.228 12.6011 17.2781 12.9989 17.0441L13.2341 16.9057C14.3594 16.2437 15.8676 16.3135 16.9059 17.0958L18.7378 18.4758C18.8281 17.9802 18.909 17.3709 19.0107 16.5999L19.3634 13.9249C19.552 12.4945 19.6826 11.4918 19.6772 10.7305C19.6719 9.98816 19.5357 9.5947 19.2888 9.30956C19.042 9.02442 18.6743 8.83585 17.9474 8.73247C17.2019 8.62643 16.2018 8.62487 14.7748 8.62487H9.22521C7.79821 8.62487 6.7981 8.62643 6.05259 8.73247Z"
private const val ALBUM_2 = "M8.85886 2.00001H15.141C15.3502 1.99995 15.5106 1.99991 15.6508 2.01515C16.6479 2.12351 17.4639 2.78957 17.81 3.68676H6.18981C6.53588 2.78957 7.35195 2.12351 8.34899 2.01515C8.48922 1.99991 8.64963 1.99995 8.85886 2.00001Z"
private const val ALBUM_3 = "M6.87943 4.5C5.62786 4.5 4.60163 5.33974 4.25915 6.45377C4.25201 6.477 4.24517 6.50034 4.23862 6.5238C4.59696 6.40323 4.96989 6.32446 5.34741 6.27068C6.31974 6.13218 7.54855 6.13225 8.97598 6.13234L9.08258 6.13234L15.1789 6.13234C16.6063 6.13225 17.8351 6.13218 18.8074 6.27068C19.185 6.32446 19.5579 6.40323 19.9162 6.5238C19.9097 6.50034 19.9028 6.477 19.8957 6.45377C19.5532 5.33974 18.527 4.5 17.2754 4.5H6.87943Z"

@Composable
private fun SvgGlyph(color: Color, size: Dp, viewBox: Float = 24f, vararg paths: String) {
    val parsed = remember(paths.toList()) { paths.map { PathParser().parsePathString(it).toPath().apply { fillType = PathFillType.EvenOdd } } }
    Canvas(Modifier.size(size)) {
        val scale = this.size.minDimension / viewBox
        scale(scale, pivot = Offset.Zero) { parsed.forEach { drawPath(it, color) } }
    }
}

@Composable
fun IconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Every icon button is an action (share, move, delete, favourite…), so each one answers with a short click.
    Box(modifier.pressable(onClick = onClick).clip(Shapes.capsule).size(width = 56.dp, height = 48.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { content() }
}

@Composable
fun PlayPauseIcon(isPlaying: Boolean, color: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        if (isPlaying) {
            drawRoundRect(color, Offset(6f * unit, 4f * unit), Size(4f * unit, 16f * unit), CornerRadius(1.5f * unit))
            drawRoundRect(color, Offset(14f * unit, 4f * unit), Size(4f * unit, 16f * unit), CornerRadius(1.5f * unit))
        } else {
            val triangle = Path().apply {
                moveTo(7f * unit, 4f * unit)
                lineTo(19f * unit, 12f * unit)
                lineTo(7f * unit, 20f * unit)
                close()
            }
            drawPath(triangle, color)
            drawPath(triangle, color, style = Stroke(2f * unit, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun HeartIcon(isFilled: Boolean, color: Color, size: Dp = 24.dp) = SvgGlyph(color, size, paths = arrayOf(if (isFilled) HEART_SOLID else HEART_OUTLINE))

// A speaker with its sound waves; muting draws a stroke across it while the waves fade and shrink away, and unmuting runs it back.
@Composable
fun SpeakerIcon(isMuted: Boolean, color: Color, size: Dp = 24.dp) {
    val mute by androidx.compose.animation.core.animateFloatAsState(
        if (isMuted) 1f else 0f,
        androidx.compose.animation.core.tween(com.vaditim.gallery.vas.Motion.STATE_MS, easing = com.vaditim.gallery.vas.Motion.powerTwoOut),
        label = "mute",
    )
    Canvas(Modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val stroke = Stroke(2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val body = Path().apply {
            moveTo(3.5f * unit, 9f * unit); lineTo(7.5f * unit, 9f * unit); lineTo(12.5f * unit, 4.5f * unit)
            lineTo(12.5f * unit, 19.5f * unit); lineTo(7.5f * unit, 15f * unit); lineTo(3.5f * unit, 15f * unit); close()
        }
        drawPath(body, color, style = stroke)
        val waves = 1f - mute
        if (waves > 0f) {
            val inner = androidx.compose.ui.geometry.Rect(10f * unit, 8.5f * unit, 17f * unit, 15.5f * unit)
            val outer = androidx.compose.ui.geometry.Rect(8.5f * unit, 4.5f * unit, 21f * unit, 19.5f * unit)
            drawArc(color.copy(alpha = color.alpha * waves), -50f * waves, 100f * waves, false, inner.topLeft, inner.size, style = stroke)
            drawArc(color.copy(alpha = color.alpha * waves), -50f * waves, 100f * waves, false, outer.topLeft, outer.size, style = stroke)
        }
        if (mute > 0f) {
            val from = Offset(3f * unit, 3f * unit)
            val to = Offset(21f * unit, 21f * unit)
            drawLine(color, from, from + (to - from) * mute, 2f * unit, StrokeCap.Round)
        }
    }
}

// Two arrows chasing each other round a rounded rectangle: the repeat glyph.
@Composable
fun LoopIcon(color: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val stroke = Stroke(2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val upper = Path().apply {
            moveTo(5f * unit, 11f * unit)
            lineTo(5f * unit, 10f * unit)
            quadraticTo(5f * unit, 7f * unit, 8f * unit, 7f * unit)
            lineTo(19f * unit, 7f * unit)
            moveTo(16f * unit, 4f * unit)
            lineTo(19f * unit, 7f * unit)
            lineTo(16f * unit, 10f * unit)
        }
        val lower = Path().apply {
            moveTo(19f * unit, 13f * unit)
            lineTo(19f * unit, 14f * unit)
            quadraticTo(19f * unit, 17f * unit, 16f * unit, 17f * unit)
            lineTo(5f * unit, 17f * unit)
            moveTo(8f * unit, 14f * unit)
            lineTo(5f * unit, 17f * unit)
            lineTo(8f * unit, 20f * unit)
        }
        drawPath(upper, color, style = stroke)
        drawPath(lower, color, style = stroke)
    }
}

// Chevrons pointing the way the picture is moving: one for gentle speeds, up to three for fast ones.
@Composable
fun SpeedArrows(isReverse: Boolean, count: Int, color: Color, size: Dp = 14.dp) {
    Canvas(Modifier.size(width = size * count, height = size)) {
        val unit = this.size.height / 14f
        val stroke = Stroke(2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        for (index in 0 until count) {
            val left = index * 14f * unit
            val chevron = Path().apply {
                if (isReverse) {
                    moveTo(left + 9f * unit, 2f * unit)
                    lineTo(left + 4f * unit, 7f * unit)
                    lineTo(left + 9f * unit, 12f * unit)
                } else {
                    moveTo(left + 5f * unit, 2f * unit)
                    lineTo(left + 10f * unit, 7f * unit)
                    lineTo(left + 5f * unit, 12f * unit)
                }
            }
            drawPath(chevron, color, style = stroke)
        }
    }
}

// Two sliders: the settings glyph.
@Composable
fun SettingsIcon(color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val stroke = Stroke(2f * unit, cap = StrokeCap.Round)
        drawLine(color, Offset(3f * unit, 8f * unit), Offset(21f * unit, 8f * unit), 2f * unit, StrokeCap.Round)
        drawLine(color, Offset(3f * unit, 16f * unit), Offset(21f * unit, 16f * unit), 2f * unit, StrokeCap.Round)
        drawCircle(Color.Black, 3.6f * unit, Offset(8f * unit, 8f * unit))
        drawCircle(color, 3.6f * unit, Offset(8f * unit, 8f * unit), style = stroke)
        drawCircle(Color.Black, 3.6f * unit, Offset(16f * unit, 16f * unit))
        drawCircle(color, 3.6f * unit, Offset(16f * unit, 16f * unit), style = stroke)
    }
}

// Line glyphs: one stroke weight for all, so a bar of them reads as one set.
@Composable
private fun LineGlyph(color: Color, size: Dp, draw: DrawScope.(unit: Float, stroke: Stroke) -> Unit) {
    Canvas(Modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        draw(unit, Stroke(1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun path(unit: Float, build: Path.(Float) -> Unit): Path = Path().apply { build(unit) }

@Composable
fun ShareIcon(color: Color, size: Dp = 22.dp) = SvgGlyph(color, size, 512f, SHARE)

@Composable
fun TrashIcon(color: Color, size: Dp = 22.dp) = SvgGlyph(color, size, paths = arrayOf(TRASH_LID, TRASH_BIN))

@Composable
fun LockIcon(color: Color, isOpen: Boolean = false, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawRoundRect(color, Offset(5f * unit, 10.5f * unit), Size(14f * unit, 10f * unit), CornerRadius(2.5f * unit), style = stroke)
    drawPath(path(unit) { u ->
        moveTo(8f * u, 10.5f * u); lineTo(8f * u, 7.5f * u)
        arcTo(Rect(8f * u, 3.5f * u, 16f * u, 11.5f * u), 180f, if (isOpen) 150f else 180f, false)
        if (!isOpen) lineTo(16f * u, 10.5f * u)
    }, color, style = stroke)
    drawCircle(color, 1.3f * unit, Offset(12f * unit, 15.5f * unit))
}

@Composable
fun MoveIcon(color: Color, size: Dp = 22.dp) = SvgGlyph(color, size, paths = arrayOf(MOVE))

@Composable
fun ImageIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawRoundRect(color, Offset(3.5f * unit, 5f * unit), Size(17f * unit, 14f * unit), CornerRadius(2.5f * unit), style = stroke)
    drawCircle(color, 1.6f * unit, Offset(9f * unit, 9.8f * unit), style = stroke)
    drawPath(path(unit) { u ->
        moveTo(4f * u, 17f * u); lineTo(9f * u, 12.8f * u); lineTo(12.8f * u, 16f * u); lineTo(15.6f * u, 13.6f * u); lineTo(20f * u, 17.2f * u)
    }, color, style = stroke)
}

@Composable
fun PenIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u ->
        moveTo(5f * u, 19f * u); lineTo(6f * u, 15f * u); lineTo(15.5f * u, 5.5f * u); lineTo(18.5f * u, 8.5f * u); lineTo(9f * u, 18f * u); close()
        moveTo(13.5f * u, 7.5f * u); lineTo(16.5f * u, 10.5f * u)
        moveTo(12.5f * u, 20f * u); lineTo(19.5f * u, 20f * u)
    }, color, style = stroke)
}

// A bar with two chevrons running back to it: back to the start.
@Composable
fun ResetIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u ->
        moveTo(5f * u, 6f * u); lineTo(5f * u, 18f * u)
        moveTo(13f * u, 6f * u); lineTo(8f * u, 12f * u); lineTo(13f * u, 18f * u)
        moveTo(19f * u, 6f * u); lineTo(14f * u, 12f * u); lineTo(19f * u, 18f * u)
    }, color, style = stroke)
}

@Composable
fun RestoreIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u ->
        arcTo(Rect(5f * u, 5f * u, 19f * u, 19f * u), 205f, 300f, true)
        moveTo(4.6f * u, 4.8f * u); lineTo(5.6f * u, 9.1f * u); lineTo(9.8f * u, 8f * u)
    }, color, style = stroke)
}

@Composable
fun CropIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(Path().apply {
        moveTo(7f * unit, 3.5f * unit); lineTo(7f * unit, 17f * unit); lineTo(20.5f * unit, 17f * unit)
        moveTo(3.5f * unit, 7f * unit); lineTo(17f * unit, 7f * unit); lineTo(17f * unit, 20.5f * unit)
    }, color, style = stroke)
}

@Composable
fun BackIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u -> moveTo(15f * u, 5f * u); lineTo(8f * u, 12f * u); lineTo(15f * u, 19f * u) }, color, style = stroke)
}

@Composable
fun PlusIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u -> moveTo(12f * u, 5f * u); lineTo(12f * u, 19f * u); moveTo(5f * u, 12f * u); lineTo(19f * u, 12f * u) }, color, style = stroke)
}

@Composable
fun CloseIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u -> moveTo(6.5f * u, 6.5f * u); lineTo(17.5f * u, 17.5f * u); moveTo(17.5f * u, 6.5f * u); lineTo(6.5f * u, 17.5f * u) }, color, style = stroke)
}

@Composable
fun InfoIcon(color: Color, size: Dp = 22.dp) = SvgGlyph(color, size, paths = arrayOf(INFO))

@Composable
fun MoreIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, _ ->
    listOf(5.5f, 12f, 18.5f).forEach { x -> drawCircle(color, 1.7f * unit, Offset(x * unit, 12f * unit)) }
}

@Composable
fun SlidersIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u ->
        moveTo(4f * u, 8f * u); lineTo(20f * u, 8f * u)
        moveTo(4f * u, 16f * u); lineTo(20f * u, 16f * u)
    }, color, style = stroke)
    drawCircle(Color.Black, 2.6f * unit, Offset(9f * unit, 8f * unit))
    drawCircle(color, 2.6f * unit, Offset(9f * unit, 8f * unit), style = stroke)
    drawCircle(Color.Black, 2.6f * unit, Offset(15f * unit, 16f * unit))
    drawCircle(color, 2.6f * unit, Offset(15f * unit, 16f * unit), style = stroke)
}

@Composable
fun PinIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u ->
        moveTo(12f * u, 21f * u)
        cubicTo(7f * u, 15.5f * u, 5f * u, 12.5f * u, 5f * u, 9.5f * u)
        cubicTo(5f * u, 5.6f * u, 8.1f * u, 3f * u, 12f * u, 3f * u)
        cubicTo(15.9f * u, 3f * u, 19f * u, 5.6f * u, 19f * u, 9.5f * u)
        cubicTo(19f * u, 12.5f * u, 17f * u, 15.5f * u, 12f * u, 21f * u)
        close()
    }, color, style = stroke)
    drawCircle(color, 2.4f * unit, Offset(12f * unit, 9.5f * unit), style = stroke)
}

@Composable
fun CheckIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u -> moveTo(5f * u, 12.5f * u); lineTo(10f * u, 17.5f * u); lineTo(19f * u, 7f * u) }, color, style = stroke)
}

// A motion photo: a dot inside a ring inside a ring of dots, the mark both Samsung and Apple use for a moving still.
@Composable
fun MotionIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawCircle(color, 2.4f * unit, Offset(12f * unit, 12f * unit))
    drawCircle(color, 5.6f * unit, Offset(12f * unit, 12f * unit), style = stroke)
    repeat(12) { step ->
        val angle = step * Math.PI / 6
        drawCircle(color, 0.9f * unit, Offset((12f + 9.2f * kotlin.math.cos(angle).toFloat()) * unit, (12f + 9.2f * kotlin.math.sin(angle).toFloat()) * unit))
    }
}

// Review: a card on top of another, leaning off it.
@Composable
fun ReviewIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawRoundRect(color, Offset(4f * unit, 6f * unit), Size(11f * unit, 14f * unit), CornerRadius(2f * unit), style = stroke)
    rotate(14f, Offset(14f * unit, 12f * unit)) {
        drawRoundRect(Color.Black, Offset(9f * unit, 4f * unit), Size(11f * unit, 14f * unit), CornerRadius(2f * unit))
        drawRoundRect(color, Offset(9f * unit, 4f * unit), Size(11f * unit, 14f * unit), CornerRadius(2f * unit), style = stroke)
    }
}

// An eye, struck through when the thing it marks is kept out of sight.
@Composable
fun EyeIcon(color: Color, isCrossed: Boolean = false, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u ->
        moveTo(2.5f * u, 12f * u)
        cubicTo(5f * u, 7f * u, 8.5f * u, 5f * u, 12f * u, 5f * u)
        cubicTo(15.5f * u, 5f * u, 19f * u, 7f * u, 21.5f * u, 12f * u)
        cubicTo(19f * u, 17f * u, 15.5f * u, 19f * u, 12f * u, 19f * u)
        cubicTo(8.5f * u, 19f * u, 5f * u, 17f * u, 2.5f * u, 12f * u)
        close()
        if (isCrossed) { moveTo(4f * u, 3.5f * u); lineTo(20f * u, 20.5f * u) }
    }, color, style = stroke)
    drawCircle(color, 3f * unit, Offset(12f * unit, 12f * unit), style = stroke)
}

// All photos at once: a grid of four.
@Composable
fun GridIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    listOf(4f, 13f).forEach { x -> listOf(4f, 13f).forEach { y -> drawRoundRect(color, Offset(x * unit, y * unit), Size(7f * unit, 7f * unit), CornerRadius(1.6f * unit), style = stroke) } }
}

// Albums: a cover with another lying behind it.
@Composable
fun AlbumsIcon(color: Color, size: Dp = 22.dp) = SvgGlyph(color, size, paths = arrayOf(ALBUM_0, ALBUM_1, ALBUM_2, ALBUM_3))

@Composable
fun GripIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, _ ->
    listOf(9f, 15f).forEach { x -> listOf(6f, 12f, 18f).forEach { y -> drawCircle(color, 1.6f * unit, Offset(x * unit, y * unit)) } }
}
