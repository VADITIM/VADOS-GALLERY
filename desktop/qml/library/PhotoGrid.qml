import QtQuick
import Gallery

// Every photo grid (MediaGrid.kt, VAS components/20): Recent, Favorites, every album, Private's grids and the trash are this one piece, so a feature in it is in all of them.
// Oldest at the top, newest at the bottom right, opened at the bottom.
Item {
    id: photoGrid

    property string source: ""
    property string view: "recent"
    // An album's key, under which it may keep photo settings of its own.
    property string folderKey: ""
    // The photo picker leaves out what is already in the album it adds to.
    property string excludedFolder: ""
    required property var selection
    required property var memories
    property bool isActive: true
    property real topInset: 0
    property real bottomInset: 0
    property real sideInset: 8 * Theme.dp
    property real timelineInset: 0
    // How far the timeline has slid off with an opening photo, 0 to 1.
    property real timelineSlide: 0
    readonly property alias model: gridModel
    readonly property alias list: list
    readonly property int columns: (Settings.revision >= 0 && Settings.gridValue(view, folderKey, "columns"))
    readonly property real gap: 3 * Theme.dp
    readonly property real cell: Math.max(8, (width - sideInset - Math.max(sideInset, timelineInset) - gap * (columns - 1)) / columns)
    // The month of the photos at the top, and how many of this grid were taken in it.
    property string monthLabel: ""
    property int monthCount: 0
    property int monthKey: 0

    // The stacks of similar shots laid out in place, by their newest shot.
    property var openStacks: []

    signal opened(int index)

    function toggleStack(stackKey: string) {
        const at = openStacks.indexOf(stackKey)
        openStacks = at >= 0 ? openStacks.filter(key => key !== stackKey) : openStacks.concat([stackKey])
    }

    // #region ── model ─────────────────────────────────────────────────────────────────────────
    MediaGridModel {
        id: gridModel
        source: photoGrid.source
        columns: photoGrid.columns
        dateGroups: (Settings.revision >= 0 && Settings.gridValue(photoGrid.view, photoGrid.folderKey, "dateGroups"))
        hasHeaders: (Settings.revision >= 0 && Settings.gridValue(photoGrid.view, photoGrid.folderKey, "headers"))
        hasDayStamps: Settings.hasDayStamps
        isFavoritesOnly: photoGrid.selection.isFavoritesOnly && photoGrid.view !== "favorites" && !gridModel.isTrash
        excludedFolder: photoGrid.excludedFolder
        isStacking: (Settings.revision >= 0 && Settings.viewValue(photoGrid.view, "stackSimilar")) === true && photoGrid.excludedFolder.length === 0
        openStacks: photoGrid.openStacks

        onAboutToRebuild: photoGrid.remember()
        onRebuilt: {
            photoGrid.restore()
            photoGrid.selection.keepOnly(gridModel.allPaths())
        }
    }

    onSourceChanged: entranceStartedAt = Date.now()
    onIsActiveChanged: {
        if (isActive)
            entranceStartedAt = Date.now()
        else
            remember()
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── memory: where each grid was left ─────────────────────────────────────────────
    property string rememberedSource: ""

    function remember() {
        if (rememberedSource.length === 0 || list.count === 0)
            return
        const isAtEnd = list.contentY + list.height >= list.contentHeight + list.originY + list.bottomMargin - 4
        const row = list.indexAt(photoGrid.width / 2, list.contentY + list.topMargin + 1)
        const item = row >= 0 ? list.itemAtIndex(row) : null
        memories[rememberedSource] = {
            key: row >= 0 ? gridModel.data(gridModel.index(row, 0), MediaGridModel.KeyRole) : "",
            row: row,
            offset: item ? list.contentY + list.topMargin - item.y : 0,
            isAtEnd: isAtEnd || row < 0,
        }
    }

    function restore() {
        const memory = memories[source]
        rememberedSource = source
        if (!memory || memory.isAtEnd) {
            list.positionViewAtEnd()
            return
        }
        // The row that holds the photo that was at the top; it may have moved if photos came or went.
        let row = -1
        for (let index = Math.max(0, memory.row - 50); index < Math.min(list.count, memory.row + 50); ++index) {
            if (gridModel.data(gridModel.index(index, 0), MediaGridModel.KeyRole) === memory.key) {
                row = index
                break
            }
        }
        if (row < 0) {
            const item = gridModel.indexOfPath(memory.key)
            row = item >= 0 ? gridModel.rowOfItem(item) : Math.min(memory.row, list.count - 1)
        }
        list.positionViewAtIndex(row, ListView.Beginning)
        list.contentY += memory.offset - list.topMargin
        readMonth()
    }

    Component.onDestruction: remember()
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── entrance ──────────────────────────────────────────────────────────────────────
    property double entranceStartedAt: Date.now()
    property int entranceRank: 0
    onEntranceStartedAtChanged: entranceRank = 0

    function nextEntranceRank(): int {
        if (Date.now() - entranceStartedAt > Motion.entranceWindow || Motion.isReduced)
            return -1
        return Math.min(16, entranceRank++)
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── geometry ──────────────────────────────────────────────────────────────────────
    function headerHeight(level: string): real {
        switch (level) {
        case "year": return (30 + 32 + 6) * Theme.dp
        case "month": return ((gridModel.dateGroups.indexOf("weeks") >= 0 ? 34 : 18) + 21 + 8) * Theme.dp
        case "week": return (8 + 15 + 4) * Theme.dp
        default: return (6 + 14 + 3) * Theme.dp
        }
    }

    // The item under a point of the grid, or -1; a folded stack answers its newest shot.
    function itemAt(x: real, y: real): int {
        return gridModel.itemOfTile(tileAt(x, y))
    }

    // The tile under a point of the grid, or -1.
    function tileAt(x: real, y: real): int {
        const row = list.indexAt(x, y + list.contentY)
        if (row < 0)
            return -1
        const kind = gridModel.data(gridModel.index(row, 0), MediaGridModel.KindRole)
        if (kind !== 1)
            return -1
        const column = Math.floor((x - sideInset) / (cell + gap))
        const count = gridModel.data(gridModel.index(row, 0), MediaGridModel.TileCountRole)
        if (column < 0 || column >= count || x - sideInset - column * (cell + gap) > cell)
            return -1
        return gridModel.data(gridModel.index(row, 0), MediaGridModel.FirstRole) + column
    }

    // Where a tile stands in the window, scrolling the grid to it first when it is not on screen; null when there is none.
    function tileRect(index: int, isScrolling: bool): var {
        const row = gridModel.rowOfItem(index)
        if (row < 0)
            return null
        let item = list.itemAtIndex(row)
        const isOnScreen = item && item.y + item.height > list.contentY + list.topMargin && item.y < list.contentY + list.height - list.bottomMargin
        if (!isOnScreen) {
            if (!isScrolling)
                return null
            list.positionViewAtIndex(row, ListView.Center)
            item = list.itemAtIndex(row)
            if (!item)
                return null
        }
        const column = gridModel.tileOfItem(index) - gridModel.data(gridModel.index(row, 0), MediaGridModel.FirstRole)
        const local = Qt.point(sideInset + column * (cell + gap), item.y - list.contentY)
        const at = list.mapToItem(null, local.x, local.y)
        return Qt.rect(at.x, at.y, cell, cell)
    }

    function stopGlide() {
        glide.stop()
    }

    function readMonth() {
        const row = list.indexAt(photoGrid.width / 2, list.contentY + list.topMargin + 2)
        if (row < 0)
            return
        const month = gridModel.monthAtRow(row)
        monthLabel = month.label ?? ""
        monthCount = month.count ?? 0
        monthKey = month.key ?? 0
    }

    // One continuous glide to the newest end, re-guessing the distance every frame and never moving back (VAS components/20 §6).
    function glideToNewest() {
        glide.startY = list.contentY
        const screens = Math.abs(list.contentHeight - list.contentY) / Math.max(1, list.height)
        glide.duration = Math.min(Motion.scrollToEndMax, Motion.scrollToEnd + screens * Motion.scrollToEndPerScreen)
        glide.startedAt = Date.now()
        glide.start()
    }

    FrameAnimation {
        id: glide
        property real startY: 0
        property real duration: 400
        property double startedAt: 0
        onTriggered: {
            const progress = Math.min(1, (Date.now() - startedAt) / duration)
            const end = list.contentHeight + list.originY - list.height + list.bottomMargin
            list.contentY = Math.max(list.contentY, startY + (end - startY) * Motion.powerThreeInOutAt(progress))
            if (progress >= 1) {
                stop()
                list.positionViewAtEnd()
            }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── the list ──────────────────────────────────────────────────────────────────────
    ListView {
        id: list
        anchors.fill: parent
        model: gridModel
        topMargin: photoGrid.topInset
        bottomMargin: photoGrid.bottomInset
        cacheBuffer: Math.max(0, height)
        reuseItems: true
        boundsBehavior: Flickable.StopAtBounds
        interactive: false
        flickDeceleration: 4000
        maximumFlickVelocity: 8000
        onContentYChanged: photoGrid.readMonth()

        delegate: Item {
            id: row
            required property int index
            required property int kind
            required property string level
            required property string label
            required property int first
            required property int tileCount

            width: list.width
            height: kind === 0 ? photoGrid.headerHeight(level) : photoGrid.cell + photoGrid.gap

            Text {
                visible: row.kind === 0
                x: photoGrid.sideInset + 4 * Theme.dp
                anchors.bottom: parent.bottom
                anchors.bottomMargin: (row.level === "year" ? 6 : row.level === "month" ? 8 : row.level === "week" ? 4 : 3) * Theme.dp
                text: row.label
                // Dates sit at the left, across the grid from the timeline, in the section colour so they read as the grid's markers.
                color: row.level === "day" ? Theme.textMuted : Theme.accent
                font.family: row.level === "year" || row.level === "month" ? Theme.heading : Theme.mono
                font.pixelSize: (row.level === "year" ? 24 : row.level === "month" ? 16 : row.level === "week" ? 11 : 10) * Theme.dp
                font.letterSpacing: (row.level === "week" || row.level === "day" ? 3 : 0.5) * Theme.dp
            }

            Repeater {
                model: row.kind === 1 ? row.tileCount : 0

                Tile {
                    required property int index
                    x: photoGrid.sideInset + index * (photoGrid.cell + photoGrid.gap)
                    tileIndex: row.first + index
                    grid: photoGrid
                }
            }
        }

        // The wheel scrolls by a steady share of the screen, as a desktop list is expected to.
        WheelHandler {
            acceptedModifiers: Qt.NoModifier
            onWheel: event => {
                glide.stop()
                const delta = event.pixelDelta.y !== 0 ? event.pixelDelta.y : event.angleDelta.y / 120 * photoGrid.cell * 0.9
                const top = list.originY - list.topMargin
                const end = list.contentHeight + list.originY - list.height + list.bottomMargin
                list.contentY = Math.max(top, Math.min(Math.max(top, end), list.contentY - delta))
            }
        }
    }

    // Ctrl and the wheel, or a pinch on the touchpad, step the columns; a spread means fewer, larger photos.
    WheelHandler {
        acceptedModifiers: Qt.ControlModifier
        property real collected: 0
        onWheel: event => {
            collected += event.angleDelta.y
            if (Math.abs(collected) < 120)
                return
            photoGrid.stepColumns(collected > 0 ? -1 : 1)
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
                photoGrid.stepColumns(-1)
                stepScale = 1
            } else if (stepScale < 1 / 1.28) {
                photoGrid.stepColumns(1)
                stepScale = 1
            }
        }
    }

    // Picks or lets go a tile: one photo, or every shot of a folded stack.
    function pick(tile: var, isPicked: bool) {
        if (tile.stackSize > 0 && tile.stackPosition === 0)
            selection.setPhotos(gridModel.stackPaths(tile.stackKey), isPicked)
        else
            selection.setPhoto(tile.path, isPicked)
    }

    // The top right corner of a tile, where a laid-out shot's place (2/5) stands.
    function isOnStackMark(x: real, y: real, tileIndex: int): bool {
        const row = gridModel.rowOfItem(gridModel.itemOfTile(tileIndex))
        const item = list.itemAtIndex(row)
        if (!item)
            return false
        const column = tileIndex - gridModel.data(gridModel.index(row, 0), MediaGridModel.FirstRole)
        const left = sideInset + column * (cell + gap)
        const top = item.y - list.contentY
        return x > left + cell - 46 * Theme.dp && y < top + 30 * Theme.dp
    }

    function stepColumns(step: int) {
        const next = Math.max(1, Math.min(6, columns + step))
        if (next === columns)
            return
        remember()
        Settings.setGridValue(view, folderKey, "columns", next)
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── pointer: open, pick, slide to pick, drag out ──────────────────────────────────
    MouseArea {
        id: pointer
        anchors.fill: parent
        acceptedButtons: Qt.LeftButton | Qt.RightButton
        hoverEnabled: false
        property point pressedAt
        property int pressedIndex: -1
        property bool isSliding: false
        property bool isDragging: false
        property bool isAdding: true
        property var visited: ({})
        property real pointerY: 0

        onPressed: mouse => {
            pressedAt = Qt.point(mouse.x, mouse.y)
            pressedIndex = photoGrid.itemAt(mouse.x, mouse.y)
            isSliding = false
            isDragging = false
            visited = {}
            glide.stop()
            if (mouse.button === Qt.LeftButton && pressedIndex >= 0 && !(mouse.modifiers & (Qt.ControlModifier | Qt.ShiftModifier)))
                hold.restart()
        }

        onPositionChanged: mouse => {
            pointerY = mouse.y
            if (isSliding) {
                slideOver(mouse.x, mouse.y)
                return
            }
            if (isDragging || pressedIndex < 0)
                return
            const distance = Math.hypot(mouse.x - pressedAt.x, mouse.y - pressedAt.y)
            if (distance < 6 * Theme.dp)
                return
            // Moved before the hold: the photo is carried out of the window, to the file manager or a chat.
            hold.stop()
            isDragging = true
            const path = gridModel.pathAt(pressedIndex)
            const paths = photoGrid.selection.has(path) ? photoGrid.selection.pickedPhotos() : [path]
            System.startDrag(paths, path)
        }

        onReleased: mouse => {
            hold.stop()
            edgeScroll.stop()
            if (isSliding || isDragging) {
                isSliding = false
                isDragging = false
                return
            }
            const tileIndex = photoGrid.tileAt(mouse.x, mouse.y)
            const index = gridModel.itemOfTile(tileIndex)
            if (index < 0 || index !== pressedIndex)
                return
            const path = gridModel.pathAt(index)
            const tile = gridModel.tile(tileIndex)
            const isFoldedStack = tile.stackSize > 0 && tile.stackPosition === 0
            if (mouse.button === Qt.RightButton) {
                // The desktop's long press: picks the photo, or every shot of a folded stack.
                photoGrid.pick(tile, !photoGrid.selection.has(path))
                photoGrid.selection.anchorIndex = index
                return
            }
            if (mouse.modifiers & Qt.ShiftModifier && photoGrid.selection.anchorIndex >= 0) {
                const from = Math.min(photoGrid.selection.anchorIndex, index)
                const to = Math.max(photoGrid.selection.anchorIndex, index)
                const range = []
                for (let at = from; at <= to; ++at)
                    range.push(gridModel.pathAt(at))
                photoGrid.selection.setPhotos(range, true)
                return
            }
            if (mouse.modifiers & Qt.ControlModifier || photoGrid.selection.isSelectingPhotos) {
                photoGrid.pick(tile, !photoGrid.selection.has(path))
                photoGrid.selection.anchorIndex = index
                return
            }
            // A folded stack lays its shots out in place; the place mark of a shot laid out folds it back.
            if (isFoldedStack) {
                photoGrid.toggleStack(tile.stackKey)
                return
            }
            if (tile.stackPosition > 0 && photoGrid.isOnStackMark(mouse.x, mouse.y, tileIndex)) {
                photoGrid.toggleStack(tile.stackKey)
                return
            }
            photoGrid.opened(index)
        }

        function slideOver(x: real, y: real) {
            const tileIndex = photoGrid.tileAt(x, y)
            if (tileIndex < 0 || visited[tileIndex])
                return
            visited[tileIndex] = true
            // Each photo reached toggles once per stroke, to what the first one became; a folded stack goes in or out as one.
            photoGrid.pick(gridModel.tile(tileIndex), isAdding)
            if (y < 90 * Theme.dp + photoGrid.topInset || y > photoGrid.height - 90 * Theme.dp - photoGrid.bottomInset)
                edgeScroll.start()
        }

        Timer {
            id: hold
            interval: photoGrid.selection.isSelectingPhotos ? Motion.selectHoldActive : Motion.selectHold
            onTriggered: {
                const path = gridModel.pathAt(pointer.pressedIndex)
                pointer.isSliding = true
                pointer.isAdding = !photoGrid.selection.has(path)
                pointer.visited = {}
                pointer.slideOver(pointer.pressedAt.x, pointer.pressedAt.y)
                photoGrid.selection.anchorIndex = pointer.pressedIndex
            }
        }

        // Near the top or bottom edge the grid brings more while the pointer stays.
        Timer {
            id: edgeScroll
            interval: 16
            repeat: true
            onTriggered: {
                const step = 22 * Theme.dp
                if (pointer.pointerY < 90 * Theme.dp + photoGrid.topInset)
                    list.contentY = Math.max(list.originY - list.topMargin, list.contentY - step)
                else if (pointer.pointerY > photoGrid.height - 90 * Theme.dp - photoGrid.bottomInset)
                    list.contentY = Math.min(list.contentHeight + list.originY - list.height + list.bottomMargin, list.contentY + step)
                else
                    stop()
            }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // Above the grid's pointer, so the strip and its labels take the pointer first; it slides off the right edge as a photo opens out of the grid.
    Timeline {
        id: timeline
        grid: photoGrid
        y: photoGrid.topInset
        width: parent.width
        height: parent.height - photoGrid.topInset - photoGrid.bottomInset
        transform: Translate { x: photoGrid.timelineSlide * 96 * Theme.dp }
    }
}
