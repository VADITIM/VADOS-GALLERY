import QtQuick
import Gallery

// The glass sheet (VAS components/17): up from a sixth below and 0.96 on the overshoot, down an eighth to 0.98 and gone; a pull from anywhere on it brings it down with the finger,
// and letting go past a quarter of its height (or fast) finishes the close from there. A click outside or Escape closes it too.
Item {
    id: sheet

    default property alias content: body.data
    property bool isOpen: false
    property real sheetWidth: Math.min(parent ? parent.width - 32 * Theme.dp : 480, 480 * Theme.dp)
    property real contentHeight: body.childrenRect.height
    property real shown: 0
    property real drag: 0
    property bool hasScrim: true
    readonly property bool isVisible: shown > 0 || isOpen
    signal dismissed

    anchors.fill: parent
    visible: isVisible
    z: 100

    function open() {
        drag = 0
        isOpen = true
    }
    function close() {
        if (!isOpen)
            return
        isOpen = false
        dismissed()
    }

    onIsOpenChanged: {
        if (isOpen) {
            leave.stop()
            arrive.restart()
        } else {
            arrive.stop()
            leave.from = shown
            leave.restart()
        }
    }

    NumberAnimation { id: arrive; target: sheet; property: "shown"; from: 0; to: 1; duration: Motion.overlayEnter; easing.type: Motion.backOut }
    NumberAnimation { id: leave; target: sheet; property: "shown"; to: 0; duration: Motion.overlayLeave; easing.type: Motion.powerTwoIn; onFinished: sheet.drag = 0 }
    NumberAnimation { id: dragBack; target: sheet; property: "drag"; to: 0; duration: Motion.stateChange; easing.type: Motion.backOut }

    Rectangle {
        anchors.fill: parent
        visible: sheet.hasScrim
        color: Qt.rgba(0, 0, 0, 0.45)
        opacity: Math.min(1, sheet.shown) * (1 - Math.min(1, sheet.drag / Math.max(1, panel.height)))

        MouseArea {
            anchors.fill: parent
            enabled: sheet.isOpen
            acceptedButtons: Qt.AllButtons
            onClicked: sheet.close()
            onWheel: wheel => wheel.accepted = true
        }
    }

    Item {
        id: panel
        readonly property bool isArriving: sheet.isOpen
        width: sheet.sheetWidth
        height: Math.min(sheet.height - 48 * Theme.dp, sheet.contentHeight + 36 * Theme.dp)
        x: (sheet.width - width) / 2
        // Arriving from a sixth of its height below; leaving an eighth down.
        y: sheet.height - height - 20 * Theme.dp + sheet.drag + (1 - sheet.shown) * height * (isArriving ? 1 / 6 : 1 / 8)
        opacity: Math.min(1, sheet.shown * 1.5)
        scale: isArriving ? 0.96 + 0.04 * sheet.shown : 0.98 + 0.02 * sheet.shown

        Glass {
            anchors.fill: parent
            radius: Theme.sheetRadius
        }

        // A pull from anywhere on the sheet.
        MouseArea {
            anchors.fill: parent
            property real pressY: 0
            property real lastY: 0
            property double lastTime: 0
            property real speed: 0
            onPressed: mouse => {
                pressY = mapToItem(sheet, 0, mouse.y).y
                lastY = pressY
                lastTime = Date.now()
                dragBack.stop()
            }
            onPositionChanged: mouse => {
                const y = mapToItem(sheet, 0, mouse.y).y
                const now = Date.now()
                speed = (y - lastY) / Math.max(1, now - lastTime) * 1000
                lastY = y
                lastTime = now
                sheet.drag = Math.max(0, y - pressY)
            }
            onReleased: {
                if (sheet.drag > panel.height * 0.25 || speed > 1200)
                    sheet.close()
                else {
                    dragBack.from = sheet.drag
                    dragBack.restart()
                }
            }
        }

        Item {
            id: body
            x: 18 * Theme.dp
            y: 18 * Theme.dp
            width: parent.width - 36 * Theme.dp
            height: parent.height - 36 * Theme.dp
            clip: sheet.contentHeight > height
        }
    }

    Keys.onEscapePressed: close()
}
