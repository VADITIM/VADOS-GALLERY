# VADOS Gallery

This project is built on VAS (VADOS APPLICATION SYSTEMS), base register. Read `.claude/skills/vas/SKILL.md`.
If it is missing, ask before cloning `https://github.com/VADITIM/VADOS-APPLICATION-SYSTEMS` there.

The phone adaptation of VAS for this app — native Compose instead of a WebView, the tool tempo instead
of the portfolio tempo, black behind photos — is in `docs/DESIGN.md`, and it overrides the base modules
where they disagree. The module layout and where new code goes are in `docs/ARCHITECTURE.md`. The product decisions are in `docs/SPEC.md`; a feature not in it is not added
without asking.

- Sections are the `Section` registry in `core/ui/src/main/java/com/vaditim/gallery/components/Section.kt`. Nothing else lists them.
- Leaf composables never name an accent; they read `LocalAccent`.
- Every pressable uses `Modifier.pressable`, never the ripple.
- Durations and curves come from `core/design/.../vas/Motion.kt`, never inline numbers.
- Every gesture that changes the UI is driven by the finger, never by a threshold: whatever the gesture changes (a sheet rising, buttons leaving, a card coming back, a viewer shrinking) moves frame by frame with the pull and goes back as it is released. Letting go past the point only decides; the motion then finishes from where the finger left it, and the state change is committed at the end, so nothing jumps or restarts (VAS `dna/05-motion.md` §11).
- Every change to a photo (favourite, trash, move) goes through a MediaStore request in `core/ui/.../components/MediaActions.kt`.
- No explanatory text in the UI: no hints, subtitles or captions describing what a control does, unless the user asks for one. Labels and state (a title, a count of selected, a time) are fine.
- Every feature exists everywhere the same kind of thing does: what works on albums works on private groups and locations, what works on a photo grid works in every photo grid (Recent, Favorites, albums, groups, locations, trash). Build it once as a shared piece in `:core:ui` (`components/Covers.kt`, `components/Reorder.kt`, `components/MediaGrid.kt`) and use that piece in every screen; before finishing a feature, list the places it should be and check each one has it.
- No abbreviations in identifiers (`dna/09-code-style.md`); comments say why, on one line.

There is no Android SDK in the cloud sessions. CI (`.github/workflows/build.yml`) is the compiler: push,
then read the run. A change is verified only on the phone.

Work directly on `master`: commit and push there, no feature branches. Every push to `master` publishes
the APK as the release `v<versionName>`.

Every change raises `versionName` in `app/build.gradle.kts` (and `versionCode`): 1.0.0, 1.0.1, and so on. The release tag comes from it.
Every change also rewrites `RELEASE_NOTES.md`: a short changelog of that version, a few plain bullets of what changed for the user. It is the release's text.

## The desktop port (`desktop/`)

VADOS Gallery for Arch under Hyprland, in Qt 6 (QML + C++), the stack of VAD/OS Files. `docs/DESKTOP.md` says what it does
differently from the phone and where things live; `docs/SPEC.md` and `docs/DESIGN.md` hold for it as for the APK.

- Build and run here: `cmake -B desktop/build -G Ninja desktop && cmake --build desktop/build && desktop/build/vados-gallery`. Unlike the APK, the compiler is local.
- The rules above hold in it, by its own files: sections are `qml/library/Sections.qml`; every change to a photo goes through `src/core/mediaActions.cpp`; durations and curves come from `qml/design/Motion.qml`; leaves read `Theme.accent`; the bubble is VAS's pop bar, taken whole.
- A desktop-only change raises `project(vados-gallery VERSION …)` in `desktop/CMakeLists.txt`, not `versionName`, and leaves `RELEASE_NOTES.md` alone: the workflow ignores `desktop/`, so it publishes no APK.
- A binding that must re-run when a store changes reads its revision as `Store.revision >= 0 && …`, never `(Store.revision, …)`: compiled bindings drop the comma's left side, and the dependency with it.
- A child handed `gallery: gallery` (or `grid: grid`, `shelf: shelf`) binds the property to itself. The roots are `shell`, `photoGrid` and `coverShelf` for that reason.
- Content of a `Pressable` lies inside its inner face, so `parent` there is not the pressable; reach it by id.
