# Releases

What changed in each feature version. Patch versions (x.x.1, x.x.2 …) are fixes and small adjustments; they are folded into the feature version they belong to. `RELEASE_NOTES.md` holds only the text of the latest release.

## 1.3: duplicates (1.3.0 – 1.3.1)

- **Duplicates:** Settings → General → Find duplicates scans every photo and groups copies of the same picture by their pixels, whatever their names, sizes, dates or compression. Exact, Close and Loose set how alike they must be; the best copy of each set is kept and the rest are marked, deleted to the trash after Confirm. Its own feature module (`feature/duplicates`); the matching (`ImagePrint.kt`) is plain Kotlin, tested on a desktop against real photos.
- **Private:** Find duplicates inside Private searches only the private photos, with its prints kept in the private folder.

## 1.2: drawing, dragging and a new settings panel (1.2.0 – 1.2.4)

- **Crop screen:** drawing and rotate.
- **Rearranging:** drag to rearrange covers, a long press picks a cover up, a long press on the background starts rearranging.
- **Albums into groups:** drag albums into groups, out of groups and between groups; hovering over a group opens it, releasing glides the cover into place.
- **Settings:** redesigned as cards with one spacing, full-height swipeable tabs, last backup time, and a panel that grows out of its button (the gear spins away, content rises in).

## 1.1: polish and structure (1.0.1 – 1.1.56)

Mostly refinement of what 1.0 introduced, plus the crop upgrade and the code split.

- **Crop:** its own violet, zoom, nav-style ratios, animated way in, undo and redo with history, trim arrows and per-frame seeking.
- **Folder label:** shown above the nav or in a top pill (Top / Bottom choice), types in and resizes smoothly.
- **Viewer opening and closing:** the library's bar changes into the open photo's buttons like a selection does, with one nav pill that frosts over the photo; swipe down seeks that change back with the finger. Viewer date pill widens from a circle and types in.
- **Timeline:** held timeline grows its labels, its end reaches the grid's end, and gliding to the newest photos is continuous.
- **Selection:** top and bottom bars pop on selection, one bottom pill that changes width, Remove from group in the album selection bar.
- **Groups:** shut groups show three stacked cards, the name is cut from the left as the group opens, and the arrow follows the finger when swiping a group open.
- **Private:** underlines its own sections, groups read as albums, Unlock in their menu, Today's selection switch.
- **Settings:** day stamps, headers and layout combined, own cover columns and headers per album, defaults match the reference screens.
- **Favourite heart:** pops and throws dots and sparkles when turned on.
- **Code:** app split into core and feature modules, settings split into display and album-arrangement stores, library broken into state holders and per-area composables.

## 1.0: the base gallery (v0.1 – v0.130)

Everything up to 1.0 is the basic feature set of a gallery app, built natively in Compose.

- **Browsing:** Recent grid with month headers, pinch to change columns, rounded tiles, sharp thumbnails once scrolling stops, a timeline along the right edge (years at rest, months while sliding), Weeks / Months / Years / Days layouts.
- **Viewer:** opens out of its tile and closes into the tile it ends on, pinch and double-tap zoom, video playback with a scrubbing timeline, forward and backward hold-to-play, sound toggle, swipe down to close, swipe up for details, HDR, rotation.
- **Albums:** create, rename (display name only), delete, set cover, rearrange by drag, 1–4 columns, hide from Recent, grouped albums shown as stacks that open into rows.
- **Private:** fingerprint-locked groups, private Recent and Favorites, move albums in and out.
- **Favorites:** red hearts, favorites-only filter, multiple Favorites albums.
- **Locations:** photo places read from the images and grouped by city.
- **Trash:** restore (as was or into an album), days left per item, delete forever, Samsung Gallery's trash included.
- **Editing:** crop photos, crop and trim videos, save a video frame as a photo, always saved as a copy.
- **Review mode:** swipe through a folder newest first, mark photos, delete at the end, progress kept per folder.
- **Other:** motion photos (hold to play), stacks of similar shots, Today's selection, undo for delete and move, a Confirm pill for every delete, haptics on every action, glass blur over floating UI, settings sheet (per-view grid and album settings), backup and restore of settings, covers and progress, launcher icon, signed releases.

## Planned

### 1.4.0: search (planned)

- **Search:** find photos by describing them. Samsung only for now: Galaxy AI ships Gemini built in, and this feature may fork that to power the search here. Other phones do not get it yet.
