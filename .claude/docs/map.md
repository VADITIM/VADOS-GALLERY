# Map

| Path | Purpose |
|---|---|
| `app/` | `:app` — `MainActivity`, `GalleryApplication`, the library shell (package `library`); holds `versionName` and `versionCode` in `build.gradle.kts` |
| `feature/viewer`, `review`, `duplicates`, `picker`, `albums`, `settings` | One screen area each; features never depend on each other |
| `core/ui` | Shared pieces used by several screens (package `components`): `Section.kt`, `MediaActions.kt`, `Covers.kt`, `Reorder.kt`, `MediaGrid.kt` |
| `core/design` | V/AS in Compose (package `vas`): `Motion.kt`, palette, type, glass, pressable |
| `core/data` | MediaStore, the private vault, locations, similar shots, duplicates, backup |
| `core/settings` | `Settings` (how things look) and `AlbumArrangement` (orders, groups, names) |
| `build-logic/` | Convention plugins `vados.android.library` and `vados.android.feature` |
| `desktop/` | The Qt 6 port: `src/core/` C++ (`library.cpp`, `mediaActions.cpp`, `settings.h`), `qml/` (`library/Sections.qml`, `design/Motion.qml`, `ui/` primitives, `viewer/`, `review/`, `crop/`, `duplicates/`, `sheets/`) |
| `.github/workflows/build.yml` | The one workflow: build, upload, publish on command |
| `RELEASE_NOTES.md` | The latest release's text |
| `Releases.md` | The longer history by feature version |
| `.claude/` | Agent-facing docs, this folder |
| `.claude/skills/vas/` | V/AS, a git-ignored clone of GitHub |

## To change X, look in Y

- A section, its order or accent: `Section.kt` in `core/ui`, and `desktop/qml/library/Sections.qml`.
- A change to a photo (favourite, trash, move): `MediaActions.kt`, and `desktop/src/core/mediaActions.cpp`.
- A duration or curve: `Motion.kt`, and `desktop/qml/design/Motion.qml`.
- A new screen, preference, place or sheet: `architecture.md` § Where new code goes.
- The version: `versionName` in `app/build.gradle.kts` for the APK; `project(vados-gallery VERSION …)` in `desktop/CMakeLists.txt` for the desktop.
