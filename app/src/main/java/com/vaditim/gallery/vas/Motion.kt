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
    const val VIEWER_CLOSE_MS = 260

    const val OVERLAY_ENTER_MS = 240
    const val OVERLAY_LEAVE_MS = 140

    const val PRESS_MS = 80
    const val RELEASE_MS = 220
    const val STATE_MS = 220

    // An album group opening into its row, or folding back onto its top card.
    const val STACK_MS = 360

    // One swing of a cover's jiggle while covers are being rearranged.
    const val JIGGLE_MS = 130

    // The pause between the two clicks of the "done" vibration.
    const val HAPTIC_CONFIRM_GAP_MS = 70

    // The viewer's buttons leaving or arriving one after another rather than as one sheet.
    const val CHROME_STAGGER_MS = 45

    // The nav highlight sliding to another section: its leading edge goes first, the trailing edge follows.
    const val NAV_SLIDE_MS = 240
    const val NAV_TRAIL_MS = 60

    // A grid's first screenful arriving as a cascade when its folder or section opens.
    const val ENTRANCE_MS = 240
    const val ENTRANCE_STAGGER_MS = 12
    const val ENTRANCE_WINDOW_MS = 350

    // The grid timeline: the bubble fading in when a finger takes hold of it, and the strip handed between finger and grid.
    const val TIMELINE_REVEAL_MS = 180

    // The bar-sweep reveal on a title: the bar grows, then retracts slower because that half is the one read; leaving is a quicker cut.
    const val SWEEP_GROW_MS = 420
    const val SWEEP_RETRACT_MS = 500
    const val SWEEP_LEAVE_MS = 300

    // Holding a tile to select waits this much past the system's long press, so a resting thumb does not select by accident.
    const val SELECT_HOLD_EXTRA_MS = 120L
    // Once a selection is open the long press has already been made, so a brief rest is enough to tell a swipe-select from a scroll.
    const val SELECT_HOLD_ACTIVE_MS = 150L
    // A finger that lands while the grid moves faster than this (pixels per second) is stopping a scroll, not starting a selection; slower, it is a slight drift and selects.
    const val SELECT_FAST_SCROLL_PX_PER_S = 400f

    // Text that changes types itself over: the old letters go back one by one at the quicker pace, the new ones come in at the slower, with a block caret blinking while it runs (as in VADOS Bubble).
    const val TYPE_MS = 60L
    const val UNTYPE_MS = 30L
    const val CARET_BLINK_MS = 250L

    // An edit in crop goes into its undo history once the finger has rested this long, so one drag is one step.
    const val EDIT_SETTLE_MS = 350L

    // How long a delete or a move can still be taken back from the pill above the bar.
    const val UNDO_MS = 4500

    // A delete's restore pill leaves sooner: the trash keeps the photo anyway.
    const val TRASH_UNDO_MS = 2500
}
