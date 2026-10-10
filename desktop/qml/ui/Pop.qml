import QtQuick
import Gallery

// Something that changes in place pops: it grows in past full size and settles, and shrinks away to nothing, never sliding (VAS dna/05 §6.9). `delay` holds the arrival until what it replaces has gone.
Item {
    id: root

    property bool isShown: true
    property int delay: 0
    property real pop: isShown ? 1 : 0
    default property alias content: holder.data

    implicitWidth: holder.childrenRect.width
    implicitHeight: holder.childrenRect.height
    visible: pop > 0

    onIsShownChanged: {
        if (isShown) {
            out.stop()
            arrive.restart()
        } else {
            arrive.stop()
            out.restart()
        }
    }

    SequentialAnimation {
        id: arrive
        PauseAnimation { duration: root.delay }
        NumberAnimation { target: root; property: "pop"; to: 1; duration: Motion.stateChange; easing.type: Motion.backOut }
    }

    NumberAnimation {
        id: out
        target: root
        property: "pop"
        to: 0
        duration: Motion.stateChange
        easing.type: Motion.backIn
    }

    Item {
        id: holder
        anchors.fill: parent
        opacity: Math.min(1, root.pop * 1.5)
        transform: Scale {
            origin.x: holder.width / 2
            origin.y: holder.height / 2
            xScale: Math.max(0, root.pop)
            yScale: Math.max(0, root.pop)
        }
    }
}
