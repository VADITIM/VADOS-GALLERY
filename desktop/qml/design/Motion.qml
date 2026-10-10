pragma Singleton

import QtQuick
import Gallery

// The gallery tempo (Motion.kt): VAD/OS TERMINAL's ceiling, nothing a finger waits on past 0.3s. Durations and curves come from here, never inline numbers.
QtObject {
    readonly property bool isReduced: System.prefersReducedMotion

    // #region ── curves ────────────────────────────────────────────────────────────────────────
    // GSAP's names, Qt's curves: power2 is quad, power3 cubic, back is back with GSAP's 1.70158.
    readonly property int backOut: Easing.OutBack
    readonly property int backIn: Easing.InBack
    readonly property int powerTwoOut: Easing.OutQuad
    readonly property int powerTwoIn: Easing.InQuad
    readonly property int powerThreeInOut: Easing.InOutCubic
    readonly property int powerThreeOut: Easing.OutCubic
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── clocks (ms) ───────────────────────────────────────────────────────────────────
    readonly property int sectionEnter: isReduced ? 0 : 260
    readonly property int sectionEnterDelay: isReduced ? 0 : 60
    readonly property int sectionLeave: isReduced ? 0 : 120

    readonly property int viewerEnter: isReduced ? 0 : 280
    readonly property int viewerLeave: isReduced ? 0 : 160
    readonly property int viewerClose: isReduced ? 0 : 260

    readonly property int overlayEnter: isReduced ? 0 : 240
    readonly property int overlayLeave: isReduced ? 0 : 140

    readonly property int press: 80
    readonly property int release: 220
    readonly property int stateChange: isReduced ? 0 : 220

    readonly property int burst: 520
    readonly property int heartDrain: 380

    readonly property int morphDelay: 80
    readonly property int morph: 160
    readonly property int riseDelay: 80
    readonly property int rise: 280
    readonly property int riseStagger: 40

    readonly property int stack: 360
    readonly property int jiggle: 130
    readonly property int chromeStagger: 45

    // The nav highlight: its leading edge goes first, the trailing edge follows.
    readonly property int navSlide: 240
    readonly property int navTrail: 60

    readonly property int entrance: 240
    readonly property int entranceStagger: 12
    readonly property int entranceWindow: 350

    readonly property int timelineReveal: 180
    readonly property int timelineLift: 240

    readonly property int sweepGrow: 420
    readonly property int sweepRetract: 500
    readonly property int sweepLeave: 300

    // Holding a tile to select waits past the system's long press, so a resting hand does not select by accident.
    readonly property int selectHold: 520
    readonly property int selectHoldActive: 150
    readonly property int coverHold: 650

    readonly property int typeLetter: 60
    readonly property int untypeLetter: 30
    readonly property int caretBlink: 250

    readonly property int undo: 2500
    readonly property int undoMove: 4500

    readonly property int scrollToEnd: 400
    readonly property int scrollToEndPerScreen: 60
    readonly property int scrollToEndMax: 1100
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── curves as functions ────────────────────────────────────────────────────────────
    // The same curves for a motion driven by a progress (a pull, a seek) rather than a clock.
    function backOutAt(t: real): real {
        const c1 = 1.70158
        return 1 + (c1 + 1) * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2)
    }
    function backInAt(t: real): real {
        const c1 = 1.70158
        return (c1 + 1) * t * t * t - c1 * t * t
    }
    function powerTwoOutAt(t: real): real {
        return 1 - (1 - t) * (1 - t)
    }
    function powerTwoInAt(t: real): real {
        return t * t
    }
    function powerThreeInOutAt(t: real): real {
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2
    }
    function clamp(value: real, low: real, high: real): real {
        return Math.max(low, Math.min(high, value))
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── shares ────────────────────────────────────────────────────────────────────────
    // The share of a pull at which the viewer's buttons have fully left, so the bar never lags the photo.
    readonly property real chromePullShare: 0.35
    // How far down a photo is pulled before letting go closes it, as a share of the window's height.
    readonly property real dismissShare: 0.12
    // #endregion ───────────────────────────────────────────────────────────────────────────────
}
