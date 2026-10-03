# VADOS Gallery — product spec

The phone's gallery, rebuilt. It replaces Samsung Gallery as the app that opens photos on a Galaxy S25
(One UI 8.5, Android 16), and it takes the Apple Photos model as the reference for how a gallery should
behave: few places, fast hands, the photo first.

**The rule behind every decision here: a feature earns its place by being used.** Samsung Gallery fails
by offering everything at equal weight. This app keeps what is used daily, makes that excellent, and
cuts the rest.

## What it is

| | |
|---|---|
| Platform | Android 11+ (API 30), targeted at Android 16 on a Galaxy S25 |
| Stack | Kotlin, Jetpack Compose, Coil 3. Native rendering throughout, no WebView |
| Distribution | Sideloaded. A debug APK built by CI on every push |
| Design | VAS (VADOS APPLICATION SYSTEMS), base register, adapted for a phone app — `docs/DESIGN.md` |

## Sections

Three, and only three. Navigation is the bar at the bottom; there is no swipe between sections, because
a horizontal swipe belongs to the photo viewer.

| Section | Contents | Accent |
|---|---|---|
| **RECENT** | Every photo and video on the phone, in one grid | Terminal green `#5bfd5b` |
| **ALBUMS** | Folders, as albums | Amber `#f09b3a` |
| **FAVORITES** | Everything marked favourite | Hot pink `#ff2e88` |

**No duplicates.** There is no "Recent" album and no "Favorites" album inside ALBUMS — both already
are sections. No Stories, no Search tab, no Suggestions, no Sharing tab.

### Order: newest at the bottom

Every grid runs **oldest at the top, newest at the bottom right**, and opens scrolled to the bottom —
the Apple order. The thumb is at the bottom of the screen and so is the photo just taken.

- A new photo arriving while the grid is at its newest end keeps the grid pinned there.
- A grid scrolled back in time stays where it is when something new arrives.
- Tapping the bar entry of the section already shown scrolls home, to the newest end.
- Each grid remembers its own position when you switch sections and come back.

### RECENT

- A 4-column grid of square thumbnails, 2dp gaps.
- A small chip at the top names the month and year of the top visible row.
- Videos carry their duration in the corner.
- *Planned:* pinch to change the column count (3 / 4 / 6), a draggable date scrubber on the right edge.

### ALBUMS

- A 2-column grid of album cards: a cover (the newest item), the name, the count.
- **Camera first**, then everything else by its most recent photo.
- Opening an album shows the same grid as RECENT, filtered — newest at the bottom.
- *Planned:* create an album (asked for when moving), a *Recently deleted* row at the very end of the
  list as the one way into the trash.

### PRIVATE (inside ALBUMS)

Samsung Gallery has one private album. This one has **groups**: as many private folders as wanted.

- **Where:** a *Private* card at the very end of the albums list — present, never in the way. It
  is not a section of its own.
- **Unlock:** the fingerprint, with the phone's PIN as the system fallback. It stays unlocked while
  the app is in front and **locks again the moment the app is left**, dropping back to the albums list.
- **Inside:** a grid of groups (cover, name, count) and a *New group* card; each group opens as the
  same newest-at-bottom grid as everywhere else.
- **Getting in:** the viewer's ••• → *Move to private* → pick a group or *+ New group*. Hiding does
  not need the fingerprint; looking does.
- **Inside a private photo:** SHARE, DELETE (a second tap to confirm — private photos are outside the
  system trash, so this one is final), and ••• → *Move to group*, *Move out to album*, *Details*.
- **Screenshots and the recent-apps preview are blocked** while anything private is on screen.
- **Storage:** plain files in `/storage/emulated/0/.vados-private/<group>/`, with a `.nomedia` marker
  so no gallery (Samsung's included) indexes them. Moving in or out is a rename on the same disk —
  instant, no copy. They live outside the app's own storage so **uninstalling the app does not delete
  them**. They are **not encrypted**: a file manager can open the folder. Encryption is possible, but
  it ties the photos to a key that an uninstall destroys.

### FAVORITES

- The same grid as RECENT, holding only favourites.
- Favourite state lives in MediaStore (`IS_FAVORITE`), so it is shared with any other app that reads it.

## The viewer

Tapping a thumbnail opens it full screen, on black.

- Swipe sideways through the neighbours of the list it was opened from.
- Tap toggles the chrome.
- Back, or the system back gesture, closes it.
- *Planned:*
  - **Shared-element zoom**: the thumbnail grows into the photo and shrinks back into its cell.
  - Pinch and double-tap to zoom.
  - Swipe down to dismiss back into the grid.
  - Inline video playback with a scrubber.

### The action bar

Four actions, always visible while the chrome is shown:

| Action | What it does |
|---|---|
| **SHARE** | The system share sheet |
| **FAVORITE** | Toggles favourite, lit in the section accent when on |
| **DELETE** | Moves to the trash (recoverable for 30 days, the system's own trash) |
| **•••** | The menu below |

### The ••• menu

Four entries. That is the whole menu.

| Entry | What it does |
|---|---|
| **MOVE TO ALBUM** | Picks an album and moves the item there — the file actually moves on disk |
| **MOVE TO PRIVATE** | Picks a private group (or makes one) and hides the item there |
| **EDIT** | Hands the photo to an installed editor (Samsung's photo editor, or any other) |
| **DETAILS** | Name, date, resolution, size, folder |

### Cut, on purpose

Everything else Samsung Gallery offers in that menu is gone: **Copy** (a second file of the same photo
is almost never what anyone wants), Rename, Slideshow, Set as wallpaper, Print, Smart View, Add tag,
Create story, Open in Video Editor, Remaster, Object eraser, Details-as-a-separate-screen, Hide, Lock.
Any of them can come back if it turns out to be missed — that is the bar, not "Samsung has it".

## Selection

*Planned.* Long-press a thumbnail to start selecting, then drag across thumbnails to select a run (the
Apple swipe-select). Actions on a selection: SHARE, FAVORITE, MOVE TO ALBUM, DELETE.

## Becoming the default gallery

- The app answers `ACTION_VIEW` for images and videos, and the camera's `REVIEW` intents, so it can be
  chosen as the default for opening photos.
- It is registered as an `APP_GALLERY` launcher category.
- **Samsung Camera's thumbnail may still open Samsung Gallery directly.** If it does, the fallback is to
  disable Samsung Gallery for the user (`adb shell pm disable-user --user 0 com.sec.android.gallery3d`,
  reversible with `pm enable`), which leaves this app as the only handler. To verify on the device
  before relying on it.

## Permissions

| Permission | Why | Required |
|---|---|---|
| **All files access** (`MANAGE_EXTERNAL_STORAGE`) | Read the whole library and move files without a popup per file | Yes |
| **Media management** (`MANAGE_MEDIA`) | Lets favourite, move and delete requests through without a system confirmation | No, but recommended |

Both are granted on a system settings page; the app opens it and re-checks on return. Without file
access the app shows only the access screen. Fine for a sideloaded app; it would not pass Play Store
review, which is not a goal.

## Known open points

- **Samsung favourites do not carry over.** Samsung Gallery keeps its favourites in its own database,
  not in MediaStore, so they are invisible here. A one-time import is possible only if that database
  turns out to be readable (via Shizuku); otherwise favourites start empty.
- **Moving videos into a `Pictures/` folder** may be refused by MediaProvider on some versions. Needs a
  device test; the fallback is a direct file move under All files access.
- **Samsung's own trash** (inside Samsung Gallery) is separate from the MediaStore trash this app uses.
  Items already in Samsung's trash will not appear here.

## Milestones

1. **Scaffold** *(this commit)* — the three sections, newest-at-bottom grids, albums, the viewer with
   SHARE / FAVORITE / DELETE / ••• (MOVE, EDIT, DETAILS), permissions, CI build.
2. **The viewer, properly** — shared-element zoom, pinch / double-tap zoom, swipe-down dismiss, video playback.
3. **Selection** — long-press and drag-select, batch actions.
4. **Moving, properly** — create an album from the move picker, recently-used albums first.
5. **Grid polish** — pinch to change columns, the date scrubber, the trash row.
6. **Default app** — verify the camera hand-off, open an external photo inside its album.
7. **Private** *(done)* — fingerprint-locked groups at the end of Albums.
