import QtQuick
import Gallery

// Prime component (VAS components/19-pop-bar.md §3): change the entry there first, then this.
// The nav: icons, not words; the highlight's leading edge goes first and the trailing edge follows 60ms later; the chosen icon grows to 1.06 and takes its colour on the cut.
Item {
    id: root

    property string active: "recent"
    // What the Albums option shows: the place the user is in (albums, trash, private).
    property string albumsGlyph: "albums"
    property bool isPrivateMode: false
    property real pop: 1
    property real washAlpha: 1
    property var accentOf: function(key) { return Sections.find(key).accent }
    signal selected(string key)

    readonly property real optionWidth: (18 * 2 + 22) * Theme.dp
    readonly property real optionHeight: (13 * 2 + 22) * Theme.dp
    implicitWidth: Sections.all.length * optionWidth + (Sections.all.length - 1) * 2 * Theme.dp + 10 * Theme.dp
    implicitHeight: optionHeight + 10 * Theme.dp

    // The chosen icon takes its colour once the outgoing section has left, as every other accent does.
    property string inkActive: active
    onActiveChanged: inkCut.restart()
    Timer {
        id: inkCut
        interval: Motion.sectionLeave
        onTriggered: root.inkActive = root.active
    }

    function spanOf(key: string): var {
        for (let index = 0; index < Sections.all.length; ++index)
            if (Sections.all[index].key === key) {
                const left = 5 * Theme.dp + index * (optionWidth + 2 * Theme.dp)
                return [left, left + optionWidth]
            }
        return [0, 0]
    }

    property real washLeft: spanOf(active)[0]
    property real washRight: spanOf(active)[1]
    Connections {
        target: root
        function onActiveChanged() {
            const span = root.spanOf(root.active)
            const isMovingRight = span[1] > root.washRight
            leftEdge.to = span[0]
            rightEdge.to = span[1]
            leftLag.duration = isMovingRight ? Motion.navTrail : 0
            rightLag.duration = isMovingRight ? 0 : Motion.navTrail
            slide.restart()
        }
    }

    ParallelAnimation {
        id: slide
        SequentialAnimation {
            PauseAnimation { id: leftLag; duration: 0 }
            NumberAnimation { id: leftEdge; target: root; property: "washLeft"; duration: Motion.navSlide; easing.type: Motion.powerTwoOut }
        }
        SequentialAnimation {
            PauseAnimation { id: rightLag; duration: 0 }
            NumberAnimation { id: rightEdge; target: root; property: "washRight"; duration: Motion.navSlide; easing.type: Motion.powerTwoOut }
        }
    }

    Rectangle {
        x: root.washLeft
        y: 5 * Theme.dp
        width: Math.max(0, root.washRight - root.washLeft)
        height: root.optionHeight
        radius: height / 2
        color: Theme.pressedWash
        opacity: root.washAlpha
    }

    Repeater {
        model: Sections.all

        Pressable {
            id: option
            required property int index
            required property var modelData
            readonly property bool isChosen: modelData.key === root.active
            readonly property color ink: modelData.key === root.inkActive ? root.accentOf(modelData.key) : Theme.textMuted
            x: 5 * Theme.dp + index * (root.optionWidth + 2 * Theme.dp)
            y: 5 * Theme.dp
            width: root.optionWidth
            height: root.optionHeight
            transform: Scale {
                origin.x: option.width / 2
                origin.y: option.height / 2
                xScale: root.pop
                yScale: root.pop
            }
            onClicked: root.selected(modelData.key)

            Item {
                id: icon
                anchors.centerIn: parent
                width: 22 * Theme.dp
                height: 22 * Theme.dp
                scale: option.isChosen ? 1.06 : 1
                Behavior on scale { NumberAnimation { duration: Motion.navSlide; easing.type: Motion.backOut } }

                // The Albums option shows the place the user is in; a new place pops the old icon away, then pops its own in.
                property string shownGlyph: option.modelData.key === "albums" ? root.albumsGlyph : option.modelData.glyph
                property string drawnGlyph: shownGlyph
                property real glyphPop: 1
                onShownGlyphChanged: glyphSwap.restart()
                SequentialAnimation {
                    id: glyphSwap
                    NumberAnimation { target: icon; property: "glyphPop"; to: 0; duration: Motion.stateChange; easing.type: Motion.backIn }
                    ScriptAction { script: icon.drawnGlyph = icon.shownGlyph }
                    NumberAnimation { target: icon; property: "glyphPop"; to: 1; duration: Motion.stateChange; easing.type: Motion.backOut }
                }

                Glyph {
                    anchors.fill: parent
                    name: icon.drawnGlyph
                    scale: icon.glyphPop
                    ink: option.isHovered && !option.isChosen ? Theme.textBright : option.ink
                    Behavior on ink { ColorAnimation { duration: Motion.stateChange } }
                }

                // A section that works its own way here (Private's Recent and Favorites) is underlined in the accent.
                Rectangle {
                    anchors.horizontalCenter: parent.horizontalCenter
                    anchors.top: parent.bottom
                    anchors.topMargin: 4 * Theme.dp
                    width: parent.width * 0.6
                    height: 2 * Theme.dp
                    radius: height / 2
                    color: root.accentOf(option.modelData.key)
                    opacity: root.isPrivateMode && option.modelData.key !== "albums" ? 1 : 0
                    Behavior on opacity { NumberAnimation { duration: Motion.stateChange } }
                }
            }
        }
    }
}
