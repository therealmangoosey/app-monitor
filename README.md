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

If you see `Connection refused`, keep Parent mode running on the receiving Android device, use the exact IP shown by Parent mode, and make sure both devices are on the same Wi-Fi/LAN.

## Notification rules

Open **Notification rules** in the app. The trigger condition and notification text are separate, so a rule can trigger on one metric while saying anything you want.

The trigger can use Battery, CPU or RAM with `<`, `<=`, `=`, `>=` or `>` and a 0–100 threshold.

The notification title and message are completely free-form. Optional placeholders are `{battery}`, `{cpu}`, `{ram}`, `{charging}`, `{time}` and `{value}`. `{value}` is the metric that actually triggered the rule.

## Webhooks

Open **Webhooks** in the app to add webhook destinations. The editor is scrollable so it works on small and large displays.

Presets:

- **Discord:** polished embed with telemetry fields, timestamp, source device and App Monitor version.
- **Slack:** formatted Block Kit message.
- **Generic JSON:** JSON telemetry for compatible webhook services.

In Parent mode, webhook data comes from the connected Android child or Termux sender, so the webhook reports the device that actually produced the telemetry.

## APK updates and signing

The app uses the same application ID for every build: `com.therealmangoosey.appmonitor`. Android updates also require a higher `versionCode` and the same signing certificate.

The release workflow builds the release APK. Signed GitHub Releases are enabled when these repository Actions secrets are configured:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The signing key must be kept private. Never commit a keystore or its base64 contents to the repository. The previously exposed development keystore was removed from `main` and must not be reused. Create a new private release key and store it only as Actions secrets.

When the signing secrets are absent, CI still builds the release APK and uploads it as an unsigned workflow artifact. It does not create a misleading release that cannot be used for app updates.

The current app version is **1.1.4** with `versionCode 5`.

An older APK signed with a different certificate cannot be updated in place. Uninstall that old copy once, then install the current consistently signed release. Future releases with the same certificate and a higher `versionCode` will update normally.

## Build

Open the project in a recent Android Studio release and sync Gradle. The project uses Android Gradle Plugin 9.3.1, Gradle 9.5 and JDK 17.

GitHub Actions runs the Android build on pushes to `main` and `feature/**`, and on pull requests targeting `main`. The release workflow publishes a signed GitHub Release when the release-signing secrets are available.
