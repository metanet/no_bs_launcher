package dev.basri.android.nobs_launcher.stats

object SystemStatsParsers {
    fun parseCpuCounters(line: String): CpuCounters? {
        val fields = line.trim().split(Regex("\\s+"))
        if (fields.firstOrNull() != "cpu") return null
        val ticks = fields.drop(1).map { it.toLongOrNull() ?: return null }
        if (ticks.size < 4) return null
        val idle = ticks[3] + ticks.getOrElse(4) { 0L }
        return CpuCounters(
            idleTicks = idle,
            totalTicks = ticks.sum(),
        )
    }
}
