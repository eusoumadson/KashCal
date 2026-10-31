package org.onekash.kashcal.data.travel

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * FORK:travel-time — A margin for weekday rush hours, applied to a car trip whose time comes from
 * a source with no live traffic. Without it the offline estimate and the static map servers would
 * tell the user to leave too late exactly when roads are slowest.
 */
object RushHourEstimator {
    /** The extra share of the trip time added during a rush hour, in percent. */
    const val MARGIN_PERCENT = 30

    private val MORNING = LocalTime.of(7, 30)..LocalTime.of(9, 30)
    private val EVENING = LocalTime.of(17, 30)..LocalTime.of(19, 30)

    /** Returns whether [epochMs] falls on a weekday, in a rush-hour window of [zone]. */
    fun isRushHour(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val local = Instant.ofEpochMilli(epochMs).atZone(zone)
        if (local.dayOfWeek == DayOfWeek.SATURDAY || local.dayOfWeek == DayOfWeek.SUNDAY) return false
        val time = local.toLocalTime()
        return time in MORNING || time in EVENING
    }

    /** Returns [minutes] plus the rush-hour margin, rounded up. */
    fun withMargin(minutes: Int): Int = (minutes * (100 + MARGIN_PERCENT) + 99) / 100
}
