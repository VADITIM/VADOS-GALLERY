import QtQuick
import Gallery

// A button a bar holds only sometimes (Set as cover at one photo, Remove from group): leaving, it pops away while the bar narrows round it; arriving, the bar widens and then it pops in (VAS components/19 §2).
Item {
    id: root

    property bool isShown: false
    property string glyph: ""
    property color ink: Theme.textBody
    property real barPop: 1
    property real room: isShown ? 1 : 0
    property real pop: isShown ? 1 : 0
    signal clicked

    implicitWidth: button.implicitWidth * room
    implicitHeight: button.implicitHeight

    onIsShownChanged: {
        if (isShown) {
            leave.stop()
            arrive.restart()
        } else {
            arrive.stop()
            leave.restart()
        }
    }

    ParallelAnimation {
        id: arrive
        NumberAnimation { target: root; property: "room"; to: 1; duration: Motion.stateChange * 2; easing.type: Motion.powerThreeInOut }
        SequentialAnimation {
            PauseAnimation { duration: Motion.stateChange }
            NumberAnimation { target: root; property: "pop"; to: 1; duration: Motion.stateChange; easing.type: Motion.backOut }
        }
    }

    ParallelAnimation {
        id: leave
        NumberAnimation { target: root; property: "pop"; to: 0; duration: Motion.stateChange; easing.type: Motion.backIn }
        NumberAnimation { target: root; property: "room"; to: 0; duration: Motion.stateChange * 2; easing.type: Motion.powerThreeInOut }
    }

    IconButton {
        id: button
        anchors.centerIn: parent
        glyph: root.glyph
        ink: root.ink
        pop: root.barPop * root.pop
        onClicked: root.clicked()
    }
}
