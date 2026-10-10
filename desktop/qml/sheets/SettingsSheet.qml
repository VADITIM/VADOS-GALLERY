import QtQuick
import QtQuick.Dialogs
import Gallery

// The settings sheet (SettingsSheet.kt): it opens out of its button, growing from the gear's size to its own, its tabs, cards and foot rising in one after another.
// Three tabs: the place you are in (its own settings), General, Interface. Every group is a card of its own; a Done pill and V/AS with the version close the foot.
Item {
    id: root

    required property var gallery
    property bool isOpen: false
    property rect origin: Qt.rect(0, 0, 0, 0)
    property real growth: 0
    property real rise: 0
    property int tab: 0
    readonly property string view: gallery.settingsView
    readonly property string folderKey: gallery.settingsFolderKey
    readonly property bool hasPhotos: gallery.gridSource.length > 0
    readonly property bool isOwn: (Settings.revision, Settings.hasOwnSettings(folderKey))
    // Inside an album that follows Recent, its photo settings show greyed.
    readonly property bool isFollowing: folderKey.length > 0 && !isOwn

    anchors.fill: parent
    visible: growth > 0
    z: 90

    function open(button: var) {
        const at = button.mapToItem(root, 0, 0)
        origin = Qt.rect(at.x, at.y, button.width, button.height)
        tab = 0
        isOpen = true
        closing.stop()
        opening.restart()
    }
    function close() {
        if (!isOpen)
            return
        isOpen = false
        opening.stop()
        closing.restart()
    }

    SequentialAnimation {
        id: opening
        PauseAnimation { duration: Motion.morphDelay }
        ParallelAnimation {
            NumberAnimation { target: root; property: "growth"; from: 0; to: 1; duration: Motion.morph; easing.type: Motion.powerTwoOut }
            SequentialAnimation {
                PauseAnimation { duration: Motion.riseDelay }
                NumberAnimation { target: root; property: "rise"; from: 0; to: 1; duration: Motion.rise + Motion.riseStagger * 6; easing.type: Easing.Linear }
            }
        }
    }
    SequentialAnimation {
        id: closing
        ParallelAnimation {
            NumberAnimation { target: root; property: "growth"; to: 0; duration: Motion.overlayLeave; easing.type: Motion.powerTwoIn }
            NumberAnimation { target: root; property: "rise"; to: 0; duration: Motion.overlayLeave }
        }
    }

    // Each piece rises in on its own turn: the tabs, then each card, then the foot.
    function riseOf(order: int): real {
        const elapsed = rise * (Motion.rise + Motion.riseStagger * 6) - order * Motion.riseStagger
        return Motion.backOutAt(Motion.clamp(elapsed / Motion.rise, 0, 1))
    }

    Rectangle {
        anchors.fill: parent
        color: Qt.rgba(0, 0, 0, 0.45)
        opacity: root.growth
        MouseArea {
            anchors.fill: parent
            enabled: root.isOpen
            onClicked: root.close()
            onWheel: wheel => wheel.accepted = true
        }
    }

    Item {
        id: panel
        readonly property rect full: Qt.rect(Math.max(16 * Theme.dp, root.width - width0 - 16 * Theme.dp), 12 * Theme.dp, width0, root.height - 24 * Theme.dp)
        readonly property real width0: Math.min(root.width - 32 * Theme.dp, 540 * Theme.dp)
        x: root.origin.x + (full.x - root.origin.x) * root.growth
        y: root.origin.y + (full.y - root.origin.y) * root.growth
        width: root.origin.width + (full.width - root.origin.width) * root.growth
        height: root.origin.height + (full.height - root.origin.height) * root.growth

        Glass {
            anchors.fill: parent
            radius: Math.min(Theme.sheetRadius, height / 2)
        }

        MouseArea { anchors.fill: parent; onWheel: wheel => wheel.accepted = false }

        Item {
            anchors.fill: parent
            anchors.margins: 18 * Theme.dp
            clip: true
            opacity: root.growth >= 1 ? 1 : 0

            SegmentedTrack {
                id: tabs
                width: parent.width
                options: [root.gallery.settingsViewLabel, "General", "Interface"]
                currentIndex: root.tab
                onPicked: index => root.tab = index
                isGlass: false
                opacity: root.riseOf(0)
                transform: Translate { y: (1 - root.riseOf(0)) * 24 * Theme.dp }
            }

            // The tab's pages side by side, following the tab pill while it is dragged and sliding when a tab is picked.
            Item {
                id: pages
                y: tabs.height + 14 * Theme.dp
                width: parent.width
                height: parent.height - y - foot.height - 10 * Theme.dp
                clip: true
                property real position: tabs.isDragging ? tabs.dragPosition : root.tab
                Behavior on position {
                    enabled: !tabs.isDragging
                    NumberAnimation { duration: Motion.navSlide; easing.type: Motion.powerTwoOut }
                }

                Repeater {
                    model: 3

                    Flickable {
                        id: page
                        required property int index
                        x: (index - pages.position) * pages.width
                        width: pages.width
                        height: pages.height
                        contentHeight: cards.implicitHeight
                        boundsBehavior: Flickable.StopAtBounds
                        clip: true
                        // The content fades at the sheet's sides while it slides.
                        opacity: 1 - Math.min(1, Math.abs(index - pages.position))

                        Column {
                            id: cards
                            width: page.width
                            spacing: 12 * Theme.dp

                            Loader {
                                width: parent.width
                                sourceComponent: page.index === 0 ? placeTab : page.index === 1 ? generalTab : interfaceTab
                            }
                        }

                        WheelHandler {
                            onWheel: event => {
                                const delta = event.pixelDelta.y !== 0 ? event.pixelDelta.y : event.angleDelta.y / 2
                                page.contentY = Math.max(0, Math.min(Math.max(0, page.contentHeight - page.height), page.contentY - delta))
                            }
                        }
                    }
                }
            }

            Item {
                id: foot
                anchors.bottom: parent.bottom
                width: parent.width
                height: 92 * Theme.dp
                opacity: root.riseOf(6)
                transform: Translate { y: (1 - root.riseOf(6)) * 24 * Theme.dp }

                // Filled with the section colour and white on it, so it is found at a glance.
                Pressable {
                    anchors.horizontalCenter: parent.horizontalCenter
                    width: 120 * Theme.dp
                    height: 44 * Theme.dp
                    onClicked: root.close()
                    Rectangle {
                        anchors.fill: parent
                        radius: height / 2
                        color: Theme.accent
                        Text {
                            anchors.centerIn: parent
                            text: "DONE"
                            color: "white"
                            font.family: Theme.mono
                            font.pixelSize: Theme.labelSize * 1.1
                            font.letterSpacing: Theme.labelSpacing
                            font.bold: true
                        }
                    }
                }

                Text {
                    anchors.left: parent.left
                    anchors.bottom: parent.bottom
                    text: "V/AS"
                    color: Theme.textFaint
                    font.family: Theme.heading
                    font.pixelSize: 14 * Theme.dp
                    opacity: 0.6
                }
                Text {
                    anchors.right: parent.right
                    anchors.bottom: parent.bottom
                    text: "V" + System.version
                    color: Theme.textFaint
                    font.family: Theme.heading
                    font.pixelSize: 14 * Theme.dp
                    opacity: 0.6
                }
            }
        }
    }

    // #region ── pieces ────────────────────────────────────────────────────────────────────────
    // A card: a darker fill, its name in the section colour at the top left, one spacing for every card, row and edge.
    component Card: Item {
        id: card
        property string title: ""
        property int order: 1
        default property alias rows: cardRows.data
        width: parent ? parent.width : 300
        implicitHeight: cardRows.implicitHeight + 50 * Theme.dp
        opacity: root.riseOf(order)
        transform: Translate { y: (1 - root.riseOf(card.order)) * 24 * Theme.dp }

        Squircle {
            anchors.fill: parent
            radius: Theme.panelRadius
            fillColor: Qt.rgba(0, 0, 0, 0.35)
            borderColor: Theme.border
            borderWidth: 1
        }
        Text {
            x: 18 * Theme.dp
            y: 14 * Theme.dp
            text: card.title.toUpperCase()
            color: Theme.accent
            font.family: Theme.mono
            font.pixelSize: Theme.labelSize
            font.letterSpacing: Theme.labelSpacing
        }
        Column {
            id: cardRows
            x: 18 * Theme.dp
            y: 38 * Theme.dp
            width: parent.width - 36 * Theme.dp
            spacing: 12 * Theme.dp
        }
    }

    component Name: Text {
        color: Theme.textBright
        font.family: Theme.mono
        font.pixelSize: Theme.valueSize
    }

    component SwitchRow: Item {
        id: switchRow
        property string label: ""
        property bool isOn: false
        property bool isEnabled: true
        signal toggled
        width: parent ? parent.width : 300
        height: 30 * Theme.dp
        opacity: isEnabled ? 1 : 0.38
        Name { anchors.verticalCenter: parent.verticalCenter; text: switchRow.label }
        Switch {
            anchors.right: parent.right
            anchors.verticalCenter: parent.verticalCenter
            isOn: switchRow.isOn
            isEnabled: switchRow.isEnabled
            onToggled: switchRow.toggled()
        }
    }

    component SliderRow: Column {
        id: sliderRow
        property string label: ""
        property string valueText: ""
        property real value: 0
        property real from: 0
        property real to: 1
        property real stepSize: 0
        signal moved(real value)
        width: parent ? parent.width : 300
        spacing: 4 * Theme.dp
        Item {
            width: parent.width
            height: 20 * Theme.dp
            Name { text: sliderRow.label }
            Text {
                anchors.right: parent.right
                text: sliderRow.valueText
                color: Theme.accent
                font.family: Theme.mono
                font.pixelSize: Theme.valueSize
            }
        }
        StepSlider {
            width: parent.width
            value: sliderRow.value
            from: sliderRow.from
            to: sliderRow.to
            stepSize: sliderRow.stepSize
            onMoved: value => sliderRow.moved(value)
        }
    }

    component Outlined: Pressable {
        id: outlined
        property string label: ""
        height: 40 * Theme.dp
        Rectangle {
            anchors.fill: parent
            radius: height / 2
            color: outlined.isHovered ? Theme.pressedWash : "transparent"
            border.width: 1.5 * Theme.dp
            border.color: Theme.borderControl
        }
        Text {
            anchors.centerIn: parent
            text: outlined.label
            color: Theme.textBright
            font.family: Theme.mono
            font.pixelSize: Theme.labelSize
            font.letterSpacing: Theme.labelSpacing * 0.6
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── the place's own tab ───────────────────────────────────────────────────────────
    Component {
        id: placeTab

        Column {
            width: parent ? parent.width : 300
            spacing: 12 * Theme.dp

            Outlined {
                visible: root.hasPhotos && !root.gallery.isInTrash
                width: parent.width
                label: "REVIEW PHOTOS"
                onClicked: {
                    root.close()
                    root.gallery.openReview()
                }
            }

            Card {
                visible: root.hasPhotos
                title: "Photos"
                order: 1

                Column {
                    width: parent.width
                    spacing: 8 * Theme.dp
                    opacity: root.isFollowing ? 0.38 : 1
                    enabled: !root.isFollowing
                    Name { text: "Image columns" }
                    SegmentedTrack {
                        width: parent.width
                        options: ["1", "2", "3", "4", "5", "6"]
                        currentIndex: (Settings.revision, Settings.gridValue(root.view, root.folderKey, "columns")) - 1
                        onPicked: index => Settings.setGridValue(root.view, root.folderKey, "columns", index + 1)
                    }
                }

                Column {
                    width: parent.width
                    spacing: 8 * Theme.dp
                    readonly property var groups: (Settings.revision, Settings.gridValue(root.view, root.folderKey, "dateGroups"))
                    readonly property bool hasHeaders: (Settings.revision, Settings.gridValue(root.view, root.folderKey, "headers"))
                    readonly property bool isByDeletion: root.gallery.isInTrash && Settings.isTrashByDeletion
                    opacity: root.isFollowing || isByDeletion ? 0.38 : 1
                    enabled: !root.isFollowing && !isByDeletion
                    Name { text: "Headers - Layout" }
                    // Any of the four at once, at least one; the switch under them turns every cut off and keeps the pick.
                    Row {
                        id: groupRow
                        width: parent.width
                        spacing: 6 * Theme.dp
                        opacity: parent.hasHeaders ? 1 : 0.38
                        Repeater {
                            model: [["days", "Days"], ["weeks", "Weeks"], ["months", "Months"], ["years", "Years"]]
                            Pressable {
                                id: chip
                                required property var modelData
                                readonly property bool isOn: groupRow.parent.groups.indexOf(modelData[0]) >= 0
                                width: (groupRow.width - 18 * Theme.dp) / 4
                                height: 34 * Theme.dp
                                isEnabled: groupRow.parent.hasHeaders
                                onClicked: {
                                    const groups = groupRow.parent.groups.slice()
                                    const at = groups.indexOf(modelData[0])
                                    if (at >= 0 && groups.length > 1)
                                        groups.splice(at, 1)
                                    else if (at < 0)
                                        groups.push(modelData[0])
                                    Settings.setGridValue(root.view, root.folderKey, "dateGroups", groups)
                                }
                                Rectangle {
                                    anchors.fill: parent
                                    radius: height / 2
                                    color: chip.isOn ? Theme.accent : "transparent"
                                    border.width: chip.isOn ? 0 : 1.5 * Theme.dp
                                    border.color: Theme.borderControl
                                    Behavior on color { ColorAnimation { duration: Motion.stateChange } }
                                }
                                Text {
                                    anchors.centerIn: parent
                                    text: chip.modelData[1].toUpperCase()
                                    color: chip.isOn ? Theme.sunkenDeep : Theme.textBright
                                    font.family: Theme.mono
                                    font.pixelSize: Theme.labelSize
                                }
                            }
                        }
                    }
                    SwitchRow {
                        label: "Headers"
                        isOn: parent.hasHeaders
                        onToggled: Settings.setGridValue(root.view, root.folderKey, "headers", !isOn)
                    }
                }

                SwitchRow {
                    visible: root.gallery.isInTrash
                    label: "Order by deletion"
                    isOn: Settings.isTrashByDeletion
                    onToggled: Settings.isTrashByDeletion = !Settings.isTrashByDeletion
                }

                SwitchRow {
                    visible: root.folderKey.length > 0
                    label: "Own settings"
                    isOn: root.isOwn
                    onToggled: Settings.setOwnSettings(root.folderKey, root.view, !root.isOwn)
                }
            }

            Card {
                visible: !root.hasPhotos
                title: "Albums"
                order: 2

                Name { text: "Album columns" }
                SegmentedTrack {
                    width: parent.width
                    options: ["1", "2", "3", "4"]
                    currentIndex: (Settings.revision, Settings.viewValue(root.view, "albumColumns")) - 1
                    onPicked: index => Settings.setViewValue(root.view, "albumColumns", index + 1)
                }
            }

            Card {
                visible: root.view === "private"
                title: "Private"
                order: 3

                SwitchRow {
                    label: "Today's selection"
                    isOn: Settings.hasTodaysSelection
                    onToggled: Settings.hasTodaysSelection = !Settings.hasTodaysSelection
                }
                Outlined {
                    width: parent.width
                    label: "LOCK"
                    onClicked: {
                        root.close()
                        Vault.lock()
                    }
                }
            }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── general ───────────────────────────────────────────────────────────────────────
    Component {
        id: generalTab

        Column {
            width: parent ? parent.width : 300
            spacing: 12 * Theme.dp

            Card {
                title: "Videos"
                order: 1
                SwitchRow {
                    label: "Autoplay"
                    isOn: Settings.isAutoplay
                    onToggled: Settings.isAutoplay = !Settings.isAutoplay
                }
            }

            // The folders the library is read from; on the phone this is the whole storage.
            Card {
                title: "Library"
                order: 2

                Repeater {
                    model: Library.roots
                    Item {
                        required property string modelData
                        width: parent.width
                        height: 30 * Theme.dp
                        FadeText {
                            width: parent.width - 40 * Theme.dp
                            anchors.verticalCenter: parent.verticalCenter
                            text: parent.modelData
                            color: Theme.textBright
                        }
                        IconButton {
                            anchors.right: parent.right
                            anchors.verticalCenter: parent.verticalCenter
                            width: 36 * Theme.dp
                            height: 30 * Theme.dp
                            glyphSize: 16 * Theme.dp
                            glyph: "close"
                            ink: Theme.textMuted
                            visible: Library.roots.length > 1
                            onClicked: Library.removeLibraryFolder(parent.modelData)
                        }
                    }
                }
                Outlined {
                    width: parent.width
                    label: "ADD FOLDER"
                    onClicked: folderDialog.open()
                }
            }

            Card {
                title: "Backup"
                order: 3
                Row {
                    width: parent.width
                    spacing: 10 * Theme.dp
                    Outlined {
                        width: (parent.width - 10 * Theme.dp) / 2
                        label: "BACK UP"
                        onClicked: backupDialog.open()
                    }
                    Outlined {
                        width: (parent.width - 10 * Theme.dp) / 2
                        label: "RESTORE"
                        onClicked: restoreDialog.open()
                    }
                }
                Text {
                    visible: (Settings.revision, Settings.viewValue("backup", "lastSaved") ?? 0) > 0
                    text: "Last saved " + System.formatDate(Settings.viewValue("backup", "lastSaved") ?? 0)
                    color: Theme.textMuted
                    font.family: Theme.mono
                    font.pixelSize: Theme.labelSize
                }
            }
        }
    }

    FolderDialog {
        id: folderDialog
        onAccepted: Library.addLibraryFolder(System.localPaths([selectedFolder])[0])
    }

    FileDialog {
        id: backupDialog
        fileMode: FileDialog.SaveFile
        defaultSuffix: "json"
        nameFilters: ["Backup (*.json)"]
        onAccepted: root.gallery.backUp(System.localPaths([selectedFile])[0])
    }

    FileDialog {
        id: restoreDialog
        fileMode: FileDialog.OpenFile
        nameFilters: ["Backup (*.json)"]
        onAccepted: root.gallery.restoreBackup(System.localPaths([selectedFile])[0])
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── interface ─────────────────────────────────────────────────────────────────────
    Component {
        id: interfaceTab

        Column {
            width: parent ? parent.width : 300
            spacing: 12 * Theme.dp

            // Where the navigation lives on the desktop: the sidebar, the bubble at the foot, or both.
            Card {
                title: "Navigation"
                order: 1
                SegmentedTrack {
                    width: parent.width
                    options: ["Sidebar", "Bubble", "Both"]
                    currentIndex: ["sidebar", "bubble", "both"].indexOf(Settings.navigation)
                    onPicked: index => Settings.navigation = ["sidebar", "bubble", "both"][index]
                }
            }

            Card {
                title: "Tiles"
                order: 2
                SwitchRow {
                    label: "Day stamps"
                    isOn: Settings.hasDayStamps
                    onToggled: Settings.hasDayStamps = !Settings.hasDayStamps
                }
                Name { text: "Folder label" }
                SegmentedTrack {
                    width: parent.width
                    options: ["Top", "Bottom"]
                    currentIndex: Settings.isFolderLabelTop ? 0 : 1
                    onPicked: index => Settings.isFolderLabelTop = index === 0
                }
            }

            Card {
                title: "Glass"
                order: 3
                SliderRow {
                    label: "Blur"
                    valueText: Math.round(Settings.blur)
                    value: Settings.blur
                    from: 0
                    to: 100
                    stepSize: 1
                    onMoved: value => Settings.blur = value
                }
                SliderRow {
                    label: "Opacity"
                    valueText: Math.round(Settings.glassOpacity * 100) + "%"
                    value: Settings.glassOpacity
                    from: 0
                    to: 1
                    stepSize: 0.01
                    onMoved: value => Settings.glassOpacity = value
                }
            }

            Card {
                title: "Background"
                order: 4
                SliderRow {
                    label: "Brightness"
                    valueText: Math.round(Settings.groundBrightness * 100) + "%"
                    value: Settings.groundBrightness
                    from: 0
                    to: 1
                    stepSize: 0.01
                    onMoved: value => Settings.groundBrightness = value
                }
            }

            // The desktop's own lever: everything scales with the screen, and this on top of it.
            Card {
                title: "Scale"
                order: 5
                SliderRow {
                    label: "Size"
                    valueText: Math.round(Settings.userScale * 100) + "%"
                    value: Settings.userScale
                    from: 0.7
                    to: 1.6
                    stepSize: 0.05
                    onMoved: value => Settings.userScale = value
                }
            }
        }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────
}
