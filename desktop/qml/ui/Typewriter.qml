import QtQuick
import Gallery

// Text that, when it changes, deletes back to what the old and new share and types the rest in, with a terminal's block caret blinking while it runs (VAS components/09, as in VADOS Bubble).
Row {
    id: root

    property string text: ""
    property color color: Theme.textBright
    property string fontFamily: Theme.mono
    property real fontSize: Theme.labelSize
    property real letterSpacing: Theme.labelSpacing
    property bool isBold: false
    property bool isCaretShown: true
    property bool isTypedIn: false
    property bool hasShadow: false
    // Where the typing is; the label around it reads this to time its own resize.
    readonly property bool isTyping: shown !== text
    property string shown: isTypedIn ? "" : text
    property bool isCaretOn: true

    spacing: fontSize * 0.12

    onTextChanged: step.restart()
    Component.onCompleted: if (shown !== text) step.restart()

    Timer {
        id: step
        interval: root.shown.length > 0 && !root.text.startsWith(root.shown) ? Motion.untypeLetter : Motion.typeLetter
        repeat: true
        onTriggered: {
            if (root.shown === root.text) {
                stop()
                return
            }
            if (root.shown.length > 0 && !root.text.startsWith(root.shown))
                root.shown = root.shown.slice(0, -1)
            else
                root.shown = root.text.slice(0, root.shown.length + 1)
        }
    }

    Timer {
        interval: Motion.caretBlink
        repeat: true
        running: root.isTyping
        onRunningChanged: root.isCaretOn = true
        onTriggered: root.isCaretOn = !root.isCaretOn
    }

    Text {
        id: words
        anchors.verticalCenter: parent.verticalCenter
        text: root.shown
        color: root.color
        font.family: root.fontFamily
        font.pixelSize: root.fontSize
        font.letterSpacing: root.letterSpacing
        font.bold: root.isBold
        textFormat: Text.PlainText
        style: root.hasShadow ? Text.Raised : Text.Normal
        styleColor: Qt.rgba(0, 0, 0, 0.6)
    }

    // The block caret of a terminal, in the text's own colour.
    Rectangle {
        anchors.verticalCenter: parent.verticalCenter
        visible: root.isTyping && root.isCaretShown
        width: root.fontSize * 0.42
        height: root.fontSize
        color: root.color
        opacity: root.isCaretOn ? 1 : 0
    }
}
