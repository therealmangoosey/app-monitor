package com.therealmangoosey.appmonitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.Future

class MonitorService : Service() {
    private val executor = Executors.newCachedThreadPool()
    private var worker: Future<*>? = null
    private var running = false
    private var clientSocket: Socket? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PARENT -> startParent(intent.getStringExtra(EXTRA_CODE).orEmpty())
            ACTION_START_CHILD -> startChild(
                intent.getStringExtra(EXTRA_HOST).orEmpty(),
                intent.getStringExtra(EXTRA_CODE).orEmpty()
            )
            ACTION_STOP -> stopMonitoring()
        }
        return START_NOT_STICKY
    }

    private fun startParent(code: String) {
        if (running) return
        running = true
        startForeground(
            NOTIFICATION_ID,
            notification("Parent mode: waiting for child device")
        )
        worker = executor.submit {
            try {
                ServerSocket(PORT).use { server ->
                    while (running) {
                        val socket = server.accept()
                        clientSocket = socket
                        handleParentClient(socket, code)
                        clientSocket = null
                    }
                }
            } catch (_: Exception) {
                if (running) broadcastStatus("Parent listener stopped")
            }
        }
    }

    private fun handleParentClient(socket: Socket, code: String) {
        socket.use { s ->
            s.soTimeout = 15_000
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val writer = PrintWriter(s.getOutputStream(), true)
            val hello = reader.readLine()
            if (hello != "HELLO|$code") {
                writer.println("DENIED")
                broadcastStatus("Pairing rejected")
                return
            }
            writer.println("OK")
            broadcastStatus("Child connected")
            s.soTimeout = 0
            while (running) {
                val line = reader.readLine() ?: break
                Telemetry.decode(line)?.let { telemetry ->
                    broadcastTelemetry(telemetry)
                }
            }
            broadcastStatus("Child disconnected")
        }
    }

    private fun startChild(host: String, code: String) {
        if (running) return
        if (!validIpv4(host) || code.length != 6) {
            broadcastStatus("Enter a valid parent IP and 6-digit code")
            return
        }
        running = true
        startForeground(
            NOTIFICATION_ID,
            notification("Child mode: sending status to parent")
        )
        worker = executor.submit {
            try {
                Socket(host, PORT).use { socket ->
                    clientSocket = socket
                    socket.soTimeout = 15_000
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    writer.println("HELLO|$code")
                    if (reader.readLine() != "OK") {
                        broadcastStatus("Parent rejected the pairing code")
                        return@submit
                    }
                    broadcastStatus("Connected to parent")
                    socket.soTimeout = 0
                    val metrics = MetricsReader(this)
                    while (running) {
                        val snapshot = metrics.read()
                        writer.println(
                            Telemetry(
                                batteryPercent = snapshot.batteryPercent,
                                cpuPercent = snapshot.cpuPercent,
                                ramPercent = snapshot.ramPercent,
                                batteryMinutesRemaining = snapshot.batteryMinutesRemaining
                            ).encode()
                        )
                        broadcastTelemetry(
                            Telemetry(
                                batteryPercent = snapshot.batteryPercent,
                                cpuPercent = snapshot.cpuPercent,
                                ramPercent = snapshot.ramPercent,
                                batteryMinutesRemaining = snapshot.batteryMinutesRemaining
                            )
                        )
                        Thread.sleep(5_000)
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: Exception) {
                if (running) broadcastStatus("Could not connect to parent")
            } finally {
                clientSocket = null
            }
        }
    }

    private fun stopMonitoring() {
        running = false
        try { clientSocket?.close() } catch (_: Exception) { }
        worker?.cancel(true)
        worker = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        try { clientSocket?.close() } catch (_: Exception) { }
        worker?.cancel(true)
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun broadcastTelemetry(telemetry: Telemetry) {
        sendBroadcast(
            Intent(ACTION_TELEMETRY).apply {
                setPackage(packageName)
                putExtra(EXTRA_BATTERY, telemetry.batteryPercent)
                putExtra(EXTRA_CPU, telemetry.cpuPercent)
                putExtra(EXTRA_RAM, telemetry.ramPercent)
                putExtra(EXTRA_MINUTES, telemetry.batteryMinutesRemaining ?: -1L)
                putExtra(EXTRA_TIMESTAMP, telemetry.timestampMillis)
            }
        )
    }

    private fun broadcastStatus(status: String) {
        sendBroadcast(
            Intent(ACTION_STATUS).apply {
                setPackage(packageName)
                putExtra(EXTRA_STATUS, status)
            }
        )
    }

    private fun notification(text: String) = android.app.Notification.Builder(this, CHANNEL_ID)
        .setContentTitle("App Monitor")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_menu_info_details)
        .setOngoing(true)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "App Monitor service",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    companion object {
        const val ACTION_START_PARENT = "com.therealmangoosey.appmonitor.START_PARENT"
        const val ACTION_START_CHILD = "com.therealmangoosey.appmonitor.START_CHILD"
        const val ACTION_STOP = "com.therealmangoosey.appmonitor.STOP"
        const val ACTION_TELEMETRY = "com.therealmangoosey.appmonitor.TELEMETRY"
        const val ACTION_STATUS = "com.therealmangoosey.appmonitor.STATUS"
        const val EXTRA_CODE = "code"
        const val EXTRA_HOST = "host"
        const val EXTRA_BATTERY = "battery"
        const val EXTRA_CPU = "cpu"
        const val EXTRA_RAM = "ram"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_TIMESTAMP = "timestamp"
        const val EXTRA_STATUS = "status"
        const val PORT = 45820
        private const val CHANNEL_ID = "app_monitor_service"
        private const val NOTIFICATION_ID = 45820

        private fun validIpv4(value: String): Boolean {
            return try {
                val address = NetworkInterface.getNetworkInterfaces().toList()
                    .flatMap { it.inetAddresses.toList() }
                    .firstOrNull { it.hostAddress == value }
                address is Inet4Address
            } catch (_: Exception) {
                false
            }
        }
    }
}
