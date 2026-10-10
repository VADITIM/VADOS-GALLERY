import QtQuick
import Gallery

// A picture cropped to cover its box inside the squircle. `status` follows the underlying Image so the caller can fall back to a glyph.
Item {
    id: root

    property alias source: picture.source
    property alias sourceSize: picture.sourceSize
    property alias asynchronous: picture.asynchronous
    readonly property alias status: picture.status
    property real radius: Theme.coverRadius
    property color borderColor: "transparent"
    property real borderWidth: 0
    property color insetColor: Theme.accent
    property real insetWidth: 0

    Image {
        id: picture
        visible: false
        asynchronous: true
        cache: true
        smooth: true
        mipmap: true
    }

    ShaderEffect {
        anchors.fill: parent
        visible: picture.status === Image.Ready
        property variant source: picture
        readonly property size itemSize: Qt.size(width, height)
        readonly property size sourceSize: Qt.size(picture.implicitWidth, picture.implicitHeight)
        property real radius: root.radius
        property real exponent: 4
        property real borderWidth: root.borderWidth
        property real insetWidth: root.insetWidth
        property color borderColor: root.borderColor
        property color insetColor: root.insetColor
        fragmentShader: "qrc:/shaders/squircleImage.frag.qsb"
    }
}
