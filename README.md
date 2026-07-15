# Karoo Ride Replay

Open-source ride-simulation extension for Hammerhead Karoo cycling computers. Replays a recorded FIT file as mock GPS plus virtual sensor data — so other Karoo extensions run as if a real ride were happening, without leaving home.

<p align="center">
  <img src="docs/playback-screen.png" alt="Karoo Ride Replay — Playback screen" width="320" />
</p>

> Status: alpha (v0.1.4). UI redesigned to the Hammerhead Karoo look and validated on-device.

## Why

Karoo extension development needs ride data. There's no Hammerhead-provided way to test extensions against meaningful sensor + GPS input without an actual ride. `karoo-ride-replay` fills that gap by playing back a real recorded ride from FIT — GPS, power, heart rate, cadence, speed, altitude, all at the original timing — so any other extension (KPower, Wattramp, or any sensor/GPS-consuming extension) sees the data as if you were on the bike.

## Install on Karoo 3

The Karoo 3 doesn't have ADB enabled by default, so the supported sideload path is via the **Hammerhead Companion** mobile app (the same app you already use to push routes to your Karoo).

1. Pair your Karoo 3 with Hammerhead Companion (one-time, if you haven't already).
2. On your phone, open this URL in your browser:
   <br>**[https://github.com/lgangitano/karoo-ride-replay/releases/latest/download/karoo-ride-replay.apk](https://github.com/lgangitano/karoo-ride-replay/releases/latest/download/karoo-ride-replay.apk)**
3. After the APK downloads, long-press it (in your browser's download list or the Files app) → **Share** → **Hammerhead Companion**.
4. Companion pushes the APK to your Karoo. On the Karoo, accept the install prompt.
5. Open the app once from the Karoo's app drawer → grant **All files access** when asked (required to read `FitFiles/`).
6. In Android Developer Options → **Select mock location app**, pick *Karoo Ride Replay*. (If Dev Options aren't on yet: Settings → About → tap "Build number" seven times.)

## Install on Karoo 2

Karoo 2 has ADB enabled. Plug it in over USB and run:

```bash
adb install -r karoo-ride-replay.apk
```

(Download the APK from the [releases page](https://github.com/lgangitano/karoo-ride-replay/releases/latest) first.)

Then the same one-time setup as Karoo 3 — grant **All files access** on first launch, and designate the app as **mock location** in Developer Options.

## Using it

The app is two screens — **Select Ride** and **Replay** — styled to match Karoo OS.

1. **Pick a ride** — the picker lists FIT files from `FitFiles/` and `Download/`, newest first. Each row leads with the ride's write-time and a parsed `duration · distance · size`, and the currently-loaded ride is highlighted. Near-empty files (aborted/quick test recordings with no real ride in them) are filtered out. Tap a row to open it straight in Replay.
2. **In Karoo Settings → Sensors → Add Sensor**, pair the virtual sensor (Power / HR / Cadence / Speed) once.
3. **Hit Play.** The Replay screen fits one pane: a large current-time readout, a draggable timeline, transport (‹10s / Play-Pause / 10s›), and a small strip confirming the live Power/HR/Speed/Cadence stream.
4. **Scrub the timeline** — drag or tap anywhere to seek to that point of the ride.
5. **Mark / Loop / Clear** — drop bookmarks, then toggle **Loop** to repeat between the outer two marks (handy for regression-testing a segment). Scrubbing out of the marked window releases the loop.
6. **Speed** — replay at 1× / 2× / 5× / 10× to exercise a long ride quickly.
7. **To ride** — the full-width bar minimizes to the Karoo's normal ride view while playback keeps streaming, so your other extensions see real-looking sensor + GPS data.
8. **Back** — the bottom-left chevron (or the hardware back button) returns to the picker; playback pauses and keeps its position, so re-selecting the ride resumes where you left off.

## Features (v0.1.x)

- **Karoo-native UI** — the Hammerhead Visual Data Field System (pure-black, mono numerals, pill controls), one pane, no scrolling
- **Ride library** — scans Karoo's `FitFiles/`, leads with write-time + parsed duration/distance/size, hides near-empty recordings
- **Draggable timeline** — drag or tap to seek anywhere in the ride
- **Bookmarks + loop** — mark points and loop between two of them for repeated segment testing
- **Mock GPS injection** via Android `LocationManager` — Karoo OS sees position move along the recorded route
- **Virtual sensor devices** for Power, Heart Rate, Cadence, Speed via the `karoo-ext` Device API
- **Variable playback speed** — 1× / 2× / 5× / 10×
- **State survives round-trips** — reopening from the Extensions list, or backing out and re-selecting a ride, resumes in place

### Known limitations

- **GPS fix isn't held while paused.** Mock locations are published only on positioned records during active playback; while the replay is paused (or passing through a stopped/positionless segment of the FIT) the fix isn't refreshed, so consumers may drop it. A last-position heartbeat is the planned fix.

### Planned

- Hold the mock GPS fix through pauses/stopped segments (heartbeat)
- External FIT import (drag-and-drop)
- GPX import (route-shape testing without sensor data)

## Architecture

- `extension/KarooRideReplayExtension.kt` — `KarooExtension` service host
- `replay/` — FIT parser (Garmin official SDK) + playback engine (coroutine-driven, emits at FIT-recorded timing × speed multiplier)
- `vdevice/` — virtual sensor Devices (KPower pattern × 4)
- `mocklocation/` — Android `LocationManager` mock-provider integration
- `ui/` — Compose ride selector + merged replay/playback control, themed to the Karoo Visual Data Field System (`ui/theme/`)

## Build from source

```bash
# Requires Karoo Extension SDK auth (~/.gradle/gradle.properties with
# gpr.user + gpr.key, read:packages scope on a GitHub PAT)
./gradlew assembleRelease
adb install app/build/outputs/apk/release/karoo-ride-replay.apk
```

## License

Apache 2.0 — same as the `karoo-ext` SDK.
