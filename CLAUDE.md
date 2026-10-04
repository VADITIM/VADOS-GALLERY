# VADOS Gallery

This project is built on VAS (VADOS APPLICATION SYSTEMS), base register. Read `.claude/skills/vas/SKILL.md`.
If it is missing, ask before cloning `https://github.com/VADITIM/VADOS-APPLICATION-SYSTEMS` there.

The phone adaptation of VAS for this app — native Compose instead of a WebView, the tool tempo instead
of the portfolio tempo, black behind photos — is in `docs/DESIGN.md`, and it overrides the base modules
where they disagree. The product decisions are in `docs/SPEC.md`; a feature not in it is not added
without asking.

- Sections are the `Section` registry in `ui/Section.kt`. Nothing else lists them.
- Leaf composables never name an accent; they read `LocalAccent`.
- Every pressable uses `Modifier.pressable`, never the ripple.
- Durations and curves come from `vas/Motion.kt`, never inline numbers.
- Every change to a photo (favourite, trash, move) goes through a MediaStore request in `ui/MediaActions.kt`.
- No explanatory text in the UI: no hints, subtitles or captions describing what a control does, unless the user asks for one. Labels and state (a title, a count of selected, a time) are fine.
- Every feature exists everywhere the same kind of thing does: what works on albums works on private groups and locations, what works on a photo grid works in every photo grid (Recent, Favorites, albums, groups, locations, trash). Build it once as a shared piece (`ui/Covers.kt`, `ui/Reorder.kt`, `ui/MediaGrid.kt`) and use that piece in every screen; before finishing a feature, list the places it should be and check each one has it.
- No abbreviations in identifiers (`dna/09-code-style.md`); comments say why, on one line.

There is no Android SDK in the cloud sessions. CI (`.github/workflows/build.yml`) is the compiler: push,
then read the run. A change is verified only on the phone.

Work directly on `master`: commit and push there, no feature branches. Every push to `master` publishes
the APK as the release `v<versionName>`.

Every change raises `versionName` in `app/build.gradle.kts` (and `versionCode`): 0.1, 0.2, and so on. The release tag comes from it.
Every change also rewrites `RELEASE_NOTES.md`: a short changelog of that version, a few plain bullets of what changed for the user. It is the release's text.
