import QtQuick
import QtQuick.Window
import Gallery

// The window: frameless under Hyprland, the ground set once at the root. Started by D-Bus activation it waits hidden until a photo or the gallery is asked for.
Window {
    id: window

    width: Settings.windowWidth
    height: Settings.windowHeight
    minimumWidth: 640
    minimumHeight: 480
    visible: !Service.isStartedHidden
    title: gallery.viewer.isRequested ? gallery.viewer.currentPath.split("/").pop() + " — VAD/OS Gallery" : "VAD/OS Gallery"
    color: gallery.viewer.isOpen && gallery.viewer.growth >= 1 ? Theme.viewerGround : Theme.ground

    onWidthChanged: saveTimer.restart()
    onHeightChanged: saveTimer.restart()
    onScreenChanged: Theme.screenWidth = screen.width

    Component.onCompleted: {
        Theme.screenWidth = screen.width
        const pending = Service.takePendingOpen()
        if (pending.length > 0) {
            gallery.isExternalSession = true
            gallery.openExternal(pending, [])
        }
    }

    Timer {
        id: saveTimer
        interval: 500
        onTriggered: {
            Settings.windowWidth = window.width
            Settings.windowHeight = window.height
        }
    }

    Connections {
        target: Service
        // A photo handed over by VAD/OS Files (with what it was listed between) or a second launch.
        function onOpenRequested(path, siblings) {
            if (!window.visible)
                gallery.isExternalSession = true
            window.show()
            window.raise()
            window.requestActivate()
            gallery.openExternal(path, siblings)
        }
        function onShowRequested() {
            gallery.isExternalSession = false
            window.show()
            window.raise()
            window.requestActivate()
        }
    }

    Gallery {
        id: gallery
        objectName: "gallery"
        anchors.fill: parent
        // Opened only to show a photo, closing it puts the window away until the next one; the process stays, so the next photo opens at once.
        onExternalClosed: {
            gallery.isExternalSession = false
            window.hide()
        }
    }
}
