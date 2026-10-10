import QtQuick
import Gallery

// One cover grid for every screen of albums (Covers.kt, AlbumsScreen.kt, VAS components/18 and 23): Albums, Private's albums and the albums made inside Favorites.
// The same columns everywhere, a list at one column, names that shrink to fit. With grouping on, groups lie above the albums as leaning stacks that peel open into rows of three.
// Covers are dragged into any order; an album dropped onto a group goes into it. Everything is laid out here, by hand, so a card can glide from any place to any other.
Item {
    id: coverShelf

    property var covers: []
    // Where the order and the groups are kept: "albums", "private" or "favorites".
    property string shelfName: "albums"
    property string view: "albums"
    property bool isGrouping: false
    property var places: []
    property string newLabel: "NEW ALBUM"
    required property var selection
    required property var memories
    property bool isActive: true
    property real topInset: 0
    property real bottomInset: 0
    property var header: null
    readonly property alias flickable: flick

    readonly property string columnsKey: isGrouping ? "looseColumns" : "albumColumns"
    readonly property int columns: (Settings.revision >= 0 && Settings.viewValue(view, columnsKey))
    readonly property real sideInset: 16 * Theme.dp
    readonly property real gap: 14 * Theme.dp
    readonly property real rowGap: columns === 1 ? 12 * Theme.dp : 20 * Theme.dp
    readonly property real labelRoom: 44 * Theme.dp
    readonly property real cell: Math.max(40, (width - sideInset * 2 - gap * (columns - 1)) / columns)
    readonly property real listCover: 84 * Theme.dp
    // Opened, a group lays its albums out three to a row, whatever the album columns are.
    readonly property int groupColumns: 3
    readonly property real groupCell: Math.max(40, (width - sideInset * 2 - gap * (groupColumns - 1)) / groupColumns)
    readonly property real stackEdge: Math.min(listCover * 1.3, groupCell * 0.8)
    readonly property real groupGap: 12.4 * Theme.dp
    readonly property real headingHeight: 44 * Theme.dp

    signal opened(var cover)
    signal menuAsked(var cover)
    signal groupMenuAsked(string name)
    signal placeOpened(string key)
    signal newAsked
    signal newGroupAsked

    // #region ── what lies on the shelf ────────────────────────────────────────────────────────
    readonly property var storedStacks: (Settings.revision >= 0 && Settings.stacks(shelfName))
    readonly property var coverOfKey: {
        const map = {}
        for (const cover of covers)
            map[cover.key] = cover
        return map
    }
    // The groups shown, each with its albums that still exist; groups come first, in the arranged order.
    readonly property var groups: {
        if (!isGrouping)
            return []
        const order = Settings.revision >= 0 && Settings.order(shelfName)
        const found = []
        for (const stack of storedStacks) {
            const albums = stack.keys.map(key => coverOfKey[key]).filter(cover => cover !== undefined)
            if (albums.length > 0)
                found.push({ name: stack.name, key: "group:" + stack.name, albums: albums })
        }
        const rank = key => { const at = order.indexOf(key); return at < 0 ? order.length : at }
        found.sort((left, right) => rank(left.key) - rank(right.key))
        return found
    }
    readonly property var groupOfKey: {
        const map = {}
        for (const group of groups)
            for (const album of group.albums)
                map[album.key] = group.name
        return map
    }
    readonly property var looseAlbums: covers.filter(cover => groupOfKey[cover.key] === undefined)
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── open groups ───────────────────────────────────────────────────────────────────
    // Several groups can be open at once; the set is kept above this screen, so it survives opening an album and coming back.
    function isGroupOpen(name: string): bool {
        return (memories["open:" + shelfName] ?? []).indexOf(name) >= 0
    }
    function setGroupOpen(name: string, isOpen: bool) {
        const open = (memories["open:" + shelfName] ?? []).filter(each => each !== name)
        if (isOpen)
            open.push(name)
        memories["open:" + shelfName] = open
    }
    function progressOf(name: string): real {
        for (let index = 0; index < groupRows.count; ++index) {
            const row = groupRows.itemAt(index)
            if (row && row.groupName === name)
                return row.progress
        }
        return 0
    }
    readonly property bool isGroupMoving: {
        for (let index = 0; index < groupRows.count; ++index) {
            const row = groupRows.itemAt(index)
            if (row && row.isMoving)
                return true
        }
        return false
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── layout ────────────────────────────────────────────────────────────────────────
    property real headerHeight: 0
    // Where every group block and every loose album stands, and how tall the shelf is; worked out again when anything it reads changes, a group's opening included.
    readonly property var layout: {
        const result = { groups: {}, albums: {}, height: 0 }
        let y = headerHeight + 8 * Theme.dp
        for (const group of groups) {
            const progress = progressOf(group.name)
            const rows = Math.ceil(group.albums.length / groupColumns)
            const closedHeight = stackEdge + groupGap * 2
            const openHeight = headingHeight + rows * (groupCell + labelRoom) + (rows - 1) * rowGap + groupGap * 2
            const height = closedHeight + (openHeight - closedHeight) * progress
            result.groups[group.name] = { y: y, height: height, closedHeight: closedHeight }
            y += height
        }
        if (groups.length > 0 && looseAlbums.length > 0)
            y += rowGap
        result.dividerY = groups.length > 0 && looseAlbums.length > 0 ? y - rowGap / 2 : -1
        for (let index = 0; index < looseAlbums.length; ++index) {
            const album = looseAlbums[index]
            if (columns === 1) {
                result.albums[album.key] = { x: sideInset, y: y, width: width - sideInset * 2, height: listCover }
                y += listCover + rowGap
            } else {
                const column = index % columns
                if (column === 0 && index > 0)
                    y += cell + labelRoom + rowGap
                result.albums[album.key] = { x: sideInset + column * (cell + gap), y: y, width: cell, height: cell + labelRoom }
            }
        }
        if (columns !== 1 && looseAlbums.length > 0)
            y += cell + labelRoom + rowGap
        result.newY = y
        y += 56 * Theme.dp
        result.placesY = y
        if (places.length > 0)
            y += 32 * Theme.dp + Math.min(cell, 150 * Theme.dp) + 46 * Theme.dp
        result.height = y
        return result
    }

    // Lying in a stack, the cards under the top one fan out to the right, each leaning further, shifted further, a little smaller and darker.
    function stackPlace(depth: int, blockY: real): var {
        const layer = Math.min(depth, 2)
        return { x: sideInset + layer * 9 * Theme.dp, y: blockY + groupGap, edge: stackEdge * (1 - 0.05 * layer), tilt: 7 * layer, shade: 1 - 0.2 * layer }
    }
    // Card `index` of a group: the deeper ones leave a little later and come back a little sooner; opening overshoots slightly.
    function cardProgress(index: int, count: int, time: real, isOpening: bool): real {
        const share = count <= 1 ? 0 : index / (count - 1)
        // The same clock both ways: deeper cards leave later and, as it runs back, come home sooner.
        const start = share * 0.35
        const local = Math.max(0, Math.min(1, (time - start) / 0.65))
        return isOpening ? Motion.backOutAt(local) : Motion.powerThreeInOutAt(local)
    }

    // Columns or width changing lay everything out at once; only a change of order glides.
    property bool isSnapping: true
    onColumnsChanged: snapNow()
    onWidthChanged: snapNow()
    function snapNow() {
        isSnapping = true
        Qt.callLater(() => coverShelf.isSnapping = false)
    }
    Component.onCompleted: snapNow()
    onCoversChanged: snapNow()
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── rearranging ───────────────────────────────────────────────────────────────────
    property string heldKey: ""
    property point heldAt: Qt.point(0, 0)
    property string hoveredGroup: ""
    property bool isHoverReady: false
    // The order while a card is carried; kept only on release.
    property var draftOrder: []

    function arrangedKeys(): var {
        return groups.map(group => group.key).concat(looseAlbums.map(album => shelfName === "albums" ? album.folder : album.name))
    }
    function keyOfCover(cover: var): string {
        return shelfName === "albums" ? cover.folder : cover.name
    }

    function startCarry(key: string, at: point) {
        if (!selection.isRearranging)
            selection.isRearranging = true
        heldKey = key
        heldAt = at
    }

    // The slot under the pointer among the loose albums; albums trade places with albums only, never above a group.
    function moveCarried(at: point) {
        heldAt = at
        const contentAt = Qt.point(at.x, at.y + flick.contentY)
        // Over a shut group, the album gets ready to go in once it has hung there a moment.
        let over = ""
        for (const group of groups) {
            const block = layout.groups[group.name]
            if (contentAt.y >= block.y && contentAt.y < block.y + block.height && groupOfKey[heldKey] !== group.name)
                over = group.name
        }
        if (over !== hoveredGroup) {
            hoveredGroup = over
            isHoverReady = false
            if (over.length > 0)
                hoverTimer.restart()
        }
        if (over.length > 0 || groupOfKey[heldKey] !== undefined)
            return
        let nearest = -1
        let nearestDistance = Infinity
        for (let index = 0; index < looseAlbums.length; ++index) {
            const place = layout.albums[looseAlbums[index].key]
            const distance = Math.hypot(place.x + place.width / 2 - contentAt.x, place.y + place.height / 2 - contentAt.y)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearest = index
            }
        }
        const from = looseAlbums.findIndex(album => album.key === heldKey)
        if (nearest < 0 || from < 0 || nearest === from)
            return
        const reordered = looseAlbums.slice()
        const moved = reordered.splice(from, 1)[0]
        reordered.splice(nearest, 0, moved)
        const keys = groups.map(group => group.key).concat(reordered.map(album => keyOfCover(album)))
        Settings.setOrder(shelfName, keys)
    }

    Timer {
        id: hoverTimer
        interval: 350
        onTriggered: coverShelf.isHoverReady = true
    }

    function dropCarried() {
        const key = heldKey
        const group = hoveredGroup
        const wasIn = groupOfKey[key]
        heldKey = ""
        hoveredGroup = ""
        if (group.length > 0 && isHoverReady)
            moveIntoGroup([key], group)
        else if (wasIn !== undefined && !isOverOwnGroup(wasIn))
            removeFromGroups([key])
        isHoverReady = false
    }

    function isOverOwnGroup(name: string): bool {
        const block = layout.groups[name]
        const y = heldAt.y + flick.contentY
        return block !== undefined && y >= block.y && y < block.y + block.height
    }

    function moveIntoGroup(keys: var, name: string) {
        const stacks = storedStacks.map(stack => ({ name: stack.name, keys: stack.keys.filter(key => keys.indexOf(key) < 0) }))
        const target = stacks.find(stack => stack.name === name)
        if (target)
            target.keys = target.keys.concat(keys)
        else
            stacks.push({ name: name, keys: keys })
        Settings.setStacks(shelfName, stacks)
    }

    function removeFromGroups(keys: var) {
        Settings.setStacks(shelfName, storedStacks.map(stack => ({ name: stack.name, keys: stack.keys.filter(key => keys.indexOf(key) < 0) })))
    }

    function ungroup(name: string) {
        Settings.setStacks(shelfName, storedStacks.filter(stack => stack.name !== name))
    }

    function renameGroup(from: string, to: string) {
        Settings.setStacks(shelfName, storedStacks.map(stack => stack.name === from ? { name: to, keys: stack.keys } : stack))
        Settings.setOrder(shelfName, (Settings.order(shelfName)).map(key => key === "group:" + from ? "group:" + to : key))
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    function stepColumns(step: int) {
        Settings.setViewValue(view, columnsKey, Math.max(1, Math.min(isGrouping ? 3 : 4, columns + step)))
    }

    function glideToTop() {
        glide.from = flick.contentY
        glide.to = -flick.topMargin
        glide.restart()
    }
    NumberAnimation { id: glide; target: flick; property: "contentY"; duration: Motion.scrollToEnd; easing.type: Motion.powerThreeInOut }

    property double entranceStartedAt: Date.now()
    property int entranceRank: 0
    onIsActiveChanged: if (isActive) { entranceStartedAt = Date.now(); entranceRank = 0 }
    function nextEntranceRank(): int {
        if (Date.now() - entranceStartedAt > Motion.entranceWindow || Motion.isReduced)
            return -1
        return Math.min(16, entranceRank++)
    }

    Flickable {
        id: flick
        anchors.fill: parent
        topMargin: coverShelf.topInset
        bottomMargin: coverShelf.bottomInset
        contentWidth: width
        contentHeight: coverShelf.layout.height
        boundsBehavior: Flickable.StopAtBounds
        interactive: false

        // A click on the shelf between covers ends rearranging; a hold there starts it.
        MouseArea {
            width: flick.width
            height: Math.max(flick.height, coverShelf.layout.height)
            acceptedButtons: Qt.LeftButton | Qt.RightButton
            pressAndHoldInterval: Motion.coverHold
            onClicked: if (coverShelf.selection.isRearranging) coverShelf.selection.isRearranging = false
            onPressAndHold: coverShelf.selection.isRearranging = true
        }

        WheelHandler {
            acceptedModifiers: Qt.NoModifier
            onWheel: event => {
                const delta = event.pixelDelta.y !== 0 ? event.pixelDelta.y : event.angleDelta.y / 120 * coverShelf.cell * 0.6
                flick.contentY = Math.max(-flick.topMargin, Math.min(Math.max(-flick.topMargin, flick.contentHeight - flick.height + flick.bottomMargin), flick.contentY - delta))
            }
        }

        Loader {
            width: flick.width
            active: coverShelf.header !== null
            sourceComponent: coverShelf.header
            onHeightChanged: coverShelf.headerHeight = height
        }

        // A short hairline, centred, between the groups and the albums, so the two kinds read apart.
        Rectangle {
            visible: coverShelf.layout.dividerY >= 0
            x: (flick.width - width) / 2
            y: coverShelf.layout.dividerY
            width: flick.width * 0.35
            height: 1
            color: Theme.border
        }

        // #region ── groups ────────────────────────────────────────────────────────────────────
        Repeater {
            id: groupRows
            model: coverShelf.groups

            GroupRow {
                required property var modelData
                shelf: coverShelf
                group: modelData
            }
        }
        // #endregion ───────────────────────────────────────────────────────────────────────────

        // #region ── the albums ────────────────────────────────────────────────────────────────
        Repeater {
            model: coverShelf.covers

            CoverCard {
                id: card
                required property var modelData
                required property int index
                cover: modelData
                grid: coverShelf
                readonly property string groupName: coverShelf.groupOfKey[modelData.key] ?? ""
                readonly property var group: groupName.length > 0 ? coverShelf.groups.find(each => each.name === groupName) : null
                readonly property int depth: group ? group.albums.findIndex(album => album.key === modelData.key) : 0
                readonly property var block: groupName.length > 0 ? coverShelf.layout.groups[groupName] : null
                readonly property var groupRow: groupName.length > 0 ? groupRows.itemAt(coverShelf.groups.findIndex(each => each.name === groupName)) : null
                readonly property real openShare: groupRow && group ? coverShelf.cardProgress(depth, group.albums.length, groupRow.time, groupRow.isOpening) : 0
                readonly property var lying: block ? coverShelf.stackPlace(depth, block.y) : null
                readonly property var slot: block ? {
                    x: coverShelf.sideInset + (depth % coverShelf.groupColumns) * (coverShelf.groupCell + coverShelf.gap),
                    y: block.y + coverShelf.groupGap + coverShelf.headingHeight + Math.floor(depth / coverShelf.groupColumns) * (coverShelf.groupCell + coverShelf.labelRoom + coverShelf.rowGap)
                } : null
                readonly property var loose: coverShelf.layout.albums[modelData.key] ?? null
                readonly property bool isHeld: coverShelf.heldKey === modelData.key
                isGrouped: block !== null
                groupShare: openShare
                lyingEdge: lying ? lying.edge : 0
                groupEdge: coverShelf.groupCell
                lyingShade: lying ? lying.shade : 1
                lyingTilt: lying ? lying.tilt : 0
                // Past the stack's visible layers, a card is hidden until it leaves.
                visible: block === null || depth < 3 || openShare > 0.01
                z: isHeld ? 100 : block ? 10 - depth : 1
                readonly property real restX: block ? lying.x + (slot.x - lying.x) * openShare : (loose ? loose.x : 0)
                readonly property real restY: block ? lying.y + (slot.y - lying.y) * openShare : (loose ? loose.y : 0)
                x: isHeld ? coverShelf.heldAt.x - width / 2 : restX
                y: isHeld ? coverShelf.heldAt.y + flick.contentY - height / 2 : restY
                Behavior on x { enabled: !coverShelf.isSnapping && !coverShelf.isGroupMoving && !card.isHeld; NumberAnimation { duration: Motion.stateChange; easing.type: Motion.powerThreeOut } }
                Behavior on y { enabled: !coverShelf.isSnapping && !coverShelf.isGroupMoving && !card.isHeld; NumberAnimation { duration: Motion.stateChange; easing.type: Motion.powerThreeOut } }
            }
        }
        // #endregion ───────────────────────────────────────────────────────────────────────────

        // New group and New album share one row, group on the left; with groups off, New album stands alone and centred.
        Row {
            x: (flick.width - width) / 2
            y: coverShelf.layout.newY
            spacing: 28 * Theme.dp

            Pressable {
                visible: coverShelf.isGrouping
                width: newGroupText.implicitWidth + 40 * Theme.dp
                height: 40 * Theme.dp
                onClicked: coverShelf.newGroupAsked()
                Row {
                    anchors.centerIn: parent
                    spacing: 8 * Theme.dp
                    Glyph { anchors.verticalCenter: parent.verticalCenter; width: 16 * Theme.dp; height: width; name: "plus"; ink: Theme.accent }
                    Text { id: newGroupText; anchors.verticalCenter: parent.verticalCenter; text: "NEW GROUP"; color: Theme.accent; font.family: Theme.mono; font.pixelSize: Theme.labelSize; font.letterSpacing: Theme.labelSpacing }
                }
            }
            Pressable {
                width: newAlbumText.implicitWidth + 40 * Theme.dp
                height: 40 * Theme.dp
                onClicked: coverShelf.newAsked()
                Row {
                    anchors.centerIn: parent
                    spacing: 8 * Theme.dp
                    Glyph { anchors.verticalCenter: parent.verticalCenter; width: 16 * Theme.dp; height: width; name: "plus"; ink: Theme.accent }
                    Text { id: newAlbumText; anchors.verticalCenter: parent.verticalCenter; text: coverShelf.newLabel; color: Theme.accent; font.family: Theme.mono; font.pixelSize: Theme.labelSize; font.letterSpacing: Theme.labelSpacing }
                }
            }
        }

        // The places, set apart from the albums by a hairline with a wider gap: Private and the trash, each as big as an album cover.
        Rectangle {
            visible: coverShelf.places.length > 0
            x: coverShelf.sideInset
            y: coverShelf.layout.placesY + 8 * Theme.dp
            width: flick.width - coverShelf.sideInset * 2
            height: 1
            color: Theme.border
        }

        Row {
            x: (flick.width - width) / 2
            y: coverShelf.layout.placesY + 32 * Theme.dp
            spacing: coverShelf.gap
            visible: coverShelf.places.length > 0

            Repeater {
                model: coverShelf.places

                Pressable {
                    id: place
                    required property var modelData
                    readonly property real edge: Math.min(coverShelf.cell, 150 * Theme.dp)
                    width: edge
                    height: edge + 40 * Theme.dp
                    onClicked: coverShelf.placeOpened(modelData.key)

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

    WheelHandler {
        acceptedModifiers: Qt.ControlModifier
        property real collected: 0
        onWheel: event => {
            collected += event.angleDelta.y
            if (Math.abs(collected) < 120)
                return
            coverShelf.stepColumns(collected > 0 ? -1 : 1)
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
                coverShelf.stepColumns(-1)
                stepScale = 1
            } else if (stepScale < 1 / 1.28) {
                coverShelf.stepColumns(1)
                stepScale = 1
            }
        }
    }
}
