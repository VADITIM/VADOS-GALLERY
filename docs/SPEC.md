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
- Tapping the bar entry of the section already shown scrolls home, to the newest end. Coming back to a section from another keeps it where it was left.
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
- **Long-press an album:** *Add photos*, *Move album to private*, *Delete album* (after Confirm;
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
  after the group by default; the emptied group is removed), *Delete group* (after Confirm — final).
- **Inside a private photo:** SHARE, DELETE (after Confirm — private photos are outside the
  system trash, so this one is final), and ••• → *Move to group*, *Move out to album*, *Details*.
- **Private favourites.** A favourite that goes into Private stays a favourite — but only as a
  *private* favourite, kept in the private folder itself (`.favorites`), never in MediaStore, so it can
  never appear in the normal FAVORITES. Moving it back out to an album makes it an ordinary favourite
  again. Private ends with a **Favorites** folder, a wide row at the very bottom, below the groups.
- **Album covers.** Long-pressing a photo anywhere (a little longer than the system's long press) selects it; a finger that lands on a moving grid, or just after it stopped, only stops the scroll and never selects, as in Recent; sliding the held finger across more tiles selects (or deselects) them along the row and scrolls at the edges. With exactly one photo selected inside an album, a private group or a Favorites album, COVER makes it the cover. The pill above the bar then says "Set as cover" for a few seconds, without an undo button. The cover is remembered per album (per group, inside the group's folder); until one is set, the newest photo is the cover.
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
- **Favorites albums.** Inside Favorites, favourites can be gathered into albums of their own, and those albums into groups; none of it touches folders or anything outside Favorites. A button in the top row switches between every favourite in one grid and the albums (the same cover grid, groups, rearranging and "New album" as ALBUMS). Selecting favourites shows the same action bar as albums (share, move, private, trash); move there picks among the Favorites albums (or a new one). An album holds favourites only, so an unfavourited photo leaves it and an emptied album disappears. Long-pressing an album or a group there opens the same menu as in Albums (see Cover menus); for a Favorites album, Hide from Recent keeps its photos out of Recent, Move to private takes its photos into Private, and delete removes the album only. Its albums can be selected and grouped, moved to Private or deleted from the selection bar.

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
| **MOVE TO ALBUM / 🔒** | One row: the left 70% picks an album and moves the item there — the file actually moves on disk; after a slash, the lock icon alone over the right 30% picks a private group (or makes one) and hides the item there. Inside Private: move to group / open lock to move out |
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
| A private group, Private Favorites | SHARE · GROUP (to another group) · OUT (back to an album) · DELETE (forever, after Confirm) |

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

- **Settings.** A button at the top right of every view opens a sheet in three tabs. The first is named after the place you are in (Recent, Albums, Favorites, Locations, Trash, Private) and holds that place's own settings: in a photo grid, Photos (image columns, layout, month headers, similar shots); on a screen of albums or groups, Albums (grouped albums in Albums and Favorites, then album columns, greyed out while grouping is on — grouped albums lie as rows). General, the same everywhere: Videos (autoplay), then Review photos, which sorts through the place's photos where it has any. Interface: Overlays (blur and opacity of the glass) and Background (the brightness of the app's ground, from black to a dark charcoal; the viewer stays black). Stored in preferences (`Settings.kt`) and applied at once.
- **Section colours stay with their section.** Switching sections, the outgoing one keeps its own accent while it leaves; only the bar and the top row blend to the new one.
- **Locations.** Each photo's GPS (EXIF, or a video's location atom) is read once with the media-location permission and named by the system geocoder per ~1 km cell; both are cached in app files. Details shows the city, country and coordinates. A Locations row above Private in Albums lists cities by photo count; each opens as a grid.
- **Trash.** A Trash row (with count) sits between Locations and Private. It lists the system trash (Android keeps items 30 days); tapping selects, and the bar restores (back where it was, or to an album) or deletes forever (after Confirm).
- **Rename.** Long-pressing an album or private group offers Rename. Renaming an album changes only the name shown in the app (stored by folder path); the folder on disk keeps its name, so the camera and other apps keep saving into it and nothing moves. Giving it the folder's name again clears the rename. Private groups are folders inside the app's own Private store, which nothing else writes into, so they are renamed on disk.
- **Moving into Private** is confirmed once more after the group is chosen (selection, whole album, viewer).
- **Icons, not words,** on every action bar and menu row: share, cover (image), move, private (lock; open lock for moving out), trash, rename (pen), restore, add, close, details, more.
- The top row keeps a fixed-width month chip so the buttons beside it never move. In the bottom right corner, just above the nav, on a small glass pill, how many photos of the grid on screen were taken in the month shown, out of all of them, as 34/1000 (typed over like the month when it changes). Inside a folder (an album, a group, Trash, Locations or a location, a Favorites album) a back button sits at the far left, before the month, doing what the system back does. When the month changes it types itself over (deleting back to what the old and new share, then typing the rest, with a blinking block caret, as in VADOS Bubble). Top buttons that come or go with the place (back, add photos, the Favorites view toggle) pop in place: they grow from nothing past full size and settle, and shrink away to nothing, never sliding; the toggle's icon swaps in motion; selecting swaps the row with a fade. Where a back button comes or goes, it pops away before the month slides into its place, and the month slides over before it pops in.
- **Album layout.** Pinch the Albums grid (or Settings → Album columns) for 1–4 albums per row. Long-press an album → Rearrange albums: drag cards into any order, confirm with the check. The order is stored by folder path.
- **Today's selection** plays a picked video silently on a loop.
- **Haptics.** Every action answers with a vibration: a click on icon buttons and menu rows, toggle on/off in Settings, ticks on sliders, column changes, each photo or cover added to or removed from a selection (by tap or drag), each album swap while rearranging, a threshold buzz when a rearrange drag starts, a confirm when rearranging ends. A short double click when something has been moved, deleted, restored or favourited. Opening and closing photos stay silent.
- **Album names fit:** at three or four per row the name and count shrink until they fit (down to 9sp), then cut. One per row is a list: small cover at the start, large name and count beside it.
- **Month chip** reads "September 25" (two-digit year) and never wraps.
- **One cover grid.** Albums, private groups and locations share `CoverGrid`/`CoverCard`: the same columns (pinch or Settings), list layout at one column, shrinking names. Albums and private groups can be rearranged (long-press → Rearrange), by dragging a card onto another.
- **Trash photos open** in the viewer on a tap (long press selects); the viewer there offers share, restore and delete forever (after Confirm).

- The blur along the top edge fades evenly: strongest at the screen edge, easing out steadily the further down it goes, over a short band (half its first height).
- Grouped albums (a setting): albums can be put into groups, as many as wanted. A group lies in the albums grid as a stack of its covers, each card leaning a little. Tapping it lays its albums out in the grid from its place onwards, wrapping by the album columns, under the group's name as a heading, with a back arrow at the right end of that heading that stacks them back. An album's menu adds it to a group, moves it to another, or takes it out; a group's menu renames it, rearranges, or ungroups it. Turning the setting off shows every album on its own again and keeps the groups for later.
- Renaming an album keeps its place in the arranged order and in its group.
- Crop (viewer → More): photos are cropped, videos cropped and trimmed. Free or a fixed ratio (Original, 1:1, 4:5, 4:3, 16:9, 9:16). The result is saved as a copy beside the original with the original's date and location; the original is never changed. Available in Private as well.
- Grid tiles bigger than the system thumbnail (few columns) load the photo itself once scrolling stops; while flinging only the cached thumbnails are read.
- Video controls are one slim bar (play, time, timeline, length, loop, sound). Sound off holds for every video while the app runs.
- Album groups each take a row of their own; the cards under the cover fan out to the right.
- Covers can be selected several at once (menu → Select, then tap): albums can then be grouped, moved to Private or deleted; private groups moved out or deleted. Deleting waits for Confirm above the bar.
- While rearranging, covers jiggle slightly. Double-tapping a zoomed photo always returns it to its original size.
- Rearranging with a group open orders that group's albums; with groups closed, it orders albums and groups. Opening a group peels its albums off the stack one after another; the rows below are pushed by the cards as they go, never crossed. Closing, the group's name sweeps back in beside the stack the way its heading sweeps in on opening.
- A group is always shown as a one-column row (stack, then name and count). Opened, its albums lie three to a row. Several groups can be open at once.
- The viewer's buttons are not part of the photo: on a swipe down, on closing, or on a tap they slide off their own edge one after another, and come back the same way once the photo has settled.
- The section bar's highlight slides from section to section, its leading edge first, and the chosen label grows slightly.
- Every photo grid and cover grid opens with a short cascade: its first screenful fades and rises into place, item after item. Items reached later by scrolling are simply there.
- Favourites carry a small red heart at the bottom right of their tile in every grid (left of a video's length). Every favourite heart is red, never the section colour.
- **Timeline.** Every photo grid (Recent, Favorites, albums, groups, locations, Private, the picker) has a timeline drawn along its right edge, which either edge of the grid takes hold of: every year and all its months, oldest at the top, fitted whole into a short window (under a third of the screen tall) centred on the grid. Labels swell around the marker and shrink away from it, and their gaps are a share of their own size, so gaps shrink with them and none overlap. The curve adapts to the strip: a strip that fits keeps the gentlest curve and sits in the middle of the window without stretched gaps; a longer one falls off just steeply enough to fill the window, so any number of years and months looks the same. Far months fade to nothing; years never shrink below a floor and always show, the outermost at the window's ends with no gap beyond the strip's own; only when the years alone overflow does the whole strip scale down. As the grid scrolls the marker runs down the window, reaching the newest month at the grid's end, and the month on the marker always shows. The month shown and its year are in the section colour. Holding and sliding on either edge's strip, or on the labels, puts the marker under the finger, so the label under the finger is where the grid goes (a year goes to its first month): the grid jumps month by month, each month's first photo at the top (instantly), shows the month in a bubble beside the timeline and ticks on each new month. Only the window's stretch of each edge, and the labels in it, take a finger.
- **Undo.** Moving to the trash or into another album (from a selection, an album's menu or the viewer) shows a pill above the bar for a few seconds: what happened and an undo button. Undo puts the photos back where they were. The final deletes (Private, Trash) and hiding into Private are already confirmed and have no undo.
- **Hide from Recent.** Long-pressing an album (in Albums or Favorites) offers Hide from Recent (Show in Recent to undo it). Its photos leave the Recent grid and the viewer opened from it; the album itself, Favorites, Locations and the photo picker still show them. Renaming the album keeps the choice.
- **Motion photos.** A photo with a clip appended (Samsung's and Google's format) carries a motion mark at the bottom right of its tile in every grid and beside the date in the viewer. Holding it in the viewer plays the clip on a loop, with sound unless video sound is off; letting go returns to the still. The clip is read straight out of the photo's file, so nothing is copied, private photos included.
- **Save frame.** A video's ••• menu offers Save frame: the frame on screen (paused there) is saved as a full-size JPEG in the video's folder, private groups included, dated at the video's time plus the position, so it sorts beside the video.
- **Review.** Recent, the Favorites grid and every open album, private group, Favorites album, Private Favorites and location can be reviewed from Settings → General → Review photos. It shows the folder's photos one at a time, newest first, on black: swipe left (or the trash button) to let one go, right (or the check) to keep it; the photo leans and tints red or in the accent as it goes. The middle button takes back the last decision. Nothing is deleted while reviewing: closing (or reaching the end) shows the count and one step that moves the let-go photos to the trash (with undo), or deletes them for good inside Private.
- **Similar shots.** Neighbouring photos at most 8 seconds apart that look alike (a 64-bit difference hash of their thumbnails, at most 12 bits apart) fold into one tile: the newest shot, with a stack mark and the count at the top right. Every photo grid does this except the trash. Tapping a folded stack lays its shots out in place, each marked with its place (2/5); tapping that mark folds it back. Selecting a folded stack (tap or drag) selects every shot in it. Hashes are worked out once in the background and kept on disk; private photos are not hashed. Settings → Stack similar shots turns it off.
- **Back buttons.** The viewer is left with the system back gesture; a folder also by the back button at the far left of the top row, before the month, with add photos and settings at the right.
- **Every press vibrates.** Every pressable gives a tick from the vibrator itself (not view haptics, which One UI can mute), on tap and on long press.
- **Review progress.** Each folder's review remembers the furthest photo reached and the photos let go but not yet deleted. Opening review again always picks up at that point with the marks carried over, with no prompt; a folder reviewed to the end with nothing marked begins again from the newest. A reset button at the top left starts over from the newest photo and clears the saved point and marks (a second tap, shown in red, while anything is marked). Closing mid-way can keep the marks for later or unmark them. Every review card fills the screen on black.
- **Review: Done and resume.** A Done pill always sits above review's buttons: with nothing marked it (check mark) just ends the sitting; once anything is marked (trash mark and count) it deletes every marked photo at once and closes review (to the trash with undo; inside Private a second tap, as the delete is final).
- **Release signing (on hold).** Until the secrets below are added, releases keep the debug key. Once added, releases are signed with a dedicated release key (RSA 4096, "CN=VADOS Gallery, O=VADITIM"), not the debug key. The key is never in the repository; CI reads it from the secrets RELEASE_KEYSTORE_BASE64, RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS and RELEASE_KEY_PASSWORD, and falls back to the debug key while they are missing. Switching keys needs one uninstall. Losing the key means the next release cannot install over the old one.
- **Open groups stay open.** Album groups opened on the albums list stay open after looking at an album and going back. A swipe to the left on an opened group pulls its albums back into the stack, each by its own amount, the last ones hardest, so they land under one another. Tapping the group's name or count opens it like the stack does. Opened, the group's name stands over it as a heading that arrives with the VAS bar-sweep, and the card that closes it is the back glyph alone.
- **Readable sheets.** Grey text steps and borders are kept bright enough to read on the ground; rows in sheets and settings have dividers, and values show in the accent.
- **Review layout.** The photo fills a rounded box, the decision buttons sit under it, and the date, position and progress bar below. Tapping the left half deletes, the right half keeps, swiping down takes back the last decision; swiping sideways still decides.
- **Review card and upcoming photos.** The review photo fills the whole screen on black; the upcoming photos, buttons and progress float over it, with a shade behind the bottom controls. The marked count sits above the position and always keeps its line. The next three photos are overlapped in a row of their own above the card, at the right, nearest in front and brightest.
- **Pull to close follows the finger.** In the viewer the buttons move out in step with a downward pull and return as it is released; letting go past the threshold continues the shrink from where it is, without a jump.
- **Every pull follows the finger.** Each gesture that changes the UI moves it in step with the finger and back on release: the viewer's swipe up raises the details sheet, review's swipe down brings the last photo back from above, an opened album group folds its albums back into the stack. The viewer's date stays put through a swipe up; only the bottom buttons give way to the details. Release past the point finishes the motion from where it is, then commits.
- **Review opens and closes in motion.** Review rises in from slightly below, growing and fading in, and sinks back out when closed.
- **Photo layout.** A setting (General → Photos → Layout) cuts every photo grid one of two ways. Months: whole months as before, the first photo of each day stamped in its top left corner with the day and calendar week (05/10/26 - CW41; the year left out from four columns up), on a backdrop. Weeks: months further apart, each split into its calendar weeks under a small header with the week and the span of its days (CW41 · 05/10 – 11/10).
- **Calendar weeks.** Calendar weeks (ISO, Monday first) show app-wide wherever a day is shown: the viewer's date and details, review's date, the grid's day stamps and week headers.
- **Top row in a folder.** Back and the month pill sit at the left end; an add-photos button (albums, private groups, Favorites albums; not locations) sits at the right, before settings.
- **Renaming selects the name.** Every name sheet opens with its current name selected whole, so typing replaces it.
- **One Favorites album per photo.** A favourite already in a Favorites album is not offered when adding to another; adding a selection to an album moves those photos out of any other.
- **Dates opposite the timeline.** In every photo grid the month and week headers sit at the left, in the section colour and a little larger, and the day stamps at each tile's top left, across from the timeline on the right; a similar-shot stack's mark stays at the top right.
- **Favorites albums in the section colour.** The names of the albums made inside Favorites are in its colour, setting them apart from real folders.
- **Cover menus.** Every long-pressed album (Albums or Favorites) has the same menu, alternatives sharing one row (words on the left 70%, the other as its icon on the right 30%, split by a slash). Under ALBUMS: Hide from Recent / delete (trash; the menu closes and the delete waits for Confirm), Remove from group when the album is in one, and Add photos at the bottom. Under EDIT: Rename; Move to group (Add to group) / private (lock), or Move album to private alone while grouping is off. Select / rearrange is always the last row. Every long-pressed group follows the same layout: GROUPS holds Ungroup all (Delete group for private groups), EDIT holds Rename (and for private groups Move group out and Add photos), and Select / rearrange closes it. Albums selected from the menu get the same actions in the bar: move to group, private, hide from Recent (or show again once all are hidden), and delete.
- **Rearranging drags at once.** While rearranging, a held card has no long press, so holding a card and then dragging always moves it.
- **The bar inside Private.** Once in Private, the bar's sections show Private's own photos until it is left: Recent is every private photo in one grid, Albums is the private groups, and Favorites the private favourites, either as one grid or grouped by private group (each group a cover of only its favourites; the top-row toggle switches, and Private remembers its own choice apart from Favorites outside). The private favourites folder inside the groups is gone, since Favorites covers it. Back from Private's Recent or Favorites goes to its groups, and back from the groups leaves Private; locking leaves it too. Above the bar, centred, a small PRIVATE pill with a back button beside it on the left says the bar is Private's, and leaves Private. Inside Private every section takes Private's own colour (#FA3438). Review works in Private's Recent and Favorites.
- **Colour on the cut.** A change of section or place recolours the buttons only once the outgoing view has left.
- **Place colours.** Locations is blue (#148BC7), the trash grey and Private red (#FA3438): each takes its colour in its own view (the bar, the buttons, the headings) and on its tile at the end of Albums. There Private, Locations and Trash are three tiles in one row, each as big as an album cover with its icon (a lock, a pin, a bin) larger in the middle and its name below, set apart from the albums by a hairline with a wider gap.
- **The bar inside Locations.** In Locations and inside a location, the same pill sits above the bar: LOCATIONS with a back button beside it on the left, which goes back to the locations from a location, and to the albums from Locations.
- **Delete now.** Above the bar in the trash, a DELETE NOW button empties the whole trash for good, after Confirm.
- **Confirm.** Every delete button works as the trash's: in every grid's bar, every cover menu and the viewer, the delete is not done at the button: a CONFIRM pill rises above the bar (in the viewer above its buttons), over anything already there, and deletes on tap; anything else lets it go. Review's Done is its own confirmation.
- **Restore from the trash.** Restore asks first: Restore puts each back where it was, Restore to album picks an album (or a new one) and puts the selection there.
- **Settings per view.** Recent, Albums, Favorites, Locations, Trash and Private each keep their own image columns, layout, month headers and stacking of similar shots, and (where they show covers) their own album columns; Albums and Favorites each have their own Grouped albums (Favorites starts with it on). The settings sheet's first tab is named after the view it is changing and shows only what applies there. The General and Interface tabs stay the same everywhere. Each view starts from what the single setting was before.
- **The bottom bar animates.** Switching between the sections bar, a selection's actions and the rearrange check pops one out and the next in, scaling and fading, rather than cutting.
- **Photo quality in the viewer.** Photos are drawn with smooth (high-quality) filtering; once zoomed in, the full-resolution picture (up to 4096 px a side) loads over the screen-sized one. The viewer, review and photos opened from other apps ask the window for HDR (Android 14+), so Ultra HDR photos from the camera show their bright highlights as in the Samsung gallery.
- **Back closes sheets.** The back gesture closes whatever sheet or menu is open (settings, long-press menus, pickers, naming), never the screen behind it.
- **Adding to an album offers only what is not placed yet.** The add screen of a folder album leaves out photos already in that album and, while grouping is on, photos in any album sorted into a group.
- **Set as cover from the viewer.** A photo opened from an album, a private group or a Favorites album has Set as cover in its More menu; it shows the "Set as cover" pill.
- **Favourite a selection.** The bar of a photo selection has a heart: it favourites every selected photo, or, once all of them are favourites, takes them all out.
