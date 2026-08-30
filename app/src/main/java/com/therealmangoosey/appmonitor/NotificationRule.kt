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
        put("id", id)
        put("metric", metric)
        put("operator", operator)
        put("threshold", threshold)
        put("title", title)
        put("message", message)
        put("importance", importance)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(value: JSONObject) = NotificationRule(
            id = value.optLong("id"),
            metric = value.optString("metric", "battery"),
            operator = value.optString("operator", "<="),
            threshold = value.optInt("threshold", 20),
            title = value.optString("title", "Battery alert"),
            message = value.optString("message", "Battery is {value}%"),
            importance = value.optInt("importance", 2),
            enabled = value.optBoolean("enabled", true)
        )
    }
}
