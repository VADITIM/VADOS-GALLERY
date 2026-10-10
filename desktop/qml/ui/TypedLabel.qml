import QtQuick
import Gallery

// A pill that types itself over: it deletes back to what the old and new text share, resizes from its middle, and types the rest in partway into the resize.
// A new colour comes as the new text starts typing, never while the old one is still leaving (VAS components/09 §10).
Item {
    id: root

    property string text: ""
    property color accent: Theme.accent
    property bool isFilled: true
    property real maximumWidth: 240 * Theme.dp
    property real fixedWidth: 0
    property real horizontalPadding: 12 * Theme.dp
    property real verticalPadding: 6 * Theme.dp
    property bool isTypedIn: false

    property string stage: isTypedIn ? "" : text
    property color shownAccent: accent
    readonly property real typeInAt: 0.3

    implicitWidth: fixedWidth > 0 ? fixedWidth : freeWidth
    implicitHeight: measure.implicitHeight + verticalPadding * 2
    // Measured once here and then only by the resize, so the width is never a binding on the text it measures.
    property real freeWidth: 0
    Component.onCompleted: {
        freeWidth = widthOf(stage)
        if (stage !== text)
            change()
    }

    function widthOf(label: string): real {
        measure.text = label
        return Math.min(maximumWidth, measure.implicitWidth + horizontalPadding * 2)
    }
    function commonPrefix(first: string, second: string): string {
        let length = 0
        while (length < first.length && length < second.length && first[length] === second[length])
            ++length
        return first.slice(0, length)
    }

    onTextChanged: change()
    onAccentChanged: change()

    function change() {
        const shared = commonPrefix(stage, text)
        const removed = stage.length - shared.length
        if (removed > 0)
            stage = shared
        // Deleting back first, then the resize, and the new text types in partway into it.
        untype.interval = Math.max(1, Motion.untypeLetter * removed)
        untype.restart()
    }

    Timer {
        id: untype
        onTriggered: {
            const target = root.widthOf(root.text)
            if (root.fixedWidth <= 0 && Math.abs(target - root.freeWidth) > 0.5) {
                resize.to = target
                resize.restart()
                typeIn.interval = Math.max(1, Motion.stateChange * root.typeInAt)
            } else {
                typeIn.interval = root.stage === root.text && root.accent !== root.shownAccent ? Math.max(1, Motion.sectionLeave) : 1
            }
            typeIn.restart()
        }
    }

    Timer {
        id: typeIn
        onTriggered: {
            root.shownAccent = root.accent
            root.stage = root.text
        }
    }

    NumberAnimation {
        id: resize
        target: root
        property: "freeWidth"
        duration: Motion.stateChange
        easing.type: Motion.powerThreeInOut
    }

    Text {
        id: measure
        visible: false
        font.family: Theme.mono
        font.pixelSize: Theme.labelSize
        font.letterSpacing: Theme.labelSpacing
        font.bold: true
    }

    Glass {
        anchors.fill: parent
        visible: !root.isFilled
    }

    Rectangle {
        anchors.fill: parent
        radius: height / 2
        color: root.isFilled ? root.shownAccent : "transparent"
        Behavior on color { ColorAnimation { duration: Motion.stateChange } }
    }

    Item {
        anchors.fill: parent
        anchors.leftMargin: root.horizontalPadding
        anchors.rightMargin: root.horizontalPadding
        clip: true

        Typewriter {
            anchors.centerIn: parent.width >= implicitWidth ? parent : undefined
            anchors.verticalCenter: parent.verticalCenter
            text: root.stage
            color: root.isFilled ? Theme.sunkenDeep : root.shownAccent
            isBold: true
            // A caret would widen text whose pill is sized to it, so only a fixed-width label shows one.
            isCaretShown: root.fixedWidth > 0
        }
    }
}
