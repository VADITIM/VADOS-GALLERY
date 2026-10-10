import QtQuick
import Gallery

// Where the user stands (LibraryNavigation.kt): the section, the place inside Albums, and Private.
// Places inside Albums: "folders" (the albums), "folder" (one album), "trash", and inside Private "private-groups", "private-folder", "private-trash".
QtObject {
    id: navigation

    property string section: "recent"
    property string albumsPlace: "folders"
    // The album's folder, or the private group's name, for the places that open one.
    property string albumsArgument: ""
    // Inside Private the sections show Private's own photos: Recent all of them, Favorites its favourites, Albums its groups.
    property bool isPrivateMode: false
    // Tapping the section already shown asks its grid to glide back to the newest photo.
    property int scrollToNewestRequest: 0

    readonly property string place: section === "albums" ? albumsPlace : ""
    readonly property bool isPrivatePlace: albumsPlace.startsWith("private")
    readonly property bool isTrashPlace: albumsPlace === "trash" || albumsPlace === "private-trash"

    function select(selected: string) {
        if (selected === section) {
            if (selected === "albums") {
                // Back to the start of the place it is in, not out to the albums; from there, to the newest.
                const start = isPrivateMode ? "private-groups" : albumsPlace === "trash" ? "trash" : "folders"
                if (albumsPlace === start)
                    ++scrollToNewestRequest
                albumsPlace = start
                albumsArgument = ""
            } else {
                ++scrollToNewestRequest
            }
        }
        section = selected
    }

    function openAlbum(folder: string) {
        section = "albums"
        albumsPlace = "folder"
        albumsArgument = folder
    }

    function openTrash() {
        section = "albums"
        albumsPlace = isPrivateMode ? "private-trash" : "trash"
        albumsArgument = ""
    }

    function enterPrivate() {
        isPrivateMode = true
        section = "albums"
        albumsPlace = "private-groups"
        albumsArgument = ""
        Vault.pickTodaysSelection()
    }

    function openGroup(name: string) {
        section = "albums"
        albumsPlace = "private-folder"
        albumsArgument = name
    }

    function leavePrivate() {
        isPrivateMode = false
        albumsPlace = "folders"
        albumsArgument = ""
    }

    // Out of Private, out of the trash.
    function leavePlace() {
        if (isPrivateMode)
            leavePrivate()
        else {
            albumsPlace = "folders"
            albumsArgument = ""
        }
    }

    // Locking drops anyone standing in Private back to the albums list.
    function onPrivateLocked() {
        if (!isPrivateMode && !isPrivatePlace)
            return
        isPrivateMode = false
        if (isPrivatePlace) {
            albumsPlace = "folders"
            albumsArgument = ""
        }
    }

    // The system back inside the library: out of a folder, out of Private's own sections; false when there is nowhere further back.
    function back(): bool {
        if (isPrivateMode) {
            if (section !== "albums") {
                section = "albums"
                albumsPlace = "private-groups"
                return true
            }
            if (albumsPlace === "private-folder" || albumsPlace === "private-trash") {
                albumsPlace = "private-groups"
                albumsArgument = ""
                return true
            }
            leavePrivate()
            return true
        }
        if (section === "albums" && albumsPlace !== "folders") {
            albumsPlace = "folders"
            albumsArgument = ""
            return true
        }
        return false
    }
}
