import QtQuick
import Gallery

// A slider that moves only by how far the pointer travels sideways, so passing over it changes nothing; a held one thickens its track (docs/SPEC.md, Settings).
Item {
    id: root

    property real value: 0
    property real from: 0
    property real to: 1
    property real stepSize: 0
    signal moved(real value)

    implicitHeight: 28 * Theme.dp
    implicitWidth: 200 * Theme.dp

    readonly property real share: (value - from) / Math.max(0.0001, to - from)

    Rectangle {
        id: track
        anchors.verticalCenter: parent.verticalCenter
        width: parent.width
        height: (drag.pressed ? 8 : 4) * Theme.dp
        radius: height / 2
        color: Theme.borderStrong
        Behavior on height { NumberAnimation { duration: Motion.stateChange; easing.type: Motion.backOut } }

        Rectangle {
            width: parent.width * root.share
            height: parent.height
            radius: height / 2
            color: Theme.accent
        }
    }

    Rectangle {
        anchors.verticalCenter: parent.verticalCenter
        x: root.share * (root.width - width)
        width: (drag.pressed ? 18 : 14) * Theme.dp
        height: width
        radius: width / 2
        color: Theme.textBright
        Behavior on width { NumberAnimation { duration: Motion.stateChange; easing.type: Motion.backOut } }
    }

    MouseArea {
        id: drag
        anchors.fill: parent
        cursorShape: Qt.SizeHorCursor
        property real startX: 0
        property real startValue: 0
        onPressed: mouse => {
            startX = mouse.x
            startValue = root.value
        }
        onPositionChanged: mouse => {
            let next = startValue + (mouse.x - startX) / Math.max(1, root.width) * (root.to - root.from)
            if (root.stepSize > 0)
                next = Math.round(next / root.stepSize) * root.stepSize
            next = Math.max(root.from, Math.min(root.to, next))
            if (next !== root.value)
                root.moved(next)
        }
    }

    // A wheel over the slider steps it, as a desktop slider is expected to.
    WheelHandler {
        onWheel: event => {
            const step = root.stepSize > 0 ? root.stepSize : (root.to - root.from) / 20
            const next = Math.max(root.from, Math.min(root.to, root.value + (event.angleDelta.y > 0 ? step : -step)))
            root.moved(next)
        }
    }
}
