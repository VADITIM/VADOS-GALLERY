import QtQuick
import Gallery

// Every delete waits on this pill above the bar, over anything already there; it deletes on a click, and anything else lets it go (VAS components/22).
Pop {
    id: root

    required property var selection
    isShown: selection.pendingDelete !== null

    Pressable {
        width: label.implicitWidth + 38 * Theme.dp
        height: label.implicitHeight + 20 * Theme.dp
        onClicked: root.selection.confirm()

        Rectangle {
            anchors.fill: parent
            radius: height / 2
            color: Theme.danger
        }

        // A fifth larger than the other labels, so the one irreversible click is the easiest to find.
        Text {
            id: label
            anchors.centerIn: parent
            text: "CONFIRM"
            color: Theme.sunkenDeep
            font.family: Theme.mono
            font.pixelSize: Theme.labelSize * 1.2
            font.letterSpacing: Theme.labelSpacing * 1.2
        }
    }
}
