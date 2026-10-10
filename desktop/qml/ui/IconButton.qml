import QtQuick
import Gallery

// Every action in a bar or a menu: an icon, never a word, 56×48 around a 22 glyph (VAS components/19 §7). `pop` is handed in by the bar it sits in, so each button comes and goes on its own.
Pressable {
    id: root

    property string glyph: ""
    property color ink: Theme.textBody
    property real glyphSize: 22 * Theme.dp
    property real pop: 1
    // The delete waiting on Confirm stays marked, so it is clear what Confirm is for.
    property bool isPending: false

    implicitWidth: 56 * Theme.dp
    implicitHeight: 48 * Theme.dp
    // A button popped away keeps its room (the bar measures it) but takes no clicks.
    isEnabled: pop > 0.5
    transform: Scale {
        origin.x: root.width / 2
        origin.y: root.height / 2
        xScale: root.pop
        yScale: root.pop
    }

    Rectangle {
        anchors.fill: parent
        radius: height / 2
        color: Theme.alpha(Theme.danger, 0.22)
        opacity: root.isPending ? 1 : 0
        Behavior on opacity { NumberAnimation { duration: Motion.stateChange } }
    }

    Glyph {
        anchors.centerIn: parent
        width: root.glyphSize
        height: root.glyphSize
        name: root.glyph
        ink: root.isHovered ? Qt.lighter(root.ink, 1.18) : root.ink
    }
}
