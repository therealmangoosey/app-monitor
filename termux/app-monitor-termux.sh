#!/data/data/com.termux/files/usr/bin/bash
set -u

PORT=45820
PARENT_IP=""
PAIRING_CODE=""
INTERVAL=10

usage() {
  echo "Usage: $0 --parent-ip IP --code 123456 [--interval SECONDS]"
  echo "Sends battery, CPU, RAM and estimated battery time to the App Monitor parent."
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --parent-ip) PARENT_IP="${2:-}"; shift 2 ;;
    --code) PAIRING_CODE="${2:-}"; shift 2 ;;
    --interval) INTERVAL="${2:-10}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1"; usage; exit 1 ;;
  esac
done

if [ -z "$PARENT_IP" ] || [ -z "$PAIRING_CODE" ]; then usage; exit 1; fi

command -v termux-battery-status >/dev/null 2>&1 || { echo "Install Termux:API and run: pkg install termux-api"; exit 1; }

cpu_percent() {
  read -r _ u n s i io irq sirq st _ < /proc/stat
  total1=$((u+n+s+i+io+irq+sirq+st)); idle1=$((i+io))
  sleep 1
  read -r _ u n s i io irq sirq st _ < /proc/stat
  total2=$((u+n+s+i+io+irq+sirq+st)); idle2=$((i+io))
  dt=$((total2-total1)); di=$((idle2-idle1))
  [ "$dt" -gt 0 ] && echo $(( (100*(dt-di))/dt )) || echo 0
}

while true; do
  battery_json="$(termux-battery-status 2>/dev/null || echo '{}')"
  battery="$(printf '%s' "$battery_json" | grep -o '"percentage"[[:space:]]*:[[:space:]]*[0-9]*' | grep -o '[0-9]*$' | head -1)"
  [ -z "$battery" ] && battery=0
  status="$(printf '%s' "$battery_json" | grep -o '"status"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/.*"\([^"]*\)"$/\1/' | head -1)"
  temp="$(printf '%s' "$battery_json" | grep -o '"temperature"[[:space:]]*:[[:space:]]*[0-9.]*' | grep -o '[0-9.]*$' | head -1)"
  cpu="$(cpu_percent)"
  ram_total_kb="$(awk '/MemTotal/ {print $2}' /proc/meminfo)"
  ram_avail_kb="$(awk '/MemAvailable/ {print $2}' /proc/meminfo)"
  ram=$(( (100*(ram_total_kb-ram_avail_kb))/ram_total_kb ))
  payload="{\"code\":\"$PAIRING_CODE\",\"batteryPercent\":$battery,\"cpuPercent\":$cpu,\"ramPercent\":$ram,\"batteryStatus\":\"$status\",\"temperature\":${temp:-0}}"
  printf '%s\n' "$payload" | nc -w 3 "$PARENT_IP" "$PORT" 2>/dev/null || true
  sleep "$INTERVAL"
done
