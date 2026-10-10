import QtQuick
import Gallery

// The library shell (GalleryApp.kt, LibraryController.kt, LibraryScreen.kt): where the user stands, what is on screen worked out from it once,
// and every action that crosses between the grids, the bars, the viewer and the sheets.
Item {
    id: shell

    focus: true

    // #region ── state ─────────────────────────────────────────────────────────────────────────
    readonly property alias navigation: navigation
    readonly property alias selection: selection
    readonly property alias viewer: viewer
    readonly property alias sheets: sheets
    // Where every grid was left, by source.
    property var memories: ({})

    Navigation { id: navigation }
    Selection { id: selection }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── the screen: what is shown, worked out once ────────────────────────────────────
    readonly property string section: navigation.section
    readonly property bool isPrivateMode: navigation.isPrivateMode
    readonly property string place: navigation.place
    readonly property bool isInTrash: place === "trash" || place === "private-trash"

    // The source of the photo grid on screen; a place of covers has none.
    readonly property string gridSource: {
        if (section === "recent")
            return isPrivateMode ? "private-recent" : "recent"
        if (section === "favorites") {
            if (isPrivateMode)
                return "private-favorites"
            if (navigation.favoriteAlbum.length > 0)
                return "favorite-album:" + navigation.favoriteAlbum
            return Settings.isFavoritesAsAlbums ? "" : "favorites"
        }
        switch (place) {
        case "folder": return "album:" + navigation.albumsArgument
        case "private-folder": return "private:" + navigation.albumsArgument
        case "trash": return "trash"
        case "private-trash": return "private-trash"
        default: return ""
        }
    }
    readonly property string openedAlbumKey: place === "folder" ? "album:" + navigation.albumsArgument
                                           : place === "private-folder" ? "private:" + navigation.albumsArgument
                                           : section === "favorites" && !isPrivateMode && navigation.favoriteAlbum.length > 0 ? "favorite-album:" + navigation.favoriteAlbum : ""
    readonly property bool isFavoriteAlbumsShown: section === "favorites" && !isPrivateMode && Settings.isFavoritesAsAlbums && navigation.favoriteAlbum.length === 0
    // The cover grid on screen where covers can be grouped: Albums, or the albums inside Favorites.
    readonly property string coverShelf: section === "albums" && place === "folders" ? "albums" : isFavoriteAlbumsShown ? "favorites" : place === "private-groups" ? "private" : ""
    // Whether any picked album sits in a group, so the bar offers to take them out.
    readonly property bool isPickedCoverGrouped: {
        if (selection.revision < 0 || coverShelf.length === 0)
            return false
        const grid = coverShelf === "favorites" ? favoriteCovers : albumCovers
        return selection.pickedCovers().some(key => grid.groupOfKey[key] !== undefined)
    }
    readonly property bool isCoverGrouping: coverShelf.length > 0 && coverShelf !== "private" && (Settings.revision >= 0 && Settings.viewValue(coverShelf, "groupedAlbums")) === true
    readonly property string settingsView: isPrivateMode ? "private" : section === "recent" ? "recent" : section === "favorites" ? "favorites" : place === "trash" ? "trash" : "albums"
    readonly property string settingsViewLabel: ({ private: "Private", recent: "Recent", favorites: "Favorites", trash: "Trash", albums: "Albums" })[settingsView]
    // Albums of any kind may keep photo settings of their own.
    readonly property string settingsFolderKey: openedAlbumKey
    readonly property string folderName: {
        if (section === "recent")
            return "RECENT"
        if (section === "favorites")
            return isPrivateMode || navigation.favoriteAlbum.length === 0 ? (isFavoriteAlbumsShown ? "" : "FAVORITES") : navigation.favoriteAlbum.toUpperCase()
        if (place === "folder")
            return (Library.revision >= 0 && Settings.revision >= 0 && Library.displayName(navigation.albumsArgument)).toUpperCase()
        if (place === "private-folder")
            return navigation.albumsArgument.toUpperCase()
        return ""
    }
    readonly property string placePill: isPrivateMode ? "PRIVATE" : place === "trash" ? "TRASH" : ""
    readonly property color accentTarget: isPrivateMode ? Theme.privateRed : section === "albums" && place === "trash" ? Theme.trashGray : Sections.find(section).accent
    readonly property string albumsGlyph: isPrivateMode ? "lock" : navigation.albumsPlace === "trash" ? "trash" : "albums"
    readonly property bool canGoBack: isPrivateMode ? section === "albums" && (place === "private-folder" || place === "private-trash")
                                                    : (section === "albums" && place !== "folders") || (section === "favorites" && navigation.favoriteAlbum.length > 0)
    readonly property bool canSetCover: openedAlbumKey.length > 0
    readonly property bool canAddPhotos: openedAlbumKey.length > 0 && !selection.isSelectingPhotos
    // Favorites switches between every favourite in one grid and the albums made inside it.
    readonly property bool canToggleFavoritesView: section === "favorites" && !isPrivateMode && navigation.favoriteAlbum.length === 0
    readonly property var activeGrid: section === "recent" ? recentGrid : section === "favorites" ? (gridSource.length > 0 ? favoritesGrid : null) : gridSource.length > 0 ? folderGrid : null
    readonly property string topPillText: Settings.isFolderLabelTop && folderName.length > 0 ? folderName
                                        : activeGrid ? activeGrid.monthLabel : isPrivateMode ? "PRIVATE" : place === "folders" ? "ALBUMS" : isFavoriteAlbumsShown ? "FAVORITES" : ""
    readonly property bool isTopPillFilled: Settings.isFolderLabelTop || !activeGrid
    readonly property bool isSelectionAllFavorite: {
        if (selection.revision < 0)
            return false
        const picked = selection.pickedPhotos()
        if (picked.length === 0)
            return false
        for (const path of picked)
            if (!(Library.revision >= 0 && Vault.revision >= 0 && Library.item(path).isFavorite))
                return false
        return true
    }

    // Private's red reaches the nav on the cut, like every other accent.
    property bool isPrivateInk: isPrivateMode
    onIsPrivateModeChanged: privateInkCut.restart()
    Timer { id: privateInkCut; interval: Motion.sectionLeave; onTriggered: shell.isPrivateInk = shell.isPrivateMode }

    function accentOf(key: string): color {
        if (isPrivateInk)
            return Theme.privateRed
        if (key === "albums" && navigation.albumsPlace === "trash")
            return Theme.trashGray
        return Sections.find(key).accent
    }

    // The accent of the place being switched to, at once; Theme.accent itself only takes it on the cut, once the outgoing view has left.
    onAccentTargetChanged: {
        Theme.accentTarget = accentTarget
        accentCut.restart()
    }
    Timer { id: accentCut; interval: Motion.sectionLeave; onTriggered: Theme.accent = shell.accentTarget }
    Component.onCompleted: {
        Theme.accentTarget = accentTarget
        Theme.accent = accentTarget
        FocusHome.item = shell
    }

    // Where navigation lives on the desktop: the sidebar, the bubble, or both.
    readonly property bool hasSidebar: Settings.navigation !== "bubble"
    readonly property bool hasBubble: Settings.navigation !== "sidebar"

    readonly property string barKind: viewer.isRequested ? "viewer"
                                    : selection.isRearranging ? "rearranging"
                                    : selection.isSelectingCovers ? "covers"
                                    : selection.isSelectingPhotos ? "photos"
                                    : hasBubble ? "navigation" : "none"
    readonly property string barUnderPhoto: selection.isSelectingPhotos ? "photos" : hasBubble ? "navigation" : "none"
    readonly property bool isViewerSettled: viewer.isSettled
    readonly property var viewerPhoto: viewerFacts.kept
    readonly property bool isViewerDeletePending: selection.pendingDelete !== null && viewer.isOpen
    readonly property string viewerCoverKey: viewer.source.startsWith("album:") || (viewer.source.startsWith("private:")) ? viewer.source : ""
    readonly property bool isSettingsOpen: settingsSheet.isOpen

    // The open photo and where it came from, kept after it closes, so its buttons pop away showing what they had.
    QtObject {
        id: viewerFacts
        property var kept: ({})
        readonly property var live: viewer.isRequested ? Object.assign({ isTrash: viewer.isTrash }, viewer.facts) : null
        onLiveChanged: if (live && live.path) kept = live
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── navigation ────────────────────────────────────────────────────────────────────
    function selectSection(key: string) {
        if (selection.isSelectingPhotos || selection.isSelectingCovers)
            selection.clear()
        selection.isFavoritesOnly = false
        const wasHome = key === section
        navigation.select(key)
        if (wasHome && activeGrid)
            activeGrid.glideToNewest()
        else if (wasHome && key === "albums")
            (isPrivateMode ? groupCovers : albumCovers).glideToTop()
    }

    function goBack() {
        selection.clear()
        selection.isFavoritesOnly = false
        navigation.back()
    }

    function leavePlace() {
        selection.clear()
        navigation.leavePlace()
    }

    function openTrash() {
        selection.clear()
        navigation.openTrash()
    }

    function openCover(cover: var) {
        selection.clear()
        selection.isFavoritesOnly = false
        if ((cover.key ?? "").startsWith("private:"))
            navigation.openGroup(cover.name)
        else if ((cover.key ?? "").startsWith("favorite-album:"))
            navigation.openFavoriteAlbum(cover.name)
        else
            navigation.openAlbum(cover.folder)
    }

    function openPlace(key: string) {
        if (key === "trash")
            openTrash()
        else if (key === "private")
            openPrivate()
    }

    // Looking needs the PIN; hiding does not.
    function openPrivate() {
        selection.clear()
        if (Vault.isUnlocked)
            navigation.enterPrivate()
        else
            sheets.askPin(() => navigation.enterPrivate())
    }

    Connections {
        target: Vault
        function onLockChanged() {
            if (!Vault.isUnlocked) {
                if (viewer.isPrivate)
                    viewer.close()
                navigation.onPrivateLocked()
            }
        }
    }

    // Private locks again the moment the app is left.
    Connections {
        target: Qt.application
        function onStateChanged() {
            if (Qt.application.state !== Qt.ApplicationActive && Vault.isUnlocked)
                lockTimer.restart()
            else
                lockTimer.stop()
        }
    }
    Timer { id: lockTimer; interval: 1500; onTriggered: Vault.lock() }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── actions ───────────────────────────────────────────────────────────────────────
    function picked(): var {
        return selection.pickedPhotos()
    }

    function photoAction(name: string) {
        const paths = picked()
        if (paths.length === 0)
            return
        switch (name) {
        case "share":
            System.share(paths)
            undo.offer(paths.length === 1 ? "Copied" : paths.length + " copied", false)
            break
        case "favorite":
            // Favourites every selected photo, or takes them all out once all of them are; the selection stays.
            Actions.setFavorite(paths, !isSelectionAllFavorite)
            break
        case "cover":
            Actions.setCover(openedAlbumKey, paths[0])
            selection.clear()
            break
        case "move":
            if (section === "favorites" && !isPrivateMode)
                sheets.pickFavoriteAlbum(paths, name => {
                    Actions.addToFavoriteAlbum(paths, name)
                    selection.clear()
                })
            else if (isPrivateMode)
                sheets.pickAlbum("MOVE TO ALBUM", true, place === "private-folder" ? Vault.groupFolder(navigation.albumsArgument) : "", group => {
                    Actions.moveToGroup(paths, group)
                    selection.clear()
                })
            else
                sheets.pickAlbum("MOVE TO ALBUM", false, place === "folder" ? navigation.albumsArgument : "", folder => {
                    Actions.move(paths, folder)
                    selection.clear()
                })
            break
        case "private":
            // Moving into Private is confirmed once more after the album is chosen.
            sheets.pickAlbum("MOVE TO PRIVATE", true, "", group => selection.confirmThen(() => {
                Actions.hide(paths, group)
                selection.clear()
            }))
            break
        case "unhide":
            sheets.pickAlbum("MOVE OUT TO ALBUM", false, "", folder => {
                Actions.unhide(paths, folder)
                selection.clear()
            })
            break
        case "delete":
            selection.confirmThen(() => {
                if (isPrivateMode)
                    Actions.trashPrivate(paths)
                else
                    Actions.trash(paths)
                selection.clear()
            })
            break
        case "restore":
            sheets.restoreChoice(paths, place === "private-trash")
            break
        case "deleteForever":
            selection.confirmThen(() => {
                if (place === "private-trash")
                    Actions.deletePrivateForever(paths)
                else
                    Actions.deleteForever(paths)
                selection.clear()
            })
            break
        }
    }

    function afterRestore() {
        selection.clear()
        if (viewer.isRequested && viewer.isTrash && viewer.items.count <= 1)
            viewer.close()
    }

    function coverAction(name: string) {
        const keys = selection.pickedCovers()
        const all = isPrivateMode ? Vault.groups() : coverShelf === "favorites" ? Library.favoriteAlbums() : Library.albums()
        const covers = all.filter(cover => keys.indexOf(cover.key) >= 0)
        const shelfGrid = coverShelf === "favorites" ? favoriteCovers : albumCovers
        switch (name) {
        case "group":
            sheets.pickGroup(coverShelf, group => {
                shelfGrid.moveIntoGroup(keys, group)
                selection.clear()
            })
            break
        case "ungroup":
            shelfGrid.removeFromGroups(keys)
            selection.clear()
            break
        case "private":
            sheets.pickAlbum("MOVE TO PRIVATE", true, "", group => selection.confirmThen(() => {
                for (const cover of covers) {
                    // A Favorites album takes its photos into Private; a folder goes whole.
                    if (cover.isFavoriteAlbum)
                        Actions.hide(Library.pathsFor(cover.key), group)
                    else
                        Actions.hideAlbum(cover.folder, group)
                }
                selection.clear()
            }))
            break
        case "unhide":
            sheets.pickAlbum("MOVE OUT TO ALBUM", false, "", folder => {
                for (const cover of covers)
                    Actions.moveGroupOut(cover.name, folder)
                selection.clear()
            })
            break
        case "delete":
            selection.confirmThen(() => {
                for (const cover of covers) {
                    if (isPrivateMode)
                        Actions.deleteGroup(cover.name)
                    else if (cover.isFavoriteAlbum)
                        Actions.removeFavoriteAlbums([cover.name])
                    else
                        Actions.deleteAlbum(cover.folder)
                }
                selection.clear()
            })
            break
        }
    }

    function deleteCover(cover: var) {
        selection.confirmThen(() => {
            if ((cover.key ?? "").startsWith("private:"))
                Actions.deleteGroup(cover.name)
            // A Favorites album lets its photos be: only the album goes.
            else if (cover.isFavoriteAlbum)
                Actions.removeFavoriteAlbums([cover.name])
            else
                Actions.deleteAlbum(cover.folder)
        })
    }

    // A group's menu acts on the shelf it lies on.
    function shelfGridFor(cover: var): var {
        return (cover.key ?? "").startsWith("favorite-album:") ? favoriteCovers : albumCovers
    }

    function newGroup(shelfName: string) {
        const shelfGrid = shelfName === "favorites" ? favoriteCovers : albumCovers
        sheets.askName("NEW GROUP", "", name => sheets.pickCovers("NEW GROUP · " + name.toUpperCase(), shelfGrid.looseAlbums, keys => {
            if (keys.length > 0)
                shelfGrid.moveIntoGroup(keys, name)
        }))
    }

    function hideAlbum(cover: var) {
        sheets.pickAlbum("MOVE TO PRIVATE", true, "", group => selection.confirmThen(() => Actions.hideAlbum(cover.folder, group)))
    }

    function moveGroupOut(cover: var) {
        sheets.pickAlbum("MOVE OUT TO ALBUM", false, "", folder => Actions.moveGroupOut(cover.name, folder))
    }

    function showCoverMenu(cover: var) {
        sheets.coverMenuFor(cover)
    }

    // A short note in the pill above the bar, with nothing to undo.
    function notify(message: string) {
        undo.offer(message, false)
    }

    function openSettings(button: var) {
        settingsSheet.open(button)
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── the viewer ────────────────────────────────────────────────────────────────────
    function openViewer(grid: var, index: int) {
        selection.pendingDelete = null
        viewer.open(grid.source, index, grid, false)
    }

    // A photo handed in by another app opens inside its album when it is one, otherwise among the files it came with.
    function openExternal(path: string, siblings: var) {
        const source = Library.openFiles(path, siblings)
        if (viewer.isRequested)
            viewer.close()
        externalOpen.source = source
        externalOpen.path = path
        externalOpen.restart()
    }
    Timer {
        id: externalOpen
        property string source: ""
        property string path: ""
        interval: 1
        onTriggered: {
            viewer.open(source, 0, null, true)
            const at = viewer.items.indexOfPath(path)
            viewer.goTo(Math.max(0, at))
        }
    }

    function viewerAction(name: string) {
        const path = viewer.currentPath
        switch (name) {
        case "share":
            System.share([path])
            undo.offer("Copied", false)
            break
        case "favorite":
            Actions.toggleFavorite(path)
            break
        case "crop":
            crop.open(path, viewer)
            break
        case "more":
            sheets.more()
            break
        case "restore":
            sheets.restoreChoice([path], viewer.source === "private-trash")
            break
        case "delete":
            selection.confirmThen(() => {
                if (viewer.source === "trash")
                    Actions.deleteForever([path])
                else if (viewer.source === "private-trash")
                    Actions.deletePrivateForever([path])
                else if (viewer.isPrivate)
                    Actions.trashPrivate([path])
                else
                    Actions.trash([path])
            })
            break
        }
    }

    function moveViewerPhoto(kind: string) {
        const path = viewer.currentPath
        switch (kind) {
        case "album":
            sheets.pickAlbum("MOVE TO ALBUM", false, Library.item(path).folder ?? "", folder => Actions.move([path], folder))
            break
        case "group":
            sheets.pickAlbum("MOVE TO ALBUM", true, "", group => Actions.moveToGroup([path], group))
            break
        case "private":
            sheets.pickAlbum("MOVE TO PRIVATE", true, "", group => selection.confirmThen(() => Actions.hide([path], group)))
            break
        case "unhide":
            sheets.pickAlbum("MOVE OUT TO ALBUM", false, "", folder => Actions.unhide([path], folder))
            break
        }
    }

    function saveFrame() {
        const video = viewer.video
        const position = video && video.item ? video.item.position : 0
        const saved = System.saveFrame(viewer.currentPath, position, viewer.facts.timestamp ?? 0)
        if (saved.length > 0) {
            Library.refresh()
            undo.offer("Frame saved", false)
        }
    }

    // The bar under the photo follows a pull, and settles back or on with the release.
    Connections {
        target: viewer
        function onPullChanged() {
            if (viewer.pull > 0 && !viewer.isClosing)
                bottomBar.seek(shell.barUnderPhoto, viewer.pull / Motion.chromePullShare)
        }
        function onIsClosingChanged() {
            if (viewer.isClosing)
                bottomBar.settleForward()
        }
        function onClosed() {
            selection.pendingDelete = null
            // Opened only to show a file from another app: the window goes back to waiting.
            if (viewer.isExternal && shell.isExternalSession)
                shell.externalClosed()
        }
    }
    property bool isExternalSession: false
    // Sound off holds for every video while the app runs.
    property bool isVideoMuted: false
    signal externalClosed
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── backup ────────────────────────────────────────────────────────────────────────
    function backUp(path: string) {
        if (System.writeTextFile(path, JSON.stringify({ app: "vados-shell", settings: Settings.snapshot() }, null, 1))) {
            Settings.setViewValue("backup", "lastSaved", Date.now())
            undo.offer("Backed up", false)
        }
    }

    // A file that is not a backup changes nothing.
    function restoreBackup(path: string) {
        try {
            const backup = JSON.parse(System.readTextFile(path))
            if (backup.app !== "vados-shell" || !backup.settings)
                return
            Settings.restoreSnapshot(backup.settings)
            Library.refresh()
            undo.offer("Restored", false)
        } catch (error) {
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── layout ────────────────────────────────────────────────────────────────────────
    readonly property real sidebarWidth: hasSidebar ? Settings.sidebarWidthRem * Theme.rem : 0
    readonly property real sidebarShown: hasSidebar ? 1 : 0
    readonly property real contentLeft: sidebarWidth > 0 ? sidebarWidth + 16 * Theme.dp : 0
    readonly property real viewerGrowth: viewer.isOpen ? viewer.growth : 0
    readonly property real topInset: 10 * Theme.dp + topRow.height + 8 * Theme.dp
    readonly property real bottomInset: bottomControls.height + 24 * Theme.dp

    // Everything the glass blurs: the grids and the photo in flight.
    Item {
        id: scene
        anchors.fill: parent
        Component.onCompleted: GlassSource.item = scene

        // The ground is part of what the glass blurs, so an empty stretch blurs to it rather than to black.
        Rectangle {
            anchors.fill: parent
            color: Theme.ground
        }

        Item {
            id: stage
            x: shell.contentLeft
            width: parent.width - x
            height: parent.height

            SectionLayer {
                anchors.fill: parent
                isShown: shell.section === "recent"

                PhotoGrid {
                    id: recentGrid
                    anchors.fill: parent
                    source: shell.isPrivateMode ? "private-recent" : "recent"
                    view: shell.isPrivateMode ? "private" : "recent"
                    selection: shell.selection
                    memories: shell.memories
                    isActive: shell.section === "recent"
                    topInset: shell.topInset
                    bottomInset: shell.bottomInset
                    timelineSlide: shell.viewerGrowth
                    onOpened: index => shell.openViewer(recentGrid, index)
                }
            }

            // Favorites: every favourite in one grid, the albums made inside it, or one of those albums.
            SectionLayer {
                anchors.fill: parent
                isShown: shell.section === "favorites"

                SectionLayer {
                    anchors.fill: parent
                    isFolder: true
                    isShown: shell.section === "favorites" && shell.gridSource.length > 0

                    PhotoGrid {
                        id: favoritesGrid
                        property string keptSource: "favorites"
                        Binding on keptSource { when: shell.section === "favorites" && shell.gridSource.length > 0; value: shell.gridSource; restoreMode: Binding.RestoreNone }
                        anchors.fill: parent
                        source: keptSource
                        view: shell.isPrivateMode ? "private" : "favorites"
                        folderKey: keptSource.startsWith("favorite-album:") ? keptSource : ""
                        selection: shell.selection
                        memories: shell.memories
                        isActive: shell.section === "favorites" && shell.gridSource.length > 0
                        topInset: shell.topInset
                        bottomInset: shell.bottomInset
                        timelineSlide: shell.viewerGrowth
                        onOpened: index => shell.openViewer(favoritesGrid, index)
                    }
                }

                SectionLayer {
                    anchors.fill: parent
                    isFolder: true
                    isShown: shell.isFavoriteAlbumsShown

                    CoverGrid {
                        id: favoriteCovers
                        anchors.fill: parent
                        covers: (Library.revision >= 0 && Settings.revision >= 0 && Library.favoriteAlbums())
                        shelfName: "favorites"
                        view: "favorites"
                        isGrouping: (Settings.revision >= 0 && Settings.viewValue("favorites", "groupedAlbums")) === true
                        selection: shell.selection
                        memories: shell.memories
                        isActive: shell.isFavoriteAlbumsShown
                        topInset: shell.topInset
                        bottomInset: shell.bottomInset
                        onOpened: cover => shell.openCover(cover)
                        onMenuAsked: cover => shell.showCoverMenu(cover)
                        onGroupMenuAsked: name => shell.sheets.groupMenuFor(favoriteCovers, name)
                        onNewAsked: shell.newFavoriteAlbum()
                        onNewGroupAsked: shell.newGroup("favorites")
                    }
                }
            }

            SectionLayer {
                id: albumsLayer
                anchors.fill: parent
                isShown: shell.section === "albums"

                SectionLayer {
                    anchors.fill: parent
                    isFolder: true
                    isShown: shell.place === "folders"

                    CoverGrid {
                        id: albumCovers
                        anchors.fill: parent
                        covers: (Library.revision >= 0 && Settings.revision >= 0 && Library.albums())
                        places: [
                            { key: "private", name: "Private", glyph: "lock", accent: Theme.privateRed, count: -1 },
                            { key: "trash", name: "Trash", glyph: "trash", accent: Theme.trashGray, count: Library.trashCount },
                        ]
                        shelfName: "albums"
                        view: "albums"
                        isGrouping: (Settings.revision >= 0 && Settings.viewValue("albums", "groupedAlbums")) === true
                        selection: shell.selection
                        memories: shell.memories
                        isActive: shell.section === "albums" && shell.place === "folders"
                        topInset: shell.topInset
                        bottomInset: shell.bottomInset
                        onOpened: cover => shell.openCover(cover)
                        onMenuAsked: cover => shell.showCoverMenu(cover)
                        onGroupMenuAsked: name => shell.sheets.groupMenuFor(albumCovers, name)
                        onPlaceOpened: key => shell.openPlace(key)
                        onNewAsked: shell.newAlbum(false)
                        onNewGroupAsked: shell.newGroup("albums")
                    }
                }

                SectionLayer {
                    anchors.fill: parent
                    isFolder: true
                    isShown: shell.place === "private-groups"

                    CoverGrid {
                        id: groupCovers
                        anchors.fill: parent
                        covers: (Vault.revision >= 0 && Vault.groups())
                        places: [{ key: "trash", name: "Trash", glyph: "trash", accent: Theme.trashGray, count: (Vault.revision >= 0 && Vault.trashCount()) }]
                        shelfName: "private"
                        view: "private"
                        selection: shell.selection
                        memories: shell.memories
                        isActive: shell.place === "private-groups"
                        topInset: shell.topInset
                        bottomInset: shell.bottomInset
                        header: Settings.hasTodaysSelection ? todaysSelection : null
                        onOpened: cover => shell.openCover(cover)
                        onMenuAsked: cover => shell.showCoverMenu(cover)
                        onPlaceOpened: key => shell.openTrash()
                        onNewAsked: shell.newAlbum(true)
                    }
                }

                // The photo grid of the place open inside Albums: an album, a private album, either trash. It keeps its source while it leaves.
                SectionLayer {
                    anchors.fill: parent
                    isFolder: true
                    isShown: shell.section === "albums" && shell.gridSource.length > 0

                    PhotoGrid {
                        id: folderGrid
                        property string keptSource: ""
                        property string keptView: "albums"
                        property string keptFolderKey: ""
                        Binding on keptSource { when: shell.section === "albums" && shell.gridSource.length > 0; value: shell.gridSource; restoreMode: Binding.RestoreNone }
                        Binding on keptView { when: shell.section === "albums" && shell.gridSource.length > 0; value: shell.settingsView; restoreMode: Binding.RestoreNone }
                        Binding on keptFolderKey { when: shell.section === "albums" && shell.gridSource.length > 0; value: shell.settingsFolderKey; restoreMode: Binding.RestoreNone }
                        anchors.fill: parent
                        source: keptSource
                        view: keptView
                        folderKey: keptFolderKey
                        selection: shell.selection
                        memories: shell.memories
                        isActive: shell.section === "albums" && shell.gridSource.length > 0
                        topInset: shell.topInset
                        bottomInset: shell.bottomInset
                        timelineSlide: shell.viewerGrowth
                        onOpened: index => shell.openViewer(folderGrid, index)
                    }
                }
            }
        }

        Viewer {
            id: viewer
            anchors.fill: parent
            gallery: shell
        }
    }

    // Today's selection: one random private favourite, a new pick every time Private is entered.
    Component {
        id: todaysSelection
        Item {
            id: todaysCard
            readonly property var pick: (Vault.revision >= 0 && Vault.todaysSelection())
            width: parent ? parent.width : 0
            height: (pick.path ?? "").length > 0 ? Math.min(width * 0.6, 420 * Theme.dp) + 20 * Theme.dp : 0
            visible: height > 0
            Pressable {
                x: 14 * Theme.dp
                width: parent.width - 28 * Theme.dp
                height: parent.height - 20 * Theme.dp
                onClicked: shell.openExternal(todaysCard.pick.path, [])
                SquircleImage {
                    anchors.fill: parent
                    radius: Theme.panelRadius
                    source: (todaysCard.pick.path ?? "").length > 0 ? "image://thumbnail/" + encodeURIComponent(todaysCard.pick.path) : ""
                    sourceSize: Qt.size(1024, 1024)
                }
                Text {
                    x: 18 * Theme.dp
                    y: 14 * Theme.dp
                    text: "TODAY'S SELECTION"
                    color: Theme.textBright
                    font.family: Theme.mono
                    font.pixelSize: Theme.labelSize
                    font.letterSpacing: Theme.labelSpacing
                    style: Text.Raised
                    styleColor: Qt.rgba(0, 0, 0, 0.7)
                }
            }
        }
    }

    function newAlbum(isGroup: bool) {
        sheets.askName("NEW ALBUM", "", name => {
            if (isGroup) {
                Actions.createGroup(name)
                navigation.openGroup(name)
                openPicker()
            } else {
                const folder = Actions.createAlbum(name)
                if (folder.length > 0)
                    picker.openFor(folder, false, name)
            }
        })
    }

    // A new album inside Favorites: named, then filled from the favourites not in an album yet.
    function newFavoriteAlbum() {
        sheets.askName("NEW ALBUM", "", name => picker.openForFavorites(name))
    }

    function toggleFavoritesView() {
        selection.clear()
        Settings.isFavoritesAsAlbums = !Settings.isFavoritesAsAlbums
    }

    function openPicker() {
        if (place === "folder")
            picker.openFor(navigation.albumsArgument, false, Library.displayName(navigation.albumsArgument))
        else if (place === "private-folder")
            picker.openFor(navigation.albumsArgument, true, navigation.albumsArgument)
        else if (section === "favorites" && navigation.favoriteAlbum.length > 0)
            picker.openForFavorites(navigation.favoriteAlbum)
    }

    function openPickerFor(cover: var) {
        if (cover.isFavoriteAlbum) {
            picker.openForFavorites(cover.name)
            return
        }
        const isGroup = (cover.key ?? "").startsWith("private:")
        picker.openFor(isGroup ? cover.name : cover.folder, isGroup, cover.name)
    }

    // Opened inside Private, duplicates are looked for among the private photos alone; anywhere else, across the whole library.
    function openDuplicates() {
        duplicates.open(isPrivateMode)
    }

    function openReview() {
        if (activeGrid)
            review.open(activeGrid.source, isPrivateMode)
    }

    Sidebar {
        id: sidebar
        gallery: shell
        x: 10 * Theme.dp - (width + 20 * Theme.dp) * shell.viewerGrowth
        y: 10 * Theme.dp
        width: Math.max(1, shell.sidebarWidth - 4 * Theme.dp)
        height: parent.height - 20 * Theme.dp
        visible: shell.hasSidebar && x > -width
        opacity: shell.hasSidebar ? 1 : 0
    }

    // The top row slides off the top as a photo grows, following it frame by frame.
    TopRow {
        id: topRow
        gallery: shell
        x: shell.contentLeft + 12 * Theme.dp
        y: 10 * Theme.dp - (height + 20 * Theme.dp) * shell.viewerGrowth
        width: parent.width - x - 12 * Theme.dp
        visible: y > -height
    }

    // The bottom controls: Confirm, the pills that name the place, and the one pill. Over an open photo they hold its buttons.
    Item {
        id: bottomControls
        readonly property real centreX: shell.contentLeft + (shell.width - shell.contentLeft) / 2
        readonly property real hiddenShare: viewer.isOpen ? (viewer.isChromeVisible || viewer.pull > 0 ? 0 : 1) : 0
        property real slide: 0
        Behavior on slide { NumberAnimation { duration: shell.viewer.isChromeVisible ? Motion.overlayEnter : Motion.overlayLeave; easing.type: shell.viewer.isChromeVisible ? Motion.backOut : Motion.powerTwoIn } }
        Binding on slide { value: Math.max(bottomControls.hiddenShare, shell.viewer.lift) }
        width: parent.width
        height: column.height
        y: parent.height - height - 16 * Theme.dp + slide * (bottomBar.height + 40 * Theme.dp)

        Column {
            id: column
            x: bottomControls.centreX - width / 2
            width: Math.max(bottomBar.width, places.width, undo.width)
            spacing: 8 * Theme.dp

            UndoPill {
                id: undo
                anchors.horizontalCenter: parent.horizontalCenter
                Connections {
                    target: Actions
                    function onOffered(message, isUndoable) { undo.offer(message, isUndoable) }
                    function onFailed(message) { undo.offer(message, false) }
                }
            }

            // The pills above the nav go with it, popping away as a selection's bar comes.
            Column {
                id: places
                anchors.horizontalCenter: parent.horizontalCenter
                spacing: 8 * Theme.dp
                readonly property bool isNavigation: shell.barKind === "navigation" || shell.barKind === "none"

                Pop {
                    anchors.horizontalCenter: parent.horizontalCenter
                    isShown: places.isNavigation && !Settings.isFolderLabelTop && shell.folderName.length > 0
                    OwnAccent { id: folderAccent; isShown: !Settings.isFolderLabelTop && shell.folderName.length > 0 }
                    TypedLabel {
                        text: shell.folderName
                        accent: folderAccent.color
                    }
                }

                // A photo opened in the trash says where it was.
                Pop {
                    id: originPill
                    anchors.horizontalCenter: parent.horizontalCenter
                    isShown: viewer.isSettled && viewer.isTrash && origin.length > 0
                    readonly property string origin: viewer.isTrash ? (Library.revision >= 0 && Library.originalAlbumName(viewer.currentPath)) : ""
                    Item {
                        width: Math.min(originText.implicitWidth + 28 * Theme.dp, 320 * Theme.dp)
                        height: 30 * Theme.dp
                        Glass { anchors.fill: parent }
                        FadeText {
                            id: originText
                            anchors.centerIn: parent
                            width: Math.min(implicitWidth, parent.width - 28 * Theme.dp)
                            text: "Was originally in " + originPill.origin
                            color: Theme.textBright
                            font.pixelSize: Theme.labelSize * 1.1
                        }
                    }
                }

                // Empties the whole trash for good, so it waits for Confirm.
                Pop {
                    anchors.horizontalCenter: parent.horizontalCenter
                    isShown: places.isNavigation && !viewer.isOpen && shell.isInTrash && (Library.revision >= 0 && Vault.revision >= 0 && Library.countFor(shell.gridSource)) > 0
                    Pressable {
                        width: deleteNow.implicitWidth + 28 * Theme.dp
                        height: 32 * Theme.dp
                        onClicked: shell.selection.confirmThen(() => shell.place === "private-trash" ? Actions.emptyPrivateTrash() : Actions.emptyTrash())
                        Glass { anchors.fill: parent }
                        Text {
                            id: deleteNow
                            anchors.centerIn: parent
                            text: "DELETE NOW"
                            color: Theme.danger
                            font.family: Theme.mono
                            font.pixelSize: Theme.labelSize
                            font.letterSpacing: Theme.labelSpacing
                        }
                    }
                }

                Pop {
                    anchors.horizontalCenter: parent.horizontalCenter
                    isShown: places.isNavigation && !viewer.isOpen && shell.placePill.length > 0
                    property string kept: ""
                    Binding on kept { when: shell.placePill.length > 0; value: shell.placePill; restoreMode: Binding.RestoreNone }
                    OwnAccent { id: placeAccent; isShown: shell.placePill.length > 0 }
                    PlacePill {
                        label: parent.parent.kept
                        accent: placeAccent.color
                        onExited: shell.leavePlace()
                    }
                }
            }

            BottomBar {
                id: bottomBar
                anchors.horizontalCenter: parent.horizontalCenter
                gallery: shell
                wanted: shell.barKind
            }
        }

        // Every delete waits here, above the bar, over anything already there.
        ConfirmPill {
            selection: shell.selection
            x: bottomControls.centreX - width / 2
            y: bottomControls.height - bottomBar.height - height - 14 * Theme.dp
            z: 5
        }

        // In the room right of the nav: the favourites-only heart, and the month's count under it.
        FavoritesCorner {
            gallery: shell
            anchors.bottom: parent.bottom
            x: bottomControls.centreX + bottomBar.width / 2 + ((shell.width - (bottomControls.centreX + bottomBar.width / 2)) - width) / 2
            height: bottomBar.height
        }
    }

    ViewerChrome {
        anchors.fill: parent
        viewer: shell.viewer
        gallery: shell
    }

    Picker {
        id: picker
        anchors.fill: parent
        gallery: shell
    }

    Review {
        id: review
        anchors.fill: parent
        gallery: shell
    }

    DuplicatesScreen {
        id: duplicates
        anchors.fill: parent
        gallery: shell
    }

    Crop {
        id: crop
        anchors.fill: parent
        gallery: shell
    }

    SettingsSheet {
        id: settingsSheet
        gallery: shell
    }

    Sheets {
        id: sheets
        gallery: shell
    }

    // Anything but Confirm lets a waiting delete go.
    MouseArea {
        anchors.fill: parent
        z: 4
        enabled: selection.pendingDelete !== null
        onPressed: mouse => {
            selection.pendingDelete = null
            mouse.accepted = true
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── keys ──────────────────────────────────────────────────────────────────────────
    // Escape is the system back: a waiting delete, then a sheet, then the photo, then a selection, then the place.
    Keys.onPressed: event => {
        if (event.key === Qt.Key_Escape || event.key === Qt.Key_Back) {
            event.accepted = true
            if (selection.pendingDelete !== null)
                selection.pendingDelete = null
            else if (settingsSheet.isOpen)
                settingsSheet.close()
            else if (sheets.closeAll())
                return
            else if (crop.isOpen)
                crop.close()
            else if (duplicates.isOpen)
                duplicates.close()
            else if (review.isOpen)
                review.close()
            else if (picker.isOpen)
                picker.close()
            else if (viewer.isRequested)
                viewer.lift > 0 ? viewer.openDetails(false) : viewer.close()
            else if (selection.isSelectingPhotos || selection.isSelectingCovers || selection.isRearranging) {
                selection.clear()
                selection.isRearranging = false
            } else
                goBack()
            return
        }
        if (viewer.isRequested && !viewer.isClosing) {
            switch (event.key) {
            case Qt.Key_Left: viewer.step(-1); event.accepted = true; return
            case Qt.Key_Right: viewer.step(1); event.accepted = true; return
            case Qt.Key_F: viewerAction("favorite"); event.accepted = true; return
            case Qt.Key_Delete: viewerAction("delete"); event.accepted = true; return
            case Qt.Key_Up: viewer.openDetails(true); event.accepted = true; return
            case Qt.Key_Down: viewer.lift > 0 ? viewer.openDetails(false) : viewer.close(); event.accepted = true; return
            case Qt.Key_Return:
            case Qt.Key_Enter:
                if (selection.pendingDelete !== null) {
                    selection.confirm()
                    event.accepted = true
                }
                return
            }
            return
        }
        if ((event.key === Qt.Key_Return || event.key === Qt.Key_Enter) && selection.pendingDelete !== null) {
            selection.confirm()
            event.accepted = true
            return
        }
        if (event.key === Qt.Key_A && (event.modifiers & Qt.ControlModifier) && activeGrid) {
            selection.setPhotos(activeGrid.model.allPaths(), true)
            event.accepted = true
            return
        }
        if (event.key === Qt.Key_Delete && selection.isSelectingPhotos) {
            photoAction(isInTrash ? "deleteForever" : "delete")
            event.accepted = true
            return
        }
        if (event.key === Qt.Key_Comma && (event.modifiers & Qt.ControlModifier)) {
            settingsSheet.open(topRow)
            event.accepted = true
            return
        }
        // Ctrl+1/2/3 jump to a section, in the registry's order.
        if ((event.modifiers & Qt.ControlModifier) && event.key >= Qt.Key_1 && event.key < Qt.Key_1 + Sections.all.length) {
            selectSection(Sections.all[event.key - Qt.Key_1].key)
            event.accepted = true
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────
}
