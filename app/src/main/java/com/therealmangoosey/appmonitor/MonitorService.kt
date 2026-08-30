package com.therealmangoosey.appmonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.Future

class MonitorService : Service() {
    private val executor = Executors.newCachedThreadPool(); private var worker: Future<*>? = null
    @Volatile private var running = false; private var clientSocket: Socket? = null
    private lateinit var rules: NotificationRules; private lateinit var webhooks: WebhookSettings
    private val lastTriggered = mutableMapOf<Long, Int>()
    override fun onCreate() { super.onCreate(); rules = NotificationRules(this); webhooks = WebhookSettings(this); createNotificationChannel() }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_LOCAL -> startLocal()
            ACTION_START_PARENT -> startParent(intent.getStringExtra(EXTRA_CODE).orEmpty())
            ACTION_START_CHILD -> startChild(intent.getStringExtra(EXTRA_HOST).orEmpty(), intent.getStringExtra(EXTRA_CODE).orEmpty())
            ACTION_STOP -> stopMonitoring()
        }; return START_NOT_STICKY
    }
    private fun startLocal() {
        if (running) return; running = true; startForeground(NOTIFICATION_ID, notification("Local monitoring active"))
        worker = executor.submit { val metrics = MetricsReader(this); try { while (running) { val s = metrics.read(); broadcastTelemetry(Telemetry(s.batteryPercent, s.charging, s.cpuPercent, s.ramPercent, s.batteryMinutesRemaining), "This device"); Thread.sleep(5_000) } } catch (_: InterruptedException) { Thread.currentThread().interrupt() } }
    }
    private fun startParent(code: String) {
        if (running || code.length != 6) return; running = true; startForeground(NOTIFICATION_ID, notification("Parent mode: waiting for child or Termux device"))
        worker = executor.submit { try { ServerSocket(PORT).use { server -> while (running) { val socket = server.accept(); clientSocket = socket; handleParentClient(socket, code); clientSocket = null } } } catch (_: Exception) { if (running) broadcastStatus("Parent listener stopped") } }
    }
    private fun handleParentClient(socket: Socket, code: String) {
        socket.use { s ->
            s.soTimeout = 15_000; val reader = BufferedReader(InputStreamReader(s.getInputStream())); val writer = PrintWriter(s.getOutputStream(), true)
            val hello = reader.readLine() ?: ""; val parts = hello.split('|')
            if (parts.size !in 2..3 || parts[0] != "HELLO" || parts[1] != code) { writer.println("DENIED"); broadcastStatus("Pairing rejected"); return }
            val source = parts.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() } ?: "Remote device"
            writer.println("OK"); broadcastStatus("Device connected: $source"); s.soTimeout = 0
            while (running) { val line = reader.readLine() ?: break; Telemetry.decode(line)?.let { broadcastTelemetry(it, source) } }; broadcastStatus("Device disconnected: $source")
        }
    }
    private fun startChild(host: String, code: String) {
        if (running) return; if (!validIpv4(host) || code.length != 6) { broadcastStatus("Enter a valid parent IPv4 address and 6-digit code"); return }
        running = true; startForeground(NOTIFICATION_ID, notification("Child mode: sending status to parent"))
        worker = executor.submit { try { Socket(host, PORT).use { socket ->
            clientSocket = socket; socket.soTimeout = 15_000; val reader = BufferedReader(InputStreamReader(socket.getInputStream())); val writer = PrintWriter(socket.getOutputStream(), true)
            writer.println("HELLO|$code|${Build.MANUFACTURER} ${Build.MODEL}"); if (reader.readLine() != "OK") { broadcastStatus("Parent rejected the pairing code"); return@submit }
            broadcastStatus("Connected to parent"); socket.soTimeout = 0; val metrics = MetricsReader(this)
            while (running) { val s = metrics.read(); val t = Telemetry(s.batteryPercent, s.charging, s.cpuPercent, s.ramPercent, s.batteryMinutesRemaining); writer.println(t.encode()); broadcastTelemetry(t, "${Build.MANUFACTURER} ${Build.MODEL}"); Thread.sleep(5_000) }
        } } catch (_: InterruptedException) { Thread.currentThread().interrupt() } catch (_: Exception) { if (running) broadcastStatus("Could not connect to parent") } finally { clientSocket = null } }
    }
    private fun stopMonitoring() { running = false; try { clientSocket?.close() } catch (_: Exception) { }; worker?.cancel(true); worker = null; lastTriggered.clear(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { running = false; try { clientSocket?.close() } catch (_: Exception) { }; worker?.cancel(true); executor.shutdownNow(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun broadcastTelemetry(t: Telemetry, source: String) {
        sendBroadcast(Intent(ACTION_TELEMETRY).apply { setPackage(packageName); putExtra(EXTRA_BATTERY, t.batteryPercent); putExtra(EXTRA_CHARGING, t.charging); putExtra(EXTRA_CPU, t.cpuPercent); putExtra(EXTRA_RAM, t.ramPercent); putExtra(EXTRA_MINUTES, t.batteryMinutesRemaining ?: -1L); putExtra(EXTRA_TIMESTAMP, t.timestampMillis) }); evaluateRules(t); sendWebhooks(t, source)
    }
    private fun evaluateRules(t: Telemetry) {
        rules.all().filter { it.enabled }.forEach { rule -> val value = when (rule.metric) { "battery" -> t.batteryPercent; "cpu" -> t.cpuPercent; "ram" -> t.ramPercent; else -> return@forEach }
            if (rule.matches(value)) { if (lastTriggered[rule.id] != value) { lastTriggered[rule.id] = value; showRuleNotification(rule, t) } } else lastTriggered.remove(rule.id) }
    }
    private fun sendWebhooks(t: Telemetry, source: String) {
        webhooks.all().filter { it.enabled && it.sendEveryUpdate }.forEach { config -> executor.submit { WebhookSender.send(config, t, source) } }
    }
    private fun showRuleNotification(rule: NotificationRule, t: Telemetry) {
        val value = when (rule.metric) { "battery" -> t.batteryPercent; "cpu" -> t.cpuPercent; "ram" -> t.ramPercent; else -> 0 }
        val manager = getSystemService(NotificationManager::class.java); val channelId = "rule_${rule.id}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(NotificationChannel(channelId, "${rule.title} notifications", rule.importance.coerceIn(1, 4)))
        val openIntent = PendingIntent.getActivity(this, rule.id.hashCode(), Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body = rule.message.replace("{value}", value.toString()).replace("{battery}", t.batteryPercent.toString()).replace("{cpu}", t.cpuPercent.toString()).replace("{ram}", t.ramPercent.toString()).replace("{charging}", if (t.charging) "Charging" else "Not charging").replace("{time}", t.batteryMinutesRemaining?.let(::formatMinutes) ?: "Unavailable")
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, channelId) else Notification.Builder(this)
        builder.setContentTitle(rule.title).setContentText(body).setSmallIcon(android.R.drawable.ic_dialog_info).setAutoCancel(true)
        if (rule.action == "open_app") builder.setContentIntent(openIntent)
        manager.notify(rule.id.hashCode(), builder.build())
    }
    private fun formatMinutes(minutes: Long): String { val hours = minutes / 60; val mins = minutes % 60; return if (hours > 0) "${hours}h ${mins}m" else "${mins}m" }
    private fun broadcastStatus(status: String) { sendBroadcast(Intent(ACTION_STATUS).apply { setPackage(packageName); putExtra(EXTRA_STATUS, status) }) }
    private fun notification(text: String) = Notification.Builder(this, CHANNEL_ID).setContentTitle("App Monitor").setContentText(text).setSmallIcon(android.R.drawable.ic_menu_info_details).setOngoing(true).build()
    private fun createNotificationChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "App Monitor service", NotificationManager.IMPORTANCE_LOW)) }
    companion object {
        const val ACTION_START_LOCAL = "com.therealmangoosey.appmonitor.START_LOCAL"; const val ACTION_START_PARENT = "com.therealmangoosey.appmonitor.START_PARENT"; const val ACTION_START_CHILD = "com.therealmangoosey.appmonitor.START_CHILD"; const val ACTION_STOP = "com.therealmangoosey.appmonitor.STOP"; const val ACTION_TELEMETRY = "com.therealmangoosey.appmonitor.TELEMETRY"; const val ACTION_STATUS = "com.therealmangoosey.appmonitor.STATUS"; const val EXTRA_CODE = "code"; const val EXTRA_HOST = "host"; const val EXTRA_BATTERY = "battery"; const val EXTRA_CHARGING = "charging"; const val EXTRA_CPU = "cpu"; const val EXTRA_RAM = "ram"; const val EXTRA_MINUTES = "minutes"; const val EXTRA_TIMESTAMP = "timestamp"; const val EXTRA_STATUS = "status"; const val PORT = 45820; private const val CHANNEL_ID = "app_monitor_service"; private const val NOTIFICATION_ID = 45820
        private fun validIpv4(value: String): Boolean = try { val parts = value.split('.'); parts.size == 4 && parts.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true } } catch (_: Exception) { false }
    }
}
