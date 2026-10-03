# VAS on a phone — the gallery's design adaptation

VADOS Gallery is built on **VAS, base register** (`VADOS-APPLICATION-SYSTEMS/dna/`). This file records
where a gallery on a phone departs from it, and why. Everything not mentioned here is VAS as written.

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
| The panel primitive | `Panel` — translucent fill, 1dp `#262626` hairline, squircle, micro-label |
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

- **The ground is `#181818` in the grids and pure black in the viewer.** A grey frame around a
  photograph changes how its blacks read; the viewer is the one place the photo owns the colour.
- **Thumbnails are squircles with a small radius (4dp)** and 2dp gaps — law 4 at a size that keeps the
  grid dense. Album covers take the panel radius (12dp) and a hairline.
- **No shadows anywhere**, the bar included. The floating section bar is a `Panel`.

## Colour

One accent per section, inherited by everything inside it:

| Section | Accent |
|---|---|
| RECENT | Terminal green `#5bfd5b` — the system's identity colour |
| ALBUMS | Amber `#f09b3a` |
| FAVORITES | Hot pink `#ff2e88` |

DELETE is always `#ff6b6b`, never a section accent. The viewer inherits the accent of the section it
was opened from — the BACK link and a lit FAVORITE carry it, nothing else does.

## Type

| Role | Face | Used for |
|---|---|---|
| Functional (the default) | Space Mono, declared as `Faces.mono` | Labels, dates, counts, captions, actions |
| Techno heading | Audiowide | The section bar, album names, menu entries |
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
