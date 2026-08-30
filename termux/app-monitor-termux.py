#!/usr/bin/env python3
"""App Monitor sender for Termux.

Sends battery percentage, charging state, CPU and RAM to the App Monitor
parent device without installing the Android app on the monitored device.
"""
import argparse
import json
import socket
import subprocess
import time

PORT = 45820


def battery():
    try:
        raw = subprocess.check_output(["termux-battery-status"], text=True, timeout=5)
        data = json.loads(raw)
        percent = int(data.get("percentage", 0))
        status = str(data.get("status", "")).upper()
        plugged = str(data.get("plugged", "")).upper()
        charging = status in {"CHARGING", "FULL"} or plugged not in {"", "UNPLUGGED", "NONE", "UNKNOWN"}
        return max(0, min(100, percent)), charging
    except Exception:
        try:
            with open("/sys/class/power_supply/battery/capacity") as f:
                percent = max(0, min(100, int(f.read().strip())))
            charging = False
            try:
                with open("/sys/class/power_supply/battery/status") as f:
                    charging = f.read().strip().upper() in {"CHARGING", "FULL"}
            except Exception:
                pass
            return percent, charging
        except Exception:
            return 0, False


def cpu_percent():
    def read():
        with open("/proc/stat") as f:
            values = f.readline().split()[1:]
        nums = list(map(int, values))
        idle = nums[3] + (nums[4] if len(nums) > 4 else 0)
        return sum(nums), idle
    total1, idle1 = read()
    time.sleep(0.25)
    total2, idle2 = read()
    total = total2 - total1
    idle = idle2 - idle1
    return max(0, min(100, round(100 * (total - idle) / total))) if total else 0


def ram_percent():
    info = {}
    with open("/proc/meminfo") as f:
        for line in f:
            key, value = line.split(":", 1)
            info[key] = int(value.strip().split()[0])
    total = info.get("MemTotal", 0)
    available = info.get("MemAvailable", info.get("MemFree", 0))
    return max(0, min(100, round(100 * (total - available) / total))) if total else 0


def send(host, code, interval):
    source = socket.gethostname() or "Termux device"
    while True:
        try:
            with socket.create_connection((host, PORT), timeout=15) as sock:
                sock.settimeout(15)
                sock.sendall(f"HELLO|{code}|{source}\n".encode())
                response = sock.recv(64).decode(errors="replace").strip()
                if response != "OK":
                    raise RuntimeError("Parent rejected the pairing code")
                print("Connected. Sending telemetry every", interval, "seconds.")
                while True:
                    b, charging = battery()
                    c = cpu_percent()
                    r = ram_percent()
                    line = f"STAT|{b}|{1 if charging else 0}|{c}|{r}|-1|{int(time.time() * 1000)}\n"
                    sock.sendall(line.encode())
                    print(f"Battery {b}% | {'Charging' if charging else 'Not charging'} | CPU {c}% | RAM {r}%")
                    time.sleep(interval)
        except KeyboardInterrupt:
            print("\nStopped.")
            return
        except Exception as exc:
            print("Connection lost:", exc, "Retrying in 5 seconds...")
            time.sleep(5)


def main():
    parser = argparse.ArgumentParser(description="Send Termux device stats to App Monitor")
    parser.add_argument("parent_ip", help="IPv4 address shown by App Monitor parent mode")
    parser.add_argument("pairing_code", help="6-digit pairing code shown by App Monitor parent mode")
    parser.add_argument("--interval", type=float, default=5, help="seconds between telemetry packets")
    args = parser.parse_args()
    if len(args.pairing_code) != 6 or not args.pairing_code.isdigit():
        parser.error("pairing_code must be exactly 6 digits")
    send(args.parent_ip, args.pairing_code, max(1, args.interval))


if __name__ == "__main__":
    main()
