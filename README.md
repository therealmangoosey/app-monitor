# App Monitor

A simple Android device monitor with two explicit modes:

- **Local-only:** reads this device's battery, CPU and RAM and shows them locally. Nothing is sent to a parent device or uploaded.
- **Parent:** listens on the local Wi-Fi network and shows telemetry from one paired child device.
- **Child:** sends only battery percentage, CPU percentage, RAM usage and estimated time until empty to the paired parent device.

## How pairing works

1. Install the app on both Android devices and put both on the same Wi-Fi network.
2. On the parent device, choose **Parent mode**. The app shows a six-digit pairing code and the parent's local IPv4 address.
3. On the child device, choose **Child mode**, enter the parent's IP and code, then press **Start**.
4. The child device shows a persistent Android foreground-service notification while it is sending telemetry.
5. Press **Stop** on either device to stop monitoring.

The connection uses a local TCP socket on port `45820`. No cloud account or third-party telemetry service is required.

## Time until empty

The app uses the Android battery charge counter and current reading when the device exposes both. Android hardware support varies, so the UI shows **Unavailable** rather than presenting a made-up value when the required readings are not available.

## Build

Open the project in a recent Android Studio release and sync Gradle. The project uses Android Gradle Plugin 9.3.1, Gradle 9.5, Kotlin 2.3.21, and JDK 17.

GitHub Actions builds the project on pushes to `main` and `feature/**`, and on pull requests targeting `main`.
