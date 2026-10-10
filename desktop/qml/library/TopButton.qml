import QtQuick
import Gallery

// The round glass button of the top row; the viewer's and the crop screen's back are this same button.
Pressable {
    id: root

    property string glyph: ""
    property color ink: Theme.textBody
    property bool isSolid: false

    width: 50 * Theme.dp
    height: 42 * Theme.dp

    Glass {
        anchors.fill: parent
        isSolid: root.isSolid
    }

    Glyph {
        anchors.centerIn: parent
        name: root.glyph
        ink: root.isHovered ? Theme.textBright : root.ink
    }
}
