import QtQuick
import Gallery

// The viewer's own pieces over the photo: back at the top left, the date pill at the top, and the details rising from below with a swipe up.
// They are not part of the photo: each leaves off its own edge, one after another, and comes back the same way once the photo has settled (VAS components/16 §5).
Item {
    id: chrome

    required property var viewer
    required property var gallery
    // How far the buttons have left with a pull, gone by 0.35 of it.
    readonly property real pullShare: Motion.clamp(viewer.pull / Motion.chromePullShare, 0, 1)

    visible: viewer.isOpen

    component Piece: Item {
        id: piece
        property int order: 0
        property real shown: chrome.viewer.isChromeVisible ? 1 : 0
        readonly property real presence: shown * (1 - chrome.pullShare)
        Behavior on shown {
            SequentialAnimation {
                PauseAnimation { duration: piece.order * Motion.chromeStagger }
                NumberAnimation {
                    duration: chrome.viewer.isChromeVisible ? Motion.overlayEnter : Motion.overlayLeave
                    easing.type: chrome.viewer.isChromeVisible ? Motion.backOut : Motion.powerTwoIn
                }
            }
        }
    }

    // #region ── back ──────────────────────────────────────────────────────────────────────────
    Piece {
        id: backPiece
        order: 0
        x: 12 * Theme.dp
        y: 10 * Theme.dp - (1 - presence) * 42 * Theme.dp * 1.6
        width: 50 * Theme.dp
        height: 42 * Theme.dp
        opacity: Math.min(1, presence * 1.5)

        TopButton {
            glyph: "back"
            isSolid: chrome.viewer.isSettled
            onClicked: chrome.viewer.close()
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── the date pill ─────────────────────────────────────────────────────────────────
    // It pops in as a circle, widens from its centre to both sides, and then the date types itself in; leaving runs the same steps back.
    Item {
        id: datePill
        readonly property bool isWanted: chrome.viewer.isChromeVisible
        readonly property string dateText: chrome.viewer.facts.timestamp ? System.formatDate(chrome.viewer.facts.timestamp) : ""
        property real pop: 0
        property real widen: 0
        property string typed: ""
        readonly property real fullWidth: measure.implicitWidth + 28 * Theme.dp
        width: height + (fullWidth - height) * widen
        height: 34 * Theme.dp
        x: (chrome.width - width) / 2
        y: 14 * Theme.dp - chrome.pullShare * 60 * Theme.dp
        opacity: 1 - chrome.pullShare
        visible: pop > 0
        transform: Scale { origin.x: datePill.width / 2; origin.y: datePill.height / 2; xScale: datePill.pop; yScale: datePill.pop }

        onIsWantedChanged: isWanted ? (dateLeave.stop(), dateArrive.restart()) : (dateArrive.stop(), dateLeave.restart())
        onDateTextChanged: if (isWanted && !dateArrive.running) typed = dateText

        SequentialAnimation {
            id: dateArrive
            NumberAnimation { target: datePill; property: "pop"; to: 1; duration: Motion.stateChange; easing.type: Motion.backOut }
            NumberAnimation { target: datePill; property: "widen"; to: 1; duration: Motion.stateChange; easing.type: Motion.powerThreeInOut }
            ScriptAction { script: datePill.typed = datePill.dateText }
        }
        SequentialAnimation {
            id: dateLeave
            ScriptAction { script: datePill.typed = "" }
            PauseAnimation { duration: Math.min(Motion.overlayLeave, datePill.dateText.length * Motion.untypeLetter) }
            NumberAnimation { target: datePill; property: "widen"; to: 0; duration: Motion.overlayLeave; easing.type: Motion.powerThreeInOut }
            NumberAnimation { target: datePill; property: "pop"; to: 0; duration: Motion.overlayLeave; easing.type: Motion.backIn }
        }

        Glass {
            anchors.fill: parent
            isSolid: chrome.viewer.isSettled
        }

        Text {
            id: measure
            visible: false
            text: datePill.dateText
            font.family: Theme.mono
            font.pixelSize: Theme.labelSize
            font.letterSpacing: Theme.labelSpacing * 0.5
        }

        // A motion photo carries its mark beside the date.
        Glyph {
            anchors.left: parent.right
            anchors.leftMargin: 8 * Theme.dp
            anchors.verticalCenter: parent.verticalCenter
            width: 20 * Theme.dp
            height: width
            name: "motion"
            ink: chrome.viewer.isMotionHeld ? Theme.accent : Theme.textBright
            visible: chrome.viewer.facts.isMotion === true && datePill.widen >= 1
        }

        Item {
            anchors.fill: parent
            anchors.leftMargin: 14 * Theme.dp
            anchors.rightMargin: 14 * Theme.dp
            clip: true
            Typewriter {
                anchors.centerIn: parent
                text: datePill.typed
                color: Theme.textBright
                letterSpacing: Theme.labelSpacing * 0.5
                isCaretShown: false
            }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── details ───────────────────────────────────────────────────────────────────────
    // Name, date, resolution, size and folder, raised by a swipe up and lowered with the finger.
    Item {
        id: details
        readonly property var facts: chrome.viewer.facts
        readonly property var file: (chrome.viewer.items.revision >= 0 && System.details(chrome.viewer.currentPath))
        readonly property var place: (chrome.viewer.items.revision >= 0 && Library.placeOf(chrome.viewer.currentPath))
        width: Math.min(chrome.width - 32 * Theme.dp, 560 * Theme.dp)
        height: rows.implicitHeight + 40 * Theme.dp
        x: (chrome.width - width) / 2
        y: chrome.height - chrome.viewer.lift * (height + 24 * Theme.dp)
        visible: chrome.viewer.lift > 0

        Glass {
            anchors.fill: parent
            radius: Theme.sheetRadius
        }

        Column {
            id: rows
            x: 24 * Theme.dp
            y: 20 * Theme.dp
            width: parent.width - 48 * Theme.dp
            spacing: 10 * Theme.dp

            Text {
                text: "DETAILS"
                color: Theme.accent
                font.family: Theme.mono
                font.pixelSize: Theme.labelSize
                font.letterSpacing: Theme.labelSpacing
            }

            Repeater {
                model: [
                    { label: "NAME", value: details.file.name ?? "" },
                    { label: "DATE", value: details.facts.timestamp ? System.formatDate(details.facts.timestamp) : "" },
                    { label: "RESOLUTION", value: details.facts.width > 0 ? details.facts.width + " × " + details.facts.height : "" },
                    { label: "SIZE", value: details.file.size ?? "" },
                    { label: "LENGTH", value: details.facts.isVideo && details.facts.durationMs > 0 ? System.formatDuration(details.facts.durationMs) : "" },
                    { label: "FOLDER", value: details.file.folder ?? "" },
                    { label: "PLACE", value: [details.place.city ?? "", details.place.country ?? ""].filter(part => part.length > 0).join(", ") },
                    { label: "COORDINATES", value: details.place.latitude !== undefined ? details.place.latitude.toFixed(5) + ", " + details.place.longitude.toFixed(5) : "" },
                ]

                Item {
                    required property var modelData
                    visible: modelData.value.length > 0
                    width: rows.width
                    height: visible ? Math.max(label.implicitHeight, value.implicitHeight) : 0

                    Text {
                        id: label
                        text: parent.modelData.label
                        color: Theme.textLabel
                        font.family: Theme.mono
                        font.pixelSize: Theme.labelSize
                        font.letterSpacing: Theme.labelSpacing * 0.6
                    }
                    Text {
                        id: value
                        x: 120 * Theme.dp
                        width: parent.width - x
                        text: parent.modelData.value
                        color: Theme.accent
                        font.family: Theme.mono
                        font.pixelSize: Theme.valueSize
                        wrapMode: Text.WrapAnywhere
                    }
                }
            }

            // The file as VAD/OS Files sees it: the folder opens there with this photo picked.
            Pressable {
                visible: !chrome.viewer.isPrivate
                width: showRow.implicitWidth + 28 * Theme.dp
                height: 36 * Theme.dp
                onClicked: System.showInFolder([chrome.viewer.currentPath])
                Rectangle {
                    anchors.fill: parent
                    radius: height / 2
                    color: "transparent"
                    border.width: 1.5 * Theme.dp
                    border.color: Theme.borderControl
                }
                Row {
                    id: showRow
                    anchors.centerIn: parent
                    spacing: 8 * Theme.dp
                    Glyph { width: 18 * Theme.dp; height: width; name: "folder"; ink: Theme.textBody; anchors.verticalCenter: parent.verticalCenter }
                    Text {
                        anchors.verticalCenter: parent.verticalCenter
                        text: "SHOW IN FILES"
                        color: Theme.textBody
                        font.family: Theme.mono
                        font.pixelSize: Theme.labelSize
                        font.letterSpacing: Theme.labelSpacing * 0.6
                    }
                }
            }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────
}
