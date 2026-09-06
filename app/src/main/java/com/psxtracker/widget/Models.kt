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
        "0802" to "Automobile Parts & Accessories",
        "0803" to "Cable & Electrical Goods",
        "0804" to "Cement",
        "0805" to "Chemical",
        "0806" to "Close-End Mutual Fund",
        "0807" to "Commercial Banks",
        "0808" to "Engineering",
        "0809" to "Fertilizer",
        "0810" to "Food & Personal Care Products",
        "0811" to "Glass & Ceramics",
        "0812" to "Insurance",
        "0813" to "Investment Banks / Companies / Securities Companies",
        "0814" to "Jute",
        "0815" to "Leasing Companies",
        "0816" to "Leather & Tanneries",
        "0817" to "Miscellaneous",
        "0818" to "Miscellaneous",
        "0819" to "Modarabas",
        "0820" to "Oil & Gas Exploration Companies",
        "0821" to "Oil & Gas Marketing Companies",
        "0822" to "Paper & Board",
        "0823" to "Pharmaceuticals",
        "0824" to "Power Generation & Distribution",
        "0825" to "Refinery",
        "0826" to "Sugar & Allied Industries",
        "0827" to "Synthetic & Rayon",
        "0828" to "Technology & Communication",
        "0829" to "Textile Composite",
        "0830" to "Textile Spinning",
        "0831" to "Textile Weaving",
        "0832" to "Tobacco",
        "0833" to "Transport",
        "0834" to "Vanaspati & Allied Industries",
        "0835" to "Woollen",
        "0836" to "Exchange Traded Funds",
        "0837" to "Close-End Mutual Fund",
        "0838" to "Modarabas",
        "0839" to "Exchange Traded Funds"
    )

    fun getName(code: String): String = MAP[code] ?: code
}
