import QtQuick
import Gallery

// Crop and draw (CropScreen.kt), in its own accent, violet. The picture travels from where the viewer showed it into the crop frame and the controls come in from the edges;
// the wheel zooms the picture under the frame, which stays put; the frame's edges and corners resize it; a fixed ratio holds while they do.
// Every edit is one undo step once the pointer rests. What is saved is what the frame covers, as a copy beside the original.
Item {
    id: root

    required property var gallery
    property bool isOpen: false
    property string path: ""
    property var viewer: null
    property real arrival: 0
    property rect startRect: Qt.rect(0, 0, 0, 0)
    property string mode: "crop"
    property int turns: 0
    property string ratio: "Free"
    property real naturalAspect: 1
    property rect picture: Qt.rect(0, 0, 0, 0)
    property rect frame: Qt.rect(0, 0, 0, 0)
    property var strokes: []
    property var undoStack: []
    property var redoStack: []
    property color pencilColor: "#ffffff"
    property real pencilThickness: 0.3
    readonly property color violet: Theme.cropViolet
    readonly property var ratios: ["Free", "Original", "1:1", "4:5", "4:3", "16:9", "9:16"]
    readonly property var pencilColors: ["#ffffff", "#000000", Theme.privateRed, Theme.amber, Theme.terminalGreen, Theme.locationBlue, Theme.hotPink, Theme.cropViolet]
    readonly property rect work: Qt.rect(48 * Theme.dp, 72 * Theme.dp, width - 96 * Theme.dp, height - 72 * Theme.dp - 120 * Theme.dp)
    readonly property real turnedAspect: turns % 2 === 0 ? naturalAspect : 1 / naturalAspect

    visible: arrival > 0
    z: 80

    // #region ── opening and closing ───────────────────────────────────────────────────────────
    function open(fromPath: string, fromViewer: var) {
        path = fromPath
        viewer = fromViewer
        const facts = fromViewer.facts
        naturalAspect = facts.width > 0 && facts.height > 0 ? facts.width / facts.height : 1
        turns = 0
        ratio = "Free"
        mode = "crop"
        strokes = []
        undoStack = []
        redoStack = []
        const shown = fromViewer.currentFit
        startRect = fromViewer.mapToItem(root, shown.x, shown.y, shown.width, shown.height)
        resetGeometry()
        isOpen = true
        leaving.stop()
        arriving.restart()
        keys.forceActiveFocus()
    }

    function close() {
        if (!isOpen)
            return
        FocusHome.restore()
        isOpen = false
        arriving.stop()
        leaving.restart()
    }

    NumberAnimation { id: arriving; target: root; property: "arrival"; from: 0; to: 1; duration: Motion.viewerEnter; easing.type: Motion.powerTwoOut }
    NumberAnimation { id: leaving; target: root; property: "arrival"; to: 0; duration: Motion.viewerClose; easing.type: Motion.powerThreeInOut }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── geometry ──────────────────────────────────────────────────────────────────────
    function fitInWork(aspect: real): rect {
        const scale = Math.min(work.width / aspect, work.height)
        return Qt.rect(work.x + (work.width - scale * aspect) / 2, work.y + (work.height - scale) / 2, scale * aspect, scale)
    }

    function resetGeometry() {
        picture = fitInWork(turnedAspect)
        frame = picture
    }

    // The frame as shares of the turned picture, which is what is saved and what an undo step keeps.
    function frameShares(): var {
        return { x: (frame.x - picture.x) / picture.width, y: (frame.y - picture.y) / picture.height, w: frame.width / picture.width, h: frame.height / picture.height }
    }

    function setFrameShares(shares: var) {
        picture = fitInWork(turnedAspect)
        frame = Qt.rect(picture.x + shares.x * picture.width, picture.y + shares.y * picture.height, shares.w * picture.width, shares.h * picture.height)
    }

    function ratioValue(name: string): real {
        switch (name) {
        case "Original": return turnedAspect
        case "1:1": return 1
        case "4:5": return 4 / 5
        case "4:3": return 4 / 3
        case "16:9": return 16 / 9
        case "9:16": return 9 / 16
        default: return 0
        }
    }

    // The largest frame of the ratio that fits the picture shown, centred on the frame it replaces.
    function applyRatio(name: string) {
        ratio = name
        const wanted = ratioValue(name)
        if (wanted <= 0) {
            remember()
            return
        }
        const bounds = visiblePicture()
        let width = bounds.width
        let height = width / wanted
        if (height > bounds.height) {
            height = bounds.height
            width = height * wanted
        }
        const centreX = Math.max(bounds.x + width / 2, Math.min(bounds.x + bounds.width - width / 2, frame.x + frame.width / 2))
        const centreY = Math.max(bounds.y + height / 2, Math.min(bounds.y + bounds.height - height / 2, frame.y + frame.height / 2))
        frame = Qt.rect(centreX - width / 2, centreY - height / 2, width, height)
        remember()
    }

    // What of the picture is on screen and can hold the frame.
    function visiblePicture(): rect {
        const left = Math.max(picture.x, work.x)
        const top = Math.max(picture.y, work.y)
        const right = Math.min(picture.x + picture.width, work.x + work.width)
        const bottom = Math.min(picture.y + picture.height, work.y + work.height)
        return Qt.rect(left, top, Math.max(1, right - left), Math.max(1, bottom - top))
    }

    // A quarter turn clockwise: the picture swings round, and the frame turns with it.
    function rotate() {
        const shares = frameShares()
        turns = (turns + 1) % 4
        const turnedStrokes = strokes.map(stroke => Object.assign({}, stroke, { points: stroke.points.map(point => [1 - point[1], point[0]]), width: stroke.width * (turns % 2 === 1 ? naturalAspect : 1 / naturalAspect) }))
        strokes = turnedStrokes
        setFrameShares({ x: 1 - shares.y - shares.h, y: shares.x, w: shares.h, h: shares.w })
        if (ratio !== "Free" && ratio !== "Original" && ratio !== "1:1")
            ratio = ({ "4:5": "4:5", "4:3": "4:3", "16:9": "9:16", "9:16": "16:9" })[ratio] ?? ratio
        swing.from = -90
        swing.restart()
        remember()
    }
    property real swingAngle: 0
    NumberAnimation { id: swing; target: root; property: "swingAngle"; to: 0; duration: Motion.overlayEnter; easing.type: Motion.powerTwoOut }

    // Zooms the picture about a point, keeping the frame where it is and inside the picture.
    function zoomAt(factor: real, x: real, y: real) {
        const fit = fitInWork(turnedAspect)
        let width = Math.max(fit.width, Math.min(fit.width * 8, picture.width * factor))
        const applied = width / picture.width
        let next = Qt.rect(x - (x - picture.x) * applied, y - (y - picture.y) * applied, picture.width * applied, picture.height * applied)
        picture = clampPicture(next)
        settle.restart()
    }

    // The picture always covers the frame.
    function clampPicture(next: rect): rect {
        let x = Math.min(frame.x, Math.max(frame.x + frame.width - next.width, next.x))
        let y = Math.min(frame.y, Math.max(frame.y + frame.height - next.height, next.y))
        return Qt.rect(x, y, next.width, next.height)
    }

    function pan(dx: real, dy: real) {
        picture = clampPicture(Qt.rect(picture.x + dx, picture.y + dy, picture.width, picture.height))
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── undo ──────────────────────────────────────────────────────────────────────────
    function snapshot(): var {
        return { turns: turns, ratio: ratio, frame: frameShares(), strokes: strokes }
    }
    property var lastSnapshot: null

    // An edit becomes one step once the pointer has rested.
    Timer { id: settle; interval: 350; onTriggered: root.remember() }
    function remember() {
        const now = snapshot()
        if (lastSnapshot && JSON.stringify(lastSnapshot) === JSON.stringify(now))
            return
        if (lastSnapshot)
            undoStack = undoStack.concat([lastSnapshot])
        redoStack = []
        lastSnapshot = now
    }
    function restore(state: var) {
        turns = state.turns
        ratio = state.ratio
        strokes = state.strokes
        setFrameShares(state.frame)
        lastSnapshot = state
    }
    function undo() {
        if (undoStack.length === 0)
            return
        redoStack = redoStack.concat([snapshot()])
        const previous = undoStack[undoStack.length - 1]
        undoStack = undoStack.slice(0, -1)
        restore(previous)
    }
    function redo() {
        if (redoStack.length === 0)
            return
        undoStack = undoStack.concat([snapshot()])
        const next = redoStack[redoStack.length - 1]
        redoStack = redoStack.slice(0, -1)
        restore(next)
    }
    // Holding undo reverts everything, as one more step.
    function revertAll() {
        if (undoStack.length === 0)
            return
        undoStack = undoStack.concat([snapshot()])
        redoStack = []
        restore({ turns: 0, ratio: "Free", frame: { x: 0, y: 0, w: 1, h: 1 }, strokes: [] })
    }
    onIsOpenChanged: if (isOpen) lastSnapshot = snapshot()

    function save() {
        const shares = frameShares()
        const saved = MediaEditor.saveCrop(path, shares.x, shares.y, shares.w, shares.h, turns, strokes)
        close()
        gallery.notify(saved.length > 0 ? "Saved as a copy" : "Could not save")
    }
    readonly property bool hasEdits: undoStack.length > 0
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    Item {
        id: keys
        focus: root.isOpen
        Keys.onPressed: event => {
            if (event.key === Qt.Key_Escape)
                root.close()
            else if (event.key === Qt.Key_Z && (event.modifiers & Qt.ControlModifier))
                (event.modifiers & Qt.ShiftModifier) ? root.redo() : root.undo()
            else if (event.key === Qt.Key_Y && (event.modifiers & Qt.ControlModifier))
                root.redo()
            else if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter)
                root.save()
            else if (event.key === Qt.Key_R)
                root.rotate()
            else
                return
            event.accepted = true
        }
    }

    Rectangle {
        anchors.fill: parent
        color: Theme.viewerGround
    }

    // #region ── the picture, the veil and the frame ───────────────────────────────────────────
    // The picture travels from the viewer's box into the crop frame as one move and one scale.
    readonly property rect shownPicture: Qt.rect(startRect.x + (picture.x - startRect.x) * arrival, startRect.y + (picture.y - startRect.y) * arrival,
                                                  startRect.width + (picture.width - startRect.width) * arrival, startRect.height + (picture.height - startRect.height) * arrival)

    Item {
        id: pictureBox
        x: root.shownPicture.x
        y: root.shownPicture.y
        width: root.shownPicture.width
        height: root.shownPicture.height
        rotation: root.swingAngle

        Image {
            anchors.centerIn: parent
            width: root.turns % 2 === 0 ? parent.width : parent.height
            height: root.turns % 2 === 0 ? parent.height : parent.width
            rotation: 90 * root.turns
            source: root.path.length > 0 ? System.fileUrl(root.path) : ""
            sourceSize: Qt.size(2048, 2048)
            autoTransform: true
            asynchronous: true
            smooth: true
            mipmap: true
            fillMode: Image.Stretch
        }

        // The pencil's lines, in shares of the turned picture.
        Canvas {
            id: lines
            anchors.fill: parent
            property var drawn: root.strokes
            property var live: null
            onDrawnChanged: requestPaint()
            onLiveChanged: requestPaint()
            onWidthChanged: requestPaint()
            onPaint: {
                const context = getContext("2d")
                context.reset()
                context.lineCap = "round"
                context.lineJoin = "round"
                const all = live ? drawn.concat([live]) : drawn
                for (const stroke of all) {
                    context.strokeStyle = stroke.color
                    context.lineWidth = Math.max(1, stroke.width * width)
                    context.beginPath()
                    stroke.points.forEach((point, index) => index === 0 ? context.moveTo(point[0] * width, point[1] * height) : context.lineTo(point[0] * width, point[1] * height))
                    if (stroke.points.length === 1)
                        context.lineTo(stroke.points[0][0] * width + 0.5, stroke.points[0][1] * height)
                    context.stroke()
                }
            }
        }
    }

    // Outside the frame stays visible, darkened by the veil; the frame fades while the picture swings round.
    Item {
        anchors.fill: parent
        opacity: root.arrival * (1 - Math.abs(root.swingAngle) / 90)
        Rectangle { x: 0; y: 0; width: parent.width; height: root.frame.y; color: Qt.rgba(0, 0, 0, 0.6) }
        Rectangle { x: 0; y: root.frame.y + root.frame.height; width: parent.width; height: parent.height - y; color: Qt.rgba(0, 0, 0, 0.6) }
        Rectangle { x: 0; y: root.frame.y; width: root.frame.x; height: root.frame.height; color: Qt.rgba(0, 0, 0, 0.6) }
        Rectangle { x: root.frame.x + root.frame.width; y: root.frame.y; width: parent.width - x; height: root.frame.height; color: Qt.rgba(0, 0, 0, 0.6) }

        Rectangle {
            x: root.frame.x
            y: root.frame.y
            width: root.frame.width
            height: root.frame.height
            color: "transparent"
            border.width: 1.5 * Theme.dp
            border.color: "white"

            // Thirds, the way every crop frame shows them.
            Repeater {
                model: 2
                Rectangle { required property int index; x: parent.width * (index + 1) / 3; width: 1; height: parent.height; color: Qt.rgba(1, 1, 1, 0.35) }
            }
            Repeater {
                model: 2
                Rectangle { required property int index; y: parent.height * (index + 1) / 3; height: 1; width: parent.width; color: Qt.rgba(1, 1, 1, 0.35) }
            }
            Repeater {
                model: 4
                Rectangle {
                    required property int index
                    readonly property bool isRight: index % 2 === 1
                    readonly property bool isBottom: index >= 2
                    x: isRight ? parent.width - width + 2 * Theme.dp : -2 * Theme.dp
                    y: isBottom ? parent.height - height + 2 * Theme.dp : -2 * Theme.dp
                    width: 18 * Theme.dp
                    height: 18 * Theme.dp
                    color: "transparent"
                    border.width: 3 * Theme.dp
                    border.color: root.violet
                    visible: root.mode === "crop"
                }
            }
        }
    }

    // One pointer area for the frame's edges and corners, panning a zoomed picture, and drawing.
    MouseArea {
        anchors.fill: parent
        enabled: root.isOpen
        hoverEnabled: true
        property string grip: ""
        property point last
        property var stroke: null
        readonly property real reach: 14 * Theme.dp

        function gripAt(x: real, y: real): string {
            const f = root.frame
            const nearLeft = Math.abs(x - f.x) < reach
            const nearRight = Math.abs(x - f.x - f.width) < reach
            const nearTop = Math.abs(y - f.y) < reach
            const nearBottom = Math.abs(y - f.y - f.height) < reach
            const insideX = x > f.x - reach && x < f.x + f.width + reach
            const insideY = y > f.y - reach && y < f.y + f.height + reach
            let grip = ""
            if (nearTop && insideX) grip += "t"
            if (nearBottom && insideX) grip += "b"
            if (nearLeft && insideY) grip += "l"
            if (nearRight && insideY) grip += "r"
            return grip
        }
        cursorShape: root.mode === "draw" ? Qt.CrossCursor : (grip.length > 0 || gripAt(mouseX, mouseY).length > 0) ? Qt.SizeAllCursor : Qt.OpenHandCursor

        onPressed: mouse => {
            last = Qt.point(mouse.x, mouse.y)
            if (root.mode === "draw") {
                const share = Qt.point((mouse.x - root.picture.x) / root.picture.width, (mouse.y - root.picture.y) / root.picture.height)
                stroke = { color: String(root.pencilColor), width: 0.002 + root.pencilThickness * 0.02, points: [[share.x, share.y]] }
                lines.live = stroke
                return
            }
            grip = gripAt(mouse.x, mouse.y)
        }
        onPositionChanged: mouse => {
            if (!pressed)
                return
            const dx = mouse.x - last.x
            const dy = mouse.y - last.y
            last = Qt.point(mouse.x, mouse.y)
            if (root.mode === "draw" && stroke) {
                stroke.points.push([(mouse.x - root.picture.x) / root.picture.width, (mouse.y - root.picture.y) / root.picture.height])
                lines.live = Object.assign({}, stroke)
                return
            }
            if (grip.length > 0)
                resize(dx, dy)
            else
                root.pan(dx, dy)
        }
        onReleased: {
            if (root.mode === "draw" && stroke) {
                // Each line is an undo step.
                root.strokes = root.strokes.concat([stroke])
                lines.live = null
                stroke = null
                root.remember()
                return
            }
            grip = ""
            settle.restart()
        }
        onWheel: wheel => root.zoomAt(Math.pow(1.0015, wheel.angleDelta.y), wheel.x, wheel.y)

        // The frame by its edges and corners, inside what of the picture shows, at least 40 across, holding a fixed ratio.
        function resize(dx: real, dy: real) {
            const bounds = root.visiblePicture()
            const minimum = 40 * Theme.dp
            let left = root.frame.x
            let top = root.frame.y
            let right = left + root.frame.width
            let bottom = top + root.frame.height
            if (grip.indexOf("l") >= 0) left = Math.max(bounds.x, Math.min(right - minimum, left + dx))
            if (grip.indexOf("r") >= 0) right = Math.min(bounds.x + bounds.width, Math.max(left + minimum, right + dx))
            if (grip.indexOf("t") >= 0) top = Math.max(bounds.y, Math.min(bottom - minimum, top + dy))
            if (grip.indexOf("b") >= 0) bottom = Math.min(bounds.y + bounds.height, Math.max(top + minimum, bottom + dy))
            const wanted = root.ratioValue(root.ratio)
            if (wanted > 0) {
                const isWidthLed = grip.indexOf("l") >= 0 || grip.indexOf("r") >= 0
                if (isWidthLed) {
                    let height = (right - left) / wanted
                    if (grip.indexOf("t") >= 0) top = bottom - height
                    else bottom = top + height
                } else {
                    const width = (bottom - top) * wanted
                    if (grip.indexOf("l") >= 0) left = right - width
                    else right = left + width
                }
                if (left < bounds.x || right > bounds.x + bounds.width || top < bounds.y || bottom > bounds.y + bounds.height)
                    return
            }
            root.frame = Qt.rect(left, top, right - left, bottom - top)
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── the controls, each from its own edge ──────────────────────────────────────────
    TopButton {
        x: 12 * Theme.dp
        y: 10 * Theme.dp - (1 - root.arrival) * 60 * Theme.dp
        glyph: "back"
        isSolid: true
        onClicked: root.close()
    }

    Pop {
        x: root.width - 62 * Theme.dp
        y: 10 * Theme.dp - (1 - root.arrival) * 60 * Theme.dp
        isShown: root.mode === "crop"
        TopButton {
            glyph: "rotate"
            ink: root.violet
            isSolid: true
            onClicked: root.rotate()
        }
    }

    // The ratios stand in a column over the picture's bottom left as bare labels read by a drop shadow, Free at the bottom; the chosen one in the crop colour.
    Pop {
        x: 20 * Theme.dp - (1 - root.arrival) * 120 * Theme.dp
        anchors.bottom: foot.top
        anchors.bottomMargin: 12 * Theme.dp
        isShown: root.mode === "crop"
        Column {
            spacing: 6 * Theme.dp
            Repeater {
                model: root.ratios.slice().reverse()
                Pressable {
                    id: ratioChoice
                    required property string modelData
                    width: ratioText.implicitWidth + 12 * Theme.dp
                    height: 26 * Theme.dp
                    onClicked: root.applyRatio(modelData)
                    Text {
                        id: ratioText
                        anchors.verticalCenter: parent.verticalCenter
                        text: ratioChoice.modelData.toUpperCase()
                        color: root.ratio === ratioChoice.modelData ? root.violet : Theme.textBright
                        font.family: Theme.mono
                        font.pixelSize: Theme.labelSize * 1.1
                        font.letterSpacing: Theme.labelSpacing * 0.6
                        style: Text.Raised
                        styleColor: Qt.rgba(0, 0, 0, 0.8)
                    }
                }
            }
        }
    }

    // In draw mode the pencil's column: its colours as dots from the bottom up, and a drag-only thickness slider standing beside them.
    Pop {
        x: 20 * Theme.dp - (1 - root.arrival) * 120 * Theme.dp
        anchors.bottom: foot.top
        anchors.bottomMargin: 12 * Theme.dp
        isShown: root.mode === "draw"
        delay: Motion.stateChange
        Row {
            spacing: 14 * Theme.dp
            Column {
                spacing: 8 * Theme.dp
                Repeater {
                    model: root.pencilColors.slice().reverse()
                    Pressable {
                        id: dot
                        required property var modelData
                        readonly property bool isChosen: Qt.colorEqual(root.pencilColor, modelData)
                        width: 24 * Theme.dp
                        height: 24 * Theme.dp
                        onClicked: root.pencilColor = modelData
                        Rectangle {
                            anchors.centerIn: parent
                            width: dot.isChosen ? 22 * Theme.dp : 16 * Theme.dp
                            height: width
                            radius: width / 2
                            color: dot.modelData
                            border.width: 1.5 * Theme.dp
                            border.color: dot.isChosen ? "white" : Theme.borderControl
                            Behavior on width { NumberAnimation { duration: Motion.stateChange; easing.type: Motion.backOut } }
                        }
                    }
                }
            }
            // The knob is the line itself, thin at the foot.
            Item {
                width: 28 * Theme.dp
                height: 8 * 32 * Theme.dp
                Rectangle {
                    anchors.horizontalCenter: parent.horizontalCenter
                    width: 2 * Theme.dp
                    height: parent.height
                    radius: 1
                    color: Theme.borderStrong
                }
                Rectangle {
                    anchors.horizontalCenter: parent.horizontalCenter
                    y: (1 - root.pencilThickness) * (parent.height - height)
                    width: 4 * Theme.dp + root.pencilThickness * 18 * Theme.dp
                    height: width
                    radius: width / 2
                    color: root.pencilColor
                    border.width: 1
                    border.color: Theme.borderControl
                }
                MouseArea {
                    anchors.fill: parent
                    cursorShape: Qt.SizeVerCursor
                    property real startY: 0
                    property real startValue: 0
                    onPressed: mouse => { startY = mouse.y; startValue = root.pencilThickness }
                    onPositionChanged: mouse => root.pencilThickness = Math.max(0, Math.min(1, startValue - (mouse.y - startY) / height))
                }
            }
        }
    }

    Item {
        id: foot
        anchors.bottom: parent.bottom
        anchors.bottomMargin: 18 * Theme.dp - (1 - root.arrival) * 90 * Theme.dp
        width: parent.width
        height: 58 * Theme.dp

        // A nav like the main view's, crop and pen; a video keeps to cropping.
        SegmentedTrack {
            x: 20 * Theme.dp
            width: 160 * Theme.dp
            anchors.verticalCenter: parent.verticalCenter
            options: ["Crop", "Draw"]
            currentIndex: root.mode === "crop" ? 0 : 1
            onPicked: index => root.mode = index === 0 ? "crop" : "draw"
            isGlass: true
            accent: root.violet
        }

        Row {
            anchors.right: parent.right
            anchors.rightMargin: 16 * Theme.dp
            anchors.verticalCenter: parent.verticalCenter
            spacing: 4 * Theme.dp

            // Faded while it has nothing to do, keeping the crop colour.
            IconButton {
                glyph: "undo"
                ink: root.violet
                opacity: root.undoStack.length > 0 ? 1 : 0.38
                holdInterval: 600
                onClicked: root.undo()
                onHeld: root.revertAll()
            }
            IconButton {
                glyph: "redo"
                ink: root.violet
                opacity: root.redoStack.length > 0 ? 1 : 0.38
                onClicked: root.redo()
            }
            IconButton {
                glyph: "check"
                ink: root.violet
                opacity: root.hasEdits ? 1 : 0.38
                onClicked: root.save()
            }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────
}
