import QtQuick
import QtMultimedia
import Gallery

// A motion photo's clip, played over its still on a loop while the photo is held, with sound unless video sound is off; letting go returns to the still.
// Built only when Qt Multimedia is installed.
Item {
    id: root

    property string path: ""
    property bool isMuted: false

    MediaPlayer {
        id: player
        videoOutput: output
        audioOutput: AudioOutput { muted: root.isMuted }
        loops: MediaPlayer.Infinite
    }

    VideoOutput {
        id: output
        anchors.fill: parent
        fillMode: VideoOutput.PreserveAspectCrop
    }

    Component.onCompleted: System.playClip(player, root.path)
    Component.onDestruction: player.stop()
}
