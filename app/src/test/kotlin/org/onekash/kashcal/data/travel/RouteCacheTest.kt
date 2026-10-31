package org.onekash.kashcal.data.travel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteCacheTest {
    private var now = 0L
    private val cache = RouteCache { now }
    private val result = RoutingResult(20, 0, RoutingSource.TOMTOM, true, false)

    @Test
    fun `returns a stored trip`() {
        cache.put(-23.5503, -46.6339, -23.5614, -46.6559, TravelMode.CAR, result)
        assertEquals(result, cache.get(-23.5503, -46.6339, -23.5614, -46.6559, TravelMode.CAR))
    }

    @Test
    fun `points within about 100 m share an entry`() {
        cache.put(-23.5503, -46.6339, -23.5614, -46.6559, TravelMode.CAR, result)
        assertEquals(result, cache.get(-23.55034, -46.63386, -23.56141, -46.65594, TravelMode.CAR))
    }

    @Test
    fun `a farther point misses`() {
        cache.put(-23.5503, -46.6339, -23.5614, -46.6559, TravelMode.CAR, result)
        assertNull(cache.get(-23.5603, -46.6339, -23.5614, -46.6559, TravelMode.CAR))
    }

    @Test
    fun `another mode misses`() {
        cache.put(-23.5503, -46.6339, -23.5614, -46.6559, TravelMode.CAR, result)
        assertNull(cache.get(-23.5503, -46.6339, -23.5614, -46.6559, TravelMode.FOOT))
    }

    @Test
    fun `expires after the ttl`() {
        cache.put(-23.5503, -46.6339, -23.5614, -46.6559, TravelMode.CAR, result)
        now = RouteCache.TTL_MS
        assertEquals(result, cache.get(-23.5503, -46.6339, -23.5614, -46.6559, TravelMode.CAR))
        now = RouteCache.TTL_MS + 1
        assertNull(cache.get(-23.5503, -46.6339, -23.5614, -46.6559, TravelMode.CAR))
    }

    @Test
    fun `drops the least recently used past the limit`() {
        repeat(RouteCache.MAX_ENTRIES + 1) { i ->
            cache.put(i.toDouble(), 0.0, 0.0, 0.0, TravelMode.CAR, result)
        }
        assertNull(cache.get(0.0, 0.0, 0.0, 0.0, TravelMode.CAR))
        assertEquals(result, cache.get(RouteCache.MAX_ENTRIES.toDouble(), 0.0, 0.0, 0.0, TravelMode.CAR))
    }
}
