import QtQuick
import Gallery

// Every one-of-many pick (columns, the folder label, the settings tabs, the nav mode) is this one track: the pill follows the finger and settles on the nearest stop when let go,
// and a tapped stop slides there with the nav's two edges, the leading one first (VAS components/19 §3).
Item {
    id: root

    property var options: []
    property int currentIndex: 0
    property bool isEnabled: true
    property bool isGlass: false
    property color accent: Theme.accent
    property real itemHeight: 34 * Theme.dp
    // Set while a finger drags the pill, so a sheet following the tabs can follow it too.
    readonly property real dragPosition: isDragging ? dragIndex : currentIndex
    property bool isDragging: false
    property real dragIndex: 0
    signal picked(int index)

    implicitHeight: itemHeight + 8 * Theme.dp
    implicitWidth: 240 * Theme.dp
    opacity: isEnabled ? 1 : 0.38

    readonly property real slotWidth: (width - 8 * Theme.dp) / Math.max(1, options.length)

    property real washLeft: 4 * Theme.dp + currentIndex * slotWidth
    property real washRight: washLeft + slotWidth

    function slideTo(index: int) {
        const toLeft = 4 * Theme.dp + index * slotWidth
        const toRight = toLeft + slotWidth
        // Before the track is laid out there is nothing to slide from: the pill is simply there.
        if (width <= 0 || !visible) {
            slide.stop()
            washLeft = toLeft
            washRight = toRight
            return
        }
        const isMovingRight = toRight > washRight
        leftEdge.to = toLeft
        rightEdge.to = toRight
        leftEdge.delay = isMovingRight ? Motion.navTrail : 0
        rightEdge.delay = isMovingRight ? 0 : Motion.navTrail
        slide.restart()
    }

    onCurrentIndexChanged: if (!isDragging) slideTo(currentIndex)
    onWidthChanged: {
        slide.stop()
        washLeft = 4 * Theme.dp + currentIndex * slotWidth
        washRight = washLeft + slotWidth
    }

    ParallelAnimation {
        id: slide
        SequentialAnimation {
            PauseAnimation { duration: leftEdge.delay }
            NumberAnimation { id: leftEdge; property real delay: 0; target: root; property: "washLeft"; duration: Motion.navSlide; easing.type: Motion.powerTwoOut }
        }
        SequentialAnimation {
            PauseAnimation { duration: rightEdge.delay }
            NumberAnimation { id: rightEdge; property real delay: 0; target: root; property: "washRight"; duration: Motion.navSlide; easing.type: Motion.powerTwoOut }
        }
    }

    Glass {
        anchors.fill: parent
        visible: root.isGlass
    }

    Rectangle {
        anchors.fill: parent
        visible: !root.isGlass
        radius: height / 2
        color: Theme.sunkenDeep
    }

    Rectangle {
        y: 4 * Theme.dp
        x: root.washLeft
        width: Math.max(0, root.washRight - root.washLeft)
        height: root.itemHeight
        radius: height / 2
        color: root.accent
    }

    Repeater {
        model: root.options

        Text {
            required property int index
            required property var modelData
            readonly property real distance: Math.abs((root.washLeft + root.washRight) / 2 - (x + width / 2)) / root.slotWidth
            x: 4 * Theme.dp + index * root.slotWidth
            y: 4 * Theme.dp
            width: root.slotWidth
            height: root.itemHeight
            text: String(modelData).toUpperCase()
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
            font.family: Theme.mono
            font.pixelSize: Theme.navigationSize
            font.letterSpacing: Theme.navigationSpacing * 0.6
            // Ink under the pill, bright beside it, eased by how far the pill is.
            color: Theme.mix(Theme.textBright, Theme.sunkenDeep, Math.min(1, distance))
            elide: Text.ElideRight
        }
    }

    MouseArea {
        anchors.fill: parent
        enabled: root.isEnabled
        cursorShape: Qt.PointingHandCursor
        property real pressX: 0
        property bool isMoved: false
        onPressed: mouse => {
            pressX = mouse.x
            isMoved = false
        }
        onPositionChanged: mouse => {
            if (!isMoved && Math.abs(mouse.x - pressX) < 4 * Theme.dp)
                return
            isMoved = true
            root.isDragging = true
            slide.stop()
            const centre = Math.max(root.slotWidth / 2, Math.min(root.width - root.slotWidth / 2, mouse.x))
            root.dragIndex = (centre - 4 * Theme.dp - root.slotWidth / 2) / root.slotWidth
            root.washLeft = 4 * Theme.dp + root.dragIndex * root.slotWidth
            root.washRight = root.washLeft + root.slotWidth
        }
        onReleased: mouse => {
            const index = isMoved ? Math.round(root.dragIndex) : Math.floor((mouse.x - 4 * Theme.dp) / root.slotWidth)
            const bounded = Math.max(0, Math.min(root.options.length - 1, index))
            root.isDragging = false
            root.slideTo(bounded)
            if (bounded !== root.currentIndex)
                root.picked(bounded)
        }
    }
}
