# Karoo Ride Replay

Open-source ride-simulation extension for Hammerhead Karoo cycling computers. Replays a recorded FIT file (the rider's own past activity OR an imported FIT/GPX) as mock GPS plus virtual sensor data — letting other Karoo extensions run as if a real ride were happening, without leaving home.

> Status: scaffold (v0.1.0-alpha). Feature work in progress.

## Why

Karoo extension development needs ride data. There's currently no way to test extensions against meaningful sensor + GPS input without an actual ride. `karoo-ride-replay` fills that gap by playing back a real recorded ride from FIT — GPS, power, heart rate, cadence, speed, altitude, all at the original timing — so any other extension (7climb, KPower, Wattramp, etc.) sees the data as if you were on the bike.

## Planned features

- **Ride library**: scan Karoo's `FitFiles/` folder, list previously-recorded rides, pick one to replay
- **Seek to start time** (hh:mm:ss) — skip the warmup, jump to the interesting climb
- **External FIT import** — drag any FIT file onto the Karoo
- **Mock GPS injection** via Android `LocationManager` — Karoo OS sees position move along the recorded route
- **Virtual sensor devices** for Power, Heart Rate, Cadence, Speed via the `karoo-ext` Device API — pairs to the Karoo as if they were real sensors
- **Variable playback speed** (1× / 2× / 5× / 10×) for fast iteration
- **Loop mode** for repeated regression testing

## Architecture

- `extension/KarooRideReplayExtension.kt` — `KarooExtension` service host
- `replay/` — FIT parser + playback engine (coroutine-driven, emits samples at FIT-recorded timing × speed multiplier)
- `vdevice/` — virtual sensor Devices (KPower pattern × 4)
- `mocklocation/` — Android `LocationManager` mock-provider integration
- `ui/` — Compose-based ride selector, configurator, and playback control

## Build

```bash
# Requires Karoo Extension SDK auth (~/.gradle/gradle.properties with
# gpr.user + gpr.key, read:packages scope)
./gradlew assembleRelease
adb install app/build/outputs/apk/release/karoo-ride-replay.apk
```

## License

Apache 2.0 — same as the karoo-ext SDK.
