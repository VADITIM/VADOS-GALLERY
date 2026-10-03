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
- No abbreviations in identifiers (`dna/09-code-style.md`); comments say why, on one line.

There is no Android SDK in the cloud sessions. CI (`.github/workflows/build.yml`) is the compiler: push,
then read the run. A change is verified only on the phone.

Work directly on `master`: commit and push there, no feature branches. Every push to `master` publishes
the APK as the `debug-latest` release.
