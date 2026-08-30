package com.therealmangoosey.appmonitor

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import android.graphics.Typeface

class NotificationRulesActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var store: NotificationRules

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = NotificationRules(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 32)
        }
        root.addView(TextView(this).apply {
            text = "Notification rules"
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Create alerts for battery, CPU or RAM. {value} in the message is replaced with the current percentage."
            textSize = 15f
            setPadding(0, 8, 0, 20)
        })
        root.addView(Button(this).apply {
            text = "Add rule"
            setOnClickListener { showRuleDialog(null) }
        })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refresh()
    }

    private fun refresh() {
        list.removeAllViews()
        store.all().forEach { rule ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 18, 0, 18)
            }
            row.addView(TextView(this).apply {
                text = "${rule.title}\n${rule.metric.uppercase()} ${rule.operator} ${rule.threshold}%\n${rule.message}\nType: ${importanceName(rule.importance)} • ${if (rule.enabled) "Enabled" else "Disabled"}"
                textSize = 16f
            })
            val buttons = LinearLayout(this)
            buttons.addView(Button(this).apply {
                text = "Edit"
                setOnClickListener { showRuleDialog(rule) }
            })
            buttons.addView(Button(this).apply {
                text = if (rule.enabled) "Disable" else "Enable"
                setOnClickListener {
                    store.update(rule.copy(enabled = !rule.enabled))
                    refresh()
                }
            })
            buttons.addView(Button(this).apply {
                text = "Delete"
                setOnClickListener {
                    store.remove(rule.id)
                    refresh()
                }
            })
            row.addView(buttons)
            list.addView(row, ViewGroup.LayoutParams(-1, -2))
        }
        if (store.all().isEmpty()) list.addView(TextView(this).apply {
            text = "No rules yet. Add one to get alerts from this device or a connected child."
            textSize = 16f
            setPadding(0, 24, 0, 0)
        })
    }

    private fun showRuleDialog(existing: NotificationRule?) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 0, 20, 0)
        }
        val metric = Spinner(this)
        metric.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("battery", "cpu", "ram"))
        val operator = Spinner(this)
        operator.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("<=", ">=", "<", ">", "="))
        val threshold = EditText(this).apply { hint = "Threshold percentage"; inputType = 2 }
        val title = EditText(this).apply { hint = "Notification title" }
        val message = EditText(this).apply { hint = "Notification message, e.g. Battery is {value}%" }
        val importance = Spinner(this)
        importance.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("Silent", "Low", "Default", "High"))
        box.addView(metric); box.addView(operator); box.addView(threshold); box.addView(title); box.addView(message); box.addView(importance)
        existing?.let {
            metric.setSelection(arrayOf("battery", "cpu", "ram").indexOf(it.metric).coerceAtLeast(0))
            operator.setSelection(arrayOf("<=", ">=", "<", ">", "=").indexOf(it.operator).coerceAtLeast(0))
            threshold.setText(it.threshold.toString()); title.setText(it.title); message.setText(it.message)
            importance.setSelection((it.importance - 1).coerceIn(0, 3))
        } ?: run {
            threshold.setText("20"); title.setText("Battery alert"); message.setText("Battery is {value}%")
            importance.setSelection(2)
        }
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "New notification rule" else "Edit notification rule")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val rule = NotificationRule(
                    id = existing?.id ?: System.currentTimeMillis(),
                    metric = metric.selectedItem.toString(),
                    operator = operator.selectedItem.toString(),
                    threshold = threshold.text.toString().toIntOrNull()?.coerceIn(0, 100) ?: 20,
                    title = title.text.toString().ifBlank { "App Monitor alert" },
                    message = message.text.toString().ifBlank { "{value}%" },
                    importance = importance.selectedItemPosition + 1,
                    enabled = existing?.enabled ?: true
                )
                if (existing == null) store.add(rule) else store.update(rule)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importanceName(value: Int) = when (value) {
        1 -> "Silent"
        2 -> "Low"
        3 -> "Default"
        else -> "High"
    }
}
