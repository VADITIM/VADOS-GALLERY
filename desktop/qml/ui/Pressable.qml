import QtQuick
import Gallery

// Every pressable: down to 0.95 in 80ms, back on the overshoot; never a ripple (docs/DESIGN.md, press feedback). The right button is the desktop's long press.
Item {
    id: root

    default property alias content: face.data
    property bool isEnabled: true
    property real pressedScale: 0.95
    property int holdInterval: Motion.selectHold
    readonly property bool isHovered: area.containsMouse
    readonly property bool isPressed: area.pressed
    property alias cursorShape: area.cursorShape
    signal clicked
    signal doubleClicked
    signal held
    signal rightClicked

    implicitWidth: face.childrenRect.width
    implicitHeight: face.childrenRect.height

    Item {
        id: face
        anchors.fill: parent
        scale: area.pressed && root.isEnabled ? root.pressedScale : 1
        Behavior on scale {
            NumberAnimation {
                duration: area.pressed ? Motion.press : Motion.release
                easing.type: area.pressed ? Motion.powerTwoOut : Motion.backOut
            }
        }
    }

    MouseArea {
        id: area
        anchors.fill: parent
        enabled: root.isEnabled
        hoverEnabled: true
        acceptedButtons: Qt.LeftButton | Qt.RightButton
        cursorShape: Qt.PointingHandCursor
        pressAndHoldInterval: root.holdInterval
        onClicked: mouse => mouse.button === Qt.RightButton ? root.rightClicked() : root.clicked()
        onDoubleClicked: mouse => { if (mouse.button === Qt.LeftButton) root.doubleClicked() }
        onPressAndHold: mouse => { if (mouse.button === Qt.LeftButton) root.held() }
    }
}
