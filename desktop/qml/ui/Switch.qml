import QtQuick
import Gallery

// Outlined with a bright knob while off, filled with the accent while on, the knob sliding on the overshoot (docs/SPEC.md, Settings).
Pressable {
    id: root

    property bool isOn: false
    signal toggled

    implicitWidth: 44 * Theme.dp
    implicitHeight: 26 * Theme.dp
    onClicked: toggled()

    Rectangle {
        anchors.fill: parent
        radius: height / 2
        color: root.isOn ? Theme.accent : "transparent"
        border.width: root.isOn ? 0 : 1.5 * Theme.dp
        border.color: Theme.borderControl
        Behavior on color { ColorAnimation { duration: Motion.stateChange } }

        Rectangle {
            id: knob
            width: parent.height - 8 * Theme.dp
            height: width
            radius: width / 2
            anchors.verticalCenter: parent.verticalCenter
            x: root.isOn ? parent.width - width - 4 * Theme.dp : 4 * Theme.dp
            color: root.isOn ? Theme.sunkenDeep : Theme.textBright
            Behavior on x { NumberAnimation { duration: Motion.stateChange; easing.type: Motion.backOut } }
            Behavior on color { ColorAnimation { duration: Motion.stateChange } }
        }
    }
}
