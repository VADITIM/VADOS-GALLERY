import QtQuick
import Gallery

// Text that does not fit fades out on the edge it overflows — never sliced, never ellipsised (VAS typography). Wraps to `maximumLineCount` first where the box can grow.
Text {
    id: root

    property bool isMirrored: horizontalAlignment === Text.AlignRight
    readonly property bool isOverflowing: maximumLineCount > 1 ? truncated : contentWidth > width + 0.5

    maximumLineCount: 1
    font.family: Theme.mono
    font.pixelSize: Theme.valueSize
    color: Theme.textBody
    elide: Text.ElideNone
    wrapMode: maximumLineCount > 1 ? Text.WrapAtWordBoundaryOrAnywhere : Text.NoWrap
    clip: isOverflowing
    textFormat: Text.PlainText

    layer.enabled: isOverflowing
    layer.effect: ShaderEffect {
        property variant source
        readonly property size itemSize: Qt.size(width, height)
        readonly property real fadeWidth: 28 * Theme.dp
        readonly property real fadeTop: root.lineCount > 1 ? root.height * (root.lineCount - 1) / root.lineCount : 0
        readonly property real isMirrored: root.isMirrored ? 1 : 0
        fragmentShader: "qrc:/shaders/fadeEdge.frag.qsb"
    }
}
