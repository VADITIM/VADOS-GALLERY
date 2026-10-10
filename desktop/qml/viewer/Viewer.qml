import QtQuick
import Gallery

// The photo and video viewer (ViewerScreen.kt, VAS components/16): it grows out of the tile it was opened from and shrinks into the tile of the photo it ends on,
// on black. One progress drives the flight; a pull down is the same animation driven by the finger, and a swipe up raises the details. Everything here follows the finger,
// and letting go only decides (VAS dna/05 §14).
Item {
    id: viewer

    required property var gallery
    property var grid: null
    property string source: ""
    property int index: 0
    property string currentPath: ""
    property bool isRequested: false
    // Opened from another app: closing it hides the window again when nothing else was open.
    property bool isExternal: false
    property real progress: 0
    property real pull: 0
    property real lift: 0
    property real swipe: 0
    property bool isClosing: false
    property bool isChromeShown: true
    property bool isDetailsOpen: false
    property rect tileRect: Qt.rect(0, 0, 0, 0)
    property bool hasTile: false
    // Pages swipe with a gap between them.
    readonly property real pageGap: 24 * Theme.dp

    readonly property bool isOpen: isRequested || progress > 0
    readonly property real growth: progress * (1 - pull)
    readonly property bool isSettled: progress >= 1 && pull === 0 && !isClosing
    readonly property bool isChromeVisible: isChromeShown && progress >= 0.85 && !isClosing && pull === 0
    readonly property var facts: (items.revision, items.item(index))
    readonly property bool isTrash: source === "trash" || source === "private-trash"
    readonly property bool isPrivate: source.startsWith("private")
    readonly property var page: currentPage

    signal closed

    MediaGridModel {
        id: items
        source: viewer.source
        columns: 1
        hasHeaders: false
        isFavoritesOnly: viewer.grid ? viewer.grid.model.isFavoritesOnly : false
        // A photo moved, trashed or favourited while open is followed: the one after a deleted photo takes its place, and an emptied viewer closes.
        onRebuilt: {
            if (!viewer.isRequested)
                return
            if (count === 0) {
                viewer.close()
                return
            }
            const at = indexOfPath(viewer.currentPath)
            viewer.index = at >= 0 ? at : Math.min(viewer.index, count - 1)
            viewer.currentPath = pathAt(viewer.index)
        }
    }
    readonly property alias items: items
    readonly property alias video: video

    // #region ── opening and closing ───────────────────────────────────────────────────────────
    function open(fromSource: string, at: int, fromGrid: var, external: bool) {
        settle.stop()
        flight.stop()
        source = fromSource
        grid = fromGrid
        isExternal = external
        index = at
        currentPath = items.pathAt(at)
        pull = 0
        lift = 0
        swipe = 0
        isDetailsOpen = false
        isChromeShown = true
        isClosing = false
        currentPage.zoom = 1
        currentPage.panX = 0
        currentPage.panY = 0
        aimAtTile(false)
        isRequested = true
        flight.from = 0
        flight.to = 1
        flight.duration = Motion.viewerEnter
        flight.easing.type = Motion.powerTwoOut
        flight.restart()
    }

    // Closing aims at the tile of the photo on screen now, scrolling the grid to it first; a quiet fade when there is none.
    function close() {
        if (!isRequested || isClosing)
            return
        isClosing = true
        isDetailsOpen = false
        lift = 0
        aimAtTile(true)
        const fromPull = pull > 0
        progress = growth
        pull = 0
        flight.from = progress
        flight.to = 0
        flight.duration = Motion.viewerClose * Math.max(0.4, progress)
        // After a pull it is already moving at the finger's speed; easing in again would stall it.
        flight.easing.type = fromPull ? Motion.powerTwoOut : Motion.powerThreeInOut
        flight.restart()
    }

    function aimAtTile(isScrolling: bool) {
        const rect = grid ? grid.tileRect(index, isScrolling) : null
        hasTile = rect !== null && rect !== undefined
        if (hasTile)
            tileRect = viewer.mapFromItem(null, rect.x, rect.y, rect.width, rect.height)
    }

    NumberAnimation {
        id: flight
        target: viewer
        property: "progress"
        onFinished: {
            if (viewer.progress > 0)
                return
            viewer.isRequested = false
            viewer.isClosing = false
            viewer.closed()
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── geometry ──────────────────────────────────────────────────────────────────────
    function aspectOf(facts: var, page: var): real {
        if (facts && facts.width > 0 && facts.height > 0)
            return facts.width / facts.height
        if (page && page.naturalSize.width > 0)
            return page.naturalSize.width / page.naturalSize.height
        return 1
    }

    function fitRect(aspect: real): rect {
        const scale = Math.min(width / aspect, height)
        const fittedHeight = scale
        const fittedWidth = scale * aspect
        return Qt.rect((width - fittedWidth) / 2, (height - fittedHeight) / 2, fittedWidth, fittedHeight)
    }

    readonly property rect currentFit: fitRect(aspectOf(facts, currentPage))
    // The frame moves from the tile's square to the photo's own box as one move and one scale.
    readonly property rect flightRect: {
        const fit = currentFit
        if (!hasTile) {
            const shrink = 0.94 + 0.06 * growth
            return Qt.rect(fit.x + fit.width * (1 - shrink) / 2, fit.y + fit.height * (1 - shrink) / 2, fit.width * shrink, fit.height * shrink)
        }
        const g = growth
        return Qt.rect(tileRect.x + (fit.x - tileRect.x) * g, tileRect.y + (fit.y - tileRect.y) * g,
                       tileRect.width + (fit.width - tileRect.width) * g, tileRect.height + (fit.height - tileRect.height) * g)
    }
    // The tile's corner at the start and a larger one nearly open, gone at the end as the photo fills its box.
    readonly property real flightCorner: {
        const g = growth
        const corner = Theme.tileRadius + (24 * Theme.dp - Theme.tileRadius) * g
        return g > 0.7 ? corner * (1 - g) / 0.3 : corner
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── layers ────────────────────────────────────────────────────────────────────────
    visible: isOpen

    Rectangle {
        anchors.fill: parent
        color: Theme.viewerGround
        opacity: viewer.hasTile ? viewer.growth : Math.min(1, viewer.growth * 1.2)
    }

    ViewerPage {
        id: previousPage
        readonly property var facts: (items.revision, items.item(viewer.index - 1))
        readonly property rect fit: viewer.fitRect(viewer.aspectOf(facts, previousPage))
        visible: viewer.isSettled && viewer.index > 0
        isNear: viewer.isSettled
        path: viewer.index > 0 ? items.pathAt(viewer.index - 1) : ""
        isVideo: facts.isVideo === true
        x: fit.x - viewer.width - viewer.pageGap + viewer.swipe
        y: fit.y
        width: fit.width
        height: fit.height
    }

    ViewerPage {
        id: nextPage
        readonly property var facts: (items.revision, items.item(viewer.index + 1))
        readonly property rect fit: viewer.fitRect(viewer.aspectOf(facts, nextPage))
        visible: viewer.isSettled && viewer.index < items.count - 1
        isNear: viewer.isSettled
        path: viewer.index < items.count - 1 ? items.pathAt(viewer.index + 1) : ""
        isVideo: facts.isVideo === true
        x: fit.x + viewer.width + viewer.pageGap + viewer.swipe
        y: fit.y
        width: fit.width
        height: fit.height
    }

    ViewerPage {
        id: currentPage
        path: viewer.currentPath
        isVideo: viewer.facts.isVideo === true
        x: viewer.flightRect.x + (viewer.isSettled ? viewer.swipe : 0)
        // The details lift the photo a little out of their way.
        y: viewer.flightRect.y - viewer.lift * viewer.height * 0.18
        width: viewer.flightRect.width
        height: viewer.flightRect.height
        cornerRadius: viewer.flightCorner
        opacity: viewer.hasTile ? 1 : Math.min(1, viewer.growth * 1.4)
    }

    Loader {
        id: video
        x: currentPage.x
        y: currentPage.y
        width: currentPage.width
        height: currentPage.height
        active: viewer.isSettled && viewer.facts.isVideo === true && System.hasVideoPlayback
        source: active ? "VideoPlayer.qml" : ""
        onLoaded: {
            item.path = Qt.binding(() => viewer.currentPath)
            item.viewer = viewer
        }
    }

    // Without Qt Multimedia a video opens in the system's player.
    Pressable {
        anchors.centerIn: currentPage
        visible: viewer.isSettled && viewer.facts.isVideo === true && !System.hasVideoPlayback
        width: 72 * Theme.dp
        height: width
        onClicked: System.openExternally(viewer.currentPath)
        Glass { anchors.fill: parent }
        Glyph {
            anchors.centerIn: parent
            width: 28 * Theme.dp
            height: width
            name: "play"
            ink: Theme.textBright
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── paging ────────────────────────────────────────────────────────────────────────
    function goTo(next: int) {
        if (next < 0 || next >= items.count || next === index)
            return
        currentPage.zoom = 1
        currentPage.panX = 0
        currentPage.panY = 0
        index = next
        currentPath = items.pathAt(next)
    }

    // Steps a page with the same slide a swipe finishes on.
    function step(direction: int) {
        if (!isSettled || index + direction < 0 || index + direction >= items.count)
            return
        settle.stop()
        pageTurn.direction = direction
        pageTurn.from = swipe
        pageTurn.to = -direction * (width + pageGap)
        pageTurn.restart()
    }

    NumberAnimation {
        id: pageTurn
        property int direction: 0
        target: viewer
        property: "swipe"
        duration: Motion.overlayEnter
        easing.type: Motion.powerTwoOut
        onFinished: {
            viewer.swipe = 0
            viewer.goTo(viewer.index + direction)
        }
    }

    NumberAnimation {
        id: settle
        target: viewer
        properties: "swipe"
        to: 0
        duration: Motion.stateChange
        easing.type: Motion.backOut
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── zoom ──────────────────────────────────────────────────────────────────────────
    // Zooms about a point, keeping what is under it still; back at fit the photo recentres.
    function zoomAt(factor: real, x: real, y: real) {
        const page = currentPage
        const next = Math.max(1, Math.min(8, page.zoom * factor))
        const centreX = page.x + page.width / 2 + page.panX
        const centreY = page.y + page.height / 2 + page.panY
        const ratio = next / page.zoom
        page.panX += (x - centreX) * (1 - ratio)
        page.panY += (y - centreY) * (1 - ratio)
        page.zoom = next
        if (next <= 1.001) {
            page.panX = 0
            page.panY = 0
        }
        clampPan()
    }

    function clampPan() {
        const page = currentPage
        const spareX = Math.max(0, (page.width * page.zoom - width) / 2)
        const spareY = Math.max(0, (page.height * page.zoom - height) / 2)
        page.panX = Math.max(-spareX, Math.min(spareX, page.panX))
        page.panY = Math.max(-spareY, Math.min(spareY, page.panY))
    }

    ParallelAnimation {
        id: zoomRun
        property real toZoom: 1
        property real toX: 0
        property real toY: 0
        NumberAnimation { target: currentPage; property: "zoom"; to: zoomRun.toZoom; duration: Motion.stateChange; easing.type: Motion.powerTwoOut }
        NumberAnimation { target: currentPage; property: "panX"; to: zoomRun.toX; duration: Motion.stateChange; easing.type: Motion.powerTwoOut }
        NumberAnimation { target: currentPage; property: "panY"; to: zoomRun.toY; duration: Motion.stateChange; easing.type: Motion.powerTwoOut }
    }

    // A double click goes to 2.5× landing on the clicked point; a zoomed photo always returns to its own size.
    function toggleZoom(x: real, y: real) {
        if (currentPage.isZoomed) {
            zoomRun.toZoom = 1
            zoomRun.toX = 0
            zoomRun.toY = 0
        } else {
            const target = 2.5
            const centreX = currentPage.x + currentPage.width / 2
            const centreY = currentPage.y + currentPage.height / 2
            const spareX = Math.max(0, (currentPage.width * target - width) / 2)
            const spareY = Math.max(0, (currentPage.height * target - height) / 2)
            zoomRun.toZoom = target
            zoomRun.toX = Math.max(-spareX, Math.min(spareX, (centreX - x) * (target - 1)))
            zoomRun.toY = Math.max(-spareY, Math.min(spareY, (centreY - y) * (target - 1)))
        }
        zoomRun.restart()
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── the finger ────────────────────────────────────────────────────────────────────
    NumberAnimation {
        id: pullBack
        target: viewer
        property: "pull"
        to: 0
        duration: Motion.stateChange
        easing.type: Motion.backOut
    }

    NumberAnimation {
        id: liftRun
        target: viewer
        property: "lift"
        duration: Motion.overlayEnter
        easing.type: Motion.powerTwoOut
        onFinished: viewer.isDetailsOpen = viewer.lift >= 1
    }

    function openDetails(isOpen: bool) {
        liftRun.from = lift
        liftRun.to = isOpen ? 1 : 0
        liftRun.restart()
    }

    MouseArea {
        id: pointer
        anchors.fill: parent
        enabled: viewer.isOpen && !viewer.isClosing
        acceptedButtons: Qt.LeftButton
        property point pressedAt
        property point lastAt
        property string mode: ""
        property double lastMoveTime: 0
        property real velocityX: 0
        property real startLift: 0

        onPressed: mouse => {
            pressedAt = Qt.point(mouse.x, mouse.y)
            lastAt = pressedAt
            mode = ""
            velocityX = 0
            lastMoveTime = Date.now()
            settle.stop()
            pageTurn.stop()
            startLift = viewer.lift
        }

        onPositionChanged: mouse => {
            const dx = mouse.x - pressedAt.x
            const dy = mouse.y - pressedAt.y
            const moveX = mouse.x - lastAt.x
            const moveY = mouse.y - lastAt.y
            const now = Date.now()
            velocityX = moveX / Math.max(1, now - lastMoveTime) * 1000
            lastMoveTime = now
            lastAt = Qt.point(mouse.x, mouse.y)
            if (mode === "") {
                if (Math.hypot(dx, dy) < 8 * Theme.dp)
                    return
                if (currentPage.isZoomed)
                    mode = "pan"
                else if (viewer.isDetailsOpen || viewer.lift > 0)
                    mode = "lift"
                else if (Math.abs(dx) > Math.abs(dy))
                    mode = "page"
                else
                    mode = dy > 0 ? "pull" : "lift"
                if (mode === "pull")
                    // A pull aims at the tile as soon as it starts, so the shrink heads for the right place.
                    viewer.aimAtTile(true)
            }
            switch (mode) {
            case "pan":
                currentPage.panX += moveX
                currentPage.panY += moveY
                viewer.clampPan()
                break
            case "page":
                // At either end the page only gives a little.
                const isEdge = (dx > 0 && viewer.index === 0) || (dx < 0 && viewer.index === items.count - 1)
                viewer.swipe = isEdge ? dx * 0.25 : dx
                break
            case "pull":
                viewer.pull = Math.max(0, Math.min(1, dy / (viewer.height * 0.4)))
                break
            case "lift":
                viewer.lift = Math.max(0, Math.min(1, startLift - dy / (viewer.height * 0.3)))
                break
            }
        }

        onReleased: mouse => {
            const dx = mouse.x - pressedAt.x
            const dy = mouse.y - pressedAt.y
            switch (mode) {
            case "":
                clickTimer.at = Qt.point(mouse.x, mouse.y)
                if (clickTimer.running) {
                    clickTimer.stop()
                    viewer.toggleZoom(mouse.x, mouse.y)
                } else {
                    clickTimer.restart()
                }
                break
            case "page":
                const isFar = Math.abs(dx) > viewer.width * 0.2 || Math.abs(velocityX) > 800
                const direction = dx < 0 ? 1 : -1
                if (isFar && viewer.index + direction >= 0 && viewer.index + direction < items.count) {
                    pageTurn.direction = direction
                    pageTurn.from = viewer.swipe
                    pageTurn.to = -direction * (viewer.width + viewer.pageGap)
                    pageTurn.restart()
                } else {
                    settle.from = viewer.swipe
                    settle.restart()
                }
                break
            case "pull":
                // Past the point the close carries on from where the finger left it; short of it everything goes back.
                if (dy > 100 * Theme.dp)
                    viewer.close()
                else {
                    pullBack.from = viewer.pull
                    pullBack.restart()
                }
                break
            case "lift":
                viewer.openDetails(viewer.lift > (viewer.isDetailsOpen ? 0.7 : 0.3) || (!viewer.isDetailsOpen && -dy > 100 * Theme.dp))
                break
            }
            mode = ""
        }

        Timer {
            id: clickTimer
            property point at
            interval: 220
            // A single click shows or hides the buttons; with the details up it lowers them.
            onTriggered: {
                if (viewer.lift > 0)
                    viewer.openDetails(false)
                else
                    viewer.isChromeShown = !viewer.isChromeShown
            }
        }

        onWheel: wheel => {
            if (!viewer.isSettled)
                return
            // Sideways on a touchpad turns the page with the fingers; up and down zooms about the pointer.
            if (Math.abs(wheel.angleDelta.x) > Math.abs(wheel.angleDelta.y) && !currentPage.isZoomed) {
                viewer.swipe += wheel.pixelDelta.x !== 0 ? wheel.pixelDelta.x : wheel.angleDelta.x / 2
                wheelSettle.restart()
                return
            }
            viewer.zoomAt(Math.pow(1.0015, wheel.angleDelta.y), wheel.x, wheel.y)
        }

        Timer {
            id: wheelSettle
            interval: 140
            onTriggered: {
                const direction = viewer.swipe < 0 ? 1 : -1
                if (Math.abs(viewer.swipe) > viewer.width * 0.15 && viewer.index + direction >= 0 && viewer.index + direction < items.count) {
                    pageTurn.direction = direction
                    pageTurn.from = viewer.swipe
                    pageTurn.to = -direction * (viewer.width + viewer.pageGap)
                    pageTurn.restart()
                } else {
                    settle.from = viewer.swipe
                    settle.restart()
                }
            }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────
}
