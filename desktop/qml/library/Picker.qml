import QtQuick
import Gallery

// The photo picker (PickerScreen.kt): every photo in one grid, picked by a click, and ADD puts them in the album it was opened for; a new album is named first, then filled here.
// It rises in from slightly below, growing and fading in, and sinks back out.
Item {
    id: root

    required property var gallery
    property bool isOpen: false
    property string target: ""
    property bool isGroup: false
    // Filling an album made inside Favorites: only favourites, and only those in no album yet.
    property bool isFavoriteAlbum: false
    property string title: ""
    property real shown: 0

    visible: shown > 0
    z: 60

    function openForFavorites(name: string) {
        openFor(name, false, name)
        isFavoriteAlbum = true
    }

    function openFor(folderOrGroup: string, toGroup: bool, name: string) {
        isFavoriteAlbum = false
        target = folderOrGroup
        isGroup = toGroup
        title = name
        picks.clear()
        isOpen = true
        sink.stop()
        riseIn.restart()
    }
    function close() {
        isOpen = false
        FocusHome.restore()
        riseIn.stop()
        sink.restart()
    }
    function add() {
        const paths = picks.pickedPhotos()
        if (isFavoriteAlbum) {
            if (paths.length > 0)
                Actions.addToFavoriteAlbum(paths, target)
            close()
            gallery.navigation.openFavoriteAlbum(target)
            return
        }
        if (paths.length > 0) {
            if (isGroup)
                Actions.hide(paths, target)
            else
                Actions.move(paths, target)
        }
        close()
        if (!isGroup)
            gallery.navigation.openAlbum(target)
    }

    NumberAnimation { id: riseIn; target: root; property: "shown"; to: 1; duration: Motion.overlayEnter; easing.type: Motion.powerTwoOut }
    NumberAnimation { id: sink; target: root; property: "shown"; to: 0; duration: Motion.overlayLeave; easing.type: Motion.powerTwoIn }

    Selection { id: picks }

    Item {
        anchors.fill: parent
        opacity: root.shown
        scale: 0.94 + 0.06 * root.shown
        transform: Translate { y: (1 - root.shown) * root.height / 12 }

        Rectangle {
            anchors.fill: parent
            color: Theme.ground
            MouseArea { anchors.fill: parent; onWheel: wheel => wheel.accepted = true }
        }

        PhotoGrid {
            id: grid
            anchors.fill: parent
            source: root.isOpen || root.shown > 0 ? (root.isFavoriteAlbum ? "favorites" : "recent") : ""
            view: root.isFavoriteAlbum ? "favorites" : "recent"
            excludedFolder: root.isGroup || root.isFavoriteAlbum ? "" : root.target
            isOutsideFavoriteAlbums: root.isFavoriteAlbum
            selection: picks
            memories: ({})
            topInset: 64 * Theme.dp
            bottomInset: 90 * Theme.dp
            onOpened: index => picks.togglePhoto(grid.model.pathAt(index))
        }

        Row {
            x: 12 * Theme.dp
            y: 10 * Theme.dp
            spacing: 8 * Theme.dp

            TopButton {
                glyph: "close"
                onClicked: root.close()
            }
            TypedLabel {
                height: 42 * Theme.dp
                text: ((root.isGroup ? "ADD TO PRIVATE · " : root.isFavoriteAlbum ? "ADD TO FAVORITES · " : "ADD TO ") + root.title).toUpperCase()
                maximumWidth: root.width - 120 * Theme.dp
            }
        }

        Pop {
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.bottom: parent.bottom
            anchors.bottomMargin: 20 * Theme.dp
            isShown: picks.photoCount > 0
            Pressable {
                width: addText.implicitWidth + 44 * Theme.dp
                height: 48 * Theme.dp
                onClicked: root.add()
                Rectangle {
                    anchors.fill: parent
                    radius: height / 2
                    color: Theme.accent
                }
                Text {
                    id: addText
                    anchors.centerIn: parent
                    text: "ADD " + picks.photoCount
                    color: Theme.sunkenDeep
                    font.family: Theme.mono
                    font.pixelSize: Theme.labelSize * 1.2
                    font.letterSpacing: Theme.labelSpacing
                    font.bold: true
                }
            }
        }
    }
}
