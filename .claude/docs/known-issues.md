# Known issues

- Source comments in `settings.gradle.kts`, `core/design/.../vas/Typography.kt`, `core/design/.../vas/Motion.kt`, `desktop/src/core/settings.h`, `desktop/qml/ui/Glass.qml` and `desktop/qml/ui/Switch.qml` cite `docs/ARCHITECTURE.md`, `docs/DESIGN.md`, `docs/SPEC.md` and `dna/…`, paths that moved on 2026-10-11 (now `.claude/docs/…`, `architecture/…`). The code was left untouched by the restructure.
- The source holds comments, which the global rules forbid; their reasons belong in `quirks.md` and `decisions.md`.
