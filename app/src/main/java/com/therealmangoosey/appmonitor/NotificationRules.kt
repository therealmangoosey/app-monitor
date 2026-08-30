package com.therealmangoosey.appmonitor

import android.content.Context
import org.json.JSONArray

class NotificationRules(context: Context) {
    private val prefs = context.getSharedPreferences("notification_rules", Context.MODE_PRIVATE)

    fun all(): List<NotificationRule> {
        val raw = prefs.getString("rules", "[]") ?: "[]"
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { index ->
            runCatching { NotificationRule.fromJson(array.getJSONObject(index)) }.getOrNull()
        }
    }

    fun save(rules: List<NotificationRule>) {
        val array = JSONArray()
        rules.forEach { array.put(it.toJson()) }
        prefs.edit().putString("rules", array.toString()).apply()
    }

    fun add(rule: NotificationRule) = save(all() + rule)
    fun remove(id: Long) = save(all().filterNot { it.id == id })
    fun update(rule: NotificationRule) = save(all().map { if (it.id == rule.id) rule else it })
}
