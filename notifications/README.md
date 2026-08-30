# Notification rules

App Monitor notifications are intended to be visible alerts for devices you have permission to monitor.

Each rule can be represented by:

- **Metric:** battery, CPU or RAM
- **Condition:** below / at-or-below / above / at-or-above
- **Threshold:** percentage value
- **Title:** notification title
- **Message:** notification body
- **Type:** normal, high-priority or persistent
- **Action:** open the monitored device, open App Monitor, or no action
- **Cooldown:** minimum time between repeated notifications

Example rules:

| Metric | Condition | Threshold | Type | Example message |
|---|---|---:|---|---|
| Battery | at-or-below | 20% | High priority | Battery is at {battery}% |
| Battery | at-or-below | 10% | Persistent | Battery critically low: {battery}% |
| CPU | at-or-above | 90% | Normal | CPU usage is {cpu}% |
| RAM | at-or-above | 90% | Normal | RAM usage is {ram}% |

Suggested behaviour:

- Normal alerts appear as ordinary notifications.
- High-priority alerts use the app's high-importance notification channel and should be used sparingly.
- Persistent alerts remain visible until dismissed when Android permits it.
- Cooldowns prevent an alert from firing every polling interval.
- Rules are local app settings and should be clearly visible/editable by the user.
