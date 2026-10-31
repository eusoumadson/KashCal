package org.onekash.kashcal.data.travel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalHaversineProviderTest {
    private val provider = LocalHaversineProvider()

    // Praça da Sé to Avenida Paulista (MASP), São Paulo: about 3.2 km in a straight line
    private val seLat = -23.5503
    private val seLon = -46.6339
    private val paulistaLat = -23.5614
    private val paulistaLon = -46.6559

    @Test
    fun `haversine distance matches a known pair within a few percent`() {
        val km = GeoMath.haversineKm(seLat, seLon, paulistaLat, paulistaLon)
        assertEquals(2.6, km, 0.3)
    }

    @Test
    fun `same point is zero minutes`() {
        assertEquals(0, provider.estimateMinutes(seLat, seLon, seLat, seLon, TravelMode.CAR))
    }

    @Test
    fun `nearby but different points are at least one minute`() {
        assertEquals(1, provider.estimateMinutes(seLat, seLon, seLat + 0.00001, seLon, TravelMode.CAR))
    }

    @Test
    fun `car estimate is distance times tortuosity over speed rounded up`() {
        val km = GeoMath.haversineKm(seLat, seLon, paulistaLat, paulistaLon)
        val expected = Math.ceil(km * 1.35 / 30.0 * 60).toInt()
        assertEquals(expected, provider.estimateMinutes(seLat, seLon, paulistaLat, paulistaLon, TravelMode.CAR))
    }

    @Test
    fun `walking takes longer than driving, transit sits between`() {
        val car = provider.estimateMinutes(seLat, seLon, paulistaLat, paulistaLon, TravelMode.CAR)
        val transit = provider.estimateMinutes(seLat, seLon, paulistaLat, paulistaLon, TravelMode.TRANSIT)
        val foot = provider.estimateMinutes(seLat, seLon, paulistaLat, paulistaLon, TravelMode.FOOT)
        assertTrue("car $car < transit $transit", car < transit)
        assertTrue("transit $transit < foot $foot", transit < foot)
    }
}
