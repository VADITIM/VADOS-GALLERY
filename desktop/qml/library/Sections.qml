pragma Singleton

import QtQuick
import Gallery

// The registry (VAS dna/07-architecture.md, Section.kt): one ordered list makes the sidebar, the bubble, the accents and the order. Adding a section here is the whole change.
QtObject {
    readonly property var all: [
        { key: "recent", label: "RECENT", accent: Theme.terminalGreen, glyph: "clock" },
        { key: "albums", label: "ALBUMS", accent: Theme.amber, glyph: "albums" },
        { key: "favorites", label: "FAVORITES", accent: Theme.hotPink, glyph: "heart-filled" },
    ]

    function find(key: string): var {
        for (const section of all)
            if (section.key === key)
                return section
        return all[0]
    }
}
