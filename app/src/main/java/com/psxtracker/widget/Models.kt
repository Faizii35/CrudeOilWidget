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

object SectorNames {
    private val MAP = mapOf(
        "0801" to "Automobile Assembler",
        "0802" to "Automobile Parts",
        "0803" to "Cable & Electrical Goods",
        "0804" to "Canning",
        "0805" to "Cement",
        "0806" to "Chemical",
        "0807" to "Commercial Banks",
        "0808" to "Engineering",
        "0809" to "Exchange Traded Funds",
        "0810" to "Fertilizer",
        "0811" to "Financial Services",
        "0812" to "Food & Personal Care",
        "0813" to "Glass & Ceramics",
        "0814" to "Insurance",
        "0815" to "Investment Banks",
        "0816" to "Jute",
        "0817" to "Leasing Companies",
        "0818" to "Miscellaneous",
        "0819" to "Modarabas",
        "0820" to "Oil & Gas Exploration",
        "0821" to "Oil & Gas Marketing",
        "0822" to "Paper & Board",
        "0823" to "Pharmaceuticals",
        "0824" to "Power Generation",
        "0825" to "Property",
        "0826" to "REIT",
        "0827" to "Refinery",
        "0828" to "Sugar & Allied",
        "0829" to "Technology & Communication",
        "0830" to "Textile Braid",
        "0831" to "Textile Composite",
        "0832" to "Textile Spinning",
        "0833" to "Textile Weaving",
        "0834" to "Transport",
        "0835" to "Vanaspati",
        "0836" to "Woollen"
    )

    fun getName(code: String): String = MAP[code] ?: code
}
