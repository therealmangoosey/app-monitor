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

Start Parent mode in App Monitor, then run:

```bash
python termux/app-monitor-termux.py PARENT_IP PAIRING_CODE
```

Telemetry is sent over the local network on TCP port `45820`. The script sends battery percentage, charging state, CPU percentage and RAM usage. It automatically retries after a dropped connection.

## Notification rules

Open **Notification rules** in the app. The trigger condition and the notification text are separate, so a rule can trigger on one value while saying anything you want.

The trigger can use Battery, CPU or RAM with `<`, `<=`, `=`, `>=` or `>` and a 0–100 threshold.

The notification title and message are free-form. You can optionally use these placeholders:

- `{battery}` = battery percentage.
- `{cpu}` = CPU percentage.
- `{ram}` = RAM percentage.
- `{charging}` = `Charging` or `Not charging`.
- `{time}` = estimated time remaining, when available.
- `{value}` = the value that caused the rule to trigger.

For example, a CPU rule at `>= 90%` can say `The device is working hard. Battery: {battery}% and power: {charging}.`.

## Webhooks

Open **Webhooks** in the app to add as many webhook destinations as you need. The editor is inside a scrollable layout so it works on small and large displays.

Presets:

- **Discord:** creates a polished embed with fields, timestamp, source device and App Monitor version.
- **Slack:** creates a formatted Block Kit message.
- **Generic JSON:** sends a simple JSON telemetry payload for compatible webhook services.

You can choose which telemetry fields are included: battery, charging state, CPU, RAM and time remaining. Webhooks can be tested, enabled/disabled, edited and deleted.

For parent mode, webhook data comes from the connected child or Termux sender, so the webhook reports the device that actually produced the telemetry. The sender's device name is included as the source where available.

Webhook delivery runs separately from monitoring, so a failed webhook does not stop telemetry collection.

## APK updates and signing

Android updates require the same application ID **and the same signing key**, with a higher `versionCode`. The release workflow now builds a release APK, signs it with a persistent signing key, verifies the signature and names the APK from `versionName`.

Configure these GitHub Actions repository secrets before using the release workflow:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Keep the keystore private. Do not commit it to the repository.

The current app version is **1.1.0** with `versionCode 2`.

If an older App Monitor APK was installed using a different signing key, Android cannot replace it with the new signed build. That old build must be uninstalled once; future releases signed with the same release key will update normally.

## Build

Open the project in a recent Android Studio release and sync Gradle. The project uses Android Gradle Plugin 9.3.1, Gradle 9.5, Kotlin 2.3.21 and JDK 17.

GitHub Actions builds the debug APK on pushes to `main` and `feature/**`, and on pull requests targeting `main`. The release workflow builds and publishes the signed APK from `main`.
