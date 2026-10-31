package org.onekash.kashcal.data.travel

import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.onekash.kashcal.data.preferences.KashCalDataStore

class RoutingServiceTest {

    private class Fake(
        override val source: RoutingSource,
        var enabled: Boolean = true,
        var answer: () -> ProviderResult? = { null },
    ) : RoutingProvider {
        var calls = 0
        override suspend fun isEnabled() = enabled
        override suspend fun estimate(
            originLat: Double, originLon: Double, destLat: Double, destLon: Double, mode: TravelMode,
        ): ProviderResult? { calls++; return answer() }
    }

    private val zone = ZoneId.of("America/Sao_Paulo")
    private var now = 0L
    private val dataStore = mockk<KashCalDataStore>()
    private val cache = RouteCache { now }
    private val breaker = CircuitBreaker { now }
    private val tomtom = Fake(RoutingSource.TOMTOM)
    private val stadia = Fake(RoutingSource.STADIA)
    private val valhalla = Fake(RoutingSource.VALHALLA)

    // Monday 2026-11-02 at the given time
    private fun at(h: Int, m: Int) = LocalDateTime.of(2026, 11, 2, h, m).atZone(zone).toInstant().toEpochMilli()
    private val noon = at(12, 0)

    private fun service() = RoutingService(
        listOf(tomtom, stadia, valhalla), LocalHaversineProvider(), dataStore, cache, breaker,
    ) { zone }

    private suspend fun estimate(
        mode: TravelMode = TravelMode.CAR,
        arrival: Long = noon,
        dLat: Double = -23.5614,
    ) = service().estimate(-23.5503, -46.6339, dLat, -46.6559, mode, arrival)

    @Before
    fun setUp() {
        coEvery { dataStore.getTravelRushHourEnabled() } returns true
    }

    @Test
    fun `uses the first enabled provider that answers`() = runTest {
        tomtom.answer = { ProviderResult(30, 8, true) }
        stadia.answer = { ProviderResult(20) }

        val r = estimate()

        assertEquals(RoutingSource.TOMTOM, r.source)
        assertEquals(30, r.minutes)
        assertEquals(8, r.trafficDelayMinutes)
        assertTrue(r.hasLiveTraffic)
        assertEquals(0, stadia.calls)
    }

    @Test
    fun `skips a disabled provider`() = runTest {
        tomtom.enabled = false
        stadia.answer = { ProviderResult(20) }
        assertEquals(RoutingSource.STADIA, estimate().source)
        assertEquals(0, tomtom.calls)
    }

    @Test
    fun `falls through a failing provider to the next`() = runTest {
        tomtom.answer = { null }
        valhalla.answer = { ProviderResult(15) }
        assertEquals(RoutingSource.VALHALLA, estimate().source)
    }

    @Test
    fun `falls back to the local estimate when every provider fails or is off`() = runTest {
        stadia.enabled = false
        valhalla.enabled = false
        tomtom.enabled = false

        val r = estimate()

        assertEquals(RoutingSource.LOCAL, r.source)
        assertTrue(r.isEstimate)
        assertTrue(r.minutes >= 1)
    }

    @Test
    fun `an exception from a provider does not escape`() = runTest {
        tomtom.answer = { throw IllegalStateException("boom") }
        stadia.enabled = false
        valhalla.enabled = false

        assertEquals(RoutingSource.LOCAL, estimate().source)
    }

    @Test
    fun `two failures pause a provider and it is not called again`() = runTest {
        tomtom.answer = { null }
        valhalla.enabled = false
        stadia.enabled = false

        estimate(dLat = -23.51); estimate(dLat = -23.52)
        assertEquals(2, tomtom.calls)
        assertTrue(breaker.isOpen(RoutingSource.TOMTOM))

        estimate(dLat = -23.53)
        assertEquals(2, tomtom.calls)
    }

    @Test
    fun `rate limit pauses a provider at once`() = runTest {
        tomtom.answer = { throw RoutingRateLimitedException("429") }
        stadia.answer = { ProviderResult(20) }

        assertEquals(RoutingSource.STADIA, estimate().source)

        assertTrue(breaker.isOpen(RoutingSource.TOMTOM))
        assertEquals(CircuitBreaker.Reason.RATE_LIMITED, breaker.reasonOf(RoutingSource.TOMTOM))
    }

    @Test
    fun `a rejected key pauses a provider for a long time`() = runTest {
        tomtom.answer = { throw RoutingAuthException("403") }
        stadia.answer = { ProviderResult(20) }

        estimate()

        assertEquals(CircuitBreaker.Reason.BAD_KEY, breaker.reasonOf(RoutingSource.TOMTOM))
    }

    @Test
    fun `a second identical trip is served from the cache`() = runTest {
        tomtom.answer = { ProviderResult(30, 0, true) }
        val first = estimate()
        val second = estimate()
        assertEquals(first, second)
        assertEquals(1, tomtom.calls)
    }

    @Test
    fun `the local estimate is not cached, so a later try can reach a provider`() = runTest {
        tomtom.enabled = false; stadia.enabled = false; valhalla.enabled = false
        estimate()
        valhalla.enabled = true
        valhalla.answer = { ProviderResult(15) }
        assertEquals(RoutingSource.VALHALLA, estimate().source)
    }

    @Test
    fun `rush hour adds the margin to a static car trip`() = runTest {
        valhalla.answer = { ProviderResult(20) }
        tomtom.enabled = false; stadia.enabled = false

        val r = estimate(arrival = at(8, 30))

        assertEquals(26, r.minutes)
        assertTrue(r.isRushHourAdjusted)
    }

    @Test
    fun `no margin outside rush hour, on foot, with live traffic, or when switched off`() = runTest {
        valhalla.answer = { ProviderResult(20) }
        tomtom.enabled = false; stadia.enabled = false
        assertEquals(20, estimate(arrival = noon).minutes)

        cache.clear()
        val foot = estimate(mode = TravelMode.FOOT, arrival = at(8, 30))
        assertEquals(20, foot.minutes); assertFalse(foot.isRushHourAdjusted)

        cache.clear()
        coEvery { dataStore.getTravelRushHourEnabled() } returns false
        val off = estimate(arrival = at(8, 30))
        assertEquals(20, off.minutes); assertFalse(off.isRushHourAdjusted)

        cache.clear()
        coEvery { dataStore.getTravelRushHourEnabled() } returns true
        tomtom.enabled = true
        tomtom.answer = { ProviderResult(40, 15, true) }
        val live = estimate(arrival = at(8, 30))
        assertEquals(40, live.minutes); assertFalse(live.isRushHourAdjusted)
    }

    @Test
    fun `rush hour also applies to the local estimate`() = runTest {
        tomtom.enabled = false; stadia.enabled = false; valhalla.enabled = false
        val calm = estimate(arrival = noon)
        val rush = estimate(arrival = at(8, 30))
        assertNotEquals(calm.minutes, rush.minutes)
        assertTrue(rush.isRushHourAdjusted)
    }

    @Test
    fun `transit adds the walking and waiting overhead to an online route only`() = runTest {
        tomtom.enabled = false; stadia.enabled = false
        valhalla.answer = { ProviderResult(20) }
        assertEquals(30, estimate(mode = TravelMode.TRANSIT).minutes)

        valhalla.enabled = false
        cache.clear()
        val local = LocalHaversineProvider().estimateMinutes(-23.5503, -46.6339, -23.5614, -46.6559, TravelMode.TRANSIT)
        assertEquals(local, estimate(mode = TravelMode.TRANSIT).minutes)
    }
}
