package com.therealmangoosey.appmonitor

import org.json.JSONObject

data class NotificationRule(
    val id: Long,
    val metric: String,
    val operator: String,
    val threshold: Int,
    val title: String,
    val message: String,
    val importance: Int,
    val action: String,
    val enabled: Boolean
) {
    fun matches(value: Int): Boolean = when (operator) {
        "<=" -> value <= threshold
        ">=" -> value >= threshold
        "<" -> value < threshold
        ">" -> value > threshold
        "=" -> value == threshold
        else -> false
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("metric", metric); put("operator", operator); put("threshold", threshold)
        put("title", title); put("message", message); put("importance", importance); put("action", action); put("enabled", enabled)
    }

    companion object {
        fun fromJson(value: JSONObject) = NotificationRule(
            value.optLong("id"), value.optString("metric", "battery"), value.optString("operator", "<="),
            value.optInt("threshold", 20), value.optString("title", "Battery alert"), value.optString("message", "Battery is {value}%"),
            value.optInt("importance", 2), value.optString("action", "open_app"), value.optBoolean("enabled", true)
        )
    }
}
