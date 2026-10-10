import QtQuick
import Gallery

// A line glyph re-inked on the GPU. A glyph never names a colour; the caller hands it the ink it inherited.
Item {
    id: root

    property string name: "file"
    property color ink: Theme.textIcon
    implicitWidth: 22 * Theme.dp
    implicitHeight: 22 * Theme.dp

    Image {
        id: mask
        visible: false
        source: root.name.length > 0 ? "image://glyph/" + root.name + "?ink=ffffff" : ""
        sourceSize: Qt.size(Math.ceil(root.width * Screen.devicePixelRatio), Math.ceil(root.height * Screen.devicePixelRatio))
        smooth: true
    }

    ShaderEffect {
        anchors.fill: parent
        property variant source: mask
        property color ink: root.ink
        fragmentShader: "qrc:/shaders/glyphInk.frag.qsb"
    }
}
