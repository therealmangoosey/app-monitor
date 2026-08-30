package com.therealmangoosey.appmonitor

import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

object WebhookSender {
    fun send(config: WebhookConfig, telemetry: Telemetry, source: String) {
        if (!config.enabled || config.url.isBlank()) return
        try {
            val connection = (URL(config.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 8_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "App-Monitor/${BuildConfig.VERSION_NAME}")
            }
            connection.outputStream.use { it.write(payload(config, telemetry, source).toString().toByteArray(Charsets.UTF_8)) }
            connection.inputStream.close()
            connection.disconnect()
        } catch (_: Exception) {
            // Webhook failures must never stop device monitoring.
        }
    }

    private fun payload(config: WebhookConfig, t: Telemetry, source: String): JSONObject = when (config.platform) {
        "discord" -> discordPayload(config, t, source)
        "slack" -> slackPayload(config, t, source)
        else -> genericPayload(config, t, source)
    }

    private fun discordPayload(config: WebhookConfig, t: Telemetry, source: String): JSONObject {
        val fields = JSONArray()
        if (config.includeBattery) fields.put(field("Battery", "${t.batteryPercent}%"))
        if (config.includeCharging) fields.put(field("Power", if (t.charging) "Charging" else "Not charging"))
        if (config.includeCpu) fields.put(field("CPU", "${t.cpuPercent}%"))
        if (config.includeRam) fields.put(field("RAM", "${t.ramPercent}%"))
        if (config.includeTimeRemaining) fields.put(field("Time until empty", t.batteryMinutesRemaining?.let(::formatMinutes) ?: "Unavailable"))
        fields.put(field("Source", source))
        return JSONObject().apply {
            put("username", "App Monitor")
            put("embeds", JSONArray().put(JSONObject().apply {
                put("title", config.name.ifBlank { "App Monitor" })
                put("description", "Live device status update")
                put("fields", fields)
                put("timestamp", Instant.ofEpochMilli(t.timestampMillis).toString())
                put("footer", JSONObject().put("text", "App Monitor • ${BuildConfig.VERSION_NAME}"))
            }))
        }
    }

    private fun slackPayload(config: WebhookConfig, t: Telemetry, source: String): JSONObject {
        val lines = mutableListOf<String>()
        if (config.includeBattery) lines += "*Battery:* ${t.batteryPercent}%"
        if (config.includeCharging) lines += "*Power:* ${if (t.charging) "Charging" else "Not charging"}"
        if (config.includeCpu) lines += "*CPU:* ${t.cpuPercent}%"
        if (config.includeRam) lines += "*RAM:* ${t.ramPercent}%"
        if (config.includeTimeRemaining) lines += "*Time until empty:* ${t.batteryMinutesRemaining?.let(::formatMinutes) ?: "Unavailable"}"
        lines += "*Source:* $source"
        return JSONObject().apply {
            put("text", "${config.name.ifBlank { "App Monitor" }} • $source")
            put("blocks", JSONArray().apply {
                put(JSONObject().put("type", "header").put("text", JSONObject().put("type", "plain_text").put("text", config.name.ifBlank { "App Monitor" })))
                put(JSONObject().put("type", "section").put("text", JSONObject().put("type", "mrkdwn").put("text", lines.joinToString("\n"))))
            })
        }
    }

    private fun genericPayload(config: WebhookConfig, t: Telemetry, source: String): JSONObject = JSONObject().apply {
        put("event", "telemetry")
        put("source", source)
        put("name", config.name)
        put("timestamp", t.timestampMillis)
        put("batteryPercent", t.batteryPercent)
        put("charging", t.charging)
        put("cpuPercent", t.cpuPercent)
        put("ramPercent", t.ramPercent)
        put("batteryMinutesRemaining", t.batteryMinutesRemaining ?: JSONObject.NULL)
    }

    private fun field(name: String, value: String) = JSONObject().put("name", name).put("value", value).put("inline", true)

    private fun formatMinutes(minutes: Long): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }
}
