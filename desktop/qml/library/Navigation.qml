import QtQuick
import Gallery

// Where the user stands (LibraryNavigation.kt): the section, the place inside Albums, and Private.
// Places inside Albums: "folders" (the albums), "folder" (one album), "locations" and "location" (one place), "trash", and inside Private "private-groups", "private-folder", "private-trash".
QtObject {
    id: navigation

    property string section: "recent"
    property string albumsPlace: "folders"
    // The album's folder, or the private group's name, for the places that open one.
    property string albumsArgument: ""
    // Inside Private the sections show Private's own photos: Recent all of them, Favorites its favourites, Albums its groups.
    property bool isPrivateMode: false
    // The album made inside Favorites that is open, by name.
    property string favoriteAlbum: ""
    // Tapping the section already shown asks its grid to glide back to the newest photo.
    property int scrollToNewestRequest: 0

    readonly property string place: section === "albums" ? albumsPlace : ""
    readonly property bool isPrivatePlace: albumsPlace.startsWith("private")
    readonly property bool isTrashPlace: albumsPlace === "trash" || albumsPlace === "private-trash"
    readonly property bool isInLocations: albumsPlace === "locations" || albumsPlace === "location"

    function select(selected: string) {
        if (selected === section) {
            if (selected === "albums") {
                // Back to the start of the place it is in, not out to the albums; from there, to the newest.
                const start = isPrivateMode ? "private-groups" : albumsPlace === "trash" ? "trash" : isInLocations ? "locations" : "folders"
                if (albumsPlace === start)
                    ++scrollToNewestRequest
                albumsPlace = start
                albumsArgument = ""
            } else if (selected === "favorites" && favoriteAlbum.length > 0) {
                favoriteAlbum = ""
            } else {
                ++scrollToNewestRequest
            }
        }
        section = selected
    }

    function openFavoriteAlbum(name: string) {
        section = "favorites"
        favoriteAlbum = name
    }

    function openAlbum(folder: string) {
        section = "albums"
        albumsPlace = "folder"
        albumsArgument = folder
    }

    function openLocations() {
        section = "albums"
        albumsPlace = "locations"
        albumsArgument = ""
    }

    function openLocation(key: string) {
        section = "albums"
        albumsPlace = "location"
        albumsArgument = key
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
        // Back from a place goes to the list of places, and from there out to the albums.
        if (section === "albums" && albumsPlace === "location") {
            albumsPlace = "locations"
            albumsArgument = ""
            return true
        }
        if (section === "albums" && albumsPlace !== "folders") {
            albumsPlace = "folders"
            albumsArgument = ""
            return true
        }
        if (section === "favorites" && favoriteAlbum.length > 0) {
            favoriteAlbum = ""
            return true
        }
        return false
    }
}
