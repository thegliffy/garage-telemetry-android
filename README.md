# jaryo

Live telemetry for a Hyundai Ioniq 5 (E-GMP), read over a classic Bluetooth ELM327
adapter. Shows a configurable gauge dashboard while driving, logs every sample locally
(Room), and uploads to the shared [`garage-telemetry-api`](../garage-telemetry-api).

Companion to [`garagepi`](../garagepi), which logs the same car while it is parked at
home. Both write into the same Postgres `readings` table through the same ingest
contract, so home and driving data form one history.

## What it reads

The Ioniq 5 answers almost nothing on standard Mode 01 PIDs — no RPM, no coolant, not
even speed — so everything useful comes from manufacturer Mode 22 (UDS) queries. Offsets
come from the [Esprit1st Ioniq 5 Torque Pro PID list](https://github.com/Esprit1st/Hyundai-Ioniq-5-Torque-Pro-PIDs),
cross-checked against captured frames from the car.

Roughly 50 values including HV SOC (raw and display), pack voltage / current / power,
energy remaining, cell voltage extremes, battery temperatures, SOH, motor RPM front and
rear, isolation resistance, lifetime kWh charged and used, cabin and outside temperature,
all four tire pressures and temperatures, charge-port state, plus derived live efficiency.

Speed and the odometer are **calibrated in-app** rather than guessed — see below.

## Setup

1. Pair the ELM327 with the phone in Android's Bluetooth settings (the app lists paired
   devices, it does not scan).
2. Install the APK from [Releases](https://github.com/thegliffy/garage-telemetry-android/releases).
   Real device only — Bluetooth SPP does not work on an emulator.
3. **Settings** → choose the adapter, and set the API base URL and key if you want sync.
   **Test connection** checks both.
4. **Live** → grant the Bluetooth and notification permissions, then Connect.

Logging runs in a foreground service, so a drive keeps recording with the screen off.

### Configuring the dashboard

Ten tiles, 2×5 in portrait and 5×2 in landscape. Tap any tile to choose what it shows and
how it is drawn — number, arc, bidirectional power arc, thermometer, or one of the
composite tiles (all four tire corners, battery hi/low temperature, front and rear motor).

**Car mode** (button on the Live tab) is the same layout full-screen in landscape with the
screen held awake, for a phone mounted in the car.

## Android Auto

jaryo has an Android Auto surface, but **it will not appear in the car until you enable
developer mode on the phone**. Android Auto ignores any app it did not get from Play, and
gives no indication why.

1. Open Android Auto settings (Settings → Connected devices → Android Auto, or the
   standalone app).
2. Tap **Version** about ten times until it offers developer mode, and accept.
3. **⋮** → **Developer settings** → enable **Unknown sources**.
4. Reconnect the cable.

If it still does not show, clear Android Auto's cache (Settings → Apps → Android Auto →
Storage → Clear cache) — it caches its app list and often misses a newly sideloaded app.

The car screen is limited to templated rows by Google: no charts or gauges are possible
there, only text. That restriction is the platform's, not a shortcut.

## Calibration

The odometer and speed byte offsets are not published for this car, and guessing them has
produced wrong decoders twice. The **Calibrate** tab instead derives them from the car:
type what the dash reads, take a sample, then take a second sample at a different value to
eliminate coincidences. Both are already calibrated and shipped as defaults; a saved
calibration overrides the built-in offset, so a bad decode can be fixed without a rebuild.

The tab is hidden by default now that both are done — see `ROUTE_CALIBRATION` in
`ui/GarageNavHost.kt` to restore it.

## Data

Every sample lands in Room first; sync is a background job that retries when the endpoint
becomes reachable, so driving away from home loses nothing. **Settings** shows how many
readings are waiting and offers **Sync now**.

Retention is configurable: 1 month, 1 year, indefinite, or until uploaded. The age-based
options are a strict limit — a drive is deleted once it ages out even if it never reached
the server — and the settings screen says so.

## Architecture

```
bluetooth/  Elm327Connection    — classic SPP socket, with a connect fallback ladder
obd/        IoniqUds            — Mode 22 decoders and the poll schedule
            ObdSession          — init sequence, staggered polling, calibration overrides
            CalibrationScan     — derives byte offsets from known dash values
            EfficiencyTracker   — live mi/kWh, trip and rolling 10s
            TelemetryField      — every displayable value: unit, range, precision
service/    ObdLoggingService   — foreground service owning the connection and poll loop
            ObdLoggingState     — process-wide state the UI and car screens observe
data/       Room entities/DAOs  — TripSessionEntity (one drive), ReadingEntity (samples)
            SessionReaper       — closes sessions orphaned by process death
sync/       GarageApiClient     — HTTP client for the /v1 ingest endpoints
            SyncWorker          — uploads unsynced readings, retries on backoff
            RetentionWorker     — applies the retention policy, then VACUUMs
car/        TelemetryCarAppService — Android Auto templated readout
ui/         dashboard/          — configurable tile grid
            gauge/              — gauge rendering and per-field style defaults
            cardash/            — full-screen car mode
            history/            — trip list, summary, per-field charts
tools/      decode_capture.py   — decodes captured frames, finds byte offsets
            capture.sh          — one-command capture from a connected phone
```

## Caveats

- Decoders are specific to the Ioniq 5 (E-GMP). Other cars will connect but produce wrong
  or missing values.
- Speed is stored as `SPEED_VMCU` in mph, not the shared `010D` (defined as km/h), to
  avoid corrupting a series garagepi also writes.
- Power and regen limits both read 277 kW parked, above the stated 270 maximum — treat as
  unverified.
- Energy remaining reads low for a 77.4 kWh pack; worth comparing against the car's own
  range estimate.
- Sync talks plain HTTP to a LAN address, and the manifest allows cleartext accordingly.
- Debug-signed builds only. The debug keystore is public, so that signature proves nothing
  about origin.
