# garage-telemetry-android

Android app for live OBD2 telemetry while driving: connects to a classic
Bluetooth ELM327 adapter, shows RPM/speed/coolant/throttle on a dashboard,
logs everything locally (Room), and backs up to the shared
[`garage-telemetry-api`](../garage-telemetry-api) once you're back on home
wifi. Companion to [`garagepi`](../garagepi), which logs the same vehicle
while it's parked at home — both write into the same Postgres `readings`
table via the identical ingest contract, keyed by standard OBD-II PID hex
codes (`010C` RPM, `010D` speed, `0105` coolant, `0111` throttle).

## Status

Full scaffold — Bluetooth SPP connection, OBD2 command/parsing, Room
storage, Compose dashboard, trip history/graphs, WorkManager sync, and a
settings screen for the API endpoint. Built by hand in a sandbox without
Android SDK/Gradle/a phone, so it has **not been compiled or run yet**.
Open it in Android Studio and get it building before trusting anything
beyond the code review that's already been done.

Known gap: `gradle/wrapper/gradle-wrapper.jar` (the wrapper's binary
launcher) isn't included — I didn't want to pull a binary blob from the
network unreviewed. Opening the project in Android Studio regenerates it
automatically on first sync; alternatively, if you have Gradle installed,
run `gradle wrapper --gradle-version 8.7` in this directory first.

## Setup

1. Open this directory in Android Studio (Gradle sync will prompt to
   generate the missing wrapper jar — accept it).
2. Pair the ELM327 adapter with the phone via Android Bluetooth settings
   first (the app only lists already-paired devices, it doesn't scan).
3. Run on a real device — no emulator support for Bluetooth SPP.
4. In the app's **Settings** tab, set the API base URL (e.g.
   `http://<pi-or-server-lan-ip>:8000`) and the Android API key configured
   in `garage-telemetry-api`'s `API_KEYS`.
5. On the **Live** tab, grant the Bluetooth permission prompt, then
   "Connect to OBD2 adapter" and pick the ELM327 from the list.

## Architecture

```
bluetooth/  Elm327Connection    — classic SPP socket, command/response over the '>' prompt
obd/        ObdPids             — canonical PID list + decode formulas (shared unit convention)
            ObdResponseParser   — raw ELM327 line -> data bytes
            ObdSession          — init sequence + poll loop
data/       Room entities/DAOs  — TripSessionEntity (one per drive), ReadingEntity (samples)
sync/       GarageApiClient     — HTTP client for garage-telemetry-api's /v1 endpoints
            SyncWorker          — WorkManager job: create/close remote sessions, upload unsynced readings
            SyncScheduler       — periodic (15 min) + on-demand triggers
            AppSettings         — API base URL / key, persisted in SharedPreferences
ui/         dashboard/          — live 2x2 stat grid, connect/disconnect
            history/            — trip list + per-PID line charts (custom Canvas, no chart library)
            settings/           — API endpoint configuration
```

Sync is local-first: Room is the source of truth, `SyncWorker` uploads
whatever hasn't been marked `uploaded` yet and retries via WorkManager's
backoff when the API isn't reachable (e.g. away from home wifi) — mirrors
the outbox pattern `garagepi`'s `sync.py` uses on the Pi side.

## Not yet decided (per the original handoff, still open)

- PID list beyond the initial four.
- Multiple vehicle/profile support (API schema already allows it via
  `vehicle_name`; app currently hardcodes `"ioniq5"`).
