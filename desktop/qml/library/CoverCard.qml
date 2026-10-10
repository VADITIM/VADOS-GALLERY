import QtQuick
import Gallery

// An album as a card (CoverCard in Covers.kt): its cover as a squircle, the name in the heading face and the count under it; at one column, a row of the list.
// In a group it lies in the stack, leaning and darker the deeper it is, and lifts off into its slot as the group opens. While rearranging it jiggles,
// and held a moment and dragged, it is carried under the pointer.
Item {
    id: card

    required property var cover
    required property var grid
    property bool isGrouped: false
    property real groupShare: 0
    property real lyingEdge: 0
    property real groupEdge: 0
    property real lyingShade: 1
    property real lyingTilt: 0
    readonly property bool isList: !isGrouped && grid.columns === 1
    readonly property real edge: isGrouped ? lyingEdge + (groupEdge - lyingEdge) * groupShare : isList ? grid.listCover : grid.cell
    readonly property bool isSelected: grid.selection.revision >= 0 && grid.selection.hasCover(cover.key)
    readonly property bool isCarried: grid.heldKey === cover.key
    readonly property bool isRearranging: grid.selection.isRearranging
    property real entrance: 1

    width: isList ? grid.width - grid.sideInset * 2 : edge
    height: isList ? grid.listCover : edge + grid.labelRoom

    Component.onCompleted: {
        const rank = grid.nextEntranceRank()
        if (rank < 0)
            return
        entrance = 0
        rise.delay = rank * Motion.entranceStagger
        rise.start()
    }

    SequentialAnimation {
        id: rise
        property int delay: 0
        PauseAnimation { duration: rise.delay }
        NumberAnimation { target: card; property: "entrance"; to: 1; duration: Motion.entrance; easing.type: Motion.powerTwoOut }
    }

    // Each card swings ±1.4° while rearranging, out of step with its neighbours; the one carried is lifted instead.
    property real jiggle: 0
    SequentialAnimation on jiggle {
        running: card.isRearranging && !card.isCarried
        loops: Animation.Infinite
        PauseAnimation { duration: Math.abs(card.cover.key.length * 7) % 20 }
        NumberAnimation { to: 1.4; duration: Motion.jiggle; easing.type: Easing.InOutSine }
        NumberAnimation { to: -1.4; duration: Motion.jiggle; easing.type: Easing.InOutSine }
        onRunningChanged: if (!running) card.jiggle = 0
    }

    Item {
        id: face
        anchors.fill: parent
        opacity: card.entrance
        rotation: card.jiggle + card.lyingTilt * (1 - card.groupShare)
        transformOrigin: Item.Center
        scale: (mouse.pressed && !card.isCarried ? 0.96 : 1) * (card.isCarried ? 1.05 : 1)
        transform: Translate { y: (1 - card.entrance) * 14 * Theme.dp }
        Behavior on scale { NumberAnimation { duration: mouse.pressed ? Motion.press : Motion.release; easing.type: mouse.pressed ? Motion.powerTwoOut : Motion.backOut } }

        Item {
            id: coverBox
            width: card.edge
            height: card.edge
            // A picked cover shrinks to 0.9 with an accent border (VAS dna/05 §6.10).
            scale: card.isSelected ? 0.9 : 1
            Behavior on scale { NumberAnimation { duration: Motion.stateChange; easing.type: Motion.backOut } }

            Squircle {
                anchors.fill: parent
                radius: card.isList ? 16 * Theme.dp : Theme.coverRadius
                fillColor: Theme.surface
                visible: picture.status !== Image.Ready
            }

            SquircleImage {
                id: picture
                anchors.fill: parent
                radius: card.isList ? 16 * Theme.dp : Theme.coverRadius
                source: (card.cover.cover ?? "").length > 0 ? "image://thumbnail/" + encodeURIComponent(card.cover.cover) : ""
                sourceSize: Qt.size(512, 512)
                borderWidth: card.isSelected ? 3 * Theme.dp : 0
                borderColor: Theme.accent
            }

            // Deeper cards in a stack lie darker.
            Squircle {
                anchors.fill: parent
                radius: Theme.coverRadius
                fillColor: Qt.rgba(0, 0, 0, (1 - card.lyingShade) * (1 - card.groupShare))
                visible: card.lyingShade < 1
            }
        }

        // The name waits until the card is well clear of the stack, so names never print over each other.
        Column {
            x: card.isList ? card.edge + 18 * Theme.dp : 4 * Theme.dp
            y: card.isList ? (card.edge - height) / 2 : card.edge + 10 * Theme.dp
            width: card.isList ? card.width - x : card.edge - 4 * Theme.dp
            spacing: (card.isList ? 6 : 2) * Theme.dp
            opacity: card.isGrouped ? Math.max(0, (card.groupShare - 0.6) / 0.4) : 1

            FadeText {
                id: name
                width: parent.width
                text: card.cover.name ?? ""
                // The albums made inside Favorites wear its colour, apart from real folders.
                // The albums made inside Favorites and the places in Locations wear their view's colour, apart from real folders.
                color: card.cover.isFavoriteAlbum === true || card.cover.isLocation === true ? Theme.accent : Theme.textBright
                font.family: Theme.heading
                // At three or four to a row the name shrinks until it fits, down to 9, then fades at its end.
                font.pixelSize: card.isList ? 20 * Theme.dp : Math.max(9 * Theme.dp, Math.min(Theme.cardTitleSize, Theme.cardTitleSize * parent.width / Math.max(1, naturalName.implicitWidth)))
                style: card.isCarried ? Text.Raised : Text.Normal
                styleColor: Qt.rgba(0, 0, 0, 0.7)
                Text {
                    id: naturalName
                    visible: false
                    text: card.cover.name ?? ""
                    font.family: Theme.heading
                    font.pixelSize: Theme.cardTitleSize
                }
            }

            Text {
                text: card.cover.count ?? ""
                color: Theme.textMuted
                font.family: Theme.mono
                font.pixelSize: card.isList ? 15 * Theme.dp : Theme.valueSize
                style: card.isCarried ? Text.Raised : Text.Normal
                styleColor: Qt.rgba(0, 0, 0, 0.7)
            }
        }
    }

    // A click opens it (or picks it while covers are picked); held, its menu; held a moment and dragged, it is carried and the covers rearrange around it.
    MouseArea {
        id: mouse
        anchors.fill: parent
        acceptedButtons: Qt.LeftButton | Qt.RightButton
        hoverEnabled: true
        cursorShape: card.isRearranging ? (card.isCarried ? Qt.ClosedHandCursor : Qt.OpenHandCursor) : Qt.PointingHandCursor
        // Lying deep in a shut stack, a card leaves the clicks to the group.
        enabled: !card.isGrouped || card.groupShare > 0.5
        property point pressedAt
        property bool isReady: false
        property bool isCarrying: false
        property bool hasMenu: false

        onPressed: mouse => {
            pressedAt = Qt.point(mouse.x, mouse.y)
            isReady = card.isRearranging
            isCarrying = false
            hasMenu = false
            if (mouse.button === Qt.LeftButton && !card.isRearranging) {
                rest.restart()
                hold.restart()
            }
        }
        onPositionChanged: mouse => {
            if (!pressed)
                return
            const at = mapToItem(card.grid, mouse.x, mouse.y)
            if (isCarrying) {
                card.grid.moveCarried(at)
                return
            }
            if (Math.hypot(mouse.x - pressedAt.x, mouse.y - pressedAt.y) < 6 * Theme.dp)
                return
            hold.stop()
            if (isReady) {
                isCarrying = true
                card.grid.startCarry(card.cover.key, at)
            }
        }
        onReleased: mouse => {
            rest.stop()
            hold.stop()
            if (isCarrying) {
                isCarrying = false
                card.grid.dropCarried()
                return
            }
            if (hasMenu || !containsMouse)
                return
            if (mouse.button === Qt.RightButton) {
                card.grid.menuAsked(card.cover)
                return
            }
            if (card.isRearranging)
                return
            if (card.grid.selection.isSelectingCovers)
                card.grid.selection.toggleCover(card.cover.key)
            else
                card.grid.opened(card.cover)
        }

        // A pointer resting this long before it moves is picking the cover up rather than passing over it.
        Timer { id: rest; interval: 150; onTriggered: mouse.isReady = true }
        Timer {
            id: hold
            interval: Motion.coverHold
            onTriggered: {
                mouse.hasMenu = true
                card.grid.menuAsked(card.cover)
            }
        }
    }
}
