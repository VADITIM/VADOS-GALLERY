import QtQuick
import Gallery

// A section or a place coming and going (docs/DESIGN.md § Motion): it enters after a 60ms gate, fading in and rising a fortieth of its height on the overshoot, and leaves at once, fading out, faster.
// `isFolder` is a place inside a section (an album, Private), which grows in from 0.96 instead of rising.
Item {
    id: root

    property bool isShown: false
    property bool isFolder: false
    property real entered: isShown ? 1 : 0
    property real leaveOpacity: 1
    readonly property bool isLive: isShown || leaving.running

    visible: isLive || entered > 0
    opacity: isShown ? Math.min(1, entered * 1.4) : leaveOpacity
    transform: [
        Translate { y: root.isFolder || !root.isShown ? 0 : (1 - root.entered) * root.height / 40 },
        Scale {
            origin.x: root.width / 2
            origin.y: root.height / 2
            xScale: root.isFolder && root.isShown ? 0.96 + 0.04 * root.entered : 1
            yScale: xScale
        }
    ]

    onIsShownChanged: {
        if (isShown) {
            leaving.stop()
            entered = 0
            entering.restart()
        } else {
            entering.stop()
            leaveOpacity = opacity
            leaving.restart()
        }
    }

    SequentialAnimation {
        id: entering
        PauseAnimation { duration: Motion.sectionEnterDelay }
        NumberAnimation {
            target: root
            property: "entered"
            to: 1
            duration: Motion.sectionEnter
            easing.type: root.isFolder ? Motion.powerTwoOut : Motion.backOut
        }
    }

    SequentialAnimation {
        id: leaving
        NumberAnimation { target: root; property: "leaveOpacity"; to: 0; duration: Motion.sectionLeave; easing.type: Motion.powerTwoIn }
        ScriptAction { script: root.entered = 0 }
    }
}
