import QtQuick
import Gallery

// A row of a menu sheet: its icon, its words, a divider under it. `splitGlyph` gives the row a second choice on its right 30%, after a slash, as its icon alone (docs/SPEC.md, the ••• menu).
Item {
    id: row

    property string glyph: ""
    property string label: ""
    property color ink: Theme.textBody
    property string splitGlyph: ""
    property bool isEnabled: true
    property bool hasDivider: true
    signal clicked
    signal splitClicked

    width: parent ? parent.width : 300
    height: 52 * Theme.dp
    opacity: isEnabled ? 1 : 0.38

    Pressable {
        id: main
        width: row.splitGlyph.length > 0 ? parent.width * 0.7 : parent.width
        height: parent.height
        pressedScale: 0.98
        isEnabled: row.isEnabled
        onClicked: row.clicked()

        Rectangle {
            anchors.fill: parent
            anchors.margins: 2 * Theme.dp
            radius: height / 2
            color: Theme.pressedWash
            opacity: main.isHovered ? 0.6 : 0
            Behavior on opacity { NumberAnimation { duration: Motion.stateChange } }
        }

        Glyph {
            x: 12 * Theme.dp
            anchors.verticalCenter: parent.verticalCenter
            width: 20 * Theme.dp
            height: width
            name: row.glyph
            ink: row.ink
            visible: row.glyph.length > 0
        }

        Text {
            x: row.glyph.length > 0 ? 46 * Theme.dp : 14 * Theme.dp
            width: parent.width - x - 8 * Theme.dp
            anchors.verticalCenter: parent.verticalCenter
            text: row.label
            color: row.ink === Theme.textBody ? Theme.textBright : row.ink
            font.family: Theme.mono
            font.pixelSize: Theme.navigationSize
            font.letterSpacing: Theme.navigationSpacing * 0.7
            elide: Text.ElideRight
        }
    }

    Text {
        visible: row.splitGlyph.length > 0
        x: parent.width * 0.7 - width / 2
        anchors.verticalCenter: parent.verticalCenter
        text: "/"
        color: Theme.textFaint
        font.family: Theme.mono
        font.pixelSize: 18 * Theme.dp
    }

    Pressable {
        id: split
        visible: row.splitGlyph.length > 0
        x: parent.width * 0.7
        width: parent.width * 0.3
        height: parent.height
        pressedScale: 0.9
        isEnabled: row.isEnabled
        onClicked: row.splitClicked()

        Glyph {
            anchors.centerIn: parent
            width: 22 * Theme.dp
            height: width
            name: row.splitGlyph
            ink: split.isHovered ? Theme.textBright : row.ink
        }
    }

    Rectangle {
        visible: row.hasDivider
        anchors.bottom: parent.bottom
        x: 12 * Theme.dp
        width: parent.width - 24 * Theme.dp
        height: 1
        color: Theme.border
    }
}
