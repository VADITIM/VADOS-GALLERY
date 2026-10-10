import QtQuick
import Gallery

// Prime component (VAS components/19-pop-bar.md): change the entry there first, then this.
// One glass pill for every kind of bottom bar, the open photo's too: when the kind changes, each old button pops away on its own, each new one pops in after it,
// and the pill's width morphs between them over both pops. With a photo open, a pull drives the same change frame by frame (`seek`).
Item {
    id: bar

    required property var gallery
    // "navigation", "photos", "covers", "rearranging", "viewer", or "none" when the sidebar alone carries the navigation.
    property string wanted: "navigation"
    property string shown: wanted
    property string previous: ""
    property real progress: 1
    readonly property bool isChanging: progress < 1

    implicitHeight: 58 * Theme.dp
    width: pillWidth
    height: implicitHeight

    onWantedChanged: {
        if (seeking)
            return
        change(wanted)
    }

    function change(kind: string) {
        if (kind === shown && progress >= 1)
            return
        if (kind === previous && progress < 1) {
            // Turning back mid-change runs the same change in reverse from where it is.
            const swapped = shown
            shown = previous
            previous = swapped
            progress = 1 - progress
        } else {
            previous = shown
            shown = kind
            progress = 0
        }
        run.from = progress
        run.duration = (1 - progress) * Motion.stateChange * 2
        run.restart()
    }

    NumberAnimation {
        id: run
        target: bar
        property: "progress"
        to: 1
    }

    // #region ── seeking with the finger ───────────────────────────────────────────────────────
    property bool seeking: false
    // With a photo open, a pull down turns the viewer's bar into the one under the photo, in step with the finger.
    function seek(kind: string, share: real) {
        if (!seeking) {
            seeking = true
            run.stop()
            previous = "viewer"
            shown = kind
        }
        progress = Math.max(0, Math.min(1, share))
    }
    // Let go without closing: the viewer's bar comes back from wherever the finger left it.
    function settleBack() {
        if (!seeking)
            return
        seeking = false
        const swapped = shown
        shown = previous
        previous = swapped
        progress = 1 - progress
        run.from = progress
        run.duration = (1 - progress) * Motion.stateChange * 2
        run.restart()
    }
    // Let go past the point: the change carries on to the bar under the photo.
    function settleForward() {
        seeking = false
        run.from = progress
        run.duration = (1 - progress) * Motion.stateChange * 2
        run.restart()
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // #region ── the pop of each kind ──────────────────────────────────────────────────────────
    // The old buttons are gone by half way; the new ones pop in over the second half; the width spans both.
    function popOf(kind: string): real {
        if (kind === shown)
            return progress >= 1 ? 1 : Motion.backOutAt(Motion.clamp(progress * 2 - 1, 0, 1)) * (progress > 0.5 ? 1 : 0)
        if (kind === previous && progress < 1)
            return Math.max(0, 1 - Motion.backInAt(Motion.clamp(progress * 2, 0, 1)))
        return 0
    }
    function widthOf(kind: string): real {
        switch (kind) {
        case "navigation": return navigation.keptWidth
        case "photos": return photos.keptWidth
        case "covers": return covers.keptWidth
        case "rearranging": return rearranging.width
        case "viewer": return viewer.keptWidth
        default: return 0
        }
    }
    readonly property real pillWidth: progress >= 1 ? widthOf(shown) : widthOf(previous) + (widthOf(shown) - widthOf(previous)) * Motion.powerThreeInOutAt(progress)
    // The nav's wash fades with its buttons and comes back only once they have popped in.
    readonly property real washAlpha: popOf("navigation") >= 1 ? 1 : shown === "navigation" ? Math.max(0, progress * 2 - 1) : Math.max(0, 1 - progress * 2)
    // #endregion ───────────────────────────────────────────────────────────────────────────────

    // Over a settled photo the pill is the viewer's black: the blur reaches past its edges, so a photo standing just above would tint it.
    Glass {
        anchors.centerIn: parent
        width: bar.pillWidth
        height: bar.height
        visible: width > 1
        isSolid: bar.gallery.isViewerSettled
    }

    // #region ── the bars ──────────────────────────────────────────────────────────────────────
    SectionBar {
        id: navigation
        property real keptWidth: implicitWidth
        anchors.centerIn: parent
        pop: bar.popOf("navigation")
        // Hidden by opacity, never by visibility, so its buttons keep their room and the pill can measure it.
        opacity: pop > 0 ? 1 : 0
        washAlpha: bar.washAlpha
        active: bar.gallery.section
        albumsGlyph: bar.gallery.albumsGlyph
        isPrivateMode: bar.gallery.isPrivateInk
        accentOf: key => bar.gallery.accentOf(key)
        onSelected: key => bar.gallery.selectSection(key)
    }

    // Picked photos: in the trash, restore or delete for good; elsewhere share, favourite, set as cover, move, take into or out of Private, or delete.
    Row {
        id: photos
        readonly property real pop: bar.popOf("photos")
        property real keptWidth: 0
        // What the bar showed, held while it leaves, so it pops away as it was.
        property bool isAllFavorite: false
        property bool isOne: false
        property bool isTrash: false
        property bool isPrivate: false
        property bool canCover: false
        property bool isPrivateRecent: false
        Binding on isAllFavorite { when: bar.shown === "photos"; value: bar.gallery.isSelectionAllFavorite; restoreMode: Binding.RestoreNone }
        Binding on isOne { when: bar.shown === "photos"; value: bar.gallery.selection.photoCount === 1; restoreMode: Binding.RestoreNone }
        Binding on isTrash { when: bar.shown === "photos"; value: bar.gallery.isInTrash; restoreMode: Binding.RestoreNone }
        Binding on isPrivate { when: bar.shown === "photos"; value: bar.gallery.isPrivateMode; restoreMode: Binding.RestoreNone }
        Binding on canCover { when: bar.shown === "photos"; value: bar.gallery.canSetCover; restoreMode: Binding.RestoreNone }
        Binding on isPrivateRecent { when: bar.shown === "photos"; value: bar.gallery.gridSource === "private-recent"; restoreMode: Binding.RestoreNone }
        // Measured while it is the bar shown, and kept while it leaves.
        Binding on keptWidth { when: bar.shown === "photos" || photos.keptWidth === 0; value: photos.implicitWidth; restoreMode: Binding.RestoreNone }
        anchors.centerIn: parent
        padding: 5 * Theme.dp
        // Hidden by opacity, never by visibility, so its buttons keep their room and the pill can measure it.
        opacity: pop > 0 ? 1 : 0

        IconButton { visible: photos.isTrash; glyph: "restore"; ink: Theme.accent; pop: photos.pop; onClicked: bar.gallery.photoAction("restore") }
        IconButton { visible: photos.isTrash; glyph: "trash"; ink: Theme.danger; pop: photos.pop; isPending: bar.gallery.selection.pendingDelete !== null; onClicked: bar.gallery.photoAction("deleteForever") }
        IconButton { visible: !photos.isTrash; glyph: "share"; pop: photos.pop; onClicked: bar.gallery.photoAction("share") }
        IconButton {
            visible: !photos.isTrash
            glyph: photos.isAllFavorite ? "heart-filled" : "heart"
            ink: photos.isAllFavorite ? Theme.favorite : Theme.textBody
            pop: photos.pop
            onClicked: bar.gallery.photoAction("favorite")
        }
        OptionalButton { visible: !photos.isTrash && photos.canCover; isShown: photos.isOne; glyph: "image"; barPop: photos.pop; onClicked: bar.gallery.photoAction("cover") }
        IconButton { visible: !photos.isTrash && !photos.isPrivateRecent; glyph: "move"; pop: photos.pop; onClicked: bar.gallery.photoAction("move") }
        IconButton { visible: !photos.isTrash; glyph: photos.isPrivate ? "lock-open" : "lock"; pop: photos.pop; onClicked: bar.gallery.photoAction(photos.isPrivate ? "unhide" : "private") }
        IconButton { visible: !photos.isTrash; glyph: "trash"; ink: Theme.danger; pop: photos.pop; isPending: bar.gallery.selection.pendingDelete !== null; onClicked: bar.gallery.photoAction("delete") }
    }

    // Picked albums or private albums: move them into or out of Private, or delete them.
    Row {
        id: covers
        readonly property real pop: bar.popOf("covers")
        property real keptWidth: 0
        property bool isGroups: false
        property bool isGrouping: false
        property bool hasGrouped: false
        Binding on isGroups { when: bar.shown === "covers"; value: bar.gallery.isPrivateMode; restoreMode: Binding.RestoreNone }
        Binding on isGrouping { when: bar.shown === "covers"; value: bar.gallery.isCoverGrouping; restoreMode: Binding.RestoreNone }
        Binding on hasGrouped { when: bar.shown === "covers"; value: bar.gallery.isPickedCoverGrouped; restoreMode: Binding.RestoreNone }
        // Measured while it is the bar shown, and kept while it leaves.
        Binding on keptWidth { when: bar.shown === "covers" || covers.keptWidth === 0; value: covers.implicitWidth; restoreMode: Binding.RestoreNone }
        anchors.centerIn: parent
        padding: 5 * Theme.dp
        // Hidden by opacity, never by visibility, so its buttons keep their room and the pill can measure it.
        opacity: pop > 0 ? 1 : 0

        IconButton { visible: covers.isGrouping; glyph: "move"; pop: covers.pop; onClicked: bar.gallery.coverAction("group") }
        OptionalButton { visible: covers.isGrouping; isShown: covers.hasGrouped; glyph: "close"; barPop: covers.pop; onClicked: bar.gallery.coverAction("ungroup") }
        IconButton { glyph: covers.isGroups ? "lock-open" : "lock"; pop: covers.pop; onClicked: bar.gallery.coverAction(covers.isGroups ? "unhide" : "private") }
        IconButton { glyph: "trash"; ink: Theme.danger; pop: covers.pop; isPending: bar.gallery.selection.pendingDelete !== null; onClicked: bar.gallery.coverAction("delete") }
    }

    // Inverted, a dark tick on the section colour, so the only way out of rearranging is not missed.
    Pressable {
        id: rearranging
        readonly property real pop: bar.popOf("rearranging")
        anchors.centerIn: parent
        width: 66 * Theme.dp
        height: bar.height
        // Hidden by opacity, never by visibility, so its buttons keep their room and the pill can measure it.
        opacity: pop > 0 ? 1 : 0
        isEnabled: pop > 0.5
        onClicked: bar.gallery.selection.isRearranging = false

        Rectangle {
            anchors.fill: parent
            radius: height / 2
            color: Theme.accent
            scale: rearranging.pop
            Glyph {
                anchors.centerIn: parent
                name: "check"
                ink: Theme.viewerGround
            }
        }
    }

    // The open photo's buttons: share, then restore and delete in a trash; elsewhere favourite, crop, delete and more.
    Row {
        id: viewer
        readonly property real pop: bar.popOf("viewer")
        readonly property var photo: bar.gallery.viewerPhoto
        readonly property bool isTrash: photo.isTrash === true
        property real keptWidth: 0
        // Measured while it is the bar shown, and kept while it leaves.
        Binding on keptWidth { when: bar.shown === "viewer" || viewer.keptWidth === 0; value: viewer.implicitWidth; restoreMode: Binding.RestoreNone }
        anchors.centerIn: parent
        padding: 5 * Theme.dp
        // Hidden by opacity, never by visibility, so its buttons keep their room and the pill can measure it.
        opacity: pop > 0 ? 1 : 0

        IconButton { glyph: "share"; pop: viewer.pop; onClicked: bar.gallery.viewerAction("share") }
        IconButton { visible: viewer.isTrash; glyph: "restore"; ink: Theme.accent; pop: viewer.pop; onClicked: bar.gallery.viewerAction("restore") }
        FavoriteHeart {
            visible: !viewer.isTrash
            pop: viewer.pop
            isFavorite: viewer.photo.isFavorite === true
            path: viewer.photo.path ?? ""
            onClicked: bar.gallery.viewerAction("favorite")
        }
        IconButton { visible: !viewer.isTrash && viewer.photo.isVideo !== true; glyph: "crop"; pop: viewer.pop; onClicked: bar.gallery.viewerAction("crop") }
        IconButton { glyph: "trash"; ink: Theme.danger; pop: viewer.pop; isPending: bar.gallery.isViewerDeletePending; onClicked: bar.gallery.viewerAction("delete") }
        IconButton { visible: !viewer.isTrash; glyph: "more"; pop: viewer.pop; onClicked: bar.gallery.viewerAction("more") }
    }
    // #endregion ───────────────────────────────────────────────────────────────────────────────
}
