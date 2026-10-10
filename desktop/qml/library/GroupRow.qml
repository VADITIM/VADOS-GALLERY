import QtQuick
import Gallery

// A group of albums (AlbumsScreen.kt GroupRow, VAS components/18): shut, its albums lie as a leaning stack with the name and count beside it, and an arrow between them pointing right.
// Opened, the same cards lift off the stack into rows of three on one 360ms clock, the arrow travels to the right end of the heading turning over the middle of its way,
// and the name stands over the cards as a heading. A sideways drag drives the same clock with the pointer; letting go far enough carries it on, otherwise it goes back.
Item {
    id: row

    required property var shelf
    required property var group
    readonly property string groupName: group.name
    readonly property var block: shelf.layout.groups[groupName] ?? { y: 0, height: 0, closedHeight: 0 }
    // Time through opening or closing, 0 shut to 1 open, run evenly; each card turns it into its own eased, staggered share.
    property real time: shelf.isGroupOpen(groupName) ? 1 : 0
    property bool isOpening: true
    readonly property real progress: time
    readonly property bool isMoving: clock.running || drag.isDragging
    readonly property bool isOpen: time > 0.5
    readonly property bool isDropTarget: shelf.hoveredGroup === groupName && shelf.isHoverReady
    readonly property real nameX: shelf.sideInset + shelf.stackEdge + 18 * Theme.dp + 30 * Theme.dp
    readonly property int photoCount: group.albums.reduce((sum, album) => sum + (album.count ?? 0), 0)

    x: 0
    y: block.y
    width: shelf.width
    height: block.height
    // A group swells a little while an album hangs over it, ready to go in.
    scale: isDropTarget ? 1.03 : 1
    Behavior on scale { NumberAnimation { duration: Motion.stateChange; easing.type: Motion.backOut } }

    function setOpen(open: bool) {
        isOpening = open
        clock.from = time
        clock.to = open ? 1 : 0
        // Started part way, by a drag, it takes only the share of the time that is left.
        clock.duration = Motion.stack * Math.abs(clock.to - time)
        clock.restart()
        shelf.setGroupOpen(groupName, open)
    }

    NumberAnimation {
        id: clock
        target: row
        property: "time"
    }

    // #region ── shut: the name and count beside the stack ─────────────────────────────────────
    // Sliced away from its left end as the cards leave, and given back the same way.
    Item {
        x: row.nameX
        y: shelf.groupGap + (shelf.stackEdge - height) / 2
        width: Math.max(0, (row.width - x - shelf.sideInset) * (1 - row.time))
        height: shutColumn.implicitHeight
        clip: true
        visible: row.time < 1

        Column {
            id: shutColumn
            anchors.right: parent.right
            width: row.width - row.nameX - shelf.sideInset
            spacing: 6 * Theme.dp
            FadeText {
                width: parent.width
                text: row.groupName
                color: Theme.textBright
                font.family: Theme.heading
                font.pixelSize: 20 * Theme.dp
            }
            Text {
                text: row.group.albums.length + (row.group.albums.length === 1 ? " album · " : " albums · ") + row.photoCount
                color: Theme.textMuted
                font.family: Theme.mono
                font.pixelSize: Theme.valueSize
            }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── open: the heading ─────────────────────────────────────────────────────────────
    // It arrives with a sweep from its left end and is cut once the arrow is back.
    Item {
        x: shelf.sideInset
        y: shelf.groupGap
        width: Math.max(0, (row.width - shelf.sideInset * 2 - 40 * Theme.dp) * Math.max(0, (row.time - 0.3) / 0.7))
        height: shelf.headingHeight
        clip: true
        visible: row.time > 0.3

        Row {
            anchors.verticalCenter: parent.verticalCenter
            spacing: 12 * Theme.dp
            Text {
                text: row.groupName
                color: Theme.accent
                font.family: Theme.heading
                font.pixelSize: 18 * Theme.dp
            }
            Text {
                anchors.baseline: parent.children[0].baseline
                text: row.photoCount
                color: Theme.textMuted
                font.family: Theme.mono
                font.pixelSize: Theme.labelSize
            }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // From left of the shut name, pointing right, to the right end of the heading, pointing left, at the heading's height; it turns over the middle of its way.
    Glyph {
        id: arrow
        readonly property real fromX: shelf.sideInset + shelf.stackEdge + 18 * Theme.dp
        readonly property real toX: row.width - shelf.sideInset - width
        readonly property real fromY: shelf.groupGap + (shelf.stackEdge - height) / 2
        readonly property real toY: shelf.groupGap + (shelf.headingHeight - height) / 2
        readonly property real way: Motion.powerThreeInOutAt(row.time)
        x: fromX + (toX - fromX) * way
        y: fromY + (toY - fromY) * way
        width: 20 * Theme.dp
        height: width
        name: "forward"
        rotation: 180 * Motion.clamp((way - 0.25) / 0.5, 0, 1)
        ink: row.time > 0.5 ? Theme.accent : Theme.textMuted
    }

    // The group's own pointer: a click on the stack, the name or the arrow opens it; open, a click on the heading's line closes it.
    // A sideways drag opens or closes it with the pointer; held, its menu; held a moment and dragged on the stack while rearranging, it is carried.
    MouseArea {
        id: drag
        x: 0
        y: 0
        width: row.width
        height: row.isOpen ? shelf.groupGap + shelf.headingHeight : row.block.closedHeight
        acceptedButtons: Qt.LeftButton | Qt.RightButton
        cursorShape: Qt.PointingHandCursor
        pressAndHoldInterval: Motion.coverHold
        property point pressedAt
        property real startTime: 0
        property bool isDragging: false
        property bool hasMenu: false

        onPressed: mouse => {
            pressedAt = Qt.point(mouse.x, mouse.y)
            startTime = row.time
            isDragging = false
            hasMenu = false
            clock.stop()
        }
        onPositionChanged: mouse => {
            const dx = mouse.x - pressedAt.x
            if (!isDragging && Math.abs(dx) < 8 * Theme.dp)
                return
            isDragging = true
            // A swipe right opens it, a swipe left pulls it shut; the whole way is 0.8 of the row's width.
            row.isOpening = dx > 0
            row.time = Motion.clamp(startTime + dx / (row.width * 0.8), 0, 1)
        }
        onReleased: mouse => {
            if (hasMenu)
                return
            if (isDragging) {
                isDragging = false
                const moved = row.time - startTime
                row.setOpen(Math.abs(moved) > 0.3 ? moved > 0 : startTime > 0.5)
                return
            }
            if (mouse.button === Qt.RightButton) {
                shelf.groupMenuAsked(row.groupName)
                return
            }
            if (shelf.selection.isRearranging)
                return
            row.setOpen(!row.isOpen)
        }
        onPressAndHold: {
            if (isDragging)
                return
            hasMenu = true
            shelf.groupMenuAsked(row.groupName)
        }
    }
}
