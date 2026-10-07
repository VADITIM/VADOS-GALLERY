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
| **RECENT** | Every photo and video on the phone, in one grid | Terminal green `#2fde75` |
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
  after the group by default; the emptied group is removed), *Delete group* (after Confirm; its photos go to Private's trash).
- **Inside a private photo:** SHARE, DELETE (after Confirm, to Private's own trash, with
  undo), and ••• → *Move to group*, *Move out to album*, *Details*.
- **Private favourites.** A favourite that goes into Private stays a favourite — but only as a
  *private* favourite, kept in the private folder itself (`.favorites`), never in MediaStore, so it can
  never appear in the normal FAVORITES. Moving it back out to an album makes it an ordinary favourite
  again. Private ends with a **Favorites** folder, a wide row at the very bottom, below the groups.
- **Album covers.** Long-pressing a photo anywhere (a little longer than the system's long press) selects it; a finger that lands on a grid that is really scrolling (fast) only stops the scroll and never selects, while a slight drift does not get in the way, as in Recent; sliding the held finger across more tiles selects (or deselects) them along the row and scrolls at the edges. With exactly one photo selected inside an album, a private group or a Favorites album, COVER makes it the cover. The pill above the bar then says "Set as cover" for a few seconds, without an undo button. The cover is remembered per album (per group, inside the group's folder); until one is set, the newest photo is the cover.
- **"Today's selection"** — a large card at the top of Private showing one random private
  favourite; a new pick every time Private is entered, kept while scrolling. Private's settings tab
  has a Today's selection switch that shows or hides it.
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
- **Favorites albums.** Inside Favorites, favourites can be gathered into albums of their own, and those albums into groups; none of it touches folders or anything outside Favorites. A button in the top row switches between every favourite in one grid and the albums (the same cover grid, groups, rearranging and "New album" as ALBUMS). Selecting favourites shows the same action bar as albums (share, move, private, trash); move there picks among the Favorites albums (or a new one). An album holds favourites only, so an unfavourited photo leaves it and an emptied album disappears. Long-pressing an album or a group there opens the same menu as in Albums (see Cover menus); for a Favorites album, Move to private takes its photos into Private, and delete removes the album only. Its albums can be selected and grouped, moved to Private or deleted from the selection bar.

## The viewer

Tapping a thumbnail opens it full screen, on black.

- Swipe sideways through the neighbours of the list it was opened from.
- Tap toggles the chrome.
- A back button at the top left closes it, as the system back gesture or a swipe down does (an open sheet closes first). It comes and goes with the rest of the viewer's chrome.
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
| **FAVORITE** | Toggles favourite, lit in the section accent when on; turning it on pops the heart and throws dots and sparkles off it, like a like button; turning it off drains the fill out of the heart from the top while it dips a little, rather than swapping icons. Swiping to another photo never plays either |
| **DELETE** | Moves to the trash (recoverable for 30 days, the system's own trash) |
| **•••** | The menu below |

### The ••• menu

Four entries. That is the whole menu.

| Entry | What it does |
|---|---|
| **MOVE TO ALBUM / 🔒** | One row: the left 70% picks an album and moves the item there — the file actually moves on disk; after a slash, the lock icon alone over the right 30% picks a private group (or makes one) and hides the item there. Inside Private: move to album / open lock to move out; in Private's Recent only the open lock |
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
| A private album, Private Favorites | SHARE · MOVE (to another private album) · OUT (back to an album) · DELETE (to Private's trash, after Confirm) |
| Private's Recent | SHARE · OUT (back to an album) · DELETE (to Private's trash, after Confirm) |

**Drag to select:** hold a thumbnail and slide across others to select or unselect the run. With nothing selected the hold is a long press; once a selection is open a brief rest (150 ms) is enough, so swiping across more photos starts almost at once.

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

- **Settings.** The button at the right end of the top row opens a sheet that runs from just under the status bar, 4dp clear of it (covering the top row and its settings button) to just over the navigation bar, its three tabs along the top as one segmented track, each tab's content starting at the top under them, and a faint V/AS at the foot, with the app's version in the same style at its right end; the finger swipes sideways between tabs, the content and the tab pill following it and settling on the nearer tab when let go, the content fading out at the sheet's sides while it slides, and a tapped tab slides there the same way. Inside an album, group or location the first tab names it on a second line under the view (FAVORITES / DESIGN); the screen behind blurs slightly while it is open, and a grid still gliding stops as it opens. Every group of settings is a card of its own (a hairline edge, a darker fill, its name in the section colour at the top left), with one spacing for every card, row and edge; setting names are all the same bright face and values are plain bright. The first tab is named after the place you are in (Recent, Albums, Favorites, Locations, Trash, Private) and holds that place's own settings: a Review photos button first, where the place has photos; in a photo grid, Photos (stack similar shots, except in the trash; image columns 1–6; Headers - Layout; Own settings in an album); on a screen of albums or groups, Albums (grouped albums in Albums and Favorites as a switch, then album columns 1–4, greyed out while grouping is on — grouped albums lie as rows); in Private, Private (today's selection). General, the same everywhere: Videos (autoplay) and Backup (Back up and Restore side by side, and under them when the last backup was saved). Interface: Tiles (day stamps, folder label), Glass (blur and opacity) and Background (the brightness of the app's ground, from black to a dark charcoal; the viewer stays black). Switches are outlined with a bright knob while off and fill with the accent on, sliding their knob on the overshoot; every one-of-many pick (columns, folder label, the tabs) is the same segmented track, whose pill follows the finger and settles on the nearest stop when let go; sliders move only by how far the finger travels sideways, so a finger passing over one on its way down changes nothing, and a finger resting on one thickens its track; buttons are outlined. Stored in preferences (`Settings.kt`) and applied at once.
- **Section colours stay with their section.** Switching sections, the outgoing one keeps its own accent while it leaves; only the bar and the top row blend to the new one.
- **Locations.** Each photo's GPS (EXIF, or a video's location atom) is read once with the media-location permission and named by the system geocoder per ~1 km cell; both are cached in app files. Details shows the city, country and coordinates. A Locations row above Private in Albums lists cities by photo count; each opens as a grid.
- **Trash.** A Trash row (with count) sits between Locations and Private. It lists the system trash (Android keeps items 30 days); tapping selects, and the bar restores (back where it was, or to an album) or deletes forever (after Confirm).
- **Rename.** Long-pressing an album or private group offers Rename. Renaming an album changes only the name shown in the app (stored by folder path); the folder on disk keeps its name, so the camera and other apps keep saving into it and nothing moves. Giving it the folder's name again clears the rename. Private groups are folders inside the app's own Private store, which nothing else writes into, so they are renamed on disk.
- **Moving into Private** is confirmed once more after the group is chosen (selection, whole album, viewer).
- **Icons, not words,** on every action bar and menu row: share, cover (image), move, private (lock; open lock for moving out), trash, rename (pen), restore, add, close, details, more.
- The top row keeps a fixed-width month chip so the buttons beside it never move; while the folder label is at Top the whole pill is filled with the section colour, its text in ink; while it is at Bottom it is the glass pill with the month in the section colour. Its colour, too, changes as the new text starts typing. In the bottom left corner, just above the nav, on a small glass pill, how many photos of the grid on screen were taken in the month shown, out of all of them, as 34/1000: each number sits in its own slot against the slash, both as wide as the total's digits with even-width digits, so neither number nor the slash moves when the other gains a digit, and each types itself over when it changes. The count keeps its height whether or not the favourites-only heart above it is shown. In the bottom right corner a heart toggles only favourites in the photo grid on screen (Recent, an album, a group, a location; not Favorites or the trash); the count follows it, and changing place turns it off. Inside a folder (an album, a group, Trash, Locations or a location, a Favorites album) a back button sits at the far left, before the month, doing what the system back does. On opening the app the month, the count and the bar's labels type themselves in from nothing as the photos arrive. When the month changes it types itself over (deleting back to what the old and new share, then typing the rest, with a blinking block caret, as in VADOS Bubble). Top buttons that come or go with the place (back, add photos, the Favorites view toggle) pop in place: they grow from nothing past full size and settle, and shrink away to nothing, never sliding; the toggle's icon swaps in motion; selecting swaps the row: its buttons pop away one by one, then the selection's pop in. The bottom bar is one glass pill for the nav and every selection bar: changing kind, its buttons pop away and the new ones pop in while the pill's width follows. Where a back button comes or goes, it pops away before the month slides into its place, and the month slides over before it pops in.
- **Album layout.** Pinch the Albums grid (or Settings → Album columns) for 1–4 albums per row. With Grouped albums on, the albums outside the groups have a count of their own, 1–3 per row (starting at 1), set the same two ways; a group's own cards stay three to a row. Long-press an album → Rearrange albums: drag cards into any order, confirm with the check. The order is stored by folder path.
- **Today's selection** plays a picked video silently on a loop.
- **Haptics.** Every action answers with a vibration: a click on icon buttons and menu rows, toggle on/off in Settings, ticks on sliders, column changes, each photo or cover added to or removed from a selection (by tap or drag), each album swap while rearranging, a threshold buzz when a rearrange drag starts, a confirm when rearranging ends. A short double click when something has been moved, deleted, restored or favourited. Opening and closing photos stay silent.
- **Album names fit:** at three or four per row the name and count shrink until they fit (down to 9sp), then cut. One per row is a list: small cover at the start, large name and count beside it.
- **Month chip** reads "September 25" (two-digit year) and never wraps.
- **One cover grid.** Albums, private groups and locations share `CoverGrid`/`CoverCard`: the same columns (pinch or Settings), list layout at one column, shrinking names. Albums and private groups can be rearranged (long-press → Rearrange), by dragging a card onto another.
- **Order by deletion.** In either trash (the system's and Private's) the Photos card of the settings sheet has Order by deletion, off to start with. On, the photos lie oldest to newest by the day they were moved to the trash, with that day as their only header (no year, month or week); the timeline and top pill follow the same day, and the headers layout is greyed out meanwhile. Off, they lie by their own date as in every grid. One setting for both trashes. The system trash's day is its 30-day expiry less 30 days; Samsung's trash has no readable move time, so its files' own time stands in.
- **Trash photos open** in the viewer on a tap (long press selects); the viewer there offers share, restore and delete forever (after Confirm).

- Nothing blurs the top edge of the screen: the grid runs plainly under the clock.
- Grouped albums (a setting): albums can be put into groups, as many as wanted. A group lies in the albums grid as a stack of its covers, each card leaning a little. Tapping it lays its albums out in the grid from its place onwards, wrapping by the album columns, under the group's name as a heading, with a back arrow at the right end of that heading that stacks them back. An album's menu adds it to a group, moves it to another, or takes it out; a group's menu renames it, rearranges, or ungroups it. Turning the setting off shows every album on its own again and keeps the groups for later.
- Renaming an album keeps its place in the arranged order and in its group.
- Crop (the viewer's bar, between the heart and delete): photos are cropped, videos cropped and trimmed. Free or a fixed ratio (Original, 1:1, 4:5, 4:3, 16:9, 9:16). The result is saved as a copy beside the original with the original's date and location; the original is never changed. Available in Private as well. Crop has its own accent, violet #7E55DD, for photos and videos alike. Opening it, the picture travels from where the viewer showed it into the crop frame and the controls come in from the edges; leaving runs it back (instant with system animations off). Back is the album back button. The ratios sit in a floating nav pill (the nav's own component). Undo, redo and save (a tick) sit centred at the bottom as icons in the crop colour, each faded while it has nothing to do. Every edit (the frame, the ratio, the zoom, the trim) becomes one undo step once the finger rests; undo steps back, redo forward again, and holding undo reverts everything as one more step. On a photo, pinch (or a mouse wheel) zooms the picture under the frame, which stays put; two fingers, or one outside the frame once zoomed, pan it; what is saved is what the frame covers. Each end of a video's cut has a back and a forward arrow that move it by 0.1 s, never past the video's ends nor across the other end; while an end or the playhead is dragged the preview seeks once a frame, so it always shows the frame under the finger.
- Grid tiles bigger than the system thumbnail (few columns) load the photo itself once scrolling stops; while flinging only the cached thumbnails are read.
- Video controls are one slim bar (play, time, timeline, length, loop, sound). Sound off holds for every video while the app runs.
- Album groups each take a row of their own; the cards under the cover fan out to the right.
- Covers can be selected several at once (menu → Select, then tap): albums can then be grouped, moved to Private or deleted; private groups moved out or deleted. Deleting waits for Confirm above the bar.
- While rearranging, covers jiggle slightly. Double-tapping a zoomed photo always returns it to its original size.
- Rearranging with a group open orders that group's albums; with groups closed, it orders albums and groups. Opening a group peels its albums off the stack one after another; the rows below are pushed by the cards as they go, never crossed. Closing, the group's name beside the stack comes back from its left end in step with the cards, never swept in.
- A group is always shown as a one-column row (stack, then name and count). Opened, its albums lie three to a row. Several groups can be open at once.
- The viewer's buttons are not part of the photo: on a swipe down, on closing, or on a tap they slide off their own edge one after another, and come back the same way once the photo has settled.
- The section bar's highlight slides from section to section, its leading edge first, and the chosen label grows slightly.
- Every photo grid and cover grid opens with a short cascade: its first screenful fades and rises into place, item after item. Items reached later by scrolling are simply there.
- Favourites carry a small red heart at the bottom right of their tile in every grid (left of a video's length). Every favourite heart is red, never the section colour.
- **Timeline.** Every photo grid (Recent, Favorites, albums, groups, locations, Private, the picker) has a timeline drawn along its right edge, which only the right edge takes hold of: every year and all its months, oldest at the top, fitted whole into a short window (under a third of the screen tall) centred on the grid. Labels swell around the marker and shrink away from it, and their gaps are a share of their own size, so gaps shrink with them and none overlap. The curve adapts to the strip: a strip that fits keeps the gentlest curve and sits in the middle of the window without stretched gaps; a longer one falls off just steeply enough to fill the window, so any number of years and months looks the same. Far months fade to nothing; years never shrink below a floor and always show, the outermost at the window's ends with no gap beyond the strip's own; only when the years alone overflow does the whole strip scale down. As the grid scrolls the marker runs down the window, reaching the newest month at the grid's end, and the month on the marker always shows. The month shown and its year are in the section colour. Holding and sliding on the right edge's strip, or on the labels, puts the marker under the finger, so the label under the finger is where the grid goes (a year goes to its first month): the grid jumps month by month, each month's first photo at the top (instantly), and ticks on each new month. The month under the finger steps out of the strip to the left, beside the finger, comes to full size and widens into its full name and year (OCT into OCTOBER 2026), the end it has not reached yet fading; moving on to another month hands this over, the old one sliding back into the strip as the new one steps out, and letting go puts it back. No separate bubble. Only the window's stretch of the right edge, and the labels in it, take a finger.
- **Undo.** Moving to the trash or into another album (from a selection, an album's menu or the viewer) shows a pill above the bar for the same short time whatever the change was: what happened and an undo button. Undo puts the photos back where they were. Deleting inside Private goes to Private's trash with the same undo. Deleting for good from either trash and hiding into Private are already confirmed and have no undo.
- **Motion photos.** A photo with a clip appended (Samsung's and Google's format) carries a motion mark at the bottom right of its tile in every grid and beside the date in the viewer. Holding it in the viewer plays the clip on a loop, with sound unless video sound is off; letting go returns to the still. The clip is read straight out of the photo's file, so nothing is copied, private photos included.
- **Save frame.** A video's ••• menu offers Save frame: the frame on screen (paused there) is saved as a full-size JPEG in the video's folder, private groups included, dated at the video's time plus the position, so it sorts beside the video.
- **Review.** Recent, the Favorites grid and every open album, private group, Favorites album, Private Favorites and location can be reviewed from Settings → General → Review photos. It shows the folder's photos one at a time, newest first, on black: swipe left (or the trash button) to let one go, right (or the check) to keep it; the photo leans and tints red or in the accent as it goes. The middle button takes back the last decision. Nothing is deleted while reviewing: closing (or reaching the end) shows the count and one step that moves the let-go photos to the trash (with undo), inside Private to Private's trash.
- **Similar shots.** Neighbouring photos at most 8 seconds apart that look alike (a 64-bit difference hash of their thumbnails, at most 12 bits apart) fold into one tile: the newest shot, with a stack mark and the count at the top right. Every photo grid does this except the trash. Tapping a folded stack lays its shots out in place, each marked with its place (2/5); tapping that mark folds it back. Selecting a folded stack (tap or drag) selects every shot in it. Hashes are worked out once in the background and kept on disk; private photos are not hashed. Settings → Photos → Stack similar shots turns it off, per view.
- **Back buttons.** The viewer is left with the system back gesture; a folder also by the back button at the far left of the top row, before the month, with add photos and settings at the right.
- **Every press vibrates.** Every pressable gives a tick from the vibrator itself (not view haptics, which One UI can mute), on tap and on long press.
- **Review progress.** Each folder's review remembers the furthest photo reached and the photos let go but not yet deleted. Opening review again always picks up at that point with the marks carried over, with no prompt; a folder reviewed to the end with nothing marked begins again from the newest. A reset button at the top left starts over from the newest photo and clears the saved point and marks (a second tap, shown in red, while anything is marked). Closing mid-way can keep the marks for later or unmark them. Every review card fills the screen on black.
- **Review: Done and resume.** A Done pill, filled with the section colour and white on it so it is found at a glance, always sits above review's buttons: with nothing marked it (check mark) just ends the sitting; once anything is marked (trash mark and count) it deletes every marked photo at once and closes review (to the trash with undo; inside Private to Private's trash).
- **Release signing (on hold).** Until the secrets below are added, releases keep the debug key. Once added, releases are signed with a dedicated release key (RSA 4096, "CN=VADOS Gallery, O=VADITIM"), not the debug key. The key is never in the repository; CI reads it from the secrets RELEASE_KEYSTORE_BASE64, RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS and RELEASE_KEY_PASSWORD, and falls back to the debug key while they are missing. Switching keys needs one uninstall. Losing the key means the next release cannot install over the old one.
- **Open groups stay open.** Album groups opened on the albums list stay open after looking at an album and going back. A swipe to the left on an opened group pulls its albums back into the stack, each by its own amount, the last ones hardest, so they land under one another. Tapping the group's name or count opens it like the stack does. Opened, the group's name stands over it as a heading that arrives with the VAS bar-sweep, and the card that closes it is the back glyph alone.
- **Readable sheets.** Grey text steps and borders are kept bright enough to read on the ground; rows in sheets have dividers and values show in the accent; settings group their rows into cards (see Settings).
- **Review layout.** The photo fills a rounded box, the decision buttons sit under it, and the date, position and progress bar below. Tapping the left half deletes, the right half keeps, swiping down takes back the last decision; swiping sideways still decides.
- **Review card and upcoming photos.** The review photo fills the whole screen on black; the upcoming photos, buttons and progress float over it, with a shade behind the bottom controls. The marked count sits above the position and always keeps its line. The next three photos are overlapped in a row of their own above the card, at the right, nearest in front and brightest.
- **Pull to close follows the finger.** In the viewer the buttons move out in step with a downward pull and return as it is released; letting go past the threshold continues the shrink from where it is, without a jump.
- **Every pull follows the finger.** Each gesture that changes the UI moves it in step with the finger and back on release: the viewer's swipe up raises the details sheet, review's swipe down brings the last photo back from above, an opened album group folds its albums back into the stack. The viewer's date stays put through a swipe up; only the bottom buttons give way to the details. Release past the point finishes the motion from where it is, then commits.
- **Review opens and closes in motion.** Review rises in from slightly below, growing and fading in, and sinks back out when closed.
- **Photo layout.** Settings → Headers - Layout cuts every photo grid by any of Days, Weeks, Months and Years at once (at least one), each under its own header (a year large, a month in the accent, a week small with the calendar week only: CW41, a day smallest: MON 05/10). The Headers switch directly under the four buttons, in the same block with no gap, turns every cut off: the buttons grey out and keep their pick for when it comes back on. Without days or weeks, the first photo of each day is stamped in its top left corner with the day and calendar week (05/10/26 - CW41), only up to three columns. With Headers off there are no cuts and no tile stamps. Settings → Interface → Day stamps turns the tile stamps off everywhere.
- **Calendar weeks.** Calendar weeks (ISO, Monday first) show app-wide wherever a day is shown: the viewer's date and details, review's date, the grid's day stamps and week headers.
- **Top row in a folder.** Back and the month pill sit at the left end; an add-photos button (albums, private groups, Favorites albums; not locations) sits at the right, before settings.
- **Renaming selects the name.** Every name sheet opens with its current name selected whole, so typing replaces it.
- **One Favorites album per photo.** A favourite already in a Favorites album is not offered when adding to another; adding a selection to an album moves those photos out of any other.
- **Dates opposite the timeline.** In every photo grid the month and week headers sit at the left, in the section colour and a little larger, and the day stamps at each tile's top left, across from the timeline on the right; a similar-shot stack's mark stays at the top right.
- **Favorites albums in the section colour.** The names of the albums made inside Favorites are in its colour, setting them apart from real folders.
- **Cover menus.** Every long-pressed album (Albums or Favorites) has the same menu, alternatives sharing one row (words on the left 70%, the other as its icon on the right 30%, split by a slash). Under ALBUMS: Delete album (trash; the menu closes and the delete waits for Confirm), Remove from group when the album is in one, and Add photos at the bottom. Under EDIT: Rename; Move to group (Add to group) / private (lock), or Move album to private alone while grouping is off. Select / rearrange is always the last row. Every long-pressed group follows the same layout: Delete group at the very top (after Confirm, a group still holding photos asks once more with the count: Delete anyway; a group of albums sends their photos to the trash, a Favorites group lets its albums go, a private group sends its photos to Private's trash), GROUPS holds Ungroup all (albums only), EDIT holds Rename (and for private albums Unlock, with the open lock, and Add photos), and Select / rearrange closes it. Albums selected from the menu get the same actions in the bar: move to group, remove from group (when any of them is in one), private, and delete. Closing a group while albums are selected lets go of the selection. While albums are grouped, a New group row stands above New album: name it, tick its albums, create.
- **Rearranging drags at once.** While rearranging, a held card has no long press, so holding a card and then dragging always moves it.
- **The bar inside Private.** Once in Private, the bar's sections show Private's own photos until it is left: Recent is every private photo in one grid, Albums is the private groups, and Favorites the private favourites, either as one grid or grouped by private group (each group a cover of only its favourites; the top-row toggle switches, and Private remembers its own choice apart from Favorites outside). The private favourites folder inside the groups is gone, since Favorites covers it. Back from Private's Recent or Favorites goes to its groups, and back from the groups leaves Private; locking leaves it too. Above the bar, centred, a small PRIVATE pill with a back button beside it on the left says the bar is Private's, and leaves Private. Inside Private every section takes Private's own colour (#FA3438). Review works in Private's Recent and Favorites.
- **Colour on the cut.** A change of section or place recolours nothing while the outgoing view leaves: buttons leaving keep their old colour to the end, buttons arriving come in with the new one, and what stays (the bar's label, buttons in both places) changes once the outgoing view has left.
- **Place colours.** Locations is blue (#148BC7), the trash grey and Private red (#FA3438): each takes its colour in its own view (the bar, the buttons, the headings) and on its tile at the end of Albums. There Private, Locations and Trash are three tiles in one row, each as big as an album cover with its icon (a lock, a pin, a bin) larger in the middle and its name below, set apart from the albums by a hairline with a wider gap.
- **The bar inside Locations.** In Locations and inside a location, the same pill sits above the bar: LOCATIONS with a back button beside it on the left, which leaves Locations entirely, to the albums, from a location as from the list. The places' names take Locations' blue. While the PRIVATE, LOCATIONS or TRASH pill or the folder label shows, the content ends that much higher for each, so none covers the last photos.
- **Delete now.** Above the bar in the trash, a DELETE NOW button empties the whole trash for good, after Confirm.
- **Confirm.** Every delete button works as the trash's: in every grid's bar, every cover menu and the viewer, the delete is not done at the button: a CONFIRM pill rises above the bar (in the viewer above its buttons), over anything already there, and deletes on tap; anything else lets it go. Review's Done is its own confirmation.
- **Restore from the trash.** Restore asks first: Restore puts each back where it was, Restore to album picks an album (or a new one) and puts the selection there.
- **Settings per view.** Recent, Albums, Favorites, Locations, Trash and Private each keep their own image columns, layout, headers and stacking of similar shots, and (where they show covers) their own album columns; Albums and Favorites each have their own Grouped albums (Favorites starts with it on). The settings sheet's first tab is named after the view it is changing and shows only what applies there. The General and Interface tabs stay the same everywhere. Each view starts from what the single setting was before.
- **The bottom bar animates.** Switching between the sections bar, a selection's actions and the rearrange check pops one out and the next in, scaling and fading, rather than cutting.
- **Photo quality in the viewer.** Photos are drawn with smooth (high-quality) filtering; once zoomed in, the full-resolution picture (up to 4096 px a side) loads over the screen-sized one. The viewer, review and photos opened from other apps ask the window for HDR (Android 14+), so Ultra HDR photos from the camera show their bright highlights as in the Samsung gallery.
- **Back closes sheets.** The back gesture closes whatever sheet or menu is open (settings, long-press menus, pickers, naming), never the screen behind it.
- **Adding to an album offers only what is not placed yet.** The add screen of a folder album leaves out photos already in that album and, while grouping is on, photos in any album sorted into a group.
- **Set as cover from the viewer.** A photo opened from an album, a private group or a Favorites album has Set as cover in its More menu; it shows the "Set as cover" pill.
- **Favourite a selection.** The bar of a photo selection has a heart: it favourites every selected photo, or, once all of them are favourites, takes them all out. The selection stays after it.
- **Group arrow and gestures.** A shut group shows the arrow in the gap between its stack and its name, at the height it has on the opened heading, pointing right, the name keeping its place; tapping it opens the group. Opening, the arrow travels to the right end of the heading, turning over the middle of its way to point left. Closing, it travels back and turns again (with the finger, when pulled shut), the heading is cut once it is back. A tap on the heading's line closes the group as the arrow does; a swipe right on a closed group opens it by the finger, and letting go far enough carries it on, otherwise the cards go back.
- **Backup.** Settings → General → Back up saves everything the app remembers (settings, covers, review progress, the album order and groups, favourites albums, names, and the location and similar-shot indexes) to a JSON file at a place the user picks; Restore reads one back and restarts the app. A file that is not a backup changes nothing. Private photos are not in it, since they already live outside the app. Meant to carry the state over a reinstall, such as a change of signing key.
- **Sharp thumbnails.** Scrolling shows the system's cached thumbnails; a tile or cover that has stood on screen a moment gets the photo itself on top, decoded sampled and upright, because the cached thumbnail can be pixelated. A cached thumbnail turned the wrong way (its shape not the photo's, or MediaStore's turn not the file's own) is never shown: that photo is decoded small instead, upright, also while scrolling; each photo is checked once a session. The viewer opens on that same picture, and its frame takes the photo's upright shape from the start, so nothing changes once the full photo has decoded.
- **Albums button follows the place.** The middle button of the nav shows the place the user is in: the albums, a pin in Locations, the trash can in the trash, the lock in Private. Pressing it goes to the start of that place (the list of locations, the trash, Private's groups), never out to the albums, and scrolls to the top when already there.
- **Place label is a way out.** The label above the nav (PRIVATE, LOCATIONS) leaves the place as the arrow beside it does.
- **The viewer opens and closes behind the nav.** The photo grows out of and shrinks into its tile behind the nav, the label above it, the top row and the count. As it grows the top row slides off the top, the nav and count off the bottom and the timeline off the right edge, following the photo frame by frame, and they slide back as it shrinks; nothing fades. The timeline stays in front of the photo the whole way. Open, its own buttons stay in front.
- **Name sheets rise and sink.** The pane for naming a new album or group rises from the bottom with the keyboard and sinks back to the bottom, whether confirmed or dismissed.
- **Group names slice on close.** A group's name over its opened cards is sliced away from its right end as the group closes (in step with the cards, or with the finger when pulled shut); opening, it sweeps in.
- **New group and New album** share one row, group on the left; with groups off, New album stands alone and centred.
- **Icons.** The Locations pin, the crop glyph and the share glyph are the supplied SVGs; the lock is drawn as wide as the filled glyphs beside it; the lock is filled with an empty keyhole. The favourites-only heart's shadow follows the heart's drawn lines.
- **Settings sheet position.** The settings sheet runs the full height between the status and navigation bars, its content from the top; it can be pulled down from anywhere to close.
- **Settings opens out of its button.** Tapping the settings button turns its gear and pops it down; while the gear is still leaving, the settings panel grows quickly (0.16 s) from the button's own size to its full size, and its tabs, cards and foot rise in from below one after another. Closing is the sheet's usual way down, and the gear turns back in after it.
- **Rotation.** The app stays upright; only the viewer (a photo or video open) turns with the phone.
- **Private marks its own sections.** Inside Private, Recent and Favorites are underlined in the accent below their nav icons, since there they show only private photos.
- **Place label above the nav.** Inside Private, Locations and the trash, a label with the place's name sits above the nav with a back arrow beside it, both ink on the place colour. In the trash, Delete now sits above them.
- **The Albums icon changes with a pop.** Moving between Albums, Locations, the trash and Private pops the old icon away to nothing, then pops the new one in.
- **Folder label.** In a photo grid, a label above the nav names where you are: the album, group, location or Favorites album, or RECENT or FAVORITES in their ungrouped grids. It has no back arrow and no press, ink on the section colour, and stands above the PRIVATE or LOCATIONS pill where one shows. When the name changes it first deletes back to what the old and new names share, then the pill resizes from its middle, and partway into the resize the rest types in; a new section colour comes as the new name starts typing, not before the old one has gone. Settings → Interface → Folder label chooses Top or Bottom (above the nav, the default); at Top the name takes the month's place in the top pill. A name too long for either fades out at its end.
- **Fresh install settings.** Recent 5 image columns, every other photo grid 3; album columns 3; Headers - Layout Days, Months and Years with Headers on; Grouped albums off in Albums; Autoplay videos on; Stack similar shots off; Day stamps off; Folder label Top; Blur 50, Opacity 85%, Brightness 27%. Settings already stored keep their values.
- **Private's groups are albums.** Everywhere the user sees them (the menus, sheets, the New card and the viewer), Private's folders are called albums; a long-pressed one offers Unlock (open lock), which moves it out to an album as before.
- **Album settings.** Every album (in Albums, Private and Favorites) follows Recent's photo settings (image columns, Headers - Layout), shown greyed in its settings tab, until Own settings at the bottom of its Photos card is turned on; then they change that album alone, starting from what it showed. Off again, it follows Recent's once more, keeping its own for next time.
- **Cover icon.** Set as cover (the bar's COVER and the viewer's menu row) uses a filled picture glyph, sized as the other filled icons.
- **Nav over an open photo.** Once an open photo has settled, the nav's pill is the viewer's solid black rather than glass, so a photo ending just above it never tints it; while the photo flies or is pulled it is glass again.
- **Tile marks.** In the trash the days left before deletion stand at a tile's top left, small and in the danger colour, in place of the day stamp. A video's length is centred at the bottom, a little smaller from five columns up. A favourite's heart sits at the top right, before a stack's count or place; a motion photo's mark stays at the bottom right.
- **Albums and groups apart.** While albums are grouped, a short hairline (35% of the width, centred) stands between a group and the albums before or after it.
- **The viewer's date pill.** Opening a photo, the date's pill pops in as a circle, widens from its centre to both sides, and then the date types itself in; closing runs it back within the close: the date types out, the pill narrows to a circle, the circle pops away. Pulling the photo down runs the same steps back with the finger, instead of shrinking the pill. A tap still sends it off the top and back.
- **Divider between groups and albums** stands in the middle of the room between them.
- **Drawing.** The crop screen has a nav like the main view's at its foot, with a crop icon and a pen icon; for photos only, a video keeps to cropping and has no nav. Draw swaps the ratios for the pencil's row (its colours as dots, white, black and the app's accents, and a drag-only thickness slider whose knob is the line itself), hides the rotate button, and lets one finger draw lines on the photo while two still zoom and pan it; outside the frame stays veiled. The rows pop between the two modes. The lines are part of the saved copy and each one is an undo step. The pencil's colour and thickness are kept for next time.
- **Rotate.** In crop mode a rotate button at the top right turns the picture a quarter clockwise, photo or video: the picture swings round in place, the frame fading while it turns, and the frame, zoom and ratio turn with it. Each turn is an undo step, and the copy is saved turned.
- **Rearranging ends on a tap.** While rearranging, a tap anywhere that is not a cover or a group (the title, the gaps, the buttons' surroundings) turns it off, as the Done button does; a tap on a cover does nothing, and a tap on an open group's heading still only closes it, as opening a group does.
- **Rearranging by dragging.** A cover (album, group, private album, Favorites album) held for a moment and then dragged turns rearranging on and moves with the finger at once, whether the drag starts before the long press or after it (its menu then closes); one dragged straight away still scrolls. A group is picked up only by its stack's pictures; elsewhere on it the swipe still opens it. Inside an open group, its albums are picked up the same way and arranged within it: held and dragged, before or after the long press, the album moves with the finger at once. While a cover is carried its name and count cast a slight shadow, fading in with the hold and out as it settles; a whole group's name and count do the same. A cover's long press waits 0.15 s past the system's, so there is time to start the drag before its menu opens. A long press on the grid between covers (with its vibration) also turns rearranging on.
- **Groups above albums.** While albums are grouped, every group stands above every album, and neither rearranging nor dragging can put an album above a group. Groups trade places with groups and albums with albums; a group dragged past an open group, above or below it, takes its place.
- **Albums into groups by dragging.** While rearranging, an album dragged onto a group (shut or open) makes the group swell and the album shrink; letting go puts it in the group, with the pill's undo to put it back where it lay. An album held over a shut group for a moment opens it as a peek: its cards open out and the rows below are pushed down as for an opened group, while the held album stays under the finger; the peek folds back once the album moves off it or is let go. An album in an open group can be dragged off it: let go over another group it moves there, let go anywhere else it leaves its group and stands among the albums; both with undo. In Albums and in Favorites alike.
- **Letting go glides.** A cover let go while rearranging glides the rest of the way into its slot from where the finger left it, quickly, instead of snapping there.
- **Buttons that come and go in a bar.** A selection button shown only sometimes (Set as cover at one photo, Remove from group) pops away while the pill narrows round it, and pops in once the pill has widened; a bar leaving keeps the buttons it had until it has popped away.
- **Duplicates.** Settings → General → Library → Find duplicates opens a screen over everything that goes through every photo on the phone, whatever album, group or section it is opened from (not videos and not the trash), and gathers copies of the same picture, found by their pixels alone: names, dates, sizes and compression do not count. Each photo is boiled down once to a print (a 64-bit DCT hash at four quarter turns, a 4×4 colour grid and 16×16 greys, from its 128 px thumbnail), kept on disk, so later searches only read new photos; pictures whose prints pair up are compared again closely at 128×128 from a 512 px thumbnail, patch by patch, so two screenshots of one form with different values are told apart. A segmented track picks how alike they must be: Exact (the same picture at any size or compression; pairs are also compared sharp at 256×256 without evening out contrast, so two shots of the same clouds a moment apart are not copies), Close (the default; also slightly brightened or recoloured) and Loose (the same scene). The search starts once the track's pill has settled and runs below the screen's priority, so the pill slides smoothly; choosing again stops a search mid-comparison. While it reads, the count and a bar show progress. Sets come largest saving first, in rows of three: the copy to keep (most pixels, then largest file, then from the camera, then oldest) first and unmarked, the others marked (dimmed, a red bin) and each with its resolution, size and album. A tap marks or keeps a copy; a long press shows it large on black until tapped. The pill at the bottom (the marked count and size) deletes them to the trash after Confirm, with the pill's undo, and closes the screen.
- **Duplicates in Private.** Opened from inside Private, Find duplicates looks only among the private photos, apart from the library's: its prints are kept in the private folder itself (`.duplicates`), never in the app's files, and its photos are decoded in memory. The same screen, in Private's red; what it deletes goes to Private's trash, with undo. Locking Private closes it.
- **Private's trash.** Private has a trash of its own, kept apart from the system's so nothing private ever passes through MediaStore: every private delete (a photo in the viewer, a selection, review, duplicates, a whole private album) moves the files into a hidden folder inside Private (`.trash`), with an index of the album each came from, when, and whether it was a favourite. It works as the ordinary trash does: a Trash tile (grey, with its count) at the foot of Private's albums, under a hairline, as big as an album cover, in the middle of its row; inside, the grid in Private's red with the days left at each tile's top left; tapping opens a photo with share, restore and delete forever; selecting gives restore (back to the album it left, made again if it is gone, a favourite again if it was one; or Restore to album, picking a private album or a new one) and delete forever; DELETE NOW above the bar empties it; all after Confirm. Photos are kept 30 days and then deleted for good when Private is next read. The pill's undo puts a delete back at once. Back from it goes to Private's albums; locking leaves it.
- **Done while rearranging.** The bottom bar shrinks to a single tick, inverted — a dark tick on a pill in the section colour — so it is not missed.
- **Back lets go of a selection.** The system back gesture, while photos or covers are selected, clears the selection first, even with groups open or a screen opened since; it never leaves the folder or the app while anything is selected.
- **Dropping into a group waits for the peek.** An album let go over a shut group goes in only once the group has opened under it; let go sooner, it glides back to its place.
- **Done in Settings.** A Done pill, filled with the section colour and white on it, stands at the foot of the settings sheet above V/AS and closes it.
