import QtQuick
import Gallery

// A place in the sidebar: its glyph in the place's own colour, its name, and how many photos it holds.
Pressable {
    id: row

    property string glyph: ""
    property string label: ""
    property int count: -1
    property color accent: Theme.accent
    property bool isChosen: false

    height: 38 * Theme.dp
    pressedScale: 0.98

    Rectangle {
        anchors.fill: parent
        radius: height / 2
        color: Theme.pressedWash
        opacity: row.isChosen ? 1 : row.isHovered ? 0.5 : 0
        Behavior on opacity { NumberAnimation { duration: Motion.stateChange } }
    }

    Glyph {
        x: 12 * Theme.dp
        anchors.verticalCenter: parent.verticalCenter
        width: 18 * Theme.dp
        height: width
        name: row.glyph
        ink: row.accent
    }

    Text {
        x: 44 * Theme.dp
        anchors.verticalCenter: parent.verticalCenter
        text: row.label
        color: row.isChosen ? row.accent : row.isHovered ? Theme.textBright : Theme.textMuted
        font.family: Theme.mono
        font.pixelSize: Theme.labelSize
        font.letterSpacing: Theme.labelSpacing * 0.7
        Behavior on color { ColorAnimation { duration: Motion.stateChange } }
    }

    Text {
        anchors.right: parent.right
        anchors.rightMargin: 12 * Theme.dp
        anchors.verticalCenter: parent.verticalCenter
        visible: row.count >= 0
        text: row.count
        color: Theme.textFaint
        font.family: Theme.mono
        font.pixelSize: Theme.labelSize
    }
}
