package com.psxtracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class KmiWidgetProvider : AppWidgetProvider() {

    companion object {
        const val TAG = "KmiWidgetProvider"
        const val ACTION_MANUAL_REFRESH = "com.psxtracker.widget.ACTION_MANUAL_REFRESH"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAllWidgets(context: Context, appWidgetManager: AppWidgetManager, widgetIds: IntArray) {
            scope.launch {
                val cached = MarketCache.load(context)
                val views = buildViews(context, cached)
                for (id in widgetIds) {
                    appWidgetManager.updateAppWidget(id, views)
                }
            }
        }

        private fun buildViews(context: Context, cached: MarketCache.Cached): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_kmi)

            val refreshPi = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, KmiWidgetProvider::class.java).apply { action = ACTION_MANUAL_REFRESH },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.btn_refresh, refreshPi)

            val openPi = PendingIntent.getActivity(
                context, 1, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPi)

            val kmi30 = cached.indices.firstOrNull { it.name == "KMI30" }
            val kmiAll = cached.indices.firstOrNull { it.name == "KMIALLSHR" }

            fun bindRow(labelViewId: Int, valueViewId: Int, changeViewId: Int, quote: IndexQuote?, label: String) {
                views.setTextViewText(labelViewId, label)
                if (quote != null) {
                    views.setTextViewText(valueViewId, String.format("%,.2f", quote.current))
                    views.setTextViewText(changeViewId, quote.changeFormatted)
                    val color = when {
                        quote.change > 0 -> Color.parseColor("#4CAF50")
                        quote.change < 0 -> Color.parseColor("#F44336")
                        else -> Color.parseColor("#BDBDBD")
                    }
                    views.setTextColor(changeViewId, color)
                } else {
                    views.setTextViewText(valueViewId, "\u2014")
                    views.setTextViewText(changeViewId, "")
                }
            }

            bindRow(R.id.tv_kmi30_label, R.id.tv_kmi30_value, R.id.tv_kmi30_change, kmi30, "KMI30")
            bindRow(R.id.tv_kmiall_label, R.id.tv_kmiall_value, R.id.tv_kmiall_change, kmiAll, "KMI ALL SHR")

            val time = if (cached.lastUpdated > 0)
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(cached.lastUpdated))
            else "--:--"

            if (cached.error.isNotEmpty() && cached.lastUpdated == 0L) {
                views.setTextViewText(R.id.tv_status, "Error: ${cached.error}")
            } else {
                views.setTextViewText(
                    R.id.tv_status,
                    "${cached.moversCount} movers past your alerts \u00b7 Updated $time"
                )
            }

            return views
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate: ${appWidgetIds.size} widgets")
        updateAllWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MANUAL_REFRESH) {
            Log.d(TAG, "Manual refresh")
            MarketFetchWorker.runNow(context, force = true)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MarketFetchWorker.schedule(context)
        MarketFetchWorker.runNow(context, force = true)
    }
}
