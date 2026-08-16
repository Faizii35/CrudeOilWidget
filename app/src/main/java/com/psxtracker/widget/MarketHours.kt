package com.psxtracker.widget

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * PSX regular market session, Pakistan time. Fetching/alerts are paused outside
 * these windows so we don't hammer the site (and drain battery) overnight.
 *
 * Mon-Thu: 09:15 - 15:30
 * Fri:     09:15 - 12:00 and 14:30 - 16:30 (Friday prayer break)
 *
 * Adjust here if PSX changes its official session timings or for public holidays.
 */
object MarketHours {

    private val ZONE = ZoneId.of("Asia/Karachi")

    fun isMarketOpen(now: ZonedDateTime = ZonedDateTime.now(ZONE)): Boolean {
        val karachiNow = now.withZoneSameInstant(ZONE)
        val day = karachiNow.dayOfWeek
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return false

        val t = karachiNow.toLocalTime()
        return if (day == DayOfWeek.FRIDAY) {
            t.isInRange(LocalTime.of(9, 15), LocalTime.of(12, 0)) ||
                t.isInRange(LocalTime.of(14, 30), LocalTime.of(16, 30))
        } else {
            t.isInRange(LocalTime.of(9, 15), LocalTime.of(15, 30))
        }
    }

    private fun LocalTime.isInRange(start: LocalTime, end: LocalTime): Boolean =
        !this.isBefore(start) && !this.isAfter(end)
}
