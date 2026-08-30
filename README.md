# App Monitor

A lightweight Android device monitor with local-only, parent and child modes.

## What is reported

- Battery percentage.
- Charging / Not charging state.
- CPU usage percentage.
- RAM usage percentage.
- Estimated time until empty when Android exposes the required readings.

## Termux sender

A monitored device does not need the Android app installed. A Termux device can run `termux/app-monitor-termux.py` and act as the child/sender.

Install Python and Termux:API:

```bash
pkg update
pkg install python termux-api
```

Start **Parent mode** in App Monitor. Parent mode starts its TCP listener automatically and shows the pairing code and local IPv4 address. Then run:

```bash
python termux/app-monitor-termux.py PARENT_IP PAIRING_CODE
```

Telemetry is sent over TCP port `45820`. The script sends battery percentage, charging state, CPU percentage and RAM usage and automatically retries after a dropped connection.

If you see `Connection refused`, make sure Parent mode is open on the receiving Android device, use the IP shown there, and make sure both devices are on the same Wi-Fi/LAN.

## Notification rules

Open **Notification rules** in the app. The trigger condition and notification text are separate, so a rule can trigger on one value while saying anything you want.

The trigger can use Battery, CPU or RAM with `<`, `<=`, `=`, `>=` or `>` and a 0–100 threshold.

The notification title and message are completely free-form. Optional placeholders are `{battery}`, `{cpu}`, `{ram}`, `{charging}`, `{time}` and `{value}`. `{value}` is the metric that actually triggered the rule.

## Webhooks

Open **Webhooks** in the app to add webhook destinations. The editor is scrollable so it works on small and large displays.

Presets:

- **Discord:** polished embed with fields, timestamp, source device and App Monitor version.
- **Slack:** formatted Block Kit message.
- **Generic JSON:** simple JSON telemetry payload.

For parent mode, webhook data comes from the connected child or Termux sender, so it reports the device that actually produced the telemetry.

## APK updates and releases

The package ID is `com.therealmangoosey.appmonitor`. Each release increases `versionCode` and uses `versionName` in the tag, release name and APK filename.

The release workflow runs only from `main`, builds the release APK, and creates one GitHub Release for that version. When the four signing secrets are configured, the APK is signed for normal in-place Android updates. Without those secrets, the release is marked as an unsigned test build and cannot replace a signed installation.

Required GitHub Actions secrets for installable updates:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Keep the keystore private and never commit it to the repository.

## Build

The project uses Android Gradle Plugin 9.3.1, Gradle 9.5, Kotlin 2.3.21 and JDK 17.
