# VADOS Gallery on the desktop — the Arch port

`desktop/` is VADOS Gallery written again for Arch under Hyprland: the same product (`docs/SPEC.md`),
the same VAS adaptation (`docs/DESIGN.md`), in Qt 6 (QML + C++) instead of Compose. It is the stack of
VAD/OS Files, so the two apps share the squircle shader, the glyph inking, the freedesktop thumbnail
cache and the trash, and talk to each other over the session bus.

Everything in `docs/SPEC.md` and `docs/DESIGN.md` holds here unless this file says otherwise. These are
the desktop's own decisions (the platform's part of VAS's 80/20), each with its reason.

## What the phone has that a desktop does not

| Phone | Desktop | Why |
|---|---|---|
| MediaStore | `Library` (`src/core/library.cpp`) scans the library folders (XDG Pictures and Videos by default, more in Settings → General → Library) and keeps an index in `~/.cache/vados/gallery/index.dat`; a watcher follows changes | There is no system media index; the index makes a second start read no EXIF and start no ffprobe |
| MediaStore's `IS_FAVORITE` | The tag `favorite` in the `user.xdg.tags` extended attribute | The freedesktop attribute other file managers read, and it travels with the file through any rename or move on the same disk |
| The system trash (30 days) | The freedesktop trash (`~/.local/share/Trash`, and `.Trash-<uid>` on other disks) | The trash VAD/OS Files and every other app share. It has no expiry, so the shared trash shows no days left; Private's own trash still keeps 30 days |
| Fingerprint for Private | A PIN, set the first time Private is opened, salted and stretched in `~/.vados-private/.lock` | There is no system biometric prompt to rely on. Kept in the vault itself, so it survives a reinstall as the photos do |
| Locks when the app is left | Locks 1.5 s after the window loses focus | A focus change for a moment (a notification, a click on the bar) should not throw the user out |
| Screenshots blocked in Private | — | Wayland gives an app no way to refuse a screenshot |
| The system share sheet | SHARE puts the photos on the clipboard as files (and a single photo as its picture too) | Every desktop chat, editor and file manager reads the clipboard; pasting sends the photo itself. Dragging a tile out of the window works as well |
| The system geocoder | A GeoNames city list kept on the machine (`~/.local/share/vados/gallery/cities1000.txt`, or `cities500`, `cities5000`, `cities15000`); without one, a place is named by its coordinates | Nothing leaves the machine. The list is the user's to download |
| Media3 for video | Qt Multimedia, optional at build time | Without `qt6-multimedia` a video opens in the system's player; with it, inline playback, motion photos and the video bar |
| Haptics | — | No vibrator. Every press keeps its scale feedback |
| Long press | Hold the button down, or the right button | The right button is the desktop's long press: it picks a photo, opens a cover's menu |
| Pinch | Ctrl and the wheel, or a touchpad pinch | Both step the columns as the pinch does |
| Swipe between photos | Drag, a sideways touchpad swipe, or ← → | Finger-driven as on the phone; the keys run the same slide |

## Navigation: the sidebar, the bubble, or both

Settings → Interface → Navigation picks where the sections live: **Sidebar**, **Bubble** or **Both** (the
default).

- **The bubble** is the pop bar (`library/BottomBar.qml`, VAS `components/19-pop-bar.md`), taken whole:
  one glass pill for the nav, a selection's actions, the rearrange tick and the open photo's buttons; the
  old buttons pop away, the new ones pop in, the width morphs over both; a pull on the photo drives it.
- **The sidebar** (`library/Sidebar.qml`) is the desktop's own: a flat raised panel down the left edge
  (it does not float, so it is not glass). The sections with the nav's two-edged highlight standing up,
  the icons taking their colour on the cut; the places (Trash, Locations, Private); then every album.
  Its width is dragged on its edge and kept in rem. It slides off the left edge as a photo grows, as the
  top row slides off the top.
- **Sidebar only** leaves the bubble for what only it can hold: it comes up for a selection, for the
  open photo and for rearranging, and goes when they end.

## Scale and type

The scale lever reads the screen, not the window (VAS Files' `Theme.rem`), so a tiled window keeps its
type. `Theme.dp` is `rem / 20`, and every number from the phone is written in it, so the Compose values
carry over unchanged. Settings → Interface → Scale multiplies it.

## Talking to VAD/OS Files

- **Opening a photo in Files opens it here.** Files calls `org.vados.Gallery.Open(path, siblings)` on the
  session bus with the photos and videos of the folder in the order it shows them, so swiping here goes
  through the same neighbours. D-Bus starts the gallery if it is not running
  (`packaging/org.vados.Gallery.service`); one already open takes the photo.
- A photo opened from another app (or from Files when the gallery was not open) shows in the viewer
  alone; closing it puts the window away and keeps the process, so the next photo opens at once.
- `scripts/install.sh --default` makes the gallery the default for every image and video type through
  `xdg-mime`, so `xdg-open` and every other app open photos here too.
- **SHOW IN FILES** (the details, the ••• menu) asks the file manager through
  `org.freedesktop.FileManager1.ShowItems`, which VAD/OS Files answers with the photo picked.
- A photo dragged out of a grid lands in Files (or anywhere) as a file.

## Layout

| Path | Holds |
|---|---|
| `src/core/` | C++: `Library` (the index, albums, trash, lists from other apps, Locations), `PrivateVault`, `MediaActions` (every change to a photo), `MediaGridModel` (rows, headers, folded stacks), `Settings` (looks and arrangement), `SimilarShots`, `DuplicateFinder` and `ImagePrint`, `MediaEditor` (crop), `PlaceNames`, `ClipDevice` (motion clips), `GalleryService` (D-Bus), the image providers |
| `qml/design/` | `Theme`, `Motion`: the only place a colour, a face, a size, a curve or a duration is written down |
| `qml/ui/` | VAS primitives: `Squircle`, `Glass`, `Pressable`, `IconButton`, `Pop`, `OptionalButton`, `TypedLabel`, `Typewriter`, `SegmentedTrack`, `Switch`, `StepSlider`, `FavoriteHeart` |
| `qml/library/` | The shell (`Gallery`), `Navigation`, `Selection`, the `Sections` registry, the grids (`PhotoGrid`, `Tile`, `Timeline`, `CoverGrid`, `CoverCard`, `GroupRow`), the bars (`BottomBar`, `SectionBar`, `TopRow`, `Sidebar`), the pills |
| `qml/viewer/` | `Viewer` (the flight, paging, zoom, the pull), `ViewerChrome`, `VideoPlayer`, `MotionClip` |
| `qml/sheets/`, `qml/review/`, `qml/crop/`, `qml/duplicates/` | The glass sheets and the settings; review; crop and draw; duplicates |

Sections are the `Sections` registry (`qml/library/Sections.qml`); nothing else lists them. Every change to
a photo goes through `MediaActions`. Every grid is `PhotoGrid`, every grid of albums `CoverGrid`.

## Not ported yet

- **Video crop and trim** and **hold-to-scrub at 1.5×**: they need Qt Multimedia to preview, and have
  not been tried on this machine.
- **Private's Favorites grouped by album** (the toggle inside Private's Favorites): Private's Favorites is
  one grid.
- **Peeking a shut group open** while an album hangs over it: the group swells, and the album goes in
  once it has hung there 0.35 s.
