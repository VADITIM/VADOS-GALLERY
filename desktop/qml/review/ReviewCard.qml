import QtQuick
import Gallery

// One review card: the photo filling the screen on black, tinted by the verdict it is heading for, and marked when it was let go before.
Item {
    id: card

    property string path: ""
    property color tint: Theme.accent
    property real tintStrength: 0
    property bool isMarked: false

    Image {
        id: thumbnail
        anchors.fill: parent
        fillMode: Image.PreserveAspectFit
        asynchronous: true
        source: card.path.length > 0 ? "image://thumbnail/" + encodeURIComponent(card.path) : ""
        sourceSize: Qt.size(512, 512)
        visible: picture.status !== Image.Ready
    }

    Image {
        id: picture
        anchors.fill: parent
        fillMode: Image.PreserveAspectFit
        asynchronous: true
        autoTransform: true
        source: card.path.length > 0 && !card.path.match(/\.(mp4|mov|m4v|mkv|webm|avi|3gp)$/i) ? System.fileUrl(card.path) : ""
        sourceSize: Qt.size(Math.ceil(Screen.width * Screen.devicePixelRatio), Math.ceil(Screen.height * Screen.devicePixelRatio))
    }

    Rectangle {
        anchors.centerIn: parent
        width: picture.status === Image.Ready ? picture.paintedWidth : thumbnail.paintedWidth
        height: picture.status === Image.Ready ? picture.paintedHeight : thumbnail.paintedHeight
        color: card.tint
        opacity: card.tintStrength
    }

    Rectangle {
        visible: card.isMarked
        anchors.top: parent.top
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.topMargin: 16 * Theme.dp
        width: markRow.implicitWidth + 24 * Theme.dp
        height: 30 * Theme.dp
        radius: height / 2
        color: Theme.danger
        Row {
            id: markRow
            anchors.centerIn: parent
            spacing: 6 * Theme.dp
            Glyph { anchors.verticalCenter: parent.verticalCenter; width: 14 * Theme.dp; height: width; name: "trash"; ink: Theme.sunkenDeep }
            Text {
                anchors.verticalCenter: parent.verticalCenter
                text: "MARKED"
                color: Theme.sunkenDeep
                font.family: Theme.mono
                font.pixelSize: Theme.labelSize
                font.letterSpacing: Theme.labelSpacing * 0.6
            }
        }
    }
}
