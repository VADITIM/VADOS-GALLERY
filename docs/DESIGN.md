# VAS on a phone — the gallery's design adaptation

VADOS Gallery is built on **VAS, base register** (`VADOS-APPLICATION-SYSTEMS/dna/`), taken as **a taste,
not a copy**: the micro-labels, the accents per section, the motion grammar and the tempo stay; the
hard terminal edges go. Softer, rounder, glassier — closer to a phone than to a console menu. This file
records where it departs from VAS, and why. Everything not mentioned here is VAS as written.

These are project overrides (the "20%" of `SKILL.md`). Any of them that holds up across a second app is
a candidate to promote into VAS — most likely as a new `platforms/android-compose.md`.

## Native, not a WebView

VAS's Android advice is to host `dna.css` in a WebView. A gallery is the case that advice does not
cover: tens of thousands of thumbnails at 120Hz, pinch-zoom and a shared-element zoom from grid to
viewer. All of that is native work, and a hybrid would split one transition across two renderers.

So the identity is **re-expressed in Compose**, under `app/src/main/java/com/vaditim/gallery/vas/`:

| VAS | Here |
|---|---|
| `--section-color` | `LocalAccent`, provided once at the root from the active `Section` |
| The panel primitive | `Panel` — a borderless raised fill with the micro-label; `Modifier.glass` when it floats |
| Squircle corners (law 4) | `SquircleShape` — a sampled superellipse (exponent 5), not a rounded rect |
| GSAP curves | `Motion` — `back.out`, `power2.out` etc. as `CubicBezierEasing` with GSAP's numbers |
| Press feedback | `Modifier.pressable` — scale in 80ms, back on the overshoot; no ripple anywhere |
| The registry | `Section` — one enum drives the bar, the accents and the order |

## Motion: the tool tempo, not the portfolio tempo

The base timings (a 0.5s enter gate, a 1.72s curtain cut) are for a site seen a handful of times. A
gallery is opened dozens of times a day, so this app takes the VAD/OS TERMINAL ceiling:
**nothing a finger waits on exceeds 0.3s.**

| | VAS base | Gallery |
|---|---|---|
| Enter gate | 0.5s | 0.06s |
| Section enter | 0.35–0.6s | 0.26s, `back.out` on a short rise |
| Section leave | 0.21–0.5s | 0.12s, `power2.in`, no stagger |
| Screen cut | 6-bar curtain, 1.72s | None — the curtain is dropped |
| Viewer open / close | — | 0.28s / 0.16s |
| Menus | — | 0.24s in from below on the overshoot / 0.14s out |
| Press | — | 0.08s down, 0.22s back on `back.out` |

The three-phase law and the asymmetry law stand unchanged: every element has an enter and a leave, the
leave is faster, immediate and unstaggered, and never a reversed enter.

**What is dropped:** the curtain, the diagonal slice backgrounds, idle drift and the bar-sweep reveal on
grid content. A photo grid is the content, not a screen to be assembled; it appears at once. The
bar-sweep is kept for later on sparse text (album titles, the details panel) once it is ported.

## Surface

- **Floating things are glass, with no border.** The section bar, the month chip, the album's back
  button, the viewer's controls and its menus all blur and darken whatever is behind them
  (`Modifier.glass`, built on Haze). Over photographs a hairline reads as a frame around a hole; a
  blur reads as a pane. This replaces VAS law 1 ("the border is the design") for anything that floats.
- **The status-bar edge is a fading blur** (`Modifier.fadingGlass`): full at the top of the screen,
  none where it meets the content, so the grid dissolves under the clock instead of being cut off.
- **Things that do not float are flat raised fills, also without a border** (`Panel`, `#202020`).
- **Corners are generous.** Sheets 30dp, panels 22dp and album covers 20dp as squircles; the bar,
  chips and buttons are full capsules; grid thumbnails 8dp rounded rects with 3dp gaps. Thumbnails
  are plain rounded rects rather than squircles because there are hundreds on screen and a rounded
  rect clips on the GPU for free.
- **The ground is `#181818` in the grids and pure black in the viewer.** A grey frame around a
  photograph changes how its blacks read; the viewer is the one place the photo owns the colour.
- **No shadows anywhere.**

## Colour

One accent per section, inherited by everything inside it:

| Section | Accent |
|---|---|
| RECENT | Terminal green `#2fde75` — the system's identity colour |
| ALBUMS | Amber `#f09b3a` |
| FAVORITES | Hot pink `#ff2e88` |

DELETE is always `#ff6b6b`, never a section accent. The viewer inherits the accent of the section it
was opened from — the BACK link and a lit FAVORITE carry it, nothing else does.

## Type

| Role | Face | Used for |
|---|---|---|
| Functional (the default) | Space Mono, declared as `Faces.mono` | The section bar, labels, dates, counts, captions, actions |
| Techno heading | Audiowide | The Albums title, album names, menu entries |
| Display | — | **Not shipped yet** |

Wosker and Striker (the display faces) are not in this repository. `VADOS-APPLICATION-SYSTEMS/fonts/`
now also carries Clash Display, General Sans and Tanker without assigned roles. Which face carries the
display role is a VAS decision, not this app's — until it is made, the gallery has no display text.

## Gestures

- Sections change **only** by the bar. The horizontal swipe belongs to the viewer.
- In the viewer: horizontal swipe pages, a tap toggles chrome. *Planned:* vertical swipe-down dismisses,
  pinch zooms, and a zoomed photo takes the horizontal stroke until it is back at fit.
- In a grid: vertical scroll. *Planned:* long-press starts selection and the same finger then owns
  both axes for drag-select until it lifts.

## Voice

Actions are one uppercase word, verb-first: SHARE, DELETE, MOVE TO ALBUM. No OK / Cancel. Captions are
one plain sentence. No emoji.

## Performance

A grid of tens of thousands of photos at 120Hz is the one place the design has to give way.

- **Thumbnails come from MediaProvider's thumbnail cache** (`media/Thumbnail.kt`), not from decoding
  the original. A 50MP photo decoded for a 100px cell is the single largest cost a gallery can pay.
- **Grid cells clip to a rounded rect, never a generic path.**
- **The APK to install is the release build** — shrunk and optimised by R8. A debug build runs
  Compose unoptimised and debuggable, and stutters on a large grid however good the code is.
