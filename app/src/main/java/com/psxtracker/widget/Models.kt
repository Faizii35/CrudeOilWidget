package com.psxtracker.widget

/** One row from the PSX "Market Watch" table for a single listed symbol. */
data class StockQuote(
    val symbol: String,
    val sector: String,
    val indices: Set<String>,
    val ldcp: Double,        // last day close price
    val current: Double,
    val change: Double,
    val changePercent: Double,
    val volume: Long
) {
    val isKmi30: Boolean get() = indices.contains("KMI30")
    val isKmiAllShare: Boolean get() = indices.contains("KMIALLSHR")
    val isTracked: Boolean get() = isKmi30 || isKmiAllShare

    val priceFormatted: String get() = String.format("%.2f", current)
    val changeFormatted: String get() {
        val sign = if (change >= 0) "+" else ""
        return "$sign${String.format("%.2f", change)} (${sign}${String.format("%.2f", changePercent)}%)"
    }
}

/** One row from the PSX "Market Indices" table (index-level, not a single stock). */
data class IndexQuote(
    val name: String,
    val current: Double,
    val change: Double,
    val changePercent: Double
) {
    val changeFormatted: String get() {
        val sign = if (change >= 0) "+" else ""
        return "$sign${String.format("%.2f", change)} (${sign}${String.format("%.2f", changePercent)}%)"
    }
}

enum class Trend { UP, DOWN, NEUTRAL }
fun Double.toTrend(): Trend = when {
    this > 0 -> Trend.UP
    this < 0 -> Trend.DOWN
    else -> Trend.NEUTRAL
}
