import QtQuick
import Gallery

// One photo in a grid (MediaGrid.kt): an 8dp rounded thumbnail with a mark at each corner, each with one job away from the timeline:
// the day stamp (or a trashed photo's days left) top left, the heart top right, a video's length at the bottom (VAS components/20 §1).
Item {
    id: tile

    required property int tileIndex
    required property var grid
    readonly property var facts: grid.model.revision >= 0 && grid.model.tile(tileIndex)
    readonly property bool isFoldedStack: (facts.stackSize ?? 0) > 0 && facts.stackPosition === 0
    readonly property string path: facts.path ?? ""
    readonly property bool isSelected: grid.selection.revision >= 0 && grid.selection.has(path)
    readonly property bool isSmall: grid.columns >= 5
    property real entrance: 1

    width: grid.cell
    height: grid.cell

    Component.onCompleted: {
        // The first screenful rises in as a short cascade; anything reached later by scrolling is simply there.
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
        NumberAnimation { target: tile; property: "entrance"; to: 1; duration: Motion.entrance; easing.type: Motion.powerTwoOut }
    }

    Item {
        id: face
        anchors.fill: parent
        opacity: tile.entrance
        // The selected shrink (VAS dna/05 §6.10): set aside rather than lifted, the gap framing the border.
        scale: (tile.isSelected ? 0.86 : 1) * (0.94 + 0.06 * tile.entrance)
        transform: Translate { y: (1 - tile.entrance) * 14 * Theme.dp }
        Behavior on scale {
            enabled: tile.entrance >= 1
            NumberAnimation { duration: Motion.stateChange; easing.type: Motion.backOut }
        }

        Rectangle {
            anchors.fill: parent
            radius: Theme.tileRadius
            color: Theme.surface
            visible: picture.status !== Image.Ready
        }

        Image {
            id: picture
            visible: false
            asynchronous: true
            cache: true
            smooth: true
            source: tile.path.length > 0 ? "image://thumbnail/" + encodeURIComponent(tile.path) : ""
            // The thumbnail bucket follows the tile's size, so a grid of few columns gets a sharp picture.
            sourceSize: Qt.size(Math.ceil(tile.width * Screen.devicePixelRatio), Math.ceil(tile.height * Screen.devicePixelRatio))
        }

        ShaderEffect {
            anchors.fill: parent
            visible: picture.status === Image.Ready
            property variant source: picture
            readonly property size itemSize: Qt.size(width, height)
            readonly property size sourceSize: Qt.size(picture.implicitWidth, picture.implicitHeight)
            property real radius: Theme.tileRadius
            // Thumbnails are plain rounded rects, not squircles: there are hundreds on screen.
            property real exponent: 2
            property real borderWidth: tile.isSelected ? 3 * Theme.dp : 0
            property real insetWidth: 0
            property color borderColor: Theme.accent
            property color insetColor: Theme.accent
            fragmentShader: "qrc:/shaders/squircleImage.frag.qsb"
        }

        Text {
            visible: (tile.facts.stamp ?? "").length > 0 || (tile.facts.daysLeft ?? "").length > 0
            anchors.left: parent.left
            anchors.top: parent.top
            anchors.margins: 5 * Theme.dp
            text: (tile.facts.daysLeft ?? "").length > 0 ? tile.facts.daysLeft : tile.facts.stamp ?? ""
            color: (tile.facts.daysLeft ?? "").length > 0 ? Theme.danger : Theme.textBright
            font.family: Theme.mono
            font.pixelSize: 9 * Theme.dp
            style: Text.Raised
            styleColor: Qt.rgba(0, 0, 0, 0.7)
        }

        // Top right: a favourite's heart, then a stack's count or a laid-out shot's place.
        Row {
            anchors.right: parent.right
            anchors.top: parent.top
            anchors.margins: 5 * Theme.dp
            spacing: 4 * Theme.dp
            layoutDirection: Qt.RightToLeft

            Rectangle {
                visible: (tile.facts.stackSize ?? 0) > 0
                anchors.verticalCenter: parent.verticalCenter
                width: stackRow.implicitWidth + 10 * Theme.dp
                height: 18 * Theme.dp
                radius: height / 2
                color: tile.isFoldedStack ? Qt.rgba(0, 0, 0, 0.55) : Theme.accent
                Row {
                    id: stackRow
                    anchors.centerIn: parent
                    spacing: 3 * Theme.dp
                    Glyph {
                        visible: tile.isFoldedStack
                        anchors.verticalCenter: parent.verticalCenter
                        width: 11 * Theme.dp
                        height: width
                        name: "duplicates"
                        ink: Theme.textBright
                    }
                    Text {
                        anchors.verticalCenter: parent.verticalCenter
                        text: tile.isFoldedStack ? tile.facts.stackSize : tile.facts.stackPosition + "/" + tile.facts.stackSize
                        color: tile.isFoldedStack ? Theme.textBright : Theme.sunkenDeep
                        font.family: Theme.mono
                        font.pixelSize: 9 * Theme.dp
                    }
                }
            }

            Glyph {
                visible: tile.facts.isFavorite === true
                anchors.verticalCenter: parent.verticalCenter
                width: (tile.isSmall ? 11 : 14) * Theme.dp
                height: width
                name: "heart-filled"
                ink: Theme.favorite
            }
        }

        // A motion photo's mark stays at the bottom right.
        Glyph {
            visible: tile.facts.isMotion === true
            anchors.right: parent.right
            anchors.bottom: parent.bottom
            anchors.margins: 5 * Theme.dp
            width: (tile.isSmall ? 12 : 15) * Theme.dp
            height: width
            name: "motion"
            ink: Theme.textBright
        }

        Text {
            visible: (tile.facts.duration ?? "").length > 0
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.bottom: parent.bottom
            anchors.bottomMargin: 4 * Theme.dp
            text: tile.facts.duration ?? ""
            color: Theme.textBright
            font.family: Theme.mono
            font.pixelSize: (tile.isSmall ? 9 : 10) * Theme.dp
            style: Text.Raised
            styleColor: Qt.rgba(0, 0, 0, 0.7)
        }
    }
}
