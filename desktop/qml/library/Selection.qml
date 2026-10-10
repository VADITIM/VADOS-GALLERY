import QtQuick
import Gallery

// Picked photos and covers, the delete that waits on Confirm, and rearranging (LibrarySelection.kt). Kept as plain sets with a revision, so a tile asks `has()` and re-asks when it changes.
QtObject {
    id: selection

    property var photos: ({})
    property var covers: ({})
    property int revision: 0
    property int photoCount: 0
    property int coverCount: 0
    property bool isRearranging: false
    // Only favourites in the photo grid on screen.
    property bool isFavoritesOnly: false
    // The delete waiting on Confirm, or null.
    property var pendingDelete: null
    // The last photo picked by a click, where a shift-click range starts.
    property int anchorIndex: -1

    readonly property bool isSelectingPhotos: photoCount > 0
    readonly property bool isSelectingCovers: coverCount > 0

    function has(path: string): bool {
        return photos[path] === true
    }
    function hasCover(key: string): bool {
        return covers[key] === true
    }
    function setPhoto(path: string, isPicked: bool) {
        if ((photos[path] === true) === isPicked)
            return
        if (isPicked)
            photos[path] = true
        else
            delete photos[path]
        photoCount += isPicked ? 1 : -1
        ++revision
    }
    function togglePhoto(path: string) {
        setPhoto(path, !has(path))
    }
    function setPhotos(paths: var, isPicked: bool) {
        for (const path of paths) {
            if ((photos[path] === true) === isPicked)
                continue
            if (isPicked)
                photos[path] = true
            else
                delete photos[path]
            photoCount += isPicked ? 1 : -1
        }
        ++revision
    }
    function toggleCover(key: string) {
        if (covers[key] === true) {
            delete covers[key]
            --coverCount
        } else {
            covers[key] = true
            ++coverCount
        }
        ++revision
    }
    function pickedPhotos(): var {
        return Object.keys(photos)
    }
    function pickedCovers(): var {
        return Object.keys(covers)
    }
    function clear() {
        photos = {}
        covers = {}
        photoCount = 0
        coverCount = 0
        anchorIndex = -1
        ++revision
    }
    // Every delete waits on one Confirm pill above the bar (VAS components/22).
    function confirmThen(action: var) {
        pendingDelete = action
    }
    function confirm() {
        const action = pendingDelete
        pendingDelete = null
        if (action)
            action()
    }
    // Drops what is no longer in the grid, so a moved or deleted photo does not stay counted.
    function keepOnly(paths: var) {
        const present = {}
        for (const path of paths)
            present[path] = true
        let count = 0
        const kept = {}
        for (const path in photos) {
            if (present[path]) {
                kept[path] = true
                ++count
            }
        }
        if (count !== photoCount) {
            photos = kept
            photoCount = count
            ++revision
        }
    }
}
