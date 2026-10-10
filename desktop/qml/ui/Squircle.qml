import QtQuick
import Gallery

// The surface primitive every box in the app is drawn with: fill, one hairline, one squircle radius, and the hue ring (VAS surface).
ShaderEffect {
    id: surface

    property real radius: Theme.panelRadius
    property real exponent: 4
    property color fillColor: Theme.panel
    property color borderColor: "transparent"
    property real borderWidth: 0
    property color insetColor: Theme.accent
    property real insetWidth: 0
    property color glowColor: Theme.accent
    property real glowOpacity: 0
    property point glowPosition: Qt.point(width / 2, height / 2)
    property real glowRadius: Theme.rem * 11
    readonly property size itemSize: Qt.size(width, height)

    fragmentShader: "qrc:/shaders/squircle.frag.qsb"
}
