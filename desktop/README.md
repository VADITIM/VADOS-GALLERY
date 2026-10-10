# VAD/OS Gallery for Arch

VADOS Gallery as a native Qt 6 app for Arch under Hyprland: Recent, Albums and Favorites, newest at the
bottom, Private behind a PIN, the trash, review, crop and draw, duplicates, similar shots, Locations, and a
viewer that grows out of its tile. It opens the photos VAD/OS Files opens.

What it does is `../docs/SPEC.md`; how it looks and moves is `../docs/DESIGN.md`; what the desktop does
differently is `../docs/DESKTOP.md`.

## Install

```bash
sudo pacman -S --needed cmake ninja qt6-base qt6-declarative qt6-svg qt6-shadertools qt6-imageformats libexif ffmpeg
sudo pacman -S --needed qt6-multimedia     # optional: videos and motion photos play inline
scripts/install.sh                         # or: scripts/install.sh --default  to make it open every photo and video
```

`VADOS_APPS=/home/vn/Apps scripts/install.sh --default` puts the binary beside VAD/OS Files instead of `~/.local/bin`.

Hyprland: the window's class is `vados-gallery`, e.g. `bind = $mainMod, G, exec, vados-gallery`.

For place names in Locations, put a GeoNames city list at `~/.local/share/vados/gallery/cities1000.txt`
(from `download.geonames.org/export/dump/cities1000.zip`). Without it a place is named by its coordinates.

## Build and run

```bash
cmake -B build -G Ninja && cmake --build build
./build/vados-gallery [photo]
VADOS_GALLERY_ROOTS=/some/folder ./build/vados-gallery     # a library of just that folder
QT_FORCE_STDERR_LOGGING=1 ./build/vados-gallery            # QML warnings on the terminal
```

A scripted run drives the window and saves frames, for checking layout and motion without a pointer:
`VADOS_REHEARSAL=/path/to/script.qml QT_QPA_PLATFORM=offscreen QT_QUICK_BACKEND=rhi QSG_RHI_BACKEND=opengl ./build/vados-gallery`.

## Keys

| Key | Does |
|---|---|
| Escape | Back: a waiting delete, a sheet, the photo, a selection, the place |
| Ctrl+1 / 2 / 3 | Recent / Albums / Favorites |
| Ctrl+wheel | Fewer or more columns |
| Ctrl+A | Pick every photo of the grid |
| Ctrl / Shift + click | Pick a photo / a run of photos |
| Right button, or hold | Pick a photo; a cover's menu |
| Hold and slide | Pick every photo the pointer passes |
| Delete | Delete what is picked (waits on Confirm) |
| Enter | Confirm |
| Ctrl+, | Settings |
| ← → | The photo before or after |
| ↑ / ↓ | Details / close the photo |
| F | Favourite the photo |
| Space | Play or pause a video |
| R, Ctrl+Z, Ctrl+Shift+Z | In crop: turn, undo, redo |
