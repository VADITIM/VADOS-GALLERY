import QtQuick
import Gallery

// The desktop's navigation down the left edge: the sections with the nav's two-edged highlight standing up, the places (Trash, Private), and the albums themselves.
// A flat raised fill, not glass: it does not float over anything (docs/DESIGN.md § Surface). Its width is dragged on its edge and kept in rem, so it scales with the type.
Item {
    id: root

    required property var gallery
    readonly property real rowHeight: 40 * Theme.dp
    readonly property var albums: gallery.isPrivateMode ? (Vault.revision >= 0 && Vault.groups()) : (Library.revision >= 0 && Settings.revision >= 0 && Library.albums())

    // #region ── the sections ──────────────────────────────────────────────────────────────────
    property string inkActive: gallery.section
    Connections {
        target: root.gallery
        function onSectionChanged() {
            inkCut.restart()
            const top = sections.y + root.indexOf(root.gallery.section) * root.rowHeight
            const isMovingDown = top > root.washTop
            topEdge.to = top
            bottomEdge.to = top + root.rowHeight
            topLag.duration = isMovingDown ? Motion.navTrail : 0
            bottomLag.duration = isMovingDown ? 0 : Motion.navTrail
            slide.restart()
        }
    }
    Timer {
        id: inkCut
        interval: Motion.sectionLeave
        onTriggered: root.inkActive = root.gallery.section
    }

    function indexOf(key: string): int {
        for (let index = 0; index < Sections.all.length; ++index)
            if (Sections.all[index].key === key)
                return index
        return 0
    }

    property real washTop: sections.y + indexOf(gallery.section) * rowHeight
    property real washBottom: washTop + rowHeight

    ParallelAnimation {
        id: slide
        SequentialAnimation {
            PauseAnimation { id: topLag; duration: 0 }
            NumberAnimation { id: topEdge; target: root; property: "washTop"; duration: Motion.navSlide; easing.type: Motion.powerTwoOut }
        }
        SequentialAnimation {
            PauseAnimation { id: bottomLag; duration: 0 }
            NumberAnimation { id: bottomEdge; target: root; property: "washBottom"; duration: Motion.navSlide; easing.type: Motion.powerTwoOut }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    Squircle {
        anchors.fill: parent
        radius: Theme.panelRadius
        fillColor: Theme.surface
    }

    Item {
        id: content
        anchors.fill: parent
        anchors.margins: 10 * Theme.dp

        Text {
            id: title
            x: 8 * Theme.dp
            y: 6 * Theme.dp
            text: root.gallery.isPrivateMode ? "PRIVATE" : "GALLERY"
            color: root.gallery.isPrivateMode ? Theme.privateRed : Theme.textLabel
            font.family: Theme.mono
            font.pixelSize: Theme.labelSize
            font.letterSpacing: Theme.labelSpacing
        }

        Rectangle {
            x: 0
            y: root.washTop
            width: content.width
            height: Math.max(0, root.washBottom - root.washTop)
            radius: height / 2
            color: Theme.pressedWash
        }

        Column {
            id: sections
            y: title.y + title.height + 14 * Theme.dp
            width: content.width

            Repeater {
                model: Sections.all

                Pressable {
                    id: row
                    required property var modelData
                    readonly property bool isChosen: modelData.key === root.gallery.section
                    readonly property color ink: modelData.key === root.inkActive ? root.gallery.accentOf(modelData.key) : Theme.textMuted
                    width: sections.width
                    height: root.rowHeight
                    pressedScale: 0.98
                    onClicked: root.gallery.selectSection(modelData.key)

                    Item {
                        id: icon
                        x: 12 * Theme.dp
                        anchors.verticalCenter: parent.verticalCenter
                        width: 20 * Theme.dp
                        height: width
                        scale: row.isChosen ? 1.06 : 1
                        Behavior on scale { NumberAnimation { duration: Motion.navSlide; easing.type: Motion.backOut } }

                        property string shownGlyph: row.modelData.key === "albums" ? root.gallery.albumsGlyph : row.modelData.glyph
                        property string drawnGlyph: shownGlyph
                        property real glyphPop: 1
                        onShownGlyphChanged: glyphSwap.restart()
                        SequentialAnimation {
                            id: glyphSwap
                            NumberAnimation { target: icon; property: "glyphPop"; to: 0; duration: Motion.stateChange; easing.type: Motion.backIn }
                            ScriptAction { script: icon.drawnGlyph = icon.shownGlyph }
                            NumberAnimation { target: icon; property: "glyphPop"; to: 1; duration: Motion.stateChange; easing.type: Motion.backOut }
                        }

                        Glyph {
                            anchors.fill: parent
                            name: icon.drawnGlyph
                            scale: icon.glyphPop
                            ink: row.isHovered && !row.isChosen ? Theme.textBright : row.ink
                            Behavior on ink { ColorAnimation { duration: Motion.stateChange } }
                        }
                    }

                    Text {
                        x: 44 * Theme.dp
                        anchors.verticalCenter: parent.verticalCenter
                        text: row.modelData.label
                        color: row.isChosen ? Theme.textBright : row.isHovered ? Theme.textBody : Theme.textMuted
                        font.family: Theme.mono
                        font.pixelSize: Theme.navigationSize
                        font.letterSpacing: Theme.navigationSpacing
                        Behavior on color { ColorAnimation { duration: Motion.stateChange } }
                    }

                    // Inside Private, Recent and Favorites show only Private's own photos, and say so with the accent's mark.
                    Rectangle {
                        anchors.right: parent.right
                        anchors.rightMargin: 14 * Theme.dp
                        anchors.verticalCenter: parent.verticalCenter
                        width: 6 * Theme.dp
                        height: width
                        radius: width / 2
                        color: root.gallery.accentOf(row.modelData.key)
                        opacity: root.gallery.isPrivateInk && row.modelData.key !== "albums" ? 1 : 0
                        Behavior on opacity { NumberAnimation { duration: Motion.stateChange } }
                    }
                }
            }
        }

        Rectangle {
            id: firstRule
            y: sections.y + sections.height + 10 * Theme.dp
            width: content.width
            height: 1
            color: Theme.border
        }

        // The places reached from Albums.
        Column {
            id: places
            y: firstRule.y + 11 * Theme.dp
            width: content.width

            SidebarRow {
                width: places.width
                glyph: "trash"
                label: root.gallery.isPrivateMode ? "PRIVATE TRASH" : "TRASH"
                count: root.gallery.isPrivateMode ? (Vault.revision >= 0 && Vault.trashCount()) : Library.trashCount
                accent: Theme.trashGray
                isChosen: root.gallery.section === "albums" && (root.gallery.navigation.albumsPlace === "trash" || root.gallery.navigation.albumsPlace === "private-trash")
                onClicked: root.gallery.openTrash()
            }

            SidebarRow {
                width: places.width
                glyph: root.gallery.isPrivateMode ? "lock-open" : "lock"
                label: root.gallery.isPrivateMode ? "LEAVE PRIVATE" : "PRIVATE"
                count: -1
                accent: Theme.privateRed
                isChosen: false
                onClicked: root.gallery.isPrivateMode ? root.gallery.leavePlace() : root.gallery.openPrivate()
            }
        }

        Rectangle {
            id: secondRule
            y: places.y + places.height + 10 * Theme.dp
            width: content.width
            height: 1
            color: Theme.border
        }

        Text {
            id: albumsTitle
            x: 8 * Theme.dp
            y: secondRule.y + 14 * Theme.dp
            text: "ALBUMS"
            color: Theme.textLabel
            font.family: Theme.mono
            font.pixelSize: Theme.labelSize
            font.letterSpacing: Theme.labelSpacing
        }

        ListView {
            id: albumList
            y: albumsTitle.y + albumsTitle.height + 8 * Theme.dp
            width: content.width
            height: content.height - y
            clip: true
            model: root.albums
            boundsBehavior: Flickable.StopAtBounds
            spacing: 2 * Theme.dp

            delegate: Pressable {
                id: album
                required property var modelData
                readonly property bool isOpen: root.gallery.section === "albums" && root.gallery.openedAlbumKey === modelData.key
                width: albumList.width
                height: 38 * Theme.dp
                pressedScale: 0.98
                onClicked: root.gallery.openCover(modelData)
                onRightClicked: root.gallery.showCoverMenu(modelData)

                Rectangle {
                    anchors.fill: parent
                    radius: height / 2
                    color: Theme.pressedWash
                    opacity: album.isOpen ? 1 : album.isHovered ? 0.5 : 0
                    Behavior on opacity { NumberAnimation { duration: Motion.stateChange } }
                }

                SquircleImage {
                    id: thumbnail
                    x: 6 * Theme.dp
                    anchors.verticalCenter: parent.verticalCenter
                    width: 28 * Theme.dp
                    height: width
                    radius: 8 * Theme.dp
                    source: (album.modelData.cover ?? "").length > 0 ? "image://thumbnail/" + encodeURIComponent(album.modelData.cover) : ""
                    sourceSize: Qt.size(64, 64)
                }

                FadeText {
                    x: thumbnail.x + thumbnail.width + 10 * Theme.dp
                    width: count.x - x - 6 * Theme.dp
                    anchors.verticalCenter: parent.verticalCenter
                    text: album.modelData.name ?? ""
                    color: album.isOpen ? Theme.accent : Theme.textBody
                    font.family: Theme.heading
                    font.pixelSize: 12 * Theme.dp
                }

                Text {
                    id: count
                    anchors.right: parent.right
                    anchors.rightMargin: 10 * Theme.dp
                    anchors.verticalCenter: parent.verticalCenter
                    text: album.modelData.count ?? ""
                    color: Theme.textFaint
                    font.family: Theme.mono
                    font.pixelSize: Theme.labelSize
                }
            }

            WheelHandler {
                onWheel: event => {
                    const delta = event.pixelDelta.y !== 0 ? event.pixelDelta.y : event.angleDelta.y / 120 * 60 * Theme.dp
                    albumList.contentY = Math.max(albumList.originY, Math.min(albumList.originY + Math.max(0, albumList.contentHeight - albumList.height), albumList.contentY - delta))
                }
            }
        }
    }

    // The edge is dragged to widen or narrow the sidebar.
    MouseArea {
        anchors.right: parent.right
        anchors.rightMargin: -6 * Theme.dp
        width: 12 * Theme.dp
        height: parent.height
        cursorShape: Qt.SizeHorCursor
        property real startX: 0
        property real startWidth: 0
        onPressed: mouse => {
            startX = mapToItem(null, mouse.x, 0).x
            startWidth = root.width
        }
        onPositionChanged: mouse => {
            const x = mapToItem(null, mouse.x, 0).x
            Settings.sidebarWidthRem = Math.max(9, Math.min(20, (startWidth + x - startX) / Theme.rem))
        }
    }
}
