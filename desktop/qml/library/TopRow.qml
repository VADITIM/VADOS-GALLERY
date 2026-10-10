import QtQuick
import Gallery

// The top row (TopRow.kt): back and the month pill at the left, settings at the right; while selecting, the count and the way out instead.
// Buttons that come and go pop in place; the back button takes its room in two steps, the month pill sliding to make it (VAS components/24 §3).
Item {
    id: root

    required property var gallery
    readonly property bool isSelecting: gallery.selection.isSelectingPhotos || gallery.selection.isSelectingCovers
    readonly property real buttonHeight: 42 * Theme.dp

    implicitHeight: buttonHeight

    // #region ── left: back and the month ─────────────────────────────────────────────────────
    property real backRoom: gallery.canGoBack && !isSelecting ? 1 : 0
    property bool isBackShown: gallery.canGoBack && !isSelecting
    onIsBackShownChanged: {
        if (isBackShown) {
            backLeave.stop()
            backArrive.restart()
        } else {
            backArrive.stop()
            backLeave.restart()
        }
    }
    SequentialAnimation {
        id: backArrive
        NumberAnimation { target: root; property: "backRoom"; to: 1; duration: Motion.stateChange; easing.type: Motion.powerThreeInOut }
        ScriptAction { script: back.isShown = true }
    }
    SequentialAnimation {
        id: backLeave
        ScriptAction { script: back.isShown = false }
        PauseAnimation { duration: Motion.stateChange }
        NumberAnimation { target: root; property: "backRoom"; to: 0; duration: Motion.stateChange; easing.type: Motion.powerThreeInOut }
    }

    Pop {
        id: back
        isShown: root.isBackShown
        anchors.verticalCenter: parent.verticalCenter
        TopButton {
            glyph: "back"
            onClicked: root.gallery.goBack()
        }
    }

    // A fixed width, so the buttons beside it never move; filled with the section colour while it names the folder, the glass pill with the month in the section colour otherwise.
    Pop {
        id: month
        isShown: !root.isSelecting && pillText.length > 0
        delay: Motion.stateChange
        x: root.backRoom * (50 * Theme.dp + 8 * Theme.dp)
        anchors.verticalCenter: parent.verticalCenter
        readonly property string pillText: root.gallery.topPillText

        TypedLabel {
            text: month.pillText.toUpperCase()
            isFilled: root.gallery.isTopPillFilled
            fixedWidth: 168 * Theme.dp
            height: root.buttonHeight
            isTypedIn: true
        }
    }

    // Selecting swaps the row: the count and the way out of the selection.
    Pop {
        isShown: root.isSelecting
        delay: Motion.stateChange
        anchors.verticalCenter: parent.verticalCenter

        Row {
            spacing: 8 * Theme.dp

            TopButton {
                glyph: "close"
                onClicked: root.gallery.selection.clear()
            }

            Item {
                width: countText.implicitWidth + 28 * Theme.dp
                height: root.buttonHeight
                Glass { anchors.fill: parent }
                Text {
                    id: countText
                    anchors.centerIn: parent
                    text: root.gallery.selection.photoCount + root.gallery.selection.coverCount
                    color: Theme.accent
                    font.family: Theme.mono
                    font.pixelSize: Theme.labelSize * 1.2
                    font.letterSpacing: Theme.labelSpacing * 0.5
                    font.bold: true
                }
            }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── right: settings ───────────────────────────────────────────────────────────────
    Row {
        anchors.right: parent.right
        anchors.verticalCenter: parent.verticalCenter
        spacing: 8 * Theme.dp

        Pop {
            isShown: !root.isSelecting && root.gallery.canAddPhotos
            delay: Motion.stateChange
            TopButton {
                glyph: "plus"
                onClicked: root.gallery.openPicker()
            }
        }

        Pop {
            id: settingsButton
            isShown: !root.isSelecting && !root.gallery.isSettingsOpen
            delay: root.gallery.isSettingsOpen ? 0 : Motion.stateChange
            TopButton {
                id: gear
                glyph: "settings"
                onClicked: root.gallery.openSettings(gear)
            }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────
}
