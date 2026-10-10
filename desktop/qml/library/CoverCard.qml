import QtQuick
import Gallery

// An album as a card (CoverCard in Covers.kt): its cover as a squircle, the name in the heading face and the count under it; at one column, a row of the list.
// At three or four to a row the name shrinks until it fits, down to 9, then fades at its end.
Pressable {
    id: card

    required property var cover
    required property var grid
    readonly property bool isList: grid.columns === 1
    readonly property real edge: isList ? 56 * Theme.dp : grid.cell
    readonly property bool isSelected: grid.selection.revision, grid.selection.hasCover(cover.key)
    property real entrance: 1

    width: isList ? grid.width - grid.sideInset * 2 : grid.cell
    height: isList ? 64 * Theme.dp : grid.cell + 44 * Theme.dp
    holdInterval: Motion.coverHold
    onClicked: grid.selection.isSelectingCovers ? grid.selection.toggleCover(cover.key) : grid.opened(cover)
    onHeld: grid.menuAsked(cover, width / 2, height / 2)
    onRightClicked: grid.menuAsked(cover, width / 2, height / 2)

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

    Item {
        anchors.fill: parent
        opacity: card.entrance
        transform: Translate { y: (1 - card.entrance) * 14 * Theme.dp }

        Item {
            id: coverBox
            width: card.edge
            height: card.edge
            // A picked cover shrinks to 0.9 with an accent border (VAS dna/05 §6.10).
            scale: card.isSelected ? 0.9 : 1
            Behavior on scale { NumberAnimation { duration: Motion.stateChange; easing.type: Motion.backOut } }

            Squircle {
                anchors.fill: parent
                radius: card.isList ? 14 * Theme.dp : Theme.coverRadius
                fillColor: Theme.surface
                visible: picture.status !== Image.Ready
            }

            SquircleImage {
                id: picture
                anchors.fill: parent
                radius: card.isList ? 14 * Theme.dp : Theme.coverRadius
                source: (card.cover.cover ?? "").length > 0 ? "image://thumbnail/" + encodeURIComponent(card.cover.cover) : ""
                sourceSize: Qt.size(Math.ceil(card.edge * Screen.devicePixelRatio), Math.ceil(card.edge * Screen.devicePixelRatio))
                borderWidth: card.isSelected ? 3 * Theme.dp : 0
                borderColor: Theme.accent
            }
        }

        Column {
            x: card.isList ? card.edge + 16 * Theme.dp : 2 * Theme.dp
            y: card.isList ? (card.edge - height) / 2 : card.edge + 8 * Theme.dp
            width: card.isList ? card.width - x : card.edge - 4 * Theme.dp
            spacing: 2 * Theme.dp

            FadeText {
                width: parent.width
                text: card.cover.name ?? ""
                color: card.cover.isFavoriteAlbum === true ? Theme.accent : Theme.textBright
                font.family: Theme.heading
                font.pixelSize: card.isList ? 18 * Theme.dp : Math.max(9 * Theme.dp, Math.min(Theme.cardTitleSize, Theme.cardTitleSize * parent.width / Math.max(1, naturalName.implicitWidth)))
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
                font.pixelSize: card.isList ? Theme.valueSize : Theme.labelSize
            }
        }
    }
}
