pragma Singleton

import QtQuick

// What every pane of glass blurs: the scene under the floating controls, the grids and the photo in flight alike. Set once, at the root.
QtObject {
    property Item item: null
}
