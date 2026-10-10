import QtQuick
import Gallery

// The favourite button (Icons.kt FavoriteHeart): turning on, the heart pops while dots and four-point sparkles fly off it, like a like button;
// turning off, its fill drains out from the top while it dips a little, rather than swapping icons. Swiping to another photo plays neither.
Pressable {
    id: root

    property bool isFavorite: false
    property string path: ""
    property real pop: 1
    property real glyphSize: 22 * Theme.dp
    property real burst: 1
    property real drain: 0
    property string lastPath: path

    implicitWidth: 56 * Theme.dp
    implicitHeight: 48 * Theme.dp
    isEnabled: pop > 0.5
    transform: Scale {
        origin.x: root.width / 2
        origin.y: root.height / 2
        xScale: root.pop
        yScale: root.pop
    }

    onPathChanged: {
        burstRun.stop()
        drainRun.stop()
        burst = 1
        drain = 0
        lastPath = path
    }
    onIsFavoriteChanged: {
        if (path !== lastPath)
            return
        if (isFavorite) {
            drainRun.stop()
            drain = 0
            burstRun.restart()
        } else {
            // A like taken back mid-burst stops its sparkles where they are.
            burstRun.stop()
            burst = 1
            drainRun.restart()
        }
    }

    NumberAnimation { id: burstRun; target: root; property: "burst"; from: 0; to: 1; duration: Motion.burst }
    NumberAnimation { id: drainRun; target: root; property: "drain"; from: 1; to: 0; duration: Motion.heartDrain; easing.type: Motion.powerThreeInOut }

    // The heart swells past its size and settles back; draining, it dips a little and comes back.
    readonly property real swell: burst < 1 ? 1 + 0.35 * Math.sin(Math.PI * Motion.backOutAt(burst)) : drain > 0 ? 1 - 0.1 * Math.sin(Math.PI * drain) : 1
    readonly property color ink: isFavorite ? Theme.favorite : root.isHovered ? Theme.textBright : Theme.textBody

    Canvas {
        id: particles
        anchors.centerIn: parent
        width: root.glyphSize * 3
        height: width
        visible: root.burst < 1
        property real progress: root.burst
        onProgressChanged: requestPaint()
        onPaint: {
            const context = getContext("2d")
            context.reset()
            const centre = width / 2
            const size = root.glyphSize
            const reach = Motion.powerTwoOutAt(progress)
            const fade = 1 - Motion.powerTwoInAt(progress)
            context.fillStyle = Qt.rgba(Theme.favorite.r, Theme.favorite.g, Theme.favorite.b, fade)
            for (let index = 0; index < 8; ++index) {
                const angle = 2 * Math.PI * index / 8
                const distance = size * (0.55 + 0.45 * reach)
                context.beginPath()
                context.arc(centre + Math.cos(angle) * distance, centre + Math.sin(angle) * distance, Math.max(0, size * 0.07 * (1 - progress)), 0, 2 * Math.PI)
                context.fill()
            }
            context.fillStyle = Qt.rgba(1, 1, 1, fade)
            for (let index = 0; index < 4; ++index) {
                // Offset half a step from the dots so the two rings interleave.
                const angle = 2 * Math.PI * (index + 0.5) / 4
                const distance = size * (0.45 + 0.5 * reach)
                const x = centre + Math.cos(angle) * distance
                const y = centre + Math.sin(angle) * distance
                const arm = size * 0.2 * Math.sin(Math.PI * progress)
                context.beginPath()
                context.moveTo(x, y - arm)
                context.quadraticCurveTo(x, y, x + arm, y)
                context.quadraticCurveTo(x, y, x, y + arm)
                context.quadraticCurveTo(x, y, x - arm, y)
                context.quadraticCurveTo(x, y, x, y - arm)
                context.fill()
            }
        }
    }

    Item {
        anchors.centerIn: parent
        width: root.glyphSize * root.swell
        height: width

        Glyph {
            anchors.fill: parent
            name: root.isFavorite ? "heart-filled" : "heart"
            ink: root.ink
        }

        // What is left of the fill under the outline, its surface sinking toward the tip.
        Item {
            visible: !root.isFavorite && root.drain > 0
            readonly property real fillTop: parent.height * (20.5 - 18 * root.drain) / 24
            y: fillTop
            width: parent.width
            height: parent.height - fillTop
            clip: true

            Glyph {
                y: -parent.fillTop
                width: parent.width
                height: parent.width
                name: "heart-filled"
                ink: Theme.favorite
            }
        }
    }
}
