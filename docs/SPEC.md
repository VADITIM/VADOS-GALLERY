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
- Every grid is divided by month and year: a header ("October 2026") in front of each month's first photo.
- Pinch in any grid steps the column count from 1 to 5 (spread = fewer, larger photos). Each folder remembers its own.
- *Planned:* a draggable date scrubber on the right edge.

### ALBUMS

- A 2-column grid of album cards: a cover (the newest item), the name, the count.
- **Camera first**, then everything else by its most recent photo.
- Opening an album shows the same grid as RECENT, filtered — newest at the bottom.
- **Creating albums, anywhere:** a *New album* card at the end of the albums grid (name it, then pick
  its photos), and *+ New album* at the end of every "move to" list — from the viewer, a selection, or
  a private group being moved out. A new album is a folder under `Pictures/`.
- *Removed:* the **+ Add** button at the top of albums and private groups; photos go in with Move. *Add photos* stays in the long-press menu.
- **Long-press an album:** *Add photos*, *Move album to private*, *Delete album* (second tap confirms;
  the photos go to the system trash).
- *Planned:* a *Recently deleted* row at the very end of the list as the one way into the trash.

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
- **Long-press a group:** *Add photos*, *Move group out to album* (an existing album or a new one, named
  after the group by default; the emptied group is removed), *Delete group* (second tap confirms — final).
- **Inside a private photo:** SHARE, DELETE (a second tap to confirm — private photos are outside the
  system trash, so this one is final), and ••• → *Move to group*, *Move out to album*, *Details*.
- **Private favourites.** A favourite that goes into Private stays a favourite — but only as a
  *private* favourite, kept in the private folder itself (`.favorites`), never in MediaStore, so it can
  never appear in the normal FAVORITES. Moving it back out to an album makes it an ordinary favourite
  again. Private ends with a **Favorites** folder, a wide row at the very bottom, below the groups.
- **Album covers.** Long-pressing a photo anywhere (a little longer than the system's long press) selects it; a finger that lands on a moving grid, or just after it stopped, only stops the scroll and never selects, as in Recent; sliding the held finger across more tiles selects (or deselects) them along the row and scrolls at the edges. With exactly one photo selected inside an album or private group, COVER makes it the cover. The cover is remembered per album (per group, inside the group's folder); until one is set, the newest photo is the cover.
- **"Today's selection for you 😏"** — a large card at the top of Private showing one random private
  favourite; a new pick every time Private is entered, kept while scrolling.
- **Screenshots and the recent-apps preview are blocked** while anything private is on screen.
- **Nothing private is visible anywhere else on the phone.** The folder carries `.nomedia`, so
  MediaStore never indexes it and no gallery can list it; this app's own library query also excludes
  the folder by path. Thumbnails are decoded in memory only — nothing private is written to a cache.
  (A copy uploaded to a cloud backup *before* it was hidden is outside this app's reach.)
- **Storage:** plain files in `/storage/emulated/0/.vados-private/<group>/`, with a `.nomedia` marker
  so no gallery (Samsung's included) indexes them. Moving in or out is a rename on the same disk —
  instant, no copy. Where the storage layer refuses a rename between two folders, the move falls back
  to copy, verify the size, then delete the original — the original is never removed before the copy
  is complete. They live outside the app's own storage so **uninstalling the app does not delete
  them**. They are **not encrypted**: a file manager can open the folder. Encryption is possible, but
  it ties the photos to a key that an uninstall destroys.

### FAVORITES

- The same grid as RECENT, holding only favourites.
- Favourite state lives in MediaStore (`IS_FAVORITE`), so it is shared with any other app that reads it.
- **Favorites albums.** Inside Favorites, favourites can be gathered into albums of their own, and those albums into groups; none of it touches folders or anything outside Favorites. A button in the top row switches between every favourite in one grid and the albums (the same cover grid, groups, rearranging and "New album" as ALBUMS). A selection of favourites adds to an album (or a new one); inside an album the selection can also leave it. An album holds favourites only, so an unfavourited photo leaves it and an emptied album disappears. Long-press an album: rename, rearrange, group, add photos (from the favourites), delete (the album only, second tap).

## The viewer

Tapping a thumbnail opens it full screen, on black.

- Swipe sideways through the neighbours of the list it was opened from.
- Tap toggles the chrome.
- The system back gesture (or a swipe down) closes it; there is no back button.
- *Planned:*
  - **Shared-element zoom** (done): the photo's own frame (no black around it) is cropped to the tile's square and moved onto the tile of whichever photo you ended on, scrolling the grid to it first when needed; a quiet fade when no tile applies.
  - Pinch and double-tap to zoom (done); photos sit in a rounded frame with a gap between pages when swiping.
  - Swipe down to dismiss (done): the photo shrinks towards its grid tile under the finger, the black falling away as it goes. Swipe up shows the details sheet (done) and moves nothing. Both only at normal size, so zoomed panning and the horizontal pager are untouched.
  - Inline video playback (done): plays on open, a timeline above the action bar with loop and play/pause icons beneath it (the favourite heart stays in the bottom bar, as for photos). Holding on the right half plays the video forward at 1.5x, holding on the left half plays it backwards at 1.5x; sliding towards the middle speeds it up, towards the edge slows it down. The speed shows as chevrons and a number above the timeline. Letting go returns to normal. Hold the timeline and slide up for finer scrubbing: half, quarter, then a tenth of finger speed, with hundredths of a second shown.

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

**Long-press a thumbnail** (with the system's long-press haptic) to start selecting; while a selection
is open, a tap adds or removes a photo. The top shows *Cancel* and the count; back also cancels. The
section bar is replaced by the selection's own bar:

| Where | Actions |
|---|---|
| Recent, Favorites, an album | SHARE · MOVE (to an album) · PRIVATE (to a group) · DELETE (to the trash) |
| A private group, Private Favorites | SHARE · GROUP (to another group) · OUT (back to an album) · DELETE (forever, second tap confirms) |

*Planned:* dragging across thumbnails to select a run (the Apple swipe-select).

**Long-press an album** for its menu: **Move album to private** — every photo in the folder goes into a
private group, an existing one or a new one named after the album by default.

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
  Its files (under `Android/.Trash`) are listed in this app's Trash too; their original folder is in Samsung's private
  database, so restoring one puts it in `Pictures/Restored`. What this app deletes cannot be added to Samsung's trash.

## Milestones

1. **Scaffold** *(this commit)* — the three sections, newest-at-bottom grids, albums, the viewer with
   SHARE / FAVORITE / DELETE / ••• (MOVE, EDIT, DETAILS), permissions, CI build.
2. **The viewer, properly** — shared-element zoom, pinch / double-tap zoom, swipe-down dismiss, video playback.
3. **Selection** — long-press and batch actions *(done)*; drag-select still to come.
4. **Moving, properly** — create an album from the move picker, recently-used albums first.
5. **Grid polish** — pinch to change columns, the date scrubber, the trash row.
6. **Default app** — verify the camera hand-off, open an external photo inside its album.
7. **Private** *(done)* — fingerprint-locked groups at the end of Albums.

- **Settings.** A button at the top right of every view opens a sheet in two tabs. General, under headers: Photos (image columns, month headers, similar shots), Albums (grouped albums, and album columns only while grouping is off — grouped albums lie as rows), Videos (autoplay). Interface: Overlays (blur and opacity of the glass) and Background (the brightness of the app's ground, from black to a dark charcoal; the viewer stays black). Stored in preferences (`Settings.kt`) and applied at once.
- **Section colours stay with their section.** Switching sections, the outgoing one keeps its own accent while it leaves; only the bar and the top row blend to the new one.
- **Locations.** Each photo's GPS (EXIF, or a video's location atom) is read once with the media-location permission and named by the system geocoder per ~1 km cell; both are cached in app files. Details shows the city, country and coordinates. A Locations row above Private in Albums lists cities by photo count; each opens as a grid.
- **Trash.** A Trash row (with count) sits between Locations and Private. It lists the system trash (Android keeps items 30 days); tapping selects, and the bar restores or deletes forever (second tap).
- **Rename.** Long-pressing an album or private group offers Rename. An album is renamed by moving its photos to a sibling folder, so MediaStore rows and favourites survive.
- **Moving into Private** is confirmed once more after the group is chosen (selection, whole album, viewer).
- **Icons, not words,** on every action bar and menu row: share, cover (image), move, private (lock; open lock for moving out), trash, rename (pen), restore, add, close, details, more.
- The top row keeps a fixed-width month chip so the buttons beside it never move.
- **Album layout.** Pinch the Albums grid (or Settings → Album columns) for 1–4 albums per row. Long-press an album → Rearrange albums: drag cards into any order, confirm with the check. The order is stored by folder path.
- **Today's selection** plays a picked video silently on a loop.
- **Haptics.** Every action answers with a vibration: a click on icon buttons and menu rows, toggle on/off in Settings, ticks on sliders, column changes, each photo or cover added to or removed from a selection (by tap or drag), each album swap while rearranging, a threshold buzz when a rearrange drag starts, a confirm when rearranging ends. A short double click when something has been moved, deleted, restored or favourited. Opening and closing photos stay silent.
- **Album names fit:** at three or four per row the name and count shrink until they fit (down to 9sp), then cut. One per row is a list: small cover at the start, large name and count beside it.
- **Month chip** reads "September 25" (two-digit year) and never wraps.
- **One cover grid.** Albums, private groups and locations share `CoverGrid`/`CoverCard`: the same columns (pinch or Settings), list layout at one column, shrinking names. Albums and private groups can be rearranged (long-press → Rearrange), by dragging a card onto another.
- **Trash photos open** in the viewer on a tap (long press selects); the viewer there offers share, restore and delete forever (second tap).

- The blur along the top edge fades evenly: strongest at the screen edge, easing out steadily the further down it goes.
- Grouped albums (a setting): albums can be put into groups, as many as wanted. A group lies in the albums grid as a stack of its covers, each card leaning a little. Tapping it lays its albums out in the grid from its place onwards, wrapping by the album columns, with a card at the end that stacks them back. An album's menu adds it to a group, moves it to another, or takes it out; a group's menu renames it, rearranges, or ungroups it. Turning the setting off shows every album on its own again and keeps the groups for later.
- Renaming an album keeps its place in the arranged order and in its group.
- Crop (viewer → More): photos are cropped, videos cropped and trimmed. Free or a fixed ratio (Original, 1:1, 4:5, 4:3, 16:9, 9:16). The result is saved as a copy beside the original with the original's date and location; the original is never changed. Available in Private as well.
- Grid tiles bigger than the system thumbnail (few columns) load the photo itself once scrolling stops; while flinging only the cached thumbnails are read.
- Video controls are one slim bar (play, time, timeline, length, loop, sound). Sound off holds for every video while the app runs.
- Album groups each take a row of their own; the cards under the cover fan out to the right.
- Covers can be selected several at once (menu → Select, then tap): albums can then be grouped, moved to Private or deleted; private groups moved out or deleted. Deleting takes a second tap.
- While rearranging, covers jiggle slightly. Double-tapping a zoomed photo always returns it to its original size.
- Rearranging with a group open orders that group's albums; with groups closed, it orders albums and groups. Opening a group peels its albums off the stack one after another.
- A group is always shown as a one-column row (stack, then name and count). Opened, its albums lie three to a row. Several groups can be open at once.
- The viewer's buttons are not part of the photo: on a swipe down, on closing, or on a tap they slide off their own edge one after another, and come back the same way once the photo has settled.
- The section bar's highlight slides from section to section, its leading edge first, and the chosen label grows slightly.
- Every photo grid and cover grid opens with a short cascade: its first screenful fades and rises into place, item after item. Items reached later by scrolling are simply there.
- Favourites carry a small red heart at the bottom right of their tile in every grid (left of a video's length). Every favourite heart is red, never the section colour.
- **Timeline.** Every photo grid with more than 90 photos has a timeline on its right edge: every year and all its months on one strip, oldest at the top, evenly apart, seen through a window about a third of the screen tall and centred. The marker runs down the window as the grid scrolls (moving between labels as the months pass, reaching the newest month at the grid's end) while the strip slides the other way so the month shown sits on it; labels fade, shrink and slide in over the window's edges. Labels shrink and draw closer together the further they are from the marker, on a steep curve that never levels off: the near months read clearly, the far ones fade to nothing, and years (larger than months) never go below a floor, so far from the marker the strip reads as years alone. The month shown and its year are in the section colour. Holding and sliding moves the marker with the finger and the strip with it, so the label under the finger is where the grid goes (a year goes to its first month): the grid jumps month by month, each month's first photo at the top (instantly, no motion on the photos), shows the month beside the finger and ticks on each new month. The strip never changes length, so nothing moves out from under the finger. Only the window's stretch of the edge, and the labels in it, take a finger.
- **Undo.** Moving to the trash or into another album (from a selection, an album's menu or the viewer) shows a pill above the bar for a few seconds: what happened and an undo button. Undo puts the photos back where they were. The final deletes (Private, Trash) and hiding into Private are already confirmed and have no undo.
- **Hide from Recent.** Long-pressing an album or an album group offers Hide from Recent (Show in Recent to undo it). Its photos leave the Recent grid and the viewer opened from it; the album itself, Favorites, Locations and the photo picker still show them. Renaming the album keeps the choice.
- **Motion photos.** A photo with a clip appended (Samsung's and Google's format) carries a motion mark at the bottom right of its tile in every grid and beside the date in the viewer. Holding it in the viewer plays the clip on a loop, with sound unless video sound is off; letting go returns to the still. The clip is read straight out of the photo's file, so nothing is copied, private photos included.
- **Save frame.** A video's ••• menu offers Save frame: the frame on screen (paused there) is saved as a full-size JPEG in the video's folder, private groups included, dated at the video's time plus the position, so it sorts beside the video.
- **Review.** Every open album, private group, Private Favorites and location has a review button at the top. It shows the folder's photos one at a time, newest first, on black: swipe left (or the trash button) to let one go, right (or the check) to keep it; the photo leans and tints red or in the accent as it goes. The middle button takes back the last decision. Nothing is deleted while reviewing: closing (or reaching the end) shows the count and one step that moves the let-go photos to the trash (with undo), or deletes them for good inside Private.
- **Similar shots.** Neighbouring photos at most 8 seconds apart that look alike (a 64-bit difference hash of their thumbnails, at most 12 bits apart) fold into one tile: the newest shot, with a stack mark and the count at the top right. Every photo grid does this except the trash. Tapping a folded stack lays its shots out in place, each marked with its place (2/5); tapping that mark folds it back. Selecting a folded stack (tap or drag) selects every shot in it. Hashes are worked out once in the background and kept on disk; private photos are not hashed. Settings → Stack similar shots turns it off.
- **No back buttons.** Folders and the viewer are left with the system back gesture; the top row holds the month at its left end and review, add photos and settings at its right.
- **Every press vibrates.** Every pressable gives a tick from the vibrator itself (not view haptics, which One UI can mute), on tap and on long press.
- **Review progress.** Each folder's review remembers the furthest photo reached and the photos let go but not yet deleted. Opening review again offers Continue from that point (with the marked count) or Start fresh; the furthest count never goes down. Closing mid-way can keep the marks for later or unmark them. Every review card fills the screen on black.
- **Review: Done and resume.** A Done pill always sits above review's buttons: with nothing marked it (check mark) just ends the sitting; once anything is marked (trash mark and count) it deletes every marked photo at once and closes review (to the trash with undo; inside Private a second tap, as the delete is final). Reopening a review with saved progress shows a resume page: the last photo looked at, its date, the furthest count over the total with a progress bar, the marked count, and Start fresh / Continue.
- **Release signing (on hold).** Until the secrets below are added, releases keep the debug key. Once added, releases are signed with a dedicated release key (RSA 4096, "CN=VADOS Gallery, O=VADITIM"), not the debug key. The key is never in the repository; CI reads it from the secrets RELEASE_KEYSTORE_BASE64, RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS and RELEASE_KEY_PASSWORD, and falls back to the debug key while they are missing. Switching keys needs one uninstall. Losing the key means the next release cannot install over the old one.
- **Open groups stay open.** Album groups opened on the albums list stay open after looking at an album and going back. A swipe to the left on an opened group pulls its albums back into the stack, each by its own amount, the last ones hardest, so they land under one another. Tapping the group's name or count opens it like the stack does. Opened, the group's name stands over it as a heading that arrives with the VAS bar-sweep, and the card that closes it is the back glyph alone.
- **Readable sheets.** Grey text steps and borders are kept bright enough to read on the ground; rows in sheets and settings have dividers, and values show in the accent.
- **Review layout.** Choosing where to begin and swiping share one layout: the photo fills a rounded box, the decision buttons sit under it, and the date, position and progress bar below. Tapping the left half deletes, the right half keeps, swiping down takes back the last decision; swiping sideways still decides.
- **Review card and upcoming photos.** The review photo fills the whole screen on black; the upcoming photos, buttons and progress float over it, with a shade behind the bottom controls. The marked count sits above the position and always keeps its line. The next three photos are overlapped in a row of their own above the card, at the right, nearest in front and brightest.
- **Pull to close follows the finger.** In the viewer the buttons move out in step with a downward pull and return as it is released; letting go past the threshold continues the shrink from where it is, without a jump.
- **Every pull follows the finger.** Each gesture that changes the UI moves it in step with the finger and back on release: the viewer's swipe up raises the details sheet, review's swipe down brings the last photo back from above, an opened album group folds its albums back into the stack. The viewer's date stays put through a swipe up; only the bottom buttons give way to the details. Release past the point finishes the motion from where it is, then commits.
- **Review opens and closes in motion.** Review rises in from slightly below, growing and fading in, and sinks back out when closed.
- **Photo layout.** A setting (General → Photos → Layout) cuts every photo grid one of two ways. Months: whole months as before, the first photo of each day stamped in its top left corner with the day and calendar week (05/10/26 - CW41; the year left out from four columns up), on a backdrop. Weeks: months further apart, each split into its calendar weeks under a small header with the week and the span of its days (CW41 · 05/10 – 11/10).
- **Calendar weeks.** Calendar weeks (ISO, Monday first) show app-wide wherever a day is shown: the viewer's date and details, review's date, the grid's day stamps and week headers.
- **Top row in a folder.** The month pill sits at the left end; review and an add-photos button (albums, private groups, Favorites albums; not locations) sit at the right, before settings.
- **Renaming selects the name.** Every name sheet opens with its current name selected whole, so typing replaces it.
- **One Favorites album per photo.** A favourite already in a Favorites album is not offered when adding to another; adding a selection to an album moves those photos out of any other.
