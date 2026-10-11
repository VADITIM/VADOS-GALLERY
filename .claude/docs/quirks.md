# Quirks

## A QML binding stops updating when a store changes

A binding written as `(Store.revision, …)` never re-runs: compiled bindings drop the left side of the comma, and the dependency with it. Write `Store.revision >= 0 && …`.

## A child's `gallery`, `grid` or `shelf` property is always itself

A child handed `gallery: gallery` (or `grid: grid`, `shelf: shelf`) binds the property to itself. The roots are named `shell`, `photoGrid` and `coverShelf` so the names differ.

## `parent` inside a `Pressable` is not the pressable

Content of a `Pressable` lies inside its inner face. Reach the pressable by id.

## A push touching only `desktop/` publishes no APK

The workflow ignores `desktop/**` and `.claude/**`: the desktop port builds on its own machine, and a docs change has nothing to build.

## Two pushes in quick succession publish the same release

The older run can finish last and overwrite the newer APK. The workflow's `concurrency` group cancels the older run.

## The APK installs over the old one only with the same key

Until the release key secrets are set, or while `.github/sign-with-debug-key` exists, the build keeps the shared debug key. Deleting that file moves to the release key and needs one uninstall.

## A failed CI run is read from its tail

The Kotlin errors are repeated last under `=== COMPILER ERRORS ===`, so the Gradle stack trace need not be read.
