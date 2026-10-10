pragma Singleton

import QtQuick

// Where the keyboard goes back to once a sheet or a field lets it go, so Escape and the arrows keep working. Set once, at the root.
QtObject {
    property Item item: null

    function restore() {
        if (item)
            item.forceActiveFocus()
    }
}
