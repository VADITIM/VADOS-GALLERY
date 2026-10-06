# Architecture

The app is split into Gradle modules. Dependencies only point downward:
the app joins the features, features stand on the core, and the core never reaches up.
Features never depend on each other.

```
:app                 MainActivity, GalleryApplication, the library shell (package library)
:feature:viewer      the photo and video viewer, crop, the external viewer
:feature:review      going through a folder one photo at a time
:feature:picker      the photo picker
:feature:albums      Albums, Locations, Private and the trash as screens
:feature:settings    the settings sheet
:core:ui             shared pieces used by several screens (package components)
:core:design         VAS in Compose: palette, type, motion, glass, pressable (package vas)
:core:data           MediaStore, the private vault, locations, similar shots, backup
:core:settings       Settings (how things look) and AlbumArrangement (orders, groups, names)
build-logic          the convention plugins every module applies
```

`vados.android.library` sets the SDK levels, the toolchain and Compose for every module.
`vados.android.feature` adds the four core modules on top of that.
A new module applies one of these two in its `build.gradle.kts` and is added to `settings.gradle.kts`.

## The library shell (`:app`, package `library`)

Before this split, one 1,700-line composable held all of the library's state.
Now the state lives in plain classes, and composables only read them.

- **`LibraryController`** owns the state holders and does the actions that cross between them.
  - `LibraryNavigation`: the section, the place inside Albums, Private, and the overlays.
  - `LibrarySelection`: picked photos and covers, the delete that waits on Confirm, and rearranging.
  - `LibrarySheets`: which sheet is open and what it acts on.
  - `ViewerTransition`: the viewer growing out of its tile and shrinking back into it.
  - `GridMemories`: scroll position and columns for every grid the user can come back to.
- **`LibraryContent`** is everything the library shows, already ordered and named. It is read from `GalleryViewModel` once per frame.
- **`LibraryScreen`** works out what is on screen from navigation and content: the open folder, its photos, its name, and what the bars offer. Any "which grid is this?" question is answered there, once.
- **`AlbumShelf`** covers the two places that hold albums and their groups, the folders and the Favorites albums. They share one set of menus, and each shelf supplies its own delete, rename and add.
- The composables are split by where they sit on screen:
  - `SectionContent`
  - `TopRow`
  - `BottomControls`
  - `LibrarySheetHost`
  - `Overlays` (the picker, review and the viewer)

## Where new code goes

- **A new screen:** a feature module. Anything it shares with another screen goes in `:core:ui`.
- **A new stored preference:** `Settings` if it changes how things look, `AlbumArrangement` if it records how the user arranged things. Every view keeps its own values, so a per-view setting is one more `PerViewSetting`.
- **A new place, sheet or overlay in the library:** a case in `LibraryPlaces` or `AppSheet`, its state in the matching holder, and its composable in the file for where it sits on screen.
