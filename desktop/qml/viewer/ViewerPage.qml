import QtQuick
import Gallery

// One photo in the viewer, in its own frame: the cached thumbnail at once, so it never opens on black, and the photo itself drawn over it once decoded;
// the full resolution (up to 4096 a side) once it is zoomed in (docs/SPEC.md, Photo quality in the viewer).
Item {
    id: page

    property string path: ""
    property bool isVideo: false
    property bool isNear: true
    property real cornerRadius: 0
    property real zoom: 1
    property real panX: 0
    property real panY: 0
    readonly property bool isZoomed: zoom > 1.001
    readonly property bool isReady: thumbnail.status === Image.Ready || picture.status === Image.Ready
    readonly property size naturalSize: picture.status === Image.Ready ? Qt.size(picture.implicitWidth, picture.implicitHeight)
                                                                     : Qt.size(thumbnail.implicitWidth, thumbnail.implicitHeight)

    Image {
        id: thumbnail
        visible: false
        asynchronous: true
        cache: true
        source: page.path.length > 0 ? "image://thumbnail/" + encodeURIComponent(page.path) : ""
        sourceSize: Qt.size(512, 512)
    }

    Image {
        id: picture
        visible: false
        asynchronous: true
        cache: false
        autoTransform: true
        smooth: true
        mipmap: true
        source: page.isNear && !page.isVideo && page.path.length > 0 ? System.fileUrl(page.path) : ""
        // Screen-sized while it fits; the whole picture once zoomed.
        sourceSize: page.isZoomed ? Qt.size(4096, 4096) : Qt.size(Math.ceil(Screen.width * Screen.devicePixelRatio), Math.ceil(Screen.height * Screen.devicePixelRatio))
    }

    Item {
        id: frame
        anchors.fill: parent
        transform: [
            Scale { origin.x: frame.width / 2; origin.y: frame.height / 2; xScale: page.zoom; yScale: page.zoom },
            Translate { x: page.panX; y: page.panY }
        ]

        ShaderEffect {
            anchors.fill: parent
            visible: page.isReady
            property variant source: picture.status === Image.Ready ? picture : thumbnail
            readonly property size itemSize: Qt.size(width, height)
            readonly property size sourceSize: picture.status === Image.Ready ? Qt.size(picture.implicitWidth, picture.implicitHeight) : Qt.size(thumbnail.implicitWidth, thumbnail.implicitHeight)
            property real radius: page.cornerRadius
            property real exponent: 2
            property real borderWidth: 0
            property real insetWidth: 0
            property color borderColor: "transparent"
            property color insetColor: "transparent"
            fragmentShader: "qrc:/shaders/squircleImage.frag.qsb"
        }
    }
}
