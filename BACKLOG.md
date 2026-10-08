# karoo-ride-replay — Backlog

## Open

_(nothing open)_

## Shipped

### Replay data field with ride-screen controls (issue #4, v1.1.0, 2026-10-08)
One adaptive **Replay** data field. Full width: a control bar (‹ 10s, play/pause, 10s ›,
elapsed + state, speed), each its own tap target. Half width: elapsed, state and speed;
tap toggles play/pause. Verified on a Karoo 3 in day and night mode; Karoo 2 untested.

### Control a replay over adb (issue #5, v1.1.0, 2026-10-08)
`adb shell am broadcast -a it.gangitano.karooridereplay.{LOAD,PLAY,PAUSE,SEEK,SPEED,STATUS,EXIT}`,
accepted only from adb/system (`DUMP`), each answering with the replay state. The app
follows rides loaded over adb. Verified on a Karoo 3; Karoo 2 untested.

### Star favourites + idempotent reopen (2026-07-17)
Tap the star to pin a ride to the top of the picker; pins are stored in
SharedPreferences so they survive ViewModel recreation, app restart, and reboot.
The picker scrolls to the top when a ride is pinned so it doesn't slip above the
viewport. `MainActivity` is `launchMode=singleTask`, so "Open" from the Extensions
list resumes the running instance (idempotent to "Switch to") instead of spawning a
fresh Activity and losing in-memory state.

### GPS mock: register on replay, release on exit (issue #1) (2026-07-17)
The mock GPS provider was registered at extension-service startup, which failed
silently until the app was selected as the device's mock-location app (forcing an
app restart) and hijacked real GPS when nothing was replaying. Now the provider is
armed when a replay starts and disarmed when the rider exits back to the picker, so
the Karoo uses real GPS whenever nothing is playing back. "To ride" keeps the replay
armed so a rider can record against it. Verified on-device (dumpsys/logcat): nothing
installed at launch; installed on Play; removed on exit; reinstalled on replay.

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
