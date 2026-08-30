# App Monitor

A lightweight Android device monitor with three modes:

- **Local-only:** monitors the current Android device.
- **Parent:** receives telemetry from a paired Android child **or a Termux device** on the same local network.
- **Child:** sends battery, CPU, RAM and estimated time-to-empty from another Android device.

Telemetry stays on the local network. There is no cloud account or third-party telemetry service.

## Termux sender

A monitored device does **not** need the Android app installed. If the device has Termux, run the included `termux/app-monitor-termux.py` script and it can act as the child/sender.

### 1. Set up the Termux device

Install Python and, for the best battery readings, Termux:API:

```bash
pkg update
pkg install python
```

Install the Termux:API Android app separately, then install its Termux package:

```bash
pkg install termux-api
```

The script also falls back to `/sys/class/power_supply/battery/capacity` when `termux-battery-status` is unavailable.

### 2. Start Parent mode on the Android app

1. Open App Monitor on the device that will receive the information.
2. Select **Parent mode**.
3. Copy the displayed **local IPv4 address** and **6-digit pairing code**.
4. Keep the parent device and Termux device on the same Wi-Fi/LAN.

The parent listens on TCP port `45820`.

### 3. Start the Termux sender

From the repository directory:

```bash
python termux/app-monitor-termux.py PARENT_IP PAIRING_CODE
```

Example:

```bash
python termux/app-monitor-termux.py 192.168.1.25 483921
```

By default, telemetry is sent every 5 seconds. Change it with:

```bash
python termux/app-monitor-termux.py 192.168.1.25 483921 --interval 10
```

Press `Ctrl+C` to stop it. If the connection drops, the script automatically retries every 5 seconds.

### Termux limitations

Termux currently sends battery percentage, CPU percentage and RAM usage. Time-to-empty is sent as unavailable because Android/Termux devices do not expose a consistent discharge-time API. The Android child app can provide time-to-empty on devices that expose the required battery readings.

## Notification rules

Open **Notification rules** in the Android app to create alerts for the local device or a connected child/Termux device.

Each rule can define:

- **Metric:** Battery, CPU or RAM.
- **Condition:** `<`, `<=`, `=`, `>=` or `>`.
- **Threshold:** percentage from 0–100.
- **Notification title.**
- **Notification message.** Use `{value}` to insert the current percentage.
- **Notification type:** Silent, Low, Default or High importance.
- **Action:** Open App when tapped, or Dismiss Only.
- **Enabled/disabled state.**

Examples:

- Battery `<= 20` → **Low battery** → `Battery is {value}%` → High → Open App.
- Battery `<= 10` → **Critical battery** → `Plug the device in. It is at {value}%` → High → Open App.
- CPU `>= 90` → **High CPU usage** → `CPU is at {value}%` → Default → Dismiss Only.
- RAM `>= 90` → **High RAM usage** → `RAM usage is {value}%` → Default → Dismiss Only.

A rule triggers when its condition is reached and can trigger again after the value leaves and re-enters the condition, preventing a notification every polling cycle.

On Android 13+, allow App Monitor's notification permission when prompted. Android notification-channel settings can also control sound/vibration after a channel has been created.

## How pairing works

1. Install the app on the parent and, if using Android as the sender, the child device.
2. Put the devices on the same Wi-Fi/LAN.
3. On the parent, choose **Parent mode**. The app shows a six-digit pairing code and local IPv4 address.
4. On an Android child, choose **Child mode**, enter the parent IP and code, then press **Start**.
5. On a Termux child, run `termux/app-monitor-termux.py` with the parent IP and code.
6. The sender transmits only battery percentage, CPU percentage, RAM usage and estimated time-to-empty when available.
7. Press **Stop** on the Android app or `Ctrl+C` in Termux to stop sending.

The connection uses a local TCP socket on port `45820`. Pairing uses the six-digit code as a simple local-network authentication step.

## Build

Open the project in a recent Android Studio release and sync Gradle. The project uses Android Gradle Plugin 9.3.1, Gradle 9.5, Kotlin 2.3.21 and JDK 17.

The Android app module uses AGP 9's built-in Kotlin support, so `org.jetbrains.kotlin.android` is intentionally not applied.

GitHub Actions builds the project on pushes to `main` and `feature/**`, and on pull requests targeting `main`.
