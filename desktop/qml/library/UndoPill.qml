import QtQuick
import Gallery

// What just happened, for a few seconds, with the way back when there is one (VAS components/22). It rises its own height on the overshoot and drops when its time is up.
// A solid pane rather than glass: it also floats over the viewer, where the blur would sample the grid hidden behind the photo.
Item {
    id: root

    property string message: ""
    property bool canUndo: false
    property bool isShown: false
    property real shown: 0

    implicitWidth: pill.width
    implicitHeight: pill.height
    visible: shown > 0

    function offer(text: string, isUndoable: bool) {
        message = text
        canUndo = isUndoable
        isShown = true
        expiry.restart()
    }

    onIsShownChanged: {
        if (isShown) {
            drop.stop()
            rise.restart()
        } else {
            rise.stop()
            drop.restart()
        }
    }

    Timer {
        id: expiry
        interval: Motion.undo
        onTriggered: root.isShown = false
    }

    NumberAnimation { id: rise; target: root; property: "shown"; to: 1; duration: Motion.overlayEnter; easing.type: Motion.backOut }
    NumberAnimation { id: drop; target: root; property: "shown"; to: 0; duration: Motion.overlayLeave; easing.type: Motion.powerTwoIn }

    Item {
        id: pill
        width: row.implicitWidth + 18 * Theme.dp + (root.canUndo ? 4 : 18) * Theme.dp
        height: 52 * Theme.dp
        opacity: Math.min(1, root.shown)
        transform: Translate { y: (1 - root.shown) * pill.height }

        Rectangle {
            anchors.fill: parent
            radius: height / 2
            color: Theme.panelSolid
        }

        Row {
            id: row
            x: 18 * Theme.dp
            anchors.verticalCenter: parent.verticalCenter
            spacing: 6 * Theme.dp

            Text {
                anchors.verticalCenter: parent.verticalCenter
                text: root.message
                color: Theme.textBright
                font.family: Theme.mono
                font.pixelSize: Theme.valueSize
            }

            IconButton {
                visible: root.canUndo
                anchors.verticalCenter: parent.verticalCenter
                glyph: "restore"
                ink: Theme.accent
                onClicked: {
                    root.isShown = false
                    Actions.undo()
                }
            }
        }
    }
}
