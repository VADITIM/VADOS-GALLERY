pragma Singleton

import QtQuick
import Gallery

// core/design: the only place a colour, a face or a size is written down (VAS architecture), the gallery's Palette.kt and Type.kt. Every leaf reads `accent`; none names one.
QtObject {
    id: theme

    // #region ── ground and structure ──────────────────────────────────────────────────────────
    // The ground is the user's: a grey from black up to a dark charcoal (64 of 255), so every surface on it follows a change at once.
    readonly property real groundLevel: Settings.groundBrightness * 64 / 255
    readonly property color ground: Qt.rgba(groundLevel, groundLevel, groundLevel, 1)
    readonly property color panel: Qt.rgba(18 / 255, 18 / 255, 18 / 255, 0.85)
    readonly property color surface: "#202020"
    readonly property color pressedWash: Qt.rgba(1, 1, 1, 0.12)
    readonly property color panelSolid: "#121212"
    readonly property color sunkenDeep: "#0e0e0e"
    readonly property color sunken: "#1c1c1c"
    readonly property color border: "#383838"
    readonly property color borderStrong: "#454545"
    readonly property color borderControl: "#5a5a5a"
    // Photos are the only colour that matters in the viewer, so it stands on black rather than the ground.
    readonly property color viewerGround: "#000000"
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── text ramp ──────────────────────────────────────────────────────────────────────
    readonly property color textPrimary: Qt.rgba(1, 1, 1, 0.87)
    readonly property color textBright: "#f0f0f0"
    readonly property color textBody: "#d8d8d8"
    readonly property color textMuted: "#bdbdbd"
    readonly property color textLabel: "#b0b0b0"
    readonly property color textIcon: "#8e8e8e"
    readonly property color textFaint: "#707070"
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── accents ───────────────────────────────────────────────────────────────────────
    readonly property color danger: "#ff6b6b"
    // Every heart is this red, whatever section it is in.
    readonly property color favorite: "#ff3b4e"
    readonly property color terminalGreen: "#2fde75"
    readonly property color amber: "#f09b3a"
    readonly property color hotPink: "#ff2e88"
    readonly property color privateRed: "#fa3438"
    readonly property color locationBlue: "#148bc7"
    readonly property color trashGray: "#9a9a9a"
    readonly property color cropViolet: "#7e55dd"

    // The accent now, taken on the cut once the outgoing view has left (VAS components/19 §5); `accentTarget` is the place being switched to, at once.
    property color accent: terminalGreen
    property color accentTarget: terminalGreen

    function alpha(base: color, value: real): color {
        return Qt.rgba(base.r, base.g, base.b, value)
    }

    function mix(first: color, second: color, amount: real): color {
        return Qt.rgba(first.r * amount + second.r * (1 - amount), first.g * amount + second.g * (1 - amount),
                       first.b * amount + second.b * (1 - amount), first.a * amount + second.a * (1 - amount))
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── faces ─────────────────────────────────────────────────────────────────────────
    readonly property var installedFamilies: Qt.fontFamilies()
    function firstInstalled(candidates: var): string {
        for (const candidate of candidates)
            if (installedFamilies.indexOf(candidate) !== -1)
                return candidate
        return "monospace"
    }
    readonly property string mono: firstInstalled(["Space Mono", "SpaceMono", "DejaVu Sans Mono"])
    readonly property string heading: firstInstalled(["Audiowide", mono])
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── scale ─────────────────────────────────────────────────────────────────────────
    // The single scale lever (VAS layout): a ratio of the screen, not the window, so a tiled half-width window keeps its type size. `dp` carries the phone's numbers across unchanged.
    property real screenWidth: 1920
    readonly property real rem: Math.max(16, Math.min(40, screenWidth * 0.0108)) * Settings.userScale
    readonly property real dp: rem / 20

    // Sheets 30dp, panels 22dp, covers 20dp as squircles; thumbnails 8dp; superellipse corners read smaller, so they are drawn larger.
    readonly property real sheetRadius: 30 * dp * 1.45
    readonly property real panelRadius: 22 * dp * 1.45
    readonly property real coverRadius: 20 * dp * 1.45
    readonly property real tileRadius: 8 * dp

    // Type.kt's roles, in dp: the micro-label (mono, uppercase, wide tracking), the nav, card titles in the heading face, values, and the title.
    readonly property real labelSize: 10 * dp
    readonly property real labelSpacing: 3 * dp
    readonly property real navigationSize: 11 * dp
    readonly property real navigationSpacing: 2 * dp
    readonly property real cardTitleSize: 13 * dp
    readonly property real valueSize: 13 * dp
    readonly property real bodySize: 13 * dp
    readonly property real titleSize: 28 * dp
    // #endregion ───────────────────────────────────────────────────────────────────────────────
}
