package com.vaditim.gallery.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.RoundRect
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
private const val LOCATION_PIN = "M68.51,106.28c-5.59,6.13-12.1,11.62-19.41,16.06c-0.9,0.66-2.12,0.74-3.12,0.1 c-10.8-6.87-19.87-15.12-27-24.09C9.14,86.01,2.95,72.33,0.83,59.15c-2.16-13.36-0.14-26.22,6.51-36.67 c2.62-4.13,5.97-7.89,10.05-11.14C26.77,3.87,37.48-0.08,48.16,0c10.28,0.08,20.43,3.91,29.2,11.92c3.08,2.8,5.67,6.01,7.79,9.49 c7.15,11.78,8.69,26.8,5.55,42.02c-3.1,15.04-10.8,30.32-22.19,42.82V106.28L68.51,106.28z M46.12,23.76 c12.68,0,22.95,10.28,22.95,22.95c0,12.68-10.28,22.95-22.95,22.95c-12.68,0-22.95-10.27-22.95-22.95 C23.16,34.03,33.44,23.76,46.12,23.76L46.12,23.76z"
private const val CROP = "M10 6H14.8C15.9201 6 16.4802 6 16.908 6.21799C17.2843 6.40973 17.5903 6.71569 17.782 7.09202C18 7.51984 18 8.07989 18 9.2V14M2 6H6M18 18V22M22 18L9.2 18C8.07989 18 7.51984 18 7.09202 17.782C6.71569 17.5903 6.40973 17.2843 6.21799 16.908C6 16.4802 6 15.9201 6 14.8V2"
private const val CLOCK = "M22 12c0 5.523-4.477 10-10 10S2 17.523 2 12 6.477 2 12 2s10 4.477 10 10zm-4.581 3.324a1 1 0 0 0-.525-1.313L13 12.341V6.5a1 1 0 0 0-2 0v6.17c0 .6.357 1.143.909 1.379l4.197 1.8a1 1 0 0 0 1.313-.525z"
private const val GEAR = "M13.7654 2.15224C13.3978 2 12.9319 2 12 2C11.0681 2 10.6022 2 10.2346 2.15224C9.74457 2.35523 9.35522 2.74458 9.15223 3.23463C9.05957 3.45834 9.0233 3.7185 9.00911 4.09799C8.98826 4.65568 8.70226 5.17189 8.21894 5.45093C7.73564 5.72996 7.14559 5.71954 6.65219 5.45876C6.31645 5.2813 6.07301 5.18262 5.83294 5.15102C5.30704 5.08178 4.77518 5.22429 4.35436 5.5472C4.03874 5.78938 3.80577 6.1929 3.33983 6.99993C2.87389 7.80697 2.64092 8.21048 2.58899 8.60491C2.51976 9.1308 2.66227 9.66266 2.98518 10.0835C3.13256 10.2756 3.3397 10.437 3.66119 10.639C4.1338 10.936 4.43789 11.4419 4.43786 12C4.43783 12.5581 4.13375 13.0639 3.66118 13.3608C3.33965 13.5629 3.13248 13.7244 2.98508 13.9165C2.66217 14.3373 2.51966 14.8691 2.5889 15.395C2.64082 15.7894 2.87379 16.193 3.33973 17C3.80568 17.807 4.03865 18.2106 4.35426 18.4527C4.77508 18.7756 5.30694 18.9181 5.83284 18.8489C6.07289 18.8173 6.31632 18.7186 6.65204 18.5412C7.14547 18.2804 7.73556 18.27 8.2189 18.549C8.70224 18.8281 8.98826 19.3443 9.00911 19.9021C9.02331 20.2815 9.05957 20.5417 9.15223 20.7654C9.35522 21.2554 9.74457 21.6448 10.2346 21.8478C10.6022 22 11.0681 22 12 22C12.9319 22 13.3978 22 13.7654 21.8478C14.2554 21.6448 14.6448 21.2554 14.8477 20.7654C14.9404 20.5417 14.9767 20.2815 14.9909 19.902C15.0117 19.3443 15.2977 18.8281 15.781 18.549C16.2643 18.2699 16.8544 18.2804 17.3479 18.5412C17.6836 18.7186 17.927 18.8172 18.167 18.8488C18.6929 18.9181 19.2248 18.7756 19.6456 18.4527C19.9612 18.2105 20.1942 17.807 20.6601 16.9999C21.1261 16.1929 21.3591 15.7894 21.411 15.395C21.4802 14.8691 21.3377 14.3372 21.0148 13.9164C20.8674 13.7243 20.6602 13.5628 20.3387 13.3608C19.8662 13.0639 19.5621 12.558 19.5621 11.9999C19.5621 11.4418 19.8662 10.9361 20.3387 10.6392C20.6603 10.4371 20.8675 10.2757 21.0149 10.0835C21.3378 9.66273 21.4803 9.13087 21.4111 8.60497C21.3592 8.21055 21.1262 7.80703 20.6602 7C20.1943 6.19297 19.9613 5.78945 19.6457 5.54727C19.2249 5.22436 18.693 5.08185 18.1671 5.15109C17.9271 5.18269 17.6837 5.28136 17.3479 5.4588C16.8545 5.71959 16.2644 5.73002 15.7811 5.45096C15.2977 5.17191 15.0117 4.65566 14.9909 4.09794C14.9767 3.71848 14.9404 3.45833 14.8477 3.23463C14.6448 2.74458 14.2554 2.35523 13.7654 2.15224Z"
private const val GEAR_RING = "M15 12A3 3 0 1 1 9 12A3 3 0 1 1 15 12Z"
private const val INFO = "M22 12C22 17.5228 17.5228 22 12 22C6.47715 22 2 17.5228 2 12C2 6.47715 6.47715 2 12 2C17.5228 2 22 6.47715 22 12ZM12 17.75C12.4142 17.75 12.75 17.4142 12.75 17V11C12.75 10.5858 12.4142 10.25 12 10.25C11.5858 10.25 11.25 10.5858 11.25 11V17C11.25 17.4142 11.5858 17.75 12 17.75ZM12 7C12.5523 7 13 7.44772 13 8C13 8.55228 12.5523 9 12 9C11.4477 9 11 8.55228 11 8C11 7.44772 11.4477 7 12 7Z"
private const val MOVE = "M22 11.7979V14C22 17.7712 22 19.6569 20.8284 20.8284C19.6569 22 17.7713 22 14 22H10C6.22878 22 4.34315 22 3.17157 20.8284C2.19725 19.8541 2.03321 18.3859 2.00559 15.7501H10.6937L8.43392 17.3935C8.09893 17.6371 8.02487 18.1062 8.26849 18.4412C8.51212 18.7762 8.98118 18.8502 9.31617 18.6066L13.4412 15.6066C13.6352 15.4655 13.75 15.24 13.75 15.0001C13.75 14.7601 13.6352 14.5346 13.4412 14.3935L9.31617 11.3935C8.98118 11.1499 8.51212 11.2239 8.26849 11.5589C8.02487 11.8939 8.09893 12.363 8.43392 12.6066L10.6937 14.2501H2.00001L2 14L2.00003 6.94975C2.00003 6.06725 2.00003 5.62594 2.06938 5.25839C2.37467 3.64031 3.64033 2.37464 5.25841 2.06935C5.62597 2 6.06724 2 6.94977 2C7.33644 2 7.52978 2 7.71559 2.01738C8.51667 2.09229 9.27654 2.40704 9.89596 2.92051C10.0396 3.03961 10.1763 3.17633 10.4498 3.44975L11 4C11.8158 4.81578 12.2237 5.22367 12.7121 5.49543C12.9805 5.64471 13.2651 5.7626 13.5604 5.84678C14.0979 6 14.6748 6 15.8284 6H16.2021C18.8345 6 20.1507 6 21.0062 6.76946C21.0849 6.84024 21.1598 6.91514 21.2306 6.99383C22 7.84935 22 9.16554 22 11.7979Z"
private const val TRASH_LID = "M3 6.52381C3 6.12932 3.32671 5.80952 3.72973 5.80952H8.51787C8.52437 4.9683 8.61554 3.81504 9.45037 3.01668C10.1074 2.38839 11.0081 2 12 2C12.9919 2 13.8926 2.38839 14.5496 3.01668C15.3844 3.81504 15.4756 4.9683 15.4821 5.80952H20.2703C20.6733 5.80952 21 6.12932 21 6.52381C21 6.9183 20.6733 7.2381 20.2703 7.2381H3.72973C3.32671 7.2381 3 6.9183 3 6.52381Z"
private const val TRASH_BIN = "M11.5956 22H12.4044C15.1871 22 16.5785 22 17.4831 21.1141C18.3878 20.2281 18.4803 18.7749 18.6654 15.8685L18.9321 11.6806C19.0326 10.1036 19.0828 9.31511 18.6289 8.81545C18.1751 8.31579 17.4087 8.31579 15.876 8.31579H8.12404C6.59127 8.31579 5.82488 8.31579 5.37105 8.81545C4.91722 9.31511 4.96744 10.1036 5.06788 11.6806L5.33459 15.8685C5.5197 18.7749 5.61225 20.2281 6.51689 21.1141C7.42153 22 8.81289 22 11.5956 22ZM10.2463 12.1885C10.2051 11.7546 9.83753 11.4381 9.42537 11.4815C9.01321 11.5249 8.71251 11.9117 8.75372 12.3456L9.25372 17.6087C9.29494 18.0426 9.66247 18.3591 10.0746 18.3157C10.4868 18.2724 10.7875 17.8855 10.7463 17.4516L10.2463 12.1885ZM14.5746 11.4815C14.9868 11.5249 15.2875 11.9117 15.2463 12.3456L14.7463 17.6087C14.7051 18.0426 14.3375 18.3591 13.9254 18.3157C13.5132 18.2724 13.2125 17.8855 13.2537 17.4516L13.7537 12.1885C13.7949 11.7546 14.1625 11.4381 14.5746 11.4815Z"
private const val SHARE = "M98.11,0A24.77,24.77,0,1,1,80.6,42.28c-.22-.22-.43-.44-.64-.67l-31.14,13a25,25,0,0,1,.53,8.95L81,78.31A24.66,24.66,0,1,1,74.72,88L45.34,74.26A24.77,24.77,0,1,1,42.28,43c.44.44.87.9,1.27,1.37L74.29,31.55A24.77,24.77,0,0,1,98.11,0Z"
private const val ALBUM_0 = "M17.2905 11.9687C17.2905 12.7073 16.6984 13.3062 15.9679 13.3062C15.2374 13.3062 14.6453 12.7073 14.6453 11.9687C14.6453 11.23 15.2374 10.6311 15.9679 10.6311C16.6984 10.6311 17.2905 11.23 17.2905 11.9687Z"
private const val ALBUM_1 = "M18.1316 7.40799C17.2832 7.28732 16.1897 7.28734 14.8267 7.28736H9.17326C7.81031 7.28734 6.7168 7.28732 5.86839 7.40799C4.99062 7.53283 4.25955 7.80072 3.71603 8.42851C3.17252 9.05629 3.00655 9.82451 3.00019 10.7209C2.99404 11.5872 3.13858 12.6834 3.31873 14.0496L3.68419 16.8214C3.825 17.8895 3.93897 18.7539 4.11616 19.4309C4.3006 20.1355 4.57289 20.7197 5.08383 21.172C5.59477 21.6244 6.20337 21.8201 6.91841 21.9119C7.60534 22 8.46777 22 9.53332 22H14.4667C15.5322 22 16.3947 22 17.0816 21.9119C17.7966 21.8201 18.4052 21.6244 18.9162 21.172C19.4271 20.7197 19.6994 20.1355 19.8838 19.4309C20.061 18.7539 20.175 17.8894 20.3158 16.8213L20.6813 14.0496C20.8614 12.6834 21.006 11.5872 20.9998 10.7209C20.9934 9.82451 20.8275 9.05629 20.284 8.42851C19.7404 7.80072 19.0094 7.53283 18.1316 7.40799ZM6.05259 8.73247C5.32568 8.83585 4.95802 9.02442 4.71116 9.30956C4.4643 9.5947 4.32805 9.98816 4.32278 10.7305C4.31738 11.4918 4.44802 12.4945 4.63662 13.9249L4.68663 14.3042L5.05822 14.032C6.0171 13.3297 7.43388 13.3643 8.34576 14.1275L11.7301 16.9603C12.0499 17.228 12.6011 17.2781 12.9989 17.0441L13.2341 16.9057C14.3594 16.2437 15.8676 16.3135 16.9059 17.0958L18.7378 18.4758C18.8281 17.9802 18.909 17.3709 19.0107 16.5999L19.3634 13.9249C19.552 12.4945 19.6826 11.4918 19.6772 10.7305C19.6719 9.98816 19.5357 9.5947 19.2888 9.30956C19.042 9.02442 18.6743 8.83585 17.9474 8.73247C17.2019 8.62643 16.2018 8.62487 14.7748 8.62487H9.22521C7.79821 8.62487 6.7981 8.62643 6.05259 8.73247Z"
private const val ALBUM_2 = "M8.85886 2.00001H15.141C15.3502 1.99995 15.5106 1.99991 15.6508 2.01515C16.6479 2.12351 17.4639 2.78957 17.81 3.68676H6.18981C6.53588 2.78957 7.35195 2.12351 8.34899 2.01515C8.48922 1.99991 8.64963 1.99995 8.85886 2.00001Z"
private const val ALBUM_3 = "M6.87943 4.5C5.62786 4.5 4.60163 5.33974 4.25915 6.45377C4.25201 6.477 4.24517 6.50034 4.23862 6.5238C4.59696 6.40323 4.96989 6.32446 5.34741 6.27068C6.31974 6.13218 7.54855 6.13225 8.97598 6.13234L9.08258 6.13234L15.1789 6.13234C16.6063 6.13225 17.8351 6.13218 18.8074 6.27068C19.185 6.32446 19.5579 6.40323 19.9162 6.5238C19.9097 6.50034 19.9028 6.477 19.8957 6.45377C19.5532 5.33974 18.527 4.5 17.2754 4.5H6.87943Z"

// Parsed once for the whole app: a grid draws a heart per favourite and parsing each one would cost the scroll.
private val parsedPaths = HashMap<String, Path>()

@Composable
// `strokeWidth`, in viewBox units, draws the outlines instead of filling them. `viewBox` is the height and `viewBoxWidth` the width, which differ for a glyph that is not square; it sits centred in its box.
private fun SvgGlyph(color: Color, size: Dp, viewBox: Float = 24f, strokeWidth: Float? = null, viewBoxWidth: Float = viewBox, vararg paths: String) {
    val parsed = paths.map { parsedPaths.getOrPut(it) { PathParser().parsePathString(it).toPath().apply { fillType = PathFillType.EvenOdd } } }
    Canvas(Modifier.size(size)) {
        val scale = this.size.minDimension / maxOf(viewBox, viewBoxWidth)
        translate((this.size.width - viewBoxWidth * scale) / 2f, (this.size.height - viewBox * scale) / 2f) {
            scale(scale, pivot = Offset.Zero) {
                parsed.forEach { if (strokeWidth == null) drawPath(it, color) else drawPath(it, color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
            }
        }
    }
}

// The pop of the bar an icon button sits in, so each button comes and goes on its own as the bar changes kind.
val LocalButtonPop = androidx.compose.runtime.staticCompositionLocalOf<Modifier> { Modifier }

@Composable
fun IconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Every icon button is an action (share, move, delete, favourite…), so each one answers with a short click.
    Box(LocalButtonPop.current.then(modifier).pressable(onClick = onClick).clip(Shapes.capsule).size(width = 56.dp, height = 48.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { content() }
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

// A cog around a ring: the settings glyph, drawn in strokes as SVG Repo draws it.
@Composable
fun SettingsIcon(color: Color, size: Dp = 22.dp) = SvgGlyph(color, size, strokeWidth = 1.5f, paths = arrayOf(GEAR, GEAR_RING))

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
fun ShareIcon(color: Color, size: Dp = 22.dp) = SvgGlyph(color, size, viewBox = 120.94f, viewBoxWidth = 122.88f, paths = arrayOf(SHARE))

@Composable
fun TrashIcon(color: Color, size: Dp = 22.dp) = SvgGlyph(color, size, paths = arrayOf(TRASH_LID, TRASH_BIN))

@Composable
fun LockIcon(color: Color, isOpen: Boolean = false, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    // Drawn across most of the grid, as wide as the filled glyphs beside it, so it does not read smaller than they do.
    drawPath(path(unit) { u ->
        moveTo(7.2f * u, 10.2f * u); lineTo(7.2f * u, 6.6f * u)
        arcTo(Rect(7.2f * u, 1.8f * u, 16.8f * u, 11.4f * u), 180f, if (isOpen) 150f else 180f, false)
        if (!isOpen) lineTo(16.8f * u, 10.2f * u)
    }, color, style = stroke)
    // The keyhole is cut out of the body, so what is behind shows through it.
    drawPath(Path().apply {
        fillType = PathFillType.EvenOdd
        addRoundRect(RoundRect(3.6f * unit, 10.2f * unit, 20.4f * unit, 22.2f * unit, CornerRadius(3f * unit)))
        addOval(Rect(Offset(12f * unit, 16.2f * unit), 1.8f * unit))
    }, color)
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

// An arrow back along a hook: one step back.
@Composable
fun UndoIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u ->
        moveTo(9f * u, 5f * u); lineTo(4.5f * u, 9.5f * u); lineTo(9f * u, 14f * u)
        moveTo(4.5f * u, 9.5f * u); lineTo(14.5f * u, 9.5f * u)
        arcTo(Rect(9.5f * u, 9.5f * u, 19.5f * u, 19.5f * u), -90f, 180f, false)
        lineTo(10f * u, 19.5f * u)
    }, color, style = stroke)
}

// The undo arrow mirrored: one step forward again.
@Composable
fun RedoIcon(color: Color, size: Dp = 22.dp) = LineGlyph(color, size) { unit, stroke ->
    drawPath(path(unit) { u ->
        moveTo(15f * u, 5f * u); lineTo(19.5f * u, 9.5f * u); lineTo(15f * u, 14f * u)
        moveTo(19.5f * u, 9.5f * u); lineTo(9.5f * u, 9.5f * u)
        arcTo(Rect(4.5f * u, 9.5f * u, 14.5f * u, 19.5f * u), -90f, -180f, false)
        lineTo(14f * u, 19.5f * u)
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
fun CropIcon(color: Color, size: Dp = 22.dp) = SvgGlyph(color, size, strokeWidth = 2f, paths = arrayOf(CROP))

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
fun PinIcon(color: Color, size: Dp = 22.dp) = SvgGlyph(color, size, viewBox = 122.88f, viewBoxWidth = 92.25f, paths = arrayOf(LOCATION_PIN))

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

@Composable
fun ClockIcon(color: Color, size: Dp = 22.dp) = SvgGlyph(color, size, paths = arrayOf(CLOCK))
