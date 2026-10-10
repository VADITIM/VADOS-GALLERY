import QtQuick
import Gallery

// The colour of a piece that comes and goes: arriving it already wears the new accent, leaving it keeps the one it had, and staying it changes on the cut with everything else (VAS components/19 §5).
QtObject {
    property bool isShown: false
    property color color: Theme.accentTarget
    property bool wasShown: false

    function follow() {
        if (isShown && !wasShown)
            color = Theme.accentTarget
        else if (isShown && Qt.colorEqual(Theme.accent, Theme.accentTarget))
            color = Theme.accent
        wasShown = isShown
    }

    onIsShownChanged: follow()
    property Connections watcher: Connections {
        target: Theme
        function onAccentChanged() { follow() }
        function onAccentTargetChanged() { follow() }
    }
    Component.onCompleted: {
        color = Theme.accentTarget
        wasShown = isShown
    }
}
