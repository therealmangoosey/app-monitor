package com.therealmangoosey.appmonitor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WebhookConfig(
    val id: Long,
    val name: String,
    val platform: String,
    val url: String,
    val enabled: Boolean,
    val sendEveryUpdate: Boolean,
    val includeBattery: Boolean,
    val includeCharging: Boolean,
    val includeCpu: Boolean,
    val includeRam: Boolean,
    val includeTimeRemaining: Boolean
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("platform", platform); put("url", url)
        put("enabled", enabled); put("sendEveryUpdate", sendEveryUpdate)
        put("includeBattery", includeBattery); put("includeCharging", includeCharging)
        put("includeCpu", includeCpu); put("includeRam", includeRam)
        put("includeTimeRemaining", includeTimeRemaining)
    }

    companion object {
        fun fromJson(o: JSONObject) = WebhookConfig(
            o.optLong("id"), o.optString("name", "Webhook"), o.optString("platform", "discord"),
            o.optString("url"), o.optBoolean("enabled", true), o.optBoolean("sendEveryUpdate", true),
            o.optBoolean("includeBattery", true), o.optBoolean("includeCharging", true),
            o.optBoolean("includeCpu", true), o.optBoolean("includeRam", true), o.optBoolean("includeTimeRemaining", true)
        )
    }
}

class WebhookSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("webhooks", Context.MODE_PRIVATE)

    fun all(): List<WebhookConfig> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        val array = JSONArray(raw)
        return (0 until array.length()).map { WebhookConfig.fromJson(array.getJSONObject(it)) }
    }

    fun add(config: WebhookConfig) = save(all() + config)
    fun update(config: WebhookConfig) = save(all().map { if (it.id == config.id) config else it })
    fun remove(id: Long) = save(all().filterNot { it.id == id })

    private fun save(items: List<WebhookConfig>) {
        val array = JSONArray(); items.forEach { array.put(it.toJson()) }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
