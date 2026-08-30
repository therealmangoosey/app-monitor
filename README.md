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

Telemetry is sent over the local network on TCP port `45820`. The script sends battery percentage, charging state, CPU percentage and RAM usage. It automatically retries after a dropped connection.

If you see `Connection refused`, make sure Parent mode is open on the receiving Android device, that its displayed IP is being used, and that both devices are on the same Wi-Fi/LAN.

## Notification rules

Open **Notification rules** in the app. The trigger condition and notification text are separate, so a rule can trigger on one value while saying anything you want.

The trigger can use Battery, CPU or RAM with `<`, `<=`, `=`, `>=` or `>` and a 0–100 threshold.

The notification title and message are completely free-form. You can optionally use `{battery}`, `{cpu}`, `{ram}`, `{charging}`, `{time}` and `{value}`. `{value}` is the metric that actually triggered the rule.

## Webhooks

Open **Webhooks** in the app to add webhook destinations. The editor is inside a scrollable layout so it works on small and large displays.

Presets:

- **Discord:** creates a polished embed with fields, timestamp, source device and App Monitor version.
- **Slack:** creates a formatted Block Kit message.
- **Generic JSON:** sends a simple JSON telemetry payload for compatible webhook services.

For parent mode, webhook data comes from the connected child or Termux sender, so the webhook reports the device that actually produced the telemetry.

## APK updates and signing

Android updates require the same application ID and signing key, with a higher `versionCode`. The release workflow now builds a release APK, signs it with the repository's stable release key, verifies it, and names the APK from `versionName`.

The current app version is **1.1.2** with `versionCode 3`.

If an older App Monitor APK was installed using a different signing key, Android cannot replace it with the stable-signed build. That old build must be uninstalled once. Future releases using the stable key will update normally.

## Build

Open the project in a recent Android Studio release and sync Gradle. The project uses Android Gradle Plugin 9.3.1, Gradle 9.5, Kotlin 2.3.21 and JDK 17.
