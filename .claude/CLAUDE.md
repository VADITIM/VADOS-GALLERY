# VADOS Gallery

A replacement for Samsung Gallery: Recent, Albums and Favorites, a private vault inside Albums, a viewer,
review, crop and trim. Native Android (Kotlin, Jetpack Compose, ten Gradle modules) with a desktop port
in Qt 6 (`desktop/`, QML + C++) for Arch under Hyprland.

Built on V/AS (VAD/OS APPLICATION SYSTEMS), base register. Read `.claude/skills/vas/SKILL.md`.
If it is missing, ask before cloning `https://github.com/VADITIM/VADOS-APPLICATION-SYSTEMS` there.

## Where things are

- `docs/map.md` — folders, entry points, where to change what.
- `docs/architecture.md` — the module layout and where new code goes.
- `docs/spec.md` — the product decisions. A feature not in it is not added without asking.
- `docs/desktop.md` — what the desktop port does differently from the phone.
- `docs/decisions.md`, `docs/quirks.md`, `docs/known-issues.md`, `docs/roadmap.md`.

## Rules

@docs/rules/design.md
@docs/rules/gallery.md
@docs/rules/desktop.md

## Commands

- Android: there is no SDK in the cloud sessions. CI (`.github/workflows/build.yml`) is the compiler: push, then read the run. A change is verified only on the phone.
- Desktop: `cmake -B desktop/build -G Ninja desktop && cmake --build desktop/build && desktop/build/vados-gallery`. The compiler is local.

## Working

Work directly on `master`: commit and push there, no feature branches.

Every change raises `versionName` in `app/build.gradle.kts` (and `versionCode`): 1.0.0, 1.0.1, and so on, by the V/AS table (`guides/release-pipeline.md` § Versioning). Every change also rewrites `RELEASE_NOTES.md`: a short changelog of that version, a few plain bullets of what changed for the user. It is the release's text. `Releases.md` holds the longer history.

A desktop-only change raises `project(vados-gallery VERSION …)` in `desktop/CMakeLists.txt`, not `versionName`, and leaves `RELEASE_NOTES.md` alone: the workflow ignores `desktop/`, so it publishes no APK.

## Releases

Releases: on command

A push to `master` builds the APK and uploads it to the run; it publishes the release `v<versionName>` only when the user asks for a release (`guides/release-pipeline.md` § Releasing on command), or when the mode is changed to `Releases: on every push` together with the `RELEASE_ON_PUSH` repository variable.
