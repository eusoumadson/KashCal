package org.onekash.kashcal.data.travel

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RushHourEstimatorTest {
    private val zone = ZoneId.of("America/Sao_Paulo")

    private fun ms(y: Int, m: Int, d: Int, h: Int, min: Int) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    // 2026-11-02 is a Monday, 2026-11-07 a Saturday
    @Test
    fun `weekday morning window is rush hour`() {
        assertTrue(RushHourEstimator.isRushHour(ms(2026, 11, 2, 8, 0), zone))
        assertTrue(RushHourEstimator.isRushHour(ms(2026, 11, 2, 7, 30), zone))
        assertTrue(RushHourEstimator.isRushHour(ms(2026, 11, 2, 9, 30), zone))
    }

    @Test
    fun `weekday evening window is rush hour`() {
        assertTrue(RushHourEstimator.isRushHour(ms(2026, 11, 3, 18, 0), zone))
    }

    @Test
    fun `outside the windows is not rush hour`() {
        assertFalse(RushHourEstimator.isRushHour(ms(2026, 11, 2, 7, 29), zone))
        assertFalse(RushHourEstimator.isRushHour(ms(2026, 11, 2, 12, 0), zone))
        assertFalse(RushHourEstimator.isRushHour(ms(2026, 11, 2, 21, 0), zone))
    }

    @Test
    fun `weekend is never rush hour`() {
        assertFalse(RushHourEstimator.isRushHour(ms(2026, 11, 7, 8, 0), zone))
        assertFalse(RushHourEstimator.isRushHour(ms(2026, 11, 8, 18, 0), zone))
    }

    @Test
    fun `the window is read in the given zone`() {
        val eightInSaoPaulo = ms(2026, 11, 2, 8, 0)
        assertTrue(RushHourEstimator.isRushHour(eightInSaoPaulo, zone))
        assertFalse(RushHourEstimator.isRushHour(eightInSaoPaulo, ZoneId.of("UTC"))) // 11:00 UTC
    }

    @Test
    fun `margin adds thirty percent rounded up`() {
        assertEquals(26, RushHourEstimator.withMargin(20))
        assertEquals(13, RushHourEstimator.withMargin(10))
        assertEquals(15, RushHourEstimator.withMargin(11)) // 14.3 rounds up
        assertEquals(2, RushHourEstimator.withMargin(1))
    }
}
