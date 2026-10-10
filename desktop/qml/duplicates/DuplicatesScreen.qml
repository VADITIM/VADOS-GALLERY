import QtQuick
import Gallery

// Every picture saved more than once (DuplicatesScreen.kt), set by set, the copy worth keeping first. All but that one start marked; a click marks or keeps a copy,
// a held click (or the right button) shows it large. Nothing is deleted until the pill at the bottom is confirmed, and then only to the trash, with undo.
Item {
    id: root

    required property var gallery
    property bool isOpen: false
    property string scope: "library"
    property string strictness: "close"
    property real shown: 0
    // What the user changed; anything not in here is marked by its place, every copy after the first.
    property var choices: ({})
    property var peek: null
    readonly property bool isSearching: Duplicates.stage !== "done"
    readonly property var sets: Duplicates.groups
    readonly property var marked: {
        const picked = []
        sets.forEach(set => set.forEach((item, index) => { if (isMarked(item, index)) picked.push(item) }))
        return picked
    }

    visible: shown > 0
    z: 75

    function isMarked(item: var, index: int): bool {
        const choice = choices[item.path]
        return choice !== undefined ? choice : index > 0
    }

    // Opened inside Private, it looks only among the private photos, in Private's red.
    function open(inPrivate: bool) {
        scope = inPrivate ? "private" : "library"
        choices = {}
        peek = null
        isOpen = true
        sink.stop()
        riseIn.restart()
        search.restart()
        keys.forceActiveFocus()
    }

    function close() {
        if (!isOpen)
            return
        Duplicates.stop()
        isOpen = false
        FocusHome.restore()
        riseIn.stop()
        sink.restart()
    }

    // The search starts once the strictness pill has settled, so the swap to the progress never lands mid-slide.
    Timer {
        id: search
        interval: Motion.stateChange
        onTriggered: Duplicates.find(root.scope, root.strictness)
    }

    function deleteMarked() {
        const paths = marked.map(item => item.path)
        if (scope === "private")
            Actions.trashPrivate(paths)
        else
            Actions.trash(paths)
        close()
    }

    NumberAnimation { id: riseIn; target: root; property: "shown"; to: 1; duration: Motion.overlayEnter; easing.type: Motion.powerTwoOut }
    NumberAnimation { id: sink; target: root; property: "shown"; to: 0; duration: Motion.overlayLeave; easing.type: Motion.powerTwoIn }

    Item {
        id: keys
        focus: root.isOpen
        Keys.onEscapePressed: {
            if (root.peek)
                root.peek = null
            else if (root.gallery.selection.pendingDelete !== null)
                root.gallery.selection.pendingDelete = null
            else
                root.close()
        }
    }

    Item {
        anchors.fill: parent
        opacity: root.shown
        scale: 0.94 + 0.06 * root.shown
        transform: Translate { y: (1 - root.shown) * root.height / 12 }

        Rectangle {
            anchors.fill: parent
            color: Theme.viewerGround
            MouseArea { anchors.fill: parent; onWheel: wheel => wheel.accepted = true }
        }

        Row {
            id: header
            x: 12 * Theme.dp
            y: 10 * Theme.dp
            spacing: 12 * Theme.dp

            TopButton {
                glyph: "back"
                isSolid: true
                onClicked: root.close()
            }
            Text {
                anchors.verticalCenter: parent.verticalCenter
                text: "DUPLICATES"
                color: Theme.textBright
                font.family: Theme.heading
                font.pixelSize: Theme.cardTitleSize * 1.3
            }
        }

        Text {
            anchors.right: parent.right
            anchors.rightMargin: 20 * Theme.dp
            anchors.verticalCenter: header.verticalCenter
            visible: !root.isSearching && root.sets.length > 0
            text: root.sets.length + " SETS"
            color: Theme.textMuted
            font.family: Theme.mono
            font.pixelSize: Theme.labelSize
            font.letterSpacing: Theme.labelSpacing
        }

        SegmentedTrack {
            id: track
            x: 16 * Theme.dp
            y: header.y + header.height + 12 * Theme.dp
            width: Math.min(parent.width - 32 * Theme.dp, 520 * Theme.dp)
            options: ["Exact", "Close", "Loose"]
            currentIndex: ["exact", "close", "loose"].indexOf(root.strictness)
            onPicked: index => {
                root.strictness = ["exact", "close", "loose"][index]
                root.choices = {}
                Duplicates.stop()
                search.restart()
            }
        }

        // Reading every photo's print the first time takes a while; later searches only read what is new.
        Column {
            anchors.centerIn: parent
            width: Math.min(parent.width - 80 * Theme.dp, 480 * Theme.dp)
            spacing: 12 * Theme.dp
            visible: root.isSearching

            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                text: Duplicates.stage === "comparing" ? "COMPARING" : "READING"
                color: Theme.accent
                font.family: Theme.mono
                font.pixelSize: Theme.labelSize
                font.letterSpacing: Theme.labelSpacing
            }
            Row {
                anchors.horizontalCenter: parent.horizontalCenter
                spacing: 8 * Theme.dp
                Text { text: Duplicates.readCount; color: Theme.accent; font.family: Theme.heading; font.pixelSize: Theme.titleSize }
                Text { anchors.baseline: parent.children[0].baseline; text: "/ " + Duplicates.totalCount; color: Theme.textMuted; font.family: Theme.heading; font.pixelSize: Theme.titleSize }
            }
            Rectangle {
                width: parent.width
                height: 4 * Theme.dp
                radius: height / 2
                color: Theme.borderStrong
                Rectangle {
                    width: parent.width * Math.min(1, Duplicates.progress)
                    height: parent.height
                    radius: height / 2
                    color: Theme.accent
                    Behavior on width { NumberAnimation { duration: Motion.stateChange } }
                }
            }
        }

        Text {
            anchors.centerIn: parent
            visible: !root.isSearching && root.sets.length === 0
            text: "NO DUPLICATES"
            color: Theme.textMuted
            font.family: Theme.mono
            font.pixelSize: Theme.labelSize
            font.letterSpacing: Theme.labelSpacing
        }

        ListView {
            id: list
            x: 16 * Theme.dp
            y: track.y + track.height + 16 * Theme.dp
            width: Math.min(parent.width - 32 * Theme.dp, 900 * Theme.dp)
            height: parent.height - y
            visible: !root.isSearching
            clip: true
            model: root.sets
            bottomMargin: 140 * Theme.dp
            boundsBehavior: Flickable.StopAtBounds
            spacing: 10 * Theme.dp

            // One picture's copies in rows of three, the one to keep first, with how much deleting the marked ones frees.
            delegate: Column {
                id: set
                required property var modelData
                readonly property real tile: (list.width - 12 * Theme.dp) / 3
                width: list.width
                spacing: 8 * Theme.dp

                Item {
                    width: parent.width
                    height: 20 * Theme.dp
                    Text {
                        text: set.modelData.length + " COPIES"
                        color: Theme.accent
                        font.family: Theme.mono
                        font.pixelSize: Theme.labelSize
                        font.letterSpacing: Theme.labelSpacing
                    }
                    Text {
                        anchors.right: parent.right
                        readonly property real freed: root.choices !== null ? set.modelData.reduce((sum, item, index) => sum + (root.isMarked(item, index) ? item.size : 0), 0) : 0
                        visible: freed > 0
                        text: System.formatSize(freed)
                        color: Theme.danger
                        font.family: Theme.mono
                        font.pixelSize: Theme.labelSize
                        font.letterSpacing: Theme.labelSpacing
                    }
                }

                Flow {
                    width: parent.width
                    spacing: 6 * Theme.dp

                    Repeater {
                        model: set.modelData

                        Column {
                            id: copy
                            required property var modelData
                            required property int index
                            readonly property bool isMarked: root.choices !== null && root.isMarked(modelData, index)
                            width: set.tile
                            spacing: 4 * Theme.dp

                            Pressable {
                                width: set.tile
                                height: set.tile
                                onClicked: {
                                    const next = Object.assign({}, root.choices)
                                    next[copy.modelData.path] = !copy.isMarked
                                    root.choices = next
                                    root.gallery.selection.pendingDelete = null
                                }
                                onHeld: root.peek = copy.modelData
                                onRightClicked: root.peek = copy.modelData

                                SquircleImage {
                                    anchors.fill: parent
                                    radius: Theme.tileRadius * 1.45
                                    source: "image://thumbnail/" + encodeURIComponent(copy.modelData.path)
                                    sourceSize: Qt.size(384, 384)
                                }
                                Rectangle {
                                    anchors.fill: parent
                                    radius: Theme.tileRadius
                                    color: Theme.viewerGround
                                    opacity: copy.isMarked ? 0.45 : 0
                                    Behavior on opacity { NumberAnimation { duration: Motion.stateChange } }
                                }
                                Rectangle {
                                    anchors.right: parent.right
                                    anchors.top: parent.top
                                    anchors.margins: 6 * Theme.dp
                                    width: 30 * Theme.dp
                                    height: 24 * Theme.dp
                                    radius: height / 2
                                    color: Theme.panelSolid
                                    Glyph {
                                        anchors.centerIn: parent
                                        width: 14 * Theme.dp
                                        height: width
                                        name: copy.isMarked ? "trash" : "check"
                                        ink: copy.isMarked ? Theme.danger : Theme.accent
                                    }
                                }
                            }

                            // What tells copies apart once they look the same: resolution, file size and the album it is in.
                            Text {
                                visible: copy.modelData.width > 0
                                text: copy.modelData.width + "×" + copy.modelData.height
                                color: Theme.textBright
                                font.family: Theme.mono
                                font.pixelSize: 11 * Theme.dp
                            }
                            Text {
                                text: System.formatSize(copy.modelData.size)
                                color: Theme.textMuted
                                font.family: Theme.mono
                                font.pixelSize: 11 * Theme.dp
                            }
                            FadeText {
                                width: parent.width
                                text: copy.modelData.album ?? ""
                                color: Theme.textMuted
                                font.pixelSize: 11 * Theme.dp
                            }
                        }
                    }
                }

                Rectangle {
                    width: parent.width
                    height: 1
                    color: Theme.border
                }
            }

            WheelHandler {
                onWheel: event => {
                    const delta = event.pixelDelta.y !== 0 ? event.pixelDelta.y : event.angleDelta.y
                    list.contentY = Math.max(list.originY, Math.min(list.originY + Math.max(0, list.contentHeight - list.height + list.bottomMargin), list.contentY - delta))
                }
            }
        }

        // The delete waits on Confirm, like every delete in the app; the photos go to the trash, with the pill's undo.
        Column {
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.bottom: parent.bottom
            anchors.bottomMargin: 20 * Theme.dp
            spacing: 14 * Theme.dp

            ConfirmPill {
                anchors.horizontalCenter: parent.horizontalCenter
                selection: root.gallery.selection
            }

            Pop {
                anchors.horizontalCenter: parent.horizontalCenter
                isShown: !root.isSearching && root.marked.length > 0
                Pressable {
                    width: deleteRow.implicitWidth + 36 * Theme.dp
                    height: 46 * Theme.dp
                    onClicked: root.gallery.selection.confirmThen(() => root.deleteMarked())
                    Rectangle {
                        anchors.fill: parent
                        radius: height / 2
                        color: Theme.panelSolid
                    }
                    Rectangle {
                        anchors.fill: parent
                        radius: height / 2
                        color: Theme.alpha(Theme.danger, 0.22)
                        visible: root.gallery.selection.pendingDelete !== null
                    }
                    Row {
                        id: deleteRow
                        anchors.centerIn: parent
                        spacing: 10 * Theme.dp
                        Glyph { anchors.verticalCenter: parent.verticalCenter; width: 18 * Theme.dp; height: width; name: "trash"; ink: Theme.danger }
                        Text {
                            anchors.verticalCenter: parent.verticalCenter
                            text: root.marked.length + " · " + System.formatSize(root.marked.reduce((sum, item) => sum + item.size, 0))
                            color: Theme.textBright
                            font.family: Theme.mono
                            font.pixelSize: Theme.navigationSize
                            font.letterSpacing: Theme.navigationSpacing * 0.6
                        }
                    }
                }
            }
        }

        // Held large on black, to tell two copies apart; a click puts it back.
        Item {
            anchors.fill: parent
            visible: root.peek !== null
            Rectangle { anchors.fill: parent; color: Theme.viewerGround }
            Image {
                anchors.fill: parent
                anchors.margins: 20 * Theme.dp
                fillMode: Image.PreserveAspectFit
                asynchronous: true
                autoTransform: true
                source: root.peek ? System.fileUrl(root.peek.path) : ""
                sourceSize: Qt.size(2048, 2048)
            }
            Column {
                x: 20 * Theme.dp
                anchors.bottom: parent.bottom
                anchors.bottomMargin: 20 * Theme.dp
                Text { text: root.peek ? root.peek.width + "×" + root.peek.height : ""; color: Theme.textBright; font.family: Theme.mono; font.pixelSize: 12 * Theme.dp; style: Text.Raised; styleColor: "black" }
                Text { text: root.peek ? System.formatSize(root.peek.size) + " · " + (root.peek.album ?? "") : ""; color: Theme.textMuted; font.family: Theme.mono; font.pixelSize: 12 * Theme.dp; style: Text.Raised; styleColor: "black" }
            }
            MouseArea { anchors.fill: parent; onClicked: root.peek = null }
        }
    }
}
