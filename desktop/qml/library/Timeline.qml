import QtQuick
import Gallery

// The timeline along a grid's right edge (Timeline.kt, VAS components/21): every year with all its months, oldest at the top, fitted whole into a window 30% of the grid's height.
// Labels swell around the marker and shrink away from it; the falloff steepens until the strip fits, so any number of years looks the same. Held, the marker is the pointer:
// the label under it is where the grid goes, and the month held steps out to the left and widens into its full name.
Item {
    id: root

    required property var grid
    readonly property var list: grid.list
    readonly property var marks: (grid.model.revision >= 0 && grid.model.months())
    readonly property bool isShown: marks.length > 0 && grid.model.count > 0

    readonly property real stripWidth: 24 * Theme.dp
    readonly property real monthHeight: 18 * Theme.dp
    readonly property real yearHeight: 22 * Theme.dp
    readonly property real gapShare: 0.25
    readonly property real falloffGentle: 0.22
    readonly property real falloffSteepest: 4
    readonly property real yearSmallest: 0.7
    readonly property real monthGone: 0.14
    readonly property real monthFade: 0.2
    readonly property var monthNames: ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"]
    readonly property var fullNames: ["JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"]

    visible: isShown

    // #region ── the steps: a year, then its months ────────────────────────────────────────────
    readonly property var labels: {
        const steps = []
        let year = -1
        for (let at = 0; at < marks.length; ++at) {
            const mark = marks[at]
            if (mark.year !== year) {
                steps.push({ year: mark.year, mark: -1 })
                year = mark.year
            }
            steps.push({ year: mark.year, mark: at })
        }
        return steps
    }
    readonly property var slotOfMark: {
        const slots = []
        for (let slot = 0; slot < labels.length; ++slot)
            if (labels[slot].mark >= 0)
                slots[labels[slot].mark] = slot
        return slots
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── where the grid stands ─────────────────────────────────────────────────────────
    // A scrollbar's reading in steps: the month at the top of the grid plus how far the grid is toward the next, so the very end reaches the newest month.
    property real gridPosition: 0
    function readGrid() {
        if (!isShown || list.count === 0)
            return
        const topY = list.contentY + list.topMargin
        const topRow = Math.max(0, list.indexAt(width / 2, topY + 1))
        const topItem = list.itemAtIndex(topRow)
        const top = topRow + (topItem ? Math.max(0, Math.min(1, (topY - topItem.y) / Math.max(1, topItem.height))) : 0)
        const bottomRow = list.indexAt(width / 2, list.contentY + list.height - list.bottomMargin - 1)
        const bottom = (bottomRow < 0 ? list.count : bottomRow + 1)
        const screenful = Math.max(0, bottom - top)
        const reach = Math.max(0, Math.min(1, top / Math.max(1, list.count - screenful)))
        const reading = top + screenful * reach
        let at = 0
        for (let index = 0; index < marks.length; ++index)
            if (marks[index].row <= reading)
                at = index
        const start = slotOfMark[at] ?? 0
        const next = marks[at + 1]
        if (!next) {
            gridPosition = start
            return
        }
        const fraction = Math.max(0, Math.min(1, (reading - marks[at].row) / Math.max(1, next.row - marks[at].row)))
        gridPosition = start + fraction * ((slotOfMark[at + 1] ?? start) - start)
    }
    Connections {
        target: root.list
        function onContentYChanged() { root.readGrid() }
        function onCountChanged() { root.readGrid() }
    }
    onMarksChanged: readGrid()
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── holding ───────────────────────────────────────────────────────────────────────
    property int heldMark: -1
    property real heldPosition: 0
    readonly property bool isHeld: heldMark >= 0
    property real reveal: isHeld ? 1 : 0
    Behavior on reveal { NumberAnimation { duration: Motion.timelineReveal; easing.type: Motion.powerTwoOut } }
    // Taking hold or letting go hands the strip between the pointer and the grid; this carries the difference away so neither jumps.
    property real handover: 0
    NumberAnimation { id: handing; target: root; property: "handover"; to: 0; duration: Motion.timelineReveal; easing.type: Motion.powerTwoOut }
    function handOver(from: real, to: real) {
        handover = from - to
        handing.restart()
    }

    readonly property real position: (isHeld ? heldPosition : gridPosition) + handover
    readonly property real grown: 1 + 0.5 * reveal
    readonly property real track: height * 0.3 * grown
    readonly property real trackTop: (height - track) / 2
    // The month shown: the one held, else the one at the grid's top.
    readonly property int shownMark: {
        if (isHeld)
            return heldMark
        for (let slot = Math.min(labels.length - 1, Math.floor(gridPosition + 0.001)); slot >= 0; --slot)
            if (labels[slot].mark >= 0)
                return labels[slot].mark
        return 0
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── the fitting ───────────────────────────────────────────────────────────────────
    function fullHeightOf(slot: int): real {
        return (labels[slot].mark < 0 ? yearHeight : monthHeight) * grown
    }
    function sizeAt(slot: int, distance: real, falloff: real): real {
        const size = Math.exp(-falloff * Math.abs(distance))
        return labels[slot].mark < 0 ? Math.max(size, yearSmallest) : size
    }
    function sizesAt(at: real, falloff: real): var {
        const sizes = []
        for (let slot = 0; slot < labels.length; ++slot)
            sizes.push(sizeAt(slot, slot - at, falloff))
        return sizes
    }
    // Neighbours stand apart by the mean of their heights and a quarter of it, so gaps shrink with their labels.
    function lengthOf(sizes: var): real {
        const last = labels.length - 1
        let length = (fullHeightOf(0) * sizes[0] + fullHeightOf(last) * sizes[last]) / 2
        for (let slot = 0; slot < last; ++slot)
            length += (fullHeightOf(slot) * sizes[slot] + fullHeightOf(slot + 1) * sizes[slot + 1]) / 2 * (1 + gapShare)
        return length
    }
    function placementAt(at: real): var {
        if (labels.length === 0)
            return { places: [], sizes: [], presence: [], marker: 0 }
        let falloff = falloffGentle
        let natural = sizesAt(at, falloff)
        if (lengthOf(natural) > track) {
            let gentle = falloffGentle
            let steep = falloffSteepest
            for (let step = 0; step < 14; ++step) {
                const middle = (gentle + steep) / 2
                if (lengthOf(sizesAt(at, middle)) > track)
                    gentle = middle
                else
                    steep = middle
            }
            falloff = steep
            natural = sizesAt(at, falloff)
        }
        const length = lengthOf(natural)
        const fit = length > track ? track / length : 1
        const sizes = natural.map(size => size * fit)
        const places = [trackTop + (track - length * fit) / 2 + fullHeightOf(0) * sizes[0] / 2]
        for (let slot = 1; slot < labels.length; ++slot)
            places.push(places[slot - 1] + (fullHeightOf(slot - 1) * sizes[slot - 1] + fullHeightOf(slot) * sizes[slot]) / 2 * (1 + gapShare))
        const presence = labels.map((label, slot) => label.mark < 0 ? 1 : Math.max(0, Math.min(1, (natural[slot] - monthGone) / monthFade)))
        // The month the marker is on always shows, whatever its size.
        const nearest = Math.max(0, Math.min(labels.length - 1, Math.round(at)))
        if (labels[nearest].mark >= 0)
            presence[nearest] = 1
        const anchor = Math.max(0, Math.min(labels.length - 1, Math.floor(at)))
        const marker = anchor < labels.length - 1 ? places[anchor] + (at - anchor) * (places[anchor + 1] - places[anchor]) : places[anchor]
        return { places: places, sizes: sizes, presence: presence, marker: marker }
    }
    readonly property var placement: placementAt(position)
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── drawing ───────────────────────────────────────────────────────────────────────
    Repeater {
        model: root.labels

        Item {
            id: label
            required property int index
            required property var modelData
            readonly property bool isMonth: modelData.mark >= 0
            readonly property bool isCurrent: isMonth ? modelData.mark === root.shownMark : modelData.year === (root.marks[root.shownMark] ?? {}).year
            readonly property real presence: root.placement.presence[index] ?? 0
            readonly property real size: root.placement.sizes[index] ?? 0
            // Held, the month under the pointer steps out of the strip to the left and widens into its full name; letting go puts it back.
            property real lift: isMonth && root.isHeld && modelData.mark === root.heldMark ? 1 : 0
            Behavior on lift { NumberAnimation { duration: Motion.timelineLift; easing.type: Motion.powerThreeInOut } }
            readonly property string shortText: isMonth ? root.monthNames[root.marks[modelData.mark].month - 1] : String(modelData.year)
            readonly property string fullText: isMonth ? root.fullNames[root.marks[modelData.mark].month - 1] + " " + modelData.year : shortText

            visible: presence > 0 || lift > 0
            width: pill.width
            height: pill.height
            x: root.width - root.stripWidth + 4 * Theme.dp - width - 72 * Theme.dp * lift
            y: (root.placement.places[index] ?? 0) + (root.placement.marker - (root.placement.places[index] ?? 0)) * lift - height / 2
            z: lift
            opacity: presence + (1 - presence) * lift
            // Shrinks toward the edge of the screen, so every label keeps its right side on the line; the held month comes to full size.
            transform: Scale {
                origin.x: label.width
                origin.y: label.height / 2
                xScale: (label.size + (1 - label.size) * label.lift) * root.grown
                yScale: xScale
            }

            Rectangle {
                id: pill
                readonly property real shortWidth: shortMeasure.implicitWidth
                width: shortWidth + (fullMeasure.implicitWidth - shortWidth) * label.lift + 12 * Theme.dp
                height: shortMeasure.implicitHeight + 4 * Theme.dp
                radius: height / 2
                color: Theme.panel
                clip: true

                Text {
                    id: shortMeasure
                    visible: false
                    text: label.shortText
                    font.family: Theme.mono
                    font.pixelSize: (label.isMonth ? 10 : 13) * Theme.dp
                }
                Text {
                    id: fullMeasure
                    visible: false
                    text: label.fullText
                    font.family: Theme.mono
                    font.pixelSize: (label.isMonth ? 10 : 13) * Theme.dp
                }
                Text {
                    x: 6 * Theme.dp
                    anchors.verticalCenter: parent.verticalCenter
                    text: label.lift > 0 ? label.fullText : label.shortText
                    color: label.isCurrent ? Theme.accent : label.isMonth ? Theme.textMuted : Theme.textBright
                    font.family: Theme.mono
                    font.pixelSize: (label.isMonth ? 10 : 13) * Theme.dp
                }
            }

            // The label itself can be taken hold of, not only the strip beside it.
            MouseArea {
                anchors.fill: parent
                anchors.margins: -6 * Theme.dp
                enabled: label.presence > 0.5
                cursorShape: Qt.SizeVerCursor
                onPressed: mouse => root.follow(mapToItem(root, 0, mouse.y).y)
                onPositionChanged: mouse => root.follow(mapToItem(root, 0, mouse.y).y)
                onReleased: root.release()
            }
        }
    }

    // Where the grid is, running down the window with it; under the pointer while held.
    Rectangle {
        x: root.width - 6 * Theme.dp - width
        y: root.placement.marker - height / 2
        width: 4 * Theme.dp
        height: 22 * Theme.dp
        radius: width / 2
        color: root.isHeld ? Theme.accent : Theme.textMuted
    }

    // Only the window's stretch of the right edge takes the pointer, so tiles under the edge stay clickable.
    MouseArea {
        x: root.width - root.stripWidth
        y: root.trackTop - 6 * Theme.dp
        width: root.stripWidth
        height: root.track + 12 * Theme.dp
        cursorShape: Qt.SizeVerCursor
        onPressed: mouse => root.follow(mapToItem(root, 0, mouse.y).y)
        onPositionChanged: mouse => root.follow(mapToItem(root, 0, mouse.y).y)
        onReleased: root.release()
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── following the pointer ─────────────────────────────────────────────────────────
    function follow(y: real) {
        // The step whose marker lands under the pointer, laid out as it would be there, so the label under the pointer is the one held.
        let low = 0
        let high = labels.length - 1
        for (let step = 0; step < 18; ++step) {
            const middle = (low + high) / 2
            if (placementAt(middle).marker < y)
                low = middle
            else
                high = middle
        }
        const at = (low + high) / 2
        if (!isHeld)
            handOver(position, at)
        heldPosition = at
        const slot = Math.max(0, Math.min(labels.length - 1, Math.round(at)))
        // A year stands for its first month.
        const mark = labels[slot].mark >= 0 ? labels[slot].mark : (labels[slot + 1] ? labels[slot + 1].mark : -1)
        if (mark < 0)
            return
        heldMark = mark
        grid.stopGlide()
        // Held at the strip's very end the grid goes to its very end, not to the top of the newest month.
        if (at >= labels.length - 1 - 0.02)
            list.positionViewAtEnd()
        else {
            list.positionViewAtIndex(marks[mark].row, ListView.Beginning)
            list.contentY -= list.topMargin
        }
    }

    function release() {
        if (!isHeld)
            return
        const from = position
        heldMark = -1
        readGrid()
        handOver(from, gridPosition)
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────
}
