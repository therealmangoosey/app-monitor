package com.therealmangoosey.appmonitor

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.SystemClock
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.roundToInt

/** Reads battery, charging state, CPU and RAM information from this Android device. */
class MetricsReader(private val context: Context) {
    data class Snapshot(
        val batteryPercent: Int,
        val charging: Boolean,
        val cpuPercent: Int,
        val ramPercent: Int,
        val batteryMinutesRemaining: Long?
    )

    fun read(): Snapshot {
        return Snapshot(
            batteryPercent = readBatteryPercent(),
            charging = readCharging(),
            cpuPercent = readCpuPercent(),
            ramPercent = readRamPercent(),
            batteryMinutesRemaining = estimateBatteryMinutes()
        )
    }

    private fun batteryIntent() = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))

    private fun readBatteryPercent(): Int {
        val manager = context.getSystemService(BatteryManager::class.java)
        val property = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (property >= 0) return property.coerceIn(0, 100)
        val level = batteryIntent()?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent()?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100f / scale).roundToInt().coerceIn(0, 100) else 0
    }

    private fun readCharging(): Boolean {
        val status = batteryIntent()?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun readCpuPercent(): Int {
        val first = cpuCounters() ?: return 0
        SystemClock.sleep(180)
        val second = cpuCounters() ?: return 0
        val totalDelta = second.first - first.first
        val idleDelta = second.second - first.second
        if (totalDelta <= 0L) return 0
        return ((1f - idleDelta.toFloat() / totalDelta.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    }

    private fun cpuCounters(): Pair<Long, Long>? {
        return try {
            RandomAccessFile("/proc/stat", "r").use { file ->
                val line = file.readLine() ?: return null
                val values = line.trim().split(Regex("\\s+"))
                if (values.size < 5 || values[0] != "cpu") return null
                val counters = values.drop(1).map { it.toLongOrNull() ?: 0L }
                val idle = counters.getOrNull(3) ?: 0L
                counters.sum() to idle
            }
        } catch (_: Exception) { null }
    }

    private fun readRamPercent(): Int {
        val manager = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        if (info.totalMem <= 0L) return 0
        return ((1f - info.availMem.toFloat() / info.totalMem.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    }

    /** Uses the hardware charge counter/current when available; otherwise reports unavailable. */
    private fun estimateBatteryMinutes(): Long? {
        return try {
            val manager = context.getSystemService(BatteryManager::class.java)
            val chargeUah = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            val currentUa = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (chargeUah <= 0L || currentUa >= -1L) return null
            val hours = chargeUah.toDouble() / abs(currentUa).toDouble()
            if (!hours.isFinite() || hours <= 0.0) null else (hours * 60.0).roundToInt().toLong()
        } catch (_: Exception) { null }
    }
}
