# karoo-ride-replay — Backlog

## Open

_(nothing open)_

## Shipped

### UI redesign to the Karoo Visual Data Field System (v0.1.4, 2026-07-15)
Full redesign of both screens to the Hammerhead Karoo look, collapsed to a two-screen
flow, validated on-device (Karoo 2). This subsumed the earlier transport-simplification
item below.

- Two screens (Select Ride → Replay); the separate "Configure replay" screen folded in.
- Replay is one pane sized to the real 256×403dp canvas (no scroll): current-time hero,
  draggable/tappable timeline, transport (‹10s / Play-Pause / 10s›), Mark/Loop/Clear,
  speed tags, streaming strip, full-width To-ride bar + bottom-left back chevron.
- Picker leads with write-time + parsed duration·distance·size, flags unsaved rides,
  highlights the loaded ride.
- Engine: additive bookmarks + loop-between-marks.
- State survives reopen (reconnect to the live engine) and back/re-select (pause keeps
  position; re-selecting the loaded ride reopens in place).

### UI: simplify PlaybackScreen transport — drop Stop, surface Back-to-profile (2026-06-16)
Delivered by the redesign: the **Stop** button is gone, and returning to the picker is a
first-class control (bottom-left chevron + hardware back), reachable without scrolling.
