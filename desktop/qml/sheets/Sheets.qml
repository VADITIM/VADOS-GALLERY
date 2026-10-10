import QtQuick
import QtQuick.Controls.Basic
import Gallery

// Every sheet the library opens (LibrarySheetHost.kt), each one a glass sheet; opening one always goes through a function here, which says what it acts on.
Item {
    id: sheets

    required property var gallery
    readonly property bool isAnyOpen: albumPicker.isOpen || moreMenu.isOpen || coverMenu.isOpen || nameSheet.isOpen || pinSheet.isOpen || restoreMenu.isOpen || groupMenu.isOpen || coverPicker.isOpen || countCheck.isOpen

    anchors.fill: parent

    function closeAll(): bool {
        for (const sheet of [albumPicker, moreMenu, coverMenu, nameSheet, pinSheet, restoreMenu, groupMenu, coverPicker, countCheck]) {
            if (sheet.isOpen) {
                sheet.close()
                return true
            }
        }
        return false
    }

    component Title: Text {
        color: Theme.accent
        font.family: Theme.mono
        font.pixelSize: Theme.labelSize
        font.letterSpacing: Theme.labelSpacing
        bottomPadding: 10 * Theme.dp
    }

    // #region ── album and group pickers ───────────────────────────────────────────────────────
    // `isGroups` picks among Private's albums; every list ends with a way to make a new one.
    function pickAlbum(title: string, isGroups: bool, exclude: string, onPicked: var) {
        albumPicker.title = title
        albumPicker.isGroups = isGroups
        albumPicker.exclude = exclude
        albumPicker.listed = null
        albumPicker.newLabel = "NEW ALBUM"
        albumPicker.onNew = null
        albumPicker.onPicked = onPicked
        albumPicker.open()
    }

    // Among the albums made inside Favorites; a favourite already in one is moved out of it, as a photo is in one at most.
    function pickFavoriteAlbum(paths: var, onPicked: var) {
        albumPicker.title = "MOVE TO ALBUM"
        albumPicker.isGroups = false
        albumPicker.exclude = ""
        albumPicker.listed = Library.favoriteAlbums()
        albumPicker.newLabel = "NEW ALBUM"
        albumPicker.onNew = name => onPicked(name)
        albumPicker.onPicked = onPicked
        albumPicker.open()
    }

    // Among the groups of a shelf, or a new one.
    function pickGroup(shelfName: string, onPicked: var) {
        albumPicker.title = "MOVE TO GROUP"
        albumPicker.isGroups = false
        albumPicker.exclude = ""
        albumPicker.listed = Settings.stacks(shelfName).map(stack => {
            const covers = shelfName === "favorites" ? Library.favoriteAlbums() : Library.albums()
            const first = covers.find(cover => stack.keys.indexOf(cover.key) >= 0)
            return { name: stack.name, folder: stack.name, count: stack.keys.length, cover: first ? first.cover : "" }
        })
        albumPicker.newLabel = "NEW GROUP"
        albumPicker.onNew = name => onPicked(name)
        albumPicker.onPicked = onPicked
        albumPicker.open()
    }

    Sheet {
        id: albumPicker
        property string title: ""
        property bool isGroups: false
        property string exclude: ""
        property var onPicked: null
        // A list handed in (Favorites albums, groups) picks by name; otherwise folders, or Private's albums.
        property var listed: null
        property string newLabel: "NEW ALBUM"
        property var onNew: null
        readonly property var choices: {
            if (listed !== null)
                return listed
            const all = isGroups ? (Vault.revision >= 0 && Vault.groups()) : (Library.revision >= 0 && Settings.revision >= 0 && Library.albums())
            return all.filter(album => album.folder !== exclude)
        }
        contentHeight: pickerColumn.implicitHeight

        Column {
            id: pickerColumn
            width: parent.width

            Title { text: albumPicker.title }

            ListView {
                width: parent.width
                height: Math.min(contentHeight, sheets.height * 0.55)
                clip: true
                model: albumPicker.choices
                boundsBehavior: Flickable.StopAtBounds
                delegate: Pressable {
                    id: choice
                    required property var modelData
                    width: ListView.view.width
                    height: 52 * Theme.dp
                    pressedScale: 0.98
                    onClicked: {
                        const picked = albumPicker.onPicked
                        albumPicker.close()
                        picked(albumPicker.isGroups || albumPicker.listed !== null ? choice.modelData.name : choice.modelData.folder)
                    }

                    Rectangle {
                        anchors.fill: parent
                        anchors.margins: 2 * Theme.dp
                        radius: height / 2
                        color: Theme.pressedWash
                        opacity: choice.isHovered ? 0.6 : 0
                    }
                    SquircleImage {
                        x: 8 * Theme.dp
                        anchors.verticalCenter: parent.verticalCenter
                        width: 36 * Theme.dp
                        height: width
                        radius: 10 * Theme.dp
                        source: (choice.modelData.cover ?? "").length > 0 ? "image://thumbnail/" + encodeURIComponent(choice.modelData.cover) : ""
                        sourceSize: Qt.size(72, 72)
                    }
                    FadeText {
                        x: 58 * Theme.dp
                        width: parent.width - x - 60 * Theme.dp
                        anchors.verticalCenter: parent.verticalCenter
                        text: choice.modelData.name
                        color: Theme.textBright
                        font.family: Theme.heading
                        font.pixelSize: Theme.cardTitleSize
                    }
                    Text {
                        anchors.right: parent.right
                        anchors.rightMargin: 14 * Theme.dp
                        anchors.verticalCenter: parent.verticalCenter
                        text: choice.modelData.count
                        color: Theme.textMuted
                        font.family: Theme.mono
                        font.pixelSize: Theme.labelSize
                    }
                }
            }

            MenuRow {
                glyph: "plus"
                label: albumPicker.newLabel
                ink: Theme.accent
                hasDivider: false
                onClicked: {
                    const picked = albumPicker.onPicked
                    const isGroups = albumPicker.isGroups
                    const onNew = albumPicker.onNew
                    albumPicker.close()
                    if (onNew) {
                        sheets.askName(albumPicker.newLabel, "", onNew)
                        return
                    }
                    sheets.askName("NEW ALBUM", "", name => {
                        const made = isGroups ? Actions.createGroup(name) : Actions.createAlbum(name)
                        if (made.length > 0)
                            picked(isGroups ? name : made)
                    })
                }
            }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── the viewer's ••• ──────────────────────────────────────────────────────────────
    function more() {
        moreMenu.open()
    }

    Sheet {
        id: moreMenu
        readonly property var viewer: sheets.gallery.viewer
        readonly property bool isPrivate: viewer.isPrivate
        contentHeight: moreColumn.implicitHeight

        Column {
            id: moreColumn
            width: parent.width

            // One row: the left 70% moves to an album, the lock after the slash hides into Private; inside Private, move to another of its albums / the open lock moves out.
            MenuRow {
                glyph: "move"
                label: "MOVE TO ALBUM"
                splitGlyph: moreMenu.isPrivate ? "lock-open" : "lock"
                onClicked: {
                    moreMenu.close()
                    sheets.gallery.moveViewerPhoto(moreMenu.isPrivate ? "group" : "album")
                }
                onSplitClicked: {
                    moreMenu.close()
                    sheets.gallery.moveViewerPhoto(moreMenu.isPrivate ? "unhide" : "private")
                }
            }
            MenuRow {
                glyph: "info"
                label: "DETAILS"
                onClicked: {
                    moreMenu.close()
                    moreMenu.viewer.openDetails(true)
                }
            }
            MenuRow {
                visible: sheets.gallery.viewerCoverKey.length > 0
                height: visible ? 52 * Theme.dp : 0
                glyph: "image"
                label: "SET AS COVER"
                onClicked: {
                    moreMenu.close()
                    Actions.setCover(sheets.gallery.viewerCoverKey, moreMenu.viewer.currentPath)
                }
            }
            MenuRow {
                visible: moreMenu.viewer.facts.isVideo === true
                height: visible ? 52 * Theme.dp : 0
                glyph: "image"
                label: "SAVE FRAME"
                onClicked: {
                    moreMenu.close()
                    sheets.gallery.saveFrame()
                }
            }
            MenuRow {
                visible: !moreMenu.isPrivate
                height: visible ? 52 * Theme.dp : 0
                glyph: "folder"
                label: "SHOW IN FILES"
                hasDivider: false
                onClicked: {
                    moreMenu.close()
                    System.showInFolder([moreMenu.viewer.currentPath])
                }
            }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── a cover's menu ────────────────────────────────────────────────────────────────
    function coverMenuFor(cover: var) {
        coverMenu.cover = cover
        coverMenu.open()
    }

    Sheet {
        id: coverMenu
        property var cover: ({})
        readonly property bool isGroup: (cover.key ?? "").startsWith("private:")
        contentHeight: coverColumn.implicitHeight

        Column {
            id: coverColumn
            width: parent.width

            Title { text: (coverMenu.cover.name ?? "").toUpperCase() }

            MenuRow {
                glyph: "trash"
                label: "DELETE ALBUM"
                ink: Theme.danger
                onClicked: {
                    const cover = coverMenu.cover
                    coverMenu.close()
                    sheets.gallery.deleteCover(cover)
                }
            }
            MenuRow {
                glyph: "pen"
                label: "RENAME"
                onClicked: {
                    const cover = coverMenu.cover
                    coverMenu.close()
                    sheets.askName("RENAME", cover.name, name => {
                        if (coverMenu.isGroup)
                            Actions.renameGroup(cover.name, name)
                        else if (cover.isFavoriteAlbum)
                            Actions.renameFavoriteAlbum(cover.name, name)
                        else
                            Actions.renameAlbum(cover.folder, name)
                    })
                }
            }
            // With grouping on: into a group (or another), and out of the one it is in.
            MenuRow {
                readonly property var shelfGrid: sheets.gallery.shelfGridFor(coverMenu.cover)
                readonly property bool isInGroup: shelfGrid.groupOfKey[coverMenu.cover.key] !== undefined
                visible: !coverMenu.isGroup && shelfGrid.isGrouping
                height: visible ? 52 * Theme.dp : 0
                glyph: "move"
                label: isInGroup ? "MOVE TO GROUP" : "ADD TO GROUP"
                splitGlyph: isInGroup ? "close" : ""
                onClicked: {
                    const cover = coverMenu.cover
                    const grid = shelfGrid
                    coverMenu.close()
                    sheets.pickGroup(grid.shelfName, group => grid.moveIntoGroup([cover.key], group))
                }
                onSplitClicked: {
                    const cover = coverMenu.cover
                    const grid = shelfGrid
                    coverMenu.close()
                    grid.removeFromGroups([cover.key])
                }
            }
            MenuRow {
                glyph: coverMenu.isGroup ? "lock-open" : "lock"
                label: coverMenu.isGroup ? "UNLOCK" : "MOVE ALBUM TO PRIVATE"
                onClicked: {
                    const cover = coverMenu.cover
                    coverMenu.close()
                    if (coverMenu.isGroup)
                        sheets.gallery.moveGroupOut(cover)
                    else
                        sheets.gallery.hideAlbum(cover)
                }
            }
            MenuRow {
                glyph: "plus"
                label: "ADD PHOTOS"
                onClicked: {
                    const cover = coverMenu.cover
                    coverMenu.close()
                    sheets.gallery.openPickerFor(cover)
                }
            }
            // Select / rearrange: picking covers on the left, the covers jiggling to be dragged on the right.
            MenuRow {
                glyph: "grid"
                label: "SELECT"
                splitGlyph: "grip"
                hasDivider: false
                onClicked: {
                    const cover = coverMenu.cover
                    coverMenu.close()
                    sheets.gallery.selection.toggleCover(cover.key)
                }
                onSplitClicked: {
                    coverMenu.close()
                    sheets.gallery.selection.isRearranging = true
                }
            }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── a group's menu ────────────────────────────────────────────────────────────────
    function groupMenuFor(grid: var, name: string) {
        groupMenu.grid = grid
        groupMenu.name = name
        groupMenu.open()
    }

    Sheet {
        id: groupMenu
        property var grid: null
        property string name: ""
        readonly property var group: grid ? grid.groups.find(each => each.name === name) : null
        readonly property int photoCount: group ? group.albums.reduce((sum, album) => sum + (album.count ?? 0), 0) : 0
        contentHeight: groupColumn.implicitHeight

        Column {
            id: groupColumn
            width: parent.width

            Title { text: groupMenu.name.toUpperCase() }

            // A group of albums sends their photos to the trash, after Confirm and once more with the count; a Favorites group lets its albums go.
            MenuRow {
                glyph: "trash"
                label: "DELETE GROUP"
                ink: Theme.danger
                onClicked: {
                    const grid = groupMenu.grid
                    const group = groupMenu.group
                    const count = groupMenu.photoCount
                    groupMenu.close()
                    sheets.gallery.selection.confirmThen(() => {
                        if (grid.shelfName === "favorites") {
                            Actions.removeFavoriteAlbums(group.albums.map(album => album.name))
                            grid.ungroup(group.name)
                            return
                        }
                        sheets.confirmCount(count, () => {
                            for (const album of group.albums)
                                Actions.deleteAlbum(album.folder)
                            grid.ungroup(group.name)
                        })
                    })
                }
            }
            MenuRow {
                glyph: "close"
                label: "UNGROUP ALL"
                onClicked: {
                    const grid = groupMenu.grid
                    const name = groupMenu.name
                    groupMenu.close()
                    grid.ungroup(name)
                }
            }
            MenuRow {
                glyph: "pen"
                label: "RENAME"
                onClicked: {
                    const grid = groupMenu.grid
                    const name = groupMenu.name
                    groupMenu.close()
                    sheets.askName("RENAME", name, renamed => grid.renameGroup(name, renamed))
                }
            }
            MenuRow {
                glyph: "grip"
                label: "REARRANGE"
                hasDivider: false
                onClicked: {
                    groupMenu.close()
                    sheets.gallery.selection.isRearranging = true
                }
            }
        }
    }

    // A group still holding photos asks once more, with the count.
    function confirmCount(count: int, action: var) {
        if (count === 0) {
            action()
            return
        }
        countCheck.count = count
        countCheck.action = action
        countCheck.open()
    }

    Sheet {
        id: countCheck
        property int count: 0
        property var action: null
        contentHeight: countColumn.implicitHeight
        Column {
            id: countColumn
            width: parent.width
            Title { text: countCheck.count + (countCheck.count === 1 ? " PHOTO GOES TO THE TRASH" : " PHOTOS GO TO THE TRASH"); color: Theme.danger }
            MenuRow {
                glyph: "trash"
                label: "DELETE ANYWAY"
                ink: Theme.danger
                hasDivider: false
                onClicked: {
                    const action = countCheck.action
                    countCheck.close()
                    action()
                }
            }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── picking albums ──────────────────────────────────────────────────────────────
    // Ticking albums, for a new group: name it, tick its albums, create.
    function pickCovers(title: string, covers: var, onPicked: var) {
        coverPicker.title = title
        coverPicker.covers = covers
        coverPicker.ticked = ({})
        coverPicker.onPicked = onPicked
        coverPicker.open()
    }

    Sheet {
        id: coverPicker
        property string title: ""
        property var covers: []
        property var ticked: ({})
        property var onPicked: null
        readonly property int tickedCount: Object.keys(ticked).length
        contentHeight: coverPickerColumn.implicitHeight

        Column {
            id: coverPickerColumn
            width: parent.width

            Title { text: coverPicker.title }

            ListView {
                width: parent.width
                height: Math.min(contentHeight, sheets.height * 0.5)
                clip: true
                model: coverPicker.covers
                boundsBehavior: Flickable.StopAtBounds
                delegate: Pressable {
                    id: tickRow
                    required property var modelData
                    readonly property bool isTicked: coverPicker.ticked[modelData.key] === true
                    width: ListView.view.width
                    height: 52 * Theme.dp
                    pressedScale: 0.98
                    onClicked: {
                        const next = Object.assign({}, coverPicker.ticked)
                        if (tickRow.isTicked)
                            delete next[tickRow.modelData.key]
                        else
                            next[tickRow.modelData.key] = true
                        coverPicker.ticked = next
                    }
                    SquircleImage {
                        x: 8 * Theme.dp
                        anchors.verticalCenter: parent.verticalCenter
                        width: 36 * Theme.dp
                        height: width
                        radius: 10 * Theme.dp
                        source: (tickRow.modelData.cover ?? "").length > 0 ? "image://thumbnail/" + encodeURIComponent(tickRow.modelData.cover) : ""
                        sourceSize: Qt.size(72, 72)
                    }
                    FadeText {
                        x: 58 * Theme.dp
                        width: parent.width - x - 60 * Theme.dp
                        anchors.verticalCenter: parent.verticalCenter
                        text: tickRow.modelData.name
                        color: Theme.textBright
                        font.family: Theme.heading
                        font.pixelSize: Theme.cardTitleSize
                    }
                    Switch {
                        anchors.right: parent.right
                        anchors.rightMargin: 8 * Theme.dp
                        anchors.verticalCenter: parent.verticalCenter
                        isOn: tickRow.isTicked
                        isEnabled: false
                    }
                }
            }

            Pressable {
                anchors.right: parent.right
                width: createText.implicitWidth + 36 * Theme.dp
                height: 44 * Theme.dp
                isEnabled: coverPicker.tickedCount > 0
                opacity: isEnabled ? 1 : 0.38
                onClicked: {
                    const picked = coverPicker.onPicked
                    const keys = Object.keys(coverPicker.ticked)
                    coverPicker.close()
                    picked(keys)
                }
                Rectangle {
                    anchors.fill: parent
                    radius: height / 2
                    color: Theme.accent
                }
                Text {
                    id: createText
                    anchors.centerIn: parent
                    text: "CREATE"
                    color: Theme.sunkenDeep
                    font.family: Theme.mono
                    font.pixelSize: Theme.labelSize * 1.1
                    font.letterSpacing: Theme.labelSpacing
                    font.bold: true
                }
            }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── restore ───────────────────────────────────────────────────────────────────────
    function restoreChoice(paths: var, isPrivate: bool) {
        restoreMenu.paths = paths
        restoreMenu.isPrivate = isPrivate
        restoreMenu.open()
    }

    Sheet {
        id: restoreMenu
        property var paths: []
        property bool isPrivate: false
        contentHeight: restoreColumn.implicitHeight

        Column {
            id: restoreColumn
            width: parent.width

            MenuRow {
                glyph: "restore"
                label: "RESTORE"
                ink: Theme.accent
                onClicked: {
                    const paths = restoreMenu.paths
                    restoreMenu.close()
                    if (restoreMenu.isPrivate)
                        Actions.restorePrivate(paths, "")
                    else
                        Actions.restore(paths)
                    sheets.gallery.afterRestore()
                }
            }
            MenuRow {
                glyph: "move"
                label: "RESTORE TO ALBUM"
                hasDivider: false
                onClicked: {
                    const paths = restoreMenu.paths
                    const isPrivate = restoreMenu.isPrivate
                    restoreMenu.close()
                    sheets.pickAlbum("RESTORE TO", isPrivate, "", target => {
                        if (isPrivate)
                            Actions.restorePrivate(paths, target)
                        else
                            Actions.restoreTo(paths, target)
                        sheets.gallery.afterRestore()
                    })
                }
            }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── naming ────────────────────────────────────────────────────────────────────────
    // Every name sheet opens with its current name selected whole, so typing replaces it.
    function askName(title: string, initial: string, onNamed: var) {
        nameSheet.title = title
        nameSheet.onNamed = onNamed
        field.text = initial
        nameSheet.open()
        field.forceActiveFocus()
        field.selectAll()
    }

    Sheet {
        id: nameSheet
        property string title: ""
        property var onNamed: null
        contentHeight: nameColumn.implicitHeight

        function confirm() {
            const name = field.text.trim()
            const named = onNamed
            close()
            if (name.length > 0 && named)
                named(name)
        }

        Column {
            id: nameColumn
            width: parent.width
            spacing: 12 * Theme.dp

            Title { text: nameSheet.title }

            Item {
                width: parent.width
                height: 48 * Theme.dp

                Rectangle {
                    anchors.fill: parent
                    radius: height / 2
                    color: Theme.sunkenDeep
                }

                TextInput {
                    id: field
                    anchors.fill: parent
                    anchors.leftMargin: 18 * Theme.dp
                    anchors.rightMargin: 18 * Theme.dp
                    verticalAlignment: TextInput.AlignVCenter
                    color: Theme.textBright
                    selectionColor: Theme.accent
                    selectedTextColor: Theme.sunkenDeep
                    font.family: Theme.heading
                    font.pixelSize: 16 * Theme.dp
                    clip: true
                    onAccepted: nameSheet.confirm()
                    Keys.onEscapePressed: nameSheet.close()
                }
            }

            Pressable {
                anchors.right: parent.right
                width: 56 * Theme.dp
                height: 44 * Theme.dp
                onClicked: nameSheet.confirm()
                Rectangle {
                    anchors.fill: parent
                    radius: height / 2
                    color: Theme.accent
                    Glyph {
                        anchors.centerIn: parent
                        name: "check"
                        ink: Theme.sunkenDeep
                    }
                }
            }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── Private's lock ────────────────────────────────────────────────────────────────
    // The phone unlocks with the fingerprint; here a PIN, set the first time Private is opened.
    function askPin(onUnlocked: var) {
        pinSheet.onUnlocked = onUnlocked
        pinSheet.isSetting = !Vault.hasPin
        pinSheet.firstPin = ""
        pinField.text = ""
        pinSheet.isWrong = false
        pinSheet.open()
        pinField.forceActiveFocus()
    }

    Sheet {
        id: pinSheet
        property var onUnlocked: null
        property bool isSetting: false
        property string firstPin: ""
        property bool isWrong: false
        sheetWidth: Math.min(parent ? parent.width - 32 * Theme.dp : 360, 360 * Theme.dp)
        contentHeight: pinColumn.implicitHeight

        function submit() {
            const pin = pinField.text
            if (pin.length < 4) {
                isWrong = true
                shake.restart()
                return
            }
            if (isSetting) {
                if (firstPin.length === 0) {
                    firstPin = pin
                    pinField.text = ""
                    return
                }
                if (pin !== firstPin) {
                    firstPin = ""
                    pinField.text = ""
                    isWrong = true
                    shake.restart()
                    return
                }
                Vault.setPin(pin)
            } else if (!Vault.unlock(pin)) {
                pinField.text = ""
                isWrong = true
                shake.restart()
                return
            }
            const unlocked = onUnlocked
            close()
            if (unlocked)
                unlocked()
        }

        Column {
            id: pinColumn
            width: parent.width
            spacing: 14 * Theme.dp

            Title {
                text: pinSheet.isSetting ? (pinSheet.firstPin.length === 0 ? "NEW PIN" : "PIN AGAIN") : "PRIVATE"
                color: Theme.privateRed
            }

            Item {
                id: pinBox
                width: parent.width
                height: 52 * Theme.dp
                property real shakeX: 0
                transform: Translate { x: pinBox.shakeX }

                SequentialAnimation {
                    id: shake
                    NumberAnimation { target: pinBox; property: "shakeX"; to: 10 * Theme.dp; duration: 50 }
                    NumberAnimation { target: pinBox; property: "shakeX"; to: -8 * Theme.dp; duration: 70 }
                    NumberAnimation { target: pinBox; property: "shakeX"; to: 0; duration: Motion.stateChange; easing.type: Motion.backOut }
                }

                Rectangle {
                    anchors.fill: parent
                    radius: height / 2
                    color: Theme.sunkenDeep
                    border.width: pinSheet.isWrong ? 1.5 * Theme.dp : 0
                    border.color: Theme.danger
                }

                TextInput {
                    id: pinField
                    anchors.fill: parent
                    anchors.leftMargin: 20 * Theme.dp
                    anchors.rightMargin: 20 * Theme.dp
                    verticalAlignment: TextInput.AlignVCenter
                    horizontalAlignment: TextInput.AlignHCenter
                    echoMode: TextInput.Password
                    passwordCharacter: "●"
                    inputMethodHints: Qt.ImhDigitsOnly | Qt.ImhSensitiveData
                    color: Theme.textBright
                    font.family: Theme.mono
                    font.pixelSize: 20 * Theme.dp
                    font.letterSpacing: 6 * Theme.dp
                    onTextChanged: pinSheet.isWrong = false
                    onAccepted: pinSheet.submit()
                    Keys.onEscapePressed: pinSheet.close()
                }
            }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────
}
