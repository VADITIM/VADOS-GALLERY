# Decisions

## 2026-10-11 — `.claude` restructured to the V/AS layout

Agent docs moved from the repository root and `docs/` into `.claude/`. `DESIGN.md` became `docs/rules/design.md`, because it is the standing rule for how the app looks; the rules of the old root `CLAUDE.md` became `docs/rules/gallery.md` and `docs/rules/desktop.md`. `SPEC.md` and `DESKTOP.md` stay as `spec.md` and `desktop.md`: product and platform documents with no slot in the standard layout. `Releases.md` stays at the root as the long history the release guide allows; its planned items moved to `roadmap.md`.

## 2026-10-11 — Releases are on command

Before, every push to `master` published the APK as `v<versionName>`. Now a push builds and uploads the APK to the run, and the release is published by `gh workflow run build.yml --ref master` when the user asks, or on every push when `RELEASE_ON_PUSH` is `true` (V/AS `guides/release-pipeline.md`). Rejected: keeping every push public, because the desktop and docs work pushes far more often than a release is wanted.

## Release text

`RELEASE_NOTES.md` holds only the latest version. A push that keeps the version replaces that release: the workflow deletes the release and its tag, then creates both again on the new commit.
