import QtQuick
import Gallery

// Beside the nav, straight on the photos with a shadow: the heart that narrows the grid on screen to its favourites, and how many of its photos were taken in the month shown, as 34/1000.
// Each number has a slot as wide as the total's digits, so neither the numbers nor the slash move when one gains a digit (BottomControls.kt).
Pop {
    id: root

    required property var gallery
    readonly property var grid: gallery.activeGrid
    readonly property bool canNarrow: grid !== null && gallery.section !== "favorites" && !gallery.isInTrash
    isShown: grid !== null && gallery.barKind !== "photos" && gallery.barKind !== "covers" && gallery.barKind !== "viewer" && gallery.barKind !== "rearranging"

    Column {
        anchors.verticalCenter: parent ? parent.verticalCenter : undefined
        spacing: 2 * Theme.dp

        Pressable {
            anchors.horizontalCenter: parent.horizontalCenter
            width: 30 * Theme.dp
            height: 30 * Theme.dp
            // Where there is nothing to narrow it still holds its room, unseen, so the count never climbs into its place.
            opacity: root.canNarrow ? 1 : 0
            isEnabled: root.canNarrow
            onClicked: root.gallery.selection.isFavoritesOnly = !root.gallery.selection.isFavoritesOnly

            Glyph {
                anchors.centerIn: parent
                anchors.verticalCenterOffset: 1
                width: 18 * Theme.dp
                height: width
                name: root.gallery.selection.isFavoritesOnly ? "heart-filled" : "heart"
                ink: Qt.rgba(0, 0, 0, 0.6)
            }
            Glyph {
                anchors.centerIn: parent
                width: 18 * Theme.dp
                height: width
                name: root.gallery.selection.isFavoritesOnly ? "heart-filled" : "heart"
                ink: root.gallery.selection.isFavoritesOnly ? Theme.favorite : Theme.textBright
            }
        }

        Row {
            anchors.horizontalCenter: parent.horizontalCenter
            readonly property int total: root.grid ? root.grid.model.count : 0
            readonly property string digits: "0".repeat(String(total).length)
            Text { id: slot; visible: false; text: parent.digits; font.family: Theme.mono; font.pixelSize: 8 * Theme.dp }
            Item {
                width: slot.implicitWidth
                height: slot.implicitHeight
                Typewriter {
                    anchors.right: parent.right
                    text: String(root.grid ? root.grid.monthCount : 0)
                    fontSize: 8 * Theme.dp
                    letterSpacing: 0
                    isCaretShown: false
                    hasShadow: true
                }
            }
            Text {
                text: "/"
                leftPadding: 2 * Theme.dp
                rightPadding: 2 * Theme.dp
                color: Theme.textMuted
                font.family: Theme.mono
                font.pixelSize: 8 * Theme.dp
                style: Text.Raised
                styleColor: Qt.rgba(0, 0, 0, 0.6)
            }
            Item {
                width: slot.implicitWidth
                height: slot.implicitHeight
                Typewriter {
                    anchors.left: parent.left
                    text: String(parent.parent.total)
                    fontSize: 8 * Theme.dp
                    letterSpacing: 0
                    isCaretShown: false
                    hasShadow: true
                }
            }
        }
    }
}
