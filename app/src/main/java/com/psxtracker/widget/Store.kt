package com.psxtracker.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "psx_kmi_prefs")

/** All the % thresholds the user can toggle on. */
val ALL_THRESHOLDS = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

/** User-selected notification thresholds for up/down moves. */
object AlertPreferences {
    private val THRESHOLDS_UP_KEY = stringSetPreferencesKey("enabled_thresholds_up")
    private val THRESHOLDS_DOWN_KEY = stringSetPreferencesKey("enabled_thresholds_down")

    suspend fun getThresholdsUp(context: Context): Set<Int> =
        context.dataStore.data.first()[THRESHOLDS_UP_KEY]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

    suspend fun getThresholdsDown(context: Context): Set<Int> =
        context.dataStore.data.first()[THRESHOLDS_DOWN_KEY]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

    suspend fun setThresholdsUp(context: Context, thresholds: Set<Int>) {
        context.dataStore.edit { prefs -> prefs[THRESHOLDS_UP_KEY] = thresholds.map { it.toString() }.toSet() }
    }

    suspend fun setThresholdsDown(context: Context, thresholds: Set<Int>) {
        context.dataStore.edit { prefs -> prefs[THRESHOLDS_DOWN_KEY] = thresholds.map { it.toString() }.toSet() }
    }

    /** Highest enabled threshold that |changePercent| has crossed, or null if none. */
    fun matchedTier(changePercent: Double, enabledUp: Set<Int>, enabledDown: Set<Int>): Int? {
        return if (changePercent >= 0) {
            enabledUp.filter { changePercent >= it }.maxOrNull()
        } else {
            enabledDown.filter { kotlin.math.abs(changePercent) >= it }.maxOrNull()
        }
    }
}

/** Specific alerts per stock symbol. */
object StockAlertPreferences {
    private val SPECIFIC_ALERTS_KEY = stringPreferencesKey("specific_stock_alerts")

    private suspend fun readMap(context: Context): MutableMap<String, Int> {
        val json = context.dataStore.data.first()[SPECIFIC_ALERTS_KEY] ?: return mutableMapOf()
        val out = mutableMapOf<String, Int>()
        try {
            val obj = JSONObject(json)
            obj.keys().forEach { symbol ->
                out[symbol] = obj.getInt(symbol)
            }
        } catch (_: Exception) { }
        return out
    }

    private suspend fun writeMap(context: Context, map: Map<String, Int>) {
        val obj = JSONObject()
        map.forEach { (symbol, threshold) ->
            obj.put(symbol, threshold)
        }
        context.dataStore.edit { prefs -> prefs[SPECIFIC_ALERTS_KEY] = obj.toString() }
    }

    suspend fun getThreshold(context: Context, symbol: String): Int? {
        return readMap(context)[symbol]
    }

    suspend fun setThreshold(context: Context, symbol: String, threshold: Int?) {
        val map = readMap(context)
        if (threshold == null) {
            map.remove(symbol)
        } else {
            map[symbol] = threshold
        }
        writeMap(context, map)
    }

    suspend fun getAllSpecificAlerts(context: Context): Map<String, Int> = readMap(context)
}

/**
 * Dedupe store: remembers, per symbol, the highest alert tier already notified TODAY
 * (Asia/Karachi date). Prevents re-notifying every poll cycle while a stock sits above
 * a threshold, but still notifies again if it climbs to a higher tier, and naturally
 * resets itself the next trading day.
 */
object NotifiedState {
    private val STATE_KEY = stringPreferencesKey("notified_state_json")
    private val zone = ZoneId.of("Asia/Karachi")

    private suspend fun readMap(context: Context): MutableMap<String, Pair<Int, String>> {
        val json = context.dataStore.data.first()[STATE_KEY] ?: return mutableMapOf()
        val out = mutableMapOf<String, Pair<Int, String>>()
        try {
            val obj = JSONObject(json)
            obj.keys().forEach { symbol ->
                val entry = obj.getJSONObject(symbol)
                out[symbol] = entry.getInt("tier") to entry.getString("date")
            }
        } catch (_: Exception) { /* corrupt/empty - start fresh */ }
        return out
    }

    private suspend fun writeMap(context: Context, map: Map<String, Pair<Int, String>>) {
        val obj = JSONObject()
        map.forEach { (symbol, pair) ->
            val entry = JSONObject()
            entry.put("tier", pair.first)
            entry.put("date", pair.second)
            obj.put(symbol, entry)
        }
        context.dataStore.edit { prefs -> prefs[STATE_KEY] = obj.toString() }
    }

    /** Returns true (and records it) if this symbol should be notified for [tier] now. */
    suspend fun shouldNotifyAndRecord(context: Context, symbol: String, tier: Int): Boolean {
        val today = LocalDate.now(zone).toString()
        val map = readMap(context)
        val existing = map[symbol]
        val alreadyCovered = existing != null && existing.second == today && existing.first >= tier
        if (alreadyCovered) return false
        map[symbol] = tier to today
        writeMap(context, map)
        return true
    }
}

/** Small cache of the last successful fetch, used to render the widget instantly on load. */
object MarketCache {
    private val INDICES_JSON = stringPreferencesKey("cached_indices_json")
    private val MOVERS_COUNT = intPreferencesKey("cached_movers_count")
    private val LAST_UPDATED = longPreferencesKey("cached_last_updated")
    private val LAST_ERROR = stringPreferencesKey("cached_last_error")

    suspend fun save(context: Context, indices: List<IndexQuote>, moversCount: Int) {
        val arr = JSONArray()
        indices.forEach { q ->
            val o = JSONObject()
            o.put("name", q.name); o.put("current", q.current)
            o.put("change", q.change); o.put("changePercent", q.changePercent)
            arr.put(o)
        }
        context.dataStore.edit { prefs ->
            prefs[INDICES_JSON] = arr.toString()
            prefs[MOVERS_COUNT] = moversCount
            prefs[LAST_UPDATED] = System.currentTimeMillis()
            prefs[LAST_ERROR] = ""
        }
    }

    suspend fun saveError(context: Context, message: String) {
        context.dataStore.edit { prefs -> prefs[LAST_ERROR] = message }
    }

    data class Cached(val indices: List<IndexQuote>, val moversCount: Int, val lastUpdated: Long, val error: String)

    suspend fun load(context: Context): Cached {
        val prefs = context.dataStore.data.first()
        val json = prefs[INDICES_JSON] ?: "[]"
        val list = mutableListOf<IndexQuote>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(IndexQuote(o.getString("name"), o.getDouble("current"), o.getDouble("change"), o.getDouble("changePercent")))
            }
        } catch (_: Exception) { }
        return Cached(
            indices = list,
            moversCount = prefs[MOVERS_COUNT] ?: 0,
            lastUpdated = prefs[LAST_UPDATED] ?: 0L,
            error = prefs[LAST_ERROR] ?: ""
        )
    }
}
