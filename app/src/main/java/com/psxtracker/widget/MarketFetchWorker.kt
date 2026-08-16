package com.psxtracker.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

class MarketFetchWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "MarketFetchWorker"
        const val WORK_NAME = "psx_market_fetch"
        const val KEY_FORCE = "force"

        /** WorkManager's minimum periodic interval is 15 min - matches PSX's own "delayed 5 min" data anyway. */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<MarketFetchWorker>(
                15, TimeUnit.MINUTES, 5, TimeUnit.MINUTES
            ).setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
            Log.d(TAG, "Periodic fetch scheduled")
        }

        /** Manual refresh always runs even outside market hours. */
        fun runNow(context: Context, force: Boolean = true) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val data = Data.Builder().putBoolean(KEY_FORCE, force).build()
            val request = OneTimeWorkRequestBuilder<MarketFetchWorker>()
                .setConstraints(constraints).setInputData(data).addTag(TAG).build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result {
        val force = inputData.getBoolean(KEY_FORCE, false)
        if (!force && !MarketHours.isMarketOpen()) {
            Log.d(TAG, "Outside market hours - skipping fetch")
            return Result.success()
        }

        val stocksResult = MarketRepository.fetchTrackedStocks()
        val indicesResult = MarketRepository.fetchTrackedIndices()

        val stocks = stocksResult.getOrNull()
        if (stocks == null) {
            MarketCache.saveError(context, stocksResult.exceptionOrNull()?.message ?: "Unknown error")
            updateWidgets()
            return Result.retry()
        }

        val enabledThresholds = AlertPreferences.getThresholds(context)
        val movers = mutableListOf<Pair<StockQuote, Int>>()
        var moversAboveAnyThreshold = 0

        if (enabledThresholds.isNotEmpty()) {
            for (stock in stocks) {
                val tier = AlertPreferences.matchedTier(stock.changePercent, enabledThresholds) ?: continue
                moversAboveAnyThreshold++
                if (NotifiedState.shouldNotifyAndRecord(context, stock.symbol, tier)) {
                    movers.add(stock to tier)
                }
            }
        }

        if (movers.isNotEmpty()) {
            NotificationHelper.postMoverAlerts(context, movers)
        }

        val indices = indicesResult.getOrElse { emptyList() }
        MarketCache.save(context, indices, moversAboveAnyThreshold)
        updateWidgets()

        Log.d(TAG, "Fetched ${stocks.size} tracked stocks, ${movers.size} new alerts this cycle")
        return Result.success()
    }

    private fun updateWidgets() {
        val ids = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, KmiWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val intent = Intent(context, KmiWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        context.sendBroadcast(intent)
    }
}
