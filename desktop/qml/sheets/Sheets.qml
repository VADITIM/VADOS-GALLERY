import QtQuick
import QtQuick.Controls.Basic
import Gallery

// Every sheet the library opens (LibrarySheetHost.kt), each one a glass sheet; opening one always goes through a function here, which says what it acts on.
Item {
    id: sheets

    required property var gallery
    readonly property bool isAnyOpen: albumPicker.isOpen || moreMenu.isOpen || coverMenu.isOpen || nameSheet.isOpen || pinSheet.isOpen || restoreMenu.isOpen

    anchors.fill: parent

    function closeAll(): bool {
        for (const sheet of [albumPicker, moreMenu, coverMenu, nameSheet, pinSheet, restoreMenu]) {
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
        albumPicker.onPicked = onPicked
        albumPicker.open()
    }

    Sheet {
        id: albumPicker
        property string title: ""
        property bool isGroups: false
        property string exclude: ""
        property var onPicked: null
        readonly property var choices: {
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
                        picked(albumPicker.isGroups ? choice.modelData.name : choice.modelData.folder)
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
                label: "NEW ALBUM"
                ink: Theme.accent
                hasDivider: false
                onClicked: {
                    const picked = albumPicker.onPicked
                    const isGroups = albumPicker.isGroups
                    albumPicker.close()
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
                        else
                            Actions.renameAlbum(cover.folder, name)
                    })
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
            MenuRow {
                glyph: "grid"
                label: "SELECT"
                hasDivider: false
                onClicked: {
                    const cover = coverMenu.cover
                    coverMenu.close()
                    sheets.gallery.selection.toggleCover(cover.key)
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
