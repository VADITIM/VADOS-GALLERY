import QtQuick
import QtQuick.Effects
import Gallery

// Floating things are glass with no border (docs/DESIGN.md § Surface): the scene behind blurred and darkened by a black veil, both set in Settings.
// Over photographs a hairline reads as a frame around a hole; a blur reads as a pane. `isSolid` swaps the blur for the viewer's black over a settled photo.
Item {
    id: root

    property real radius: height / 2
    property real exponent: radius >= height / 2 - 0.5 ? 2 : 4
    property bool isSolid: false
    property color solidColor: Theme.viewerGround
    property Item backdrop: GlassSource.item
    readonly property real blurRadius: Math.min(64, Settings.blur * Theme.dp)
    // The blur reaches past the pane's edges, so it is captured with a margin and the edges do not darken.
    readonly property real margin: blurRadius
    property point origin: Qt.point(0, 0)

    // The pane moves with whatever carries it (a bar sliding off, a sheet rising), so its place in the scene is read every frame it shows.
    FrameAnimation {
        running: root.visible && root.opacity > 0 && root.backdrop !== null && !root.isSolid
        onTriggered: {
            const at = root.backdrop.mapFromItem(root, 0, 0)
            if (at.x !== root.origin.x || at.y !== root.origin.y)
                root.origin = at
        }
    }

    ShaderEffectSource {
        id: capture
        visible: false
        sourceItem: root.isSolid ? null : root.backdrop
        sourceRect: Qt.rect(root.origin.x - root.margin, root.origin.y - root.margin, root.width + root.margin * 2, root.height + root.margin * 2)
        textureSize: Qt.size(Math.max(1, Math.ceil((root.width + root.margin * 2) / 2)), Math.max(1, Math.ceil((root.height + root.margin * 2) / 2)))
        live: true
        recursive: false
        smooth: true
    }

    Item {
        id: mask
        visible: false
        layer.enabled: true
        width: root.width + root.margin * 2
        height: root.height + root.margin * 2

        Squircle {
            x: root.margin
            y: root.margin
            width: root.width
            height: root.height
            radius: root.radius
            exponent: root.exponent
            fillColor: "white"
        }
    }

    MultiEffect {
        visible: !root.isSolid
        x: -root.margin
        y: -root.margin
        width: root.width + root.margin * 2
        height: root.height + root.margin * 2
        source: capture
        autoPaddingEnabled: false
        blurEnabled: true
        blur: 1
        blurMax: Math.max(1, Math.round(root.blurRadius / 2))
        blurMultiplier: 0.5
        maskEnabled: true
        maskSource: mask
    }

    Squircle {
        anchors.fill: parent
        radius: root.radius
        exponent: root.exponent
        fillColor: root.isSolid ? root.solidColor : Qt.rgba(0, 0, 0, Settings.glassOpacity)
    }
}
