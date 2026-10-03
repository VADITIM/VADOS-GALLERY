package com.vaditim.gallery.vas

import androidx.compose.animation.core.CubicBezierEasing

// The gallery tempo. VAS's base numbers (0.5s enter gate, 1.72s curtain) are tuned for a portfolio seen a handful of times; this app is opened dozens of times a day, so it takes the VAD/OS TERMINAL ceiling instead: nothing a finger waits on exceeds 0.3s. See docs/DESIGN.md § Motion.
object Motion {
    // GSAP's curves, as cubic béziers, so the numbers in dna/05-motion.md can be read straight across.
    val backOut = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val backIn = CubicBezierEasing(0.36f, 0f, 0.66f, -0.56f)
    val powerTwoOut = CubicBezierEasing(0.5f, 1f, 0.89f, 1f)
    val powerTwoIn = CubicBezierEasing(0.11f, 0f, 0.5f, 0f)
    val powerThreeInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    const val SECTION_ENTER_MS = 260
    const val SECTION_ENTER_DELAY_MS = 60
    const val SECTION_LEAVE_MS = 120

    const val VIEWER_ENTER_MS = 280
    const val VIEWER_LEAVE_MS = 160

    const val OVERLAY_ENTER_MS = 240
    const val OVERLAY_LEAVE_MS = 140

    const val PRESS_MS = 80
    const val RELEASE_MS = 220
    const val STATE_MS = 220
}
