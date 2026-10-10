import QtQuick
import Gallery

// Where you are, above the nav (PRIVATE, TRASH): a back arrow beside the label, both ink on the place colour; the label is a way out as much as the arrow (VAS components/19 §4).
Row {
    id: root

    property string label: ""
    property color accent: Theme.accent
    signal exited

    spacing: 8 * Theme.dp

    Pressable {
        width: 36 * Theme.dp
        height: 28 * Theme.dp
        onClicked: root.exited()
        Rectangle {
            anchors.fill: parent
            radius: height / 2
            color: root.accent
            Glyph {
                anchors.centerIn: parent
                width: 16 * Theme.dp
                height: 16 * Theme.dp
                name: "back"
                ink: Theme.sunkenDeep
            }
        }
    }

    Pressable {
        width: text.implicitWidth + 24 * Theme.dp
        height: 28 * Theme.dp
        onClicked: root.exited()
        Rectangle {
            anchors.fill: parent
            radius: height / 2
            color: root.accent
            Text {
                id: text
                anchors.centerIn: parent
                text: root.label
                color: Theme.sunkenDeep
                font.family: Theme.mono
                font.pixelSize: Theme.labelSize
                font.letterSpacing: Theme.labelSpacing
                font.bold: true
            }
        }
    }
}
