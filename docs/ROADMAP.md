# Roadmap

What is planned, by version. Nothing here is in `SPEC.md` until it is built.

## 1.2.0

- **Drawing.** Simple, not the bloated kind other gallery apps ship: draw lines, change the pencil's colour and its thickness. Nothing more.
  - It lives in the crop screen. The crop screen gets a nav like the main view's, with the crop icon and a draw icon; switching between them changes the layout and the elements visible.
- **Albums into groups by dragging.** An album outside any group can be dragged into a group, which is impossible today.
- **Rearranging by dragging.** Dragging an album before the long press's haptic fires turns rearrange mode on, instead of waiting for the haptic.
  - Rearrange only juggles what can be interacted with.
  - Groups behave the same, but they already have the swipe to open and close, so rearrange starts only when the drag begins on the group's pictures; anywhere else on the group the swipe still opens it.
- **Rearranging around open groups.** Moving a closed group above or below an open group bugs out and does nothing today; it has to work.
- **Albums stay below groups.** An album can never be placed above a group, in rearrange or by dragging.
