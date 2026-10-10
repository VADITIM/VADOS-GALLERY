import QtQuick
import Gallery

// Going through a folder one photo at a time (ReviewScreen.kt, VAS components/23): newest first, on black. Swipe left (or the bin) to let one go, right (or the tick) to keep it;
// the card leans and tints as it goes, and the next waits underneath. Nothing is deleted while reviewing: Done, or the end, sends what was let go to the trash in one step, with undo.
// Each folder remembers how far it got and what was marked, and picks up there the next time.
Item {
    id: root

    required property var gallery
    property bool isOpen: false
    property string source: ""
    property bool isPrivate: false
    property real shown: 0
    property int position: 0
    property var marks: ({})
    property int markCount: 0
    // The decisions in order, so the middle button can take back the last one.
    property var history: []
    property real drag: 0
    property real pullDown: 0
    property bool isLeaving: false
    property bool isSummaryShown: false
    readonly property int total: items.count
    readonly property bool isAtEnd: position >= total
    readonly property string currentPath: isAtEnd ? "" : items.pathAt(total - 1 - position)
    readonly property var currentFacts: (items.revision >= 0 && items.item(total - 1 - position))

    visible: shown > 0
    z: 70

    MediaGridModel {
        id: items
        source: root.source
        columns: 1
        hasHeaders: false
    }

    // #region ── opening, saving, closing ──────────────────────────────────────────────────────
    function key(): string {
        return "review/" + source
    }

    function open(fromSource: string, inPrivate: bool) {
        source = fromSource
        isPrivate = inPrivate
        items.reload()
        const saved = JSON.parse(Settings.viewValue("review", key()) || "{}")
        const loaded = {}
        let count = 0
        for (const path of saved.marks ?? [])
            if (items.indexOfPath(path) >= 0) {
                loaded[path] = true
                ++count
            }
        marks = loaded
        markCount = count
        // A folder reviewed to the end with nothing marked begins again from the newest.
        position = saved.position ?? 0
        if (position >= total && markCount === 0)
            position = 0
        history = []
        drag = 0
        isSummaryShown = position >= total
        isOpen = true
        sink.stop()
        riseIn.restart()
        focusItem.forceActiveFocus()
    }

    function save() {
        Settings.setViewValue("review", key(), JSON.stringify({ position: position, marks: Object.keys(marks) }))
    }

    function close() {
        if (!isOpen)
            return
        if (markCount > 0 && !isSummaryShown) {
            isSummaryShown = true
            return
        }
        save()
        isOpen = false
        FocusHome.restore()
        riseIn.stop()
        sink.restart()
    }

    // Every marked photo at once, to the trash with undo; inside Private to Private's trash.
    function deleteMarked() {
        const paths = Object.keys(marks)
        marks = {}
        markCount = 0
        if (isPrivate)
            Actions.trashPrivate(paths)
        else
            Actions.trash(paths)
        position = 0
        save()
        isSummaryShown = false
        close()
    }

    function unmarkAll() {
        marks = {}
        markCount = 0
        save()
        isSummaryShown = false
        close()
    }

    function keepForLater() {
        isSummaryShown = false
        save()
        isOpen = false
        FocusHome.restore()
        riseIn.stop()
        sink.restart()
    }

    // Starts over from the newest, clearing the saved point and the marks; a second click while anything is marked.
    property bool isResetArmed: false
    function reset() {
        if (markCount > 0 && !isResetArmed) {
            isResetArmed = true
            return
        }
        isResetArmed = false
        marks = {}
        markCount = 0
        history = []
        position = 0
        isSummaryShown = false
        save()
    }

    NumberAnimation { id: riseIn; target: root; property: "shown"; to: 1; duration: Motion.overlayEnter; easing.type: Motion.powerTwoOut }
    NumberAnimation { id: sink; target: root; property: "shown"; to: 0; duration: Motion.overlayLeave; easing.type: Motion.powerTwoIn }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── deciding ──────────────────────────────────────────────────────────────────────
    function decide(isKept: bool) {
        if (isAtEnd || isLeaving)
            return
        fly.to = (isKept ? 1 : -1) * root.width * 1.3
        fly.isKept = isKept
        fly.from = drag
        isLeaving = true
        fly.restart()
    }

    function commit(isKept: bool) {
        const path = currentPath
        const wasMarked = marks[path] === true
        const next = Object.assign({}, marks)
        if (!isKept && !wasMarked) {
            next[path] = true
            ++markCount
        } else if (isKept && wasMarked) {
            delete next[path]
            --markCount
        }
        marks = next
        history = history.concat([{ path: path, wasMarked: wasMarked }])
        ++position
        drag = 0
        isLeaving = false
        if (position % 10 === 0)
            save()
        if (isAtEnd)
            isSummaryShown = true
    }

    function takeBack() {
        if (history.length === 0 || isLeaving)
            return
        const last = history[history.length - 1]
        history = history.slice(0, -1)
        const isMarked = marks[last.path] === true
        const next = Object.assign({}, marks)
        if (isMarked && !last.wasMarked) {
            delete next[last.path]
            --markCount
        } else if (!isMarked && last.wasMarked) {
            next[last.path] = true
            ++markCount
        }
        marks = next
        --position
        isSummaryShown = false
        // The last card comes back down from above.
        pullDown = -1
        comeBack.restart()
    }

    NumberAnimation {
        id: fly
        property bool isKept: true
        target: root
        property: "drag"
        duration: Motion.overlayLeave * 1.4
        easing.type: Motion.powerTwoIn
        onFinished: root.commit(isKept)
    }
    NumberAnimation { id: home; target: root; property: "drag"; to: 0; duration: Motion.stateChange; easing.type: Motion.backOut }
    NumberAnimation { id: comeBack; target: root; property: "pullDown"; to: 0; duration: Motion.overlayEnter; easing.type: Motion.powerTwoOut }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    Item {
        id: focusItem
        anchors.fill: parent
        focus: root.isOpen
        Keys.onPressed: event => {
            switch (event.key) {
            case Qt.Key_Left: root.decide(false); break
            case Qt.Key_Right: root.decide(true); break
            case Qt.Key_Backspace:
            case Qt.Key_Z: root.takeBack(); break
            case Qt.Key_Escape: root.close(); break
            default: return
            }
            event.accepted = true
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
        }

        // The upcoming photos, overlapped in a row above the card at the right, nearest in front and brightest.
        Row {
            anchors.right: parent.right
            anchors.rightMargin: 18 * Theme.dp
            y: 64 * Theme.dp
            spacing: -18 * Theme.dp
            layoutDirection: Qt.RightToLeft
            z: 3
            Repeater {
                model: 3
                SquircleImage {
                    required property int index
                    readonly property int at: root.total - 1 - root.position - 1 - index
                    visible: at >= 0
                    width: 54 * Theme.dp
                    height: width
                    z: 3 - index
                    opacity: 1 - index * 0.25
                    radius: 12 * Theme.dp
                    source: at >= 0 ? "image://thumbnail/" + encodeURIComponent(items.pathAt(at)) : ""
                    sourceSize: Qt.size(128, 128)
                }
            }
        }

        // The next card waits underneath at 0.92 and grows as the one on top goes.
        ReviewCard {
            anchors.fill: parent
            path: root.position + 1 < root.total ? items.pathAt(root.total - 2 - root.position) : ""
            scale: 0.92 + 0.08 * Math.min(1, Math.abs(root.drag) / (root.width * 0.28))
            opacity: path.length > 0 ? 1 : 0
        }

        ReviewCard {
            id: card
            anchors.fill: parent
            path: root.currentPath
            readonly property real share: root.drag / Math.max(1, root.width)
            x: root.drag
            y: root.pullDown * root.height
            // Leans 14° per screen width, about a point below the card.
            rotation: 14 * share
            transformOrigin: Item.Bottom
            tint: root.drag < 0 ? Theme.danger : Theme.accent
            tintStrength: Math.min(0.45, Math.abs(share) * 1.2)
            isMarked: root.marks[root.currentPath] === true
        }

        MouseArea {
            anchors.fill: parent
            enabled: root.isOpen && !root.isAtEnd
            property real pressX: 0
            property real pressY: 0
            property bool isDragging: false
            onPressed: mouse => {
                pressX = mouse.x
                pressY = mouse.y
                isDragging = false
                home.stop()
            }
            onPositionChanged: mouse => {
                if (!isDragging && Math.hypot(mouse.x - pressX, mouse.y - pressY) > 8 * Theme.dp)
                    isDragging = true
                if (isDragging && Math.abs(mouse.x - pressX) > Math.abs(mouse.y - pressY))
                    root.drag = mouse.x - pressX
            }
            onReleased: mouse => {
                if (!isDragging) {
                    // Clicking the left half lets the photo go, the right half keeps it.
                    root.decide(mouse.x > width / 2)
                    return
                }
                if (mouse.y - pressY > 100 * Theme.dp && Math.abs(root.drag) < 40 * Theme.dp) {
                    root.drag = 0
                    root.takeBack()
                } else if (Math.abs(root.drag) > root.width * 0.28)
                    root.decide(root.drag > 0)
                else {
                    home.from = root.drag
                    home.restart()
                }
            }
        }

        // #region ── the controls ──────────────────────────────────────────────────────────────
        Row {
            x: 12 * Theme.dp
            y: 10 * Theme.dp
            spacing: 8 * Theme.dp
            z: 4

            TopButton {
                glyph: "close"
                isSolid: true
                onClicked: root.close()
            }
            TopButton {
                glyph: "reset"
                isSolid: true
                ink: root.isResetArmed ? Theme.danger : Theme.textBody
                onClicked: root.reset()
            }
        }

        // A shade behind the bottom controls, so they read over any photo.
        Rectangle {
            anchors.bottom: parent.bottom
            width: parent.width
            height: controls.height + 80 * Theme.dp
            z: 3
            gradient: Gradient {
                GradientStop { position: 0; color: "transparent" }
                GradientStop { position: 1; color: Qt.rgba(0, 0, 0, 0.75) }
            }
        }

        Column {
            id: controls
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.bottom: parent.bottom
            anchors.bottomMargin: 20 * Theme.dp
            spacing: 12 * Theme.dp
            z: 4

            // Filled with the section colour and white on it: a tick ends the sitting; once anything is marked, the bin and the count delete them all and close.
            Pressable {
                anchors.horizontalCenter: parent.horizontalCenter
                width: doneRow.implicitWidth + 36 * Theme.dp
                height: 44 * Theme.dp
                onClicked: root.markCount > 0 ? root.deleteMarked() : root.keepForLater()
                Rectangle {
                    anchors.fill: parent
                    radius: height / 2
                    color: Theme.accent
                }
                Row {
                    id: doneRow
                    anchors.centerIn: parent
                    spacing: 8 * Theme.dp
                    Glyph { anchors.verticalCenter: parent.verticalCenter; width: 18 * Theme.dp; height: width; name: root.markCount > 0 ? "trash" : "check"; ink: "white" }
                    Text {
                        anchors.verticalCenter: parent.verticalCenter
                        text: root.markCount > 0 ? "DONE · " + root.markCount : "DONE"
                        color: "white"
                        font.family: Theme.mono
                        font.pixelSize: Theme.labelSize * 1.1
                        font.letterSpacing: Theme.labelSpacing
                        font.bold: true
                    }
                }
            }

            Row {
                anchors.horizontalCenter: parent.horizontalCenter
                spacing: 18 * Theme.dp
                IconButton { glyph: "trash"; ink: Theme.danger; onClicked: root.decide(false) }
                IconButton { glyph: "undo"; ink: root.history.length > 0 ? Theme.textBody : Theme.textFaint; onClicked: root.takeBack() }
                IconButton { glyph: "check"; ink: Theme.accent; onClicked: root.decide(true) }
            }

            // The marked count keeps its line whether or not anything is marked.
            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                text: root.markCount > 0 ? root.markCount + " MARKED" : " "
                color: Theme.danger
                font.family: Theme.mono
                font.pixelSize: Theme.labelSize
                font.letterSpacing: Theme.labelSpacing
            }

            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                text: (root.currentFacts.timestamp ? System.formatDate(root.currentFacts.timestamp) + "   " : "") + Math.min(root.position + 1, root.total) + " / " + root.total
                color: Theme.textBright
                font.family: Theme.mono
                font.pixelSize: Theme.labelSize
                font.letterSpacing: Theme.labelSpacing * 0.5
            }

            Rectangle {
                anchors.horizontalCenter: parent.horizontalCenter
                width: Math.min(root.width * 0.6, 420 * Theme.dp)
                height: 3 * Theme.dp
                radius: height / 2
                color: Theme.borderStrong
                Rectangle {
                    width: parent.width * Math.min(1, root.position / Math.max(1, root.total))
                    height: parent.height
                    radius: height / 2
                    color: Theme.accent
                    Behavior on width { NumberAnimation { duration: Motion.stateChange; easing.type: Motion.powerTwoOut } }
                }
            }
        }
        // #endregion ───────────────────────────────────────────────────────────────────────────

        // #region ── the end of a sitting ──────────────────────────────────────────────────────
        // Closing mid-way can keep the marks for later or unmark them; reaching the end offers the one step that deletes them.
        Pop {
            anchors.centerIn: parent
            isShown: root.isSummaryShown
            z: 6

            Item {
                width: 340 * Theme.dp
                height: summary.implicitHeight + 40 * Theme.dp
                Glass {
                    anchors.fill: parent
                    radius: Theme.sheetRadius
                    isSolid: true
                    solidColor: Theme.panelSolid
                }
                Column {
                    id: summary
                    x: 20 * Theme.dp
                    y: 20 * Theme.dp
                    width: parent.width - 40 * Theme.dp
                    spacing: 6 * Theme.dp
                    Text {
                        text: root.markCount > 0 ? root.markCount + " MARKED" : "ALL REVIEWED"
                        color: root.markCount > 0 ? Theme.danger : Theme.accent
                        font.family: Theme.mono
                        font.pixelSize: Theme.labelSize
                        font.letterSpacing: Theme.labelSpacing
                        bottomPadding: 8 * Theme.dp
                    }
                    MenuRow {
                        visible: root.markCount > 0
                        glyph: "trash"
                        label: "DELETE " + root.markCount
                        ink: Theme.danger
                        onClicked: root.deleteMarked()
                    }
                    MenuRow {
                        visible: root.markCount > 0 && !root.isAtEnd
                        glyph: "check"
                        label: "KEEP MARKS FOR LATER"
                        onClicked: root.keepForLater()
                    }
                    MenuRow {
                        visible: root.markCount > 0
                        glyph: "close"
                        label: "UNMARK ALL"
                        onClicked: root.unmarkAll()
                    }
                    MenuRow {
                        visible: root.markCount === 0
                        glyph: "check"
                        label: "CLOSE"
                        hasDivider: false
                        onClicked: root.keepForLater()
                    }
                }
            }
        }
        // #endregion ───────────────────────────────────────────────────────────────────────────
    }
}
