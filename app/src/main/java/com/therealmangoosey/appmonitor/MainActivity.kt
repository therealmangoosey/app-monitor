package com.therealmangoosey.appmonitor

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

class MainActivity : Activity() {
    private lateinit var content: LinearLayout
    private lateinit var modeLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var statsLabel: TextView
    private lateinit var pairingLabel: TextView
    private lateinit var ipInput: EditText
    private lateinit var codeInput: EditText

    private val handler = Handler(Looper.getMainLooper())
    private var localOnly = true
    private var localPolling = false
    private val localReader by lazy { MetricsReader(this) }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MonitorService.ACTION_STATUS -> {
                    statusLabel.text = intent.getStringExtra(MonitorService.EXTRA_STATUS).orEmpty()
                }
                MonitorService.ACTION_TELEMETRY -> {
                    val battery = intent.getIntExtra(MonitorService.EXTRA_BATTERY, 0)
                    val cpu = intent.getIntExtra(MonitorService.EXTRA_CPU, 0)
                    val ram = intent.getIntExtra(MonitorService.EXTRA_RAM, 0)
                    val minutes = intent.getLongExtra(MonitorService.EXTRA_MINUTES, -1L)
                    showStats(battery, cpu, ram, minutes)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        registerReceiverCompat()
        requestNotificationPermission()
        showLocalMode()
    }

    override fun onDestroy() {
        stopLocalPolling()
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 40)
        }
        scroll.addView(content)
        setContentView(scroll)

        val title = TextView(this).apply {
            text = "App Monitor"
            textSize = 30f
            setTypeface(null, Typeface.BOLD)
        }
        content.addView(title)

        modeLabel = TextView(this).apply {
            textSize = 16f
            setPadding(0, 8, 0, 32)
        }
        content.addView(modeLabel)

        content.addView(makeButton("Local-only mode") { showLocalMode() })
        content.addView(makeButton("Parent mode") { showParentMode() })
        content.addView(makeButton("Child mode") { showChildMode() })

        pairingLabel = TextView(this).apply {
            textSize = 17f
            setPadding(0, 28, 0, 8)
        }
        content.addView(pairingLabel)

        ipInput = EditText(this).apply {
            hint = "Parent IP address"
            maxLines = 1
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        codeInput = EditText(this).apply {
            hint = "6-digit pairing code"
            maxLines = 1
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        content.addView(ipInput)
        content.addView(codeInput)

        content.addView(makeButton("Start") { startSelectedMode() })
        content.addView(makeButton("Stop") { stopMonitoring() })

        statusLabel = TextView(this).apply {
            textSize = 16f
            setPadding(0, 24, 0, 16)
        }
        statsLabel = TextView(this).apply {
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 16, 0, 24)
        }
        content.addView(statusLabel)
        content.addView(statsLabel)
    }

    private fun showLocalMode() {
        localOnly = true
        stopMonitoringServiceOnly()
        pairingLabel.text = "Local-only\nNothing is sent to a parent or uploaded."
        ipInput.visibility = View.GONE
        codeInput.visibility = View.GONE
        modeLabel.text = "Showing this device's battery, CPU and RAM"
        statusLabel.text = "Local monitoring active"
        startLocalPolling()
    }

    private fun showParentMode() {
        localOnly = false
        stopLocalPolling()
        val code = getOrCreatePairingCode()
        pairingLabel.text = "Parent mode\nGive the child device this code:\n$code\n\nYour local IP: ${getLocalIpv4() ?: "Unavailable"}"
        ipInput.visibility = View.GONE
        codeInput.visibility = View.GONE
        modeLabel.text = "This device receives battery, CPU, RAM and time-to-empty from the paired child."
        statusLabel.text = "Ready to listen"
        statsLabel.text = "No child connected"
    }

    private fun showChildMode() {
        localOnly = false
        stopLocalPolling()
        pairingLabel.text = "Child mode\nEnter the parent device's local IP and pairing code."
        ipInput.visibility = View.VISIBLE
        codeInput.visibility = View.VISIBLE
        modeLabel.text = "This device will send only battery, CPU, RAM and estimated time-to-empty."
        statusLabel.text = "Not connected"
        statsLabel.text = ""
    }

    private fun startSelectedMode() {
        if (localOnly) {
            startLocalPolling()
            return
        }
        if (ipInput.visibility == View.VISIBLE) {
            val host = ipInput.text.toString().trim()
            val code = codeInput.text.toString().trim()
            if (host.isBlank() || code.length != 6) {
                statusLabel.text = "Enter the parent IP and 6-digit code"
                return
            }
            startMonitorService(
                Intent(this, MonitorService::class.java).apply {
                    action = MonitorService.ACTION_START_CHILD
                    putExtra(MonitorService.EXTRA_HOST, host)
                    putExtra(MonitorService.EXTRA_CODE, code)
                }
            )
        } else {
            startMonitorService(
                Intent(this, MonitorService::class.java).apply {
                    action = MonitorService.ACTION_START_PARENT
                    putExtra(MonitorService.EXTRA_CODE, getOrCreatePairingCode())
                }
            )
        }
    }

    private fun stopMonitoring() {
        stopLocalPolling()
        stopMonitoringServiceOnly()
        statusLabel.text = "Stopped"
    }

    private fun startMonitorService(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopMonitoringServiceOnly() {
        stopService(Intent(this, MonitorService::class.java))
    }

    private fun startLocalPolling() {
        if (localPolling) return
        localPolling = true
        val task = object : Runnable {
            override fun run() {
                if (!localPolling) return
                Thread {
                    val snapshot = localReader.read()
                    runOnUiThread {
                        if (localPolling) {
                            showStats(
                                snapshot.batteryPercent,
                                snapshot.cpuPercent,
                                snapshot.ramPercent,
                                snapshot.batteryMinutesRemaining ?: -1L
                            )
                        }
                    }
                }.start()
                handler.postDelayed(this, 2_000)
            }
        }
        handler.post(task)
    }

    private fun stopLocalPolling() {
        localPolling = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun showStats(battery: Int, cpu: Int, ram: Int, minutes: Long) {
        val time = if (minutes >= 0) formatMinutes(minutes) else "Unavailable"
        statsLabel.text = "Battery  $battery%\nCPU  $cpu%\nRAM  $ram%\nTime until empty  $time"
    }

    private fun formatMinutes(minutes: Long): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }

    private fun getOrCreatePairingCode(): String {
        val prefs = getSharedPreferences("pairing", MODE_PRIVATE)
        val existing = prefs.getString("code", null)
        if (existing != null && existing.length == 6) return existing
        val code = (100000 + SecureRandom().nextInt(900000)).toString()
        prefs.edit().putString("code", code).apply()
        return code
    }

    private fun getLocalIpv4(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }

    private fun makeButton(text: String, click: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setOnClickListener { click() }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun registerReceiverCompat() {
        val filter = IntentFilter().apply {
            addAction(MonitorService.ACTION_STATUS)
            addAction(MonitorService.ACTION_TELEMETRY)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }
}
