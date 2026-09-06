package com.psxtracker.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    const val CHANNEL_ID_ALERT = "psx_stock_alerts"

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID_ALERT, "PSX Price Alerts", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Fires when a KMI30 / KMI All Share stock crosses one of your % move thresholds"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
            enableLights(true)
            lightColor = Color.GREEN
            val audioAttr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), audioAttr)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun createSimpleNotification(context: Context, title: String, content: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID_ALERT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** Post one notification per moving stock. */
    fun postMoverAlerts(context: Context, movers: List<Pair<StockQuote, Int>>) {
        if (movers.isEmpty()) return
        val manager = NotificationManagerCompat.from(context)

        val openAppPi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        for ((quote, tier) in movers) {
            val isUp = quote.change >= 0
            val arrow = if (isUp) "\u25B2" else "\u25BC"
            val title = "$arrow ${quote.symbol} ${if (isUp) "+" else ""}${String.format("%.2f", quote.changePercent)}%  (\u2265${tier}% alert)"
            val body = "Rs. ${quote.priceFormatted}   ${quote.changeFormatted}   \u00b7   ${quote.sector}"

            val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERT)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(openAppPi)
                .setColor(if (isUp) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
                .build()

            try {
                manager.notify(quote.symbol.hashCode(), notification)
            } catch (_: SecurityException) {
                // Notification permission not granted - nothing we can do here.
            }
        }
    }
}
