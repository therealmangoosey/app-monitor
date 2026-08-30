package com.therealmangoosey.appmonitor

data class Telemetry(
    val batteryPercent: Int,
    val cpuPercent: Int,
    val ramPercent: Int,
    val batteryMinutesRemaining: Long?,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    fun encode(): String {
        val minutes = batteryMinutesRemaining?.toString() ?: "-1"
        return "STAT|$batteryPercent|$cpuPercent|$ramPercent|$minutes|$timestampMillis"
    }

    companion object {
        fun decode(line: String): Telemetry? {
            val parts = line.trim().split('|')
            if (parts.size != 6 || parts[0] != "STAT") return null
            val battery = parts[1].toIntOrNull() ?: return null
            val cpu = parts[2].toIntOrNull() ?: return null
            val ram = parts[3].toIntOrNull() ?: return null
            val minutesRaw = parts[4].toLongOrNull() ?: return null
            val timestamp = parts[5].toLongOrNull() ?: return null
            return Telemetry(
                batteryPercent = battery.coerceIn(0, 100),
                cpuPercent = cpu.coerceIn(0, 100),
                ramPercent = ram.coerceIn(0, 100),
                batteryMinutesRemaining = minutesRaw.takeIf { it >= 0 },
                timestampMillis = timestamp
            )
        }
    }
}
