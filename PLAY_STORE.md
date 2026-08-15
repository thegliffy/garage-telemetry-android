# Play Console — jaryo

Use this when creating the listing and declarations. Host `PRIVACY.md` at a
public HTTPS URL and paste that URL into the Privacy policy field (Play
requires a live page, not a file inside the APK).

## Store listing

**Short description (80 chars max)**

Unofficial Ioniq 5 gauges over Bluetooth OBD. Not affiliated with Hyundai.

**Full description (draft)**

jaryo is unofficial live telemetry for a Hyundai Ioniq 5 (E-GMP). It talks to
a Bluetooth ELM327 that you pair in Android settings, shows a configurable
gauge dashboard, logs trips on the phone, and can upload to an ingest API you
host yourself.

This app is not affiliated with, endorsed by, or a product of Hyundai Motor
Company or Kia Corporation. Do not expect Hyundai support, logos, or
certificates.

Hardware required for live logging: a Bluetooth ELM327 left plugged into the
car. Reviewers and anyone without that hardware can tap Try demo on the Live
tab. History ships with a sample Drive and a sample Charge so charts are not
empty on first launch.

**Reviewer notes (paste into the review-notes / “notes for reviewers” box)**

This app reads a Hyundai Ioniq 5 over a Bluetooth ELM327 (classic SPP). Google
review devices do not have that hardware.

How to exercise the UI with no car:

1. Open the app. Grant notifications only if prompted; demo does not need
   Bluetooth.
2. Live tab → Try demo. Gauges move. Car mode and Charging buttons work.
3. History already contains “Demo · Drive” and “Demo · Charge” sample sessions.
   Open either for charts.

The connected-device foreground service starts only when the user taps Connect
to a paired adapter (not during demo). A hardware demo video is attached under
the FGS declaration.

## Data safety

| Question | Answer |
| --- | --- |
| Collects user or vehicle data? | Yes, on-device only (and optional upload to a user-owned server) |
| Sold? | No |
| Shared with third parties? | No |
| Encrypted in transit? | Yes for HTTPS; LAN HTTP only to RFC1918 / localhost |
| Users can request deletion? | Yes — they clear app storage or use in-app retention |
| Location | Not collected, not declared |
| Advertising ID | Not used (`AD_ID` is removed) |

Data types: app activity / crash (none we collect), personal info none,
financial none, health none, location none, photos none, files (app database),
vehicle telemetry (trip history). Optional: other in-app messages none.

## App content / target audience

Utility, 18+ not required, no user-generated public content, no war/news.

## Permissions that Play will ask you to declare

### Bluetooth / nearby devices

Purpose: connect to a bonded ELM327. The app never scans (`bondedDevices`
only). LOCATION is not declared.

### Notifications

Purpose: ongoing notification required by the logging foreground service.

### Foreground service — Connected device

Type: `connectedDevice`. Trigger: user taps Connect after choosing a paired
adapter. Stops on Disconnect or when the car stops answering. Does not run in
demo mode.

Play often wants a short video of a real car/adapter session. Record: Settings
→ choose adapter → Live → Connect → gauges update with the screen off.

### Internet

Optional sync to the user’s ingest URL.

## Signing a Play upload

Play accepts an Android App Bundle, not a debug APK.

```bash
# Create a Play upload keystore once (do not commit it):
# keytool -genkey -v -keystore play-upload.jks -keyalg RSA -keysize 4096 -validity 10000 -alias play

./gradlew bundleRelease
```

Enroll in Play App Signing. Keep the upload keystore offline.

targetSdk is 35. After the first AAB, check native 16 KB alignment if Play
flags `libandroidx.graphics.path.so`.

## Trademarks in graphics

Do not upload Hyundai or Kia logos, wordmarks, or vehicle photos that imply
an official app. The in-app launcher is a generic gauge.
