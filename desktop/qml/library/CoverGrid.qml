import QtQuick
import Gallery

// One cover grid for albums and Private's albums (Covers.kt): the same columns, a list at one column, names that shrink to fit, a New card at the end,
// and the places (Private, Trash) as tiles of their own after a hairline.
Item {
    id: root

    property var covers: []
    // The tiles after the albums: { key, name, glyph, accent, count }.
    property var places: []
    property string newLabel: "NEW ALBUM"
    property string view: "albums"
    required property var selection
    property bool isActive: true
    property real topInset: 0
    property real bottomInset: 0
    property var header: null
    readonly property int columns: (Settings.revision, Settings.viewValue(view, "albumColumns"))
    readonly property real sideInset: 14 * Theme.dp
    readonly property real gap: 12 * Theme.dp
    readonly property real cell: Math.max(40, (width - sideInset * 2 - gap * (columns - 1)) / columns)
    readonly property alias flickable: flick

    signal opened(var cover)
    signal menuAsked(var cover, real x, real y)
    signal placeOpened(string key)
    signal newAsked

    property double entranceStartedAt: Date.now()
    property int entranceRank: 0
    onIsActiveChanged: if (isActive) { entranceStartedAt = Date.now(); entranceRank = 0 }
    function nextEntranceRank(): int {
        if (Date.now() - entranceStartedAt > Motion.entranceWindow || Motion.isReduced)
            return -1
        return Math.min(16, entranceRank++)
    }

    function stepColumns(step: int) {
        Settings.setViewValue(view, "albumColumns", Math.max(1, Math.min(4, columns + step)))
    }

    function glideToTop() {
        glide.from = flick.contentY
        glide.to = -flick.topMargin
        glide.restart()
    }
    NumberAnimation { id: glide; target: flick; property: "contentY"; duration: Motion.scrollToEnd; easing.type: Motion.powerThreeInOut }

    Flickable {
        id: flick
        anchors.fill: parent
        topMargin: root.topInset
        bottomMargin: root.bottomInset
        contentWidth: width
        contentHeight: column.height
        boundsBehavior: Flickable.StopAtBounds
        interactive: false
        clip: false

        WheelHandler {
            acceptedModifiers: Qt.NoModifier
            onWheel: event => {
                const delta = event.pixelDelta.y !== 0 ? event.pixelDelta.y : event.angleDelta.y / 120 * root.cell * 0.6
                flick.contentY = Math.max(-flick.topMargin, Math.min(Math.max(-flick.topMargin, flick.contentHeight - flick.height + flick.bottomMargin), flick.contentY - delta))
            }
        }

        Column {
            id: column
            width: flick.width
            spacing: 0

            Loader {
                width: parent.width
                active: root.header !== null
                sourceComponent: root.header
            }

            Flow {
                id: flow
                x: root.sideInset
                width: parent.width - root.sideInset * 2
                spacing: root.gap
                bottomPadding: root.gap

                Repeater {
                    model: root.covers

                    CoverCard {
                        required property var modelData
                        required property int index
                        cover: modelData
                        grid: root
                    }
                }

                // The New card closes the albums: name it, then pick its photos.
                Pressable {
                    width: root.columns === 1 ? flow.width : root.cell
                    height: root.columns === 1 ? 64 * Theme.dp : root.cell + 40 * Theme.dp
                    onClicked: root.newAsked()

                    Squircle {
                        width: root.columns === 1 ? 56 * Theme.dp : parent.width
                        height: width
                        radius: root.columns === 1 ? 14 * Theme.dp : Theme.coverRadius
                        fillColor: "transparent"
                        borderColor: Theme.borderStrong
                        borderWidth: 1.5 * Theme.dp

                        Glyph {
                            anchors.centerIn: parent
                            width: 28 * Theme.dp
                            height: width
                            name: "plus"
                            ink: Theme.accent
                        }
                    }

                    Text {
                        x: root.columns === 1 ? 72 * Theme.dp : 2 * Theme.dp
                        y: root.columns === 1 ? (56 * Theme.dp - height) / 2 : root.cell + 8 * Theme.dp
                        text: root.newLabel
                        color: Theme.accent
                        font.family: Theme.mono
                        font.pixelSize: Theme.labelSize
                        font.letterSpacing: Theme.labelSpacing
                    }
                }
            }

            // The places, set apart from the albums by a hairline with a wider gap.
            Item {
                width: parent.width
                height: root.places.length > 0 ? 32 * Theme.dp : 0
                visible: root.places.length > 0
                Rectangle {
                    anchors.centerIn: parent
                    width: parent.width - root.sideInset * 2
                    height: 1
                    color: Theme.border
                }
            }

            Row {
                x: (parent.width - width) / 2
                spacing: root.gap
                visible: root.places.length > 0

                Repeater {
                    model: root.places

                    Pressable {
                        id: place
                        required property var modelData
                        readonly property real edge: Math.min(root.cell, 150 * Theme.dp)
                        width: edge
                        height: edge + 40 * Theme.dp
                        onClicked: root.placeOpened(modelData.key)

                        Squircle {
                            width: place.edge
                            height: place.edge
                            radius: Theme.coverRadius
                            fillColor: Theme.surface

                            Glyph {
                                anchors.centerIn: parent
                                width: place.edge * 0.32
                                height: width
                                name: place.modelData.glyph
                                ink: place.modelData.accent
                            }
                        }

                        Column {
                            y: place.edge + 8 * Theme.dp
                            width: place.edge
                            spacing: 2 * Theme.dp
                            Text {
                                width: parent.width
                                horizontalAlignment: Text.AlignHCenter
                                text: place.modelData.name
                                color: place.modelData.accent
                                font.family: Theme.heading
                                font.pixelSize: Theme.cardTitleSize
                            }
                            Text {
                                width: parent.width
                                horizontalAlignment: Text.AlignHCenter
                                visible: (place.modelData.count ?? -1) >= 0
                                text: place.modelData.count ?? ""
                                color: Theme.textMuted
                                font.family: Theme.mono
                                font.pixelSize: Theme.labelSize
                            }
                        }
                    }
                }
            }
        }
    }

    WheelHandler {
        acceptedModifiers: Qt.ControlModifier
        property real collected: 0
        onWheel: event => {
            collected += event.angleDelta.y
            if (Math.abs(collected) < 120)
                return
            root.stepColumns(collected > 0 ? -1 : 1)
            collected = 0
        }
    }

    PinchHandler {
        target: null
        property real stepScale: 1
        onActiveChanged: stepScale = 1
        onScaleChanged: delta => {
            stepScale *= delta
            if (stepScale > 1.28) {
                root.stepColumns(-1)
                stepScale = 1
            } else if (stepScale < 1 / 1.28) {
                root.stepColumns(1)
                stepScale = 1
            }
        }
    }
}
