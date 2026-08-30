package com.therealmangoosey.appmonitor

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.graphics.Typeface

class WebhooksActivity : Activity() {
    private lateinit var list: LinearLayout
    private lateinit var store: WebhookSettings
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); store = WebhookSettings(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 32, 24, 24) }
        root.addView(TextView(this).apply { text = "Webhooks"; textSize = 28f; setTypeface(null, Typeface.BOLD) })
        root.addView(TextView(this).apply { text = "Send telemetry from the device that actually produced it. Discord and Slack use formatted cards; Generic sends JSON."; textSize = 15f; setPadding(0, 8, 0, 18) })
        root.addView(Button(this).apply { text = "Add webhook"; setOnClickListener { showEditor(null) } })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root); refresh()
    }
    private fun refresh() {
        list.removeAllViews(); val items = store.all()
        items.forEach { config ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 16, 0, 16) }
            row.addView(TextView(this).apply { text = "${config.name}\nPreset: ${platformName(config.platform)}\n${if (config.enabled) "Enabled" else "Disabled"} • ${if (config.sendEveryUpdate) "Every update" else "Manual/test only"}"; textSize = 16f })
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            listButtons@ buttons.apply {
                addView(Button(this@WebhooksActivity).apply { text = "Test"; setOnClickListener { val test = Telemetry(73, false, 18, 42, 215); Thread { WebhookSender.send(config, test, "Test device") }.start() } })
                addView(Button(this@WebhooksActivity).apply { text = "Edit"; setOnClickListener { showEditor(config) } })
                addView(Button(this@WebhooksActivity).apply { text = if (config.enabled) "Disable" else "Enable"; setOnClickListener { store.update(config.copy(enabled = !config.enabled)); refresh() } })
                addView(Button(this@WebhooksActivity).apply { text = "Delete"; setOnClickListener { store.remove(config.id); refresh() } })
            }
            row.addView(buttons); list.addView(row, ViewGroup.LayoutParams(-1, -2))
        }
        if (items.isEmpty()) list.addView(TextView(this).apply { text = "No webhooks yet."; textSize = 16f; setPadding(0, 24, 0, 0) })
    }
    private fun showEditor(existing: WebhookConfig?) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 0, 12, 0) }
        val preset = Spinner(this).apply { adapter = ArrayAdapter(this@WebhooksActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("Discord", "Slack", "Generic JSON")) }
        val name = EditText(this).apply { hint = "Webhook name" }
        val url = EditText(this).apply { hint = "Webhook URL"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
        val every = CheckBox(this).apply { text = "Send every telemetry update"; isChecked = existing?.sendEveryUpdate ?: true }
        val battery = CheckBox(this).apply { text = "Battery %"; isChecked = existing?.includeBattery ?: true }
        val charging = CheckBox(this).apply { text = "Charging state"; isChecked = existing?.includeCharging ?: true }
        val cpu = CheckBox(this).apply { text = "CPU %"; isChecked = existing?.includeCpu ?: true }
        val ram = CheckBox(this).apply { text = "RAM %"; isChecked = existing?.includeRam ?: true }
        val time = CheckBox(this).apply { text = "Time until empty"; isChecked = existing?.includeTimeRemaining ?: true }
        val detail = TextView(this).apply { textSize = 13f; setPadding(0, 8, 0, 12) }
        fun updateDetail() { detail.text = when (preset.selectedItemPosition) { 0 -> "Discord: polished embed with fields, timestamp and source."; 1 -> "Slack: formatted Block Kit message with device statistics."; else -> "Generic: plain JSON for services that accept custom webhook payloads." } }
        preset.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener { override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = updateDetail(); override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit }
        box.addView(TextView(this).apply { text = "Preset"; textSize = 13f }); box.addView(preset); box.addView(detail); box.addView(name); box.addView(url); box.addView(every); box.addView(battery); box.addView(charging); box.addView(cpu); box.addView(ram); box.addView(time)
        existing?.let { c -> preset.setSelection(when (c.platform) { "discord" -> 0; "slack" -> 1; else -> 2 }); name.setText(c.name); url.setText(c.url) } ?: run { name.setText("Device status"); updateDetail() }
        AlertDialog.Builder(this).setTitle(if (existing == null) "New webhook" else "Edit webhook").setView(ScrollView(this).apply { addView(box) }).setPositiveButton("Save") { _, _ ->
            val platform = when (preset.selectedItemPosition) { 0 -> "discord"; 1 -> "slack"; else -> "generic" }
            val config = WebhookConfig(existing?.id ?: System.currentTimeMillis(), name.text.toString().ifBlank { "Device status" }, platform, url.text.toString().trim(), existing?.enabled ?: true, every.isChecked, battery.isChecked, charging.isChecked, cpu.isChecked, ram.isChecked, time.isChecked)
            if (existing == null) store.add(config) else store.update(config); refresh()
        }.setNegativeButton("Cancel", null).show()
    }
    private fun platformName(value: String) = when (value) { "discord" -> "Discord Embed"; "slack" -> "Slack Blocks"; else -> "Generic JSON" }
}
