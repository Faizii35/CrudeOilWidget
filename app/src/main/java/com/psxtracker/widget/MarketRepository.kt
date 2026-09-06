package com.psxtracker.widget

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

/**
 * Fetches data from PSX's public Data Portal (dps.psx.com.pk).
 *
 * IMPORTANT: This site does not offer a public API for this use case. We parse
 * its server-rendered HTML pages. PSX's Terms of Use restrict automated access
 * and redistribution of their market data - this app is intended for a single
 * person's own, personal, non-commercial, on-device use only. If PSX changes
 * page markup, the CSS selectors below (deliberately written to key off column
 * headers rather than brittle class names) may need to be re-checked against
 * the live page source.
 */
object MarketRepository {

    private const val TAG = "MarketRepository"
    private const val MARKET_WATCH_URL = "https://dps.psx.com.pk/market-watch"
    private const val INDICES_URL = "https://dps.psx.com.pk/indices"

    val TRACKED_INDICES = setOf("KMI30", "KMIALLSHR")

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Cache-Control", "no-cache")
                .build()
            chain.proceed(request)
        }
        .build()

    private suspend fun fetchDoc(url: String): Result<Document> = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code} for $url"))
            }
            val html = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response from $url"))
            Result.success(Jsoup.parse(html, url))
        } catch (e: Exception) {
            Log.e(TAG, "Fetch failed for $url", e)
            Result.failure(e)
        }
    }

    /** Every stock listed under KMI30 and/or KMIALLSHR, with live price/change data. */
    suspend fun fetchTrackedStocks(): Result<List<StockQuote>> {
        val doc = fetchDoc(MARKET_WATCH_URL).getOrElse { return Result.failure(it) }
        return try {
            val all = parseMarketWatch(doc)
            if (all.isEmpty()) {
                Result.failure(Exception("Parsed 0 rows from market-watch - page structure may have changed"))
            } else {
                Result.success(all.filter { it.isTracked })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error (market-watch)", e)
            Result.failure(e)
        }
    }

    /** Headline KMI30 / KMIALLSHR index values (not individual stocks) - used for the widget summary. */
    suspend fun fetchTrackedIndices(): Result<List<IndexQuote>> {
        val doc = fetchDoc(INDICES_URL).getOrElse { return Result.failure(it) }
        return try {
            val all = parseIndicesTable(doc)
            val tracked = all.filter { it.name in TRACKED_INDICES }
            if (tracked.isEmpty()) Result.failure(Exception("Could not find KMI30/KMIALLSHR rows"))
            else Result.success(tracked)
        } catch (e: Exception) {
            Log.e(TAG, "Parse error (indices)", e)
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------------------
    // Parsing: keyed off column HEADER TEXT rather than CSS classes, so small
    // markup/styling changes on PSX's side are less likely to break this.
    // ---------------------------------------------------------------------

    private fun parseMarketWatch(doc: Document): List<StockQuote> {
        val table = doc.select("table").firstOrNull { table ->
            table.select("thead th, tr th").any { it.text().uppercase().contains("SYMBOL") }
        } ?: doc.select("table").firstOrNull() ?: return emptyList()

        val headerCells = table.select("thead th").ifEmpty { table.select("tr").firstOrNull()?.select("th") ?: return emptyList() }
        val colIndex = headerCells.mapIndexed { i, el -> el.text().trim().uppercase() to i }.toMap()

        fun idxOf(vararg names: String): Int? = names.firstNotNullOfOrNull { colIndex[it] }

        val symbolCol = idxOf("SYMBOL") ?: 0
        val sectorCol = idxOf("SECTOR", "SECTOR NAME")
        val listedInCol = idxOf("LISTED IN")
        val ldcpCol = idxOf("LDCP")
        val currentCol = idxOf("CURRENT")
        val changeCol = idxOf("CHANGE")
        val changePctCol = idxOf("CHANGE (%)", "CHANGE(%)", "CHANGE %")
        val volumeCol = idxOf("VOLUME")

        val rows = table.select("tbody tr").ifEmpty { table.select("tr").drop(1) }
        val result = mutableListOf<StockQuote>()

        for (row in rows) {
            val cells = row.select("td")
            if (cells.isEmpty() || cells.size <= symbolCol) continue

            // Symbol cell usually contains an <a href="/company/SYM"> plus a status badge span (e.g. "NC", "XD").
            val symbolCell = cells[symbolCol]
            val symbol = symbolCell.select("a").firstOrNull()?.text()?.trim()
                ?: symbolCell.text().trim().split(Regex("\\s+")).firstOrNull()
                ?: continue
            if (symbol.isBlank()) continue

            val listedIn = listedInCol?.let { cells.getOrNull(it)?.text() } ?: ""
            val indices = listedIn.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet()
            // Skip rows that aren't equities in either tracked index at all (cheap early exit for huge table).
            if (indices.none { it in TRACKED_INDICES }) continue

            val sectorCell = sectorCol?.let { cells.getOrNull(it) }
            val rawSector = sectorCell?.attr("title")?.takeIf { it.isNotBlank() }
                ?: sectorCell?.attr("data-title")?.takeIf { it.isNotBlank() }
                ?: sectorCell?.text()?.trim()
                ?: ""
            val sector = SectorNames.getName(rawSector)
            
            val ldcp = ldcpCol?.let { cells.getOrNull(it)?.text()?.toCleanDouble() } ?: 0.0
            val current = currentCol?.let { cells.getOrNull(it)?.text()?.toCleanDouble() } ?: 0.0
            val change = changeCol?.let { cells.getOrNull(it)?.text()?.toCleanDouble() } ?: (current - ldcp)
            val changePct = changePctCol?.let { cells.getOrNull(it)?.text()?.toCleanDouble() }
                ?: if (ldcp != 0.0) (change / ldcp) * 100.0 else 0.0
            val volume = volumeCol?.let { cells.getOrNull(it)?.text()?.replace(",", "")?.toLongOrNull() } ?: 0L

            result.add(
                StockQuote(
                    symbol = symbol,
                    sector = sector,
                    indices = indices,
                    ldcp = ldcp,
                    current = current,
                    change = change,
                    changePercent = changePct,
                    volume = volume
                )
            )
        }
        return result
    }

    private fun parseIndicesTable(doc: Document): List<IndexQuote> {
        val table = doc.select("table").firstOrNull { t ->
            t.select("thead th, tr th").any { it.text().uppercase().contains("INDEX") }
        } ?: return emptyList()

        val headerCells = table.select("thead th").ifEmpty { table.select("tr").firstOrNull()?.select("th") ?: return emptyList() }
        val colIndex = headerCells.mapIndexed { i, el -> el.text().trim().uppercase() to i }.toMap()
        fun idxOf(vararg names: String): Int? = names.firstNotNullOfOrNull { colIndex[it] }

        val nameCol = idxOf("INDEX") ?: 0
        val currentCol = idxOf("CURRENT")
        val changeCol = idxOf("CHANGE")
        val changePctCol = idxOf("% CHANGE", "CHANGE (%)", "CHANGE(%)")

        val rows = table.select("tbody tr").ifEmpty { table.select("tr").drop(1) }
        val out = mutableListOf<IndexQuote>()
        for (row in rows) {
            val cells = row.select("td")
            if (cells.isEmpty() || cells.size <= nameCol) continue
            val name = cells[nameCol].select("a").firstOrNull()?.text()?.trim()
                ?: cells[nameCol].text().trim()
            if (name.isBlank()) continue
            val current = currentCol?.let { cells.getOrNull(it)?.text()?.toCleanDouble() } ?: continue
            val change = changeCol?.let { cells.getOrNull(it)?.text()?.toCleanDouble() } ?: 0.0
            val changePct = changePctCol?.let { cells.getOrNull(it)?.text()?.toCleanDouble() } ?: 0.0
            out.add(IndexQuote(name.uppercase(), current, change, changePct))
        }
        return out
    }

    private fun String?.toCleanDouble(): Double {
        if (this == null) return 0.0
        val cleaned = this.trim().replace(",", "").replace("%", "").replace("+", "")
        return cleaned.toDoubleOrNull() ?: 0.0
    }
}
