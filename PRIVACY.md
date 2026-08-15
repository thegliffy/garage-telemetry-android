# Privacy policy — jaryo

jaryo (“the app”) reads telemetry from a Hyundai Ioniq 5 through a Bluetooth
OBD2 adapter you pair in Android Settings. It is unofficial and not a Hyundai
product.

Last updated: 15 August 2026

## Data the app stores on the device

- Bluetooth address of the ELM327 you select
- Dashboard layout and display preferences
- Optional ingest URL and API key for a server **you** run
- Trip and reading history (SOC, pack power, temperatures, speed, and similar
  vehicle signals) in a local database

None of this is sold, used for advertising, or sent to us. There is no
analytics SDK and no advertising ID.

## Optional upload to your own server

If you paste an API base URL and key in Settings, unsynced readings are
uploaded to that host over HTTPS, or over HTTP only when the host is on your
private LAN or localhost. Sample “Demo” trips are never uploaded.

## Backups

Android Auto Backup is disabled for the app so the API key and trip database
are not copied to Google Drive.

## Permissions

- **Nearby devices / Bluetooth connect** — talk to the paired OBD adapter
- **Notifications** — required by Android for the logging foreground service
- **Internet / network state** — optional upload to the ingest URL you set

The app does not access location, contacts, camera, microphone, or storage
outside its own files.

## Demo mode

“Try demo” and the two sample History rows are simulated. They are not a
recording of your car.

## Contact

Use the GitHub repository that distributed this build, or the email listed on
the Play Store listing.
