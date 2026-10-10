import QtQuick
import QtMultimedia
import Gallery

// A video playing inline in the viewer (VideoPlayer.kt): it plays on open when autoplay is on, with one slim bar over it: play, the time, the timeline, the length, loop and sound.
// Sound off holds for every video while the app runs. Built only when Qt Multimedia is installed; without it a video opens in the system's player.
Item {
    id: root

    property string path: ""
    property var viewer: null
    property bool isLooping: true
    readonly property real position: player.position
    readonly property bool isMuted: viewer ? viewer.gallery.isVideoMuted : false

    MediaPlayer {
        id: player
        source: root.path.length > 0 ? System.fileUrl(root.path) : ""
        videoOutput: output
        audioOutput: AudioOutput { muted: root.isMuted }
        loops: root.isLooping ? MediaPlayer.Infinite : 1
        onSourceChanged: if (Settings.isAutoplay) play()
    }

    VideoOutput {
        id: output
        anchors.fill: parent
        fillMode: VideoOutput.PreserveAspectFit
    }

    Component.onCompleted: if (Settings.isAutoplay) player.play()

    function toggle() {
        player.playbackState === MediaPlayer.PlayingState ? player.pause() : player.play()
    }

    Shortcut {
        sequence: "Space"
        enabled: root.viewer && root.viewer.isSettled
        onActivated: root.toggle()
    }

    // The slim bar, above the viewer's buttons, coming and going with them.
    Item {
        id: bar
        anchors.horizontalCenter: parent.horizontalCenter
        y: Math.min(parent.height, (root.viewer ? root.viewer.height : parent.height) - parent.y) - 140 * Theme.dp
        width: Math.min(parent.width - 32 * Theme.dp, 640 * Theme.dp)
        height: 44 * Theme.dp
        opacity: root.viewer && root.viewer.isChromeVisible ? 1 : 0
        visible: opacity > 0
        Behavior on opacity { NumberAnimation { duration: Motion.stateChange } }

        Glass {
            anchors.fill: parent
            isSolid: true
        }

        Row {
            anchors.fill: parent
            anchors.leftMargin: 6 * Theme.dp
            anchors.rightMargin: 6 * Theme.dp
            spacing: 6 * Theme.dp

            IconButton {
                anchors.verticalCenter: parent.verticalCenter
                width: 40 * Theme.dp
                height: 40 * Theme.dp
                glyph: player.playbackState === MediaPlayer.PlayingState ? "pause" : "play"
                glyphSize: 18 * Theme.dp
                onClicked: root.toggle()
            }

            Text {
                anchors.verticalCenter: parent.verticalCenter
                text: System.formatDuration(player.position, scrub.isFine)
                color: Theme.textBright
                font.family: Theme.mono
                font.pixelSize: Theme.labelSize
            }

            // Held and slid up, the scrub gets finer: half, a quarter, then a tenth of the pointer's speed.
            Item {
                id: scrub
                property bool isFine: false
                anchors.verticalCenter: parent.verticalCenter
                width: parent.width - x - lengthText.width - 2 * 40 * Theme.dp - 3 * parent.spacing - 6 * Theme.dp
                height: 30 * Theme.dp

                Rectangle {
                    anchors.verticalCenter: parent.verticalCenter
                    width: parent.width
                    height: (scrubArea.pressed ? 8 : 4) * Theme.dp
                    radius: height / 2
                    color: Theme.borderStrong
                    Behavior on height { NumberAnimation { duration: Motion.stateChange; easing.type: Motion.powerTwoOut } }
                    Rectangle {
                        width: parent.width * (player.duration > 0 ? player.position / player.duration : 0)
                        height: parent.height
                        radius: height / 2
                        color: Theme.accent
                    }
                }

                MouseArea {
                    id: scrubArea
                    anchors.fill: parent
                    property real lastX: 0
                    property real pressY: 0
                    property real at: 0
                    onPressed: mouse => {
                        lastX = mouse.x
                        pressY = mouse.y
                        at = mouse.x / width * player.duration
                        player.setPosition(at)
                    }
                    onPositionChanged: mouse => {
                        const lift = pressY - mouse.y
                        const speed = lift > 120 * Theme.dp ? 0.1 : lift > 80 * Theme.dp ? 0.25 : lift > 40 * Theme.dp ? 0.5 : 1
                        scrub.isFine = speed < 1
                        at = Math.max(0, Math.min(player.duration, at + (mouse.x - lastX) / width * player.duration * speed))
                        lastX = mouse.x
                        player.setPosition(at)
                    }
                    onReleased: scrub.isFine = false
                }
            }

            Text {
                id: lengthText
                anchors.verticalCenter: parent.verticalCenter
                text: System.formatDuration(player.duration)
                color: Theme.textMuted
                font.family: Theme.mono
                font.pixelSize: Theme.labelSize
            }

            IconButton {
                anchors.verticalCenter: parent.verticalCenter
                width: 40 * Theme.dp
                height: 40 * Theme.dp
                glyph: "loop"
                glyphSize: 18 * Theme.dp
                ink: root.isLooping ? Theme.accent : Theme.textMuted
                onClicked: root.isLooping = !root.isLooping
            }

            IconButton {
                anchors.verticalCenter: parent.verticalCenter
                width: 40 * Theme.dp
                height: 40 * Theme.dp
                glyph: root.isMuted ? "speaker-muted" : "speaker"
                glyphSize: 18 * Theme.dp
                onClicked: if (root.viewer) root.viewer.gallery.isVideoMuted = !root.viewer.gallery.isVideoMuted
            }
        }
    }
}
