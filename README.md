# VADOS Gallery

A replacement for Samsung Gallery: three sections (Recent, Albums, Favorites), newest at the bottom
like Apple Photos, and a ••• menu that holds three things instead of twenty. Native Android, built on
the VAS design system.

- **What it does and why:** [`docs/SPEC.md`](docs/SPEC.md)
- **How it looks and moves:** [`docs/DESIGN.md`](docs/DESIGN.md)

## Install

Every push to `master` publishes the APK at
**[releases/latest](https://github.com/VADITIM/VADOS-GALLERY/releases/latest)**.
Install **`vados-gallery-v<version>.apk`**, the optimised build. Only the release build is published.

```bash
adb install -r vados-gallery-v0.10.apk
```

Builds share one committed debug key, so a new build installs over the old one.

On first launch the app asks for **All files access** (required) and **Media management** (optional,
removes the confirmation popup from favourite, move and delete).

To make it the default: open any photo from a file manager or chat and choose *Gallery → Always*.

## Build locally

```bash
./gradlew assembleDebug
```

Needs JDK 17 and the Android SDK (compile SDK 36).

## Layout

```
app/src/main/java/com/vaditim/gallery/
  vas/      the VAS identity in Compose: palette, type, squircle, motion, panel, press
  media/    MediaStore: the library query, albums, moving
  access/   the two special permissions
  ui/       sections, grids, albums, the viewer, the ••• menu
```

## Licences

Space Mono and Audiowide are under the SIL Open Font License; see `licenses/`.
