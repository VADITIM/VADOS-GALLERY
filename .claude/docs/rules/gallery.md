# Gallery rules

- Sections are the `Section` registry in `core/ui/src/main/java/com/vaditim/gallery/components/Section.kt`. Nothing else lists them.
- Leaf composables never name an accent; they read `LocalAccent`.
- Every pressable uses `Modifier.pressable`, never the ripple.
- Durations and curves come from `core/design/.../vas/Motion.kt`, never inline numbers.
- Every gesture that changes the UI is driven by the finger, never by a threshold: whatever the gesture changes (a sheet rising, buttons leaving, a card coming back, a viewer shrinking) moves frame by frame with the pull and goes back as it is released. Letting go past the point only decides; the motion then finishes from where the finger left it, and the state change is committed at the end, so nothing jumps or restarts (V/AS `architecture/05-motion.md` §11).
- Every change to a photo (favourite, trash, move) goes through a MediaStore request in `core/ui/.../components/MediaActions.kt`.
- No explanatory text in the UI: no hints, subtitles or captions describing what a control does, unless the user asks for one. Labels and state (a title, a count of selected, a time) are fine.
- Every feature exists everywhere the same kind of thing does: what works on albums works on private groups and locations, what works on a photo grid works in every photo grid (Recent, Favorites, albums, groups, locations, trash). Build it once as a shared piece in `:core:ui` (`components/Covers.kt`, `components/Reorder.kt`, `components/MediaGrid.kt`) and use that piece in every screen; before finishing a feature, list the places it should be and check each one has it.
- No abbreviations in identifiers (`architecture/09-code-style.md`).
