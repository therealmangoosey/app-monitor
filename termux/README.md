# Termux sender

This lets an Android device act as the monitored/child device without installing the App Monitor Android app on that device. It sends its own battery, CPU and RAM information over the local network to an App Monitor parent.

## Requirements

- Termux
- Termux:API
- `netcat`
- Both devices on the same Wi-Fi/LAN
- App Monitor running in **Parent mode** on the receiving device

Install the dependencies in Termux:

```sh
pkg update
pkg install termux-api netcat
```

Install Termux:API from the F-Droid/official Termux ecosystem if it is not already installed. Grant the requested battery/API permissions.

## Start sending

On the parent device, open App Monitor, choose **Parent mode**, and note the displayed IP address and 6-digit pairing code.

On the Termux device:

```sh
chmod +x app-monitor-termux.sh
./app-monitor-termux.sh --parent-ip 192.168.1.25 --code 123456
```

Replace the IP and code with the values shown by the parent. To change the update interval:

```sh
./app-monitor-termux.sh --parent-ip 192.168.1.25 --code 123456 --interval 15
```

The script sends battery percentage, CPU percentage, RAM percentage, battery status and temperature. The parent estimates time remaining from the received battery information where possible.

## Security / privacy

The Termux sender does not use a cloud service. It sends the monitoring payload only to the IP address you provide. Stop the script with `Ctrl+C`. Use the pairing code shown by the parent and only pair devices you control or have permission to monitor.
